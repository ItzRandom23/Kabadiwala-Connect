import { describe, expect, it, vi } from 'vitest';
import { SyncService, decodeSyncCursor, encodeSyncCursor } from '../src/services/syncService.js';
import { createSupplyHandoverQr } from '../src/routes/formalisationRoutes.js';

describe('formal handover offline sync', () => {
  it('round-trips an opaque per-feed cursor and rejects tampering', () => {
    const cursor = encodeSyncCursor({ formalHandovers: { at: '2026-09-15T10:00:00.000Z', id: 'handover-1' } });
    expect(cursor).not.toContain('2026-09-15');
    expect(decodeSyncCursor(cursor)).toEqual({ version: 1, positions: { formalHandovers: { at: '2026-09-15T10:00:00.000Z', id: 'handover-1' } } });
    expect(() => decodeSyncCursor('not-a-sync-cursor')).toThrowError('Invalid sync cursor');
  });

  it('applies collector confirmation once and records the sync audit in the same transaction', async () => {
    const now = new Date(Date.now() + 60_000);
    const handover = { id: 'handover-1', collectorId: 'collector-1', status: 'PREPARED', expiresAt: now };
    const tx = {
      supplyHandover: {
        findFirst: vi.fn().mockResolvedValue(handover),
        updateMany: vi.fn().mockResolvedValue({ count: 1 }),
        findUniqueOrThrow: vi.fn().mockResolvedValue({ ...handover, status: 'COLLECTOR_CONFIRMED' })
      },
      auditEvent: { create: vi.fn().mockResolvedValue({}) },
      materialPassportEvent: { create: vi.fn().mockResolvedValue({}) },
      syncOperation: { create: vi.fn().mockResolvedValue({}) }
    };
    const db = {
      syncOperation: { findUnique: vi.fn().mockResolvedValue(null) },
      $transaction: vi.fn(async (callback: (value: typeof tx) => unknown) => callback(tx))
    } as any;
    const service = new SyncService(db, {} as any);

    const result = await service.batch('collector-1', [{ operationId: 'offline-confirm-1', operationType: 'UPDATE', entityType: 'SUPPLY_HANDOVER', entityId: 'handover-1', payload: { action: 'COLLECTOR_CONFIRM' } }]);

    expect(result.results[0]).toMatchObject({ status: 'APPLIED', entityType: 'SUPPLY_HANDOVER', entityId: 'handover-1' });
    expect(db.$transaction).toHaveBeenCalledOnce();
    expect(tx.supplyHandover.updateMany).toHaveBeenCalledWith(expect.objectContaining({ where: expect.objectContaining({ status: 'PREPARED' }) }));
    expect(tx.auditEvent.create).toHaveBeenCalledOnce();
    expect(tx.materialPassportEvent.create).toHaveBeenCalledOnce();
    expect(tx.syncOperation.create).toHaveBeenCalledOnce();
  });

  it('reserves a client-owned pool contribution transactionally and rejects Recycler role misuse', async () => {
    const pool = { id: 'pool-1', recyclerId: 'recycler-1', materialCategory: 'PCB', preferredGrade: 'B', status: 'FORMING', minimumQuantityKg: 500, totalReservedKg: 0, requirementId: null };
    const recycler = { id: 'recycler-1', authorizationStatus: 'VERIFIED', authorizationValidUntil: null, materials: [{ category: 'PCB' }], rates: [{ materialCategory: 'PCB', pricePerKg: 30 }] };
    const balance = { id: 'balance-1', kabadiwalaId: 'collector-1', materialCategory: 'PCB', grade: 'B', availableKg: 530, reservedKg: 0, soldKg: 0 };
    const contribution = { id: 'contribution-1', poolId: 'pool-1', collectorId: 'collector-1', quantityKg: 120, grade: 'B', status: 'RESERVED' };
    const tx = {
      poolContribution: { findUnique: vi.fn().mockResolvedValue(null), create: vi.fn().mockResolvedValue(contribution) },
      pooledConsignment: { findUnique: vi.fn().mockResolvedValue(pool), updateMany: vi.fn().mockResolvedValue({ count: 1 }), findUniqueOrThrow: vi.fn().mockResolvedValue({ ...pool, totalReservedKg: 120 }) },
      recycler: { findUnique: vi.fn().mockResolvedValue(recycler) },
      procurementRequirement: { findUnique: vi.fn().mockResolvedValue(null) },
      inventoryBalance: { findFirst: vi.fn().mockResolvedValue(balance), updateMany: vi.fn().mockResolvedValue({ count: 1 }), findUniqueOrThrow: vi.fn().mockResolvedValue({ ...balance, availableKg: 410, reservedKg: 120 }) },
      inventoryMovement: { create: vi.fn().mockResolvedValue({}) },
      pickupRequest: { findMany: vi.fn().mockResolvedValue([]) },
      auditEvent: { create: vi.fn().mockResolvedValue({}) },
      materialPassportEvent: { create: vi.fn().mockResolvedValue({}) },
      syncOperation: { create: vi.fn().mockResolvedValue({}) }
    };
    const db = { syncOperation: { findUnique: vi.fn().mockResolvedValue(null), create: vi.fn().mockResolvedValue({}) }, $transaction: vi.fn(async (callback: (value: typeof tx) => unknown) => callback(tx)) } as any;
    const service = new SyncService(db, {} as any);

    const applied = await service.batch('collector-1', [{ operationId: 'pool-join-1', operationType: 'CREATE', entityType: 'POOL_CONTRIBUTION', entityId: 'contribution-1', payload: { poolId: 'pool-1', quantityKg: 120, grade: 'B', sourceListingIds: [] } }]);

    expect(applied.results[0]).toMatchObject({ status: 'APPLIED', entityType: 'POOL_CONTRIBUTION', entityId: 'contribution-1' });
    expect(tx.inventoryBalance.updateMany).toHaveBeenCalledWith(expect.objectContaining({ data: { availableKg: { decrement: 120 }, reservedKg: { increment: 120 } } }));
    expect(tx.syncOperation.create).toHaveBeenCalledOnce();

    const misuse = await service.batch('recycler-1', [{ operationId: 'pool-join-2', operationType: 'CREATE', entityType: 'POOL_CONTRIBUTION', entityId: 'contribution-2', payload: { poolId: 'pool-1', quantityKg: 120, grade: 'B' } }], 'RECYCLER');
    expect(misuse.results[0]).toMatchObject({ status: 'REJECTED', errorCode: 'POOL_CONTRIBUTION_ROLE_REQUIRED' });
  });

  it('records disposal evidence through the same replay ledger used by other offline writes', async () => {
    const handover = { id: 'handover-2', recyclerId: 'recycler-1', status: 'COMPLETED', dataBearingDevice: true, dataDestructionRequested: true, destructionEvidenceStatus: 'RECYCLER_EVIDENCE_PENDING', sourceListingIds: [] };
    const tx = {
      supplyHandover: { findUnique: vi.fn().mockResolvedValue(handover), updateMany: vi.fn().mockResolvedValue({ count: 1 }) },
      householdListing: { updateMany: vi.fn().mockResolvedValue({ count: 0 }) },
      auditEvent: { create: vi.fn().mockResolvedValue({}) },
      materialPassportEvent: { create: vi.fn().mockResolvedValue({}) },
      syncOperation: { create: vi.fn().mockResolvedValue({}) }
    };
    const db = { syncOperation: { findUnique: vi.fn().mockResolvedValue(null), create: vi.fn().mockResolvedValue({}) }, $transaction: vi.fn(async (callback: (value: typeof tx) => unknown) => callback(tx)) } as any;
    const service = new SyncService(db, {} as any);

    const result = await service.batch('recycler-1', [{ operationId: 'disposal-1', operationType: 'UPDATE', entityType: 'SUPPLY_HANDOVER', entityId: 'handover-2', payload: { action: 'DISPOSAL_EVIDENCE', evidenceReference: 'evidence-1', method: 'CERTIFICATE', deviceDataDestroyed: true } }], 'RECYCLER');

    expect(result.results[0]).toMatchObject({ status: 'APPLIED', entityType: 'SUPPLY_HANDOVER', entityId: 'handover-2' });
    expect(tx.supplyHandover.updateMany).toHaveBeenCalledWith(expect.objectContaining({ where: expect.objectContaining({ destructionEvidenceStatus: 'RECYCLER_EVIDENCE_PENDING' }) }));
    expect(tx.auditEvent.create).toHaveBeenCalledOnce();
    expect(tx.syncOperation.create).toHaveBeenCalledOnce();
  });

  it('requires a signed current QR before applying an offline Recycler confirmation', async () => {
    const secret = 'sync-service-test-secret-with-at-least-32-chars';
    const qr = createSupplyHandoverQr({ version: 1, referenceId: 'REF-3', recyclerId: 'recycler-1', nonce: 'nonce-3' }, secret);
    const handover = { id: 'handover-3', referenceId: 'REF-3', recyclerId: 'recycler-1', collectorId: 'collector-1', materialCategory: 'PCB', status: 'COLLECTOR_CONFIRMED', expiresAt: new Date(Date.now() + 60_000), qrCodeData: qr.data, qrNonceHash: 'e0e0', quotedWeightKg: 100, quotedRatePerKg: 30, quotedValue: 3000, poolId: null, bulkLotId: null };
    handover.qrNonceHash = (await import('node:crypto')).createHash('sha256').update('nonce-3').digest('hex');
    const tx = {
      supplyHandover: { findUnique: vi.fn().mockResolvedValue(handover), updateMany: vi.fn().mockResolvedValue({ count: 1 }) },
      recycler: { findUnique: vi.fn().mockResolvedValue({ id: 'recycler-1', authorizationStatus: 'VERIFIED', authorizationValidUntil: null, materials: [{ category: 'PCB' }], rates: [{ materialCategory: 'PCB', pricePerKg: 30 }] }) },
      settlementBreakdown: { create: vi.fn().mockResolvedValue({}) },
      anomalyFlag: { create: vi.fn().mockResolvedValue({}) },
      auditEvent: { create: vi.fn().mockResolvedValue({}) },
      materialPassportEvent: { create: vi.fn().mockResolvedValue({}) },
      syncOperation: { create: vi.fn().mockResolvedValue({}) }
    };
    const db = { syncOperation: { findUnique: vi.fn().mockResolvedValue(null), create: vi.fn().mockResolvedValue({}) }, supplyHandover: { findUnique: vi.fn().mockResolvedValue(handover) }, $transaction: vi.fn(async (callback: (value: typeof tx) => unknown) => callback(tx)) } as any;
    const service = new SyncService(db, {} as any, secret);

    const result = await service.batch('recycler-1', [{ operationId: 'recycler-confirm-1', operationType: 'UPDATE', entityType: 'SUPPLY_HANDOVER', entityId: 'handover-3', payload: { action: 'RECYCLER_CONFIRM', qrCodeData: qr.data, actualWeightKg: 100, acceptedWeightKg: 100, finalRatePerKg: 30, materialMatch: true } }], 'RECYCLER');

    expect(result.results[0]).toMatchObject({ status: 'APPLIED', entityType: 'SUPPLY_HANDOVER', entityId: 'handover-3' });
    expect(tx.supplyHandover.updateMany).toHaveBeenCalledWith(expect.objectContaining({ where: expect.objectContaining({ status: 'COLLECTOR_CONFIRMED' }) }));
    expect(tx.settlementBreakdown.create).toHaveBeenCalledOnce();
    expect(tx.syncOperation.create).toHaveBeenCalledOnce();
  });
});
