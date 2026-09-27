import express from 'express';
import request from 'supertest';
import { describe, expect, it, vi } from 'vitest';
import { formalisationRoutes } from '../src/routes/formalisationRoutes.js';
import { JwtService } from '../src/services/jwt.js';
import { errorHandler } from '../src/middleware/errors.js';

describe('recycler handover QR renewal', () => {
  it('replaces an expired QR without releasing the accepted lot', async () => {
    const prior = {
      id: 'old-handover', sourceKey: 'BULK:bulk-1', status: 'COLLECTOR_CONFIRMED',
      expiresAt: new Date(Date.now() - 60_000)
    };
    const lot = {
      id: 'bulk-1', kabadiwalaId: 'collector-1', status: 'RESERVED',
      materialCategory: 'COPPER', quantityKg: 5, sourceListingIds: []
    };
    const created = vi.fn(async ({ data }: any) => ({ id: 'new-handover', ...data }));
    const db: any = {
      user: { findFirst: vi.fn().mockResolvedValue({ role: 'COLLECTOR', accountStatus: 'ACTIVE' }) },
      bulkLot: { findFirst: vi.fn().mockResolvedValue(lot) },
      bulkOffer: { findFirst: vi.fn().mockResolvedValue({ offeredRatePerKg: 50, recyclerId: 'recycler-1' }) },
      recycler: { findUnique: vi.fn().mockResolvedValue({ id: 'recycler-1', authorizationStatus: 'VERIFIED', rates: [] }) },
      supplyHandover: { findUnique: vi.fn().mockResolvedValue(prior), update: vi.fn().mockResolvedValue({ ...prior, status: 'EXPIRED' }), create: created },
      auditEvent: { create: vi.fn().mockResolvedValue({}) },
      materialPassportEvent: { create: vi.fn().mockResolvedValue({}) },
      $transaction: async (callback: any) => callback(db)
    };
    const jwt = new JwtService({ JWT_SECRET: 'handover-renewal-secret', JWT_EXPIRES_IN: '1h' } as any);
    const app = express();
    app.use(express.json());
    app.use('/api/v1', formalisationRoutes(jwt, { findById: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE' }) } as any, db, 'handover-signing-secret'));
    app.use(errorHandler);

    const response = await request(app)
      .post('/api/v1/kabadiwala/bulk-lots/bulk-1/prepare-handover')
      .set('Authorization', `Bearer ${jwt.generateToken('collector-1')}`)
      .send({});

    expect(response.status).toBe(201);
    expect(response.body.data.id).toBe('new-handover');
    expect(db.supplyHandover.update).toHaveBeenCalledWith(expect.objectContaining({
      where: { id: 'old-handover' },
      data: { status: 'EXPIRED', sourceKey: 'BULK:bulk-1:expired:old-handover' }
    }));
    expect(created).toHaveBeenCalledOnce();
    expect(db.bulkOffer.findFirst).toHaveBeenCalledWith({ where: { bulkLotId: 'bulk-1', status: 'ACCEPTED' } });
  });

  it('renews a pool QR after the pool moved to handover scheduling', async () => {
    const prior = { id: 'old-pool-handover', sourceKey: 'POOL:pool-1', status: 'PREPARED', expiresAt: new Date(Date.now() - 60_000) };
    const pool = { id: 'pool-1', createdByCollectorId: 'collector-1', status: 'PICKUP_SCHEDULED', totalReservedKg: 5, minimumQuantityKg: 5, materialCategory: 'COPPER', recyclerId: 'recycler-1' };
    const db: any = {
      user: { findFirst: vi.fn().mockResolvedValue({ role: 'COLLECTOR', accountStatus: 'ACTIVE' }) },
      pooledConsignment: { findFirst: vi.fn().mockResolvedValue(pool), update: vi.fn().mockResolvedValue(pool) },
      poolContribution: { findMany: vi.fn().mockResolvedValue([{ id: 'contribution-1', status: 'RESERVED', quantityKg: 5, sourceListingIds: [] }]), updateMany: vi.fn().mockResolvedValue({ count: 1 }) },
      recycler: { findUnique: vi.fn().mockResolvedValue({ id: 'recycler-1', authorizationStatus: 'VERIFIED', rates: [{ materialCategory: 'COPPER', pricePerKg: 50 }] }) },
      supplyHandover: { findUnique: vi.fn().mockResolvedValue(prior), update: vi.fn().mockResolvedValue({ ...prior, status: 'EXPIRED' }), create: vi.fn(async ({ data }: any) => ({ id: 'new-pool-handover', ...data })) },
      auditEvent: { create: vi.fn().mockResolvedValue({}) },
      materialPassportEvent: { create: vi.fn().mockResolvedValue({}) },
      $transaction: async (callback: any) => callback(db)
    };
    const jwt = new JwtService({ JWT_SECRET: 'handover-renewal-secret', JWT_EXPIRES_IN: '1h' } as any);
    const app = express();
    app.use(express.json());
    app.use('/api/v1', formalisationRoutes(jwt, { findById: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE' }) } as any, db, 'handover-signing-secret'));
    app.use(errorHandler);

    const response = await request(app)
      .post('/api/v1/kabadiwala/pools/pool-1/prepare-handover')
      .set('Authorization', `Bearer ${jwt.generateToken('collector-1')}`)
      .send({});

    expect(response.status).toBe(201);
    expect(response.body.data.id).toBe('new-pool-handover');
    expect(db.supplyHandover.update).toHaveBeenCalledWith(expect.objectContaining({ where: { id: 'old-pool-handover' } }));
    expect(db.poolContribution.updateMany).toHaveBeenCalledOnce();
  });
});
