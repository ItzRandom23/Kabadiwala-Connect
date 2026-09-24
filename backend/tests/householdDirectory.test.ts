import { describe, expect, it, vi } from 'vitest';
import express from 'express';
import request from 'supertest';
import { JwtService } from '../src/services/jwt.js';
import { supplyChainRoutes } from '../src/routes/supplyChainRoutes.js';
import { errorHandler } from '../src/middleware/errors.js';

const config = { JWT_SECRET: 'a-secure-test-secret', JWT_EXPIRES_IN: '1h' } as never;

function householdApp(db: any) {
  const jwt = new JwtService(config);
  const app = express();
  app.use(express.json());
  app.use('/api/v1', supplyChainRoutes(jwt, { findById: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE' }) } as never, db));
  app.use(errorHandler);
  return { app, token: jwt.generateHouseholdToken('household-1') };
}

describe('household Kabadiwala directory', () => {
  it('returns paginated rounded distance and public aggregates without exact coordinates', async () => {
    const findManyProfiles = vi.fn().mockResolvedValue([
      { id: 'collector-near', displayName: 'Asha', areaName: 'Kothrud, Pune', latitude: 0.01, longitude: 0, dailyPickupCapacity: 8, pilotVerifiedAt: new Date() },
      { id: 'collector-far', displayName: 'Rafiq', areaName: 'Pune', latitude: 2, longitude: 0, dailyPickupCapacity: 8, pilotVerifiedAt: new Date() }
    ]);
    const db = {
      user: {
        findFirst: vi.fn().mockResolvedValue({ role: 'HOUSEHOLD', accountStatus: 'ACTIVE' }),
        findMany: vi.fn().mockResolvedValue([{ collectorProfileId: 'collector-near' }, { collectorProfileId: 'collector-far' }])
      },
      collector: { findMany: findManyProfiles },
      pickupRequest: { findMany: vi.fn().mockResolvedValue([{ kabadiwalaId: 'collector-near', actualWeight: 4.25, finalCategory: 'CABLE' }]) },
      householdPickupReview: { findMany: vi.fn().mockResolvedValue([{ kabadiwalaId: 'collector-near', rating: 5 }, { kabadiwalaId: 'collector-near', rating: 4 }]) },
      collectorPickupDay: { findMany: vi.fn().mockResolvedValue([{ collectorId: 'collector-near', capacity: 8, bookedCount: 3 }]) }
    } as any;
    const { app, token } = householdApp(db);

    const response = await request(app)
      .get('/api/v1/household/kabadiwalas?latitude=0&longitude=0&radiusKm=5&page=1&limit=1')
      .set('Authorization', `Bearer ${token}`);

    expect(response.status).toBe(200);
    expect(response.body.data).toMatchObject({
      pagination: { page: 1, limit: 1, total: 1, totalPages: 1 },
      items: [{
        id: 'collector-near',
        displayName: 'Asha',
        areaName: 'Kothrud, Pune',
        verified: true,
        distanceKm: 1.1,
        acceptingPickups: true,
        availablePickupSlots: 5,
        completedPickupCount: 1,
        acceptedWeightKg: 4.25,
        collectedMaterials: ['CABLE'],
        ratingAverage: 4.5,
        reviewCount: 2
      }]
    });
    expect(JSON.stringify(response.body)).not.toContain('latitude');
    expect(JSON.stringify(response.body)).not.toContain('longitude');
    expect(JSON.stringify(response.body)).not.toContain('0.01');
    expect(findManyProfiles).toHaveBeenCalledWith(expect.objectContaining({ where: expect.objectContaining({ pilotVerifiedAt: { not: null } }), select: expect.not.objectContaining({ phone: true, email: true }) }));
  });

  it('requires a selected area or an explicit device location instead of listing everyone nationwide', async () => {
    const findManyUsers = vi.fn();
    const db = {
      user: { findFirst: vi.fn().mockResolvedValue({ role: 'HOUSEHOLD', accountStatus: 'ACTIVE' }), findMany: findManyUsers },
      collector: { findMany: vi.fn() }
    } as any;
    const { app, token } = householdApp(db);

    const response = await request(app).get('/api/v1/household/kabadiwalas').set('Authorization', `Bearer ${token}`);

    expect(response.status).toBe(200);
    expect(response.body.data).toMatchObject({ items: [], requiresLocation: true, pagination: { total: 0 } });
    expect(findManyUsers).not.toHaveBeenCalled();
  });

  it('allows a household to rate only its own completed pickup and returns a verified aggregate', async () => {
    const createReview = vi.fn().mockResolvedValue({ id: 'review-1', rating: 5 });
    const db = {
      user: { findFirst: vi.fn().mockResolvedValue({ role: 'HOUSEHOLD', accountStatus: 'ACTIVE' }) },
      pickupRequest: { findFirst: vi.fn().mockResolvedValue({ id: 'pickup-1', kabadiwalaId: 'collector-1' }) },
      householdPickupReview: {
        findUnique: vi.fn().mockResolvedValue(null),
        findMany: vi.fn().mockResolvedValue([{ rating: 5 }, { rating: 4 }])
      },
      $transaction: vi.fn(async (callback: (tx: any) => unknown) => callback({
        householdPickupReview: { create: createReview },
        auditEvent: { create: vi.fn().mockResolvedValue({}) },
        materialPassportEvent: { create: vi.fn().mockResolvedValue({}) }
      }))
    } as any;
    const { app, token } = householdApp(db);

    const response = await request(app)
      .post('/api/v1/household/pickups/pickup-1/review')
      .set('Authorization', `Bearer ${token}`)
      .send({ rating: 5 });

    expect(response.status).toBe(201);
    expect(response.body.data).toMatchObject({ pickupId: 'pickup-1', rating: 5, verified: true, ratingAverage: 4.5, reviewCount: 2 });
    expect(db.pickupRequest.findFirst).toHaveBeenCalledWith(expect.objectContaining({ where: { id: 'pickup-1', householdId: 'household-1', status: 'COMPLETED', kabadiwalaId: { not: null } } }));
    expect(createReview).toHaveBeenCalledWith({ data: { pickupId: 'pickup-1', householdId: 'household-1', kabadiwalaId: 'collector-1', rating: 5, verified: true } });
  });

  it('rejects reviews before pickup completion', async () => {
    const db = {
      user: { findFirst: vi.fn().mockResolvedValue({ role: 'HOUSEHOLD', accountStatus: 'ACTIVE' }) },
      pickupRequest: { findFirst: vi.fn().mockResolvedValue(null) },
      householdPickupReview: { findUnique: vi.fn() }
    } as any;
    const { app, token } = householdApp(db);

    const response = await request(app)
      .post('/api/v1/household/pickups/pickup-1/review')
      .set('Authorization', `Bearer ${token}`)
      .send({ rating: 5 });

    expect(response.status).toBe(409);
    expect(response.body.error.code).toBe('CONFLICT');
  });
});
