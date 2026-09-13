import { Router, type Request, type Response } from 'express';
import type { PrismaClient } from '@prisma/client';
import type { JwtService } from '../services/jwt.js';
import type { CollectorRepository } from '../repositories/collectorRepository.js';
import { requireAuth } from '../middleware/auth.js';
import { AppError } from '../utils/errors.js';

/** Participant-facing transaction passport assembled from existing lifecycle records. */
export const transactionRoutes = (jwt: JwtService, collectors: CollectorRepository, db: PrismaClient) => Router()
  .get('/transactions/:lotId/timeline', requireAuth(jwt, collectors), async (req: Request, res: Response) => {
    const lotId = String(req.params.lotId);
    const lot = await db.lot.findFirst({
      where: { id: lotId, collectorId: req.identity!.collectorId },
      include: {
        quotes: { include: { recycler: { select: { id: true, name: true, areaName: true, authorizationStatus: true } } } },
        handovers: { include: { disputes: true } },
        payments: { include: { audits: true, disputes: true } }
      }
    });
    if (!lot) throw new AppError('NOT_FOUND', 'Transaction not found', 404, { code: 'TRANSACTION_NOT_FOUND' });

    const events = [
      { type: 'LOT_CREATED', at: lot.createdAt, status: lot.status, data: { materialCategory: lot.materialCategory, materialSubcategory: lot.materialSubcategory, weight: lot.weight, weightUnit: lot.weightUnit, originalWeight: lot.originalWeight, originalWeightUnit: lot.originalWeightUnit, sourceType: lot.sourceType, wasteRegime: lot.wasteRegime, imageProvenance: lot.imageProvenance, imageQualityStatus: lot.imageQualityStatus, areaName: lot.collectionAreaName } },
      ...lot.quotes.map(quote => ({ type: quote.status === 'ACCEPTED' ? 'QUOTE_ACCEPTED' : `QUOTE_${quote.status}`, at: quote.acceptedAt ?? quote.respondedAt ?? quote.sentAt, status: quote.status, data: { quoteId: quote.id, recyclerId: quote.recyclerId, recyclerName: quote.recycler.name, recyclerArea: quote.recycler.areaName, recyclerVerified: quote.recycler.authorizationStatus === 'VERIFIED', pricePerKg: quote.pricePerKg, totalQuotedPrice: quote.totalQuotedPrice, validUntil: quote.validUntil, anomaly: quote.anomaly, comparison: quote.comparison } })),
      ...lot.handovers.flatMap(handover => [
        { type: 'HANDOVER_CREATED', at: handover.createdAt, status: handover.status, data: { handoverId: handover.id, referenceId: handover.referenceId, qrVersion: handover.qrVersion, expiresAt: handover.expiresAt } },
        ...(handover.recyclerConfirmedAt ? [{ type: 'HANDOVER_CONFIRMED', at: handover.recyclerConfirmedAt, status: handover.status, data: { handoverId: handover.id, actualWeight: handover.actualWeight, materialConfirmed: Boolean(handover.materialConfirmedAt) } }] : []),
        ...(handover.collectorConfirmedAt ? [{ type: 'HANDOVER_SIGNED', at: handover.collectorConfirmedAt, status: handover.status, data: { handoverId: handover.id } }] : []),
        ...handover.disputes.map(dispute => ({ type: `DISPUTE_${dispute.status}`, at: dispute.resolvedAt ?? dispute.createdAt, status: dispute.status, data: { disputeId: dispute.id, type: dispute.type, resolution: dispute.resolution, resolvedAt: dispute.resolvedAt } }))
      ]),
      ...lot.payments.flatMap(payment => [
        { type: 'PAYMENT_RECORDED', at: payment.recordedAt, status: payment.status, data: { paymentId: payment.id, amount: payment.amount, method: payment.paymentMethod, reference: payment.reference, anomaly: payment.anomaly } },
        ...payment.audits.map(audit => ({ type: audit.event, at: audit.createdAt, status: payment.status, data: { paymentId: payment.id } })),
        ...payment.disputes.map(dispute => ({ type: `PAYMENT_DISPUTE_${dispute.status}`, at: dispute.resolvedAt ?? dispute.createdAt, status: dispute.status, data: { paymentId: payment.id, type: dispute.type, resolvedAt: dispute.resolvedAt } }))
      ])
    ].sort((left, right) => new Date(left.at).getTime() - new Date(right.at).getTime());

    return res.json({ success: true, data: { schemaVersion: 1, lotId: lot.id, status: lot.status, finalValue: lot.finalPrice, quotedValue: lot.quotedPrice, events }, message: 'Transaction timeline retrieved' });
  });
