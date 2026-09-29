import { randomUUID } from 'node:crypto';
import type { RequestHandler } from 'express';

const requestIdPattern = /^[A-Za-z0-9._:-]{1,96}$/;
const WINDOW_MS = 60_000;
const MAX_SAMPLES = 2_048;
const p95WarningMs = Number.isFinite(Number(process.env.API_P95_WARN_MS)) && Number(process.env.API_P95_WARN_MS) > 0
  ? Number(process.env.API_P95_WARN_MS) : 1_000;
type LatencyBucket = { samples: number[]; requests: number; failures: number };
let windowStartedAt = Date.now();
const latencyBuckets = new Map<string, LatencyBucket>();

function flushLatencyWindow(now: number) {
  if (now - windowStartedAt < WINDOW_MS) return;
  for (const [operation, bucket] of latencyBuckets) {
    const sorted = bucket.samples.sort((a, b) => a - b);
    const quantile = (fraction: number) => sorted.length ? sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * fraction) - 1)] : null;
    const p95Ms = quantile(.95);
    console.info(JSON.stringify({ event: 'api_latency_window', operation, windowMs: now - windowStartedAt, requests: bucket.requests, failures: bucket.failures, p50Ms: quantile(.5), p95Ms, p99Ms: quantile(.99) }));
    if (bucket.requests >= 20 && p95Ms != null && p95Ms > p95WarningMs) {
      console.warn(JSON.stringify({ event: 'api_latency_regression', operation, requests: bucket.requests, p95Ms, thresholdMs: p95WarningMs }));
    }
  }
  latencyBuckets.clear();
  windowStartedAt = now;
}

export const requestContext: RequestHandler = (req, res, next) => {
  const supplied = req.header('x-request-id');
  const id = supplied && requestIdPattern.test(supplied) ? supplied : randomUUID();
  req.requestId = id;
  res.setHeader('x-request-id', id);
  const start = process.hrtime.bigint();
  res.on('finish', () => {
    const now = Date.now();
    flushLatencyWindow(now);
    // Express's route pattern contains parameter names, never user IDs or addresses.
    const operation = `${req.method} ${req.baseUrl}${req.route?.path ?? '/unmatched'}`;
    const durationMs = Number(process.hrtime.bigint() - start) / 1_000_000;
    const bucket = latencyBuckets.get(operation) ?? { samples: [], requests: 0, failures: 0 };
    bucket.requests++;
    if (res.statusCode >= 500) bucket.failures++;
    // Bounded sampling keeps observability from growing with traffic.
    if (bucket.samples.length < MAX_SAMPLES) bucket.samples.push(durationMs);
    else bucket.samples[bucket.requests % MAX_SAMPLES] = durationMs;
    latencyBuckets.set(operation, bucket);
    console.log(JSON.stringify({ event: 'api_request', requestId: id, operation, status: res.statusCode, durationMs: Math.round(durationMs * 100) / 100 }));
  });
  next();
};
