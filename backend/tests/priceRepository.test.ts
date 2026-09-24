import { describe, expect, it, vi } from 'vitest';
import { PriceRepository } from '../src/repositories/priceRepository.js';

const price = { id: 'pune-pcb', materialCategory: 'PCB', city: 'Pune', areaName: null };

function fakeDb() {
  return {
    price: { findFirst: vi.fn() },
    priceHistory: { findMany: vi.fn() }
  } as any;
}

describe('price location matching', () => {
  it('falls back from an area label to its city', async () => {
    const db = fakeDb();
    db.price.findFirst.mockResolvedValueOnce(null).mockResolvedValueOnce(null).mockResolvedValueOnce(price);

    const result = await new PriceRepository(db).latest('PCB' as any, 'Kothrud, Pune');

    expect(result).toEqual(price);
    expect(db.price.findFirst).toHaveBeenNthCalledWith(3, expect.objectContaining({
      where: { materialCategory: 'PCB', OR: [{ areaName: { equals: 'Pune', mode: 'insensitive' } }, { city: { equals: 'Pune', mode: 'insensitive' } }] }
    }));
  });

  it('does not return a price from an unrelated area', async () => {
    const db = fakeDb();
    db.price.findFirst.mockResolvedValueOnce(null).mockResolvedValueOnce(null).mockResolvedValueOnce(null);

    const result = await new PriceRepository(db).latest('PCB' as any, 'Baner, Pune');

    expect(result).toBeNull();
    expect(db.price.findFirst).toHaveBeenCalledTimes(3);
  });

  it('does not return history from an unrelated area', async () => {
    const db = fakeDb();
    db.priceHistory.findMany.mockResolvedValue([]);

    const result = await new PriceRepository(db).history('PCB' as any, 'Kothrud, Pune', new Date(0));

    expect(result).toEqual([]);
    expect(db.priceHistory.findMany).toHaveBeenCalledTimes(3);
  });
});
