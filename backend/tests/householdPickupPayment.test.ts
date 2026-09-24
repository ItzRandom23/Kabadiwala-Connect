import { describe, expect, it, vi } from 'vitest';
import express from 'express';
import request from 'supertest';
import { JwtService } from '../src/services/jwt.js';
import { supplyChainRoutes } from '../src/routes/supplyChainRoutes.js';
import { errorHandler } from '../src/middleware/errors.js';

const config = { JWT_SECRET: 'a-secure-test-secret', JWT_EXPIRES_IN: '1h' } as never;

describe('operator-reconciled household pickup payments', () => {
  it('records an external UPI payment as pending operator reconciliation', async () => {
    const paymentCreate = vi.fn().mockResolvedValue({ id: 'payment-1', pickupId: 'pickup-1', amount: 250, paymentMethod: 'UPI', status: 'RECORDED' });
    const pickupUpdate = vi.fn().mockResolvedValue({ count: 1 });
    const db = {
      user: { findFirst: vi.fn().mockResolvedValue({ role: 'COLLECTOR', accountStatus: 'ACTIVE' }) },
      pickupRequest: { findFirst: vi.fn().mockResolvedValue({ id: 'pickup-1', householdId: 'household-1', finalAmount: 250, status: 'COMPLETED', settlementStatus: 'ACCEPTED' }) },
      $transaction: vi.fn(async (callback: (tx: any) => unknown) => callback({
        idempotencyRecord: { findUnique: vi.fn(), create: vi.fn() },
        pickupSettlementPayment: { findUnique: vi.fn().mockResolvedValue(null), create: paymentCreate },
        pickupRequest: { updateMany: pickupUpdate },
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
    expect(paymentCreate).toHaveBeenCalledWith(expect.objectContaining({ data: expect.objectContaining({ paymentMethod: 'UPI', reference: 'upi-reference-1', status: 'RECORDED' }) }));
    expect(pickupUpdate).toHaveBeenCalledWith(expect.objectContaining({ data: { settlementStatus: 'ACCEPTED' } }));
  });

  it('lets only a payment-verification operator reconcile a recorded pickup payment', async () => {
    const updatePayment = vi.fn().mockResolvedValue({ count: 1 });
    const updatePickup = vi.fn().mockResolvedValue({ count: 1 });
    const db = {
      adminAccount: { findUnique: vi.fn().mockResolvedValue({ active: true, permissions: ['PAYMENT_VERIFICATION'] }) },
      $transaction: vi.fn(async (callback: (tx: any) => unknown) => callback({
        pickupSettlementPayment: {
          findUnique: vi.fn().mockResolvedValue({ id: 'payment-1', pickupId: 'pickup-1', householdId: 'household-1', collectorId: 'collector-1', status: 'RECORDED' }),
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
