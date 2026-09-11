# Connected Three-Role Demo

Use a non-production database and explicitly label all seeded companies and rates as demo data.

1. Start backend with an HTTPS endpoint, private storage, database rate limiting and fresh secrets. Seed the demo collector and verified recycler.
2. Set Android to Hindi or Marathi. Disconnect the device, create a PCB lot with photo, condition, approximate kg and area, then show the local queued state.
3. Restore connectivity. Confirm one lot exists server-side and open its estimated range; state aloud that it is not a guaranteed price.
4. List verified recyclers ranked by proximity. Open authorization details and request a quote.
5. Sign in as the recycler, submit a quote, then return to the collector. Reject it to demonstrate disagreement/rematching; send and accept a revised/second quote.
6. Open the automatically created private conversation. Agree the inspection/pickup details, then continue to handover.
7. Capture weight/location evidence. Verify the signed QR using `/api/v1/verify/handover`, then confirm from the recycler role.
8. Record CASH payment, show the earnings ledger, and export the complete transaction as an admin.

Failure branches to rehearse: expired authorization, expired quote, GPS denial, image upload retry, duplicate sync, >5% weight discrepancy, material mismatch and tampered QR.

