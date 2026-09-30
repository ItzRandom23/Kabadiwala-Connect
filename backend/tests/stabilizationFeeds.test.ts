import { describe, expect, it, vi } from 'vitest';
import { ActivityService } from '../src/services/activityService.js';
import { SyncService, decodeSyncCursor } from '../src/services/syncService.js';
import { AppError } from '../src/utils/errors.js';

function emptyDatabase() {
  const models = ['notificationEvent', 'lot', 'payment', 'handover', 'householdListing', 'pickupRequest', 'bulkLot', 'bulkOffer', 'procurementRequirement', 'quote', 'quoteRequest', 'supplyHandover', 'supplyPayment', 'inventoryBalance', 'inventoryMovement', 'poolContribution', 'pooledConsignment', 'pickupSettlementPayment', 'poolSettlement', 'settlementBreakdown', 'anomalyFlag', 'materialPassportEvent'];
  return { ...Object.fromEntries(models.map(name => [name, { findMany: vi.fn().mockResolvedValue([]) }])), $runCommandRaw: vi.fn().mockResolvedValue({ cursor: { firstBatch: [] } }) } as any;
}

describe('bounded account-owned change feeds', () => {
  it('does not skip equal-timestamp notification backlog and binds cursors to account and role', async () => {
    const db = emptyDatabase();
    const at = new Date('2026-01-01T00:00:00Z');
    const rows = Array.from({ length: 205 }, (_, i) => ({ id: String(i).padStart(4, '0'), accountId: 'h1', createdAt: at }));
    db.notificationEvent.findMany.mockImplementation(async (query: any) => {
      const after = query.where.AND[1].OR?.[1]?.id?.gt;
      return rows.filter(row => !after || row.id > after).slice(0, query.take);
    });
    const service = new ActivityService(db);
    const identity = { collectorId: 'h1', role: 'HOUSEHOLD' as const };
    const first = await service.changes(identity);
    const second = await service.changes(identity, undefined, first.nextCursor);
    const third = await service.changes(identity, undefined, second.nextCursor);
    expect([first, second, third].map(x => x.notifications.length)).toEqual([100, 100, 5]);
    expect(first.hasMore).toBe(true);
    expect(third.hasMore).toBe(false);
    expect(new Set([...first.notifications, ...second.notifications, ...third.notifications].map(x => x.id)).size).toBe(205);
    await expect(service.changes({ ...identity, collectorId: 'h2' }, undefined, first.nextCursor)).rejects.toMatchObject({ status: 400 });
    await expect(service.changes({ ...identity, role: 'COLLECTOR' }, undefined, first.nextCursor)).rejects.toMatchObject({ status: 400 });
  });

  it('signals pickup changes even when notification creation was missed', async () => {
    const db = emptyDatabase();
    db.pickupRequest.findMany.mockResolvedValue([{ id: 'pickup1', updatedAt: new Date('2026-01-01') }]);
    const result = await new ActivityService(db).changes({ collectorId: 'h1', role: 'HOUSEHOLD' });
    expect(result.changed.pickupIds).toEqual(['pickup1']);
    expect(result.notifications).toEqual([]);
    expect(db.pickupRequest.findMany).toHaveBeenCalledWith(expect.objectContaining({ take: 101, where: { AND: expect.arrayContaining([{ householdId: 'h1' }]) } }));
    expect(db.bulkLot.findMany).not.toHaveBeenCalled();
  });

  it('redacts pooled contributors in sync and advances raw cursor rows', async () => {
    const db = emptyDatabase();
    const at = new Date('2026-01-01');
    db.poolContribution.findMany.mockResolvedValue([{ id: 'ownContribution', collectorId: 'contributor', handoverId: 'shared', quantityKg: 2, expectedRatePerKg: 100, finalAcceptedKg: 2, finalPayout: 200, updatedAt: at }]);
    db.supplyHandover.findMany.mockResolvedValue([{ id: 'shared', poolId: 'pool1', collectorId: 'owner', recyclerId: 'r1', qrCodeData: 'SECRET_QR', sourceListingIds: ['otherPrivate'], quotedWeightKg: 100, quotedRatePerKg: 100, quotedValue: 10000, finalValue: 10000, updatedAt: at }]);
    db.settlementBreakdown.findMany.mockResolvedValue([{ id: 'breakdown', handoverId: 'shared', finalValue: 10000, updatedAt: at }]);
    db.anomalyFlag.findMany.mockResolvedValue([{ id: 'anomaly', entityId: 'shared', details: { otherAccount: 'private' }, createdAt: at }]);
    db.materialPassportEvent.findMany.mockResolvedValue([{ id: 'event1', entityId: 'shared', actorId: 'owner', metadata: { private: true }, occurredAt: at }, { id: 'event2', entityId: 'shared', actorId: 'contributor', metadata: { unrelated: true }, occurredAt: at }]);
    const result = await new SyncService(db, {} as any).changes('contributor');
    expect(result.changes.formalHandovers[0]).toMatchObject({ qrCodeData: null, collectorId: '', quotedWeightKg: 2, finalValue: 200 });
    expect(result.changes.formalHandovers[0]).not.toHaveProperty('sourceListingIds');
    expect(result.changes.settlementBreakdowns).toEqual([]);
    expect(result.changes.formalAnomalies).toEqual([]);
    expect(result.changes.formalEvents).toEqual([expect.objectContaining({ id: 'event2', metadata: null })]);
    expect(decodeSyncCursor(result.nextCursor).positions.formalEvents.id).toBe('event2');
  });

  it('keeps owned handover fields and bounds sync feeds', async () => {
    const db = emptyDatabase();
    db.lot.findMany.mockResolvedValue(Array.from({ length: 101 }, (_, i) => ({ id: `lot${i}`, collectorId: 'owner', updatedAt: new Date(i * 1000) })));
    db.supplyHandover.findMany.mockResolvedValue([{ id: 'direct', collectorId: 'owner', qrCodeData: 'OWN_QR', updatedAt: new Date() }]);
    const result = await new SyncService(db, {} as any).changes('owner', undefined, 'COLLECTOR', undefined, true);
    expect(result.hasMore).toBe(true);
    expect(result.changes.lots).toHaveLength(100);
    expect(result.changes.formalHandovers[0].qrCodeData).toBe('OWN_QR');
    expect(db.lot.findMany).toHaveBeenCalledWith(expect.objectContaining({ take: 101 }));
  });

  it('keeps pooled privacy after the handover delta has already advanced', async () => {
    const db = emptyDatabase();
    const at = new Date('2026-01-01');
    db.poolContribution.findMany.mockResolvedValue([{ id: 'own', collectorId: 'contributor', handoverId: 'shared', quantityKg: 2, updatedAt: at }]);
    db.supplyHandover.findMany.mockResolvedValue([]);
    db.settlementBreakdown.findMany.mockResolvedValue([{ id: 'later', handoverId: 'shared', finalValue: 10000, updatedAt: at }]);
    db.materialPassportEvent.findMany.mockResolvedValue([{ id: 'newEvent', entityId: 'shared', actorId: 'owner', metadata: { private: true }, occurredAt: at }]);
    const result = await new SyncService(db, {} as any).changes('contributor');
    expect(result.changes.formalHandovers).toEqual([]);
    expect(result.changes.settlementBreakdowns).toEqual([]);
    expect(result.changes.formalEvents).toEqual([]);
  });

  it('preserves complete legacy since responses during cursor rollout', async () => {
    const db = emptyDatabase();
    db.lot.findMany.mockResolvedValue(Array.from({ length: 150 }, (_, i) => ({ id: `lot${i}`, updatedAt: new Date(i * 1000) })));
    const result = await new SyncService(db, {} as any).changes('owner', new Date(0));
    expect(result.changes.lots).toHaveLength(150);
    expect(result.hasMore).toBe(false);
    expect(db.lot.findMany).toHaveBeenCalledWith(expect.objectContaining({ take: undefined }));
  });

  it('invalidates Collector offers independently of notifications', async () => {
    const db = emptyDatabase();
    db.$runCommandRaw.mockResolvedValue({ cursor: { firstBatch: [{ id: 'offer1', bulkLotId: 'lot1', updatedAt: { $date: '2026-01-01T00:00:00Z' } }] } });
    const result = await new ActivityService(db).changes({ collectorId: 'owner', role: 'COLLECTOR' });
    expect(result.changed.offerIds).toEqual(['offer1']);
    expect(db.$runCommandRaw).toHaveBeenCalledWith(expect.objectContaining({ pipeline: expect.arrayContaining([{ $match: { kabadiwalaId: 'owner' } }]) }));
    const pipeline = db.$runCommandRaw.mock.calls[0][0].pipeline;
    expect(pipeline[5].$match.$and[2].updatedAt.$lte).toHaveProperty('$date');
    await new ActivityService(db).changes({ collectorId: 'owner', role: 'COLLECTOR' }, undefined, result.nextCursor);
    const nextMatch = db.$runCommandRaw.mock.calls[1][0].pipeline[5].$match;
    expect(nextMatch.$and[1].$or[0].updatedAt.$gt).toHaveProperty('$date');
    expect(nextMatch.$and[1].$or[1].id).toEqual({ $gt: 'offer1' });
  });

  it('does not store infrastructure failure as a permanent sync rejection', async () => {
    const db = { syncOperation: { findUnique: vi.fn().mockResolvedValue(null), create: vi.fn() }, lot: { findFirst: vi.fn().mockRejectedValue(new Error('database disconnected')) } } as any;
    const service = new SyncService(db, {} as any);
    await expect(service.batch('owner', [{ operationId: 'op1', entityId: 'lot1', operationType: 'CREATE', entityType: 'LOT', payload: {} }])).rejects.toMatchObject({ status: 503 });
    expect(db.syncOperation.create).not.toHaveBeenCalled();
  });

  it('keeps domain rejection terminal', async () => {
    const db = { syncOperation: { findUnique: vi.fn().mockResolvedValue(null), create: vi.fn() }, lot: { findFirst: vi.fn().mockRejectedValue(new AppError('VALIDATION_ERROR', 'Invalid', 422)) } } as any;
    const result = await new SyncService(db, {} as any).batch('owner', [{ operationId: 'op1', entityId: 'lot1', operationType: 'CREATE', entityType: 'LOT', payload: {} }]);
    expect(result.results[0].status).toBe('REJECTED');
    expect(db.syncOperation.create).toHaveBeenCalledOnce();
  });

  it('rejects a different payload winning a concurrent replay ledger insert', async () => {
    const db = { syncOperation: { create: vi.fn().mockRejectedValue({ code: 'P2002' }), findUnique: vi.fn().mockResolvedValue({ requestHash: 'other', status: 'APPLIED' }) } } as any;
    await expect((new SyncService(db, {} as any) as any).save('owner', { operationId: 'same' }, 'mine', 'APPLIED')).rejects.toMatchObject({ status: 409, details: { code: 'SYNC_PAYLOAD_MISMATCH' } });
  });
});
