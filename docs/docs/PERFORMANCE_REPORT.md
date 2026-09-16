# Prototype Performance Report

Status: emulator baseline captured on the final debug/testing APK. These values
are directional only and are not representative of entry-level field hardware.

## Test profile

- Device: connected Android emulator `emulator-5554`, Android 17, 1080×2400.
- Build: debug/testing variant, minification enabled, development API.
- Final APK: `app-debug.apk`, 14 September 2026 build, 8,820,198 bytes,
  SHA-256 `933D13281225ED95D46A6802626E8E7A89538ADBEFB617E71AC8BED81A710A20`.
- Scenarios: cold launch, authenticated dashboard, inventory/pool list, QR
  rendering, Room cache read, network-off cache read and queued confirmation.

## Captured observations

- Final-APK cold launch with `adb shell am start -W` reported
  `LaunchState: COLD`, `TotalTime: 3123 ms` and `WaitTime: 3180 ms`.
- Final-process `dumpsys meminfo` reported `TOTAL PSS: 76253 kB`,
  `TOTAL RSS: 188844 kB` and `TOTAL SWAP PSS: 3512 kB`.
- A second `am start -W` while the activity was already running returned
  `TotalTime: 0 ms`; this is not treated as a valid warm-start measurement.
- Logcat showed one cold Compose transition with `Skipped 83 frames` and a
  `Davey` duration of about `1318 ms`. This is recorded as a follow-up risk,
  not hidden behind an average.

The implementation uses bounded lists, account-scoped cache snapshots, stable
server pagination for catalogue reads and Room/WorkManager for queued work. A
release-quality pass still needs a representative 2 GB Android 6/7 device,
large-list fixture, image-size measurement, frame/jank capture and network
profiles.

## Correctness/performance guardrails

Do not move JSON/bitmap work onto the main thread, do not show unbounded lists,
keep actual image bounds stable, avoid duplicate refreshes, and preserve
offline evidence labels even when a refresh is slow or fails.
