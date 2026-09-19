import type { PrismaClient } from '@prisma/client';
import { AppError } from '../utils/errors.js';

export type NotificationDeviceInput = {
  token: string;
  platform: 'ANDROID' | 'IOS' | 'WEB';
  appVersion?: string;
};

/**
 * Stores provider registration state separately from the in-app event inbox.
 * Sending is deliberately not performed here: a provider worker can consume
 * NotificationEvent records later without making a user action depend on FCM.
 */
export class NotificationDeviceService {
  constructor(private readonly db: PrismaClient) {}

  /**
   * A provider token can survive an app logout/account switch. Invalidate
   * queued fan-out targets before changing ownership so a later worker retry
   * cannot deliver an earlier account's notification to the new account.
   */
  private async invalidatePendingTargets(deviceId: string, accountId: string, reason: string) {
    const targets = (this.db as PrismaClient & {
      notificationDeliveryTarget?: { updateMany?: (args: unknown) => Promise<unknown> };
    }).notificationDeliveryTarget;
    await targets?.updateMany?.({
      where: {
        deviceId,
        accountId,
        status: { in: ['PENDING', 'RETRY', 'PROCESSING'] }
      },
      data: { status: 'SKIPPED', claimedAt: null, lastError: reason }
    });
  }

  async register(accountId: string, input: NotificationDeviceInput) {
    const current = await (this.db.notificationDevice as typeof this.db.notificationDevice & {
      findUnique?: (args: unknown) => Promise<{ id: string; accountId: string } | null>;
    }).findUnique?.({ where: { token: input.token }, select: { id: true, accountId: true } });
    if (current && current.accountId !== accountId) {
      await this.invalidatePendingTargets(current.id, current.accountId, 'DEVICE_REASSIGNED');
    }
    const device = await this.db.notificationDevice.upsert({
      where: { token: input.token },
      update: {
        accountId,
        platform: input.platform,
        appVersion: input.appVersion,
        enabled: true,
        lastSeenAt: new Date()
      },
      create: {
        accountId,
        token: input.token,
        platform: input.platform,
        appVersion: input.appVersion,
        enabled: true,
        lastSeenAt: new Date()
      },
      select: { id: true, platform: true, appVersion: true, enabled: true, lastSeenAt: true, createdAt: true }
    });
    return device;
  }

  async unregister(accountId: string, token: string) {
    const current = await (this.db.notificationDevice as typeof this.db.notificationDevice & {
      findUnique?: (args: unknown) => Promise<{ id: string; accountId: string } | null>;
    }).findUnique?.({ where: { token }, select: { id: true, accountId: true } });
    if (current?.accountId === accountId) {
      await this.invalidatePendingTargets(current.id, accountId, 'DEVICE_UNREGISTERED');
    }
    const result = await this.db.notificationDevice.deleteMany({ where: { accountId, token } });
    return { removed: result.count > 0 };
  }

  async list(accountId: string) {
    return this.db.notificationDevice.findMany({
      where: { accountId, enabled: true },
      orderBy: { lastSeenAt: 'desc' },
      select: { id: true, platform: true, appVersion: true, enabled: true, lastSeenAt: true, createdAt: true }
    });
  }
}

export function parseNotificationDeviceInput(body: unknown): NotificationDeviceInput {
  const value = body as Record<string, unknown> | null;
  const token = typeof value?.token === 'string' ? value.token.trim() : '';
  const platform = typeof value?.platform === 'string' ? value.platform.trim().toUpperCase() : 'ANDROID';
  const appVersion = typeof value?.appVersion === 'string' ? value.appVersion.trim() : undefined;
  if (token.length < 20 || token.length > 4096 || !['ANDROID', 'IOS', 'WEB'].includes(platform)) {
    throw new AppError('VALIDATION_ERROR', 'A valid push token and platform are required', 422, { code: 'INVALID_NOTIFICATION_DEVICE' });
  }
  if (appVersion && appVersion.length > 64) {
    throw new AppError('VALIDATION_ERROR', 'App version is too long', 422, { code: 'INVALID_NOTIFICATION_DEVICE' });
  }
  return { token, platform: platform as NotificationDeviceInput['platform'], ...(appVersion ? { appVersion } : {}) };
}
