# VisualTrace AI

**Discover anything visually.** VisualTrace AI is an Android app plus a FastAPI backend that
lets users upload an image or video and find visually similar content on publicly accessible
websites and supported platforms, through official and licensed search APIs.

- Android: Kotlin, Jetpack Compose + Material 3, MVVM + Clean Architecture, Hilt, Retrofit/OkHttp,
  Coroutines/Flow, Room, DataStore, WorkManager, Coil 3, Media3, Firebase Authentication, Credential Manager.
- Backend: Python FastAPI, PostgreSQL (SQLAlchemy 2 async + Alembic), OpenCV, OpenCLIP (optional),
  Tesseract OCR (optional), firebase-admin.

> Honesty rules built into the system: providers without credentials report *not configured* and
> return nothing; every result shows which provider found it and whether each field came from the
> source, was measured, inferred, or is unavailable; similarity is never presented as proof of
> identity, ownership or that two images show the same person; no face recognition is performed.

## Repository layout

```
VisualTraceAI/
├── android/                 Gradle (Kotlin DSL) multi-module project
│   ├── app/                 Application, MainActivity, DI wiring, manifest, signing
│   ├── core/                Design system (navy/blue/violet theme, light & dark), shared UI, formatters
│   ├── domain/              Pure Kotlin models, repository contracts, use cases, media rules
│   ├── data/                Retrofit API, DTOs/mappers, Room, DataStore, repositories, WorkManager workers
│   └── features/            Compose screens + ViewModels
│       ├── home/  image_search/  video_search/  results/
│       ├── history/  favorites/  profile/  settings/  auth/  legal/  navigation/
├── backend/
│   ├── app/
│   │   ├── api/v1/          search, history, favorites, account, system routers
│   │   ├── core/            config, security (Firebase/guest/client keys), rate limiting, errors, logging
│   │   ├── database/        engine/session, declarative base
│   │   ├── models/          ORM tables
│   │   ├── schemas/         Pydantic request/response models
│   │   ├── services/        validation, analysis, hashing, embeddings, similarity, video, aggregation, search
│   │   ├── providers/       one class per provider (+ social/)
│   │   └── workers/         background job runner
│   ├── alembic/             migrations
│   ├── tests/               pytest suite, sample media, mock provider responses
│   ├── requirements*.txt  .env.example  Dockerfile  docker-compose.yml
└── docs/                    API.md, PROVIDERS.md, DEPLOYMENT.md, TESTING.md
```

## Quick start

### 1. Backend (local development)
```bash
cd VisualTraceAI/backend
python -m venv .venv && . .venv/bin/activate
pip install -r requirements-dev.txt          # add requirements-ml.txt for CLIP + OCR
cp .env.example .env                         # set DATABASE_URL; for a first run you may set
                                             # PROVIDER_MOCK_MODE=true and AUTH_DEV_TOKENS=true
docker run -d -p 5432:5432 -e POSTGRES_USER=visualtrace -e POSTGRES_PASSWORD=visualtrace -e POSTGRES_DB=visualtrace postgres:16
alembic upgrade head
uvicorn app.main:create_app --factory --reload --port 8000
# open http://localhost:8000/docs
```
Or simply `docker compose up --build` in `backend/`.

Without any provider keys, Openverse, Wikimedia Commons and the local index work out of the box
(keyword-based – add a description or install CLIP so keywords can be derived from the image).
See [docs/PROVIDERS.md](docs/PROVIDERS.md) for each provider's exact credentials.

### 2. Android
Requirements: Android Studio (Narwhal or newer) / JDK 17, Android SDK 36.
```bash
cd VisualTraceAI/android
echo "sdk.dir=$ANDROID_HOME" > local.properties
echo "VT_API_BASE_URL_DEBUG=http://10.0.2.2:8000/" >> local.properties   # emulator -> your machine
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```
Optional: put `google-services.json` in `android/app/` and set `VT_GOOGLE_WEB_CLIENT_ID` to enable
email and Google sign-in (otherwise the app runs in guest mode). Details in [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

### Build outputs
| Artifact | Command | Location |
|---|---|---|
| Debug APK | `./gradlew assembleDebug` | `android/app/build/outputs/apk/debug/app-debug.apk` |
| Release APK | `./gradlew assembleRelease` (signing configured) | `android/app/build/outputs/apk/release/app-release.apk` |
| Play bundle | `./gradlew bundleRelease` | `android/app/build/outputs/bundle/release/app-release.aab` |

CI (`.github/workflows/visualtrace-ci.yml`) builds all three on every push and publishes them as
the `visualtrace-apks` workflow artifact.

## Features

**Search input** – Photo Picker (no storage permission), Storage Access Framework "Browse files",
system camera via `FileProvider` (no camera permission), pasted image URL, optional description.
Formats: JPG, JPEG, PNG, WEBP · MP4, MOV, WEBM. Preview with dimensions/size; video preview with
Media3 and duration.

**Image search pipeline** – client & server validation → EXIF-stripping re-encode on device →
server re-validation (magic bytes, decoder verification, decompression-bomb limits, polyglot
detection, optional ClamAV) → analysis (dimensions, orientation, quality, dominant colours,
background, OCR, CLIP zero-shot objects/clothing/environment) → pHash/dHash/aHash → embedding →
providers in parallel (timeouts, retries, rate-limit handling, consent gating) → SSRF-safe
candidate download → similarity (embedding + hashes + ORB/RANSAC for crops) → de-duplication
across providers → ranked results.

**Video search** – representative frames extracted *on the device* (MediaMetadataRetriever,
configurable interval, default every 3 s, max duration & frame caps, near-duplicate frames
skipped); each frame is searched independently and matches show the timestamp that produced them.
Full-video upload exists only behind an explicit consent switch; the server then extracts frames
with OpenCV/FFmpeg and deletes the video immediately. Videos are never sent to third parties.

**Results** – preview, date/time, result count, status, every provider with its real status
(searched / not configured / skipped – no consent / rate limited / error / mock), progress while
running, AI analysis with source labels and "search by these characteristics", filters (all, exact,
similar, social, websites, news & blogs), sorting (similarity, most recent, source relevance),
result cards with thumbnail, platform, title, URL, description, similarity meter, publication
date, public profile fields with provenance, and actions: open website, view image (pinch-zoom),
visit profile, save (collection + note), share, copy URL, report.

**Library** – local-first history (images / videos / text tabs, thumbnails, delete, clear all –
also deleted on the server, retried by WorkManager when offline), cached results for offline
viewing, saved results with collections and notes, optional cloud sync for accounts.

**Accounts** – guest mode, email registration with verification, email login, Google Sign-In
(Credential Manager), forgot password, logout, delete server data.

**Settings** – theme (system/light/dark + dynamic colour), language (en/es/fr/de/hi), provider
preferences with limitations and alternatives, search quality, frame interval, max video duration,
history management, privacy consents, account, privacy policy, terms, about, contact support.

**Security & privacy** – HTTPS-only release network config, provider keys only on the server,
client API keys, Firebase token verification with revocation checks, per-user and per-search rate
limits, input validation everywhere, upload size limits, temporary files deleted after each job
(and TTL sweeps), signed 5-minute links for URL-based providers, ownership checks on every
resource, no logging of bodies or tokens, backups exclude auth/guest identifiers.

## Configuration
- Backend: [`backend/.env.example`](backend/.env.example) documents every variable.
- API: [docs/API.md](docs/API.md).
- Providers & credentials: [docs/PROVIDERS.md](docs/PROVIDERS.md).
- Deployment & signing: [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).
- Testing: [docs/TESTING.md](docs/TESTING.md).

## Development status by phase
| Phase | Status |
|---|---|
| 1 Project setup & architecture | Done – multi-module Android project, FastAPI backend, CI |
| 2 Android UI & navigation | Done – 5-tab bottom navigation, all screens, light/dark themes |
| 3 Image & video upload | Done – picker/SAF/camera/URL, previews, on-device frame extraction, WorkManager submission |
| 4 Backend | Done – all specified endpoints + text search, collections, settings, account deletion |
| 5 AI analysis & similarity | Done – OpenCV analysis + hashes + ORB; CLIP/OCR when the ML extras are installed |
| 6 Provider integrations | Implemented against official APIs; live use requires the credentials listed in PROVIDERS.md |
| 7 Results, history, favorites | Done |
| 8 Authentication & security | Done (Firebase project configuration required for sign-in) |
| 9 Testing | Backend 68 tests (SQLite + PostgreSQL); Android JVM/Robolectric unit & UI tests in CI |
| 10 APK & documentation | CI produces debug APK, release APK, AAB; docs in `docs/` |
