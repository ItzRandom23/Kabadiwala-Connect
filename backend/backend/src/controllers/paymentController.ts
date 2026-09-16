import type { Request, Response } from 'express';
import { z } from 'zod';
import { PaymentService } from '../services/paymentService.js';
import { AppError } from '../utils/errors.js';
import type { PrismaClient } from '@prisma/client';
import { emitNotification } from '../services/notificationService.js';

const rec = z.object({ lotId: z.string().trim().min(1).max(120), amount: z.number().finite(), method: z.enum(['CASH', 'BANK_TRANSFER', 'DIGITAL_WALLET']), date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/), time: z.string().regex(/^(?:[01]\d|2[0-3]):[0-5]\d$/).optional(), notes: z.string().max(1000).optional() });
const edit = z.object({ amount: z.number().finite(), method: z.enum(['CASH', 'BANK_TRANSFER', 'DIGITAL_WALLET']), notes: z.string().max(1000).optional() }).strict();
const dispute = z.object({ reason: z.enum(['PAYMENT_AMOUNT_DIFFERENCE', 'PAYMENT_NOT_RECEIVED', 'WRONG_PAYMENT_METHOD', 'OTHER']), description: z.string().trim().min(10).max(1000) }).strict();
export const paymentController = (s: PaymentService, db?: PrismaClient) => ({
  record: async (q: Request, r: Response) => { const p = rec.safeParse(q.body); if (!p.success) throw new AppError('VALIDATION_ERROR', 'Invalid payment', 422, { code: 'PAYMENT_AMOUNT_INVALID' }); const data = await s.record(q.identity!.collectorId, p.data); if (db) await emitNotification(db, { accountId: q.identity!.collectorId, type: 'PAYMENT_RECORDED', title: 'Payment recorded', body: 'Your payment is saved and will appear in the verified earnings ledger after sync.', route: 'earnings' }); return r.status(201).json({ success: true, data, message: 'Payment recorded' }); },
  list: async (q: Request, r: Response) => r.json({ success: true, data: await s.list(q.identity!.collectorId), message: 'Payments retrieved' }),
  get: async (q: Request, r: Response) => r.json({ success: true, data: await s.get(String(q.params.paymentId), q.identity!.collectorId), message: 'Payment retrieved' }),
  adminGet: async (q: Request, r: Response) => r.json({ success: true, data: await s.adminGet(String(q.params.paymentId)), message: 'Payment retrieved' }),
  edit: async (q: Request, r: Response) => { const p = edit.safeParse(q.body); if (!p.success) throw new AppError('VALIDATION_ERROR', 'Invalid payment correction', 422, { code: 'PAYMENT_AMOUNT_INVALID' }); const data = await s.edit(String(q.params.paymentId), q.identity!.collectorId, p.data); if (db) await emitNotification(db, { accountId: q.identity!.collectorId, type: 'PAYMENT_UPDATED', title: 'Payment updated', body: 'Your payment correction is saved and visible in your earnings history.', route: 'earnings' }); return r.json({ success: true, data, message: 'Payment updated' }); },
  dispute: async (q: Request, r: Response) => { const p = dispute.safeParse(q.body); if (!p.success) throw new AppError('VALIDATION_ERROR', 'Describe the payment problem before submitting', 422, { code: 'INVALID_PAYMENT_DISPUTE' }); const data = await s.dispute(String(q.params.paymentId), q.identity!.collectorId, p.data); if (db) await emitNotification(db, { accountId: q.identity!.collectorId, type: 'PAYMENT_DISPUTED', title: 'Payment dispute opened', body: 'Your payment issue is recorded for review.', route: 'disputes/analytics' }); return r.status(201).json({ success: true, data, message: 'Payment dispute created' }); },
  ledger: async (q: Request, r: Response) => r.json({ success: true, data: await s.ledger(q.identity!.collectorId), message: 'Earnings ledger retrieved' }),
  adminList: async (_q: Request, r: Response) => r.json({ success: true, data: await s.adminList(), message: 'Payments retrieved' }),
  verify: async (q: Request, r: Response) => { const data = await s.verify(String(q.params.paymentId), q.identity!.collectorId); if (db && data.collectorId) await emitNotification(db, { accountId: data.collectorId, type: 'PAYMENT_VERIFIED', title: 'Payment verified', body: 'An operator verified your payment record.', route: 'earnings' }); return r.json({ success: true, data, message: 'Payment verified' }); }
});
