# Backend stabilization status

Updated **1 October 2026**, accompanying Android **0.1.13-beta (77)**. This is engineering status, not production certification.

## Implemented

- Transactional quote validation checks lot state, expiry and authorization; acceptance closes competing pending requests.
- Counterproposals preserve original Recycler pricing and require explicit consent.
- Competitive and financial outcomes stay online-authoritative; old queued confirmations need reconciliation.
- Account/session checks protect async results, caches and queued work.
- Participant privacy projections apply to ordinary and incremental handover reads.
- Exact-record authenticated QR verification replaces recent-history searching.
- Replay distinguishes transient failures from terminal business rejection and retains operation identity.
- Eligible Household messages in existing conversations can use the outbox.
- Activity cursors are account/role-scoped; resource changes trigger reconciliation.
- Bounded histories and complete financial aggregates are separate.
- Password hashing is asynchronous without changing existing password formats.
- Photos validate before storage; content-derived filenames distinguish changed uploads.
- Required database index preparation fails explicitly when maintenance cannot complete.
- Pickup hours are **7:30 AM–9:30 PM Asia/Kolkata**.

Android counterparts include shared account state, immediate pending controls, selective refresh and Room migration 29 → 30. See [state ownership](../docs/live-screen-state.md).

## Recorded verification

For 0.1.13-beta, backend Prisma generation/TypeScript compilation and Android `assembleEnvTestingDebug` passed. The APK manifest, update checksum and file size were checked. Tests and live two-device journeys were not re-run for this release.

Backend: **258 tests passed**, two live integration files skipped; Prisma generation, build and type checking passed. Isolated MongoDB fixtures exercised selected Recycler review, acceptance races, listing/photo retry and high-volume feeds. Traversal covered 1,000 offers and 1,500 account notifications without duplicates/skips.

Android: **169 unit tests passed**, testing APK and instrumentation APK built. The connected suite was not fully passing: SafetyLayoutTest failed on an off-screen lazy-list lookup.

These checks were recorded during stabilization, not re-run for this documentation update.

## Remaining limitations

- Actual offline listing/photos across process death and reconnect needs full acceptance.
- Two-device FCM and delayed shared-device delivery need runtime verification.
- Remote database samples still include seconds of latency; no representative-device under-100-ms p95 feedback gate is established.
- Complete journeys, long sessions, resource usage and accessibility remain acceptance work.
- Legacy since-only sync reads retain older unbounded behavior; current Android requests pages.
- Direct Recycler payment controls do not add individual pooled-contributor payout entry.
- Local storage retry file operations need further verification; content hashing alone is not complete runtime assurance.
- Two moderate development-tooling dependency advisories remained at the recorded audit; no blanket upgrade was performed.

## Rollout

Deploy additive backend contracts before Android. Back up data, review counterproposal schema/index changes and prepare indexes. Keep the non-destructive Room upgrade. Source pushes are not production deployment.

This release also adds nullable `BulkLot.fulfillmentMode` and `SupplyHandover.recyclerQrScannedAt`, participant trade-detail routes and a Collector handover-status route. Existing documents need no value backfill. Regenerate Prisma Client and deploy these contracts before the Android update. Delivery/pickup rules, scan dismissal and portrait scanning still need live journey acceptance.

See [documentation index](../docs/README.md) and [backend setup](README.md).
