import { describe, expect, it, vi } from 'vitest';
import express from 'express';
import request from 'supertest';
import { AccountPrivacyService } from '../src/services/accountPrivacyService.js';
import { authRoutes } from '../src/routes/authRoutes.js';
import { errorHandler } from '../src/middleware/errors.js';
import { JwtService } from '../src/services/jwt.js';

describe('account privacy service', () => {
  it('soft-deletes a household, revokes sessions, and preserves an audit event', async () => {
    const user = {
      id: 'user-1', role: 'HOUSEHOLD', accountStatus: 'ACTIVE', email: 'person@example.test', phone: '919876543210'
    };
    const userUpdate = vi.fn().mockResolvedValue({});
    const collectorUpdate = vi.fn().mockResolvedValue({});
    const revoke = vi.fn().mockResolvedValue({ count: 2 });
    const notificationDelete = vi.fn().mockResolvedValue({ count: 4 });
    const deviceDelete = vi.fn().mockResolvedValue({ count: 1 });
    const otpDelete = vi.fn().mockResolvedValue({ count: 1 });
    const auditCreate = vi.fn().mockResolvedValue({ id: 'audit-1' });
    const tx = {
      user: { update: userUpdate },
      collector: { update: collectorUpdate },
      refreshToken: { updateMany: revoke },
      notificationEvent: { deleteMany: notificationDelete },
      notificationDevice: { deleteMany: deviceDelete },
      otpChallenge: { deleteMany: otpDelete },
      auditEvent: { create: auditCreate }
    };
    const db = {
      user: { findFirst: vi.fn().mockResolvedValue(user) },
      $transaction: vi.fn(async (callback: (transaction: unknown) => Promise<unknown>) => callback(tx))
    } as never;

    const result = await new AccountPrivacyService(db).deleteAccount('profile-1', 'HOUSEHOLD');

    expect(result).toEqual({ deleted: true, alreadyDeleted: false, profileId: 'profile-1' });
    expect(userUpdate).toHaveBeenCalledWith({ where: { id: 'user-1' }, data: { email: null, phone: null, passwordHash: null, accountStatus: 'DELETED' } });
    expect(collectorUpdate).toHaveBeenCalledWith(expect.objectContaining({ where: { id: 'profile-1' }, data: expect.objectContaining({ accountStatus: 'DELETED', areaName: 'WITHDRAWN_ACCOUNT' }) }));
    expect(revoke).toHaveBeenCalledWith({ where: { actorId: 'profile-1', revokedAt: null }, data: { revokedAt: expect.any(Date) } });
    expect(notificationDelete).toHaveBeenCalledWith({ where: { accountId: 'profile-1' } });
    expect(deviceDelete).toHaveBeenCalledWith({ where: { accountId: 'profile-1' } });
    expect(otpDelete).toHaveBeenCalledWith({ where: { phone: '919876543210' } });
    expect(auditCreate).toHaveBeenCalledWith(expect.objectContaining({ data: expect.objectContaining({ event: 'ACCOUNT_DELETED', entityId: 'profile-1', actorRole: 'HOUSEHOLD' }) }));
  });

  it('is idempotent when the account is already deleted', async () => {
    const user = { id: 'user-1', role: 'COLLECTOR', accountStatus: 'DELETED' };
    const transaction = vi.fn();
    const db = { user: { findFirst: vi.fn().mockResolvedValue(user) }, $transaction: transaction } as never;

    const result = await new AccountPrivacyService(db).deleteAccount('profile-1', 'COLLECTOR');

    expect(result).toEqual({ deleted: true, alreadyDeleted: true, profileId: 'profile-1' });
    expect(transaction).not.toHaveBeenCalled();
  });

  it('protects the HTTP export/delete contract with the current account role', async () => {
    const exportAccount = vi.fn().mockResolvedValue({ schemaVersion: 1, account: { profileId: 'profile-1' } });
    const deleteAccount = vi.fn().mockResolvedValue({ deleted: true, alreadyDeleted: false, profileId: 'profile-1' });
    const privacy = { exportAccount, deleteAccount } as never;
    const config = { JWT_SECRET: 'a-secure-test-secret', JWT_EXPIRES_IN: '1h' } as never;
    const jwt = new JwtService(config);
    const db = {
      collector: { findUnique: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE' }) },
      user: { findFirst: vi.fn().mockResolvedValue({ role: 'HOUSEHOLD', accountStatus: 'ACTIVE' }) }
    } as never;
    const app = express();
    app.use(express.json());
    app.use('/auth', authRoutes({} as never, undefined, jwt, db, privacy));
    app.use(errorHandler);
    const token = jwt.generateHouseholdToken('profile-1');

    const unauthorized = await request(app).get('/auth/account/export');
    expect(unauthorized.status).toBe(401);

    const exported = await request(app).get('/auth/account/export').set('Authorization', `Bearer ${token}`);
    expect(exported.status).toBe(200);
    expect(exportAccount).toHaveBeenCalledWith('profile-1', 'HOUSEHOLD');

    const invalidDelete = await request(app).post('/auth/account/delete').set('Authorization', `Bearer ${token}`).send({ confirmation: 'no' });
    expect(invalidDelete.status).toBe(422);
    expect(deleteAccount).not.toHaveBeenCalled();

    const deleted = await request(app).post('/auth/account/delete').set('Authorization', `Bearer ${token}`).send({ confirmation: 'DELETE' });
    expect(deleted.status).toBe(200);
    expect(deleteAccount).toHaveBeenCalledWith('profile-1', 'HOUSEHOLD');
  });
});
