import { afterAll, describe, expect, it } from 'vitest';
import { PrismaClient } from '@prisma/client';
import { ActivityService } from '../src/services/activityService.js';
import { SyncService } from '../src/services/syncService.js';

// Read-only checks against the deterministic performance fixture; never an
// application database. Set this separately from DATABASE_URL intentionally.
const url = process.env.PERF_AUDIT_DATABASE_URL;
const enabled = !!url && new URL(url).pathname === '/kabadiwala_perf_test';
describe.skipIf(!enabled)('high-volume database change feeds', () => {
  if (!enabled) return;
  const db = new PrismaClient({ datasources: { db: { url: url! } } });
  afterAll(async () => db.$disconnect());
  it('drains every Collector offer and notification without duplicates', async () => {
    const service = new ActivityService(db);
    const offers = new Set<string>();
    const notifications = new Set<string>();
    const timings: number[] = [];
    let cursor: string | undefined;
    let complete = false;
    for (let page = 0; page < 100; page++) {
      const started = performance.now();
      const result = await service.changes({ collectorId: 'perf-collector', role: 'COLLECTOR' }, undefined, cursor);
      timings.push(performance.now() - started);
      expect(result.notifications.length).toBeLessThanOrEqual(100);
      expect(result.changed.offerIds.length).toBeLessThanOrEqual(100);
      for (const id of result.changed.offerIds) { expect(offers.has(id)).toBe(false); offers.add(id); }
      for (const row of result.notifications) { expect(row.accountId).toBe('perf-collector'); expect(notifications.has(row.id)).toBe(false); notifications.add(row.id); }
      cursor = result.nextCursor;
      if (!result.hasMore) { complete = true; break; }
    }
    expect(complete).toBe(true);
    expect(offers.size).toBe(await db.bulkOffer.count({ where: { recyclerId: 'perf-recycler' } }));
    expect(notifications.size).toBe(await db.notificationEvent.count({ where: { accountId: 'perf-collector' } }));
    const sorted = timings.sort((a, b) => a - b);
    const percentile = (p: number) => Math.round(sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * p) - 1)]);
    console.info(JSON.stringify({ event: 'activity_fixture_latency', pages: timings.length, offers: offers.size, notifications: notifications.size, p50Ms: percentile(.5), p95Ms: percentile(.95), p99Ms: percentile(.99) }));
  }, 180000);
  it('bounds the sync page and returns a resumable cursor', async () => {
    const result = await new SyncService(db, {} as never).changes('perf-collector', undefined, 'COLLECTOR', undefined, true);
    expect(result.hasMore).toBe(true);
    expect(result.changes.bulkLots.length).toBe(100);
    expect(result.changes.inventoryMovements.length).toBe(100);
    expect(result.nextCursor).toBeTruthy();
  }, 60000);
});
