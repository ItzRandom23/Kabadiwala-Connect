import { afterEach, describe, expect, it, vi } from 'vitest';
import { generateKeyPairSync } from 'node:crypto';
import { FcmPushProvider, NotificationDeliveryService } from '../src/services/notificationDeliveryService.js';
import type { AppConfig } from '../src/config/env.js';

const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 1024 });
const privateKeyPem = privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();

const pushConfig = {
  NOTIFICATION_PUSH_PROVIDER: 'fcm',
  NOTIFICATION_PUSH_ENABLED: true,
  FCM_PROJECT_ID: 'kabadiwala-staging',
  FCM_CLIENT_EMAIL: 'firebase-adminsdk@example.iam.gserviceaccount.com',
  FCM_PRIVATE_KEY: privateKeyPem,
  NOTIFICATION_SMS_PROVIDER: 'disabled',
  NOTIFICATION_SMS_ENABLED: false
} as AppConfig;

describe('FCM push delivery', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('exchanges a server-only service-account assertion and sends HTTP v1 data', async () => {
    let requestBodies: string[] = [];
    vi.stubGlobal('fetch', vi.fn(async (_url: string, init: RequestInit) => {
      requestBodies.push(String(init.body));
      if (requestBodies.length === 1) return new Response(JSON.stringify({ access_token: 'oauth-access-token', expires_in: 3600 }), { status: 200 });
      return new Response(JSON.stringify({ name: 'projects/kabadiwala-staging/messages/1' }), { status: 200 });
    }));

    const result = await new FcmPushProvider(pushConfig).send({ token: 'fcm-device-token', title: 'Pickup', body: 'Confirmed', route: 'home', type: 'PICKUP_CONFIRMED', notificationId: 'event-1' });

    expect(result).toEqual({ providerMessageId: 'projects/kabadiwala-staging/messages/1' });
    expect(requestBodies[0]).toContain('assertion=');
    expect(requestBodies[0]).not.toContain(privateKeyPem);
    expect(JSON.parse(requestBodies[1])).toEqual({
      message: {
        token: 'fcm-device-token',
        notification: { title: 'Pickup', body: 'Confirmed' },
        data: { notificationId: 'event-1', type: 'PICKUP_CONFIRMED', route: 'home' }
      }
    });
  });

  it('fans out once per device and records independent target state', async () => {
    const parent = { id: 'delivery-1', notificationId: 'event-1', accountId: 'account-1', channel: 'PUSH', status: 'PENDING', attempts: 0, nextAttemptAt: new Date() };
    const targets: any[] = [];
    const targetStore = {
      upsert: vi.fn(async ({ where, update, create }: any) => {
        const existing = targets.find(item => item.deliveryId === where.deliveryId_deviceId.deliveryId && item.deviceId === where.deliveryId_deviceId.deviceId);
        if (existing) Object.assign(existing, update);
        else targets.push({ ...create, id: `target-${targets.length + 1}`, claimedAt: null });
      }),
      findMany: vi.fn(async ({ select }: any) => select ? targets.map(({ status, nextAttemptAt }) => ({ status, nextAttemptAt })) : targets.filter(item => item.status === 'PENDING' || item.status === 'RETRY')),
      updateMany: vi.fn(async ({ where, data }: any) => {
        const target = targets.find(item => item.id === where.id && item.status === where.status);
        if (!target) return { count: 0 };
        target.status = data.status;
        target.claimedAt = data.claimedAt;
        target.attempts += data.attempts.increment;
        return { count: 1 };
      }),
      update: vi.fn(async ({ where, data }: any) => {
        const target = targets.find(item => item.id === where.id);
        Object.assign(target, data);
      })
    };
    const parentUpdate = vi.fn(async ({ data }: any) => Object.assign(parent, data));
    const fakeDb = {
      notificationDelivery: {
        findMany: vi.fn().mockResolvedValue([parent]),
        updateMany: vi.fn().mockImplementation(async ({ data }: any) => { parent.status = data.status; parent.attempts += data.attempts.increment; return { count: 1 }; }),
        update: parentUpdate
      },
      notificationEvent: { findUnique: vi.fn().mockResolvedValue({ id: 'event-1', type: 'PICKUP_CONFIRMED', title: 'Pickup', body: 'Confirmed', route: 'home' }) },
      user: { findFirst: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE', pushNotificationsEnabled: true }) },
      notificationDevice: {
        findMany: vi.fn().mockResolvedValue([{ id: 'device-1', token: 'token-1' }, { id: 'device-2', token: 'token-2' }]),
        updateMany: vi.fn().mockResolvedValue({ count: 0 })
      },
      notificationDeliveryTarget: targetStore
    };
    const send = vi.fn().mockResolvedValue({ providerMessageId: 'provider-message' });

    const result = await new NotificationDeliveryService(fakeDb as never, pushConfig, undefined, { send }).dispatchPendingPush();

    expect(result).toEqual({ processed: 1, sent: 1, retried: 0, skipped: false });
    expect(send).toHaveBeenCalledTimes(2);
    expect(targets.map(item => item.status)).toEqual(['SENT', 'SENT']);
    expect(parentUpdate).toHaveBeenCalledWith({ where: { id: 'delivery-1' }, data: expect.objectContaining({ status: 'SENT' }) });
  });
});
