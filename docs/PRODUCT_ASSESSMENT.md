# Kabadiwala Connect — Product and Production-Readiness Assessment

**Assessment date:** 15 September 2026  
**Scope:** Working-tree source under `android_app/` and `backend/`, existing engineering documents, and a current public-market scan.  
**Bottom line:** Kabadiwala Connect is a substantial Android-first beta/SIH prototype with a credible backend foundation. It is not yet ready for open public production, but it can become pilot-ready through a tightly scoped, one-city deployment of the Household → Kabadiwala → verified Recycler journey.

## 1. Executive verdict

The product is trying to solve a real coordination problem in the informal e-waste chain:

> Help households hand over e-waste safely, help Kabadiwalas collect and aggregate it profitably, and help verified recyclers procure it with evidence of what happened at every important step.

The codebase is more ambitious than a simple “sell scrap and book pickup” app. It contains three role experiences, a large API surface, a local-first Android data layer, an inventory ledger, bulk-lot and procurement concepts, cooperative pooling, signed handover QR codes, material passports, settlement variance rules, and audit/event models.

However, the product currently has two realities:

1. **A newer live supply-chain surface** for households, Kabadiwalas, and recyclers.
2. **An older demo/legacy surface** containing the richer lot, quote, handover, payment, chat, rewards, and analytics journeys.

The navigation code intentionally keeps many older flows demo-only. This is the most important product-readiness issue: the repository contains many capabilities, but a user in the live role experience cannot necessarily reach or complete all of them.

### Readiness summary

| Area | Current assessment | Meaning |
|---|---|---|
| Product thesis | Strong and differentiated | The collector-first network thesis is clear enough for a pilot. |
| Android engineering foundation | Amber | Compose, Room, Retrofit, WorkManager, encrypted session storage, and role-aware navigation are present. |
| Backend domain foundation | Amber | The schema and route/service surface model a serious supply-chain product. |
| End-to-end live proof | Amber/Red | Important flows are online-first, some actions are partial, and live Mongo/storage/provider testing is not proven. |
| Production operations | Red | No production domain, signed release path, real notification/payment operations, admin console, monitoring, backup/restore proof, or field-operations tooling. |
| Compliance and trust claims | Red | Recycler authorization, disposal evidence, data destruction, retention, and EPR workflows need operational and legal validation. |
| Open-market launch | Red | The app needs a narrow pilot, measurable supply/demand, and verified operator processes before scaling. |

The repository’s own backend verification document reports approximately **79% source implementation completeness** but only **36% verified working completeness**. That is a useful warning, not a formal KPI: the code has breadth, while live-system evidence is still limited.

## 2. What the app is about

### Primary users

| User | Job to be done |
|---|---|
| Household | List unwanted e-waste, understand a price range, request a nearby collector, and receive a transparent final settlement. |
| Kabadiwala / collector | Find nearby supply, schedule pickups, weigh and classify material, maintain inventory, aggregate stock, and choose a financially sensible downstream route. |
| Recycler | Publish requirements, discover bulk material, make offers, receive material through a verified handover, inspect it, and record settlement/disposal evidence. |
| Operator/admin | Verify recyclers, manage exceptions, monitor trust/compliance evidence, resolve disputes, and operate the network. This role is modeled in parts of the backend but has no complete user-facing admin application. |

### Intended supply-chain flow

```text
Household listing
        ↓
Nearby Kabadiwala accepts and schedules pickup
        ↓
Collection, weight/category/grade capture, safety checks
        ↓
Kabadiwala inventory ledger
        ↓
Bulk lot or consent-based cooperative pool
        ↓
Verified Recycler offer and signed handover
        ↓
Recycler receipt, QC, settlement, disposal evidence
        ↓
Material passport, payment/audit history, dispute path
```

The intended value is not only a higher scrap price. It is a better **net route**: rate minus transport/logistics/time, adjusted by route eligibility and evidence. That is a more defensible product direction than competing only on a commodity price card.

## 3. What is currently implemented

### Live role experiences

#### Household

The current live household surface can:

- Create a material listing with category, approximate kilograms, condition, area, notes, and a photo reference.
- Capture data-bearing-device and data-destruction intent.
- Show hazardous-material warnings and require a safety acknowledgement for batteries, CRT, LCD panels, and PCBs.
- Display a clearly labeled simulated price range rather than pretending it is a guaranteed offer.
- View listings and pickup state.
- Request a nearby Kabadiwala pickup.
- Cancel or reschedule in the available flow.
- Review the final settlement and accept it or raise an issue.

The UI itself explicitly says that the photo is attached “for this prototype listing” and calls the valuation a “Simulated AI estimate”. This honesty is good product practice, but it also confirms that the flow is not yet production-grade.

#### Kabadiwala

The live Kabadiwala surface can:

- View household listings and pickup requests.
- Accept or reject work.
- Set availability, schedule a pickup, start a trip, mark arrival, record weight, and complete a pickup.
- Report a no-show and use reassignment-related backend states.
- View inventory balances and inventory movements.
- Create and manage bulk lots.
- View recycler requirements and offers.
- Accept, reject, or counter an offer.
- See route-advantage estimates.
- Create/join/leave/lock cooperative pools.
- Use safety-routing, demand-intelligence, growth-passport, material-passport, and formal-handover surfaces where the current UI exposes them.

#### Recycler

The live Recycler surface can:

- Enter a verification flow; new recyclers start pending and must become verified before protected recycler actions.
- Browse bulk lots and submit offers.
- Publish or update procurement requirements.
- View orders and pooled consignments.
- Scan a formal handover QR or use a manual reference fallback.
- Confirm actual weight and material match.
- Produce a review-required result when settlement variance rules are triggered.

The backend also models QC, disposal evidence, formal payments, reversals, and material-passport events. The Android experience does not yet expose all of that as a complete, polished, role-specific journey.

### Backend foundation

The backend is an Express/TypeScript/Prisma service backed by MongoDB. It mounts both legacy and supply-chain routes and includes:

- Role and ownership authorization for Household, Collector/Kabadiwala, Recycler, and Admin capabilities.
- Revalidation of current account/role linkage rather than trusting only a stale token claim.
- Idempotency records and account-scoped synchronization.
- Household listings, pickup state machines, reassignment states, inventory, movements, bulk lots, offers, requirements, pools, and handovers.
- Append-only inventory movements with available/reserved/sold invariants.
- Signed handover QR payloads using an HMAC verification path, expiry checks, nonce/reference checks, and timing-safe comparison.
- Two-party handover confirmation and settlement variance handling.
- Audit events, notifications, activity events, disputes, anomaly flags, and passport events in the data model.
- Environment guards that reject unsafe production settings such as wildcard CORS, placeholder secrets, development OTP, and non-HTTPS production origins.

The Prisma schema shows a serious domain model. The risk is not lack of modeled entities; it is proving that the model is correctly operated under real concurrency, failure, recovery, and field conditions.

### Android foundation

The Android app uses Kotlin, Jetpack Compose/Material 3, Room, Retrofit/OkHttp, WorkManager, Android security crypto, camera/gallery capture, location, and QR scanning. It has:

- Encrypted session/token storage.
- Account-scoped local persistence and cleanup.
- Cached listings, prices, recyclers, pickups, inventory, payments, handovers, and formalisation evidence.
- A WorkManager sync queue with retry/error state and idempotency keys.
- Delta reconciliation that attempts to preserve unsynced local work.
- Offline-aware formal receipt confirmation for selected operations.
- Role-aware route guards.
- Localization resources, dark theme support, scalable Compose layouts, safety copy, and text-to-speech hooks.

This is a good foundation for a field app. It should not yet be described as “fully offline”: most new marketplace mutations are direct online calls, and shared pooling cannot safely behave like an offline replicated marketplace without a stronger conflict protocol.

## 4. What is left, missing, or only partial

### P0 — Required before a real pilot

| Gap | Evidence/impact | Recommended completion |
|---|---|---|
| Production environment | The Android debug default points to a testing IP over HTTP; release defaults to an invalid placeholder API unless explicitly configured. | Provision a real HTTPS API, staging environment, DNS, certificates, environment separation, and release configuration. |
| Production deployment | The backend can run with local storage and memory rate limiting in development; production guards exist, but the operational stack is not delivered here. | Use managed MongoDB, private object storage, secret management, distributed rate limits, logs, metrics, alerts, backups, restore drills, and a runbook. |
| Authentication and notifications | Development OTP and provider integrations are optional/configuration-dependent. | Integrate production OTP/email, FCM or equivalent push, notification templates, delivery tracking, and account recovery. |
| Household photo handling | The current listing sends a local `photoReference`; the UI labels it prototype-only. | Upload through a protected endpoint, validate type/size/content, issue scoped URLs, encrypt or restrict storage, define retention, and verify deletion. |
| Recycler verification operations | The platform has a verification state and authorization audit model, but no complete admin console or independent verification evidence workflow. | Build operator review, document expiry/renewal, approval history, suspension, evidence access controls, and escalation. Never market platform verification as government authorization without proof. |
| Payments and reconciliation | The code records internal payments and formal reversal evidence; it does not call a bank/UPI/cash provider or reconcile payouts. | Add a real payment/payout strategy, ledger reconciliation, webhook verification, failure handling, refunds/reversals, and collector-facing receipts. |
| Live database proof | Historical verification notes state that live MongoDB integration, concurrency, and deployment were not proven. | Create staging integration tests with real MongoDB, transaction/concurrency tests, replay/IDOR tests, and a production-like smoke test. |
| Operator/support workflow | There is no complete admin website in the repository. | Build a small operator console before expanding cities: verification, pickup exceptions, settlement review, disputes, fraud flags, and support notes. |
| Legal/compliance operating model | The app deals with e-waste, batteries, data-bearing devices, recycler claims, payments, location, and photos. | Obtain specialist legal/compliance review, verify registered downstream partners, define consent/retention/deletion, and document disposal/data-destruction evidence. |

### P1 — Required for a credible user experience

- **Unify live and demo navigation.** `AppNavHost` explicitly isolates legacy lot/quote/handover/payment routes from the live collector role. Either finish those routes for live use or remove them from the product narrative and label them as an internal demo.
- **Add dedicated detail destinations.** Household listing details, pickup details, passport, anomaly/dispute, settlement breakdown, and collector confirmation should not depend on dense inline cards alone.
- **Complete pickup operations.** The current reschedule options are three near-term client-generated slots. A production flow needs real collector capacity, slot expiry, ETA, map/routing, arrival notifications, no-show policy, and a robust “find another Kabadiwala” action.
- **Make offline behavior explicit.** Queue all safe, idempotent mutations that the field role needs, not just pickup requests and selected handover confirmations. Show pending, failed, retry, and conflict states clearly.
- **Complete recycler receipt/QC.** The scanner and confirmation exist, but recycler QC, rejected/accepted quantities, disposal evidence, settlement breakdown, and the collector’s review loop need complete UI and notification paths.
- **Finish payout UX.** Show quoted weight/rate/value, actual accepted weight/rate/value, logistics/fees, reason codes, payment status, and expected payout date in one receipt.
- **Improve price credibility.** A range should show source, freshness, material grade assumptions, deductions, and confidence. The current deterministic/seeded range is suitable for a prototype only.
- **Handle household reassignment.** The backend models reassignment states, but the live household experience needs a clear action when a pickup becomes `REASSIGNMENT_REQUIRED`.

### P2 — Required for scale and adoption

- Complete Hindi and other target-language copy in the live supply-chain screens; resource strings alone are not enough without native review and field testing.
- Validate TalkBack, large fonts, touch targets, camera permissions, location denial, QR scanning, process death, rotation, low-end devices, weak networks, battery usage, and long-running sync.
- Replace hardcoded English labels and demo fixture copy in production paths.
- Add analytics with privacy boundaries: funnel completion, pickup reliability, material mismatch, payout latency, sync failure, recycler acceptance, and repeat usage.
- Establish deletion, retention, legal hold, backup, export, and anonymized reporting policies.
- Replace broad/generic API bodies with versioned, granular OpenAPI contracts and generated contract tests.
- Build a governed AI dataset and evaluation process before using “AI” as a market claim.

## 5. What is special about the current product

These are the strongest differentiators visible in the source. They are **product hypotheses and implementation foundations**, not yet proven market advantages.

### 5.1 Collector-first net-route economics

The app can compare more than a nominal recycler rate. Its route-advantage concept combines rate, pickup/logistics cost, quantity, eligibility, confidence, and an explanation of “why this match”. This is valuable because Kabadiwala profitability depends on the net route, not only the highest displayed per-kilogram number.

**Why it can matter:** it can help a small collector decide whether to sell now, hold stock, aggregate locally, or join a downstream route.

**Current limitation:** the estimate is not yet a provider-backed logistics quote, historical reliability score, or guaranteed payout.

### 5.2 Consent-based cooperative pooling

Multiple Kabadiwalas can reserve stock toward a Recycler’s minimum demand threshold, with pool contributions and proportional settlement modeled in the backend.

**Why it can matter:** small collectors may access demand that is uneconomical individually, while keeping contributions attributable.

**Current limitation:** pooling requires connectivity and real coordination. Savings, fulfillment reliability, and fairness are not proven with field data.

### 5.3 Material passport and signed evidence chain

The model can connect source listing, pickup, inventory movement, bulk lot/pool, signed QR handover, two-party confirmation, QC, settlement, and disposal evidence.

**Why it can matter:** it turns an opaque scrap transaction into a traceable chain that can eventually support recycler trust, household receipts, EPR reporting, and dispute resolution.

**Current limitation:** the passport is a platform projection, not an independent government credential or disposal certificate. Downstream evidence still depends on real operators and real documents.

### 5.4 Fairness guard for settlement changes

The backend requires review when material changes, accepted weight is lower, weight changes by more than 5%, or rate changes by more than 10%. It records a reason code, evidence reference, audit event, and anomaly flag.

**Why it can matter:** the platform can make renegotiation visible instead of silently replacing the original expectation.

**Current limitation:** a rule is only useful if the user sees it, receives it promptly, can respond, and has a real dispute SLA.

### 5.5 Low-connectivity field orientation

Room caching, encrypted state, WorkManager retries, account-scoped queues, delta cursors, and preservation of unsynced work are unusually relevant for informal collection work.

**Why it can matter:** connectivity, device quality, and intermittent access are likely operational realities for the target user.

**Current limitation:** the new marketplace is still mostly online-first. Offline support must be expanded carefully without creating duplicate pickups, double reservations, or stale settlement decisions.

### 5.6 Safety and data-bearing-device awareness

The household flow calls out hazardous categories and asks about data-bearing devices/data destruction. This is more responsible than treating all scrap as interchangeable.

**Current limitation:** warnings are not a substitute for licensed handling, training, documented data wiping, or downstream compliance.

## 6. Production-readiness judgment

### Is it close to production?

**It is close to a controlled pilot, not close to an open-market production launch.**

The correct next milestone is not “finish every screen.” It is a verifiable, narrow operating loop:

1. One city or defined service area.
2. A limited list of material categories.
3. A small, manually verified Kabadiwala cohort.
4. A small, manually verified Recycler cohort.
5. Real household listings and pickup slots.
6. Real protected photos and evidence.
7. Real payout/reconciliation, even if initially assisted by an operator.
8. Operator support for every exception.
9. Instrumentation for completion, margin, reliability, mismatches, and disputes.

The app should not be marketed as a nationwide authorized recycling network, guaranteed price engine, fully offline marketplace, government certificate system, or production AI valuation product based on the current source.

## 7. Recommended improvement plan

### Phase 0 — Simplify the product story

- Choose the primary launch promise: **“Know the net route. Pool only with consent. Settle with proof.”**
- Define the pilot geography, materials, operating hours, service radius, and minimum downstream requirements.
- Mark every old route as `Live`, `Pilot`, `Demo`, or `Not available` in product documentation and UI.
- Freeze the minimum success funnel: listing → accepted pickup → weighed collection → downstream handover → payout receipt.

### Phase 1 — Make the narrow flow reliable

- Deploy HTTPS staging and production infrastructure.
- Complete recycler/operator verification and evidence renewal.
- Implement protected photo upload and storage.
- Integrate OTP, push notifications, support, and a basic operator console.
- Add real pickup capacity/ETA and reassignment.
- Add staging tests against MongoDB and production-like failure tests.
- Sign the release artifact, configure update distribution, and add crash/error monitoring.

### Phase 2 — Make trust visible

- Add dedicated listing, pickup, passport, settlement, and dispute screens.
- Provide a single downloadable/shareable transaction receipt.
- Show price source/freshness, weight evidence, rate changes, fees, and settlement reason codes.
- Add verified recycler document status and a clear “what is verified” explanation.
- Implement payout ledger/reconciliation and a dispute SLA.

### Phase 3 — Prove the network advantage

- Use actual logistics quotes or measured route costs.
- Track collector pickup reliability and recycler acceptance history.
- Run opt-in pooling pilots with reservation expiry, minimum thresholds, contribution visibility, and settlement reconciliation.
- Replace seeded demand and price examples with live, source-labeled data.
- Measure whether route advantage, pooling, and evidence increase collector margin, recycler fill rate, household trust, and repeat usage.

### Phase 4 — Scale responsibly

- Add multi-city operations and partner onboarding.
- Add B2B recurring pickups, invoices, weight slips, procurement contracts, and EPR reporting.
- Add partner APIs for verified recyclers and logistics providers.
- Introduce carefully evaluated AI assistance only where it reduces work without inventing certainty.

## 8. Future features that can improve UX and market position

### Highest-value user experience features

1. **One-tap role home screens:** “Sell e-waste”, “My pickups”, “Record weight”, “Build a lot”, “Find demand”, and “Confirm receipt”.
2. **Voice and low-literacy mode:** spoken prompts, TTS for price/safety, IVR/WhatsApp fallback, vernacular onboarding, and assisted phone support.
3. **Live pickup promise:** real capacity, slot confirmation, collector ETA, map view, arrival notification, no-show handling, and one-tap reassignment.
4. **Transparent price receipt:** source, freshness, grade assumptions, quoted/final weight, accepted/rejected weight, rate changes, fees, logistics, and net payout.
5. **Trust passport:** QR/shareable receipt that shows only safe, consented evidence; no unnecessary collector contact details.
6. **Verified reviews:** reviews only after a completed, verified transaction, with separate scores for pickup reliability, weight transparency, and settlement fairness.
7. **Offline mutation queue:** a visible outbox with pending, retrying, failed, conflict, and server-confirmed states.
8. **Low-end performance:** image compression, progressive sync, small payloads, robust process-death recovery, and measured cold-start/performance budgets.

### Network and economics features

- Dynamic route optimizer using real distance, vehicle capacity, time, material grade, and downstream acceptance probability.
- Collector reliability score based on completed pickup evidence, not popularity alone.
- Recycler demand contracts with expiry, minimum lot, grade, location, and settlement terms.
- Pool reservation expiry, partial fulfillment, contribution caps, and route/capacity-aware matching.
- Collector tier benefits tied to safe evidence and reliable completion rather than artificial engagement.
- Household repeat pickups, recurring business pickups, and neighborhood collection days.
- B2B invoices, GST-ready documents where applicable, weight slips, pickup manifests, and EPR/export reports.

### Trust, safety, and circularity features

- Certified data wipe workflow for data-bearing devices, with a user-visible certificate only when the real operation supports it.
- Refurbish-versus-recycle decisioning to preserve higher-value devices before material recovery.
- Licensed downstream partner documents, expiry alerts, and suspension workflows.
- Disposal evidence with document metadata, operator identity, timestamps, and tamper-evident references.
- Safety checklist by category, PPE/training acknowledgements, incident reporting, and escalation.
- Privacy controls for precise location, photos, phone visibility, retention, deletion, and export.
- Impact reporting with methodology: kilograms collected, accepted downstream, refurbished, recycled, rejected, and evidence coverage.

### Responsible AI features

- On-device or server-assisted category suggestion from a photo, always editable by the user.
- Confidence and “why” explanation instead of false precision.
- Localized description drafting for a listing.
- Quality/grade suggestion only as an assistive prompt.
- Price-band guidance using labeled, fresh inputs and uncertainty ranges.
- Anomaly assistance that routes a case to human review rather than automatically penalizing a user.

The current code already has isolated AI boundaries and provenance fields, but material suggestion/description assistance can fall back to templates, while valuation, demand, matching, and anomaly logic are largely deterministic or seeded. That is acceptable for a prototype; it must be stated clearly in the product.

## 9. Competitor and alternative landscape

This is a directional positioning scan, not a complete competitor benchmark. The descriptions below summarize public positioning and competitor-owned claims as visible on 15 September 2026. Availability, prices, service areas, and feature depth can vary by city and product tier.

| Alternative | Publicly visible strength | Where it overlaps | Kabadiwala Connect opportunity |
|---|---|---|---|
| Local Kabadiwala, phone calls, or WhatsApp | High neighborhood trust, immediate availability, flexible cash/UPI negotiation | Household collection and scrap sale | Provide a lightweight field tool that preserves local relationships while adding scheduling, evidence, inventory, and downstream options. |
| [ScrapUncle](https://scrapuncle.com/) | Doorstep pickup for many recyclables, digital weighing, trained/verified collection positioning, household and business services | Household pickup, price/weight transparency | Focus on the multi-party collector-to-recycler network, net route economics, consent-based pooling, and evidence chain. |
| [The Kabadiwala](https://www.thekabadiwala.com/) and its [user app](https://play.google.com/store/apps/details?id=com.thekabadiwala.userapp) | Door-to-door pickup, broad recyclable coverage, digital bills/trackability/rewards positioning, business/EPR services | Consumer pickup and formal waste services | Avoid competing only on pickup. Differentiate around collector economics, cooperative supply aggregation, and auditable handover. |
| [Cashify recycling](https://www.cashify.in/recycle-old-mobile-phone) and [e-waste policy](https://www.cashify.in/e-waste-policy) | Strong device/phone specialization, data-erasure and recycling positioning, consumer convenience | Data-bearing electronics and recycling trust | Own the broader Kabadiwala supply network and mixed e-waste aggregation; add certified data destruction only with real downstream capability. |
| [Recykal.Market](https://www.recykal.market/) | B2B circular marketplace positioning, verified buyers/sellers, live pricing, pickup, tracking, invoices, and certificates | Recycler procurement, traceability, material marketplace | Be simpler and field-first for informal collectors, with cooperative pooling and low-connectivity workflows. Integrate with enterprise platforms later where useful. |
| [Recykal DRS](https://www.recykal.com/drs-platform) | Enterprise reverse-logistics/deposit-return control, unit-level tracking, refunds, and compliance reporting | Traceability, returns, compliance | Position as the upstream collection and aggregation layer rather than an enterprise DRS replacement. |
| [Kabadiwalla Connect](https://app.kabadiwallaconnect.in/) | Neighborhood scrap-shop discovery, sale records, points/rewards positioning | Name-adjacent local scrap discovery | Treat the name collision as a brand/search risk. Own a clear “formal route and evidence” story and verify trademark/SEO implications. |

### Competitive conclusion

The crowded part of the market is “book scrap pickup and get a price”. Kabadiwala Connect should not lead with that alone. Its strongest defensible direction is:

> A collector-first operating network that helps informal collectors find the best net downstream route, combine supply only with consent, and settle every important handover with evidence.

That positioning is stronger when backed by measured outcomes: higher collector net margin, fewer failed pickups, lower settlement disputes, higher recycler fill rate, faster payout, and better evidence coverage.

## 10. Compliance, privacy, and trust considerations

India’s [E-Waste Management Rules, 2022](https://moef.gov.in/uploads/2022/11/E-Waste-Management-Rules-2022.pdf) and [CPCB e-waste FAQ](https://cpcb.nic.in/uploads/Projects/E-Waste/FAQ_ewaste_23012024.pdf) make formal roles, EPR, registered recyclers, and safe handling important parts of the operating context. Battery waste and other applicable rules must also be checked for the actual material categories and partner activities.

Before launch, the team should validate:

- Which entity is the collector, aggregator, recycler, transporter, or platform in each operating model.
- How registered recycler evidence is collected and renewed.
- How hazardous material is identified, segregated, transported, and handed over.
- What “data destruction” means operationally and what evidence can legally be issued.
- What personal data is collected, why exact location/photo/contact data is needed, how long it is retained, and how it is deleted.
- How payment records, disputes, audit events, and legal holds are retained.
- What impact and EPR reports can be issued without overstating what the platform itself verified.

This document is a product/engineering assessment, not legal advice.

## 11. Source evidence reviewed

### Repository evidence

- [Project README](../README.md) — product intent, beta status, deployment limitations, and known non-production defaults.
- [Android build configuration](../android_app/app/build.gradle.kts) — SDK/version, debug/release API defaults, dependencies, and build signing configuration.
- [Navigation and route guards](../android_app/app/src/main/java/com/irinteractivestudios/kabadiwalaconnect/ui/navigation/AppNavHost.kt) — live versus demo route split and role boundaries.
- [API service](../android_app/app/src/main/java/com/irinteractivestudios/kabadiwalaconnect/data/remote/ApiService.kt) and [DTOs](../android_app/app/src/main/java/com/irinteractivestudios/kabadiwalaconnect/data/remote/ApiDtos.kt) — typed Android contract surface.
- [Supply-chain ViewModel](../android_app/app/src/main/java/com/irinteractivestudios/kabadiwalaconnect/ui/screens/supplychain/SupplyChainViewModel.kt) — refresh, mutation, fallback, and queue behavior.
- [Supply-chain screens](../android_app/app/src/main/java/com/irinteractivestudios/kabadiwalaconnect/ui/screens/supplychain/SupplyChainScreens.kt) — current user-visible live flows and prototype disclaimers.
- [Sync worker](../android_app/app/src/main/java/com/irinteractivestudios/kabadiwalaconnect/data/sync/Sync.kt) — WorkManager queue, idempotency, retry, and delta reconciliation.
- [App container](../android_app/app/src/main/java/com/irinteractivestudios/kabadiwalaconnect/di/AppContainer.kt) — session, Room, Retrofit, cache, and account cleanup setup.
- [Backend app mounting](../backend/src/app.ts) and [server startup](../backend/src/server.ts) — middleware, route modules, storage, and runtime setup.
- [Supply-chain routes](../backend/src/routes/supplyChainRoutes.ts) and [formalisation routes](../backend/src/routes/formalisationRoutes.ts) — pickup, inventory, bulk, pool, passport, QR, QC, settlement, and admin-capability endpoints.
- [Traceability service](../backend/src/services/traceability.ts) — signed handover QR construction and verification.
- [Inventory ledger](../backend/src/services/inventoryLedger.ts) — stock invariants and append-only movements.
- [Settlement rules](../backend/src/services/settlementRules.ts) — mismatch, weight, rate, and partial-acceptance review rules.
- [Prisma schema](../backend/prisma/schema.prisma) — domain entities, role model, audit/evidence fields, and state enums.
- [Backend final verification](../docs/BACKEND_FINAL_VERIFICATION.md) — existing completeness and verification assessment.
- [Known limitations](../docs/KNOWN_LIMITATIONS.md) — explicit prototype, data, compliance, offline, AI, and testing limits.
- [Android API coverage matrix](../android_app/ANDROID_API_COVERAGE_MATRIX.md) — definitions of complete, partial, compatibility-only, and implementation-required coverage.
- [Android completion report](../android_app/ANDROID_COMPLETION_REPORT.md) — Android build/acceptance status and follow-on gaps.
- [Privacy and retention notes](../docs/PRIVACY_RETENTION.md), [AI dataset card](../docs/AI_DATASET_CARD.md), and [unit economics](../docs/UNIT_ECONOMICS.md) — important caveats behind privacy, AI, and economics claims.

### Validation performed for this assessment

- Backend TypeScript type-check: `npx tsc -p tsconfig.json --noEmit` — passed.
- Backend test suite: `npx vitest run` — **25 test files and 68 tests passed**.
- Backend lint: `npm run lint` — passed in the current working tree.
- Android unit tests: `./gradlew testDebugUnitTest --no-daemon` — passed after a clean rebuild.
- Android lint/package checks: current attempts reached Kotlin compilation but were interrupted by Windows Kotlin incremental-cache/daemon errors (`kspCaches` missing and already-registered incremental cache files). The output showed warnings rather than a source compile error; the checks should be rerun in a clean Android Studio/CI environment before release.

## 12. Definition of “ready for pilot”

Do not call the product pilot-ready until all of the following are demonstrated in a staging environment and on representative low-end Android devices:

- A household can create a listing with a protected uploaded photo.
- A real Kabadiwala can accept, schedule, collect, weigh, and complete it.
- The app recovers from offline periods without duplicates or lost evidence.
- A verified Recycler can accept a real bulk/pool handover with signed QR and two-party confirmation.
- The final settlement and any variance are visible to both sides.
- A real payout or operator-assisted payout is reconciled to the ledger.
- An operator can verify, suspend, support, and audit participants.
- A failed pickup, rejected material, disputed weight, expired document, expired QR, duplicate retry, and partial sync have tested outcomes.
- Privacy, retention, deletion, incident response, and partner compliance have written procedures.
- The team has baseline metrics and a go/no-go review after the first controlled cohort.

## Final recommendation

Keep the product thesis. Reduce the launch surface. Make the live Household → Kabadiwala → Recycler journey excellent and provable before exposing the full breadth of the legacy/demo surface.

The most promising market wedge is not another scrap-price app. It is a trusted, low-connectivity supply-chain workbench for the people who currently make the informal collection network function—paired with verified downstream demand and evidence-backed settlement.
