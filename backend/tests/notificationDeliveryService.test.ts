import { afterEach, describe, expect, it, vi } from 'vitest';
import { NotificationDeliveryService, TwoFactorTransactionalSmsProvider } from '../src/services/notificationDeliveryService.js';
import type { AppConfig } from '../src/config/env.js';

const providerConfig = {
  TWOFACTOR_API_KEY: 'test-key',
  TWOFACTOR_BASE_URL: 'https://2factor.in',
  TWOFACTOR_SMS_SENDER_ID: 'KABADI',
  NOTIFICATION_SMS_PROVIDER: 'twofactor',
  NOTIFICATION_SMS_ENABLED: true
} as AppConfig;

describe('notification SMS delivery', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('uses the server-side 2Factor key and does not put it in the message body', async () => {
    let request: { url: string; init: RequestInit } | undefined;
    vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit) => {
      request = { url, init };
      return new Response(JSON.stringify({ Status: 'Success', Details: 'provider-message-1' }), { status: 200 });
    }));

    const result = await new TwoFactorTransactionalSmsProvider(providerConfig).send({ to: '+919876543210', message: 'Your pickup is confirmed.' });
    expect(request?.url).toBe('https://2factor.in/API/V1/test-key/ADDON_SERVICES/SEND/TSMS');
    expect(JSON.parse(String(request?.init.body))).toEqual({ From: 'KABADI', To: '+919876543210', Msg: 'Your pickup is confirmed.' });
    expect(JSON.stringify(request?.init.body)).not.toContain('TWOFACTOR');
    expect(result).toEqual({ providerMessageId: 'provider-message-1' });
  });

  it('claims, sends and records one pending delivery', async () => {
    const row = { id: 'delivery-1', notificationId: 'event-1', accountId: 'account-1', channel: 'SMS', status: 'PENDING', attempts: 0 };
    const findMany = vi.fn().mockResolvedValue([row]);
    const claim = vi.fn().mockResolvedValue({ count: 1 });
    const update = vi.fn().mockResolvedValue({});
    const send = vi.fn().mockResolvedValue({ providerMessageId: 'message-1' });
    const fakeDb = {
      notificationDelivery: { findMany, updateMany: claim, update },
      notificationEvent: { findUnique: vi.fn().mockResolvedValue({ id: 'event-1', body: 'Pickup confirmed.' }) },
      user: { findFirst: vi.fn().mockResolvedValue({ phone: '9876543210', accountStatus: 'ACTIVE' }) },
      collector: { findUnique: vi.fn().mockResolvedValue(null) },
      recycler: { findUnique: vi.fn().mockResolvedValue(null) }
    };

    const result = await new NotificationDeliveryService(fakeDb as never, providerConfig, { send }).dispatchPendingSms();

    expect(result).toEqual({ processed: 1, sent: 1, retried: 0, skipped: false });
    expect(send).toHaveBeenCalledWith({ to: '+919876543210', message: 'Pickup confirmed.' });
    expect(update).toHaveBeenCalledWith({ where: { id: 'delivery-1' }, data: expect.objectContaining({ status: 'SENT', providerMessageId: 'message-1' }) });
  });

  it('skips SMS for an account that has opted out', async () => {
    const update = vi.fn().mockResolvedValue({});
    const send = vi.fn();
    const fakeDb = {
      notificationDelivery: {
        findMany: vi.fn().mockResolvedValue([{ id: 'delivery-1', notificationId: 'event-1', accountId: 'account-1', status: 'PENDING', attempts: 0 }]),
        updateMany: vi.fn().mockResolvedValue({ count: 1 }),
        update
      },
      notificationEvent: { findUnique: vi.fn().mockResolvedValue({ id: 'event-1', body: 'Pickup confirmed.' }) },
      user: { findFirst: vi.fn().mockResolvedValue({ phone: '9876543210', accountStatus: 'ACTIVE', smsNotificationsEnabled: false }) },
      collector: { findUnique: vi.fn().mockResolvedValue(null) },
      recycler: { findUnique: vi.fn().mockResolvedValue(null) }
    };

    const result = await new NotificationDeliveryService(fakeDb as never, providerConfig, { send }).dispatchPendingSms();

    expect(result).toEqual({ processed: 1, sent: 0, retried: 0, skipped: false });
    expect(send).not.toHaveBeenCalled();
    expect(update).toHaveBeenCalledWith({ where: { id: 'delivery-1' }, data: { status: 'SKIPPED', claimedAt: null, lastError: 'SMS_NOT_OPTED_IN' } });
  });

  it('schedules a bounded retry after a provider failure without exposing provider details', async () => {
    const update = vi.fn().mockResolvedValue({});
    const fakeDb = {
      notificationDelivery: {
        findMany: vi.fn().mockResolvedValue([{ id: 'delivery-1', notificationId: 'event-1', accountId: 'account-1', status: 'PENDING', attempts: 0 }]),
        updateMany: vi.fn().mockResolvedValue({ count: 1 }),
        update
      },
      notificationEvent: { findUnique: vi.fn().mockResolvedValue({ id: 'event-1', body: 'Pickup confirmed.' }) },
      user: { findFirst: vi.fn().mockResolvedValue({ phone: '9876543210', accountStatus: 'ACTIVE' }) },
      collector: { findUnique: vi.fn().mockResolvedValue(null) },
      recycler: { findUnique: vi.fn().mockResolvedValue(null) }
    };
    const service = new NotificationDeliveryService(fakeDb as never, providerConfig, { send: vi.fn().mockRejectedValue(new Error('secret provider response')) });

    const result = await service.dispatchPendingSms();

    expect(result).toEqual({ processed: 1, sent: 0, retried: 1, skipped: false });
    expect(update).toHaveBeenCalledWith({ where: { id: 'delivery-1' }, data: expect.objectContaining({ status: 'RETRY', lastError: 'SMS_PROVIDER_ERROR', nextAttemptAt: expect.any(Date) }) });
    expect(JSON.stringify(update.mock.calls)).not.toContain('secret provider response');
  });

  it('reclaims a stale processing row after a worker restart', async () => {
    const stale = new Date(Date.now() - 11 * 60 * 1000);
    const updateMany = vi.fn().mockResolvedValue({ count: 1 });
    const update = vi.fn().mockResolvedValue({});
    const fakeDb = {
      notificationDelivery: {
        findMany: vi.fn().mockResolvedValue([{ id: 'delivery-1', notificationId: 'event-1', accountId: 'account-1', status: 'PROCESSING', attempts: 1, claimedAt: stale }]),
        updateMany,
        update
      },
      notificationEvent: { findUnique: vi.fn().mockResolvedValue({ id: 'event-1', body: 'Pickup confirmed.' }) },
      user: { findFirst: vi.fn().mockResolvedValue({ phone: '9876543210', accountStatus: 'ACTIVE' }) },
      collector: { findUnique: vi.fn().mockResolvedValue(null) },
      recycler: { findUnique: vi.fn().mockResolvedValue(null) }
    };

    await new NotificationDeliveryService(fakeDb as never, providerConfig, { send: vi.fn().mockResolvedValue({}) }).dispatchPendingSms();

    expect(updateMany).toHaveBeenCalledWith(expect.objectContaining({ where: { id: 'delivery-1', status: 'PROCESSING', claimedAt: stale } }));
  });
});
