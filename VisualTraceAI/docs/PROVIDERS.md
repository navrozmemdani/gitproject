# Search providers – configuration and honest limitations

Every provider is an independent class implementing `ImageSearchProvider`
(`backend/app/providers/`). A provider without credentials reports `not_configured`
and returns nothing – it never fabricates results. The **only** exception is
`PROVIDER_MOCK_MODE=true` (development), where unconfigured providers return synthetic
results flagged `is_mock=true`, titled `[MOCK] …`, linking to `mock.visualtrace.invalid`,
and shown with a MOCK badge and banner in the app. Production startup refuses mock mode.

The results screen lists every provider with its status, so users always see which
providers were actually searched.

## Reverse-image providers (send the image – require user consent)

| key | Class | Credentials | Notes |
|---|---|---|---|
| `serpapi_lens` | `SerpApiLensProvider` | `SERPAPI_API_KEY`, `PUBLIC_BASE_URL` | Google Lens visual matches via SerpApi (paid). SerpApi fetches the image from a 5-minute signed URL on your backend, so `PUBLIC_BASE_URL` must be publicly reachable over HTTPS. Get a key at https://serpapi.com. |
| `tineye` | `TinEyeProvider` | `TINEYE_API_KEY` | TinEye commercial API (search bundles). Finds exact and modified copies. https://services.tineye.com/TinEyeAPI |
| `bing_visual` | `BingImageSearchProvider` | `BING_VISUAL_SEARCH_KEY`, `BING_VISUAL_SEARCH_ENDPOINT` | Microsoft **retired the public Bing Search APIs on 11 Aug 2025**. Kept for organisations with access to a compatible Visual Search endpoint; disabled by default. |

## Web / index providers (keywords only)

| key | Class | Credentials | Notes |
|---|---|---|---|
| `google_cse` | `GoogleImageSearchProvider` | `GOOGLE_CSE_API_KEY`, `GOOGLE_CSE_CX` | Custom Search JSON API with `searchType=image`. Google has no public upload-based reverse image API, so keywords from the analysis / OCR / user description are used and candidates are visually re-ranked. Create an engine at https://programmablesearchengine.google.com (enable "Search the entire web" and "Image search"), then an API key in Google Cloud. Note Google's announced changes to the Custom Search JSON API for new customers – check current eligibility. |
| `public_web` | `PublicWebForumsProvider` | same as Google | Public websites, blogs and forums (web results with preview images). |
| `openverse` | `OpenverseProvider` | none (`OPENVERSE_ENABLED`) | Openly licensed images, anonymous API with rate limits. |
| `wikimedia` | `WikimediaCommonsProvider` | none (`WIKIMEDIA_ENABLED`) | Wikimedia Commons MediaWiki API. |
| `local_index` | `LocalIndexProvider` | none | VisualTrace's own index of *public* result images previously returned by providers (perceptual-hash proximity). User uploads are never indexed. |

## Social platforms

No social platform offers a public reverse-image API. Each module uses the official API
that exists; when that is not configured it falls back to the authorised public-index
alternative (Google Programmable Search restricted to the platform domain). Fallback
results carry `via = "Google Programmable Search (<domain>)"`. Private accounts, login
walls and CAPTCHAs are never bypassed.

| key | Native API used | Credentials | Limitations |
|---|---|---|---|
| `instagram` | Instagram Graph API hashtag search (`ig_hashtag_search` → `top_media`) | `META_GRAPH_ACCESS_TOKEN`, `INSTAGRAM_BUSINESS_ACCOUNT_ID` (business account with Instagram Public Content Access) | 30 unique hashtags / 7 days; usernames are not returned by that endpoint. |
| `facebook` | none exists | (Google CSE for fallback) | Meta has no public post/image search API; only the site-restricted index is used. |
| `tiktok` | TikTok Research API `video/query` | `TIKTOK_RESEARCH_CLIENT_KEY`, `TIKTOK_RESEARCH_CLIENT_SECRET` | Approved non-commercial researchers only; no thumbnails, so results are not visually scored. |
| `x` | X API v2 recent search (`has:images`) | `X_BEARER_TOKEN` (Basic tier or above) | Last 7 days; protected accounts are skipped. |
| `reddit` | Reddit OAuth `search` | `REDDIT_CLIENT_ID`, `REDDIT_CLIENT_SECRET`, `REDDIT_USER_AGENT` | Text search over titles; image posts are visually re-ranked. Follow Reddit's Data API terms. |
| `pinterest` | Pinterest API v5 `search/pins` | `PINTEREST_ACCESS_TOKEN` | Searches only the authorised account's own pins (no public visual search). |

## Similarity scoring

For every candidate whose thumbnail can be fetched (SSRF-safe, ≤4 MB, limited per
search) the backend computes:

- embedding cosine (OpenCLIP ViT-B-32 when `requirements-ml.txt` is installed, else the
  OpenCV global descriptor – the model name is reported in every search),
- combined perceptual-hash distance (pHash/dHash/aHash) → `exact` when ≤ 4,
- ORB keypoints + RANSAC homography inliers → `cropped` for crops/partial copies.

`score = 0.45·embedding + 0.30·hash + 0.25·keypoints`. Candidates that cannot be fetched
keep `similarity_score = null` ("Similarity not measured") rather than a guessed value.
Scores describe visual resemblance only and are never presented as proof of identity,
ownership or origin. No face recognition is performed anywhere in the system.

## Adding a provider

1. Subclass `ImageSearchProvider` (or `_SocialProvider`), set `key`, `name`, `category`,
   `credential_env`, `supports_reverse_image`, `uploads_media`, `limitations`.
2. Implement `async def search(query, client) -> list[ProviderHit]` using `self.raise_for_status`
   so 429/5xx map to rate-limit/retry handling.
3. Register the class in `providers/registry.py` and add mock responses + tests under `backend/tests/`.
