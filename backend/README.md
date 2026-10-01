# Kabadiwala Connect backend

Express 5 / TypeScript API with Prisma 6.12 and MongoDB. Current Android beta: **0.1.14-beta (78)**. The npm package version is independently **1.0.0**.

## Setup

Requirements: Node.js 20+, npm and a MongoDB replica set. Standalone MongoDB does not support the transactions used here.

From the repository root:

```powershell
cd backend
Copy-Item .env.testing.example .env
npm ci
npm run db:generate
```

Configure the untracked environment file: DATABASE_URL, separate strong JWT_SECRET and TRACEABILITY_SIGNING_SECRET, storage settings and PORT. The example database hostname is the Docker service name. Its APP_VERSION is illustrative, not the Android release source of truth.

For a new disposable database, review and apply schema/index preparation:

```powershell
npm run db:push
npm run db:prepare
npm run dev
```

Back up existing databases and review changes before db:push. Both db:migrate and db:push invoke Prisma db push; neither is a versioned SQL migration runner.

The API prefix is /api/v1, readiness is /api/v1/ready, and the example port is 4000. [compose.testing.yml](compose.testing.yml) provides a disposable replica set; example credentials are testing-only.

## Authorization and transitions

Middleware and routes enforce role, resource ownership, account state, Recycler authorization and Admin permissions. Client-supplied user/role values and UI guards do not grant permissions.

Conditional claims and transactions protect competing acceptance. QR verification checks signature, nonce, expiry, recipient and current server state. Household pickup hours are **7:30 AM–9:30 PM Asia/Kolkata**; scheduling also validates lead time, horizon and capacity.

Physical completion, agreed amount, recorded payment and verified receipt are separate. See [pickup transitions](../docs/PICKUP_STATE_MACHINE.md) and [Recycler settlement](../docs/recycler-receipt-payment.md).

New direct bulk lots can specify `COLLECTOR_DELIVERY` or `RECYCLER_PICKUP` through optional `BulkLot.fulfillmentMode`. Pickup requires a pickup-enabled Recycler; handover preparation and receipt enforce the selected location. Legacy lots retain location selection. Participant-only trade-detail routes expose meeting locations without private source records. Collector handover-status reads and optional `SupplyHandover.recyclerQrScannedAt` let Android dismiss a scanned QR without implying receipt or payment.

## Sync, notification and paging contracts

- Notifications target an account; delivery rechecks device ownership and Android checks account/role before display. Push is a refresh hint, not transaction confirmation.
- Eligible replay operations retain owner, dependencies and operation identity. Transient failure remains retryable; terminal business rejection remains visible.
- Changed payload under an existing operation identity is a conflict. Supported domain and successful replay-ledger writes commit atomically.
- Legacy queued acceptance/handover confirmations require explicit online reconciliation.
- Incremental sync uses participant privacy projections; contributors must not receive another participant's QR, private source references or unrelated settlement metadata.
- Current paged callers use bounded pages/cursors. Financial totals are independent of paginated history.
- Activity cursors are account/role-scoped with stable timestamp/ID positions and traversal bounds.
- Legacy sync changes with only since and no limit/cursor retains its older response behavior; do not assume every compatibility route is bounded.
- Optional BulkOffer.counterRatePerKg preserves the original offer pending explicit agreement. Review additive schema/index changes before dependent client rollout.

## Checks and deployment

```powershell
npm run build
npm run lint
npm test
```

Build/test pre-scripts generate Prisma Client. Ordinary tests skip opt-in live integration fixtures. Use disposable databases and fixture guards; never seed application data. See [performance instructions](../docs/PERFORMANCE_BASELINE.md) and [recorded verification](../docs/README.md).

For deployment:

```powershell
npm ci
npm run build
npm run db:prepare
npm start
```

Apply schema changes only through reviewed maintenance. Required index preparation reports failure. Restart the deployed service and verify readiness and role journeys.

Production requires HTTPS, protected storage, strong secrets and correct providers. Development OTP settings must not be used in production. Keep credentials and signing material out of Git.

Beta artifacts are served under /app/. Publish the [manifest](app-update/update.json) and APK together using [these instructions](app-update/README.md). Source pushes do not deploy a running service.

## Observability

Request IDs correlate API work. Route-template summaries report bounded latency percentiles without user content. API_P95_WARN_MS defaults to 1000 ms; warnings require at least 20 requests in a summary window. External collection/alerting must be configured separately.

Never log credentials, authorization headers, private addresses, photos or message bodies.
