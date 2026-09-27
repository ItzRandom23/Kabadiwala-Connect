import { describe, expect, it, vi } from 'vitest';
import { PaymentService } from '../src/services/paymentService.js';

describe('payment settlement lifecycle', () => {
  it('completes the confirmed handover in the same transaction as payment', async () => {
    const handoverUpdate = vi.fn().mockResolvedValue({ id: 'handover-1', status: 'COMPLETED' });
    const tx = {
      lot: { updateMany: vi.fn().mockResolvedValue({ count: 1 }) },
      payment: { create: vi.fn().mockResolvedValue({ id: 'payment-1', amount: 425, paymentMethod: 'CASH' }) },
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
      payment: { findMany: vi.fn().mockResolvedValue([]) },
      supplyPayment: { findMany: vi.fn().mockResolvedValue([reversed]) }
    } as never;

    const ledger = await new PaymentService(db).ledger('collector-1');
    expect(ledger.summary).toMatchObject({ totalEarnings: 0, thisMonthEarnings: 0, averageLotValue: 0 });
    expect(ledger.formalPayments).toEqual([reversed]);
    expect(ledger.transactions).toEqual([expect.objectContaining({ id: reversed.id, status: 'REVERSED' })]);
  });
});
