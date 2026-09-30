import { describe, expect, it, vi } from 'vitest';
import { PaymentService } from '../src/services/paymentService.js';

describe('payment settlement lifecycle', () => {
  it('rejects impossible calendar dates and amounts that round to zero before a database write', async () => {
    const db = { $transaction: vi.fn() } as never;
    const service = new PaymentService(db);
    const input = { lotId: 'lot-1', amount: 100, method: 'CASH', date: '2020-01-01' };
    await expect(service.record('collector-1', { ...input, date: '2020-02-31' })).rejects.toMatchObject({ details: { code: 'PAYMENT_DATE_INVALID' } });
    await expect(service.record('collector-1', { ...input, amount: 0.001 })).rejects.toMatchObject({ details: { code: 'PAYMENT_AMOUNT_INVALID' } });
    expect((db as any).$transaction).not.toHaveBeenCalled();
  });
  it('completes the confirmed handover in the same transaction as payment', async () => {
    const handoverUpdate = vi.fn().mockResolvedValue({ id: 'handover-1', status: 'COMPLETED' });
    const tx = {
      lot: { findFirst: vi.fn().mockResolvedValue({ id: 'lot-1', status: 'HANDED_OVER', quotes: [], handovers: [{ status: 'CONFIRMED_BY_RECYCLER' }] }), updateMany: vi.fn().mockResolvedValue({ count: 1 }) },
      payment: { findFirst: vi.fn().mockResolvedValue(null), create: vi.fn().mockResolvedValue({ id: 'payment-1', amount: 425, paymentMethod: 'CASH' }) },
      handover: { findFirst: vi.fn().mockResolvedValue({ id: 'handover-1', status: 'CONFIRMED_BY_RECYCLER' }), update: handoverUpdate },
      paymentAudit: { create: vi.fn().mockResolvedValue({}) }
    };
    const db = {
      lot: { findFirst: vi.fn().mockResolvedValue({ id: 'lot-1', status: 'HANDED_OVER', quotes: [], handovers: [{ status: 'CONFIRMED_BY_RECYCLER' }] }) },
      payment: { findFirst: vi.fn().mockResolvedValue(null) },
      $transaction: vi.fn(async (work: (client: typeof tx) => unknown) => work(tx))
    } as never;

    const result = await new PaymentService(db).record('collector-1', {
      lotId: 'lot-1', amount: 425, method: 'CASH', date: '2020-01-01', time: '10:30', notes: ''
    });

    expect(result.id).toBe('payment-1');
    expect(handoverUpdate).toHaveBeenCalledWith({ where: { id: 'handover-1' }, data: { status: 'COMPLETED' } });
  });

  it('does not apply an edit after a concurrent verification claimed the payment', async () => {
    const payment = {
      id: 'payment-race', collectorId: 'collector-1', status: 'RECORDED', amount: 100,
      paymentMethod: 'CASH', createdAt: new Date(), lot: { quotes: [], handovers: [] }
    };
    const tx = {
      payment: { updateMany: vi.fn().mockResolvedValue({ count: 0 }), findUniqueOrThrow: vi.fn() },
      paymentAudit: { create: vi.fn() }
    };
    const db = {
      payment: { findUnique: vi.fn().mockResolvedValue(payment) },
      $transaction: vi.fn(async (work: (client: typeof tx) => unknown) => work(tx))
    } as never;

    await expect(new PaymentService(db).edit('payment-race', 'collector-1', { amount: 120, method: 'CASH', notes: '' }))
      .rejects.toMatchObject({ code: 'CONFLICT' });
    expect(tx.payment.updateMany).toHaveBeenCalledWith(expect.objectContaining({
      where: expect.objectContaining({ id: 'payment-race', collectorId: 'collector-1', status: 'RECORDED' })
    }));
    expect(tx.paymentAudit.create).not.toHaveBeenCalled();
  });

  it('does not count reversed formal payments as earnings but keeps them in transaction history', async () => {
    const reversed = {
      id: 'supply-payment-reversed', status: 'REVERSED', amount: 250,
      recordedAt: new Date(), collectorId: 'collector-1'
    };
    const db = {
      payment: { findMany: vi.fn().mockResolvedValue([]), aggregate: vi.fn().mockResolvedValue({ _sum: { amount: 0 }, _count: { _all: 0 } }) },
      supplyPayment: { findMany: vi.fn().mockResolvedValue([reversed]), aggregate: vi.fn().mockResolvedValue({ _sum: { amount: 0 }, _count: { _all: 0 } }) }
    } as never;

    const ledger = await new PaymentService(db).ledger('collector-1');
    expect(ledger.summary).toMatchObject({ totalEarnings: 0, thisMonthEarnings: 0, averageLotValue: 0 });
    expect(ledger.formalPayments).toEqual([reversed]);
    expect(ledger.transactions).toEqual([expect.objectContaining({ id: reversed.id, status: 'REVERSED' })]);
  });
  it('keeps full-database totals and India month bounds while displaying bounded history', async () => {
    const rows = Array.from({ length: 101 }, (_, index) => ({ id: `payment-${index}`, amount: 10, recordedAt: new Date(), status: 'VERIFIED' }));
    const aggregate = vi.fn(async ({ where, _count }: any) => ({ _sum: { amount: where.recordedAt ? 20000 : 100000 }, ...(_count ? { _count: { _all: 10000 } } : {}) }));
    const formalAggregate = vi.fn(async ({ where, _count }: any) => ({ _sum: { amount: where.status === 'RECORDED' ? 500 : where.recordedAt ? 10000 : 50000 }, ...(_count ? { _count: { _all: 5000 } } : {}) }));
    const db = { payment: { findMany: vi.fn().mockResolvedValue(rows), aggregate }, supplyPayment: { findMany: vi.fn().mockResolvedValue([]), aggregate: formalAggregate } } as never;
    const ledger = await new PaymentService(db).ledger('collector-1');
    expect(ledger).toMatchObject({ total: 150000, currentMonth: 30000, averagePerLot: 10, pending: 500, historyPage: { payments: { nextCursor: 'payment-99' } } });
    expect(ledger.payments).toHaveLength(100);
    expect(ledger.summary.pendingAmount).toBe(500);
    expect(aggregate.mock.calls[0][0].where).toMatchObject({ collectorId: 'collector-1', status: { notIn: ['DISPUTED', 'REVERSED'] } });
    const monthStart = aggregate.mock.calls[1][0].where.recordedAt.gte as Date;
    expect(monthStart.toISOString()).toMatch(/T18:30:00\.000Z$/);
  });
  it('validates account ownership of payment cursors and bounds legacy lists', async () => {
    const db = { payment: { findFirst: vi.fn().mockResolvedValue(null), findMany: vi.fn().mockResolvedValue([]) } } as never;
    const service = new PaymentService(db);
    await expect(service.page('collector-1', { cursor: 'other-account-payment' })).rejects.toMatchObject({ details: { code: 'INVALID_CURSOR' } });
    await expect(service.page('collector-1', { limit: 101 })).rejects.toMatchObject({ details: { code: 'INVALID_PAYMENT_LIMIT' } });
    await service.list('collector-1');
    expect((db as any).payment.findMany).toHaveBeenCalledWith(expect.objectContaining({ take: 101, where: { collectorId: 'collector-1' } }));
  });
  it('validates immutable details for a stable payment id and supports an outer sync transaction', async () => {
    const input = { id: 'payment-stable', lotId: 'lot-1', amount: 100, method: 'CASH', date: '2020-01-01', time: '10:30', notes: 'paid' };
    const existing = { id: input.id, lotId: input.lotId, amount: 100, paymentMethod: 'CASH', recordedAt: new Date('2020-01-01T10:30:00+05:30'), notes: 'paid' };
    const tx = { payment: { findFirst: vi.fn().mockResolvedValue(existing) } } as never;
    const db = { $transaction: vi.fn() } as never;
    const service = new PaymentService(db);
    expect(await service.record('collector-1', input, tx)).toBe(existing);
    expect((db as any).$transaction).not.toHaveBeenCalled();
    for (const change of [{ amount: 101 }, { method: 'BANK_TRANSFER' }, { lotId: 'lot-2' }, { notes: 'changed' }, { date: '2020-01-02' }]) {
      await expect(service.record('collector-1', { ...input, ...change }, tx)).rejects.toMatchObject({ details: { code: 'PAYMENT_ID_PAYLOAD_MISMATCH' } });
    }
  });
});
