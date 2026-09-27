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
});
