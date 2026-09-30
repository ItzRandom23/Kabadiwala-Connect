# Live screen state and action reconciliation

## Cause of stale screens

Operational destinations previously created a separate `SupplyChainViewModel` for each navigation entry. Completing a pickup on Home updated that entry, while a restored Pickups entry retained its old snapshot. Several mutations also discarded their returned record and waited for an unrelated dashboard fanout before changing the visible row.

## State ownership

- An authenticated feature scope shares Supply Chain, Recycler profile/orders, account profile and chat/inbox ViewModels across related routes.
- The scope survives activity recreation and releases its ViewModels when the account or role changes, including logout.
- Room-backed legacy lot, quote, handover and payment screens continue observing their existing repositories.
- Confirmed supply mutations publish their returned records into the shared StateFlow before background reconciliation. Pending controls acknowledge the tap immediately; financial and competitive outcomes remain server-confirmed.
- Confirmed supply snapshots are saved into the existing account-scoped Room cache. Safe offline drafts/outbox operations retain their existing pending status.

## Automatic reconciliation

- Visible screens refresh on resume and react to account-matched successful mutation and push hints. Rapid hints are coalesced for 200 ms.
- Operational tabs reuse shared data for ten seconds on navigation. Explicit refresh and mutation/push hints bypass this navigation freshness window.
- Completed WorkManager operations emit a hint after the local cache/outbox has been updated.
- Supply mutation success cancels reads started before or during that write, publishes the authoritative record, then starts one reconciliation pass.
- Independent pickup, inventory, offer, lot and handover reads publish as they complete. Optional tools do not delay those rows.
- Recycler scanner confirmation publishes its handover to Orders and marketplace state immediately, and updates the existing offline cache.
- Rewards, schemes, activities and analytics request their own data rather than fetching six unrelated features.
- Admin section reads use cancellation/generations; payment reads run concurrently and successful returned rows are merged before reconciliation.

## Regression coverage

`LiveScreenStateTest` exercises real Compose/navigation/ViewModels with controlled API responses:

- Complete on Home, navigate to Pickups, and see Completed while refresh requests remain stalled.
- Publish history while the assigned-pickup endpoint is stalled.
- Preserve Scheduled after a failed completion.
- Acknowledge a slow completion immediately and prevent duplicate submission.
- Reject an older Scheduled response after completion.
- Reflect scanner confirmation in Recycler Orders without another refresh.

Unit tests cover account-scope cleanup, mutation hints, isolated feature reads and offline activity fallback. Existing offer, chat-entry, QR-handover and listing-navigation tests are retained.

These fixtures establish state behavior, not live-server latency or a physical-device p95 performance result. Real API latency and two-device notification delivery still need measurement against the deployed backend.
