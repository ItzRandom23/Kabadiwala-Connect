import { describe, expect, it, vi } from 'vitest';
import express from 'express';
import request from 'supertest';
import { JwtService } from '../src/services/jwt.js';
import { supplyChainRoutes } from '../src/routes/supplyChainRoutes.js';
import { errorHandler } from '../src/middleware/errors.js';

const config = { JWT_SECRET: 'a-secure-test-secret', JWT_EXPIRES_IN: '1h' } as never;

describe('operator-reconciled household pickup payments', () => {
  it.each(['missing', 'null'])('lets the household confirm a payment with a %s receipt timestamp', async (timestamp) => {
    const payment = { id: 'payment-1', pickupId: 'pickup-1', householdId: 'household-1', collectorId: 'collector-1', amount: 250, status: 'RECORDED', ...(timestamp === 'null' ? { householdReceivedAt: null } : {}) };
    // Model Mongo's distinction: a null equality does not match a missing
    // optional field. An isSet:false alternative is needed for older records.
    const updateMany = vi.fn(async ({ where }: any) => ({ count: where.OR?.some((condition: any) =>
      timestamp === 'missing' ? condition.householdReceivedAt?.isSet === false : condition.householdReceivedAt === null
    ) ? 1 : 0 }));
    const db = {
      user: { findFirst: vi.fn().mockResolvedValue({ role: 'HOUSEHOLD', accountStatus: 'ACTIVE' }) },
      pickupSettlementPayment: { findUnique: vi.fn().mockResolvedValue(payment), findUniqueOrThrow: vi.fn().mockResolvedValue({ ...payment, householdReceivedAt: new Date() }), updateMany },
      pickupRequest: { findFirst: vi.fn().mockResolvedValue({ id: 'pickup-1', householdId: 'household-1', finalAmount: 250, status: 'COMPLETED', settlementStatus: 'ACCEPTED' }) }
    } as any;
    const jwt = new JwtService(config);
    const app = express();
    app.use(express.json());
    app.use('/api/v1', supplyChainRoutes(jwt, { findById: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE' }) } as never, db));
    app.use(errorHandler);
    const response = await request(app).post('/api/v1/household/pickups/pickup-1/payment-received').set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-1')}`);
    expect(response.status).toBe(200);
    expect(updateMany).toHaveBeenCalledWith(expect.objectContaining({ where: expect.objectContaining({ householdId: 'household-1', OR: [{ householdReceivedAt: null }, { householdReceivedAt: { isSet: false } }] }) }));
  });
  it.each(['RECORDED', 'VERIFIED'])('returns an existing receipt on a retry after payment is %s without changing its timestamp', async (status) => {
    const payment = { id: 'payment-1', pickupId: 'pickup-1', householdId: 'household-1', status, householdReceivedAt: new Date('2026-09-29T12:00:00Z') };
    const updateMany = vi.fn();
    const db = {
      user: { findFirst: vi.fn().mockResolvedValue({ role: 'HOUSEHOLD', accountStatus: 'ACTIVE' }) },
      pickupSettlementPayment: { findUnique: vi.fn().mockResolvedValue(payment), updateMany }
    } as any;
    const jwt = new JwtService(config);
    const app = express();
    app.use(express.json());
    app.use('/api/v1', supplyChainRoutes(jwt, { findById: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE' }) } as never, db));
    app.use(errorHandler);
    const response = await request(app).post('/api/v1/household/pickups/pickup-1/payment-received').set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-1')}`);
    expect(response.status).toBe(200);
    expect(response.body.data.householdReceivedAt).toBe(payment.householdReceivedAt.toISOString());
    expect(updateMany).not.toHaveBeenCalled();
  });
  it.each([true, false])('handles a lost confirmation race without falsely reporting confirmation (confirmed=%s)', async (confirmed) => {
    const payment = { id: 'payment-1', pickupId: 'pickup-1', householdId: 'household-1', collectorId: 'collector-1', amount: 250, status: 'RECORDED' };
    const db = {
      user: { findFirst: vi.fn().mockResolvedValue({ role: 'HOUSEHOLD', accountStatus: 'ACTIVE' }) },
      pickupSettlementPayment: {
        findUnique: vi.fn().mockResolvedValueOnce(payment).mockResolvedValueOnce({ ...payment, ...(confirmed ? { householdReceivedAt: new Date() } : { status: 'DISPUTED' }) }),
        updateMany: vi.fn().mockResolvedValue({ count: 0 })
      },
      pickupRequest: { findFirst: vi.fn().mockResolvedValue({ finalAmount: 250 }) }
    } as any;
    const jwt = new JwtService(config);
    const app = express();
    app.use(express.json());
    app.use('/api/v1', supplyChainRoutes(jwt, { findById: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE' }) } as never, db));
    app.use(errorHandler);
    const response = await request(app).post('/api/v1/household/pickups/pickup-1/payment-received').set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-1')}`);
    expect(response.status).toBe(confirmed ? 200 : 409);
    if (!confirmed) expect(response.body.error.details.code).toBe('PICKUP_PAYMENT_CONFIRMATION_CONFLICT');
  });
  it('does not let another household confirm or read an existing receipt', async () => {
    const updateMany = vi.fn();
    const db = {
      user: { findFirst: vi.fn().mockResolvedValue({ role: 'HOUSEHOLD', accountStatus: 'ACTIVE' }) },
      pickupSettlementPayment: { findUnique: vi.fn().mockResolvedValue({ householdId: 'other-household', householdReceivedAt: new Date(), status: 'RECORDED' }), updateMany }
    } as any;
    const jwt = new JwtService(config);
    const app = express();
    app.use(express.json());
    app.use('/api/v1', supplyChainRoutes(jwt, { findById: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE' }) } as never, db));
    app.use(errorHandler);
    const response = await request(app).post('/api/v1/household/pickups/pickup-1/payment-received').set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-1')}`);
    expect(response.status).toBe(409);
    expect(updateMany).not.toHaveBeenCalled();
  });
  it('records an external UPI payment as pending operator reconciliation', async () => {
    const paymentCreate = vi.fn().mockResolvedValue({ id: 'payment-1', pickupId: 'pickup-1', amount: 250, paymentMethod: 'UPI', status: 'RECORDED' });
    const pickupUpdate = vi.fn().mockResolvedValue({ count: 1 });
    const db = {
      user: { findFirst: vi.fn().mockResolvedValue({ role: 'COLLECTOR', accountStatus: 'ACTIVE' }) },
      pickupRequest: { findFirst: vi.fn().mockResolvedValue({ id: 'pickup-1', householdId: 'household-1', finalAmount: 250, status: 'COMPLETED', settlementStatus: 'ACCEPTED' }) },
      $transaction: vi.fn(async (callback: (tx: any) => unknown) => callback({
        idempotencyRecord: { findUnique: vi.fn(), create: vi.fn() },
        pickupSettlementPayment: { findUnique: vi.fn().mockResolvedValue(null), create: paymentCreate },
        pickupRequest: {
          findFirst: vi.fn().mockResolvedValue({ id: 'pickup-1', householdId: 'household-1', finalAmount: 250, status: 'COMPLETED', settlementStatus: 'ACCEPTED' }),
          updateMany: pickupUpdate
        },
        anomalyFlag: { create: vi.fn() },
        auditEvent: { create: vi.fn().mockResolvedValue({}) },
        materialPassportEvent: { create: vi.fn().mockResolvedValue({}) }
      }))
    } as any;
    const jwt = new JwtService(config);
    const app = express();
    app.use(express.json());
    app.use('/api/v1', supplyChainRoutes(jwt, { findById: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE' }) } as never, db));
    app.use(errorHandler);

    const response = await request(app)
      .post('/api/v1/kabadiwala/pickups/pickup-1/settlement-payment')
      .set('Authorization', `Bearer ${jwt.generateToken('collector-1')}`)
      .send({ amount: 250, method: 'UPI', reference: 'upi-reference-1' });

    expect(response.status).toBe(201);
    expect(paymentCreate).toHaveBeenCalledWith(expect.objectContaining({ data: expect.objectContaining({ paymentMethod: 'UPI', reference: 'upi-reference-1', status: 'RECORDED', householdReceivedAt: null }) }));
    expect(pickupUpdate).toHaveBeenCalledWith(expect.objectContaining({ data: { settlementStatus: 'ACCEPTED' } }));
  });

  it.each([
    { settlementStatus: 'DISPUTED', amount: 200 },
    { settlementStatus: 'DISPUTED', amount: 250 },
    { settlementStatus: 'COMPLETED', amount: 200 },
    { settlementStatus: 'COMPLETED', amount: 250 }
  ])('rejects payment for a $settlementStatus settlement with amount $amount', async ({ settlementStatus, amount }) => {
    const paymentCreate = vi.fn();
    const db = {
      user: { findFirst: vi.fn().mockResolvedValue({ role: 'COLLECTOR', accountStatus: 'ACTIVE' }) },
      pickupRequest: { findFirst: vi.fn().mockResolvedValue({ id: 'pickup-1', householdId: 'household-1', finalAmount: 250, status: 'COMPLETED', settlementStatus }) },
      $transaction: vi.fn(async (callback: (tx: any) => unknown) => callback({
        idempotencyRecord: { findUnique: vi.fn().mockResolvedValue(null), create: vi.fn() },
        pickupSettlementPayment: { findUnique: vi.fn().mockResolvedValue(null), create: paymentCreate },
        pickupRequest: {
          findFirst: vi.fn().mockResolvedValue(null),
          updateMany: vi.fn().mockResolvedValue({ count: 0 })
        },
        anomalyFlag: { create: vi.fn() },
        auditEvent: { create: vi.fn().mockResolvedValue({}) },
        materialPassportEvent: { create: vi.fn().mockResolvedValue({}) }
      }))
    } as any;
    const jwt = new JwtService(config);
    const app = express();
    app.use(express.json());
    app.use('/api/v1', supplyChainRoutes(jwt, { findById: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE' }) } as never, db));
    app.use(errorHandler);

    const response = await request(app)
      .post('/api/v1/kabadiwala/pickups/pickup-1/settlement-payment')
      .set('Authorization', `Bearer ${jwt.generateToken('collector-1')}`)
      .send({ amount, method: 'CASH' });

    expect(response.status).toBe(409);
    expect(response.body.error.message).toMatch(/must accept.*before payment/i);
    expect(paymentCreate).not.toHaveBeenCalled();
  });

  it('lets only a payment-verification operator reconcile a recorded pickup payment', async () => {
    const updatePayment = vi.fn().mockResolvedValue({ count: 1 });
    const updatePickup = vi.fn().mockResolvedValue({ count: 1 });
    const db = {
      adminAccount: { findUnique: vi.fn().mockResolvedValue({ active: true, permissions: ['PAYMENT_VERIFICATION'] }) },
      $transaction: vi.fn(async (callback: (tx: any) => unknown) => callback({
        pickupSettlementPayment: {
          findUnique: vi.fn().mockResolvedValue({ id: 'payment-1', pickupId: 'pickup-1', householdId: 'household-1', collectorId: 'collector-1', status: 'RECORDED', householdReceivedAt: new Date() }),
          updateMany: updatePayment,
          findUniqueOrThrow: vi.fn().mockResolvedValue({ id: 'payment-1', pickupId: 'pickup-1', householdId: 'household-1', collectorId: 'collector-1', status: 'VERIFIED' })
        },
        pickupRequest: { updateMany: updatePickup },
        anomalyFlag: { create: vi.fn() },
        auditEvent: { create: vi.fn().mockResolvedValue({}) },
        materialPassportEvent: { create: vi.fn().mockResolvedValue({}) }
      }))
    } as any;
    const jwt = new JwtService(config);
    const app = express();
    app.use(express.json());
    app.use('/api/v1', supplyChainRoutes(jwt, {} as never, db));
    app.use(errorHandler);

    const response = await request(app)
      .post('/api/v1/admin/household-pickup-payments/payment-1/reconcile')
      .set('Authorization', `Bearer ${jwt.generateAdminToken('operator-1')}`)
      .send({ decision: 'VERIFY' });

    expect(response.status).toBe(200);
    expect(response.body.data).toMatchObject({ id: 'payment-1', status: 'VERIFIED', kind: 'HOUSEHOLD_PICKUP_SETTLEMENT' });
    expect(updatePayment).toHaveBeenCalledWith(expect.objectContaining({ where: { id: 'payment-1', status: 'RECORDED' }, data: expect.objectContaining({ status: 'VERIFIED' }) }));
    expect(updatePickup).toHaveBeenCalledWith(expect.objectContaining({ data: { settlementStatus: 'COMPLETED' } }));
  });

  it('requires an explanation when an operator flags a payment mismatch', async () => {
    const db = { adminAccount: { findUnique: vi.fn().mockResolvedValue({ active: true, permissions: ['PAYMENT_VERIFICATION'] }) } } as any;
    const jwt = new JwtService(config);
    const app = express();
    app.use(express.json());
    app.use('/api/v1', supplyChainRoutes(jwt, {} as never, db));
    app.use(errorHandler);

    const response = await request(app)
      .post('/api/v1/admin/household-pickup-payments/payment-1/reconcile')
      .set('Authorization', `Bearer ${jwt.generateAdminToken('operator-1')}`)
      .send({ decision: 'DISPUTE' });

    expect(response.status).toBe(422);
    expect(response.body.error.code).toBe('VALIDATION_ERROR');
  });
});
