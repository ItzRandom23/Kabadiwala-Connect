import { describe, expect, it, vi } from 'vitest';
import express from 'express';
import request from 'supertest';
import { supplyChainRoutes } from '../src/routes/supplyChainRoutes.js';
import { JwtService } from '../src/services/jwt.js';
import { errorHandler } from '../src/middleware/errors.js';

const config = { JWT_SECRET: 'availability-test-secret', JWT_EXPIRES_IN: '1h' } as never;

describe('pickup availability confirmation', () => {
  it('rejects a negative confirmation without writing a confirmed timestamp', async () => {
    const pickupFindFirst = vi.fn();
    const transaction = vi.fn();
    const db = {
      user: { findFirst: vi.fn().mockResolvedValue({ role: 'COLLECTOR', accountStatus: 'ACTIVE' }) },
      pickupRequest: { findFirst: pickupFindFirst },
      $transaction: transaction
    } as never;
    const jwt = new JwtService(config);
    const app = express();
    app.use(express.json());
    app.use('/api/v1', supplyChainRoutes(jwt, { findById: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE' }) } as never, db));
    app.use(errorHandler);

    const response = await request(app)
      .post('/api/v1/kabadiwala/pickups/pickup-1/confirm-availability')
      .set('Authorization', `Bearer ${jwt.generateToken('collector-1')}`)
      .send({ availabilityConfirmed: false });

    expect(response.status).toBe(422);
    expect(response.body.error.code).toBe('VALIDATION_ERROR');
    expect(pickupFindFirst).not.toHaveBeenCalled();
    expect(transaction).not.toHaveBeenCalled();
  });

  it('rejects collector schedule slots less than five minutes after acceptance before reserving capacity', async () => {
    const slot = new Date();
    slot.setUTCDate(slot.getUTCDate() + 1);
    slot.setUTCHours(5, 30, 0, 0); // 11:00 India time
    const acceptedAt = new Date(slot.getTime() - 4 * 60 * 1000);
    const pickupFindFirst = vi.fn().mockResolvedValue({ acceptedAt });
    const transaction = vi.fn();
    const db = {
      user: { findFirst: vi.fn().mockResolvedValue({ role: 'COLLECTOR', accountStatus: 'ACTIVE' }) },
      pickupRequest: { findFirst: pickupFindFirst },
      $transaction: transaction
    } as never;
    const jwt = new JwtService(config);
    const app = express();
    app.use(express.json());
    app.use('/api/v1', supplyChainRoutes(jwt, { findById: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE' }) } as never, db));
    app.use(errorHandler);

    const response = await request(app)
      .post('/api/v1/kabadiwala/pickups/pickup-1/schedule')
      .set('Authorization', `Bearer ${jwt.generateToken('collector-1')}`)
      .send({ scheduledSlot: slot.toISOString() });

    expect(response.status).toBe(409);
    expect(response.body.error.code).toBe('CONFLICT');
    expect(response.body.error.details.code).toBe('PICKUP_SLOT_TOO_SOON');
    expect(pickupFindFirst).toHaveBeenCalledOnce();
    expect(transaction).not.toHaveBeenCalled();
  });
});
