# Kabadiwala Connect — Execution Plan

Date: 2026-09-14  
Status: Phase 0 complete; implementation proceeds automatically in priority order.  
Environment: testing/SIH prototype only; no production deployment.

## Product/design lock

Designing a collector-first operational Android app for informal e-waste collectors, with Household as the upstream source and Recycler as the downstream formal buyer.

Goal: make the formal route understandable and economically defensible for small, low-connectivity collector operations.

Tone: precise field workbench — warm, high-contrast, calm enough for outdoor use, with a restrained amber value signal and deep green trust signal.

Main objection/risk: a headline recycler rate may not beat the collector’s local route once travel, minimum lots, delays and deductions are included.

Must remember: the memorable product action is the explicit net-route calculation followed by cooperative pooling and a two-party material handover.

Constraints: Material 3; minSdk 23; entry-level devices; English/Hindi/Marathi; dark mode; offline-first; no fabricated live/official/ML/field claims; preserve working code.

Research needed: craft references for theme/composition/accessibility and the supplied master document’s competitor/research basis; live Refero MCP was unavailable in this environment.

Path: audit → reference lock → backend/domain implementation → Android integration → real-device adversarial QA → evidence refresh.

Reference lock: use the existing field-operations visual foundation in `ui/theme/Color.kt` and `Theme.kt`, strengthened with the Android Compose craft references:

- Preserve: light-first high-contrast warm canvas, deep green trust primary, amber reserved for value/attention, tonal surface depth, varied role-based radii, large controls and icon+word labels.
- Borrow only: a precise instrument-panel hierarchy for net-value and quantity metrics, and asymmetrical surface shapes for focal workflow cards.
- Role rules: green means trust/primary action; amber means value/attention; semantic success/warning/error always pair color with text/icon; demo status stays explicit.
- Media strategy: use actual captured listing/evidence photos; reserve stable thumbnail bounds; no decorative fake photography.
- Reject: indigo/violet defaults, uniform card grids, emoji icons, decorative accent stripes, dark-only default, and claims not backed by data.

## Priority scale

- P0 — correctness, official requirements, role security, the differentiation stack, and the testable main supply-chain story.
- P1 — demo reliability, low-literacy/localized UX, accessibility, performance and evidence.
- P2 — future integrations or non-core enhancements that are explicitly documented, not implied complete.

## Phase order and deliverables

### Phase 0 — repository and requirement audit — COMPLETE

- [x] Inspect Android, backend, schema, routes, tests, navigation, localization, Room, WorkManager, auth and role matrix.
- [x] Run backend build/lint/tests and Android unit tests.
- [x] Build current debug APK and verify connected emulator state.
- [x] Create `docs/MASTER_GAP_ANALYSIS.md`.
- [x] Create this `docs/EXECUTION_PLAN.md`.

### Phase 1 — role and data integrity — P0 — COMPLETE (code + automated checks)

1. Add shared backend domain models for `AuditEvent`, collector passport/safety progress, route advantage estimate, pooled consignment/contribution, material passport events, handover confirmations/evidence, settlement breakdown, anomaly flags and optional data-safe disposal states. Reuse existing `User`, `Collector`, `Recycler`, `InventoryBalance`, `BulkLot`, `Payment`, `Handover` where correct.
2. Add server-owned state-transition helpers and idempotency keys for new critical operations.
3. Enforce ownership and verified-recycler requirements on all new routes; reject household/recycler/other-collector mutations at backend regardless of hidden UI.
4. Make inventory movements explicit and atomic; ensure pool reservation/cancellation/settlement cannot oversell or create negative balances.
5. Emit audit events for pickup completion, inventory movement, bulk/pool reservation, offer acceptance, handover confirmation, QC/settlement and dispute changes.
6. Add backend integration tests for role matrix, duplicate/replay, optimistic conflicts, pool threshold/cancellation, deduction guard and expired verification.

Exit evidence: `npm run build`, `npm run lint`, `npm test` pass; role and
handover integrity tests pass; the remaining end-to-end evidence is recorded
in the final report after the device pass.

### Phase 2 — baseline end-to-end live workflows — P0 — IMPLEMENTED; device evidence captured

1. Complete Household listing → selected Kabadiwala pickup → final weighing/settlement → collector inventory with honest range and status feedback.
2. Complete collector inventory → single bulk lot → verified Recycler offer → collector acceptance → handover/QC/payment.
3. Add rematch/reassignment and cancellation reason flows where an accepted pickup fails before completion.
4. Add recycler QC fields (received weight, accepted/rejected quantity, grade/contamination, reason/evidence) and collector accept/raise issue actions.
5. Create a Material Passport event timeline from origin/intake to recycler receipt/payment.

Exit evidence: fresh Household, Kabadiwala and Recycler accounts were used on
`emulator-5554`; listing creation, collector route comparison, pooling and a
formal Recycler handover completed with backend readback. A separate offline
Collector lot survived process death and synchronized once after reconnect.
All records are seeded/development evidence.

### Phase 3 — differentiation stack — P0 — IMPLEMENTED; device evidence captured

Implement in the required order:

1. Formal Route Advantage: offer, cost, distance, pickup, authorization freshness, confidence and local baseline; show calculation and “Why this match?”.
2. Cooperative Pooling: independent collector-owned contributions, threshold math, opt-in/lock/cancel, route, per-contribution QR/reference and settlement allocation. No collector-to-collector trade.
3. Offline Material Passport: prepared one-time handover payload/nonce, collector and recycler confirmations, evidence hash/reference, replay rejection, queued sync and `REVIEW_REQUIRED` conflicts.
4. Fairness & Dispute Guard: quote-vs-final breakdown, reason/evidence requirement, anomaly flag, audit record, collector accept/raise issue and evidence pack.
5. Collector Growth Passport: platform-generated counts, formal kg, handovers, reliability, safety completion and recycler feedback; clearly not official certification.
6. Reverse Demand Network: demand fan-out/targeting, supply gap, demand levels, pooling suggestion and privacy-safe demand signal.

Exit evidence: the connected emulator exercised route advantage, two-collector
pool contribution/threshold/lock, one-time signed QR, Recycler material-passport
verification/receipt, Collector passport metrics, cached offline evidence and
one durable offline lot replay. The prototype does not claim the master
prompt's illustrative 530 kg scenario as real volume; the executed fixtures
were smaller seeded quantities.

### Phase 4 — low-literacy/offline UX — P1 — PARTIAL

- [ ] Move critical live supply-chain strings into resources for English/Hindi/Marathi.
- [x] Add hazardous-material warning/acknowledgement and explicit cached/live labels.
- [ ] Add pictorial safety and TTS for hazardous guidance, prices, route advantage and handover status.
- [ ] Add prominent online/offline/sync/conflict states, restart-safe drafts and large touch targets.
- [ ] Verify TalkBack/content descriptions, contrast, IME/keyboard overlap, back navigation and no misleading precision.

### Phase 5 — data/AI boundaries — P1/P2 — DOCUMENTED; no new ML claim

- [ ] Keep explainable statistics/rules as the shipped baseline.
- [ ] Capture privacy-safe correction/outcome fields for future models.
- [x] Create/update `docs/DATASET_CARD.md`.
- [x] Create/update `docs/AI_MODEL_CARD.md` for the existing assistive integration; no measured accuracy claim.

### Phase 6 — performance — P1 — BASELINE CAPTURED; FOLLOW-UP RISK RECORDED

- [x] Measure cold startup and process memory on the connected emulator; record the invalid warm-start measurement honestly.
- [ ] Fix stable list keys, duplicate calls, main-thread work, image bounds and broad state invalidation where evidence shows a problem.
- [x] Record actual measurements and qualitative frame/jank observations in `docs/PERFORMANCE_REPORT.md`.

### Phase 7 — QA/adversarial — P0/P1 — COMPLETE FOR PROTOTYPE SCOPE

- [x] Run the connected Household/Kabadiwala/Recycler matrix and fresh-install/reinstall checks.
- [x] Attempt cross-role, ownership, duplicate, replay, stale state, oversell, settlement and pending-verification attacks against the backend.
- [x] Exercise device network-off/reconnect/restart, cached evidence, language resources and dark-mode-capable Compose theme. Remaining accessibility/localization limits are documented.
- [x] Perform the final adversarial review; the scanner form's missing scroll, formal-weight default, session-expiry data loss and minified WorkManager reflection defect were found and fixed before the final build.

### Phase 8 — evidence/demo — P1 — COMPLETE WITH EXPLICIT LIMITATIONS

Maintain these documents from actual implementation/tests:

- [x] `MASTER_GAP_ANALYSIS.md`
- [x] `EXECUTION_PLAN.md`
- [x] `ROLE_ARCHITECTURE_AUDIT.md`
- [x] `REQUIREMENTS_EVIDENCE.md`
- [x] `COMPETITOR_DIFFERENTIATION.md`
- [x] `UNIQUE_VALUE_PROPOSITION.md`
- [x] `UNIT_ECONOMICS.md`
- [x] `FIELD_RESEARCH_PROTOCOL.md`
- [ ] `FIELD_RESEARCH_RESULTS.md` only after real human research
- [x] `DATASET_CARD.md`
- [x] `AI_MODEL_CARD.md` for the existing assistive integration; no accuracy claim
- [x] `OFFLINE_SYNC_DESIGN.md`
- [x] `MATERIAL_PASSPORT.md`
- [x] `SECURITY_THREAT_MODEL.md`
- [x] `PERFORMANCE_REPORT.md` (emulator baseline captured; field-device pass remains)
- [x] `DEMO_RUNBOOK.md`
- [x] `KNOWN_LIMITATIONS.md`

## Test commands

```text
cd backend
npm run build
npm run lint
npm test

cd ../android_app
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug
```

Device contract:

```text
adb devices -l
adb uninstall com.irinteractivestudios.kabadiwalaconnect
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.irinteractivestudios.kabadiwalaconnect 1
```

## Stop conditions

Proceed without approval. Stop only for a genuine external blocker such as unavailable database credentials, inaccessible device/backend, a destructive action outside the testing scope, or an irreducible product choice with equally valid mutually exclusive outcomes. Do not deploy production or fabricate evidence.
