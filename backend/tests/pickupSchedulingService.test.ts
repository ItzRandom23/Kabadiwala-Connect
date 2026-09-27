import { describe, expect, it, vi } from 'vitest';
import { movePickupDay, pickupDayKey, validateCollectorPickupSlot, validatePickupSlot } from '../src/services/pickupSchedulingService.js';

describe('pickup scheduling policy', () => {
  it('accepts aligned near-term slots and rejects unsafe windows', () => {
    const now = new Date('2026-09-18T08:00:00.000Z');
    expect(validatePickupSlot('2026-09-18T08:15:00.000Z', now)).toEqual(new Date('2026-09-18T08:15:00.000Z'));
    expect(() => validatePickupSlot('2026-09-18T08:14:00.000Z', now)).toThrow(/15 minutes/);
    expect(() => validatePickupSlot('2026-10-03T10:00:00.000Z', now)).toThrow(/14 days/);
    expect(() => validatePickupSlot('2026-09-18T08:20:00.000Z', now)).toThrow(/15-minute interval/);
  });

  it('allows collector scheduling 15 minutes after acceptance while retaining work-hour and interval rules', () => {
    const now = new Date('2026-09-18T08:00:00.000Z');
    const acceptedAt = new Date('2026-09-18T08:00:00.000Z');
    expect(validateCollectorPickupSlot('2026-09-18T08:15:00.000Z', acceptedAt, now)).toEqual(new Date('2026-09-18T08:15:00.000Z'));
    expect(() => validateCollectorPickupSlot('2026-09-18T08:14:00.000Z', acceptedAt, now)).toThrow(/15 minutes/);
    expect(() => validateCollectorPickupSlot('2026-09-18T08:16:00.000Z', acceptedAt, now)).toThrow(/15-minute interval/);
    expect(() => validateCollectorPickupSlot('2026-09-18T12:35:00.000Z', acceptedAt, now)).toThrow(/10:30 AM/);
  });

  it('requires at least 15 minutes from now when an old accepted pickup is rescheduled', () => {
    const now = new Date('2026-09-18T08:00:00.000Z');
    const acceptedAt = new Date('2026-09-18T07:00:00.000Z');
    expect(validateCollectorPickupSlot('2026-09-18T08:15:00.000Z', acceptedAt, now)).toEqual(new Date('2026-09-18T08:15:00.000Z'));
    expect(() => validateCollectorPickupSlot('2026-09-18T08:00:00.000Z', acceptedAt, now)).toThrow(/15 minutes/);
  });

  it('moves one reservation between operating days and enforces capacity', async () => {
    const days = new Map<string, { id: string; collectorId: string; dayKey: string; capacity: number; bookedCount: number }>();
    const tx = {
      collector: { findUnique: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE', dailyPickupCapacity: 2 }) },
      collectorPickupDay: {
        upsert: vi.fn().mockImplementation(async ({ where, create }: any) => {
          const key = `${where.collectorId_dayKey.collectorId}:${where.collectorId_dayKey.dayKey}`;
          const existing = days.get(key);
          if (existing) return existing;
          const day = { id: `day-${days.size + 1}`, ...create, bookedCount: 0 };
          days.set(key, day);
          return day;
        }),
        updateMany: vi.fn().mockImplementation(async ({ where, data }: any) => {
          const day = [...days.values()].find(item => item.id === where.id) ?? [...days.values()].find(item => item.collectorId === where.collectorId && item.dayKey === where.dayKey);
          if (!day) return { count: 0 };
          if (where.bookedCount?.lt !== undefined && !(day.bookedCount < where.bookedCount.lt)) return { count: 0 };
          if (where.bookedCount?.gt !== undefined && !(day.bookedCount > where.bookedCount.gt)) return { count: 0 };
          if (data.bookedCount?.increment) day.bookedCount += data.bookedCount.increment;
          if (data.bookedCount?.decrement) day.bookedCount -= data.bookedCount.decrement;
          return { count: 1 };
        })
      }
    };
    const firstDay = new Date('2026-09-18T10:00:00.000Z');
    const secondSameDay = new Date('2026-09-18T12:00:00.000Z');
    const nextDay = new Date('2026-09-19T10:00:00.000Z');

    await movePickupDay(tx, 'collector-1', firstDay);
    await movePickupDay(tx, 'collector-1', secondSameDay);
    await expect(movePickupDay(tx, 'collector-1', new Date('2026-09-18T14:00:00.000Z'))).rejects.toThrow(/capacity/);
    await movePickupDay(tx, 'collector-1', nextDay, firstDay);

    expect(days.get(`collector-1:${pickupDayKey(firstDay)}`)?.bookedCount).toBe(1);
    expect(days.get(`collector-1:${pickupDayKey(nextDay)}`)?.bookedCount).toBe(1);
  });
});
