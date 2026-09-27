import express from 'express';
import request from 'supertest';
import { describe, expect, it, vi } from 'vitest';
import { datasetRoutes } from '../src/routes/datasetRoutes.js';
import { JwtService } from '../src/services/jwt.js';
import { errorHandler } from '../src/middleware/errors.js';

const config = { JWT_SECRET: 'dataset-import-test-secret', JWT_EXPIRES_IN: '1h' } as never;
const row = (externalId: string) => ({
  externalId, materialCategory: 'PCB', city: 'Pune', priceMin: 100, priceMax: 140, marketPrice: 120,
  sourceOrganization: 'Audit source', sourceReference: 'test row', effectiveAt: '2026-09-20T10:00:00.000Z'
});

function appFor(db: any, jwt: JwtService) {
  const app = express();
  app.use(express.json());
  app.use('/api/v1', datasetRoutes(jwt, db));
  app.use(errorHandler);
  return app;
}

describe('dataset price import authorization and atomicity', () => {
  it('does not let an export-only admin mutate the active price board', async () => {
    const transaction = vi.fn();
    const db = {
      adminAccount: { findUnique: vi.fn().mockResolvedValue({ active: true, permissions: ['DATASET_EXPORT'] }) },
      $transaction: transaction
    };
    const jwt = new JwtService(config);

    const response = await request(appFor(db, jwt))
      .post('/api/v1/admin/datasets/prices/import')
      .set('Authorization', `Bearer ${jwt.generateAdminToken('admin-1')}`)
      .send({ rows: [row('price-a')] });

    expect(response.status).toBe(403);
    expect(response.body.error.details.code).toBe('PRICE_MANAGEMENT');
    expect(transaction).not.toHaveBeenCalled();
  });

  it('runs all price and history writes in one transaction', async () => {
    const tx = {
      price: { upsert: vi.fn(async ({ where }: any) => ({
        id: where.id, materialCategory: 'PCB', city: 'Pune', areaName: null, priceMin: 100, priceMax: 140,
        marketPrice: 120, unit: 'KILOGRAM', source: 'ADMIN', sourceOrganization: 'Audit source',
        sourceReference: 'test row', ingestedAt: new Date(), qualityStatus: 'VALIDATED', effectiveAt: new Date()
      })) },
      priceHistory: { create: vi.fn().mockResolvedValue({}) }
    };
    const transaction = vi.fn(async (work: (client: typeof tx) => unknown) => work(tx));
    const db = {
      adminAccount: { findUnique: vi.fn().mockResolvedValue({ active: true, permissions: ['PRICE_MANAGEMENT'] }) },
      $transaction: transaction
    };
    const jwt = new JwtService(config);

    const response = await request(appFor(db, jwt))
      .post('/api/v1/admin/datasets/prices/import')
      .set('Authorization', `Bearer ${jwt.generateAdminToken('admin-1')}`)
      .send({ rows: [row('price-a'), row('price-b')] });

    expect(response.status).toBe(201);
    expect(response.body.data).toEqual({ imported: ['price-a', 'price-b'], count: 2 });
    expect(transaction).toHaveBeenCalledTimes(1);
    expect(tx.price.upsert).toHaveBeenCalledTimes(2);
    expect(tx.priceHistory.create).toHaveBeenCalledTimes(2);
  });

  it('propagates a later-row failure through the transaction boundary', async () => {
    let committedIds: string[] = [];
    const transaction = vi.fn(async (work: (client: any) => Promise<string[]>) => {
      const staged: string[] = [];
      const tx = {
        price: { upsert: vi.fn(async ({ where }: any) => {
          staged.push(where.id);
          return { id: where.id, materialCategory: 'PCB', city: 'Pune', areaName: null, priceMin: 100, priceMax: 140, marketPrice: 120, unit: 'KILOGRAM', source: 'ADMIN', sourceOrganization: 'Audit source', sourceReference: 'test row', ingestedAt: new Date(), qualityStatus: 'VALIDATED', effectiveAt: new Date() };
        }) },
        priceHistory: { create: vi.fn(async ({ data }: any) => {
          if (data.priceId === 'price-b') throw new Error('history write failed');
        }) }
      };
      try {
        const result = await work(tx);
        committedIds = [...staged];
        return result;
      } catch (error) {
        throw error;
      }
    });
    const db = {
      adminAccount: { findUnique: vi.fn().mockResolvedValue({ active: true, permissions: ['PRICE_MANAGEMENT'] }) },
      $transaction: transaction
    };
    const jwt = new JwtService(config);

    const response = await request(appFor(db, jwt))
      .post('/api/v1/admin/datasets/prices/import')
      .set('Authorization', `Bearer ${jwt.generateAdminToken('admin-1')}`)
      .send({ rows: [row('price-a'), row('price-b')] });

    expect(response.status).toBe(500);
    expect(committedIds).toEqual([]);
  });
});
