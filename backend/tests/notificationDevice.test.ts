import { describe, expect, it, vi } from 'vitest';
import express from 'express';
import request from 'supertest';
import { JwtService } from '../src/services/jwt.js';
import { NotificationDeviceService } from '../src/services/notificationDeviceService.js';
import { notificationRoutes } from '../src/routes/notificationRoutes.js';
import { errorHandler } from '../src/middleware/errors.js';

const token = 'fcm-token-for-account-one-1234567890';
const config = { JWT_SECRET: 'a-secure-test-secret', JWT_EXPIRES_IN: '1h' } as never;

describe('notification device registration', () => {
  it('moves a token to the current account and returns no token material', async () => {
    const upsert = vi.fn().mockResolvedValue({
      id: 'device-1', platform: 'ANDROID', appVersion: '1.2.3', enabled: true,
      lastSeenAt: new Date('2026-09-18T00:00:00.000Z'), createdAt: new Date('2026-09-18T00:00:00.000Z')
    });
    const db = { notificationDevice: { upsert } } as never;
    const result = await new NotificationDeviceService(db).register('account-2', { token, platform: 'ANDROID', appVersion: '1.2.3' });

    expect(upsert).toHaveBeenCalledWith(expect.objectContaining({
      where: { token },
      update: expect.objectContaining({ accountId: 'account-2', enabled: true }),
      create: expect.objectContaining({ accountId: 'account-2', token })
    }));
    expect(result).not.toHaveProperty('token');
  });

  it('invalidates queued targets before moving a token to another account', async () => {
    const updateMany = vi.fn().mockResolvedValue({ count: 1 });
    const db = {
      notificationDevice: {
        findUnique: vi.fn().mockResolvedValue({ id: 'device-1', accountId: 'account-1' }),
        upsert: vi.fn().mockResolvedValue({ id: 'device-1', platform: 'ANDROID', enabled: true })
      },
      notificationDeliveryTarget: { updateMany }
    } as never;

    await new NotificationDeviceService(db).register('account-2', { token, platform: 'ANDROID' });

    expect(updateMany).toHaveBeenCalledWith({
      where: { deviceId: 'device-1', accountId: 'account-1', status: { in: ['PENDING', 'RETRY', 'PROCESSING'] } },
      data: { status: 'SKIPPED', claimedAt: null, lastError: 'DEVICE_REASSIGNED' }
    });
  });

  it('protects registration, listing, and unregistering with the account token', async () => {
    const deviceDb = {
      collector: { findUnique: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE' }) },
      user: { findFirst: vi.fn().mockResolvedValue({ role: 'HOUSEHOLD', accountStatus: 'ACTIVE' }) },
      notificationDevice: {
        findUnique: vi.fn().mockResolvedValue({ id: 'device-1', accountId: 'account-1' }),
        upsert: vi.fn().mockResolvedValue({ id: 'device-1', platform: 'ANDROID', appVersion: null, enabled: true, lastSeenAt: new Date(), createdAt: new Date() }),
        findMany: vi.fn().mockResolvedValue([{ id: 'device-1', platform: 'ANDROID', appVersion: null, enabled: true, lastSeenAt: new Date(), createdAt: new Date() }]),
        deleteMany: vi.fn().mockResolvedValue({ count: 1 })
      },
      notificationEvent: {},
      notificationDeliveryTarget: { updateMany: vi.fn().mockResolvedValue({ count: 1 }) }
    };
    const db = deviceDb as never;
    const jwt = new JwtService(config);
    const app = express();
    app.use(express.json());
    app.use('/notifications', notificationRoutes(jwt, db));
    app.use(errorHandler);

    expect((await request(app).post('/notifications/devices').send({ token })).status).toBe(401);
    const auth = { Authorization: `Bearer ${jwt.generateHouseholdToken('account-1')}` };
    const registered = await request(app).post('/notifications/devices').set(auth).send({ token, platform: 'ANDROID', appVersion: '1.2.3' });
    expect(registered.status).toBe(201);
    expect(registered.body.data).not.toHaveProperty('token');

    const listed = await request(app).get('/notifications/devices').set(auth);
    expect(listed.status).toBe(200);
    expect(listed.body.data).toHaveLength(1);

    const invalid = await request(app).post('/notifications/devices/unregister').set(auth).send({ token: 'short' });
    expect(invalid.status).toBe(422);

    const removed = await request(app).post('/notifications/devices/unregister').set(auth).send({ token });
    expect(removed.status).toBe(200);
    expect(removed.body.data.removed).toBe(true);
    expect(deviceDb.notificationDevice.deleteMany).toHaveBeenCalledWith({ where: { accountId: 'account-1', token } });
  });

  it('invalidates queued targets before unregistering a token', async () => {
    const updateMany = vi.fn().mockResolvedValue({ count: 1 });
    const db = {
      notificationDevice: {
        findUnique: vi.fn().mockResolvedValue({ id: 'device-1', accountId: 'account-1' }),
        deleteMany: vi.fn().mockResolvedValue({ count: 1 })
      },
      notificationDeliveryTarget: { updateMany }
    } as never;

    await new NotificationDeviceService(db).unregister('account-1', token);

    expect(updateMany).toHaveBeenCalledWith({
      where: { deviceId: 'device-1', accountId: 'account-1', status: { in: ['PENDING', 'RETRY', 'PROCESSING'] } },
      data: { status: 'SKIPPED', claimedAt: null, lastError: 'DEVICE_UNREGISTERED' }
    });
  });
});
