import express from 'express';
import request from 'supertest';
import { describe, expect, it } from 'vitest';
import { JwtService } from '../src/services/jwt.js';
import { requireAuth, requireHousehold } from '../src/middleware/auth.js';

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
});
