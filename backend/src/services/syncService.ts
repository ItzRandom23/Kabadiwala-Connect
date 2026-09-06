import crypto from 'node:crypto';
import type { PrismaClient } from '@prisma/client';
import { PaymentService } from './paymentService.js';

type SyncResult = { operationId: string; status: string; entityType?: string; entityId?: string; errorCode?: string };

export class SyncService {
  constructor(private db: PrismaClient, private payments: PaymentService) {}

  async batch(cid: string, ops: any[]) {
    const results: SyncResult[] = [];
    for (const op of ops) {
      const hash = crypto.createHash('sha256').update(JSON.stringify(op.payload ?? {})).digest('hex');
      const prior = await this.db.syncOperation.findUnique({ where: { operationId_collectorId: { operationId: op.operationId, collectorId: cid } } });
      if (prior) {
        if (prior.requestHash !== hash) {
          results.push({ operationId: op.operationId, status: 'CONFLICT', entityType: prior.entityType, entityId: prior.entityId, errorCode: 'SYNC_PAYLOAD_MISMATCH' });
        } else {
          results.push({ operationId: op.operationId, status: 'ALREADY_APPLIED', entityType: prior.entityType, entityId: prior.entityId });
        }
        continue;
      }

      try {
        if (op.operationType === 'CREATE' && op.entityType === 'LOT') {
          const p = op.payload;
          const lot = await this.db.lot.create({ data: { id: op.entityId, collectorId: cid, materialCategory: p.materialCategory, materialSubcategory: p.materialSubcategory, condition: p.condition, weight: p.weight, collectionLatitude: p.collectionLocation?.latitude, collectionLongitude: p.collectionLocation?.longitude, collectionAreaName: p.collectionLocation?.areaName, collectionLocationPrecision: p.collectionLocation?.precision, notes: p.notes, status: 'CREATED' } });
          await this.save(cid, op, hash, 'APPLIED');
          results.push({ operationId: op.operationId, status: 'APPLIED', entityType: 'LOT', entityId: lot.id });
        } else if (op.operationType === 'UPDATE' && op.entityType === 'LOT') {
          const p = op.payload;
          const out = await this.db.lot.updateMany({ where: { id: op.entityId, collectorId: cid, status: 'CREATED', version: p.clientVersion }, data: { weight: p.weight, condition: p.condition, notes: p.notes, version: { increment: 1 } } });
          if (!out.count) {
            await this.save(cid, op, hash, 'CONFLICT', 'LOT_UPDATE_CONFLICT');
            results.push({ operationId: op.operationId, status: 'CONFLICT', entityType: 'LOT', entityId: op.entityId, errorCode: 'LOT_UPDATE_CONFLICT' });
            continue;
          }
          await this.save(cid, op, hash, 'APPLIED');
          results.push({ operationId: op.operationId, status: 'APPLIED', entityType: 'LOT', entityId: op.entityId });
        } else if (op.operationType === 'CREATE' && op.entityType === 'PAYMENT') {
          const payment = await this.payments.record(cid, op.payload);
          await this.save(cid, op, hash, 'APPLIED');
          results.push({ operationId: op.operationId, status: 'APPLIED', entityType: 'PAYMENT', entityId: payment.id });
        } else {
          await this.save(cid, op, hash, 'INVALID', 'UNSUPPORTED_SYNC_OPERATION');
          results.push({ operationId: op.operationId, status: 'INVALID', entityType: op.entityType, entityId: op.entityId, errorCode: 'UNSUPPORTED_SYNC_OPERATION' });
        }
      } catch (e: any) {
        await this.save(cid, op, hash, 'REJECTED', e?.details?.code ?? e?.code ?? 'SYNC_OPERATION_REJECTED');
        results.push({ operationId: op.operationId, status: 'REJECTED', entityType: op.entityType, entityId: op.entityId, errorCode: e?.details?.code ?? e?.code ?? 'SYNC_OPERATION_REJECTED' });
      }
    }
    return { results };
  }

  private save(cid: string, op: any, hash: string, status: any, errorCode?: string) {
    return this.db.syncOperation.create({ data: { operationId: op.operationId, collectorId: cid, operationType: op.operationType, entityType: op.entityType, entityId: op.entityId, status, requestHash: hash, processedAt: new Date(), errorCode } });
  }

  async changes(cid: string, since?: Date) {
    const where = since ? { collectorId: cid, updatedAt: { gte: since } } : { collectorId: cid };
    const [lots, payments, handovers] = await Promise.all([
      this.db.lot.findMany({ where, orderBy: { updatedAt: 'asc' } }),
      this.db.payment.findMany({ where, orderBy: { updatedAt: 'asc' } }),
      this.db.handover.findMany({ where, orderBy: { updatedAt: 'asc' } })
    ]);
    return { serverTime: new Date().toISOString(), changes: { lots, payments, handovers } };
  }
}
