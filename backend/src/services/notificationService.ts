import type { PrismaClient } from '@prisma/client';

export type NotificationInput = {
  accountId: string;
  type: string;
  title: string;
  body: string;
  route?: string;
};

/**
 * Notification events are intentionally persisted before any push provider is
 * involved. The in-app inbox is therefore reliable in offline/low-connectivity
 * conditions and push delivery can be added without changing domain services.
 */
export async function emitNotification(db: PrismaClient, input: NotificationInput) {
  try {
    return await db.notificationEvent.create({ data: input });
  } catch {
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
