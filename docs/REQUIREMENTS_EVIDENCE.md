# Requirement Evidence Matrix

This file is the submission index. “Implemented” means a repository path exists and is testable; it does not claim field validation or official authorization data.

| Requirement | Android evidence | API/data evidence | Demo proof |
|---|---|---|---|
| Photograph, categorize, weigh, estimate | `LotScreens.kt`, `LotManagementViewModel.kt` | `POST /lots`, `POST /lots/:id/photo`, `GET /lots/:id/valuation`; `Lot` fields include source, unit, provenance, quality and regime | Create a PCB lot offline, reconnect, show estimate disclaimer |
| Price board, speech, trend, provenance | `PricesScreen.kt`, `PriceSpeaker.kt` | `/prices/board`, `/prices/history`; `Price` and `PriceHistory` include unit, source reference, ingestion and quality | Play Hindi/Marathi price; show stale warning using old fixture |
| Verified nearby recycler matching | `RecyclersScreen.kt`; household live mode routes here | `/recyclers`, `/recyclers/match`; verified evidence fields on `Recycler` | Compare distance, rate, pickup and authorization evidence |
| Quote, acceptance, disagreement/rematch | `QuoteEntryScreens.kt`, `QuoteScreens.kt` | `/quotes/*`; accepted quote creates an open `Conversation`; rejected quotes leave the lot available | Reject one quote, request another, accept the second |
| Private chat after acceptance | `FutureScreens.kt` chat | `/future/conversations/*`; acceptance upserts conversation | Send messages from collector and recycler accounts |
| Verifiable handover | `HandoverScreens.kt` | HMAC-signed QR payload, `/verify/handover`, DB-bound reference, expiry, recycler confirmation | Alter one QR character and show rejection; verify original |
| Earnings and optional payment | `PaymentScreens.kt`, `EarningsScreen.kt` | `/payments`, `/earnings/ledger`; CASH remains supported | Record cash, show ledger and pending/paid state |
| Safety and separate battery routing | `InfoScreens.kt`, lot material warnings | `Lot.wasteRegime`; battery lots use `BATTERY_WASTE` | Select battery and explain separate handling stream |
| Hindi/Marathi, low-literacy | locale resources, large controls, TTS | preferred-language fields | Complete collector path in Hindi or Marathi with font scale 1.3× |
| Offline tolerance | Room, WorkManager, `Sync.kt` | idempotent `/sync` with conflict results | Create lot offline, reconnect, verify one server record |
| Structured operational datasets | Room cache + network DTOs | Prisma material, price, recycler, transaction, traceability and AI records; admin import/export | Export one completed transaction from `/admin/datasets/export` |
| AI governance | manual confirmation UI | `AiInference` records model/version, provenance, confidence, correction and training consent | Classify a photo, manually correct, explain limitations |

## Evidence still requiring people or deployment

- Two consented collector/aggregator sessions and one recycler session must be conducted and attached using `FIELD_RESEARCH_PROTOCOL.md`.
- CPCB/SPCB evidence must be checked by an authorized operator; seeded recyclers are demo records only.
- A signed release must be benchmarked on a 2 GB Android 6/7 device.
- Provider owners must rotate/revoke the previously committed MongoDB and Gemini credentials and decide whether to rewrite Git history.

