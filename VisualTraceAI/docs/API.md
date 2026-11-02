# VisualTrace AI – API reference

Base path: `/api/v1`. All responses are JSON. Errors use one envelope:

```json
{"error": {"code": "unsupported_media_type", "message": "Unsupported image format. Use JPG, JPEG, PNG or WEBP.", "retryable": false}}
```

Interactive OpenAPI docs are served at `/docs` when `ENVIRONMENT` is not `production`.

## Authentication

Every request except `/health` and `/public/temp/*` must carry an identity:

| Header | Meaning |
|---|---|
| `Authorization: Bearer <Firebase ID token>` | Signed-in user (email/password or Google). Verified with firebase-admin, revocation checked. |
| `X-Guest-Id: <uuid>` | Guest mode (device-scoped random UUID). Allowed when `ALLOW_GUEST=true`. |
| `X-API-Key: <client key>` | Required when `CLIENT_API_KEYS` is set. Identifies trusted client builds – it is **not** a provider secret. |

Development only: with `AUTH_DEV_TOKENS=true` (refused in production) `Authorization: Bearer dev:<name>` is accepted.

Rate limits: `RATE_LIMIT_PER_MINUTE` per identity on all endpoints, plus `SEARCH_RATE_LIMIT_PER_HOUR` on search creation. Exceeding returns `429` with `Retry-After`.

## Error codes

| HTTP | code | When |
|---|---|---|
| 401 | `authentication_failed`, `invalid_client_key` | Missing/invalid identity or client key |
| 403 | `account_required` | Guest calling an account-only endpoint (favorites, collections) |
| 404 | `not_found` | Unknown id or another user's resource |
| 413 | `file_too_large` | Upload over the limit |
| 415 | `unsupported_media_type`, `corrupt_image`, `corrupt_video`, `empty_file` | Bad format |
| 422 | `suspicious_file` | Extension/MIME/magic mismatch, polyglot payload, malware scan hit |
| 422 | `validation_error`, `missing_image`, `unknown_provider`, `unsafe_url`, `fetch_failed`, `too_many_frames` | Invalid input |
| 428 | `consent_required` | Full-video upload without `upload_consent=true` |
| 429 | `rate_limited` | Too many requests |
| 500 | `internal_error` | Unexpected failure (retryable) |
| 503 | `auth_not_configured`, `scan_unavailable` | Server-side dependency missing |

## Endpoints

### `POST /search/image` → 202
`multipart/form-data`

| field | type | notes |
|---|---|---|
| `file` | file | JPG/JPEG/PNG/WEBP, ≤ `MAX_IMAGE_BYTES` (15 MB). Either this or `image_url`. |
| `image_url` | string | Public http(s) URL; fetched server-side with SSRF protection (private IPs rejected, every redirect checked). |
| `providers` | csv | Provider keys; default = user preference or provider defaults. |
| `third_party_consent` | bool | Required for upload-based providers (TinEye, SerpApi Lens, Bing Visual). |
| `query_text` | string ≤300 | Optional description / characteristics to combine with the image. |
| `save_preview` | bool | Store a 160px thumbnail with the history entry (default true). |

Response:
```json
{"search_id": "uuid", "status": "queued", "status_url": ".../search/uuid", "results_url": ".../search/uuid/results"}
```

### `POST /search/video` → 202
Two modes:

1. **Frames (default, privacy-preserving)** – the app extracts representative frames on the device:
   `frames` (repeated JPEG files, ≤ `MAX_FRAMES_PER_VIDEO`), `frame_timestamps_ms` (csv, one per frame).
2. **Full video** – `video` (MP4/MOV/WEBM ≤ `MAX_VIDEO_BYTES`) **and** `upload_consent=true`. The server extracts frames with OpenCV/FFmpeg and deletes the video immediately.

Other fields: `frame_interval_seconds` (0.5–60, default 3), `max_duration_seconds` (5–600), `duration_ms`, `original_filename`, `query_text`, `providers`, `third_party_consent`, `save_preview`.

### `POST /search/text` → 202
`{"query": "red sports car", "providers": ["google_cse"]}` – search by characteristics only.

### `GET /search/{search_id}`
Search detail: `status` (`queued|running|completed|partial|failed`), `job_status` (`stage`, `progress` 0–1), `media_metadata`, `analysis`, `perceptual_hashes`, `provider_reports[]`, `result_count`, `disclaimer`.

`provider_reports[].status` is one of: `ok`, `no_results`, `not_configured`, `unsupported`, `skipped_no_consent`, `skipped_no_query`, `disabled`, `rate_limited`, `timeout`, `error`, `mock`. `searched=true` only for `ok`/`no_results` – i.e. providers that were actually queried.

`analysis` sections each carry `source`: `measured` (computed from pixels), `inferred:<model>` (CLIP zero-shot), `tesseract-ocr`, or `unavailable` (+ `reason`).

### `GET /search/{search_id}/results`
Query: `filter` = `all|exact|similar|social|websites|news`, `sort` = `similarity|recent|relevance`, `page`, `page_size` (≤100).

Each item: `platform`, `category` (`social|website|news`), `match_type` (`exact|cropped|similar|related`), `source_url`, `image_url`, `thumbnail_url`, `page_title`, `description`, `similarity_score` (0–1 or null when the candidate image could not be fetched), `similarity_method`, `published_at`, `author_display_name`, `author_username`, `profile_url`, `profile_bio`, `website_url`, `caption`, `frame_timestamp_ms` (video), `also_found_by[]`, `is_mock`, and `field_provenance` (`provider|measured|inferred|unavailable` per field, plus `via` when found through an index such as Google Programmable Search).

`counts` gives the number of results per filter.

### `POST /results/{result_id}/report` → 204
`{"reason": "not_similar|wrong_source|wrong_profile|broken_link|inappropriate|other", "comment": "..."}`

### History
- `GET /history?type=all|image|video|text&page=&page_size=`
- `DELETE /history/{search_id}` → 204 (removes results, job and reports)
- `DELETE /history` → 204 (clear all)

### Favorites & collections (account required)
- `POST /favorites` `{"search_result_id"?, "search_id"?, "collection_id"?, "note"?, "snapshot"?}` → 201. A frozen snapshot of the result is stored so favourites survive history deletion.
- `GET /favorites?collection_id=`
- `PATCH /favorites/{id}` `{"note"?, "collection_id"?, "clear_collection"?}`
- `DELETE /favorites/{favorite_id}` → 204
- `GET /collections`, `POST /collections` `{"name"}` (409 on duplicate), `DELETE /collections/{id}`

### Account & settings
- `GET /me`, `DELETE /me` (deletes all server-side data of the caller)
- `GET /settings`, `PUT /settings` (theme, language, enabled_providers, search_quality, frame_interval_seconds, max_video_duration_seconds, history_enabled, cloud_sync_enabled [account only], third_party_consent)

### System
- `GET /providers` – catalog with `configured`, `mode` (`live|mock|unavailable`), `supports_reverse_image`, `uploads_media`, `limitations`, `alternatives`, `credential_env`.
- `GET /health` – `{"status", "database", "embedding_model", "mock_mode", ...}`
- `GET /public/temp/{token}` – 5-minute HMAC-signed link to a query image, used only by URL-based reverse-image providers.
