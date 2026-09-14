# Requirement Evidence Matrix

This file is the submission index. “Implemented” means a repository path exists and is testable; it does not claim field validation or official authorization data.

| Requirement | Android evidence | API/data evidence | Demo proof |
|---|---|---|---|
| Photograph, categorize, weigh, estimate | `LotScreens.kt`, `LotManagementViewModel.kt` | `POST /lots`, `POST /lots/:id/photo`, `GET /lots/:id/valuation`; `Lot` fields include source, unit, provenance, quality and regime | Create a PCB lot offline, reconnect, show estimate disclaimer |
| Price board, speech, trend, provenance | `PricesScreen.kt`, `PriceSpeaker.kt` | `/prices/board`, `/prices/history`; `Price` and `PriceHistory` include unit, source reference, ingestion and quality | Play Hindi/Marathi price; show stale warning using old fixture |
| Verified nearby recycler matching | `RecyclersScreen.kt`; household live mode routes here | `/recyclers`, `/recyclers/match`; verified evidence fields on `Recycler` | Compare distance, rate, pickup and authorization evidence |
| Quote, acceptance, disagreement/rematch | `QuoteEntryScreens.kt`, `QuoteScreens.kt` | `/quotes/*`; accepted quote creates an open `Conversation`; rejected quotes leave the lot available | Reject one quote, request another, accept the second |
| Private chat after acceptance | `FutureScreens.kt` chat | `/future/conversations/*`; acceptance upserts conversation | Send messages from collector and recycler accounts |
| Verifiable handover | `HandoverScreens.kt`, `RecyclerScreens.kt`, `SupplyChainScreens.kt` | HMAC-signed legacy QR plus one-time formal QR, `/verify/handover`, `/recycler/handovers/confirm`, DB-bound reference, expiry, two-party confirmation | Prepare pool/bulk QR, collector-confirm, replay/tamper/expiry rejection |
| Earnings and optional payment | `PaymentScreens.kt`, `EarningsScreen.kt` | `/payments`, `/earnings/ledger`; CASH remains supported | Record cash, show ledger and pending/paid state |
| Safety and separate battery routing | `InfoScreens.kt`, lot material warnings | `Lot.wasteRegime`; battery lots use `BATTERY_WASTE` | Select battery and explain separate handling stream |
| Hindi/Marathi, low-literacy | locale resources, large controls, TTS | preferred-language fields | Complete collector path in Hindi or Marathi with font scale 1.3× |
| Offline tolerance | Room, WorkManager, `Sync.kt`, Sync Center | idempotent `/sync` with conflict results and account-scoped queue ownership | On `emulator-5554`, save a 2.3 kg lot offline, force-stop, restore radios, replay once, and verify `All local actions synced` |
| Structured operational datasets | Room cache + network DTOs | Prisma material, price, recycler, transaction, traceability and AI records; admin import/export | Export one completed transaction from `/admin/datasets/export` |
| AI governance | manual confirmation UI | `AiInference` records model/version, provenance, confidence, correction and training consent | Classify a photo, manually correct, explain limitations |
| Formal Route Advantage | `FormalisationDashboard` route card | `/kabadiwala/route-advantage`; rate, baseline, logistics, net, confidence, freshness and “why” fields | Compare two verified routes; state estimate/demo boundary |
| Cooperative Pooling | `FormalisationDashboard` pool cards | `PooledConsignment`, `PoolContribution`, threshold/lock/leave/settlement APIs and atomic inventory reservations | Add independent contributions, reach threshold, lock and settle proportionally |
| Offline Material Passport | formal handover cards, cache and sync queue; restart-safe cached dashboard/scanner evidence | `MaterialPassportEvent`, `SupplyHandover`, QR nonce/hash, cached evidence and `CONFIRM_SUPPLY_HANDOVER` retry | Emulator: disconnect/relaunch showed cached route/pool/passport/safety/handover evidence; connected final QR receipt completed once; separate offline lot replay completed once |
| Fairness & Dispute Guard | recycler QC and collector settlement actions | `SettlementBreakdown`, `AnomalyFlag`, accepted/rejected weight, reason/evidence, `REVIEW_REQUIRED` | Use >5% weight or mismatch branch; accept or raise issue |
| Collector Growth Passport | passport card and safety modules | `CollectorPassport`, `SafetyProgress`; platform-generated labels | Show formal kg/handovers/safety labels with non-certification disclaimer |
| Reverse Demand Network | demand-intelligence and pool-opportunity cards | recycler requirements, supply gap/level, privacy-safe aggregate targeting | Seed demand and show gap/pooling suggestion as demo data |

## Evidence still requiring people or deployment

- Two consented collector/aggregator sessions and one recycler session must be conducted and attached using `FIELD_RESEARCH_PROTOCOL.md`.
- CPCB/SPCB evidence must be checked by an authorized operator; seeded recyclers are demo records only.
- A signed release must be benchmarked on a 2 GB Android 6/7 device.
- A second independent device profile and performance pass are still needed
  before any field-scale performance claim; the current emulator workflow and
  baseline are recorded in the final report.
- Provider owners must rotate/revoke the previously committed MongoDB and Gemini credentials and decide whether to rewrite Git history.
