import { describe, expect, it } from 'vitest';
import { safeErrorLog } from '../src/utils/safeErrorLog.js';

describe('safeErrorLog', () => {
  it('redacts credential-shaped keys and bearer values', () => {
    const result = safeErrorLog({
      authorization: 'Bearer secret-token',
      request: { password: 'p@ss', otp: '123456', nested: 'Bearer another-token' },
      message: 'provider failed with token=leaked-token'
    }) as Record<string, unknown>;

    expect(result.authorization).toBe('[REDACTED]');
    expect(result.request).toEqual({ password: '[REDACTED]', otp: '[REDACTED]', nested: 'Bearer [REDACTED]' });
    expect(result.message).toBe('provider failed with token=[REDACTED]');
  });

  it('handles thrown errors and circular metadata without throwing', () => {
    const error = new Error('failed with apiKey=secret');
    const circular: Record<string, unknown> = { error };
    circular.self = circular;

    expect(safeErrorLog(circular)).toMatchObject({
      error: expect.objectContaining({ message: 'failed with apiKey=[REDACTED]' }),
      self: '[Circular]'
    });
  });
});
