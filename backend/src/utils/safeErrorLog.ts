const sensitiveKey = /(authorization|cookie|password|passcode|otp|token|secret|api[-_]?key|refresh|access)/i;

const redactText = (value: string): string => value
  .replace(/Bearer\s+[^\s,;]+/gi, 'Bearer [REDACTED]')
  .replace(/((?:password|passcode|otp|token|secret|api[-_]?key)\s*[:=]\s*)([^\s,;]+)/gi, '$1[REDACTED]');

/**
 * Keeps development diagnostics useful without serialising arbitrary thrown
 * objects, request payloads, or credentials into application logs.
 */
export const safeErrorLog = (value: unknown, depth = 0, seen = new WeakSet<object>()): unknown => {
  if (value == null || typeof value === 'number' || typeof value === 'boolean') return value;
  if (typeof value === 'string') return redactText(value);
  if (depth > 5) return '[Truncated]';

  if (value instanceof Error) {
    return {
      name: value.name,
      message: redactText(value.message),
      ...(value.stack ? { stack: redactText(value.stack) } : {})
    };
  }

  if (typeof value !== 'object') return String(value);
  if (seen.has(value)) return '[Circular]';
  seen.add(value);

  if (Array.isArray(value)) return value.slice(0, 50).map(item => safeErrorLog(item, depth + 1, seen));

  const output: Record<string, unknown> = {};
  for (const [key, entry] of Object.entries(value)) {
    output[key] = sensitiveKey.test(key) ? '[REDACTED]' : safeErrorLog(entry, depth + 1, seen);
  }
  return output;
};
