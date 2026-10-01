# Household pickup and settlement state contract

Updated **1 October 2026**, accompanying Android **0.1.13-beta (77)**. API paths below are relative to `/api/v1`.

Household pickup hours are **7:30 AM–9:30 PM, Asia/Kolkata**. Scheduling also checks minimum lead time, horizon, slot interval and capacity; being within the shift alone does not guarantee a valid slot.

The backend pickup row is authoritative. Android may show a provisional pending action, but it must replace that state with the server result or roll it back on failure. A pickup's physical status, settlement status, and payment status are separate dimensions. In particular, `PickupStatus.COMPLETED` means the material was weighed and added to Collector inventory; it does **not** mean the Household has received payment.

## Physical pickup

| From → to | Actor and API | Preconditions and effect | Retry/offline | Household / Collector view | Notification |
| --- | --- | --- | --- | --- | --- |
| POSTED listing → WAITING_FOR_PICKUP | Household `POST /household/listings/:id/pickups` | Open marketplace request; no Collector assigned | Online; first-wins acceptance stays server-authoritative | Waiting / available nearby | Nearby Collector event, where configured |
| POSTED listing → REQUESTED | Household same API with selected Collector | Assigns request to selected Collector | Online | Requested / request in queue | `PICKUP_REQUESTED` to Collector; `PICKUP_REQUEST_SENT` to Household |
| WAITING_FOR_PICKUP or REQUESTED → ACCEPTED | Collector `POST /kabadiwala/listings/:id/accept` | Conditional database update locks the request to the first eligible Collector; computes configured pickup charge | Online; 409 means another Collector won; never queue offline | Assigned / accepted | `PICKUP_ACCEPTED` to Household |
| REQUESTED → REJECTED | Selected Collector `POST /kabadiwala/pickups/:id/reject` | Releases matched listing | Online | May request again / removed from queue | No explicit event currently |
| ACCEPTED → SCHEDULED | Collector `POST /kabadiwala/pickups/:id/schedule` or confirm availability with a slot | Valid working slot and capacity | Online; server validates slot | Scheduled time / scheduled time | `PICKUP_SCHEDULED` to Household |
| ACCEPTED or SCHEDULED → IN_TRANSIT | Collector `POST /kabadiwala/pickups/:id/status` | Pickup shift must be open | Online; no offline queue | Collector en route / trip active | `PICKUP_IN_TRANSIT` to Household |
| IN_TRANSIT → ARRIVED | Collector same status API | Only assigned Collector may mark arrival | Online | QR becomes available / scan QR | `PICKUP_ARRIVED` to Household |
| ARRIVED → QR scanned | Collector `POST /kabadiwala/pickups/:id/confirm-household-qr` | Valid short-lived QR for this Household and pickup; duplicate scan is safe | Online; replay is idempotent | Handover confirmed / ready to weigh | No explicit event currently |
| ARRIVED with QR → WEIGHED → COMPLETED | Collector `POST /kabadiwala/pickups/:id/complete` | Actual weight, category and rate; transaction records final amount and inventory movement; chat closes | Online with operation key; no offline queue | Review final amount / inventory added | `PICKUP_WEIGHED` to Household |
| Eligible active request → CANCELLED | Household or Collector cancel API | Household may cancel WAITING_FOR_PICKUP, REQUESTED, ACCEPTED, SCHEDULED or REASSIGNMENT_REQUIRED; actor-specific guards apply. Releases schedule/listing as applicable; reason and late cancellation tracked | Online | Cancelled / cancelled | No explicit event currently |
| ACCEPTED through ARRIVED → REASSIGNMENT_REQUIRED | Collector `POST /kabadiwala/pickups/:id/reassign` | Releases capacity and prompts Household to choose another Collector | Online | Choose another Collector / closed assignment | `PICKUP_REASSIGNMENT_REQUIRED` to Household |
| REASSIGNMENT_REQUIRED → REQUESTED | Household `POST /household/pickups/:id/reassign` | Clears old trip, QR, amount and settlement fields | Online | New request / available if chosen | New Collector notification, where configured |

The cancellation and reassignment paths reverse an **assignment**, not a completed inventory or payment transfer. `COMPLETED` has no ordinary reverse transition.

## Settlement and payment after physical completion

| From → to | Actor and API | Preconditions and money effect | Retry/offline | Household / Collector view | Notification |
| --- | --- | --- | --- | --- | --- |
| null → PENDING_HOUSEHOLD_CONFIRMATION | Collector completes pickup | Final amount = material amount minus applicable pickup charge; no payment asserted | Online | Review amount / await Household | No explicit event currently |
| PENDING → ACCEPTED or DISPUTED | Household `POST /household/pickups/:id/settlement` | Accept final amount or supply dispute reason | Online; server-authoritative | Collect accepted amount or dispute / await decision | No explicit event currently |
| ACCEPTED → payment RECORDED | Collector `POST /kabadiwala/pickups/:id/settlement-payment` | Records claimed payment and method; mismatch moves settlement to DISPUTED | Online; replay guarded by operation identity | Confirm money received / payment recorded | `PICKUP_PAYMENT_RECORDED` or `PICKUP_PAYMENT_DISPUTED` to Household |
| payment RECORDED → household received timestamp | Household `POST /household/pickups/:id/payment-received` | Confirms actual receipt for the agreed amount; repeated confirmation returns the existing receipt | Online; never queue or optimistically finalize | Receipt acknowledged / await reconciliation | `PICKUP_PAYMENT_RECEIVED` to Collector |
| payment RECORDED → VERIFIED and settlement COMPLETED | Admin `POST /admin/household-pickup-payments/:id/reconcile` with VERIFY | Requires Household receipt; audit record | Online; final server decision | Paid and completed / reconciled | `PICKUP_PAYMENT_RECONCILED` to each account with role-specific route |
| payment RECORDED → DISPUTED | Admin same API with DISPUTE | Reason required; anomaly tracked | Online | Needs review / needs review | `PICKUP_PAYMENT_DISPUTED` to each account with role-specific route |

## Cross-role invariants

- Household QR display dismisses after the authoritative pickup reports `householdQrScannedAt`; the Collector scanner uses portrait capture. This does not skip weighing or payment confirmation.
- Household-confirmed payment receipt is shown on customer cards immediately after reconciliation of the returned record. Admin reconciliation remains an internal audit state and is not shown as unfinished customer work.

- Every write checks the authenticated actor and the pickup's current state. Conditional updates are required for competing acceptances and other races.
- Push recipient account ID must equal the stored notification recipient and the device's active account when displayed. A stale push is suppressed after logout or account switching.
- Android must not treat a provisional UI state as payment, QR, inventory, or verified completion. Those states require a confirmed server response.
- Notifications without an explicit event above are a current product gap; clients should derive current state from the authoritative pickup on refresh.
- Recycler bulk trade begins after Collector inventory exists. Bulk lot reservation, handover and payment have their own state models and must not alter this Household settlement state.

## Verification and rollout

Conditional acceptance and selected retry/state-transition paths have backend regression coverage and isolated live database checks. Full cross-role emulator acceptance, real payment evidence and representative-device performance remain incomplete. See the [current verification record](README.md).

Deploy backend validation before dependent Android changes. The [Recycler contract](recycler-receipt-payment.md) describes the later inventory-to-buyer journey; [live state reconciliation](live-screen-state.md) describes how confirmed changes propagate across tabs.
