import type { PrismaClient } from '@prisma/client';
import { AppError } from '../utils/errors.js';
import { assertLotTransition } from './lotStateMachine.js';

export class HandoverService {
  constructor(private db: PrismaClient) {}
  private async get(id: string) {
    const h = await this.db.handover.findUnique({ where: { id }, include: { lot: true, quote: true, recycler: true } });
    if (!h) throw new AppError('NOT_FOUND', 'Handover not found', 404, { code: 'HANDOVER_NOT_FOUND' });
    if (h.status !== 'EXPIRED' && h.expiresAt <= new Date()) { await this.db.handover.update({ where: { id }, data: { status: 'EXPIRED' } }); const refreshed = await this.db.handover.findUnique({ where: { id }, include: { lot: true, quote: true, recycler: true } }); return refreshed ?? h; }
    return h;
  }
  async create(cid: string, p: any) {
    const lot = await this.db.lot.findFirst({ where: { id: p.lotId, collectorId: cid } });
    if (!lot) throw new AppError('NOT_FOUND', 'Lot not found', 404, { code: 'LOT_NOT_FOUND' });
    const q = await this.db.quote.findFirst({ where: { id: p.quoteId, lotId: lot.id, status: 'ACCEPTED' }, include: { recycler: true } });
    if (!q) throw new AppError('CONFLICT', 'Accepted quote required', 409, { code: 'QUOTE_NOT_ACCEPTED' });
    if (q.validUntil <= new Date()) throw new AppError('CONFLICT', 'Quote has expired', 409, { code: 'QUOTE_EXPIRED' });
    if (q.recycler.authorizationStatus !== 'VERIFIED') throw new AppError('CONFLICT', 'Recycler is not verified', 409, { code: 'RECYCLER_NOT_VERIFIED' });
    assertLotTransition(lot.status, 'HANDED_OVER');
    const old = await this.db.handover.findFirst({ where: { lotId: lot.id, status: { in: ['GENERATED', 'DISPUTED', 'PENDING_MANUAL_REVIEW'] } } });
    if (old) return old;
    const ref = `HOV-${new Date().toISOString().slice(0, 10).replaceAll('-', '')}-${Math.random().toString(36).slice(2, 7).toUpperCase()}`;
    return this.db.$transaction(async tx => {
      const h = await tx.handover.create({ data: { lotId: lot.id, quoteId: q.id, collectorId: cid, recyclerId: q.recyclerId, photoReference: lot.photoUrl, materialCategory: lot.materialCategory, materialDescription: lot.materialSubcategory, weight: lot.weight, quotedPrice: q.totalQuotedPrice, collectionLocation: { latitude: lot.collectionLatitude, longitude: lot.collectionLongitude, areaName: lot.collectionAreaName }, handoverLocation: p.handoverLocation, timestamp: p.timestamp ? new Date(p.timestamp) : new Date(), referenceId: ref, qrCodeData: ref, expiresAt: new Date(Date.now() + 7 * 86400000) } });
      await tx.lot.update({ where: { id: lot.id }, data: { status: 'HANDED_OVER' } });
      return h;
    });
  }
  async view(id: string, actor: string, role: string) {
    const h = await this.get(id);
    if (role === 'COLLECTOR' && h.collectorId !== actor || role === 'RECYCLER' && h.recyclerId !== actor) throw new AppError('NOT_FOUND', 'Handover not found', 404, { code: 'HANDOVER_NOT_FOUND' });
    return h;
  }
  async byReference(referenceId: string, actor: string) {
    const h = await this.db.handover.findUnique({ where: { referenceId } });
    if (!h || h.collectorId !== actor) throw new AppError('NOT_FOUND', 'Handover not found', 404, { code: 'HANDOVER_NOT_FOUND' });
    return this.get(h.id);
  }
  async mark(id: string, cid: string) { const h = await this.view(id, cid, 'COLLECTOR'); if (h.status !== 'GENERATED') throw new AppError('CONFLICT', 'Handover is not actionable', 409, { code: 'HANDOVER_NOT_ACTIONABLE' }); return h; }
  async updateEvidence(id: string, cid: string, p: { actualWeight: number; materialMatch: boolean; scalePhotoReference?: string; collectorConfirmed?: boolean }) {
    const h = await this.view(id, cid, 'COLLECTOR');
    if (!['GENERATED', 'CONFIRMED_BY_RECYCLER'].includes(h.status)) throw new AppError('CONFLICT', 'Handover evidence is locked', 409, { code: 'HANDOVER_EVIDENCE_LOCKED' });
    if (!Number.isFinite(p.actualWeight) || p.actualWeight <= 0 || p.actualWeight > 500) throw new AppError('VALIDATION_ERROR', 'Invalid actual weight', 422, { code: 'INVALID_ACTUAL_WEIGHT' });
    const updated = await this.db.handover.updateMany({
      where: { id, collectorId: cid, status: { in: ['GENERATED', 'CONFIRMED_BY_RECYCLER'] } },
      data: {
        actualWeight: Number(p.actualWeight.toFixed(3)),
        materialConfirmedAt: p.materialMatch ? new Date() : null,
        collectorConfirmedAt: p.collectorConfirmed === false ? null : new Date(),
        actualWeightPhotoReference: p.scalePhotoReference?.trim() || null
      }
    });
    if (!updated.count) throw new AppError('CONFLICT', 'Handover evidence is no longer editable', 409, { code: 'HANDOVER_EVIDENCE_LOCKED' });
    return this.get(id);
  }
  async confirm(id: string, rid: string, p: any) {
    const h = await this.view(id, rid, 'RECYCLER');
    if (h.recycler.authorizationStatus !== 'VERIFIED') throw new AppError('CONFLICT', 'Recycler is not verified', 409, { code: 'RECYCLER_NOT_VERIFIED' });
    if (h.status !== 'GENERATED') throw new AppError('CONFLICT', 'Handover is not actionable', 409, { code: 'HANDOVER_NOT_ACTIONABLE' });
    const actual = Number(p.actualWeight);
    if (!Number.isFinite(actual) || actual <= 0) throw new AppError('VALIDATION_ERROR', 'Invalid actual weight', 422, { code: 'INVALID_ACTUAL_WEIGHT' });
    const diff = Math.abs(actual - h.weight) / h.weight;
    return this.db.$transaction(async tx => {
      if (!p.materialMatch || diff > .05) {
        const claimed = await tx.handover.updateMany({
          where: { id, status: 'GENERATED' },
          data: { status: 'DISPUTED', actualWeight: actual, actualWeightPhotoReference: p.scalePhotoReference?.trim() || null, materialConfirmedAt: p.materialMatch ? new Date() : null, recyclerNotes: p.notes }
        });
        if (!claimed.count) throw new AppError('CONFLICT', 'Handover is no longer actionable', 409, { code: 'HANDOVER_ALREADY_REVIEWED' });
        const dispute = await tx.dispute.create({ data: { handoverId: h.id, lotId: h.lotId, collectorId: h.collectorId, recyclerId: h.recyclerId, type: !p.materialMatch ? 'MATERIAL_MISMATCH' : 'WEIGHT_DISCREPANCY', reportedBy: rid, description: p.reason || p.notes || 'Handover discrepancy', claimedValue: h.weight, actualValue: actual } });
        const handover = await tx.handover.findUniqueOrThrow({ where: { id } });
        return { handover, dispute };
      }
      const updated = await tx.handover.updateMany({ where: { id, status: 'GENERATED' }, data: { status: 'CONFIRMED_BY_RECYCLER', actualWeight: actual, actualWeightPhotoReference: p.scalePhotoReference?.trim() || null, materialConfirmedAt: p.materialMatch ? new Date() : null, recyclerNotes: p.notes, recyclerConfirmedAt: new Date() } });
      if (!updated.count) throw new AppError('CONFLICT', 'Handover is no longer actionable', 409, { code: 'HANDOVER_ALREADY_REVIEWED' });
      return { handover: await tx.handover.findUniqueOrThrow({ where: { id } }) };
    });
  }
  async reject(id: string, rid: string, reason: string) {
    const h = await this.view(id, rid, 'RECYCLER');
    if (h.recycler.authorizationStatus !== 'VERIFIED') throw new AppError('CONFLICT', 'Recycler is not verified', 409, { code: 'RECYCLER_NOT_VERIFIED' });
    if (h.status !== 'GENERATED') throw new AppError('CONFLICT', 'Handover is not actionable', 409, { code: 'HANDOVER_NOT_ACTIONABLE' });
    return this.db.$transaction(async tx => {
      const updated = await tx.handover.updateMany({ where: { id, status: 'GENERATED' }, data: { status: 'DISPUTED', recyclerNotes: reason } });
      if (!updated.count) throw new AppError('CONFLICT', 'Handover is no longer actionable', 409, { code: 'HANDOVER_ALREADY_REVIEWED' });
      const dispute = await tx.dispute.create({ data: { handoverId: h.id, lotId: h.lotId, collectorId: h.collectorId, recyclerId: h.recyclerId, type: 'OTHER', reportedBy: rid, description: reason } });
      const handover = await tx.handover.findUniqueOrThrow({ where: { id } });
      return { handover, dispute };
    });
  }
  recyclerList(rid: string) { return this.db.handover.findMany({ where: { recyclerId: rid }, orderBy: { createdAt: 'desc' } }); }
  async dispute(cid: string, id: string, p: any) { const h = await this.view(id, cid, 'COLLECTOR'); return this.db.dispute.create({ data: { handoverId: h.id, lotId: h.lotId, collectorId: cid, recyclerId: h.recyclerId, type: p.type, reportedBy: cid, description: p.description, claimedValue: p.claimedWeight, evidence: p.evidence } }); }
  async adminDisputes() { return this.db.dispute.findMany({ orderBy: { createdAt: 'desc' } }); }
  async adminDispute(id: string) { const d = await this.db.dispute.findUnique({ where: { id }, include: { handover: true } }); if (!d) throw new AppError('NOT_FOUND', 'Dispute not found', 404, { code: 'DISPUTE_NOT_FOUND' }); return d; }
  async resolve(id: string, admin: string, p: any) { const d = await this.db.dispute.findUnique({ where: { id } }); if (!d) throw new AppError('NOT_FOUND', 'Dispute not found', 404, { code: 'DISPUTE_NOT_FOUND' }); if (d.status === 'RESOLVED') throw new AppError('CONFLICT', 'Dispute already resolved', 409, { code: 'DISPUTE_ALREADY_RESOLVED' }); return this.db.$transaction(async tx => { const out = await tx.dispute.update({ where: { id }, data: { status: 'RESOLVED', resolution: p.resolution, resolutionNotes: p.notes, resolvedBy: admin, resolvedAt: new Date() } }); await tx.handover.update({ where: { id: d.handoverId }, data: { status: 'COMPLETED' } }); return out; }); }
}
