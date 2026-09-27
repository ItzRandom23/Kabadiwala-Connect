import express from 'express';
import request from 'supertest';
import { describe, expect, it, vi } from 'vitest';
import { JwtService } from '../src/services/jwt.js';
import { supplyChainRoutes } from '../src/routes/supplyChainRoutes.js';
import { errorHandler } from '../src/middleware/errors.js';

const config = { JWT_SECRET: 'waiting-pickup-test-secret', JWT_EXPIRES_IN: '1h' } as any;

function appFor(db: any, jwt: JwtService) {
  const app = express();
  app.use(express.json());
  app.use('/api/v1', supplyChainRoutes(jwt, { findById: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE' }) } as any, db));
  app.use(errorHandler);
  return app;
}

function auditMocks() {
  return { auditEvent: { create: vi.fn() }, materialPassportEvent: { create: vi.fn() } };
}

describe('waiting pickup lifecycle', () => {
  it('creates an idempotent waiting request and notifies a matching collector', async () => {
    const pickup = { id: 'pickup-waiting-1', listingId: 'listing-1', householdId: 'household-1', kabadiwalaId: null, status: 'WAITING_FOR_PICKUP' };
    const tx = {
      ...auditMocks(),
      idempotencyRecord: { findUnique: vi.fn().mockResolvedValue(null), create: vi.fn() },
      pickupRequest: { findFirst: vi.fn().mockResolvedValue(null), create: vi.fn().mockResolvedValue(pickup) },
      householdListing: { updateMany: vi.fn().mockResolvedValue({ count: 1 }) }
    };
    const db: any = {
      user: {
        findFirst: vi.fn().mockResolvedValue({ role: 'HOUSEHOLD', accountStatus: 'ACTIVE' }),
        findMany: vi.fn().mockResolvedValue([{ collectorProfileId: 'collector-1' }])
      },
      householdListing: { findFirst: vi.fn().mockResolvedValue({ areaName: 'Sector 12', latitude: null, longitude: null, photoReference: 'listing-photo.jpg', photoReferences: ['listing-photo.jpg'] }) },
      collector: { findMany: vi.fn().mockResolvedValue([{ id: 'collector-1', areaName: 'Sector 12', latitude: null, longitude: null }]) },
      notificationEvent: { create: vi.fn().mockResolvedValue({ id: 'notification-1', accountId: 'collector-1' }) },
      $transaction: vi.fn(async (callback: (value: any) => unknown) => callback(tx))
    };
    const jwt = new JwtService(config);
    const response = await request(appFor(db, jwt))
      .post('/api/v1/household/listings/listing-1/pickups')
      .set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-1')}`)
      .set('Idempotency-Key', 'waiting-request-1')
      .send({});

    expect(response.status).toBe(201);
    expect(response.body.data).toMatchObject({ status: 'WAITING_FOR_PICKUP', kabadiwalaId: null });
    expect(tx.pickupRequest.create).toHaveBeenCalledWith({ data: expect.objectContaining({ status: 'WAITING_FOR_PICKUP', kabadiwalaId: null }) });
    expect(db.notificationEvent.create).toHaveBeenCalledWith({ data: expect.objectContaining({ accountId: 'collector-1', type: 'PICKUP_WAITING_FOR_PICKUP' }) });
  });

  it('retries a transaction conflict and returns the concurrent waiting request', async () => {
    const winner = { id: 'pickup-waiting-winner', listingId: 'listing-race', householdId: 'household-1', kabadiwalaId: null, status: 'WAITING_FOR_PICKUP' };
    let winnerCommitted = false;
    const create = vi.fn();
    const updateMany = vi.fn(async () => {
      winnerCommitted = true;
      const conflict = new Error('Transaction failed due to a write conflict') as Error & { code?: string };
      conflict.code = 'P2034';
      throw conflict;
    });
    const db: any = {
      user: { findFirst: vi.fn().mockResolvedValue({ role: 'HOUSEHOLD', accountStatus: 'ACTIVE' }) },
      householdListing: { findFirst: vi.fn().mockResolvedValue({ areaName: 'Sector 12', photoReference: 'listing-photo.jpg', photoReferences: ['listing-photo.jpg'] }) },
      $transaction: vi.fn(async (callback: (value: any) => unknown) => callback({
        ...auditMocks(),
        pickupRequest: {
          findFirst: vi.fn(async () => winnerCommitted ? winner : null),
          create
        },
        householdListing: { updateMany }
      }))
    };
    const jwt = new JwtService(config);
    const response = await request(appFor(db, jwt))
      .post('/api/v1/household/listings/listing-race/pickups')
      .set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-1')}`)
      .send({});

    expect(response.status).toBe(200);
    expect(response.body.data).toMatchObject({ id: winner.id, status: 'WAITING_FOR_PICKUP' });
    expect(db.$transaction).toHaveBeenCalledTimes(2);
    expect(updateMany).toHaveBeenCalledOnce();
    expect(create).not.toHaveBeenCalled();
  });

  it('notifies both accounts when the household requests a specific collector', async () => {
    const pickup = { id: 'pickup-assigned-1', listingId: 'listing-assigned-1', householdId: 'household-1', kabadiwalaId: 'collector-1', status: 'REQUESTED' };
    const tx = {
      ...auditMocks(),
      idempotencyRecord: { findUnique: vi.fn().mockResolvedValue(null), create: vi.fn() },
      pickupRequest: {
        findUnique: vi.fn().mockResolvedValue(null),
        upsert: vi.fn().mockResolvedValue(pickup)
      },
      householdListing: { updateMany: vi.fn().mockResolvedValue({ count: 1 }) }
    };
    const notificationCreate = vi.fn().mockResolvedValue({ id: 'notification-1' });
    const db: any = {
      user: {
        findFirst: vi.fn(async ({ where }: any) => where.collectorProfileId === 'household-1'
          ? { role: 'HOUSEHOLD', accountStatus: 'ACTIVE' }
          : { id: 'collector-user', role: 'COLLECTOR', accountStatus: 'ACTIVE' })
      },
      householdListing: { findFirst: vi.fn().mockResolvedValue({ areaName: 'Sector 12', latitude: null, longitude: null, photoReference: 'listing-photo.jpg', photoReferences: ['listing-photo.jpg'] }) },
      collector: { findFirst: vi.fn().mockResolvedValue({ id: 'collector-1', accountStatus: 'ACTIVE' }) },
      notificationEvent: { create: notificationCreate },
      $transaction: vi.fn(async (callback: (value: any) => unknown) => callback(tx))
    };
    const jwt = new JwtService(config);

    const response = await request(appFor(db, jwt))
      .post('/api/v1/household/listings/listing-assigned-1/pickups')
      .set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-1')}`)
      .send({ kabadiwalaId: 'collector-1' });

    expect(response.status).toBe(201);
    expect(notificationCreate).toHaveBeenCalledTimes(2);
    expect(notificationCreate).toHaveBeenCalledWith({ data: expect.objectContaining({
      accountId: 'collector-1', type: 'PICKUP_REQUESTED', route: 'kabadiwala/pickups/pickup-assigned-1'
    }) });
    expect(notificationCreate).toHaveBeenCalledWith({ data: expect.objectContaining({
      accountId: 'household-1', type: 'PICKUP_REQUESTED', route: 'household/pickups/pickup-assigned-1'
    }) });
  });

  it('returns the active assigned pickup after a concurrent targeted-request conflict', async () => {
    const winner = { id: 'pickup-assigned-winner', listingId: 'listing-assigned-race', householdId: 'household-1', kabadiwalaId: 'collector-1', status: 'REQUESTED' };
    let winnerCommitted = false;
    const updateMany = vi.fn(async () => {
      winnerCommitted = true;
      const conflict = new Error('Transaction failed due to a write conflict') as Error & { code?: string };
      conflict.code = 'P2034';
      throw conflict;
    });
    const notificationCreate = vi.fn();
    const db: any = {
      user: { findFirst: vi.fn(async ({ where }: any) => ({ role: where.collectorProfileId === 'household-1' ? 'HOUSEHOLD' : 'COLLECTOR', accountStatus: 'ACTIVE' })) },
      householdListing: { findFirst: vi.fn().mockResolvedValue({ photoReference: 'listing-photo.jpg', photoReferences: ['listing-photo.jpg'] }) },
      collector: { findFirst: vi.fn().mockResolvedValue({ id: 'collector-1', accountStatus: 'ACTIVE' }) },
      notificationEvent: { create: notificationCreate },
      $transaction: vi.fn(async (callback: (value: any) => unknown) => callback({
        ...auditMocks(),
        idempotencyRecord: { findUnique: vi.fn().mockResolvedValue(null), create: vi.fn() },
        pickupRequest: { findUnique: vi.fn(async () => winnerCommitted ? winner : null), upsert: vi.fn() },
        householdListing: { updateMany }
      }))
    };
    const jwt = new JwtService(config);
    const response = await request(appFor(db, jwt))
      .post('/api/v1/household/listings/listing-assigned-race/pickups')
      .set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-1')}`)
      .send({ kabadiwalaId: 'collector-1' });

    expect(response.status).toBe(200);
    expect(response.body.data).toMatchObject({ id: winner.id, status: 'REQUESTED' });
    expect(db.$transaction).toHaveBeenCalledTimes(2);
    expect(updateMany).toHaveBeenCalledOnce();
    expect(notificationCreate).not.toHaveBeenCalled();
  });

  it('claims a waiting request atomically for the accepting collector', async () => {
    const claimed = { id: 'pickup-waiting-2', listingId: 'listing-2', kabadiwalaId: 'collector-2', status: 'ACCEPTED' };
    const tx = {
      ...auditMocks(),
      pickupRequest: {
        updateMany: vi.fn()
          .mockResolvedValueOnce({ count: 0 })
          .mockResolvedValueOnce({ count: 1 }),
        findFirstOrThrow: vi.fn().mockResolvedValue(claimed)
      },
      householdListing: {
        findUnique: vi.fn().mockResolvedValue({ areaName: 'Sector 12', latitude: null, longitude: null }),
        updateMany: vi.fn().mockResolvedValue({ count: 1 })
      },
      collector: { findUnique: vi.fn().mockResolvedValue({ areaName: 'Sector 12', latitude: null, longitude: null }) }
    };
    const db: any = {
      user: { findFirst: vi.fn().mockResolvedValue({ role: 'COLLECTOR', accountStatus: 'ACTIVE' }) },
      $transaction: vi.fn(async (callback: (value: any) => unknown) => callback(tx))
    };
    const jwt = new JwtService(config);
    const response = await request(appFor(db, jwt))
      .post('/api/v1/kabadiwala/listings/listing-2/accept')
      .set('Authorization', `Bearer ${jwt.generateToken('collector-2')}`);

    expect(response.status).toBe(200);
    expect(tx.pickupRequest.updateMany).toHaveBeenLastCalledWith({
      where: { listingId: 'listing-2', kabadiwalaId: null, status: 'WAITING_FOR_PICKUP' },
      data: { kabadiwalaId: 'collector-2', status: 'ACCEPTED', acceptedAt: expect.any(Date) }
    });
  });

  it('rejects a guessed waiting-pickup ID outside the collector service area', async () => {
    const tx = {
      ...auditMocks(),
      pickupRequest: { updateMany: vi.fn().mockResolvedValue({ count: 0 }) },
      collector: { findUnique: vi.fn().mockResolvedValue({ areaName: 'North', latitude: 19.2, longitude: 73.1 }) },
      householdListing: { findUnique: vi.fn().mockResolvedValue({ areaName: 'South', latitude: 21.2, longitude: 75.1 }), updateMany: vi.fn() }
    };
    const db: any = {
      user: { findFirst: vi.fn().mockResolvedValue({ role: 'COLLECTOR', accountStatus: 'ACTIVE' }) },
      $transaction: vi.fn(async (callback: (value: any) => unknown) => callback(tx))
    };
    const jwt = new JwtService(config);
    const response = await request(appFor(db, jwt))
      .post('/api/v1/kabadiwala/listings/guessed-listing/accept')
      .set('Authorization', `Bearer ${jwt.generateToken('collector-2')}`);

    expect(response.status).toBe(403);
    expect(response.body.error.code).toBe('AUTHORIZATION_ERROR');
    expect(response.body.error.details).toMatchObject({ code: 'PICKUP_OUTSIDE_SERVICE_AREA' });
    expect(tx.pickupRequest.updateMany).toHaveBeenCalledTimes(1);
  });
});
