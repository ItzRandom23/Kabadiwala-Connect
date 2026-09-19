# Production deployment runbook

This runbook is the operational boundary for the first controlled pilot. It is not a claim that external accounts, DLT approval, payment execution, backups, or legal review have already been completed.

## 1. Staging first

Use a separate MongoDB database, 2Factor account/balance, object-storage bucket, JWT secret, traceability secret, and Android API origin for staging. Do not point staging at the production database.

Required backend settings:

```env
NODE_ENV=production
DATABASE_URL=<staging-or-production-mongodb-replica-set>
JWT_SECRET=<random-secret-at-least-32-characters>
TRACEABILITY_SIGNING_SECRET=<different-random-secret-at-least-32-characters>
CORS_ORIGIN=https://<allowed-origin>
OTP_PROVIDER=twofactor
TWOFACTOR_API_KEY=<server-side-secret>
TWOFACTOR_BASE_URL=https://2factor.in
NOTIFICATION_SMS_PROVIDER=disabled
NOTIFICATION_SMS_ENABLED=false
TWOFACTOR_SMS_SENDER_ID=<approved-dlt-sender-id>
NOTIFICATION_PUSH_PROVIDER=disabled
NOTIFICATION_PUSH_ENABLED=false
FCM_PROJECT_ID=<staging-or-production-firebase-project>
FCM_CLIENT_EMAIL=<server-only-service-account-email>
FCM_PRIVATE_KEY=<server-only-private-key>
STORAGE_PROVIDER=s3
S3_ENDPOINT=<private-s3-compatible-endpoint>
S3_REGION=ap-south-1
S3_BUCKET=<private-bucket>
S3_ACCESS_KEY_ID=<secret>
S3_SECRET_ACCESS_KEY=<secret>
LOCAL_UPLOAD_PUBLIC=false
RATE_LIMIT_STORE=database
```

Never put the 2Factor key in the Android app. The Android client calls this backend; only the backend calls 2Factor.

Push is also server-mediated. Do not put `FCM_PRIVATE_KEY` in the Android app. For Firebase project creation, `google-services.json`, service-account permissions, and staging acceptance, follow [FIREBASE_PUSH_SETUP.md](FIREBASE_PUSH_SETUP.md). Keep both push variables disabled until that checklist passes.

## 2. Database preparation

The repository uses MongoDB and maintains some runtime indexes separately. Use a disposable staging database to validate the Prisma client/schema change first. Do not run a direct `prisma db push` against production because this repository maintains intentional partial indexes for optional contact fields:

```powershell
cd backend
npm ci
npm run db:generate
npm run db:prepare
npm run build
npm test
```

Only after the staging smoke test passes should the same schema/index preparation be performed against production. Take a backup and record the database name before changing it.

## 3. Container deployment

The backend includes `backend/Dockerfile` and runs as the unprivileged `node` user. Build and start it with the deployment platform's secret manager:

```powershell
docker build -f backend/Dockerfile -t kabadiwala-connect-backend:pilot backend
docker run --rm -p 4000:4000 --env-file backend/.env kabadiwala-connect-backend:pilot
```

Put the container behind an HTTPS reverse proxy or managed load balancer. Do not expose port 4000 directly to the public internet without TLS and network controls.

## 4. Smoke test

1. `GET /api/v1/health` returns `healthy` and `database: connected`; `GET /api/v1/ready` returns `ready` with every check true.
2. Request one OTP using a controlled test number and confirm the SMS arrives.
3. Verify the OTP and confirm access/refresh tokens are issued.
4. Create a household listing with an idempotency key and upload a photo.
5. Confirm a retry with the same idempotency key does not create a duplicate listing.
6. Verify a Kabadiwala can see only eligible listings and cannot access another household's photo.
7. Walk through accept, schedule, arrival, weighing, settlement, payment evidence, and household confirmation using separate accounts.
8. In a controlled collector account, schedule two pickups on the configured test day, verify the next reservation returns `PICKUP_CAPACITY_FULL`, then cancel or reassign one pickup and verify a new reservation succeeds. Also verify a reschedule to another day releases the old day and reserves the new one.
9. Confirm the same state is visible after process restart and offline sync recovery.
10. From a disposable staging account, call `GET /api/v1/auth/account/export` and confirm the response contains account-owned records but no password hash, refresh token, OTP challenge, or server secret.
11. Call `POST /api/v1/auth/account/delete` with `{ "confirmation": "DELETE" }`; confirm the account is marked deleted, refresh sessions are revoked, contact credentials are cleared, and the account-deletion audit event remains available to operators.
12. After the Firebase provider integration is configured, sign in on one staging Android build, grant notification permission, confirm `POST /api/v1/notifications/devices` returns metadata only, trigger one non-sensitive event, and verify the `PUSH` outbox plus per-device target reach `SENT` without duplication after a worker restart. Uninstall/revoke the test app and confirm the invalid token is disabled.
13. Disable push in the account’s notification preferences and verify a later event is marked `PUSH_NOT_OPTED_IN` with no provider call. Then unregister the device and verify `removed=true`.
14. For SMS notification testing, configure a staging-only 2Factor sender ID and approved DLT template contract, set `NOTIFICATION_SMS_PROVIDER=twofactor` and `NOTIFICATION_SMS_ENABLED=true`, trigger one non-sensitive event, and verify the outbox reaches `SENT` or a bounded `FAILED` state without duplicating on worker retry. Disable SMS in the account’s notification preferences and verify a later event is marked `SKIPPED` with no provider call. Do not enable this with an unapproved raw message template.

Account deletion is a soft-delete boundary: financial, dispute, and traceability records are retained for controlled operational/legal retention, while direct contact/authentication data and in-app notifications are removed. Final retention periods and legal holds still require policy and legal approval.

## 5. Release gate

Do not distribute a release APK until the API base URL is the real HTTPS origin:

```powershell
cd android_app
.\gradlew.bat testDebugUnitTest --no-daemon
.\gradlew.bat assembleRelease --no-daemon -PproductionApiBaseUrl=https://api.example.org/api/v1/ -PallowPlaceholderProductionApiUrl=true -PrequireProductionSigning=false
```

The command above validates release packaging with a placeholder host and produces an unsigned APK. Do not distribute that artifact. For a distributable build, use the real API host, inject the keystore values through the CI secret manager or local environment, and require signing:

```powershell
$env:ORG_GRADLE_PROJECT_productionSigningStoreFile = "C:\secure\kabadiwala-release.jks"
$env:ORG_GRADLE_PROJECT_productionSigningStorePassword = $env:KABADIWALA_STORE_PASSWORD
$env:ORG_GRADLE_PROJECT_productionSigningKeyAlias = $env:KABADIWALA_KEY_ALIAS
$env:ORG_GRADLE_PROJECT_productionSigningKeyPassword = $env:KABADIWALA_KEY_PASSWORD
.\gradlew.bat assembleRelease --no-daemon `
  -PproductionApiBaseUrl=https://api.example.org/api/v1/ `
  -PrequireProductionSigning=true
```

The Android release build fails when the HTTPS API property is missing, uses a cleartext/placeholder URL, or lacks all four signing values. Never commit the keystore or passwords.

The protected `android-release` CI job is manual-only (`workflow_dispatch`) and requires a GitHub `production` environment with these secrets: `PRODUCTION_KEYSTORE_BASE64`, `PRODUCTION_KEYSTORE_PASSWORD`, `PRODUCTION_KEY_ALIAS`, `PRODUCTION_KEY_PASSWORD`, and `PRODUCTION_API_BASE_URL`. It produces an AAB artifact only after the real HTTPS host and signing gate pass. Configure environment approval rules before using it for a pilot release.

## 6. Rollback and incident controls

- Keep the previous container image and Android artifact available.
- Rotate 2Factor/API/database credentials if exposed; never commit them.
- Disable new signups or switch the API to maintenance mode if OTP, database, storage, or payment evidence is unreliable.
- Preserve audit events and source records; do not repair financial or settlement history by editing rows in place.
- Record incident time, affected account IDs, request IDs, provider response class, and recovery action without logging OTPs, tokens, or secrets.
