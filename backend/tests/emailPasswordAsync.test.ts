import { scryptSync } from 'node:crypto';
import { describe, expect, it, vi } from 'vitest';
import { EmailAuthService } from '../src/services/emailAuthService.js';

describe('asynchronous email password hashing', () => {
  it('creates a compatible password hash before the signup transaction starts', async () => {
    const password = 'New-password-123';
    const createdAt = new Date();
    const create = vi.fn(async ({ data }: any) => ({ id: 'user-1', ...data, accountStatus: 'ACTIVE', createdAt, updatedAt: createdAt }));
    const tx = { collector: { create: vi.fn().mockResolvedValue({ id: 'profile-1' }) }, user: { create } };
    let started = false;
    const db = { user: { findFirst: vi.fn().mockResolvedValue(null) }, $transaction: vi.fn(async (work: (value: typeof tx) => unknown) => { started = true; return work(tx); }), loginAudit: { create: vi.fn().mockResolvedValue({}) } } as never;
    const signup = new EmailAuthService(db, { generateHouseholdToken: vi.fn().mockReturnValue('token') } as never).signup({ email: 'new@example.test', password, role: 'HOUSEHOLD', preferredLanguage: 'ENGLISH' });
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(started).toBe(false);
    await signup;
    const [salt, digest] = create.mock.calls[0][0].data.passwordHash.split(':');
    expect(digest).toBe(scryptSync(password, Buffer.from(salt, 'hex'), 64).toString('hex'));
  });
  it('accepts existing scrypt passwords while allowing the event loop to run', async () => {
    const salt = Buffer.alloc(16, 7);
    const password = 'Existing-password-123';
    const user = { id: 'user-1', email: 'demo@example.test', passwordHash: `${salt.toString('hex')}:${scryptSync(password, salt, 64).toString('hex')}`, role: 'HOUSEHOLD', collectorProfileId: 'household-1', accountStatus: 'ACTIVE', createdAt: new Date(), updatedAt: new Date() };
    const db = { user: { findFirst: vi.fn().mockResolvedValue(user) }, collector: { findUnique: vi.fn().mockResolvedValue({ id: 'household-1' }) }, loginAudit: { create: vi.fn().mockResolvedValue({}) } } as never;
    const service = new EmailAuthService(db, { generateHouseholdToken: vi.fn().mockReturnValue('token') } as never);
    let finished = false;
    const login = service.login(user.email, password).then(value => { finished = true; return value; });
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(finished).toBe(false);
    expect((await login).user.profileId).toBe('household-1');
    await expect(service.login(user.email, 'incorrect-password')).rejects.toMatchObject({ status: 401 });
  });
  it('rejects malformed stored hashes without crashing', async () => {
    const db = { adminAccount: { findUnique: vi.fn().mockResolvedValue({ passwordHash: 'bad:hash', active: true }) }, loginAudit: { create: vi.fn().mockResolvedValue({}) } } as never;
    await expect(new EmailAuthService(db, {} as never).adminLogin('admin@example.test', 'password')).rejects.toMatchObject({ status: 401, details: { code: 'INVALID_CREDENTIALS' } });
  });
});
