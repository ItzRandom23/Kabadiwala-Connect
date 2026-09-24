import { describe, expect, it, vi } from 'vitest';
import express from 'express';
import request from 'supertest';
import { JwtService } from '../src/services/jwt.js';
import { supplyChainRoutes } from '../src/routes/supplyChainRoutes.js';
import { errorHandler } from '../src/middleware/errors.js';

const config = { JWT_SECRET: 'a-secure-test-secret', JWT_EXPIRES_IN: '1h' } as never;

function adminApp(db: any) {
  const jwt = new JwtService(config);
  const app = express();
  app.use(express.json());
  app.use('/api/v1', supplyChainRoutes(jwt, { findById: vi.fn() } as never, db));
  app.use(errorHandler);
  return { app, token: jwt.generateAdminToken('admin-1') };
}

describe('pilot Kabadiwala verification', () => {
  it('lists only active, pending profiles in the operator queue', async () => {
    const findMany = vi.fn().mockResolvedValue([{ id: 'collector-1', displayName: 'Asha', areaName: 'Kothrud', createdAt: new Date(), pilotVerifiedAt: null }]);
    const db = {
      adminAccount: { findUnique: vi.fn().mockResolvedValue({ active: true, permissions: ['PARTNER_VERIFICATION'] }) },
      user: { findMany: vi.fn().mockResolvedValue([{ collectorProfileId: 'collector-1' }]) },
      collector: { findMany }
    } as any;
    const { app, token } = adminApp(db);

    const response = await request(app).get('/api/v1/admin/kabadiwala-cohort?status=PENDING').set('Authorization', `Bearer ${token}`);

    expect(response.status).toBe(200);
    expect(response.body.data).toMatchObject([{ id: 'collector-1', displayName: 'Asha', areaName: 'Kothrud', verifiedAt: null }]);
    expect(findMany).toHaveBeenCalledWith(expect.objectContaining({ where: expect.objectContaining({ accountStatus: 'ACTIVE', pilotVerifiedAt: null }) }));
  });

  it('requires an audit note and records the approving operator', async () => {
    const update = vi.fn().mockResolvedValue({ id: 'collector-1', displayName: 'Asha', areaName: 'Kothrud', pilotVerifiedAt: new Date() });
    const audit = vi.fn().mockResolvedValue({});
    const passport = vi.fn().mockResolvedValue({});
    const db = {
      adminAccount: { findUnique: vi.fn().mockResolvedValue({ active: true, permissions: ['PARTNER_VERIFICATION'] }) },
      user: { findFirst: vi.fn().mockResolvedValue({ id: 'account-1' }) },
      collector: { findFirst: vi.fn().mockResolvedValue({ id: 'collector-1' }) },
      $transaction: vi.fn(async (callback: (tx: any) => unknown) => callback({ collector: { update }, auditEvent: { create: audit }, materialPassportEvent: { create: passport } }))
    } as any;
    const { app, token } = adminApp(db);

    const response = await request(app).post('/api/v1/admin/kabadiwala-cohort/collector-1/verification')
      .set('Authorization', `Bearer ${token}`).send({ decision: 'APPROVE', notes: 'Identity and pilot service area checked' });

    expect(response.status).toBe(200);
    expect(response.body.data).toMatchObject({ id: 'collector-1', areaName: 'Kothrud' });
    expect(update).toHaveBeenCalledWith({ where: { id: 'collector-1' }, data: { pilotVerifiedAt: expect.any(Date), pilotVerifiedBy: 'admin-1' } });
    expect(audit).toHaveBeenCalledWith(expect.objectContaining({ data: expect.objectContaining({ actorId: 'admin-1', actorRole: 'ADMIN', event: 'KABADIWALA_PILOT_APPROVED' }) }));
  });
});
