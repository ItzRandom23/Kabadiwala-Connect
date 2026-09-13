import { describe, expect, it, vi } from 'vitest';
import { RecyclerService } from '../src/services/recyclerService.js';

const recycler = (overrides: Record<string, unknown> = {}) => ({
  id: 'recycler-1',
  name: 'Green Loop',
  latitude: 18.5204,
  longitude: 73.8567,
  address: 'Pune',
  areaName: 'Pune',
  authorizationStatus: 'VERIFIED',
  authorizationAuthority: 'MPCB',
  authorizationValidUntil: null,
  maxPickupDistanceKm: 50,
  pickupAvailability: 'TODAY',
  operatingHours: {},
  averageHandoverTime: null,
  rating: 4.5,
  reviewCount: 10,
  updatedAt: new Date('2026-01-01T00:00:00Z'),
  phone: null,
  email: null,
  alternatePhone: null,
  materials: [{
    category: 'PCB',
    subcategories: [],
    minAcceptableWeight: null,
    maxAcceptableWeight: null
  }],
  rates: [{
    materialCategory: 'PCB',
    pricePerKg: 300,
    updatedAt: new Date('2026-01-01T00:00:00Z')
  }],
  _count: { handovers: 4 },
  ...overrides
});

describe('RecyclerService optimized queries', () => {
  it('filters, counts and paginates in MongoDB when distance calculation is unnecessary', async () => {
    const count = vi.fn().mockResolvedValue(21);
    const findMany = vi.fn().mockResolvedValue([recycler()]);
    const service = new RecyclerService({ recycler: { count, findMany } } as never);

    const result = await service.list({
      location: ' pune ',
      material: 'PCB',
      availability: 'TODAY',
      sort: 'proximity',
      page: 2,
      limit: 10
    });

    const expectedWhere = {
      authorizationStatus: 'VERIFIED',
      OR: [
        { authorizationValidUntil: null },
        { authorizationValidUntil: { gte: expect.any(Date) } }
      ],
      areaName: { contains: 'pune', mode: 'insensitive' },
      materials: { some: { category: 'PCB' } },
      pickupAvailability: 'TODAY'
    };
    expect(count).toHaveBeenCalledWith({ where: expectedWhere });
    expect(findMany).toHaveBeenCalledWith(expect.objectContaining({
      where: expectedWhere,
      orderBy: { id: 'asc' },
      skip: 10,
      take: 10
    }));
    expect(result.pagination).toEqual({ page: 2, limit: 10, total: 21, totalPages: 3 });
    expect(result.items).toHaveLength(1);
  });

  it('pushes cheap filters down while retaining coordinate radius filtering in memory', async () => {
    const findMany = vi.fn().mockResolvedValue([
      recycler(),
      recycler({ id: 'recycler-far', latitude: 19.076, longitude: 72.8777 })
    ]);
    const service = new RecyclerService({ recycler: { findMany } } as never);

    const result = await service.list({
      latitude: 18.5204,
      longitude: 73.8567,
      radius: 5,
      material: 'PCB',
      availability: 'TODAY',
      sort: 'proximity',
      page: 1,
      limit: 20
    });

    expect(findMany).toHaveBeenCalledWith(expect.objectContaining({
      where: {
        authorizationStatus: 'VERIFIED',
        OR: [
          { authorizationValidUntil: null },
          { authorizationValidUntil: { gte: expect.any(Date) } }
        ],
        materials: { some: { category: 'PCB' } },
        pickupAvailability: 'TODAY'
      }
    }));
    expect(result.items.map(item => item.id)).toEqual(['recycler-1']);
    expect(result.items[0].distanceKm).toBe(0);
  });

  it('queries only recyclers that accept the lot material during matching', async () => {
    const findMany = vi.fn().mockResolvedValue([recycler()]);
    const service = new RecyclerService({
      lot: {
        findFirst: vi.fn().mockResolvedValue({ materialCategory: 'PCB', weight: 2 })
      },
      collector: {
        findUnique: vi.fn().mockResolvedValue({ latitude: 18.5204, longitude: 73.8567 })
      },
      recycler: { findMany }
    } as never);

    const result = await service.match('lot-1', 'collector-1');

    expect(findMany).toHaveBeenCalledWith(expect.objectContaining({
      where: {
        authorizationStatus: 'VERIFIED',
        OR: [
          { authorizationValidUntil: null },
          { authorizationValidUntil: { gte: expect.any(Date) } }
        ],
        materials: { some: { category: 'PCB' } }
      }
    }));
    expect(result.matches).toHaveLength(1);
    expect(result.matches[0].offeredRatePerKg).toBe(300);
  });
});
