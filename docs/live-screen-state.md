# Live screen state and action reconciliation

Updated 1 October 2026.

## Root cause

Separate SupplyChainViewModel instances per navigation entry let Home update while restored Pickups retained an old snapshot. Mutations also discarded returned records and waited for a whole dashboard reload.

## Ownership

Phone registration must not silently sign into an existing account of another role. The backend checks selected versus existing role, including concurrent insert recovery; Android checks the returned role before session persistence. Signup conflicts require explicit existing-account sign-in or a different phone number.

- Related authenticated routes share feature ViewModels/StateFlow.
- Account or role changes, including logout, release the old scope.
- Async work captures account/session and request generation; late results cannot publish into another session.
- Confirmed mutations merge returned records before background reconciliation and save account-scoped cache snapshots.
- Pending controls acknowledge actions and block duplicate taps. Financial, competitive, QR and inventory outcomes remain server-confirmed.
- Existing Room-backed features retain repository ownership.

## Automatic refresh

Visible screens refresh on resume and account-checked push/mutation hints, coalesced for 200 ms. Operational navigation reuses data within a ten-second window; explicit refresh and relevant events bypass it.

While operational journey routes are resumed, an account-scoped change-feed fallback runs every two seconds; other authenticated screens use a 120-second fallback. A feed check only reconciles content when changes are found. This is a network completion mechanism, not a measured UI latency guarantee.

Mutation refresh selects affected dependency groups. Reads begun before/during a confirmed write are cancelled or rejected. Independent sections publish as they complete. Paging/read-only work must not invalidate its own generation; partial refresh must not reset the age of a whole cached snapshot.

Activity traversal uses stable account/role cursors. Android drains up to 20 pages per run, checks cancellation/session ownership and persists each cursor after applying its page. Outbox success emits a hint after updating local storage.

## Scanner, chat and settings

Exact-record QR verification replaces history lookup. Reset cancels verification; confirmation blocks competing reset/scan actions until reconciliation. Confirmed receipt updates Orders/marketplace before navigation.

Household QR display checks the exact pickup for `householdQrScannedAt`. Collector handover display checks an owner-only status endpoint for `recyclerQrScannedAt` and receipt status. The display hides after a server-confirmed scan; scanning alone does not confirm material receipt or payment. Both capture entry points use a portrait activity. Recycler receipt completion returns to Market; Collector handover completion returns Home and reconciles affected data.

Chat preserves pending/failed states, deduplicates client message IDs and retains conversation metadata through Room 30. Timestamp normalization supports legacy epoch text. Cached content remains usable with refresh failure/retry feedback.

Availability and pickup-charge saves have separate action identities. Profile updates should not reload the entire role dashboard solely because the account snapshot changed.

## Coverage and limits

Controlled fixtures cover cross-tab completion, independent results, rollback, duplicate taps, stale responses and scanner publication. Unit tests cover account scope, refresh dependencies and cache behavior.

Recorded Android unit results: 169 passed. Full connected acceptance remains incomplete: SafetyLayoutTest failed on an off-screen lazy-list lookup. Existing test coverage is not a claim that every connected test passed.

See [verification](README.md) and [performance measurements](PERFORMANCE_BASELINE.md). Fixtures do not establish live FCM latency or physical-device p95.
