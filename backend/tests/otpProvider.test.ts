import { afterEach, describe, expect, it, vi } from 'vitest';
import { TwoFactorOtpProvider } from '../src/services/otp.js';
import type { AppConfig } from '../src/config/env.js';

const config: AppConfig = {
  APP_ENV: 'testing',
  NODE_ENV: 'test',
  PORT: 4000,
  DATABASE_URL: 'mongodb://test',
  JWT_SECRET: 'a-secure-test-secret',
  JWT_EXPIRES_IN: '1h',
  REFRESH_TOKEN_EXPIRES_IN_DAYS: 30,
  TRACEABILITY_SIGNING_SECRET: 'a-separate-traceability-test-secret',
  CORS_ORIGIN: '*',
  APP_VERSION: '1.0.0',
  OTP_PROVIDER: 'twofactor',
  TWOFACTOR_API_KEY: 'test-key',
  DEV_OTP_CODE: '123456',
  STORAGE_PROVIDER: 'local',
  LOCAL_UPLOAD_DIR: 'uploads',
  LOCAL_UPLOAD_BASE_URL: '',
  LOCAL_UPLOAD_PUBLIC: true,
  S3_REGION: 'ap-south-1',
  RATE_LIMIT_STORE: 'memory'
};

describe('2Factor OTP provider', () => {
  let record: any = null;
  const db = {
    otpChallenge: {
      upsert: vi.fn(async ({ create, update }: any) => { record = { ...create, ...update }; return record; }),
      findUnique: vi.fn(async () => record),
      update: vi.fn(async ({ data }: any) => { record = { ...record, ...data }; return record; }),
      delete: vi.fn(async () => { record = null; })
    }
  } as any;

  afterEach(() => {
    record = null;
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('sends a generated OTP and verifies it without storing plaintext', async () => {
    let sentUrl = '';
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      sentUrl = url;
      return new Response(JSON.stringify({ Status: 'Success' }), { status: 200 });
    }));
    const provider = new TwoFactorOtpProvider(config, db);

    await provider.request('9876543210');
    expect(sentUrl).toContain('/API/V1/test-key/SMS/%2B919876543210/');
    expect(record.codeHash).toMatch(/^[a-f0-9]{64}$/);
    expect(record.codeHash).not.toBe(decodeURIComponent(sentUrl.split('/').at(-1)!));

    await expect(provider.verify('9876543210', '000000')).resolves.toBe('invalid');
    const otp = decodeURIComponent(sentUrl.split('/').at(-1)!);
    await expect(provider.verify('9876543210', otp)).resolves.toBe('approved');
    expect(record).toBeNull();
  });

  it('turns provider rejection into a safe upstream error and removes the challenge', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ Status: 'Error' }), { status: 200 })));
    const provider = new TwoFactorOtpProvider(config, db);

    await expect(provider.request('9876543210')).rejects.toMatchObject({ code: 'INTERNAL_SERVER_ERROR', status: 502 });
    expect(record).toBeNull();
  });
});
