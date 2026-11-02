"""Social-media providers.

None of these platforms offers a public *reverse image* search API. Each provider uses
the official API that is available (keyword/hashtag search over public content), and
falls back to the authorised public-index alternative - Google Programmable Search
restricted to the platform's domain - when the native API is not configured. Results
found through that index are attributed with ``via`` so the user knows the source.

Private accounts, login walls and CAPTCHAs are never bypassed; only content the
platform returns to an authorised API client is shown."""
from __future__ import annotations

import time

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
from app.providers.web import GoogleImageSearchProvider


class _SocialProvider(ImageSearchProvider):
    category = ProviderCategory.SOCIAL
    site_domain: str = ""

    def native_configured(self) -> bool:
        return all(getattr(self.settings, env.lower(), "") for env in self.credential_env)

    def site_search_configured(self) -> bool:
        return bool(self.site_domain and self.settings.google_cse_api_key and self.settings.google_cse_cx)

    def is_configured(self) -> bool:
        return self.native_configured() or self.site_search_configured()

    def describe(self) -> dict:
        d = super().describe()
        d["native_api_configured"] = self.native_configured()
        d["site_search_fallback"] = self.site_search_configured() and not self.native_configured()
        return d

    async def native_search(self, query: ProviderQuery, client: httpx.AsyncClient) -> list[ProviderHit]:
        raise ProviderNotConfigured("Native API not available.")

    async def site_search(self, query: ProviderQuery, client: httpx.AsyncClient) -> list[ProviderHit]:
        google = GoogleImageSearchProvider(self.settings)
        terms = query.search_terms()
        if not terms:
            return []
        items = await google.cse(client, terms, image=True, site=self.site_domain, num=query.max_results)
        hits = []
        for rank, it in enumerate(items):
            page = (it.get("image") or {}).get("contextLink") or it.get("link")
            platform, category = platform_for(page)
            hits.append(ProviderHit(
                source_url=page, platform=self.platform, category="social", image_url=it.get("link"),
                thumbnail_url=(it.get("image") or {}).get("thumbnailLink"), page_title=clip(it.get("title"), 300),
                description=clip(it.get("snippet")), relevance_rank=rank,
                via=f"Google Programmable Search ({self.site_domain})"))
        return hits

    async def search(self, query: ProviderQuery, client: httpx.AsyncClient) -> list[ProviderHit]:
        if self.native_configured():
            return await self.native_search(query, client)
        return await self.site_search(query, client)


class RedditProvider(_SocialProvider):
    key = "reddit"
    name = "Reddit"
    platform = "Reddit"
    site_domain = "reddit.com"
    credential_env = ("REDDIT_CLIENT_ID", "REDDIT_CLIENT_SECRET")
    homepage = "https://www.reddit.com/dev/api/"
    limitations = "Reddit's API searches post text/titles, not images; image posts are visually re-ranked."
    alternatives = "Google Programmable Search restricted to reddit.com."
    _token: tuple[str, float] | None = None

    async def _access_token(self, client: httpx.AsyncClient) -> str:
        cached = type(self)._token
        if cached and cached[1] > time.time() + 30:
            return cached[0]
        resp = await client.post("https://www.reddit.com/api/v1/access_token",
                                 auth=(self.settings.reddit_client_id, self.settings.reddit_client_secret),
                                 data={"grant_type": "client_credentials"},
                                 headers={"User-Agent": self.settings.reddit_user_agent})
        self.raise_for_status(resp)
        data = resp.json()
        type(self)._token = (data["access_token"], time.time() + float(data.get("expires_in", 3600)))
        return data["access_token"]

    async def native_search(self, query: ProviderQuery, client: httpx.AsyncClient) -> list[ProviderHit]:
        terms = query.search_terms(4)
        if not terms:
            return []
        token = await self._access_token(client)
        resp = await client.get("https://oauth.reddit.com/search", params={
            "q": terms, "type": "link", "limit": 50, "sort": "relevance", "include_over_18": "false"},
            headers={"Authorization": f"Bearer {token}", "User-Agent": self.settings.reddit_user_agent})
        self.raise_for_status(resp)
        hits = []
        for rank, child in enumerate((resp.json().get("data") or {}).get("children", [])):
            p = child.get("data") or {}
            image = None
            if p.get("post_hint") == "image" or str(p.get("url", "")).lower().endswith((".jpg", ".jpeg", ".png", ".webp")):
                image = p.get("url")
            preview = (((p.get("preview") or {}).get("images") or [{}])[0].get("source") or {}).get("url")
            thumb = (preview or "").replace("&amp;", "&") or None
            if not (image or thumb):
                continue
            author = p.get("author")
            hits.append(ProviderHit(
                source_url="https://www.reddit.com" + p.get("permalink", ""), platform="Reddit", category="social",
                image_url=image or thumb, thumbnail_url=thumb or image, page_title=clip(p.get("title"), 300),
                caption=clip(p.get("title"), 300), description=clip(f"r/{p.get('subreddit')}"),
                published_at=parse_date(p.get("created_utc")),
                author_username=author if author and author != "[deleted]" else None,
                profile_url=f"https://www.reddit.com/user/{author}" if author and author != "[deleted]" else None,
                relevance_rank=rank))
        return hits


class XProvider(_SocialProvider):
    key = "x"
    name = "X (Twitter)"
    platform = "X (Twitter)"
    site_domain = "x.com"
    credential_env = ("X_BEARER_TOKEN",)
    homepage = "https://developer.x.com/en/docs/x-api/tweets/search/api-reference/get-tweets-search-recent"
    limitations = "Recent search (last 7 days) on paid API tiers; text query with has:images, visually re-ranked."
    alternatives = "Google Programmable Search restricted to x.com."

    async def native_search(self, query: ProviderQuery, client: httpx.AsyncClient) -> list[ProviderHit]:
        terms = query.search_terms(3)
        if not terms:
            return []
        resp = await client.get("https://api.x.com/2/tweets/search/recent", params={
            "query": f"{terms} has:images -is:retweet -is:nullcast", "max_results": 25,
            "expansions": "attachments.media_keys,author_id", "media.fields": "url,preview_image_url,type",
            "user.fields": "username,name,description,url,protected", "tweet.fields": "created_at"},
            headers={"Authorization": f"Bearer {self.settings.x_bearer_token}"})
        self.raise_for_status(resp)
        data = resp.json()
        media = {m["media_key"]: m for m in (data.get("includes") or {}).get("media", [])}
        users = {u["id"]: u for u in (data.get("includes") or {}).get("users", [])}
        hits = []
        for rank, t in enumerate(data.get("data", [])):
            user = users.get(t.get("author_id"), {})
            if user.get("protected"):
                continue
            keys = (t.get("attachments") or {}).get("media_keys", [])
            img = next((media[k].get("url") or media[k].get("preview_image_url") for k in keys if k in media), None)
            if not img or not user.get("username"):
                continue
            hits.append(ProviderHit(
                source_url=f"https://x.com/{user['username']}/status/{t['id']}", platform="X (Twitter)",
                category="social", image_url=img, thumbnail_url=img, caption=clip(t.get("text")),
                page_title=clip(t.get("text"), 120), published_at=parse_date(t.get("created_at")),
                author_display_name=user.get("name"), author_username=user.get("username"),
                profile_url=f"https://x.com/{user['username']}", profile_bio=clip(user.get("description")),
                website_url=user.get("url"), relevance_rank=rank))
        return hits


class InstagramProvider(_SocialProvider):
    key = "instagram"
    name = "Instagram"
    platform = "Instagram"
    site_domain = "instagram.com"
    credential_env = ("META_GRAPH_ACCESS_TOKEN", "INSTAGRAM_BUSINESS_ACCOUNT_ID")
    homepage = "https://developers.facebook.com/docs/instagram-platform/instagram-graph-api/reference/ig-hashtag-search"
    limitations = ("No reverse image search API. Graph API hashtag search (business account, 30 hashtags / 7 days) "
                   "returns public posts only; usernames are not exposed by that endpoint.")
    alternatives = "Google Programmable Search restricted to instagram.com."

    async def native_search(self, query: ProviderQuery, client: httpx.AsyncClient) -> list[ProviderHit]:
        if not query.keywords:
            return []
        tag = "".join(ch for ch in query.keywords[0].lower() if ch.isalnum())[:50]
        if not tag:
            return []
        base = "https://graph.facebook.com/v21.0"
        token = self.settings.meta_graph_access_token
        uid = self.settings.instagram_business_account_id
        r = await client.get(f"{base}/ig_hashtag_search", params={"user_id": uid, "q": tag, "access_token": token})
        self.raise_for_status(r)
        ids = [d["id"] for d in r.json().get("data", [])]
        if not ids:
            return []
        r = await client.get(f"{base}/{ids[0]}/top_media", params={
            "user_id": uid, "access_token": token, "limit": 25,
            "fields": "id,caption,media_type,media_url,permalink,timestamp"})
        self.raise_for_status(r)
        hits = []
        for rank, m in enumerate(r.json().get("data", [])):
            if m.get("media_type") not in ("IMAGE", "CAROUSEL_ALBUM") or not m.get("permalink"):
                continue
            hits.append(ProviderHit(
                source_url=m["permalink"], platform="Instagram", category="social", image_url=m.get("media_url"),
                thumbnail_url=m.get("media_url"), caption=clip(m.get("caption")), page_title=f"#{tag} on Instagram",
                published_at=parse_date(m.get("timestamp")), relevance_rank=rank))
        return hits


class FacebookProvider(_SocialProvider):
    key = "facebook"
    name = "Facebook"
    platform = "Facebook"
    site_domain = "facebook.com"
    credential_env = ("GOOGLE_CSE_API_KEY", "GOOGLE_CSE_CX")  # only the public-index alternative exists
    homepage = "https://developers.facebook.com/docs/graph-api/"
    limitations = "Meta provides no public post or image search API. Only the public-index alternative is used."
    alternatives = "Google Programmable Search restricted to facebook.com (public pages only)."

    def native_configured(self) -> bool:
        return False


class TikTokProvider(_SocialProvider):
    key = "tiktok"
    name = "TikTok"
    platform = "TikTok"
    site_domain = "tiktok.com"
    credential_env = ("TIKTOK_RESEARCH_CLIENT_KEY", "TIKTOK_RESEARCH_CLIENT_SECRET")
    homepage = "https://developers.tiktok.com/products/research-api/"
    limitations = ("No reverse image search. The Research API is limited to approved non-commercial researchers "
                   "and returns metadata without thumbnails.")
    alternatives = "Google Programmable Search restricted to tiktok.com."
    _token: tuple[str, float] | None = None

    async def native_search(self, query: ProviderQuery, client: httpx.AsyncClient) -> list[ProviderHit]:
        terms = query.search_terms(2)
        if not terms:
            return []
        cached = type(self)._token
        if not cached or cached[1] < time.time() + 30:
            r = await client.post("https://open.tiktokapis.com/v2/oauth/token/", data={
                "client_key": self.settings.tiktok_research_client_key,
                "client_secret": self.settings.tiktok_research_client_secret, "grant_type": "client_credentials"})
            self.raise_for_status(r)
            d = r.json()
            if "access_token" not in d:
                raise ProviderError("TikTok token request was rejected.")
            type(self)._token = cached = (d["access_token"], time.time() + float(d.get("expires_in", 7200)))
        today = time.strftime("%Y%m%d")
        start = time.strftime("%Y%m%d", time.gmtime(time.time() - 29 * 86400))
        r = await client.post(
            "https://open.tiktokapis.com/v2/research/video/query/",
            params={"fields": "id,video_description,create_time,username"},
            headers={"Authorization": f"Bearer {cached[0]}"},
            json={"query": {"and": [{"operation": "IN", "field_name": "keyword", "field_values": terms.split()[:3]}]},
                  "start_date": start, "end_date": today, "max_count": 20})
        self.raise_for_status(r)
        hits = []
        for rank, v in enumerate((r.json().get("data") or {}).get("videos", [])):
            user = v.get("username")
            if not user:
                continue
            hits.append(ProviderHit(
                source_url=f"https://www.tiktok.com/@{user}/video/{v['id']}", platform="TikTok", category="social",
                caption=clip(v.get("video_description")), page_title=clip(v.get("video_description"), 120),
                published_at=parse_date(v.get("create_time")), author_username=user,
                profile_url=f"https://www.tiktok.com/@{user}", relevance_rank=rank))
        return hits


class PinterestProvider(_SocialProvider):
    key = "pinterest"
    name = "Pinterest"
    platform = "Pinterest"
    site_domain = "pinterest.com"
    credential_env = ("PINTEREST_ACCESS_TOKEN",)
    homepage = "https://developers.pinterest.com/docs/api/v5/search_pins-list"
    limitations = ("Pinterest API v5 has no public visual search; with a token it searches only the authorised "
                   "account's own pins.")
    alternatives = "Google Programmable Search restricted to pinterest.com."

    async def native_search(self, query: ProviderQuery, client: httpx.AsyncClient) -> list[ProviderHit]:
        terms = query.search_terms(3)
        if not terms:
            return []
        r = await client.get("https://api.pinterest.com/v5/search/pins", params={"query": terms},
                             headers={"Authorization": f"Bearer {self.settings.pinterest_access_token}"})
        self.raise_for_status(r)
        hits = []
        for rank, pin in enumerate(r.json().get("items", [])):
            images = (pin.get("media") or {}).get("images") or {}
            img = (images.get("600x") or images.get("400x300") or next(iter(images.values()), {})).get("url")
            hits.append(ProviderHit(
                source_url=f"https://www.pinterest.com/pin/{pin['id']}/", platform="Pinterest", category="social",
                image_url=img, thumbnail_url=img, page_title=clip(pin.get("title"), 300),
                description=clip(pin.get("description")), website_url=pin.get("link"),
                published_at=parse_date(pin.get("created_at")), relevance_rank=rank))
        return hits


class PublicWebForumsProvider(_SocialProvider):
    """Public websites, blogs and forums via Google Programmable Search (web results)."""

    key = "public_web"
    name = "Public websites & forums"
    platform = "Web"
    category = ProviderCategory.WEB
    site_domain = ""
    credential_env = ("GOOGLE_CSE_API_KEY", "GOOGLE_CSE_CX")
    homepage = "https://developers.google.com/custom-search/v1/overview"
    limitations = "Page search by derived keywords; pages without a preview image are skipped."

    def native_configured(self) -> bool:
        return bool(self.settings.google_cse_api_key and self.settings.google_cse_cx)

    async def native_search(self, query: ProviderQuery, client: httpx.AsyncClient) -> list[ProviderHit]:
        terms = query.search_terms()
        if not terms:
            return []
        google = GoogleImageSearchProvider(self.settings)
        items = await google.cse(client, f"{terms} (forum OR blog OR discussion)", image=False, num=10)
        hits = []
        for rank, it in enumerate(items):
            pagemap = it.get("pagemap") or {}
            img = ((pagemap.get("cse_image") or [{}])[0]).get("src")
            thumb = ((pagemap.get("cse_thumbnail") or [{}])[0]).get("src")
            if not (img or thumb):
                continue
            meta = (pagemap.get("metatags") or [{}])[0]
            platform, category = platform_for(it.get("link", ""))
            hits.append(ProviderHit(
                source_url=it["link"], platform=platform, category=category, image_url=img or thumb,
                thumbnail_url=thumb or img, page_title=clip(it.get("title"), 300), description=clip(it.get("snippet")),
                published_at=parse_date(meta.get("article:published_time")),
                author_display_name=clip(meta.get("author"), 200), relevance_rank=rank, via="Google Programmable Search"))
        return hits
