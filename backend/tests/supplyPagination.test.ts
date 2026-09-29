import { describe, expect, it } from 'vitest';
import { listingsForCollectorPickupIds, nearbyCollectorPage, pagedCollectorPickups, pagedRows } from '../src/routes/supplyChainRoutes.js';

describe('nearby collector database paging', () => {
  it('filters coordinates before paging and keeps an area fallback for profiles without GPS', async () => {
    let pipeline: any[] = [];
    const store = { collector: { aggregateRaw: async (query: any) => {
      pipeline = query.pipeline;
      return [{ count: [{ total: 23 }], items: [{ id: 'collector-21', areaName: 'Rohini, Delhi' }] }];
    } } };
    const result = await nearbyCollectorPage(store, { latitude: 28.72, longitude: 77.11, area: 'Rohini, Delhi', radiusKm: 25, page: 2, limit: 20 });
    expect(result.total).toBe(23);
    expect(result.profiles.map((profile: any) => profile.id)).toEqual(['collector-21']);
    expect(pipeline[0].$match.latitude).toMatchObject({ $gte: expect.any(Number), $lte: expect.any(Number) });
    expect(pipeline.some(stage => stage.$unionWith?.coll === 'Collector')).toBe(true);
    expect(pipeline.at(-1).$facet.items[0]).toEqual({ $skip: 20 });
    expect(pipeline.at(-1).$facet.items[1]).toEqual({ $limit: 21 });
    expect(pipeline.some(stage => stage.$lookup?.from === 'User')).toBe(true);
  });
});

const rows = [
  { id: 'third', householdId: 'owner', createdAt: new Date('2026-01-03T00:00:00Z') },
  { id: 'second', householdId: 'owner', createdAt: new Date('2026-01-02T00:00:00Z') },
  { id: 'first', householdId: 'owner', createdAt: new Date('2026-01-01T00:00:00Z') },
  { id: 'foreign', householdId: 'other', createdAt: new Date('2026-01-04T00:00:00Z') }
];

const model = {
  findFirst: async ({ where }: any) => rows.find(row => row.id === where.id && row.householdId === where.householdId) ?? null,
  findMany: async ({ where, take, cursor, skip }: any) => {
    const owned = rows.filter(row => row.householdId === where.householdId).sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime());
    const start = cursor ? owned.findIndex(row => row.id === cursor.id) + (skip ?? 0) : 0;
    return take ? owned.slice(start, start + take) : owned;
  }
};

describe('account-scoped supply history pagination', () => {
  it('returns stable pages and preserves the legacy unpaged response', async () => {
    const first = await pagedRows(model, { householdId: 'owner' }, { limit: '2' });
    expect(first.items.map((row: any) => row.id)).toEqual(['third', 'second']);
    expect(first.page?.nextCursor).toBe('second');
    const second = await pagedRows(model, { householdId: 'owner' }, { limit: '2', cursor: first.page?.nextCursor });
    expect(second.items.map((row: any) => row.id)).toEqual(['first']);
    expect(second.page?.nextCursor).toBeNull();
    const legacy = await pagedRows(model, { householdId: 'owner' }, {});
    expect(legacy.items).toHaveLength(3);
    expect(legacy.page).toBeUndefined();
  });

  it('rejects a cursor owned by another account', async () => {
    await expect(pagedRows(model, { householdId: 'owner' }, { limit: '2', cursor: 'foreign' }))
      .rejects.toMatchObject({ status: 422 });
  });
});

describe('collector pickup feed pagination', () => {
  const own = { areaName: 'Rohini, Delhi', latitude: 28.72, longitude: 77.11, pickupMaxDistanceKm: 10 };
  const pickups = [
    { id: 'assigned', listingId: 'assigned-listing', kabadiwalaId: 'collector', status: 'SCHEDULED', createdAt: new Date('2026-09-29T11:00:00Z') },
    { id: 'near', listingId: 'near-listing', kabadiwalaId: null, status: 'WAITING_FOR_PICKUP', createdAt: new Date('2026-09-29T10:00:00Z') },
    { id: 'far', listingId: 'far-listing', kabadiwalaId: null, status: 'WAITING_FOR_PICKUP', createdAt: new Date('2026-09-29T09:00:00Z') },
    ...Array.from({ length: 120 }, (_, i) => ({ id: `history-${String(i).padStart(3, '0')}`, listingId: `history-listing-${i}`, kabadiwalaId: 'collector', status: 'COMPLETED', createdAt: new Date(Date.parse('2026-09-28T00:00:00Z') - i * 1000) })),
    { id: 'other-account', listingId: 'other-listing', kabadiwalaId: 'other', status: 'COMPLETED', createdAt: new Date('2026-09-29T12:00:00Z') }
  ];
  const locations: Record<string, any> = {
    'near-listing': { id: 'near-listing', areaName: 'Rohini, Delhi', latitude: 28.72, longitude: 77.11 },
    'far-listing': { id: 'far-listing', areaName: 'Mumbai', latitude: 19.07, longitude: 72.87 }
  };
  const matches = (row: any, where: any): boolean => {
    if (where.OR) return where.OR.some((part: any) => matches(row, part));
    return Object.entries(where).every(([key, expected]) => {
      if (key === 'id') return row.id === expected;
      if (expected && typeof expected === 'object') {
        const condition = expected as any;
        return (!condition.in || condition.in.includes(row[key])) && (!condition.notIn || !condition.notIn.includes(row[key]));
      }
      return row[key] === expected;
    });
  };
  const store = {
    pickupRequest: {
      findFirst: async ({ where }: any) => pickups.find(row => matches(row, where)) ?? null,
      findMany: async ({ where, cursor, skip, take }: any) => {
        const ordered = pickups.filter(row => matches(row, where)).sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime() || b.id.localeCompare(a.id));
        const start = cursor ? ordered.findIndex(row => row.id === cursor.id) + (skip ?? 0) : 0;
        return ordered.slice(start, start + take);
      }
    },
    householdListing: { findMany: async ({ where }: any) => (where.id.in as string[]).map(id => locations[id]).filter(Boolean) }
  };

  it('keeps active work visible and pages long history without sending another account or distant waiting requests', async () => {
    const active = await pagedCollectorPickups(store, 'collector', own, { limit: '50', scope: 'active' });
    expect(active.items.map((row: any) => row.id)).toEqual(['assigned', 'near']);
    expect(active.page.nextCursor).toBeNull();
    const assigned = await pagedCollectorPickups(store, 'collector', own, { limit: '50', scope: 'assigned' });
    const waiting = await pagedCollectorPickups(store, 'collector', own, { limit: '50', scope: 'waiting' });
    expect(assigned.items.map((row: any) => row.id)).toEqual(['assigned']);
    expect(waiting.items.map((row: any) => row.id)).toEqual(['near']);
    const first = await pagedCollectorPickups(store, 'collector', own, { limit: '50', scope: 'history' });
    expect(first.items).toHaveLength(50);
    expect(first.page.nextCursor).toBe('history-049');
    const second = await pagedCollectorPickups(store, 'collector', own, { limit: '50', scope: 'history', cursor: first.page.nextCursor });
    expect(second.items.map((row: any) => row.id)).toContain('history-050');
    expect(second.items.map((row: any) => row.id)).not.toContain('history-000');
  });

  it('rejects a cursor outside the collector feed', async () => {
    await expect(pagedCollectorPickups(store, 'collector', own, { scope: 'history', cursor: 'other-account' }))
      .rejects.toMatchObject({ status: 422 });
  });

  it('uses a limited projection for unclaimed pickup rows', async () => {
    let requestedSelect: Record<string, boolean> | undefined;
    await pagedCollectorPickups({
      pickupRequest: { findMany: async ({ select }: any) => { requestedSelect = select; return []; } },
      householdListing: { findMany: async () => [] }
    }, 'collector', own, { scope: 'waiting', limit: '10' });
    expect(requestedSelect?.id).toBe(true);
    expect(requestedSelect).not.toHaveProperty('householdId');
    expect(requestedSelect).not.toHaveProperty('settlementEvidenceReference');
  });

  it('returns listing details only for assigned or nearby waiting pickups and hides unclaimed addresses', async () => {
    const listingRows = [
      { id: 'assigned-listing', pickupAddress: 'Private assigned address', latitude: 28.72, longitude: 77.11, areaName: 'Rohini, Delhi', photoReference: 'secret-photo-key' },
      { id: 'near-listing', pickupAddress: 'Private waiting address', latitude: 28.72, longitude: 77.11, areaName: 'Rohini, Delhi' },
      { id: 'far-listing', pickupAddress: 'Private far address', latitude: 19.07, longitude: 72.87, areaName: 'Mumbai' },
      { id: 'other-listing', pickupAddress: 'Private foreign address', latitude: 28.72, longitude: 77.11, areaName: 'Rohini, Delhi' }
    ];
    const scopedStore = {
      pickupRequest: { findMany: async ({ where }: any) => pickups.filter(row => where.id.in.includes(row.id) && matches(row, { OR: where.OR })) },
      collector: { findUnique: async () => own },
      householdListing: { findMany: async ({ where }: any) => listingRows.filter(row => where.id.in.includes(row.id)) }
    };
    const result = await listingsForCollectorPickupIds(scopedStore, 'collector', ['assigned', 'near', 'far', 'other-account']);
    expect(result.map((row: any) => row.id)).toEqual(['assigned-listing', 'near-listing']);
    expect(result[0].pickupAddress).toBe('Private assigned address');
    expect(result[0].photoAttached).toBe(true);
    expect(result[0]).not.toHaveProperty('photoReference');
    expect(result[1].pickupAddress).toBeNull();
    expect(result[1].latitude).toBeNull();
  });
});
