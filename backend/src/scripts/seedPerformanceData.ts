import { PrismaClient, MaterialCategory } from '@prisma/client';

/** Deterministic load fixture. It deliberately refuses every non-disposable database. */
const url = process.env.DATABASE_URL;
const databaseName = url ? decodeURIComponent(new URL(url).pathname.replace(/^\//, '')) : '';
if (process.env.APP_ENV !== 'testing' || process.env.NODE_ENV === 'production' ||
    databaseName !== 'kabadiwala_perf_test' || process.env.PERF_SEED_CONFIRM !== databaseName) {
  throw new Error('Performance seed requires APP_ENV=testing, database kabadiwala_perf_test and PERF_SEED_CONFIRM=kabadiwala_perf_test.');
}

const db = new PrismaClient();
const scale = Number(process.env.PERF_SEED_SCALE ?? 1000);
if (!Number.isInteger(scale) || scale < 100 || scale > 5000) throw new Error('PERF_SEED_SCALE must be 100–5000.');
const base = Date.parse('2026-01-01T12:00:00.000Z');
const when = (n: number) => new Date(base - n * 60_000);
const categories = Object.values(MaterialCategory);

async function batches<T>(rows: T[], create: (batch: T[]) => Promise<unknown>) {
  for (let i = 0; i < rows.length; i += 250) await create(rows.slice(i, i + 250));
}

async function main() {
  if (await db.notificationEvent.findFirst({ where: { id: 'perf-notification-0' }, select: { id: true } })) {
    throw new Error('Performance fixture already exists. Use a fresh disposable database for reproducible measurements.');
  }
  await db.collector.createMany({ data: [
    { id: 'perf-household', email: 'perf-household@example.invalid', displayName: 'Performance Household', areaName: 'Rohini, Delhi', preferredLanguage: 'ENGLISH', latitude: 28.72, longitude: 77.11 },
    { id: 'perf-collector', email: 'perf-collector@example.invalid', displayName: 'Performance Collector', areaName: 'Rohini, Delhi', preferredLanguage: 'ENGLISH', latitude: 28.72, longitude: 77.11, pickupMaxDistanceKm: 50 }
  ] });
  await db.recycler.create({ data: { id: 'perf-recycler', name: 'Performance Recycler', areaName: 'Rohini, Delhi', address: 'Performance fixture', authorizationStatus: 'VERIFIED', pickupAvailability: 'FLEXIBLE', maxPickupDistanceKm: 50, operatingHours: {} } });
  await db.user.createMany({ data: [
    { id: 'perf-household-user', email: 'perf-household@example.invalid', role: 'HOUSEHOLD', collectorProfileId: 'perf-household' },
    { id: 'perf-collector-user', email: 'perf-collector@example.invalid', role: 'COLLECTOR', collectorProfileId: 'perf-collector' },
    { id: 'perf-recycler-user', email: 'perf-recycler@example.invalid', role: 'RECYCLER', recyclerProfileId: 'perf-recycler' }
  ] });
  const listings = Array.from({ length: scale }, (_, i) => ({ id: `perf-listing-${i}`, householdId: 'perf-household', materialCategory: categories[i % categories.length], estimatedWeight: 1 + i % 10, condition: 'INTACT' as const, areaName: 'Rohini, Delhi', status: 'MATCHED' as const, createdAt: when(i) }));
  await batches(listings, data => db.householdListing.createMany({ data }));
  const pickups = Array.from({ length: scale }, (_, i) => ({ id: `perf-pickup-${i}`, listingId: `perf-listing-${i}`, householdId: 'perf-household', kabadiwalaId: 'perf-collector', status: 'COMPLETED' as const, actualWeight: 1 + i % 10, finalCategory: categories[i % categories.length], finalAmount: 100 + i % 100, createdAt: when(i) }));
  await batches(pickups, data => db.pickupRequest.createMany({ data }));
  await db.inventoryBalance.create({ data: { id: 'perf-balance', kabadiwalaId: 'perf-collector', materialCategory: 'OTHER', availableKg: scale * 5 } });
  const movements = Array.from({ length: scale }, (_, i) => ({ id: `perf-movement-${i}`, inventoryBalanceId: 'perf-balance', kabadiwalaId: 'perf-collector', movementType: 'PICKUP_IN', quantityKg: 1, availableBefore: i, availableAfter: i + 1, reservedBefore: 0, reservedAfter: 0, soldBefore: 0, soldAfter: 0, sourceType: 'PICKUP_REQUEST', sourceId: `perf-pickup-${i}`, createdAt: when(i) }));
  await batches(movements, data => db.inventoryMovement.createMany({ data }));
  const lots = Array.from({ length: scale }, (_, i) => ({ id: `perf-lot-${i}`, kabadiwalaId: 'perf-collector', materialCategory: categories[i % categories.length], quantityKg: 2 + i % 10, askingRatePerKg: 100 + i % 50, areaName: 'Rohini, Delhi', status: 'LISTED' as const, createdAt: when(i) }));
  await batches(lots, data => db.bulkLot.createMany({ data }));
  const offers = Array.from({ length: scale }, (_, i) => ({ id: `perf-offer-${i}`, bulkLotId: `perf-lot-${i}`, recyclerId: 'perf-recycler', offeredRatePerKg: 90 + i % 50, createdAt: when(i) }));
  await batches(offers, data => db.bulkOffer.createMany({ data }));
  const demands = Array.from({ length: scale }, (_, i) => ({ id: `perf-demand-${i}`, recyclerId: 'perf-recycler', materialCategory: categories[i % categories.length], minimumLotKg: 1, requiredQuantityKg: 100, procurementRadiusKm: 50, createdAt: when(i) }));
  await batches(demands, data => db.procurementRequirement.createMany({ data }));
  await db.pickupConversation.create({ data: { id: 'perf-conversation', pickupRequestId: 'perf-pickup-0', householdId: 'perf-household', kabadiwalaId: 'perf-collector' } });
  const messages = Array.from({ length: scale * 3 }, (_, i) => ({ id: `perf-message-${i}`, conversationId: 'perf-conversation', senderId: i % 2 ? 'perf-household' : 'perf-collector', senderRole: i % 2 ? 'HOUSEHOLD' as const : 'COLLECTOR' as const, clientMessageId: `perf-client-${i}`, body: `Performance fixture message ${i}`, createdAt: when(i) }));
  await batches(messages, data => db.pickupChatMessage.createMany({ data }));
  const notifications = Array.from({ length: scale * 3 }, (_, i) => ({ id: `perf-notification-${i}`, accountId: i % 2 ? 'perf-household' : 'perf-collector', type: 'PERFORMANCE_FIXTURE', title: 'Performance fixture', body: `Fixture ${i}`, createdAt: when(i) }));
  await batches(notifications, data => db.notificationEvent.createMany({ data }));
  console.info(JSON.stringify({ event: 'performance_seed_complete', scale, listings: scale, pickups: scale, inventoryMovements: scale, lots: scale, offers: scale, demands: scale, messages: scale * 3, notifications: scale * 3 }));
}

main().catch(error => { console.error(error); process.exitCode = 1; }).finally(() => db.$disconnect());
