import { describe, expect, it } from 'vitest';
import { AuthService } from '../src/services/authService.js';
import { DevelopmentOtpProvider } from '../src/services/otp.js';
import { JwtService } from '../src/services/jwt.js';
import type { AppConfig } from '../src/config/env.js';

const config: AppConfig = { NODE_ENV: 'test', PORT: 4000, DATABASE_URL: 'mongodb://test', JWT_SECRET: 'a-secure-test-secret', JWT_EXPIRES_IN: '1h', CORS_ORIGIN: '*', APP_VERSION: '1.0.0', OTP_PROVIDER: 'development', DEV_OTP_CODE: '123456', STORAGE_PROVIDER: 'local', LOCAL_UPLOAD_DIR: 'uploads', LOCAL_UPLOAD_BASE_URL: '', LOCAL_UPLOAD_PUBLIC: true, S3_REGION: 'ap-south-1' };
const collector = (id: string) => ({ id, phone: '9876543210', preferredLanguage: 'HINDI', latitude: null, longitude: null, areaName: '', accountStatus: 'ACTIVE', createdAt: new Date(), lastLoginAt: new Date() });
function setup(existing = false) { let current = existing ? collector('collector-1') : null; const repo = { findByPhone: async () => current, create: async (phone: string) => { current = collector('collector-new'); return { ...current, phone }; }, touchLogin: async () => current! } as any; const service = new AuthService(new DevelopmentOtpProvider(config), repo, new JwtService(config)); return { service, repo }; }
describe('collector authentication service', () => {
  it('accepts the configured development OTP when the request record is not local', async () => {
    const provider = new DevelopmentOtpProvider(config);
    await expect(provider.verify('9876543210', '123456')).resolves.toBe('approved');
  });

  it('verifies a development OTP and creates a collector', async () => { const { service } = setup(); await service.requestOtp('9876543210', 'ip-1'); const result = await service.verifyOtp('9876543210', '123456', 'ip-1'); expect(result.collector.id).toBe('collector-new'); expect(result.token).toBeTruthy(); });
  it('rejects an invalid OTP', async () => { const { service } = setup(); await service.requestOtp('9876543210', 'ip-1'); await expect(service.verifyOtp('9876543210', '000000', 'ip-1')).rejects.toMatchObject({ code: 'OTP_INVALID' }); });
  it('logs in an existing collector', async () => { const { service } = setup(true); await service.requestOtp('9876543210', 'ip-2'); const result = await service.verifyOtp('9876543210', '123456', 'ip-2'); expect(result.collector.id).toBe('collector-1'); });
});
