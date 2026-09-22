import { describe, expect, it, vi } from 'vitest';
import { isTransientTransactionConflict, withTransactionRetry } from '../src/utils/transactionRetry.js';

describe('transaction retry helper', () => {
  it('reruns the complete operation after a Prisma write conflict', async () => {
    let calls = 0;
    const delay = vi.fn(async () => undefined);

    const result = await withTransactionRetry(async () => {
      calls += 1;
      if (calls < 3) {
        const error = new Error('Transaction failed due to a write conflict or deadlock. Please retry your transaction') as Error & { code?: string };
        error.code = 'P2034';
        throw error;
      }
      return 'committed';
    }, { delay });

    expect(result).toBe('committed');
    expect(calls).toBe(3);
    expect(delay).toHaveBeenCalledTimes(2);
  });

  it('does not retry non-transient errors', async () => {
    const failure = new Error('validation failed');
    const operation = vi.fn(async () => { throw failure; });

    await expect(withTransactionRetry(operation, { delay: vi.fn(async () => undefined) })).rejects.toBe(failure);
    expect(operation).toHaveBeenCalledTimes(1);
  });

  it('recognizes the provider message when Prisma omits the error code', () => {
    expect(isTransientTransactionConflict(new Error('Transaction failed due to a write conflict or a deadlock'))).toBe(true);
    expect(isTransientTransactionConflict(new Error('A different request failed'))).toBe(false);
  });
});
