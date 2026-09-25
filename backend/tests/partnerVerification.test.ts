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

describe('Kabadiwala account verification', () => {
  it('does not expose Kabadiwala approval through admin endpoints', async () => {
    const { app, token } = adminApp({ adminAccount: { findUnique: vi.fn() } });

    const queue = await request(app).get('/api/v1/admin/kabadiwala-cohort?status=PENDING').set('Authorization', `Bearer ${token}`);
    const verification = await request(app).post('/api/v1/admin/kabadiwala-cohort/collector-1/verification')
      .set('Authorization', `Bearer ${token}`).send({ decision: 'APPROVE', notes: 'No longer an admin action' });

    expect(queue.status).toBe(404);
    expect(verification.status).toBe(404);
  });
});
