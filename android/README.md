# Temp Number — Android app

Kotlin + Jetpack Compose + Retrofit client for the backend in `../backend`.

## Screens

1. **Settings** — enter the backend URL and API key (`APP_API_KEY`). Stored
   with DataStore. The app won't do anything until these are set.
2. **Your numbers** — search rentable numbers by area code, rent one, release
   one, or open its messages.
3. **Messages** — the SMS received on a selected number.

## Build & run

This module is a standard Gradle/Android project. Open the `android/` folder in
**Android Studio** (Ladybug or newer) and let it sync, or from the command line
once a Gradle wrapper is present:

```bash
cd android
# If you don't have the wrapper yet, generate it once with a local Gradle 8.9+:
#   gradle wrapper --gradle-version 8.9
./gradlew assembleDebug
```

> The Gradle wrapper JAR/scripts are intentionally not committed here. Generate
> them with the command above (or via Android Studio) so you get a wrapper that
> matches your Gradle install.

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
