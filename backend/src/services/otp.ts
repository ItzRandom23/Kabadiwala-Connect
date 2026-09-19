import { createHmac, randomInt, timingSafeEqual } from 'node:crypto';
import type { PrismaClient } from '@prisma/client';
import type { AppConfig } from '../config/env.js';
import { AppError } from '../utils/errors.js';

export type OtpResult = 'approved'|'invalid'|'expired'|'locked';
export interface OtpProvider { request(phone: string): Promise<void>; verify(phone: string, otp: string): Promise<OtpResult>; }

export class DevelopmentOtpProvider implements OtpProvider {
  private readonly records = new Map<string, { code: string; expires: number; attempts: number; lockedUntil: number }>();
  constructor(private readonly config: AppConfig) {}
  async request(phone: string) { this.records.set(phone, { code: this.config.DEV_OTP_CODE, expires: Date.now() + 5 * 60_000, attempts: 0, lockedUntil: 0 }); }
  async verify(phone: string, otp: string) {
    const record = this.records.get(phone);
    // Development deployments may run multiple processes, so the in-memory
    // request record can be missing on verify. The fixed test code remains a
    // deliberate development-only fallback.
    if (!record) return otp === this.config.DEV_OTP_CODE ? 'approved' : 'expired';
    if (Date.now() >= record.expires) return 'expired';
    if (Date.now() < record.lockedUntil) return 'locked';
    if (++record.attempts > 5) { record.lockedUntil = Date.now() + 15 * 60_000; return 'locked'; }
    if (otp !== record.code) return 'invalid';
    this.records.delete(phone);
    return 'approved';
  }
}

export class TwilioVerifyProvider implements OtpProvider {
  constructor(private readonly config: AppConfig) {}
  private get auth() { return Buffer.from(`${this.config.TWILIO_ACCOUNT_SID}:${this.config.TWILIO_AUTH_TOKEN}`).toString('base64'); }
  async request(phone: string) {
    const response = await fetch(`https://verify.twilio.com/v2/Services/${this.config.TWILIO_VERIFY_SERVICE_SID}/Verifications`, { method: 'POST', headers: { Authorization: `Basic ${this.auth}`, 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ To: `+91${phone}`, Channel: 'sms' }), signal: AbortSignal.timeout(10_000) });
    if (!response.ok) throw new AppError('INTERNAL_SERVER_ERROR', 'OTP provider unavailable', 502);
  }
  async verify(phone: string, otp: string) {
    const response = await fetch(`https://verify.twilio.com/v2/Services/${this.config.TWILIO_VERIFY_SERVICE_SID}/VerificationCheck`, { method: 'POST', headers: { Authorization: `Basic ${this.auth}`, 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ To: `+91${phone}`, Code: otp }), signal: AbortSignal.timeout(10_000) });
    if (response.status === 404) return 'expired';
    if (!response.ok) throw new AppError('INTERNAL_SERVER_ERROR', 'OTP provider unavailable', 502);
    const body = await response.json() as { status?: string };
    return body.status === 'approved' ? 'approved' : 'invalid';
  }
}

type OtpDatabase = Pick<PrismaClient, 'otpChallenge'>;

/**
 * 2Factor's documented SMS endpoint accepts a server-generated six-digit code.
 * The code hash and expiry live in MongoDB; the plaintext code never does.
 */
export class TwoFactorOtpProvider implements OtpProvider {
  constructor(private readonly config: AppConfig, private readonly db?: OtpDatabase) {}

  private get database() {
    if (!this.db) throw new AppError('INTERNAL_SERVER_ERROR', 'OTP storage is unavailable', 503);
    return this.db.otpChallenge;
  }

  private hash(phone: string, otp: string) {
    return createHmac('sha256', this.config.JWT_SECRET).update(`${phone}:${otp}`).digest('hex');
  }

  private baseUrl() {
    return (this.config.TWOFACTOR_BASE_URL?.trim() || 'https://2factor.in').replace(/\/+$/, '');
  }

  async request(phone: string) {
    const otp = randomInt(0, 1_000_000).toString().padStart(6, '0');
    const expiresAt = new Date(Date.now() + 5 * 60_000);
    await this.database.upsert({
      where: { phone },
      create: { phone, provider: 'twofactor', codeHash: this.hash(phone, otp), expiresAt, attempts: 0, lockedUntil: null },
      update: { provider: 'twofactor', codeHash: this.hash(phone, otp), expiresAt, attempts: 0, lockedUntil: null }
    });

    const apiKey = this.config.TWOFACTOR_API_KEY!;
    const url = `${this.baseUrl()}/API/V1/${encodeURIComponent(apiKey)}/SMS/${encodeURIComponent(`+91${phone}`)}/${otp}`;
    try {
      const response = await fetch(url, { method: 'POST', headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(10_000) });
      const raw = await response.text();
      let body: { Status?: string; status?: string } = {};
      try { body = JSON.parse(raw) as typeof body; } catch { /* handled as provider failure */ }
      const status = String(body.Status ?? body.status ?? '').toLowerCase();
      if (!response.ok || !['success', 'sent'].includes(status)) throw new Error('2Factor rejected the OTP request');
    } catch {
      await this.database.delete({ where: { phone } }).catch(() => undefined);
      throw new AppError('INTERNAL_SERVER_ERROR', 'OTP provider unavailable', 502);
    }
  }

  async verify(phone: string, otp: string) {
    const record = await this.database.findUnique({ where: { phone } });
    if (!record) return 'expired' as const;
    if (Date.now() >= record.expiresAt.getTime()) {
      await this.database.delete({ where: { phone } }).catch(() => undefined);
      return 'expired' as const;
    }
    if (record.lockedUntil && Date.now() < record.lockedUntil.getTime()) return 'locked' as const;
    const nextAttempts = record.attempts + 1;
    if (nextAttempts > 5) {
      await this.database.update({ where: { phone }, data: { attempts: nextAttempts, lockedUntil: new Date(Date.now() + 15 * 60_000) } });
      return 'locked' as const;
    }
    const expected = Buffer.from(record.codeHash);
    const actual = Buffer.from(this.hash(phone, otp));
    const matched = expected.length === actual.length && timingSafeEqual(expected, actual);
    if (!matched) {
      await this.database.update({ where: { phone }, data: { attempts: nextAttempts, ...(nextAttempts === 5 ? { lockedUntil: new Date(Date.now() + 15 * 60_000) } : {}) } });
      return 'invalid' as const;
    }
    await this.database.delete({ where: { phone } });
    return 'approved' as const;
  }
}

export function createOtpProvider(config: AppConfig, db?: OtpDatabase): OtpProvider {
  if (config.OTP_PROVIDER === 'twilio') return new TwilioVerifyProvider(config);
  if (config.OTP_PROVIDER === 'twofactor') return new TwoFactorOtpProvider(config, db);
  return new DevelopmentOtpProvider(config);
}
