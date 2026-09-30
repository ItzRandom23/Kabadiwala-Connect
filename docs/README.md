# Project documentation

Updated **30 September 2026**. Current Android beta: **0.1.12-beta (76)**.

These documents describe current contracts and recorded verification. They do not certify every workflow or device. The five obsolete Android planning/report documents remain removed.

## Documentation map

| Document | Purpose |
| --- | --- |
| [Project README](../README.md) | Product, architecture and Android build |
| [Backend README](../backend/README.md) | API setup, authorization and deployment |
| [Backend progress](../backend/PROGRESS.md) | Stabilization status and limitations |
| [APK publishing](../backend/app-update/README.md) | Current manifest and publishing procedure |
| [Pickup state machine](PICKUP_STATE_MACHINE.md) | Physical and financial transitions |
| [Recycler receipt/payment](recycler-receipt-payment.md) | Offers, handovers and settlement |
| [Live screen state](live-screen-state.md) | Account ownership and reconciliation |
| [Performance baseline](PERFORMANCE_BASELINE.md) | Measurements and outstanding gates |

## Current contracts

- Household pickup hours: **7:30 AM–9:30 PM, Asia/Kolkata**.
- Acceptance, QR verification, inventory transfer and payment confirmation require server-confirmed online results.
- Cached content and supported drafts/messages can survive network loss. Queued work is pending, not accepted or completed.
- Physical completion and payment receipt are separate.
- Shared account-scoped state publishes confirmed records before reconciliation; stale session/request responses are rejected.
- Android notification display checks recipient account and supported role.
- English and 20 additional locale packs are included. Resource coverage does not certify translation quality or every dynamic server message.

## Recorded verification

| Area | Result | Limit |
| --- | --- | --- |
| Backend suite | 258 tests passed; two live integration files skipped | Database fixtures run separately |
| Backend build/type checking | Passed | Not deployed-server verification |
| Android unit suite | 169 passed | Controlled JVM fixtures |
| Android debug APK/test APK | Built | Not production signing |
| Connected Android suite | Not fully passing; SafetyLayoutTest failed on an off-screen lazy-list lookup | Complete emulator acceptance pending |
| Live isolated MongoDB | Recycler review/resubmission, concurrent pickup acceptance, listing/photo retry and bounded feeds exercised | Selected journeys only |
| High-volume traversal | 1,000 offers and 1,500 account notifications without skipped/duplicate IDs | Not an app latency benchmark |
| Offline persistence | Queue/cache ownership and persistence covered | Actual listing/photo process-death replay still needs complete acceptance |

These are point-in-time stabilization results, not tests re-run for this documentation change.

## Remaining acceptance

Complete role journeys under slow network, process death and reconnect. Measure local tap feedback below 100 ms p95 on a representative physical device separately from API completion. Verify two-device FCM, external payment evidence, long-session resource usage and accessibility.

## Rollout order

1. Back up the database and review additive Prisma fields/index changes.
2. Deploy backend contracts first; generate Prisma Client and prepare required indexes.
3. Deploy Android with non-destructive Room migration 29 → 30.
4. Publish matching APK and manifest together.
5. Check deployed readiness and critical journeys.

GitHub pushes do not deploy a running backend. Keep credentials, tokens and private test data out of documentation.
