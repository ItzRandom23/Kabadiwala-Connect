# Android API coverage matrix

Audit date: 2026-09-15. Source: `../docs/BACKEND_API_CONTRACT.md`, `ApiService.kt`, existing Compose navigation, repositories, ViewModels and tests.

## Reading the matrix

- `COMPLETE` means a typed service contract and an existing visible workflow/background repository are present.
- `PARTIAL` means the service/model is present and at least part of the role flow is wired, but a detail/action/state/test is still missing.
- `COMPATIBILITY ONLY` means the backend route is intentionally not used by normal Android navigation.
- `ANDROID IMPLEMENTATION REQUIRED` means the contract is exposed or documented, but the current app has no safe role surface yet.
- UX codes: `L` loading, `C` content, `E` empty, `R` recoverable error, `O` offline/cached evidence. Shared Compose state components are reused where the route has a screen.
- Model status is `T` typed DTO, `J` `JsonObject` projection, `M` multipart typed transport.

## Health, authentication and profiles

| Endpoint | Role | Existing screen/repository/ViewModel | Model | UX / navigation / tests | Status |
|---|---|---|---|---|---|
| `GET /health` | Public | Retrofit service only; startup does not block on health | T/J | No visible destination; no endpoint test | PARTIAL |
| `POST /auth/request-otp` | Public | Onboarding / AuthenticationRepository | T | L/C/R; auth entry; auth tests present | COMPLETE |
| `POST /auth/verify-otp` | Public | Onboarding / AuthenticationRepository | T | L/C/R; role routing; auth tests present | COMPLETE |
| `POST /auth/refresh` | Refresh token | Retrofit authenticator / secure session | T | Background; safe logout on failure | COMPLETE |
| `POST /auth/logout` | Refresh token | Session/logout action | T | Confirmation and local session clear | COMPLETE |
| `POST /auth/signup` | Public | AuthenticationRepository | T | Auth entry; error envelope | COMPLETE |
| `POST /auth/login` | Public | AuthenticationRepository | T | Auth entry; error envelope | COMPLETE |
| `POST /auth/admin-login` | Public | Service only; no Admin role in `AccountRole` | T | No navigation; capability surface missing | ANDROID IMPLEMENTATION REQUIRED |
| `GET /auth/profile` | Household/Collector/Recycler | Startup profile load and role router | T | L/C/R; wrong-role route blocked | COMPLETE |
| `GET /collectors/me` | Collector | Collector profile/settings | T | Profile destination; R | COMPLETE |
| `PUT /collectors/me` | Collector | Collector profile/settings | T | Form validation and retry | COMPLETE |

## Legacy lots, prices, recyclers and quotes

| Endpoint | Role | Existing screen/repository/ViewModel | Model | UX / navigation / tests | Status |
|---|---|---|---|---|---|
| `POST /lots` | Collector | LotManagementViewModel / Sell | T | L/C/R/O queue | COMPLETE |
| `GET /lots` | Collector | LotRepository / lot list | T | L/C/E/R/O | COMPLETE |
| `GET /lots/:lotId` | Collector | Lot detail / repository | T | C/R; detail destination | COMPLETE |
| `PUT /lots/:lotId` | Collector | LotManagementViewModel | T | Inline validation/R | COMPLETE |
| `DELETE /lots/:lotId` | Collector | Lot management | T | Confirm destructive action | COMPLETE |
| `GET /lots/:lotId/photo` | Collector | Retrofit compatibility method; no separate photo viewer | J | Detail can show reference; image-service test missing | PARTIAL |
| `POST /lots/:lotId/photo` | Collector | Existing lot photo picker/queue | M/T | Upload error/size path | COMPLETE |
| `GET /prices/board` | Household/Collector/Recycler | PricesViewModel / price cards | T | L/C/E/R/O | COMPLETE |
| `GET /prices/history` | Household/Collector/Recycler | PricesViewModel / price history | T | L/C/E/R/O | COMPLETE |
| `GET /lots/:lotId/valuation` | Collector | Lot detail valuation | T | C/R; test gap | PARTIAL |
| `PUT /admin/prices/:priceId` | Admin + capability | Service-only capability method | J | No Admin destination | ANDROID IMPLEMENTATION REQUIRED |
| `GET /recycler/profile` | Recycler incl. pending | RecyclerProfileViewModel | T | Pending/rejected/expired state cards | COMPLETE |
| `POST /recycler/verification-request` | Recycler incl. pending | RecyclerProfileViewModel | T | Evidence form/R | COMPLETE |
| `PATCH /recycler/profile` | Verified Recycler | RecyclerProfileViewModel | T | Verified-only form | COMPLETE |
| `PUT /recycler/rates` | Verified Recycler | RecyclerProfileViewModel | T | Rates form/R | COMPLETE |
| `GET /recyclers` | Any authenticated | RecyclersViewModel / directory | T | L/C/E/R/O | COMPLETE |
| `GET /recyclers/match` | Collector | Recycler matching flow | T | Ranked cards/R | COMPLETE |
| `GET /recyclers/:recyclerId` | Any authenticated | Recycler detail | T | C/R | COMPLETE |
| `GET /admin/recyclers` | Admin + capability | Service-only | J | No Admin destination | ANDROID IMPLEMENTATION REQUIRED |
| `GET /admin/recyclers/:recyclerId` | Admin + capability | Service-only | J | No Admin destination | ANDROID IMPLEMENTATION REQUIRED |
| `PUT /admin/recyclers/:recyclerId/authorization` | Admin + capability | Service-only | J | Explicit confirmation not yet surfaced | ANDROID IMPLEMENTATION REQUIRED |
| `POST /quotes/request` | Collector | QuoteRepository / recycler matching | T | L/C/R/O | COMPLETE |
| `POST /quotes/request-batch` | Collector | QuoteRepository | J/T | Background/action; test gap | PARTIAL |
| `GET /quotes/pending` | Collector | QuoteRepository / quotes screen | T | L/C/E/R/O | COMPLETE |
| `GET /quotes/:quoteId` | Collector | Quote detail | T | C/R | COMPLETE |
| `POST /quotes/:quoteId/accept` | Collector | Quote action | T | Confirmation; queue | COMPLETE |
| `POST /quotes/:quoteId/reject` | Collector | Quote action | T | Confirmation; queue | COMPLETE |
| `GET /recycler/quote-requests` | Verified Recycler | Recycler marketplace/orders | T | L/C/E/R | COMPLETE |
| `GET /recycler/quote-requests/:requestId` | Verified Recycler | Typed service; detail action not promoted | T | Detail destination missing | PARTIAL |
| `POST /recycler/quotes` | Verified Recycler | Recycler quote flow | T | Form/R | COMPLETE |

## Legacy handover, payment, sync and reporting

| Endpoint | Role | Existing screen/repository/ViewModel | Model | UX / navigation / tests | Status |
|---|---|---|---|---|---|
| `POST /verify/handover` | Public | RecyclerScanViewModel | T | Scan success/invalid/expired | COMPLETE |
| `POST /handovers` | Collector | HandoverRepository | T | L/C/R/O queue | COMPLETE |
| `GET /handovers/reference/:referenceId` | Collector participant | Typed service; reference lookup not a dedicated screen | T | Detail action missing | PARTIAL |
| `GET /handovers/:handoverId` | Collector participant | Handover detail/repository | T | C/R | COMPLETE |
| `POST /handovers/:handoverId/mark-handed-over` | Collector | HandoverRepository | T | Confirmation/R/O queue | COMPLETE |
| `PUT /handovers/:handoverId/evidence` | Collector | Handover evidence form | T | Validation/R/O queue | COMPLETE |
| `POST /handovers/:handoverId/evidence/photo` | Collector | Evidence photo picker | M/T | Size/provider failure; device test missing | PARTIAL |
| `POST /handovers/:handoverId/dispute` | Collector | Dispute action / sync queue | J/T | Evidence form; R/O | COMPLETE |
| `GET /recycler/handovers` | Verified Recycler | RecyclerOrdersViewModel | T | L/C/E/R/O | COMPLETE |
| `GET /recycler/handovers/:handoverId` | Verified Recycler | Typed service; existing list detail is partial | T | Detail destination missing | PARTIAL |
| `POST /recycler/handovers/:handoverId/confirm` | Verified Recycler | Legacy handover path in RecyclerScanViewModel | T | L/C/R; formal QR path preferred | COMPLETE |
| `POST /recycler/handovers/:handoverId/reject` | Verified Recycler | Typed service; no visible reject form | J | Reason/evidence UI missing | PARTIAL |
| `GET /admin/disputes` | Admin + capability | Service-only | J | No Admin destination | ANDROID IMPLEMENTATION REQUIRED |
| `GET /admin/disputes/:disputeId` | Admin + capability | Service-only | J | No Admin destination | ANDROID IMPLEMENTATION REQUIRED |
| `POST /admin/disputes/:disputeId/resolve` | Admin + capability | Service-only | J | Confirmation/capability denied missing | ANDROID IMPLEMENTATION REQUIRED |
| `POST /payments/record` | Collector | PaymentRepository | T | L/C/R/O queue; confirmation | COMPLETE |
| `GET /payments` | Collector | PaymentRepository / earnings | T | L/C/E/R/O | COMPLETE |
| `GET /payments/:paymentId` | Collector | Payment detail | T | C/R | COMPLETE |
| `PUT /payments/:paymentId` | Collector | Payment edit | T | Validation/R | COMPLETE |
| `POST /payments/:paymentId/dispute` | Collector | Payment dispute | T | Evidence form/R | COMPLETE |
| `GET /earnings/ledger` | Collector | EarningsViewModel | T | L/C/E/R/O | COMPLETE |
| `GET /admin/payments` | Admin + capability | Service-only | J | No Admin destination | ANDROID IMPLEMENTATION REQUIRED |
| `GET /admin/payments/:paymentId` | Admin + capability | Service-only | J | No Admin destination | ANDROID IMPLEMENTATION REQUIRED |
| `POST /admin/payments/:paymentId/verify` | Admin + capability | Service-only | J | Explicit financial confirmation missing | ANDROID IMPLEMENTATION REQUIRED |
| `POST /sync` | Collector | Sync worker / Room queue | T | Background; retry/conflict queue state | COMPLETE |
| `GET /sync/changes` | Collector | Reconciliation in app container | T | Background; cache merge | COMPLETE |
| `GET /transactions/:lotId/timeline` | Collector | TransactionTimelineViewModel | T | Timeline L/C/R/O | COMPLETE |
| `GET /activity/changes` | Household/Collector/Recycler | Activity cache / future surfaces | T | Background/activity | COMPLETE |
| `GET /notifications` | Household/Collector/Recycler | Notification surface | T | L/C/E/R/O | COMPLETE |
| `GET /notifications/unread-count` | Household/Collector/Recycler | App chrome badge | T | Background | COMPLETE |
| `POST /notifications/:notificationId/read` | Household/Collector/Recycler | Notification action / queue | T | Snackbar/R/O | COMPLETE |
| `POST /notifications/read-all` | Household/Collector/Recycler | Notification action / queue | T | Confirmation/R/O | COMPLETE |

## Household source and pickup

| Endpoint | Role | Existing screen/repository/ViewModel | Model | UX / navigation / tests | Status |
|---|---|---|---|---|---|
| `POST /household/listings` | Household | HouseholdSupplyScreen → SupplyChainViewModel | T | L/C/E/R; offline key | COMPLETE |
| `GET /household/listings` | Household | HouseholdSupplyScreen → SupplyChainViewModel | T | L/C/E/R/O | COMPLETE |
| `GET /household/listings/:listingId` | Household | Typed service; selection uses list projection | J | Dedicated detail missing | PARTIAL |
| `PATCH /household/listings/:listingId` | Household | Typed service; no edit destination | T | Form/detail missing | PARTIAL |
| `GET /household/listings/:listingId/passport` | Household | Typed service; passport card is present for formal evidence | T | Dedicated source passport missing | PARTIAL |
| `POST /household/listings/:listingId/cancel` | Household | Listing card action | J | Confirmed destructive action/R | COMPLETE |
| `GET /household/kabadiwalas` | Household | HouseholdKabadiwalasScreen | T | L/C/E/R | COMPLETE |
| `POST /household/listings/:listingId/pickups` | Household | Listing card request action → VM | T | L/C/R/O; persisted UUID + queue | COMPLETE |
| `GET /household/pickups` | Household | HouseholdSupplyScreen | T | L/C/E/R/O | COMPLETE |
| `GET /household/pickups/:pickupId` | Household | Typed service; list card projection | J | Detail destination missing | PARTIAL |
| `GET /household/pickups/:pickupId/passport` | Household | Typed service; no dedicated detail screen | J | Timeline destination missing | PARTIAL |
| `POST /household/pickups/:pickupId/reschedule` | Household | VM action exists | T | Screen action missing | PARTIAL |
| `POST /household/pickups/:pickupId/settlement` | Household | VM action exists with reason/notes model | T | Settlement evidence UI missing | PARTIAL |
| `POST /household/pickups/:pickupId/cancel` | Household | Pickup card action | J | Confirmed destructive action/R | COMPLETE |

## Kabadiwala pickup, inventory and offers

| Endpoint | Role | Existing screen/repository/ViewModel | Model | UX / navigation / tests | Status |
|---|---|---|---|---|---|
| `GET /kabadiwala/listings` | Collector | KabadiwalaSupplyScreen | T | L/C/E/R/O | COMPLETE |
| `GET /kabadiwala/pickups` | Collector | KabadiwalaSupplyScreen | T | L/C/E/R/O | COMPLETE |
| `POST /kabadiwala/listings/:listingId/accept` | Collector | PickupCard | J | Confirm action/R | COMPLETE |
| `POST /kabadiwala/pickups/:pickupId/reject` | Collector | VM action; card control not yet exposed | T | Reason dialog missing | PARTIAL |
| `POST /kabadiwala/pickups/:pickupId/confirm-availability` | Collector | VM action; card control not yet exposed | T | Availability UI missing | PARTIAL |
| `POST /kabadiwala/pickups/:pickupId/schedule` | Collector | PickupCard schedule dialog | T | L/C/R | COMPLETE |
| `POST /kabadiwala/pickups/:pickupId/cancel` | Collector | VM action; card control not yet exposed | J | Reason dialog missing | PARTIAL |
| `POST /kabadiwala/pickups/:pickupId/reassign` | Collector | VM action; no-show/reassignment UI missing | T | Dedicated reassignment UI missing | PARTIAL |
| `POST /kabadiwala/pickups/:pickupId/status` | Collector | PickupCard status progression | T | L/C/R | COMPLETE |
| `POST /kabadiwala/pickups/:pickupId/complete` | Collector | CompletionDialog | T | Required weight/category/rate validation | COMPLETE |
| `GET /kabadiwala/inventory` | Collector | Inventory section | T | L/C/E/R/O | COMPLETE |
| `GET /kabadiwala/inventory/movements` | Collector | VM/cache state fetch | T | Movement detail screen missing | PARTIAL |
| `POST /kabadiwala/bulk-lots` | Collector | BulkLotDialog | T | Validation/R; inventory reservation confirmed by backend | COMPLETE |
| `GET /kabadiwala/bulk-lots` | Collector | Bulk lot cards | T | L/C/E/R/O | COMPLETE |
| `POST /kabadiwala/bulk-lots/:lotId/cancel` | Collector | BulkLotCard | J | Confirmation/R | COMPLETE |
| `GET /kabadiwala/bulk-offers` | Collector | Offer cards | T | L/C/E/R/O | COMPLETE |
| `POST /kabadiwala/bulk-offers/:offerId/reject` | Collector | Typed service only | T | Reason action missing | PARTIAL |
| `POST /kabadiwala/bulk-offers/:offerId/counter` | Collector | Typed service only | T | Counter form missing | PARTIAL |
| `POST /kabadiwala/bulk-offers/:offerId/accept` | Collector | OfferCard | J | Financial confirmation/R | COMPLETE |

## Recycler demand, pools and formal supply handovers

| Endpoint | Role | Existing screen/repository/ViewModel | Model | UX / navigation / tests | Status |
|---|---|---|---|---|---|
| `GET /recycler/bulk-lots` | Verified Recycler | RecyclerSupplyScreen | T | L/C/E/R/O | COMPLETE |
| `GET /recycler/bulk-lots/:lotId` | Verified Recycler | Typed service; card uses list projection | T | Detail destination missing | PARTIAL |
| `POST /recycler/bulk-lots/:lotId/offers` | Verified Recycler | OfferDialog | T | Validation/R | COMPLETE |
| `GET /recycler/offers` | Verified Recycler | RecyclerSupplyScreen state | T | L/C/E/R/O | COMPLETE |
| `POST /recycler/offers/:offerId/withdraw` | Verified Recycler | Typed service only | T | Withdraw confirmation missing | PARTIAL |
| `POST /recycler/bulk-lots/:lotId/receive` | Verified Recycler | No normal caller by design | J | Explicit compatibility guard; signed QR destination | COMPATIBILITY ONLY |
| `POST /recycler/procurement-requirements` | Verified Recycler | DemandDialog | T | Validation/R | COMPLETE |
| `PATCH /recycler/procurement-requirements/:requirementId` | Verified Recycler | Typed service only | T | Demand lifecycle editor missing | PARTIAL |
| `GET /recycler/procurement-requirements` | Verified Recycler | RecyclerSupplyScreen state | T | L/C/E/R/O | COMPLETE |
| `GET /kabadiwala/procurement-requirements` | Collector | Kabadiwala refresh/opportunity dashboard | T | L/C/E/R/O | COMPLETE |
| `GET /kabadiwala/route-advantage` | Collector | FormalisationDashboard | T | L/C/E/R/O; explainable route card | COMPLETE |
| `GET /kabadiwala/pool-opportunities` | Collector | FormalisationDashboard | T | L/C/E/R/O | COMPLETE |
| `GET /kabadiwala/pools/suggestions` | Collector | VM state fetch; suggestion card pending | T | No dedicated suggestion card | PARTIAL |
| `POST /kabadiwala/pools` | Collector | FormalisationDashboard | T | Confirmation/R | COMPLETE |
| `GET /kabadiwala/pools` | Collector | FormalisationDashboard | T | L/C/E/R/O | COMPLETE |
| `POST /kabadiwala/pools/:poolId/join` | Collector | JoinPoolDialog | T | Validation/R | COMPLETE |
| `POST /kabadiwala/pools/:poolId/leave` | Collector | PoolActionCard | J | Confirmation/R | COMPLETE |
| `POST /kabadiwala/pools/:poolId/lock` | Pool creator | PoolActionCard | T | Confirmation/R | COMPLETE |
| `GET /recycler/pools` | Verified Recycler | RecyclerSupplyScreen pool cards | T | L/C/E/R/O | COMPLETE |
| `GET /kabadiwala/demand-intelligence` | Collector | Typed service; no dedicated card | J | Opportunity projection missing | PARTIAL |
| `GET /kabadiwala/passport` | Collector | Growth Passport card | T | L/C/E/R/O | COMPLETE |
| `GET /safety-routing` | Collector | Typed service/model; warning UI currently uses safety modules | T | Material-specific routing card missing | PARTIAL |
| `GET /kabadiwala/safety` | Collector | Safety gate | T | L/C/E/R/O | COMPLETE |
| `POST /kabadiwala/safety/:moduleKey/acknowledge` | Collector | Safety gate action | T | Snackbar/R | COMPLETE |
| `POST /kabadiwala/pools/:poolId/prepare-handover` | Pool creator | PoolActionCard / QR card | T | L/C/R; signed QR | COMPLETE |
| `POST /kabadiwala/bulk-lots/:lotId/prepare-handover` | Collector owner | Formal handover card | T | L/C/R; signed QR | COMPLETE |
| `POST /kabadiwala/handovers/:handoverId/collector-confirm` | Collector | Handover card | T | Persisted UUID/R | COMPLETE |
| `GET /kabadiwala/handovers` | Collector | Handover cards | T | L/C/E/R/O | COMPLETE |
| `GET /recycler/supply-handovers` | Verified Recycler | RecyclerOrdersViewModel | T | L/C/E/R/O | COMPLETE |
| `POST /recycler/handovers/confirm` | Verified Recycler | RecyclerScanViewModel | T | QR/QC/R/O queue; persisted UUID | COMPLETE |
| `POST /kabadiwala/handovers/:handoverId/settlement` | Collector | Typed service; no settlement detail action | T | Review form missing | PARTIAL |
| `GET /kabadiwala/handovers/:handoverId/passport` | Collector | Typed service; handover card links conceptually | T | Dedicated passport destination missing | PARTIAL |
| `GET /kabadiwala/handovers/:handoverId/anomalies` | Collector | Typed service only | T/J | Anomaly explanation UI missing | PARTIAL |

## Future/secondary capabilities

| Endpoint | Role | Existing screen/repository/ViewModel | Model | Status |
|---|---|---|---|---|
| `GET/PATCH /future/preferences` | Any authenticated | SettingsViewModel | T | COMPLETE |
| `GET /future/rewards` | Collector | FutureFeatureViewModel | T | COMPLETE |
| `GET /future/schemes` | Any authenticated | FutureFeatureViewModel | T | COMPLETE |
| `POST /future/schemes/check` | Any authenticated | FutureFeatureViewModel | T | COMPLETE |
| `GET /future/activities` | Any authenticated | FutureFeatureViewModel | T | COMPLETE |
| `GET /future/activities/:slug` | Any authenticated | FutureFeatureViewModel | T | COMPLETE |
| `POST /future/lots/description-suggestion` | Collector | FutureFeatureViewModel | T | COMPLETE |
| `POST /future/lots/material-suggestion` | Collector | FutureFeatureViewModel | M/T | PARTIAL — device upload test gap |
| `GET /future/recyclers/:recyclerId/reviews` | Authenticated | FutureFeatureViewModel | T | COMPLETE |
| `POST /future/reviews` | Participant | FutureFeatureViewModel | T | COMPLETE |
| `GET/POST /future/conversations` | Collector/Recycler | FutureFeatureViewModel | T | COMPLETE |
| `GET/POST /future/conversations/:conversationId/messages` | Participants | FutureFeatureViewModel | T | COMPLETE |
| `POST /future/conversations/:conversationId/draft-reply` | Participants | FutureFeatureViewModel | T | COMPLETE |
| `POST /future/lots/:lotId/repeat` | Collector | FutureFeatureViewModel | T | COMPLETE |
| `GET /future/disputes/analytics` | Collector/Recycler | FutureFeatureViewModel | T | COMPLETE |

## Admin datasets

| Endpoint | Role | Existing screen/repository/ViewModel | Model | Status |
|---|---|---|---|---|
| `POST /admin/datasets/prices/import` | Admin + `DATASET_EXPORT` | Capability-gated ApiService method only | J | ANDROID IMPLEMENTATION REQUIRED |
| `GET /admin/datasets/export` | Admin + `DATASET_EXPORT` | Capability-gated ApiService method only | J | ANDROID IMPLEMENTATION REQUIRED |

## Cross-cutting test and state gaps

Existing unit tests cover auth, DTO/contract parsing, local repositories, reducer/formatter and several ViewModels; existing Android tests cover Compose/navigation surfaces. Missing or not executed in this environment: full MockWebServer coverage for every new route, concurrent refresh stress, QR tamper/expiry/replay device tests, Room migration tests for a fresh upgraded database, WorkManager instrumentation, screenshot diffs, TalkBack traversal and startup/scroll benchmarks.
