# Role architecture audit — 2026-09-14

## Decision

`COLLECTOR` is the persisted technical name for **Kabadiwala**. It is not a
shared seller role. `HOUSEHOLD` and `COLLECTOR` happen to use the same profile
store during the migration, but their bearer tokens are now distinct authority
boundaries.

| Capability | Household | Kabadiwala | Recycler | Enforcement |
| --- | --- | --- | --- | --- |
| Create household material listing | Yes | No | No | `/household/*` requires `HOUSEHOLD` |
| Request and view a household pickup | Yes, own records | No | No | household ownership predicate |
| Discover/accept/schedule/complete pickup | No | Yes, assigned records | No | `/kabadiwala/*` requires `COLLECTOR` plus `kabadiwalaId` |
| Record final weight and settlement | View result | Yes, only arrived assigned pickup | No | state transition + ownership |
| Own/manage scrap inventory | No | Yes | No | inventory owner key |
| Create/cancel bulk lot | No | Yes, own stock only | No | atomic available/reserved balance update |
| Browse lots / make procurement offer | No | No | Yes, verified account | `/recycler/*` requires verified `RECYCLER` |
| Accept recycler offer | No | Lot owner only | No | lot-owner predicate |
| Mark reserved lot received | No | No | Accepted recycler only | `reservedForId` predicate |
| Create procurement demand | No | No | Yes | recycler ownership key |
| Browse open procurement demand | No | Yes | Own demand only | role-scoped endpoints |
| Compare formal route advantage | No | Yes | No | current verified Recycler and explicit estimate fields |
| Opt into/lock a cooperative pool | No | Yes, own inventory contribution | No | atomic reservation; unique demand pool |
| View supply handover/passport | Own completed listing result | Own handover/contribution evidence | Assigned formal handovers only | source/reference/party predicates |
| Confirm formal supply receipt | No | No | Yes, assigned current Recycler | signed QR + Collector confirmation + conditional transition |
| Accept/raise a formal settlement issue | No | Own contribution/handover | No | breakdown status and anomaly event |
| View collector growth passport | No | Own platform-generated passport | No | authenticated owner scope |
| View reverse-demand opportunities | No | Aggregate demand/gap only | Own demand management | no cross-collector identity disclosure |
| Legacy collector→recycler lots/quotes/handovers/payments | No | Yes | Role-specific recycler actions | household removed from collector middleware |
| Shared preferences, education, safety | Yes | Yes | Yes | authenticated account only |

## Violations found and corrected

1. `requireAuth` accepted both `HOUSEHOLD` and `COLLECTOR`. This made every
   collector route—legacy lots, quote actions, handovers, payments and
   earnings—callable by a household token. It now accepts `COLLECTOR` only.
2. Future-feature role checks treated a household as collector-equivalent.
   Legacy lot tools, rewards, reviews and transaction chat now require the
   actual kabadiwala role; generic account preferences remain shared.
3. The original model had no household pickup or kabadiwala inventory boundary.
   The new supply-chain API separates household listings, assigned pickups,
   inventory balances, bulk lots, recycler offers and procurement demand.

## State machines

- Household listing: `DRAFT → POSTED → MATCHED → COMPLETED`, or `DRAFT/POSTED → CANCELLED`.
- Pickup: `REQUESTED → ACCEPTED → SCHEDULED → IN_TRANSIT → ARRIVED → WEIGHED → COMPLETED`; an accepted pickup without a requested slot may move directly to `IN_TRANSIT`. Rejected/cancelled transitions are intentionally reserved for the cancellation API.
- Bulk lot: `DRAFT → LISTED → RESERVED → SOLD`, or `LISTED → CANCELLED`.
- Bulk offer: `PENDING → ACCEPTED → COMPLETED` (or `REJECTED`/`CANCELLED`). Receipt is the completing event and atomically settles the reserved inventory.
- Procurement requirement: `OPEN → PAUSED/FULFILLED/CANCELLED`.

## Inventory invariants

Pickup completion atomically adds final weighed stock and purchase cost to the
assigned kabadiwala. Bulk-lot creation atomically moves stock from `available`
to `reserved`, cancellation reverses that move, and recycler receipt moves the
same reserved stock to `sold`. Every mutation is owner-scoped and conditional
on the expected status; retries therefore cannot create negative or duplicate
stock movements.

## API map

`POST/GET /household/listings`, `GET /household/kabadiwalas`, and
`POST /household/listings/:id/pickups` power the Household Sell Scrap workspace.
`GET /kabadiwala/listings`, `/kabadiwala/pickups`, `/kabadiwala/inventory`,
`/kabadiwala/bulk-lots`, `/kabadiwala/bulk-offers`, and
`/kabadiwala/procurement-requirements` power the Kabadiwala collection,
inventory, aggregation, offer, and market-demand views. `GET/POST
/recycler/bulk-lots`, `/recycler/offers`, and
`/recycler/procurement-requirements` power Recycler procurement. Formalisation
adds `/kabadiwala/route-advantage`, `/kabadiwala/pool-opportunities`,
`/kabadiwala/pools/*`, `/kabadiwala/passport`, `/kabadiwala/safety/*`,
`/kabadiwala/*/prepare-handover`, `/kabadiwala/handovers/*`,
`/recycler/pools`, `/recycler/supply-handovers` and
`/recycler/handovers/confirm`. All Android
DTOs mirror these request and response shapes; loading, empty, retry, conflict,
forbidden, and expired-session states are mapped to user-facing copy.

The old lot/quote/handover screens remain available only for legacy/offline
regression coverage. The live supply-chain workspace uses the formal handover
path for new pool and bulk receipt. A persisted live account cannot cross role
boundaries, and the backend denies household or cross-role requests even when a
route or resource id is constructed manually.
