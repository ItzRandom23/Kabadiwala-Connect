import { AppError } from '../utils/errors.js';

export const DEFAULT_DAILY_PICKUP_CAPACITY = 8;
export const PICKUP_MIN_LEAD_MS = 90 * 60 * 1000;
export const PICKUP_MAX_HORIZON_MS = 14 * 24 * 60 * 60 * 1000;

/**
 * Pickup times are intentionally validated on the server. The Android client
 * may suggest convenient choices, but it cannot reserve an expired or
 * unreasonably distant slot by changing its request payload.
 */
export function validatePickupSlot(value: string, now = new Date()) {
  const slot = new Date(value);
  if (Number.isNaN(slot.getTime())) throw new AppError('VALIDATION_ERROR', 'Pickup time is invalid', 422, { code: 'PICKUP_SLOT_INVALID' });
  if (slot.getTime() < now.getTime() + PICKUP_MIN_LEAD_MS) throw new AppError('CONFLICT', 'Choose a pickup time at least 90 minutes from now', 409, { code: 'PICKUP_SLOT_TOO_SOON' });
  if (slot.getTime() > now.getTime() + PICKUP_MAX_HORIZON_MS) throw new AppError('CONFLICT', 'Pickup time must be within the next 14 days', 409, { code: 'PICKUP_SLOT_TOO_FAR' });
  if (slot.getUTCSeconds() !== 0 || slot.getUTCMilliseconds() !== 0 || ![0, 30].includes(slot.getUTCMinutes())) {
    throw new AppError('VALIDATION_ERROR', 'Pickup time must start on the hour or half-hour', 422, { code: 'PICKUP_SLOT_ALIGNMENT' });
  }
  return slot;
}

export function pickupDayKey(value: Date, timeZone = 'Asia/Kolkata') {
  const parts = new Intl.DateTimeFormat('en-US', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(value);
  const year = parts.find(part => part.type === 'year')?.value ?? '0000';
  const month = parts.find(part => part.type === 'month')?.value ?? '01';
  const day = parts.find(part => part.type === 'day')?.value ?? '01';
  return `${year}-${month}-${day}`;
}

async function reserveDay(tx: any, collectorId: string, slot: Date) {
  const dayKey = pickupDayKey(slot);
  const collector = await tx.collector.findUnique({ where: { id: collectorId }, select: { accountStatus: true, dailyPickupCapacity: true } });
  if (!collector || collector.accountStatus !== 'ACTIVE') throw new AppError('CONFLICT', 'Collector is not available for pickup scheduling', 409, { code: 'COLLECTOR_NOT_AVAILABLE' });
  const capacity = Math.max(1, Math.min(100, Number(collector.dailyPickupCapacity ?? DEFAULT_DAILY_PICKUP_CAPACITY)));
  const day = await tx.collectorPickupDay.upsert({
    where: { collectorId_dayKey: { collectorId, dayKey } },
    update: {},
    create: { collectorId, dayKey, capacity }
  });
  const claimed = await tx.collectorPickupDay.updateMany({ where: { id: day.id, bookedCount: { lt: day.capacity } }, data: { bookedCount: { increment: 1 } } });
  if (!claimed.count) throw new AppError('CONFLICT', 'That collector has no pickup capacity on the selected day', 409, { code: 'PICKUP_CAPACITY_FULL', dayKey });
  return dayKey;
}

async function releaseDay(tx: any, collectorId: string, slot: Date | null | undefined) {
  if (!slot) return;
  const dayKey = pickupDayKey(slot);
  await tx.collectorPickupDay.updateMany({ where: { collectorId, dayKey, bookedCount: { gt: 0 } }, data: { bookedCount: { decrement: 1 } } });
}

/** Moves a reservation only when the local operating day changes. */
export async function movePickupDay(tx: any, collectorId: string, nextSlot: Date, previousSlot?: Date | null) {
  const nextDay = pickupDayKey(nextSlot);
  const previousDay = previousSlot ? pickupDayKey(previousSlot) : null;
  if (nextDay === previousDay) return nextDay;
  await reserveDay(tx, collectorId, nextSlot);
  await releaseDay(tx, collectorId, previousSlot);
  return nextDay;
}

export async function releasePickupDay(tx: any, collectorId: string, slot?: Date | null) {
  await releaseDay(tx, collectorId, slot);
}
