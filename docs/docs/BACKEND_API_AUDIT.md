# Kabadiwala Connect Backend Audit

Audit date: 2026-09-15

> Historical baseline audit. This document records the initial audit state and is not the current completion verdict. See [BACKEND_FINAL_VERIFICATION.md](./BACKEND_FINAL_VERIFICATION.md) for the repository-verified status after the implementation pass.

Scope: `backend/` runtime code, Prisma schema, routes, middleware, services, repositories, seed data and tests. Android code was not changed. The supplied execution prompt was treated as the implementation instruction; repository documentation was used only as supporting context.

## Audit notes

- The application is mounted under `/api/v1` in `backend/src/app.ts`.
- The current identity vocabulary is `COLLECTOR` for the Kabadiwala role, `HOUSEHOLD`, `RECYCLER`, and `ADMIN`; the API contract will describe `COLLECTOR` as Kabadiwala for Android consumers.
- The backend currently contains two intentionally separate business paths: the older collector lot/quote/handover/payment path and the newer household-listing, collector-inventory, bulk-lot, pooling and supply-handover path. They must not be casually merged because they have different data ownership semantics.
- The initial audit was performed before the current repository checkout metadata was available; its historical endpoint statuses are superseded by the final verification report.
- Baseline `pnpm` commands initially attempted dependency reconciliation and stopped in the non-interactive shell with `ERR_PNPM_ABORTED_REMOVE_MODULES_DIR_NO_TTY`. The bundled Node/pnpm runtime is available; final verification will use the existing installed modules and CI-safe invocation.

## Current endpoint matrix

Status meanings follow the execution prompt.

### Health, identity and collector profile

| Method | Endpoint | Allowed Role | Purpose | Status | Tests | Problems |
|---|---|---|---|---|---|---|
| GET | `/health` | Public | Liveness/database health | ✅ COMPLETE | `app.test.ts` | None found in audit |
| POST | `/auth/request-otp` | Public | Request phone OTP | ✅ COMPLETE | `app.test.ts`, `authService.test.ts` | Rate-limit behavior is service-level |
| POST | `/auth/verify-otp` | Public | Verify OTP and issue collector/household token | ✅ COMPLETE | `authService.test.ts` | Endpoint integration coverage is light |
| POST | `/auth/refresh` | Public | Rotate refresh token | ✅ COMPLETE | `sessionService.test.ts` | None found in audit |
| POST | `/auth/logout` | Public/token | Revoke refresh session | ✅ COMPLETE | `sessionService.test.ts` | None found in audit |
| POST | `/auth/signup` | Public | Email/password account signup | 🟡 PARTIAL | `app.test.ts` | Requires configured email auth and has limited route coverage |
| POST | `/auth/login` | Public | Email/password login | 🟡 PARTIAL | Service tests only | Route-level failure/role coverage is incomplete |
| POST | `/auth/admin-login` | Public | Admin login | 🟡 PARTIAL | None | Needs adversarial endpoint coverage |
| GET | `/auth/profile` | Household/Collector/Recycler | Current profile | 🟡 PARTIAL | None | Response shape is shared but role-specific profile completeness is not documented |
| GET | `/collectors/me` | Collector | Read collector profile | ✅ COMPLETE | None | Endpoint test missing |
| PUT | `/collectors/me` | Collector | Update collector profile | ✅ COMPLETE | None | Endpoint test missing |

### Legacy collector lot, price, recycler and quote flows

| Method | Endpoint | Allowed Role | Purpose | Status | Tests | Problems |
|---|---|---|---|---|---|---|
| POST | `/lots` | Collector | Create collector-owned legacy lot | ✅ COMPLETE | `lotService.test.ts` | No route-level ownership test |
| GET | `/lots` | Collector | List own lots | ✅ COMPLETE | None | Pagination/route coverage missing |
| GET | `/lots/:lotId` | Collector | Read own lot | ✅ COMPLETE | None | IDOR requires endpoint test |
| PUT | `/lots/:lotId` | Collector | Update own mutable lot | 🟡 PARTIAL | None | Contract/state/error behavior not frozen |
| DELETE | `/lots/:lotId` | Collector | Cancel own mutable lot | ✅ COMPLETE | `lotService.test.ts` | No route-level test |
| GET | `/lots/:lotId/photo` | Collector | Read own lot image | 🟡 PARTIAL | None | Storage behavior depends on deployment configuration |
| POST | `/lots/:lotId/photo` | Collector | Upload own lot image | 🟡 PARTIAL | None | Upload route lacks dedicated adversarial tests |
| GET | `/prices/board` | Household/Collector/Recycler | Indicative range, freshness and trend | ✅ COMPLETE | `priceService.test.ts`, repository tests | Route/controller validation coverage missing |
| GET | `/prices/history` | Household/Collector/Recycler | Historical price observations | ✅ COMPLETE | `priceRepository.test.ts` | Confidence/observation contract needs expansion |
| GET | `/lots/:lotId/valuation` | Collector | Estimate own lot value | ✅ COMPLETE | None | Does not apply to household listing path |
| PUT | `/admin/prices/:priceId` | Admin with `PRICE_MANAGEMENT` | Update validated price and audit history | ✅ COMPLETE | None | Admin endpoint coverage missing |
| GET | `/recycler/profile` | Recycler, including pending | Read own recycler profile | ✅ COMPLETE | `recyclerVerification.test.ts` | None found in audit |
| POST | `/recycler/verification-request` | Recycler, including pending | Submit evidence for operator review | ✅ COMPLETE | `recyclerVerification.test.ts` | State vocabulary is smaller than requested lifecycle |
| PATCH | `/recycler/profile` | Verified Recycler | Update operational profile | ✅ COMPLETE | None | Needs endpoint test |
| PUT | `/recycler/rates` | Verified Recycler | Update buying rates | ✅ COMPLETE | None | Needs endpoint test and audit event |
| GET | `/recyclers` | Household/Collector/Recycler | Browse verified recycler directory | ✅ COMPLETE | `recyclerServiceOptimization.test.ts` | Endpoint integration coverage missing |
| GET | `/recyclers/match` | Collector | Match legacy lot to recyclers | 🟡 PARTIAL | `recyclerServiceOptimization.test.ts` | Matching is explainable in service but not connected to route-advantage economics |
| GET | `/recyclers/:recyclerId` | Household/Collector/Recycler | Read public recycler detail | 🟡 PARTIAL | None | Public field/privacy contract needs freezing |
| GET | `/admin/recyclers` | Admin with `RECYCLER_REVIEW` | Review queue | ✅ COMPLETE | None | Admin route coverage missing |
| GET | `/admin/recyclers/:recyclerId` | Admin with `RECYCLER_REVIEW` | Review detail | ✅ COMPLETE | None | Admin route coverage missing |
| PUT | `/admin/recyclers/:recyclerId/authorization` | Admin with `RECYCLER_AUTHORIZATION` | Review authorization evidence | ✅ COMPLETE | `recyclerVerification.test.ts` | Missing freshness-state tests |
| POST | `/quotes/request` | Collector | Request quote for own legacy lot | ✅ COMPLETE | `quoteLifecycle.test.ts` | No route-level IDOR test |
| POST | `/quotes/request-batch` | Collector | Request quotes from multiple recyclers | ✅ COMPLETE | None | Needs role/duplicate tests |
| GET | `/quotes/pending` | Collector | List own pending quote requests | ✅ COMPLETE | None | None found in audit |
| GET | `/quotes/:quoteId` | Collector | Read own quote | ✅ COMPLETE | `quotePrivacy.test.ts` | Route-level IDOR test needed |
| POST | `/quotes/:quoteId/accept` | Collector | Accept own quote | ✅ COMPLETE | `quoteLifecycle.test.ts` | No adversarial concurrent acceptance test |
| POST | `/quotes/:quoteId/reject` | Collector | Reject own quote | ✅ COMPLETE | `quoteLifecycle.test.ts` | No route-level test |
| GET | `/recycler/quote-requests` | Verified Recycler | Read quote requests addressed to recycler | ✅ COMPLETE | None | Needs endpoint ownership test |
| GET | `/recycler/quote-requests/:requestId` | Verified Recycler | Read one quote request | ✅ COMPLETE | None | Needs endpoint ownership test |
| POST | `/recycler/quotes` | Verified Recycler | Submit quote to collector | ✅ COMPLETE | None | Needs duplicate/expiry tests |

### Legacy handover, payment, sync, notifications and reporting

| Method | Endpoint | Allowed Role | Purpose | Status | Tests | Problems |
|---|---|---|---|---|---|---|
| POST | `/verify/handover` | Public | Verify signed legacy QR | ✅ COMPLETE | `traceability.test.ts`, `formalisationIntegrity.test.ts` | Public rate-limit/abuse coverage missing |
| POST | `/handovers` | Collector | Create legacy handover | ✅ COMPLETE | `handoverLifecycle.test.ts` | Separate from supply handover path; contract needs distinction |
| GET | `/handovers/reference/:referenceId` | Collector participant | Read own handover by reference | ✅ COMPLETE | None | IDOR endpoint test needed |
| GET | `/handovers/:handoverId` | Collector participant | Read own handover | ✅ COMPLETE | None | IDOR endpoint test needed |
| POST | `/handovers/:handoverId/mark-handed-over` | Collector | Mark own legacy handover | ✅ COMPLETE | `handoverLifecycle.test.ts` | None found in audit |
| PUT | `/handovers/:handoverId/evidence` | Collector | Add legacy handover evidence | ✅ COMPLETE | None | Material-change reason enforcement needs review |
| POST | `/handovers/:handoverId/evidence/photo` | Collector | Upload legacy evidence photo | 🟡 PARTIAL | None | Storage and ownership need route tests |
| POST | `/handovers/:handoverId/dispute` | Collector | Open legacy handover dispute | ✅ COMPLETE | None | Structured evidence pack is incomplete |
| GET | `/recycler/handovers` | Verified Recycler | List legacy handovers addressed to recycler | ✅ COMPLETE | None | None found in audit |
| GET | `/recycler/handovers/:handoverId` | Verified Recycler | Read legacy handover | ✅ COMPLETE | None | IDOR endpoint test needed |
| POST | `/recycler/handovers/:handoverId/confirm` | Verified Recycler | Confirm legacy handover | ✅ COMPLETE | `handoverLifecycle.test.ts` | Material/weight variance guard needs review |
| POST | `/recycler/handovers/:handoverId/reject` | Verified Recycler | Reject legacy handover | ✅ COMPLETE | None | None found in audit |
| GET | `/admin/disputes` | Admin with `DISPUTE_RESOLUTION` | Dispute queue | ✅ COMPLETE | None | Admin route coverage missing |
| GET | `/admin/disputes/:disputeId` | Admin with `DISPUTE_RESOLUTION` | Dispute detail | ✅ COMPLETE | None | Admin route coverage missing |
| POST | `/admin/disputes/:disputeId/resolve` | Admin with `DISPUTE_RESOLUTION` | Resolve dispute | ✅ COMPLETE | None | Admin route coverage missing |
| POST | `/payments/record` | Collector | Record own legacy payment | 🟡 PARTIAL | `paymentLifecycle.test.ts` | Positive amount/settlement relationship needs stronger enforcement |
| GET | `/payments` | Collector | List own payments | ✅ COMPLETE | `paymentLifecycle.test.ts` | None found in audit |
| GET | `/payments/:paymentId` | Collector | Read own payment | ✅ COMPLETE | None | IDOR route test missing |
| PUT | `/payments/:paymentId` | Collector | Edit own payment record | 🟡 PARTIAL | None | Fairness/reason evidence needs tightening |
| POST | `/payments/:paymentId/dispute` | Collector | Dispute own payment | ✅ COMPLETE | `paymentLifecycle.test.ts` | None found in audit |
| GET | `/earnings/ledger` | Collector | Earnings ledger | ✅ COMPLETE | None | None found in audit |
| GET | `/admin/payments` | Admin with `PAYMENT_VERIFICATION` | Payment review queue | ✅ COMPLETE | None | Admin route coverage missing |
| GET | `/admin/payments/:paymentId` | Admin with `PAYMENT_VERIFICATION` | Payment detail | ✅ COMPLETE | None | Admin route coverage missing |
| POST | `/admin/payments/:paymentId/verify` | Admin with `PAYMENT_VERIFICATION` | Verify payment | ✅ COMPLETE | None | Admin route coverage missing |
| POST | `/sync` | Collector | Replay queued field operations | 🟡 PARTIAL | None | Conflict/idempotency contract is not complete for new supply flows |
| GET | `/sync/changes` | Collector | Pull delta changes | 🟡 PARTIAL | None | Cursor semantics and entity coverage need freezing |
| GET | `/transactions/:lotId/timeline` | Collector | Legacy lot material timeline | ✅ COMPLETE | None | Household/source and new supply events are not unified |
| GET | `/activity/changes` | Collector/Household/Recycler | Activity delta feed | ✅ COMPLETE | None | Limit/pagination not documented |
| GET | `/notifications` | Household/Collector/Recycler | Notification inbox | ✅ COMPLETE | `notificationService.test.ts` | Limit bounds need validation |
| GET | `/notifications/unread-count` | Household/Collector/Recycler | Unread count | ✅ COMPLETE | `notificationService.test.ts` | None found in audit |
| POST | `/notifications/:notificationId/read` | Household/Collector/Recycler | Mark own notification read | ✅ COMPLETE | `notificationService.test.ts` | None found in audit |
| POST | `/notifications/read-all` | Household/Collector/Recycler | Mark own notifications read | ✅ COMPLETE | `notificationService.test.ts` | None found in audit |

### Future/secondary authenticated capabilities

These routes are mounted under `/api/v1/future` and are authenticated by `requireAccount`; some are legacy or product backlog capabilities rather than the core supply-chain contract.

| Method | Endpoint | Allowed Role | Purpose | Status | Tests | Problems |
|---|---|---|---|---|---|---|
| GET/PATCH | `/future/preferences` | Household/Collector/Recycler | Read/update language and appearance | ✅ COMPLETE | None | Endpoint coverage missing |
| GET | `/future/rewards` | Collector | Read reward ledger | 🟡 PARTIAL | None | Derived from legacy handovers, not new supply handovers |
| GET/POST | `/future/schemes`, `/future/schemes/check` | Household/Collector/Recycler | Scheme catalogue/eligibility | 🟡 PARTIAL | None | External freshness is not verified by backend |
| GET | `/future/activities`, `/future/activities/:slug` | Household/Collector/Recycler | Safety/DIY content | 🟡 PARTIAL | None | Must not be confused with hazardous routing guidance |
| POST | `/future/lots/description-suggestion` | Collector | Deterministic/AI description suggestion | ✅ COMPLETE | `geminiDescription.test.ts` | AI is optional and must remain non-authoritative |
| POST | `/future/lots/material-suggestion` | Collector | Image material suggestion | 🟡 PARTIAL | None | AI/vision evidence is optional and not core business truth |
| GET/POST | `/future/recyclers/:recyclerId/reviews`, `/future/reviews` | Collector/Recycler as applicable | Verified recycler feedback | 🟡 PARTIAL | None | Growth passport does not yet consume all feedback |
| GET/POST | `/future/conversations`, `/future/conversations/:conversationId/messages` | Collector/Recycler transaction participants | Private quote conversation | ✅ COMPLETE | None | Needs endpoint IDOR/replay coverage |
| POST | `/future/conversations/:conversationId/draft-reply` | Collector/Recycler transaction participants | Optional AI/template draft | ✅ COMPLETE | None | Draft only; must not auto-send |
| POST | `/future/lots/:lotId/repeat` | Collector | Repeat a completed legacy lot | ✅ COMPLETE | None | Idempotency header is optional, not mandatory |
| GET | `/future/disputes/analytics` | Collector/Recycler | Own dispute analytics | ✅ COMPLETE | None | Does not include new settlement anomaly fields |

### Household → Kabadiwala flow

| Method | Endpoint | Allowed Role | Purpose | Status | Tests | Problems |
|---|---|---|---|---|---|---|
| POST | `/household/listings` | Household | Create scrap source listing | 🟡 PARTIAL | None | No data-safe disposal metadata; idempotency absent |
| GET | `/household/listings` | Household | List own source listings | ✅ COMPLETE | None | Detail/update endpoints missing |
| GET | `/household/kabadiwalas` | Household | Discover active collectors | ✅ COMPLETE | None | Location filtering is not implemented |
| POST | `/household/listings/:listingId/pickups` | Household | Request one eligible collector | 🟡 PARTIAL | None | No idempotency header; status/reassignment semantics incomplete |
| GET | `/household/pickups` | Household | List own pickup requests | ✅ COMPLETE | None | No detailed status/settlement projection |
| POST | `/household/listings/:listingId/cancel` | Household | Cancel open listing | 🟡 PARTIAL | None | Does not record reliability/no-show effects or audit all cancellations |
| POST | `/household/pickups/:pickupId/cancel` | Household | Cancel an eligible pickup | 🟡 PARTIAL | None | No late-cancellation classification |
| GET | `/kabadiwala/listings` | Collector | Discover open household listings | ✅ COMPLETE | None | Fixed result cap and limited eligibility filtering |
| GET | `/kabadiwala/pickups` | Collector | List assigned pickups | ✅ COMPLETE | None | Timestamp/reliability fields missing |
| POST | `/kabadiwala/listings/:listingId/accept` | Collector | Accept assigned pickup | 🟡 PARTIAL | None | Acceptance timestamp and confirmation state missing |
| POST | `/kabadiwala/pickups/:pickupId/schedule` | Collector | Schedule pickup | 🟡 PARTIAL | None | Reschedule/reliability metadata missing |
| POST | `/kabadiwala/pickups/:pickupId/status` | Collector | Move pickup to in-transit/arrived | 🟡 PARTIAL | None | No availability confirmation or no-show branch |
| POST | `/kabadiwala/pickups/:pickupId/complete` | Collector | Weigh, price and add inventory exactly once | 🟡 PARTIAL | None | Inventory is aggregated without movement history; final settlement/dispute is absent |
| GET | `/kabadiwala/inventory` | Collector | Read own inventory balances | 🟡 PARTIAL | None | No movement history and no explicit invariant projection |
| POST | `/kabadiwala/bulk-lots` | Collector | Reserve owned inventory into recycler lot | 🟡 PARTIAL | None | No idempotency key; movement history absent |
| GET | `/kabadiwala/bulk-lots` | Collector | List own bulk lots | ✅ COMPLETE | None | Detail/read projection missing |
| POST | `/kabadiwala/bulk-lots/:lotId/cancel` | Collector | Release unreserved lot | 🟡 PARTIAL | None | No idempotency/audit invariant test |
| GET | `/kabadiwala/bulk-offers` | Collector | Read offers for own lots | 🟡 PARTIAL | None | No pagination |
| GET | `/recycler/bulk-lots` | Verified Recycler | Browse collector lots | ✅ COMPLETE | None | Missing detail and route explanation |
| POST | `/recycler/bulk-lots/:lotId/offers` | Verified Recycler | Make/update an offer | 🟡 PARTIAL | None | Cannot explicitly withdraw/expire/counter; no idempotency header |
| GET | `/recycler/offers` | Verified Recycler | Read own offers | ✅ COMPLETE | None | None found in audit |
| POST | `/kabadiwala/bulk-offers/:offerId/accept` | Collector owner | Accept an offer | 🟡 PARTIAL | None | Missing reject/counter; race test required |
| POST | `/recycler/bulk-lots/:lotId/receive` | Verified Recycler | Receive a bulk lot | 🐛 BROKEN | None | Always rejects and directs to formal QR; contract should expose formal path clearly |
| POST | `/recycler/procurement-requirements` | Verified Recycler | Publish demand | ✅ COMPLETE | None | No idempotency/update/pause/cancel endpoint |
| GET | `/recycler/procurement-requirements` | Verified Recycler | List own demand | ✅ COMPLETE | None | No detail/update/pause/cancel |
| GET | `/kabadiwala/procurement-requirements` | Collector | Read open recycler demand | ✅ COMPLETE | None | No location filtering; no privacy-safe opportunity projection |

### Formalisation, pooling and supply handover

| Method | Endpoint | Allowed Role | Purpose | Status | Tests | Problems |
|---|---|---|---|---|---|---|
| GET | `/kabadiwala/route-advantage` | Collector | Explain net route economics | 🟡 PARTIAL | None | Does not include demand, minimum lot, payment reliability or platform fee; location baseline is broad; no idempotency concern |
| GET | `/kabadiwala/pool-opportunities` | Collector | Demand-triggered pooling opportunities | 🟡 PARTIAL | None | Supply is global rather than geographically eligible and existing pool totals are double-counted in some paths |
| POST | `/kabadiwala/pools` | Collector | Open canonical pool for demand | ✅ COMPLETE | None | Creator-only locking is intentional but needs tests |
| GET | `/kabadiwala/pools` | Collector | List own/member pools | 🟡 PARTIAL | None | Exposes other contributors' quantities before workflow stage |
| POST | `/kabadiwala/pools/:poolId/join` | Collector | Reserve own contribution | 🟡 PARTIAL | None | Rejoin/release accounting and pool threshold concurrency need tests |
| POST | `/kabadiwala/pools/:poolId/leave` | Collector | Release own contribution | ✅ COMPLETE | None | No explicit idempotency response |
| POST | `/kabadiwala/pools/:poolId/lock` | Pool creator | Lock threshold-met pool | ✅ COMPLETE | None | Needs state/audit route test |
| GET | `/recycler/pools` | Verified Recycler | Read own-target pooled consignments | ✅ COMPLETE | None | Does not expose QC/settlement summary contract |
| GET | `/kabadiwala/demand-intelligence` | Collector | Reverse demand summary | 🟡 PARTIAL | None | `activeDemands` and `isDemo` are misleading; nearby verified recycler count and pool suggestion missing |
| GET | `/kabadiwala/passport` | Collector | Collector growth passport | 🟡 PARTIAL | None | Only formal contribution/handovers and safety are counted; reliability, dispute and feedback metrics missing |
| GET | `/kabadiwala/safety` | Collector | Safety modules and progress | ✅ COMPLETE | None | Hard-coded guidance needs stable IDs/versioning |
| POST | `/kabadiwala/safety/:moduleKey/acknowledge` | Collector | Acknowledge safety module | ✅ COMPLETE | None | Any syntactically valid module key can be inserted |
| POST | `/kabadiwala/pools/:poolId/prepare-handover` | Pool creator | Prepare signed offline-capable pool QR | 🟡 PARTIAL | `formalisationIntegrity.test.ts` | No idempotency key and nonce replay record; QR response hides nonce but reconciliation is one-sided |
| POST | `/kabadiwala/bulk-lots/:lotId/prepare-handover` | Collector owner | Prepare signed bulk QR | 🟡 PARTIAL | `formalisationIntegrity.test.ts` | Same replay/idempotency gaps |
| POST | `/kabadiwala/handovers/:handoverId/collector-confirm` | Collector owner/member | Confirm handover before recycler receipt | 🟡 PARTIAL | None | No idempotency key; direct update outside transaction with audit transaction |
| GET | `/kabadiwala/handovers` | Collector/member | List supply handovers | ✅ COMPLETE | None | Pagination missing |
| GET | `/recycler/supply-handovers` | Verified Recycler | List incoming formal handovers | ✅ COMPLETE | None | Pagination missing |
| POST | `/recycler/handovers/confirm` | Verified target Recycler | Confirm/reconcile QR handover and settlement | 🟡 PARTIAL | `formalisationIntegrity.test.ts` | Reason code is auto-filled for material changes; replay/idempotency and zero accepted quantity need tightening |
| POST | `/kabadiwala/handovers/:handoverId/settlement` | Collector owner/member | Accept or raise issue on changed settlement | 🟡 PARTIAL | None | Good conditional update, but evidence pack and household path absent |
| GET | `/kabadiwala/handovers/:handoverId/passport` | Collector owner/member | Read formal material passport | ✅ COMPLETE | None | Does not include source listing events in all cases |

## Required but missing endpoints

These are the gaps identified before implementation. Some will be added as new endpoints; others will be completed by extending existing routes while preserving their paths.

| Required capability | Proposed backend contract |
|---|---|
| Household listing update/detail and disposal metadata | `GET/PATCH /api/v1/household/listings/:listingId`; request/response includes data-bearing device and destruction-evidence fields |
| Household pickup reschedule/status/reassignment | `POST /household/pickups/:pickupId/reschedule`, `POST /household/pickups/:pickupId/settlement`, `GET /household/pickups/:pickupId/passport`; reassignment is represented in pickup status and surfaced in list/detail |
| Collector reject/cancel/no-show/reassignment | `POST /kabadiwala/pickups/:pickupId/reject`, `POST /kabadiwala/pickups/:pickupId/cancel`, `POST /kabadiwala/pickups/:pickupId/confirm-availability`, `POST /kabadiwala/pickups/:pickupId/reassign` |
| Inventory movement history | `GET /kabadiwala/inventory/movements` with ownership-scoped cursor pagination |
| Recycler bulk-lot detail and complete offer lifecycle | `GET /recycler/bulk-lots/:lotId`, `POST /recycler/offers/:offerId/withdraw`, collector reject/counter endpoints |
| Demand lifecycle | `PATCH/POST /recycler/procurement-requirements/:requirementId` for update, pause/resume, cancel, and idempotent create |
| Pool suggestion/privacy-safe detail | `GET /kabadiwala/pools/suggestions` and privacy-safe pool projections |
| Safety routing | `GET /safety-routing?materialCategory=...&condition=...` returning hazard level, warning code, routing and guidance ID |
| Settlement anomaly/evidence query | `GET /kabadiwala/handovers/:handoverId/anomalies` or included in passport, with deterministic rule codes |
| Offline reconciliation idempotency | Idempotency-key support on collector and recycler handover confirmations, request-hash mismatch rejection, nonce replay protection and `REVIEW_REQUIRED` conflict state |
| Source-to-supply material passport | Household-facing `/household/pickups/:pickupId/passport` and formal passport events tied to the source listing |

## Differentiation implementation matrix (pre-implementation)

| Capability | Backend Exists? | Database Support? | Tests? | Complete? | Missing Work |
|---|---|---|---|---|---|
| Formal Route Advantage | Yes, basic deterministic estimate | Yes, `RouteAdvantageEstimate`, rates, prices | No route test | No | Add demand/minimum/reliability inputs, location-aware baseline, confidence and tests |
| Cooperative Pooling | Yes | Yes, pool/contribution/settlement models | No | Partial | Fix aggregate/ownership/privacy/concurrency and tests |
| Collector Growth Passport | Yes, basic | Yes, `CollectorPassport` | No | Partial | Add pickup/reliability/dispute/feedback-derived evidence and tests |
| Offline handover reconciliation | Yes, signed QR and two status steps | Yes, `SupplyHandover` | QR unit tests only | Partial | Idempotency, request hash, nonce replay and conflict reconciliation |
| Material Passport | Yes for formal supply handover | Yes, `MaterialPassportEvent` | QR/traceability tests | Partial | Source listing/pickup/inventory/bulk/pool/QC/settlement event completeness |
| Fairness Guard | Partial | Yes, settlement breakdown/anomaly flags | No | No | Require reason/evidence for material changes, structured evidence pack, tests |
| Anomaly Detection | Partial | Yes, `AnomalyFlag`, payment anomaly | No | Partial | Deterministic deduction/weight/range/cancellation/delay/duplicate rules |
| Reverse Demand Network | Yes, basic demand intelligence | Yes, procurement requirements/inventory | No | Partial | Correct nearby/verified aggregation, supply gap and demand-level contract |
| Demand-triggered pool suggestions | Yes, pool opportunities | Yes, pool/inventory | No | Partial | Eligibility geography, contributorsNeeded, privacy-safe suggestions, tests |
| Explainable Matching | Yes in recycler service and route reasons | Yes, rates/locations | Service optimization tests | Partial | Align match with route economics and expose stable reason codes |
| Confidence-aware Pricing | Yes, ranges/freshness/trend | Yes, price/history/source | Price tests | Partial | Observation count/confidence/source classification in all relevant responses |
| Safety Routing | Basic safety modules | `SafetyProgress` only | No | Partial | Stable material routing API and hazard metadata |
| Recycler Verification Freshness | Yes, status/evidence/expiry | Yes, recycler/audits | Verification tests | Partial | Review/expired/revoked semantics and route tests |
| Data-safe Disposal Evidence | No | No listing fields | No | No | Add source metadata/state/evidence contract |
| Pickup Reliability/Reassignment | No | Pickup lacks timestamps/reassignment state | No | No | Add fields/state transitions, metrics and tests |

## Implementation plan

1. Extend the schema for pickup reliability/settlement, disposal metadata, inventory movement, and idempotency request hashes; regenerate client against the test database only.
2. Complete household and collector pickup state machines with ownership, timestamps, rescheduling, rejection/cancellation/no-show/reassignment and exactly-once inventory movement.
3. Add household settlement/dispute/passport contracts and structured source-to-recycler passport events.
4. Harden pooling, bulk offers, demand lifecycle and route advantage calculations, including privacy-safe projections and deterministic explainable reasons.
5. Harden offline supply handover reconciliation with idempotency, request-hash conflict detection, replay protection, required change reasons and safe settlement accounting.
6. Add safety-routing and data-safe disposal APIs, then expand deterministic anomaly detection and growth-passport metrics.
7. Add indexes and bounded pagination where the new queries require them.
8. Add focused unit/integration/adversarial tests, run build/test/lint with the bundled runtime, perform a final break-it pass, and freeze the final contract/report.

## Post-implementation audit delta

The plan above was executed in this repository. The pre-implementation tables remain useful as the gap record; the following delta is the final status reconciliation against the current source tree and the frozen contract in `BACKEND_API_CONTRACT.md`.

### Completed route and state changes

| Area | Final status | Evidence in source |
|---|---|---|
| Household listing lifecycle | ✅ COMPLETE | Listing create/list/detail/edit/cancel, disposal metadata, owner-preparation state, and source passport in `src/routes/supplyChainRoutes.ts` |
| Household pickup lifecycle | ✅ COMPLETE | Detail/passport/reschedule/settlement/cancel plus single-winner pickup claim and request-hash idempotency |
| Kabadiwala reliability | ✅ COMPLETE | Reject, availability confirmation, schedule, status, cancel, no-show/reassignment and server timestamps |
| Exactly-once inventory | ✅ COMPLETE | `InventoryMovement`, invariant checks, conditional completion claim, bulk/pool reservation/release movements |
| Bulk offer lifecycle | ✅ COMPLETE | Recycler detail/withdraw, Kabadiwala reject/counter/accept, explicit legacy receive guard |
| Demand lifecycle | ✅ COMPLETE | Procurement requirement patch for active/paused/cancelled lifecycle and validation |
| Route advantage | ✅ COMPLETE | Location-aware price baseline, confidence, demand/minimum lot, payment proxy, fee, gross/net and explainable reasons |
| Pooling privacy/accounting | ✅ COMPLETE | Aggregate suggestions, own-contribution projection, inventory movement accounting, creator-only lock |
| Growth passport | ✅ COMPLETE | Derived pickup completion/cancellation/no-show/late-cancellation, dispute and feedback metrics |
| Offline formal handover | ✅ COMPLETE | Idempotency request hashes, persisted QR nonce hash comparison, replay-safe collector/recycler confirmations, conditional settlement |
| Fairness/anomaly rules | ✅ COMPLETE | Reason/evidence requirements for material/weight/rate changes, deterministic variance codes/risk projection, household settlement issue path |
| Safety routing | ✅ COMPLETE | Stable hazard/routing/warning/guidance response; no hazardous dismantling instructions |
| Recycler freshness | ✅ COMPLETE | Future-validity checks and lifecycle statuses `UNDER_REVIEW`, `REVIEW_REQUIRED`, `EXPIRED`, `REVOKED` |

### Final route additions

The implementation added or completed these paths without removing legacy paths:

- `GET/PATCH /household/listings/:listingId`
- `GET /household/pickups/:pickupId/passport`
- `POST /household/pickups/:pickupId/reschedule`
- `POST /household/pickups/:pickupId/settlement`
- `POST /kabadiwala/pickups/:pickupId/reject`
- `POST /kabadiwala/pickups/:pickupId/confirm-availability`
- `POST /kabadiwala/pickups/:pickupId/cancel`
- `POST /kabadiwala/pickups/:pickupId/reassign`
- `GET /kabadiwala/inventory/movements`
- `GET /recycler/bulk-lots/:lotId`
- `POST /recycler/offers/:offerId/withdraw`
- `POST /kabadiwala/bulk-offers/:offerId/reject`
- `POST /kabadiwala/bulk-offers/:offerId/counter`
- `PATCH /recycler/procurement-requirements/:requirementId`
- `GET /kabadiwala/pools/suggestions`
- `GET /safety-routing`
- `GET /kabadiwala/handovers/:handoverId/anomalies`

### Final verification

- `tsc -p tsconfig.json`: passed.
- `tsc -p tsconfig.json --noEmit` (lint equivalent): passed.
- Vitest: **24 test files, 60 tests passed**.
- Prisma client generation: passed.
- Prisma schema validation with a local non-production test URL: passed.
- `pnpm` package-script execution was attempted with the bundled runtime. The installed pnpm version repeatedly reconciled `node_modules` during lifecycle execution in this restricted Windows workspace; the equivalent direct compiler/Vitest commands passed after restoring the locked dependency layout. No source failure was found.
- Prisma `db push`/migration was intentionally not run: the repository has no configured test MongoDB URL, and the checked-in example URL is not a usable database. No production database was contacted.

The complete final endpoint-by-endpoint contract is in `docs/BACKEND_API_CONTRACT.md`.
