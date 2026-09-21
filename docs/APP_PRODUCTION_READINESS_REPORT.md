# Kabadiwala Connect — Production Readiness Report

Date: 2026-09-21
Scope: Android `envTesting` build, backend TypeScript/Prisma application, local static audit, and emulator smoke/regression checks.

## Final verdict

**NOT READY for an unconditional production launch.**

The P0 authentication gate, environment separation, GPS lot persistence defect,
Gemini failure semantics, and several eager-request paths were corrected. The
repository builds and the automated suites pass. The testing deployment has
also passed disposable-account API smoke tests for authentication, role
boundaries, waiting pickup, and the core Household → Kabadiwala → Recycler
supply-chain path. Production still requires real, separately provisioned
services and credentials, a signed release build, and remaining device-level
and adversarial gates. Those items are deployment and test-environment
dependencies, not reasons to hide failures in the client.

## Issues found

- A cached account could make the app appear restorable before the session had
  been validated. The activity and protected navigation could then construct
  role screens while the auth flow was still resolving.
- Several role ViewModels performed protected calls in `init`, making their
  network behavior dependent on construction timing rather than an explicit
  authenticated route entry.
- A restored navigation stack could still briefly compose the live Kabadiwala
  route before the navigation guard redirected to auth. The role-only check in
  `SupplyChainViewModel` treated the cached role as sufficient and could send
  `GET /kabadiwala/listings` without a bearer token.
- Email authentication mapped a backend `401 Email or password is incorrect`
  response to the generic connectivity message, misleading users and making a
  credential failure look like an outage.
- The persisted-session check accepted any non-empty token with a future local
  expiry. A corrupted opaque value could therefore be treated as authenticated
  on a real-backend build until the server rejected it.
- Debug/demo state was stored with `rememberSaveable`, allowing a restored
  Android task snapshot to resurrect a prior fixture role after force-stop or
  data-clear testing instead of returning to the unauthenticated entry flow.
- Lot GPS permission previously stored the UI status string
  `Approximate GPS location saved` as the area name, never requested a real
  location, persisted `MANUAL`, and emitted a sync payload with `MANUAL`.
- Android accepted images up to 8 MB while the backend accepted 5 MB. Android
  and backend both converted detection/provider failure into a fake successful
  `OTHER` suggestion.
- Testing and production endpoints were not represented by one explicit Android
  environment strategy, and backend examples did not require an explicit
  `APP_ENV`.
- The returning-user email sign-in path always sent `COLLECTOR` in the Android
  request model, even when the account being authenticated was Household or
  Recycler. The backend correctly ignored the extra login fields, but this was
  an unsafe client contract and could break stricter deployments.
- Returning phone sign-in omitted registration details entirely. The backend's
  legacy no-input branch then issued a collector identity even for an existing
  Household account. A live ADB check with the seeded Household phone exposed
  this as a real role-boundary defect.
- A rotated refresh token can be rejected with `TOKEN_INVALID` / `Refresh token
  was already used` when a stale token is replayed by another process, device,
  or delayed request. This is expected single-use rotation protection, but the
  client previously needed an explicit stale-session cleanup path.
- The Household listing response could contain nullable `photoAttached` and
  `photoCount` values and private photo-storage references. Android treated an
  already-created offline listing as an extended-operation rejection during
  replay instead of reconciling the server state. The OTP request path also
  logged `otp_requested` before rate limiting, and the shared database limiter
  did not apply the same resend cooldown as the in-memory limiter.
- The legacy lot-creation idempotency path returned a prior lot for a reused
  key without comparing the new payload. A retry with a different payload
  could therefore be acknowledged as if it were the original operation.
- Several Recycler and operator UI ViewModels passed raw exception messages
  directly to Compose state, which could expose backend/provider wording to
  normal users instead of a useful recovery message.
- Several Room repositories used an unscoped fallback when the active account
  was temporarily absent. That was unsafe during expiry, logout, or account
  switching because stale legacy rows could be read or mutated outside an
  authenticated account boundary.
- The Household scrap flow was implemented as an `AlertDialog` around a single
  `GetContent()` picker, which made it feel like a picture-in-picture prompt
  and prevented multi-angle selection. The Kabadiwala lot step also retained
  only one local photo, and both UIs exposed backend-oriented material names
  rather than everyday descriptions.

## Authentication fix

The startup path now has explicit `RESTORING`, `UNAUTHENTICATED`,
`AUTHENTICATED`, and `EXPIRED` states. Startup restores/refreshes the encrypted
session first and only exposes protected navigation after a valid token and an
account profile are available. The activity refresh loop and protected effects
are gated by that state. A stale cached profile alone is no longer enough to
start authenticated work.

Role ViewModels that previously loaded protected data in constructors now load
from route-scoped `LaunchedEffect` calls after the protected route is entered.
The app also clears pending photo uploads with the rest of account state on
logout/account cleanup. Token refresh remains centralized in the API/session
layer, and an invalid restoration falls back to the auth surface.

The final continuation closed a second startup race found by source review:
`Application.onCreate()` no longer arms WorkManager or push-device registration
from cached credentials. A process-local authenticated-background-work gate is
opened only after session restoration or fresh sign-in has produced the current
account and a valid access token; logout and expiry revoke it. FCM token
callbacks may still cache a token while signed out, but cannot send it to a
protected endpoint until that gate opens. Durable sync is re-armed after the
authenticated bootstrap completes. The explicit state machine is now shared by
the app container and UI through `SessionCoordinator`, with a unit regression
covering all four transitions and ensuring expired/unauthenticated states carry
no account snapshot. `SyncWorker` checks the same gate before it can refresh a
token or replay a protected mutation.

The latest continuation added a defense in depth for the remaining restored
navigation race. Protected SupplyChain refreshes and mutations now require the
process-local authenticated-session gate in addition to the role check, and the
Retrofit interceptor blocks any protected request with no access token before
OkHttp can contact the backend. Public login, signup, OTP, refresh, and logout
endpoints remain available without an access token. This closes the specific
no-token `/kabadiwala/listings` path rather than hiding its 401 response.

The session repository now also applies an environment-aware token-shape
validator: configured real backends require a three-part JWT before a cached
session can open protected work, while the local `.invalid` debug repository
may continue to use opaque mock tokens. A malformed-token regression test
proves that a future timestamp alone cannot unlock a remote session. Debug
fixture mode is process-local and explicit-intent driven; it is no longer
restored from an old Activity/task snapshot after force-stop or data clearing.

ADB evidence from a cleared install and a force-stop/restart showed no
app-owned `401`, `AUTHENTICATION_REQUIRED`, bearer-token, Retrofit/OkHttp, or
crash signatures before authentication. A direct unauthenticated request to
the testing backend correctly returned `401 AUTHENTICATION_REQUIRED`.
The returning-user email path now passes the selected role through the shared
request model, and debug builds log only exception type, stable error code, and
HTTP status when authentication fails; credentials, tokens, and response
payloads are never logged. Returning phone sign-in now passes an empty typed
account object through the HTTP layer and the database-backed service resolves
the existing `User` row before any legacy collector fallback. The response
contract and service tests verify that an existing Household role is preserved.
The rebuilt Android client trusts the returned server profile role, and the
testing VPS was revalidated after deployment with the seeded Household phone.
Both OTP verification and the authenticated profile endpoint now return
`HOUSEHOLD`.

Email auth errors are now typed in the UI: a backend 401 renders
`Email or password is incorrect.`, while transport failures retain the
connectivity message. A fresh testing APK wrong-credential ADB check produced
the expected 401 in logcat and the new credential-specific UI text; it did not
show a false network-outage message.

Refresh rotation remains single-use on the backend. Android now serializes
refresh attempts, and a failed stale/replayed refresh clears credentials and
account metadata, revokes authenticated background work, and returns to the
auth surface while retaining durable account-scoped outbox data for a later
authenticated recovery. The app does not bypass refresh-token replay
protection. OTP `429 OTP_RATE_LIMITED` and `OTP_COOLDOWN` responses now render a
specific wait-and-retry message instead of the generic connectivity error.
The backend logs `otp_sent` only after the provider succeeds, returns a safe
`Retry-After` value, and applies the 30-second resend cooldown in both memory
and shared-database limiter modes. Android claims the busy state
synchronously, so rapid taps cannot launch duplicate OTP requests.

## Environment and secret handling

Android now has the `envTesting` and `production` product flavors. Testing uses
`-PtestingApiBaseUrl`; production requires an explicit HTTPS
`-PproductionApiBaseUrl`, and release signing properties are supplied outside
the repository. Generated `BuildConfig` values are the only runtime endpoint
source.

Backend configuration now validates `APP_ENV=testing|production`, rejects a
production/development mismatch, and keeps production credentials backend-only.
Tracked examples contain placeholders only. The Prisma development seed is now
hard-stopped unless `APP_ENV=testing`, so it cannot populate production with
demo users, prices, or sample lots. Any previously exposed real production
secret must be rotated outside this change. Production startup now also fails
fast when the Gemini key or model is missing/placeholder; testing may leave
Gemini unset and will surface the typed service-unavailable/manual-fallback
state instead. The production example now marks both Gemini values as required
deployment substitutions rather than presenting a copyable provider model.
The environment audit found no tracked Gemini keys, Mongo credentials, OTP
provider secrets, bearer tokens, or production endpoint literals. The current
testing URL is an explicit Gradle property for the testing flavor; production
still requires a separately supplied HTTPS property at build time.

## GPS lot creation fix

Lot draft, domain, Room, sync payload, and remote mapping now carry latitude,
longitude, area name, and precision separately. Permission success invokes the
real `AndroidLocationProvider` and reverse geocodes when possible. Editing the
human-readable label preserves captured coordinates and `GPS` precision.
Manual text remains `MANUAL`; the helper/status message is never written into
`areaName`.

Automated coverage now verifies GPS coordinates survive label edits and are
saved with `GPS` precision. The testing build includes the GPS migration
(`23 → 24`) for the coordinate columns, plus later photo durability migrations
(`25 → 26` for multiple pending-upload paths and `26 → 27` for offline lot
photo paths).

## Gemini image detection

The backend material endpoint now allows both Household and Kabadiwala roles,
validates the JPEG/PNG/WebP multipart boundary, validates the structured
category/confidence response, and returns a typed service-unavailable error if
Gemini is unavailable. It no longer returns a fake template success for a
provider outage.

Android detection now has explicit processing, success, low-confidence,
unsupported-image, network-error, and service-error states. The shared image
preparation path decodes and bounds dimensions, applies EXIF orientation, and
compresses to a bounded JPEG before multipart upload. The manual material
picker is always available, and raw provider/API payloads are not shown. The
client-side file limit is aligned to the backend 5 MB limit.

The same normalization is now applied before final Household listing and
Kabadiwala lot photo persistence, not only during detection. Gallery URIs and
camera captures are copied into app-private storage, converted through the
shared pipeline, and only then retained for immediate upload or offline sync.

The same pipeline is now connected to the dedicated Household listing screen.
Selecting the first image starts a typed detection request, a confident result
preselects the material for review, and low-confidence/provider/network
failures leave the manual material choices available. A household listing is
never created from an unvalidated provider response.

The feature still depends on a valid backend Gemini key and a model enabled for
each deployment. The ignored local deployment file uses a model value that was
not provider-validated during this sprint; production must use the approved
model value from the sanitized examples and exercise the endpoint before launch.
After the backend upload, the live testing endpoint was rechecked with a
disposable Collector session: a correctly formed `curl -F
photo=@...;type=image/png` request returned HTTP 200 with a structured
`OTHER` result at confidence `0.2` and a safe user message. That is evidence
that authentication, multipart handling, provider invocation, and response
parsing are live; it is intentionally treated as low confidence rather than a
successful material identification. The earlier 422 probe was caused by a
malformed multipart file part and correctly rejected the request before provider
invocation. The new `materialSuggestion.test.ts` regression coverage locks in
missing-photo validation, valid multipart handling, provider-unavailable
fallback, server-side key usage, MIME/base64 forwarding, and constrained
response parsing. Android was exercised with a real gallery-selected image:
Photo Picker URI → local normalization/compression → multipart request → typed
service error. The UI kept manual material selection available, showed no raw
provider JSON, and did not create a fake detection.

After the backend upload, the same Android flow was repeated against the live
VPS testing deployment: fresh Collector OTP sign-in → dedicated `Record lot`
screen → Photo Picker selection → `Identify from photo`. The app rendered the
structured low-confidence result (`Other scrap / not sure`, 10%) and the safe
manual-selection fallback copy. This validates the client-to-VPS request and
fallback path; it does not establish production scrap-photo accuracy because
the disposable image was intentionally a non-scrap UI image.

The Household client was then exercised against the same VPS with a fresh OTP
session: `Sell your scrap` → two Photo Picker selections → two independent
`Remove photo` controls → live detection. The result stayed low confidence and
the screen kept the plain-language manual categories available, with no raw
Gemini response or HTTP error shown.

## UI/UX redesign

- Applied the requested “Graphite Neon Room” system to the shared Compose
  theme: background `#0B0E0C`, surface `#141915`, raised surface `#1D251F`,
  border `#2A352D`, primary text `#FFFFFF`, muted text `#AEB9AF`, action
  `#C9FF3A`, and error `#FF6B6B`.
- Added shared spacing and radius roles for the 4/8/12/16/20/24/32dp rhythm,
  compact typography hierarchy with tabular numerals, bordered graphite
  surfaces, lime-only primary emphasis, reusable skeleton/state styling, and
  minimum 48dp interaction targets.
- Removed the remaining dashboard/background gradients from the primary
  household, Kabadiwala, recycler, and legacy home surfaces. The hierarchy now
  comes from tonal surfaces, borders, spacing, and one focal action per screen.
- Improved bottom navigation distribution with role-correct 4–5 destination
  structures, single-line ellipsized labels, accessible icon descriptions,
  restrained 140ms selection motion, safe navigation-bar padding, and a clear
  lime active state.
- Confirmed dark mode remains the default while persisted Dark/Light/System
  selection remains available under Settings → Display. Testing labels remain
  confined to the debug/testing build.
- Light-mode contrast was corrected on 2026-09-21. The neon lime token is no
  longer reused as Material 3 `primary` text on a white canvas; light mode now
  uses an accessible dark-green primary token (measured at approximately 7:1
  against white), while dark mode keeps the lime action treatment. ADB visual
  QA on `Pixel_10_Pro(AVD) - 17` confirmed readable light onboarding,
  selected-state accents, and primary/secondary actions.
- Removed the visible operator sign-in, Pune demo, and role-demo controls from
  the onboarding welcome surface. Debug/instrumentation preview remains
  available only through the explicit test intent, so the customer path no
  longer presents internal tooling as onboarding choices.
- Removed synthetic Recycler catalog seeding from real `envTesting` builds;
  bundled facilities are now limited to the explicit `.invalid` offline
  preview backend. A clean rebuilt testing APK authenticated against the VPS
  showed server-provided facility records rather than bundled sample names.
- Unified the welcome primary and secondary button corners with the shared
  50%-radius pill shape so both actions have fully curved ends.
- Refined the welcome hero after emulator visual QA: the previous bright
  green block is now a graphite `surfaceContainer` panel with a restrained
  border, asymmetric hero corners, a smaller brand mark, and a tighter
  headline/body hierarchy. This keeps the welcome message readable on narrow
  phones while reserving lime for the primary action.
- Replaced the cramped one-photo Household listing dialog with a dedicated,
  scrollable listing screen. It supports up to six images from different
  angles, local preview/removal, Photo Picker multi-select, manual material
  selection, and a single clear primary action. The Kabadiwala lot photo step
  now uses the same six-photo limit with angle previews and removal.
- Replaced technical material keys with everyday labels and examples, such as
  `Plastic bottles & containers`, `Wires & cables`, `Old TV / monitor`, and
  `Circuit boards & computer parts`. `Other scrap / not sure` remains an
  explicit safe fallback, and the AI suggestion is always presented as a
  suggestion that the user can change.
- Follow-up emulator visual QA removed the duplicate inline `Back` action from
  the Household form. The shared app bar now owns the only back affordance and
  correctly titles the route `Sell scrap`; a fresh ADB screenshot confirmed the
  single-arrow hierarchy. The testing marker remains visible only in the
  testing flavor.
- Latest light-mode pass: the light palette now uses a contrast-safe tertiary
  green for inline status text and icons instead of the pale mint used by the
  dark palette. System status and navigation bars now switch their icon
  appearance with the selected theme. Unit contrast coverage was extended for
  the tertiary token, and an ADB emulator check confirmed readable Settings
  content, selected Light state, navigation labels, and the testing marker.
- Preserved the existing role-specific navigation boundaries and manual/error
  states while avoiding new fake content. A dedicated physical-device,
  TalkBack, large-font, and low-end performance pass remains a release gate.

The no-partner path now has a durable `WAITING_FOR_PICKUP` state. A household
can notify nearby collectors when the current radius has no match; matching
collectors receive an in-app notification and see the waiting request in their
pickup queue, where an atomic claim assigns ownership before acceptance.
The Android flow exposes progressive 5 km → 10 km → 20 km discovery controls
before the household chooses notification/waiting.

The full application still needs a dedicated visual QA pass on every role
screen, including localized strings, TalkBack, large font scales, and low-end
device performance. This is not claimed as complete from the emulator smoke
run alone.

## Security and authorization

The backend route audit confirms separate Household, Kabadiwala/Collector,
Recycler, and Admin middleware, ownership filters for supply-chain resources,
current Recycler authorization checks, and idempotency/conditional-update
guards for important mutations. Existing authorization tests cover cross-role
and ownership boundaries; the full backend suite passed. Legacy collector and
household middleware now also rejects a token whose profile has no matching
User linkage, rather than treating the missing linkage as acceptable.
The generic account middleware now also rechecks the linked User account status,
so a stale active profile cannot keep a suspended or deleted account authorized.

The Android cache boundary was tightened during the final pass: cached
handovers are now filtered by the current account as collector or recycler,
queued handover replay resolves only an account-owned local row, payment delta
reconciliation uses an account-scoped lookup, and conversation cache reads
match either participant. A regression test proves a foreign cached handover
does not appear through the active account's repository. The subsequent
hardening pass also added owner predicates to offline lot mutations, quote
reads/status changes, handover evidence/confirmation, and dispute cache
operations, preventing stale or deep-linked IDs from mutating another
account's Room row. Offline idempotency keys are now account-scoped as well;
queued listing, pickup, and supply-handover operations retain their key until
the sync worker receives an applied result, then clear only that account's
key. This prevents repeated offline taps from creating new server identities
and prevents one account from reusing another account's pending key. A final
isolation pass removed unscoped Room fallbacks from lots, quotes, handovers,
payments, disputes, conversations, messages, and notifications: with no
authenticated account these repositories now return empty/null or reject the
mutation rather than reading or changing arbitrary local rows.

A repository-wide Codex Security Standard scan was launched against the
earlier repository snapshot `7fa2773`, but its authoritative status remains
`running` in `threat_model` with zero completed coverage and no reportable
findings. It has not produced a sealed report, and later commits are outside
that scan's snapshot. The targeted code audit and authorization suite are
useful evidence, but not a replacement for an independent scan of the final
revision. The backend error boundary now emits structured 5xx events without
serialising arbitrary thrown objects in production; development diagnostics
redact bearer tokens, passwords, OTPs, API keys, and circular metadata before
logging. This is covered by `safeErrorLog.test.ts`.

## Offline and data integrity

The existing Room/WorkManager queue, account-scoped cache snapshots, and
idempotency-key paths were preserved and hardened. Existing sync, inventory,
handover, payment, conflict, and session-coordinator tests passed. The final
Android unit run includes both the account-scoped handover-cache regression and the
no-authenticated-account empty/read-only regression. Logout cleanup now also
clears pending photo uploads and account-scoped idempotency keys.

An exhaustive device-level offline transition run (process death during every
mutation, reconnect, and multi-account switching) was not completed against
disposable accounts in this sprint. It remains a release gate.

The household pickup queue now permits a null collector target for the waiting
state and replays it with the same idempotency key. The sync worker is allowed
to process household pickup operations (and excludes household work from
collector/recycler replay), while account-scoped queue semantics are preserved.
Household listing creation now also falls back to the durable queue on network
failure. Its listing payload, idempotency key, and all selected local photo
paths replay under the household account; a successful multi-photo upload
removes the local evidence files. Repeated offline listing/pickup/confirmation actions keep
the same key until the worker applies them, which closes the duplicate-action
window. This closes the earlier mismatch where pickup creation was
offline-capable but listing creation was online-only.

The latest Android audit also fixed a client-side pickup outbox gap: repeated
offline pickup taps now look up the existing `REQUEST_HOUSEHOLD_PICKUP` row by
account and idempotency key before enqueueing. The queue remains account-
scoped, and a repeated tap reports that the pickup is already saved instead of
creating another local replay row. The emulator Room persistence test now
asserts that this lookup survives database close/reopen and cannot cross the
account boundary.

The final continuation added Room migration 24→25 and an account-scoped
`household_listings` cache. An authenticated ADB run created a 5.2 kg Plastic
listing in airplane mode, showed the offline banner, force-stopped and
restarted the app, and recovered the cached listing from the Room row while
the network error remained actionable. Reconnect testing then exposed an
important reconciliation defect: the VPS had already created the listing and
its two photos, but the local queue remained in `EXTENDED_OPERATION_REJECTED`
because Android treated the nullable photo flags and already-uploaded photo
state as a new upload failure. The client now reconciles an existing remote
listing, avoids duplicate photo upload, deletes only confirmed local evidence,
and keeps the local cache consistent. The backend source now returns stable
photo flags and strips private storage references from Household responses.
The latest backend contract patch still needs deployment to the VPS followed
by a fresh authenticated offline/reconnect replay, so exact-once closure is
not claimed here. The full role-by-role offline mutation matrix and
multi-account process-death run remain open.

## Build and test evidence

### Android

- `:app:assembleEnvTestingDebug` — passed.
- `:app:testEnvTestingDebugUnitTest` — passed: 87 tests, including GPS
  coordinate, returning-Household role, account-scoped cache, no-account
  isolation, and the real-backend-versus-offline-preview synthetic-catalog
  safety test.
- The latest Android unit/build pass also includes the offline cache error-path
  fix described above; `:app:testEnvTestingDebugUnitTest` and
  `:app:assembleEnvTestingDebug` both passed.
- The final source-state pass also includes household-listing replay
  reconciliation, nullable photo DTO handling, stale-refresh cleanup, and
  OTP-rate-limit UI mapping; the Android unit suite and
  `:app:assembleEnvTestingDebug` passed after these changes.
- A shared Android user-facing error mapper now covers auth expiry, role
  denial, rate limits, validation, conflicts, service failures, photo errors,
  and connectivity without exposing raw backend text. The unit/build gate
  passed after this change.
- `:app:assembleProductionDebug` with an explicit placeholder production URL
  and placeholder allowance — passed as a configuration/build check only; it
  was not installed or used for data access.
- After the shared session-coordinator changes, `:app:assembleProductionDebug`
  was rerun with an explicit placeholder URL, placeholder allowance, and
  signing disabled for this configuration-only check; it passed. No production
  APK was installed or used against a backend.
- Device: `emulator-5554`, `sdk_gphone16k_x86_64`, online.
- APK installed: `app/build/outputs/apk/envTesting/debug/app-envTesting-debug.apk`.
- `pm clear`, launch, onboarding, sign-in navigation, invalid phone validation,
  notification permission denial, and force-stop/restart were exercised with
  ADB. The seeded Household phone completed OTP sign-in, opened the Household
  dashboard, and restored that role after force-stop/restart; the post-restart
  logcat sample contained no authentication-required, bearer-token, 401,
  crash, or ANR signatures.
- `:app:connectedEnvTestingDebugAndroidTest` — passed: 9/9 tests on
  `Pixel_10_Pro(AVD) - 17`, including cold demo entry, role-correct live
  Kabadiwala tabs, language switching without losing the demo session, and the
  final account-ownership data-layer changes plus Room queue persistence and
  account filtering across database close/reopen.
- Final testing APK was installed after the no-account isolation pass, app
  data was cleared, and
  the app was cold-launched. The captured logcat contained no protected API
  routes, `AUTHENTICATION_REQUIRED`, bearer-token, Retrofit/OkHttp, FATAL, or
  ANR signatures before authentication:
  `FINAL_CLEAN_START_NO_PROTECTED_REQUEST_OR_APP_CRASH_SIGNATURES`.
- The rebuilt testing APK was installed again after onboarding cleanup. ADB
  screenshot inspection confirmed the welcome surface shows only the customer
  actions (Get started / I already have an account); the operator/demo
  controls are absent, and both buttons use the same corner treatment.
- After a clean rebuild, install, and clear, the seeded Household completed
  OTP sign-in and opened Nearby Kabadiwalas. The UI showed facilities returned
  by the testing VPS; no bundled `MockRecyclerData` names were displayed.
- The same APK was exercised with the explicit debug-only role preview entry
  points on `emulator-5554`: Household rendered the Household dashboard with
  Home / Prices / Nearby / Settings; Recycler rendered Marketplace / Orders /
  Pickups / Rates / Profile; and Kabadiwala rendered Home / Inventory /
  Pickups / Buyers / Settings. These previews are test-only and do not create
  or mutate backend records.
- A bounded accessibility smoke check ran on the debug Household preview at
  Android font scale `1.30`. The dashboard remained vertically scrollable, its
  primary actions stayed inside the viewport, and all four bottom-navigation
  labels remained readable without overlap. The emulator did not rotate in
  response to the settings-based landscape request, so landscape and physical
  device behavior remain unverified; the font scale was restored to `1.0`.

### Backend

- `npm ci` — completed; npm reported two moderate audit findings that need
  dependency-owner review.
- `npm run build` — passed.
- `npm run lint` — passed.
- `npm test` — passed: 38 test files, 114 tests, including the material
  suggestion multipart/provider regression, waiting-pickup
  creation/notification, atomic collector-claim, malformed-database-URL
  configuration regression coverage, and returning-phone role preservation.
- The current backend gate passes 38 test files and 114 tests after adding
  legacy lot idempotency-mismatch and OTP limiter coverage. `npm run build` and `npm run lint`
  also pass after the hardening change.
- The latest backend run passed 39 test files and 117 tests, including explicit
  missing-linkage rejection for collector, household, and account middleware,
  plus the no-registration-details Household phone-login regression.
- Testing health endpoint — HTTP 200, database connected.
- Testing readiness endpoint — HTTP 200; database, storage, OTP provider, and
  rate-limit store reported ready.
- Current source-state rerun on 2026-09-21 — `npm test` passed 39 test files
  and 117 tests; `npm run lint` and `npm run build` also passed. The backend
  test stderr contains intentional negative-case validation logs only (for
  example missing-photo 422 and unavailable-Gemini 503 coverage), and 5xx
  diagnostics are now redacted structured events.
- Current VPS post-reset probe on 2026-09-21 — `/api/v1/health` and
  `/api/v1/ready` returned HTTP 200 with the database connected and all
  readiness checks true. The VPS reports `0.0.38-beta`; its update manifest
  still reports versionCode 39, so the latest repository release (versionCode
  40 / `0.0.39-beta`) is not yet deployed there.
- A fresh probe after the latest Android push still reports VPS version
  `0.0.38-beta`, update manifest versionCode 39, HTTP 200 health/readiness, and
  therefore confirms deployment remains the release blocker rather than a
  client build failure.
- Current VPS smoke checks for the previously uploaded build — `/api/v1/health`
  and `/api/v1/ready` returned HTTP 200; unauthenticated
  `/api/v1/kabadiwala/listings` returned 401; Collector login/profile returned
  200; Household access to Collector inventory returned 403; material
  suggestion without a photo returned 422; and a valid PNG multipart request
  returned HTTP 200 with a low-confidence structured result. No credentials or
  bearer tokens were recorded in the report.
- Live phone-role revalidation — seeded Household OTP verification returned a
  `HOUSEHOLD` user and token role; the authenticated profile endpoint returned
  the same `HOUSEHOLD` role and profile linkage.
- Unauthenticated protected listing probe — HTTP 401 with the expected typed
  authentication error.
- The local backend source now maps Household listing responses through a safe
  DTO: `photoAttached`/`photoCount` are stable and private storage keys are
  omitted. `npm run build`, `npm run lint`, and the full 38-file/110-test suite
  passed with this change. A live authenticated check confirms the VPS still
  returns the old nullable fields and private photo references, so the latest
  source patch was not yet deployed at the time of this report and live replay
  verification remains pending.
- Disposable role/IDOR probes passed: Household → collector inventory and
  recycler lots were denied; Collector → household listings and recycler lots
  were denied; pending Recycler → protected collector inventory was denied; an
  invalid token and wrong password were rejected.
- Live waiting-pickup flow passed after the VPS deployment: Household created a
  listing, requested without a collector, received `WAITING_FOR_PICKUP` with a
  null collector, the Collector saw the waiting request, atomically claimed it,
  and the Household observed `ACCEPTED`.
- Live core supply-chain API flow passed with disposable Household, Collector,
  and admin-approved Recycler accounts: listing → pickup acceptance → arrival
  → final weight/rate → Household settlement → Collector payment → inventory
  movement → bulk lot → Recycler offer → accepted offer → QR handover → QC
  pass → Recycler payment → Collector passport. Inventory invariants remained
  valid and cross-role denials passed. All disposable accounts were deleted
  after the run.
- Rebuilt APK install/clear/launch after the waiting, household-detection,
  queued-idempotency, and no-account isolation changes — passed; the final run
  emitted
  `FINAL_CLEAN_START_NO_PROTECTED_REQUEST_OR_APP_CRASH_SIGNATURES`; no
  app-owned protected-call/auth/crash signatures appeared before
  authentication.
- After the offline queue persistence regression test was added, the
  instrumentation suite was rerun at 9/9 on `Pixel_10_Pro(AVD) - 17`.
  `SyncQueuePersistenceTest` closed and reopened a Room database and proved
  queued operations survive the lifecycle boundary while
  `observeForAccount`/`observePendingForAccount` keep account A from seeing
  account B's work. The rebuilt APK was installed, cleared, and cold-launched
  again. The clean-start marker was
  `FINAL_CLEAN_START_NO_PROTECTED_REQUEST_OR_APP_CRASH_SIGNATURES`.
- After the final shared image-pipeline change, `:app:connectedEnvTestingDebugAndroidTest`
  was rerun and passed 9/9 on `Pixel_10_Pro(AVD) - 17`.
- After the final DTO, sync, refresh-cleanup, and OTP-rate-limit changes,
  `:app:connectedEnvTestingDebugAndroidTest` completed 9/9 on
  `Pixel_10_Pro(AVD) - 17`. A final clean install, `pm clear`, and direct
  `MainActivity` launch reached the unauthenticated language-selection screen;
  filtered logcat contained no protected route, bearer-token, 401,
  `AUTHENTICATION_REQUIRED`, FATAL, or ANR signature before authentication.
- After the shared user-facing error-mapping change, the instrumentation suite
  completed 9/9 on `Pixel_10_Pro(AVD) - 17` again.
- Current source-state rerun on 2026-09-21 —
  `:app:testEnvTestingDebugUnitTest` passed, and
  `:app:connectedEnvTestingDebugAndroidTest` passed 9/9 on
  `Pixel_10_Pro(AVD) - 17`. `:app:assembleEnvTestingRelease` and
  `:app:assembleEnvTestingDebug` both passed after the release-variant guard
  fix. A fresh APK install through the SDK `adb` executable, `pm clear`, and
  cold launch produced an app-process-only
  `PASS_NO_APP_PROTECTED_REQUEST_OR_CRASH_SIGNATURES` result; no protected
  route, bearer token, 401, Retrofit/OkHttp, FATAL, or ANR signature appeared
  before authentication.
- The current Android unit suite contains 90 passing tests across 20 suites;
  The latest theme patch also passed the full `:app:connectedEnvTestingDebugAndroidTest`
  suite 11/11 on `Pixel_10_Pro(AVD) - 17`.
  `ThemeContrastTest` enforces WCAG-AA contrast for light primary, warning,
  success, and error text plus the lime action label/fill pair, so the
  light-mode fix cannot regress. Reusable offline/success/error/sync states
  and Recycler warning labels now use theme-aware semantic tokens rather than
  static dark-theme accent constants. The latest
  `:app:connectedEnvTestingDebugAndroidTest` run passed 11/11 on
  `Pixel_10_Pro(AVD) - 17` after this patch.
- Current post-light-theme Android release gate on 2026-09-21:
  `:app:assembleEnvTestingRelease` passed with R8 and release lint checks.
- Current Android transport-error boundary rerun on 2026-09-21:
  `:app:testEnvTestingDebugUnitTest` passed 91 tests across 21 suites,
  `:app:assembleEnvTestingDebug` passed, and
  `:app:connectedEnvTestingDebugAndroidTest` passed 11/11 on
  `Pixel_10_Pro(AVD) - 17`. Malformed 5xx response bodies now become a
  generic client exception instead of carrying raw backend/provider content;
  `:app:assembleEnvTestingRelease` also passed with R8 and release lint.
- Latest offline account-boundary rerun on 2026-09-21:
  sync-queue removal, retry, attempt-increment, and failure mutations now
  require both the queue UID and the current account ID. The Android unit/build
  gate passed, and `:app:connectedEnvTestingDebugAndroidTest` passed 12/12 on
  `Pixel_10_Pro(AVD) - 17`, including a Room persistence regression proving
  account A cannot mutate account B's queued operation.
- Latest waiting-pickup authorization hardening on 2026-09-21: the
  `WAITING_FOR_PICKUP` accept path now repeats the same area/25 km service
  boundary used by collector feeds, so a Kabadiwala cannot bypass discovery by
  guessing a listing ID. The route has a regression test for this IDOR/business
  logic case; backend gates pass with 39 files and 118 tests.
- The account-boundary pass now also removes the account-keyed formalisation
  dashboard cache during explicit logout, alongside Room rows, outbox items,
  pending photos, idempotency keys, and app-private evidence files. Android
  unit/build gates passed and the emulator instrumentation suite passed 9/9
  after this change.
- The added account-cache regression then ran on `Pixel_10_Pro(AVD) - 17` with
  the full instrumentation suite at 10/10, proving that clearing one account's
  formalisation snapshot leaves another account's snapshot intact.
- A fresh current-APK install, `pm clear`, logcat reset, force-stop, and cold
  launch on `emulator-5554` again produced
  `CLEAN_START_PASS_NO_PROTECTED_REQUEST_OR_CRASH_SIGNATURES`; app-process
  logcat contained no protected route, bearer-token, Retrofit/OkHttp, fatal,
  or ANR signature before authentication. The UI dump was attempted during the
  splash transition and was unavailable, so no screen assertion is inferred
  from that command.
- Post-push verification for commit `4ff9c19` repeated the same clean install,
  data clear, cold launch, and app-process-only logcat filter on `emulator-5554`:
  PID `8779` reached the unauthenticated surface with no protected request,
  bearer-token/401, Retrofit/OkHttp, crash, or ANR signature before login.
- Final pushed-revision smoke verification for commit `8526331` repeated
  `adb install -r`, `pm clear`, logcat reset, and cold launch on
  `emulator-5554`. PID `10419` produced
  `FINAL_PUSHED_REVISION_CLEAN_START_PASS` with no protected request,
  bearer-token/401, Retrofit/OkHttp, crash, or ANR signature before login.
- The final continuation also added a process-local account boundary to the
  SupplyChain ViewModel: retained navigation state now resets when the
  authenticated profile changes, including listing photos, photo errors,
  listings, and radius. The Android unit/build gate and connected emulator
  suite passed 11/11 after this change.
- The current source also passed `:app:assembleEnvTestingRelease`, including
  release resource processing, lint-vital, R8 shrinking, and packaging. The
  only compiler note was the existing Android deprecation warning for direct
  `statusBarColor` assignment in the shared theme.
- The offline/cache audit also fixed authoritative empty notification
  responses: they now clear only the active account's cached notifications,
  preventing stale alerts after reconnect without touching another account.
  The Android suite passed 11/11 on `Pixel_10_Pro(AVD) - 17` and
  `:app:assembleEnvTestingRelease` passed again after this fix.
- Multi-angle listing viewing is now implemented behind authenticated indexed
  photo endpoints. Household owners can request any stored angle, and a
  Kabadiwala can request them only after an active pickup assignment; storage
  keys never enter the response. The backend photo suite passed 7/7, the
  Android unit/build gate passed, the emulator suite passed 11/11 again, and
  `:app:assembleEnvTestingRelease` passed after the partial-failure retry
  hardening. The viewer preserves successfully
  loaded angles when a later image is unavailable and gives an actionable retry
  message. The new endpoint still needs deployment to the VPS before live
  verification.
- Earlier backend verification rerun on 2026-09-21: `npm test` passed 38 test files and
  114 tests, `npm run build` passed, and `npm run lint` passed. The attempted
  `npm test -- --runInBand` command is not a supported Vitest option; the
  successful plain `npm test` run is the authoritative result.
- Dependency audit on 2026-09-21: `npm audit --omit=dev` reports zero
  production vulnerabilities. The full audit reports two moderate Vitest
  development-tool advisories (`@vitest/mocker`, fixed only by the major
  Vitest 5 upgrade); this remains a test-tool maintenance item and does not
  affect the runtime dependency graph.
- Latest rerun after the offline pickup outbox dedupe fix:
  `:app:testEnvTestingDebugUnitTest` and `:app:assembleEnvTestingDebug`
  passed, and `:app:connectedEnvTestingDebugAndroidTest` passed 9/9 on
  `Pixel_10_Pro(AVD) - 17`, including the Room queue persistence assertion.
- A follow-up error-surface audit removed the internal `unsupported_image`
  exception value from Kabadiwala material-detection UI state. The screen now
  consistently uses its safe localized unsupported-image/manual-fallback copy;
  the unit/build gate and emulator suite passed again (89 unit tests, 9/9
  instrumentation tests).
- Fresh post-push install/clear/cold-launch on `emulator-5554` was checked with
  app-process-only logcat filtering and again produced
  `CLEAN_START_PASS_NO_PROTECTED_REQUEST_OR_CRASH_SIGNATURES` before login.
- After the OTP limiter, retry-window, and rapid-tap guard change, the Android
  unit suite completed with 86 tests and `:app:assembleEnvTestingDebug`
  passed. The rebuilt APK was installed on `emulator-5554`, application data
  was cleared, and a cold launch again produced
  `CLEAN_START_NO_PROTECTED_REQUEST_OR_APP_CRASH_SIGNATURES`.
- After the QR verification/confirmation single-flight guard change, the
  Android unit suite completed with 87 tests and the combined
  `:app:assembleEnvTestingDebug :app:connectedEnvTestingDebugAndroidTest`
  gate passed. All 9/9 emulator tests passed on
  `Pixel_10_Pro(AVD) - 17`. Client verification and confirmation now claim
  their in-flight state synchronously; confirmation retries retain the
  existing idempotency key for transient HTTP 408/429 failures. A fresh APK
  install, `pm clear`, and cold launch again produced
  `CLEAN_START_NO_PROTECTED_REQUEST_OR_APP_CRASH_SIGNATURES`.
- After the welcome-hero visual refinement, the Android unit suite and the
  connected emulator suite passed again: 87 unit tests and 9/9 instrumentation
  tests on `Pixel_10_Pro(AVD) - 17`. Emulator visual QA confirmed the graphite
  hero, two-line phone headline, readable benefit rows, and fully rounded
  primary/secondary actions.
- After the authenticated-background-work gate change, the instrumentation
  suite was rerun and passed 9/9 on `Pixel_10_Pro(AVD) - 17`. The exact rebuilt
  APK was installed, application data was cleared, and a cold launch again
  produced `FINAL_CLEAN_START_NO_PROTECTED_REQUEST_OR_APP_CRASH_SIGNATURES`;
  the filtered logcat also contained no notification-device registration,
  protected-route, bearer-token, Retrofit/OkHttp, crash, or ANR signature.
- After extracting the shared `SessionCoordinator` and adding the worker gate,
  `:app:testEnvTestingDebugUnitTest` (86 tests) and
  `:app:connectedEnvTestingDebugAndroidTest` passed again (9/9 on
  `Pixel_10_Pro(AVD) - 17`). The final APK was installed and cleared once more;
  the clean-start marker remained
  `FINAL_CLEAN_START_NO_PROTECTED_REQUEST_OR_APP_CRASH_SIGNATURES`.
- The same final APK completed the seeded disposable Household OTP sign-in on
  `emulator-5554`, opened the live Household dashboard, and then restored that
  dashboard after a force-stop/relaunch. A fresh UIAutomator dump confirmed
  `Sell your scrap` and the `Home` destination; the post-login/restart logcat
  had no auth, Retrofit/OkHttp, crash, or ANR signatures.
- One first instrumentation invocation was aborted by the emulator's
  `system_server` disappearing (`Can't find service: activity/package`), not by
  an application assertion. After the emulator was rebooted and its services
  recovered, the same suite completed 9/9; the run was not counted as green
  until that recovery rerun passed.
- The final rebuilt APK was installed with `adb install -r`, application data
  was cleared, and the app was cold-launched again. The clean-start marker
  remained `FINAL_CLEAN_START_NO_PROTECTED_REQUEST_OR_APP_CRASH_SIGNATURES`.
- After the invalid-image handling hardening, camera normalization failures
  now stay on the photo step with a user-facing error, and gallery imports
  report partial failures while retaining valid selections; no raw fallback
  path is accepted by the lot flow.
- ADB airplane-mode cold-start smoke test also passed after a clear: the app
  reached its unauthenticated surface without auth/crash signatures, and the
  emulator network state was restored afterward. This is an offline startup
  check, not a substitute for authenticated offline mutation/process-death
  testing. The targeted authenticated household listing mutation/restart/
  reconnect proof is documented in the Offline and data integrity section.
- The latest APK was exercised through the dedicated Household `Sell scrap`
  route. ADB selected two real Photo Picker images in one action; the returned
  screen showed both previews and two independent `Remove photo` controls. The
  post-picker app-private files were inspected with `run-as`: both were
  normalized `.jpg` files under `files/household_photos` (approximately 90 KB
  and 112 KB in this run), confirming the shared pipeline is used before
  persistence. The configured Gemini provider returned a typed service-error
  state on this testing deployment, and the screen kept the manual category
  choices available; no raw provider JSON, 422 image-validation message, crash,
  or fatal exception appeared in the app logcat sample.
  backend multi-photo contract has direct regression coverage for stable
  indexed storage keys, primary-photo backward compatibility,
  `photoReferences`, and an audit `photoCount` of two. The Android offline
  payload and Room migrations carry all selected paths rather than silently
  dropping additional angles.
- The Kabadiwala lot upload service now has equivalent regression coverage for
  two angle photos, stable `LOT-1.jpg`/`LOT-1-1.jpg` references, and persistence
  of the complete reference list.
- A fresh cleared-install ADB session signed in with the seeded Collector phone
  `9876543203`, denied notification permission, and reached the collector
  dashboard with the role-specific `Home / Inventory / Pickups / Buyers /
  Settings` navigation. Inventory opened with its authenticated empty summary
  (`0.0 kg` available/reserved, `0` materials) and Pickups opened with the
  authenticated empty queue (`0 requests`, `0 scheduled`). A logcat sample
  captured after navigation contained no 401, bearer-token,
  `AUTHENTICATION_REQUIRED`, Retrofit/OkHttp, crash, or ANR signatures. This
  confirms the collector read-only shell on the emulator; it does not replace
  the still-required mutation, QR, offline process-death, and physical-device
  tests.
- The latest APK was rebuilt after the no-token interceptor and typed-auth
  changes. `:app:testEnvTestingDebugUnitTest` passed and
  `:app:connectedEnvTestingDebugAndroidTest` passed 9/9 on
  `Pixel_10_Pro(AVD) - 17`. A cleared-install cold launch again produced
  `CLEAN_START_NO_PROTECTED_REQUEST_OR_APP_CRASH_SIGNATURES`. A deliberate
  invalid email/password submission produced the expected backend 401 and the
  user-facing `Email or password is incorrect.` message; the same logcat
  window contained no `/kabadiwala/listings`, bearer-token, or
  `AUTHENTICATION_REQUIRED` signature after the failed login.
- After the structural-token and demo-task-state hardening, the testing APK
  was rebuilt and installed, application data was cleared, and the activity was
  launched directly. A fresh UIAutomator dump showed the unauthenticated
  language-selection entry screen; the previous Household fixture screen was
  not restored. The same clean logcat window contained no protected API route,
  bearer-token, `AUTHENTICATION_REQUIRED`, FATAL, or ANR signature.
- The final source-state `:app:connectedEnvTestingDebugAndroidTest` rerun then
  completed 9/9 on `Pixel_10_Pro(AVD) - 17` with zero failures.
- The final Android client was driven against the uploaded VPS testing
  deployment using the seeded Collector phone `9876543203`: OTP login,
  authenticated Home, `Record lot`, Photo Picker, and live material detection
  completed. The UI showed the typed low-confidence/manual-selection state; the
  logcat sample contained no app crash, ANR, 401, bearer-token, or raw provider
  payload signature.
- The same final APK was driven as seeded Household `9876543201` against the
  VPS: OTP login, Household Home, dedicated `Sell scrap`, two-image Photo
  Picker selection, removable previews, and low-confidence/manual fallback all
  completed. This closes the previously missing live Household image-flow
  evidence, but not the complete Household pickup-to-settlement UI journey.
- Current revision clean-start recheck on `emulator-5554` after installing the
  testing debug APK and clearing app data: the unauthenticated surface launched
  cold, and app-process logcat contained
  `PASS_NO_APP_PROTECTED_REQUEST_OR_CRASH_SIGNATURES` (no 401, bearer-token,
  Retrofit/OkHttp, protected-route, FATAL, or ANR signature before login).
- After the Household form draft-preservation and Gemini retry changes,
  `:app:testEnvTestingDebugUnitTest` and
  `:app:assembleEnvTestingDebug` passed, and the connected suite passed 12/12
  on `Pixel_10_Pro(AVD) - 17`. The exact rebuilt APK was installed on
  `emulator-5554`, app data was cleared, and a fresh cold launch again produced
  no protected-request, 401, bearer-token, crash, or ANR signature in the app
  process log. Household text fields, checkboxes, and selected photo paths now
  use saveable state across Activity recreation; both image-detection surfaces
  expose a retry action while preserving the manual fallback.
- After the version 41 bump and backend version-configuration change,
  `:app:testEnvTestingDebugUnitTest` and
  `:app:connectedEnvTestingDebugAndroidTest` passed again on
  `Pixel_10_Pro(AVD) - 17`; all 12 connected tests completed successfully.

## Performance observations

The final testing APK built and installed successfully, and a cleared emulator
launch reached the onboarding/auth UI in approximately 7.9 seconds including
ADB launch overhead, without an API storm or crash. `dumpsys gfxinfo` reported
only two startup frames on the emulator, both marked janky; that sample is too
small and splash-dominated to support a product performance claim. No
representative Macrobenchmark, memory capture, or low-end physical-device run
was available.

## Remaining external dependencies

- Production HTTPS API and managed MongoDB/Prisma deployment.
- Production Gemini key, approved model, quotas, timeout monitoring, and a
  successful structured-response smoke test.
- Production S3/private storage, SMS/OTP provider, FCM credentials, and push
  configuration.
- Production signing keystore and CI secret injection.
- Deployment of the latest backend Household photo-response contract to the
  VPS, followed by a fresh offline listing reconnect/replay check. Confirmed
  current state: the VPS still returns the older nullable/raw photo fields
  observed during diagnosis.
- The VPS is currently running the older `0.0.38-beta` deployment and update
  manifest versionCode 39, while the repository contains the versionCode 40
  `0.0.39-beta` release. The latest backend contract and Android release APK
  therefore require a deliberate VPS/app-update upload and restart before
  live deployment evidence can be refreshed.
- A read-only health probe still returned HTTP 200 with the old version. A
  read-only SSH connectivity check from this workspace was refused with
  `publickey`, so no VPS files, database, or process state were changed during
  this pass; deployment still requires the operator's configured VPS key or
  upload path.
- The recent VPS database reset completed runtime index preparation, but the
  development seed was correctly refused because the server `.env` is in
  production mode. No testing fixtures should be inserted into that database;
  a separate `APP_ENV=testing` database is required for seeded disposable
  accounts and destructive live testing.
- Deployment/restart of the latest OTP limiter contract is also pending on the
  VPS. Until that upload is active, VPS logs may still show `otp_requested`
  before a rejected request; the local source now logs `otp_sent` only after
  successful delivery and returns `Retry-After` for 429 responses.
- Physical-device camera/GPS/permission/TalkBack testing, authenticated UI
  completion for every role, and a device-level offline process-death run.
- The checked-out backend `.env` is not a usable schema-deployment environment:
  its Mongo database path contains an illegal encoded-space database name.
  `prisma db push` refused to run, and startup configuration now rejects this
  class of URL. The configured VPS testing deployment does include the
  waiting-flow schema and passed the live waiting test; the local `.env` still
  must be corrected before local schema operations.

## Known issues and follow-up gates

- Full role-by-role authenticated UI regression and offline process-death
  recovery remain required. Client/server defenses for repeated QR
  verification/confirmation are implemented and covered by unit/server
  idempotency logic, but a device-level double-scan replay still needs to be
  exercised against the deployed testing API. The core role/IDOR and
  cross-role supply-chain checks have passed against the testing API, but not
  every path has been driven through Android UI.
- Household multi-photo selection, local preprocessing, upload, server
  persistence, and authenticated indexed viewing now support up to six images.
  The secondary-angle viewer is implemented locally but remains unverified
  against the VPS until the backend deployment is updated.
- A stale refresh-token replay is now handled safely by returning to auth, but
  multi-device refresh-rotation behavior and the post-deployment offline
  listing replay still require live verification.
- After the database reset, all former users, sessions, lots, pickups,
  inventory, and history are gone unless restored from backup. The production
  VPS must not be seeded with the development fixtures; testing requires a
  separately isolated database and fresh disposable accounts.
- Production release signing and provider configuration are intentionally
  absent from source control.
- The testing Gemini provider now responds with structured low-confidence
  results after the VPS upload. Production still requires separately managed
  Gemini key/model/quota configuration and a representative scrap-photo
  accuracy smoke test; low confidence must continue to offer manual selection.
- A repository-wide Codex Security Standard scan was launched against the
  earlier repository snapshot `7fa2773`, but its authoritative status remains
  `running` in `threat_model` with zero completed coverage and no reportable
  findings. It has not produced a sealed report, and later commits are outside
  that scan's snapshot, so this is not a clean security result; existing
  role-boundary tests and targeted authorization review remain evidence, not a
  replacement for an independent scan of the final revision.

## Release recommendation

### Latest backend upload gate — 2026-09-21

- Backend revision `d20f519` plus safe Gemini provider diagnostics is
  upload-ready as source: `npm test` passed 39/39 files and 121/121 tests,
  `npm run build` passed, and `npm run lint` passed.
- The tracked testing/default examples now use the requested stable multimodal
  `gemini-3.5-flash-lite` model. The local untracked `.env` also contains that
  model, but its configured Gemini key returned HTTP 401 during a metadata-only
  provider check. Do not upload that `.env`; set a valid server-side
  `GEMINI_API_KEY` with access to `GEMINI_MODEL=gemini-3.5-flash-lite` on the
  VPS.
- This provider check was read-only and did not mutate Gemini, the VPS, or
  application data. Until the VPS key/model is corrected, the app is expected
  to show the manual material-selection fallback rather than a false AI result.
- The material-suggestion contract now requests JSON output, normalizes safe
  category aliases such as `lcd`/`plastic`, and rejects malformed or incomplete
  provider output as `GEMINI_INVALID_RESPONSE` without recording an AI
  inference. Backend coverage is now 39 files / 121 tests, with build and lint
  still passing.
- Material detection provider failures now emit only a structured event with
  request ID, operation, model, HTTP status, or a coarse timeout/network reason.
  Keys, image bytes, prompts, and provider response bodies are never logged;
  this makes VPS diagnosis of 401/404/429/timeout failures possible without
  leaking sensitive data.
- Live VPS verification after the reported backend update returned HTTP 200 for
  `/api/v1/health` and `/api/v1/ready`, and the update manifest returned
  versionCode 41 / `0.0.40-beta` with the expected 27,246,976-byte APK. However,
  both health responses still report backend version `0.0.38-beta`; the running
  backend deployment or its `APP_VERSION` environment value is therefore not
  yet aligned with the repository revision and must be checked before release.
- The backend configuration default and all tracked environment examples now
  use `APP_VERSION=0.0.40-beta`, so a fresh deployment cannot silently fall back
  to the old generic `1.0.0` identifier. The VPS still needs its actual `.env`
  value updated and the process restarted.
- The rebuilt versionCode 41 APK was installed on `emulator-5554`, app data was
  cleared, and the app was cold-launched. The app process remained alive and a
  PID-scoped logcat check found no protected API request, 401/Bearer-token
  message, fatal exception, or ANR signature before authentication.

**NOT READY** until the external dependencies and remaining on-device gates
above are completed. The implemented P0 fixes materially reduce the startup,
GPS, environment, and false-Gemini-success risks, and the disposable testing
API supply-chain run is complete. A production Gemini configuration, signed
release configuration, physical/device-level regression, and independent
security scan are still outstanding.
