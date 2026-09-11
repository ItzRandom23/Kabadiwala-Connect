import { createHash, randomBytes, randomUUID } from 'node:crypto';
import type { PrismaClient } from '@prisma/client';
import type { AppConfig } from '../config/env.js';
import type { JwtService, AuthIdentity } from './jwt.js';
import { AppError } from '../utils/errors.js';

const hash = (token: string) => createHash('sha256').update(token).digest('hex');

export class SessionService {
  constructor(private readonly db: PrismaClient, private readonly jwt: JwtService, private readonly config: AppConfig) {}

  async issue(actorId: string, role: AuthIdentity['role'], familyId: string = randomUUID()) {
    const refreshToken = randomBytes(48).toString('base64url');
    await this.db.refreshToken.create({ data: { actorId, actorRole: role, tokenHash: hash(refreshToken), familyId, expiresAt: new Date(Date.now() + this.config.REFRESH_TOKEN_EXPIRES_IN_DAYS * 86400000) } });
    const token = role === 'RECYCLER' ? this.jwt.generateRecyclerToken(actorId) : role === 'ADMIN' ? this.jwt.generateAdminToken(actorId) : this.jwt.generateToken(actorId);
    return { token, refreshToken };
  }

  async rotate(refreshToken: string) {
    const tokenHash = hash(refreshToken);
    const existing = await this.db.refreshToken.findUnique({ where: { tokenHash } });
    if (!existing || existing.expiresAt <= new Date()) throw new AppError('TOKEN_INVALID', 'Refresh token is invalid or expired', 401);
    if (existing.revokedAt) {
      await this.db.refreshToken.updateMany({ where: { familyId: existing.familyId, revokedAt: null }, data: { revokedAt: new Date() } });
      throw new AppError('TOKEN_INVALID', 'Refresh token reuse detected; sign in again', 401);
    }
    const nextRefreshToken = randomBytes(48).toString('base64url');
    const nextHash = hash(nextRefreshToken);
    const now = new Date();
    const rotated = await this.db.$transaction(async transaction => {
      const claimed = await transaction.refreshToken.updateMany({ where: { id: existing.id, revokedAt: null, expiresAt: { gt: now } }, data: { revokedAt: now, replacedBy: nextHash } });
      if (!claimed.count) return false;
      await transaction.refreshToken.create({ data: { actorId: existing.actorId, actorRole: existing.actorRole, tokenHash: nextHash, familyId: existing.familyId, expiresAt: new Date(Date.now() + this.config.REFRESH_TOKEN_EXPIRES_IN_DAYS * 86400000) } });
      return true;
    });
    if (!rotated) throw new AppError('TOKEN_INVALID', 'Refresh token was already used', 401);
    const role = existing.actorRole as AuthIdentity['role'];
    const token = role === 'RECYCLER' ? this.jwt.generateRecyclerToken(existing.actorId) : role === 'ADMIN' ? this.jwt.generateAdminToken(existing.actorId) : this.jwt.generateToken(existing.actorId);
    return { token, refreshToken: nextRefreshToken };
  }

  async revoke(refreshToken?: string) {
    if (!refreshToken) return;
    const existing = await this.db.refreshToken.findUnique({ where: { tokenHash: hash(refreshToken) } });
    if (existing) await this.db.refreshToken.updateMany({ where: { familyId: existing.familyId, revokedAt: null }, data: { revokedAt: new Date() } });
  }
}
