import express from 'express';
import request from 'supertest';
import sharp from 'sharp';
import { describe, expect, it, vi } from 'vitest';
import { JwtService } from '../src/services/jwt.js';
import { supplyChainRoutes } from '../src/routes/supplyChainRoutes.js';

const config = { JWT_SECRET: 'supply-chain-photo-test-secret', JWT_EXPIRES_IN: '1h' } as any;
const jwt = new JwtService(config);

function photoApp(overrides: { listing?: any; storage?: any } = {}) {
  const listing = overrides.listing ?? { id: 'listing-1', householdId: 'household-1', status: 'POSTED', photoReference: null };
  const storage = overrides.storage ?? {
    putImage: vi.fn().mockResolvedValue({ key: 'household-listings/household-1/listing-1.jpg', url: 'private-key' }),
    getImage: vi.fn(),
    delete: vi.fn().mockResolvedValue(undefined)
  };
  const tx = {
    householdListing: {
      updateMany: vi.fn().mockResolvedValue({ count: 1 }),
      findUnique: vi.fn().mockResolvedValue({ ...listing, photoReference: 'household-listings/household-1/listing-1.jpg' })
    },
    auditEvent: { create: vi.fn().mockResolvedValue(undefined) },
    materialPassportEvent: { create: vi.fn().mockResolvedValue(undefined) }
  };
  const db = {
    user: { findFirst: vi.fn().mockResolvedValue({ role: 'HOUSEHOLD', accountStatus: 'ACTIVE' }) },
    householdListing: { findFirst: vi.fn().mockResolvedValue(listing) },
    $transaction: vi.fn(async (callback: (transaction: any) => unknown) => callback(tx))
  } as any;
  const collectors = { findById: vi.fn().mockResolvedValue({ id: 'household-1', accountStatus: 'ACTIVE' }) } as any;
  const app = express();
  app.use('/api/v1', supplyChainRoutes(jwt, collectors, db, storage));
  app.use((error: any, _req: any, res: any, _next: any) => res.status(error.status ?? 500).json({ code: error.code, details: error.details }));
  return { app, storage, tx };
}

describe('household listing photo contract', () => {
  it('rejects a collector token before touching the upload', async () => {
    const { app, storage } = photoApp();
    const response = await request(app)
      .post('/api/v1/household/listings/listing-1/photo')
      .set('Authorization', `Bearer ${jwt.generateToken('collector-1')}`)
      .attach('photo', Buffer.from('not an image'), { filename: 'listing.jpg', contentType: 'image/jpeg' });

    expect(response.status).toBe(403);
    expect(response.body.code).toBe('AUTHORIZATION_ERROR');
    expect(storage.putImage).not.toHaveBeenCalled();
  });

  it('rejects undersized images and accepts a validated upload with a stable key', async () => {
    const { app, storage, tx } = photoApp();
    const smallPhoto = await sharp({ create: { width: 64, height: 64, channels: 3, background: 'red' } }).png().toBuffer();
    const rejected = await request(app)
      .post('/api/v1/household/listings/listing-1/photo')
      .set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-1')}`)
      .attach('photo', smallPhoto, { filename: 'listing.png', contentType: 'image/png' });
    expect(rejected.status).toBe(400);
    expect(rejected.body.details.code).toBe('INVALID_PHOTO');

    const validPhoto = await sharp({ create: { width: 300, height: 300, channels: 3, background: 'green' } }).png().toBuffer();
    const accepted = await request(app)
      .post('/api/v1/household/listings/listing-1/photo')
      .set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-1')}`)
      .attach('photo', validPhoto, { filename: 'listing.png', contentType: 'image/png' });

    expect(accepted.status).toBe(200);
    expect(storage.putImage).toHaveBeenCalledWith(validPhoto, 'household-listings/household-1/listing-1.jpg');
    expect(tx.auditEvent.create).toHaveBeenCalled();
    expect(tx.materialPassportEvent.create).toHaveBeenCalled();
    expect(accepted.body.data).toMatchObject({ photoAttached: true, photoCount: 1 });
    expect(accepted.body.data).not.toHaveProperty('photoReference');
  });

  it('accepts multiple angle photos and persists stable indexed references', async () => {
    const storage = {
      putImage: vi.fn(async (_buffer: Buffer, key: string) => ({ key, url: key })),
      getImage: vi.fn(),
      delete: vi.fn().mockResolvedValue(undefined)
    };
    const { app, tx } = photoApp({ storage });
    const frontPhoto = await sharp({ create: { width: 400, height: 300, channels: 3, background: 'red' } }).jpeg().toBuffer();
    const sidePhoto = await sharp({ create: { width: 300, height: 400, channels: 3, background: 'blue' } }).png().toBuffer();

    const accepted = await request(app)
      .post('/api/v1/household/listings/listing-1/photo')
      .set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-1')}`)
      .attach('photos', frontPhoto, { filename: 'front.jpg', contentType: 'image/jpeg' })
      .attach('photos', sidePhoto, { filename: 'side.png', contentType: 'image/png' });

    expect(accepted.status).toBe(200);
    expect(storage.putImage).toHaveBeenCalledTimes(2);
    expect(storage.putImage.mock.calls.map(([, key]) => key)).toEqual([
      'household-listings/household-1/listing-1.jpg',
      'household-listings/household-1/listing-1-1.jpg'
    ]);
    expect(tx.householdListing.updateMany).toHaveBeenCalledWith(expect.objectContaining({
      data: {
        photoReference: 'household-listings/household-1/listing-1.jpg',
        photoReferences: [
          'household-listings/household-1/listing-1.jpg',
          'household-listings/household-1/listing-1-1.jpg'
        ]
      }
    }));
    expect(tx.auditEvent.create).toHaveBeenCalledWith(expect.objectContaining({
      data: expect.objectContaining({ metadata: expect.objectContaining({ photoCount: 2 }) })
    }));
  });
});
