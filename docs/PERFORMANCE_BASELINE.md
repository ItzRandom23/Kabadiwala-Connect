# Performance baseline and measurement gates

## Emulator sample, 28 September 2026

Build: `envTestingDebug`, version 0.1.5-beta (69). Device: Pixel 10 Pro AVD, Android 17. The app was installed directly from the local debug APK. `adb shell am start -W` measured Activity launch; this is an Android launch metric, not tap-to-visible-feedback or fully loaded dashboard time.

| Scenario | Samples, ms | Median | Approximate p95 |
| --- | --- | ---: | ---: |
| Force-stop then launch | 5162, 5802, 5601, 5436, 5590, 5319 | 5513 | 5752 |
| Home then bring existing task forward | 234, 138, 227, 129, 181, 139 | 160 | 232 |

The six-sample p95 is descriptive only. The emulator was also running instrumentation during this session, and no representative physical device or high-volume backend dataset was available. Do not use these figures to accept the `<100 ms p95` tap-feedback gate or a production startup target.

## Candidate spot check, 29 September 2026

Build: current `envTestingDebug` checkout on the same Pixel 10 Pro AVD. Two force-stop launches measured 5642 ms and 6245 ms (`am start -W`); bringing the running task forward after Home measured 1270 ms. `dumpsys meminfo` reported 139246 KB total PSS. These are two cold samples and one task-resume sample only; the emulator had just completed instrumentation tests, so they are not a statistically comparable regression result. No representative-device or high-volume journey performance acceptance was run.

## Tracing now available

- Android debug `KcUiTiming`: tap/action start, immediate state publication, first subsequent frame, request ID, and server response. It contains operation names and elapsed time, not account data.
- Android debug `KcScreenTiming`: first frame after a destination route is observed and time since Activity creation. The route is the navigation template.
- Android debug `KcApiTiming`: Retrofit operation, request ID, elapsed time, status or failure class. No URL parameters, message bodies, or personal data.
- Backend request middleware: route templates and bounded one-minute p50/p95/p99 latency/failure summaries. It does not log query values or user content.

## Acceptance measurements still required

1. Representative physical devices: cold/warm start; login to dashboard; each bottom tab; detail and back navigation; return-to-screen refetch counts.
2. Tap-to-visible-feedback p95 for accept pickup, schedule/status, chat send, offer, pricing save, demand publish, and profile save. Target: below 100 ms for local feedback, while server-authoritative outcomes remain pending until confirmed.
3. Slow network, offline/reconnect, process death, account switching, and long-running sessions. Capture CPU, memory, battery, network bytes, Room growth, WorkManager frequency and ANRs.
4. Deterministic high-volume histories, inventory, offers, demands, messages and notifications against a disposable MongoDB test deployment; record backend route p50/p95/p99 and query plans before changing indexes.

The guarded fixture is `backend/src/scripts/seedPerformanceData.ts`. It requires a fresh MongoDB database named `kabadiwala_perf_test`, `APP_ENV=testing`, and `PERF_SEED_CONFIRM=kabadiwala_perf_test`. `PERF_SEED_SCALE` defaults to 1000 and produces 1000 listings, pickups, inventory movements, lots, offers and demands, plus 3000 chat messages and notifications. The script refuses a second run so samples remain deterministic. Do not point this fixture at the development or production database.

The backend emits bounded one-minute route-template p50/p95/p99 summaries and `api_latency_regression` warnings when a route with at least 20 requests exceeds its p95 threshold. `API_P95_WARN_MS` defaults to 1000. Logs contain route templates and request IDs, not addresses, message contents or account identifiers. A deployed log/metrics collector must turn warnings into operational alerts; no external alert destination is configured in this repository.
5. Compare baseline and candidate builds using the same device, data, account state and test script. A single emulator run cannot distinguish app regression from emulator load.
