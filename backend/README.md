# Kabadiwala Connect Backend

## Payment & Earnings API (Phase 8)

This phase records payments; it does not transfer money. Collector APIs record/list/view/edit/dispute payments and retrieve an earnings ledger. Admin APIs list, inspect, and verify payments. Supported methods are CASH, BANK_TRANSFER, and DIGITAL_WALLET. Payments require an eligible confirmed/completed handover, are server-owned, limited to one payment per lot, and use a 24-hour correction window. Reporting uses Asia/Kolkata calendar-month semantics; stored timestamps remain UTC. Amounts differing materially from the accepted quote are flagged for review.

## Offline Sync (Phase 9)

`POST /api/v1/sync` accepts at most 100 explicitly supported operations: `CREATE/LOT`, `UPDATE/LOT`, and `CREATE/PAYMENT`. Each operation is deduplicated by `(authenticated collector, operationId)` in `SyncOperation`; retries return `ALREADY_APPLIED`. Lot updates require the client version and return `CONFLICT` when stale. `GET /api/v1/sync/changes?since=<ISO timestamp>` returns only the collector's lot, payment, and handover changes with a server timestamp. The server remains authoritative; quote/handover offline mutations are not accepted through this endpoint yet.

## Handover & Disputes (Phase 7)

Handover APIs create records from accepted quotes, generate unique `HOV-YYYYMMDD-RANDOM` references and QR payloads containing only the reference, enforce seven-day expiry, and support collector/recycler/admin ownership boundaries. Recycler confirmations preserve original and actual weight; differences over 5% or material mismatches open disputes. Payment is not performed here.

Backend API for the Android-first Kabadiwala Connect product. The service is the source of truth for account roles, recycler verification, collector lots, prices, matching, offers, handovers, payments, and offline sync. There is no user-facing web frontend.

## Requirements

Node.js 20+, npm, and MongoDB (Atlas or self-hosted replica set).

## Setup

```bash
npm install
copy .env.example .env
npm run db:generate
npm run db:push
npm run db:seed
```

Set `DATABASE_URL` to the MongoDB connection string and set a JWT secret of at least 16 characters in `.env`. Never commit the database password or other credentials. `db:push` is used because Prisma migrations are not supported for MongoDB.

## Authentication and roles

New Android accounts use email/password:

- `POST /api/v1/auth/signup` accepts email, password, role (`COLLECTOR` or `RECYCLER`), preferred language, and optional recycler business details.
- `POST /api/v1/auth/login` returns a bearer token plus the role-bearing account profile.
- `GET /api/v1/auth/profile` revalidates the profile after an offline launch.

Recycler accounts are created with `authorizationStatus=PENDING`; only secure backend/admin data can move them to `VERIFIED`. Role is never inferred from an email string or domain.

The phone/OTP contract below remains for older collector clients during migration.

`POST /api/v1/auth/request-otp` accepts `{ "phone": "9876543210" }`. `POST /api/v1/auth/verify-otp` accepts the phone and six-digit OTP, creates or logs in a collector, and returns a JWT plus the public collector profile. `POST /api/v1/auth/logout` is stateless and tells the client to remove its token. Use the JWT as `Authorization: Bearer <token>` for collector APIs. `PUT /api/v1/collectors/me` updates only language and primary location; latitude/longitude may be omitted for a manual area and must be valid when supplied.

## Android test updates

The server exposes `backend/app-update` at `/app`. The Android app checks `/app/update.json` on launch and compares its `versionCode`. Upload an APK and a manifest there to offer an update. The APK must have a higher `versionCode` and the same signing key as the installed app. The app asks before downloading, and Android asks for final installation confirmation; silent installation is not supported.

For local development, set `OTP_PROVIDER=development` and use `DEV_OTP_CODE` (default `123456`). This provider is rejected when `NODE_ENV=production`; no OTP is returned by the API. Set `OTP_PROVIDER=twilio` and provide the three `TWILIO_*` variables to use Twilio Verify. OTP values and tokens are never logged.

Gemini features are optional and fail safely when the key is absent or the provider is unavailable. Set `GEMINI_API_KEY` and optionally `GEMINI_MODEL` (for example, `gemini-2.5-flash`). The key remains server-side and is sent to Google's `generateContent` API. Lot descriptions still fall back to a deterministic template; photo material identification falls back to `OTHER` and always requires collector confirmation. Transaction chat remains human-to-human: `POST /api/v1/future/conversations/:conversationId/draft-reply` returns an editable Gemini reply draft and never sends or stores it automatically.

## Development and build

```bash
npm run dev
npm run build
npm run start
npm run test
npm run lint
```

## Production deployment boundary

Upload this `backend` directory without local `.env` files, `node_modules`, `dist`, test uploads, or other generated output. Configure production environment variables on the server, then run:

```bash
npm ci
npm run db:generate
npm run build
npm prune --omit=dev
npm start
```

Use `NODE_ENV=production`, a strong `JWT_SECRET`, a production MongoDB replica set, an explicit `CORS_ORIGIN`, and S3-compatible storage for horizontally scaled deployments. Keep `.env.example` as the configuration reference; never upload local credentials.

## API

- `GET /api/v1/health` reports API/database status, timestamp, and version.
- `GET /api/v1/collectors/me` returns the authenticated collector using `Authorization: Bearer <JWT>`.

The implemented `/api/v1/prices`, `/recyclers`, `/quotes`, `/handovers`, `/payments`, and `/earnings` endpoints are documented in the feature sections below. Admin functionality is limited to the explicitly listed review and authorization routes.

## Recycler API (Phase 5)

Collector endpoints are `GET /api/v1/recyclers`, `GET /api/v1/recyclers/{recyclerId}`, and `GET /api/v1/recyclers/match?lotId=...`. Discovery returns VERIFIED recyclers only and supports `location`, radius `5|10|25|50`, `materialCategory`, `availability`, `sort=proximity|rate`, `page`, and `limit`. Matching additionally checks accepted material, weight limits, service area, and collector-owned lot access.

Matching is deterministic and explainable: material +30, distance under 10 km +20 (10–25 km +10), availability +10, an available recycler offer +20, VERIFIED +10, and rating above 4 +5. Missing coordinates, rates, or ratings receive neutral points. Recycler offered rates are discovery data and are not market prices.

Admin-only management endpoints are `GET /api/v1/admin/recyclers`, `GET /api/v1/admin/recyclers/{recyclerId}`, and `PUT /api/v1/admin/recyclers/{recyclerId}/authorization`; authorization changes create audit records. Development seed recyclers are clearly test fixtures, not real facilities or licenses.

## Quote API (Phase 6)

Collector endpoints: `POST /api/v1/quotes/request`, `GET /api/v1/quotes/pending`, `GET /api/v1/quotes/{quoteId}`, and quote accept/reject actions. Recycler endpoints use `RECYCLER` JWTs: `GET /api/v1/recycler/quote-requests`, `GET /api/v1/recycler/quote-requests/{requestId}`, and `POST /api/v1/recycler/quotes`.

Requests are scoped to the authenticated owner, require a VERIFIED recycler accepting the lot material, and expire after 24 hours. Recycler submissions calculate totals server-side, compare against the latest market price when available, and flag quotes at least 2× market as anomalies. Acceptance is transactional and rejects other active quotes for the lot. No SMS/push delivery is implemented.

## Price API (Phase 4)

`GET /api/v1/prices/board?materialCategory=PCB&location=Mumbai` returns the latest trusted/development price, range, 30-day trend, and timestamp. `GET /api/v1/prices/history?materialCategory=PCB&location=Mumbai&days=30` returns chronological history. `GET /api/v1/lots/{lotId}/valuation` calculates and stores an owned lot's estimate using market price × weight × condition multiplier (`INTACT=1`, `DAMAGED=0.7`, `PARTIAL=0.4`) with neutral quality adjustment `1.0`. Values are rounded to two decimal places and are estimates, not real-time market claims.

The protected `PUT /api/v1/admin/prices/{priceId}` endpoint accepts `{ "priceMin": 2200, "priceMax": 2800, "marketPrice": 2500, "reason": "review" }` and records history plus an audit entry. It requires an ADMIN JWT; collector tokens receive 403. Development seed data covers Mumbai/Pune, PCB/CABLE, and multiple historical dates.

## Lot API

Authenticated collectors can create, list, inspect, edit eligible `CREATED` lots, cancel eligible lots without physical deletion, and upload a photo using multipart field `photo`. Create locations require GPS coordinates or a manual area name. Photos must be JPEG/PNG/WebP, at least 300×300, and under 5 MB. Set `STORAGE_PROVIDER=local` for development; processed JPEGs are stored under `LOCAL_UPLOAD_DIR` (default `uploads`) and served from the local `/uploads` route. Set `STORAGE_PROVIDER=s3` for AWS/S3-compatible storage using `S3_ENDPOINT`, `S3_REGION`, `S3_BUCKET`, `S3_ACCESS_KEY_ID`, `S3_SECRET_ACCESS_KEY`, and optional `S3_PUBLIC_BASE_URL`. Do not use local storage for horizontally scaled production deployments. The server generates lot IDs and owns collector association from the JWT.
