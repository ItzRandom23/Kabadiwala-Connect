import type { Request, Response } from 'express';
import { z } from 'zod';
import { HandoverService } from '../services/handoverService.js';
import { AppError } from '../utils/errors.js';
import type { PrismaClient } from '@prisma/client';
import { emitNotification } from '../services/notificationService.js';

const location = z.object({ type: z.enum(['COLLECTOR_LOCATION', 'RECYCLER_FACILITY', 'THIRD_PARTY']), latitude: z.number().finite().min(-90).max(90).optional(), longitude: z.number().finite().min(-180).max(180).optional(), address: z.string().max(300).optional() });
const recyclerReview = z.object({ actualWeight: z.number().finite().positive().max(500), materialMatch: z.boolean(), scalePhotoReference: z.string().trim().max(500).optional(), notes: z.string().trim().max(1000).optional(), reason: z.string().trim().max(500).optional() }).strict();
const resolution = z.object({ resolution: z.enum(['ACCEPT_COLLECTOR', 'ACCEPT_RECYCLER', 'SPLIT_DIFFERENCE', 'OTHER']), notes: z.string().trim().max(1000).optional() }).strict();
export const handoverController = (s: HandoverService, db?: PrismaClient) => ({
  verifyPublic: async (q: Request, r: Response) => { const p = z.object({ qrCodeData: z.string().trim().min(40).max(4000) }).safeParse(q.body); if (!p.success) throw new AppError('VALIDATION_ERROR', 'A handover verification code is required', 422, { code: 'INVALID_HANDOVER_QR' }); return r.json({ success: true, data: await s.verifyPublic(p.data.qrCodeData, q.ip ?? 'unknown'), message: 'Handover verification complete' }); },
  create: async (q: Request, r: Response) => { const p = z.object({ lotId: z.string(), quoteId: z.string(), clientHandoverId: z.string().trim().min(16).max(120).optional(), handoverLocation: location, timestamp: z.string().optional() }).safeParse(q.body); if (!p.success) throw new AppError('VALIDATION_ERROR', 'Invalid handover', 422, { code: 'INVALID_HANDOVER_LOCATION' }); const data = await s.create(q.identity!.collectorId, p.data); if (db) await emitNotification(db, { accountId: q.identity!.collectorId, type: 'HANDOVER_CREATED', title: 'Handover ready', body: 'Your signed handover record is ready for verification.', route: `handovers/document/${data.id}` }); return r.status(201).json({ success: true, data, message: 'Handover generated' }); },
  get: async (q: Request, r: Response) => r.json({ success: true, data: await s.view(String(q.params.handoverId), q.identity!.collectorId, 'COLLECTOR'), message: 'Handover retrieved' }),
  getReference: async (q: Request, r: Response) => r.json({ success: true, data: await s.byReference(String(q.params.referenceId), q.identity!.collectorId), message: 'Handover retrieved' }),
  mark: async (q: Request, r: Response) => { const data = await s.mark(String(q.params.handoverId), q.identity!.collectorId); if (db) await emitNotification(db, { accountId: data.recyclerId, type: 'HANDOVER_COLLECTOR_CONFIRMED', title: 'Collector confirmed handover', body: 'Review the handover evidence and confirm receipt.', route: 'recycler/orders' }); return r.json({ success: true, data, message: 'Handover marked' }); },
  evidence: async (q: Request, r: Response) => { const p = z.object({ actualWeight: z.number().finite().positive().max(500), materialMatch: z.boolean(), scalePhotoReference: z.string().trim().max(500).optional(), collectorConfirmed: z.boolean().optional() }).safeParse(q.body); if (!p.success) throw new AppError('VALIDATION_ERROR', 'Invalid handover evidence', 422, { code: 'INVALID_HANDOVER_EVIDENCE' }); return r.json({ success: true, data: await s.updateEvidence(String(q.params.handoverId), q.identity!.collectorId, p.data), message: 'Handover evidence saved' }); },
  evidencePhoto: async (q: Request, r: Response) => r.json({ success: true, data: await s.uploadEvidencePhoto(String(q.params.handoverId), q.identity!.collectorId, (q as any).file), message: 'Handover evidence photo uploaded' }),
  recyclerList: async (q: Request, r: Response) => r.json({ success: true, data: await s.recyclerList(q.identity!.collectorId), message: 'Handovers retrieved' }),
  recyclerGet: async (q: Request, r: Response) => r.json({ success: true, data: await s.view(String(q.params.handoverId), q.identity!.collectorId, 'RECYCLER'), message: 'Handover retrieved' }),
  confirm: async (q: Request, r: Response) => { const p = recyclerReview.safeParse(q.body); if (!p.success) throw new AppError('VALIDATION_ERROR', 'Enter a valid weight and material review', 422, { code: 'INVALID_HANDOVER_REVIEW' }); const data = await s.confirm(String(q.params.handoverId), q.identity!.collectorId, p.data); if (db && data.handover) await emitNotification(db, { accountId: data.handover.collectorId, type: data.dispute ? 'HANDOVER_DISPUTED' : 'HANDOVER_CONFIRMED', title: data.dispute ? 'Handover needs review' : 'Handover confirmed', body: data.dispute ? 'A discrepancy was recorded and is ready for review.' : 'The recycler confirmed the handover evidence.', route: data.dispute ? `handovers/dispute/${data.handover.id}` : `handovers/document/${data.handover.id}` }); return r.json({ success: true, data, message: 'Handover reviewed' }); },
  reject: async (q: Request, r: Response) => { const p = z.object({ reason: z.string().trim().min(1).max(500) }).safeParse(q.body); if (!p.success) throw new AppError('VALIDATION_ERROR', 'A rejection reason is required', 422, { code: 'INVALID_REJECTION_REASON' }); const data = await s.reject(String(q.params.handoverId), q.identity!.collectorId, p.data.reason); if (db && data.handover) await emitNotification(db, { accountId: data.handover.collectorId, type: 'HANDOVER_DISPUTED', title: 'Handover needs review', body: 'The recycler recorded a handover issue. Review the evidence and dispute timeline.', route: `handovers/dispute/${data.handover.id}` }); return r.json({ success: true, data, message: 'Handover rejected' }); },
  dispute: async (q: Request, r: Response) => {
    const p = z.object({
      clientDisputeId: z.string().trim().min(16).max(120).optional(),
      type: z.enum(['WEIGHT_DISCREPANCY', 'MATERIAL_MISMATCH', 'PRICE_DISAGREEMENT', 'OTHER']),
      description: z.string().trim().min(10).max(500),
      claimedWeight: z.number().finite().positive().max(500).optional(),
      actualValue: z.number().finite().positive().max(500).optional(),
      evidence: z.record(z.string(), z.unknown()).optional()
    }).safeParse(q.body);
    if (!p.success) throw new AppError('VALIDATION_ERROR', 'A clear problem description is required', 422, { code: 'INVALID_DISPUTE' });
    const data = await s.dispute(q.identity!.collectorId, String(q.params.handoverId), p.data);
    if (db) await emitNotification(db, { accountId: q.identity!.collectorId, type: 'HANDOVER_DISPUTED', title: 'Dispute opened', body: 'Your handover evidence is queued for review.', route: `disputes/analytics` });
    return r.status(201).json({ success: true, data, message: 'Dispute created' });
  },
  resolve: async (q: Request, r: Response) => { const p = resolution.safeParse(q.body); if (!p.success) throw new AppError('VALIDATION_ERROR', 'A resolution decision is required', 422, { code: 'INVALID_DISPUTE_RESOLUTION' }); return r.json({ success: true, data: await s.resolve(String(q.params.disputeId), q.identity!.collectorId, p.data), message: 'Dispute resolved' }); },
  adminList: async (_q: Request, r: Response) => r.json({ success: true, data: await s.adminDisputes(), message: 'Disputes retrieved' }),
  adminDetail: async (q: Request, r: Response) => r.json({ success: true, data: await s.adminDispute(String(q.params.disputeId)), message: 'Dispute retrieved' })
});
