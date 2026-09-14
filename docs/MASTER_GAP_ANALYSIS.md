# Kabadiwala Connect — Master Gap Analysis

Date: 2026-09-14  
Baseline: `main` at `efd7010` (`Fix auth races and unavailable price responses`)  
Scope: Android prototype, TypeScript/Prisma backend, local test database/runtime, connected Android emulator.

This is an evidence-backed audit against `C:\Users\pulki\Downloads\KABADIWALA_CONNECT_SELECTION_MASTER_PROMPT.md`. “Implemented” means the behavior is present in code and covered by an automated test or an existing documented workflow. “Partial” means a useful primitive exists but the end-to-end product behavior, live-role path, or evidence is incomplete. Seeded/demo records are not treated as live market, government, recycler, ML, environmental, or field-research evidence.

## Phase 0 environment evidence

| Check | Result | Evidence |
|---|---|---|
| Git baseline | Clean at audit start | `git status --short --branch`; `git log -1 --oneline` |
| Backend | Build, lint, 22 files / 55 tests pass | `backend/package.json`; `npm run build`; `npm run lint`; `npm test` |
| Android | Debug unit tests pass | `android_app/`; `gradlew.bat testDebugUnitTest` |
| Android build | Debug APK assembled successfully | `gradlew.bat assembleDebug` |
| Device | Connected emulator `emulator-5554`, Android 17, 1080×2400 override | `C:\Users\pulki\AppData\Local\Android\Sdk\platform-tools\adb.exe devices -l`; `adb shell getprop`; `adb shell wm size` |
| Fresh launch | Current APK installed and language onboarding rendered | `adb install -r app-debug.apk`; `adb shell monkey`; `adb shell uiautomator dump` |
| Live backend | Local reverse-forwarded development API exercised by authenticated device flows; no production deployment | `android_app/app/build.gradle.kts`, `backend/.env`, `DEMO_RUNBOOK.md` |

## Product and architecture summary

The repository contains two overlapping supply-chain boundaries:

1. The newer live role path: `HOUSEHOLD → PickupRequest → COLLECTOR inventory → BulkLot → verified RECYCLER offer/receive`, implemented in `backend/src/routes/supplyChainRoutes.ts` and `android_app/.../ui/screens/supplychain/`.
2. The older collector-to-recycler path: `Lot → Quote → Handover → Payment → timeline`, implemented in `backend/src/services/` and Android legacy screens, but intentionally hidden from live collector sessions by `AppNavHost.kt` unless demo mode is enabled.

The master prompt requires one coherent chain. The primary implementation decision is therefore to extend the newer live boundary with the differentiation stack, and to reuse legacy cryptographic/settlement primitives only where their contracts are correct.

## Requirement and gap matrix

| Area | Status | Evidence | Gap / decision |
|---|---|---|---|
| Explicit three-role identity | Implemented | `backend/prisma/schema.prisma` `User`, `AccountRole`; `authController.ts`; `middleware/auth.ts` | Preserve explicit server role. Do not infer role from email or UI route. |
| Household listings and selected pickup | Implemented / partial | `supplyChainRoutes.ts` listing, pickup, cancellation; `HouseholdSupplyScreen.kt` | Final settlement confirmation, rematch after collector failure, distance/reliability explanation and data-safe disposal are incomplete. |
| Kabadiwala intake and inventory | Implemented / partial | `supplyChainRoutes.ts` pickup completion, `InventoryBalance`; `SupplyChainViewModel.kt` | Core atomic increment exists. Add audit events, stronger movement/reservation records, clearer settlement ownership and offline live-path mutation handling. |
| Recycler pending verification | Implemented | `RecyclerAuthorizationStatus`, `requireRecycler`, verification routes/services, Android verification screens, `recyclerVerification.test.ts` | Expiry/review-required lifecycle and operator evidence readback need explicit end-to-end coverage. |
| Recycler demand | Implemented primitive / partial | `ProcurementRequirement`, recycler create/list routes and UI | No supply-gap calculation, collector targeting, notification fan-out, pooling suggestion, or price-board demand signal. |
| Recycler bulk lots/offers/receive | Implemented / partial | `BulkLot`, `BulkOffer`, formal QR handover routes and `RecyclerSupplyScreen.kt` | Direct receive bypass is blocked. QC/settlement evidence exists for formal handover; payment/earnings linkage remains incomplete. |
| Formal Route Advantage | Implemented / partial | `formalisationRoutes.ts`, `FormalisationDashboard` | Net route estimate, baseline, confidence, freshness and “why” are present. Historical deductions and validated field savings are not. |
| Cooperative pooling | Implemented / partial | `Pool*` Prisma models, atomic routes, Android dashboard | Independent contributions, threshold/lock/leave, proportional settlement and oversell guards exist. Shared pool state is online-only and no inter-collector trade is allowed. |
| Offline dual-confirmation handover | Implemented / partial | `SupplyHandover`, signed QR, `CONFIRM_SUPPLY_HANDOVER`, formal cache and Android handover screens | Cached evidence and queued Recycler confirmation exist. Marketplace mutations and fully local QR cryptographic verification remain online-bound. |
| Material Passport | Implemented / partial | `MaterialPassportEvent`, `AuditEvent`, `MATERIAL_PASSPORT.md`, formal handover endpoints | Live formal chain has event/hash/reference/quantity evidence. Official certification, external notarisation and full payment linkage are not claimed. |
| Fairness & Dispute Guard | Implemented / partial | `SettlementBreakdown`, `AnomalyFlag`, QC/Collector decision routes | Variance thresholds, reason/evidence, review state and accept/issue actions exist. Admin/human resolution UI remains incomplete. |
| Collector Growth Passport | Implemented / partial | `CollectorPassport`, `SafetyProgress`, dashboard and passport endpoint | Platform-generated kg/handovers/safety labels exist. It is not government/CPCB certification or income proof. |
| Data-safe household disposal | Partial / future | Hazardous-material acknowledgement and passport evidence exist; no destruction certificate | Add preparation/checklist states only if real disposal evidence can be captured. Never claim destruction without recycler evidence. |
| Safety-first handling | Implemented / partial | `InfoScreens.kt`, live listing warning/acknowledgement, lot material warnings, `WasteRegime` | Hazardous guidance is now gated for battery/CRT/PCB; pictorial/native-speaker/TTS review remains. No dismantling instructions. |
| Price board credibility | Implemented / partial | `Price`, `PriceHistory`, board/history routes, `PricesScreen.kt`, seeded data | Range/source/freshness/observations exist, but live route advantage and demand overlay do not. Keep `DEMO / SEEDED DATA` visible in testing. |
| Explainable valuation/matching | Partial | `PriceService`, `RecyclerService`, `Valuation.kt`, `RecyclerMatching.kt` | Preserve explainable statistics. Do not add ML claims without data card/evaluation. |
| Offline reference data | Implemented / partial | Room price/recycler caches, `FormalisationCacheStore`, repositories | Formal evidence cache is account-scoped; shared pool mutations and full listing/pickup replication remain online-bound. |
| Sync operation integrity | Implemented / partial | `SyncOperation`, `/sync`, `SyncService`, `SyncQueue`, formal handover queue | LOT/PAYMENT and Recycler formal confirmation are supported with role/validation/replay guards; broader marketplace mutations are intentionally not queued. |
| State machines | Partial | `lotStateMachine.ts`, enums, route conditional updates | Supply-chain state transitions are inline strings and lack shared transition/audit helpers. Add controlled transitions for pickup, pool, lot, handover, verification and settlement. |
| Audit history | Implemented / partial | `AuditEvent`, `MaterialPassportEvent`, supply-chain transition helpers | Critical live transitions emit audit/passport events; a stronger append-only inventory movement ledger remains future hardening. |
| Role and ownership enforcement | Implemented / partial | `middleware/auth.ts`, `ROLE_ARCHITECTURE_AUDIT.md`, route predicates | Strong baseline. Add adversarial integration tests for every new endpoint and review `futureRoutes.ts`/legacy boundaries for accidental household access. |
| Household UI boundary | Implemented / partial | `AppNavHost.kt`, `HouseholdSupplyScreen.kt` | Live UI is correctly separate, but strings are largely hardcoded and there is no final settlement/passport view. |
| Kabadiwala UI boundary | Implemented / partial | live route allow-list and `SupplyChainScreens.kt` | Live UI now exposes route, pooling, handover, passport, safety and settlement surfaces; critical strings remain partly hardcoded. |
| Recycler UI boundary | Implemented / partial | recycler route allow-list and screens | Formal supply handovers, QR confirmation and QC fields are present; review resolution/payment linkage remain incomplete. |
| English/Hindi/Marathi critical flows | Partial | `res/values`, `values-hi`, `values-mr`, locale tests | Shared shell is localized, but live supply-chain screens use hardcoded English. Add string resources for critical actions, states, safety, pricing and sync. |
| Low-literacy UX/TTS | Partial | large controls, `PriceSpeaker`, safety screen, Material 3 theme | Add icon+word labels, spoken route/settlement/safety actions, and visible offline/conflict states to live flow. |
| Accessibility | Partial | Compose semantics in tests and large touch targets | Audit content descriptions, contrast, keyboard/IME overlap and status color + text pairing on new screens. |
| Performance | Partial, measured baseline | `docs/PERFORMANCE_REPORT.md`; emulator cold launch and PSS captured | One cold jank event was observed; representative low-end device and full frame/network profiling remain. |
| Documentation/evidence | Implemented / partial | required docs under `docs/` plus final emulator readback | Core evidence, threat, offline, passport, differentiation, performance and limitation docs now exist; field research, low-end-device evidence and official checks remain external evidence. |
| Field research | Not conducted / must remain blank | `docs/FIELD_RESEARCH_PROTOCOL.md` exists | Do not create `FIELD_RESEARCH_RESULTS.md` or claim interviews without human sessions. |
| Unit economics | Partial | `docs/UNIT_ECONOMICS.md` | Expand formula/scenarios/sensitivity to actual route-advantage and pooling outputs; label assumptions. |
| Demo readiness | Implemented / partial | `docs/DEMO_RUNBOOK.md`, `SUPPLY_CHAIN_TEST_RUNBOOK.md`; ADB UI/API readback | Integrated route → pool → QR → receipt → passport/offline path is demonstrable with seeded data; field and admin gaps remain. |

## Classification of legacy/future areas

- Preserve: auth/session security, explicit roles, existing recycler verification, price provenance/ranges, collector ownership, Room migrations, WorkManager foundation, signed legacy QR where compatible, and tested lot/quote/payment services.
- Refactor: supply-chain inline mutations, live recycler receive behavior, hardcoded live UI strings, and the split between live supply-chain and legacy handover screens.
- De-emphasize: rewards, government schemes, DIY activities, chat and broad future features until the core collector formalisation stack is complete. These are not evidence of differentiation.
- Do not add: blockchain, unsupported ML, fake live prices, fake government APIs, environmental impact claims, crypto rewards, or a giant admin UI.

## Phase 0 conclusion

The repository is now a stronger prototype implementation of the requested
core stack, but it is not a production-ready or field-validated product. The
remaining material risks are incomplete localization, limited offline scope,
admin dispute resolution/payment linkage, lack of real field research, one
emulator-observed cold-start jank event and non-production signing-key handling.
The core connected device workflow and a process-death/reconnect lot sync are
now evidenced. The final adversarial review also fixed session-expiry data loss
and the minified WorkManager reflection defect; the execution plan and
remaining limitations keep claims bounded.
