import { describe, expect, it, vi } from 'vitest';
import express from 'express';
import request from 'supertest';
import { JwtService } from '../src/services/jwt.js';
import { createHash } from 'node:crypto';
import { createSupplyHandoverQr, formalisationRoutes } from '../src/routes/formalisationRoutes.js';
import { errorHandler } from '../src/middleware/errors.js';

function fixture() {
  const handover: any = { id: 'handover-1', collectorId: 'collector-1', recyclerId: 'recycler-1', bulkLotId: 'lot-1', status: 'COMPLETED', finalValue: 300, quotedValue: 300, quotedWeightKg: 0.5, recyclerConfirmedAt: new Date(), updatedAt: new Date() };
  let payment: any = null;
  const matches = (row: any, where: any) => row && Object.entries(where).every(([key, value]) => row[key] === value);
  const db: any = {
    user: { findFirst: vi.fn(async ({ where }: any) => ({ role: where.recyclerProfileId ? 'RECYCLER' : 'COLLECTOR', accountStatus: 'ACTIVE' })) },
    recycler: { findUnique: vi.fn(async () => ({ authorizationStatus: 'VERIFIED', accountStatus: 'ACTIVE' })) },
    supplyHandover: { findFirst: vi.fn(async ({ where }: any) => matches(handover, where) ? handover : null), findUnique: vi.fn(async () => handover), findMany: vi.fn(async () => [handover]) },
    poolContribution: { findMany: vi.fn(async () => []), findFirst: vi.fn(async () => null) },
    supplyPayment: {
      findMany: vi.fn(async ({ where }: any) => payment && (!where.collectorId || where.collectorId === payment.collectorId) && (!where.recyclerId || where.recyclerId === payment.recyclerId) ? [payment] : []),
      findUnique: vi.fn(async () => payment), findUniqueOrThrow: vi.fn(async () => payment),
      findFirst: vi.fn(async ({ where }: any) => matches(payment, where) ? payment : null),
      create: vi.fn(async ({ data }: any) => payment = { id: 'payment-1', status: 'RECORDED', ...data }),
      updateMany: vi.fn(async ({ where, data }: any) => { if (!matches(payment, where)) return { count: 0 }; Object.assign(payment, data); return { count: 1 }; })
    },
    auditEvent: { create: vi.fn(async () => ({})) },
    materialPassportEvent: { create: vi.fn(async () => ({})) },
    anomalyFlag: { create: vi.fn(async () => ({})) },
    notificationEvent: { create: vi.fn(async ({ data }: any) => ({ id: 'notice', ...data })) }, notificationDelivery: { upsert: vi.fn(async () => ({})) }
  };
  db.$transaction = async (fn: any) => fn(db);
  const jwt = new JwtService({ JWT_SECRET: 'supply-payment-test-secret', JWT_EXPIRES_IN: '1h' } as any);
  const app = express(); app.use(express.json());
  app.use('/api/v1', formalisationRoutes(jwt, { findById: vi.fn(async () => ({ accountStatus: 'ACTIVE' })) } as any, db, 'test-signing-secret'));
  app.use(errorHandler);
  return { app, db, jwt, handover };
}

describe('Recycler to Kabadiwala payment journey', () => {
  it('keeps completed receipts visible, records payment, then requires separate collector confirmation', async () => {
    const { app, db, jwt } = fixture();
    const recycler = { Authorization: `Bearer ${jwt.generateRecyclerToken('recycler-1')}` };
    const collector = { Authorization: `Bearer ${jwt.generateToken('collector-1')}` };
    const before = await request(app).get('/api/v1/recycler/supply-handovers').set(recycler);
    expect(before.status).toBe(200); expect(before.body.data[0]).toMatchObject({ status: 'COMPLETED', payments: [] });
    expect(db.supplyHandover.findMany.mock.calls[0][0].where.status.in).toContain('COMPLETED');
    const record = await request(app).post('/api/v1/recycler/handovers/handover-1/payment').set(recycler).send({ amount: 300, method: 'CASH' });
    expect(record.status).toBe(201); expect(record.body.data.status).toBe('RECORDED');
    const visible = await request(app).get('/api/v1/kabadiwala/handovers').set(collector);
    expect(visible.body.data[0].payments[0]).toMatchObject({ amount: 300, status: 'RECORDED', collectorId: 'collector-1' });
    const confirm = await request(app).post('/api/v1/kabadiwala/handovers/handover-1/payment-confirm').set(collector).send({ decision: 'ACCEPT' });
    expect(confirm.status).toBe(200); expect(confirm.body.data.status).toBe('VERIFIED');
    const replay = await request(app).post('/api/v1/kabadiwala/handovers/handover-1/payment-confirm').set(collector).send({ decision: 'ACCEPT' });
    expect(replay.status).toBe(200); expect(db.supplyPayment.create).toHaveBeenCalledTimes(1);
    expect(db.notificationEvent.create).toHaveBeenCalledWith(expect.objectContaining({ data: expect.objectContaining({ accountId: 'collector-1', type: 'SUPPLY_PAYMENT_RECORDED' }) }));
    expect(db.notificationEvent.create).toHaveBeenCalledWith(expect.objectContaining({ data: expect.objectContaining({ accountId: 'recycler-1', type: 'SUPPLY_PAYMENT_CONFIRMED' }) }));
  });
  it('records signed material receipt, closes accepted offers and chat, then notifies only the collector', async () => {
    const { app, db, jwt, handover } = fixture();
    const expiry = new Date(Date.now() + 3600000);
    const nonce = 'test-receipt-nonce';
    const qr = createSupplyHandoverQr({ version: 1, referenceId: 'REF-1', recyclerId: 'recycler-1', nonce, materialCategory: 'OTHER', weightKg: 0.5, issuedAt: new Date().toISOString(), expiresAt: expiry.toISOString() }, 'test-signing-secret');
    Object.assign(handover, { status: 'COLLECTOR_CONFIRMED', referenceId: 'REF-1', qrCodeData: qr.data, qrNonceHash: createHash('sha256').update(nonce).digest('hex'), expiresAt: expiry, quotedRatePerKg: 600, handoverLocation: {} });
    db.supplyHandover.updateMany = vi.fn(async ({ data }: any) => { Object.assign(handover, data); return { count: 1 }; });
    db.supplyHandover.findUniqueOrThrow = vi.fn(async () => handover);
    db.settlementBreakdown = { create: vi.fn(async () => ({})) };
    db.bulkLot = { findUnique: vi.fn(async () => ({ id: 'lot-1', kabadiwalaId: 'collector-1', quantityKg: 0.5, materialCategory: 'OTHER', grade: 'UNSPECIFIED' })), update: vi.fn(async () => ({})) };
    db.bulkOffer = { updateMany: vi.fn(async () => ({ count: 1 })) };
    db.conversation = { updateMany: vi.fn(async () => ({ count: 1 })) };
    const balance: any = { id: 'inventory-1', availableKg: 0, reservedKg: 0.5, soldKg: 0 };
    db.inventoryBalance = { findUniqueOrThrow: vi.fn(async () => ({ ...balance })), updateMany: vi.fn(async () => { balance.reservedKg = 0; balance.soldKg = 0.5; return { count: 1 }; }) };
    db.collector = { findUnique: vi.fn(async () => null) };
    db.safetyProgress = { count: vi.fn(async () => 0) };
    db.pickupRequest = { findMany: vi.fn(async () => []) };
    db.dispute = { count: vi.fn(async () => 0) };
    db.recyclerReview = { findMany: vi.fn(async () => []) };
    db.inventoryMovement = { create: vi.fn(async () => ({})) };
    const receipt = await request(app).post('/api/v1/recycler/handovers/confirm').set('Authorization', `Bearer ${jwt.generateRecyclerToken('recycler-1')}`).send({ qrCodeData: qr.data, actualWeightKg: 0.5, materialMatch: true });
    expect(receipt.status).toBe(200); expect(receipt.body.data.status).toBe('COMPLETED');
    expect(db.bulkOffer.updateMany).toHaveBeenCalledWith({ where: { bulkLotId: 'lot-1', status: 'ACCEPTED' }, data: { status: 'COMPLETED' } });
    expect(db.conversation.updateMany).toHaveBeenCalledWith({ where: { lotId: 'lot-1' }, data: { status: 'CLOSED' } });
    expect(db.notificationEvent.create).toHaveBeenCalledWith(expect.objectContaining({ data: expect.objectContaining({ accountId: 'collector-1', type: 'SUPPLY_HANDOVER_RECEIVED' }) }));
    expect(db.supplyPayment.create).not.toHaveBeenCalled();
  });
  it('blocks an unrelated collector or recycler and overpayment', async () => {
    const { app, db, jwt } = fixture();
    expect((await request(app).post('/api/v1/recycler/handovers/handover-1/payment').set('Authorization', `Bearer ${jwt.generateRecyclerToken('recycler-2')}`).send({ amount: 300, method: 'CASH' })).status).toBe(404);
    expect((await request(app).post('/api/v1/recycler/handovers/handover-1/payment').set('Authorization', `Bearer ${jwt.generateRecyclerToken('recycler-1')}`).send({ amount: 301, method: 'CASH' })).status).toBe(422);
    expect((await request(app).post('/api/v1/kabadiwala/handovers/handover-1/payment-confirm').set('Authorization', `Bearer ${jwt.generateToken('collector-2')}`).send({ decision: 'ACCEPT' })).status).toBe(404);
    expect(db.supplyPayment.create).not.toHaveBeenCalled();
  });
  it('requires a reason for non-receipt and preserves the disputed payment', async () => {
    const { app, jwt } = fixture();
    await request(app).post('/api/v1/recycler/handovers/handover-1/payment').set('Authorization', `Bearer ${jwt.generateRecyclerToken('recycler-1')}`).send({ amount: 300, method: 'CASH' });
    const auth = { Authorization: `Bearer ${jwt.generateToken('collector-1')}` };
    expect((await request(app).post('/api/v1/kabadiwala/handovers/handover-1/payment-confirm').set(auth).send({ decision: 'RAISE_ISSUE' })).status).toBe(422);
    const issue = await request(app).post('/api/v1/kabadiwala/handovers/handover-1/payment-confirm').set(auth).send({ decision: 'RAISE_ISSUE', reasonCode: 'Money not received' });
    expect(issue.status).toBe(200); expect(issue.body.data.status).toBe('DISPUTED');
  });
});
