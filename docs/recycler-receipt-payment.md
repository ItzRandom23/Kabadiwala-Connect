# Recycler offers, receipt and payment

Updated 30 September 2026.

## Offer consent

Asking rate, offered rate and total value are distinct. Applicable minimum/asking rules must be enforced on the backend, not just Android.

Optional BulkOffer.counterRatePerKg retains the original Recycler price while a counterproposal awaits explicit agreement. Collector acceptance is blocked until the Recycler submits agreed terms. Quote submission checks current lot state, expiry and authorization transactionally; accepted lots cannot be reopened by late quotes. Acceptance closes competing pending requests.

Acceptance requires connectivity. Transport failure is not success; legacy queued acceptance needs explicit online reconciliation.

## Material and money

1. Collector accepts agreed terms, prepares the signed QR and confirms their side.
2. Recycler verifies the exact QR online and explicitly confirms inspected material/weight.
3. Backend commits inventory transfer, marks the lot sold, completes the offer and closes transaction chat.
4. Scanner returns to Market after confirmed receipt; shared Collector state reconciles terminal offers.
5. Records show final weight, rate, amount and payment status.
6. Recycler pays externally and records method/reference.
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

Stable payment identities prevent duplicates. Retrying the same supported decision is safe; conflicting decisions are rejected. Financial totals remain independent of paged history.

Old accepted offers on sold lots are excluded from active actions. Backend chat guards prevent sending in terminal conversations; existing messages remain history.

Participant privacy projections protect pooled handovers, including other Collectors' QR, private source references and unrelated settlement metadata.

## Presentation and limitations

Passport means provenance records; risk flags are review signals. Neither is government certification or proof of payment. Prioritize material, final amount, payment state and the next action.

Availability and pickup-charge sections save separately. Completed records retain reference information rather than active offer controls.

Direct bulk-lot payment controls do not add individual payout entry for pooled contributors; existing pooled settlement APIs remain separate.

## Deployment and verification

Deploy additive backend contracts first, review the optional counterproposal field and prepare required indexes. Android includes Room migration 29 → 30 without destructive migration.

Fixtures cover consent, signed receipt, inventory/offer/chat transitions, recipients, authorization, payments and retries. Live database tests exercise selected paths, not the entire journey.

Connected Android acceptance, live two-device FCM, external bank transfers and physical-device performance remain incomplete. See [verification status](README.md) and [Household settlement](PICKUP_STATE_MACHINE.md).
