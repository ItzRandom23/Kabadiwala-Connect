import { describe, expect, it, vi } from 'vitest';
import { PriceService } from '../src/services/priceService.js';

describe('price board availability', () => {
  it('returns an unavailable board when no market row exists', async () => {
    const repo = { latest: vi.fn().mockResolvedValue(null) } as any;
    const service = new PriceService(repo, {} as any);

    await expect(service.board('PCB' as any, 'Kothrud, Pune')).resolves.toMatchObject({
      available: false,
      materialCategory: 'PCB',
      location: 'Kothrud, Pune',
      marketPrice: null,
      qualityStatus: 'UNAVAILABLE'
    });
    expect(repo.latest).toHaveBeenCalledWith('PCB', 'Kothrud, Pune');
  });
});
