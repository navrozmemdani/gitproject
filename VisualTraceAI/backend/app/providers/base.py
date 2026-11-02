"""Provider contract.

Every search source (reverse-image API, web index, social platform) is an independent
``ImageSearchProvider`` subclass. A provider must report honestly whether it is
configured; it never returns invented results. The only exception is development
*mock mode* (``PROVIDER_MOCK_MODE=true``) where unconfigured providers return results
explicitly flagged ``is_mock=True`` which the app labels as mock data.
"""
from __future__ import annotations

import asyncio
import base64
import hashlib
import io
import logging
import random
from dataclasses import dataclass, field
from datetime import datetime
from enum import StrEnum

import httpx

from app.core.config import Settings

log = logging.getLogger("visualtrace.providers")


class ProviderCategory(StrEnum):
    REVERSE_IMAGE = "reverse_image"
    WEB = "web"
    SOCIAL = "social"
    INDEX = "index"


class ProviderStatus(StrEnum):
    OK = "ok"
    NO_RESULTS = "no_results"
    NOT_CONFIGURED = "not_configured"
    UNSUPPORTED = "unsupported"
    SKIPPED_NO_CONSENT = "skipped_no_consent"
    SKIPPED_NO_QUERY = "skipped_no_query"
    DISABLED = "disabled"
    RATE_LIMITED = "rate_limited"
    TIMEOUT = "timeout"
    ERROR = "error"
    MOCK = "mock"


@dataclass
class ProviderQuery:
    image_jpeg: bytes | None
    keywords: list[str]
    text_query: str | None = None
    ocr_text: str | None = None
    public_image_url: str | None = None  # signed, short-lived URL for URL-based reverse search
    third_party_consent: bool = False
    frame_timestamp_ms: int | None = None
    max_results: int = 20
    phash_hex: str | None = None
    exclude_search_id: str | None = None

    def search_terms(self, limit: int = 5) -> str:
        parts: list[str] = []
        if self.text_query:
            parts.append(self.text_query.strip())
        parts.extend(self.keywords[:limit])
        seen: set[str] = set()
        uniq = [p for p in parts if p and not (p.lower() in seen or seen.add(p.lower()))]
        return " ".join(uniq)[:256]


@dataclass
class ProviderHit:
    source_url: str
    platform: str
    image_url: str | None = None
    thumbnail_url: str | None = None
    page_title: str | None = None
    description: str | None = None
    published_at: datetime | None = None
    author_display_name: str | None = None
    author_username: str | None = None
    profile_url: str | None = None
    profile_bio: str | None = None
    website_url: str | None = None
    caption: str | None = None
    provider_match_type: str | None = None  # e.g. provider says "exact" / "visual_match"
    provider_score: float | None = None
    relevance_rank: int = 0
    category: str | None = None  # social | website | news (derived when None)
    via: str | None = None  # e.g. "Google Programmable Search index"
    candidate_bytes: bytes | None = None  # pre-fetched image bytes (mock mode / upload APIs)
    is_mock: bool = False
    extra: dict = field(default_factory=dict)


@dataclass
class ProviderOutcome:
    key: str
    name: str
    status: ProviderStatus
    hits: list[ProviderHit] = field(default_factory=list)
    message: str | None = None
    duration_ms: int = 0
    attempts: int = 0

    def report(self) -> dict:
        return {"key": self.key, "name": self.name, "status": self.status.value, "message": self.message,
                "result_count": len(self.hits), "duration_ms": self.duration_ms, "attempts": self.attempts,
                "searched": self.status in (ProviderStatus.OK, ProviderStatus.NO_RESULTS),
                "is_mock": self.status == ProviderStatus.MOCK}


class ProviderError(Exception):
    def __init__(self, message: str, *, retryable: bool = False, rate_limited: bool = False,
                 retry_after: float | None = None):
        super().__init__(message)
        self.retryable = retryable
        self.rate_limited = rate_limited
        self.retry_after = retry_after


class ProviderNotConfigured(Exception):
    pass


class ImageSearchProvider:
    key: str = "base"
    name: str = "Base"
    platform: str = "Web"
    category: ProviderCategory = ProviderCategory.WEB
    supports_reverse_image: bool = False
    uploads_media: bool = False  # sends the query image to the third party (requires consent)
    requires_credentials: bool = True
    credential_env: tuple[str, ...] = ()
    homepage: str = ""
    limitations: str = ""
    alternatives: str = ""
    enabled_by_default: bool = True

    def __init__(self, settings: Settings):
        self.settings = settings

    # --- configuration --------------------------------------------------------------
    def is_configured(self) -> bool:
        return all(getattr(self.settings, env.lower(), "") for env in self.credential_env)

    def describe(self) -> dict:
        configured = self.is_configured()
        mode = "live" if configured else ("mock" if self.settings.provider_mock_mode else "unavailable")
        return {
            "key": self.key, "name": self.name, "platform": self.platform, "category": self.category.value,
            "supports_reverse_image": self.supports_reverse_image, "uploads_media": self.uploads_media,
            "requires_credentials": self.requires_credentials, "configured": configured, "mode": mode,
            "credential_env": list(self.credential_env), "limitations": self.limitations,
            "alternatives": self.alternatives, "enabled_by_default": self.enabled_by_default,
            "homepage": self.homepage,
        }

    # --- searching ------------------------------------------------------------------
    async def search(self, query: ProviderQuery, client: httpx.AsyncClient) -> list[ProviderHit]:
        raise NotImplementedError

    def mock_hits(self, query: ProviderQuery) -> list[ProviderHit]:
        """Development-only, clearly flagged synthetic results derived from the query image."""
        return build_mock_hits(self, query)

    # --- helpers --------------------------------------------------------------------
    @staticmethod
    def raise_for_status(resp: httpx.Response) -> None:
        if resp.status_code == 429:
            ra = resp.headers.get("retry-after")
            raise ProviderError("Provider rate limit reached", rate_limited=True,
                                retry_after=float(ra) if ra and ra.isdigit() else None)
        if resp.status_code in (401, 403):
            raise ProviderError(f"Provider rejected the credentials (HTTP {resp.status_code})")
        if resp.status_code >= 500:
            raise ProviderError(f"Provider unavailable (HTTP {resp.status_code})", retryable=True)
        if resp.status_code >= 400:
            raise ProviderError(f"Provider request failed (HTTP {resp.status_code})")


async def run_provider(provider: ImageSearchProvider, query: ProviderQuery, client: httpx.AsyncClient,
                       settings: Settings) -> ProviderOutcome:
    """Execute a provider with consent checks, timeout and bounded retries."""
    loop = asyncio.get_running_loop()
    start = loop.time()
    outcome = ProviderOutcome(key=provider.key, name=provider.name, status=ProviderStatus.ERROR)

    def done(status: ProviderStatus, message: str | None = None, hits: list[ProviderHit] | None = None):
        outcome.status, outcome.message, outcome.hits = status, message, hits or []
        outcome.duration_ms = int((loop.time() - start) * 1000)
        return outcome

    if not provider.is_configured():
        if settings.provider_mock_mode:
            hits = provider.mock_hits(query)
            return done(ProviderStatus.MOCK, "MOCK DATA - provider credentials not configured", hits)
        missing = ", ".join(provider.credential_env) or "configuration"
        msg = f"Not configured on the server (requires {missing})."
        if provider.limitations:
            msg += f" {provider.limitations}"
        return done(ProviderStatus.NOT_CONFIGURED, msg)
    if provider.uploads_media and not query.third_party_consent:
        return done(ProviderStatus.SKIPPED_NO_CONSENT,
                    "Skipped: sending your image to this provider requires your consent.")

    if not provider.supports_reverse_image and not query.search_terms():
        return done(ProviderStatus.SKIPPED_NO_QUERY,
                    "Skipped: this source searches by description and no keywords could be derived from the image. "
                    "Add a short description to include it.")

    attempts = settings.provider_max_retries + 1
    for attempt in range(1, attempts + 1):
        outcome.attempts = attempt
        try:
            hits = await asyncio.wait_for(provider.search(query, client), timeout=settings.provider_timeout_seconds)
            hits = hits[: query.max_results]
            return done(ProviderStatus.OK if hits else ProviderStatus.NO_RESULTS, None, hits)
        except ProviderNotConfigured as exc:
            return done(ProviderStatus.NOT_CONFIGURED, str(exc))
        except asyncio.TimeoutError:
            if attempt == attempts:
                return done(ProviderStatus.TIMEOUT, "The provider did not respond in time.")
        except ProviderError as exc:
            if exc.rate_limited:
                if attempt == attempts or (exc.retry_after or 0) > 5:
                    return done(ProviderStatus.RATE_LIMITED, "The provider's rate limit was reached. Try later.")
                await asyncio.sleep(exc.retry_after or 1.0)
                continue
            if not exc.retryable or attempt == attempts:
                return done(ProviderStatus.ERROR, str(exc))
        except httpx.HTTPError as exc:
            if attempt == attempts:
                return done(ProviderStatus.ERROR, f"Network error contacting provider ({type(exc).__name__}).")
        except Exception as exc:  # noqa: BLE001 - a broken provider must not break the search
            log.exception("provider %s crashed", provider.key)
            return done(ProviderStatus.ERROR, f"Provider error ({type(exc).__name__}).")
        await asyncio.sleep(min(4.0, 0.5 * 2 ** (attempt - 1)) + random.random() * 0.2)
    return done(ProviderStatus.ERROR, "Provider failed.")


# ---------------------------------------------------------------------------------------
# Mock mode helpers
# ---------------------------------------------------------------------------------------
def _variant(img_jpeg: bytes, kind: int) -> bytes:
    from PIL import Image, ImageEnhance, ImageOps

    img = Image.open(io.BytesIO(img_jpeg)).convert("RGB")
    w, h = img.size
    if kind == 0:  # resized copy
        img = img.resize((max(32, w // 2), max(32, h // 2)))
    elif kind == 1:  # cropped copy
        img = img.crop((w // 8, h // 8, w - w // 8, h - h // 8))
    elif kind == 2:  # colour-adjusted / mirrored copy
        img = ImageOps.mirror(ImageEnhance.Color(img).enhance(0.6))
    else:  # unrelated synthetic pattern
        rnd = random.Random(kind)
        img = Image.new("RGB", (256, 256), tuple(rnd.randint(0, 255) for _ in range(3)))
    img.thumbnail((360, 360))
    out = io.BytesIO()
    img.save(out, "JPEG", quality=70)
    return out.getvalue()


def build_mock_hits(provider: ImageSearchProvider, query: ProviderQuery) -> list[ProviderHit]:
    seed = hashlib.sha1(provider.key.encode()).hexdigest()[:8]
    hits = []
    labels = ["resized copy", "cropped copy", "colour-edited copy", "unrelated image"]
    for i in range(3):
        kind = (i + int(seed, 16)) % 4
        data = _variant(query.image_jpeg, kind) if query.image_jpeg else None
        thumb = "data:image/jpeg;base64," + base64.b64encode(data).decode() if data else None
        url = f"https://mock.visualtrace.invalid/{provider.key}/{seed}-{i}"
        hits.append(ProviderHit(
            source_url=url, platform=provider.platform, image_url=thumb, thumbnail_url=thumb,
            page_title=f"[MOCK] {provider.name} result {i + 1} ({labels[kind]})",
            description="Development mock result. Not a real search result.",
            author_username=f"mock_user_{i}" if provider.category == ProviderCategory.SOCIAL else None,
            relevance_rank=i, candidate_bytes=data, is_mock=True,
        ))
    return hits
