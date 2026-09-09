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

  it('treats blank optional phone-registration fields as omitted', async () => {
    let captured: any;
    const authService = {
      requestOtp: async () => 'OTP sent successfully',
      verifyOtp: async (_phone: string, _otp: string, _ip: string, account: any) => {
        captured = account;
        return { token: 'token', collector: null, user: { id: 'u1', role: 'COLLECTOR', profileId: 'c1' } };
      }
    };
    const authApp = createApp(config, db, new JwtService(config), service, authService as any);

    const response = await request(authApp).post('/api/v1/auth/verify-otp').send({
      phone: '9876543210',
      otp: '123456',
      role: 'COLLECTOR',
      preferredLanguage: 'ENGLISH',
      areaName: ' Pune ',
      email: '   ',
      displayName: ''
    });

    expect(response.status).toBe(200);
    expect(captured.areaName).toBe('Pune');
    expect(captured.email).toBeUndefined();
    expect(captured.displayName).toBeUndefined();
  });

  it('normalizes common Indian phone formats before requesting an OTP', async () => {
    let capturedPhone = '';
    const authService = {
      requestOtp: async (phone: string) => {
        capturedPhone = phone;
        return 'OTP sent successfully';
      }
    };
    const authApp = createApp(config, db, new JwtService(config), service, authService as any);

    const response = await request(authApp).post('/api/v1/auth/request-otp').send({ phone: '+91 93107-07756' });

    expect(response.status).toBe(200);
    expect(capturedPhone).toBe('9310707756');
  });

  it('uses the existing-account path when registration fields are omitted', async () => {
    let capturedAccount: unknown = 'not-called';
    const authService = {
      verifyOtp: async (_phone: string, _otp: string, _ip: string, account?: unknown) => {
        capturedAccount = account;
        return { token: 'token', collector: { id: 'c1' }, user: null };
      }
    };
    const authApp = createApp(config, db, new JwtService(config), service, authService as any);

    const response = await request(authApp).post('/api/v1/auth/verify-otp').send({
      phone: '9876543210',
      otp: '123456'
    });

    expect(response.status).toBe(200);
    expect(capturedAccount).toBeUndefined();
  });

  it('requires mandatory phone-registration fields when creating an account', async () => {
    const authService = {
      requestOtp: async () => 'OTP sent successfully',
      verifyOtp: async () => { throw new Error('verifyOtp should not be called for invalid input'); }
    };
    const authApp = createApp(config, db, new JwtService(config), service, authService as any);

    const response = await request(authApp).post('/api/v1/auth/verify-otp').send({
      phone: '9876543210',
      otp: '123456',
      role: 'COLLECTOR',
      preferredLanguage: 'ENGLISH',
      areaName: ''
    });

    expect(response.status).toBe(400);
    expect(response.body.error.code).toBe('VALIDATION_ERROR');
  });
});
