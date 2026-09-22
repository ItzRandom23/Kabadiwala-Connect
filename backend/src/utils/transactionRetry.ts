export type TransactionRetryOptions = {
  attempts?: number;
  baseDelayMs?: number;
  delay?: (milliseconds: number) => Promise<void>;
};

/**
 * Prisma reports MongoDB transaction write conflicts and deadlocks as P2034.
 * These are safe to retry only by rerunning the complete transaction callback;
 * retrying a single write inside an already-aborted transaction is not safe.
 */
export function isTransientTransactionConflict(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false;
  if ((error as { code?: unknown }).code === 'P2034') return true;
  const message = error instanceof Error ? error.message : String(error);
  return /write conflict|deadlock|transaction failed due to a .*conflict/i.test(message);
}

export async function withTransactionRetry<T>(
  operation: () => Promise<T>,
  options: TransactionRetryOptions = {}
): Promise<T> {
  const attempts = Math.max(1, Math.floor(options.attempts ?? 3));
  const baseDelayMs = Math.max(0, options.baseDelayMs ?? 25);
  const delay = options.delay ?? ((milliseconds: number) => new Promise<void>(resolve => setTimeout(resolve, milliseconds)));
  let lastError: unknown;

  for (let attempt = 0; attempt < attempts; attempt += 1) {
    try {
      return await operation();
    } catch (error) {
      lastError = error;
      if (!isTransientTransactionConflict(error) || attempt === attempts - 1) throw error;
      await delay(baseDelayMs * (2 ** attempt));
    }
  }

  throw lastError instanceof Error ? lastError : new Error('Transaction failed');
}
