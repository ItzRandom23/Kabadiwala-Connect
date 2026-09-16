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
});
