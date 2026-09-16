import { describe, expect, it } from 'vitest';
import { SessionService } from '../src/services/sessionService.js';
import { JwtService } from '../src/services/jwt.js';
import type { AppConfig } from '../src/config/env.js';

const config: AppConfig = { NODE_ENV:'test', PORT:4000, DATABASE_URL:'mongodb://test', JWT_SECRET:'a-secure-test-secret', JWT_EXPIRES_IN:'15m', REFRESH_TOKEN_EXPIRES_IN_DAYS:30, TRACEABILITY_SIGNING_SECRET:'a-separate-traceability-test-secret', CORS_ORIGIN:'*', APP_VERSION:'1.0.0', OTP_PROVIDER:'development', DEV_OTP_CODE:'123456', STORAGE_PROVIDER:'local', LOCAL_UPLOAD_DIR:'uploads', LOCAL_UPLOAD_BASE_URL:'', LOCAL_UPLOAD_PUBLIC:true, S3_REGION:'ap-south-1', RATE_LIMIT_STORE:'memory' };

function fakeDatabase() {
  const rows = new Map<string, any>();
  let sequence = 0;
  const refreshToken = {
    create: async ({ data }: any) => { const row = { id:`rt-${++sequence}`, createdAt:new Date(), revokedAt:null, replacedBy:null, ...data }; rows.set(row.tokenHash, row); return row; },
    findUnique: async ({ where }: any) => where.tokenHash ? rows.get(where.tokenHash) ?? null : [...rows.values()].find(row => row.id === where.id) ?? null,
    update: async ({ where, data }: any) => { const row = [...rows.values()].find(value => value.id === where.id); Object.assign(row, data); return row; },
    updateMany: async ({ where, data }: any) => { const matches = [...rows.values()].filter(row => (!where.id || row.id === where.id) && (!where.familyId || row.familyId === where.familyId) && (where.revokedAt !== null || row.revokedAt === null) && (!where.expiresAt?.gt || row.expiresAt > where.expiresAt.gt)); matches.forEach(row => Object.assign(row, data)); return { count:matches.length }; }
  };
  const db: any = { refreshToken };
  db.$transaction = async (callback: any) => callback(db);
  return db;
}

describe('refresh-token rotation', () => {
  it('rotates once and rejects reuse of the old token family', async () => {
    const db = fakeDatabase();
    const service = new SessionService(db, new JwtService(config), config);
    const first = await service.issue('collector-1', 'COLLECTOR');
    const second = await service.rotate(first.refreshToken);
    expect(second.refreshToken).not.toBe(first.refreshToken);
    await expect(service.rotate(first.refreshToken)).rejects.toThrow(/reuse detected/i);
    await expect(service.rotate(second.refreshToken)).rejects.toThrow();
  });

  it('revokes the complete session family on logout', async () => {
    const db = fakeDatabase();
    const service = new SessionService(db, new JwtService(config), config);
    const first = await service.issue('collector-1', 'COLLECTOR');
    await service.revoke(first.refreshToken);
    await expect(service.rotate(first.refreshToken)).rejects.toThrow();
  });
});
