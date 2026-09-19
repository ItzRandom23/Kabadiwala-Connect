import { describe, expect, it, vi } from 'vitest';
import express from 'express';
import request from 'supertest';
import { JwtService } from '../src/services/jwt.js';
import { futureRoutes } from '../src/routes/futureRoutes.js';
import { errorHandler } from '../src/middleware/errors.js';

describe('account notification preferences', () => {
  it('reads and updates SMS and push consent for the authenticated account', async () => {
    const user = {
      id: 'user-1', role: 'HOUSEHOLD', preferredLanguage: 'ENGLISH', appearanceMode: 'SYSTEM',
      smsNotificationsEnabled: false, pushNotificationsEnabled: true
    };
    const update = vi.fn().mockImplementation(async ({ data }: { data: Record<string, unknown> }) => ({ ...user, ...data }));
    const db = {
      collector: { findUnique: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE' }) },
      user: { findFirst: vi.fn().mockResolvedValue(user), update }
    } as never;
    const jwt = new JwtService({ JWT_SECRET: 'a-secure-test-secret', JWT_EXPIRES_IN: '1h' } as never);
    const app = express();
    app.use(express.json());
    app.use('/future', futureRoutes(jwt, db));
    app.use(errorHandler);
    const auth = { Authorization: `Bearer ${jwt.generateHouseholdToken('account-1')}` };

    const read = await request(app).get('/future/preferences').set(auth);
    expect(read.status).toBe(200);
    expect(read.body.data).toMatchObject({ smsNotificationsEnabled: false, pushNotificationsEnabled: true });

    const saved = await request(app).patch('/future/preferences').set(auth).send({ smsNotificationsEnabled: true, pushNotificationsEnabled: false });
    expect(saved.status).toBe(200);
    expect(saved.body.data).toMatchObject({ smsNotificationsEnabled: true, pushNotificationsEnabled: false });
    expect(update).toHaveBeenCalledWith(expect.objectContaining({ data: { smsNotificationsEnabled: true, pushNotificationsEnabled: false } }));
  });
});
