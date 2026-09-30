import type { PrismaClient } from '@prisma/client';
import { AppError } from '../utils/errors.js';

export type ActivityIdentity = { collectorId: string; role: 'COLLECTOR' | 'HOUSEHOLD' | 'RECYCLER' };
type Position = { at: string; id: string };
type Cursor = { version: 1; accountId: string; role: string; positions: Record<string, Position> };

/** Each feed advances only past delivered rows, including equal timestamps. */
export class ActivityService {
  constructor(private readonly db: PrismaClient) {}

  async changes(identity: ActivityIdentity, since?: Date, cursor?: string) {
    let positions: Record<string, Position> = {};
    if (cursor) {
      try {
        if (cursor.length > 16000) throw new Error('size');
        const parsed = JSON.parse(Buffer.from(cursor, 'base64url').toString()) as Cursor;
        if (parsed.version !== 1 || parsed.accountId !== identity.collectorId || parsed.role !== identity.role || !parsed.positions || typeof parsed.positions !== 'object' || Array.isArray(parsed.positions)) throw new Error('owner');
        for (const value of Object.values(parsed.positions)) if (!value || typeof value.id !== 'string' || !value.id || typeof value.at !== 'string' || !Number.isFinite(Date.parse(value.at))) throw new Error('position');
        positions = parsed.positions;
      } catch { throw new AppError('VALIDATION_ERROR', 'Invalid activity cursor', 400); }
    }
    const upper = new Date();
    const next = { ...positions };
    let hasMore = false;
    let legacyTime = upper.getTime();
    const page = async (key: string, model: any, owner: object, field = 'updatedAt', select?: object): Promise<any[]> => {
      const position = positions[key];
      const lower = position ? { OR: [{ [field]: { gt: new Date(position.at) } }, { [field]: new Date(position.at), id: { gt: position.id } }] } : since ? { [field]: { gte: since } } : {};
      const found = await model.findMany({ where: { AND: [owner, lower, { [field]: { lte: upper } }] }, orderBy: [{ [field]: 'asc' }, { id: 'asc' }], take: 101, ...(select ? { select: { id: true, [field]: true, ...select } } : {}) });
      const rows = found.slice(0, 100);
      if (found.length > 100) { hasMore = true; legacyTime = Math.min(legacyTime, new Date(rows[rows.length - 1][field]).getTime() - 1); }
      const last = rows[rows.length - 1];
      if (last) next[key] = { at: new Date(last[field]).toISOString(), id: last.id };
      return rows;
    };
    const account = identity.collectorId;
    const collector = identity.role === 'COLLECTOR';
    const recycler = identity.role === 'RECYCLER';
    const mongoWhere = (value: any): any => {
      if (value instanceof Date) return { $date: value.toISOString() };
      if (Array.isArray(value)) return value.map(mongoWhere);
      if (!value || typeof value !== 'object') return value;
      const operators: Record<string, string> = { AND: '$and', OR: '$or', gt: '$gt', gte: '$gte', lt: '$lt', lte: '$lte' };
      return Object.fromEntries(Object.entries(value).map(([key, item]) => [operators[key] ?? key, mongoWhere(item)]));
    };
    // Resolve ownership inside MongoDB rather than downloading every owned
    // lot id. This also catches offer changes when notification emission fails.
    const collectorOffers = { findMany: async (query: any) => {
      const response = await this.db.$runCommandRaw({ aggregate: 'BulkLot', pipeline: [
        { $match: { kabadiwalaId: account } },
        { $lookup: { from: 'BulkOffer', localField: '_id', foreignField: 'bulkLotId', as: 'offers' } },
        { $unwind: '$offers' }, { $replaceRoot: { newRoot: '$offers' } },
        { $set: { id: '$_id' } }, { $match: mongoWhere(query.where) },
        { $sort: { updatedAt: 1, id: 1 } }, { $limit: query.take },
        { $project: { _id: 0, id: 1, bulkLotId: 1, updatedAt: 1 } }
      ], cursor: {} }) as any;
      return (response.cursor?.firstBatch ?? []).map((row: any) => ({ ...row, updatedAt: new Date(typeof row.updatedAt === 'object' ? row.updatedAt.$date : row.updatedAt) }));
    } };
    const feeds: Array<{ key: string; model: any; owner: object; field?: string; select?: object }> = [
      { key: 'notifications', model: this.db.notificationEvent, owner: { accountId: account }, field: 'createdAt' }
    ];
    if (collector) feeds.push(
      { key: 'lots', model: this.db.lot, owner: { collectorId: account }, select: {} },
      { key: 'payments', model: this.db.payment, owner: { collectorId: account }, select: { lotId: true } },
      { key: 'bulkLots', model: this.db.bulkLot, owner: { kabadiwalaId: account }, select: {} }
    );
    if (collector) feeds.push({ key: 'offers', model: collectorOffers, owner: {}, select: { bulkLotId: true } });
    if (recycler) feeds.push(
      { key: 'quotes', model: this.db.quote, owner: { recyclerId: account }, select: { lotId: true } },
      { key: 'quoteRequests', model: this.db.quoteRequest, owner: { recyclerId: account }, field: 'createdAt', select: { lotId: true } },
      { key: 'offers', model: this.db.bulkOffer, owner: { recyclerId: account }, select: { bulkLotId: true } },
      { key: 'demands', model: this.db.procurementRequirement, owner: { recyclerId: account }, select: {} }
    );
    if (identity.role !== 'HOUSEHOLD') feeds.push(
      { key: 'handovers', model: this.db.handover, owner: { [recycler ? 'recyclerId' : 'collectorId']: account }, select: { lotId: true } },
      { key: 'supplyHandovers', model: this.db.supplyHandover, owner: { [recycler ? 'recyclerId' : 'collectorId']: account }, select: {} },
      { key: 'supplyPayments', model: this.db.supplyPayment, owner: { [recycler ? 'recyclerId' : 'collectorId']: account }, select: {} }
    );
    if (!recycler) feeds.push({ key: 'pickups', model: this.db.pickupRequest, owner: { [collector ? 'kabadiwalaId' : 'householdId']: account }, select: {} });
    if (identity.role === 'HOUSEHOLD') feeds.push({ key: 'listings', model: this.db.householdListing, owner: { householdId: account }, select: {} });
    const results = await Promise.all(feeds.map(async feed => [feed.key, await page(feed.key, feed.model, feed.owner, feed.field, feed.select)] as const));
    const rows = Object.fromEntries(results) as Record<string, any[]>;
    const ids = (...keys: string[]) => [...new Set(keys.flatMap(key => (rows[key] ?? []).map(row => row.id)))];
    return {
      serverTime: new Date(Math.max(0, legacyTime)).toISOString(),
      nextCursor: Buffer.from(JSON.stringify({ version: 1, accountId: account, role: identity.role, positions: next })).toString('base64url'),
      hasMore, notifications: rows.notifications,
      changed: {
        lotIds: [...new Set([...ids('lots'), ...['quotes', 'quoteRequests', 'handovers', 'payments'].flatMap(key => (rows[key] ?? []).map(row => row.lotId))])],
        quoteIds: ids('quotes', 'quoteRequests'), handoverIds: ids('handovers', 'supplyHandovers'), paymentIds: ids('payments', 'supplyPayments'),
        listingIds: ids('listings'), pickupIds: ids('pickups'), bulkLotIds: [...new Set([...ids('bulkLots'), ...(rows.offers ?? []).map(row => row.bulkLotId)])],
        offerIds: ids('offers'), demandIds: ids('demands')
      }
    };
  }
}
