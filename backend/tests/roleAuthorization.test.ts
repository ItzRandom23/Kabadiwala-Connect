import express from 'express';
import request from 'supertest';
import { describe, expect, it, vi } from 'vitest';
import { JwtService } from '../src/services/jwt.js';
import { requireAccount, requireAuth, requireHousehold } from '../src/middleware/auth.js';
import { errorHandler } from '../src/middleware/errors.js';

const config = { JWT_SECRET: 'role-boundary-test-secret', JWT_EXPIRES_IN: '1h' } as any;
const jwt = new JwtService(config);
const profiles = { findById: async (id: string) => ({ id, accountStatus: 'ACTIVE' }) } as any;

function protectedApp() {
  const app = express();
  app.get('/kabadiwala', requireAuth(jwt, profiles), (_req, res) => res.json({ ok: true }));
  app.get('/household', requireHousehold(jwt, profiles), (_req, res) => res.json({ ok: true }));
  app.use((error: any, _req: any, res: any, _next: any) => res.status(error.status ?? 500).json({ code: error.code }));
  return app;
}

function linkedAccountApp(role: 'COLLECTOR' | 'HOUSEHOLD', userOverride: any = { role, accountStatus: 'ACTIVE' }) {
  const app = express();
  const db = { user: { findFirst: vi.fn().mockResolvedValue(userOverride) }, collector: { findUnique: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE' }) } } as any;
  app.get('/kabadiwala', requireAuth(jwt, profiles, db), (_req, res) => res.json({ ok: true }));
  app.get('/household', requireHousehold(jwt, profiles, db), (_req, res) => res.json({ ok: true }));
  app.get('/account', requireAccount(jwt, db, false), (_req, res) => res.json({ ok: true }));
  app.use((error: any, _req: any, res: any, _next: any) => res.status(error.status ?? 500).json({ code: error.code }));
  return app;
}

describe('role boundaries', () => {
  it('never treats a household profile as kabadiwala authority', async () => {
    const response = await request(protectedApp()).get('/kabadiwala').set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-a')}`);
    expect(response.status).toBe(403);
    expect(response.body.code).toBe('AUTHORIZATION_ERROR');
  });

  it('denies collector and recycler tokens from household-only mutations', async () => {
    await request(protectedApp()).get('/household').set('Authorization', `Bearer ${jwt.generateToken('kabadiwala-a')}`).expect(403);
    await request(protectedApp()).get('/household').set('Authorization', `Bearer ${jwt.generateRecyclerToken('recycler-a')}`).expect(403);
  });

  it('accepts the correct token for each role', async () => {
    await request(protectedApp()).get('/kabadiwala').set('Authorization', `Bearer ${jwt.generateToken('kabadiwala-a')}`).expect(200);
    await request(protectedApp()).get('/household').set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-a')}`).expect(200);
  });

  it('rechecks the linked account role when the database is available', async () => {
    await request(linkedAccountApp('HOUSEHOLD')).get('/kabadiwala').set('Authorization', `Bearer ${jwt.generateToken('kabadiwala-a')}`).expect(403);
    await request(linkedAccountApp('COLLECTOR')).get('/household').set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-a')}`).expect(403);
    await request(linkedAccountApp('HOUSEHOLD')).get('/account').set('Authorization', `Bearer ${jwt.generateToken('kabadiwala-a')}`).expect(403);
  });

  it('rejects profile tokens when the database linkage is missing', async () => {
    await request(linkedAccountApp('COLLECTOR', null)).get('/kabadiwala')
      .set('Authorization', `Bearer ${jwt.generateToken('kabadiwala-a')}`)
      .expect(403);
    await request(linkedAccountApp('HOUSEHOLD', null)).get('/household')
      .set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-a')}`)
      .expect(403);
    await request(linkedAccountApp('COLLECTOR', null)).get('/account')
      .set('Authorization', `Bearer ${jwt.generateToken('kabadiwala-a')}`)
      .expect(403);
  });

  it('rejects suspended and deleted linked user accounts from generic account routes', async () => {
    await request(linkedAccountApp('COLLECTOR', { role: 'COLLECTOR', accountStatus: 'SUSPENDED' }))
      .get('/account')
      .set('Authorization', `Bearer ${jwt.generateToken('kabadiwala-suspended')}`)
      .expect(403, { code: 'ACCOUNT_SUSPENDED' });
    await request(linkedAccountApp('HOUSEHOLD', { role: 'HOUSEHOLD', accountStatus: 'DELETED' }))
      .get('/account')
      .set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-deleted')}`)
      .expect(403, { code: 'ACCOUNT_DELETED' });
  });

  it('keeps transient profile and database failures separate from invalid-token errors', async () => {
    const profileFailureApp = express();
    profileFailureApp.get('/kabadiwala', requireAuth(jwt, {
      findById: vi.fn().mockRejectedValue(new Error('profile database unavailable'))
    } as any), (_req, res) => res.json({ ok: true }));
    profileFailureApp.use(errorHandler);

    const profileResponse = await request(profileFailureApp)
      .get('/kabadiwala')
      .set('Authorization', `Bearer ${jwt.generateToken('collector-db-outage')}`);
    expect(profileResponse.status).toBe(500);
    expect(profileResponse.body.error.code).toBe('INTERNAL_SERVER_ERROR');
    expect(profileResponse.body.error.code).not.toBe('TOKEN_INVALID');

    const linkageFailureApp = express();
    const db = {
      collector: { findUnique: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE' }) },
      user: { findFirst: vi.fn().mockRejectedValue(new Error('account database unavailable')) }
    } as any;
    linkageFailureApp.get('/account', requireAccount(jwt, db, false), (_req, res) => res.json({ ok: true }));
    linkageFailureApp.use(errorHandler);

    const linkageResponse = await request(linkageFailureApp)
      .get('/account')
      .set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-db-outage')}`);
    expect(linkageResponse.status).toBe(500);
    expect(linkageResponse.body.error.code).toBe('INTERNAL_SERVER_ERROR');
    expect(linkageResponse.body.error.code).not.toBe('TOKEN_INVALID');
  });
});
