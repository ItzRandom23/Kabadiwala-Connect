import type { Request, Response } from 'express';
import type { PrismaClient } from '@prisma/client';
import { z } from 'zod';
import { QuoteService } from '../services/quoteService.js';
import { emitNotification } from '../services/notificationService.js';
import { AppError } from '../utils/errors.js';

const requestSchema = z.object({ lotId: z.string().min(1), recyclerId: z.string().min(1) });
const requestBatchSchema = z.object({ lotId: z.string().min(1), recyclerIds: z.array(z.string().min(1)).min(1).max(10) });
const submitSchema = z.object({ quoteRequestId: z.string().min(1), pricePerKg: z.number().finite(), validUntil: z.string().optional(), recyclerNotes: z.string().max(1000).optional() });

export const quoteController = (s: QuoteService, db?: PrismaClient) => ({
  request: async (q: Request, r: Response) => {
    const p = requestSchema.safeParse(q.body);
    if (!p.success) throw new AppError('VALIDATION_ERROR', 'Invalid quote request', 400, { code: 'INVALID_QUOTE_REQUEST' });
    const data = await s.requestQuote(q.identity!.collectorId, p.data.lotId, p.data.recyclerId);
    if (db) await emitNotification(db, { accountId: p.data.recyclerId, type: 'QUOTE_REQUESTED', title: 'New quote request', body: 'A collector requested a quote for a verified material lot.', route: 'recycler/marketplace', dedupeKey: `QUOTE_REQUESTED:${data.id}` });
    return r.status(201).json({ success: true, data, message: 'Quote requested' });
  },
  requestBatch: async (q: Request, r: Response) => {
    const p = requestBatchSchema.safeParse(q.body);
    if (!p.success) throw new AppError('VALIDATION_ERROR', 'Select at least one eligible recycler', 400, { code: 'INVALID_QUOTE_BATCH' });
    const data = await s.requestQuoteBatch(q.identity!.collectorId, p.data.lotId, p.data.recyclerIds);
    if (db) await Promise.all(data.requested.map((request: any) => emitNotification(db, { accountId: request.recyclerId, type: 'QUOTE_REQUESTED', title: 'New quote request', body: 'A collector requested a quote for a verified material lot.', route: 'recycler/marketplace', dedupeKey: `QUOTE_REQUESTED:${request.id}` })));
    return r.status(201).json({ success: true, data, message: 'Quote requests sent' });
  },
  pending: async (q: Request, r: Response) => r.json({ success: true, data: await s.pending(q.identity!.collectorId, typeof q.query.lotId === 'string' ? q.query.lotId : undefined), message: 'Pending quotes retrieved' }),
  detail: async (q: Request, r: Response) => r.json({ success: true, data: await s.detail(String(q.params.quoteId), q.identity!.collectorId, 'COLLECTOR'), message: 'Quote retrieved' }),
  accept: async (q: Request, r: Response) => {
    const data = await s.action(String(q.params.quoteId), q.identity!.collectorId, true);
    // The accepted quote notification is delivered to the recycler. The
    // collector owns handover creation, so routing this event to a collector
    // handover screen leaves the recycler at a dead/unauthorized destination.
    // Keep the event actionable for the recipient's marketplace/order graph.
    if (db) await emitNotification(db, { accountId: data.recyclerId, type: 'QUOTE_ACCEPTED', title: 'Quote accepted', body: 'Your quote was accepted. Review the order and prepare the handover.', route: 'recycler/orders', dedupeKey: `QUOTE_ACCEPTED:${data.id}` });
    return r.json({ success: true, data, message: 'Quote accepted' });
  },
  reject: async (q: Request, r: Response) => {
    const data = await s.action(String(q.params.quoteId), q.identity!.collectorId, false);
    if (db) await emitNotification(db, { accountId: data.recyclerId, type: 'QUOTE_REJECTED', title: 'Quote declined', body: 'The collector declined your quote. Review the marketplace for another request.', route: 'recycler/marketplace', dedupeKey: `QUOTE_REJECTED:${data.id}` });
    return r.json({ success: true, data, message: 'Quote rejected' });
  },
  recyclerRequests: async (q: Request, r: Response) => r.json({ success: true, data: await s.recyclerRequests(q.identity!.collectorId), message: 'Quote requests retrieved' }),
  recyclerDetail: async (q: Request, r: Response) => r.json({ success: true, data: await s.detail(String(q.params.requestId), q.identity!.collectorId, 'RECYCLER'), message: 'Quote request retrieved' }),
  submit: async (q: Request, r: Response) => {
    const p = submitSchema.safeParse(q.body);
    if (!p.success) throw new AppError('VALIDATION_ERROR', 'Invalid quote', 422, { code: 'INVALID_QUOTE' });
    const data = await s.submit(q.identity!.collectorId, p.data);
    const request = db ? await db.quoteRequest.findUnique({ where: { id: p.data.quoteRequestId }, select: { collectorId: true } }) : null;
    if (db && request) await emitNotification(db, { accountId: request.collectorId, type: 'QUOTE_RECEIVED', title: 'New quote received', body: 'A verified recycler sent a quote for your lot.', route: `quotes/compare/${data.lotId}`, dedupeKey: `QUOTE_RECEIVED:${data.id}` });
    return r.status(201).json({ success: true, data, message: 'Quote submitted' });
  }
});
