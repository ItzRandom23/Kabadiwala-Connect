import { createSign } from 'node:crypto';
import type { AppConfig } from '../config/env.js';
import type { PrismaClient } from '@prisma/client';

export type TransactionalSms = { to: string; message: string };
export type SmsSendResult = { providerMessageId?: string };

export interface TransactionalSmsProvider {
  send(input: TransactionalSms): Promise<SmsSendResult>;
}

export type PushNotification = {
  token: string;
  title: string;
  body: string;
  route?: string | null;
  type?: string | null;
  notificationId?: string;
};
export type PushSendResult = { providerMessageId?: string };

export interface PushProvider {
  send(input: PushNotification): Promise<PushSendResult>;
}

/** Provider response category used to disable only a dead device token. */
export class InvalidPushTokenError extends Error {
  constructor() {
    super('Push provider rejected the device token');
    this.name = 'InvalidPushTokenError';
  }
}

const timeout = () => AbortSignal.timeout(10_000);

function providerMessageId(body: unknown): string | undefined {
  if (!body || typeof body !== 'object') return undefined;
  const value = body as Record<string, unknown>;
  for (const key of ['Details', 'details', 'message_id', 'messageId', 'id', 'name']) {
    if (typeof value[key] === 'string' && value[key].trim()) return value[key].trim().slice(0, 200);
  }
  return undefined;
}

/**
 * Adapter for the documented 2Factor TSMS endpoint. 2Factor also exposes a
 * newer template-based API, but the account/API contract must be confirmed
 * before switching endpoints; this adapter is intentionally isolated so that
 * change does not touch notification/domain code.
 */
export class TwoFactorTransactionalSmsProvider implements TransactionalSmsProvider {
  constructor(private readonly config: AppConfig) {}

  private endpoint() {
    const base = (this.config.TWOFACTOR_BASE_URL?.trim() || 'https://2factor.in').replace(/\/+$/, '');
    return `${base}/API/V1/${encodeURIComponent(this.config.TWOFACTOR_API_KEY!)}/ADDON_SERVICES/SEND/TSMS`;
  }

  async send(input: TransactionalSms): Promise<SmsSendResult> {
    const response = await fetch(this.endpoint(), {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ From: this.config.TWOFACTOR_SMS_SENDER_ID, To: input.to, Msg: input.message }),
      signal: timeout()
    });
    const raw = await response.text();
    let parsed: unknown;
    try { parsed = raw ? JSON.parse(raw) : undefined; } catch { parsed = undefined; }
    const status = parsed && typeof parsed === 'object' ? (parsed as Record<string, unknown>).Status ?? (parsed as Record<string, unknown>).status : undefined;
    const statusText = typeof status === 'string' ? status.toLowerCase() : '';
    const accepted = response.ok && (!statusText || ['success', 'sent', 'queued', 'accepted'].includes(statusText));
    if (!accepted) throw new Error('2Factor transactional SMS rejected the request');
    return { providerMessageId: providerMessageId(parsed) };
  }
}

type FcmConfig = Pick<AppConfig, 'FCM_PROJECT_ID' | 'FCM_CLIENT_EMAIL' | 'FCM_PRIVATE_KEY'>;

const base64Url = (value: string | Buffer) => Buffer.from(value).toString('base64url');

/**
 * FCM HTTP v1 uses a Google service-account access token. The private key is
 * read only from server configuration and is never sent to the Android app.
 */
export class FcmPushProvider implements PushProvider {
  private cachedAccessToken: { value: string; expiresAt: number } | null = null;

  constructor(private readonly config: FcmConfig) {}

  private async accessToken() {
    const now = Math.floor(Date.now() / 1000);
    if (this.cachedAccessToken && this.cachedAccessToken.expiresAt > now + 60) return this.cachedAccessToken.value;
    const clientEmail = this.config.FCM_CLIENT_EMAIL;
    const privateKey = this.config.FCM_PRIVATE_KEY?.replace(/\\n/g, '\n');
    if (!clientEmail || !privateKey) throw new Error('FCM provider credentials are not configured');

    const header = base64Url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
    const payload = base64Url(JSON.stringify({
      iss: clientEmail,
      scope: 'https://www.googleapis.com/auth/firebase.messaging',
      aud: 'https://oauth2.googleapis.com/token',
      iat: now,
      exp: now + 3600
    }));
    const unsigned = `${header}.${payload}`;
    const signature = createSign('RSA-SHA256').update(unsigned).end().sign(privateKey);
    const assertion = `${unsigned}.${base64Url(signature)}`;
    const response = await fetch('https://oauth2.googleapis.com/token', {
      method: 'POST',
      headers: { 'content-type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }).toString(),
      signal: timeout()
    });
    if (!response.ok) throw new Error('FCM access token request failed');
    const parsed = await response.json() as { access_token?: unknown; expires_in?: unknown };
    if (typeof parsed.access_token !== 'string' || !parsed.access_token) throw new Error('FCM access token response was invalid');
    const expiresIn = typeof parsed.expires_in === 'number' ? parsed.expires_in : 3600;
    this.cachedAccessToken = { value: parsed.access_token, expiresAt: now + Math.max(300, expiresIn) };
    return parsed.access_token;
  }

  async send(input: PushNotification): Promise<PushSendResult> {
    const projectId = this.config.FCM_PROJECT_ID;
    if (!projectId) throw new Error('FCM project is not configured');
    const token = await this.accessToken();
    const response = await fetch(`https://fcm.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/messages:send`, {
      method: 'POST',
      headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' },
      body: JSON.stringify({
        message: {
          token: input.token,
          notification: { title: input.title, body: input.body },
          data: {
            notificationId: input.notificationId ?? '',
            type: input.type ?? '',
            route: input.route ?? ''
          }
        }
      }),
      signal: timeout()
    });
    const raw = await response.text();
    if (!response.ok) {
      const normalized = raw.toUpperCase();
      if (response.status === 404 || normalized.includes('UNREGISTERED')) throw new InvalidPushTokenError();
      throw new Error('FCM push request was rejected');
    }
    let parsed: unknown;
    try { parsed = raw ? JSON.parse(raw) : undefined; } catch { parsed = undefined; }
    return { providerMessageId: providerMessageId(parsed) };
  }
}

type DeliveryConfig = Pick<AppConfig,
  'NOTIFICATION_SMS_ENABLED' | 'NOTIFICATION_SMS_PROVIDER' | 'TWOFACTOR_API_KEY' |
  'TWOFACTOR_BASE_URL' | 'TWOFACTOR_SMS_SENDER_ID' | 'NOTIFICATION_PUSH_ENABLED' |
  'NOTIFICATION_PUSH_PROVIDER' | 'FCM_PROJECT_ID' | 'FCM_CLIENT_EMAIL' | 'FCM_PRIVATE_KEY'>;

const normalizePhone = (phone: string) => {
  const digits = phone.replace(/\D/g, '');
  if (/^[6-9]\d{9}$/.test(digits)) return `+91${digits}`;
  if (/^91[6-9]\d{9}$/.test(digits)) return `+${digits}`;
  if (phone.startsWith('+') && digits.length >= 8 && digits.length <= 15) return `+${digits}`;
  return null;
};

const retryAt = (attempts: number) => new Date(Date.now() + Math.min(60 * 60 * 1000, 2 ** Math.min(attempts, 10) * 1000));

/**
 * Claims and dispatches bounded provider outbox batches. Push fan-out creates
 * one target row per enabled device so a retry cannot resend to a device that
 * already accepted the notification.
 */
export class NotificationDeliveryService {
  private readonly provider: TransactionalSmsProvider | null;
  private readonly pushProvider: PushProvider | null;

  constructor(
    private readonly db: PrismaClient,
    private readonly config: DeliveryConfig,
    provider?: TransactionalSmsProvider,
    pushProvider?: PushProvider
  ) {
    this.provider = provider ?? (config.NOTIFICATION_SMS_PROVIDER === 'twofactor' ? new TwoFactorTransactionalSmsProvider(config as AppConfig) : null);
    this.pushProvider = pushProvider ?? (config.NOTIFICATION_PUSH_PROVIDER === 'fcm' ? new FcmPushProvider(config) : null);
  }

  async dispatchPendingSms(limit = 20) {
    if (this.config.NOTIFICATION_SMS_ENABLED !== true || !this.provider) return { processed: 0, sent: 0, retried: 0, skipped: true };
    const now = new Date();
    const staleClaimBefore = new Date(now.getTime() - 10 * 60 * 1000);
    const rows = await this.db.notificationDelivery.findMany({
      where: {
        channel: 'SMS',
        OR: [
          { status: { in: ['PENDING', 'RETRY'] }, nextAttemptAt: { lte: now } },
          { status: 'PROCESSING', claimedAt: { lt: staleClaimBefore } }
        ]
      },
      orderBy: { nextAttemptAt: 'asc' },
      take: Math.min(Math.max(limit, 1), 100)
    });
    let sent = 0;
    let retried = 0;
    for (const row of rows) {
      const claimed = await this.db.notificationDelivery.updateMany({
        where: row.status === 'PROCESSING'
          ? { id: row.id, status: 'PROCESSING', claimedAt: row.claimedAt }
          : { id: row.id, status: row.status },
        data: { status: 'PROCESSING', claimedAt: now, attempts: { increment: 1 } }
      });
      if (!claimed.count) continue;
      const attempts = row.attempts + 1;
      try {
        const event = await this.db.notificationEvent.findUnique({ where: { id: row.notificationId } });
        if (!event) {
          await this.db.notificationDelivery.update({ where: { id: row.id }, data: { status: 'SKIPPED', claimedAt: null, lastError: 'NOTIFICATION_EVENT_MISSING' } });
          continue;
        }
        const contact = await this.accountContact(row.accountId);
        if (!contact.phone) {
          await this.db.notificationDelivery.update({ where: { id: row.id }, data: { status: 'SKIPPED', claimedAt: null, lastError: contact.reason } });
          continue;
        }
        const result = await this.provider.send({ to: contact.phone, message: event.body });
        await this.db.notificationDelivery.update({
          where: { id: row.id },
          data: { status: 'SENT', claimedAt: null, sentAt: new Date(), providerMessageId: result.providerMessageId, lastError: null }
        });
        sent += 1;
      } catch {
        const terminal = attempts >= 5;
        await this.db.notificationDelivery.update({
          where: { id: row.id },
          data: { status: terminal ? 'FAILED' : 'RETRY', claimedAt: null, nextAttemptAt: retryAt(attempts), lastError: 'SMS_PROVIDER_ERROR' }
        });
        if (!terminal) retried += 1;
      }
    }
    return { processed: rows.length, sent, retried, skipped: false };
  }

  async dispatchPendingPush(limit = 20) {
    const db = this.db as any;
    if (this.config.NOTIFICATION_PUSH_ENABLED !== true || !this.pushProvider) {
      // Do not accumulate an unbounded backlog while push is intentionally
      // disabled. In-app inbox persistence remains the source of truth.
      await db.notificationDelivery?.updateMany?.({
        where: { channel: 'PUSH', status: { in: ['PENDING', 'RETRY'] } },
        data: { status: 'SKIPPED', lastError: 'PUSH_DISABLED', claimedAt: null }
      });
      return { processed: 0, sent: 0, retried: 0, skipped: true };
    }
    const targetStore = db.notificationDeliveryTarget;
    if (!targetStore) return { processed: 0, sent: 0, retried: 0, skipped: true };
    const now = new Date();
    const staleClaimBefore = new Date(now.getTime() - 10 * 60 * 1000);
    const rows = await db.notificationDelivery.findMany({
      where: {
        channel: 'PUSH',
        OR: [
          { status: { in: ['PENDING', 'RETRY'] }, nextAttemptAt: { lte: now } },
          { status: 'PROCESSING', claimedAt: { lt: staleClaimBefore } }
        ]
      },
      orderBy: { nextAttemptAt: 'asc' },
      take: Math.min(Math.max(limit, 1), 100)
    });
    let sent = 0;
    let retried = 0;
    for (const row of rows) {
      const claimed = await db.notificationDelivery.updateMany({
        where: row.status === 'PROCESSING'
          ? { id: row.id, status: 'PROCESSING', claimedAt: row.claimedAt }
          : { id: row.id, status: row.status },
        data: { status: 'PROCESSING', claimedAt: now, attempts: { increment: 1 } }
      });
      if (!claimed.count) continue;
      try {
        const event = await db.notificationEvent.findUnique({ where: { id: row.notificationId } });
        if (!event) {
          await db.notificationDelivery.update({ where: { id: row.id }, data: { status: 'SKIPPED', claimedAt: null, lastError: 'NOTIFICATION_EVENT_MISSING' } });
          continue;
        }
        const preference = await this.accountPushPreference(row.accountId);
        if (preference !== 'ENABLED') {
          await db.notificationDelivery.update({ where: { id: row.id }, data: { status: 'SKIPPED', claimedAt: null, lastError: preference } });
          continue;
        }
        const devices = await db.notificationDevice.findMany({ where: { accountId: row.accountId, enabled: true }, select: { id: true, token: true } });
        if (!devices.length) {
          await db.notificationDelivery.update({ where: { id: row.id }, data: { status: 'SKIPPED', claimedAt: null, lastError: 'NO_ENABLED_DEVICE' } });
          continue;
        }
        for (const device of devices) {
          await targetStore.upsert({
            where: { deliveryId_deviceId: { deliveryId: row.id, deviceId: device.id } },
            update: { token: device.token },
            create: { deliveryId: row.id, notificationId: row.notificationId, accountId: row.accountId, deviceId: device.id, token: device.token, status: 'PENDING', attempts: 0, nextAttemptAt: now }
          });
        }
        const targets = await targetStore.findMany({
          where: {
            deliveryId: row.id,
            OR: [
              { status: { in: ['PENDING', 'RETRY'] }, nextAttemptAt: { lte: now } },
              { status: 'PROCESSING', claimedAt: { lt: staleClaimBefore } }
            ]
          },
          orderBy: { nextAttemptAt: 'asc' },
          take: 100
        });
        for (const target of targets) {
          const targetClaimed = await targetStore.updateMany({
            where: target.status === 'PROCESSING'
              ? { id: target.id, status: 'PROCESSING', claimedAt: target.claimedAt }
              : { id: target.id, status: target.status },
            data: { status: 'PROCESSING', claimedAt: now, attempts: { increment: 1 } }
          });
          if (!targetClaimed.count) continue;
          const attempts = target.attempts + 1;
          try {
            const result = await this.pushProvider.send({ token: target.token, title: event.title, body: event.body, route: event.route, type: event.type, notificationId: event.id });
            await targetStore.update({ where: { id: target.id }, data: { status: 'SENT', claimedAt: null, sentAt: new Date(), providerMessageId: result.providerMessageId, lastError: null } });
          } catch (error) {
            if (error instanceof InvalidPushTokenError) {
              await db.notificationDevice.updateMany({ where: { id: target.deviceId, token: target.token }, data: { enabled: false } });
              await targetStore.update({ where: { id: target.id }, data: { status: 'SKIPPED', claimedAt: null, lastError: 'PUSH_TOKEN_INVALID' } });
            } else {
              const terminal = attempts >= 5;
              await targetStore.update({ where: { id: target.id }, data: { status: terminal ? 'FAILED' : 'RETRY', claimedAt: null, nextAttemptAt: retryAt(attempts), lastError: 'PUSH_PROVIDER_ERROR' } });
            }
          }
        }
        const allTargets = await targetStore.findMany({ where: { deliveryId: row.id }, select: { status: true, nextAttemptAt: true } });
        const pending = allTargets.filter((target: { status: string }) => ['PENDING', 'RETRY', 'PROCESSING'].includes(target.status));
        const sentTarget = allTargets.some((target: { status: string }) => target.status === 'SENT');
        const failedTarget = allTargets.some((target: { status: string }) => target.status === 'FAILED');
        if (pending.length) {
          const next = pending.map((target: { nextAttemptAt: Date }) => target.nextAttemptAt).sort((a: Date, b: Date) => a.getTime() - b.getTime())[0] ?? retryAt(1);
          await db.notificationDelivery.update({ where: { id: row.id }, data: { status: 'RETRY', claimedAt: null, nextAttemptAt: next, lastError: 'PUSH_TARGET_RETRY' } });
          retried += 1;
        } else {
          await db.notificationDelivery.update({ where: { id: row.id }, data: { status: sentTarget ? 'SENT' : (failedTarget ? 'FAILED' : 'SKIPPED'), claimedAt: null, sentAt: sentTarget ? new Date() : null, lastError: sentTarget && failedTarget ? 'PUSH_PARTIAL_FAILURE' : (failedTarget ? 'PUSH_PROVIDER_ERROR' : null) } });
          if (sentTarget) sent += 1;
        }
      } catch {
        await db.notificationDelivery.update({ where: { id: row.id }, data: { status: 'RETRY', claimedAt: null, nextAttemptAt: retryAt((row.attempts ?? 0) + 1), lastError: 'PUSH_PROVIDER_ERROR' } });
        retried += 1;
      }
    }
    return { processed: rows.length, sent, retried, skipped: false };
  }

  private async accountContact(accountId: string): Promise<{ phone: string | null; reason: 'SMS_NOT_OPTED_IN' | 'NO_ACTIVE_PHONE' }> {
    const db = this.db as any;
    const [user, collector, recycler] = await Promise.all([
      db.user?.findFirst?.({ where: { OR: [{ collectorProfileId: accountId }, { recyclerProfileId: accountId }] }, select: { phone: true, accountStatus: true, smsNotificationsEnabled: true } }) ?? null,
      db.collector?.findUnique?.({ where: { id: accountId }, select: { phone: true, accountStatus: true } }) ?? null,
      db.recycler?.findUnique?.({ where: { id: accountId }, select: { phone: true, authorizationStatus: true } }) ?? null
    ]);
    if (user?.smsNotificationsEnabled === false) return { phone: null, reason: 'SMS_NOT_OPTED_IN' };
    if (user?.accountStatus && user.accountStatus !== 'ACTIVE') return { phone: null, reason: 'NO_ACTIVE_PHONE' };
    const phone = user?.phone ?? collector?.phone ?? recycler?.phone;
    return { phone: typeof phone === 'string' ? normalizePhone(phone) : null, reason: 'NO_ACTIVE_PHONE' };
  }

  private async accountPushPreference(accountId: string): Promise<'ENABLED' | 'PUSH_NOT_OPTED_IN' | 'NO_ACTIVE_ACCOUNT'> {
    const db = this.db as any;
    const user = await db.user?.findFirst?.({ where: { OR: [{ collectorProfileId: accountId }, { recyclerProfileId: accountId }] }, select: { accountStatus: true, pushNotificationsEnabled: true } }) ?? null;
    if (user?.accountStatus && user.accountStatus !== 'ACTIVE') return 'NO_ACTIVE_ACCOUNT';
    if (user?.pushNotificationsEnabled === false) return 'PUSH_NOT_OPTED_IN';
    return 'ENABLED';
  }
}

export function createNotificationDeliveryService(db: PrismaClient, config: AppConfig) {
  return new NotificationDeliveryService(db, config);
}
