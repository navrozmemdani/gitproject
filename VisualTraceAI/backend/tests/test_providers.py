import asyncio

import httpx
import pytest

from app.core.config import Settings
from app.providers.base import ProviderQuery, ProviderStatus, run_provider
from app.providers.registry import ProviderRegistry
from app.providers.social.platforms import FacebookProvider, InstagramProvider, RedditProvider
from app.providers.web import GoogleImageSearchProvider, TinEyeProvider
from tests.conftest import MockProviderTransport, fixture_bytes

Q = ProviderQuery(image_jpeg=fixture_bytes("sample_car.jpg"), keywords=["red sports car"], max_results=20)


def settings(**kw):
    base = dict(_env_file=None, provider_timeout_seconds=2, provider_max_retries=1)
    base.update(kw)
    return Settings(**base)


def client(handler):
    return httpx.AsyncClient(transport=httpx.MockTransport(handler))


async def test_unconfigured_provider_reports_not_configured():
    s = settings()
    async with client(MockProviderTransport()) as c:
        out = await run_provider(GoogleImageSearchProvider(s), Q, c, s)
    assert out.status == ProviderStatus.NOT_CONFIGURED and out.hits == []
    assert "GOOGLE_CSE_API_KEY" in out.message


async def test_mock_mode_results_are_flagged():
    s = settings(provider_mock_mode=True)
    async with client(MockProviderTransport()) as c:
        out = await run_provider(GoogleImageSearchProvider(s), Q, c, s)
    assert out.status == ProviderStatus.MOCK
    assert out.hits and all(h.is_mock and h.page_title.startswith("[MOCK]") for h in out.hits)
    assert all(".invalid/" in h.source_url for h in out.hits)


async def test_upload_provider_requires_consent():
    s = settings(tineye_api_key="k")
    t = MockProviderTransport()
    async with client(t) as c:
        out = await run_provider(TinEyeProvider(s), Q, c, s)
        assert out.status == ProviderStatus.SKIPPED_NO_CONSENT and not t.calls
        consented = ProviderQuery(**{**Q.__dict__, "third_party_consent": True})
        out = await run_provider(TinEyeProvider(s), consented, c, s)
    assert out.status == ProviderStatus.OK and out.hits[0].provider_match_type == "exact"


async def test_rate_limit_and_server_errors_are_reported():
    s = settings(google_cse_api_key="k", google_cse_cx="cx")
    t = MockProviderTransport()
    t.fail["customsearch.googleapis.com"] = 429
    async with client(t) as c:
        out = await run_provider(GoogleImageSearchProvider(s), Q, c, s)
    assert out.status == ProviderStatus.RATE_LIMITED
    t.fail["customsearch.googleapis.com"] = 503
    async with client(t) as c:
        out = await run_provider(GoogleImageSearchProvider(s), Q, c, s)
    assert out.status == ProviderStatus.ERROR and out.attempts == 2  # retried once
    t.fail["customsearch.googleapis.com"] = 401
    async with client(t) as c:
        out = await run_provider(GoogleImageSearchProvider(s), Q, c, s)
    assert out.status == ProviderStatus.ERROR and "credentials" in out.message and out.attempts == 1


async def test_timeout_is_reported():
    s = settings(google_cse_api_key="k", google_cse_cx="cx", provider_timeout_seconds=0.05, provider_max_retries=0)

    async def slow(request):
        await asyncio.sleep(1)
        return httpx.Response(200, json={})

    async with client(slow) as c:
        out = await run_provider(GoogleImageSearchProvider(s), Q, c, s)
    assert out.status == ProviderStatus.TIMEOUT


async def test_network_error_is_reported():
    s = settings(google_cse_api_key="k", google_cse_cx="cx", provider_max_retries=0)

    def boom(request):
        raise httpx.ConnectError("down")

    async with client(boom) as c:
        out = await run_provider(GoogleImageSearchProvider(s), Q, c, s)
    assert out.status == ProviderStatus.ERROR and "Network" in out.message


async def test_reddit_parsing_skips_text_posts_and_deleted_authors():
    s = settings(reddit_client_id="a", reddit_client_secret="b")
    async with client(MockProviderTransport()) as c:
        out = await run_provider(RedditProvider(s), Q, c, s)
    assert out.status == ProviderStatus.OK and len(out.hits) == 1
    h = out.hits[0]
    assert h.author_username == "car_spotter" and h.profile_url.endswith("/user/car_spotter")
    assert h.thumbnail_url == "https://preview.redd.test/red.jpg?width=640&s=1"
    assert h.published_at.year == 2026


async def test_social_site_search_fallback_via_google():
    s = settings(google_cse_api_key="k", google_cse_cx="cx")
    t = MockProviderTransport()
    async with client(t) as c:
        out = await run_provider(InstagramProvider(s), Q, c, s)
    assert out.status == ProviderStatus.OK
    assert all(h.via.startswith("Google Programmable Search (instagram.com)") for h in out.hits)
    assert InstagramProvider(s).describe()["site_search_fallback"] is True


def test_facebook_never_claims_native_api():
    d = FacebookProvider(settings(google_cse_api_key="k", google_cse_cx="cx")).describe()
    assert d["native_api_configured"] is False and "no public" in d["limitations"]
    assert FacebookProvider(settings()).describe()["mode"] == "unavailable"


def test_registry_selection():
    reg = ProviderRegistry(settings())
    assert {"google_cse", "instagram", "facebook", "tiktok", "x", "reddit", "pinterest", "public_web",
            "bing_visual", "tineye", "serpapi_lens", "openverse", "wikimedia", "local_index"} <= set(reg.providers)
    assert [p.key for p in reg.select(["reddit"], ["x"])] == ["reddit"]
    assert [p.key for p in reg.select(None, ["x"])] == ["x"]
    assert "bing_visual" not in [p.key for p in reg.select(None, None)]


@pytest.mark.parametrize("terms,expected", [(["a", "b"], "a b"), ([], "")])
def test_search_terms(terms, expected):
    assert ProviderQuery(image_jpeg=None, keywords=terms).search_terms() == expected
