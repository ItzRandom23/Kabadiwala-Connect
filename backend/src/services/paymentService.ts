import type { PrismaClient, Prisma } from '@prisma/client';
import { AppError } from '../utils/errors.js';
import { assertLotTransition } from './lotStateMachine.js';
import { withTransactionRetry } from '../utils/transactionRetry.js';

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
  async record(cid: string, p: any, transaction?: Prisma.TransactionClient) {
    if (typeof p.date !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(p.date) || (p.time !== undefined && (typeof p.time !== 'string' || !/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(p.time)))) throw new AppError('VALIDATION_ERROR', 'Invalid payment date', 422, { code: 'PAYMENT_DATE_INVALID' });
    const when = new Date(p.date + 'T' + (p.time || '00:00') + ':00+05:30');
    if (Number.isNaN(when.getTime()) || when > new Date() || new Date(when.getTime() + 5.5 * 60 * 60 * 1000).toISOString().slice(0, 10) !== p.date) throw new AppError('VALIDATION_ERROR', 'Invalid payment date', 422, { code: 'PAYMENT_DATE_INVALID' });
    if (!Number.isFinite(p.amount) || p.amount <= 0 || p.amount >= 1000000) throw new AppError('VALIDATION_ERROR', 'Invalid payment amount', 422, { code: 'PAYMENT_AMOUNT_INVALID' });
    if (!['CASH', 'BANK_TRANSFER', 'DIGITAL_WALLET'].includes(p.method)) throw new AppError('VALIDATION_ERROR', 'Invalid payment method', 422, { code: 'PAYMENT_METHOD_INVALID' });
    const amount = Number(p.amount.toFixed(2));
    if (amount <= 0) throw new AppError('VALIDATION_ERROR', 'Invalid payment amount', 422, { code: 'PAYMENT_AMOUNT_INVALID' });
    const write = async (tx: Prisma.TransactionClient) => {
      if (typeof p.id === 'string' && p.id.trim()) {
        const existing = await tx.payment.findFirst({ where: { id: p.id.trim(), collectorId: cid } });
        if (existing) {
          if (existing.lotId !== p.lotId || existing.amount !== amount || existing.paymentMethod !== p.method || existing.recordedAt.getTime() !== when.getTime() || (existing.notes ?? null) !== (p.notes ?? null)) throw new AppError('CONFLICT', 'Payment id was already used for different payment details', 409, { code: 'PAYMENT_ID_PAYLOAD_MISMATCH' });
          return existing;
        }
      }
      const lot = await tx.lot.findFirst({ where: { id: p.lotId, collectorId: cid }, include: { handovers: true, quotes: true } });
      if (!lot) throw new AppError('NOT_FOUND', 'Lot not found', 404, { code: 'LOT_NOT_FOUND' });
      if (!['HANDED_OVER', 'PAID'].includes(lot.status) || !lot.handovers.some(h => ['CONFIRMED_BY_RECYCLER', 'COMPLETED'].includes(h.status))) throw new AppError('CONFLICT', 'Confirmed handover required', 409, { code: 'HANDOVER_NOT_CONFIRMED' });
      if (lot.status === 'PAID' || await tx.payment.findFirst({ where: { lotId: lot.id } })) throw new AppError('CONFLICT', 'Payment already exists', 409, { code: 'PAYMENT_ALREADY_EXISTS' });
      const quote = lot.quotes.find(q => q.status === 'ACCEPTED');
      const anomaly = !!quote && (amount > quote.totalQuotedPrice * 1.5 || amount < quote.totalQuotedPrice * .5);
      assertLotTransition(lot.status, 'PAID');
      const claimed = await tx.lot.updateMany({ where: { id: lot.id, collectorId: cid, status: 'HANDED_OVER' }, data: { status: 'PAID' } });
      if (!claimed.count) throw new AppError('CONFLICT', 'Payment already exists', 409, { code: 'PAYMENT_ALREADY_EXISTS' });
      const x = await tx.payment.create({ data: { ...(typeof p.id === 'string' && p.id.trim() ? { id: p.id.trim() } : {}), lotId: lot.id, collectorId: cid, amount, paymentMethod: p.method, recordedAt: when, notes: p.notes, anomaly, anomalyReason: anomaly ? 'Payment differs materially from accepted quote' : null } });
      // A recorded payment completes the physical handover. Payment keeps
      // its own RECORDED/VERIFIED state for reconciliation, while the
      // handover becomes eligible for rewards and verified reviews.
      const handover = await tx.handover.findFirst({ where: { lotId: lot.id, status: { in: ['CONFIRMED_BY_RECYCLER', 'COMPLETED'] } }, select: { id: true, status: true } });
      if (!handover) throw new AppError('CONFLICT', 'Confirmed handover required', 409, { code: 'HANDOVER_NOT_CONFIRMED' });
      if (handover.status === 'CONFIRMED_BY_RECYCLER') await tx.handover.update({ where: { id: handover.id }, data: { status: 'COMPLETED' } });
      await tx.paymentAudit.create({ data: { paymentId: x.id, actorId: cid, actorRole: 'COLLECTOR', event: 'PAYMENT_RECORDED', newValues: { amount: x.amount, method: x.paymentMethod } } });
      return x;
    };
    return transaction ? write(transaction) : withTransactionRetry(() => this.db.$transaction(write));
  }
  async list(cid: string) { return (await this.page(cid)).items; }
  async page(cid: string | null, query: { limit?: unknown; cursor?: unknown; status?: unknown } = {}) {
    const limit = query.limit == null ? 100 : Number(query.limit);
    if (!Number.isInteger(limit) || limit < 1 || limit > 100) throw new AppError('VALIDATION_ERROR', 'Invalid payment page size', 422, { code: 'INVALID_PAYMENT_LIMIT' });
    const cursor = query.cursor == null ? null : String(query.cursor);
    if (cursor && !/^[A-Za-z0-9_-]{1,120}$/.test(cursor)) throw new AppError('VALIDATION_ERROR', 'Invalid payment cursor', 422, { code: 'INVALID_CURSOR' });
    const statuses = ['RECORDED', 'VERIFIED', 'DISPUTED', 'REVERSED'];
    if (query.status != null && !statuses.includes(String(query.status))) throw new AppError('VALIDATION_ERROR', 'Invalid payment status', 422);
    const where = { ...(cid ? { collectorId: cid } : {}), ...(query.status ? { status: String(query.status) as any } : {}) };
    if (cursor && !await this.db.payment.findFirst({ where: { ...where, id: cursor }, select: { id: true } })) throw new AppError('VALIDATION_ERROR', 'Invalid payment cursor', 422, { code: 'INVALID_CURSOR' });
    const rows = await this.db.payment.findMany({ where, orderBy: [{ recordedAt: 'desc' }, { id: 'desc' }], take: limit + 1, ...(cursor ? { cursor: { id: cursor }, skip: 1 } : {}) });
    const items = rows.slice(0, limit);
    return { items, page: { nextCursor: rows.length > limit ? items[items.length - 1].id : null } };
  }
  async get(id: string, cid: string) { return this.owned(id, cid); }
  async edit(id: string, cid: string, p: any) {
    const old = await this.owned(id, cid);
    if (old.status !== 'RECORDED' || Date.now() - old.createdAt.getTime() > 86400000) throw new AppError('CONFLICT', 'Payment correction window expired', 409, { code: 'PAYMENT_EDIT_WINDOW_EXPIRED' });
    if (old.lot.handovers.some(h => ['DISPUTED', 'PENDING_MANUAL_REVIEW'].includes(h.status))) throw new AppError('CONFLICT', 'Payment is locked while the handover is under review', 409, { code: 'PAYMENT_HANDOVER_UNDER_REVIEW' });
    if (!Number.isFinite(p.amount) || p.amount <= 0 || p.amount >= 1000000) throw new AppError('VALIDATION_ERROR', 'Invalid payment amount', 422, { code: 'PAYMENT_AMOUNT_INVALID' });
    if (!['CASH', 'BANK_TRANSFER', 'DIGITAL_WALLET'].includes(p.method)) throw new AppError('VALIDATION_ERROR', 'Invalid payment method', 422, { code: 'PAYMENT_METHOD_INVALID' });
    const acceptedQuote = old.lot.quotes.find(q => q.status === 'ACCEPTED');
    if (acceptedQuote && (p.amount > acceptedQuote.totalQuotedPrice * 1.5 || p.amount < acceptedQuote.totalQuotedPrice * 0.5)) throw new AppError('CONFLICT', 'This correction is outside the accepted quote range; raise a dispute for review', 409, { code: 'PAYMENT_AMOUNT_OUTSIDE_QUOTE' });
    return this.db.$transaction(async tx => {
      const changed = await tx.payment.updateMany({
        where: { id, collectorId: cid, status: 'RECORDED', createdAt: { gte: new Date(Date.now() - 86400000) } },
        data: { amount: Number(p.amount.toFixed(2)), paymentMethod: p.method, notes: p.notes, anomaly: false, anomalyReason: null }
      });
      if (!changed.count) throw new AppError('CONFLICT', 'Payment changed or its correction window expired', 409, { code: 'PAYMENT_EDIT_WINDOW_EXPIRED' });
      const x = await tx.payment.findUniqueOrThrow({ where: { id } });
      await tx.paymentAudit.create({ data: { paymentId: id, actorId: cid, actorRole: 'COLLECTOR', event: 'PAYMENT_EDITED', oldValues: { amount: old.amount, paymentMethod: old.paymentMethod }, newValues: { amount: x.amount, paymentMethod: x.paymentMethod } } });
      return x;
    });
  }
  async dispute(id: string, cid: string, p: any) {
    const pay = await this.owned(id, cid);
    return this.db.$transaction(async tx => {
      const claimed = await tx.payment.updateMany({ where: { id: pay.id, collectorId: cid, status: { not: 'DISPUTED' } }, data: { status: 'DISPUTED' } });
      if (!claimed.count) throw new AppError('CONFLICT', 'Payment dispute already exists', 409, { code: 'PAYMENT_DISPUTE_EXISTS' });
      return tx.paymentDispute.create({ data: { paymentId: id, reportedBy: cid, type: p.reason, description: p.description } });
    });
  }
  async ledger(cid: string, query: { limit?: unknown; cursor?: unknown; formalCursor?: unknown } = {}) {
    // Ledger months are business months in India, independent of the host
    // machine's timezone (which is commonly UTC in production).
    const indiaParts = new Intl.DateTimeFormat('en-US', { timeZone: 'Asia/Kolkata', year: 'numeric', month: 'numeric' }).formatToParts(new Date());
    const year = Number(indiaParts.find(part => part.type === 'year')?.value);
    const month = Number(indiaParts.find(part => part.type === 'month')?.value) - 1;
    const offsetMs = 5.5 * 60 * 60 * 1000;
    const monthStart = new Date(Date.UTC(year, month, 1) - offsetMs);
    const nextMonthStart = new Date(Date.UTC(year, month + 1, 1) - offsetMs);
    const limit = query.limit == null ? 100 : Number(query.limit);
    if (!Number.isInteger(limit) || limit < 1 || limit > 100) throw new AppError('VALIDATION_ERROR', 'Invalid ledger page size', 422, { code: 'INVALID_PAYMENT_LIMIT' });
    const formalCursor = query.formalCursor == null ? null : String(query.formalCursor);
    if (formalCursor && (!/^[A-Za-z0-9_-]{1,120}$/.test(formalCursor) || !await this.db.supplyPayment.findFirst({ where: { id: formalCursor, collectorId: cid }, select: { id: true } }))) throw new AppError('VALIDATION_ERROR', 'Invalid formal payment cursor', 422, { code: 'INVALID_CURSOR' });
    const settledWhere = { collectorId: cid, status: { notIn: ['DISPUTED', 'REVERSED'] as any[] } };
    const monthWhere = { ...settledWhere, recordedAt: { gte: monthStart, lt: nextMonthStart } };
    const [legacyPage, formalRows, legacyTotal, formalTotal, legacyMonth, formalMonth, pendingTotal] = await Promise.all([
      this.page(cid, { limit, cursor: query.cursor }),
      this.db.supplyPayment.findMany({ where: { collectorId: cid }, orderBy: [{ recordedAt: 'desc' }, { id: 'desc' }], take: limit + 1, ...(formalCursor ? { cursor: { id: formalCursor }, skip: 1 } : {}) }),
      this.db.payment.aggregate({ where: settledWhere, _sum: { amount: true }, _count: { _all: true } }),
      this.db.supplyPayment.aggregate({ where: settledWhere, _sum: { amount: true }, _count: { _all: true } }),
      this.db.payment.aggregate({ where: monthWhere, _sum: { amount: true } }),
      this.db.supplyPayment.aggregate({ where: monthWhere, _sum: { amount: true } }),
      this.db.supplyPayment.aggregate({ where: { collectorId: cid, status: 'RECORDED' }, _sum: { amount: true } })
    ]);
    const payments = legacyPage.items;
    const supplyPayments = formalRows.slice(0, limit);
    const total = (legacyTotal._sum.amount ?? 0) + (formalTotal._sum.amount ?? 0);
    const thisMonth = (legacyMonth._sum.amount ?? 0) + (formalMonth._sum.amount ?? 0);
    const allSettledCount = legacyTotal._count._all + formalTotal._count._all;
    const pending = Number((pendingTotal._sum.amount ?? 0).toFixed(2));
    const summary = {
      totalEarnings: Number(total.toFixed(2)),
      pendingAmount: pending,
      thisMonthEarnings: Number(thisMonth.toFixed(2)),
      averageLotValue: allSettledCount ? Number((total / allSettledCount).toFixed(2)) : 0
    };
    // Keep the historical summary/transactions shape for existing clients,
    // while exposing the flat contract consumed by the Android ledger cache.
    return {
      total: summary.totalEarnings,
      pending,
      currentMonth: summary.thisMonthEarnings,
      averagePerLot: summary.averageLotValue,
      payments,
      formalPayments: supplyPayments,
      summary,
      transactions: [...payments.map((payment: any) => ({ ...payment, sourceType: 'LEGACY_LOT' })), ...supplyPayments.map((payment: any) => ({ ...payment, sourceType: 'FORMAL_HANDOVER' }))].sort((left, right) => right.recordedAt.getTime() - left.recordedAt.getTime() || String(right.id).localeCompare(String(left.id))),
      historyPage: { payments: legacyPage.page, formalPayments: { nextCursor: formalRows.length > limit ? supplyPayments[supplyPayments.length - 1].id : null } }
    };
  }
  async adminList() { return (await this.page(null)).items; }
  async verify(id: string, admin: string) { const p = await this.db.payment.findUnique({ where: { id } }); if (!p) throw new AppError('NOT_FOUND', 'Payment not found', 404, { code: 'PAYMENT_NOT_FOUND' }); if (p.status !== 'RECORDED') throw new AppError('CONFLICT', 'Only an undisputed recorded payment can be verified', 409, { code: 'PAYMENT_NOT_VERIFIABLE' }); return this.db.$transaction(async tx => { const claimed = await tx.payment.updateMany({ where: { id, status: 'RECORDED' }, data: { status: 'VERIFIED', confirmedAt: new Date() } }); if (!claimed.count) throw new AppError('CONFLICT', 'Payment changed while being verified', 409, { code: 'PAYMENT_NOT_VERIFIABLE' }); const x = await tx.payment.findUniqueOrThrow({ where: { id } }); await tx.paymentAudit.create({ data: { paymentId: id, actorId: admin, actorRole: 'ADMIN', event: 'PAYMENT_VERIFIED' } }); return x; }); }
}
