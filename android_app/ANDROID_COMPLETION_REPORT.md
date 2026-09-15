# Android completion report — Neon Purple edition

Date: 2026-09-15
Scope: `android_app` only. Backend source and database were not modified. No deployment or publishing was performed.

## Outcome

The Android application now builds on the existing Compose/MVVM foundation with a dark-first Neon Purple theme, the new household/pickup/inventory/formal-handover contract surface, persisted idempotency keys, offline pickup/handover replay and a clear compatibility guard against using the legacy recycler receipt endpoint.

Existing business flows were preserved: authentication, role routing, legacy lot/quote/payment/handover flows, Room caches, WorkManager sync, recycler QR scanning, formalisation dashboard, safety acknowledgements and the three operational role surfaces.

## Implemented files and behavior

- `ui/theme/Color.kt`, `ui/theme/Theme.kt`: brand palette, dark/light Material 3 schemes and semantic colours.
- `ui/components/AppBars.kt`, `ui/components/CommonStates.kt`: violet/magenta app chrome, active navigation and accessible primary/status/loading states.
- `data/remote/ApiDtos.kt`: typed request/response models for the current backend additions, with defensive nullable defaults.
- `data/remote/ApiService.kt`: current household, pickup, inventory, pooling, offer, demand, safety, passport, anomaly, legacy and capability-gated admin paths.
- `ui/screens/supplychain/SupplyChainViewModel.kt`: role checks, lifecycle actions, inventory movement/pool suggestion reads, offline pickup enqueue and formal handover idempotency.
- `data/local/IdempotencyKeyStore.kt`: persisted UUID keys scoped to stable business operations.
- `data/sync/Sync.kt`: replays pickup and formal handover mutations with the original payload and key; 409 replay checks do not fabricate success.
- `ui/screens/recycler/RecyclerOrdersViewModel.kt`: formal QR receipt sends the persisted key and queues the same payload on transient failure.
- `ui/screens/supplychain/SupplyChainScreens.kt`: household data-bearing device preparation/destruction-evidence request fields.

## Endpoint coverage summary

The detailed matrix is in `ANDROID_API_COVERAGE_MATRIX.md`. In summary:

- Auth/profile, legacy household/collector/recycler marketplace, quotes, payments, notifications, activity and future-feature endpoints already have existing repositories/screens or are now retained in the shared Retrofit service.
- Core household → Kabadiwala flow is visible for listing, Kabadiwala discovery, pickup request/cancel, pickup status, completion, inventory, route advantage, pooling, QR preparation and material evidence.
- New pickup reliability, reschedule, household settlement, inventory movement, offer lifecycle, demand update, safety-routing, passport and anomaly paths are typed and available; several still need dedicated detail/action destinations and are labelled `ANDROID PARTIAL` in the matrix.
- Admin endpoints are typed and capability-gated at the service boundary but no Admin role is exposed by the current `AccountRole`/navigation model. They are `ANDROID IMPLEMENTATION REQUIRED`.
- `/recycler/bulk-lots/:lotId/receive` remains a `COMPATIBILITY ONLY` backend guard and is not called by the normal UI. Receipt uses signed formal handover confirmation.

## Offline and idempotency

- Room `SyncQueueItemEntity` and WorkManager remain the single offline queue.
- Household pickup creation queues the exact listing/collector payload and persisted key on transient connectivity/server failure.
- Recycler formal handover confirmation queues the signed QR reference, weights, material-match decision, reason and persisted key.
- The same UUID key is reused across process death and retry. Confirmed server response is required before inventory, settlement or handover completion is shown as successful.
- Cached formalisation evidence is account-scoped and visibly labelled. A future full sync-status screen and richer conflict reducer remain `ANDROID PARTIAL`.

## Security and contract handling

- Existing bearer interceptor and single-flight authenticator were preserved.
- Tokens, OTPs, passwords, QR payloads and evidence are not logged by the added code.
- Release base URL remains HTTPS-only; debug/staging/release base URL configuration remains build-type controlled.
- Retrofit responses continue through the centralized `ApiEnvelope`/`requireData` parser and `RemoteApiException` mapping for 400/401/403/404/409/422/503 classes.
- Role checks are local navigation guards only; ownership is still delegated to backend identity rules.

## Verification

| Check | Result |
|---|---|
| Debug unit tests | PASS — `testDebugUnitTest`, 28 actionable tasks |
| Android lint | PASS — `lint` |
| Debug APK | PASS — `assembleDebug` |
| Release APK | PASS — `assembleRelease` |
| Instrumentation tests | Not run — no configured emulator/device in this environment |
| Screenshot/accessibility/performance benchmarks | Not run — device/emulator harness not available |

The build emits only existing/deprecation warnings (status-bar Java API, Kotlin annotation target, `VolumeUp` icon) and the existing debug minification warning. They do not block the artifacts.

## Acceptance journey status

1. Household listing → Kabadiwala → pickup → settlement → passport: `ANDROID PARTIAL`; primary listing/pickup path exists, dedicated settlement/passport detail actions remain.
2. Kabadiwala pickup → inventory → pool → formal handover: `PARTIAL`; the existing formalisation dashboard and QR flow are connected, while some lifecycle controls remain service/VM-only.
3. Recycler demand → offer → QR scan → QC/settlement: `PARTIAL`; demand/offer/scan are connected, formal QC reason/evidence and settlement detail need a dedicated screen pass.
4. Offline duplicate prevention: `IMPLEMENTED` for pickup and formal handover paths covered by the new queue/key changes; broader legacy mutations retain their existing queue behavior.
5. Settlement differences and anomaly review: `PARTIAL`; typed fields and anomaly endpoint are present, detail presentation remains.
6. Wrong role/non-owned data: `IMPLEMENTED` at navigation guard/cache scoping plus backend ownership enforcement; Admin navigation remains withheld.
7. English/Hindi, large text, TalkBack, light/dark: `ANDROID PARTIAL`; dark/light and scalable Compose patterns exist, Hindi completion and device-level TalkBack validation remain.
8. Slow network/empty/error/offline: `IMPLEMENTED` for shared loading/error/cached evidence patterns; detailed per-screen skeleton refinement remains.

## Remaining limitations

- `ANDROID PARTIAL`: dedicated detail destinations for household listing/pickup passports, rescheduling, household settlement, handover passport and anomaly explanation.
- `ANDROID PARTIAL`: full recycler offer withdrawal/counter/reject and procurement lifecycle controls in visible cards.
- `ANDROID PARTIAL`: Admin role/account model, capability-denied screens and admin review workflows.
- `ANDROID PARTIAL`: complete Hindi resource migration; several pre-existing supply-chain labels remain literals.
- `ANDROID PARTIAL`: screenshot, accessibility, rotation/process-death and performance benchmark execution on a real device.
- `EXTERNAL SERVICE REQUIRED`: production image/object storage, maps/navigation and camera/QR hardware permissions require configured services/devices.
- `BACKEND CHANGE REQUIRED`: none discovered or required for this Android pass. The backend contract already exposes the missing fields/routes used here.
