import { randomBytes } from 'node:crypto';
import type { PrismaClient } from '@prisma/client';
import { AppError } from '../utils/errors.js';
import { assertLotTransition } from './lotStateMachine.js';
import { createSignedTraceabilityQr, verifySignedTraceabilityQr } from './traceability.js';
import type { AuthenticationRateLimiter } from './rateLimiter.js';
import { canonicalWeight } from './lotService.js';
import type { StorageService } from './storage.js';

export class HandoverService {
  constructor(private db: PrismaClient, private readonly traceabilitySecret: string, private readonly rateLimiter?: AuthenticationRateLimiter, private readonly storage?: StorageService) {}
  private async get(id: string) {
    const h = await this.db.handover.findUnique({ where: { id }, include: { lot: true, quote: true, recycler: true } });
    if (!h) throw new AppError('NOT_FOUND', 'Handover not found', 404, { code: 'HANDOVER_NOT_FOUND' });
    if (h.status !== 'EXPIRED' && h.expiresAt <= new Date()) { await this.db.handover.update({ where: { id }, data: { status: 'EXPIRED' } }); const refreshed = await this.db.handover.findUnique({ where: { id }, include: { lot: true, quote: true, recycler: true } }); return refreshed ?? h; }
    return h;
  }
  async create(cid: string, p: any) {
    const lot = await this.db.lot.findFirst({ where: { id: p.lotId, collectorId: cid } });
    if (!lot) throw new AppError('NOT_FOUND', 'Lot not found', 404, { code: 'LOT_NOT_FOUND' });
    // Check the canonical record before validating the original quote/lot
    // transition. A retry after the transaction committed observes a
    // HANDED_OVER lot, but must still return the existing handover instead of
    // turning a successful request into a false conflict.
    const old = await this.db.handover.findFirst({ where: { lotId: lot.id } });
    if (old) return old;
    const weightKg = canonicalWeight(lot.weight, lot.weightUnit);
    const q = await this.db.quote.findFirst({ where: { id: p.quoteId, lotId: lot.id, status: 'ACCEPTED' }, include: { recycler: true } });
    if (!q) throw new AppError('CONFLICT', 'Accepted quote required', 409, { code: 'QUOTE_NOT_ACCEPTED' });
    if (q.validUntil <= new Date()) throw new AppError('CONFLICT', 'Quote has expired', 409, { code: 'QUOTE_EXPIRED' });
    if (q.recycler.authorizationStatus !== 'VERIFIED') throw new AppError('CONFLICT', 'Recycler is not verified', 409, { code: 'RECYCLER_NOT_VERIFIED' });
    assertLotTransition(lot.status, 'HANDED_OVER');
    // Handover creation is idempotent for a lot. Returning any existing
    // record also makes retries safe after the database write succeeds but
    // the client loses its response.
    const ref = `HOV-${new Date().toISOString().slice(0, 10).replaceAll('-', '')}-${randomBytes(5).toString('hex').toUpperCase()}`;
    const issuedAt = new Date();
    const expiresAt = new Date(Date.now() + 7 * 86400000);
    const signedQr = createSignedTraceabilityQr({ version: 1, referenceId: ref, lotId: lot.id, recyclerId: q.recyclerId, materialCategory: lot.materialCategory, weight: weightKg, issuedAt: issuedAt.toISOString(), expiresAt: expiresAt.toISOString() }, this.traceabilitySecret);
    try {
      return await this.db.$transaction(async tx => {
      const h = await tx.handover.create({ data: { ...(p.clientHandoverId ? { id: p.clientHandoverId } : {}), lotId: lot.id, quoteId: q.id, collectorId: cid, recyclerId: q.recyclerId, photoReference: lot.photoUrl, materialCategory: lot.materialCategory, materialDescription: lot.materialSubcategory, weight: weightKg, quotedPrice: q.totalQuotedPrice, collectionLocation: { latitude: lot.collectionLatitude, longitude: lot.collectionLongitude, areaName: lot.collectionAreaName }, handoverLocation: p.handoverLocation, timestamp: p.timestamp ? new Date(p.timestamp) : issuedAt, referenceId: ref, qrCodeData: signedQr.qrCodeData, qrVersion: 1, qrSignature: signedQr.qrSignature, expiresAt } });
      await tx.lot.update({ where: { id: lot.id }, data: { status: 'HANDED_OVER' } });
      return h;
      });
    } catch (error: any) {
      // The lot-level unique constraint is the final concurrency guard. A
      // racing request should receive the canonical handover, not a 500.
      if (error?.code === 'P2002') {
        const existing = await this.db.handover.findUnique({ where: { lotId: lot.id } });
        if (existing) return existing;
      }
      throw error;
    }
  }
  async verifyPublic(qrCodeData: string, ip: string) {
    await this.rateLimiter?.check('handover-verification', ip, 'public_verify');
    const payload = verifySignedTraceabilityQr(qrCodeData, this.traceabilitySecret);
    const h = await this.db.handover.findUnique({ where: { referenceId: payload.referenceId }, include: { recycler: true } });
    const valid = Boolean(h && h.qrSignature && h.qrSignature === qrCodeData.split('.')[2] && h.lotId === payload.lotId && h.recyclerId === payload.recyclerId && h.materialCategory === payload.materialCategory && h.weight === payload.weight && h.expiresAt.toISOString() === payload.expiresAt && h.expiresAt > new Date());
    if (!valid || !h) throw new AppError('NOT_FOUND', 'No valid handover record matches this code', 404, { code: 'HANDOVER_NOT_VERIFIED' });
    return {
      valid: true,
      handoverId: h.id,
      referenceId: h.referenceId,
      materialCategory: h.materialCategory,
      declaredWeight: h.weight,
      actualWeight: h.actualWeight,
      timestamp: h.timestamp,
      status: h.status,
      recycler: {
        name: h.recycler.name,
        authorizationStatus: h.recycler.authorizationStatus,
        authorizationAuthority: h.recycler.authorizationAuthority,
        licenseNumber: h.recycler.licenseNumber,
        authorizationValidUntil: h.recycler.authorizationValidUntil
      }
    };
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
  async mark(id: string, cid: string) {
    const h = await this.view(id, cid, 'COLLECTOR');
    if (h.status !== 'GENERATED') throw new AppError('CONFLICT', 'Handover is not actionable', 409, { code: 'HANDOVER_NOT_ACTIONABLE' });
    const claimed = await this.db.handover.updateMany({ where: { id, collectorId: cid, status: 'GENERATED', collectorConfirmedAt: null }, data: { collectorConfirmedAt: new Date() } });
    if (!claimed.count && !h.collectorConfirmedAt) throw new AppError('CONFLICT', 'Handover is no longer actionable', 409, { code: 'HANDOVER_ALREADY_ACTIONED' });
    return this.get(id);
  }
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
  async uploadEvidencePhoto(id: string, cid: string, file?: { buffer: Buffer; mimetype: string }) {
    if (!file) throw new AppError('VALIDATION_ERROR', 'Photo is required', 422, { code: 'PHOTO_REQUIRED' });
    if (!this.storage) throw new AppError('INTERNAL_SERVER_ERROR', 'Photo storage is not configured', 503, { code: 'PHOTO_STORAGE_UNAVAILABLE' });
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.mimetype)) throw new AppError('VALIDATION_ERROR', 'Unsupported image type', 422, { code: 'INVALID_PHOTO' });
    const handover = await this.view(id, cid, 'COLLECTOR');
    if (!['GENERATED', 'CONFIRMED_BY_RECYCLER'].includes(handover.status)) throw new AppError('CONFLICT', 'Handover evidence is locked', 409, { code: 'HANDOVER_EVIDENCE_LOCKED' });
    const key = `handovers/${cid}/${id}-${Date.now()}.jpg`;
    const stored = await this.storage.putImage(file.buffer, key);
    const updated = await this.db.handover.updateMany({ where: { id, collectorId: cid, status: { in: ['GENERATED', 'CONFIRMED_BY_RECYCLER'] } }, data: { actualWeightPhotoReference: stored.key } });
    if (!updated.count) {
      await this.storage.delete(stored.key).catch(() => undefined);
      throw new AppError('CONFLICT', 'Handover changed while uploading photo', 409, { code: 'HANDOVER_EVIDENCE_LOCKED' });
    }
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
  async dispute(cid: string, id: string, p: any) { const h = await this.view(id, cid, 'COLLECTOR'); if (p.clientDisputeId) { const prior = await this.db.dispute.findFirst({ where: { id: p.clientDisputeId, collectorId: cid, handoverId: h.id } }); if (prior) return prior; } return this.db.dispute.create({ data: { ...(p.clientDisputeId ? { id: p.clientDisputeId } : {}), handoverId: h.id, lotId: h.lotId, collectorId: cid, recyclerId: h.recyclerId, type: p.type, reportedBy: cid, description: p.description, claimedValue: p.claimedWeight, evidence: p.evidence } }); }
  async adminDisputes() { return this.db.dispute.findMany({ orderBy: { createdAt: 'desc' } }); }
  async adminDispute(id: string) { const d = await this.db.dispute.findUnique({ where: { id }, include: { handover: true } }); if (!d) throw new AppError('NOT_FOUND', 'Dispute not found', 404, { code: 'DISPUTE_NOT_FOUND' }); return d; }
  async resolve(id: string, admin: string, p: any) { const d = await this.db.dispute.findUnique({ where: { id } }); if (!d) throw new AppError('NOT_FOUND', 'Dispute not found', 404, { code: 'DISPUTE_NOT_FOUND' }); if (d.status === 'RESOLVED') throw new AppError('CONFLICT', 'Dispute already resolved', 409, { code: 'DISPUTE_ALREADY_RESOLVED' }); return this.db.$transaction(async tx => { const out = await tx.dispute.update({ where: { id }, data: { status: 'RESOLVED', resolution: p.resolution, resolutionNotes: p.notes, resolvedBy: admin, resolvedAt: new Date() } }); await tx.handover.update({ where: { id: d.handoverId }, data: { status: 'COMPLETED' } }); const payment = await tx.payment.findUnique({ where: { lotId: d.lotId }, select: { status: true } }); await tx.lot.update({ where: { id: d.lotId }, data: { status: payment && payment.status !== 'DISPUTED' ? 'PAID' : 'HANDED_OVER' } }); return out; }); }
}
