import type { PrismaClient } from '@prisma/client';
import { AppError } from '../utils/errors.js';
import { assertLotTransition } from './lotStateMachine.js';

export class PaymentService {
  constructor(private db: PrismaClient) {}
  private async owned(id: string, cid: string) {
    const p = await this.db.payment.findUnique({ where: { id }, include: { lot: { include: { handovers: true, quotes: true } } } });
    if (!p || p.collectorId !== cid) throw new AppError('NOT_FOUND', 'Payment not found', 404, { code: 'PAYMENT_NOT_FOUND' });
    return p;
  }
  async adminGet(id: string) {
    const p = await this.db.payment.findUnique({ where: { id }, include: { lot: { include: { handovers: true, quotes: true } }, collector: true } });
    if (!p) throw new AppError('NOT_FOUND', 'Payment not found', 404, { code: 'PAYMENT_NOT_FOUND' });
    return p;
  }
  async record(cid: string, p: any) {
    const lot = await this.db.lot.findFirst({ where: { id: p.lotId, collectorId: cid }, include: { handovers: true, quotes: true } });
    if (!lot) throw new AppError('NOT_FOUND', 'Lot not found', 404, { code: 'LOT_NOT_FOUND' });
    if (!['HANDED_OVER', 'PAID'].includes(lot.status) || !lot.handovers.some(h => ['CONFIRMED_BY_RECYCLER', 'COMPLETED'].includes(h.status))) throw new AppError('CONFLICT', 'Confirmed handover required', 409, { code: 'HANDOVER_NOT_CONFIRMED' });
    if (lot.status === 'PAID' || await this.db.payment.findFirst({ where: { lotId: lot.id } })) throw new AppError('CONFLICT', 'Payment already exists', 409, { code: 'PAYMENT_ALREADY_EXISTS' });
    const when = new Date(p.date + 'T' + (p.time || '00:00') + ':00+05:30');
    if (Number.isNaN(when.getTime()) || when > new Date()) throw new AppError('VALIDATION_ERROR', 'Invalid payment date', 422, { code: 'PAYMENT_DATE_INVALID' });
    if (!Number.isFinite(p.amount) || p.amount <= 0 || p.amount >= 1000000) throw new AppError('VALIDATION_ERROR', 'Invalid payment amount', 422, { code: 'PAYMENT_AMOUNT_INVALID' });
    const quote = lot.quotes.find(q => q.status === 'ACCEPTED');
    const anomaly = !!quote && (p.amount > quote.totalQuotedPrice * 1.5 || p.amount < quote.totalQuotedPrice * .5);
    assertLotTransition(lot.status, 'PAID');
    return this.db.$transaction(async tx => {
      const claimed = await tx.lot.updateMany({ where: { id: lot.id, status: 'HANDED_OVER' }, data: { status: 'PAID' } });
      if (!claimed.count) throw new AppError('CONFLICT', 'Payment already exists', 409, { code: 'PAYMENT_ALREADY_EXISTS' });
      const x = await tx.payment.create({ data: { lotId: lot.id, collectorId: cid, amount: Number(p.amount.toFixed(2)), paymentMethod: p.method, recordedAt: when, notes: p.notes, anomaly, anomalyReason: anomaly ? 'Payment differs materially from accepted quote' : null } });
      await tx.paymentAudit.create({ data: { paymentId: x.id, actorId: cid, actorRole: 'COLLECTOR', event: 'PAYMENT_RECORDED', newValues: { amount: x.amount, method: x.paymentMethod } } });
      return x;
    });
  }
  async list(cid: string) { return this.db.payment.findMany({ where: { collectorId: cid }, orderBy: { recordedAt: 'desc' } }); }
  async get(id: string, cid: string) { return this.owned(id, cid); }
  async edit(id: string, cid: string, p: any) {
    const old = await this.owned(id, cid);
    if (old.status === 'VERIFIED' || Date.now() - old.createdAt.getTime() > 86400000) throw new AppError('CONFLICT', 'Payment correction window expired', 409, { code: 'PAYMENT_EDIT_WINDOW_EXPIRED' });
    if (!Number.isFinite(p.amount) || p.amount <= 0 || p.amount >= 1000000) throw new AppError('VALIDATION_ERROR', 'Invalid payment amount', 422, { code: 'PAYMENT_AMOUNT_INVALID' });
    if (!['CASH', 'BANK_TRANSFER', 'DIGITAL_WALLET'].includes(p.method)) throw new AppError('VALIDATION_ERROR', 'Invalid payment method', 422, { code: 'PAYMENT_METHOD_INVALID' });
    return this.db.$transaction(async tx => { const x = await tx.payment.update({ where: { id }, data: { amount: Number(p.amount.toFixed(2)), paymentMethod: p.method, notes: p.notes } }); await tx.paymentAudit.create({ data: { paymentId: id, actorId: cid, actorRole: 'COLLECTOR', event: 'PAYMENT_EDITED', oldValues: { amount: old.amount, paymentMethod: old.paymentMethod }, newValues: { amount: x.amount, paymentMethod: x.paymentMethod } } }); return x; });
  }
  async dispute(id: string, cid: string, p: any) {
    const pay = await this.owned(id, cid);
    return this.db.$transaction(async tx => {
      const claimed = await tx.payment.updateMany({ where: { id: pay.id, collectorId: cid, status: { not: 'DISPUTED' } }, data: { status: 'DISPUTED' } });
      if (!claimed.count) throw new AppError('CONFLICT', 'Payment dispute already exists', 409, { code: 'PAYMENT_DISPUTE_EXISTS' });
      return tx.paymentDispute.create({ data: { paymentId: id, reportedBy: cid, type: p.reason, description: p.description } });
    });
  }
  async ledger(cid: string) { const payments = await this.list(cid); const settled = payments.filter(p => p.status !== 'DISPUTED'); const total = settled.reduce((s, p) => s + p.amount, 0); const now = new Date(), month = now.getMonth(), year = now.getFullYear(); const thisMonth = settled.filter(p => p.recordedAt.getMonth() === month && p.recordedAt.getFullYear() === year).reduce((s, p) => s + p.amount, 0); return { summary: { totalEarnings: Number(total.toFixed(2)), pendingAmount: 0, thisMonthEarnings: Number(thisMonth.toFixed(2)), averageLotValue: settled.length ? Number((total / settled.length).toFixed(2)) : 0 }, transactions: payments }; }
  async adminList() { return this.db.payment.findMany({ orderBy: { createdAt: 'desc' } }); }
  async verify(id: string, admin: string) { const p = await this.db.payment.findUnique({ where: { id } }); if (!p) throw new AppError('NOT_FOUND', 'Payment not found', 404, { code: 'PAYMENT_NOT_FOUND' }); if (p.status === 'VERIFIED') throw new AppError('CONFLICT', 'Payment already verified', 409, { code: 'PAYMENT_ALREADY_VERIFIED' }); return this.db.$transaction(async tx => { const x = await tx.payment.update({ where: { id }, data: { status: 'VERIFIED', confirmedAt: new Date() } }); await tx.paymentAudit.create({ data: { paymentId: id, actorId: admin, actorRole: 'ADMIN', event: 'PAYMENT_VERIFIED' } }); return x; }); }
}
