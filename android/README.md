# Lymow Companion — Android app

A native Android companion for the [Lymow Toolkit](https://github.com/AppGuy77/lymow-toolkit-downloads)
dashboard — the community companion server for the **Lymow One** robotic mower.

> **Independent community project — not affiliated with, endorsed by, or supported by Lymow.**
> This app can control a real machine with spinning blades. Always supervise your mower.

## Features

- **One-step connect** — type the Toolkit address (e.g. `192.168.1.50`, port `8787`), tap Connect, done.
- **Native home screen** — mower state, battery ring, RTK / Wi-Fi chips, next scheduled mow.
- **Quick actions** — Start / Pause / Dock, each behind a safety confirmation dialog.
- **Full dashboard built in** — the complete Toolkit web UI (live map, scheduling, mow-history
  calendar, freshness and RTK heat maps, diagnostics) embedded in the app, so every feature works
  with zero setup.
- **Material 3 + Material You** — dynamic color, light/dark theme, edge-to-edge.

## Requirements

- Android 8.0 (API 26) or newer
- The [Lymow Toolkit](https://github.com/AppGuy77/lymow-toolkit-downloads) running on your network
  (Windows installer, macOS/Linux/Docker bundle, or the Home Assistant app)
- Phone and Toolkit server on the same network

## Getting the APK

- **Build yourself:** open this `android/` folder in Android Studio (Ladybug or newer) and press
  Run, or from a shell:

  ```bash
  cd android
  gradle wrapper --gradle-version 8.9   # one-time: the wrapper jar is not committed
  ./gradlew :app:assembleDebug          # APK lands in app/build/outputs/apk/debug/
  ```

- **Build in the cloud:** a ready-made GitHub Actions workflow lives at
  [`ci/android-build.yml`](ci/android-build.yml). Move it to `.github/workflows/android-build.yml`
  and every push under `android/` produces a downloadable APK artifact.

## How it talks to the Toolkit

The Toolkit is a local web app served at `http://<your-computer-ip>:8787`. This app:

1. **Probes** the address you give it and saves it locally (Android DataStore — nothing leaves
   your phone; the dashboard password is stored only on-device).
2. Renders the **full dashboard** in an embedded WebView — always complete, always up to date
   with your Toolkit version.
3. Additionally tries a few conventional JSON endpoints (`/api/status`, `/api/state`, …) for a
   **native** status read-out and quick actions. The Toolkit's native API is undocumented, so if
   a given build doesn't expose them, the home screen simply points you at the Dashboard tab —
   nothing breaks.

Cleartext HTTP is allowed on purpose: the Toolkit serves plain HTTP on your own LAN, and the app
only ever contacts the address you typed in.

## Project layout

```
android/
├── app/
│   └── src/main/
│       ├── java/com/lymow/toolkit/companion/
│       │   ├── MainActivity.kt          # nav host + bottom bar
│       │   ├── data/                    # DataStore settings + OkHttp Toolkit client
│       │   └── ui/                      # Connect / Home / Web dashboard / Settings screens
│       └── res/                         # icons, theme, network config
├── ci/android-build.yml                 # GitHub Actions workflow (move to .github/workflows/)
└── build.gradle.kts                     # Kotlin 2.0 + Jetpack Compose, minSdk 26
```

## Safety

This software controls a **real machine with spinning blades**. It is provided "as is", with no
warranty. Remote start/stop is no substitute for keeping people, pets, and hands clear of the
blades. You sign in with **your own** Lymow account on **your own** Toolkit server — nothing is
shared with anyone.

## ☕ Donations

The Toolkit is free and always will be. Support its development at
**https://ko-fi.com/lymow_toolkit** — appreciated, never expected.
