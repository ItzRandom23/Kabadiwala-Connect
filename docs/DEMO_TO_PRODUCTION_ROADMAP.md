# Kabadiwala Connect — Demo-to-Production Roadmap

**Status:** Living implementation checklist
**Created:** 2026-09-18
**Audience:** AI coding agents and engineers working in this repository
**Goal:** Convert every meaningful Demo Mode capability into a safe, authenticated, observable, production workflow.

## 1. How to use this document

This is the implementation source of truth for converting Demo Mode to production. Read it before changing Demo Mode, the live Android flows, or the backend.

Use these status labels:

- `DEMO_ONLY` — currently local fixture/UI behavior; no production implementation is proven.
- `PARTIAL` — production primitives exist, but the end-to-end role workflow, recovery, evidence, or operations is incomplete.
- `LIVE_UNVERIFIED` — a live path exists but requires integration, concurrency, security, or field verification before being called production-ready.
- `READY` — implemented, tested, observable, authorized, recoverable, and accepted against the criteria in this document.
- `BLOCKED` — requires an external decision, credential, provider, legal review, or operational dependency.

Every implementation item must have:

1. A backend contract and authorization rule.
2. Account-scoped local persistence and sync behavior where the action can occur offline.
3. Loading, empty, error, retry, conflict, and permission-denied UI states.
4. Idempotency and duplicate-action handling.
5. Audit/event and notification behavior where the action changes a transaction.
6. Unit, route/integration, and Android workflow tests appropriate to the risk.
7. Updated documentation and an evidence link before its status becomes `READY`.

Do not mark an item complete because a screen renders. A production workflow is complete only when the state is owned by the authenticated account, validated by the backend, recoverable after failure, and evidenced by tests or a staging run.

## 2. Current architecture and conversion rule

The repository currently contains two overlapping product surfaces:

1. **New live supply-chain surface:** Household listing/pickup → Kabadiwala inventory/bulk lot → verified Recycler offer/handover.
2. **Legacy Demo surface:** Lot → Recycler directory → quote → handover → payment → timeline, plus rewards, chat, and dispute analytics.

The production target is **one coherent canonical workflow**:

```text
Authenticated Household listing
  → collector discovery and pickup booking
  → collector acceptance, arrival, weighing and payment
  → inventory movement and material passport
  → bulk lot or cooperative pool
  → verified Recycler requirement and offer
  → signed two-party handover
  → Recycler receipt, QC, settlement and disposal evidence
  → notifications, audit trail, rating and dispute resolution
```

Before adding new production code, decide whether a legacy capability should:

- be integrated into the canonical supply-chain model;
- replace an existing live implementation; or
- be removed from the production narrative while remaining available only in debug Demo Mode.

Do not maintain two independent production state machines for the same transaction.

### Current implementation evidence — 2026-09-18

The following production-boundary work is implemented in the current working tree, but the affected roadmap items remain `PARTIAL` until staging and operational acceptance are complete:

- Demo activation is debug-only: release navigation rejects demo mode, release builds require an explicit HTTPS API endpoint, and the release APK was checked for demo entry/routes/fixture identifiers.
- Android authentication now persists and refreshes the server-issued account role without rewriting it into a local presentation role; role selection remains only a signup request and protected navigation still follows the returned account.
- Household listing photos use an authenticated multipart upload, server-side MIME/dimension validation, private storage keys, an audit event, protected household/assigned-collector reads, and a stable object key for retry convergence.
- Listing creation no longer sends a local filesystem path or fabricated price range. Failed photo uploads are stored in an account-scoped Room queue and can be retried after a process restart while the app-private source file remains available.
- The backend now supports `OTP_PROVIDER=twofactor`; 2Factor credentials are environment-only, production configuration rejects a missing key, and pending OTPs are stored as database-backed HMAC challenges rather than plaintext codes. Actual provider delivery still requires account/DLT configuration and a staging SMS test.
- Deployment foundation is present: the backend has an unprivileged production Docker image with a health check, and CI now runs backend tests/build, Android unit tests, an HTTPS-configured release build, and the container build.
- Release safety is explicit: real release builds reject placeholder/local API hosts and require signing credentials by default; CI opts out only for unsigned placeholder packaging validation. `/api/v1/ready` now checks database, storage initialization, production OTP mode, and shared rate limiting.
- Notification emissions now accept deterministic business-event keys, so retried quote, handover, payment and pickup operations do not duplicate the persisted in-app inbox event; the original best-effort behavior remains for unkeyed events.
- Authenticated privacy endpoints now support account export and explicit self-service deletion. Deletion revokes refresh sessions, clears direct contact/authentication data and notifications, preserves transactional/audit records, and is covered by focused tests. Retention periods, legal holds, storage-object cleanup, and staging acceptance remain operational work.
- Notification delivery now has an authenticated, account-scoped device-token registry with token reassignment on re-login, unregister, safe response shaping, and deletion cleanup. It also has durable SMS and push outboxes with bounded retries, per-device push targets, stale-token cleanup, safe provider error handling, a server-only 2Factor transactional adapter, an FCM HTTP v1 adapter, Android token registration, Android 13+ notification consent, and account-scoped SMS/push preference enforcement. This remains `PARTIAL`: Firebase project/service-account provisioning, DLR webhook reconciliation, approved DLT templates, delivery monitoring, and staging SMS/push acceptance remain external or incomplete.
- Push token lifecycle now invalidates queued per-device delivery targets before token reassignment or unregister. This closes the account-switch/logout retry leak where an older account's queued notification could otherwise be sent to the same physical device; the boundary is covered by notification-device regression tests.
- Collector listing discovery is now assignment-scoped: `/kabadiwala/listings` no longer broadcasts every posted household listing, and its response exposes only a `photoAttached` boolean. Private photo bytes remain available only through the assigned-pickup authorization check on `/kabadiwala/listings/:listingId/photo`; this boundary is covered by a route regression test.
- Pickup scheduling now has server-owned ISO slot validation (hour/half-hour alignment, 90-minute minimum lead time and 14-day horizon) plus a per-collector, India-local-day capacity ledger. Reservations are conditionally incremented and released on cancellation, reassignment and cross-day rescheduling; capacity conflicts return a deterministic error. ETA/routing, holidays, operating hours and staging acceptance remain incomplete.
- CI now contains a manual-only protected `android-release` job that restores a base64-keystore from GitHub environment secrets, requires a real HTTPS API URL and production signing, and uploads only the resulting AAB. Ordinary CI remains unsigned placeholder validation.
- Verification: `android_app` `testDebugUnitTest` passes (28 tests); an HTTPS-configured, R8/minified `assembleRelease` passes; `backend` `npm run build` and lint pass; focused photo, OTP provider, notification, privacy, device-registration, preference, SMS-delivery, FCM push and pickup-scheduling tests pass; the full backend suite passes (33 files, 92 tests).

Still outstanding for the photo item: EXIF/privacy and malware policy, retention/deletion jobs, staging integration coverage, and production storage/operations configuration.

## 3. Demo inventory and production conversion checklist

### 3.1 Demo shell, role selection and demo data

**Sources:** `android_app/app/src/main/java/com/irinteractivestudios/kabadiwalaconnect/ui/screens/auth/OnboardingScreen.kt`, `MainActivity.kt`, `ui/demo/`, `ui/navigation/AppNavHost.kt`.

| Status | Demo capability | Production work remaining | Acceptance criteria |
|---|---|---|---|
| `DEMO_ONLY` | Debug-only “Preview the Pune demo” entry | Keep Demo Mode behind debug/testing builds. Ensure release builds cannot expose it and that demo fixtures cannot be loaded by production repositories. | Release APK/AAB has no demo entry, demo routes, fixture data, or demo accounts. Debug demo remains usable. |
| `DEMO_ONLY` | Choose Household, Kabadiwala, or Recycler role | Replace presentation role selection with authenticated account registration, role approval, and server-owned role boundaries. Do not allow a user to switch role locally. | A role comes only from the server account; every protected route and mutation rejects the wrong role. |
| `DEMO_ONLY` | Local profiles for Aarohi Sharma, Pulkit Kabadiwala, and GreenLoop Materials | Replace hard-coded profiles with account/profile APIs, editable profile data, verified contact information, and consented location/area. | Profile data survives process death, logout/login, and account switching without leaking between accounts. |
| `DEMO_ONLY` | Reset demo journey | Replace with controlled cancellation/restart operations. Never expose a production “reset all transaction state” action to users. | Only authorized operators can reverse/void production records; user cancellation follows documented state rules. |
| `DEMO_ONLY` | Exit demo mode | Use real logout/session revocation and account deletion/export flows for production. | Tokens, local account-scoped data, pending work, and cached private data are handled according to retention policy. |
| `DEMO_ONLY` | Local cross-role `DemoSessionStore` | Replace with server-backed records, notifications, and real-time/polling refresh. | Household, Kabadiwala, and Recycler see the same authoritative state from separate authenticated devices. |
| `DEMO_ONLY` | Hard-coded prices, ratings, distances, weights, statuses, and partner names | Remove synthetic values from production paths. Seed only controlled staging data and label it clearly. | Production UI never claims a fixture is a live rate, government authorization, completed pickup, payment, or customer rating. |
| `PARTIAL` | Language, appearance, accessibility-sized controls | Finish localization of live supply-chain screens, native review of translations, TalkBack semantics, large-font layouts, contrast, and RTL/long-string testing. | Supported locales pass resource and screenshot/accessibility tests on low-end device profiles. |

### 3.2 Household production workflow

**Demo sources:** `DemoHouseholdHomeScreen`, `DemoHouseholdKabadiwalasScreen`, `DemoHouseholdDealScreen`, `DemoSessionStore`.
**Existing live surface:** `ui/screens/household/HouseholdScreens.kt`, `backend/src/routes/supplyChainRoutes.ts`.

| Status | Demo capability | Production work remaining | Acceptance criteria |
|---|---|---|---|
| `PARTIAL` | Create a copper-cable lot/listing with weight, condition, area and estimated value | Use the live Household listing model for all materials. Validate category, grade, weight bounds, hazardous/data-bearing flags, notes, location and consent server-side. | A listing is owned by the household, immutable after pickup acceptance where required, and visible only to authorized participants. |
| `PARTIAL` | Bundled demo photo / photo step | Implement authenticated image upload rather than a local `photoReference`. Validate MIME/type, size, content, malware, EXIF/privacy, storage access, retention and deletion. | Photo upload works on weak networks, retries safely, shows upload state, and never exposes an unscoped object URL. |
| `PARTIAL` | Nearby Kabadiwala directory | Provide real discovery using service area, availability, distance, pickup capability, reliability and authorization signals. Protect exact household address until the correct workflow stage. | Results are server-ranked, fresh, explainable, paginated, and cannot reveal private household data to unrelated collectors. |
| `PARTIAL` | Partner rating, specialty and distance cards | Back every field with verified records. Separate platform verification, business registration, user rating, and government authorization claims. | Every trust claim shows its source/freshness and can be audited or withdrawn. |
| `PARTIAL` | Choose a connected Kabadiwala | Replace local partner selection with a server booking/request state machine and conflict handling. | Only one active collector assignment exists; reassignment and cancellation are explicit and auditable. |
| `PARTIAL` | Schedule pickup for a time slot | Replace fixed “today, 5:30 PM” text with collector capacity, slot expiry, timezone, holidays, travel time, reschedule and no-show rules. | Two users cannot reserve the same capacity; expired slots and reschedules are handled deterministically. |
| `PARTIAL` | Private Household ↔ Kabadiwala chat | Use authenticated conversations linked to a listing/pickup. Add moderation, rate limits, delivery/read state, offline retry, abuse reporting and retention controls. | Messages are delivered only to participants, are idempotent, and show pending/failed states. |
| `PARTIAL` | Collector marks items collected and payment recorded | Record arrival, actual weight, material/grade confirmation, evidence, payment method/status, and both parties’ acknowledgements. | No transaction becomes complete from a client-only flag; backend verifies actor, state, quantity and payment evidence. |
| `PARTIAL` | One-time QR confirmation | Use a signed, short-lived, nonce-bound payload tied to the exact pickup/settlement. Prevent replay, wrong-role use, tampering and duplicate confirmation. | QR tests cover expiry, replay, wrong account, wrong transaction, duplicate confirmation and offline recovery. |
| `PARTIAL` | Final receipt showing actual weight, amount and locked receipt | Build a canonical settlement receipt with quoted vs actual weight/rate/value, deductions, fees, payment status, reason codes and timestamps. | Household and collector see the same server receipt; edits require a controlled correction/dispute path. |
| `PARTIAL` | Rate the Kabadiwala from 1–5 stars | Add one-review-per-completed-transaction rules, moderation, abuse prevention, edit policy and aggregation privacy thresholds. | Ratings cannot be fabricated by demo state, duplicated, or used to expose a user’s identity improperly. |
| `PARTIAL` | Household dispute/fairness path | Add dispute types, evidence upload, deadline, status, operator assignment, decision, appeal and settlement adjustment. | A dispute preserves the original immutable transaction and records every decision/event. |
| `PARTIAL` | Household notifications | Add push/in-app/SMS policy for assignment, slot confirmation, arrival, collection, payment, QR, settlement, reassignment and dispute events. | Notification delivery is observable, retryable, preference-aware and never contains excessive private data. |

### 3.3 Kabadiwala/Collector production workflow

**Demo sources:** `DemoKabadiwalaHomeScreen`, `DemoKabadiwalaInventoryScreen`, `DemoKabadiwalaPickupsScreen`, `DemoKabadiwalaLotsScreen`.
**Existing live surface:** `ui/screens/supplychain/`, `backend/src/routes/supplyChainRoutes.ts` and related services.

| Status | Demo capability | Production work remaining | Acceptance criteria |
|---|---|---|---|
| `PARTIAL` | Command center with daily collection value | Replace hard-coded ₹2,450/₹680 metrics with account-scoped aggregates. Define timezone, settlement inclusion, pending vs paid and correction rules. | Metrics reconcile with ledger/receipts and show freshness/source. |
| `PARTIAL` | Household connection status and rating | Read live assignments, pickup state and completed transaction ratings. | Collector sees only assigned/authorized household information and current state transitions. |
| `PARTIAL` | Formal route advantage | Back rate, distance, logistics, minimum lot, eligibility, demand and confidence with fresh source records. Never present an estimate as a guaranteed offer. | Route result explains inputs, age, assumptions, missing data and uncertainty; backend recomputes before acceptance. |
| `DEMO_ONLY` | Growth Passport with level, score, completed pickups and on-time rate | Define production metric formula, anti-gaming rules, privacy, dispute correction, explanation, and whether any external certification is claimed. | Score is reproducible from auditable events and explicitly not a government certification unless independently verified. |
| `PARTIAL` | Pooling opportunity and threshold math | Complete pool ownership, invitation/consent, reservations, contribution limits, lock, cancellation, expiry, underfill, oversell prevention, proportional settlement and notification policy. | Concurrent contributors cannot oversell stock; ownership and settlement remain attributable per contributor. |
| `PARTIAL` | Inventory totals: owned, reserved and available | Use atomic server ledger movements and explicit reservation/release/consumption records. Reconcile Room cache with server truth. | Invariants hold under concurrency, retries, cancellation and process death; no negative or double-reserved stock. |
| `PARTIAL` | Copper, PCB and PET inventory cards | Replace demo materials/values with real material categories, grades, units, source lot links, valuation freshness and ownership. | Every balance traces to source pickups or adjustments and has an audit trail. |
| `PARTIAL` | Pickup queue | Finish accept/reject, capacity, schedule, arrival, GPS/privacy, weighing, material confirmation, completion, no-show, reassignment and cancellation paths. | Collector can recover from denied GPS, weak network, duplicate taps, process death and a changed assignment. |
| `PARTIAL` | “Mark collected & paid” | Split collection confirmation from payment recording where necessary. Support cash/UPI/provider references, partial/failure/reversal states and receipts. | Payment status is never inferred from a UI button; ledger and payout records reconcile. |
| `PARTIAL` | QR shown to Household | Bind QR to the actual pickup, weight and settlement version. | Household confirmation cannot be performed against a stale or altered amount. |
| `PARTIAL` | Buyers and formal lots | Convert demo lot creation into a live bulk-lot workflow with material passport, source inventory reservations, minimum quantity, grade and destination constraints. | Lot creation is atomic with reservations, can be safely retried, and exposes only authorized source/area data. |
| `PARTIAL` | Select Recycler and accept/hand over material | Unify with the canonical Recycler offer model. Define offer expiry, counter-offer, rejection, cancellation, handover readiness and settlement ownership. | Both parties see a consistent state machine; no client can skip required prerequisites. |
| `PARTIAL` | Collector ↔ Recycler chat | Link conversation to offer/lot/handover, enforce participants, retain evidence, and support delivery/retry/moderation. | Chat cannot bypass formal offer/handover records or leak other lots. |
| `PARTIAL` | Collector notifications | Notify new listing, acceptance, schedule, arrival, offer, pool threshold, lock, QR readiness, receipt, variance and dispute events. | Notifications are role-scoped and traceable to the event that generated them. |

### 3.4 Recycler production workflow

**Demo sources:** `DemoRecyclerMarketplaceScreen`, `DemoRecyclerOrdersScreen`, `DemoRecyclerScanScreen`, `RecyclerPickupsScreen(demoMode = true)`, `RecyclerRatesScreen(demoMode = true)`.
**Existing live surface:** `ui/screens/recycler/`, `backend/src/routes/`, Recycler authorization and supply-chain services.

| Status | Demo capability | Production work remaining | Acceptance criteria |
|---|---|---|---|
| `PARTIAL` | Recycler verification/profile marked verified | Build operator review for authorization evidence, registration, type, validity, source, expiry, suspension, renewal and appeal. Store restricted evidence securely. | Protected Recycler actions require current server authorization; expiry/suspension immediately affects access. |
| `PARTIAL` | Marketplace of connected and sample lots | Replace sample lots with authorized demand/listing queries, pagination, freshness, material/grade/location matching and privacy-safe summaries. | Recycler sees only eligible lots and can distinguish live, cached, expired and demo/staging data. |
| `PARTIAL` | Accept demo offer | Implement real offer creation/acceptance with rate, validity, quantity, terms, permissions, idempotency and notifications. | Offer acceptance is atomic, auditable and cannot exceed demand or available inventory. |
| `PARTIAL` | Orders and handover queue | Show only the Recycler’s authorized accepted orders. Add expiry, cancellation, missing evidence, variance and dispute states. | Orders reconcile with offer, lot, handover, receipt and settlement records. |
| `PARTIAL` | Pickup readiness and address privacy | Replace fixed PCB pickup card with accepted order/pickup availability, capacity and address-release policy. | Exact address is released only at the allowed state and is removed/retained according to policy. |
| `PARTIAL` | Rate editor for PCB/Copper | Persist rates through an authorized API with effective time, material/grade, unit, source, approval and audit history. | Rate updates are validated, versioned, visible with freshness, and cannot rewrite historical offers. |
| `PARTIAL` | Signed QR scanner/receipt | Complete scan and manual-reference fallback with signature, expiry, nonce, role, order, lot and settlement-version checks. | Tests cover malformed, tampered, expired, replayed, wrong-role, wrong-order and duplicate payloads. |
| `PARTIAL` | Confirm actual weight/material match | Add accepted/rejected quantity, grade, notes, evidence photo, QC result, discrepancy reason and operator review path. | Variance thresholds are server-owned; the UI cannot force a clean completion. |
| `PARTIAL` | Receipt complete/material passport updated | Create immutable receipt and passport events linking source, inventory, lot, handover, accepted quantity, settlement and disposal evidence. | A complete passport is queryable by authorized parties and every event has actor/time/source. |
| `PARTIAL` | Recycler settlement | Implement settlement calculation, deductions, tax/fee policy, payment status, reversal and dispute handling. | Collector and Recycler see the same calculation and reason codes; financial records reconcile. |
| `PARTIAL` | Recycler notifications | Add offer, acceptance, handover-ready, QR, variance, settlement and document-expiry notifications. | Notifications are reliable, deduplicated, preference-aware and observable. |

### 3.5 Legacy Demo features that must be promoted, unified, or retired

**Sources:** `ui/screens/home/HomeScreen.kt`, `PricesScreen.kt`, `RecyclersScreen.kt`, `LotScreens.kt`, `QuoteScreens.kt`, `HandoverScreens.kt`, `PaymentScreens.kt`, `FutureScreens.kt`, `SettingsScreen.kt`, and the legacy route set in `AppNavHost.kt`.

The following capabilities are present in the legacy Demo navigation. Each must be integrated into the canonical production state model or explicitly retired from the product promise.

| Status | Capability | Production decision/work |
|---|---|---|
| `PARTIAL` | Price board with current rate, range, trend, history, source, freshness, disclaimer and text-to-speech | Connect to an approved price-observation source and material/grade/unit model. Store provenance, freshness, confidence and fallback behavior. Never label seeded data as live. |
| `PARTIAL` | Recycler search, radius/material/pickup filters and proximity/rate sort | Unify with live discovery and server pagination/geospatial filtering. Add stale/cache semantics and privacy-safe location handling. |
| `PARTIAL` | Recycler detail with authorization, rate, materials, hours, trust passport, call and map | Back each field with verified data, consent/contact rules and current authorization. Call/map must not expose private information before a valid relationship. |
| `PARTIAL` | Lot creation, saved lots, detail, edit, cancel, repeat and timeline | Decide whether the legacy `Lot` model becomes the canonical source listing/bulk-lot model or is retired. Do not duplicate ownership, status or payment records. |
| `PARTIAL` | Quote request and quote comparison | Prefer the live offer model if it covers the same negotiation. If retained, define quote/offer equivalence, expiry, acceptance, rejection and migration. |
| `PARTIAL` | Handover creation and document | Reuse signed handover, location, scheduled time, evidence, two-party confirmation and settlement contracts from the canonical flow. |
| `PARTIAL` | Handover evidence, scale photo, actual weight and material confirmation | Securely upload evidence, validate actor/state, retain original values, and route variance to review. |
| `PARTIAL` | Recycler rating and handover dispute | Reuse production review/dispute model with moderation, deadlines, evidence and audit history. |
| `PARTIAL` | Earnings dashboard and payment ledger | Replace local/demo totals with server-reconciled ledger entries, payout status, payment provider references, refunds and reversals. |
| `DEMO_ONLY` | Rewards with projected bonus | Define a real incentive policy, funding source, eligibility, anti-fraud, expiry, payout and tax treatment before exposing it publicly. |
| `DEMO_ONLY` | Official schemes links | Curate current official sources, verify links, record update date and avoid implying government endorsement. |
| `PARTIAL` | DIY recycling activities | Add reviewed content ownership, localization, safety review, offline caching, content versioning and reporting. |
| `PARTIAL` | Chat list/detail, send, retry and AI draft reply | Complete participant authorization, message persistence, moderation, delivery, retention, abuse reporting, encryption/transport, and AI privacy/quality controls. |
| `PARTIAL` | Notifications and mark-read | Implement push/in-app delivery, event routing, unread counts, deep-link authorization, pagination, retention and provider failure handling. |
| `PARTIAL` | Fairness/dispute analytics | Replace hard-coded example numbers with permissioned aggregates, explainable calculations, anonymization and operator workflows. |
| `PARTIAL` | Settings sync center | Show real account-scoped queued operations, conflict states, retry backoff, permanent failure actions and safe recovery after logout/session expiry. |

## 4. Cross-cutting production work

These items apply to every feature above and are release blockers unless explicitly waived by a documented pilot decision.

### 4.1 Identity, authorization and privacy

- Use production OTP/email/phone verification and account recovery; remove development OTP/provider fallbacks.
- Enforce server-side role, ownership, participant, organization and operator authorization on every read and mutation.
- Recheck current account status, role, authorization expiry and suspension at mutation time.
- Define consent for phone, location, photos, messages, data-bearing devices and data-destruction intent.
- Implement account deletion, export, retention, legal hold, backup and restoration policies.
- Protect exact household addresses, phone numbers, photos, authorization documents and payment references.
- Review IDOR, mass assignment, replay, cross-tenant cache, stale-token, wrong-role QR and notification-leak risks.

### 4.2 Backend, database and contracts

- Publish versioned OpenAPI contracts for every production route.
- Add schema indexes for account ownership, state, expiry, location, material, offer/lot/order links and idempotency keys.
- Make state transitions explicit and server-owned; reject invalid transitions with stable error codes.
- Use idempotency for create, accept, schedule, payment, QR confirmation, offer and sync mutations.
- Preserve immutable audit events and original values for corrections, disputes and reversals.
- Run MongoDB staging integration tests for concurrency, duplicate requests, process retries, stale state, authorization and recovery.
- Add migration/rollback plans and backup/restore drills before production data is created.

### 4.3 Payments and financial controls

- Choose the supported production payment modes: cash, UPI, bank transfer or provider-backed payout.
- Define who pays whom, when funds are considered pending/complete/failed/reversed, and how fees/taxes are displayed.
- Store provider references and verify provider webhooks/signatures server-side.
- Reconcile app ledger, provider ledger and operational cash records.
- Add refund, reversal, partial payment, disputed settlement and failed payout paths.
- Obtain legal/accounting review before exposing rewards, projected bonuses or financial guarantees.

### 4.4 Offline, sync and recovery

- Classify every mutation as safe offline, queueable with conflict resolution, or online-only.
- Scope Room tables, outbox items and sync records by account/tenant.
- Use stable client IDs and idempotency keys; never replay a mutation without a defined server outcome.
- Show pending, syncing, retrying, failed, conflict and permanently rejected states.
- Test airplane mode, network switching, process death, force-stop, clock skew, token expiry, duplicate sync and partial upload.
- Preserve unsynced user work across session refresh where policy allows, without leaking it after account switch/logout.

### 4.5 Evidence, trust and compliance

- Define the material passport schema and event ownership for listing, pickup, weighing, inventory, lot/pool, handover, receipt, QC, settlement and disposal.
- Store evidence provenance: actor, timestamp, device/session, source, hash/reference, visibility and retention.
- Separate “platform verified”, “business verified”, “government authorization”, “user rated” and “operator reviewed”.
- Add hazardous-material and data-destruction safety workflows with acknowledgement and escalation.
- Obtain specialist e-waste, privacy, payments and consumer-protection review for the launch geography.

### 4.6 Notifications, support and operations

- Configure and accept the implemented FCM HTTP v1 push path, durable in-app inbox, and any approved SMS/WhatsApp provider.
- Make notification templates localized, deduplicated, privacy-safe and linked to authorized routes only.
- Build an operator console or controlled support tool for Recycler verification, pickup exceptions, disputes, anomaly review, payment verification, settlement correction and user support.
- Add audit log search, operator reason codes, two-person approval where financial/authorization risk requires it, and break-glass logging.
- Define escalation SLAs for missed pickup, payment dispute, hazardous material, data-bearing device and fraud reports.

### 4.7 Production infrastructure and release

- Provision separate development, staging and production environments.
- Use HTTPS, managed MongoDB, private object storage, secret management, distributed rate limits and least-privilege service accounts.
- Configure real domain, certificates, CORS allowlist, backups, restore tests, logs, metrics, alerts and incident response.
- Remove invalid placeholder release API defaults and unsafe debug/test flags.
- Add signed release builds, Play/App Distribution configuration, crash reporting, staged rollout and rollback plan.
- Verify R8/ProGuard, WorkManager reflection, deep links, backup rules and secure storage in a release build.

## 5. Recommended implementation order

### Phase 0 — Canonical product contract

- [ ] Choose the canonical production state machine and entity mapping.
- [ ] Decide the relationship between legacy `Lot/Quote/Handover/Payment` and live `Listing/Pickup/Inventory/BulkLot/Offer/Handover` models.
- [ ] Publish state diagrams, role permissions, event names, error codes and settlement rules.
- [ ] Decide pilot geography, materials, payment modes, notification channels and supported languages.

### Phase 1 — Production foundation

- [ ] Production auth, account recovery and role authorization.
- [ ] Production environment, HTTPS, secrets, storage, database, backups and observability.
- [ ] OpenAPI contracts, idempotency, audit events, notification foundation and operator access.
- [ ] Secure image/document upload and retention/deletion policy.

### Phase 2 — Household to Kabadiwala

- [ ] Listing creation and evidence.
- [ ] Live collector discovery and assignment.
- [ ] Capacity-aware scheduling, reschedule, cancellation and reassignment.
- [ ] Arrival, weighing, material confirmation and payment.
- [ ] Household QR confirmation, receipt, rating, notification and dispute.

### Phase 3 — Kabadiwala operations

- [ ] Inventory ledger and reservations.
- [ ] Pickup queue and offline field recovery.
- [ ] Material passport and evidence timeline.
- [ ] Bulk lot creation and source attribution.
- [ ] Route advantage with real data and clear uncertainty.

### Phase 4 — Recycler and downstream chain

- [ ] Recycler authorization/operator review.
- [ ] Requirements, marketplace, offers and order states.
- [ ] Formal handover QR, two-party confirmation and variance review.
- [ ] Recycler QC, settlement, disposal evidence and passport completion.
- [ ] Pooling with concurrency, lock, underfill, cancellation and proportional settlement.

### Phase 5 — Legacy parity and supporting features

- [ ] Unify or retire legacy lots, quotes, handovers, payments and timelines.
- [ ] Production price board and recycler detail.
- [ ] Production chat, notifications, rewards policy, schemes content, activities and analytics.
- [ ] Complete profile, settings, sync center, localization and accessibility.

### Phase 6 — Pilot readiness

- [ ] Complete staging end-to-end run with separate Household, Kabadiwala, Recycler and Operator accounts.
- [ ] Test concurrency, duplicate/replay, wrong-role, privacy, payment, offline, recovery and dispute cases.
- [ ] Run field-device/accessibility/localization/performance checks.
- [ ] Verify support/on-call, legal/compliance, data retention, backup/restore and incident runbooks.
- [ ] Release a narrow pilot; do not claim open-market production readiness until the pilot evidence is reviewed.

## 6. Minimum end-to-end production acceptance test

The following must pass on a staging environment using four separate accounts: Household, Kabadiwala, Recycler and Operator.

1. Household registers, creates a real listing, uploads a protected photo, acknowledges safety requirements, and sees a clearly labeled estimate with source/freshness.
2. Kabadiwala discovers only eligible nearby supply, accepts it, reserves a valid time slot and receives a notification.
3. Household and Kabadiwala see the same pickup state; process death and network loss do not create duplicate assignments.
4. Kabadiwala records arrival, actual weight, material/grade and payment; inventory increases atomically and the receipt remains auditable.
5. Household confirms the correct signed QR; expired, replayed, tampered and wrong-role payloads fail safely.
6. Both parties see the same final settlement, payment status and evidence timeline.
7. Kabadiwala creates a bulk lot from owned available inventory without overselling or hiding source ownership.
8. Recycler with current authorization sees the lot, submits/accepts an offer and receives only authorized details.
9. Handover QR is generated, scanned and confirmed by the correct Recycler; actual accepted quantity and material match are recorded.
10. A variance above policy threshold routes to review rather than silently completing.
11. Recycler QC/disposal evidence and material passport events are recorded with provenance.
12. Household, Kabadiwala and Recycler receive correct notifications and cannot open each other’s unrelated records.
13. Payment/payout records reconcile with the application ledger and provider/reference data.
14. Operator can inspect the audit trail, resolve a dispute, correct/reverse a payment through controlled actions, and see who performed each action.
15. Logout, token expiry, account switch, reinstall, backup restore and deletion/retention behavior are verified.

## 7. AI implementation rules

When an AI agent works on this roadmap:

- Read this file plus `docs/MASTER_GAP_ANALYSIS.md`, `docs/PRODUCT_ASSESSMENT.md`, `docs/BACKEND_API_CONTRACT.md`, `docs/OFFLINE_SYNC_DESIGN.md`, `docs/MATERIAL_PASSPORT.md`, `docs/PRIVACY_RETENTION.md`, `docs/SECURITY_THREAT_MODEL.md`, and the relevant source files before editing.
- Inspect the current branch and preserve unrelated user changes.
- Search for all uses of `DemoDataProvider`, `DemoSessionStore`, `MockRecyclerData`, `demoMode`, hard-coded demo IDs, sample names, sample rates, and demo-only route guards before converting a feature.
- Prefer extending the existing live supply-chain APIs and repositories over creating a parallel implementation.
- Keep demo fixtures isolated to debug/testing builds. Do not silently use demo fallback data when a production request fails.
- Implement backend contract and authorization before wiring a production UI action.
- Add or update tests for both success and failure/security paths.
- Do not invent provider credentials, legal claims, government verification, real prices, payout guarantees, or field evidence.
- If a required provider, legal decision, operational policy or credential is missing, mark the item `BLOCKED`, document the exact dependency, and continue with safe non-blocked work.
- Update this file’s status and evidence links only after verification. “Compiles” is not sufficient for `READY`.

## 8. Repository references

- Demo entry and role selection: `android_app/app/src/main/java/com/irinteractivestudios/kabadiwalaconnect/ui/screens/auth/OnboardingScreen.kt`
- Demo activation/navigation: `android_app/app/src/main/java/com/irinteractivestudios/kabadiwalaconnect/MainActivity.kt`, `ui/navigation/AppNavHost.kt`, `ui/navigation/Destinations.kt`
- Demo fixtures and role screens: `android_app/app/src/main/java/com/irinteractivestudios/kabadiwalaconnect/ui/demo/`
- Live Household screens: `android_app/app/src/main/java/com/irinteractivestudios/kabadiwalaconnect/ui/screens/household/`
- Live Kabadiwala/Recycler supply-chain screens: `android_app/app/src/main/java/com/irinteractivestudios/kabadiwalaconnect/ui/screens/supplychain/` and `ui/screens/recycler/`
- Legacy workflow screens: `ui/screens/lots/`, `ui/screens/quotes/`, `ui/screens/handovers/`, `ui/screens/payments/`, `ui/screens/transactions/`
- Android data/repositories/sync: `android_app/app/src/main/java/com/irinteractivestudios/kabadiwalaconnect/data/`
- Backend routes/services/schema: `backend/src/`, `backend/prisma/`
- Existing architecture and readiness evidence: `docs/MASTER_GAP_ANALYSIS.md`, `docs/PRODUCT_ASSESSMENT.md`, `docs/EXECUTION_PLAN.md`, `docs/BACKEND_FINAL_VERIFICATION.md`
