# Recycler offers, receipt and payment

Updated 1 October 2026 for 0.1.13-beta (77).

## Offer consent

Asking rate, offered rate and total value are distinct. Applicable minimum/asking rules must be enforced on the backend, not just Android.

Optional BulkOffer.counterRatePerKg retains the original Recycler price while a counterproposal awaits explicit agreement. Collector acceptance is blocked until the Recycler submits agreed terms. Quote submission checks current lot state, expiry and authorization transactionally; accepted lots cannot be reopened by late quotes. Acceptance closes competing pending requests.

Acceptance requires connectivity. Transport failure is not success; legacy queued acceptance needs explicit online reconciliation.

## Material and money

New bulk lots save optional `fulfillmentMode`: `COLLECTOR_DELIVERY` means handover at the Recycler facility; `RECYCLER_PICKUP` means collection at the Collector location. Android requires a choice for new lots. Legacy clients/records can omit it and retain location selection. Pickup requires a pickup-enabled Recycler at offer submission and acceptance. The server enforces the chosen location during handover preparation and receipt.

1. Collector accepts agreed terms. The direct-trade QR action prepares the signed QR and submits Collector confirmation. A partially successful preparation retains a separate confirmation retry.
2. Recycler verifies the exact QR online and explicitly confirms inspected material/weight.
3. Backend commits inventory transfer, marks the lot sold, completes the offer and closes transaction chat.
4. Scanner returns to Market after confirmed receipt; shared Collector state reconciles terminal offers.
5. Records show final weight, rate, amount and payment status.
6. Recycler pays externally and records method/reference inline in Market or Orders.
7. Collector confirms receipt or reports an issue.

SupplyHandover.status = COMPLETED means material receipt. It does not mean SupplyPayment.status = VERIFIED. Recorded, verified, disputed and reversed money states remain separate.

## APIs

Paths are relative to /api/v1.

| Operation | Endpoint |
| --- | --- |
| Verify exact signed QR | POST /recycler/handovers/verify |
| Record Recycler payment | POST /recycler/handovers/:id/payment |
| Collector payment decision | POST /kabadiwala/handovers/:id/payment-confirm |

QR checks signature, nonce, expiry, recipient and current record. Reset cancels stale verification; duplicate confirmation cannot duplicate inventory transfer.

Successful Recycler verification records optional `SupplyHandover.recyclerQrScannedAt`. The Collector's QR hides after this server-confirmed scan; the marker does not transfer inventory, confirm material receipt or prove payment. Both scanner entry points use a portrait capture activity. Account-checked push events and a two-second change-feed fallback on visible journey screens reconcile remote changes.

Stable payment identities prevent duplicates. Retrying the same supported decision is safe; conflicting decisions are rejected. Financial totals remain independent of paged history.

Old accepted offers on sold lots are excluded from active actions. Backend chat guards prevent sending in terminal conversations; existing messages remain history.

Participant privacy projections protect pooled handovers, including other Collectors' QR, private source references and unrelated settlement metadata.

## Presentation and limitations

Passport means provenance records; risk flags are review signals. Neither is government certification or proof of payment. Prioritize material, final amount, payment state and the next action.

Availability and pickup-charge sections save separately. Completed records retain reference information rather than active offer controls.

Direct bulk-lot payment controls do not add individual payout entry for pooled contributors; existing pooled settlement APIs remain separate.

## Deployment and verification

Deploy additive backend contracts first, review the optional counterproposal field and prepare required indexes. Android includes Room migration 29 → 30 without destructive migration.

This beta adds nullable MongoDB fields `BulkLot.fulfillmentMode` and `SupplyHandover.recyclerQrScannedAt`; existing documents need no value backfill. Regenerate Prisma Client during backend build. Deploy the participant trade-detail and Collector handover-status endpoints before distributing the APK. APK assembly and backend compilation passed; this batch was not accepted through a live two-device journey.

Fixtures cover consent, signed receipt, inventory/offer/chat transitions, recipients, authorization, payments and retries. Live database tests exercise selected paths, not the entire journey.

Connected Android acceptance, live two-device FCM, external bank transfers and physical-device performance remain incomplete. See [verification status](README.md) and [Household settlement](PICKUP_STATE_MACHINE.md).
