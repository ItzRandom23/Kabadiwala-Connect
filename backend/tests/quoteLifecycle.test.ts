import { describe, expect, it, vi } from 'vitest';
import { QuoteService } from '../src/services/quoteService.js';

describe('quote lifecycle recovery', () => {
  it('does not create a quote request when cancellation wins after the eligibility read', async () => {
    const created = vi.fn();
    const audit = vi.fn();
    const lot = {
      id: 'lot-race', collectorId: 'collector-1', status: 'CREATED', materialCategory: 'PCB',
      weight: 2, weightUnit: 'KILOGRAM', estimatedValue: 50,
      collectionLatitude: 18.5, collectionLongitude: 73.8, collectionAreaName: 'Pune'
    };
    const tx = {
      lot: {
        updateMany: vi.fn().mockResolvedValue({ count: 0 }),
        findFirst: vi.fn().mockResolvedValue(null)
      },
      quoteRequest: { create: created },
      quoteAudit: { create: audit }
    };
    const db = {
      lot: { findFirst: vi.fn().mockResolvedValue(lot) },
      recycler: { findUnique: vi.fn().mockResolvedValue({ authorizationStatus: 'VERIFIED', authorizationValidUntil: null, materials: [{ category: 'PCB' }] }) },
      quoteRequest: { findFirst: vi.fn().mockResolvedValue(null) },
      $transaction: vi.fn(async (work: (client: typeof tx) => unknown) => work(tx))
    } as never;

    await expect(new QuoteService(db).requestQuote('collector-1', 'lot-race', 'recycler-1'))
      .rejects.toMatchObject({ details: { code: 'LOT_NOT_ELIGIBLE_FOR_QUOTE' } });
    expect(tx.lot.updateMany).toHaveBeenCalledWith({
      where: { id: 'lot-race', collectorId: 'collector-1', status: 'CREATED' },
      data: { status: 'QUOTE_REQUESTED' }
    });
    expect(created).not.toHaveBeenCalled();
    expect(audit).not.toHaveBeenCalled();
  });

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
