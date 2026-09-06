import type { Request, Response } from 'express';
import { z } from 'zod';
import { HandoverService } from '../services/handoverService.js';
import { AppError } from '../utils/errors.js';

const location = z.object({ type: z.enum(['COLLECTOR_LOCATION', 'RECYCLER_FACILITY', 'THIRD_PARTY']), latitude: z.number().finite().min(-90).max(90).optional(), longitude: z.number().finite().min(-180).max(180).optional(), address: z.string().max(300).optional() });
export const handoverController = (s: HandoverService) => ({
  create: async (q: Request, r: Response) => { const p = z.object({ lotId: z.string(), quoteId: z.string(), handoverLocation: location, timestamp: z.string().optional() }).safeParse(q.body); if (!p.success) throw new AppError('VALIDATION_ERROR', 'Invalid handover', 422, { code: 'INVALID_HANDOVER_LOCATION' }); r.status(201).json({ success: true, data: await s.create(q.identity!.collectorId, p.data), message: 'Handover generated' }); },
  get: async (q: Request, r: Response) => r.json({ success: true, data: await s.view(String(q.params.handoverId), q.identity!.collectorId, 'COLLECTOR'), message: 'Handover retrieved' }),
  getReference: async (q: Request, r: Response) => r.json({ success: true, data: await s.byReference(String(q.params.referenceId), q.identity!.collectorId), message: 'Handover retrieved' }),
  mark: async (q: Request, r: Response) => r.json({ success: true, data: await s.mark(String(q.params.handoverId), q.identity!.collectorId), message: 'Handover marked' }),
  evidence: async (q: Request, r: Response) => { const p = z.object({ actualWeight: z.number().finite().positive().max(500), materialMatch: z.boolean(), scalePhotoReference: z.string().trim().max(500).optional(), collectorConfirmed: z.boolean().optional() }).safeParse(q.body); if (!p.success) throw new AppError('VALIDATION_ERROR', 'Invalid handover evidence', 422, { code: 'INVALID_HANDOVER_EVIDENCE' }); return r.json({ success: true, data: await s.updateEvidence(String(q.params.handoverId), q.identity!.collectorId, p.data), message: 'Handover evidence saved' }); },
  recyclerList: async (q: Request, r: Response) => r.json({ success: true, data: await s.recyclerList(q.identity!.collectorId), message: 'Handovers retrieved' }),
  recyclerGet: async (q: Request, r: Response) => r.json({ success: true, data: await s.view(String(q.params.handoverId), q.identity!.collectorId, 'RECYCLER'), message: 'Handover retrieved' }),
  confirm: async (q: Request, r: Response) => r.json({ success: true, data: await s.confirm(String(q.params.handoverId), q.identity!.collectorId, q.body), message: 'Handover reviewed' }),
  reject: async (q: Request, r: Response) => { const p = z.object({ reason: z.string().trim().min(1).max(500) }).safeParse(q.body); if (!p.success) throw new AppError('VALIDATION_ERROR', 'A rejection reason is required', 422, { code: 'INVALID_REJECTION_REASON' }); return r.json({ success: true, data: await s.reject(String(q.params.handoverId), q.identity!.collectorId, p.data.reason), message: 'Handover rejected' }); },
  dispute: async (q: Request, r: Response) => {
    const p = z.object({
      type: z.enum(['WEIGHT_DISCREPANCY', 'MATERIAL_MISMATCH', 'PRICE_DISAGREEMENT', 'OTHER']),
      description: z.string().trim().min(10).max(500),
      claimedWeight: z.number().finite().positive().max(500).optional(),
      actualValue: z.number().finite().positive().max(500).optional(),
      evidence: z.record(z.string(), z.unknown()).optional()
    }).safeParse(q.body);
    if (!p.success) throw new AppError('VALIDATION_ERROR', 'A clear problem description is required', 422, { code: 'INVALID_DISPUTE' });
    return r.status(201).json({ success: true, data: await s.dispute(q.identity!.collectorId, String(q.params.handoverId), p.data), message: 'Dispute created' });
  },
  resolve: async (q: Request, r: Response) => r.json({ success: true, data: await s.resolve(String(q.params.disputeId), q.identity!.collectorId, q.body), message: 'Dispute resolved' }),
  adminList: async (_q: Request, r: Response) => r.json({ success: true, data: await s.adminDisputes(), message: 'Disputes retrieved' }),
  adminDetail: async (q: Request, r: Response) => r.json({ success: true, data: await s.adminDispute(String(q.params.disputeId)), message: 'Dispute retrieved' })
});
