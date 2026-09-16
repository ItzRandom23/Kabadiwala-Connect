import { describe, expect, it, vi } from 'vitest';
import { PriceRepository } from '../src/repositories/priceRepository.js';

const price = { id: 'pune-pcb', materialCategory: 'PCB', city: 'Pune', areaName: null };

function fakeDb() {
  return {
    price: { findFirst: vi.fn() },
    priceHistory: { findMany: vi.fn() }
  } as any;
}

describe('price location fallback', () => {
  it('falls back from an area label to its city', async () => {
    const db = fakeDb();
    db.price.findFirst.mockResolvedValueOnce(null).mockResolvedValueOnce(price);

    const result = await new PriceRepository(db).latest('PCB' as any, 'Kothrud, Pune');

    expect(result).toEqual(price);
    expect(db.price.findFirst).toHaveBeenNthCalledWith(2, expect.objectContaining({
      where: { materialCategory: 'PCB', OR: [{ areaName: 'Pune' }, { city: 'Pune' }] }
    }));
  });

  it('falls back to the latest known rate for the material', async () => {
    const db = fakeDb();
    db.price.findFirst.mockResolvedValueOnce(null).mockResolvedValueOnce(null).mockResolvedValueOnce(price);

    const result = await new PriceRepository(db).latest('PCB' as any, 'Baner, Pune');

    expect(result).toEqual(price);
    expect(db.price.findFirst).toHaveBeenLastCalledWith({
      where: { materialCategory: 'PCB' },
      orderBy: { effectiveAt: 'desc' }
    });
  });

  it('uses the same fallback rule for history', async () => {
    const db = fakeDb();
    db.priceHistory.findMany.mockResolvedValueOnce([]).mockResolvedValueOnce([{ marketPrice: 2400 }]);

    const result = await new PriceRepository(db).history('PCB' as any, 'Kothrud, Pune', new Date(0));

    expect(result).toEqual([{ marketPrice: 2400 }]);
    expect(db.priceHistory.findMany).toHaveBeenNthCalledWith(2, expect.objectContaining({
      where: { materialCategory: 'PCB', effectiveAt: { gte: new Date(0) }, OR: [{ areaName: 'Pune' }, { city: 'Pune' }] }
    }));
  });
});
