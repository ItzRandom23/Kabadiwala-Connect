# Kabadiwala Connect — Backend Final Verification

Verification date: 2026-09-15  
Target specification: `C:\Users\pulki\Downloads\KABADIWALA_CONNECT_BACKEND_MASTER_PROMPT.md`  
Source of truth: current files under `backend/`  
Scope: backend verification plus the implementation pass requested after the initial audit. Android/frontend changes were preserved and not treated as backend proof.

The attached master prompt was treated as a target specification. Its implementation instructions were not treated as authorization to change the repository. Existing documentation and tests were treated as evidence only and were checked against the implementation.

## 1. Overall backend status

The percentages below are deliberately conservative, requirement-based estimates rather than lines-of-code measurements. The master prompt is prose and does not define a numeric scoring rubric.

| Measure | Result | Basis |
|---|---:|---|
| Implementation completeness | **79%** | The main inventory, pooling, formal handover, QC, payment/reversal evidence, disposal-evidence, passport, reassignment, and role-aware retry primitives now exist; external-provider and operational workflows remain partial. |
| Verified working completeness | **36%** | Build, lint, focused service/helper tests, role-linkage tests, and mocked formal-sync transactions pass; there is still no live MongoDB integration or concurrency proof, and the newest route workflows have no integration tests. |
| Differentiation-feature completeness | **71%** | The differentiators have substantial end-to-end source support, including grade-aware routing, pooling, QC, evidence, anomaly and reassignment paths; external settlement, unified economics, and production proof remain incomplete. |
| Security/authorization confidence | **MEDIUM (SOURCE-VERIFIED, LIVE-UNPROVEN)** | Owner predicates, role guards, linked-account revalidation, conditional claims, and privacy-scoped passports are present; live IDOR/concurrency testing is unavailable. |
| Test-suite status | **PASSING UNIT GATES / INCOMPLETE VERIFICATION** | Build, lint, and 68 tests pass; live transactions, seed, integration, and load/adversarial tests were not run. |

### Final verdict

## 🔴 BACKEND NOT COMPLETE

The backend is not ready to freeze as the authoritative API contract. The previously identified source defects were addressed in this implementation pass, and the source now includes strict geographic/capability-aware aggregation, grade-aware routing, formal QC and anomaly resolution actions, household-controlled reassignment, persisted notifications, automated Recycler-expiry handling, pickup/formal payment evidence, and role-aware generalized formal sync operations. Actual external payment execution, granular OpenAPI schemas, and live MongoDB proof of the critical transactions remain.

## 2. Complete and verified

These are bounded areas that were actually supported by source plus tests. They must not be read as a claim that the surrounding business workflow is complete.

- TypeScript compilation, Prisma client generation, and the configured lint-equivalent check pass.
- JWT signing/verification and malformed/expired-token behavior are covered by the JWT tests.
- Refresh-token rotation/reuse behavior is covered by the session tests.
- Environment guards cover production secrets, CORS, storage, OTP, and rate-limit configuration.
- Role middleware unit tests verify basic denial of Household-to-Collector and Collector/Recycler-to-Household access.
- The formal QR helper creates and verifies signed payloads; tampering is covered by the traceability/formalisation tests.
- Legacy lot, quote, payment, and handover service state cases have unit coverage.
- Inventory invariant helper tests reject negative balances and invalid movement deltas.
- Settlement-variance helper tests cover the configured weight/rate/material rules.
- Formal settlement now rejects actual weight above the quoted/reserved source and accepted weight above actual weight; the pure guard is covered by `formalisationIntegrity.test.ts`.
- Formal offline collector confirmation is transactionally recorded with its sync operation, audit event, and material-passport event; this path has a mocked transaction test in `syncService.test.ts`.
- Account-backed route registration now passes Prisma-backed role/status revalidation into Collector/Household middleware, with mismatch tests in `roleAuthorization.test.ts`.
- Recycler verification validation tests cover required evidence on submission/verification and administrator-only authorization in the tested service/controller path.
- Notification service ownership behavior and price repository/service calculations have focused unit coverage.

No complete end-to-end differentiating capability could be promoted to this section because the required route, transaction, ownership, state-machine, and retry behavior is not proven together.

## 3. Partial

### Collector-first informal → formal bridge

Household listings, pickup assignment, collector inventory, bulk lots, offers, pooling, signed supply handovers, formal/pickup settlement payments, disposal evidence, linked passport projections, and household-controlled replacement assignment exist. The bridge remains partial because disputed pickup inventory reversal/reconciliation policy is not implemented and the full chain is not live-tested as one workflow.

### Formal Route Advantage Engine

`GET /api/v1/kabadiwala/route-advantage` calculates gross, configured-or-fallback logistics cost, net value, a price baseline, confidence, freshness, observations, grade/weight/minimum-lot and distance eligibility, active demand capacity/cap, a rating/handover/payment reliability signal, and explanation reasons. It remains partial because the logistics estimate is not a provider quote and the reliability signal is not a historical risk model.

### Cooperative Collector Pooling

Pool creation, separate contributions, preferred-grade enforcement, server-derived rates, conditional inventory reservation, atomic aggregate updates, leave, lock, expiry release, formal handover, proportional allocation, per-contributor settlement, and formal payment allocation are present. Demand-linked opportunity/suggestion reads now filter by preferred grade, verified Recycler capability, active authorization, and distance when coordinates exist. Live MongoDB concurrency and full payment reconciliation remain unverified.

### Collector Growth Passport

`refreshPassport` derives pickup, formal-handover, safety, cancellation, no-show, dispute, and feedback values and explicitly labels the result as platform-generated. It is not a government credential. The derivation mixes direct handovers and pooled contribution counts, and its dispute ratio reads the legacy `Dispute` model rather than all new anomaly/formal settlement records.

### Offline dual-confirmation Material Passport

The new formal path has signed QR/nonce data, collector confirmation, Recycler confirmation, request hashes, expiry, anomaly states, and role-aware generalized `SUPPLY_HANDOVER`, `POOL_CONTRIBUTION`, `POOL_SETTLEMENT`, and `SUPPLY_PAYMENT` sync operations. `/sync/changes` returns collector- or Recycler-scoped formal handovers, payments, settlements, anomalies, and events. Dedicated APIs remain available as the online equivalent; the sync vocabulary now converges on the same server-authoritative state transitions.

### End-to-end Material Passport

The household listing passport now joins source listing, pickup, inventory movement, bulk/pool contribution, handover, settlement, anomaly, disposal-evidence, and downstream event projections. The formal handover passport joins its source listings/pickups, inventory movements, settlements, formal payments, and events. It remains a projection assembled from scalar links, not a single immutable graph, and is not live-tested.

### Fairness & Dispute Guard

Household and Recycler changes require reason codes for material settlement changes; formal variance records before/after values, actor, time, reason, and optional evidence; payment disputes, delayed/underpaid payments, QC failures, pickup cancellations, and pool/settlement issues create anomaly flags; and admins can list/resolve formal anomaly flags. Admin resolution supports accept-as-recorded, revert-to-quote, reservation release, and acknowledgement actions, while a separate admin route records provider-neutral reversal evidence for formal payments. External bank/cash reversal execution remains outside this backend.

### Settlement Anomaly Detection

Deterministic variance rules flag material mismatch, partial acceptance, >5% weight change, and >10% rate change; source quantity is rejected before settlement when exceeded. Repeated/late/no-show pickup cancellations, delayed formal payment, underpayment, duplicate-source allocation attempts, and QC failures now also create deterministic anomaly records. There is no statistical detector for historical outliers or broader behavioral risk.

### Reverse Recycler-Demand Network

Recycler procurement requirements, updates, listing, verified-recycler filtering, supply aggregation, gap, and deadline fields exist. Create and PATCH validation enforce `minimumLotKg <= requiredQuantityKg`; demand intelligence and pooling projections now apply grade, capability, active authorization, and a strict coordinate/radius policy. A full per-demand geo index remains.

### Demand-triggered Pool Suggestions

Suggestions are based on active demand and aggregate inventory without contributor identities. They now filter preferred grade, verified Recycler capability, active authorization, and collector-to-Recycler distance; records without complete coordinates are not treated as eligible. They still do not expose a full automatic contributor-selection workflow.

### Explainable Matching

Legacy Recycler matching returns score and reasons, while route advantage returns `whyThisMatch`. The two engines are not unified; legacy matching does not use net route economics, demand, minimum lot, or real reliability, and route advantage does not provide a complete eligibility decision.

### Confidence-aware Price Intelligence

The price board returns range, reference price, source classification, freshness, observation count, trend, demo flag, and confidence. The history response now also returns observation count, aggregate confidence, latest freshness, explicit location-match/fallback context, per-row source/age, and demo indicators. Confidence remains a deterministic heuristic rather than a market forecast, and route-level tests are absent.

### Offline backend reconciliation/idempotency

Some APIs use unique source keys, conditional updates, or optional `Idempotency-Key` records. `/sync` remains sequential, but now accepts role-aware transactional formal pool contribution, settlement decision, handover confirmation, QC, disposal evidence, and payment operations; `/sync/changes` includes role-scoped formal supply entities plus pickup settlement payments and an opaque per-feed timestamp/ID cursor. Legacy LOT/PAYMENT mutation plus sync-ledger writes are not one transaction.

### Safety-aware Material Routing

`GET /api/v1/safety-routing` returns structured hazard level, warning code, route, required capability, and guidance ID. The map is hardcoded, has no content/version lifecycle, and is only indirectly linked to Recycler evidence through the formal handover capability. Safe Android presentation remains separate work.

### Recycler Authorization Evidence + Freshness

Evidence fields, operator authorization, validity date, a freshness check, stale-to-`EXPIRED` transition on authenticated access, and an hourly server-side expiry sweep with lifecycle audit records exist in the main verified-Recycler/account path. Operator review/renewal workflow, notification delivery, and the secondary lifecycle remain only partially unified.

### Data-safe E-Waste Disposal evidence support

Household listings store data-bearing, owner-preparation, destruction-request, and evidence-status/reference fields. Verified Recyclers can submit idempotent disposal evidence for completed formal handovers, which updates the handover, source listings, audit trail, and passport projection. Physical evidence storage/authenticity is outside this backend.

### Pickup reliability / reassignment

Pickup timestamps, no-show, late-cancellation, reassignment, status transitions, active replacement options, household reassignment selection, reassignment notification, and derived passport metrics exist. Transfer is an explicit Household action rather than automatic assignment, and household settlement still records a dispute without reversing inventory already acquired.

### Strict role boundaries

Route-level role gates and owner predicates are implemented. `/kabadiwala/listings` now scopes assigned listing IDs to the authenticated Kabadiwala. Account-backed app registration passes Prisma role/status revalidation through Collector and Household middleware; legacy profiles without a linked `User` remain supported for compatibility. Live IDOR coverage is still absent.

## 4. Missing

The following target requirements are absent or have no usable backend workflow:

### Required capability APIs that are absent or still partial

The master prompt does not prescribe literal URI names for every capability, so these are expressed as capability-level endpoint gaps rather than invented paths:

| Required API capability | Current result |
|---|---|
| Generalized formal offline sync/reconciliation for pool contribution, settlement decision, Recycler confirmation, and disposal evidence | `/sync` accepts role-aware formal operations with conditional transactions, QR/nonce verification for Recycler confirmation, replay ledger writes, and formal change download. Live route/replay/concurrency proof remains unavailable. |
| Recycler/operator submission of data-destruction evidence | Implemented at `POST /recycler/handovers/:handoverId/disposal-evidence`; physical evidence storage and authenticity verification remain outside the backend. |
| Unified end-to-end material-passport retrieval | Implemented as privacy-scoped projections at household listing and formal handover passport routes; it is scalar-link assembled and live-unverified. |
| Admin review/resolution for formal settlement anomalies | Implemented at `GET/POST /admin/formal-anomalies`; internal formal settlement/reservation/payment rows support audited accept, revert, release, and acknowledgement actions. External payment reversal remains outside the backend. |
| Authoritative formal payment/earnings API | Implemented with `SupplyPayment`, Recycler payment recording, Collector payment confirmation/dispute, and formal entries in the Collector earnings ledger; no external payment gateway settlement exists. |

- A live-proven reconciliation environment for formal pool contributions, settlement decisions, Recycler confirmation, disposal evidence, payments, and conflicts; the role-aware operation vocabulary and conditional source logic are implemented, but live execution is unavailable.
- A durable geospatial index/selection engine; the current demand/pooling reads apply a strict complete-coordinate policy, preferred grade, capability, and authorization, but use in-memory distance calculations.
- A route-advantage engine backed by external logistics quotes and a historical reliability/risk model; current source uses configured/fallback logistics and deterministic platform signals.
- An operator review/renewal workflow and notification policy for expired Recycler authorizations; automated expiry itself is now covered by request middleware and an hourly server-side sweep.
- External bank/cash reversal execution; the backend now records provider-neutral reversal evidence and internal `REVERSED` state, but cannot call or verify a provider.
- A shared, tested state-machine implementation for pickup, pool, supply handover, offer, authorization, QC, and settlement. The formal routes use inline conditional updates and do not cover every declared state.
- Live MongoDB integration tests, concurrent transaction tests, and route-level IDOR tests for the new endpoints; a Docker Compose testing definition exists, but the local Docker engine was unavailable during this pass.
- Granular per-route OpenAPI request/response schemas and examples; `backend/openapi.yaml` now inventories all 170 operations and `docs/BACKEND_API_CONTRACT.md` carries the detailed semantics.

## 5. Broken

No currently reproducible source defect from the initial audit remains after the implementation pass. The following previously reported defects are resolved in source but still require live MongoDB verification:

| Finding | Source fix | Remaining proof gap |
|---|---|---|
| B-01 cross-household matched-listing disclosure | Assigned listing IDs are filtered by the authenticated `kabadiwalaId`; household listing/passport queries remain owner-scoped. | No live IDOR route test. |
| B-02 expired formal handover stranded reservations | Expiry atomically releases pool contributions or bulk-lot reservations, records inventory movements, and returns the pool/lot to a recoverable state. | No live transaction/concurrency test. |
| B-03 competing bulk offers | Lot and offer are claimed with conditional `updateMany` status predicates; counters are also conditional. | No live concurrent offer test. |
| B-04 settlement over source quantity | `assertSettlementQuantity` rejects actual weight above quoted source and accepted weight above actual weight before settlement. | No live settlement test. |
| B-05 legacy handover confirmation bypass | Legacy `HandoverService.confirm` now requires `collectorConfirmedAt`. | No route-level legacy handover test. |
| B-06 pool parent remains stale | After the final contributor accepts, the parent pool becomes `SETTLED` and the review handover becomes `COMPLETED`. | No live multi-contributor test. |
| B-07 impossible demand minimum | Create and update both enforce `minimumLotKg <= requiredQuantityKg`. | No route-level demand validation test. |

The remaining inability to certify these fixes is environmental: no testing MongoDB is available in this workspace, so the critical HTTP, transaction, and concurrency reproductions could not be executed.

## 6. Implemented but unverified

The following code appears materially implemented but was not proven end-to-end because there is no configured test MongoDB and no route integration suite:

- Household listing → pickup → inventory completion.
- Pickup cancellation, no-show/reassignment, rescheduling, and household settlement.
- Bulk-lot reservation/release and offer lifecycle.
- Procurement demand update and collector opportunity views.
- Pool creation, join, leave, lock, formal preparation, and allocation.
- Recycler authorization evidence, expiry checks, rates, offers, and formal confirmation.
- Route advantage, demand intelligence, pool suggestions, passport, safety routing, and anomaly query responses.
- Formal collector confirmation and idempotency-key replay behavior.
- Dataset import/export privacy projections.
- Future conversations, reviews, preferences, rewards, schemes, and AI/template suggestions.
- Runtime Mongo index maintenance: `ensureOptionalUniqueIndexes` is best effort and the test suite emitted a warning that Prisma could not decode the MongoDB `listIndexes` output. Optional uniqueness enforcement therefore needs staging proof.

## 7. Endpoint audit

The inventory below contains **170 endpoint registrations** (excluding router-level middleware mounts). All routes are mounted below `/api/v1` unless the prefix is shown. `COLLECTOR` is the backend role name used for Kabadiwala. `⚠️` means code exists but the route behavior is not adequately proven by tests. `♻️` identifies the legacy path that overlaps the newer formal supply-chain path; it remains mounted and therefore must either be frozen and supported or explicitly retired.

### Health, identity, and collector profile

| Method | Route | Allowed role(s) | Purpose | Status | Tests | Problems |
|---|---|---|---|---|---|---|
| GET | `/health` | Public | Liveness/database health | ✅ COMPLETE & VERIFIED | `app.test.ts` | No live production health check. |
| POST | `/auth/request-otp` | Public | Request phone OTP | ⚠️ IMPLEMENTED BUT UNVERIFIED | `app.test.ts`, `authService.test.ts` | Thin route integration coverage. |
| POST | `/auth/verify-otp` | Public | Verify OTP and issue account token | ⚠️ IMPLEMENTED BUT UNVERIFIED | `authService.test.ts` | Role-registration path not route-tested; legacy no-registration path issues Collector identity. |
| POST | `/auth/refresh` | Public | Rotate refresh token | ⚠️ IMPLEMENTED BUT UNVERIFIED | `sessionService.test.ts` | No route-level session test. |
| POST | `/auth/logout` | Public/token | Revoke refresh session | ⚠️ IMPLEMENTED BUT UNVERIFIED | `sessionService.test.ts` | Access-token revocation is not stateful. |
| POST | `/auth/signup` | Public | Email/password signup | ⚠️ IMPLEMENTED BUT UNVERIFIED | `app.test.ts` | No complete role/account integration test. |
| POST | `/auth/login` | Public | Email/password login | ⚠️ IMPLEMENTED BUT UNVERIFIED | Service coverage only | No route-level failure/role coverage. |
| POST | `/auth/admin-login` | Public | Admin login | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No endpoint/adversarial test. |
| GET | `/auth/profile` | Household/Collector/Recycler | Current account profile | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Shared contract and role-specific projection not frozen. |
| GET | `/collectors/me` | Collector | Read own Collector profile | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No route test. |
| PUT | `/collectors/me` | Collector | Update own Collector profile | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No route or IDOR test. |

### Legacy lot, price, Recycler, and quote routes

| Method | Route | Allowed role(s) | Purpose | Status | Tests | Problems |
|---|---|---|---|---|---|---|
| POST | `/lots` | Collector | Create legacy lot | ♻️ DUPLICATE | `lotService.test.ts` | Separate from HouseholdListing/BulkLot path; no route test. |
| GET | `/lots` | Collector | List own legacy lots | ♻️ DUPLICATE | None | No route test. |
| GET | `/lots/:lotId` | Collector | Read own lot | ♻️ DUPLICATE | None | IDOR route test absent. |
| PUT | `/lots/:lotId` | Collector | Update own mutable lot | ♻️ DUPLICATE | None | Legacy state/contract not frozen. |
| DELETE | `/lots/:lotId` | Collector | Cancel own lot | ♻️ DUPLICATE | `lotService.test.ts` | No route test. |
| GET | `/lots/:lotId/photo` | Collector | Read own lot photo | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Storage behavior is deployment-dependent. |
| POST | `/lots/:lotId/photo` | Collector | Upload own lot photo | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No upload/ownership adversarial test. |
| GET | `/prices/board` | Household/Collector/Recycler | Price range, freshness, confidence | ⚠️ IMPLEMENTED BUT UNVERIFIED | `priceService.test.ts` | No route test; fallback context is implicit. |
| GET | `/prices/history` | Household/Collector/Recycler | Historical prices | ⚠️ IMPLEMENTED BUT UNVERIFIED | `priceRepository.test.ts` | History now returns observation count, confidence, freshness, source context, and demo/source fields; no route test. |
| GET | `/lots/:lotId/valuation` | Collector | Value estimate for own legacy lot | ♻️ DUPLICATE | None | Does not value HouseholdListing. |
| PUT | `/admin/prices/:priceId` | Admin with `PRICE_MANAGEMENT` | Update validated price | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No endpoint/permission integration test. |
| GET | `/recycler/profile` | Recycler, including pending | Read own profile | ⚠️ IMPLEMENTED BUT UNVERIFIED | `recyclerVerification.test.ts` | No route test. |
| POST | `/recycler/verification-request` | Recycler, including pending | Submit authorization evidence | ⚠️ IMPLEMENTED BUT UNVERIFIED | `recyclerVerification.test.ts` | Renewal submission exists; no route/operator review integration test. |
| PATCH | `/recycler/profile` | Verified Recycler | Update operating profile | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No route test. |
| PUT | `/recycler/rates` | Verified Recycler | Update buying rates | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Omitted rate rows do not remove corresponding material capability rows. |
| GET | `/recyclers` | Household/Collector/Recycler | Browse verified Recycler directory | ⚠️ IMPLEMENTED BUT UNVERIFIED | `recyclerServiceOptimization.test.ts` | No endpoint/privacy test. |
| GET | `/recyclers/match` | Collector | Match legacy lot to Recycler | 🟡 PARTIAL | `recyclerServiceOptimization.test.ts` | Explainable but disconnected from formal net economics/demand. |
| GET | `/recyclers/:recyclerId` | Household/Collector/Recycler | Read public Recycler detail | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Public-field contract not frozen. |
| GET | `/admin/recyclers` | Admin with `RECYCLER_REVIEW` | Review queue | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No admin endpoint test. |
| GET | `/admin/recyclers/:recyclerId` | Admin with `RECYCLER_REVIEW` | Review detail | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No admin endpoint test. |
| PUT | `/admin/recyclers/:recyclerId/authorization` | Admin with `RECYCLER_AUTHORIZATION` | Set authorization status/evidence | 🟡 PARTIAL | `recyclerVerification.test.ts` | Automated expiry exists; endpoint/operator renewal behavior is not integration-tested. |
| POST | `/quotes/request` | Collector | Request legacy quote | ♻️ DUPLICATE | `quoteLifecycle.test.ts` | Legacy lot path; no route IDOR test. |
| POST | `/quotes/request-batch` | Collector | Request multiple legacy quotes | ♻️ DUPLICATE | None | No route/duplicate/concurrency test. |
| GET | `/quotes/pending` | Collector | List own pending quotes | ♻️ DUPLICATE | None | No route test. |
| GET | `/quotes/:quoteId` | Collector | Read own quote | ♻️ DUPLICATE | `quotePrivacy.test.ts` | Route IDOR test absent. |
| POST | `/quotes/:quoteId/accept` | Collector | Accept own quote | ♻️ DUPLICATE | `quoteLifecycle.test.ts` | No route/concurrent acceptance test. |
| POST | `/quotes/:quoteId/reject` | Collector | Reject own quote | ♻️ DUPLICATE | `quoteLifecycle.test.ts` | No route test. |
| GET | `/recycler/quote-requests` | Verified Recycler | List incoming quote requests | ♻️ DUPLICATE | None | No endpoint ownership test. |
| GET | `/recycler/quote-requests/:requestId` | Verified Recycler | Read incoming request | ♻️ DUPLICATE | None | No endpoint IDOR test. |
| POST | `/recycler/quotes` | Verified Recycler | Submit legacy quote | ♻️ DUPLICATE | None | No duplicate/expiry endpoint test. |

### Legacy handover, payment, sync, notification, and activity routes

| Method | Route | Allowed role(s) | Purpose | Status | Tests | Problems |
|---|---|---|---|---|---|---|
| POST | `/verify/handover` | Public | Verify signed legacy QR | ♻️ DUPLICATE | `traceability.test.ts`, `formalisationIntegrity.test.ts` | Legacy path is separate from SupplyHandover. |
| POST | `/handovers` | Collector | Create legacy handover | ♻️ DUPLICATE | `handoverLifecycle.test.ts` | Separate duplicate contract. |
| GET | `/handovers/reference/:referenceId` | Collector owner | Read legacy handover by reference | ♻️ DUPLICATE | None | No IDOR endpoint test. |
| GET | `/handovers/:handoverId` | Collector owner | Read legacy handover | ♻️ DUPLICATE | None | No IDOR endpoint test. |
| POST | `/handovers/:handoverId/mark-handed-over` | Collector owner | Record collector-side handover mark | ♻️ DUPLICATE | `handoverLifecycle.test.ts` | Does not change legacy status; Recycler can still confirm before marker. |
| PUT | `/handovers/:handoverId/evidence` | Collector owner | Add legacy evidence | ♻️ DUPLICATE | None | Evidence can race with confirmation. |
| POST | `/handovers/:handoverId/evidence/photo` | Collector owner | Upload legacy evidence photo | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No upload/race test. |
| POST | `/handovers/:handoverId/dispute` | Collector owner | Create legacy dispute | ♻️ DUPLICATE | None | No full evidence/idempotency test. |
| GET | `/recycler/handovers` | Verified Recycler | List legacy handovers | ♻️ DUPLICATE | None | Does not share formal path. |
| GET | `/recycler/handovers/:handoverId` | Verified Recycler | Read legacy handover | ♻️ DUPLICATE | None | No IDOR endpoint test. |
| POST | `/recycler/handovers/:handoverId/confirm` | Verified Recycler | Confirm legacy handover | ♻️ DUPLICATE | `handoverLifecycle.test.ts` | Legacy duplicate now requires `collectorConfirmedAt`; no route test. |
| POST | `/recycler/handovers/:handoverId/reject` | Verified Recycler | Reject legacy handover | ♻️ DUPLICATE | None | Legacy dispute path. |
| GET | `/admin/disputes` | Admin with `DISPUTE_RESOLUTION` | Legacy dispute queue | ♻️ DUPLICATE | None | Does not cover new anomaly flags. |
| GET | `/admin/disputes/:disputeId` | Admin with `DISPUTE_RESOLUTION` | Legacy dispute detail | ♻️ DUPLICATE | None | Does not cover formal settlement reviews. |
| POST | `/admin/disputes/:disputeId/resolve` | Admin with `DISPUTE_RESOLUTION` | Resolve legacy dispute | ♻️ DUPLICATE | None | Formal anomaly resolution is a separate `/admin/formal-anomalies` contract; concurrent resolve not proven. |
| POST | `/payments/record` | Collector | Record legacy payment | ♻️ DUPLICATE | `paymentLifecycle.test.ts` | Not connected to SupplyHandover/PoolSettlement. |
| GET | `/payments` | Collector | List own legacy payments | ♻️ DUPLICATE | `paymentLifecycle.test.ts` | Formal settlements absent. |
| GET | `/payments/:paymentId` | Collector | Read own payment | ♻️ DUPLICATE | None | IDOR route test absent. |
| PUT | `/payments/:paymentId` | Collector | Edit legacy payment | ♻️ DUPLICATE | None | Reason/evidence policy is not target-level. |
| POST | `/payments/:paymentId/dispute` | Collector | Dispute payment | ♻️ DUPLICATE | `paymentLifecycle.test.ts` | Legacy only. |
| GET | `/earnings/ledger` | Collector | Legacy + formal earnings ledger | 🟡 PARTIAL | None | Formal records are included, but external payment settlement is not performed. |
| GET | `/admin/payments` | Admin with `PAYMENT_VERIFICATION` | Payment queue | ♻️ DUPLICATE | None | Formal settlement queue absent. |
| GET | `/admin/payments/:paymentId` | Admin with `PAYMENT_VERIFICATION` | Payment detail | ♻️ DUPLICATE | None | Formal settlement absent. |
| POST | `/admin/payments/:paymentId/verify` | Admin with `PAYMENT_VERIFICATION` | Verify legacy payment | ♻️ DUPLICATE | None | Formal settlement absent. |
| POST | `/sync` | Collector/Verified Recycler | Replay queued legacy and formal operations with role-aware state transitions | 🟡 PARTIAL | `syncService.test.ts` | Formal operation branches and replay ledgers exist; route-level, live replay, and concurrency tests are absent. |
| GET | `/sync/changes` | Collector/Verified Recycler | Pull role-scoped legacy/formal supply deltas | 🟡 PARTIAL | `syncService.test.ts` (cursor codec) | Opaque per-feed timestamp/ID cursor is implemented; live delta/replay tests are absent. |
| GET | `/transactions/:lotId/timeline` | Collector owner | Legacy lot timeline | ♻️ DUPLICATE | None | Not the unified material passport. |
| GET | `/activity/changes` | Household/Collector/Recycler | Activity delta feed | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Cursor/limit contract not tested. |
| GET | `/notifications` | Household/Collector/Recycler | Notification inbox | ⚠️ IMPLEMENTED BUT UNVERIFIED | `notificationService.test.ts` | Limit is not bounded at route boundary. |
| GET | `/notifications/unread-count` | Household/Collector/Recycler | Unread count | ⚠️ IMPLEMENTED BUT UNVERIFIED | `notificationService.test.ts` | No route test. |
| POST | `/notifications/:notificationId/read` | Household/Collector/Recycler | Mark own notification read | ⚠️ IMPLEMENTED BUT UNVERIFIED | `notificationService.test.ts` | No route IDOR test. |
| POST | `/notifications/read-all` | Household/Collector/Recycler | Mark own notifications read | ⚠️ IMPLEMENTED BUT UNVERIFIED | `notificationService.test.ts` | No route test. |

### Household → Kabadiwala → Recycler supply-chain routes

| Method | Route | Allowed role(s) | Purpose | Status | Tests | Problems |
|---|---|---|---|---|---|---|
| POST | `/household/listings` | Household | Create source listing | 🟡 PARTIAL | None | Optional idempotency replay exists; no route/live test and disposal evidence remains downstream. |
| GET | `/household/listings` | Household | List own listings | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No route/IDOR test. |
| GET | `/household/listings/:listingId` | Household owner | Read own listing and pickups | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No route/IDOR test. |
| PATCH | `/household/listings/:listingId` | Household owner | Edit open listing | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No route/state test. |
| GET | `/household/kabadiwalas` | Household | Discover active collectors | 🟡 PARTIAL | None | Optional coordinate/radius filtering now exists; no live route/geo test. |
| POST | `/household/listings/:listingId/pickups` | Household owner | Request a collector pickup | 🟡 PARTIAL | None | Optional idempotency only; no full live race test. |
| GET | `/household/pickups` | Household | List own pickups | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No route test. |
| GET | `/household/pickups/:pickupId` | Household owner | Read pickup/listing | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No route/IDOR test. |
| GET | `/household/pickups/:pickupId/passport` | Household owner | Read source/pickup passport | 🟡 PARTIAL | None | Pickup projection; listing passport is the downstream chain projection. |
| POST | `/household/pickups/:pickupId/reschedule` | Household owner | Reschedule pickup | 🟡 PARTIAL | None | Policy and retry behavior unverified. |
| POST | `/household/pickups/:pickupId/settlement` | Household owner | Accept/dispute final pickup settlement | 🟡 PARTIAL | None | Payment linkage is now persisted; no live payment/reconciliation test and disputed inventory reversal policy remains explicit work. |
| POST | `/kabadiwala/pickups/:pickupId/settlement-payment` | Collector owner | Record household pickup settlement payment | 🟡 PARTIAL | None | Unique source key, payload-hash replay protection, amount anomaly, notification, and conditional completion exist; no live route/concurrency test or provider integration. |
| GET | `/household/listings/:listingId/passport` | Household owner | Read end-to-end listing passport | 🟡 PARTIAL | None | Joins downstream lots/pools/handovers/settlements/events but is scalar-link assembled and untested live. |
| POST | `/household/listings/:listingId/cancel` | Household owner | Cancel open listing | 🟡 PARTIAL | None | Reliability effect and idempotent replay not proven. |
| POST | `/household/pickups/:pickupId/cancel` | Household owner | Cancel open pickup | 🟡 PARTIAL | None | Late-cancellation policy not fully connected to matching. |
| GET | `/kabadiwala/listings` | Collector | Discover posted/own-assigned listings | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Assigned IDs are owner-filtered; no live IDOR test. |
| GET | `/kabadiwala/pickups` | Collector | List own pickups | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No route/IDOR test. |
| POST | `/kabadiwala/listings/:listingId/accept` | Collector | Accept own pickup request | 🟡 PARTIAL | None | No full assignment/retry test. |
| POST | `/kabadiwala/pickups/:pickupId/reject` | Collector | Reject assigned request | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No route test. |
| POST | `/kabadiwala/pickups/:pickupId/confirm-availability` | Collector | Confirm availability | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No route/state test. |
| POST | `/kabadiwala/pickups/:pickupId/schedule` | Collector | Schedule pickup | 🟡 PARTIAL | None | Reliability and slot policy incomplete. |
| POST | `/kabadiwala/pickups/:pickupId/cancel` | Collector | Cancel own pickup | 🟡 PARTIAL | None | Retry and notification behavior unverified. |
| POST | `/kabadiwala/pickups/:pickupId/reassign` | Collector | Mark reassignment required | 🟡 PARTIAL | None | Replacement remains a Household choice; notification is persisted but live delivery is unverified. |
| GET | `/household/pickups/:pickupId/reassignment-options` | Household | List active replacement Kabadiwalas | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Distance ordering is available when coordinates exist; no live route/IDOR test. |
| POST | `/household/pickups/:pickupId/reassign` | Household owner | Commit replacement Kabadiwala choice | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Conditional owner-scoped transition, prior-row reuse, notification, and optional idempotency exist; no live route/race test. |
| POST | `/kabadiwala/pickups/:pickupId/status` | Collector | Move to in-transit/arrived | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Inline state machine has no integration tests. |
| POST | `/kabadiwala/pickups/:pickupId/complete` | Collector | Weigh, price, acquire inventory | 🟡 PARTIAL | None | Atomic conditional claim and optional idempotency replay exist; household settlement/payment linkage and live concurrency test remain. |
| GET | `/kabadiwala/inventory` | Collector | Read own balances | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Projection has invariants, but no live DB proof. |
| GET | `/kabadiwala/inventory/movements` | Collector | Read own movements | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No pagination cursor or route test. |
| POST | `/kabadiwala/bulk-lots` | Collector | Reserve inventory into bulk lot | 🟡 PARTIAL | None | Optional idempotency replay and conditional reservation exist; live oversell/concurrency untested. |
| GET | `/kabadiwala/bulk-lots` | Collector | List own bulk lots | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No detail endpoint from collector side. |
| POST | `/kabadiwala/bulk-lots/:lotId/cancel` | Collector owner | Release listed lot reservation | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No live invariant/idempotency test. |
| GET | `/kabadiwala/bulk-offers` | Collector owner | Read offers for own lots | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No route test. |
| GET | `/recycler/bulk-lots` | Verified Recycler | Browse listed lots | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No pagination or route test. |
| GET | `/recycler/bulk-lots/:lotId` | Verified Recycler | Read lot detail | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Reserved-lot visibility policy not explicit. |
| POST | `/recycler/bulk-lots/:lotId/offers` | Verified Recycler | Create/update offer | 🟡 PARTIAL | None | No idempotency header; capability and race coverage absent. |
| GET | `/recycler/offers` | Verified Recycler | Read own offers | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No route test. |
| POST | `/recycler/offers/:offerId/withdraw` | Verified Recycler | Withdraw own pending offer | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No route/concurrency test. |
| POST | `/kabadiwala/bulk-offers/:offerId/reject` | Collector owner | Reject offer | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Concurrent accept/reject untested. |
| POST | `/kabadiwala/bulk-offers/:offerId/counter` | Collector owner | Counter offer | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Conditional pending-state update exists; no live race test. |
| POST | `/kabadiwala/bulk-offers/:offerId/accept` | Collector owner | Accept one offer | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Conditional single-winner lot/offer claims exist; no live concurrent test. |
| POST | `/recycler/bulk-lots/:lotId/receive` | Verified Recycler | Direct receive compatibility route | 🗑️ OBSOLETE | None | Always returns `FORMAL_HANDOVER_REQUIRED`; formal route must be the contract. |
| POST | `/recycler/procurement-requirements` | Verified Recycler | Publish demand | 🟡 PARTIAL | None | Minimum/required relation is enforced; no idempotency or route test. |
| PATCH | `/recycler/procurement-requirements/:requirementId` | Verified Recycler owner | Update/pause/cancel demand | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Ownership exists; no endpoint/lifecycle test. |
| GET | `/recycler/procurement-requirements` | Verified Recycler owner | List own demand | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No route test. |
| GET | `/kabadiwala/procurement-requirements` | Collector | Read open demand | 🟡 PARTIAL | None | Demand is privacy-safe but does not yet return the full grade/geography/capability eligibility projection. |

### Formalisation, pooling, passport, safety, and handover routes

| Method | Route | Allowed role(s) | Purpose | Status | Tests | Problems |
|---|---|---|---|---|---|---|
| GET | `/kabadiwala/route-advantage` | Collector | Estimate net formal route outcome | 🟡 PARTIAL | None | Grade/weight capability, configured logistics, demand caps, freshness, confidence, and reliability signals exist; provider logistics quotes and historical risk modeling remain. |
| GET | `/kabadiwala/pool-opportunities` | Collector | Show demand-linked pool opportunities | 🟡 PARTIAL | None | Verified capability, grade, authorization, and strict complete-coordinate radius filtering are present; live proof remains. |
| GET | `/kabadiwala/pools/suggestions` | Collector | Suggest demand-triggered pools | 🟡 PARTIAL | None | Verified capability and grade/radius filters are present; no live proof or automatic contributor selection. |
| POST | `/kabadiwala/pools` | Collector | Create canonical demand pool | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Unique source key helps duplicate creation; live creator-side eligibility/geography proof is absent. |
| GET | `/kabadiwala/pools` | Collector | Read own/member pools | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No live privacy/ownership test. |
| POST | `/kabadiwala/pools/:poolId/join` | Collector | Reserve own contribution | 🟡 PARTIAL | None | Preferred grade, server rate, source provenance, atomic aggregate update, and idempotency exist; no live concurrency proof. |
| POST | `/kabadiwala/pools/:poolId/leave` | Collector owner | Release own contribution | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Conditional release and atomic aggregate decrement exist; no live race test. |
| POST | `/kabadiwala/pools/:poolId/lock` | Pool creator | Lock threshold pool | 🟡 PARTIAL | None | Threshold claim and audit are in one transaction; live concurrency proof is absent. |
| GET | `/recycler/pools` | Verified Recycler | Read targeted pools | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No endpoint/privacy test. |
| GET | `/kabadiwala/demand-intelligence` | Collector | Demand, gap, supply intelligence | 🟡 PARTIAL | None | Grade, capability, authorization, and strict complete-coordinate radius filtering are present; no live proof. |
| GET | `/kabadiwala/passport` | Collector | Derived growth passport | 🟡 PARTIAL | None | Derived metrics are incomplete/mixed across legacy/formal records. |
| GET | `/safety-routing` | Collector | Hazard-aware route guidance | 🟡 PARTIAL | None | Hardcoded metadata, no authorization/disposal evidence chain. |
| GET | `/kabadiwala/safety` | Collector | Safety modules/progress | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No route/content version test. |
| POST | `/kabadiwala/safety/:moduleKey/acknowledge` | Collector | Record own module acknowledgement | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No route test. |
| POST | `/kabadiwala/pools/:poolId/prepare-handover` | Pool creator | Prepare signed pool handover | 🟡 PARTIAL | None | Source-key replay, source passport metadata, expiry release, and settlement allocation exist; no live proof. |
| POST | `/kabadiwala/bulk-lots/:lotId/prepare-handover` | Lot owner | Prepare signed bulk handover | 🟡 PARTIAL | None | Source metadata, quantity guard, expiry release, and settlement exist; no live proof. |
| POST | `/kabadiwala/handovers/:handoverId/collector-confirm` | Collector owner | Confirm formal handover | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Optional idempotency; no live replay/ownership test. |
| GET | `/kabadiwala/handovers` | Collector owner/contributor | List formal handovers | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No endpoint/IDOR test. |
| GET | `/recycler/supply-handovers` | Verified Recycler | List incoming formal handovers | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No route/freshness test. |
| POST | `/recycler/handovers/confirm` | Verified Recycler | Verify QR and settle/review handover | 🟡 PARTIAL | None | Quantity guard, expiry release, variance/review, and formal payment follow-up exist; no live transaction test. |
| POST | `/recycler/handovers/:handoverId/disposal-evidence` | Verified Recycler | Record data-destruction evidence | 🟡 PARTIAL | None | Idempotent evidence state transition exists; storage/authenticity and live test remain. |
| POST | `/recycler/handovers/:handoverId/qc` | Verified Recycler owner | Record formal handover QC decision | 🟡 PARTIAL | `syncService.test.ts` (formal transaction primitives) | Conditional PASS/FAIL, notes/evidence, anomaly, pool review, and replay handling exist; no live route/QC test. |
| POST | `/recycler/handovers/:handoverId/payment` | Verified Recycler | Record formal settlement payment | 🟡 PARTIAL | None | Unique recipient/handover key, delayed/underpayment anomalies, and replay hash exist; external payment gateway is out of scope. |
| GET | `/kabadiwala/handovers/:handoverId/payments` | Collector owner/contributor | Read formal payments | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Owner/contribution scoped; no route test. |
| POST | `/kabadiwala/handovers/:handoverId/payment-confirm` | Collector owner/contributor | Confirm/dispute formal payment | 🟡 PARTIAL | None | Conditional confirmation and anomaly evidence exist; no live test. |
| POST | `/kabadiwala/handovers/:handoverId/settlement` | Collector owner/contributor | Accept/dispute formal settlement | 🟡 PARTIAL | None | Conditional decision, issue reason/evidence, inventory settlement, and final pool transition exist; no live test. |
| GET | `/kabadiwala/handovers/:handoverId/passport` | Collector owner/contributor | Read formal passport | 🟡 PARTIAL | None | Joins source/pickup/inventory/settlement/payment/event projections; scalar-link and live-unverified. |
| GET | `/kabadiwala/handovers/:handoverId/anomalies` | Collector owner/contributor | Read anomaly flags | 🟡 PARTIAL | None | Deterministic flags and admin resolution exist; no historical/repeated-behavior detector. |
| GET | `/admin/formal-anomalies` | Admin with `DISPUTE_RESOLUTION` | List unresolved formal anomalies | 🟡 PARTIAL | None | Queue exists; no live/admin route test. |
| POST | `/admin/formal-anomalies/:flagId/resolve` | Admin with `DISPUTE_RESOLUTION` | Accept, revert, release, or acknowledge formal anomaly | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Internal settlement/reservation/payment rows are updated transactionally; no live/admin route test. |
| POST | `/admin/formal-payments/:paymentId/reverse` | Admin with `PAYMENT_VERIFICATION` | Record externally confirmed formal-payment reversal | 🟡 PARTIAL | None | Provider-neutral reversal evidence and internal `REVERSED` state are transactional/idempotent; this backend does not execute bank/wallet/cash reversal. |

### Future/secondary authenticated routes

These are mounted under `/api/v1/future`.

| Method | Route | Allowed role(s) | Purpose | Status | Tests | Problems |
|---|---|---|---|---|---|---|
| GET | `/future/preferences` | Household/Collector/Recycler | Read preferences | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No route test. |
| PATCH | `/future/preferences` | Household/Collector/Recycler | Update preferences | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No route test. |
| GET | `/future/rewards` | Collector | Read reward ledger | 🟡 PARTIAL | None | Derived from legacy handovers/payments, not formal settlements. |
| GET | `/future/schemes` | Household/Collector/Recycler | Scheme catalogue | 🟡 PARTIAL | None | Backend does not verify external freshness. |
| POST | `/future/schemes/check` | Household/Collector/Recycler | Check scheme eligibility | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No route/content test. |
| GET | `/future/activities` | Household/Collector/Recycler | Read activities | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Not the formal safety-routing contract. |
| GET | `/future/activities/:slug` | Household/Collector/Recycler | Read activity detail | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Not disposal authorization. |
| POST | `/future/lots/description-suggestion` | Collector | Template/AI description suggestion | ⚠️ IMPLEMENTED BUT UNVERIFIED | `geminiDescription.test.ts` | Non-authoritative; no route test. |
| POST | `/future/lots/material-suggestion` | Collector | Image material suggestion | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Optional AI; no route/upload test. |
| GET | `/future/recyclers/:recyclerId/reviews` | Authenticated account | Read verified reviews | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No route/privacy test. |
| POST | `/future/reviews` | Collector with completed legacy handover/payment | Submit Recycler review | 🟡 PARTIAL | None | Does not consume formal handover/payment path. |
| GET | `/future/conversations` | Collector/Recycler participant | List private conversations | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No route/IDOR test; secondary legacy path. |
| POST | `/future/conversations` | Collector/Recycler participant | Open private conversation | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No route/IDOR/freshness test. |
| GET | `/future/conversations/:conversationId/messages` | Collector/Recycler participant | Read messages | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No route/IDOR test. |
| POST | `/future/conversations/:conversationId/messages` | Collector/Recycler participant | Send message | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Client ID de-duplication is not a transaction. |
| POST | `/future/conversations/:conversationId/draft-reply` | Collector/Recycler participant | Generate non-sending draft | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Optional AI; no route test. |
| POST | `/future/lots/:lotId/repeat` | Collector owner | Copy completed legacy lot | ♻️ DUPLICATE | None | Optional idempotency, legacy only. |
| GET | `/future/disputes/analytics` | Collector/Recycler | Own legacy dispute analytics | 🟡 PARTIAL | None | Does not include formal anomaly/settlement records. |

### Admin dataset routes

| Method | Route | Allowed role(s) | Purpose | Status | Tests | Problems |
|---|---|---|---|---|---|---|
| POST | `/admin/datasets/prices/import` | Admin with `DATASET_EXPORT` | Import validated price rows | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | No endpoint/permission/integration test. |
| GET | `/admin/datasets/export` | Admin with `DATASET_EXPORT` | Pseudonymized operational export | ⚠️ IMPLEMENTED BUT UNVERIFIED | None | Configuration and privacy projection need staging proof. |

## 8. Differentiation audit

| Capability | Route/API | Service logic | DB support | Validation | Authorization | Tests | Complete? | Missing |
|---|---|---|---|---|---|---|---|---|
| Formal Route Advantage | `GET /kabadiwala/route-advantage` | Inline route estimator with net economics/reasons | `RouteAdvantageEstimate`, `Price`, `PriceHistory`, `ProcurementRequirement`, `Recycler` | Quantity/material/grade/weight, min lot, distance, configured logistics, demand caps, freshness/confidence | DB-backed Collector middleware; current verified Recycler and capability query | None for route | 🟡 PARTIAL | Provider logistics quotes and historical reliability model. |
| Cooperative Pooling | `/kabadiwala/pools*`, `/recycler/pools` | Inline pool create/join/leave/lock/prepare/settle | `PooledConsignment`, `PoolContribution`, `PoolSettlement`, inventory ledger | Positive quantity, preferred grade, source provenance, server rate, idempotency | Collector owner/contributor; verified target Recycler | None for live route | 🟡 PARTIAL | Live race proof and full financial reconciliation. |
| Growth Passport | `GET /kabadiwala/passport` | `refreshPassport` from events/metrics | `CollectorPassport`, `MaterialPassportEvent`, `SafetyProgress` | Derived server-side | Own Collector | None | 🟡 PARTIAL | All formal dispute sources and unambiguous pool/direct event semantics. |
| Offline handover reconciliation | Collector confirm, Recycler confirm, QC, payment/disposal evidence, `/sync` | QR/nonce/hash, conditional state claims, atomic formal sync operations | `SupplyHandover`, `IdempotencyRecord`, `SyncOperation`, passport/audit events | QR, expiry, request hash, variance reason, QC/evidence/payment validation | Role/owner/fresh Recycler | `syncService.test.ts`; no live route | 🟡 PARTIAL | Live route/replay/concurrency proof and stronger delta-cursor proof. |
| Material Passport | Household listing/pickup passport, formal handover passport, legacy timeline | Scalar-link projections plus event writes | `MaterialPassportEvent`, inventory movements, settlement/payment/anomaly records | Entity-specific owner-scoped queries | Owner/contributor predicates | Helper tests only | 🟡 PARTIAL | Single immutable graph and live end-to-end test. |
| Fairness Guard | Household/formal settlement/payment routes, QC, `/admin/formal-anomalies`, and payment reversal | Reason/evidence, before/after values, variance/anomaly flags, audited accept/revert/release actions, provider-neutral reversal evidence | Settlement breakdowns, pool settlements, payment/reversal/anomaly flags, legacy disputes | Reason required for issue/variance decisions; reversal requires provider/reference/evidence/reason | Household/Collector/Recycler owner; admin resolution/reversal permissions | Pure settlement and mocked formal-sync tests | 🟡 PARTIAL | External payment execution and live route proof. |
| Anomaly Detection | Formal confirmation, payment/dispute/QC/pickup routes, anomaly query/admin queue | Deterministic variance, delayed/underpaid payment, repeated cancellation/no-show, duplicate-source and QC-failure persistence/resolution | `AnomalyFlag` | Weight/rate/material/payment/QC/pickup variance | Handover/pickup owner; admin permission | Pure rule tests; no route tests | 🟡 PARTIAL | Statistical historical-outlier detector, alerts, and automatic remediation. |
| Reverse Demand Network | Recycler requirement routes, demand intelligence | Inline DB aggregation with grade, capability, authorization, and strict complete-coordinate distance eligibility | `ProcurementRequirement`, Recycler capabilities/rates, inventory | Create/PATCH relation validation; active/deadline/radius filters; demand idempotency | Verified Recycler writes; Collector reads | None | 🟡 PARTIAL | Geospatial indexes, route tests, and production-scale query proof. |
| Demand-triggered pool suggestions | `/pools/suggestions`, `/pool-opportunities` | Aggregate inventory/demand scans with eligibility filters | Requirements, inventory, pools | Active/deadline, grade, capability, authorization, and strict complete-coordinate distance filters | Collector read | None | 🟡 PARTIAL | Automatic contributor selection/notification and live route proof. |
| Explainable Matching | `/recyclers/match`, route advantage | Two separate scoring/estimation implementations | Legacy Lot/Recycler plus route models | Material/distance/rate filters | Collector; verified Recycler source | Service tests only | 🟡 PARTIAL | Unified explanation and economic/operational eligibility. |
| Confidence-aware Pricing | `/prices/board`, `/prices/history`, route advantage | Price service/repository fallback and confidence | Price/PriceHistory | Positive ranges, source/status | Marketplace read; admin write | Price service/repository tests | 🟡 PARTIAL | Freshness-aware history confidence and explicit fallback/source context. |
| Offline backend reconciliation/idempotency | `/sync`, optional `Idempotency-Key` routes | Sequential role-aware batch; formal pool, settlement, handover, QC, disposal, and payment operations are atomic with sync/audit/passport writes | `SyncOperation`, `IdempotencyRecord`, unique source keys | Supported operation schema; QR, ownership, state, and request-hash checks | Collector/verified Recycler role guards | `syncService.test.ts` | 🟡 PARTIAL | Live replay/conflict/concurrency testing and stronger delta-cursor proof. |
| Safety Routing | `/safety-routing`, `/kabadiwala/safety` | Hardcoded route metadata plus acknowledgements | `SafetyProgress`, hardcoded module map | Material/condition enums | Collector | None | 🟡 PARTIAL | Versioned policy/content and linkage to verified disposal capabilities. |
| Recycler Verification Freshness | Verification/admin routes and middleware plus hourly server sweep | `isCurrentRecyclerAuthorization` plus stale-to-`EXPIRED` transition on access and scheduled sweep notification | Recycler evidence/status/valid-until/audit, notifications | Evidence/date validation in paths | Admin write; verified read/write | Verification service tests | 🟡 PARTIAL | Operator renewal/review workflow and live revocation/sweep proof. |
| Data-safe Disposal Evidence | Household listing and formal handover routes, online/offline evidence APIs | Recycler evidence claim updates handover/listing/audit/passport state | Listing + `SupplyHandover` evidence fields | Device/preparation/request/evidence refinements | Household request; verified Recycler evidence writer | `syncService.test.ts` for offline transaction primitive | 🟡 PARTIAL | Durable evidence storage/authenticity and live route test. |
| Pickup Reliability/Reassignment | Household/Collector pickup routes plus reassignment options/commit/payment | Inline lifecycle, replacement options, persisted notification, settlement payment and passport aggregation | Pickup timestamps/no-show/reassignment/settlement/payment fields, anomalies, notifications | Status-specific updates, owner-scoped options, payment hash/idempotency | Household/assigned Collector | None | 🟡 PARTIAL | Notification delivery policy, disputed-inventory reconciliation policy, and live route proof. |

## 9. Role security audit

| Boundary | Source enforcement | Result | Verification status / gap |
|---|---|---|---|
| Household may create/read/update only its own listings | `requireHousehold`; queries include `householdId` | Mostly enforced | No route-level IDOR tests; downstream passport is narrower than target. |
| Household may not mutate Collector inventory, bulk lots, pools, or Recycler demand/offers | Those routes use `requireAuth`/`requireRecycler` | Denied by role middleware | Only a small middleware matrix is tested; no full route matrix. |
| Household may not create professional bulk lots or Recycler demand | No Household route reaches those handlers | Denied | Not live-tested. |
| Collector may mutate only own inventory/pickups/lots/offers/pool contribution | Most writes filter `collectorId`/`kabadiwalaId`; pool contribution uses `(poolId, collectorId)` | Mostly enforced | `/kabadiwala/listings` disclosure; offer/pool concurrency unproven. |
| Collector may not self-verify as Recycler | Recycler verification request requires Recycler; admin authorization requires admin permission | Enforced in route design | No full endpoint test; stale legacy tokens/role linkage need review. |
| Collector may not create Recycler procurement demand | Requirement create uses `requireRecycler` | Enforced | Not route-tested. |
| Collector may not buy another Collector’s lot as Recycler | Bulk offer routes use `requireRecycler`; accept uses lot owner | Enforced in sequential source path | Competing accept race is unsafe. |
| Recycler may only modify own profile/rates/demand/offers | `requireRecycler`, recycler/owner predicates | Mostly enforced | Some secondary routes check only `authorizationStatus`; no route IDOR suite. |
| Recycler may not process Household pickup or mutate Collector inventory | Pickup/inventory writes use `requireAuth`/Household gates | Enforced by role | Not live-tested. |
| Recycler may not set itself VERIFIED | Admin authorization route is the only status writer found | Enforced by route | No adversarial endpoint test. |
| Recycler may not access another Recycler’s private procurement | Own requirement list filters `recyclerId`; incoming demand is broad by design | Own writes protected | Public/Collector demand response includes `recyclerId`; privacy projection should be explicit. |
| Admin actions | `requireAdmin` checks active account and permission | Enforced in source | No route-level admin permission tests. |
| IDOR / matched listing query | `GET /kabadiwala/listings` filters assigned IDs by the authenticated `kabadiwalaId`; household routes filter `householdId` | Source-fixed | Live IDOR test is still required. |
| Current account authority | Production route registration passes Prisma-backed revalidation into Collector/Household middleware; Recycler/Admin middleware also re-read current DB state | Source-fixed | Legacy profiles without a linked `User` remain compatible; no live revocation test. |

Frontend hiding is not counted as authorization. The source predicates are the authority; Android changes and docs are not used as proof.

## 10. Data integrity audit

| Invariant | Source behavior | Assessment |
|---|---|---|
| Pickup completion adds inventory once | Transaction claims `ARRIVED → WEIGHED` conditionally, upserts inventory, writes movement, then marks completed; optional idempotency record replays the response | 🟡 Good source shape; no live/concurrent test. |
| Repeated completion cannot duplicate inventory | Second request no longer finds `ARRIVED`; same idempotency key returns the stored response | 🟡 Likely safe sequentially; unverified in MongoDB. |
| Inventory is non-negative | Positive Zod inputs, conditional reservation, `assertInventoryInvariant` | 🟡 Does not cover all database-level paths or physical quantity conservation. |
| Reserved inventory ≤ owned inventory | Projection and helper calculate owned as available+reserved+sold; settlement quantity guard prevents accepted weight above source | 🟡 Arithmetic check exists; no live conservation test. |
| Bulk-lot reservation is atomic | `availableKg >= quantity` conditional update inside transaction | 🟡 Good sequential pattern; competing lot/offer workflows untested. |
| Cancellation releases reservation | Listed bulk-lot cancel and expired formal handover release available/reserved in transaction | ⚠️ No live test. |
| Pool contribution ownership | Unique `(poolId, collectorId)` and current collector filters | 🟡 Good owner predicate; no live IDOR proof. |
| Pool reservations are safe under concurrency | Conditional balance reservation plus atomic pool aggregate increment/decrement | ⚠️ No concurrent MongoDB proof. |
| Duplicate offer acceptance is prevented | Conditional lot claim and conditional offer claim; pending competitors are rejected | 🟡 Good source shape; no live concurrent proof. |
| Duplicate handover preparation | Unique `SupplyHandover.sourceKey` and replay lookup | 🟡 Good source-key primitive; expired source cannot be re-prepared automatically. |
| Duplicate formal Recycler confirmation | Conditional handover status update; optional idempotency record | 🟡 Sequentially protected; no route/concurrency test. |
| Duplicate legacy handover confirmation | Conditional legacy handover status update plus collector-confirmation requirement | 🟡 Duplicate confirmation blocked; no route test. |
| Duplicate payment/settlement | Legacy payment has one-per-lot uniqueness; formal `SupplyPayment.sourceKey`, settlement status, and handover claims prevent duplicates | 🟡 Source-fixed; no live proof. |
| Formal settlement cannot exceed source | `assertSettlementQuantity` rejects actual above quoted source and accepted above actual | 🟡 Source-fixed; no live proof. |
| Stale state transitions rejected | Many inline `updateMany` predicates reject invalid current status | 🟡 Several declared states are not used consistently; no shared transition table. |
| Audit events are atomic with changes | Most formal writes use a transaction; pool lock updates state then writes audit separately | ⚠️ Audit can diverge on lock path; no immutable DB constraint. |
| Recycler verification authoritative | Verified middleware checks DB status/expiry and marks stale verified records `EXPIRED` on access; hourly sweep also expires stale records with an audit | 🟡 Good primary guard; no live revocation test or operator renewal notification flow. |

## 11. Offline/idempotency audit

| Operation | Current retry mechanism | Safe for Android retries? |
|---|---|---|
| Household listing create | Optional `Idempotency-Key` + request hash stored in transaction | 🟡 Same keyed retry converges; no live route test. |
| Household pickup request | Optional `Idempotency-Key`, unique `(listingId,kabadiwalaId)`, source request hash | 🟡 Same request is usually convergent; no-key retries and concurrent live proof are incomplete. |
| Pickup completion | Conditional status + optional idempotency record | 🟡 Duplicate inventory is guarded; no live route test. |
| Bulk-lot create | Transactional reservation + optional `Idempotency-Key` request hash | 🟡 Same keyed retry converges; no live route test. |
| Pool create | Unique `sourceKey=REQUIREMENT:<id>` | 🟡 Duplicate pool creation converges; no request hash/actor intent. |
| Pool join | Unique contributor row, request hash, and optional `Idempotency-Key` | 🟡 Partial; grade/rate changes conflict as intended, live race untested. |
| Formal collector confirm | Optional `Idempotency-Key` and request hash | 🟡 Server primitive exists; no route test. |
| Formal Recycler confirm | Optional `Idempotency-Key`, QR nonce/hash, conditional status, expiry release, and settlement guards | 🟡 Server primitive exists; live replay/concurrency proof remains. |
| Formal settlement decision | Conditional settlement status, reason/evidence fields, anomaly records | 🟡 Duplicate decision is blocked and supported by `/sync`; no live replay test. |
| Legacy LOT sync | `SyncOperation(operationId,collectorId)` and request hash | 🟡 Replays are handled; mutation and sync record are separate writes. |
| Legacy PAYMENT sync | Payment ID plus sync operation | 🟡 More recoverable because payment lot uniqueness exists; still no transaction spanning both records. |
| Formal handover/pool/evidence sync | `/sync` supports transactional collector/Recycler handover, pool, settlement, disposal-evidence, and payment operations | 🟡 Source-complete operation vocabulary; no live route/replay/concurrency proof. |
| Change feed | Preferred opaque per-feed cursor with timestamp/ID ordering; legacy `since` remains compatible; includes formal handovers/payments/reversals, pickup settlement payments, settlements, anomalies and events | 🟡 Source-complete cursor contract; no live delta/replay test. |

Specific `/sync` limitations in `backend/src/services/syncService.ts`:

- Accepted entity types include legacy `LOT`/`PAYMENT`, formal `POOL_CONTRIBUTION`, `POOL_SETTLEMENT`, `SUPPLY_HANDOVER`, and `SUPPLY_PAYMENT`; formal actions are role-checked and transactionally applied.
- The request hash covers operation type, entity type, entity identity, and payload.
- Batch operations are processed sequentially.
- Formal pool contribution, settlement decision, Recycler confirmation, disposal evidence, and payment mutations include their audit/passport event and `SyncOperation` persistence in one transaction. Legacy LOT/PAYMENT branches still persist the sync row separately.
- `changes()` returns legacy records plus role-scoped formal handovers, payments, settlements, anomalies, and passport events; formal pool handovers are included for contributing collectors and targeted Recycler feeds.
- The opaque cursor codec and per-feed timestamp/ID ordering are unit-tested; live delta pagination and concurrent-write behavior remain unproven.

## 12. Test results

Commands run from `C:\Users\pulki\AndroidStudioProjects\KabadiwalaConnect\backend`:

| Command | Result |
|---|---|
| `npm ci` | Completed dependency setup; npm reported 2 moderate audit findings in development dependencies. No source files were intentionally changed by this command. |
| `npm run build` | **PASS** — `prisma generate` and `tsc -p tsconfig.json` completed. |
| `npm test -- --run` | **PASS** — 25 test files passed; 68 tests passed; no skipped tests were reported. |
| `npm run lint` | **PASS** — `tsc -p tsconfig.json --noEmit` completed. |
| `npm audit --omit=dev --json` | **PASS / 0 production vulnerabilities** in the installed dependency graph. |
| `npm audit --json` | **2 moderate development vulnerabilities**: `vitest`/`@vitest/mocker` path traversal/arbitrary file read advisory; the suggested fix is a major Vitest upgrade and was not applied. |
| `npx prisma validate` | **PASS** — current MongoDB Prisma schema validates. |

Test warning observed:

- The suite emitted: `Skipping runtime index maintenance for Collector: Prisma could not decode MongoDB listIndexes output.` This is a warning from optional runtime index maintenance, not a failing test.
- Prisma also printed an available-version notice; it is not a test failure.

Not run:

- `db:push`, migrations, seed, or any production database mutation.
- Live transaction/integration tests, because `backend/.env.testing` is absent and no MongoDB server (`mongod`/`mongosh`) is available. `backend/compose.testing.yml` now defines an isolated MongoDB service, but Docker Desktop's Linux engine was unavailable during this pass.
- Load/concurrency tests against MongoDB.

Adversarial coverage actually established:

- Existing unit tests cover basic role denial, JWT behavior, QR tamper helpers, inventory helper invariants, and pure settlement rules.
- Static/source adversarial tracing rechecked B-01 through B-07; all seven source defects were addressed, but live execution was unavailable.
- No live IDOR, concurrent oversell, double-offer, double-handover, or replay test was able to run without a test database.

## 13. Documentation accuracy

### `docs/BACKEND_COMPLETION_REPORT.md`

This document is now explicitly an implementation report rather than a freeze approval. Its remaining claims are bounded by the final verification report; live integration/concurrency proof is still unavailable.

### `docs/BACKEND_API_AUDIT.md`

It is a historical baseline and is explicitly superseded by this report. Several initial statuses and omissions are intentionally retained for audit history.

### `docs/BACKEND_API_CONTRACT.md`

It describes a desired/frozen contract, including privacy-safe pooling, route economics, formal confirmation, settlement, safety, and passports. The current source now covers more of those descriptions, but the contract should not be frozen until the remaining P1 policy gaps and live integration tests are complete.

### `backend/openapi.yaml`

The OpenAPI file now inventories all 170 audited operations and points readers to the detailed contract for exact semantics. Its operation bodies intentionally use generic JSON schemas, so granular per-route schemas and response examples remain a documentation-quality gap.

### `backend/README.md`

The README now describes the role-aware formal sync operations and formal change feed, but remains a high-level guide rather than a complete contract for every newer route.

### `docs/SECURITY_THREAT_MODEL.md`

It is supporting threat-model context, not proof. Its claim that formal receipt requires collector confirmation is now true for both the new `SupplyHandover` route and the mounted legacy handover service. Its listed adversarial exercises are not represented by the current 67-test suite as live route tests.

### `docs/MASTER_GAP_ANALYSIS.md`, `docs/KNOWN_LIMITATIONS.md`, `docs/OFFLINE_SYNC_DESIGN.md`, and `docs/MATERIAL_PASSPORT.md`

These documents are directionally more candid: they mention partial sync, non-certification, and offline limitations. Their broad product claims still go beyond what the current authoritative endpoint implementation proves; the current contract is `BACKEND_API_CONTRACT.md` and the verification evidence is this file.

## 14. Top remaining backend gaps

### P0 — blocks core workflow or security

No new source-level P0 defect was left open after this pass. The certification blocker is the absence of a live MongoDB integration/concurrency environment for proving the corrected authorization, reservation, offer, expiry, settlement, and replay paths.

### P1 — blocks differentiation or selection value

1. Integrate external bank/cash/provider reversal execution; internal formal accept/revert/release resolution and provider-neutral reversal evidence are implemented.
2. Complete route advantage with provider logistics quotes and historical reliability; complete pooling with route-level and live concurrency tests.
3. Add operator renewal/review processing and live delivery policy around the automated Recycler authorization expiry sweep.
4. Complete pickup settlement reconciliation policy for disputed inventory and notification delivery; household-confirmed reassignment, replacement options, and persisted reassignment notification are implemented.
5. Add live route/IDOR/replay tests and granular OpenAPI schemas before contract freeze.

### P2 — quality and verification

1. Add MongoDB integration tests for transactions, ownership, IDOR, concurrent reservation/offer/confirmation, and replay.
2. Add a shared state-machine transition module for all formal enums and remove/retire unreachable states.
3. Make audit/event writes atomic on every critical mutation and define immutability/retention behavior.
4. Add route-level tests for price history confidence/fallback/source fields.
5. Add granular OpenAPI schemas/examples and update the remaining legacy README/supporting documents from the verified route behavior.

### P3 — optional/future or Android/frontend scope

1. Android Room/WorkManager queue, retry/conflict UX, QR scan/display, evidence capture, and offline UI for the formal flow.
2. Low-literacy language/TTS/accessibility flows, safety presentation, and household/collector/recycler screen completion.
3. Optional AI suggestions, richer notification delivery, statistical/ML anomaly scoring, and non-core future content.

## 15. Final verdict

## 🔴 BACKEND NOT COMPLETE

The repository contains substantial backend work and its unit/build gates pass. Formal sync, QC, pickup payment, anomaly, reversal-evidence, routing, pooling, and reassignment coverage are implemented at source level, but the target specification is not fully implemented or verified: external payment execution, granular contract schemas, and live integration/concurrency proof remain incomplete. These gaps prevent an authoritative API freeze.
