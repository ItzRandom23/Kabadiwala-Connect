# Kabadiwala Connect

Kabadiwala Connect is one Android-first product connecting informal e-waste collectors with authorized recyclers and aggregators. There is no user-facing website in this repository.

## Product

The Android APK contains two role-routed experiences:

- Collector: create lots offline, add photos, select material, enter weight, hear prices, find verified recyclers, compare offers, create a QR handover record, confirm final value, record optional cash/digital payment, and view earnings.
- Recycler: submit facility details for verification, then browse matched lots, manage buying rates, make offers, manage orders and pickups, and load a handover by its server-side QR/reference.

Role is stored in the backend account profile and cached locally only to make offline launch sensible. The app never chooses a role from an email address or domain. Recycler verification remains backend-controlled; a new recycler starts as `PENDING`.

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
copy .env.example .env
npm install
npm run db:generate
npm run db:push
npm run db:seed
npm run dev
```

The backend owns role, recycler authorization, ownership, lot/offer/handover transitions, price ranges, valuation, payment records, and traceability-related audit data. MongoDB indexes cover account lookup, roles, authorization, material, lot status, timestamps, and transaction references. Secrets remain environment-only.

### Authentication

New accounts use:

- `POST /api/v1/auth/signup` with email, password, role, language, and optional recycler details.
- `POST /api/v1/auth/login` with email and password.
- `GET /api/v1/auth/profile` with the bearer token for revalidation.

The existing phone/OTP endpoints remain available for older collector deployments during migration, but the Android onboarding uses email accounts. Passwords are hashed with Node’s `scrypt`; they are never stored or logged in plaintext.

## Android setup

Open `android_app/` in Android Studio, or build from PowerShell:

```text
cd android_app
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug
./gradlew.bat assembleRelease -PapiBaseUrl=https://your-host.example/api/v1/
```

The safe default API URL is `.invalid`, which keeps the debug build in its local/offline authentication path. Set a real HTTPS URL for backend integration. Release signing credentials are intentionally not included.

## Offline and AI integration boundaries

Room stores drafts, cached prices/recyclers, payments, handovers, and sync operations. WorkManager retries supported collector lot/payment mutations with idempotency keys and server-side conflict responses. The UI exposes honest estimate ranges rather than fake precision. Material classification, valuation, recycler matching, and anomaly detection are isolated integration points; seeded data is marked development data and no model-accuracy claim is made.

## Testing

```text
cd backend
npm run build
npm test
npm run lint

cd ../android_app
./gradlew.bat testDebugUnitTest
```

The current automated suite covers authentication boundaries, JWTs, validation, lot rules, price/valuation utilities, recycler filtering, quote workflows, Room-backed state, and ViewModel transitions. Device validation is still required for CameraX permissions, QR camera scanning, TalkBack, GPS, and real network loss/recovery.

## Known limitations

- Production email delivery, FCM notifications, CameraX capture, and ML Kit camera QR scanning still need environment wiring; the Android flows have safe local/manual fallbacks.
- The backend verification/admin seed path is intentionally backend-only; no admin website is provided.
- Live MongoDB, object storage, payment provider, and signed-release credentials must be supplied by deployment.
- Development demo fixtures are not real government-verified companies or live market claims.
