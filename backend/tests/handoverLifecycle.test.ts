import { describe, expect, it, vi } from 'vitest';
import { HandoverService } from '../src/services/handoverService.js';

describe('handover lifecycle recovery', () => {
  it('returns the canonical handover on a retry after the lot is already handed over', async () => {
    const existing = { id: 'handover-1', lotId: 'lot-1', status: 'GENERATED' };
    const findFirst = vi.fn().mockResolvedValue({ id: 'lot-1', collectorId: 'collector-1', status: 'HANDED_OVER' });
    const handoverFindFirst = vi.fn().mockResolvedValue(existing);
    const db = {
      lot: { findFirst },
      handover: { findFirst: handoverFindFirst, findUnique: vi.fn() }
    } as never;
    const result = await new HandoverService(db, 'test-secret').create('collector-1', { lotId: 'lot-1', quoteId: 'quote-1' });
    expect(result).toBe(existing);
    expect(handoverFindFirst).toHaveBeenCalledWith({ where: { lotId: 'lot-1' } });
  });
});
