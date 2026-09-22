import express from 'express';
import request from 'supertest';
import sharp from 'sharp';
import { describe, expect, it, vi } from 'vitest';
import { JwtService } from '../src/services/jwt.js';
import { supplyChainRoutes } from '../src/routes/supplyChainRoutes.js';

const config = { JWT_SECRET: 'supply-chain-photo-test-secret', JWT_EXPIRES_IN: '1h' } as any;
const jwt = new JwtService(config);

function photoApp(overrides: { listing?: any; storage?: any; role?: 'HOUSEHOLD' | 'COLLECTOR'; pickup?: any } = {}) {
  const role = overrides.role ?? 'HOUSEHOLD';
  const accountId = role === 'COLLECTOR' ? 'collector-1' : 'household-1';
  const listing = overrides.listing ?? { id: 'listing-1', householdId: 'household-1', status: 'POSTED', photoReference: null };
  const storage = overrides.storage ?? {
    putImage: vi.fn().mockResolvedValue({ key: 'household-listings/household-1/listing-1.jpg', url: 'private-key' }),
    getImage: vi.fn(),
    delete: vi.fn().mockResolvedValue(undefined)
  };
  const tx = {
    householdListing: {
      create: vi.fn(({ data }: { data: any }) => ({ id: 'listing-created', ...data })),
      updateMany: vi.fn().mockResolvedValue({ count: 1 }),
      findUnique: vi.fn().mockResolvedValue({ ...listing, photoReference: 'household-listings/household-1/listing-1.jpg' })
    },
    auditEvent: { create: vi.fn().mockResolvedValue(undefined) },
    materialPassportEvent: { create: vi.fn().mockResolvedValue(undefined) }
  };
  const db = {
    user: { findFirst: vi.fn().mockResolvedValue({ role, accountStatus: 'ACTIVE' }) },
    pickupRequest: { findFirst: vi.fn().mockResolvedValue('pickup' in overrides ? overrides.pickup : { id: 'pickup-1' }) },
    householdListing: { findFirst: vi.fn().mockResolvedValue(listing), findUnique: vi.fn().mockResolvedValue(listing) },
    $transaction: vi.fn(async (callback: (transaction: any) => unknown) => callback(tx))
  } as any;
  const collectors = { findById: vi.fn().mockResolvedValue({ id: accountId, accountStatus: 'ACTIVE' }) } as any;
  const app = express();
  app.use(express.json());
  app.use('/api/v1', supplyChainRoutes(jwt, collectors, db, storage));
  app.use((error: any, _req: any, res: any, _next: any) => res.status(error.status ?? 500).json({ code: error.code, details: error.details }));
  return { app, storage, tx };
}

describe('household listing photo contract', () => {
  it('keeps a JSON-only listing as a draft until a photo is uploaded', async () => {
    const { app, tx } = photoApp();
    const response = await request(app)
      .post('/api/v1/household/listings')
      .set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-1')}`)
      .send({
        materialCategory: 'PLASTIC',
        estimatedWeight: 4,
        condition: 'INTACT',
        areaName: 'Pune'
      });

    expect(response.status).toBe(201);
    expect(response.body.data.status).toBe('DRAFT');
    expect(tx.householdListing.create).toHaveBeenCalledWith(expect.objectContaining({
      data: expect.objectContaining({ status: 'DRAFT', photoReference: null, photoReferences: [] })
    }));
  });

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
        ],
        status: 'POSTED'
      }
    }));
    expect(tx.auditEvent.create).toHaveBeenCalledWith(expect.objectContaining({
      data: expect.objectContaining({ metadata: expect.objectContaining({ photoCount: 2 }) })
    }));
  });

  it('serves an indexed private photo to the listing owner without exposing the storage key', async () => {
    const storage = {
      putImage: vi.fn(),
      getImage: vi.fn().mockResolvedValue({ body: Buffer.from('side-photo'), contentType: 'image/jpeg' }),
      delete: vi.fn().mockResolvedValue(undefined)
    };
    const { app } = photoApp({
      storage,
      listing: {
        id: 'listing-1',
        householdId: 'household-1',
        status: 'POSTED',
        photoReference: 'household-listings/household-1/listing-1.jpg',
        photoReferences: [
          'household-listings/household-1/listing-1.jpg',
          'household-listings/household-1/listing-1-1.jpg'
        ]
      }
    });

    const response = await request(app)
      .get('/api/v1/household/listings/listing-1/photo/1')
      .set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-1')}`);

    expect(response.status).toBe(200);
    expect(response.headers['content-type']).toContain('image/jpeg');
    expect(response.body.toString()).toBe('side-photo');
    expect(storage.getImage).toHaveBeenCalledWith('household-listings/household-1/listing-1-1.jpg');
  });

  it('does not turn an out-of-range photo index into a storage lookup', async () => {
    const storage = {
      putImage: vi.fn(),
      getImage: vi.fn(),
      delete: vi.fn().mockResolvedValue(undefined)
    };
    const { app } = photoApp({
      storage,
      listing: {
        id: 'listing-1',
        householdId: 'household-1',
        status: 'POSTED',
        photoReference: 'household-listings/household-1/listing-1.jpg',
        photoReferences: ['household-listings/household-1/listing-1.jpg']
      }
    });

    const response = await request(app)
      .get('/api/v1/household/listings/listing-1/photo/2')
      .set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-1')}`);

    expect(response.status).toBe(404);
    expect(response.body.details.code).toBe('PHOTO_NOT_FOUND');
    expect(storage.getImage).not.toHaveBeenCalled();
  });

  it('serves indexed photos to an assigned Kabadiwala only', async () => {
    const storage = {
      putImage: vi.fn(),
      getImage: vi.fn().mockResolvedValue({ body: Buffer.from('collector-side-photo'), contentType: 'image/jpeg' }),
      delete: vi.fn().mockResolvedValue(undefined)
    };
    const listing = {
      id: 'listing-1',
      householdId: 'household-1',
      status: 'MATCHED',
      photoReference: 'household-listings/household-1/listing-1.jpg',
      photoReferences: [
        'household-listings/household-1/listing-1.jpg',
        'household-listings/household-1/listing-1-1.jpg'
      ]
    };
    const { app } = photoApp({ storage, listing, role: 'COLLECTOR' });

    const allowed = await request(app)
      .get('/api/v1/kabadiwala/listings/listing-1/photo/1')
      .set('Authorization', `Bearer ${jwt.generateToken('collector-1')}`);

    expect(allowed.status).toBe(200);
    expect(allowed.body.toString()).toBe('collector-side-photo');
    expect(storage.getImage).toHaveBeenCalledWith('household-listings/household-1/listing-1-1.jpg');
  });

  it('does not serve indexed photos to an unassigned Kabadiwala', async () => {
    const storage = {
      putImage: vi.fn(),
      getImage: vi.fn(),
      delete: vi.fn().mockResolvedValue(undefined)
    };
    const { app } = photoApp({ storage, role: 'COLLECTOR', pickup: null });

    const response = await request(app)
      .get('/api/v1/kabadiwala/listings/listing-1/photo/1')
      .set('Authorization', `Bearer ${jwt.generateToken('collector-1')}`);

    expect(response.status).toBe(404);
    expect(response.body.details.code).toBe('PHOTO_NOT_FOUND');
    expect(storage.getImage).not.toHaveBeenCalled();
  });
});
