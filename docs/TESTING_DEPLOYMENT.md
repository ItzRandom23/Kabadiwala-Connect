# Testing deployment profile

The Android debug variant is intentionally labelled **TESTING** and targets:

`http://140.245.232.208:4000/api/v1/`

The host was not reachable from the development machine when this profile was added. Confirm `GET /api/v1/health` before distributing a test APK.

## Safety boundary

- Cleartext traffic is enabled only for the debug variant.
- Release builds default to `https://api.invalid/api/v1/` and require an explicit HTTPS endpoint through `-PproductionApiBaseUrl=...`.
- The in-app testing banner makes the active environment visible.
- Test accounts, prices, authorization evidence and payments must be labelled synthetic.
- Do not reuse testing database, JWT, traceability, object-storage or AI credentials in production.

## Build commands

```powershell
# Current testing server
.\gradlew.bat assembleDebug

# Override the testing host without editing source
.\gradlew.bat assembleDebug -PtestingApiBaseUrl=http://140.245.232.208:4000/api/v1/

# Future production build; HTTPS is enforced
.\gradlew.bat assembleRelease -PproductionApiBaseUrl=https://api.example.org/api/v1/
```

Before any public pilot, place the server behind an HTTPS domain and update the testing URL to HTTPS. Authentication, refresh tokens, chat, location, and transaction data must not cross a public network over cleartext HTTP.

Prepare a testing database with `npm run db:prepare`. Do not use `prisma db push`; Prisma cannot represent the partial unique MongoDB indexes used by optional phone and email identities.
