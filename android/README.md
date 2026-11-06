# Temp Number — Android app

Kotlin + Jetpack Compose + Retrofit client for the backend in `../backend`.

## Screens

1. **Sign in / Register** — enter the backend URL, email and password. To
   register you also need the **signup key** (the backend's `APP_API_KEY`),
   unless the backend has open signup enabled. The issued bearer token is
   stored with DataStore and reused on next launch.
2. **Your numbers** — search rentable numbers by area code, rent one, release
   one, or open its messages. Shows only numbers your account owns. Log out
   from the top bar.
3. **Messages** — the SMS received on a selected number.

## Build & run

This module is a standard Gradle/Android project with the Gradle **wrapper
committed** (`gradlew`, `gradle/wrapper/`). Open the `android/` folder in
**Android Studio** (Ladybug or newer) and let it sync, or from the command
line:

```bash
cd android
./gradlew assembleDebug
```

The first build downloads the Android Gradle Plugin and SDK components from
Google, so it needs normal internet access and an installed Android SDK
(Android Studio sets `sdk.dir` in `local.properties` for you).

## Connecting to a local backend

- An Android **emulator** reaches your host machine at `http://10.0.2.2:3000`.
- A **physical device** needs the backend on a reachable URL — use the same
  public tunnel URL you gave Twilio (HTTPS), e.g. your ngrok URL.
- Cleartext `http://10.0.2.2` works on the emulator because `minSdk` debug
  builds allow it on loopback; for anything else, use HTTPS.

## What it can and can't show

It shows SMS delivered to numbers **you rented** through your Twilio account and
tracked by your backend. It cannot show codes sent to any other number — the
backend drops inbound SMS for numbers it doesn't track (see the backend test).
