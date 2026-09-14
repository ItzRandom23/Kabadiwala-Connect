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
    if (typeof p.id === 'string' && p.id.trim()) {
      const existing = await this.db.payment.findFirst({ where: { id: p.id.trim(), collectorId: cid } });
      if (existing) return existing;
    }
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
      const x = await tx.payment.create({ data: { ...(typeof p.id === 'string' && p.id.trim() ? { id: p.id.trim() } : {}), lotId: lot.id, collectorId: cid, amount: Number(p.amount.toFixed(2)), paymentMethod: p.method, recordedAt: when, notes: p.notes, anomaly, anomalyReason: anomaly ? 'Payment differs materially from accepted quote' : null } });
      // A recorded payment completes the physical handover. Payment keeps
      // its own RECORDED/VERIFIED state for reconciliation, while the
      // handover becomes eligible for rewards and verified reviews.
      const handover = await tx.handover.findFirst({ where: { lotId: lot.id, status: { in: ['CONFIRMED_BY_RECYCLER', 'COMPLETED'] } }, select: { id: true, status: true } });
      if (!handover) throw new AppError('CONFLICT', 'Confirmed handover required', 409, { code: 'HANDOVER_NOT_CONFIRMED' });
      if (handover.status === 'CONFIRMED_BY_RECYCLER') await tx.handover.update({ where: { id: handover.id }, data: { status: 'COMPLETED' } });
      await tx.paymentAudit.create({ data: { paymentId: x.id, actorId: cid, actorRole: 'COLLECTOR', event: 'PAYMENT_RECORDED', newValues: { amount: x.amount, method: x.paymentMethod } } });
      return x;
    });
  }
  async list(cid: string) { return this.db.payment.findMany({ where: { collectorId: cid }, orderBy: { recordedAt: 'desc' } }); }
  async get(id: string, cid: string) { return this.owned(id, cid); }
  async edit(id: string, cid: string, p: any) {
    const old = await this.owned(id, cid);
    if (old.status === 'VERIFIED' || old.status === 'DISPUTED' || Date.now() - old.createdAt.getTime() > 86400000) throw new AppError('CONFLICT', 'Payment correction window expired', 409, { code: 'PAYMENT_EDIT_WINDOW_EXPIRED' });
    if (old.lot.handovers.some(h => ['DISPUTED', 'PENDING_MANUAL_REVIEW'].includes(h.status))) throw new AppError('CONFLICT', 'Payment is locked while the handover is under review', 409, { code: 'PAYMENT_HANDOVER_UNDER_REVIEW' });
    if (!Number.isFinite(p.amount) || p.amount <= 0 || p.amount >= 1000000) throw new AppError('VALIDATION_ERROR', 'Invalid payment amount', 422, { code: 'PAYMENT_AMOUNT_INVALID' });
    if (!['CASH', 'BANK_TRANSFER', 'DIGITAL_WALLET'].includes(p.method)) throw new AppError('VALIDATION_ERROR', 'Invalid payment method', 422, { code: 'PAYMENT_METHOD_INVALID' });
    const acceptedQuote = old.lot.quotes.find(q => q.status === 'ACCEPTED');
    if (acceptedQuote && (p.amount > acceptedQuote.totalQuotedPrice * 1.5 || p.amount < acceptedQuote.totalQuotedPrice * 0.5)) throw new AppError('CONFLICT', 'This correction is outside the accepted quote range; raise a dispute for review', 409, { code: 'PAYMENT_AMOUNT_OUTSIDE_QUOTE' });
    return this.db.$transaction(async tx => { const x = await tx.payment.update({ where: { id }, data: { amount: Number(p.amount.toFixed(2)), paymentMethod: p.method, notes: p.notes, anomaly: false, anomalyReason: null } }); await tx.paymentAudit.create({ data: { paymentId: id, actorId: cid, actorRole: 'COLLECTOR', event: 'PAYMENT_EDITED', oldValues: { amount: old.amount, paymentMethod: old.paymentMethod }, newValues: { amount: x.amount, paymentMethod: x.paymentMethod } } }); return x; });
  }
  async dispute(id: string, cid: string, p: any) {
    const pay = await this.owned(id, cid);
    return this.db.$transaction(async tx => {
      const claimed = await tx.payment.updateMany({ where: { id: pay.id, collectorId: cid, status: { not: 'DISPUTED' } }, data: { status: 'DISPUTED' } });
      if (!claimed.count) throw new AppError('CONFLICT', 'Payment dispute already exists', 409, { code: 'PAYMENT_DISPUTE_EXISTS' });
      return tx.paymentDispute.create({ data: { paymentId: id, reportedBy: cid, type: p.reason, description: p.description } });
    });
  }
  async ledger(cid: string) {
    const payments = await this.list(cid);
    const settled = payments.filter(p => p.status !== 'DISPUTED');
    const total = settled.reduce((s, p) => s + p.amount, 0);
    // Ledger months are business months in India, independent of the host
    // machine's timezone (which is commonly UTC in production).
    const indiaParts = new Intl.DateTimeFormat('en-US', { timeZone: 'Asia/Kolkata', year: 'numeric', month: 'numeric' }).formatToParts(new Date());
    const year = Number(indiaParts.find(part => part.type === 'year')?.value);
    const month = Number(indiaParts.find(part => part.type === 'month')?.value) - 1;
    const offsetMs = 5.5 * 60 * 60 * 1000;
    const monthStart = new Date(Date.UTC(year, month, 1) - offsetMs);
    const nextMonthStart = new Date(Date.UTC(year, month + 1, 1) - offsetMs);
    const thisMonth = settled.filter(p => p.recordedAt >= monthStart && p.recordedAt < nextMonthStart).reduce((s, p) => s + p.amount, 0);
    const summary = {
      totalEarnings: Number(total.toFixed(2)),
      pendingAmount: 0,
      thisMonthEarnings: Number(thisMonth.toFixed(2)),
      averageLotValue: settled.length ? Number((total / settled.length).toFixed(2)) : 0
    };
    // Keep the historical summary/transactions shape for existing clients,
    // while exposing the flat contract consumed by the Android ledger cache.
    return {
      total: summary.totalEarnings,
      pending: summary.pendingAmount,
      currentMonth: summary.thisMonthEarnings,
      averagePerLot: summary.averageLotValue,
      payments,
      summary,
      transactions: payments
    };
  }
  async adminList() { return this.db.payment.findMany({ orderBy: { createdAt: 'desc' } }); }
  async verify(id: string, admin: string) { const p = await this.db.payment.findUnique({ where: { id } }); if (!p) throw new AppError('NOT_FOUND', 'Payment not found', 404, { code: 'PAYMENT_NOT_FOUND' }); if (p.status !== 'RECORDED') throw new AppError('CONFLICT', 'Only an undisputed recorded payment can be verified', 409, { code: 'PAYMENT_NOT_VERIFIABLE' }); return this.db.$transaction(async tx => { const claimed = await tx.payment.updateMany({ where: { id, status: 'RECORDED' }, data: { status: 'VERIFIED', confirmedAt: new Date() } }); if (!claimed.count) throw new AppError('CONFLICT', 'Payment changed while being verified', 409, { code: 'PAYMENT_NOT_VERIFIABLE' }); const x = await tx.payment.findUniqueOrThrow({ where: { id } }); await tx.paymentAudit.create({ data: { paymentId: id, actorId: admin, actorRole: 'ADMIN', event: 'PAYMENT_VERIFIED' } }); return x; }); }
}
