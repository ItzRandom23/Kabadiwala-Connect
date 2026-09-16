# Kabadiwala Connect Backend API Contract

Contract date: 2026-09-15
Implementation source: `backend/src/app.ts`, route modules, controllers, services and Prisma schema.

This is the backend contract for the current repository. Android must consume these contracts; Android code is intentionally out of scope.

## 1. Runtime and identity rules

- Base URL: `/api/v1`.
- `COLLECTOR` is the persisted role name for a Kabadiwala. This document calls it Kabadiwala for readability.
- Roles are `HOUSEHOLD`, `COLLECTOR`, `RECYCLER`, and `ADMIN`. A recycler must be `VERIFIED` for operational recycler endpoints. Pending, rejected, under-review, expired, revoked, or suspended recyclers may only use their own verification/profile paths where explicitly listed.
- Authentication is a bearer access token unless the endpoint is marked public. Ownership is checked server-side against the token identity; client navigation is not an authorization boundary.
- Successful responses use `{ success: true, data, message? }`. Errors use the centralized error envelope `{ success: false, error: { code, message, requestId?, details? } }`.
- Validation failures are normally `400` or `422`; unauthenticated is `401`; role/capability denial is `403`; missing or non-owned resources are intentionally collapsed to `404`; state/race/idempotency conflicts are `409`; unavailable infrastructure/configuration is `503`.
- `Idempotency-Key` is accepted on pickup creation, bulk-lot creation, pool join, formal collector/Recycler confirmation, QC, disposal evidence, pickup settlement payment, and the supported `/sync` operations. Formal handover preparation and online formal payment recording converge through unique server source keys; offline formal payment recording also converges on that same key. The header must be 8–160 characters matching `[A-Za-z0-9._:-]+`. Reusing a key with a different request body returns `409 IDEMPOTENCY_KEY_REUSED`; replaying the same operation returns the canonical prior response where the route persists a canonical response.
- All timestamps are ISO-8601 strings. Quantities are decimal numbers in the unit stated by the response. Uploads are `multipart/form-data` and capped at 5 MiB per file.

## 2. State and evidence invariants

- Household source listings are owned by the household and move through `DRAFT`, `POSTED`, `MATCHED`, `CANCELLED`, and `COMPLETED`.
- Pickup requests are owned by the household and assigned to exactly one Kabadiwala at a time. Relevant states are `REQUESTED`, `ACCEPTED`, `SCHEDULED`, `IN_TRANSIT`, `ARRIVED`, `WEIGHED`, `COMPLETED`, `CANCELLED`, `REASSIGNMENT_REQUIRED`.
- Inventory is owned by a Kabadiwala. `availableKg + reservedKg + soldKg` is never allowed to be negative or non-finite; every material movement has an `InventoryMovement` record.
- Formal supply handovers are QR/nonce protected and use `PREPARED`, `COLLECTOR_CONFIRMED`, `RECEIVED`, `REVIEW_REQUIRED`, `COMPLETED`, `EXPIRED`, and `CANCELLED` states. Material/weight/rate changes require a reason and may require manual review; terminal rows cannot be confirmed again.
- Data-bearing devices expose owner-preparation/destruction-evidence state. The backend stores traceability evidence and warnings; safe wiping/dismantling instructions and device interaction remain Android/operator responsibilities.

## 3. Health, authentication and profiles

| Method and route | Allowed roles | Request | Response and validation | Ownership/idempotency/state |
|---|---|---|---|---|
| `GET /health` | Public | None | Service/database health and configuration-safe status. | Read-only. |
| `POST /auth/request-otp` | Public | Phone number. | OTP request result; normalizes common Indian phone formats and validates input. | Rate-limited by service. |
| `POST /auth/verify-otp` | Public | Phone, OTP, optional registration/profile fields. | Access/refresh session and account identity; invalid details return validation error. | Creates or authenticates the requested account role under server rules. |
| `POST /auth/refresh` | Public with refresh token | Refresh token. | Rotated access/refresh session. | Old refresh session is revoked/rotated. |
| `POST /auth/logout` | Public with refresh token | Refresh token/session. | Revocation acknowledgement. | Idempotent session revocation. |
| `POST /auth/signup` | Public | Email, password and account fields. | Account/session result when email auth is configured. | Email-auth configuration is required; duplicate identity is rejected. |
| `POST /auth/login` | Public | Email/password. | Access/refresh session. | Account status and password are checked server-side. |
| `POST /auth/admin-login` | Public | Admin credentials. | Admin session with capability claims. | Admin account/capabilities are enforced by middleware. |
| `GET /auth/profile` | Household/Collector/Recycler | None. | Current account and role-specific profile. | Only the token identity is returned. |
| `GET /collectors/me` | Collector | None. | Own Kabadiwala profile. | Own profile only. |
| `PUT /collectors/me` | Collector | Allowed profile fields. | Updated own profile. | No role/account ownership changes. |

## 4. Legacy collector lots, prices, recyclers and quotes

| Method and route | Allowed roles | Request | Response/validation | Ownership/state |
|---|---|---|---|---|
| `POST /lots` | Collector | Material, condition, weight/source metadata. | Created legacy lot. | Collector-owned; schema and positive-weight validation. |
| `GET /lots` | Collector | Optional list filters. | Own legacy lots. | Ownership scoped to token collector. |
| `GET /lots/:lotId` | Collector | Path ID. | One own lot or `404`. | IDOR-safe. |
| `PUT /lots/:lotId` | Collector | Mutable lot fields. | Updated own lot. | State machine blocks immutable/completed changes. |
| `DELETE /lots/:lotId` | Collector | Path ID. | Cancellation result. | Own mutable lot only. |
| `GET /lots/:lotId/photo` | Collector | Path ID. | Owned photo stream/reference. | Storage/provider configuration applies. |
| `POST /lots/:lotId/photo` | Collector | One `photo` multipart file, max 5 MiB. | Photo reference and updated lot. | Own lot only; file type/size checked. |
| `GET /prices/board` | Household/Collector/Recycler | Optional material, city, area, unit. | Price ranges, trend, freshness, `sourceClassification`, observation count, confidence and `isDemoData`. | Indicative data only; source/freshness are explicit. |
| `GET /prices/history` | Household/Collector/Recycler | Optional material/location/range. | Historical validated price observations. | Read-only. |
| `GET /lots/:lotId/valuation` | Collector | Path ID. | Indicative valuation for own legacy lot. | Uses lot owner and current price/rate data. |
| `PUT /admin/prices/:priceId` | Admin + `PRICE_MANAGEMENT` | Validated price fields. | Updated price and audit result. | Admin capability and audit history required. |
| `GET /recycler/profile` | Recycler, including pending | None. | Own recycler profile/authorization state. | Self only. |
| `POST /recycler/verification-request` | Recycler, including pending | Evidence fields and future `validUntil`. | Verification request/audit state. | Future expiry required; status becomes reviewable. |
| `PATCH /recycler/profile` | Verified Recycler | Operational profile fields. | Updated own profile. | Verified-only; profile ownership enforced. |
| `PUT /recycler/rates` | Verified Recycler | Material rates/capabilities. | Updated rates. | Own rates only; audit/state validation applies. |
| `GET /recyclers` | Any authenticated account | Optional material/location filters. | Verified recycler directory. | Only public-safe fields. |
| `GET /recyclers/match` | Collector | Legacy lot/matching query. | Ranked recycler matches with score, reasons and `whyThisMatch`. | Matching excludes unauthorized recyclers. |
| `GET /recyclers/:recyclerId` | Any authenticated account | Path ID. | Public-safe recycler detail. | Evidence/contact internals are not exposed. |
| `GET /admin/recyclers` | Admin + `RECYCLER_REVIEW` | Optional review filters. | Verification queue. | Admin capability required. |
| `GET /admin/recyclers/:recyclerId` | Admin + `RECYCLER_REVIEW` | Path ID. | Review detail/evidence metadata. | Admin-only. |
| `PUT /admin/recyclers/:recyclerId/authorization` | Admin + `RECYCLER_AUTHORIZATION` | Authorization status/evidence and future validity. | Updated authorization state. | Only valid lifecycle states; verified/expiry checks are enforced and audited. |
| `POST /quotes/request` | Collector | Own lot and target recycler. | Created quote request. | Lot ownership and recycler eligibility checked. |
| `POST /quotes/request-batch` | Collector | Own lot and target recycler list. | Created requests and per-target results. | Duplicate/invalid targets are rejected without crossing ownership. |
| `GET /quotes/pending` | Collector | Optional filters. | Own pending requests. | Own collector only. |
| `GET /quotes/:quoteId` | Collector | Path ID. | Own quote/request. | IDOR-safe. |
| `POST /quotes/:quoteId/accept` | Collector | Optional acceptance evidence. | Accepted quote and lot state. | Own quote; conditional state transition prevents double acceptance. |
| `POST /quotes/:quoteId/reject` | Collector | Optional reason. | Rejected quote. | Own quote and valid state only. |
| `GET /recycler/quote-requests` | Verified Recycler | Optional filters. | Requests addressed to the recycler. | Target recycler ownership enforced. |
| `GET /recycler/quote-requests/:requestId` | Verified Recycler | Path ID. | One addressed request. | Target recycler only. |
| `POST /recycler/quotes` | Verified Recycler | Request ID, rate, validity and terms. | Submitted quote. | Duplicate/expired/state-invalid submissions return conflict. |

## 5. Legacy handover, payment, sync and reporting

| Method and route | Allowed roles | Request | Response/validation | Ownership/idempotency/state |
|---|---|---|---|---|
| `POST /verify/handover` | Public | Signed QR/reference payload. | Safe verification projection. | Signature, expiry and nonce rules apply; no private fields. |
| `POST /handovers` | Collector | Own lot/recycler and handover fields. | Created legacy handover. | Collector/participant ownership checked. |
| `GET /handovers/reference/:referenceId` | Collector participant | Reference ID. | Own matching handover. | Non-participants receive `404`. |
| `GET /handovers/:handoverId` | Collector participant | Path ID. | Own handover. | IDOR-safe. |
| `POST /handovers/:handoverId/mark-handed-over` | Collector | Optional evidence. | Updated handover. | Collector participant and valid state only. |
| `PUT /handovers/:handoverId/evidence` | Collector | Evidence JSON. | Updated evidence. | Ownership and evidence validation. |
| `POST /handovers/:handoverId/evidence/photo` | Collector | One `photo` multipart file, max 5 MiB. | Evidence reference. | Ownership/storage validation. |
| `POST /handovers/:handoverId/dispute` | Collector | Structured dispute type/notes/evidence. | Open dispute. | Participant only; duplicate terminal disputes rejected. |
| `GET /recycler/handovers` | Verified Recycler | Optional filters. | Incoming handovers. | Recycler target only. |
| `GET /recycler/handovers/:handoverId` | Verified Recycler | Path ID. | One incoming handover. | Target ownership only. |
| `POST /recycler/handovers/:handoverId/confirm` | Verified Recycler | Actual weight/material/evidence. | Confirmed or review-required handover. | State transition and variance rules prevent double confirmation. |
| `POST /recycler/handovers/:handoverId/reject` | Verified Recycler | Reason/evidence. | Rejected handover. | Target ownership and valid state. |
| `GET /admin/disputes` | Admin + `DISPUTE_RESOLUTION` | Optional status filters. | Dispute queue. | Admin capability. |
| `GET /admin/disputes/:disputeId` | Admin + `DISPUTE_RESOLUTION` | Path ID. | Dispute detail. | Admin-only. |
| `POST /admin/disputes/:disputeId/resolve` | Admin + `DISPUTE_RESOLUTION` | Resolution, reason and evidence. | Resolved dispute/audit. | Terminal transition is conditional. |
| `POST /payments/record` | Collector | Own lot/handover, amount, method, reference. | Recorded payment. | Positive amount and relationship validation; collector-owned. |
| `GET /payments` | Collector | Optional filters. | Own payments. | Ownership scoped. |
| `GET /payments/:paymentId` | Collector | Path ID. | Own payment. | IDOR-safe. |
| `PUT /payments/:paymentId` | Collector | Mutable payment/evidence fields. | Updated payment. | Own mutable payment only; anomaly/audit rules apply. |
| `POST /payments/:paymentId/dispute` | Collector | Dispute type/notes/evidence. | Payment dispute. | Own payment only. |
| `GET /earnings/ledger` | Collector | Optional range. | Own earnings ledger. | Read-only projection. |
| `GET /admin/payments` | Admin + `PAYMENT_VERIFICATION` | Optional filters. | Payment review queue. | Admin capability. |
| `GET /admin/payments/:paymentId` | Admin + `PAYMENT_VERIFICATION` | Path ID. | Payment detail/audits. | Admin-only. |
| `POST /admin/payments/:paymentId/verify` | Admin + `PAYMENT_VERIFICATION` | Verification decision/evidence. | Verified/disputed payment. | Conditional terminal transition and audit. |
| `POST /sync` | Collector/Verified Recycler | Queued operation batch with client IDs/timestamps. Supported formal actions include pool contribution/release, settlement decision, collector/Recycler handover confirmation, disposal evidence, and formal payment. | Per-operation applied/rejected/conflict/invalid result. | Role-aware offline queue replay; server validation, QR/nonce verification, ownership and state transitions still apply. |
| `GET /sync/changes` | Collector/Verified Recycler | Preferred opaque `cursor` returned by the previous response; legacy `since=<ISO timestamp>` remains accepted for compatibility. | Role-scoped deltas including legacy records, formal handovers, QC/reversals, pickup settlement payments, pool settlements, anomalies and passport events, plus `nextCursor`. | Cursor is read-only, per-feed timestamp+ID ordered, and cannot be combined with `since`; no unrelated account rows. |
| `GET /transactions/:lotId/timeline` | Collector | Path legacy lot ID. | Ordered lot/quote/handover/payment timeline. | Own lot only; source-to-supply passport is separate. |
| `GET /activity/changes` | Household/Collector/Recycler | Optional ISO `since`. | Own activity delta. | Invalid cursor is `400 INVALID_ACTIVITY_CURSOR`. |
| `GET /notifications` | Household/Collector/Recycler | `unread=true`, bounded `limit`. | Own notifications. | Account-scoped; limit is server-bounded. |
| `GET /notifications/unread-count` | Household/Collector/Recycler | None. | `{ count }`. | Own account only. |
| `POST /notifications/:notificationId/read` | Household/Collector/Recycler | Path ID. | `{ marked }`. | Own notification; repeated read is safe. |
| `POST /notifications/read-all` | Household/Collector/Recycler | None. | Count marked. | Own account only. |

`POST /sync` operation contract:

| Operation | Allowed role | `entityId` | Required payload/action | Server effect |
|---|---|---|---|---|
| `CREATE/LOT` | Collector | Client lot ID | Legacy lot fields | Creates one owned lot; retries converge by operation ID and lot ID. |
| `UPDATE/LOT` | Collector | Lot ID | `clientVersion`, mutable lot fields | Conditional versioned update; stale versions return a conflict. |
| `CREATE/PAYMENT` | Collector | Client payment ID | Legacy payment fields | Records one collector payment; retry ledger is authoritative. |
| `CREATE/POOL_CONTRIBUTION` | Collector | Client contribution ID | `poolId`, `quantityKg`, `grade`, optional `sourceListingIds`, optional expected rate | Rechecks current Recycler authorization/capability, reserves owned inventory, updates threshold, writes audit/passport event. |
| `UPDATE/POOL_CONTRIBUTION` | Collector | Contribution ID | `action: RELEASE` | Releases only the caller's reserved contribution while the pool is still forming. |
| `UPDATE/POOL_SETTLEMENT` | Collector | Contribution ID | `action: SETTLEMENT_DECISION`, `decision: ACCEPT\|RAISE_ISSUE`, reason for issue | Conditionally accepts/disputes one contributor settlement; acceptance moves reserved inventory exactly once. |
| `UPDATE/SUPPLY_HANDOVER` | Collector | Handover ID | `action: COLLECTOR_CONFIRM` | Conditionally confirms the collector side. |
| `UPDATE/SUPPLY_HANDOVER` | Verified Recycler | Handover ID | `action: RECYCLER_CONFIRM`, signed `qrCodeData`, optional actual/accepted weight, rate, material match and reason | Verifies QR/nonce/current authorization, applies variance guard, settles or marks review, and updates pooled/bulk state atomically. |
| `UPDATE/SUPPLY_HANDOVER` | Verified Recycler | Handover ID | `action: DISPOSAL_EVIDENCE`, evidence reference/method, `deviceDataDestroyed: true` | Conditionally records evidence for a completed data-bearing handover and updates linked household listings. |
| `CREATE/SUPPLY_PAYMENT` | Verified Recycler | Client payment ID | `handoverId`, amount, method, optional contributor/reference/notes | Records one formal payment under the same deterministic handover/contributor source key as the online API. |

Every operation is hashed over operation type, entity type, entity ID and payload and stored in `SyncOperation` under the authenticated account identity. Reusing an operation ID with a different payload returns `SYNC_PAYLOAD_MISMATCH`; a valid replay returns `ALREADY_APPLIED`.

## 6. Household source, pickup and data-safe disposal contract

| Method and route | Allowed roles | Request | Response/validation | Ownership/idempotency/state |
|---|---|---|---|---|
| `POST /household/listings` | Household | Material, estimated weight, condition, area, optional price/photo and `dataBearingDevice`, `ownerPreparationCompleted`, `dataDestructionRequested`. | Created listing with `destructionEvidenceStatus`. | Household-owned; positive weight, bounded strings/coordinates, price ordering and disposal-field consistency enforced. |
| `GET /household/listings` | Household | None. | Own listings. | Own household only. |
| `GET /household/listings/:listingId` | Household | Path ID. | Listing plus own pickup projections. | IDOR-safe. |
| `PATCH /household/listings/:listingId` | Household | Any editable listing fields. | Updated listing and evidence state. | Only `DRAFT`/`POSTED`; revalidates merged record. |
| `GET /household/listings/:listingId/passport` | Household | Path ID. | Listing, pickup IDs, passport events, inventory movements and disclaimer. | Own source only; evidence is not a government certificate. |
| `POST /household/listings/:listingId/cancel` | Household | Optional reason. | Success acknowledgement. | Only open listing; active pickups are cancelled in the same transaction and reliability markers are written. |
| `GET /household/kabadiwalas` | Household | Optional `latitude`, `longitude`, `radiusKm` (coordinates must be supplied together). | Active Kabadiwala public-safe profiles, optionally distance-filtered. | No private contact/evidence fields; radius is server-enforced. |
| `POST /household/listings/:listingId/pickups` | Household | `kabadiwalaId`, optional ISO `requestedSlot`. | Pickup request (`201`) or canonical existing/replayed request (`200`). | Listing ownership, active collector, single-winner claim and optional request-hash idempotency. |
| `GET /household/pickups` | Household | None. | Own pickup requests with reliability/settlement fields. | Own household only. |
| `GET /household/pickups/:pickupId` | Household | Path ID. | Pickup plus source listing. | IDOR-safe. |
| `GET /household/pickups/:pickupId/passport` | Household | Path ID. | Pickup, source listing, ordered passport events, inventory movements and disclaimer. | Own pickup only. |
| `POST /household/pickups/:pickupId/reschedule` | Household | New ISO `scheduledSlot`. | Updated pickup. | Only request/accepted/scheduled/reassignment states; resets reassignment reason. |
| `POST /household/pickups/:pickupId/settlement` | Household | `decision: ACCEPT|RAISE_ISSUE`, required `reasonCode` for issue, optional evidence/notes and `Idempotency-Key`. | Settlement decision and updated pickup. | Only completed pickup pending household confirmation; conditional update prevents duplicate decisions and creates anomaly evidence for issues. |
| `POST /household/pickups/:pickupId/cancel` | Household | Optional reason. | Success acknowledgement. | Eligible pre-completion states only; late cancellation is classified. |
| `GET /household/pickups/:pickupId/reassignment-options` | Household | Path ID. | Active replacement Kabadiwala options with privacy-safe profile and distance when coordinates exist. | Own `REASSIGNMENT_REQUIRED` pickup only; explicit household selection remains a client action. |
| `POST /household/pickups/:pickupId/reassign` | Household | `kabadiwalaId`, optional `requestedSlot`, optional `Idempotency-Key`. | New `REQUESTED` pickup assigned to the selected Kabadiwala. | Own `REASSIGNMENT_REQUIRED` pickup only; conditional transition, prior-request reuse, and collector notification are server-side. |

## 7. Kabadiwala pickup, inventory and bulk-lot contract

| Method and route | Allowed roles | Request | Response/validation | Ownership/state |
|---|---|---|---|---|
| `GET /kabadiwala/listings` | Collector | None. | Open listings plus listings assigned to this Kabadiwala. | No hidden household-private fields; max 100 rows. |
| `GET /kabadiwala/pickups` | Collector | None. | Own assigned pickups and lifecycle timestamps. | Kabadiwala ownership only. |
| `POST /kabadiwala/listings/:listingId/accept` | Collector | Path ID. | Accepted pickup. | Assigned request only; conditional `REQUESTED → ACCEPTED`. |
| `POST /kabadiwala/pickups/:pickupId/reject` | Collector | Optional reason. | Rejected/cancelled request. | Assigned request only; listing can reopen. |
| `POST /kabadiwala/pickups/:pickupId/confirm-availability` | Collector | Optional availability note/slot. | Availability-confirmed pickup. | Assigned request only; writes availability timestamp. |
| `POST /kabadiwala/pickups/:pickupId/schedule` | Collector | Scheduled ISO slot. | Scheduled pickup. | Assigned accepted/reassignment request only. |
| `POST /kabadiwala/pickups/:pickupId/cancel` | Collector | Optional reason. | Cancelled pickup. | Assigned pre-completion pickup; reason/audit/reliability fields. |
| `POST /kabadiwala/pickups/:pickupId/reassign` | Collector | Reason and optional `noShow`. | `REASSIGNMENT_REQUIRED` pickup and reopened listing. | Assigned pickup only; no arbitrary collector transfer. |
| `POST /kabadiwala/pickups/:pickupId/status` | Collector | `IN_TRANSIT` or `ARRIVED`. | Updated pickup. | Assigned state transition only; timestamps are server time. |
| `POST /kabadiwala/pickups/:pickupId/complete` | Collector | Actual weight, final category/grade/rate, optional reason/evidence. | Completed pickup, settlement breakdown and inventory update. | Only `ARRIVED`; material/weight/value changes require reason; exactly-once conditional claim and inventory movement transaction. |
| `POST /kabadiwala/pickups/:pickupId/settlement-payment` | Collector owner | Positive amount not above accepted settlement, `method`, optional recorded date/reference/notes. | Pickup settlement payment with `VERIFIED` or `DISPUTED` status. | Only completed, household-accepted/disputed settlement; unique pickup source key and request hash prevent duplicate or conflicting replay; underpayment creates an anomaly and exact payment completes settlement. |
| `GET /kabadiwala/inventory` | Collector | Optional material/grade. | Own balances, `ownedKg`, invariant status. | Never exposes another collector’s balance. |
| `GET /kabadiwala/inventory/movements` | Collector | Optional material/cursor/limit. | Own append-only inventory movements. | Ownership scoped; movements are created transactionally with source actions. |
| `POST /kabadiwala/bulk-lots` | Collector | Material, grade, quantity, asking/minimum rate, area and optional coordinates/notes. | Reserved bulk lot. | Quantity cannot exceed available inventory; balance and movement are atomic. |
| `GET /kabadiwala/bulk-lots` | Collector | None. | Own bulk lots. | Ownership scoped. |
| `POST /kabadiwala/bulk-lots/:lotId/cancel` | Collector | Optional reason. | Cancelled lot/released inventory. | Owner and pre-lock state only; movement/audit recorded. |
| `GET /kabadiwala/bulk-offers` | Collector | None. | Offers on own bulk lots. | Lot ownership filter. |
| `POST /kabadiwala/bulk-offers/:offerId/reject` | Collector | Optional reason. | Rejected offer. | Lot owner only. |
| `POST /kabadiwala/bulk-offers/:offerId/counter` | Collector | Counter rate/terms. | Countered offer. | Lot owner and valid offer state only. |
| `POST /kabadiwala/bulk-offers/:offerId/accept` | Collector | Optional acceptance evidence. | Accepted offer/lot state. | Lot owner; conditional state transition. |

## 8. Recycler demand, offers, pooling and formal supply handovers

| Method and route | Allowed roles | Request | Response/validation | Ownership/state/idempotency |
|---|---|---|---|---|
| `GET /recycler/bulk-lots` | Verified Recycler | Optional material/area filters. | Eligible bulk lots. | Verified recycler only; private collector fields excluded. |
| `GET /recycler/bulk-lots/:lotId` | Verified Recycler | Path ID. | Bulk-lot detail safe for recycler review. | No unauthorized inventory disclosure. |
| `POST /recycler/bulk-lots/:lotId/offers` | Verified Recycler | Rate/terms/evidence. | Created or updated offer. | Target lot and recycler eligibility checked. |
| `GET /recycler/offers` | Verified Recycler | None. | Own offers. | Recycler-owned. |
| `POST /recycler/offers/:offerId/withdraw` | Verified Recycler | Optional reason. | Withdrawn offer. | Own mutable offer only. |
| `POST /recycler/bulk-lots/:lotId/receive` | Verified Recycler | Legacy receive payload. | `409` compatibility response directing the client to signed formal handover confirmation. | Kept as an explicit non-silent migration guard; it does not bypass QR/nonce/evidence rules. |
| `POST /recycler/procurement-requirements` | Verified Recycler | Material, required quantity, min lot, location/deadline and capability fields. | Created demand requirement. | Recycler-owned, validated positive quantities and deadline. |
| `PATCH /recycler/procurement-requirements/:requirementId` | Verified Recycler | Demand fields plus lifecycle status. | Updated/paused/resumed/cancelled demand. | Own requirement only; min lot cannot exceed required quantity. |
| `GET /recycler/procurement-requirements` | Verified Recycler | Optional lifecycle filters. | Own demand. | Recycler-owned. |
| `GET /kabadiwala/procurement-requirements` | Collector | Optional material/location filters. | Open privacy-safe demand. | No recycler private data. |
| `GET /kabadiwala/route-advantage` | Collector | Material/location/quantity/grade query. | Route economics: baseline range, confidence, demand relevance, grade/weight/minimum-lot and distance eligibility, configured logistics, payment-reliability proxy, platform fee, gross/net estimates, reasons and `whyThisMatch`. | Deterministic read-only estimate; does not promise a price or payout. Missing coordinates cannot be marked eligible. |
| `GET /kabadiwala/pool-opportunities` | Collector | Optional material/location query. | Demand-linked pooling opportunities. | Aggregate/read-only; no contributor identity leakage. |
| `GET /kabadiwala/pools/suggestions` | Collector | None. | Aggregate demand suggestions with required/eligible kg, contributor estimate, supply gap and deadline. | Privacy-safe, no other collector quantities. |
| `POST /kabadiwala/pools` | Collector | Demand ID/material/target quantity/deadline. | Created pool. | Creator-owned; canonical demand/material key. |
| `GET /kabadiwala/pools` | Collector | None. | Pools relevant to this collector with own contribution and aggregate counts. | Other contributors’ individual quantities are hidden. |
| `POST /kabadiwala/pools/:poolId/join` | Collector | Contribution kg. | Reserved contribution. | Own available inventory only; movement and reservation are atomic. |
| `POST /kabadiwala/pools/:poolId/leave` | Collector | None. | Released contribution. | Own contribution only; movement and state are atomic. |
| `POST /kabadiwala/pools/:poolId/lock` | Pool creator | None. | Locked pool when threshold/state allows. | Creator-only conditional transition. |
| `GET /recycler/pools` | Verified Recycler | Optional demand/status filters. | Targeted pools and aggregate settlement state. | Recycler target only. |
| `GET /kabadiwala/demand-intelligence` | Collector | Optional material/location. | Active demand, verified recycler count, known supply, gap, deadline and pool opportunity. | Real database aggregation; `isDemo: false`; no private identity data. |
| `GET /kabadiwala/passport` | Collector | None. | Growth passport with active-since, pickup reliability, completion/cancellation/no-show rates, dispute ratio, feedback and formal handover evidence. | Own derived projection; values are evidence-based and not a government credential. |
| `GET /safety-routing` | Collector | Optional material category/condition. | Hazard level, warning code, recommended routing, required recycler capability and guidance ID. | Guidance is safety metadata, not dismantling instruction. |
| `GET /kabadiwala/safety` | Collector | None. | Versioned safety modules/progress. | Own acknowledgements. |
| `POST /kabadiwala/safety/:moduleKey/acknowledge` | Collector | Optional module version. | Updated acknowledgement. | Only known module keys; own progress. |
| `POST /kabadiwala/pools/:poolId/prepare-handover` | Pool creator | Optional expiry/route metadata. | Signed QR handover and public reference. | Pool state/creator/threshold checked; QR nonce is persisted hashed. |
| `POST /kabadiwala/bulk-lots/:lotId/prepare-handover` | Collector lot owner | Optional expiry/route metadata. | Signed QR handover and public reference. | Lot ownership and lock/state checked. |
| `POST /kabadiwala/handovers/:handoverId/collector-confirm` | Collector contributor/owner | Optional `Idempotency-Key`, evidence/reference. | Canonical collector-confirmed handover. | Contributor/owner authorization, request-hash replay protection and transactionally recorded audit/passport movement. |
| `GET /kabadiwala/handovers` | Collector | Optional status/cursor. | Own/member supply handovers. | Contributor/owner scoped. |
| `GET /recycler/supply-handovers` | Verified Recycler | Optional status/cursor. | Incoming formal handovers. | Target recycler scoped. |
| `POST /recycler/handovers/confirm` | Verified Recycler | Handover ID/reference, QR nonce/signature, accepted weight/material/rate, evidence, optional `Idempotency-Key`, reason for variance. | Confirmed/review-required handover and settlement. | QR hash/expiry/target/replay/state/quantity checks; request-hash conflict is `409`; material/weight/rate variance produces rule-coded anomaly. |
| `POST /recycler/handovers/:handoverId/disposal-evidence` | Verified Recycler | Destruction status, evidence reference/hash, method and optional notes. | Updated disposal-evidence state and passport event. | Target Recycler and data-bearing handover only; evidence updates are idempotent and locked after terminal confirmation. |
| `POST /recycler/handovers/:handoverId/qc` | Verified Recycler | `decision: PASS|FAIL`, required notes, optional evidence and `Idempotency-Key`. | QC status and updated handover; FAIL moves the handover/pool to review and creates a high-severity anomaly. | Receiving Recycler only; completed/received/review handovers, conditional `PENDING → PASSED|FAILED` transition. |
| `POST /recycler/handovers/:handoverId/payment` | Verified Recycler | Amount, method, reference and optional `Idempotency-Key`. | Formal payment record and payment anomaly result. | Target Recycler, positive amount, one payment per handover/recipient; this records backend state and does not call a bank/provider. |
| `GET /kabadiwala/handovers/:handoverId/payments` | Collector contributor/owner | Path ID. | Owner-scoped formal payments. | Contributor/owner access only. |
| `POST /kabadiwala/handovers/:handoverId/payment-confirm` | Collector contributor/owner | Confirm/dispute decision, reason/evidence. | Payment confirmation/dispute and anomaly state. | Owner/contributor scoped; conditional update prevents duplicate decisions. |
| `POST /kabadiwala/handovers/:handoverId/settlement` | Collector contributor/owner | Accept/raise issue, reason/evidence/notes. | Settlement state. | Ownership, review state and conditional transition; dispute evidence is append-only. |
| `GET /kabadiwala/handovers/:handoverId/passport` | Collector contributor/owner | Path ID. | Formal material passport and event history. | Own contribution/owner only. |
| `GET /kabadiwala/handovers/:handoverId/anomalies` | Collector contributor/owner | Path ID. | Deterministic anomaly flags and aggregate risk level. | Own handover only; no internal reviewer-only data. |
| `GET /admin/formal-anomalies` | Admin + `DISPUTE_RESOLUTION` | Optional status/type filters. | Formal anomaly review queue. | Admin capability and account status are revalidated server-side. |
| `POST /admin/formal-anomalies/:flagId/resolve` | Admin + `DISPUTE_RESOLUTION` | `action: ACCEPT_AS_RECORDED|REVERT_TO_QUOTE|RELEASE_RESERVATION|ACKNOWLEDGE`, resolution and optional evidence. | Audited resolution plus internal settlement/reservation/payment state result. | Conditional, linked-record resolution; external bank/cash reversal is not performed by this API. |
| `POST /admin/formal-payments/:paymentId/reverse` | Admin + `PAYMENT_VERIFICATION` | Provider, external reference, evidence reference and reason. | Provider-neutral reversal record and `REVERSED` internal payment status. | Idempotent by payment/provider/reference; this endpoint records externally confirmed evidence and does not call a bank, wallet, or cash provider. |

## 9. Future/secondary capabilities

All routes below are mounted under `/api/v1/future` and require an authenticated account. They are secondary product capabilities; they do not override the core ownership/state rules.

| Method and route | Allowed roles | Request | Response/validation and ownership |
|---|---|---|---|
| `GET/PATCH /future/preferences` | Any account | Read or language/appearance fields. | Own preferences; validated bounded values. |
| `GET /future/rewards` | Collector | Optional range. | Own derived reward ledger. |
| `GET /future/schemes` | Any account | Optional filters. | Scheme catalogue. |
| `POST /future/schemes/check` | Any account | Eligibility inputs. | Eligibility result. |
| `GET /future/activities`, `GET /future/activities/:slug` | Any account | Optional slug. | Content/activity projection; content is not a disposal authorization. |
| `POST /future/lots/description-suggestion` | Collector | Lot description/material context. | Non-authoritative deterministic/AI suggestion. |
| `POST /future/lots/material-suggestion` | Collector | One `photo` multipart file, max 5 MiB. | Non-authoritative material suggestion. |
| `GET /future/recyclers/:recyclerId/reviews` | Authenticated account | Recycler ID. | Public-safe verified reviews. |
| `POST /future/reviews` | Authorized transaction participant | Recycler/rating/comment context. | Created review; ownership/participant validation. |
| `GET/POST /future/conversations` | Collector/Recycler participants | Conversation target or filters. | Private conversations only for participants. |
| `GET/POST /future/conversations/:conversationId/messages` | Conversation participants | Message text/evidence. | Messages scoped to conversation participants. |
| `POST /future/conversations/:conversationId/draft-reply` | Conversation participants | Context/request for a draft. | Draft only; never auto-sent. |
| `POST /future/lots/:lotId/repeat` | Collector | Own completed lot ID. | New copied lot. | Ownership and repeatable state checked. |
| `GET /future/disputes/analytics` | Collector/Recycler | Optional range. | Own dispute analytics. | No cross-account aggregation. |

## 10. Admin dataset endpoints

| Method and route | Allowed roles | Request | Response/validation |
|---|---|---|---|
| `POST /admin/datasets/prices/import` | Admin + `DATASET_EXPORT` | 1–500 validated price rows with source/effective timestamps. | Imported IDs/count; rows are validated and price history is appended. |
| `GET /admin/datasets/export` | Admin + `DATASET_EXPORT` | Optional ISO `from`; configured export salt required. | Schema-versioned pseudonymized operational dataset; direct contact/exact-location/evidence secrets are excluded. |

## 11. Android implementation required

The backend exposes the data and state needed for these client responsibilities; none are implemented here:

- Room/WorkManager offline queue, sync retry UX and conflict presentation for `/sync` and idempotent handover/pickup operations.
- QR scanning/display, nonce transport and dual-confirmation screens for the formal handover contract.
- Hindi/regional-language layouts, low-literacy copy, text-to-speech, accessibility controls and the “Find another Kabadiwala” action after `REASSIGNMENT_REQUIRED`.
- Camera/scale/device preparation UI and safe owner-confirmation flows. The backend only stores evidence/state and safety-routing identifiers.
- Maps/navigation and live route rendering. `/kabadiwala/route-advantage` and `/safety-routing` are contract inputs, not a navigation SDK.

## 12. Compatibility and deprecation notes

- Existing legacy lot/quote/handover/payment paths remain available. The newer household/pickup/inventory/pool/supply-handover path is additive and has separate ownership semantics.
- `/recycler/bulk-lots/:lotId/receive` remains as an explicit compatibility guard and must not be used as a bypass for signed formal handover confirmation.
- No production deployment, database push, destructive data migration, or Android change is part of this contract.
