# Supply-chain testing runbook

This runbook is for the non-production development database only. The fixture
accounts are created by `npm run db:seed` and are not selected by application
code as defaults.

| Role | Account | Fixture profile |
| --- | --- | --- |
| Household A | `household-a@kabadiwala.example` | PET/PLASTIC and newspaper (`OTHER`) listings |
| Household B | `household-b@kabadiwala.example` | Copper/metal listing |
| Kabadiwala A | `dev-collector@kabadiwala.example` | Development Area |
| Kabadiwala B | `kabadiwala-b@kabadiwala.example` | Aundh, Pune |
| Recycler A | `dev-recycler@kabadiwala.example` | Verified PET/PCB buyer |
| Recycler B | `recycler-b@kabadiwala.example` | Verified copper/metal buyer |

All fixture email accounts use the seed-only password `DemoPass123!`.

From `backend`, synchronize the testing database before starting the server:

```text
npm run db:push
npm run db:seed
npm run dev
```

The Android debug build uses `http://140.245.232.208:4000/api/v1/` by default;
the emulator/device must be able to reach that host over the testing network.

The manual happy path is Household listing → selected Kabadiwala pickup →
arrival and final weighing → inventory increment → inventory-backed bulk lot →
Recycler offer → Kabadiwala acceptance → Recycler receipt. Verify that the
pickup is `COMPLETED`, the offer is `COMPLETED`, the lot is `SOLD`, and the
Kabadiwala inventory has moved from `availableKg` to `soldKg` with no residual
reservation.

The conflict pass should repeat completion and receipt, submit a lot larger
than available stock, act on a cancelled/completed record, and call each role's
endpoint with the other roles' tokens. Expected outcomes are 409 for stale or
conflicting mutations, 404 for unavailable/cancelled resources, and 403 for
role violations.

`MaterialCategory` currently has no `PAPER` value; the seed represents
newspaper as `OTHER` until the backend contract adds a dedicated category.
