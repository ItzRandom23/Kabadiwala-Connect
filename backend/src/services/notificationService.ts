import { createHash } from 'node:crypto';
import type { PrismaClient } from '@prisma/client';

export type NotificationInput = {
  accountId: string;
  type: string;
  title: string;
  body: string;
  route?: string;
  /** Stable business-event identity used to make retried requests idempotent. */
  dedupeKey?: string;
};

/**
 * Create provider outbox rows without making provider configuration a
 * dependency of the business operation. Older test/disposable databases may
 * not have the optional model yet, so enqueueing remains best-effort.
 */
export async function enqueueNotificationDelivery(db: unknown, event: { id: string; accountId: string }) {
  const delivery = (db as { notificationDelivery?: { upsert?: (args: unknown) => Promise<unknown> } }).notificationDelivery;
  if (!delivery?.upsert) return;
  for (const channel of ['SMS', 'PUSH']) {
    try {
      await delivery.upsert({
        where: { notificationId_channel: { notificationId: event.id, channel } },
        update: {},
        create: { notificationId: event.id, accountId: event.accountId, channel, status: 'PENDING', attempts: 0, nextAttemptAt: new Date() }
      });
    } catch {
      // The in-app event remains authoritative if an optional outbox write fails.
    }
  }
}

function notificationId(input: NotificationInput) {
  return createHash('sha256')
    .update(`${input.accountId}\n${input.dedupeKey ?? ''}`)
    .digest('hex');
}

/**
 * Notification events are intentionally persisted before any push provider is
 * involved. The in-app inbox is therefore reliable in offline/low-connectivity
 * conditions and push delivery can be added without changing domain services.
 */
export async function emitNotification(db: PrismaClient, input: NotificationInput) {
  const { dedupeKey, ...data } = input;
  const id = dedupeKey ? notificationId(input) : undefined;
  try {
    const event = await db.notificationEvent.create({ data: { ...data, ...(id ? { id } : {}) } });
    await enqueueNotificationDelivery(db, event);
    return event;
  } catch {
    // A deterministic ID turns a retry into a harmless duplicate-key error.
    // Read the original event back when the provider supports the lookup;
    // otherwise retain the historical best-effort behaviour.
    if (id) {
      try {
        const event = await db.notificationEvent.findUnique({ where: { id } });
        if (event) await enqueueNotificationDelivery(db, event);
        return event;
      } catch {
        // Notification delivery must never fail the business operation.
      }
    }
    // A notification must never make the business transaction fail.
    return null;
  }
}

export class NotificationService {
  constructor(private readonly db: PrismaClient) {}

  async list(accountId: string, unreadOnly = false, limit = 50) {
    return this.db.notificationEvent.findMany({
      where: { accountId, ...(unreadOnly ? { readAt: null } : {}) },
      orderBy: { createdAt: 'desc' },
      take: Math.min(Math.max(limit, 1), 100)
    });
  }

  async unreadCount(accountId: string) {
    return this.db.notificationEvent.count({ where: { accountId, readAt: null } });
  }

  async markRead(accountId: string, id: string) {
    const updated = await this.db.notificationEvent.updateMany({
      where: { id, accountId, readAt: null },
      data: { readAt: new Date() }
    });
    return updated.count > 0;
  }

  async markAllRead(accountId: string) {
    const updated = await this.db.notificationEvent.updateMany({ where: { accountId, readAt: null }, data: { readAt: new Date() } });
    return updated.count;
  }
}
