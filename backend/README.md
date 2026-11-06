# Temp Number — backend

Node + Express. Receives Twilio SMS webhooks and serves them to the Android
client over an API protected by a single app API key.

## Setup

```bash
cd backend
npm install
cp .env.example .env
# edit .env: set TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, APP_API_KEY, PUBLIC_BASE_URL
npm start
```

Generate a strong `APP_API_KEY`:

```bash
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
```

### Exposing the webhook during development

Twilio needs a public URL to deliver SMS. Use a tunnel:

```bash
# e.g. with ngrok
ngrok http 3000
# then set PUBLIC_BASE_URL in .env to the https URL ngrok prints
```

When you rent a number through `POST /api/numbers`, the backend automatically
points that number's SMS webhook at `PUBLIC_BASE_URL/sms`.

## API

All `/api/*` routes require header `X-API-Key: <APP_API_KEY>`.
The `/sms` webhook is **not** key-protected — it's verified by Twilio's request
signature instead.

| Method | Path                         | Purpose                                  |
|--------|------------------------------|------------------------------------------|
| GET    | `/health`                    | Liveness check (open)                    |
| POST   | `/sms`                       | Twilio inbound-SMS webhook               |
| GET    | `/api/available-numbers`     | Search rentable numbers (`?country=US&areaCode=415`) |
| GET    | `/api/numbers`               | List numbers you've rented               |
| POST   | `/api/numbers`               | Rent a number `{ "phoneNumber": "+1..." }` |
| DELETE | `/api/numbers/:phoneNumber`  | Release a number                         |
| GET    | `/api/messages?number=+1...` | List received SMS (all, or for a number) |

## Tests

```bash
npm test
```

Runs without any Twilio credentials: it stubs out the signature check in test
mode and exercises the storage + auth + webhook-routing logic, including the
guarantee that SMS to an **untracked** number is dropped.

## Notes & production hardening

- Storage is a JSON file (`src/store.js`) — fine for one instance. Swap for a
  real DB before production.
- Add per-user accounts if more than one person uses the backend; today the
  single `APP_API_KEY` grants access to every tracked number.
- Rate-limit `/api/available-numbers` and `/api/numbers` — they cost money.
