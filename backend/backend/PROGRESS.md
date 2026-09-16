# Kabadiwala Connect Backend Progress

## Final audit update — 2026-09-04

- Fixed admin payment detail authorization so admin reads no longer use collector ownership lookup.
- Fixed handover reference lookup, recycler handover rejection, and admin dispute detail routing.
- Made handover discrepancy persistence atomic and added payment correction validation plus atomic payment-dispute persistence.
- Production wildcard CORS is now rejected by configuration validation.
- Fixed the production start script to use the emitted `dist/src/server.js` entrypoint and verified a live health response with the database connected.
- Added selectable local photo storage for development (`backend/uploads/`) while retaining AWS/S3-compatible storage through `STORAGE_PROVIDER=s3`.
- Backend build, lint, tests, Prisma generation, and Android release verification passed from the workspace.
- Release remains blocked by missing Android/backend integration, incomplete sync concurrency protection, incomplete production web authentication, and other items in the root `ISSUES.md`.

## Backend Phase 9 — Offline Sync, Android Integration & Security Hardening

### Completed

- Added `SyncOperation` with collector-scoped unique operation IDs, request hashes, processing status, timestamps, and error codes.
- Added bounded `POST /api/v1/sync` batch processing with per-operation APPLIED, ALREADY_APPLIED, REJECTED, INVALID, and CONFLICT results.
- Added supported offline CREATE LOT, UPDATE LOT, and RECORD PAYMENT operations with server ownership/lifecycle validation and optimistic lot-version conflicts.
- Added authenticated `GET /api/v1/sync/changes` delta retrieval for collector-owned lots, payments, and handovers.
- Kept existing JSON limits, JWT role enforcement, ORM parameterization, ownership checks, upload validation, security headers, and development/production OTP separation.
- Updated final README and API/application wiring without modifying Android or website code.

### Sync

Operations are deduplicated by authenticated collector plus client `operationId`; retries are safe and return `ALREADY_APPLIED`. Batches are capped at 100. Server timestamps are returned for pull cursors. Stale lot updates return `CONFLICT`; financial and transaction operations are not handled with last-write-wins.

### Tests

- `npm run db:generate` — passed.
- `npm run db:migrate` / MongoDB `db push` — passed; `SyncOperation` collection and indexes synchronized.
- `npm run build` — passed.
- `npm run test` — passed: 10 existing tests.
- `npm run lint` — passed.

### Known Issues

- Dedicated Phase 9 sync, security, integration, load, and photo-retry test suites were not added in this pass.
- Sync currently supports LOT and PAYMENT operations only; quote-request and handover queued mutations remain deferred pending contract-specific reconciliation rules.
- Delta sync uses a server timestamp filter rather than a fully opaque cursor; clients should retain the returned server time and use inclusive filtering.

### Deferred

Recycler Portal UI, Admin Dashboard UI, digital payment gateways, ML classification, advanced analytics, advanced reputation/rating, messaging, and environmental-impact features.

### Backend Status

FOUNDATION COMPLETE for the implemented MVP API scope; production rollout still requires dedicated integration/load/security testing and operational backup verification.

## Backend Phase 8 — Payment & Earnings API

### Completed

- Added MongoDB `Payment`, `PaymentAudit`, and `PaymentDispute` models with ownership and lifecycle indexes.
- Added collector payment record/list/detail/edit/dispute APIs and earnings ledger summary.
- Added admin payment list/detail/verify APIs with audit events.
- Added confirmed-handover eligibility, server-side amount/date validation, duplicate prevention, quote anomaly flags, 24-hour correction window, and Asia/Kolkata reporting semantics.

### APIs

- `POST/GET /api/v1/payments/record`, `/payments`, `/payments/:paymentId`
- `PUT /api/v1/payments/:paymentId`
- `POST /api/v1/payments/:paymentId/dispute`
- `GET /api/v1/earnings/ledger`
- `GET /api/v1/admin/payments`, `/admin/payments/:paymentId`
- `POST /api/v1/admin/payments/:paymentId/verify`

### Tests

- MongoDB push, Prisma generation, seed compatibility, build, lint, and all 10 existing tests passed.

### Known Issues

- Dedicated Phase 8 integration tests, recycler-side payment dispute access, and admin dispute resolution UI are deferred.

### Deferred

Digital payment gateways, PhonePe/Google Pay, bank APIs, full offline synchronization, notification delivery, Recycler Portal UI, and Admin Dashboard UI.

### Next Phase

Backend Phase 9 — Offline Sync, Android Integration & Final Security Hardening.

## Backend Phase 7 — Handover & Disputes

### Completed

- Added MongoDB `Handover` and `Dispute` models with accepted-quote, lot, collector, and recycler relations, indexes, and unique handover references.
- Added collector handover creation/retrieval/reference/mark/dispute actions, recycler list/detail/confirm/reject actions, and admin dispute list/resolve endpoints.
- Added QR reference payloads, seven-day expiry enforcement, original/actual weight preservation, 5% discrepancy threshold, material mismatch disputes, and transactional resolution.

### Tests

- `npm run db:generate`, `npm run db:migrate`, `npm run build`, `npm run test` (10 passed), and `npm run lint` passed.

### Known Issues

- Dedicated Phase 7 integration tests, background expiry reminders, and notification delivery are deferred; expiry is enforced synchronously.

### Deferred

Payment API, Earnings API, full notification delivery, offline synchronization, Recycler Portal UI, Admin Dashboard UI, digital payments, and advanced dispute automation.

### Next Phase

Backend Phase 8 — Payment & Earnings API.

## Backend Phase 6 — Quote API & Quote Management

### Completed

- Added `QuoteRequest`, `Quote`, and `QuoteAudit` MongoDB models with collector, lot, and recycler relations and lifecycle indexes.
- Added collector quote request, pending/list, detail, accept, and reject APIs.
- Added recycler request list/detail and quote submission APIs with dedicated RECYCLER JWT authorization.
- Added verified-recycler/material eligibility checks, duplicate pending-request prevention, server-side totals, validity bounds, market comparison, anomaly flagging, and transactional quote acceptance.
- Added safe quote audit records for request and submission events.

### APIs

- `POST /api/v1/quotes/request`
- `GET /api/v1/quotes/pending`
- `GET /api/v1/quotes/:quoteId`
- `POST /api/v1/quotes/:quoteId/accept`
- `POST /api/v1/quotes/:quoteId/reject`
- `GET /api/v1/recycler/quote-requests`
- `GET /api/v1/recycler/quote-requests/:requestId`
- `POST /api/v1/recycler/quotes`

### Business Logic and Security

- Quote requests transition lots from `CREATED` to `QUOTE_REQUESTED`; valid submissions transition requests to `ACCEPTED` and lots to `QUOTE_RECEIVED`.
- Quotes default to 24-hour validity and are checked against server time before acceptance.
- Only one quote can be accepted for a lot; other SENT quotes are rejected transactionally.
- Collector and recycler ownership is derived from JWT identity; collectors cannot submit recycler quotes and recyclers cannot access other recyclers' requests.

### Tests

- MongoDB schema push, client generation, development seed, build, lint, and all 10 existing automated tests passed.

### Known Issues

- Dedicated Phase 6 integration tests and background expiry cleanup are not yet added; read/action paths enforce expiry synchronously.
- Admin/recycler login UI and notification delivery are deferred.

### Deferred

Handover API, Handover confirmation, Disputes, Payment API, Earnings API, Offline synchronization, Push/SMS delivery, Recycler Portal UI, Admin Dashboard UI, ML quote prediction, and automated negotiation.

### Next Phase

Backend Phase 7 — Handover & Disputes.

## Backend Phase 5 — Recycler API & Matching

### Completed

- Added Recycler, RecyclerMaterial, RecyclerRate, and authorization-audit collections.
- Added authenticated recycler discovery, details, material/location/radius filtering, pagination, and proximity/rate sorting.
- Added owned-lot matching endpoint with verified-only eligibility, material/weight compatibility, service-area checks, distance calculation, and explainable scoring.
- Added admin-only recycler authorization list/detail/update endpoints with audit records.
- Added Mumbai/Pune development recycler fixtures with varied materials, rates, availability, ranges, and authorization states.

### APIs

- `GET /api/v1/recyclers`
- `GET /api/v1/recyclers/:recyclerId`
- `GET /api/v1/recyclers/match?lotId=...`
- `GET /api/v1/admin/recyclers`
- `GET /api/v1/admin/recyclers/:recyclerId`
- `PUT /api/v1/admin/recyclers/:recyclerId/authorization`

### Matching

Only VERIFIED recyclers accepting the lot material and within their configured service area are eligible. Scores include material, distance, availability, offered-rate presence, verification, and real rating factors. Missing coordinates/rating/rate remain neutral; no travel time is fabricated.

### Security and Tests

Collector ownership and JWT protection are reused. Admin routes require an ADMIN JWT. MongoDB push and seed completed; build, lint, and all 10 existing tests passed. Dedicated recycler integration tests remain a known follow-up.

### Deferred

Quote API, Handover, Disputes, Payments, Earnings, Offline Sync, Recycler Portal UI, Admin Dashboard UI, ML Matching, and Historical Match Prediction.

### Next Phase

Backend Phase 6 — Quote API.

## Backend Phase 4 — Price API & Auto-Valuation

### Completed

- Added MongoDB Prisma models for `Price`, `PriceHistory`, and `PriceAudit` with query indexes.
- Added seeded development prices for multiple materials, locations, and dates.
- Added authenticated price board and history APIs with validation, timestamps, range values, location matching, and calculated trend direction.
- Added owned-lot valuation endpoint using server-controlled condition multipliers, neutral quality adjustment, two-decimal rounding, and persisted `estimatedValue`.
- Added admin-only price update foundation with historical preservation and audit records; collector JWTs cannot update prices.
- Extended JWT claim validation to recognize an ADMIN role without adding admin UI.

### Database

- Added `Price`, `PriceHistory`, and `PriceAudit` collections and indexes through `prisma db push`.
- Seed data is explicitly development/test data and must not be presented as live market data.

### APIs

- `GET /api/v1/prices/board`
- `GET /api/v1/prices/history`
- `GET /api/v1/lots/:lotId/valuation`
- `PUT /api/v1/admin/prices/:priceId`

### Pricing Logic

- Trend compares the latest market price against the average of earlier returned history; changes above 1% are UP/DOWN, otherwise STABLE.
- Valuation is `marketPrice × weight × conditionMultiplier × 1.0`, rounded to two decimals.
- Recycler bids and ML quality classification are intentionally absent.

### Tests

- `npm run db:migrate` / `prisma db push` — passed against configured MongoDB.
- `npm run db:generate` — passed.
- `npm run db:seed` — passed.
- `npm run build` — passed.
- `npm run test` — passed: 10 existing tests.
- `npm run lint` — passed.
- Live health endpoint — passed with MongoDB connected.

### Known Issues

- No dedicated Phase 4 integration test suite has been added yet; live board/valuation walkthrough requires a fresh OTP session and is not claimed here.
- Admin token issuance is an authorization foundation only; no admin login or dashboard exists.

### Deferred

Recycler bid integration, Recycler API, Quote API, Handover, Payments, Earnings, offline sync, Admin Dashboard, Recycler Portal, and ML quality classification.

### Next Phase

Backend Phase 5 — Recycler API & Matching.

## Database configuration update — MongoDB

- Switched the Prisma datasource from PostgreSQL to MongoDB.
- Converted SQL-native fields to MongoDB-compatible scalar fields and mapped model IDs to MongoDB `_id`.
- Replaced the migration script with `prisma db push`; historical SQL migration files are retained as prior-phase artifacts and are not executed for MongoDB.
- Updated health checks to use MongoDB `ping` and updated local setup documentation.
- The supplied connection string is represented only with a password placeholder in `.env.example`; the real credential must be provided locally.

## Backend Phase 3 — Lot & Photo API

Status: implementation complete; MongoDB database connectivity and source verification passed. S3 upload remains configuration-dependent.

### Completed

- Added Prisma Lot model, lifecycle enums, collector relation, indexes, and version field.
- Added authenticated create/list/detail/update/cancel lot endpoints with ownership enforcement, pagination, filters, safe lifecycle locking, and optimistic concurrency.
- Added server-controlled `LOT-{timestamp}-{random}` IDs and server-owned lifecycle/value fields.
- Added multipart photo upload with memory limits, MIME/type parsing through Sharp, minimum dimensions, JPEG normalization/compression, S3-compatible storage abstraction, safe object keys, and failure cleanup.
- Added stable lot/photo error codes and OpenAPI/README documentation.
- Added lot service/repository seams suitable for future Android Retrofit integration.

### Database Changes

- Added Lot model for MongoDB; no price/recycler/quote/handover/payment models were added.

### APIs

- `POST /api/v1/lots`
- `GET /api/v1/lots`
- `GET /api/v1/lots/:lotId`
- `PUT /api/v1/lots/:lotId`
- `DELETE /api/v1/lots/:lotId`
- `POST /api/v1/lots/:lotId/photo`

### Storage

- Added `StorageService` and `S3StorageService`; credentials/configuration are environment-only.

### Tests

- `npm run db:generate` — passed.
- `npm run db:push` — passed against the configured MongoDB cluster; Collector/Lot collections and indexes are synchronized.
- `npm run db:seed` — passed; development collector upserted.
- `npm run build` — passed.
- `npm run test` — passed: 10 tests.
- `npm run lint` — passed.
- Live `GET /api/v1/health` — passed with database status `connected`.

### Known Issues

- Photo uploads require S3-compatible configuration; the server intentionally keeps the API available while returning a storage error if unconfigured.
- Full authenticated live lot/photo workflow and image fixture tests still require S3-compatible storage configuration.

### Deferred

Price API, auto-valuation, Recycler API, matching, Quote API, Handover, Payments, Earnings, offline sync, Admin, and Recycler Portal.

### Next Phase

Backend Phase 4 — Price API & Auto-Valuation.

## Backend Phase 2 — Authentication & Collector API

Status: implementation complete; runtime verification pending environment setup.

### Completed

- Added request, verify, and stateless logout endpoints under `/api/v1/auth`.
- Added isolated `OtpProvider` boundary with explicit development provider and Twilio Verify integration using environment credentials.
- Added OTP expiry, five-attempt lockout, cooldown, per-phone and per-IP request limits, and verification throttling.
- Added collector create/login on successful OTP verification, stable database-generated collector IDs, last-login updates, and configurable JWT issuance.
- Completed server-side Bearer authentication with collector existence and ACTIVE/SUSPENDED/DELETED checks.
- Added protected profile update endpoint for preferred language and primary location with validation; immutable fields are not accepted for update.
- Added masked security event logging without OTP/JWT/secret values.
- Added authentication, profile, OTP provider, and abuse-control tests plus updated API documentation.

### API Endpoints

- `POST /api/v1/auth/request-otp`
- `POST /api/v1/auth/verify-otp`
- `POST /api/v1/auth/logout`
- `GET /api/v1/collectors/me`
- `PUT /api/v1/collectors/me`

### Database Changes

- No new tables; the Phase 1 Collector model is reused.

### Security

- Development OTP is explicitly disabled in production configuration.
- Provider credentials are environment-only; OTP and JWT values are not logged or returned in request responses.
- Input validation, rate limits, lockout, safe errors, and account-status checks are enforced server-side.

### Tests

- Added unit and HTTP tests for validation, JWT, health/404, OTP/provider boundaries, and authentication contracts.
- `npm install`, `npm run build`, `npm run test`, `npm run lint`, migrations, seed, and live HTTP testing could not be executed because npm installation did not complete and PostgreSQL is unavailable in this environment.

### Known Issues

- Twilio Verify live behavior requires configured credentials and network access.
- Runtime/database verification must be rerun on a host with npm registry access and PostgreSQL.

### Deferred

Lot API, Photo Storage, Price API, Recycler API, Matching, Quote API, Handover API, Dispute System, Payment API, Earnings API, Sync API, Admin API, and Recycler Portal.

### Next Phase

Backend Phase 3 — Lot & Photo API.

## Current Phase

Backend Phase 1 — Foundation & Project Setup.

## Completed

- Created Node.js, TypeScript, Express, Prisma, PostgreSQL backend structure.
- Added validated environment configuration and `.env.example`.
- Added Helmet, configured CORS, JSON body limit, request IDs, structured request logging, 404 handling, safe central errors, and graceful shutdown.
- Added `/api/v1/health` with degraded database status handling.
- Added Collector Prisma model with language/status enums, Indian phone constraint, coordinates, UTC timestamps, and indexes.
- Added collector repository/service/controller and protected `/api/v1/collectors/me`.
- Added JWT generation/verification and Bearer authentication middleware.
- Added reusable Zod validators and standardized success/error response helpers.
- Added development-only idempotent seed collector.
- Wired future route namespaces without implementing future business APIs.

## Files and folders

`src/config`, `controllers`, `middleware`, `repositories`, `routes`, `services`, `types`, `utils`, `app.ts`, `server.ts`; `prisma/schema.prisma`, `prisma/seed.ts`; `tests/`.

## Database

Collector model only in this phase. Migration generation requires a reachable PostgreSQL instance.

## APIs

Only health and authenticated collector profile retrieval are implemented.

## Tests

Automated validation, JWT, health, 404, and app error-path tests are included. Runtime execution is blocked in this environment because `npm install` does not complete and PostgreSQL/psql is unavailable; no tests are claimed as passed.

## Known issues

- PostgreSQL CLI/service is not available in this environment, so database migration, seed, and live health verification require a configured PostgreSQL instance.
- npm dependency installation did not complete, so build/lint/test execution must be rerun on a host with npm registry access.
- No Android files or website files were modified.

## Deferred

OTP/SMS, login/registration, lots, photos, prices, recyclers, matching, quotes, handovers, payments, earnings, synchronization, disputes, admin APIs, and website work.

## Next Phase

Backend Phase 2 — Authentication & Collector API.
# Email roles and Android-first contract — 2026-09-05

- Added a MongoDB `User` account profile with email, password hash, explicit role, language, account status, and profile ids.
- Added email signup/login/profile endpoints; recycler signup always starts pending.
- Added verified-status enforcement on recycler quote submission and handover review.
- Existing phone/OTP collector auth and API contracts remain available during migration.
