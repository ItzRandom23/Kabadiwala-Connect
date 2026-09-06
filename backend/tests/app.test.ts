import { describe, expect, it } from 'vitest';
import request from 'supertest';
import { createApp } from '../src/app.js';
import { JwtService } from '../src/services/jwt.js';
import { CollectorService } from '../src/services/collectorService.js';

const config = {
  NODE_ENV: 'test' as const,
  PORT: 4000,
  DATABASE_URL: 'mongodb://test',
  JWT_SECRET: 'a-secure-test-secret',
  JWT_EXPIRES_IN: '1h',
  CORS_ORIGIN: '*',
  APP_VERSION: '1.0.0',
  OTP_PROVIDER: 'development' as const,
  DEV_OTP_CODE: '123456',
  STORAGE_PROVIDER: 'local' as const,
  LOCAL_UPLOAD_DIR: 'uploads',
  LOCAL_UPLOAD_BASE_URL: '',
  LOCAL_UPLOAD_PUBLIC: true,
  S3_REGION: 'ap-south-1'
};
const db = { $runCommandRaw: async () => ({ ok: 1 }) } as never;
const service = { getMe: async (id: string) => ({ id, phone: '9876543210', preferredLanguage: 'HINDI', areaName: 'Test', accountStatus: 'ACTIVE' }) } as unknown as CollectorService;
const app = createApp(config, db, new JwtService(config), service);

describe('app', () => {
  it('returns health and handles 404', async () => {
    const health = await request(app).get('/api/v1/health');
    expect(health.status).toBe(200);
    expect(health.body.data.status).toBe('healthy');
    const missing = await request(app).get('/missing');
    expect(missing.status).toBe(404);
    expect(missing.body.error.code).toBe('NOT_FOUND');
  });
});
