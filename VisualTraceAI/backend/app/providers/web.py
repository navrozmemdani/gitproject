"""Reverse-image and web image providers."""
from __future__ import annotations

import re

import httpx

from app.providers.base import (
    ImageSearchProvider,
    ProviderCategory,
    ProviderError,
    ProviderHit,
    ProviderNotConfigured,
    ProviderQuery,
)
from app.providers.util import clip, parse_date, platform_for


class GoogleImageSearchProvider(ImageSearchProvider):
    """Google Programmable Search Engine - Custom Search JSON API (``searchType=image``).

    Google offers no public API for uploading an image for reverse search, so this
    provider queries the image index with keywords derived from the image analysis /
    OCR / user text and the backend re-ranks candidates by *visual* similarity."""

    key = "google_cse"
    name = "Google Programmable Search"
    platform = "Google"
    category = ProviderCategory.WEB
    credential_env = ("GOOGLE_CSE_API_KEY", "GOOGLE_CSE_CX")
    homepage = "https://developers.google.com/custom-search/v1/overview"
    limitations = ("Keyword-based image index search; candidates are re-ranked by visual similarity. "
                   "Free tier: 100 queries/day.")
    endpoint = "https://customsearch.googleapis.com/customsearch/v1"

    async def cse(self, client: httpx.AsyncClient, q: str, *, image: bool, site: str | None = None,
                  num: int = 10) -> list[dict]:
        params = {"key": self.settings.google_cse_api_key, "cx": self.settings.google_cse_cx, "q": q,
                  "num": min(10, num), "safe": "active"}
        if image:
            params["searchType"] = "image"
        if site:
            params["siteSearch"] = site
            params["siteSearchFilter"] = "i"
        resp = await client.get(self.endpoint, params=params)
        self.raise_for_status(resp)
        return resp.json().get("items", []) or []

    async def search(self, query: ProviderQuery, client: httpx.AsyncClient) -> list[ProviderHit]:
        terms = query.search_terms()
        if not terms:
            return []
        items = await self.cse(client, terms, image=True, num=query.max_results)
        hits = []
        for rank, it in enumerate(items):
            page = (it.get("image") or {}).get("contextLink") or it.get("link")
            platform, category = platform_for(page)
            hits.append(ProviderHit(
                source_url=page, platform=platform, category=category, image_url=it.get("link"),
                thumbnail_url=(it.get("image") or {}).get("thumbnailLink"), page_title=clip(it.get("title"), 300),
                description=clip(it.get("snippet")), relevance_rank=rank, via=self.name))
        return hits


class BingImageSearchProvider(ImageSearchProvider):
    """Bing Visual Search (upload-based reverse image search).

    NOTE: Microsoft retired the public Bing Search APIs on 2025-08-11. This provider
    remains for organisations that hold access to a compatible Visual Search endpoint
    (set BING_VISUAL_SEARCH_ENDPOINT); otherwise it reports 'not configured'."""

    key = "bing_visual"
    name = "Bing Visual Search"
    platform = "Bing"
    category = ProviderCategory.REVERSE_IMAGE
    supports_reverse_image = True
    uploads_media = True
    credential_env = ("BING_VISUAL_SEARCH_KEY", "BING_VISUAL_SEARCH_ENDPOINT")
    homepage = "https://learn.microsoft.com/bing/search-apis/"
    limitations = "Public Bing Search APIs were retired by Microsoft in August 2025; requires an eligible endpoint."
    enabled_by_default = False

    async def search(self, query: ProviderQuery, client: httpx.AsyncClient) -> list[ProviderHit]:
        if not query.image_jpeg:
            return []
        resp = await client.post(
            self.settings.bing_visual_search_endpoint,
            headers={"Ocp-Apim-Subscription-Key": self.settings.bing_visual_search_key},
            files={"image": ("query.jpg", query.image_jpeg, "image/jpeg")},
        )
        self.raise_for_status(resp)
        hits: list[ProviderHit] = []
        for tag in resp.json().get("tags", []):
            for action in tag.get("actions", []):
                if action.get("actionType") not in ("VisualSearch", "PagesIncluding"):
                    continue
                match = "exact" if action["actionType"] == "PagesIncluding" else "visual_match"
                for rank, v in enumerate((action.get("data") or {}).get("value", [])):
                    page = v.get("hostPageUrl")
                    if not page:
                        continue
                    platform, category = platform_for(page)
                    hits.append(ProviderHit(
                        source_url=page, platform=platform, category=category, image_url=v.get("contentUrl"),
                        thumbnail_url=v.get("thumbnailUrl"), page_title=clip(v.get("name"), 300),
                        published_at=parse_date(v.get("datePublished")), provider_match_type=match,
                        relevance_rank=rank))
        return hits


class SerpApiLensProvider(ImageSearchProvider):
    """Licensed reverse image search through SerpApi's Google Lens engine.

    SerpApi fetches the query image from a short-lived signed URL served by this
    backend (PUBLIC_BASE_URL must be reachable over HTTPS)."""

    key = "serpapi_lens"
    name = "Google Lens via SerpApi"
    platform = "Google Lens"
    category = ProviderCategory.REVERSE_IMAGE
    supports_reverse_image = True
    uploads_media = True
    credential_env = ("SERPAPI_API_KEY", "PUBLIC_BASE_URL")
    homepage = "https://serpapi.com/google-lens-api"
    limitations = "Paid API; the image is made available to SerpApi through a 5-minute signed link."

    async def search(self, query: ProviderQuery, client: httpx.AsyncClient) -> list[ProviderHit]:
        if not query.public_image_url:
            raise ProviderNotConfigured("No public image URL available for URL-based reverse search.")
        resp = await client.get("https://serpapi.com/search.json", params={
            "engine": "google_lens", "url": query.public_image_url, "api_key": self.settings.serpapi_api_key})
        self.raise_for_status(resp)
        data = resp.json()
        if data.get("error"):
            raise ProviderError(str(data["error"])[:200])
        hits = []
        for rank, m in enumerate(data.get("visual_matches", [])):
            page = m.get("link")
            if not page:
                continue
            platform, category = platform_for(page)
            hits.append(ProviderHit(
                source_url=page, platform=m.get("source") or platform, category=category,
                image_url=m.get("image"), thumbnail_url=m.get("thumbnail"), page_title=clip(m.get("title"), 300),
                published_at=parse_date(m.get("date")), provider_match_type="visual_match", relevance_rank=rank))
        return hits


class TinEyeProvider(ImageSearchProvider):
    """TinEye commercial API - exact / modified-copy reverse image search by upload."""

    key = "tineye"
    name = "TinEye API"
    platform = "TinEye"
    category = ProviderCategory.REVERSE_IMAGE
    supports_reverse_image = True
    uploads_media = True
    credential_env = ("TINEYE_API_KEY",)
    homepage = "https://services.tineye.com/TinEyeAPI"
    limitations = "Paid API (search bundles). Finds copies and edited versions, not merely similar images."

    async def search(self, query: ProviderQuery, client: httpx.AsyncClient) -> list[ProviderHit]:
        if not query.image_jpeg:
            return []
        resp = await client.post(
            self.settings.tineye_api_url.rstrip("/") + "/search/",
            headers={"x-api-key": self.settings.tineye_api_key},
            data={"limit": str(query.max_results), "sort": "score", "order": "desc"},
            files={"image_upload": ("query.jpg", query.image_jpeg, "image/jpeg")},
        )
        self.raise_for_status(resp)
        hits = []
        for rank, match in enumerate((resp.json().get("results") or {}).get("matches", [])):
            for backlink in match.get("backlinks", [])[:2]:
                page = backlink.get("backlink") or match.get("domain")
                if not page:
                    continue
                platform, category = platform_for(page)
                hits.append(ProviderHit(
                    source_url=page, platform=platform, category=category, image_url=backlink.get("url"),
                    thumbnail_url=match.get("image_url"), page_title=clip(match.get("domain"), 300),
                    published_at=parse_date(backlink.get("crawl_date")), provider_match_type="exact",
                    provider_score=match.get("score"), relevance_rank=rank))
        return hits


class OpenverseProvider(ImageSearchProvider):
    """Openverse (WordPress Foundation) - openly licensed images; anonymous access allowed."""

    key = "openverse"
    name = "Openverse"
    platform = "Openverse"
    category = ProviderCategory.INDEX
    requires_credentials = False
    credential_env = ()
    homepage = "https://api.openverse.org/v1/"
    limitations = "Indexes openly licensed (Creative Commons) images only; keyword-based, visually re-ranked."

    def is_configured(self) -> bool:
        return self.settings.openverse_enabled

    async def search(self, query: ProviderQuery, client: httpx.AsyncClient) -> list[ProviderHit]:
        terms = query.search_terms(3)
        if not terms:
            return []
        resp = await client.get("https://api.openverse.org/v1/images/",
                                params={"q": terms, "page_size": min(20, query.max_results), "mature": "false"})
        self.raise_for_status(resp)
        hits = []
        for rank, r in enumerate(resp.json().get("results", [])):
            page = r.get("foreign_landing_url") or r.get("url")
            if not page:
                continue
            platform, category = platform_for(page)
            hits.append(ProviderHit(
                source_url=page, platform=r.get("source") or platform, category=category, image_url=r.get("url"),
                thumbnail_url=r.get("thumbnail"), page_title=clip(r.get("title"), 300),
                author_display_name=clip(r.get("creator"), 200), profile_url=r.get("creator_url"),
                description=clip(f"License: {r.get('license', '').upper()} {r.get('license_version') or ''}".strip()),
                relevance_rank=rank, via=self.name))
        return hits


class WikimediaCommonsProvider(ImageSearchProvider):
    """Wikimedia Commons MediaWiki API - public, free, no key."""

    key = "wikimedia"
    name = "Wikimedia Commons"
    platform = "Wikimedia Commons"
    category = ProviderCategory.INDEX
    requires_credentials = False
    credential_env = ()
    homepage = "https://commons.wikimedia.org/w/api.php"
    limitations = "Freely licensed media only; keyword-based, visually re-ranked."

    def is_configured(self) -> bool:
        return self.settings.wikimedia_enabled

    async def search(self, query: ProviderQuery, client: httpx.AsyncClient) -> list[ProviderHit]:
        terms = query.search_terms(3)
        if not terms:
            return []
        resp = await client.get("https://commons.wikimedia.org/w/api.php", params={
            "action": "query", "format": "json", "generator": "search", "gsrnamespace": "6",
            "gsrsearch": f"filetype:bitmap {terms}", "gsrlimit": min(20, query.max_results),
            "prop": "imageinfo", "iiprop": "url|extmetadata|timestamp", "iiurlwidth": "400"})
        self.raise_for_status(resp)
        pages = (resp.json().get("query") or {}).get("pages", {})
        hits = []
        for page in sorted(pages.values(), key=lambda p: p.get("index", 0)):
            info = (page.get("imageinfo") or [{}])[0]
            meta = info.get("extmetadata") or {}
            desc = re.sub(r"<[^>]+>", "", (meta.get("ImageDescription") or {}).get("value", ""))
            artist = re.sub(r"<[^>]+>", "", (meta.get("Artist") or {}).get("value", ""))
            hits.append(ProviderHit(
                source_url=info.get("descriptionurl") or f"https://commons.wikimedia.org/?curid={page.get('pageid')}",
                platform="Wikimedia Commons", category="website", image_url=info.get("url"),
                thumbnail_url=info.get("thumburl"), page_title=clip(page.get("title"), 300), description=clip(desc),
                author_display_name=clip(artist, 200),
                published_at=parse_date((meta.get("DateTimeOriginal") or {}).get("value")) or parse_date(info.get("timestamp")),
                relevance_rank=page.get("index", 0), via=self.name))
        return hits
