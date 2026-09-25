import { describe, expect, it, vi } from 'vitest';
import express from 'express';
import request from 'supertest';
import { JwtService } from '../src/services/jwt.js';
import { supplyChainRoutes } from '../src/routes/supplyChainRoutes.js';
import { errorHandler } from '../src/middleware/errors.js';

const config = { JWT_SECRET: 'a-secure-test-secret', JWT_EXPIRES_IN: '1h' } as never;

describe('collector listing discovery privacy', () => {
  it('returns only assigned listings and never exposes the private photo key', async () => {
    const findManyPickups = vi.fn().mockResolvedValue([
      { listingId: 'assigned-listing', status: 'REQUESTED' },
      { listingId: 'assigned-listing', status: 'ACCEPTED' }
    ]);
    const findManyListings = vi.fn().mockResolvedValue([{
      id: 'assigned-listing',
      materialCategory: 'COPPER',
      estimatedWeight: 4.5,
      condition: 'INTACT',
      notes: 'Sorted wire',
      photoReference: 'private/household/secret.jpg',
      areaName: 'Sector 12',
      estimatedPriceMin: null,
      estimatedPriceMax: null,
      status: 'MATCHED',
      createdAt: new Date('2026-09-18T00:00:00.000Z'),
      updatedAt: new Date('2026-09-18T00:00:00.000Z')
    }]);
    const db = {
      user: { findFirst: vi.fn().mockResolvedValue({ role: 'COLLECTOR', accountStatus: 'ACTIVE' }) },
      pickupRequest: { findMany: findManyPickups },
      householdListing: { findMany: findManyListings }
    } as never;
    const collectors = { findById: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE' }) } as never;
    const jwt = new JwtService(config);
    const app = express();
    app.use(express.json());
    app.use('/api/v1', supplyChainRoutes(jwt, collectors, db));
    app.use(errorHandler);

    const response = await request(app)
      .get('/api/v1/kabadiwala/listings')
      .set('Authorization', `Bearer ${jwt.generateToken('collector-1')}`);

    expect(response.status).toBe(200);
    expect(findManyPickups).toHaveBeenCalledWith({ where: { kabadiwalaId: 'collector-1' }, select: { listingId: true, status: true } });
    expect(findManyListings).toHaveBeenCalledWith(expect.objectContaining({
      where: { id: { in: ['assigned-listing'] } },
      select: expect.not.objectContaining({ photoReference: false })
    }));
    expect(response.body.data).toHaveLength(1);
    expect(response.body.data[0]).toMatchObject({ id: 'assigned-listing', photoAttached: true });
    expect(response.body.data[0]).not.toHaveProperty('photoReference');
    expect(JSON.stringify(response.body)).not.toContain('private/household/secret.jpg');
  });

  it('returns an empty discovery result when the collector has no pickup assignments', async () => {
    const findManyListings = vi.fn();
    const db = {
      user: { findFirst: vi.fn().mockResolvedValue({ role: 'COLLECTOR', accountStatus: 'ACTIVE' }) },
      pickupRequest: { findMany: vi.fn().mockResolvedValue([]) },
      householdListing: { findMany: findManyListings }
    } as never;
    const collectors = { findById: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE' }) } as never;
    const jwt = new JwtService(config);
    const app = express();
    app.use(express.json());
    app.use('/api/v1', supplyChainRoutes(jwt, collectors, db));
    app.use(errorHandler);

    const response = await request(app)
      .get('/api/v1/kabadiwala/listings')
      .set('Authorization', `Bearer ${jwt.generateToken('collector-2')}`);

    expect(response.status).toBe(200);
    expect(response.body.data).toEqual([]);
    expect(findManyListings).not.toHaveBeenCalled();
  });
});
