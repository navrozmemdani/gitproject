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

## Accounts & auth

The backend is **multi-user**. Each account owns its own numbers and sees only
the SMS delivered to those numbers.

- **Register** with `POST /api/auth/register`. Unless `ALLOW_OPEN_SIGNUP=true`,
  the caller must send the shared signup key in header `X-Signup-Key:
  <APP_API_KEY>`. Register and login both return a **bearer token**.
- **Authenticate** every `/api/*` call (except the auth routes) with header
  `Authorization: Bearer <token>`.
- Passwords are hashed with scrypt (Node stdlib); tokens are random 256-bit
  strings stored server-side and revoked on logout.

The `/sms` webhook is **not** token-protected — it's verified by Twilio's
request signature instead. Inbound SMS is stored keyed by the destination
number; ownership is resolved at read time, so a user can only ever read
messages for numbers they own.

## API

| Method | Path                         | Auth           | Purpose                                  |
|--------|------------------------------|----------------|------------------------------------------|
| GET    | `/health`                    | none           | Liveness check                           |
| POST   | `/sms`                       | Twilio sig     | Twilio inbound-SMS webhook               |
| POST   | `/api/auth/register`         | signup key     | Create account → `{ token, user }`       |
| POST   | `/api/auth/login`            | none           | Log in → `{ token, user }`               |
| POST   | `/api/logout`                | bearer         | Revoke the current token                 |
| GET    | `/api/me`                    | bearer         | Current user                             |
| GET    | `/api/available-numbers`     | bearer         | Search rentable numbers (`?country=US&areaCode=415`) |
| GET    | `/api/numbers`               | bearer         | List **your** rented numbers             |
| POST   | `/api/numbers`               | bearer         | Rent a number `{ "phoneNumber": "+1..." }` |
| DELETE | `/api/numbers/:phoneNumber`  | bearer         | Release one of **your** numbers          |
| GET    | `/api/messages?number=+1...` | bearer         | SMS for **your** numbers (all, or one)   |

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
- Tokens don't expire on their own yet; add a TTL / refresh scheme for
  production, and consider moving the token out of plain storage.
- Rate-limit `/api/auth/*`, `/api/available-numbers` and `/api/numbers` —
  the first to slow credential guessing, the others because they cost money.
