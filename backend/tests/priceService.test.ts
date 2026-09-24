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

  it('shows a current validated local row with source and freshness metadata', async () => {
    const repo = {
      latest: vi.fn().mockResolvedValue({
        materialCategory: 'PCB', city: 'Pune', areaName: 'Kothrud', priceMin: 100, priceMax: 120,
        marketPrice: 110, historicalAverage: 105, unit: 'KILOGRAM', source: 'ADMIN',
        sourceOrganization: 'Pilot operator', sourceReference: 'rate-sheet-1', qualityStatus: 'VALIDATED',
        ingestedAt: new Date(), effectiveAt: new Date()
      }),
      history: vi.fn().mockResolvedValue([])
    } as any;
    const service = new PriceService(repo, {} as any);

    await expect(service.board('PCB' as any, 'Kothrud, Pune')).resolves.toMatchObject({
      available: true,
      location: 'Kothrud',
      requestedLocation: 'Kothrud, Pune',
      locationMatched: true,
      sourceContext: 'LOCATION_MATCH',
      source: { type: 'ADMIN', organization: 'Pilot operator' },
      dataAgeDays: 0,
      isDemoData: false
    });
  });

  it.each([
    [{ source: 'SYSTEM', qualityStatus: 'VALIDATED', effectiveAt: new Date(), ingestedAt: new Date() }, 'DEMO_DATA'],
    [{ source: 'ADMIN', qualityStatus: 'UNVERIFIED', effectiveAt: new Date(), ingestedAt: new Date() }, 'RATE_NOT_VERIFIED'],
    [{ source: 'ADMIN', qualityStatus: 'VALIDATED', effectiveAt: new Date(Date.now() - 8 * 86400000), ingestedAt: new Date() }, 'RATE_STALE']
  ])('hides non-current or non-verified rate rows', async (row, reason) => {
    const repo = { latest: vi.fn().mockResolvedValue({ materialCategory: 'PCB', city: 'Pune', priceMin: 1, priceMax: 2, marketPrice: 1.5, ...row }) } as any;
    const service = new PriceService(repo, {} as any);
    await expect(service.board('PCB' as any, 'Pune')).resolves.toMatchObject({ available: false, marketPrice: null, unavailableReason: reason });
  });
});
