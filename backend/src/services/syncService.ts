import crypto from 'node:crypto';
import type { PrismaClient } from '@prisma/client';
import { PaymentService } from './paymentService.js';
import { canonicalWeight } from './lotService.js';
import { AppError } from '../utils/errors.js';

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
          const existingLot = await this.db.lot.findFirst({ where: { id: op.entityId, collectorId: cid } });
          if (existingLot) {
            await this.save(cid, op, hash, 'APPLIED');
            results.push({ operationId: op.operationId, status: 'ALREADY_APPLIED', entityType: 'LOT', entityId: existingLot.id });
            continue;
          }
          const weight = canonicalWeight(p.weight, p.weightUnit ?? 'KILOGRAM');
          if (!['CRT', 'LCD_PANEL', 'PCB', 'CABLE', 'COPPER', 'BATTERY', 'MOTOR', 'MAGNET', 'PLASTIC', 'OTHER'].includes(p.materialCategory)) throw new AppError('VALIDATION_ERROR', 'Invalid material category', 422, { code: 'INVALID_MATERIAL_CATEGORY' });
          if (!['INTACT', 'DAMAGED', 'PARTIAL'].includes(p.condition)) throw new AppError('VALIDATION_ERROR', 'Invalid lot condition', 422, { code: 'INVALID_LOT_CONDITION' });
          if (p.collectionLocation && (p.collectionLocation.latitude != null && (p.collectionLocation.latitude < -90 || p.collectionLocation.latitude > 90) || p.collectionLocation.longitude != null && (p.collectionLocation.longitude < -180 || p.collectionLocation.longitude > 180))) throw new AppError('VALIDATION_ERROR', 'Invalid lot location', 422, { code: 'INVALID_LOCATION' });
          const wasteRegime = ['E_WASTE', 'BATTERY_WASTE', 'OTHER'].includes(p.wasteRegime) ? p.wasteRegime : (p.materialCategory === 'BATTERY' ? 'BATTERY_WASTE' : 'E_WASTE');
          const lot = await this.db.lot.create({ data: { id: op.entityId, collectorId: cid, materialCategory: p.materialCategory, materialSubcategory: p.materialSubcategory, sourceType: p.sourceType, wasteRegime, condition: p.condition, weight, weightUnit: 'KILOGRAM', originalWeight: p.originalWeight ?? p.weight, originalWeightUnit: p.originalWeightUnit ?? p.weightUnit ?? 'KILOGRAM', imageProvenance: p.imageProvenance, collectionLatitude: p.collectionLocation?.latitude, collectionLongitude: p.collectionLocation?.longitude, collectionAreaName: p.collectionLocation?.areaName, collectionLocationPrecision: p.collectionLocation?.precision, notes: p.notes, quotedPrice: p.quotedPrice, status: 'CREATED' } });
          await this.save(cid, op, hash, 'APPLIED');
          results.push({ operationId: op.operationId, status: 'APPLIED', entityType: 'LOT', entityId: lot.id });
        } else if (op.operationType === 'UPDATE' && op.entityType === 'LOT') {
          const p = op.payload;
          const weight = canonicalWeight(p.weight, p.weightUnit ?? 'KILOGRAM');
          if (!['INTACT', 'DAMAGED', 'PARTIAL'].includes(p.condition)) throw new AppError('VALIDATION_ERROR', 'Invalid lot condition', 422, { code: 'INVALID_LOT_CONDITION' });
          const out = await this.db.lot.updateMany({ where: { id: op.entityId, collectorId: cid, status: 'CREATED', version: p.clientVersion }, data: { weight, condition: p.condition, notes: p.notes, version: { increment: 1 } } });
          if (!out.count) {
            await this.save(cid, op, hash, 'CONFLICT', 'LOT_UPDATE_CONFLICT');
            results.push({ operationId: op.operationId, status: 'CONFLICT', entityType: 'LOT', entityId: op.entityId, errorCode: 'LOT_UPDATE_CONFLICT' });
            continue;
          }
          await this.save(cid, op, hash, 'APPLIED');
          results.push({ operationId: op.operationId, status: 'APPLIED', entityType: 'LOT', entityId: op.entityId });
        } else if (op.operationType === 'CREATE' && op.entityType === 'PAYMENT') {
          // The local payment id is the idempotency key. Reusing it on retry
          // prevents a successful payment from being recorded twice when the
          // network drops between the database write and sync acknowledgement.
          const payment = await this.payments.record(cid, { ...op.payload, id: op.entityId });
          await this.save(cid, op, hash, 'APPLIED');
          results.push({ operationId: op.operationId, status: 'APPLIED', entityType: 'PAYMENT', entityId: payment.id });
        } else {
          await this.save(cid, op, hash, 'INVALID', 'UNSUPPORTED_SYNC_OPERATION');
          results.push({ operationId: op.operationId, status: 'INVALID', entityType: op.entityType, entityId: op.entityId, errorCode: 'UNSUPPORTED_SYNC_OPERATION' });
        }
      } catch (e: any) {
        // A concurrent retry can win the lot insert before its sync ledger
        // row is committed. Treat the existing owned lot as an applied
        // operation instead of surfacing a false rejection.
        if (e?.code === 'P2002' && op.operationType === 'CREATE' && op.entityType === 'LOT') {
          const existingLot = await this.db.lot.findFirst({ where: { id: op.entityId, collectorId: cid } });
          if (existingLot) {
            await this.save(cid, op, hash, 'APPLIED');
            results.push({ operationId: op.operationId, status: 'ALREADY_APPLIED', entityType: 'LOT', entityId: existingLot.id });
            continue;
          }
        }
        await this.save(cid, op, hash, 'REJECTED', e?.details?.code ?? e?.code ?? 'SYNC_OPERATION_REJECTED');
        results.push({ operationId: op.operationId, status: 'REJECTED', entityType: op.entityType, entityId: op.entityId, errorCode: e?.details?.code ?? e?.code ?? 'SYNC_OPERATION_REJECTED' });
      }
    }
    return { results };
  }

  private async save(cid: string, op: any, hash: string, status: any, errorCode?: string) {
    try {
      return await this.db.syncOperation.create({ data: { operationId: op.operationId, collectorId: cid, operationType: op.operationType, entityType: op.entityType, entityId: op.entityId, status, requestHash: hash, processedAt: new Date(), errorCode } });
    } catch (error: any) {
      // Unique operation ids are expected under retries. Return the winner's
      // row so callers remain idempotent instead of turning a retry into 500.
      if (error?.code === 'P2002') return this.db.syncOperation.findUnique({ where: { operationId_collectorId: { operationId: op.operationId, collectorId: cid } } });
      throw error;
    }
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
