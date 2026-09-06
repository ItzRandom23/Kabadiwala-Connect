import type { Request, Response } from 'express';
import { z } from 'zod';
import { PaymentService } from '../services/paymentService.js';
import { AppError } from '../utils/errors.js';

const rec = z.object({ lotId: z.string(), amount: z.number().finite(), method: z.enum(['CASH', 'BANK_TRANSFER', 'DIGITAL_WALLET']), date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/), time: z.string().regex(/^\d{2}:\d{2}$/).optional(), notes: z.string().max(1000).optional() });
const edit = z.object({ amount: z.number().finite(), method: z.enum(['CASH', 'BANK_TRANSFER', 'DIGITAL_WALLET']), notes: z.string().max(1000).optional() }).strict();
export const paymentController = (s: PaymentService) => ({
  record: async (q: Request, r: Response) => { const p = rec.safeParse(q.body); if (!p.success) throw new AppError('VALIDATION_ERROR', 'Invalid payment', 422, { code: 'PAYMENT_AMOUNT_INVALID' }); r.status(201).json({ success: true, data: await s.record(q.identity!.collectorId, p.data), message: 'Payment recorded' }); },
  list: async (q: Request, r: Response) => r.json({ success: true, data: await s.list(q.identity!.collectorId), message: 'Payments retrieved' }),
  get: async (q: Request, r: Response) => r.json({ success: true, data: await s.get(String(q.params.paymentId), q.identity!.collectorId), message: 'Payment retrieved' }),
  adminGet: async (q: Request, r: Response) => r.json({ success: true, data: await s.adminGet(String(q.params.paymentId)), message: 'Payment retrieved' }),
  edit: async (q: Request, r: Response) => { const p = edit.safeParse(q.body); if (!p.success) throw new AppError('VALIDATION_ERROR', 'Invalid payment correction', 422, { code: 'PAYMENT_AMOUNT_INVALID' }); return r.json({ success: true, data: await s.edit(String(q.params.paymentId), q.identity!.collectorId, p.data), message: 'Payment updated' }); },
  dispute: async (q: Request, r: Response) => r.status(201).json({ success: true, data: await s.dispute(String(q.params.paymentId), q.identity!.collectorId, q.body), message: 'Payment dispute created' }),
  ledger: async (q: Request, r: Response) => r.json({ success: true, data: await s.ledger(q.identity!.collectorId), message: 'Earnings ledger retrieved' }),
  adminList: async (_q: Request, r: Response) => r.json({ success: true, data: await s.adminList(), message: 'Payments retrieved' }),
  verify: async (q: Request, r: Response) => r.json({ success: true, data: await s.verify(String(q.params.paymentId), q.identity!.collectorId), message: 'Payment verified' })
});
