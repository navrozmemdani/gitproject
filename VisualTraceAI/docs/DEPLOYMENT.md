# Deployment

## Backend

### Requirements
- Python 3.12+ (tested on 3.13), PostgreSQL 14+ (tested on 16), FFmpeg (OpenCV video backend).
- Optional: `tesseract-ocr` (OCR) and the ML extras (`requirements-ml.txt`, PyTorch + OpenCLIP) for semantic analysis and CLIP embeddings. Allow ~2 GB RAM per worker with CLIP.
- Optional: ClamAV `clamd` for malware scanning (`CLAMAV_HOST`).

### Render (free test deployment, no computer needed)
`render.yaml` at the repository root describes the backend web service and a PostgreSQL database.
1. Sign in at https://dashboard.render.com with GitHub.
2. **New → Blueprint**, pick this repository, then **Apply**. Render builds the Docker image and creates the database (about 5–10 minutes).
3. Copy the service URL (e.g. `https://visualtrace-api.onrender.com`) and enter it in the app under **Settings → Server**.

The blueprint runs in test mode (`PROVIDER_MOCK_MODE=true`): providers without API keys return results labelled MOCK. Add real keys in the service's **Environment** tab and set `PROVIDER_MOCK_MODE=false` when ready. Free instances sleep after 15 minutes without traffic, so the first request after a pause can take about a minute, and Render's free PostgreSQL databases expire after 30 days.

### Docker Compose (single host)
```bash
cd backend
cp .env.example .env          # fill in secrets, set ENVIRONMENT=production
docker compose up -d --build  # runs `alembic upgrade head`, then uvicorn on :8000
# with CLIP + OCR:
docker compose build --build-arg WITH_ML=true api && docker compose up -d
```

### Manual
```bash
cd backend
python -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt            # + requirements-ml.txt for CLIP
export $(grep -v '^#' .env | xargs)        # or use a process manager's env file
alembic upgrade head
uvicorn app.main:create_app --factory --host 0.0.0.0 --port 8000 --proxy-headers
```

### Production checklist
- `ENVIRONMENT=production` – the app refuses to start with mock mode, dev tokens, the default `SIGNED_URL_SECRET`, or no Firebase configuration.
- Terminate TLS at a reverse proxy (nginx, Caddy, Cloud Run, ALB). Only expose HTTPS. Set `client_max_body_size` ≥ your `MAX_VIDEO_BYTES`.
- `FIREBASE_CREDENTIALS_FILE` → service-account JSON mounted as a secret (or `FIREBASE_PROJECT_ID` on GCP with workload identity).
- `CLIENT_API_KEYS` → random value(s); put the same value in the Android build as `VT_CLIENT_API_KEY`.
- Provider keys only in the environment / secret manager – never in the app or repository.
- `PUBLIC_BASE_URL` → the public HTTPS URL (needed only for SerpApi Lens).
- `TEMP_DIR` on local disk with enough space for concurrent uploads; files are deleted after each job and swept by TTL.
- Rate limiting and the job runner are in-process. Run **one worker per container** and scale horizontally behind a load balancer with sticky sessions, or move rate limiting to the gateway (e.g. Redis/Envoy) and the job runner to a queue worker (Celery/RQ/Cloud Tasks) for multi-instance deployments.
- Back up PostgreSQL; history rows contain only metadata, results and an optional 160px preview.

### Firebase
1. Create a Firebase project → Authentication → enable **Email/Password** and **Google**.
2. Android app: add package `com.visualtrace.ai` (and `com.visualtrace.ai.debug` for debug builds) with your SHA-1/SHA-256 signing fingerprints; download `google-services.json` to `android/app/`.
3. Copy the **Web client ID** (OAuth client of type *Web application* created by Firebase) to `VT_GOOGLE_WEB_CLIENT_ID`.
4. Backend: Project settings → Service accounts → generate a key → `FIREBASE_CREDENTIALS_FILE`.

## Android

### Configuration (`android/local.properties` or environment variables)
```properties
sdk.dir=/path/to/Android/sdk
VT_API_BASE_URL=https://api.your-domain.com/
VT_API_BASE_URL_DEBUG=http://10.0.2.2:8000/      # emulator -> host machine
VT_CLIENT_API_KEY=<same as CLIENT_API_KEYS on the server>
VT_GOOGLE_WEB_CLIENT_ID=<firebase web client id>.apps.googleusercontent.com
VT_SUPPORT_EMAIL=support@your-domain.com
```
Without `google-services.json` the app builds and runs in guest mode; account features explain that sign-in is not configured.

### Release signing
Create a keystore once (keep it out of the repository – `*.jks` is git-ignored):
```bash
keytool -genkeypair -v -keystore visualtrace-release.jks -alias visualtrace -keyalg RSA -keysize 4096 -validity 10000
```
Then either create `android/keystore.properties`:
```properties
VT_KEYSTORE_FILE=/absolute/path/visualtrace-release.jks
VT_KEYSTORE_PASSWORD=...
VT_KEY_ALIAS=visualtrace
VT_KEY_PASSWORD=...
```
or export the same names as environment variables. For Google Play use Play App Signing and upload the AAB signed with your upload key.

### Build
```bash
cd android
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk  (app-release-unsigned.apk without signing config)
./gradlew bundleRelease        # app/build/outputs/bundle/release/app-release.aab
```

### CI
`.github/workflows/visualtrace-ci.yml` runs backend tests against PostgreSQL and builds/tests the Android app, uploading `app-debug.apk`, `app-release.apk` and `app-release.aab` as the `visualtrace-apks` artifact. Add repository secrets `VT_KEYSTORE_BASE64` (`base64 -w0 visualtrace-release.jks`), `VT_KEYSTORE_PASSWORD`, `VT_KEY_ALIAS`, `VT_KEY_PASSWORD` to sign with your real key; without them CI signs the release with a throw-away key that must not be distributed.
