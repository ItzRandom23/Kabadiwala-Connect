import { describe, expect, it, vi } from 'vitest';
import { QuoteService } from '../src/services/quoteService.js';

describe('quote lifecycle recovery', () => {
  it('reopens a lot when its last quote is rejected', async () => {
    const q = {
      id: 'quote-1', quoteRequestId: 'request-1', lotId: 'lot-1', recyclerId: 'recycler-1',
      status: 'SENT', validUntil: new Date(Date.now() + 60_000),
      quoteRequest: { collectorId: 'collector-1', status: 'ACCEPTED' },
      lot: { id: 'lot-1', status: 'QUOTE_RECEIVED' }
    };
    const lotUpdateMany = vi.fn().mockResolvedValue({ count: 1 });
    const tx = {
      quote: {
        updateMany: vi.fn().mockResolvedValue({ count: 1 }),
        count: vi.fn().mockResolvedValue(0),
        findUniqueOrThrow: vi.fn().mockResolvedValue({ ...q, status: 'REJECTED' })
      },
      quoteRequest: { updateMany: vi.fn().mockResolvedValue({ count: 1 }) },
      lot: { updateMany: lotUpdateMany },
      quoteAudit: { create: vi.fn().mockResolvedValue({}) }
    };
    const db = {
      quote: { findUnique: vi.fn().mockResolvedValue(q) },
      $transaction: vi.fn(async (work: (client: typeof tx) => unknown) => work(tx))
    } as never;

    const result = await new QuoteService(db).action('quote-1', 'collector-1', false);

    expect(result.status).toBe('REJECTED');
    expect(lotUpdateMany).toHaveBeenCalledWith({
      where: { id: 'lot-1', status: 'QUOTE_RECEIVED' },
      data: { status: 'QUOTE_REQUESTED' }
    });
    expect(tx.quoteAudit.create).toHaveBeenCalled();
  });

  it('does not reject an already-actioned quote twice', async () => {
    const q = {
      id: 'quote-1', quoteRequestId: 'request-1', lotId: 'lot-1', recyclerId: 'recycler-1',
      status: 'SENT', validUntil: new Date(Date.now() + 60_000),
      quoteRequest: { collectorId: 'collector-1', status: 'ACCEPTED' },
      lot: { id: 'lot-1', status: 'QUOTE_RECEIVED' }
    };
    const tx = {
      quote: { updateMany: vi.fn().mockResolvedValue({ count: 0 }) },
      quoteRequest: { updateMany: vi.fn() },
      lot: { updateMany: vi.fn() },
      quoteAudit: { create: vi.fn() }
    };
    const db = {
      quote: { findUnique: vi.fn().mockResolvedValue(q) },
      $transaction: vi.fn(async (work: (client: typeof tx) => unknown) => work(tx))
    } as never;
    await expect(new QuoteService(db).action('quote-1', 'collector-1', false))
      .rejects.toMatchObject({ details: { code: 'QUOTE_ALREADY_ACTIONED' } });
  });

  it('marks an expired sent quote before refusing the action', async () => {
    const q = {
      id: 'quote-expired', quoteRequestId: 'request-1', lotId: 'lot-1', recyclerId: 'recycler-1',
      status: 'SENT', validUntil: new Date(Date.now() - 60_000),
      quoteRequest: { collectorId: 'collector-1' },
      lot: { id: 'lot-1', status: 'QUOTE_RECEIVED' }
    };
    const expire = vi.fn().mockResolvedValue({ count: 1 });
    const db = {
      quote: { findUnique: vi.fn().mockResolvedValue(q), updateMany: expire }
    } as never;

    await expect(new QuoteService(db).action('quote-expired', 'collector-1', true))
      .rejects.toMatchObject({ details: { code: 'QUOTE_EXPIRED' } });
    expect(expire).toHaveBeenCalledWith(expect.objectContaining({
      where: { id: 'quote-expired', status: 'SENT' },
      data: expect.objectContaining({ status: 'EXPIRED' })
    }));
  });
});
