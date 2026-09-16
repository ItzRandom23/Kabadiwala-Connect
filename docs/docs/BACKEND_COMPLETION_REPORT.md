# Kabadiwala Connect Backend Implementation Report

Completion date: 2026-09-15
Scope: backend only; no production deployment and no Android/frontend changes.

## Outcome

The audit, gap matrix, implementation pass, hardening pass, test expansion and adversarial review are complete as an engineering pass. The backend itself is **not certified complete**: live MongoDB integration/concurrency proof, external payment execution, granular contract schemas, and some operational/policy workflows remain open. Existing legacy lot/quote/handover/payment flows were preserved. The household → Kabadiwala → verified Recycler path now has server-enforced ownership, reliability states, inventory accounting, pooling, demand, formal handover/QC, traceability, pickup/formal payment evidence, reversal evidence, and role-aware generalized offline sync primitives.

The endpoint-by-endpoint contract is a current candidate contract in [BACKEND_API_CONTRACT.md](./BACKEND_API_CONTRACT.md), not a freeze approval. The evidence-first final verdict is in [BACKEND_FINAL_VERIFICATION.md](./BACKEND_FINAL_VERIFICATION.md); the initial baseline remains in [BACKEND_API_AUDIT.md](./BACKEND_API_AUDIT.md).

## Existing backend audited

- Express app mounted at `/api/v1` with health, auth, collector, legacy lots, prices, recycler verification, quotes, handovers, payments, sync, notifications, activity, dataset and future routes.
- Prisma MongoDB schema with legacy lot/quote/handover/payment models and the newer household listing, pickup, inventory, bulk-lot, pool and supply-handover models.
- Central JWT role middleware and centralized error/request logging middleware.
- Existing service/unit tests for authentication, roles, quote privacy/lifecycle, handover lifecycle/integrity, traceability, prices, payments, recycler verification, state machines and validation.

## Implemented changes

### Data integrity and database

- Added pickup lifecycle timestamps, late-cancellation/no-show/reassignment fields and household settlement decision/evidence fields.
- Added `REASSIGNMENT_REQUIRED`, settlement and data-destruction evidence state enums.
- Added household data-bearing device/preparation/destruction metadata.
- Added `InventoryMovement` with source/action metadata and indexes.
- Added `IdempotencyRecord.requestHash` for same-key/different-payload rejection.
- Added structured Recycler grade capability/logistics fields, formal QC state/evidence, pickup settlement payment, formal payment reversal evidence, and related indexes/relations.
- Added a pickup status/slot index and reused existing ownership/unique constraints for single-winner pickup claims.
- Added `inventoryLedger.ts` invariant checks and append-only movement recording.
- Generated Prisma Client and validated the schema using a local non-production test URL. No database push was attempted because no test MongoDB instance is configured.

### Household and Kabadiwala operations

- Household listing detail/edit/cancel and source material passport.
- Household pickup detail, pickup-level passport, reschedule, settlement acceptance/issue, and late-cancellation classification.
- Kabadiwala accept/reject/availability confirmation/schedule/status/cancel/reassign flows.
- No-show/reassignment state reopens the source listing for another Kabadiwala; Android must render the “Find another Kabadiwala” action.
- Completion now conditionally claims the pickup, requires a reason for material/weight/value changes, settles to pending household confirmation, and records inventory/passport/audit evidence exactly once.
- Household can filter replacement Kabadiwalas by complete coordinates/radius, commit a replacement assignment idempotently, and receive a persisted reassignment notification. A Collector can record the accepted pickup settlement payment exactly once; underpayment becomes a disputed/anomalous record.

### Recycler, demand and pooling

- Bulk-lot detail, offer withdrawal, collector reject/counter, and explicit compatibility guard for the old receive endpoint.
- Procurement requirement update/pause/resume/cancel lifecycle.
- Route advantage now includes location-aware price baselines, confidence, observation count, demand relevance, grade/weight/minimum-lot eligibility, configured logistics, payment-reliability proxy, platform fee, gross/net estimates and explainable reasons.
- Recycler capabilities expose accepted grades, pickup inclusion/fee, and configured logistics cost; demand/pool projections require current authorization, supported grade/material, and complete coordinates for radius eligibility.
- Pool suggestions are demand-triggered and privacy-safe. Pool lists expose own contribution and aggregate counts rather than other collectors’ quantities.
- Pool join/leave and bulk-lot reserve/release write inventory movements and enforce invariants.
- Demand intelligence is database-backed and returns supply gaps, verified recycler counts, deadlines and pool opportunity; demo labeling is explicit.

### Formalisation, fairness and safety

- Collector and recycler formal handover confirmations support request hashes, idempotent replay, nonce-hash checking, evidence and conditional state transitions.
- `/sync` now supports role-aware formal pool contribution/release, settlement decision, collector/Recycler handover confirmation, QC, disposal evidence and formal payment operations with transactionally recorded sync/audit/passport evidence.
- Verified Recyclers can record one conditional PASS/FAIL QC decision with notes/evidence; failures move the formal handover/pool to review and create a high-severity anomaly.
- Admins can record provider-neutral formal payment reversal evidence; the internal payment moves to `REVERSED`, but no external provider is called.
- Material/weight/rate variance rules produce deterministic rule codes and risk severity; material mismatch is high risk.
- Household and collector settlement issue paths require reason codes/evidence and write anomaly/audit/passport records.
- Added handover anomaly query and structured safety-routing response.
- Recycler verification accepts review/expiry/revocation lifecycle states and requires future validity for active authorization.
- Recycler matching and route advantage return stable human-readable reasons and `whyThisMatch` explanations.

## Differentiation status

Status legend: ✅ backend complete; 🟡 backend foundation complete with a stated limitation; 🔵 Android implementation required; 🚫 intentionally excluded.

| Capability | Status | Backend evidence and limitation |
|---|---|---|
| Formal Route Advantage | 🟡 | Deterministic economics, grade/weight eligibility, configured logistics, demand, price confidence and explanation are implemented; provider quotes and historical risk modeling are not. |
| Cooperative Pooling | ✅ | Canonical demand pools, privacy-safe membership, inventory reservation/release, creator lock and formal supply handover are implemented. Live route optimization remains outside backend scope. |
| Collector Growth Passport | 🟡 | Derived reliability, completion, cancellation, no-show, dispute, feedback and formal-evidence metrics are returned; it is a platform evidence projection, not a government credential. |
| Offline dual handover confirmation | 🟡 | Backend supports signed QR/nonce, collector/recycler confirmation, generalized formal sync operations, request hash replay and review states; Room/WorkManager/offline UI is 🔵 Android implementation required. |
| Material Passport | 🟡 | Source listing, pickup, inventory movement and formal events are queryable; external regulator certification is intentionally not asserted. |
| Fairness Guard | ✅ | Settlement changes require reason/evidence, conditional transitions prevent silent overwrite, and household issue decisions create anomaly evidence. |
| Anomaly Detection | 🟡 | Deterministic variance, duplicate/replay, inventory and pickup-reliability signals are implemented; a statistical/ML risk model is not part of this backend pass. |
| Reverse Demand Network | ✅ | Active procurement demand, verified recycler capability, supply, gaps and deadlines are database-backed and privacy-safe. |
| Demand-triggered pool suggestions | ✅ | Suggestions expose aggregate eligible supply, contributor estimate and gap without contributor identity leakage. |
| Explainable Matching | ✅ | Match rows include score, reasons, authorization snapshot and `whyThisMatch`. |
| Confidence-aware Pricing | ✅ | Board and route responses include ranges, freshness/source classification, observation count and confidence. |
| Offline-first operations | 🟡 | Sync deltas and idempotent mutation contracts are available; Android queue/retry/conflict UX is 🔵. |
| Low-literacy/accessibility flow | 🔵 | Android implementation required: language layouts, TTS, accessibility, scan/display and action design. Backend supplies stable safety IDs and state codes. |
| Safety routing | ✅ | Hazard level, warning code, recommended route, required recycler capability and guidance ID are exposed without hazardous dismantling instructions. |
| Recycler verification freshness | ✅ | Future expiry and under-review/review-required/expired/revoked lifecycle enforcement is implemented. |
| Data-safe disposal evidence | 🟡 | Data-bearing/preparation/destruction state and evidence references are stored, queueable and exposed; physical wiping, durable evidence storage/authenticity and Recycler evidence capture UI are 🔵 Android/operator implementation required. |
| Pickup reliability/reassignment | 🟡 | Timestamps, cancellation/no-show/late-cancellation, household reassignment commit, persisted notifications, settlement payment and derived reliability metrics are implemented; disputed inventory reconciliation policy and live proof remain. |
| Formalization without replacing Kabadiwalas | ✅ | The formal flow consumes Kabadiwala inventory/pools and preserves Kabadiwala ownership; no open collector-to-collector marketplace was introduced. |

## Security and adversarial pass

Checked and covered by source review plus the 25-file test suite:

- Role boundaries for household, Kabadiwala, recycler and admin tokens.
- Ownership/IDOR protections on listing, pickup, lot, quote, handover, payment, inventory, pool and conversation flows.
- Verified recycler plus validity-date checks before matching, quoting, offers and handovers.
- Single-winner household pickup claim and conditional state updates for duplicate/concurrent actions.
- Idempotency-key format, same-key/different-body conflicts and canonical replay responses.
- QR signature/expiry/nonce protection and material/weight/rate variance review.
- Negative/NaN inventory values and movement invariant protection.
- Privacy-safe pool/demand/recycler projections and pseudonymized dataset export.
- Upload limits, bounded request bodies, centralized error envelopes and non-production-only schema validation.

Remaining security work for a production hardening cycle would be operational rate-limit/load testing against a real staging MongoDB, dependency vulnerability scanning, and full route-level integration tests with seeded role-separated accounts.

## Verification results

Using the bundled Node runtime in the repository’s backend workspace:

- `tsc -p tsconfig.json` — passed.
- `tsc -p tsconfig.json --noEmit` — passed (lint-equivalent check).
- Vitest — **25 test files passed; 68 tests passed**.
- Prisma Client generation — passed.
- Prisma schema validation with `mongodb://127.0.0.1:27017/kabadiwala_connect_test?directConnection=true` — passed without connecting to that database.
- `pnpm` package-script attempts were made with the bundled runtime. In this restricted Windows workspace, pnpm lifecycle reconciliation repeatedly recreated `node_modules`; after restoring the locked dependency layout, the equivalent direct compiler and Vitest gates passed. This is an environment/package-manager issue, not a TypeScript or test failure.

## Known limitations and handoff

- No test MongoDB server is configured in the repository, so Prisma `db push`, seed execution and live transaction/integration tests were not run. The checked-in `.env.example` contains a placeholder database URL and was not used against any external environment.
- The implementation did not deploy or contact production.
- Android/frontend work is not included. See the explicit `ANDROID IMPLEMENTATION REQUIRED` section in the API contract.
- Legacy and new supply paths remain separate because their data models and ownership semantics differ; the contract documents both.
- The old recycler bulk receive route intentionally returns a migration conflict instead of bypassing formal signed handover controls.
