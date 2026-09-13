# Kabadiwala Connect — Progress

> This file is a historical implementation log. The entries below describe
> the milestones when they were completed and may mention earlier placeholders
> or mock boundaries. For the current production-readiness baseline, use the
> root README and the latest audit update below.

## Current production-readiness baseline — 2026-09-12

- Android and backend builds, tests, lint, and the release compilation pass.
- Collector lot, quote, handover, payment, recycler discovery, and offline
  sync flows are connected to the backend with validation, retry/error states,
  idempotency, and Room persistence.
- Android now consumes server change deltas with an encrypted cursor, preserves
  unsynced local rows, shows queued work in Settings, and uploads handover scale
  evidence to private backend storage with retry support.
- Quote rejection reopens the request lifecycle for rematching; an in-app
  notification inbox is populated by quote, handover, payment, dispute, and
  verification events.
- Household preview screens disclose sample data and simulated interactions so
  they cannot be mistaken for a live backend role.
- Recycler profile and material-rate editing are backed by authenticated API
  endpoints; Copper is supported consistently across the schema, APIs, and UI.
- Debug-only authentication, sample price fixtures, and the `.invalid` API
  default are intentionally isolated from production builds. Supply a real
  HTTPS endpoint and production providers before shipping.

## Roadmap integration pass — 2026-09-13

- Added a persisted household role with role-preserving email/OTP sessions.
- Completed payment settlement by moving a confirmed handover to `COMPLETED`
  in the same backend transaction as the payment record.
- Preserved lot provenance, waste regime, original weight/unit, image quality,
  price source, freshness, and trend metadata across the API and Room cache.
- Added account-scoped local reads, durable retry metadata, notification read
  replay, and a visible sync center; logout clears account-owned local data.
- Added privacy-safe recycler/quote views, persistent scoped admin login and
  permissions, pseudonymized dataset export, and the transaction passport UI.
- Added a real multi-recycler quote request path with an offline fallback and
  updated OpenAPI for the new auth, provenance, batch quote, and timeline APIs.

The remaining production work is environment-dependent or intentionally later:
FCM push delivery, an admin web console, pickup scheduling/capacity, payment
provider integration, signed release distribution, and device validation for
camera, GPS, QR, TalkBack, large-font, and real network-loss scenarios.

## Final audit update — 2026-09-04

- Fixed the minSdk 23 connectivity callback path.
- Fixed Compose configuration-aware resource reads.
- Added missing Hindi and Marathi translations for handover, payment, earnings, and safety strings.
- `./gradlew.bat lint` now passes; remaining output is non-blocking deprecation/dependency guidance.
- `./gradlew.bat testDebugUnitTest` and `./gradlew.bat assembleRelease` pass.
- Android now supports a configured backend auth path, queued collector mutations,
  encrypted-cursor delta reconciliation, and server-backed catalogue/earnings
  refresh. See the root `ISSUES.md` for environment-only deployment work.

## Phase 10 — Configurable backend transport & sync

Status: transport complete; live service verification pending

### Completed

- Added typed, versioned Retrofit DTOs for the backend API surface with explicit local-to-server boundaries.
- Added build-time `-PapiBaseUrl=...` configuration and bearer-token injection from encrypted storage.
- Added remote OTP verification, JWT expiry parsing, server collector IDs, profile updates, and network-safe onboarding errors.
- Added queued local lot/payment operations and a connectivity-constrained idempotent sync worker with terminal-result handling.

### Known limitations

- Price, recycler, quote, handover, payment-ledger, and lot-list screens still render Room cache data; feature-specific remote refresh/mappers should be added before release.
- A live backend/database/device run was not available in this environment.

## Phase 9 — Safety, accessibility & final collector UX

Status: complete

### Completed

- Added an offline safety directory for CRT, batteries, hazardous motors/components, transformers, capacitors, and other hazardous materials.
- Added filter chips, Tip of the Day, session bookmarks, large warning visual, DO/DO NOT guidance, Hindi/Marathi reminder text, and Android TTS playback.
- Hazardous material selection in lot creation now exposes safety guidance before continuing.
- Preserved large touch targets, high-contrast status colors with text/icon pairing, scalable Compose text, screen-reader labels on key visuals/actions, system dark mode, and no rapid animation.
- Added local loading/empty/offline/error/success/syncing patterns across the existing feature screens and retained offline-first Room repositories.
- Audited the complete local journey: authentication → home → lot → valuation → recycler → quote → handover → payment → earnings.
- Kept image display local and bounded, Room queries reactive, and all future server work behind repository/sync boundaries.

### Tests performed

- `./gradlew testDebugUnitTest assembleDebug` — passed after Phase 9 changes.
- Static navigation and route audit completed for all primary and secondary destinations.
- Device/emulator online/offline run was not available because `adb` is not installed in this environment.

### Known issues

- Safety bookmarks are session-local UI state; persistent bookmarks can be added with a later settings/storage refinement.
- Some legacy screens still use compact one-line Compose declarations and null icon descriptions in non-critical decorative contexts; functional labels and major actions remain accessible.

### Explicitly deferred

- Recycler website, admin dashboard, backend production system, ML classification, live sync, dispute server, and payment integrations.

## Phase 8 — Payment tracking & earnings

Status: complete

### Completed

- Added local Room-backed payment entity/repository with migration 7→8.
- Added payment recording screen with lot selection, amount validation, payment method, notes, current date/time, and confirmation dialog.
- Added Cash, Bank Transfer, and Digital Wallet methods; records are marked `WAITING_TO_SYNC` for future backend delivery.
- Payment confirmation updates the related lot to `PAID` and stores the final amount locally.
- Earnings now derive from local payments: total, pending foundation, current month, average lot value, transaction list component, and a simple monthly chart.
- Added conflict-ready `ALREADY_RECORDED` and `DISCREPANCY` payment states without server dispute behavior.

### Tests performed

- `./gradlew testDebugUnitTest assembleDebug` — passed after Phase 8 changes.

### Known issues

- Date/time currently defaults to the current device time; a full date/time picker is deferred.
- Payment transaction detail navigation is prepared through the reusable list component but not exposed as a separate tab route yet.

### Explicitly deferred

- Payment gateways, banking APIs, server reconciliation, dispute resolution, and real synchronization.

## Phase 7 — Handover & traceability

Status: complete

### Completed

- Added local Room-backed handover repository and schema migration 6→7.
- Added handover creation after quote acceptance with collector, recycler, and third-party location choices plus timestamp review.
- Added `HOV-yyyyMMdd-random` reference generation and local `SAVED_LOCALLY` / `HANDED_OVER` status.
- Added document-style handover screen with photo, metadata, value, both locations, timestamp, reference, QR code, and share/SMS actions.
- Added replaceable repository boundary and local-only storage; no backend synchronization was implemented.

### Tests performed

- `./gradlew testDebugUnitTest assembleDebug` — passed after Phase 7 changes.
- Added focused handover reference format test.

### Known issues

- Print uses Android share support where available; a dedicated print adapter is deferred.
- Missing lot photos do not block handover record creation.

### Explicitly deferred

- Backend synchronization, server verification, live QR scanning, digital signatures, and payment integration.

## Phase 1 — Android project foundation

Status: complete

### Completed

- Created the Kotlin Android application `com.irinteractivestudios.kabadiwalaconnect`.
- Added debug and release build configuration with a low minimum SDK (23) for entry-level devices.
- Established a single-activity, Compose UI with MVVM-style ViewModels, repositories, a manual app container, and a navigation graph.
- Added the primary navigation tabs: Home, Prices, Recyclers, Earnings, and Settings.
- Added Settings-linked Safety and Help placeholder screens.
- Added the initial high-contrast visual system: large type, large touch targets, light surfaces, green success/primary actions, and explicit offline status.
- Added English, Hindi, and Marathi string resources for all Phase 1 screens. User-facing UI copy is resource-backed rather than hard-coded in Kotlin.
- Added the Room database foundation with a sync queue table and a test seam. Future domain contracts exist for collector profiles, lots, prices, recyclers, quotes, handovers, and payments; their feature tables are intentionally deferred.
- Added Retrofit/OkHttp API seams without making network calls or fabricating backend responses.
- Added Android Keystore-backed secure storage for future sensitive credentials.
- Added WorkManager-based deferred-sync structure without implementing synchronization.
- Added permission decision/rationale infrastructure for camera and location. No permission is requested at launch.
- Added connectivity observation for online, offline, and limited/API-unreachable states, plus reusable loading, offline, empty, error, success, and syncing UI states.

### Tests performed

- `./gradlew testDebugUnitTest assembleDebug` — passed.
- Unit coverage includes navigation destinations, connection-state resolution, locale normalization, permission decisions, Room/data foundation seams, and ViewModel state transitions.
- Compose instrumentation coverage includes bottom navigation semantics and app package identity. Instrumentation tests require an attached Android device/emulator to execute.

### Known issues

- Instrumentation tests were not executed in this environment because no Android device/emulator was attached.
- The Room database currently contains only the sync queue table; feature entities and migrations belong to their later phases.
- The API layer has no production base URL or endpoints yet.
- The Phase 1 screens intentionally show empty/local placeholder states and do not implement authentication, lot creation, prices, recycler discovery, quotes, handovers, payments, or analytics.

### Explicitly deferred

- Authentication and collector onboarding.
- Lot creation, camera capture, categorization, condition, weight, and location capture.
- Live/cached price feeds and estimated lot values.
- Recycler discovery, quote requests/comparison, and handover generation.
- Payment integrations and advanced earnings analytics.
- Backend production services, ML classification, chat/messaging, and admin/recycler interfaces.

## Phase 2 — Collector onboarding & authentication

Status: complete

### Completed

- Added first-launch onboarding: Welcome, Indian mobile number, OTP, language, location choice, primary area, completion, and Home.
- Added returning-collector routing: valid local sessions open Home; expired or missing sessions open onboarding.
- Added Indian mobile validation with `^[6-9]\\d{9}$`.
- Added a replaceable `AuthenticationRepository` and `OtpService` boundary with a development-only mock OTP implementation.
- Added six-digit OTP input, focus-on-entry, incorrect/expired/attempt-limit states, resend cooldown, expiry timing, and development OTP hint.
- Added local Room collector profile storage for collector ID, phone, language, primary area, location source, creation time, and last login; included a Room 1→2 migration.
- Added secure session token and expiry storage through the Android Keystore-backed storage abstraction.
- Added logout and offline-valid-session checks. No network call or real OTP backend was added.
- Added GPS permission request only from the location step, with manual area entry as the fallback after denial or user choice.
- Added Hindi and Marathi translations for all onboarding/authentication strings.

### Tests performed

- `./gradlew testDebugUnitTest assembleDebug` — passed.
- Added unit coverage for Indian phone validation, OTP success/incorrect/expired/attempt-limit behavior, session expiry/logout, and manual-location fallback.

### Known issues

- Instrumentation tests still require an attached Android device/emulator; no `adb` executable/device is available in this environment.
- OTP is intentionally a development mock. The visible development code is `123456` and must be removed when the real backend is connected.

### Explicitly deferred

- Real OTP delivery/backend authentication.
- Lot management, camera capture, material categorization, weight, collection history, and all post-onboarding collector workflows.

## Phase 3 — Lot management

Status: complete

### Completed

- Added an offline-first seven-step lot flow: photo, material, condition, weight, location, review, and save confirmation.
- Added camera capture through `FileProvider`, app-private local photo storage, preview, retake, confirmation, and validation seams.
- Added photo validation for existence, minimum 320×240 dimensions, and maximum 8 MB file size; the structure is ready for later compression.
- Added icon-first material choices for CRT, LCD Panel, PCB/Circuit Board, Cables, Battery, Motor, Magnet, Plastic, and Other, with hazard indicators.
- Added Intact, Damaged, and Partial condition choices.
- Added decimal kilogram entry, slider alternative, and validation for values greater than 0 and less than 500 kg.
- Added GPS permission request only from the lot location step, manual area fallback, and location preview.
- Added full local Room lot schema with generated `LOT-{timestamp}-{randomID}` IDs, collector linkage, photo path, future server/value placeholders, location, notes, timestamps, and status.
- Added My Lots list and Lot Details screens with thumbnail/metadata, saved status, timeline foundation, and cancellation for saved lots.
- Added Room 2→3 migration and repository writer/read boundaries. No synchronization or server calls were added.

### Tests performed

- `./gradlew testDebugUnitTest assembleDebug` — passed after Phase 3 changes.
- Added unit coverage for missing-photo validation, weight bounds, local lot creation, ID generation, collector linkage, and manual location.

### Known issues

- Camera and instrumentation behavior require an attached Android device/emulator for manual verification; no `adb` executable/device is available in this environment.
- The development app currently stores the material key in English for stable local data; the displayed selection labels are localized.
- Lot cancellation updates local status only. Server synchronization is intentionally deferred.

### Explicitly deferred

- Compression implementation, server photo upload, estimated values, quotes, final values, and synchronization.
- Lot editing beyond the local draft flow, recycler handover, payments, and analytics.

## Phase 4 — Price board & auto-valuation

Status: complete

### Completed

- Added Room-backed local price storage with deterministic mock data for Pune and Mumbai.
- Added location selector, material tabs, large current rate, typical range, cached/offline indicator, last-updated date, trend indicator, six-point 30-day trend view, and recycler-rate placeholder.
- Added replaceable `PriceCatalogRepository` boundary for future API data.
- Added reusable Android TTS price action with Hindi and Marathi locale support plus a recording test fake.
- Added valuation formula with condition multipliers: Intact 1.0, Damaged 0.7, Partial 0.4, and quality adjustment support.
- Integrated estimated value into lot review, saved lot records, My Lots, and Lot Details, using local mock prices and current lot weight/condition.
- Added Room 3→4 migration for cached prices and updated localized English/Hindi/Marathi copy.

### Tests performed

- `./gradlew testDebugUnitTest assembleDebug` — passed after the final Phase 4 changes.
- Added tests for valuation math, all condition multipliers, and Hindi/Marathi TTS requests.

### Known issues

- Mock price data is local development data, not a backend quote; the recycler-rate section is intentionally a placeholder.
- The historical view uses six stored points as a lightweight 30-day trend summary rather than a chart library.
- TTS behavior requires an Android device/emulator with a Hindi/Marathi voice installed for manual audio verification.

### Explicitly deferred

- Live price APIs, server refresh/sync, recycler-specific rates, and production price provenance.

## Phase 6 — Quote workflow

Status: complete

### Completed

- Added Room-backed local quote repository with a replaceable `QuoteRepository` boundary.
- Added request screen with lot/recycler selection, lot summary, recycler summary, estimated value, confirmation, and local mock submission.
- Added multiple mock quotes per request with price/kg, total, market comparison, distance, pickup, status, delivery state, and 24-hour expiry.
- Added quote comparison with actual best-price highlighting and accept/reject controls.
- Added local acceptance transition to `COLLECTOR_CONFIRMED` on the lot; rejection leaves alternative offers visible.
- Added lifecycle states Draft, Pending, Accepted, Rejected, Expired and delivery states Saved Locally, Waiting to Send, Sent, Response Received.
- Added Room 5→6 migration. No recycler backend or synchronization call was added.
- Added request entry from recycler discovery and comparison navigation.

### Tests performed

- `./gradlew testDebugUnitTest assembleDebug` — passed after Phase 6 changes.
- Added tests for quote lifecycle states, exact 24-hour expiry, and best-offer selection by actual price.

### Known issues

- The mock submission returns local response offers immediately while retaining delivery-state modeling for future queued/server flows.
- Contact/map actions remain placeholders from Phase 5.

### Explicitly deferred

- Real quote requests, recycler responses, server delivery, notifications, chat, handover, and payments.

## Phase 5 — Recycler discovery

Status: complete

### Completed

- Added Room-backed local recycler cache with deterministic mock facilities, rates, accepted materials, pickup availability, authorization, addresses, hours, handover times, contact details, and map coordinates.
- Added recycler list cards showing name, distance, authorization, rate, pickup status, and local matching score.
- Added search by name/area, 5/10/25/50 km radius filters, material filter, pickup-only filter, and proximity/rate sorting.
- Added recycler details with facility, address, authorization, accepted materials, rate, operating hours, handover time, map-coordinate placeholder, and contact/map actions.
- Added local MVP matching score across material match, distance, pickup availability, rate, and verified status.
- Added offline cached recycler rendering and empty-result handling.
- Added Room 4→5 migration and discovery routes without creating any recycler website or backend calls.
- Added focused tests for search, filters, sorting, empty results, matching, and mock recycler data.

### Tests performed

- `./gradlew testDebugUnitTest assembleDebug` — passed after Phase 5 changes.
- Discovery tests cover name/area search, radius/material/pickup filtering, rate sorting, matching dimensions, and empty results.

### Known issues

- Recycler data is local development data; contact/map buttons are placeholders until production integrations exist.
- The location displayed in the list is mock distance data; no precise location history is collected.
- Instrumentation/manual map and phone-call verification requires an attached Android device/emulator.

### Explicitly deferred

- Recycler website, backend discovery API, live authorization verification, quote requests, chat, handover, and payment workflows.
# Android-first role routing update — 2026-09-05

The app now uses email-based account onboarding with explicit collector/recycler selection, caches the backend role and recycler verification state in encrypted storage, and routes verified recyclers to a dedicated Android operations navigation. Collector flows remain offline-first through Room and WorkManager. The old phone/OTP classes stay as a compatibility boundary for older deployments.
