import crypto from 'node:crypto';
import type { PrismaClient } from '@prisma/client';
import { PaymentService } from './paymentService.js';
import { canonicalWeight } from './lotService.js';
import { assertInventoryInvariant, recordInventoryMovement } from './inventoryLedger.js';
import { evaluateSettlementVariance } from './settlementRules.js';
import { AppError } from '../utils/errors.js';

type SyncResult = { operationId: string; status: string; entityType?: string; entityId?: string; errorCode?: string };
type SyncRole = 'COLLECTOR' | 'RECYCLER';
type ChangePosition = { at: string; id: string };
type SyncCursor = { version: 1; positions: Record<string, ChangePosition> };

const formalPaymentMethods = ['CASH', 'BANK_TRANSFER', 'DIGITAL_WALLET'];

function hash(value: unknown) {
  return crypto.createHash('sha256').update(JSON.stringify(value)).digest('hex');
}

export function encodeSyncCursor(positions: Record<string, ChangePosition>) {
  return Buffer.from(JSON.stringify({ version: 1, positions }), 'utf8').toString('base64url');
}

export function decodeSyncCursor(value: string): SyncCursor {
  try {
    const decoded = JSON.parse(Buffer.from(value, 'base64url').toString('utf8')) as SyncCursor;
    if (decoded?.version !== 1 || !decoded.positions || typeof decoded.positions !== 'object' || Array.isArray(decoded.positions)) throw new Error('invalid');
    for (const position of Object.values(decoded.positions)) {
      if (!position || typeof position.at !== 'string' || !Number.isFinite(new Date(position.at).getTime()) || typeof position.id !== 'string' || !position.id) throw new Error('invalid');
    }
    return decoded;
  } catch {
    throw new AppError('VALIDATION_ERROR', 'Invalid sync cursor', 400, { code: 'INVALID_SYNC_CURSOR' });
  }
}

function asId(value: unknown, field: string) {
  if (typeof value !== 'string' || !/^[A-Za-z0-9_-]{1,120}$/.test(value)) throw new AppError('VALIDATION_ERROR', `${field} is invalid`, 422, { code: `INVALID_${field.toUpperCase()}` });
  return value;
}

function asPositive(value: unknown, field: string, max = 100000000) {
  const number = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(number) || number <= 0 || number > max) throw new AppError('VALIDATION_ERROR', `${field} must be positive`, 422, { code: `INVALID_${field.toUpperCase()}` });
  return number;
}

function asNonNegative(value: unknown, field: string, max = 100000000) {
  const number = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(number) || number < 0 || number > max) throw new AppError('VALIDATION_ERROR', `${field} cannot be negative`, 422, { code: `INVALID_${field.toUpperCase()}` });
  return number;
}

function asDate(value: unknown, field: string, allowMissing = true) {
  if (value == null && allowMissing) return new Date();
  if (typeof value !== 'string') throw new AppError('VALIDATION_ERROR', `${field} must be an ISO date`, 422, { code: `INVALID_${field.toUpperCase()}` });
  const date = new Date(value);
  if (!Number.isFinite(date.getTime()) || date > new Date()) throw new AppError('VALIDATION_ERROR', `${field} is invalid`, 422, { code: `INVALID_${field.toUpperCase()}` });
  return date;
}

export class SyncService {
  constructor(private db: PrismaClient, private payments: PaymentService, private signingSecret?: string) {}

  private async audit(tx: any, actorId: string, actorRole: SyncRole, event: string, entityType: string, entityId: string, metadata: Record<string, unknown>) {
    await tx.auditEvent.create({ data: { actorId, actorRole, event, entityType, entityId, metadata } });
    await tx.materialPassportEvent.create({ data: { entityType, entityId, eventType: event, actorId, actorRole, metadata, evidenceHash: hash(metadata), occurredAt: new Date() } });
  }

  private async releaseContribution(tx: any, contribution: any, sourceId: string, reason: string) {
    const before = await tx.inventoryBalance.findUniqueOrThrow({ where: { id: contribution.inventoryBalanceId } });
    const released = await tx.inventoryBalance.updateMany({
      where: { id: contribution.inventoryBalanceId, reservedKg: { gte: contribution.quantityKg } },
      data: { reservedKg: { decrement: contribution.quantityKg }, availableKg: { increment: contribution.quantityKg } }
    });
    if (!released.count) throw new AppError('CONFLICT', 'Reserved inventory changed before it could be released', 409, { code: 'INVENTORY_RELEASE_CONFLICT' });
    const after = await tx.inventoryBalance.findUniqueOrThrow({ where: { id: contribution.inventoryBalanceId } });
    assertInventoryInvariant(after);
    await recordInventoryMovement(tx, before, after, 'RELEASE', contribution.quantityKg, 'POOL_CONTRIBUTION', sourceId, { poolId: contribution.poolId, contributionId: contribution.id, reason });
    await tx.poolContribution.update({ where: { id: contribution.id }, data: { status: 'RELEASED', handoverId: null, finalAcceptedKg: null, finalPayout: null } });
  }

  private async expireHandover(tx: any, handover: any, actorId: string) {
    const expired = await tx.supplyHandover.updateMany({
      where: { id: handover.id, status: { in: ['PREPARED', 'COLLECTOR_CONFIRMED'] } },
      data: { status: 'EXPIRED', sourceKey: `${handover.sourceKey}:expired:${handover.id}` }
    });
    if (!expired.count) return false;
    if (handover.poolId) {
      const contributions = await tx.poolContribution.findMany({ where: { handoverId: handover.id, status: 'RESERVED' } });
      for (const contribution of contributions) await this.releaseContribution(tx, contribution, handover.id, 'HANDOVER_EXPIRED');
      const remaining = await tx.poolContribution.findMany({ where: { poolId: handover.poolId, status: 'RESERVED' }, select: { quantityKg: true } });
      const remainingKg = remaining.reduce((sum: number, row: any) => sum + row.quantityKg, 0);
      const pool = await tx.pooledConsignment.findUniqueOrThrow({ where: { id: handover.poolId }, select: { minimumQuantityKg: true } });
      await tx.pooledConsignment.update({ where: { id: handover.poolId }, data: { totalReservedKg: Number(remainingKg.toFixed(3)), status: remainingKg >= pool.minimumQuantityKg ? 'THRESHOLD_MET' : 'FORMING' } });
    } else if (handover.bulkLotId) {
      const lot = await tx.bulkLot.findUnique({ where: { id: handover.bulkLotId } });
      if (lot?.status === 'RESERVED') {
        const before = await tx.inventoryBalance.findUniqueOrThrow({ where: { kabadiwalaId_materialCategory_grade: { kabadiwalaId: lot.kabadiwalaId, materialCategory: lot.materialCategory, grade: lot.grade } } });
        const released = await tx.inventoryBalance.updateMany({ where: { id: before.id, reservedKg: { gte: lot.quantityKg } }, data: { reservedKg: { decrement: lot.quantityKg }, availableKg: { increment: lot.quantityKg } } });
        if (!released.count) throw new AppError('CONFLICT', 'Reserved inventory changed before it could be released', 409, { code: 'INVENTORY_RELEASE_CONFLICT' });
        const after = await tx.inventoryBalance.findUniqueOrThrow({ where: { id: before.id } });
        assertInventoryInvariant(after);
        await recordInventoryMovement(tx, before, after, 'RELEASE', lot.quantityKg, 'SUPPLY_HANDOVER', handover.id, { bulkLotId: lot.id, reason: 'HANDOVER_EXPIRED' });
        await tx.bulkLot.update({ where: { id: lot.id }, data: { status: 'LISTED', reservedForId: null } });
        await tx.bulkOffer.updateMany({ where: { bulkLotId: lot.id, status: 'ACCEPTED' }, data: { status: 'CANCELLED' } });
      }
    }
    await this.audit(tx, actorId, 'RECYCLER', 'HANDOVER_EXPIRED', 'SUPPLY_HANDOVER', handover.id, { via: 'OFFLINE_SYNC' });
    return true;
  }

  private async findCurrentRecycler(tx: any, recyclerId: string, materialCategory: string) {
    const recycler = await tx.recycler.findUnique({ where: { id: recyclerId }, include: { materials: true, rates: true } });
    if (!recycler || recycler.authorizationStatus !== 'VERIFIED' || (recycler.authorizationValidUntil && recycler.authorizationValidUntil <= new Date())) throw new AppError('CONFLICT', 'Recycler authorization is not current', 409, { code: 'RECYCLER_NOT_VERIFIED' });
    if (!recycler.materials.some((item: any) => item.category === materialCategory)) throw new AppError('CONFLICT', 'Recycler is not authorized for this material', 409, { code: 'RECYCLER_MATERIAL_UNSUPPORTED' });
    return recycler;
  }

  private verifyQr(qrCodeData: string, handover: any, recyclerId: string) {
    if (!this.signingSecret) throw new AppError('INTERNAL_SERVER_ERROR', 'Traceability signing is not configured', 500, { code: 'TRACEABILITY_SECRET_MISSING' });
    const parts = qrCodeData.split('.');
    if (parts.length !== 3 || parts[0] !== 'kc-supply-handover-v1') throw new AppError('VALIDATION_ERROR', 'Invalid handover QR', 422, { code: 'INVALID_HANDOVER_QR' });
    const expected = crypto.createHmac('sha256', this.signingSecret).update(`kc-supply-handover-v1.${parts[1]}`).digest('base64url');
    const actual = Buffer.from(parts[2]);
    const expectedBytes = Buffer.from(expected);
    if (actual.length !== expectedBytes.length || !crypto.timingSafeEqual(actual, expectedBytes)) throw new AppError('VALIDATION_ERROR', 'Handover QR has been altered', 422, { code: 'INVALID_HANDOVER_SIGNATURE' });
    let payload: any;
    try { payload = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8')); } catch { throw new AppError('VALIDATION_ERROR', 'Invalid handover QR payload', 422, { code: 'INVALID_HANDOVER_QR' }); }
    if (payload.version !== 1 || payload.referenceId !== handover.referenceId || payload.recyclerId !== recyclerId || typeof payload.nonce !== 'string') throw new AppError('CONFLICT', 'Handover QR does not match the server record', 409, { code: 'HANDOVER_QR_MISMATCH' });
    if (crypto.createHash('sha256').update(String(payload.nonce)).digest('hex') !== handover.qrNonceHash) throw new AppError('CONFLICT', 'Handover nonce has already been replaced or is invalid', 409, { code: 'HANDOVER_NONCE_MISMATCH' });
    if (qrCodeData !== handover.qrCodeData) throw new AppError('CONFLICT', 'This handover QR is not the current server record', 409, { code: 'HANDOVER_QR_MISMATCH' });
    return payload;
  }

  private async applyPoolContribution(cid: string, op: any, requestHash: string) {
    const p = op.payload ?? {};
    const poolId = asId(p.poolId, 'poolId');
    const quantityKg = asPositive(p.quantityKg, 'quantityKg');
    const grade = typeof p.grade === 'string' && p.grade.trim() ? p.grade.trim().slice(0, 80) : 'UNSPECIFIED';
    const sourceListingIds = Array.isArray(p.sourceListingIds) ? p.sourceListingIds.map((value: unknown) => asId(value, 'sourceListingId')) : [];
    if (new Set(sourceListingIds).size !== sourceListingIds.length) throw new AppError('VALIDATION_ERROR', 'sourceListingIds must be unique', 422, { code: 'DUPLICATE_SOURCE_LISTING' });
    const result = await this.db.$transaction(async (tx: any) => {
      const existingById = await tx.poolContribution.findUnique({ where: { id: op.entityId } });
      if (existingById) {
        if (existingById.collectorId !== cid || existingById.poolId !== poolId || Math.abs(existingById.quantityKg - quantityKg) > 0.0001 || existingById.grade !== grade) throw new AppError('CONFLICT', 'Contribution id is already owned by a different pool or Collector', 409, { code: 'POOL_CONTRIBUTION_ID_CONFLICT' });
        await this.save(cid, op, requestHash, 'APPLIED', undefined, tx);
        return { status: 'ALREADY_APPLIED', entityType: 'POOL_CONTRIBUTION', entityId: existingById.id };
      }
      const pool = await tx.pooledConsignment.findUnique({ where: { id: poolId } });
      if (!pool || !['FORMING', 'THRESHOLD_MET'].includes(pool.status)) throw new AppError('CONFLICT', 'This pool is no longer accepting contributions', 409, { code: 'POOL_NOT_JOINABLE' });
      if (pool.preferredGrade && pool.preferredGrade !== grade) throw new AppError('VALIDATION_ERROR', 'This pool only accepts its preferred material grade', 422, { code: 'POOL_GRADE_MISMATCH' });
      const recycler = await this.findCurrentRecycler(tx, pool.recyclerId, pool.materialCategory);
      const demand = pool.requirementId ? await tx.procurementRequirement.findUnique({ where: { id: pool.requirementId }, select: { maxRatePerKg: true } }) : null;
      const serverRate = demand?.maxRatePerKg ?? recycler.rates.find((row: any) => row.materialCategory === pool.materialCategory)?.pricePerKg ?? 0;
      if (!Number.isFinite(serverRate) || serverRate <= 0) throw new AppError('CONFLICT', 'A current Recycler rate is required before joining this pool', 409, { code: 'POOL_RATE_UNAVAILABLE' });
      if (p.expectedRatePerKg != null && Math.abs(asPositive(p.expectedRatePerKg, 'expectedRatePerKg') - serverRate) > 0.01) throw new AppError('CONFLICT', 'The expected rate is stale; refresh the pool before joining', 409, { code: 'POOL_RATE_CHANGED' });
      if (sourceListingIds.length) {
        const pickups = await tx.pickupRequest.findMany({ where: { listingId: { in: sourceListingIds }, kabadiwalaId: cid, status: 'COMPLETED' }, select: { listingId: true, finalCategory: true } });
        if (new Set(pickups.map((row: any) => row.listingId)).size !== sourceListingIds.length || pickups.some((row: any) => row.finalCategory !== pool.materialCategory)) throw new AppError('CONFLICT', 'Every source listing must be a completed pickup of the pooled material', 409, { code: 'INVALID_SOURCE_LISTINGS' });
        const [allocatedLots, allocatedContributions] = await Promise.all([
          tx.bulkLot.findMany({ where: { sourceListingIds: { hasSome: sourceListingIds }, status: { not: 'CANCELLED' } }, select: { id: true } }),
          tx.poolContribution.findMany({ where: { sourceListingIds: { hasSome: sourceListingIds }, status: { not: 'RELEASED' } }, select: { id: true } })
        ]);
        if (allocatedLots.length || allocatedContributions.length) throw new AppError('CONFLICT', 'A source listing is already allocated to an active or completed formal stock record', 409, { code: 'SOURCE_LISTING_ALREADY_ALLOCATED' });
      }
      const balance = await tx.inventoryBalance.findFirst({ where: { kabadiwalaId: cid, materialCategory: pool.materialCategory, grade } });
      if (!balance || balance.availableKg < quantityKg) throw new AppError('CONFLICT', 'Not enough available inventory for this contribution', 409, { code: 'POOL_INSUFFICIENT_INVENTORY' });
      const reserved = await tx.inventoryBalance.updateMany({ where: { id: balance.id, availableKg: { gte: quantityKg } }, data: { availableKg: { decrement: quantityKg }, reservedKg: { increment: quantityKg } } });
      if (!reserved.count) throw new AppError('CONFLICT', 'Inventory changed; retry the contribution', 409, { code: 'INVENTORY_RESERVATION_CONFLICT' });
      const after = await tx.inventoryBalance.findUniqueOrThrow({ where: { id: balance.id } });
      assertInventoryInvariant(after);
      await recordInventoryMovement(tx, balance, after, 'RESERVATION', quantityKg, 'POOL_CONTRIBUTION', op.entityId, { poolId, via: 'OFFLINE_SYNC' });
      const contribution = await tx.poolContribution.create({ data: { id: op.entityId, poolId, collectorId: cid, inventoryBalanceId: balance.id, sourceListingIds, materialCategory: pool.materialCategory, grade, quantityKg, expectedRatePerKg: serverRate, expectedPayout: Number((serverRate * quantityKg).toFixed(2)) } });
      const claimedPool = await tx.pooledConsignment.updateMany({ where: { id: poolId, status: { in: ['FORMING', 'THRESHOLD_MET'] } }, data: { totalReservedKg: { increment: quantityKg } } });
      if (!claimedPool.count) throw new AppError('CONFLICT', 'Pool changed before the contribution could be added', 409, { code: 'POOL_UPDATE_CONFLICT' });
      const updatedPool = await tx.pooledConsignment.findUniqueOrThrow({ where: { id: poolId } });
      const nextStatus = updatedPool.totalReservedKg >= updatedPool.minimumQuantityKg ? 'THRESHOLD_MET' : 'FORMING';
      if (updatedPool.status !== nextStatus) await tx.pooledConsignment.update({ where: { id: poolId }, data: { status: nextStatus } });
      await this.audit(tx, cid, 'COLLECTOR', 'POOL_CONTRIBUTION_RESERVED', 'POOL_CONTRIBUTION', contribution.id, { poolId, quantityKg, totalReservedKg: updatedPool.totalReservedKg, via: 'OFFLINE_SYNC' });
      await this.save(cid, op, requestHash, 'APPLIED', undefined, tx);
      return { status: 'APPLIED', entityType: 'POOL_CONTRIBUTION', entityId: contribution.id };
    });
    return { operationId: op.operationId, ...result };
  }

  private async applyPoolRelease(cid: string, op: any, requestHash: string) {
    if (op.payload?.action !== 'RELEASE') throw new AppError('VALIDATION_ERROR', 'Unsupported pool contribution sync action', 422, { code: 'UNSUPPORTED_POOL_CONTRIBUTION_SYNC_ACTION' });
    const result = await this.db.$transaction(async (tx: any) => {
      const contribution = await tx.poolContribution.findUnique({ where: { id: op.entityId } });
      if (!contribution || contribution.collectorId !== cid) throw new AppError('NOT_FOUND', 'Pool contribution not found', 404, { code: 'POOL_CONTRIBUTION_NOT_FOUND' });
      if (contribution.status === 'RELEASED') {
        await this.save(cid, op, requestHash, 'APPLIED', undefined, tx);
        return { status: 'ALREADY_APPLIED', entityType: 'POOL_CONTRIBUTION', entityId: contribution.id };
      }
      const pool = await tx.pooledConsignment.findUnique({ where: { id: contribution.poolId } });
      if (contribution.status !== 'RESERVED' || !pool || !['FORMING', 'THRESHOLD_MET'].includes(pool.status)) throw new AppError('CONFLICT', 'This contribution can no longer be released', 409, { code: 'POOL_CONTRIBUTION_NOT_RELEASABLE' });
      await this.releaseContribution(tx, contribution, contribution.id, 'COLLECTOR_RELEASED');
      const claimed = await tx.pooledConsignment.updateMany({ where: { id: pool.id, status: { in: ['FORMING', 'THRESHOLD_MET'] }, totalReservedKg: { gte: contribution.quantityKg } }, data: { totalReservedKg: { decrement: contribution.quantityKg } } });
      if (!claimed.count) throw new AppError('CONFLICT', 'Pool total changed before the contribution could be released', 409, { code: 'POOL_UPDATE_CONFLICT' });
      const updatedPool = await tx.pooledConsignment.findUniqueOrThrow({ where: { id: pool.id } });
      const nextStatus = updatedPool.totalReservedKg >= updatedPool.minimumQuantityKg ? 'THRESHOLD_MET' : 'FORMING';
      if (updatedPool.status !== nextStatus) await tx.pooledConsignment.update({ where: { id: pool.id }, data: { status: nextStatus } });
      await this.audit(tx, cid, 'COLLECTOR', 'POOL_CONTRIBUTION_RELEASED', 'POOL_CONTRIBUTION', contribution.id, { poolId: pool.id, quantityKg: contribution.quantityKg, via: 'OFFLINE_SYNC' });
      await this.save(cid, op, requestHash, 'APPLIED', undefined, tx);
      return { status: 'APPLIED', entityType: 'POOL_CONTRIBUTION', entityId: contribution.id };
    });
    return { operationId: op.operationId, ...result };
  }

  private async applyPoolSettlementDecision(cid: string, op: any, requestHash: string) {
    const p = op.payload ?? {};
    if (p.action !== 'SETTLEMENT_DECISION' || !['ACCEPT', 'RAISE_ISSUE'].includes(p.decision)) throw new AppError('VALIDATION_ERROR', 'Unsupported pool settlement sync action', 422, { code: 'UNSUPPORTED_POOL_SETTLEMENT_SYNC_ACTION' });
    if (p.decision === 'RAISE_ISSUE' && (typeof p.reasonCode !== 'string' || !p.reasonCode.trim())) throw new AppError('VALIDATION_ERROR', 'A reason code is required when raising a settlement issue', 422, { code: 'SETTLEMENT_REASON_REQUIRED' });
    const result = await this.db.$transaction(async (tx: any) => {
      const contribution = await tx.poolContribution.findUnique({ where: { id: op.entityId } });
      if (!contribution || contribution.collectorId !== cid || !contribution.handoverId) throw new AppError('NOT_FOUND', 'Pool settlement not found', 404, { code: 'POOL_SETTLEMENT_NOT_FOUND' });
      const settlement = await tx.poolSettlement.findUnique({ where: { contributionId: contribution.id } });
      if (!settlement || settlement.status !== 'PENDING_COLLECTOR_CONFIRMATION') throw new AppError('CONFLICT', 'This contribution settlement is no longer actionable', 409, { code: 'SETTLEMENT_ALREADY_DECIDED' });
      const changed = await tx.poolSettlement.updateMany({ where: { contributionId: contribution.id, status: 'PENDING_COLLECTOR_CONFIRMATION' }, data: { status: p.decision === 'ACCEPT' ? 'COMPLETED' : 'DISPUTED', collectorDecision: p.decision, reasonCode: p.reasonCode ?? undefined, evidenceReference: p.evidenceReference ?? undefined, updatedAt: new Date() } });
      if (!changed.count) throw new AppError('CONFLICT', 'Settlement was already decided', 409, { code: 'SETTLEMENT_ALREADY_DECIDED' });
      const handover = await tx.supplyHandover.findUniqueOrThrow({ where: { id: contribution.handoverId } });
      if (p.decision === 'ACCEPT') {
        const acceptedKg = contribution.finalAcceptedKg ?? contribution.quantityKg;
        const before = await tx.inventoryBalance.findUniqueOrThrow({ where: { id: contribution.inventoryBalanceId } });
        const moved = await tx.inventoryBalance.updateMany({ where: { id: contribution.inventoryBalanceId, reservedKg: { gte: contribution.quantityKg } }, data: { reservedKg: { decrement: contribution.quantityKg }, availableKg: { increment: Math.max(0, contribution.quantityKg - acceptedKg) }, soldKg: { increment: acceptedKg } } });
        if (!moved.count) throw new AppError('CONFLICT', 'Reserved inventory changed before settlement acceptance', 409, { code: 'INVENTORY_SETTLEMENT_CONFLICT' });
        const after = await tx.inventoryBalance.findUniqueOrThrow({ where: { id: contribution.inventoryBalanceId } });
        assertInventoryInvariant(after);
        await recordInventoryMovement(tx, before, after, 'SALE', acceptedKg, 'SUPPLY_HANDOVER', handover.id, { poolId: contribution.poolId, contributionId: contribution.id, via: 'OFFLINE_SYNC' });
        await tx.poolContribution.update({ where: { id: contribution.id }, data: { status: 'SETTLED' } });
        const outstanding = await tx.poolContribution.count({ where: { poolId: contribution.poolId, status: { not: 'SETTLED' } } });
        if (!outstanding) {
          await tx.pooledConsignment.update({ where: { id: contribution.poolId }, data: { status: 'SETTLED' } });
          await tx.supplyHandover.updateMany({ where: { id: handover.id, status: 'REVIEW_REQUIRED' }, data: { status: 'COMPLETED' } });
        }
        await this.audit(tx, cid, 'COLLECTOR', 'POOL_SETTLEMENT_ACCEPTED', 'POOL_CONTRIBUTION', contribution.id, { handoverId: handover.id, via: 'OFFLINE_SYNC' });
      } else {
        await tx.anomalyFlag.create({ data: { entityType: 'POOL_CONTRIBUTION', entityId: contribution.id, ruleCode: p.reasonCode, severity: 'MEDIUM', details: { handoverId: handover.id, evidenceReference: p.evidenceReference ?? null, notes: p.notes ?? null, via: 'OFFLINE_SYNC' } } });
        await this.audit(tx, cid, 'COLLECTOR', 'POOL_SETTLEMENT_DISPUTED', 'POOL_CONTRIBUTION', contribution.id, { handoverId: handover.id, reasonCode: p.reasonCode, via: 'OFFLINE_SYNC' });
      }
      await this.save(cid, op, requestHash, 'APPLIED', undefined, tx);
      return { status: 'APPLIED', entityType: 'POOL_SETTLEMENT', entityId: contribution.id };
    });
    return { operationId: op.operationId, ...result };
  }

  private async applyRecyclerConfirmation(cid: string, op: any, requestHash: string) {
    const p = op.payload ?? {};
    if (typeof p.qrCodeData !== 'string' || p.qrCodeData.length < 40 || p.qrCodeData.length > 5000) throw new AppError('VALIDATION_ERROR', 'qrCodeData is invalid', 422, { code: 'INVALID_HANDOVER_QR' });
    const handover = await this.db.supplyHandover.findUnique({ where: { id: op.entityId } });
    if (!handover || handover.recyclerId !== cid) throw new AppError('NOT_FOUND', 'Handover is not available for this Recycler', 404, { code: 'HANDOVER_NOT_FOUND' });
    this.verifyQr(p.qrCodeData, handover, cid);
    if (handover.expiresAt <= new Date()) {
      await this.db.$transaction(async (tx: any) => { await this.expireHandover(tx, handover, cid); });
      throw new AppError('CONFLICT', 'Handover QR has expired; the Kabadiwala must prepare a new QR', 409, { code: 'HANDOVER_EXPIRED' });
    }
    const result = await this.db.$transaction(async (tx: any) => {
      const current = await tx.supplyHandover.findUnique({ where: { id: op.entityId } });
      if (!current || current.recyclerId !== cid) throw new AppError('NOT_FOUND', 'Handover is not available for this Recycler', 404, { code: 'HANDOVER_NOT_FOUND' });
      if (current.status !== 'COLLECTOR_CONFIRMED') throw new AppError('CONFLICT', 'Collector confirmation is required before the Recycler can receive this handover', 409, { code: 'COLLECTOR_CONFIRMATION_REQUIRED' });
      await this.findCurrentRecycler(tx, cid, current.materialCategory);
      const actual = p.actualWeightKg == null ? current.quotedWeightKg : asPositive(p.actualWeightKg, 'actualWeightKg', 100000);
      const accepted = p.acceptedWeightKg == null ? actual : asNonNegative(p.acceptedWeightKg, 'acceptedWeightKg', 100000);
      if (typeof p.materialMatch !== 'undefined' && typeof p.materialMatch !== 'boolean') throw new AppError('VALIDATION_ERROR', 'materialMatch must be boolean', 422, { code: 'INVALID_MATERIAL_MATCH' });
      const materialMatch = p.materialMatch ?? true;
      const rate = p.finalRatePerKg == null ? current.quotedRatePerKg : asPositive(p.finalRatePerKg, 'finalRatePerKg', 1000000);
      if (!Number.isFinite(current.quotedWeightKg) || current.quotedWeightKg <= 0 || actual > current.quotedWeightKg + 0.0001) throw new AppError('VALIDATION_ERROR', 'Actual received weight cannot exceed the reserved handover quantity', 422, { code: 'ACTUAL_WEIGHT_EXCEEDS_SOURCE' });
      if (accepted > actual + 0.0001) throw new AppError('VALIDATION_ERROR', 'Accepted weight cannot exceed actual received weight', 422, { code: 'ACCEPTED_WEIGHT_EXCEEDS_ACTUAL' });
      const variance = evaluateSettlementVariance({ quotedWeightKg: current.quotedWeightKg, actualWeightKg: actual, quotedRatePerKg: current.quotedRatePerKg, finalRatePerKg: rate, acceptedWeightKg: accepted, materialMatch });
      const reasonCode = typeof p.reasonCode === 'string' && p.reasonCode.trim() ? p.reasonCode.trim().slice(0, 120) : undefined;
      if (variance.requiresReview && !reasonCode) throw new AppError('VALIDATION_ERROR', 'A reason code is required for a material or settlement change', 422, { code: 'SETTLEMENT_REASON_REQUIRED' });
      if (p.evidenceReference != null && typeof p.evidenceReference !== 'string') throw new AppError('VALIDATION_ERROR', 'evidenceReference must be text', 422, { code: 'INVALID_EVIDENCE_REFERENCE' });
      if (p.handoverLocation != null && (typeof p.handoverLocation !== 'object' || Array.isArray(p.handoverLocation))) throw new AppError('VALIDATION_ERROR', 'handoverLocation must be an object', 422, { code: 'INVALID_HANDOVER_LOCATION' });
      const status = variance.requiresReview ? 'REVIEW_REQUIRED' : 'COMPLETED';
      const claimed = await tx.supplyHandover.updateMany({ where: { id: current.id, recyclerId: cid, status: 'COLLECTOR_CONFIRMED', expiresAt: { gt: new Date() } }, data: { status, recyclerConfirmedAt: new Date(), finalAcceptedKg: accepted, finalRejectedKg: Math.max(0, actual - accepted), finalRatePerKg: rate, finalValue: Number((accepted * rate).toFixed(2)), handoverLocation: p.handoverLocation ?? current.handoverLocation, reviewReason: variance.requiresReview ? (reasonCode ?? (!materialMatch ? 'MATERIAL_MISMATCH' : 'SETTLEMENT_VARIANCE')) : null, reviewEvidence: p.evidenceReference ?? null } });
      if (!claimed.count) throw new AppError('CONFLICT', 'Handover was already confirmed', 409, { code: 'HANDOVER_REPLAYED' });
      await tx.settlementBreakdown.create({ data: { handoverId: current.id, quotedWeightKg: current.quotedWeightKg, quotedRatePerKg: current.quotedRatePerKg, quotedValue: current.quotedValue, finalAcceptedKg: accepted, finalRejectedKg: Math.max(0, actual - accepted), finalRatePerKg: rate, finalValue: Number((accepted * rate).toFixed(2)), reasonCode: variance.requiresReview ? (reasonCode ?? 'SETTLEMENT_VARIANCE') : null, evidenceReference: p.evidenceReference ?? null, changedBy: cid, status: variance.requiresReview ? 'PENDING_COLLECTOR_CONFIRMATION' : 'COMPLETED' } });
      if (variance.requiresReview) await tx.anomalyFlag.create({ data: { entityType: 'SUPPLY_HANDOVER', entityId: current.id, ruleCode: variance.ruleCode!, severity: variance.ruleCode === 'MATERIAL_MISMATCH' ? 'HIGH' : 'MEDIUM', details: { quotedWeightKg: current.quotedWeightKg, actualWeightKg: actual, quotedRatePerKg: current.quotedRatePerKg, finalRatePerKg: rate, acceptedWeightKg: accepted, reasonCode, via: 'OFFLINE_SYNC' } } });
      if (current.poolId) {
        const contributions = await tx.poolContribution.findMany({ where: { poolId: current.poolId, status: { in: ['RESERVED', 'RECEIVED', 'REVIEW_REQUIRED'] } } });
        const total = contributions.reduce((sum: number, row: any) => sum + row.quantityKg, 0) || 1;
        for (const contribution of contributions) {
          const acceptedForContribution = Number((accepted * contribution.quantityKg / total).toFixed(3));
          await tx.poolContribution.update({ where: { id: contribution.id }, data: { finalAcceptedKg: acceptedForContribution, finalPayout: Number((acceptedForContribution * rate).toFixed(2)), status: variance.requiresReview ? 'REVIEW_REQUIRED' : 'SETTLED' } });
          await tx.poolSettlement.upsert({ where: { contributionId: contribution.id }, update: { acceptedWeightKg: acceptedForContribution, finalRatePerKg: rate, finalValue: Number((acceptedForContribution * rate).toFixed(2)), reasonCode: variance.requiresReview ? (reasonCode ?? 'SETTLEMENT_VARIANCE') : null, evidenceReference: p.evidenceReference ?? undefined, changedBy: cid, status: variance.requiresReview ? 'PENDING_COLLECTOR_CONFIRMATION' : 'COMPLETED' }, create: { contributionId: contribution.id, poolId: current.poolId, quotedWeightKg: contribution.quantityKg, quotedRatePerKg: contribution.expectedRatePerKg, quotedValue: contribution.expectedPayout, acceptedWeightKg: acceptedForContribution, finalRatePerKg: rate, finalValue: Number((acceptedForContribution * rate).toFixed(2)), reasonCode: variance.requiresReview ? (reasonCode ?? 'SETTLEMENT_VARIANCE') : null, evidenceReference: p.evidenceReference ?? null, changedBy: cid, status: variance.requiresReview ? 'PENDING_COLLECTOR_CONFIRMATION' : 'COMPLETED' } });
          if (!variance.requiresReview) {
            const before = await tx.inventoryBalance.findUniqueOrThrow({ where: { id: contribution.inventoryBalanceId } });
            const moved = await tx.inventoryBalance.updateMany({ where: { id: contribution.inventoryBalanceId, reservedKg: { gte: contribution.quantityKg } }, data: { reservedKg: { decrement: contribution.quantityKg }, availableKg: { increment: Math.max(0, contribution.quantityKg - acceptedForContribution) }, soldKg: { increment: acceptedForContribution } } });
            if (!moved.count) throw new AppError('CONFLICT', 'Reserved inventory changed before Recycler settlement', 409, { code: 'INVENTORY_SETTLEMENT_CONFLICT' });
            const after = await tx.inventoryBalance.findUniqueOrThrow({ where: { id: contribution.inventoryBalanceId } });
            assertInventoryInvariant(after);
            await recordInventoryMovement(tx, before, after, 'SALE', acceptedForContribution, 'SUPPLY_HANDOVER', current.id, { poolId: current.poolId, contributionId: contribution.id, via: 'OFFLINE_SYNC' });
          }
        }
        await tx.pooledConsignment.update({ where: { id: current.poolId }, data: { status: variance.requiresReview ? 'REVIEW_REQUIRED' : 'SETTLED' } });
      } else if (current.bulkLotId && !variance.requiresReview) {
        const lot = await tx.bulkLot.findUnique({ where: { id: current.bulkLotId } });
        if (lot) {
          const before = await tx.inventoryBalance.findUniqueOrThrow({ where: { kabadiwalaId_materialCategory_grade: { kabadiwalaId: lot.kabadiwalaId, materialCategory: lot.materialCategory, grade: lot.grade } } });
          const moved = await tx.inventoryBalance.updateMany({ where: { id: before.id, reservedKg: { gte: lot.quantityKg } }, data: { reservedKg: { decrement: lot.quantityKg }, availableKg: { increment: Math.max(0, lot.quantityKg - accepted) }, soldKg: { increment: accepted } } });
          if (!moved.count) throw new AppError('CONFLICT', 'Reserved inventory changed before Recycler settlement', 409, { code: 'INVENTORY_SETTLEMENT_CONFLICT' });
          const after = await tx.inventoryBalance.findUniqueOrThrow({ where: { id: before.id } });
          assertInventoryInvariant(after);
          await recordInventoryMovement(tx, before, after, 'SALE', accepted, 'SUPPLY_HANDOVER', current.id, { bulkLotId: lot.id, via: 'OFFLINE_SYNC' });
          await tx.bulkLot.update({ where: { id: lot.id }, data: { status: 'SOLD' } });
        }
      }
      await this.audit(tx, cid, 'RECYCLER', variance.requiresReview ? 'HANDOVER_REVIEW_REQUIRED' : 'HANDOVER_COMPLETED', 'SUPPLY_HANDOVER', current.id, { actualWeightKg: actual, acceptedWeightKg: accepted, finalRatePerKg: rate, reasonCode: reasonCode ?? null, via: 'OFFLINE_SYNC' });
      await this.save(cid, op, requestHash, 'APPLIED', undefined, tx);
      return { status: 'APPLIED', entityType: 'SUPPLY_HANDOVER', entityId: current.id };
    });
    return { operationId: op.operationId, ...result };
  }

  private async applyQualityControl(cid: string, op: any, requestHash: string) {
    const p = op.payload ?? {};
    if (p.action !== 'QC_COMPLETE' || !['PASS', 'FAIL'].includes(p.decision) || typeof p.notes !== 'string' || p.notes.trim().length < 2) throw new AppError('VALIDATION_ERROR', 'A QC decision and notes are required', 422, { code: 'INVALID_QC_DECISION' });
    const result = await this.db.$transaction(async (tx: any) => {
      const current = await tx.supplyHandover.findFirst({ where: { id: op.entityId, recyclerId: cid, status: { in: ['COMPLETED', 'REVIEW_REQUIRED', 'RECEIVED'] } } });
      if (!current) throw new AppError('NOT_FOUND', 'Received handover not found', 404, { code: 'HANDOVER_NOT_FOUND' });
      if (current.qcStatus !== 'PENDING') throw new AppError('CONFLICT', 'Quality control has already been recorded', 409, { code: 'QC_ALREADY_RECORDED' });
      const failed = p.decision === 'FAIL';
      const claimed = await tx.supplyHandover.updateMany({ where: { id: current.id, recyclerId: cid, status: { in: ['COMPLETED', 'REVIEW_REQUIRED', 'RECEIVED'] }, qcStatus: 'PENDING' }, data: { qcStatus: failed ? 'FAILED' : 'PASSED', qcCompletedAt: new Date(), qcCompletedBy: cid, qcNotes: p.notes.trim().slice(0, 1000), qcEvidenceReference: typeof p.evidenceReference === 'string' ? p.evidenceReference.trim().slice(0, 500) : null, ...(failed ? { status: 'REVIEW_REQUIRED', reviewReason: 'QC_FAILED', reviewEvidence: typeof p.evidenceReference === 'string' ? p.evidenceReference.trim().slice(0, 500) : null } : {}) } });
      if (!claimed.count) throw new AppError('CONFLICT', 'Quality control was already recorded', 409, { code: 'QC_ALREADY_RECORDED' });
      if (failed) {
        await tx.anomalyFlag.create({ data: { entityType: 'SUPPLY_HANDOVER', entityId: current.id, ruleCode: 'QC_FAILED', severity: 'HIGH', details: { notes: p.notes.trim().slice(0, 1000), evidenceReference: p.evidenceReference ?? null, via: 'OFFLINE_SYNC' } } });
        if (current.poolId) await tx.pooledConsignment.updateMany({ where: { id: current.poolId, status: { not: 'CANCELLED' } }, data: { status: 'REVIEW_REQUIRED' } });
      }
      await this.audit(tx, cid, 'RECYCLER', failed ? 'QC_FAILED' : 'QC_COMPLETED', 'SUPPLY_HANDOVER', current.id, { decision: p.decision, notes: p.notes.trim().slice(0, 1000), evidenceReference: p.evidenceReference ?? null, via: 'OFFLINE_SYNC' });
      await this.save(cid, op, requestHash, 'APPLIED', undefined, tx);
      return { status: 'APPLIED', entityType: 'SUPPLY_HANDOVER', entityId: current.id };
    });
    return { operationId: op.operationId, ...result };
  }

  private async applyDisposalEvidence(cid: string, op: any, requestHash: string) {
    const p = op.payload ?? {};
    if (typeof p.evidenceReference !== 'string' || !p.evidenceReference.trim() || typeof p.method !== 'string' || !p.method.trim() || p.deviceDataDestroyed !== true) throw new AppError('VALIDATION_ERROR', 'Complete disposal evidence is required', 422, { code: 'INVALID_DISPOSAL_EVIDENCE' });
    const result = await this.db.$transaction(async (tx: any) => {
      const handover = await tx.supplyHandover.findUnique({ where: { id: op.entityId } });
      if (!handover || handover.recyclerId !== cid) throw new AppError('NOT_FOUND', 'Completed handover not found', 404, { code: 'HANDOVER_NOT_FOUND' });
      if (handover.destructionEvidenceStatus === 'EVIDENCE_RECEIVED') {
        throw new AppError('CONFLICT', 'Disposal evidence was already recorded', 409, { code: 'DISPOSAL_EVIDENCE_ALREADY_RECORDED' });
      }
      if (handover.status !== 'COMPLETED' || !handover.dataBearingDevice || !handover.dataDestructionRequested) throw new AppError('CONFLICT', 'This handover does not require data-destruction evidence', 409, { code: 'DISPOSAL_EVIDENCE_NOT_REQUIRED' });
      const claimed = await tx.supplyHandover.updateMany({ where: { id: handover.id, recyclerId: cid, status: 'COMPLETED', destructionEvidenceStatus: 'RECYCLER_EVIDENCE_PENDING' }, data: { destructionEvidenceStatus: 'EVIDENCE_RECEIVED', recyclerEvidenceReference: p.evidenceReference.trim().slice(0, 500), destructionEvidenceHash: typeof p.evidenceHash === 'string' ? p.evidenceHash.trim().slice(0, 200) : null, destructionCompletedAt: new Date() } });
      if (!claimed.count) throw new AppError('CONFLICT', 'Disposal evidence was already recorded', 409, { code: 'DISPOSAL_EVIDENCE_ALREADY_RECORDED' });
      if (handover.sourceListingIds?.length) await tx.householdListing.updateMany({ where: { id: { in: handover.sourceListingIds }, dataBearingDevice: true, dataDestructionRequested: true }, data: { destructionEvidenceStatus: 'EVIDENCE_RECEIVED', recyclerEvidenceReference: p.evidenceReference.trim().slice(0, 500) } });
      await this.audit(tx, cid, 'RECYCLER', 'DATA_DESTRUCTION_EVIDENCE_RECEIVED', 'SUPPLY_HANDOVER', handover.id, { evidenceReference: p.evidenceReference.trim().slice(0, 500), evidenceHash: p.evidenceHash ?? null, method: p.method.trim().slice(0, 120), notes: typeof p.notes === 'string' ? p.notes.trim().slice(0, 1000) : null, via: 'OFFLINE_SYNC' });
      await this.save(cid, op, requestHash, 'APPLIED', undefined, tx);
      return { status: 'APPLIED', entityType: 'SUPPLY_HANDOVER', entityId: handover.id };
    });
    return { operationId: op.operationId, ...result };
  }

  private async applySupplyPayment(cid: string, op: any, requestHash: string) {
    const p = op.payload ?? {};
    const handoverId = asId(p.handoverId, 'handoverId');
    const collectorId = p.collectorId == null ? undefined : asId(p.collectorId, 'collectorId');
    const amount = asPositive(p.amount, 'amount');
    if (typeof p.method !== 'string' || !formalPaymentMethods.includes(p.method)) throw new AppError('VALIDATION_ERROR', 'Invalid payment method', 422, { code: 'INVALID_PAYMENT_METHOD' });
    const recordedAt = asDate(p.recordedAt, 'recordedAt');
    const result = await this.db.$transaction(async (tx: any) => {
      const handover = await tx.supplyHandover.findUnique({ where: { id: handoverId } });
      if (!handover || handover.recyclerId !== cid || handover.status !== 'COMPLETED') throw new AppError('NOT_FOUND', 'Completed handover not found', 404, { code: 'HANDOVER_NOT_FOUND' });
      const recipientId = collectorId ?? handover.collectorId;
      if (handover.poolId && !collectorId) throw new AppError('VALIDATION_ERROR', 'A pooled payment must identify its contributing Collector', 422, { code: 'COLLECTOR_REQUIRED_FOR_POOL_PAYMENT' });
      const contribution = handover.poolId ? await tx.poolContribution.findFirst({ where: { poolId: handover.poolId, handoverId, collectorId: recipientId } }) : null;
      if (handover.poolId && !contribution) throw new AppError('NOT_FOUND', 'Pool contribution not found', 404, { code: 'CONTRIBUTION_NOT_FOUND' });
      if (!handover.poolId && recipientId !== handover.collectorId) throw new AppError('NOT_FOUND', 'Handover payment recipient not found', 404, { code: 'PAYMENT_RECIPIENT_NOT_FOUND' });
      const expectedAmount = Number((contribution?.finalPayout ?? handover.finalValue ?? 0).toFixed(2));
      if (expectedAmount <= 0) throw new AppError('CONFLICT', 'A positive settled amount is required before payment', 409, { code: 'SETTLEMENT_AMOUNT_UNAVAILABLE' });
      if (amount > expectedAmount + 0.01) throw new AppError('VALIDATION_ERROR', 'Payment cannot exceed the settled amount', 422, { code: 'PAYMENT_EXCEEDS_SETTLEMENT' });
      const paymentHash = hash({ handoverId, collectorId: recipientId, amount, method: p.method, recordedAt: recordedAt.toISOString(), reference: p.reference ?? null, notes: p.notes ?? null });
      const sourceKey = `SUPPLY_HANDOVER:${handoverId}:COLLECTOR:${recipientId}`;
      const prior = await tx.supplyPayment.findUnique({ where: { sourceKey } });
      if (prior) {
        if (prior.requestHash && prior.requestHash !== paymentHash) throw new AppError('CONFLICT', 'A different payment is already recorded for this settlement', 409, { code: 'PAYMENT_PAYLOAD_MISMATCH' });
        await this.save(cid, op, requestHash, 'APPLIED', undefined, tx);
        return { status: 'ALREADY_APPLIED', entityType: 'SUPPLY_PAYMENT', entityId: prior.id };
      }
      const underpaid = Math.abs(amount - expectedAmount) > 0.01;
      const delayed = recordedAt.getTime() - (handover.recyclerConfirmedAt ?? handover.updatedAt).getTime() > 7 * 24 * 60 * 60 * 1000;
      const anomaly = underpaid || delayed;
      const payment = await tx.supplyPayment.create({ data: { id: op.entityId, sourceKey, supplyHandoverId: handoverId, collectorId: recipientId, recyclerId: cid, contributionId: contribution?.id ?? null, amount: Number(amount.toFixed(2)), paymentMethod: p.method, recordedAt, reference: typeof p.reference === 'string' ? p.reference.trim().slice(0, 200) : null, notes: typeof p.notes === 'string' ? p.notes.trim().slice(0, 1000) : null, anomaly, anomalyReason: anomaly ? [underpaid ? 'Payment differs from the settled amount' : null, delayed ? 'Payment was recorded more than seven days after receipt' : null].filter(Boolean).join('; ') : null, requestHash: paymentHash } });
      if (underpaid) await tx.anomalyFlag.create({ data: { entityType: 'SUPPLY_PAYMENT', entityId: payment.id, ruleCode: 'PAYMENT_AMOUNT_DIFFERENCE', severity: 'MEDIUM', details: { handoverId, expectedAmount, amount: payment.amount, via: 'OFFLINE_SYNC' } } });
      if (delayed) await tx.anomalyFlag.create({ data: { entityType: 'SUPPLY_PAYMENT', entityId: payment.id, ruleCode: 'DELAYED_PAYMENT', severity: 'MEDIUM', details: { handoverId, recordedAt, receivedAt: handover.recyclerConfirmedAt ?? handover.updatedAt, via: 'OFFLINE_SYNC' } } });
      await this.audit(tx, cid, 'RECYCLER', 'SUPPLY_PAYMENT_RECORDED', 'SUPPLY_PAYMENT', payment.id, { handoverId, collectorId: recipientId, amount: payment.amount, expectedAmount, method: payment.paymentMethod, anomaly, via: 'OFFLINE_SYNC' });
      await this.save(cid, op, requestHash, 'APPLIED', undefined, tx);
      return { status: 'APPLIED', entityType: 'SUPPLY_PAYMENT', entityId: payment.id };
    });
    return { operationId: op.operationId, ...result };
  }

  async batch(cid: string, ops: any[], role: SyncRole = 'COLLECTOR') {
    const results: SyncResult[] = [];
    for (const op of ops) {
      const hash = crypto.createHash('sha256').update(JSON.stringify({ operationType: op.operationType, entityType: op.entityType, entityId: op.entityId, payload: op.payload ?? {} })).digest('hex');
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
        if (op.operationType === 'CREATE' && op.entityType === 'POOL_CONTRIBUTION') {
          if (role !== 'COLLECTOR') throw new AppError('AUTHORIZATION_ERROR', 'Only a Kabadiwala can contribute inventory to a pool', 403, { code: 'POOL_CONTRIBUTION_ROLE_REQUIRED' });
          results.push(await this.applyPoolContribution(cid, op, hash));
        } else if (op.operationType === 'UPDATE' && op.entityType === 'POOL_CONTRIBUTION') {
          if (role !== 'COLLECTOR') throw new AppError('AUTHORIZATION_ERROR', 'Only the contributing Kabadiwala can release a pool contribution', 403, { code: 'POOL_CONTRIBUTION_ROLE_REQUIRED' });
          results.push(await this.applyPoolRelease(cid, op, hash));
        } else if (op.operationType === 'UPDATE' && op.entityType === 'POOL_SETTLEMENT') {
          if (role !== 'COLLECTOR') throw new AppError('AUTHORIZATION_ERROR', 'Only the contributing Kabadiwala can decide a pool settlement', 403, { code: 'POOL_SETTLEMENT_ROLE_REQUIRED' });
          results.push(await this.applyPoolSettlementDecision(cid, op, hash));
        } else if (op.operationType === 'UPDATE' && op.entityType === 'SUPPLY_HANDOVER' && op.payload?.action === 'RECYCLER_CONFIRM') {
          if (role !== 'RECYCLER') throw new AppError('AUTHORIZATION_ERROR', 'Only the receiving Recycler can confirm a formal handover', 403, { code: 'RECYCLER_CONFIRM_ROLE_REQUIRED' });
          results.push(await this.applyRecyclerConfirmation(cid, op, hash));
        } else if (op.operationType === 'UPDATE' && op.entityType === 'SUPPLY_HANDOVER' && op.payload?.action === 'QC_COMPLETE') {
          if (role !== 'RECYCLER') throw new AppError('AUTHORIZATION_ERROR', 'Only the receiving Recycler can record QC', 403, { code: 'QC_ROLE_REQUIRED' });
          results.push(await this.applyQualityControl(cid, op, hash));
        } else if (op.operationType === 'UPDATE' && op.entityType === 'SUPPLY_HANDOVER' && op.payload?.action === 'DISPOSAL_EVIDENCE') {
          if (role !== 'RECYCLER') throw new AppError('AUTHORIZATION_ERROR', 'Only the receiving Recycler can submit disposal evidence', 403, { code: 'DISPOSAL_EVIDENCE_ROLE_REQUIRED' });
          results.push(await this.applyDisposalEvidence(cid, op, hash));
        } else if (op.operationType === 'CREATE' && op.entityType === 'SUPPLY_PAYMENT') {
          if (role !== 'RECYCLER') throw new AppError('AUTHORIZATION_ERROR', 'Only a Recycler can record a formal payment', 403, { code: 'SUPPLY_PAYMENT_ROLE_REQUIRED' });
          results.push(await this.applySupplyPayment(cid, op, hash));
        } else if (op.operationType === 'CREATE' && op.entityType === 'LOT') {
          if (role !== 'COLLECTOR') throw new AppError('AUTHORIZATION_ERROR', 'Only a Kabadiwala can sync legacy lots', 403, { code: 'LEGACY_SYNC_ROLE_REQUIRED' });
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
          if (role !== 'COLLECTOR') throw new AppError('AUTHORIZATION_ERROR', 'Only a Kabadiwala can sync legacy lots', 403, { code: 'LEGACY_SYNC_ROLE_REQUIRED' });
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
          if (role !== 'COLLECTOR') throw new AppError('AUTHORIZATION_ERROR', 'Only a Kabadiwala can sync legacy payments', 403, { code: 'LEGACY_SYNC_ROLE_REQUIRED' });
          // The local payment id is the idempotency key. Reusing it on retry
          // prevents a successful payment from being recorded twice when the
          // network drops between the database write and sync acknowledgement.
          const payment = await this.payments.record(cid, { ...op.payload, id: op.entityId });
          await this.save(cid, op, hash, 'APPLIED');
          results.push({ operationId: op.operationId, status: 'APPLIED', entityType: 'PAYMENT', entityId: payment.id });
        } else if (op.operationType === 'UPDATE' && op.entityType === 'SUPPLY_HANDOVER' && op.payload?.action === 'COLLECTOR_CONFIRM') {
          if (role !== 'COLLECTOR') throw new AppError('AUTHORIZATION_ERROR', 'Only the contributing Kabadiwala can confirm a formal handover', 403, { code: 'COLLECTOR_CONFIRM_ROLE_REQUIRED' });
          if (op.payload?.action !== 'COLLECTOR_CONFIRM') throw new AppError('VALIDATION_ERROR', 'Unsupported formal handover sync action', 422, { code: 'UNSUPPORTED_HANDOVER_SYNC_ACTION' });
          const now = new Date();
          const result = await this.db.$transaction(async (tx: any) => {
            const current = await tx.supplyHandover.findFirst({ where: { id: op.entityId, collectorId: cid } });
            if (!current) throw new AppError('NOT_FOUND', 'Formal handover not found', 404, { code: 'HANDOVER_NOT_FOUND' });
            if (current.status === 'COLLECTOR_CONFIRMED') return { handover: current, alreadyApplied: true };
            if (current.status !== 'PREPARED' || current.expiresAt <= now) throw new AppError('CONFLICT', 'Formal handover is not available for confirmation', 409, { code: current.expiresAt <= now ? 'HANDOVER_EXPIRED' : 'HANDOVER_NOT_CONFIRMABLE' });
            const claimed = await tx.supplyHandover.updateMany({ where: { id: op.entityId, collectorId: cid, status: 'PREPARED', expiresAt: { gt: now } }, data: { status: 'COLLECTOR_CONFIRMED', collectorConfirmedAt: now } });
            if (!claimed.count) throw new AppError('CONFLICT', 'Formal handover was already updated', 409, { code: 'HANDOVER_ALREADY_UPDATED' });
            const handover = await tx.supplyHandover.findUniqueOrThrow({ where: { id: op.entityId } });
            await tx.auditEvent.create({ data: { actorId: cid, actorRole: 'COLLECTOR', event: 'HANDOVER_COLLECTOR_CONFIRMED', entityType: 'SUPPLY_HANDOVER', entityId: op.entityId, metadata: { via: 'OFFLINE_SYNC', operationId: op.operationId } } });
            await tx.materialPassportEvent.create({ data: { entityType: 'SUPPLY_HANDOVER', entityId: op.entityId, eventType: 'HANDOVER_COLLECTOR_CONFIRMED', actorId: cid, actorRole: 'COLLECTOR', metadata: { via: 'OFFLINE_SYNC', operationId: op.operationId }, occurredAt: now } });
            await this.save(cid, op, hash, 'APPLIED', undefined, tx);
            return { handover, alreadyApplied: false };
          });
          if (result.alreadyApplied) {
            await this.save(cid, op, hash, 'APPLIED');
            results.push({ operationId: op.operationId, status: 'ALREADY_APPLIED', entityType: 'SUPPLY_HANDOVER', entityId: result.handover.id });
          } else {
            results.push({ operationId: op.operationId, status: 'APPLIED', entityType: 'SUPPLY_HANDOVER', entityId: result.handover.id });
          }
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

  private async save(cid: string, op: any, hash: string, status: any, errorCode?: string, database: any = this.db) {
    try {
      return await database.syncOperation.create({ data: { operationId: op.operationId, collectorId: cid, operationType: op.operationType, entityType: op.entityType, entityId: op.entityId, status, requestHash: hash, processedAt: new Date(), errorCode } });
    } catch (error: any) {
      // Unique operation ids are expected under retries. Return the winner's
      // row so callers remain idempotent instead of turning a retry into 500.
      if (error?.code === 'P2002') return database.syncOperation.findUnique({ where: { operationId_collectorId: { operationId: op.operationId, collectorId: cid } } });
      throw error;
    }
  }

  private changeWhere(owner: any, field: string, position?: ChangePosition, since?: Date) {
    if (position) {
      const at = new Date(position.at);
      return { AND: [owner, { OR: [{ [field]: { gt: at } }, { [field]: at, id: { gt: position.id } }] }] };
    }
    return since ? { AND: [owner, { [field]: { gte: since } }] } : owner;
  }

  async changes(cid: string, since?: Date, role: SyncRole = 'COLLECTOR', cursor?: string) {
    const isCollector = role === 'COLLECTOR';
    const decoded = cursor ? decodeSyncCursor(cursor) : undefined;
    const position = (key: string) => decoded?.positions[key];
    const [lots, payments, handovers, pickups, inventory, movements, bulkLots, contributions, pools] = await Promise.all([
      isCollector ? this.db.lot.findMany({ where: this.changeWhere({ collectorId: cid }, 'updatedAt', position('lots'), since), orderBy: [{ updatedAt: 'asc' }, { id: 'asc' }] }) : [],
      isCollector ? this.db.payment.findMany({ where: this.changeWhere({ collectorId: cid }, 'updatedAt', position('payments'), since), orderBy: [{ updatedAt: 'asc' }, { id: 'asc' }] }) : [],
      isCollector ? this.db.handover.findMany({ where: this.changeWhere({ collectorId: cid }, 'updatedAt', position('handovers'), since), orderBy: [{ updatedAt: 'asc' }, { id: 'asc' }] }) : [],
      isCollector ? this.db.pickupRequest.findMany({ where: this.changeWhere({ kabadiwalaId: cid }, 'updatedAt', position('pickups'), since), orderBy: [{ updatedAt: 'asc' }, { id: 'asc' }] }) : [],
      isCollector ? this.db.inventoryBalance.findMany({ where: this.changeWhere({ kabadiwalaId: cid }, 'updatedAt', position('inventory'), since), orderBy: [{ updatedAt: 'asc' }, { id: 'asc' }] }) : [],
      isCollector ? this.db.inventoryMovement.findMany({ where: this.changeWhere({ kabadiwalaId: cid }, 'createdAt', position('inventoryMovements'), since), orderBy: [{ createdAt: 'asc' }, { id: 'asc' }] }) : [],
      isCollector ? this.db.bulkLot.findMany({ where: this.changeWhere({ kabadiwalaId: cid }, 'updatedAt', position('bulkLots'), since), orderBy: [{ updatedAt: 'asc' }, { id: 'asc' }] }) : [],
      isCollector ? this.db.poolContribution.findMany({ where: this.changeWhere({ collectorId: cid }, 'updatedAt', position('poolContributions'), since), orderBy: [{ updatedAt: 'asc' }, { id: 'asc' }] }) : [],
      isCollector ? this.db.pooledConsignment.findMany({ where: this.changeWhere({ createdByCollectorId: cid }, 'updatedAt', position('pools'), since), orderBy: [{ updatedAt: 'asc' }, { id: 'asc' }] }) : this.db.pooledConsignment.findMany({ where: this.changeWhere({ recyclerId: cid }, 'updatedAt', position('pools'), since), orderBy: [{ updatedAt: 'asc' }, { id: 'asc' }] })
    ]);
    const contributionIds = (contributions as any[]).map((row: any) => row.id);
    const collectorContributionScope = isCollector
      ? await this.db.poolContribution.findMany({ where: { collectorId: cid }, select: { id: true, handoverId: true } })
      : [];
    const handoverIds = [...new Set([...(contributions as any[]).map((row: any) => row.handoverId), ...(collectorContributionScope as any[]).map((row: any) => row.handoverId)].filter(Boolean))];
    const formalHandovers = isCollector
      ? await this.db.supplyHandover.findMany({ where: this.changeWhere({ OR: [{ collectorId: cid }, ...(handoverIds.length ? [{ id: { in: handoverIds } }] : [])] }, 'updatedAt', position('formalHandovers'), since), orderBy: [{ updatedAt: 'asc' }, { id: 'asc' }] })
      : await this.db.supplyHandover.findMany({ where: this.changeWhere({ recyclerId: cid }, 'updatedAt', position('formalHandovers'), since), orderBy: [{ updatedAt: 'asc' }, { id: 'asc' }] });
    const recyclerPoolScope = !isCollector
      ? await this.db.pooledConsignment.findMany({ where: { recyclerId: cid }, select: { id: true } })
      : [];
    const recyclerPoolIds = !isCollector ? [...new Set([...formalHandovers.map((row: any) => row.poolId), ...(pools as any[]).map((row: any) => row.id), ...(recyclerPoolScope as any[]).map((row: any) => row.id)].filter(Boolean))] : [];
    const contributionScope = isCollector
      ? collectorContributionScope
      : recyclerPoolIds.length ? await this.db.poolContribution.findMany({ where: { poolId: { in: recyclerPoolIds } }, select: { id: true, handoverId: true } }) : [];
    const rawVisibleContributions = isCollector
      ? contributions
      : recyclerPoolIds.length ? await this.db.poolContribution.findMany({ where: this.changeWhere({ poolId: { in: recyclerPoolIds } }, 'updatedAt', position('poolContributions'), since), orderBy: [{ updatedAt: 'asc' }, { id: 'asc' }] }) : [];
    const visibleContributions = isCollector
      ? rawVisibleContributions
      : (rawVisibleContributions as any[]).map((row: any) => ({ id: row.id, poolId: row.poolId, materialCategory: row.materialCategory, grade: row.grade, quantityKg: row.quantityKg, expectedRatePerKg: row.expectedRatePerKg, expectedPayout: row.expectedPayout, finalAcceptedKg: row.finalAcceptedKg, finalPayout: row.finalPayout, status: row.status, handoverId: row.handoverId, createdAt: row.createdAt, updatedAt: row.updatedAt }));
    const allContributionIds = [...new Set([...(contributionIds as string[]), ...(visibleContributions as any[]).map((row: any) => row.id), ...(contributionScope as any[]).map((row: any) => row.id)])];
    const allHandoverIds = [...new Set([...formalHandovers.map((row: any) => row.id), ...(contributionScope as any[]).map((row: any) => row.handoverId), ...handoverIds].filter(Boolean))];
    const [formalPayments, pickupSettlementPayments, poolSettlements, settlementBreakdowns, formalAnomalies, formalEvents] = await Promise.all([
      this.db.supplyPayment.findMany({ where: this.changeWhere({ [isCollector ? 'collectorId' : 'recyclerId']: cid }, 'updatedAt', position('formalPayments'), since), include: { reversals: true }, orderBy: [{ updatedAt: 'asc' }, { id: 'asc' }] }),
      isCollector ? this.db.pickupSettlementPayment.findMany({ where: this.changeWhere({ collectorId: cid }, 'updatedAt', position('pickupSettlementPayments'), since), orderBy: [{ updatedAt: 'asc' }, { id: 'asc' }] }) : [],
      allContributionIds.length ? this.db.poolSettlement.findMany({ where: this.changeWhere({ contributionId: { in: allContributionIds } }, 'updatedAt', position('poolSettlements'), since), orderBy: [{ updatedAt: 'asc' }, { id: 'asc' }] }) : [],
      allHandoverIds.length ? this.db.settlementBreakdown.findMany({ where: this.changeWhere({ handoverId: { in: allHandoverIds } }, 'updatedAt', position('settlementBreakdowns'), since), orderBy: [{ updatedAt: 'asc' }, { id: 'asc' }] }) : [],
      (allHandoverIds.length || allContributionIds.length) ? this.db.anomalyFlag.findMany({ where: this.changeWhere({ OR: [...allHandoverIds.map((id: string) => ({ entityType: 'SUPPLY_HANDOVER', entityId: id })), ...allContributionIds.map((id: string) => ({ entityType: 'POOL_CONTRIBUTION', entityId: id }))] }, 'createdAt', position('formalAnomalies'), since), orderBy: [{ createdAt: 'asc' }, { id: 'asc' }] }) : [],
      this.db.materialPassportEvent.findMany({ where: this.changeWhere({ OR: [{ actorId: cid }, ...allHandoverIds.map((id: string) => ({ entityType: 'SUPPLY_HANDOVER', entityId: id })), ...allContributionIds.map((id: string) => ({ entityType: 'POOL_CONTRIBUTION', entityId: id }))] }, 'occurredAt', position('formalEvents'), since), orderBy: [{ occurredAt: 'asc' }, { id: 'asc' }] })
    ]);
    const visibleFormalHandovers = isCollector
      ? formalHandovers
      : (formalHandovers as any[]).map(({ collectorId: _collectorId, sourceListingIds: _sourceListingIds, ...row }) => row);
    const visibleFormalPayments = isCollector
      ? formalPayments
      : (formalPayments as any[]).map(({ collectorId: _collectorId, contributionId: _contributionId, ...row }) => row);
    const visibleFormalEvents = isCollector
      ? formalEvents
      : (formalEvents as any[]).map(({ actorId: _actorId, ...row }) => row);
    const nextPositions = { ...(decoded?.positions ?? {}) };
    const advance = (key: string, rows: any[], field: string) => {
      for (const row of rows) {
        const date = new Date(row[field]);
        if (!row.id || !Number.isFinite(date.getTime())) continue;
        const current = nextPositions[key];
        if (!current || date.getTime() > new Date(current.at).getTime() || (date.getTime() === new Date(current.at).getTime() && String(row.id) > current.id)) nextPositions[key] = { at: date.toISOString(), id: String(row.id) };
      }
    };
    advance('lots', lots as any[], 'updatedAt'); advance('payments', payments as any[], 'updatedAt'); advance('handovers', handovers as any[], 'updatedAt'); advance('pickups', pickups as any[], 'updatedAt'); advance('inventory', inventory as any[], 'updatedAt'); advance('inventoryMovements', movements as any[], 'createdAt'); advance('bulkLots', bulkLots as any[], 'updatedAt'); advance('poolContributions', visibleContributions as any[], 'updatedAt'); advance('pools', pools as any[], 'updatedAt'); advance('formalHandovers', formalHandovers as any[], 'updatedAt'); advance('formalPayments', formalPayments as any[], 'updatedAt'); advance('pickupSettlementPayments', pickupSettlementPayments as any[], 'updatedAt'); advance('poolSettlements', poolSettlements as any[], 'updatedAt'); advance('settlementBreakdowns', settlementBreakdowns as any[], 'updatedAt'); advance('formalAnomalies', formalAnomalies as any[], 'createdAt'); advance('formalEvents', formalEvents as any[], 'occurredAt');
    return { serverTime: new Date().toISOString(), nextCursor: encodeSyncCursor(nextPositions), changes: { lots, payments, handovers, pickups, inventory, inventoryMovements: movements, bulkLots, poolContributions: visibleContributions, pools, formalHandovers: visibleFormalHandovers, formalPayments: visibleFormalPayments, pickupSettlementPayments, poolSettlements, settlementBreakdowns, formalAnomalies, formalEvents: visibleFormalEvents } };
  }
}
