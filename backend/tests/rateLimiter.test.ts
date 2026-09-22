import { describe, expect, it } from 'vitest';
import { DatabaseAuthenticationRateLimiter, OtpRateLimiter } from '../src/services/rateLimiter.js';

describe('OTP rate limiting', () => {
  it('caps the repeated-request recovery window at two minutes by default', async () => {
    const limiter = new OtpRateLimiter({ requestCooldownMs: 0 });
    for (let attempt = 0; attempt < 5; attempt++) await limiter.check('9876543210', 'test-ip', 'request');

    const error = await limiter.check('9876543210', 'test-ip', 'request').then(() => null).catch((value: any) => value);
    expect(error).toMatchObject({
      code: 'OTP_RATE_LIMITED',
      status: 429,
      details: { retryAfterSeconds: expect.any(Number) }
    });
    expect(error.details.retryAfterSeconds).toBeLessThanOrEqual(120);
  });

  it('returns a retry window for repeated in-memory requests', async () => {
    const limiter = new OtpRateLimiter();
    await limiter.check('9876543210', 'test-ip', 'request');

    await expect(limiter.check('9876543210', 'test-ip', 'request')).rejects.toMatchObject({
      code: 'OTP_COOLDOWN',
      status: 429,
      details: { retryAfterSeconds: expect.any(Number) }
    });
  });

  it('applies the same resend cooldown to the shared database limiter', async () => {
    const buckets = new Map<string, { id: string; count: number; resetAt: Date; lastAt: Date }>();
    const db = {
      requestRateLimit: {
        findUnique: async ({ where }: { where: { id: string } }) => buckets.get(where.id) ?? null,
        upsert: async ({ where, create, update }: any) => {
          const current = buckets.get(where.id);
          const bucket = current ?? { ...create };
          if (current) bucket.count += update.count.increment;
          buckets.set(where.id, bucket);
          return bucket;
        }
      }
    } as any;
    const limiter = new DatabaseAuthenticationRateLimiter(db);

    await limiter.check('9876543210', 'test-ip', 'request');
    await expect(limiter.check('9876543210', 'test-ip', 'request')).rejects.toMatchObject({
      code: 'OTP_COOLDOWN',
      status: 429,
      details: { retryAfterSeconds: expect.any(Number) }
    });
  });
});
