import type { PrismaClient } from '@prisma/client';

export type ActivityIdentity = { collectorId: string; role: 'COLLECTOR' | 'HOUSEHOLD' | 'RECYCLER' };

/**
 * Small, cursor-based activity projection for the prototype.
 *
 * NotificationEvent is the durable fan-out record already emitted by the
 * domain services. Entity references are included so a client can decide
 * which local catalogue to refresh without adding a second event bus.
 */
export class ActivityService {
  constructor(private readonly db: PrismaClient) {}

  async changes(identity: ActivityIdentity, since?: Date) {
    const lowerBound = since ?? new Date(0);
    const notifications = await this.db.notificationEvent.findMany({
      where: { accountId: identity.collectorId, createdAt: { gt: lowerBound } },
      orderBy: { createdAt: 'asc' },
      take: 100
    });

    if (identity.role === 'RECYCLER') {
      const [quotes, quoteRequests, handovers] = await Promise.all([
        this.db.quote.findMany({ where: { recyclerId: identity.collectorId, updatedAt: { gt: lowerBound } }, select: { id: true, lotId: true, updatedAt: true } }),
        this.db.quoteRequest.findMany({ where: { recyclerId: identity.collectorId, createdAt: { gt: lowerBound } }, select: { id: true, lotId: true, createdAt: true } }),
        this.db.handover.findMany({ where: { recyclerId: identity.collectorId, updatedAt: { gt: lowerBound } }, select: { id: true, lotId: true, updatedAt: true } })
      ]);
      return {
        serverTime: new Date().toISOString(),
        notifications,
        changed: {
          lotIds: [...new Set([...quotes.map(x => x.lotId), ...quoteRequests.map(x => x.lotId), ...handovers.map(x => x.lotId)])],
          quoteIds: [...new Set([...quotes.map(x => x.id), ...quoteRequests.map(x => x.id)])],
          handoverIds: handovers.map(x => x.id),
          paymentIds: []
        }
      };
    }

    const [lots, handovers, payments] = await Promise.all([
      this.db.lot.findMany({ where: { collectorId: identity.collectorId, updatedAt: { gt: lowerBound } }, select: { id: true } }),
      this.db.handover.findMany({ where: { collectorId: identity.collectorId, updatedAt: { gt: lowerBound } }, select: { id: true, lotId: true } }),
      this.db.payment.findMany({ where: { collectorId: identity.collectorId, updatedAt: { gt: lowerBound } }, select: { id: true, lotId: true } })
    ]);
    return {
      serverTime: new Date().toISOString(),
      notifications,
      changed: {
        lotIds: [...new Set([...lots.map(x => x.id), ...handovers.map(x => x.lotId), ...payments.map(x => x.lotId)])],
        quoteIds: [],
        handoverIds: handovers.map(x => x.id),
        paymentIds: payments.map(x => x.id)
      }
    };
  }
}
