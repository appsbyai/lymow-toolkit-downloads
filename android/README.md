# Lymow Companion — Android app

A **fully native** Android companion for the [Lymow Toolkit](https://github.com/AppGuy77/lymow-toolkit-downloads)
dashboard — the community companion server for the **Lymow One** robotic mower.

No web view, no wrapper: every screen is native Jetpack Compose talking directly to the
Toolkit's HTTP API (reverse-engineered from the v2.10.3 server bundle).

> **Independent community project — not affiliated with, endorsed by, or supported by Lymow.**
> This app can control a real machine with spinning blades. Always supervise your mower.

## Features

- **Native sign-in** — server address → first-run password creation → password login →
  optional 2FA (TOTP/recovery codes). The app exchanges the dashboard password for the
  Toolkit's `lymow_session` cookie and stores only that — never the password.
- **Home** — live status polled from `/api/telemetry`: 16 mower states (Mowing, Paused,
  Charging, Docking…), battery ring, mow-progress %, RTK fix and network chips, fault
  banner with the mower's own error codes, and context-aware quick actions
  (Mow / Resume / Pause / Dock) — each behind a safety confirmation dialog.
- **Map** — zones, no-go areas, dock and the mower's live position drawn from
  `/api/geojson` on a Compose Canvas. Pinch to zoom, drag to pan.
- **Schedule** — list, enable/disable, run-now, skip-next, delete, and create schedules
  (once / weekly / even / odd days, any start time) via `/api/schedule(s)`.
- **History** — lifetime totals (area, mow time, count), per-zone freshness
  (`/api/zone-staleness`) and recent mow records (`/api/mow-history`).
- **Settings** — sign out, forget server, theme (System/Light/Dark), community links.
- **Material 3 + Material You** — dynamic color, edge-to-edge, light/dark.

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

The Toolkit is a FastAPI server at `http://<your-computer-ip>:8787`. This app uses its real
endpoints — no screen-scraping, no WebView:

| Area | Endpoints |
|---|---|
| Auth | `GET /api/access/status` · `POST /api/access/setup` · `POST /api/access/login` · `POST /api/access/totp` · `POST /api/access/logout` |
| Status | `GET /api/telemetry` (robot status codes 0–15, battery, clean %, error codes, RTK fix) |
| Control | `POST /api/mow` · `POST /api/command/{pause\|resume\|dock\|…}` · `GET /api/commands` |
| Schedules | `GET /api/schedules` · `POST /api/schedule` · `DELETE /api/schedule/{id}` · `POST /api/schedule/{id}/skip` · `POST /api/schedule/{id}/run` |
| History | `GET /api/mow-totals` · `GET /api/mow-history` · `GET /api/zone-staleness` |
| Map | `GET /api/geojson` (zones, no-go, dock, robot position) |

Unsafe methods pass the Toolkit's same-origin check because the app sends no `Origin` header
(it is not a browser). Cleartext HTTP is allowed on purpose: the Toolkit serves plain HTTP on
your own LAN, and the app only ever contacts the address you typed in.

## Project layout

```
android/
├── app/
│   └── src/main/
│       ├── java/com/lymow/toolkit/companion/
│       │   ├── MainActivity.kt          # 5-tab nav host
│       │   ├── data/
│       │   │   ├── SettingsStore.kt     # server URL + session cookie (DataStore)
│       │   │   └── ToolkitApi.kt        # the native API client
│       │   └── ui/                      # Connect / Home / Map / Schedule / History / Settings
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
