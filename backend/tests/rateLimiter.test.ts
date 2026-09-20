import { describe, expect, it } from 'vitest';
import { DatabaseAuthenticationRateLimiter, OtpRateLimiter } from '../src/services/rateLimiter.js';

describe('OTP rate limiting', () => {
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
