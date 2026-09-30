# Recycler receipt and payment flow

## Design direction

Use the existing Household/Collector operational surfaces and theme as the visual
reference. Keep Recycler tasks dense and glanceable: Market has Lots, Offers and
Demand sections; Orders owns receipt and payment evidence. Availability and pickup
pricing use distinct sections and save feedback. This follows the bundled Refero
typography guidance for work tools and the Android Compose design-system patterns.

## Material and money are separate

1. Collector accepts a Recycler offer and prepares/confirms a signed handover QR.
2. Recycler verifies the QR and explicitly confirms the received material online.
3. The backend commits inventory movement, marks the lot SOLD and the accepted
   offer COMPLETED, closes its chat, and creates a collector receipt notification.
4. The Recycler scanner returns to Market after the confirmed receipt. A collector
   viewing the active handover returns to Home when the updated receipt arrives.
5. Orders and Collector Home show the final amount and Payment pending.
6. Recycler pays externally, then records the payment method/reference. Recording
   payment does not prove that the Collector received it.
7. Collector confirms receipt or reports a payment issue. Verified, recorded,
   disputed and reversed states remain distinct. These writes require connectivity.

Existing `SupplyHandover.status = COMPLETED` means the material receipt is complete;
it does not automatically set `SupplyPayment.status = VERIFIED`. No historical
receipt is marked paid without payment evidence and recipient confirmation.

## API and compatibility

- Existing payment endpoints are connected to Android:
  `POST /recycler/handovers/:id/payment` and
  `POST /kabadiwala/handovers/:id/payment-confirm`.
- Handover list responses include account-scoped payments; Recycler Orders also
  returns completed material receipts, within the existing 100-record window.
- Source keys prevent duplicate payments. The same confirmed payment decision can
  be retried; a different decision is not silently accepted.
- Old accepted offers attached to SOLD lots are excluded from active UI lists.
  Backend chat guards also cover old OPEN conversations for those lots.
- Track-record statistics are derived after the receipt commit; their failure
  cannot make an already-committed transfer appear to fail. The passport read path
  recomputes the derived evidence profile.
- No database schema change is required. Deploy backend and Android together for
  the expanded list response and terminal-offer behavior.

## Verification

Backend tests exercise signed receipt, inventory/offer/chat transitions, recipient
notification isolation, separate payment recording/confirmation, retry behavior,
wrong-account rejection, overpayment and non-receipt disputes. Compose tests cover
terminal offers, separate saves, receipt navigation and explicit payment decisions.
Visual fixtures include 320 dp, increased font scale and light/dark themes.

Tests use controlled API/database fixtures. Live two-device FCM delivery, external
bank transfers and physical-device performance are not established by those tests.
The new payment controls cover direct bulk lots. Existing pooled settlement APIs
are retained; recording separate payouts for each pool contributor is not added
to the new Recycler order card.
