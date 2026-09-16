# Connected Three-Role Demo

Use a non-production database and explicitly label all seeded companies and rates as demo data.

Device setup used for the verified run:

```text
C:\Users\pulki\AppData\Local\Android\Sdk\platform-tools\adb.exe reverse tcp:4000 tcp:4000
cd android_app
gradlew.bat testDebugUnitTest assembleDebug --no-daemon -PtestingApiBaseUrl=http://127.0.0.1:4000/api/v1
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The verified run used `emulator-5554` (Android 17, 1080×2400), fresh
development accounts and seeded Recycler authority/rate/demand records.

1. Use the non-production development database, local testing backend and
   fresh secrets. Seed only clearly labelled demo rates, demand and verified
   test Recycler records.
2. Create fresh Household, Kabadiwala and Recycler accounts. Complete phone
   registration/login through the app; verify the Recycler only through the
   development operator path.
3. As Household, post a PCB or battery listing. Select one Kabadiwala, accept
   the pickup, schedule it, arrive/weigh/complete it and read back the
   Kabadiwala inventory.
4. As Kabadiwala, open Formal Route Advantage. Compare the current verified
   Recycler route with the local baseline and say aloud that net value is an
   estimate, not a guaranteed offer.
5. As Recycler, publish a demand requirement/rate. As two Kabadiwala test
   accounts, join the same opportunity from separate inventories, show the
   threshold math, then lock the pool.
6. As the pool owner, prepare the one-time QR and confirm the physical
   handover. The QR/reference should be visible with a material-passport label.
7. As Recycler, enter the exact QR payload in the scanner test field (or scan
   the displayed code), submit actual/accepted weight and material match, and
   demonstrate both the clean completion path and a variance path requiring
   Collector review.
8. Disconnect the emulator after a handover is prepared. Show cached evidence;
   restore connectivity and confirm that the queued Recycler action converges
   to one server handover. Separately use the Collector `Record a lot offline`
   action, force-stop/relaunch while offline, then restore connectivity and
   show the lot syncing once in Sync Center. Read the passport timeline and
   settlement.
9. Run the legacy CASH ledger flow only as regression evidence. Do not imply
   that every formal pooled settlement is already linked to payment/earnings.

Observed final proof: the Android Recycler scanner accepted a complete 538-byte
signed payload in chunks, displayed `Material passport matched`, and then
displayed `Receipt recorded` / `Server receipt: Completed · final 1.0 kg`.

Failure branches to rehearse: expired authorisation, GPS denial, image upload
retry, duplicate pool creation, duplicate sync, >5% weight discrepancy,
material mismatch, tampered/expired QR, wrong-role receipt and stale settlement.
