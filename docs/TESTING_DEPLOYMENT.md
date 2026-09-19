# Testing deployment profile

The Android debug variant is intentionally labelled **TESTING** and has no
implicit remote host. It defaults to an invalid non-network URL, so configure
an explicit local or approved staging host before exercising live API flows:

`-PtestingApiBaseUrl=http://10.0.2.2:4000/api/v1/`

Confirm `GET /api/v1/health` returns 200 with database status `connected`
before distributing a test APK.

## Safety boundary

- Cleartext traffic is enabled only for the debug variant.
- Release builds require an explicit HTTPS endpoint through `-PproductionApiBaseUrl=...`; there is no placeholder fallback host.
- The in-app testing banner makes the active environment visible.
- Test accounts, prices, authorization evidence and payments must be labelled synthetic.
- Do not reuse testing database, JWT, traceability, object-storage or AI credentials in production.

## Build commands

```powershell
# Local emulator backend; do not rely on an implicit remote host
.\gradlew.bat assembleDebug -PtestingApiBaseUrl=http://10.0.2.2:4000/api/v1/

# Override the testing host without editing source
.\gradlew.bat assembleDebug -PtestingApiBaseUrl=https://staging.example.org/api/v1/

# Future production packaging validation; placeholder host and unsigned output are explicit
.\gradlew.bat assembleRelease -PproductionApiBaseUrl=https://api.example.org/api/v1/ -PallowPlaceholderProductionApiUrl=true -PrequireProductionSigning=false
```

Before any public pilot, place the server behind an HTTPS domain and update the testing URL to HTTPS. Authentication, refresh tokens, chat, location, and transaction data must not cross a public network over cleartext HTTP.

Prepare a testing database with `npm run db:push`, then load the safe fixtures
with `npm run db:seed`. `npm run db:prepare` remains available for older
deployments that still maintain optional MongoDB indexes at runtime.
