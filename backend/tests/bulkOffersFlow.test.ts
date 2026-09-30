import { describe, expect, it, vi } from 'vitest';
import express from 'express';
import request from 'supertest';
import { JwtService } from '../src/services/jwt.js';
import { supplyChainRoutes } from '../src/routes/supplyChainRoutes.js';
import { errorHandler } from '../src/middleware/errors.js';

function matches(row: any, where: any): boolean {
  return Object.entries(where ?? {}).every(([key, value]: any) => {
    if (key === 'OR') return value.some((clause: any) => matches(row, clause));
    if (key === 'bulkLotId_recyclerId') return matches(row, value);
    if (value && typeof value === 'object' && !(value instanceof Date)) {
      if ('in' in value) return value.in.includes(row[key]);
      if ('not' in value) return row[key] !== value.not;
    }
    return row[key] === value;
  });
}
function fixture() {
  const lot: any = { id: 'lot-1', kabadiwalaId: 'collector-1', materialCategory: 'PLASTIC', grade: 'UNSPECIFIED', quantityKg: 10, askingRatePerKg: 120, minimumRatePerKg: 100, status: 'LISTED', areaName: 'Delhi', createdAt: new Date(), updatedAt: new Date() };
  const offers: any[] = [];
  const db: any = {
    user: { findFirst: vi.fn(async ({ where }: any) => ({ role: where.recyclerProfileId ? 'RECYCLER' : 'COLLECTOR', accountStatus: 'ACTIVE' })) },
    recycler: {
      findUnique: vi.fn(async () => ({ authorizationStatus: 'VERIFIED' })),
      findFirst: vi.fn(async ({ where }: any) => ({ id: where.id, authorizationStatus: 'VERIFIED', materials: [{ category: lot.materialCategory }] })),
      findMany: vi.fn(async ({ where }: any) => where.id.in.map((id: string) => ({ id, name: 'Demo Recycler' })))
    },
    recyclerMaterial: { findFirst: vi.fn(async () => ({ category: lot.materialCategory })) },
    bulkLot: {
      findFirst: vi.fn(async ({ where }: any) => matches(lot, where) ? { ...lot } : null),
      findUnique: vi.fn(async ({ where }: any) => lot.id === where.id ? { ...lot } : null),
      findMany: vi.fn(async ({ where }: any) => matches(lot, where) ? [{ ...lot }] : []),
      updateMany: vi.fn(async ({ where, data }: any) => {
        if (!matches(lot, where)) return { count: 0 };
        Object.assign(lot, data);
        return { count: 1 };
      })
    },
    bulkOffer: {
      findUnique: vi.fn(async ({ where }: any) => { const row = offers.find(row => matches(row, where)); return row ? { ...row } : null; }),
      findUniqueOrThrow: vi.fn(async ({ where }: any) => ({ ...offers.find(row => matches(row, where)) })),
      findFirst: vi.fn(async ({ where }: any) => { const row = offers.find(row => matches(row, where)); return row ? { ...row } : null; }),
      findMany: vi.fn(async ({ where, take }: any) => offers.filter(row => matches(row, where)).slice(0, take).map(row => ({ ...row }))),
      upsert: vi.fn(async ({ where, create, update }: any) => {
        let row = offers.find(row => matches(row, where));
        if (row) Object.assign(row, update, { updatedAt: new Date() });
        else { row = { id: `offer-${offers.length + 1}`, status: 'PENDING', ...create, createdAt: new Date(), updatedAt: new Date() }; offers.push(row); }
        return { ...row };
      }),
      updateMany: vi.fn(async ({ where, data }: any) => {
        const rows = offers.filter(row => matches(row, where));
        rows.forEach(row => Object.assign(row, data, { updatedAt: new Date() }));
        return { count: rows.length };
      })
    },
    auditEvent: { create: vi.fn(async () => ({})) },
    materialPassportEvent: { create: vi.fn(async () => ({})) },
    notificationEvent: { create: vi.fn(async ({ data }: any) => ({ ...data })) }
  };
  db.$transaction = vi.fn(async (work: any) => work(db));
  const jwt = new JwtService({ JWT_SECRET: 'bulk-offer-flow-test-secret', JWT_EXPIRES_IN: '1h' } as never);
  const app = express();
  app.use(express.json());
  app.use('/api/v1', supplyChainRoutes(jwt, { findById: vi.fn(async () => ({ accountStatus: 'ACTIVE' })) } as never, db));
  app.use(errorHandler);
  const recycler = (id = 'recycler-1') => `Bearer ${jwt.generateRecyclerToken(id)}`;
  const collector = (id = 'collector-1') => `Bearer ${jwt.generateToken(id)}`;
  const submit = (rate: number, id?: string) => request(app).post('/api/v1/recycler/bulk-lots/lot-1/offers').set('Authorization', recycler(id)).send({ offeredRatePerKg: rate });
  return { app, db, jwt, lot, offers, recycler, collector, submit };
}

describe('Kabadiwala to Recycler bulk offer journey', () => {
  it('rejects an offer below the minimum before storing or notifying', async () => {
    const { submit, db } = fixture();
    const response = await submit(99.99);
    expect(response.status).toBe(422);
    expect(response.body.error.details.code).toBe('BULK_OFFER_BELOW_MINIMUM');
    expect(db.bulkOffer.upsert).not.toHaveBeenCalled();
    expect(db.notificationEvent.create).not.toHaveBeenCalled();
  });
  it('submits, displays to the owner, edits, withdraws, resubmits and accepts the same offer', async () => {
    const { app, submit, offers, lot, db, collector, recycler } = fixture();
    const first = await submit(100);
    expect(first.status).toBe(201);
    const id = first.body.data.id;
    expect(db.recycler.findFirst.mock.calls[0][0].where.OR).toContainEqual({ authorizationValidUntil: { isSet: false } });
    const inbox = await request(app).get('/api/v1/kabadiwala/bulk-offers?limit=50').set('Authorization', collector());
    expect(inbox.status).toBe(200);
    expect(inbox.body.data[0]).toMatchObject({ id, recyclerName: 'Demo Recycler', bulkLot: { id: lot.id, quantityKg: 10, minimumRatePerKg: 100 } });
    expect((await submit(110)).body.data).toMatchObject({ id, offeredRatePerKg: 110, status: 'PENDING' });
    const withdrawn = await request(app).post(`/api/v1/recycler/offers/${id}/withdraw`).set('Authorization', recycler()).send({ reason: 'Revise price' });
    expect(withdrawn.status).toBe(200);
    expect(withdrawn.body.data.status).toBe('CANCELLED');
    expect(lot.status).toBe('LISTED');
    expect((await submit(115)).body.data).toMatchObject({ id, offeredRatePerKg: 115, status: 'PENDING' });
    expect(offers).toHaveLength(1);
    const accepted = await request(app).post(`/api/v1/kabadiwala/bulk-offers/${id}/accept`).set('Authorization', collector());
    expect(accepted.status).toBe(200);
    expect(accepted.body.success).toBe(true);
    expect(lot).toMatchObject({ status: 'RESERVED', reservedForId: 'recycler-1' });
    expect(offers[0].status).toBe('ACCEPTED');
    const market = await request(app).get('/api/v1/recycler/bulk-lots?limit=50').set('Authorization', recycler());
    expect(market.body.data[0]).toMatchObject({ id: lot.id, kabadiwalaId: 'collector-1', status: 'RESERVED' });
    const ownOffers = await request(app).get('/api/v1/recycler/offers?limit=50').set('Authorization', recycler());
    expect(ownOffers.body.data[0].bulkLot.kabadiwalaId).toBe('collector-1');
    expect((await submit(125)).status).toBe(404);
    expect((await request(app).post(`/api/v1/recycler/offers/${id}/withdraw`).set('Authorization', recycler())).status).toBe(409);
    const events = db.notificationEvent.create.mock.calls.map(([arg]: any[]) => arg.data);
    expect(events.filter((event: any) => event.type === 'BULK_OFFER_SUBMITTED').every((event: any) => event.accountId === 'collector-1')).toBe(true);
    expect(events.find((event: any) => event.type === 'BULK_OFFER_WITHDRAWN')).toMatchObject({ accountId: 'collector-1', route: 'kabadiwala/lots' });
    expect(events.find((event: any) => event.type === 'BULK_OFFER_ACCEPTED')).toMatchObject({ accountId: 'recycler-1', route: 'recycler/marketplace' });
  });
  it('allows a revised offer after the owner rejects it', async () => {
    const { app, submit, collector } = fixture();
    const offered = await submit(100);
    const rejected = await request(app).post(`/api/v1/kabadiwala/bulk-offers/${offered.body.data.id}/reject`).set('Authorization', collector());
    expect(rejected.status).toBe(200);
    expect((await submit(120)).body.data).toMatchObject({ id: offered.body.data.id, status: 'PENDING', offeredRatePerKg: 120 });
  });
  it('isolates owners and keeps reserved lots private to the selected Recycler', async () => {
    const { app, submit, collector, recycler, lot } = fixture();
    const offered = await submit(100);
    expect((await request(app).get('/api/v1/kabadiwala/bulk-offers?limit=50').set('Authorization', collector('other-collector'))).body.data).toEqual([]);
    expect((await request(app).post(`/api/v1/kabadiwala/bulk-offers/${offered.body.data.id}/accept`).set('Authorization', collector('other-collector'))).status).toBe(404);
    expect((await request(app).post(`/api/v1/recycler/offers/${offered.body.data.id}/withdraw`).set('Authorization', recycler('other-recycler'))).status).toBe(409);
    lot.status = 'RESERVED'; lot.reservedForId = 'recycler-1';
    expect((await request(app).get('/api/v1/recycler/bulk-lots?limit=50').set('Authorization', recycler('other-recycler'))).body.data).toEqual([]);
  });
  it('rejects a rate revision when the lot changes before the transaction claims it', async () => {
    const { submit, db } = fixture();
    db.bulkLot.updateMany.mockResolvedValueOnce({ count: 0 });
    expect((await submit(110)).status).toBe(409);
    expect(db.bulkOffer.upsert).not.toHaveBeenCalled();
  });
  it.each(['ACCEPTED', 'COMPLETED'])('does not reopen a %s offer even if legacy lot status is LISTED', async (status) => {
    const { submit, offers } = fixture();
    offers.push({ id: 'offer-1', bulkLotId: 'lot-1', recyclerId: 'recycler-1', offeredRatePerKg: 100, status });
    expect((await submit(110)).status).toBe(409);
    expect(offers[0]).toMatchObject({ status, offeredRatePerKg: 100 });
  });
  it('rejects a Household trying to submit a Recycler offer', async () => {
    const { app, jwt, db } = fixture();
    const response = await request(app).post('/api/v1/recycler/bulk-lots/lot-1/offers').set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-1')}`).send({ offeredRatePerKg: 120 });
    expect(response.status).toBe(403);
    expect(db.bulkOffer.upsert).not.toHaveBeenCalled();
  });
});
