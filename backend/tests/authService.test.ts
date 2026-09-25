import { describe, expect, it } from 'vitest';
import { AuthService } from '../src/services/authService.js';
import { DevelopmentOtpProvider } from '../src/services/otp.js';
import { JwtService } from '../src/services/jwt.js';
import type { AppConfig } from '../src/config/env.js';

const config: AppConfig = { APP_ENV: 'testing', NODE_ENV: 'test', PORT: 4000, DATABASE_URL: 'mongodb://test', JWT_SECRET: 'a-secure-test-secret', JWT_EXPIRES_IN: '1h', REFRESH_TOKEN_EXPIRES_IN_DAYS: 30, TRACEABILITY_SIGNING_SECRET: 'a-separate-traceability-test-secret', CORS_ORIGIN: '*', APP_VERSION: '1.0.0', OTP_PROVIDER: 'development', DEV_OTP_CODE: '123456', STORAGE_PROVIDER: 'local', LOCAL_UPLOAD_DIR: 'uploads', LOCAL_UPLOAD_BASE_URL: '', LOCAL_UPLOAD_PUBLIC: true, S3_REGION: 'ap-south-1', RATE_LIMIT_STORE: 'memory', OTP_REQUEST_WINDOW_MINUTES: 2 };
const collector = (id: string) => ({ id, phone: '9876543210', preferredLanguage: 'HINDI', latitude: null, longitude: null, areaName: '', accountStatus: 'ACTIVE', createdAt: new Date(), lastLoginAt: new Date() });
function setup(existing = false) { let current = existing ? collector('collector-1') : null; const repo = { findByPhone: async () => current, create: async (phone: string) => { current = collector('collector-new'); return { ...current, phone }; }, touchLogin: async () => current! } as any; const service = new AuthService(new DevelopmentOtpProvider(config), repo, new JwtService(config)); return { service, repo }; }
describe('collector authentication service', () => {
  it('accepts the configured development OTP when the request record is not local', async () => {
    const provider = new DevelopmentOtpProvider(config);
    await expect(provider.verify('9876543210', '123456')).resolves.toBe('approved');
  });

  it('verifies a development OTP and creates a collector with an explicit role', async () => { const { service } = setup(); await service.requestOtp('9876543210', 'ip-1'); const result = await service.verifyOtp('9876543210', '123456', 'ip-1', { role: 'COLLECTOR' }); expect(result.collector.id).toBe('collector-new'); expect(result.token).toBeTruthy(); });
  it('does not create a collector when the account type was not chosen', async () => { const { service } = setup(); await service.requestOtp('9876543210', 'ip-no-role'); await expect(service.verifyOtp('9876543210', '123456', 'ip-no-role')).rejects.toMatchObject({ code: 'VALIDATION_ERROR', status: 422, details: { code: 'ROLE_REQUIRED' } }); });
  it('rejects an invalid OTP', async () => { const { service } = setup(); await service.requestOtp('9876543210', 'ip-1'); await expect(service.verifyOtp('9876543210', '000000', 'ip-1')).rejects.toMatchObject({ code: 'OTP_INVALID' }); });
  it('logs in an existing collector', async () => { const { service } = setup(true); await service.requestOtp('9876543210', 'ip-2'); const result = await service.verifyOtp('9876543210', '123456', 'ip-2'); expect(result.collector.id).toBe('collector-1'); });

  it('preserves an existing household role when phone login omits registration details', async () => {
    const existingUser = {
      id: 'household-user-1',
      phone: '9876543201',
      email: 'household@example.test',
      role: 'HOUSEHOLD',
      preferredLanguage: 'ENGLISH',
      accountStatus: 'ACTIVE',
      collectorProfileId: 'household-profile-1',
      recyclerProfileId: null,
      createdAt: new Date(),
      updatedAt: new Date()
    };
    const existingProfile = {
      id: 'household-profile-1',
      phone: '9876543201',
      email: 'household@example.test',
      displayName: 'Household A',
      preferredLanguage: 'ENGLISH',
      areaName: 'Pune',
      accountStatus: 'ACTIVE',
      createdAt: new Date(),
      lastLoginAt: new Date()
    };
    const tx = {
      user: {
        findFirst: async () => existingUser,
        update: async ({ data }: any) => Object.assign(existingUser, data)
      },
      collector: {
        update: async () => existingProfile,
        findUnique: async () => existingProfile
      }
    };
    const db = { $transaction: async (work: (value: typeof tx) => unknown) => work(tx) } as any;
    const service = new AuthService(new DevelopmentOtpProvider(config), {} as any, new JwtService(config), undefined, db);

    const result = await service.verifyOtp('9876543201', '123456', 'ip-household');

    expect(result.user?.role).toBe('HOUSEHOLD');
    expect(result.user?.profileId).toBe('household-profile-1');
    expect(new JwtService(config).verifyToken(result.token).role).toBe('HOUSEHOLD');
  });

  it('links a legacy recycler profile when phone login omits a role', async () => {
    const legacyProfile = {
      id: 'legacy-recycler-1',
      phone: '9876543203',
      name: 'Recycler A',
      areaName: 'Pune',
      authorizationStatus: 'PENDING'
    };
    let createdUser: any = null;
    const tx = {
      user: {
        findFirst: async () => null,
        create: async ({ data }: any) => {
          createdUser = { id: 'linked-recycler-user-1', ...data, accountStatus: 'ACTIVE', createdAt: new Date(), updatedAt: new Date() };
          return createdUser;
        }
      },
      collector: { findFirst: async () => null },
      recycler: {
        findFirst: async () => legacyProfile,
        findUnique: async () => legacyProfile
      }
    };
    const db = { $transaction: async (work: (value: typeof tx) => unknown) => work(tx) } as any;
    const service = new AuthService(new DevelopmentOtpProvider(config), {} as any, new JwtService(config), undefined, db);

    const result = await service.verifyOtp('9876543203', '123456', 'ip-legacy-recycler');

    expect(createdUser).toMatchObject({ phone: '9876543203', role: 'RECYCLER', recyclerProfileId: 'legacy-recycler-1' });
    expect(result.user?.role).toBe('RECYCLER');
    expect(result.user?.profileId).toBe('legacy-recycler-1');
    expect(new JwtService(config).verifyToken(result.token).role).toBe('RECYCLER');
  });

  it('requires a role before creating a new phone account', async () => {
    let created = false;
    const tx = {
      user: { findFirst: async () => null },
      collector: {
        findFirst: async () => null,
        create: async () => { created = true; throw new Error('Unexpected account creation'); }
      },
      recycler: { findFirst: async () => null }
    };
    const db = { $transaction: async (work: (value: typeof tx) => unknown) => work(tx) } as any;
    const service = new AuthService(new DevelopmentOtpProvider(config), {} as any, new JwtService(config), undefined, db);

    await service.requestOtp('9876543202', 'ip-new-account');
    await expect(service.verifyOtp('9876543202', '123456', 'ip-new-account', {
      areaName: 'Pune',
      displayName: 'New household'
    })).rejects.toMatchObject({ code: 'VALIDATION_ERROR', status: 422, details: { code: 'ROLE_REQUIRED' } });
    expect(created).toBe(false);
  });

  it('does not block a verified phone when the optional email belongs to another account', async () => {
    const existingUser = {
      id: 'user-1',
      phone: '9876543210',
      email: 'old@example.com',
      role: 'COLLECTOR',
      preferredLanguage: 'ENGLISH',
      accountStatus: 'ACTIVE',
      collectorProfileId: 'collector-1',
      recyclerProfileId: null,
      createdAt: new Date(),
      updatedAt: new Date()
    };
    const existingProfile = {
      id: 'collector-1',
      phone: '9876543210',
      email: 'old@example.com',
      displayName: null,
      preferredLanguage: 'ENGLISH',
      areaName: 'Pune',
      accountStatus: 'ACTIVE',
      createdAt: new Date(),
      lastLoginAt: new Date()
    };
    const tx = {
      user: {
        findUnique: async ({ where }: any) => 'phone' in where ? existingUser : { id: 'other-user' },
        findFirst: async ({ where }: any) => 'phone' in where ? existingUser : null,
        update: async ({ data }: any) => Object.assign(existingUser, data)
      },
      collector: {
        findFirst: async ({ where }: any) => 'email' in where ? { id: 'other-collector' } : null,
        findUnique: async ({ where }: any) => 'email' in where ? { id: 'other-collector' } : existingProfile,
        update: async () => existingProfile
      }
    };
    const db = { $transaction: async (work: (value: typeof tx) => unknown) => work(tx) } as any;
    const service = new AuthService(new DevelopmentOtpProvider(config), {} as any, new JwtService(config), undefined, db);

    await service.requestOtp('9876543210', 'ip-3');
    const result = await service.verifyOtp('9876543210', '123456', 'ip-3', {
      role: 'COLLECTOR',
      preferredLanguage: 'ENGLISH',
      areaName: 'Pune',
      email: 'other@example.com'
    });

    expect(result.user?.profileId).toBe('collector-1');
    expect(result.user?.email).toBe('old@example.com');
  });

  it('requires a name when creating a new collector phone account', async () => {
    const tx = {
      user: { findFirst: async () => null },
      collector: { findFirst: async () => null },
      recycler: { findFirst: async () => null }
    };
    const db = { $transaction: async (work: (value: typeof tx) => unknown) => work(tx) } as any;
    const service = new AuthService(new DevelopmentOtpProvider(config), {} as any, new JwtService(config), undefined, db);

    await service.requestOtp('9876543210', 'ip-4');
    await expect(service.verifyOtp('9876543210', '123456', 'ip-4', {
      role: 'COLLECTOR',
      preferredLanguage: 'ENGLISH',
      areaName: 'Pune',
      displayName: '   '
    })).rejects.toMatchObject({ code: 'VALIDATION_ERROR', details: { code: 'DISPLAY_NAME_REQUIRED' } });
  });
});
