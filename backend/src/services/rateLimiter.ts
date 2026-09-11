import { createHash } from 'node:crypto';
import type { PrismaClient } from '@prisma/client';
import { AppError } from '../utils/errors.js';
type Entry = { count: number; resetAt: number; lastAt?: number };
export interface AuthenticationRateLimiter { check(identifier: string, ip: string, kind: 'request'|'verify'|'login'|'signup'|'public_verify'): Promise<void>; }
export class OtpRateLimiter implements AuthenticationRateLimiter { private readonly identifiers = new Map<string, Entry>(); private readonly ips = new Map<string, Entry>(); async check(identifier: string, ip: string, kind: 'request'|'verify'|'login'|'signup'|'public_verify') { const now = Date.now(); const check = (map: Map<string, Entry>, key: string, max: number, window: number, cooldown = 0) => { const old = map.get(key); const item = !old || old.resetAt <= now ? { count: 0, resetAt: now + window } : old; if (cooldown && item.lastAt && now - item.lastAt < cooldown) throw new AppError('OTP_COOLDOWN', 'Please wait before trying again', 429); if (item.count >= max) throw new AppError('OTP_RATE_LIMITED', 'Too many requests. Try again later.', 429); item.count++; item.lastAt = now; map.set(key, item); }; if (kind === 'request') { check(this.identifiers, identifier, 5, 60 * 60_000, 30_000); check(this.ips, ip, 20, 60 * 60_000); } else if (kind === 'public_verify') check(this.ips, ip, 30, 15 * 60_000); else { check(this.identifiers, identifier, 10, 15 * 60_000); check(this.ips, ip, 30, 15 * 60_000); } } }

export class DatabaseAuthenticationRateLimiter implements AuthenticationRateLimiter {
  constructor(private readonly db: PrismaClient) {}
  private key(scope: string, value: string, windowStart: number) { return `${scope}:${createHash('sha256').update(value).digest('hex')}:${windowStart}`; }
  async check(identifier: string, ip: string, kind: 'request'|'verify'|'login'|'signup'|'public_verify') {
    const windowMs = kind === 'request' ? 60 * 60_000 : 15 * 60_000;
    const maximum = kind === 'request' ? 5 : kind === 'public_verify' ? 30 : 10;
    const now = Date.now();
    const windowStart = Math.floor(now / windowMs) * windowMs;
    const values = kind === 'public_verify' ? [['ip', ip, maximum] as const] : [['identifier', identifier, maximum] as const, ['ip', ip, kind === 'request' ? 20 : 30] as const];
    for (const [scope, value, limit] of values) {
      const id = this.key(`${kind}:${scope}`, value, windowStart);
      const bucket = await this.db.requestRateLimit.upsert({ where: { id }, create: { id, count: 1, resetAt: new Date(windowStart + windowMs), lastAt: new Date(now) }, update: { count: { increment: 1 }, lastAt: new Date(now) } });
      if (bucket.count > limit) throw new AppError('OTP_RATE_LIMITED', 'Too many requests. Try again later.', 429);
    }
  }
}
