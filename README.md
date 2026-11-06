# Temp Number — virtual phone numbers for OTP / SMS

An Android app + backend for **renting real phone numbers from a licensed
telephony provider and viewing the SMS that arrive on them**. Useful for
protecting your personal number when signing up for services, avoiding spam,
and testing your own SMS/OTP flows during development.

## How it works (and its one hard limit)

```
 ┌────────────┐   SMS to a number    ┌──────────────┐   webhook    ┌──────────┐   HTTPS   ┌─────────────┐
 │  Sender    │ ───────────────────▶ │  Twilio      │ ───────────▶ │ Backend  │ ◀───────▶ │ Android app │
 │ (any app)  │   you rented         │ (provider)   │   /sms       │ (this repo)│  API      │             │
 └────────────┘                      └──────────────┘              └──────────┘           └─────────────┘
```

The app can **only** show SMS that are sent to numbers *you rent through the
provider*. It cannot receive a code sent to anyone else's number — there is no
telephony path for that, by design. That is what separates a legitimate
temp-number app from account-takeover tooling.

You must have your own [Twilio](https://www.twilio.com/) account (free trial
works). Numbers cost money to rent; that is billed by Twilio, not this app.

The backend supports **multiple user accounts** — each account owns its own
numbers and can only read the SMS delivered to those numbers. Accounts sign in
from the app and get a bearer token; registration is gated by a shared signup
key unless you enable open signup.

## Layout

| Path        | What it is                                                        |
|-------------|-------------------------------------------------------------------|
| `backend/`  | Node + Express. Receives Twilio webhooks, exposes an authed API.  |
| `android/`  | Kotlin + Jetpack Compose app that talks to the backend.           |

Each has its own README with setup steps. Start with `backend/`.

## Legitimate use only

Don't use this to bypass phone verification for fraud, evade bans, or create
accounts you aren't entitled to. Renting a number doesn't give you any right to
codes meant for other people.
