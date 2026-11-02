# Testing

## Backend (`backend/tests`)

```bash
cd backend
pip install -r requirements-dev.txt
pytest -q                                   # SQLite (fast, no services needed)
TEST_DATABASE_URL=postgresql+asyncpg://user:pass@localhost:5432/visualtrace_test pytest -q   # PostgreSQL
ruff check app tests
```

| File | Covers |
|---|---|
| `test_validation.py` | Image upload validation: JPG/PNG/WEBP accepted; GIF, extension/MIME mismatch, image+PHP polyglot, HTML disguised as JPG, truncated, empty and oversized files rejected; video container sniffing (MP4/MOV/WEBM) and limits. |
| `test_vision.py` | Perceptual hashes (resize-robust), embeddings ordering, exact/cropped/related classification, honest analysis sections (no invented objects without CLIP), dominant colours, **video processing**: duration probe, representative frame extraction with near-duplicate removal, interval/duration/frame limits. |
| `test_providers.py` | **Provider failure handling**: not configured, flagged mock mode, consent gating for upload providers, rate limiting (429), retries on 5xx, credential errors (401), timeouts, network errors; Reddit parsing; social site-search fallback; Facebook never claims a native API; registry selection. |
| `test_api_search.py` | **API integration**: image search end-to-end (validation → analysis → providers → scoring → dedupe → persistence), filters/sorting/counts, provenance, temp-file deletion, consent, partial/failed statuses, SSRF protection for pasted URLs, video frames with timestamps, full-video consent, text characteristics search, ownership isolation, reporting, local index, provider table sync. |
| `test_api_account.py` | **Authentication** (guest ids, dev tokens on/off, client API keys), rate limiting, history list/delete/clear, favourites & collections (account-only, snapshots survive history deletion, cross-user protection), settings validation, account deletion, production config validation, signed temp URLs. |

Sample media: `tests/fixtures/` (regenerate with `python tests/fixtures/generate_fixtures.py`).
Mock provider responses: `tests/mock_responses/` (Google CSE, Openverse, Reddit, TinEye), served by `MockProviderTransport` in `conftest.py`, which can also inject HTTP failures per host.

## Android

```bash
cd android
./gradlew :domain:test testDebugUnitTest      # JVM + Robolectric tests
./gradlew lintDebug
./gradlew connectedDebugAndroidTest           # instrumentation tests (device/emulator)
```

| Module | Tests |
|---|---|
| `domain` | `MediaRulesTest` (formats, size limits, URL validation, frame timestamp planning), `UseCaseTest` (offline handling, on-device frame extraction + cleanup, full-video consent, text search validation, offline result filtering/sorting). |
| `data` | `MappersTest` (backend JSON → domain incl. provenance, unknown statuses, cache round trip), `ErrorHandlingTest` (error envelope mapping, transport errors, WorkManager error codec), `ApiIntegrationTest` (Retrofit + MockWebServer: multipart upload, guest/client headers, query params, error envelopes, server down), `DatabaseTest` (Room DAOs, Robolectric), `FrameHashTest`. |
| `features` | `HomeViewModelTest` (URL validation, unsupported file, offline, consent prompt before upload providers, provider preferences, full-video consent), `ComponentsUiTest` (Compose UI via Robolectric: error state retry, result card match type/similarity/MOCK badge/frame timestamp/provenance, save toggle, auth validation). |

## Manual end-to-end check

1. Start the backend with `PROVIDER_MOCK_MODE=true AUTH_DEV_TOKENS=true` (development only) – every provider returns flagged mock results derived from your image, so the full flow can be exercised without credentials.
2. Run the debug app on an emulator (`VT_API_BASE_URL_DEBUG=http://10.0.2.2:8000/`), continue as guest, pick `backend/tests/fixtures/sample_car.jpg`, search.
3. Verify the MOCK banner/badges, provider report, filters, sorting, save/share/copy/report, history and video search (`sample_video.mp4`).
4. Configure real provider keys, disable mock mode and repeat.
