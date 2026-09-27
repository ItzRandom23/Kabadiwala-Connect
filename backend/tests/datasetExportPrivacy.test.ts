import { afterEach, describe, expect, it, vi } from 'vitest';
import express from 'express';
import request from 'supertest';
import { datasetRoutes } from '../src/routes/datasetRoutes.js';
import { JwtService } from '../src/services/jwt.js';
import { errorHandler } from '../src/middleware/errors.js';

const config = { JWT_SECRET: 'dataset-export-test-secret', JWT_EXPIRES_IN: '1h' } as never;

afterEach(() => vi.unstubAllEnvs());

describe('dataset export privacy', () => {
  it('pseudonymizes inference and lot keys and exports only coarse input provenance', async () => {
    vi.stubEnv('DATASET_EXPORT_SALT', 'dataset-export-test-salt');
    const inference = {
      id: 'internal-inference-id',
      lotId: 'internal-lot-id',
      feature: 'LOT_DESCRIPTION',
      modelProvider: 'GOOGLE_GEMINI',
      modelVersion: 'test-model',
      inputProvenance: {
        imageSha256: 'private-image-hash',
        mimeType: 'image/jpeg',
        bytes: 12345,
        notes: 'private household note',
        language: 'en'
      },
      prediction: { text: 'Recovered copper wire', source: 'AI' },
      confidence: 0.91,
      humanCorrection: { text: 'private corrected description', actorId: 'internal-user-id' },
      correctedAt: new Date('2026-09-20T12:00:00.000Z'),
      consentForTraining: false,
      createdAt: new Date('2026-09-20T11:00:00.000Z')
    };
    const db = {
      adminAccount: { findUnique: vi.fn().mockResolvedValue({ active: true, permissions: ['DATASET_EXPORT'] }) },
      lot: { findMany: vi.fn().mockResolvedValue([]) },
      priceHistory: { findMany: vi.fn().mockResolvedValue([]) },
      recycler: { findMany: vi.fn().mockResolvedValue([]) },
      handover: { findMany: vi.fn().mockResolvedValue([]) },
      collector: { findMany: vi.fn().mockResolvedValue([]) },
      aiInference: { findMany: vi.fn().mockResolvedValue([inference]) }
    } as never;
    const jwt = new JwtService(config);
    const app = express();
    app.use('/api/v1', datasetRoutes(jwt, db));
    app.use(errorHandler);

    const response = await request(app)
      .get('/api/v1/admin/datasets/export')
      .set('Authorization', `Bearer ${jwt.generateAdminToken('admin-1')}`);

    expect(response.status).toBe(200);
    const exported = response.body.data.ai[0];
    expect(exported).toMatchObject({
      inferenceKey: expect.stringMatching(/^ai_[a-f0-9]{20}$/),
      lotKey: expect.stringMatching(/^lot_[a-f0-9]{20}$/),
      feature: 'LOT_DESCRIPTION',
      inputProvenance: { hasImageHash: true, mimeType: 'image/jpeg' }
    });
    expect(exported).not.toHaveProperty('id');
    expect(exported).not.toHaveProperty('lotId');
    expect(exported).not.toHaveProperty('humanCorrection');
    expect(exported.inputProvenance).not.toHaveProperty('imageSha256');
    expect(exported.inputProvenance).not.toHaveProperty('bytes');
    expect(exported.inputProvenance).not.toHaveProperty('notes');
    const json = JSON.stringify(response.body);
    for (const secret of ['internal-inference-id', 'internal-lot-id', 'private-image-hash', 'private household note', 'private corrected description', 'internal-user-id']) {
      expect(json).not.toContain(secret);
    }
  });
});
