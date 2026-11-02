from __future__ import annotations

import json
import os
from pathlib import Path

import httpx
import pytest
import pytest_asyncio

os.environ.setdefault("ENVIRONMENT", "test")

from app.core.config import Settings, get_settings  # noqa: E402
from app.core.rate_limit import limiter  # noqa: E402
from app.core.security import TokenVerifier, get_token_verifier  # noqa: E402
from app.database.base import Base  # noqa: E402
from app.database.session import dispose_engine, get_engine, init_engine  # noqa: E402
from app.main import create_app  # noqa: E402
from app.services import safe_http  # noqa: E402
from app.services.embeddings import OpenCvDescriptorEmbedder  # noqa: E402

FIXTURES = Path(__file__).parent / "fixtures"
MOCKS = Path(__file__).parent / "mock_responses"
GUEST = {"X-Guest-Id": "3f1c2a8e-1d4b-4c6e-9f2a-7b8c9d0e1f2a"}
ACCOUNT = {"Authorization": "Bearer dev:alice"}


def fixture_bytes(name: str) -> bytes:
    return (FIXTURES / name).read_bytes()


def mock_json(name: str) -> dict:
    return json.loads((MOCKS / name).read_text())


# Thumbnail URLs used in the mock provider responses -> local fixture images
IMAGE_ROUTES = {
    "img.autoblog.test/thumb/red-car.jpg": "sample_car.jpg",
    "wallpapers.test/thumb/car-small.jpg": "sample_car_resized.jpg",
    "art.test/thumb/green.jpg": "sample_unrelated.jpg",
    "img.autoblog.test/thumb/cropped.jpg": "sample_car_cropped.jpg",
    "api.openverse.test/thumb/red.jpg": "sample_car_resized.jpg",
    "preview.redd.test/red.jpg": "sample_car_cropped.jpg",
    "img.tineye.test/match1.jpg": "sample_car.jpg",
}


class MockProviderTransport:
    """Routes provider API calls to recorded responses; counts calls; can inject failures."""

    def __init__(self) -> None:
        self.calls: list[str] = []
        self.fail: dict[str, int] = {}  # host -> status code to return

    def __call__(self, request: httpx.Request) -> httpx.Response:
        host, path = request.url.host, request.url.path
        self.calls.append(f"{host}{path}")
        if host in self.fail:
            return httpx.Response(self.fail[host], json={"error": "injected"})
        key = f"{host}{path}"
        if key in IMAGE_ROUTES:
            return httpx.Response(200, content=fixture_bytes(IMAGE_ROUTES[key]), headers={"content-type": "image/jpeg"})
        if host == "customsearch.googleapis.com":
            return httpx.Response(200, json=mock_json("google_cse_image.json"))
        if host == "api.openverse.org":
            return httpx.Response(200, json=mock_json("openverse.json"))
        if host == "www.reddit.com" and path == "/api/v1/access_token":
            return httpx.Response(200, json=mock_json("reddit_token.json"))
        if host == "oauth.reddit.com":
            return httpx.Response(200, json=mock_json("reddit_search.json"))
        if host == "api.tineye.com":
            return httpx.Response(200, json=mock_json("tineye.json"))
        if host == "commons.wikimedia.org":
            return httpx.Response(200, json={"query": {"pages": {}}})
        return httpx.Response(404, json={"error": "unmocked"})


@pytest.fixture
def transport() -> MockProviderTransport:
    return MockProviderTransport()


@pytest.fixture
def settings(tmp_path) -> Settings:
    url = os.environ.get("TEST_DATABASE_URL", f"sqlite+aiosqlite:///{tmp_path}/test.db")
    return Settings(
        _env_file=None, environment="test", database_url=url, temp_dir=tmp_path / "uploads",
        auth_dev_tokens=True, provider_mock_mode=False, google_cse_api_key="g-key", google_cse_cx="g-cx",
        reddit_client_id="r-id", reddit_client_secret="r-secret", tineye_api_key="t-key",
        openverse_enabled=True, wikimedia_enabled=True, provider_timeout_seconds=5, provider_max_retries=1,
        rate_limit_per_minute=1000, search_rate_limit_per_hour=1000, embedding_backend="opencv", ocr_enabled=False,
    )


@pytest_asyncio.fixture
async def app(settings, transport, monkeypatch):
    async def _public(url: str) -> None:
        return None

    monkeypatch.setattr(safe_http, "assert_public_url", _public)
    init_engine(settings.database_url)
    async with get_engine().begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)
        await conn.run_sync(Base.metadata.create_all)
    application = create_app(settings, embedder=OpenCvDescriptorEmbedder(),
                             http_client_factory=lambda: httpx.AsyncClient(transport=httpx.MockTransport(transport)),
                             init_db=False)
    application.dependency_overrides[get_settings] = lambda: settings
    application.dependency_overrides[get_token_verifier] = lambda: TokenVerifier(settings)
    limiter.reset()
    yield application
    await application.state.runner.drain()
    await dispose_engine()


@pytest_asyncio.fixture
async def client(app):
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as c:
        yield c


async def wait_done(app, client, search_id: str, headers=GUEST) -> dict:
    await app.state.runner.drain()
    r = await client.get(f"/api/v1/search/{search_id}", headers=headers)
    assert r.status_code == 200, r.text
    return r.json()
