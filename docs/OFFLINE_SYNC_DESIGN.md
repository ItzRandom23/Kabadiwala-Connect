# Offline and Synchronisation Design

Status: implemented for the prototype, with explicit online-only boundaries.

## What survives a disconnected session

| Surface | Local state | Reconnect behavior | Limitation |
| --- | --- | --- | --- |
| Lot intake | Room `LotEntity` and `SyncQueueEntity` | Idempotent create/update/photo operations are retried by WorkManager | Server confirmation is pending until connectivity returns |
| Price and recycler catalogue | Room catalogue rows | Successful server refresh replaces the area snapshot; stale rows are marked by their timestamp | No live price claim while offline |
| Formalisation dashboard | Account-scoped `FormalisationSnapshot` | Last successful route, pool, handover, passport and safety evidence is shown with a cached label | Marketplace mutations still require a connection |
| Recycler supply receipt | Signed QR text plus queued `RECYCLER_CONFIRM`/`DISPOSAL_EVIDENCE` operations | Worker retries the exact operation; the server verifies QR/nonce, current authorization and conditional state transitions | A disconnected Recycler cannot receive a final server settlement immediately |

The app does not claim that all screens are offline-first. Creating a route
estimate, opening/joining/locking a pool, preparing a QR, or changing a
settlement requires a live API response because those operations reserve shared
inventory or change another party's state.

## Queue rules

Every queued mutation has an operation UUID, account/profile scope, payload,
attempt metadata and a retry state. The worker validates material, condition,
weight and role before sending. Backend mutations use conditional status and
ownership predicates; duplicate operation keys or already-applied terminal
states are treated as successful convergence, not as a second transaction.

The backend sync queue is role-aware. Collector operations include pool
contribution/release, settlement decision and collector confirmation; verified
Recycler operations include signed handover confirmation, QC, disposal evidence
and formal payment. A Collector cannot enqueue a Recycler receipt, and a
Recycler cannot use the queue to invoke collector-only operations. Retryable
network/server failures remain pending; validation, forbidden and terminal
conflict errors are surfaced for operator attention.

## Conflict and recovery behavior

The server is authoritative for inventory and settlement. On reconnect, the app
reconciles server changes before refreshing catalogues, preserves unsynced local
lot edits, and exposes review/conflict states instead of silently overwriting
them. A handover QR is bound to a server record, a nonce hash, a consignment
hash, an expiry and the Recycler identity; the server rejects a changed,
expired, wrong-role or replayed payload.

## Executed emulator evidence

With Wi-Fi and mobile data disabled, the final installed APK saved a Collector
lot (`Cables`, 2.3 kg, Kothrud Pune), then the app process was force-stopped.
Room inspection after the kill showed one durable `CREATE_LOT` outbox row and
the unsynced lot still present. After radios were restored and the session was
re-authenticated, WorkManager replayed that row once; the worker returned HTTP
200 and the in-app Sync Center showed `All local actions synced`. The formal
Recycler flow was also exercised on the same emulator: a 538-byte signed QR
was entered in chunks, the passport matched, and one 1.0 kg receipt completed.
Shared marketplace writes remain intentionally online-only.

During this test a minified-build reflection defect was found: WorkManager's
default `OverwritingInputMerger` had been removed by R8. The final rules keep
the merger and `SyncWorker` constructors, and the reconnect test above was
repeated successfully with those rules installed.

## Known offline gaps

There is no local transactional replica for shared pool membership or demand;
discovery and coordination remain online-only even though selected mutations
can be queued for server reconciliation. QR display can be cached, but the
prototype does not perform the HMAC verification entirely on-device before
reconnect. The backend change feed uses an opaque per-feed cursor with a
timestamp/ID tie-breaker; Android must persist and advance `nextCursor` after a
successful pull. Background sync behavior is tested on the emulator, not yet on a
representative low-end Android 6/7 handset.
