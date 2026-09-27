import { describe, expect, it, vi } from 'vitest';
import express from 'express';
import request from 'supertest';
import { JwtService } from '../src/services/jwt.js';
import { supplyChainRoutes } from '../src/routes/supplyChainRoutes.js';
import { errorHandler } from '../src/middleware/errors.js';

const config = { JWT_SECRET: 'critical-path-test-secret', JWT_EXPIRES_IN: '1h' } as never;

function appWith(db: any, jwt: JwtService) {
  const app = express();
  app.use(express.json());
  app.use('/api/v1', supplyChainRoutes(jwt, { findById: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE' }) } as never, db));
  app.use(errorHandler);
  return app;
}

describe('household pickup critical paths', () => {
  it('claims a household QR when Mongo has no householdQrScannedAt field', async () => {
    const pickup: Record<string, any> = {
      id: 'pickup-1', listingId: 'listing-1', householdId: 'household-1',
      kabadiwalaId: 'collector-1', status: 'ARRIVED'
    };
    const updateMany = vi.fn(async ({ where, data }: any) => {
      const includesMissingField = where.OR?.some((clause: any) => clause.householdQrScannedAt?.isSet === false);
      const explicitNull = Object.hasOwn(pickup, 'householdQrScannedAt') && pickup.householdQrScannedAt === null;
      const missing = !Object.hasOwn(pickup, 'householdQrScannedAt');
      if (pickup.id !== where.id || pickup.kabadiwalaId !== where.kabadiwalaId || pickup.status !== where.status || !(explicitNull || (missing && includesMissingField))) return { count: 0 };
      Object.assign(pickup, data);
      return { count: 1 };
    });
    const findFirst = vi.fn(async () => ({ ...pickup }));
    const db = {
      user: { findFirst: vi.fn(async ({ where }: any) => ({ role: where.collectorProfileId === 'household-1' ? 'HOUSEHOLD' : 'COLLECTOR', accountStatus: 'ACTIVE' })) },
      pickupRequest: { findFirst },
      $transaction: vi.fn(async (callback: (tx: any) => unknown) => callback({
        pickupRequest: {
          findFirst,
          updateMany,
          findUniqueOrThrow: vi.fn(async () => ({ ...pickup }))
        },
        auditEvent: { create: vi.fn().mockResolvedValue({}) },
        materialPassportEvent: { create: vi.fn().mockResolvedValue({}) }
      }))
    } as any;
    const jwt = new JwtService(config);
    const app = appWith(db, jwt);
    const qr = await request(app)
      .get('/api/v1/household/pickups/pickup-1/qr')
      .set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-1')}`);
    expect(qr.status).toBe(200);

    const scan = () => request(app)
      .post('/api/v1/kabadiwala/pickups/pickup-1/confirm-household-qr')
      .set('Authorization', `Bearer ${jwt.generateToken('collector-1')}`)
      .send({ qrCodeData: qr.body.data.qrCodeData });
    const first = await scan();
    expect(first.status).toBe(200);
    expect(first.body.data.householdQrScannedAt).toBeTruthy();
    expect(updateMany).toHaveBeenCalledWith(expect.objectContaining({
      where: expect.objectContaining({ OR: [{ householdQrScannedAt: null }, { householdQrScannedAt: { isSet: false } }] })
    }));

    const replay = await scan();
    expect(replay.status).toBe(200);
    expect(updateMany).toHaveBeenCalledTimes(1);
  });

  it('replays the household settlement decision after the first request changed its status', async () => {
    let settlementStatus = 'PENDING_HOUSEHOLD_CONFIRMATION';
    const operations = new Map<string, any>();
    const pickup = () => ({ id: 'pickup-1', listingId: 'listing-1', householdId: 'household-1', kabadiwalaId: 'collector-1', status: 'COMPLETED', settlementStatus });
    const findRecord = vi.fn(async ({ where }: any) => operations.get(where.actorId_operationId.operationId) ?? null);
    const createRecord = vi.fn(async ({ data }: any) => { operations.set(data.operationId, data); return data; });
    const updatePickup = vi.fn(async ({ where, data }: any) => {
      if (settlementStatus !== where.settlementStatus || where.status !== 'COMPLETED') return { count: 0 };
      settlementStatus = data.settlementStatus;
      return { count: 1 };
    });
    const db = {
      user: { findFirst: vi.fn().mockResolvedValue({ role: 'HOUSEHOLD', accountStatus: 'ACTIVE' }) },
      pickupRequest: { findFirst: vi.fn(async ({ where }: any) => where.status === 'COMPLETED' ? pickup() : null) },
      notificationEvent: { create: vi.fn().mockResolvedValue({ id: 'notification-1' }) },
      $transaction: vi.fn(async (callback: (tx: any) => unknown) => callback({
        idempotencyRecord: { findUnique: findRecord, create: createRecord },
        pickupRequest: { updateMany: updatePickup, findUniqueOrThrow: vi.fn(async () => pickup()) },
        anomalyFlag: { create: vi.fn() },
        auditEvent: { create: vi.fn().mockResolvedValue({}) },
        materialPassportEvent: { create: vi.fn().mockResolvedValue({}) }
      }))
    } as any;
    const jwt = new JwtService(config);
    const app = appWith(db, jwt);
    const key = 'settlement-audit-1';
    const householdToken = jwt.generateHouseholdToken('household-1');
    const decide = (body: any) => request(app)
      .post('/api/v1/household/pickups/pickup-1/settlement')
      .set('Authorization', `Bearer ${householdToken}`)
      .set('Idempotency-Key', key)
      .send(body);

    const first = await decide({ decision: 'ACCEPT' });
    expect(first.status).toBe(200);
    expect(first.body.data.settlementStatus).toBe('ACCEPTED');

    const retry = await decide({ decision: 'ACCEPT' });
    expect(retry.status).toBe(200);
    expect(retry.body.message).toBe('Settlement decision already processed');
    expect(updatePickup).toHaveBeenCalledTimes(1);

    const changedPayload = await decide({ decision: 'RAISE_ISSUE', reasonCode: 'WEIGHT_MISMATCH' });
    expect(changedPayload.status).toBe(409);
    expect(changedPayload.body.error.message).toMatch(/idempotency key.*different settlement decision/i);
  });
});
