import { createHash } from 'node:crypto';
import type { PrismaClient } from '@prisma/client';
import { AppError } from '../utils/errors.js';
type Entry = { count: number; resetAt: number; lastAt?: number };
export type RateLimiterOptions = {
  /** Window for repeated OTP requests. Keep this short enough for recovery UX. */
  requestWindowMs?: number;
  /** Minimum delay between two OTP requests for the same identifier. */
  requestCooldownMs?: number;
};

export interface AuthenticationRateLimiter { check(identifier: string, ip: string, kind: 'request'|'verify'|'login'|'signup'|'public_verify'): Promise<void>; }
const retryAfterSeconds = (until: number, now = Date.now()) => Math.max(1, Math.ceil((until - now) / 1000));
const rateLimitError = (code: 'OTP_COOLDOWN' | 'OTP_RATE_LIMITED', message: string, until: number, now = Date.now()) =>
  new AppError(code, message, 429, { retryAfterSeconds: retryAfterSeconds(until, now) });
const DEFAULT_REQUEST_WINDOW_MS = 2 * 60_000;
const DEFAULT_REQUEST_COOLDOWN_MS = 30_000;

export class OtpRateLimiter implements AuthenticationRateLimiter {
  private readonly identifiers = new Map<string, Entry>();
  private readonly ips = new Map<string, Entry>();

  constructor(private readonly options: RateLimiterOptions = {}) {}

  async check(identifier: string, ip: string, kind: 'request'|'verify'|'login'|'signup'|'public_verify') {
    const now = Date.now();
    const requestWindowMs = this.options.requestWindowMs ?? DEFAULT_REQUEST_WINDOW_MS;
    const requestCooldownMs = this.options.requestCooldownMs ?? DEFAULT_REQUEST_COOLDOWN_MS;
    const check = (map: Map<string, Entry>, key: string, max: number, window: number, cooldown = 0) => {
      const old = map.get(key);
      const item = !old || old.resetAt <= now ? { count: 0, resetAt: now + window } : old;
      if (cooldown && item.lastAt && now - item.lastAt < cooldown) throw rateLimitError('OTP_COOLDOWN', 'Please wait before trying again', item.lastAt + cooldown, now);
      if (item.count >= max) throw rateLimitError('OTP_RATE_LIMITED', 'Too many requests. Try again later.', item.resetAt, now);
      item.count++;
      item.lastAt = now;
      map.set(key, item);
    };
    if (kind === 'request') {
      check(this.identifiers, identifier, 5, requestWindowMs, requestCooldownMs);
      check(this.ips, ip, 20, requestWindowMs);
    } else if (kind === 'public_verify') {
      check(this.ips, ip, 30, 15 * 60_000);
    } else {
      check(this.identifiers, identifier, 10, 15 * 60_000);
      check(this.ips, ip, 30, 15 * 60_000);
    }
  }
}

export class DatabaseAuthenticationRateLimiter implements AuthenticationRateLimiter {
  constructor(private readonly db: PrismaClient, private readonly options: RateLimiterOptions = {}) {}
  private key(scope: string, value: string, windowStart: number) { return `${scope}:${createHash('sha256').update(value).digest('hex')}:${windowStart}`; }
  async check(identifier: string, ip: string, kind: 'request'|'verify'|'login'|'signup'|'public_verify') {
    const requestWindowMs = this.options.requestWindowMs ?? DEFAULT_REQUEST_WINDOW_MS;
    const requestCooldownMs = this.options.requestCooldownMs ?? DEFAULT_REQUEST_COOLDOWN_MS;
    const windowMs = kind === 'request' ? requestWindowMs : 15 * 60_000;
    const maximum = kind === 'request' ? 5 : kind === 'public_verify' ? 30 : 10;
    const now = Date.now();
    const windowStart = Math.floor(now / windowMs) * windowMs;
    const values = kind === 'public_verify' ? [['ip', ip, maximum] as const] : [['identifier', identifier, maximum] as const, ['ip', ip, kind === 'request' ? 20 : 30] as const];
    for (const [scope, value, limit] of values) {
      const id = this.key(`${kind}:${scope}`, value, windowStart);
      const existing = await this.db.requestRateLimit.findUnique({ where: { id } });
      if (kind === 'request' && scope === 'identifier' && existing?.lastAt) {
        const cooldownUntil = existing.lastAt.getTime() + requestCooldownMs;
        if (cooldownUntil > now) throw rateLimitError('OTP_COOLDOWN', 'Please wait before trying again', cooldownUntil, now);
      }
      const bucket = await this.db.requestRateLimit.upsert({ where: { id }, create: { id, count: 1, resetAt: new Date(windowStart + windowMs), lastAt: new Date(now) }, update: { count: { increment: 1 }, lastAt: new Date(now) } });
      if (bucket.count > limit) throw rateLimitError('OTP_RATE_LIMITED', 'Too many requests. Try again.', bucket.resetAt.getTime(), now);
    }
  }
}
