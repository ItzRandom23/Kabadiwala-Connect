# Kabadiwala Connect

Kabadiwala Connect is one Android-first product connecting informal e-waste collectors with authorized recyclers and aggregators. There is no user-facing website in this repository.

## Current release

- Android testing release: `0.0.52-beta` (`versionCode 53`)
- Environments: `envTesting` and `production`
- OTA manifest: `backend/app-update/update.json`
- Backend update path: `/app/update.json`

The current beta keeps signed-out users on authentication, uses saved household
coordinates for nearby Kabadiwala discovery, and recovers from concurrent pickup
QR scans. It also includes GPS-aware lot locations, multi-photo scrap capture,
Gemini-assisted material suggestions with manual fallback, account-scoped
offline sync, role-aware navigation, verified household pickup QR handover,
full-screen final weighing, QR handover to recyclers, and crash-safe camera and
external-activity handling.

## Product

The Android APK contains three role-routed experiences:

- Household seller: post recyclable material with a photo, approximate weight and area, review a clearly labelled prototype price range, choose an active Kabadiwala, request pickup, and view weighing, rate, status, and final settlement.
- Kabadiwala: discover household pickup requests, schedule and weigh collections, manage owned inventory, create recycler-facing bulk lots, review offers, and respond to procurement demand.
- Recycler: submit facility details and authorization evidence for verification, browse Kabadiwala bulk lots, make and track procurement offers, confirm receipt, and publish material requirements.

Role is stored in the backend account profile and cached locally only to make offline launch sensible. The app never chooses a role from an email address or domain. A new recycler/aggregator starts as `PENDING`, submits authorization evidence in the app, and becomes `VERIFIED` only after an authorized operator checks the record and approves it.

## Architecture

```text
android_app/   Kotlin + Jetpack Compose + Material 3 + Room + Retrofit + WorkManager
backend/       Express + TypeScript + Prisma + MongoDB
design/        Brand tokens and Android reference lock
```

Android uses a single activity, role-aware Navigation Compose, MVVM-style ViewModels, encrypted session storage, Room caches, an idempotent sync queue, and a warm collector / dense operations visual system. English, Hindi, and Marathi are supported through Android resources and the onboarding language choice. Dark mode, large touch targets, explicit offline states, TTS price/safety hooks, and local cached safety guidance are included.

## Backend setup

Requirements: Node.js 20+, npm, and MongoDB/Atlas.

```text
cd backend
copy .env.testing.example .env
npm ci
npm run db:generate
npm run db:prepare
npm run dev
```

For PowerShell, use `Copy-Item .env.testing.example .env`. `db:prepare`
creates the runtime MongoDB indexes while preserving intentional partial unique
indexes; do not replace it with a direct `prisma db push` on a deployment
database. Development seed data is restricted to `APP_ENV=testing`.

The backend owns role, recycler authorization, ownership, lot/offer/handover transitions, price ranges, valuation, payment records, and traceability-related audit data. MongoDB indexes cover account lookup, roles, authorization, material, lot status, timestamps, and transaction references. Secrets remain environment-only.

The supply-chain boundary is explicit: a Household posts material and requests a
Kabadiwala pickup; only a Kabadiwala can weigh it into inventory and reserve
that inventory in a bulk lot; only a verified Recycler can offer on and receive
that lot. See [the role architecture audit](docs/ROLE_ARCHITECTURE_AUDIT.md)
for the route-level capability matrix and inventory/state invariants. After
pulling schema changes into a testing database, run `npm run db:push` before
starting the API. The seeded end-to-end accounts and conflict checks are
documented in [the supply-chain test runbook](docs/SUPPLY_CHAIN_TEST_RUNBOOK.md).

### Authentication

New accounts use:

- `POST /api/v1/auth/signup` with email, password, role, language, and optional recycler details.
- `POST /api/v1/auth/login` with email and password.
- `POST /api/v1/auth/admin-login` for a persisted, permission-scoped admin account seeded from deployment environment variables.
- `GET /api/v1/auth/profile` with the bearer token for revalidation.

The existing phone/OTP endpoints remain available for older collector deployments during migration, but the Android onboarding uses email accounts. Household accounts are persisted as a distinct seller role while sharing the collector-owned lot, quote, handover, payment, and notification boundary. Passwords are hashed with Node’s `scrypt`; they are never stored or logged in plaintext. Recycler discovery and quote responses use privacy-safe public views; exact facility/contact details are reserved for authorized workflows.

## Android setup

Open `android_app/` in Android Studio, or build from PowerShell:

```text
cd android_app
./gradlew.bat :app:testEnvTestingDebugUnitTest
./gradlew.bat :app:assembleEnvTestingDebug -PtestingApiBaseUrl=https://testing-host.example/api/v1/
./gradlew.bat :app:assembleProductionRelease -PproductionApiBaseUrl=https://your-production-host.example/api/v1/ \
  -PproductionSigningStoreFile=... -PproductionSigningStorePassword=... \
  -PproductionSigningKeyAlias=... -PproductionSigningKeyPassword=...
```

Android uses product flavors for environment selection. `envTesting` is the
disposable testing flavor and `production` is the release flavor; environment
values are generated into `BuildConfig` rather than scattered through source.
The testing endpoint can be set with `-PtestingApiBaseUrl=...` (the checked-in
developer properties file points at the current testing service). Production
variants require an explicit HTTPS `-PproductionApiBaseUrl` and release builds
also require signing properties supplied by CI or local secret configuration.
Never put production credentials, Gemini keys, storage credentials, or signing
secrets in this repository.

The testing build is the safe target for intensive ADB work. Production builds
must use the production flavor, a legitimate HTTPS API, and the production
signing key; do not point a testing build at production data.

The backend uses the matching centralized environment contract. Start from
`backend/.env.testing.example` or `backend/.env.production.example`, copy the
selected file to an untracked `.env`, and set `APP_ENV=testing` or
`APP_ENV=production`. Production startup rejects development mode, insecure
CORS, placeholder secrets, and incomplete provider configuration.

Production secrets, Gemini keys, storage credentials, OTP provider keys, and
JWT/traceability secrets remain backend-only and must be supplied through the
VPS or CI environment.

## Offline and AI integration boundaries

Room stores drafts, cached prices/recyclers, payments, handovers, and sync operations. WorkManager retries supported collector/household mutations with account-scoped idempotency keys and server-side conflict responses, then pulls a delta feed without overwriting unsynced local work. Handover scale evidence and household listing photos upload independently to private storage; failed household photo uploads are retained in an account-scoped local retry queue. The UI exposes honest estimate ranges rather than fake precision. Material classification, valuation, recycler matching, and anomaly detection are isolated integration points; seeded data is marked development data and no model-accuracy claim is made. Successful payment closes the confirmed handover and exposes the transaction passport timeline.

The lot material classifier is assistive only: it validates JPEG/PNG/WebP input
and never blocks manual material selection. Provider outage and low confidence
are distinct UI states. GPS lot capture stores latitude, longitude, precision,
and a separate human-readable area label; the helper text is never persisted as
the area name.

## Testing

```text
cd backend
npm run build
npm test
npm run lint

cd ../android_app
./gradlew.bat :app:testEnvTestingDebugUnitTest
./gradlew.bat :app:lintEnvTestingDebug
./gradlew.bat :app:assembleEnvTestingDebug
./gradlew.bat :app:connectedEnvTestingDebugAndroidTest
```

The current automated suite covers authentication boundaries, JWTs, validation, lot rules, price/valuation utilities, recycler filtering, quote rematching/idempotency, handover recovery, notification isolation, Room-backed state, and ViewModel transitions. Device validation remains important for camera permissions, QR scanning, TalkBack, GPS, and real network loss/recovery.

The latest testing pass also verified a 1,200-event rapid-tap run and camera
recovery after deliberately killing the app while the external camera Activity
was open. The crash buffer remained empty. Camera, gallery, QR, share, and
handover photo failures are surfaced as recoverable UI states.

## OTA Android updates

The app checks the backend-hosted `/app/update.json` and compares its
`versionCode` with the installed version. Each published entry must reference
an APK signed with the same key as the installed app and include a matching
SHA-256 and byte size. The current manifest publishes `0.0.52-beta` with
`versionCode 53`.

The user confirms the download and Android separately confirms installation;
updates are never installed silently. After changing the manifest or APK,
redeploy the backend `app-update` directory to the VPS.

## Submission evidence

See `docs/REQUIREMENTS_EVIDENCE.md`, `docs/DEMO_RUNBOOK.md`, `docs/FIELD_RESEARCH_PROTOCOL.md`, `docs/UNIT_ECONOMICS.md`, `docs/AI_DATASET_CARD.md`, and `docs/PRIVACY_RETENTION.md`.

## Known limitations

- Production email delivery and FCM push delivery still need provider wiring; QR scanning depends on the device camera/activity, while the durable in-app notification inbox and Android photo capture flows work without those providers.
- The backend verification/admin seed path is intentionally backend-only; no admin website is provided.
- Live MongoDB, object storage, payment provider, and signed-release credentials must be supplied by deployment.
- Development demo fixtures are not real government-verified companies or live market claims.
