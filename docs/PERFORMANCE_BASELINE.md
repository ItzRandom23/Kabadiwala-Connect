# Performance baseline and measurement gates

Updated 1 October 2026. Visible tap feedback and network completion are separate measurements.

## Recorded Android samples

| Date/build | Scenario | Result | Interpretation |
| --- | --- | --- | --- |
| 28 September, 0.1.5-beta (69), Pixel 10 Pro AVD / Android 17 | Six force-stop launches | 5162, 5802, 5601, 5436, 5590, 5319 ms; median 5513; approximate p95 5752 | adb am start -W activity launch, not loaded dashboard |
| Same session | Six existing-task resumes | 234, 138, 227, 129, 181, 139 ms; median 160; approximate p95 232 | Not login/tap feedback |
| 29 September candidate | Two cold starts / one resume | 5642 / 6245 ms cold; 1270 ms resume; total PSS 139246 KB | Spot check after instrumentation |

Small samples and emulator load prevent a defensible regression or acceptance claim. These historical builds are not current 0.1.14-beta.

## High-volume database verification

Scale 1000 generated 1,000 listings, pickups, inventory movements, lots, offers and demands, plus 3,000 messages and notifications in an isolated database.

Feed verification traversed 1,000 offers and 1,500 account notifications without skipped/duplicate IDs. Fifteen activity-page samples recorded approximately p50 796 ms and p95/p99 5641 ms, including a cold sample and remote database latency.

These measure the sampled service/database path, not all HTTP endpoints or UI feedback. Other remote journeys still took seconds. Bounded traversal does not establish acceptable app latency.

## Instrumentation

- Android debug KcUiTiming: action start, publication, subsequent frame and request correlation.
- KcScreenTiming: destination-template first frame and Activity timing.
- KcApiTiming: operation, request ID, elapsed time and status/failure class.
- Backend: route-template request timing and bounded one-minute p50/p95/p99 summaries.

No credentials, query values, private addresses or message content should be logged. API_P95_WARN_MS defaults to 1000 ms and requires at least 20 requests per summary window. External production collection/alerting is separate.

## Deterministic fixture

The [seed script](../backend/src/scripts/seedPerformanceData.ts) requires a fresh kabadiwala_perf_test database, APP_ENV=testing, non-production NODE_ENV and PERF_SEED_CONFIRM=kabadiwala_perf_test. It refuses repeated seeding. PERF_SEED_SCALE supports 100–5000; default 1000.

From backend/, after securely supplying a disposable DATABASE_URL in the shell:

```powershell
$env:APP_ENV = 'testing'
$env:NODE_ENV = 'development'
$env:PERF_SEED_CONFIRM = 'kabadiwala_perf_test'
$env:PERF_SEED_SCALE = '1000'
npm run db:seed:performance
```

Never use the application database. The seed reads process environment; do not assume it loads .env. Replica-set support is required for transactional journey tests.

## Outstanding gates

The 0.1.13-beta journey refresh fallback is two seconds while resumed. Measure its request volume and battery impact alongside cross-account visible update delay; this interval is not a performance result. APK assembly and backend compilation passed, but no fresh latency baseline was captured for this release.

1. Compare builds using identical device, data, account state and script.
2. Measure cold/warm start, login/dashboard, tabs, details/back and return-screen request counts.
3. Gate local tap feedback below **100 ms p95**, independently of server confirmation.
4. Capture HTTP p50/p95/p99, query plans, payload sizes and database round trips.
5. Exercise slow network, offline/reconnect, process death, account switching and long sessions.
6. Measure CPU, leaks/PSS, battery, network bytes, Room growth, WorkManager frequency and ANRs.
7. Complete representative physical-device acceptance.

No physical-device under-100-ms feedback gate is established. See [verification status](README.md).
