import { Router } from 'express';
import { createHash, createHmac, randomBytes, timingSafeEqual } from 'node:crypto';
import { z } from 'zod';
import type { PrismaClient } from '@prisma/client';
import type { JwtService } from '../services/jwt.js';
import type { CollectorRepository } from '../repositories/collectorRepository.js';
import { requireAuth, requireRecycler } from '../middleware/auth.js';
import { AppError } from '../utils/errors.js';
import { assertInventoryInvariant, recordInventoryMovement } from '../services/inventoryLedger.js';
import { evaluateSettlementVariance, riskLevelForFlags } from '../services/settlementRules.js';

const material = z.enum(['CRT', 'LCD_PANEL', 'PCB', 'CABLE', 'COPPER', 'BATTERY', 'MOTOR', 'MAGNET', 'PLASTIC', 'OTHER']);
const positive = z.number().finite().positive();
const id = z.string().regex(/^[A-Za-z0-9_-]{1,100}$/);
const grade = z.string().trim().min(1).max(80).default('UNSPECIFIED');
const location = z.object({ type: z.enum(['COLLECTOR_LOCATION', 'RECYCLER_FACILITY', 'THIRD_PARTY']).default('COLLECTOR_LOCATION'), latitude: z.number().finite().min(-90).max(90).optional(), longitude: z.number().finite().min(-180).max(180).optional(), areaName: z.string().trim().max(160).optional() }).strict();

type Identity = { collectorId: string; role: 'COLLECTOR' | 'RECYCLER' };
type Store = any;

function parse<T>(schema: z.ZodType<T>, value: unknown): T {
  const result = schema.safeParse(value);
  if (!result.success) throw new AppError('VALIDATION_ERROR', 'Invalid request', 422, { details: result.error.flatten() });
  return result.data;
}

function jsonHash(value: unknown) {
  return createHash('sha256').update(JSON.stringify(value)).digest('hex');
}

function operationKey(req: any) {
  const value = req.header('idempotency-key')?.trim();
  if (value && !/^[A-Za-z0-9._:-]{8,160}$/.test(value)) throw new AppError('VALIDATION_ERROR', 'Invalid idempotency key', 422, { code: 'INVALID_IDEMPOTENCY_KEY' });
  return value || null;
}

const jsonValue = (value: unknown) => JSON.parse(JSON.stringify(value));

function signQr(encoded: string, secret: string) {
  return createHmac('sha256', secret).update(`kc-supply-handover-v1.${encoded}`).digest('base64url');
}

export function createSupplyHandoverQr(payload: Record<string, unknown>, secret: string) {
  const encoded = Buffer.from(JSON.stringify(payload), 'utf8').toString('base64url');
  return { data: `kc-supply-handover-v1.${encoded}.${signQr(encoded, secret)}`, encoded };
}

export function verifySupplyHandoverQr(value: string, secret: string) {
  const parts = value.split('.');
  if (parts.length !== 3 || parts[0] !== 'kc-supply-handover-v1' || !parts[1] || !parts[2]) throw new AppError('VALIDATION_ERROR', 'Invalid handover QR', 422, { code: 'INVALID_HANDOVER_QR' });
  const expected = signQr(parts[1], secret);
  const actual = Buffer.from(parts[2]);
  const expectedBytes = Buffer.from(expected);
  if (actual.length !== expectedBytes.length || !timingSafeEqual(actual, expectedBytes)) throw new AppError('VALIDATION_ERROR', 'Handover QR has been altered', 422, { code: 'INVALID_HANDOVER_SIGNATURE' });
  try {
    const payload = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8')) as Record<string, unknown>;
    if (payload.version !== 1 || typeof payload.referenceId !== 'string' || typeof payload.nonce !== 'string' || typeof payload.recyclerId !== 'string') throw new Error('invalid');
    return payload;
  } catch {
    throw new AppError('VALIDATION_ERROR', 'Invalid handover QR payload', 422, { code: 'INVALID_HANDOVER_QR' });
  }
}

async function audit(tx: Store, actorId: string, actorRole: string, event: string, entityType: string, entityId: string, metadata: Record<string, unknown>) {
  await tx.auditEvent.create({ data: { actorId, actorRole, event, entityType, entityId, metadata } });
  await tx.materialPassportEvent.create({ data: { entityType, entityId, eventType: event, actorId, actorRole, metadata, evidenceHash: jsonHash(metadata), occurredAt: new Date() } });
}

async function ensurePassport(tx: Store, collectorId: string) {
  const profile = await tx.collector.findUnique({ where: { id: collectorId }, select: { areaName: true, preferredLanguage: true } });
  if (!profile) throw new AppError('NOT_FOUND', 'Collector profile not found', 404, { code: 'COLLECTOR_NOT_FOUND' });
  return tx.collectorPassport.upsert({
    where: { collectorId },
    update: { operatingZone: profile.areaName, preferredLanguage: profile.preferredLanguage },
    create: { collectorId, operatingZone: profile.areaName, preferredLanguage: profile.preferredLanguage, materialCategories: [], platformLabels: ['BASIC'] }
  });
}

async function refreshPassport(tx: Store, collectorId: string) {
  const [profile, contributions, completedHandovers, safety, pickups, disputes, reviews] = await Promise.all([
    tx.collector.findUnique({ where: { id: collectorId }, select: { areaName: true, preferredLanguage: true, createdAt: true } }),
    tx.poolContribution.findMany({ where: { collectorId }, select: { materialCategory: true, quantityKg: true, finalAcceptedKg: true, status: true } }),
    tx.supplyHandover.findMany({ where: { collectorId, status: 'COMPLETED' }, select: { poolId: true, quotedWeightKg: true, finalAcceptedKg: true } }),
    tx.safetyProgress.count({ where: { collectorId, acknowledged: true } }),
    tx.pickupRequest.findMany({ where: { kabadiwalaId: collectorId }, select: { status: true, noShow: true, lateCancellation: true } }),
    tx.dispute.count({ where: { collectorId } }),
    tx.recyclerReview.findMany({ where: { collectorId, verified: true }, select: { rating: true, pickupReliability: true, paymentClarity: true } })
  ]);
  if (!profile) return null;
  const settledRows = contributions.filter((row: any) => row.status === 'SETTLED');
  const categories = [...new Set(contributions.map((row: any) => row.materialCategory))];
  const labels = ['BASIC'];
  if (safety > 0) labels.push('SAFETY MODULES COMPLETED');
  if (completedHandovers.length > 0 || settledRows.length > 0) labels.push('FORMAL HANDOVER HISTORY AVAILABLE');
  const completedDirectQuantity = completedHandovers.filter((row: any) => !row.poolId).reduce((sum: number, row: any) => sum + (row.finalAcceptedKg ?? row.quotedWeightKg), 0);
  const formalQuantityKg = Number((completedDirectQuantity + settledRows.reduce((sum: number, row: any) => sum + (row.finalAcceptedKg ?? row.quantityKg), 0)).toFixed(3));
  const completedTransactionCount = completedHandovers.length + settledRows.length;
  const passport = await tx.collectorPassport.upsert({
    where: { collectorId },
    update: { operatingZone: profile.areaName, preferredLanguage: profile.preferredLanguage, materialCategories: categories, completedTransactions: completedTransactionCount, formalHandoverCount: completedTransactionCount, formalQuantityKg, safetyModulesCompleted: safety, platformLabels: labels, verificationState: 'PLATFORM GENERATED' },
    create: { collectorId, operatingZone: profile.areaName, preferredLanguage: profile.preferredLanguage, materialCategories: categories, completedTransactions: completedTransactionCount, formalHandoverCount: completedTransactionCount, formalQuantityKg, safetyModulesCompleted: safety, platformLabels: labels, verificationState: 'PLATFORM GENERATED' }
  });
  const acceptedPickups = pickups.filter((row: any) => ['ACCEPTED', 'SCHEDULED', 'IN_TRANSIT', 'ARRIVED', 'WEIGHED', 'COMPLETED', 'CANCELLED', 'REASSIGNMENT_REQUIRED'].includes(row.status)).length;
  const completedPickups = pickups.filter((row: any) => row.status === 'COMPLETED').length;
  const cancelledPickups = pickups.filter((row: any) => ['CANCELLED', 'REASSIGNMENT_REQUIRED', 'REJECTED'].includes(row.status)).length;
  return { ...passport, activeSince: profile.createdAt, pickupMetrics: { accepted: acceptedPickups, completed: completedPickups, cancelled: cancelledPickups, noShow: pickups.filter((row: any) => row.noShow).length, lateCancellation: pickups.filter((row: any) => row.lateCancellation).length, completionRate: acceptedPickups ? Number((completedPickups / acceptedPickups).toFixed(3)) : null, cancellationRate: acceptedPickups ? Number((cancelledPickups / acceptedPickups).toFixed(3)) : null }, disputeRatio: completedTransactionCount ? Number((disputes / completedTransactionCount).toFixed(3)) : 0, feedback: { count: reviews.length, averageRating: reviews.length ? Number((reviews.reduce((sum: number, row: any) => sum + row.rating, 0) / reviews.length).toFixed(2)) : null, averagePickupReliability: reviews.filter((row: any) => row.pickupReliability != null).length ? Number((reviews.filter((row: any) => row.pickupReliability != null).reduce((sum: number, row: any) => sum + row.pickupReliability, 0) / reviews.filter((row: any) => row.pickupReliability != null).length).toFixed(2)) : null, averagePaymentClarity: reviews.filter((row: any) => row.paymentClarity != null).length ? Number((reviews.filter((row: any) => row.paymentClarity != null).reduce((sum: number, row: any) => sum + row.paymentClarity, 0) / reviews.filter((row: any) => row.paymentClarity != null).length).toFixed(2)) : null } };
}

function distanceKm(aLat?: number | null, aLng?: number | null, bLat?: number | null, bLng?: number | null) {
  if ([aLat, aLng, bLat, bLng].some(value => value == null)) return null;
  const radians = (value: number) => value * Math.PI / 180;
  const dLat = radians((bLat as number) - (aLat as number));
  const dLng = radians((bLng as number) - (aLng as number));
  const aa = Math.sin(dLat / 2) ** 2 + Math.cos(radians(aLat as number)) * Math.cos(radians(bLat as number)) * Math.sin(dLng / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(aa), Math.sqrt(1 - aa));
}

function authoritySnapshot(recycler: any) {
  return { status: recycler.authorizationStatus, authority: recycler.authorizationAuthority, type: recycler.authorizationType, reference: recycler.authorizationEvidenceReference, verificationSource: recycler.verificationSource, verifiedAt: recycler.verifiedAt, validUntil: recycler.authorizationValidUntil };
}

function routeReasons(recycler: any, distance: number | null, logisticsCost: number, confidence: string, baselineAvailable: boolean) {
  const reasons = [`Accepts this material`, `Platform verification status: ${recycler.authorizationStatus}`];
  if (recycler.pickupAvailability === 'TODAY' || recycler.pickupAvailability === 'THIS_WEEK') reasons.push('Pickup availability reduces collector travel');
  if (distance != null) reasons.push(`${distance.toFixed(1)} km from the collector profile`);
  reasons.push(`Estimated logistics cost: ₹${logisticsCost.toFixed(0)}`);
  reasons.push(baselineAvailable ? `${confidence} confidence from local reference data` : 'No reliable local baseline; no advantage claim');
  return reasons;
}

export function formalisationRoutes(jwt: JwtService, collectors: CollectorRepository, db: PrismaClient, signingSecret: string) {
  const router = Router();
  const store: Store = db;

  router.get('/kabadiwala/route-advantage', requireAuth(jwt, collectors), async (req, res) => {
    // Express exposes query values as strings, including Retrofit's numeric
    // query parameters. Coerce at the HTTP boundary so a valid Android request
    // is not rejected before route estimation runs.
    const input = parse(z.object({ materialCategory: material, quantityKg: z.coerce.number().finite().positive().max(100000), grade: grade.optional(), areaName: z.string().trim().max(160).optional() }), req.query);
    const collector = await store.collector.findUnique({ where: { id: req.identity!.collectorId }, select: { id: true, areaName: true, latitude: true, longitude: true } });
    const locationName = input.areaName ?? collector?.areaName;
    const city = locationName?.split(',').map((part: string) => part.trim()).filter(Boolean).at(-1);
    const price = await store.price.findFirst({ where: { materialCategory: input.materialCategory, ...(locationName ? { OR: [{ areaName: locationName }, ...(city ? [{ city }] : [])] } : {}) }, orderBy: { effectiveAt: 'desc' } }) ?? await store.price.findFirst({ where: { materialCategory: input.materialCategory }, orderBy: { effectiveAt: 'desc' } });
    const observations = price ? await store.priceHistory.count({ where: { priceId: price.id } }) : 0;
    const baseline = price ? Number((price.marketPrice * input.quantityKg).toFixed(2)) : null;
    const confidence = observations >= 20 && price?.qualityStatus === 'VALIDATED' ? 'HIGH' : observations >= 10 ? 'MEDIUM' : observations > 0 ? 'LOW' : 'INSUFFICIENT';
    const demand = await store.procurementRequirement.findMany({ where: { materialCategory: input.materialCategory, status: 'OPEN', OR: [{ deadline: null }, { deadline: { gt: new Date() } }] }, select: { recyclerId: true, minimumLotKg: true, maxRatePerKg: true, requiredQuantityKg: true } });
    const recyclers = await store.recycler.findMany({ where: { authorizationStatus: 'VERIFIED', OR: [{ authorizationValidUntil: null }, { authorizationValidUntil: { gt: new Date() } }], materials: { some: { category: input.materialCategory } } }, include: { materials: true, rates: true }, orderBy: { updatedAt: 'desc' }, take: 25 });
    const rows = recyclers.map((recycler: any) => {
      const rate = recycler.rates.find((item: any) => item.materialCategory === input.materialCategory)?.pricePerKg;
      if (!rate) return null;
      const distance = distanceKm(collector?.latitude, collector?.longitude, recycler.latitude, recycler.longitude);
      const pickup = recycler.pickupAvailability === 'TODAY' || recycler.pickupAvailability === 'THIS_WEEK';
      const logisticsCost = pickup ? 0 : Number(((distance ?? 8) * 12).toFixed(2));
      const relevantDemand = demand.filter((row: any) => row.recyclerId === recycler.id);
      const minimumLotKg = Math.max(...relevantDemand.map((row: any) => row.minimumLotKg), recycler.materials.find((row: any) => row.category === input.materialCategory)?.minAcceptableWeight ?? 0);
      const demandRelevant = relevantDemand.length > 0;
      const paymentReliability = recycler.rating == null ? null : Number(Math.min(1, Math.max(0, recycler.rating / 5)).toFixed(2));
      const platformFee = 0;
      const gross = Number((rate * input.quantityKg).toFixed(2));
      const net = Number((gross - logisticsCost - platformFee).toFixed(2));
      const advantage = baseline == null ? null : Number((net - baseline).toFixed(2));
      const advantagePercent = baseline && baseline > 0 && advantage != null ? Number((advantage / baseline * 100).toFixed(1)) : null;
      const reasons = routeReasons(recycler, distance, logisticsCost, confidence, baseline != null);
      if (minimumLotKg > 0) reasons.push(input.quantityKg >= minimumLotKg ? `The ${minimumLotKg} kg minimum is satisfied` : `The route minimum is ${minimumLotKg} kg`);
      reasons.push(demandRelevant ? 'Recycler demand is active for this material' : 'No active recycler demand was found for this material');
      if (paymentReliability != null) reasons.push(`Recent platform reliability signal: ${Math.round(paymentReliability * 100)}%`);
      return { recyclerId: recycler.id, recyclerName: recycler.name, materialCategory: input.materialCategory, quantityKg: input.quantityKg, offeredRatePerKg: rate, estimatedGrossValue: gross, grossValue: gross, logisticsCost, platformFee, estimatedNetValue: net, localBaseline: baseline, baselineNetValue: baseline, advantageValue: advantage, advantage, advantagePercent, confidence, observationCount: observations, minimumLotKg, demandRelevant, paymentReliability, authorization: authoritySnapshot(recycler), pickupAvailability: recycler.pickupAvailability, distanceKm: distance == null ? null : Number(distance.toFixed(1)), whyThisMatch: reasons, reasons, isDemo: price?.source === 'SYSTEM' };
    }).filter(Boolean);
    const sorted = rows.sort((a: any, b: any) => (b.estimatedNetValue - a.estimatedNetValue) || a.recyclerName.localeCompare(b.recyclerName));
    await Promise.all(sorted.map((row: any) => store.routeAdvantageEstimate.create({ data: { collectorId: req.identity!.collectorId, targetType: 'MATERIAL_QUERY', recyclerId: row.recyclerId, materialCategory: row.materialCategory, quantityKg: row.quantityKg, offeredRatePerKg: row.offeredRatePerKg, grossValue: row.grossValue, logisticsCost: row.logisticsCost, estimatedNetValue: row.estimatedNetValue, localBaseline: row.localBaseline, advantageValue: row.advantageValue, advantagePercent: row.advantagePercent, confidence: row.confidence, observationCount: row.observationCount, authorizationSnapshot: row.authorization, reasons: row.whyThisMatch, isDemo: row.isDemo, expiresAt: new Date(Date.now() + 30 * 60 * 1000) } })));
    res.json({ success: true, data: { items: sorted, baseline: price ? { minPrice: price.priceMin, maxPrice: price.priceMax, referencePrice: price.marketPrice, unit: price.unit, source: price.source, qualityStatus: price.qualityStatus, lastUpdated: price.effectiveAt, observationCount: observations, confidence, isDemo: price.source === 'SYSTEM' } : null, disclaimer: baseline == null ? 'Insufficient data for formal-route advantage. Showing verified recyclers without a savings claim.' : 'Net outcome is an estimate. Logistics assumptions and reference data must be confirmed in the field.' } });
  });

  router.get('/kabadiwala/pool-opportunities', requireAuth(jwt, collectors), async (req, res) => {
    const requirements = await store.procurementRequirement.findMany({ where: { status: 'OPEN', OR: [{ deadline: null }, { deadline: { gt: new Date() } }] }, orderBy: { createdAt: 'desc' }, take: 50 });
    const inventories = await store.inventoryBalance.findMany({ where: { availableKg: { gt: 0 } }, select: { materialCategory: true, availableKg: true, kabadiwalaId: true } });
    const pools = await store.pooledConsignment.findMany({ where: { status: { not: 'CANCELLED' } }, select: { id: true, requirementId: true, totalReservedKg: true, status: true } });
    const rows = requirements.map((requirement: any) => {
      const supply = inventories.filter((row: any) => row.materialCategory === requirement.materialCategory).reduce((sum: number, row: any) => sum + row.availableKg, 0);
      const existing = pools.filter((pool: any) => pool.requirementId === requirement.id).reduce((sum: number, pool: any) => sum + pool.totalReservedKg, 0);
      return { requirement, eligibleCollectorCount: new Set(inventories.filter((row: any) => row.materialCategory === requirement.materialCategory).map((row: any) => row.kabadiwalaId)).size, clusterAvailableKg: Number((supply + existing).toFixed(2)), supplyGapKg: Number(Math.max(0, requirement.minimumLotKg - supply - existing).toFixed(2)), thresholdMet: supply + existing >= requirement.minimumLotKg, existingPool: pools.find((pool: any) => pool.requirementId === requirement.id) ?? null };
    });
    res.json({ success: true, data: rows });
  });
  router.get('/kabadiwala/pools/suggestions', requireAuth(jwt, collectors), async (_req, res) => {
    const requirements = await store.procurementRequirement.findMany({ where: { status: 'OPEN', OR: [{ deadline: null }, { deadline: { gt: new Date() } }] }, select: { id: true, recyclerId: true, materialCategory: true, minimumLotKg: true, requiredQuantityKg: true, deadline: true }, take: 100 });
    const inventories = await store.inventoryBalance.findMany({ where: { availableKg: { gt: 0 } }, select: { materialCategory: true, availableKg: true, kabadiwalaId: true } });
    const activePools = await store.pooledConsignment.findMany({ where: { status: { in: ['FORMING', 'THRESHOLD_MET', 'LOCKED', 'PICKUP_SCHEDULED', 'IN_TRANSIT'] } }, select: { requirementId: true, totalReservedKg: true } });
    const suggestions = requirements.map((requirement: any) => {
      const rows = inventories.filter((row: any) => row.materialCategory === requirement.materialCategory);
      const reserved = activePools.filter((pool: any) => pool.requirementId === requirement.id).reduce((sum: number, pool: any) => sum + pool.totalReservedKg, 0);
      const eligibleSupplyKg = Number((rows.reduce((sum: number, row: any) => sum + row.availableKg, 0) + reserved).toFixed(2));
      const contributorsNeeded = new Set(rows.map((row: any) => row.kabadiwalaId)).size;
      return { recyclerDemandId: requirement.id, recyclerId: requirement.recyclerId, materialCategory: requirement.materialCategory, requiredKg: requirement.minimumLotKg, eligibleSupplyKg, contributorsNeeded, suggested: eligibleSupplyKg >= requirement.minimumLotKg, supplyGapKg: Number(Math.max(0, requirement.minimumLotKg - eligibleSupplyKg).toFixed(2)), deadline: requirement.deadline };
    }).filter((row: any) => row.suggested);
    res.json({ success: true, data: suggestions, disclaimer: 'Suggestions use aggregate eligible inventory. Other collectors remain private until they join a pool.' });
  });

  router.post('/kabadiwala/pools', requireAuth(jwt, collectors), async (req, res) => {
    const input = parse(z.object({ requirementId: id, areaName: z.string().trim().min(1).max(160) }), req.body);
    const requirement = await store.procurementRequirement.findFirst({ where: { id: input.requirementId, status: 'OPEN', OR: [{ deadline: null }, { deadline: { gt: new Date() } }] } });
    if (!requirement) throw new AppError('NOT_FOUND', 'Open recycler demand not found', 404, { code: 'DEMAND_NOT_FOUND' });
    const existing = await store.pooledConsignment.findFirst({ where: { requirementId: requirement.id, status: { in: ['FORMING', 'THRESHOLD_MET', 'LOCKED', 'PICKUP_SCHEDULED', 'IN_TRANSIT'] } } });
    if (existing) return res.json({ success: true, data: existing, message: 'An active pool already exists for this demand' });
    try {
      const pool = await store.$transaction(async (tx: Store) => {
        const created = await tx.pooledConsignment.create({ data: { requirementId: requirement.id, sourceKey: `REQUIREMENT:${requirement.id}`, recyclerId: requirement.recyclerId, materialCategory: requirement.materialCategory, preferredGrade: requirement.preferredGrade, minimumQuantityKg: requirement.minimumLotKg, targetQuantityKg: requirement.requiredQuantityKg, areaName: input.areaName, pickupRadiusKm: requirement.procurementRadiusKm, createdByCollectorId: req.identity!.collectorId } });
        await audit(tx, req.identity!.collectorId, 'COLLECTOR', 'POOL_CREATED', 'POOL', created.id, { requirementId: requirement.id, minimumQuantityKg: requirement.minimumLotKg });
        return created;
      });
      res.status(201).json({ success: true, data: pool });
    } catch (error: any) {
      // sourceKey is unique, so two collectors racing to open the same
      // opportunity converge on one canonical pool instead of producing a
      // 500 or duplicate consignment.
      if (error?.code === 'P2002') {
        const concurrent = await store.pooledConsignment.findUnique({ where: { sourceKey: `REQUIREMENT:${requirement.id}` } });
        if (concurrent) return res.json({ success: true, data: concurrent, message: 'An active pool already exists for this demand' });
      }
      throw error;
    }
  });

  router.get('/kabadiwala/pools', requireAuth(jwt, collectors), async (req, res) => {
    const contributed = await store.poolContribution.findMany({ where: { collectorId: req.identity!.collectorId }, select: { poolId: true } });
    const ids = [...new Set(contributed.map((row: any) => row.poolId))];
    const pools = await store.pooledConsignment.findMany({ where: { OR: [{ createdByCollectorId: req.identity!.collectorId }, ...(ids.length ? [{ id: { in: ids } }] : [])], status: { not: 'CANCELLED' } }, orderBy: { updatedAt: 'desc' }, take: 100 });
    const contributions = await store.poolContribution.findMany({ where: { poolId: { in: pools.map((row: any) => row.id) } }, orderBy: { createdAt: 'asc' } });
    res.json({ success: true, data: pools.map((pool: any) => {
      const rows = contributions.filter((row: any) => row.poolId === pool.id);
      const mine = rows.filter((row: any) => row.collectorId === req.identity!.collectorId);
      return { ...pool, contributorCount: new Set(rows.map((row: any) => row.collectorId)).size, otherContributedKg: Number(rows.filter((row: any) => row.collectorId !== req.identity!.collectorId).reduce((sum: number, row: any) => sum + row.quantityKg, 0).toFixed(3)), contributions: mine.map((row: any) => ({ ...row, isMine: true })) };
    }) });
  });

  router.post('/kabadiwala/pools/:poolId/join', requireAuth(jwt, collectors), async (req, res) => {
    const poolId = parse(id, req.params.poolId);
    const input = parse(z.object({ quantityKg: positive.max(100000), grade: grade.optional(), expectedRatePerKg: positive.max(1000000).optional() }), req.body);
    const result = await store.$transaction(async (tx: Store) => {
      const pool = await tx.pooledConsignment.findUnique({ where: { id: poolId } });
      if (!pool || ['CANCELLED', 'LOCKED', 'PICKUP_SCHEDULED', 'IN_TRANSIT', 'RECEIVED', 'SETTLED', 'REVIEW_REQUIRED'].includes(pool.status)) throw new AppError('CONFLICT', 'This pool is no longer accepting contributions', 409, { code: 'POOL_NOT_JOINABLE' });
      const existing = await tx.poolContribution.findUnique({ where: { poolId_collectorId: { poolId, collectorId: req.identity!.collectorId } } });
      if (existing && existing.status === 'RESERVED') {
        if (Math.abs(existing.quantityKg - input.quantityKg) > 0.0001) throw new AppError('CONFLICT', 'This collector already has a reserved contribution in this pool', 409, { code: 'POOL_DUPLICATE_CONTRIBUTION' });
        return existing;
      }
      if (existing && existing.status !== 'RELEASED') throw new AppError('CONFLICT', 'This collector already has a completed or review-locked contribution in this pool', 409, { code: 'POOL_DUPLICATE_CONTRIBUTION' });
      const balance = await tx.inventoryBalance.findFirst({ where: { kabadiwalaId: req.identity!.collectorId, materialCategory: pool.materialCategory, grade: input.grade } });
      if (!balance || balance.availableKg < input.quantityKg) throw new AppError('CONFLICT', 'Not enough available inventory for this contribution', 409, { code: 'POOL_INSUFFICIENT_INVENTORY' });
      const reserved = await tx.inventoryBalance.updateMany({ where: { id: balance.id, availableKg: { gte: input.quantityKg } }, data: { availableKg: { decrement: input.quantityKg }, reservedKg: { increment: input.quantityKg } } });
      if (!reserved.count) throw new AppError('CONFLICT', 'Inventory changed; refresh and try again', 409, { code: 'INVENTORY_RESERVATION_CONFLICT' });
      const afterBalance = await tx.inventoryBalance.findUniqueOrThrow({ where: { id: balance.id } });
      assertInventoryInvariant(afterBalance);
      await recordInventoryMovement(tx, balance, afterBalance, 'RESERVATION', input.quantityKg, 'POOL_CONTRIBUTION', poolId, { poolId });
      const rate = input.expectedRatePerKg ?? 0;
      const contribution = existing
        ? await tx.poolContribution.update({ where: { id: existing.id }, data: { inventoryBalanceId: balance.id, quantityKg: input.quantityKg, expectedRatePerKg: rate, expectedPayout: Number((rate * input.quantityKg).toFixed(2)), status: 'RESERVED' } })
        : await tx.poolContribution.create({ data: { poolId, collectorId: req.identity!.collectorId, inventoryBalanceId: balance.id, materialCategory: pool.materialCategory, grade: input.grade, quantityKg: input.quantityKg, expectedRatePerKg: rate, expectedPayout: Number((rate * input.quantityKg).toFixed(2)) } });
      const total = Number((pool.totalReservedKg + (existing?.status === 'RELEASED' ? input.quantityKg : input.quantityKg)).toFixed(2));
      const nextStatus = total >= pool.minimumQuantityKg ? 'THRESHOLD_MET' : 'FORMING';
      await tx.pooledConsignment.update({ where: { id: poolId }, data: { totalReservedKg: total, status: nextStatus } });
      await audit(tx, req.identity!.collectorId, 'COLLECTOR', 'POOL_CONTRIBUTION_RESERVED', 'POOL_CONTRIBUTION', contribution.id, { poolId, quantityKg: input.quantityKg, totalReservedKg: total });
      return contribution;
    });
    res.status(201).json({ success: true, data: result });
  });

  router.post('/kabadiwala/pools/:poolId/leave', requireAuth(jwt, collectors), async (req, res) => {
    const poolId = parse(id, req.params.poolId);
    await store.$transaction(async (tx: Store) => {
      const pool = await tx.pooledConsignment.findUnique({ where: { id: poolId } });
      const contribution = await tx.poolContribution.findUnique({ where: { poolId_collectorId: { poolId, collectorId: req.identity!.collectorId } } });
      if (!pool || !contribution || contribution.status !== 'RESERVED' || !['FORMING', 'THRESHOLD_MET'].includes(pool.status)) throw new AppError('CONFLICT', 'This contribution can no longer be released', 409, { code: 'POOL_CONTRIBUTION_NOT_RELEASABLE' });
      const beforeBalance = await tx.inventoryBalance.findUniqueOrThrow({ where: { id: contribution.inventoryBalanceId } });
      const released = await tx.inventoryBalance.updateMany({ where: { id: contribution.inventoryBalanceId, reservedKg: { gte: contribution.quantityKg } }, data: { availableKg: { increment: contribution.quantityKg }, reservedKg: { decrement: contribution.quantityKg } } });
      if (!released.count) throw new AppError('CONFLICT', 'Inventory reservation could not be released', 409, { code: 'INVENTORY_RELEASE_CONFLICT' });
      const afterBalance = await tx.inventoryBalance.findUniqueOrThrow({ where: { id: contribution.inventoryBalanceId } });
      assertInventoryInvariant(afterBalance);
      await recordInventoryMovement(tx, beforeBalance, afterBalance, 'RELEASE', contribution.quantityKg, 'POOL_CONTRIBUTION', contribution.id, { poolId });
      await tx.poolContribution.update({ where: { id: contribution.id }, data: { status: 'RELEASED' } });
      const total = Number(Math.max(0, pool.totalReservedKg - contribution.quantityKg).toFixed(2));
      await tx.pooledConsignment.update({ where: { id: poolId }, data: { totalReservedKg: total, status: total >= pool.minimumQuantityKg ? 'THRESHOLD_MET' : 'FORMING' } });
      await audit(tx, req.identity!.collectorId, 'COLLECTOR', 'POOL_CONTRIBUTION_RELEASED', 'POOL_CONTRIBUTION', contribution.id, { poolId, quantityKg: contribution.quantityKg, totalReservedKg: total });
    });
    res.json({ success: true });
  });

  router.post('/kabadiwala/pools/:poolId/lock', requireAuth(jwt, collectors), async (req, res) => {
    const poolId = parse(id, req.params.poolId);
    const pool = await store.pooledConsignment.findFirst({ where: { id: poolId, createdByCollectorId: req.identity!.collectorId } });
    if (!pool) throw new AppError('NOT_FOUND', 'Pool not found', 404, { code: 'POOL_NOT_FOUND' });
    if (pool.totalReservedKg < pool.minimumQuantityKg) throw new AppError('CONFLICT', 'Pool threshold has not been met', 409, { code: 'POOL_THRESHOLD_NOT_MET' });
    const updated = await store.pooledConsignment.updateMany({ where: { id: poolId, createdByCollectorId: req.identity!.collectorId, status: { in: ['THRESHOLD_MET', 'FORMING'] } }, data: { status: 'LOCKED' } });
    if (!updated.count) throw new AppError('CONFLICT', 'Pool is already locked or no longer actionable', 409, { code: 'POOL_ALREADY_LOCKED' });
    await store.$transaction(async (tx: Store) => audit(tx, req.identity!.collectorId, 'COLLECTOR', 'POOL_LOCKED', 'POOL', poolId, { totalReservedKg: pool.totalReservedKg }));
    res.json({ success: true, data: await store.pooledConsignment.findUnique({ where: { id: poolId } }) });
  });

  router.get('/recycler/pools', requireRecycler(jwt, db), async (req, res) => {
    const pools = await store.pooledConsignment.findMany({ where: { recyclerId: req.identity!.collectorId, status: { not: 'CANCELLED' } }, orderBy: { updatedAt: 'desc' }, take: 100 });
    const contributions = await store.poolContribution.findMany({ where: { poolId: { in: pools.map((row: any) => row.id) } }, orderBy: { createdAt: 'asc' } });
    res.json({ success: true, data: pools.map((pool: any) => ({ ...pool, contributions: contributions.filter((row: any) => row.poolId === pool.id).map((row: any) => ({ materialCategory: row.materialCategory, grade: row.grade, quantityKg: row.quantityKg, status: row.status, finalAcceptedKg: row.finalAcceptedKg })) })) });
  });

  router.get('/kabadiwala/demand-intelligence', requireAuth(jwt, collectors), async (req, res) => {
    const requirements = await store.procurementRequirement.findMany({ where: { status: 'OPEN', OR: [{ deadline: null }, { deadline: { gt: new Date() } }] }, orderBy: { createdAt: 'desc' }, take: 100 });
    const inventories = await store.inventoryBalance.findMany({ where: { availableKg: { gt: 0 } }, select: { materialCategory: true, availableKg: true, kabadiwalaId: true } });
    const materials: string[] = Array.from(new Set<string>(requirements.map((row: any) => String(row.materialCategory))));
    const verifiedCounts = await Promise.all(materials.map(async (category) => [category, await store.recycler.count({ where: { authorizationStatus: 'VERIFIED', OR: [{ authorizationValidUntil: null }, { authorizationValidUntil: { gt: new Date() } }], materials: { some: { category } } } })] as [string, number]));
    const countByMaterial = new Map(verifiedCounts as [string, number][]);
    const grouped = new Map<string, any>();
    for (const requirement of requirements) {
      const current = grouped.get(requirement.materialCategory) ?? { materialCategory: requirement.materialCategory, activeDemands: 0, requiredQuantityKg: 0, minimumLotKg: 0, deadlines: [], recyclerIds: new Set<string>() };
      current.activeDemands += 1;
      current.requiredQuantityKg += requirement.requiredQuantityKg;
      current.minimumLotKg = Math.max(current.minimumLotKg, requirement.minimumLotKg);
      if (requirement.deadline) current.deadlines.push(requirement.deadline);
      current.recyclerIds.add(requirement.recyclerId);
      grouped.set(requirement.materialCategory, current);
    }
    const data = [...grouped.values()].map((row: any) => {
      const availableKg = inventories.filter((item: any) => item.materialCategory === row.materialCategory).reduce((sum: number, item: any) => sum + item.availableKg, 0);
      const supplyGapKg = Math.max(0, row.requiredQuantityKg - availableKg);
      const demandLevel = availableKg >= row.requiredQuantityKg ? 'FULFILLING' : availableKg >= row.minimumLotKg ? 'ACTIVE' : 'HIGH';
      return { materialCategory: row.materialCategory, demandLevel, activeDemands: row.activeDemands, activeRecyclerCount: countByMaterial.get(row.materialCategory) ?? 0, requiredQuantityKg: Number(row.requiredQuantityKg.toFixed(2)), knownNearbySupplyKg: Number(availableKg.toFixed(2)), availableKg: Number(availableKg.toFixed(2)), supplyGapKg: Number(supplyGapKg.toFixed(2)), minimumLotKg: row.minimumLotKg, recyclerIds: [...row.recyclerIds], expiresAt: row.deadlines.sort((a: Date, b: Date) => a.getTime() - b.getTime())[0] ?? null, poolOpportunity: availableKg >= row.minimumLotKg, isDemo: false };
    });
    res.json({ success: true, data, disclaimer: 'Demand levels are derived from active platform records. They are not a guarantee of purchase.' });
  });

  router.get('/kabadiwala/passport', requireAuth(jwt, collectors), async (req, res) => {
    const passport = await store.$transaction(async (tx: Store) => { await ensurePassport(tx, req.identity!.collectorId); return refreshPassport(tx, req.identity!.collectorId); });
    res.json({ success: true, data: { ...passport, officialCertification: false, disclaimer: 'Platform-generated evidence profile. Not a government, CPCB or official license.' } });
  });

  router.get('/safety-routing', requireAuth(jwt, collectors), async (req, res) => {
    const input = parse(z.object({ materialCategory: material, condition: z.enum(['INTACT', 'DAMAGED', 'PARTIAL']).optional() }), req.query);
    const damaged = input.condition === 'DAMAGED';
    const routing: Record<string, { hazardLevel: string; handlingWarningCode: string; recommendedRouting: string; requiredRecyclerCapability: string; safetyGuidanceId: string }> = {
      BATTERY: { hazardLevel: damaged ? 'HIGH' : 'MEDIUM', handlingWarningCode: 'BATTERY_NO_OPEN_BURN_PUNCTURE', recommendedRouting: 'AUTHORIZED_BATTERY_RECYCLER', requiredRecyclerCapability: 'BATTERY_WASTE_HANDLER', safetyGuidanceId: 'BATTERY_SAFE_HANDLING' },
      PCB: { hazardLevel: damaged ? 'HIGH' : 'MEDIUM', handlingWarningCode: 'PCB_NO_UNCONTROLLED_DISMANTLING', recommendedRouting: 'AUTHORIZED_E_WASTE_RECYCLER', requiredRecyclerCapability: 'PCB_E_WASTE_HANDLER', safetyGuidanceId: 'CRT_PCB_SAFE_HANDLING' },
      CRT: { hazardLevel: 'HIGH', handlingWarningCode: 'CRT_NO_BREAKING', recommendedRouting: 'AUTHORIZED_CRT_RECYCLER', requiredRecyclerCapability: 'CRT_E_WASTE_HANDLER', safetyGuidanceId: 'CRT_PCB_SAFE_HANDLING' },
      OTHER: { hazardLevel: 'UNKNOWN', handlingWarningCode: 'UNKNOWN_ESCALATE', recommendedRouting: 'SAFE_REVIEW_REQUIRED', requiredRecyclerCapability: 'MATERIAL_REVIEW', safetyGuidanceId: 'MIXED_UNKNOWN_ESCALATION' }
    };
    const result = routing[input.materialCategory] ?? { hazardLevel: 'LOW', handlingWarningCode: 'STANDARD_FIELD_HANDLING', recommendedRouting: 'VERIFIED_RECYCLER', requiredRecyclerCapability: 'GENERAL_RECYCLER', safetyGuidanceId: 'GENERAL_FIELD_SAFETY' };
    res.json({ success: true, data: { materialCategory: input.materialCategory, condition: input.condition ?? 'UNSPECIFIED', ...result, disclaimer: 'Safety metadata is guidance for routing. It is not hazardous dismantling instruction.' } });
  });

  router.get('/kabadiwala/safety', requireAuth(jwt, collectors), async (req, res) => {
    const progress = await store.safetyProgress.findMany({ where: { collectorId: req.identity!.collectorId }, orderBy: { moduleKey: 'asc' } });
    res.json({ success: true, data: { modules: [
      { key: 'BATTERY_SAFE_HANDLING', title: 'Damaged battery awareness', whatNotToDo: 'Do not burn, puncture, open or acid-process batteries.', hazardousMaterials: ['BATTERY'] },
      { key: 'CRT_PCB_SAFE_HANDLING', title: 'CRT and PCB awareness', whatNotToDo: 'Do not break screens or dismantle boards without approved equipment.', hazardousMaterials: ['CRT', 'PCB'] },
      { key: 'MIXED_UNKNOWN_ESCALATION', title: 'Unknown material escalation', whatNotToDo: 'Do not burn or open unknown components. Flag them for safe routing.', hazardousMaterials: ['OTHER'] }
    ], progress } });
  });

  router.post('/kabadiwala/safety/:moduleKey/acknowledge', requireAuth(jwt, collectors), async (req, res) => {
    const moduleKey = parse(z.string().regex(/^[A-Z0-9_]{3,80}$/), req.params.moduleKey);
    if (!['BATTERY_SAFE_HANDLING', 'CRT_PCB_SAFE_HANDLING', 'MIXED_UNKNOWN_ESCALATION'].includes(moduleKey)) throw new AppError('NOT_FOUND', 'Safety module not found', 404, { code: 'SAFETY_MODULE_NOT_FOUND' });
    const result = await store.$transaction(async (tx: Store) => {
      const row = await tx.safetyProgress.upsert({ where: { collectorId_moduleKey: { collectorId: req.identity!.collectorId, moduleKey } }, update: { acknowledged: true, completedAt: new Date() }, create: { collectorId: req.identity!.collectorId, moduleKey, acknowledged: true, completedAt: new Date() } });
      await audit(tx, req.identity!.collectorId, 'COLLECTOR', 'SAFETY_MODULE_ACKNOWLEDGED', 'SAFETY', row.id, { moduleKey });
      await refreshPassport(tx, req.identity!.collectorId);
      return row;
    });
    res.json({ success: true, data: result });
  });

  async function prepareHandover(req: any, res: any, target: 'POOL' | 'BULK') {
    const targetId = parse(id, target === 'POOL' ? req.params.poolId : req.params.lotId);
    const result = await store.$transaction(async (tx: Store) => {
      let materialCategory: string; let quotedWeightKg: number; let quotedRatePerKg: number; let recyclerId: string; let poolId: string | null = null; let bulkLotId: string | null = null; let contributions: any[] = []; let sourceStatus: string;
      if (target === 'POOL') {
        const pool = await tx.pooledConsignment.findFirst({ where: { id: targetId, createdByCollectorId: req.identity!.collectorId }, include: undefined });
        if (!pool || pool.status !== 'LOCKED' || pool.totalReservedKg < pool.minimumQuantityKg) throw new AppError('CONFLICT', 'Pool must be locked at its threshold before handover', 409, { code: 'POOL_NOT_READY_FOR_HANDOVER' });
        materialCategory = pool.materialCategory; quotedWeightKg = pool.totalReservedKg; recyclerId = pool.recyclerId; poolId = pool.id; sourceStatus = pool.status;
        const demand = pool.requirementId ? await tx.procurementRequirement.findUnique({ where: { id: pool.requirementId } }) : null;
        quotedRatePerKg = demand?.maxRatePerKg ?? 0;
        contributions = await tx.poolContribution.findMany({ where: { poolId: pool.id, status: 'RESERVED' } });
        if (!contributions.length) throw new AppError('CONFLICT', 'Pool has no reserved contributions', 409, { code: 'POOL_EMPTY' });
      } else {
        const lot = await tx.bulkLot.findFirst({ where: { id: targetId, kabadiwalaId: req.identity!.collectorId, status: 'RESERVED' } });
        const offer = lot ? await tx.bulkOffer.findFirst({ where: { bulkLotId: lot.id, status: 'ACCEPTED' } }) : null;
        if (!lot || !offer) throw new AppError('CONFLICT', 'A reserved bulk lot with an accepted offer is required', 409, { code: 'BULK_LOT_NOT_READY_FOR_HANDOVER' });
        materialCategory = lot.materialCategory; quotedWeightKg = lot.quantityKg; quotedRatePerKg = offer.offeredRatePerKg; recyclerId = offer.recyclerId; bulkLotId = lot.id; sourceStatus = lot.status;
      }
      const recycler = await tx.recycler.findUnique({ where: { id: recyclerId }, include: { rates: true } });
      if (!recycler || recycler.authorizationStatus !== 'VERIFIED' || (recycler.authorizationValidUntil && recycler.authorizationValidUntil <= new Date())) throw new AppError('CONFLICT', 'Recycler authorization is not current', 409, { code: 'RECYCLER_NOT_VERIFIED' });
      if (target === 'POOL' && quotedRatePerKg <= 0) quotedRatePerKg = recycler.rates.find((row: any) => row.materialCategory === materialCategory)?.pricePerKg ?? 0;
      if (quotedRatePerKg <= 0) throw new AppError('CONFLICT', 'No current recycler rate is available for this material', 409, { code: 'RECYCLER_RATE_UNAVAILABLE' });
      const sourceKey = `${target}:${targetId}`;
      const prior = await tx.supplyHandover.findUnique({ where: { sourceKey } });
      if (prior) {
        if (prior.status !== 'EXPIRED' && prior.status !== 'CANCELLED') return { ...prior, payload: null, replayed: true };
        throw new AppError('CONFLICT', 'This source already has an expired handover record; operator re-preparation is required', 409, { code: 'HANDOVER_SOURCE_EXPIRED' });
      }
      const referenceId = `KC-HO-${Date.now()}-${randomBytes(4).toString('hex').toUpperCase()}`;
      const nonce = randomBytes(18).toString('base64url');
      const issuedAt = new Date(); const expiresAt = new Date(Date.now() + 2 * 60 * 60 * 1000);
      const contributionIds = contributions.map((row: any) => row.id);
      const payload = { version: 1, referenceId, poolId, bulkLotId, recyclerId, materialCategory, weightKg: quotedWeightKg, contributionIds, nonce, issuedAt: issuedAt.toISOString(), expiresAt: expiresAt.toISOString() };
      const qr = createSupplyHandoverQr(payload, signingSecret);
      const handover = await tx.supplyHandover.create({ data: { sourceKey, bulkLotId, poolId, collectorId: req.identity!.collectorId, recyclerId, referenceId, qrCodeData: qr.data, qrNonceHash: createHash('sha256').update(nonce).digest('hex'), consignmentHash: jsonHash({ poolId, bulkLotId, contributionIds, materialCategory, quotedWeightKg }), materialCategory: materialCategory as any, quotedWeightKg, quotedRatePerKg, quotedValue: Number((quotedWeightKg * quotedRatePerKg).toFixed(2)), handoverLocation: parse(location, req.body?.handoverLocation ?? {}), authorizationSnapshot: authoritySnapshot(recycler), expiresAt } });
      if (target === 'POOL') {
        await tx.poolContribution.updateMany({ where: { id: { in: contributionIds }, status: 'RESERVED' }, data: { handoverId: handover.id } });
        await tx.pooledConsignment.update({ where: { id: poolId as string }, data: { status: 'PICKUP_SCHEDULED' } });
      }
      await audit(tx, req.identity!.collectorId, 'COLLECTOR', 'HANDOVER_PREPARED', 'SUPPLY_HANDOVER', handover.id, { referenceId, poolId, bulkLotId, quotedWeightKg, sourceStatus });
      return { ...handover, qrCodeData: qr.data, payload: { ...payload, nonce: undefined }, replayed: false };
    });
    res.status((result as any).replayed ? 200 : 201).json({ success: true, data: result, message: (result as any).replayed ? 'Existing handover QR returned' : 'One-time handover QR prepared. Keep it available for the Recycler scan.' });
  }

  router.post('/kabadiwala/pools/:poolId/prepare-handover', requireAuth(jwt, collectors), (req, res) => prepareHandover(req, res, 'POOL'));
  router.post('/kabadiwala/bulk-lots/:lotId/prepare-handover', requireAuth(jwt, collectors), (req, res) => prepareHandover(req, res, 'BULK'));

  router.post('/kabadiwala/handovers/:handoverId/collector-confirm', requireAuth(jwt, collectors), async (req, res) => {
    const handoverId = parse(id, req.params.handoverId);
    const idempotency = operationKey(req);
    const hash = jsonHash({ action: 'COLLECTOR_CONFIRM_HANDOVER', handoverId });
    const result = await store.$transaction(async (tx: Store) => {
      if (idempotency) {
        const replay = await tx.idempotencyRecord.findUnique({ where: { actorId_operationId: { actorId: req.identity!.collectorId, operationId: idempotency } } });
        if (replay) {
          if (replay.requestHash && replay.requestHash !== hash) throw new AppError('CONFLICT', 'Idempotency key was already used for a different handover', 409, { code: 'IDEMPOTENCY_KEY_REUSED' });
          return { handover: replay.response, replayed: true };
        }
      }
      const updated = await tx.supplyHandover.updateMany({ where: { id: handoverId, collectorId: req.identity!.collectorId, status: 'PREPARED', expiresAt: { gt: new Date() } }, data: { status: 'COLLECTOR_CONFIRMED', collectorConfirmedAt: new Date() } });
      if (!updated.count) throw new AppError('CONFLICT', 'Handover is not available for collector confirmation', 409, { code: 'HANDOVER_NOT_CONFIRMABLE' });
      const handover = await tx.supplyHandover.findUniqueOrThrow({ where: { id: handoverId } });
      await audit(tx, req.identity!.collectorId, 'COLLECTOR', 'HANDOVER_COLLECTOR_CONFIRMED', 'SUPPLY_HANDOVER', handoverId, {});
      if (idempotency) await tx.idempotencyRecord.create({ data: { actorId: req.identity!.collectorId, operationId: idempotency, action: 'COLLECTOR_CONFIRM_HANDOVER', entityId: handoverId, requestHash: hash, response: jsonValue(handover) } });
      return { handover, replayed: false };
    });
    res.json({ success: true, data: result.handover, ...(result.replayed ? { message: 'Handover confirmation already processed' } : {}) });
  });

  router.get('/kabadiwala/handovers', requireAuth(jwt, collectors), async (req, res) => {
    const contributions = await store.poolContribution.findMany({ where: { collectorId: req.identity!.collectorId, handoverId: { not: null } }, select: { handoverId: true } });
    const contributionHandoverIds = [...new Set(contributions.map((row: any) => row.handoverId).filter(Boolean))];
    const handovers = await store.supplyHandover.findMany({
      where: { OR: [{ collectorId: req.identity!.collectorId }, ...(contributionHandoverIds.length ? [{ id: { in: contributionHandoverIds } }] : [])] },
      orderBy: { createdAt: 'desc' },
      take: 100
    });
    res.json({ success: true, data: handovers });
  });

  router.get('/recycler/supply-handovers', requireRecycler(jwt, db), async (req, res) => {
    const handovers = await store.supplyHandover.findMany({ where: { recyclerId: req.identity!.collectorId, status: { in: ['PREPARED', 'COLLECTOR_CONFIRMED', 'REVIEW_REQUIRED'] } }, orderBy: { createdAt: 'desc' }, take: 100 });
    res.json({ success: true, data: handovers });
  });

  router.post('/recycler/handovers/confirm', requireRecycler(jwt, db), async (req, res) => {
    const input = parse(z.object({ qrCodeData: z.string().trim().min(40).max(5000), actualWeightKg: positive.max(100000).optional(), acceptedWeightKg: z.number().finite().min(0).max(100000).optional(), finalRatePerKg: positive.max(1000000).optional(), materialMatch: z.boolean().default(true), reasonCode: z.string().trim().min(2).max(120).optional(), evidenceReference: z.string().trim().max(500).optional(), handoverLocation: location.optional() }), req.body);
    const idempotency = operationKey(req);
    const reconciliationHash = jsonHash(input);
    const payload = verifySupplyHandoverQr(input.qrCodeData, signingSecret);
    const handover = await store.supplyHandover.findUnique({ where: { referenceId: payload.referenceId } });
    if (!handover || handover.recyclerId !== req.identity!.collectorId) throw new AppError('NOT_FOUND', 'Handover is not available for this Recycler', 404, { code: 'HANDOVER_NOT_FOUND' });
    if (handover.qrCodeData !== input.qrCodeData) throw new AppError('CONFLICT', 'This handover QR is not the current server record', 409, { code: 'HANDOVER_QR_MISMATCH' });
    if (createHash('sha256').update(String(payload.nonce)).digest('hex') !== handover.qrNonceHash) throw new AppError('CONFLICT', 'Handover nonce has already been replaced or is invalid', 409, { code: 'HANDOVER_NONCE_MISMATCH' });
    if (idempotency) {
      const replay = await store.idempotencyRecord.findUnique({ where: { actorId_operationId: { actorId: req.identity!.collectorId, operationId: idempotency } } });
      if (replay) {
        if (replay.requestHash && replay.requestHash !== reconciliationHash) throw new AppError('CONFLICT', 'Idempotency key was already used for a different reconciliation', 409, { code: 'IDEMPOTENCY_KEY_REUSED' });
        return res.json({ success: true, data: replay.response, message: 'Handover reconciliation already processed' });
      }
    }
    if (handover.expiresAt <= new Date()) {
      await store.$transaction(async (tx: Store) => {
        const expired = await tx.supplyHandover.updateMany({ where: { id: handover.id, status: { in: ['PREPARED', 'COLLECTOR_CONFIRMED'] } }, data: { status: 'EXPIRED', sourceKey: `${handover.sourceKey}:expired:${handover.id}` } });
        if (!expired.count) return;
        if (handover.poolId) {
          await tx.poolContribution.updateMany({ where: { handoverId: handover.id }, data: { handoverId: null } });
          await tx.pooledConsignment.update({ where: { id: handover.poolId }, data: { status: 'LOCKED' } });
        }
        await audit(tx, req.identity!.collectorId, 'RECYCLER', 'HANDOVER_EXPIRED', 'SUPPLY_HANDOVER', handover.id, { sourceKey: handover.sourceKey });
      });
      throw new AppError('CONFLICT', 'Handover QR has expired; the Kabadiwala must prepare a new QR', 409, { code: 'HANDOVER_EXPIRED' });
    }
    if (handover.status !== 'COLLECTOR_CONFIRMED') throw new AppError('CONFLICT', 'Collector confirmation is required before the Recycler can receive this handover', 409, { code: 'COLLECTOR_CONFIRMATION_REQUIRED' });
    const actual = input.actualWeightKg ?? handover.quotedWeightKg;
    const accepted = input.acceptedWeightKg ?? actual;
    if (accepted > actual) throw new AppError('VALIDATION_ERROR', 'Accepted weight cannot exceed actual received weight', 422, { code: 'ACCEPTED_WEIGHT_EXCEEDS_ACTUAL' });
    const rate = input.finalRatePerKg ?? handover.quotedRatePerKg;
    const variance = evaluateSettlementVariance({ quotedWeightKg: handover.quotedWeightKg, actualWeightKg: actual, quotedRatePerKg: handover.quotedRatePerKg, finalRatePerKg: rate, acceptedWeightKg: accepted, materialMatch: input.materialMatch });
    const { weightDelta, rateDelta, requiresReview } = variance;
    if (requiresReview && !input.reasonCode) throw new AppError('VALIDATION_ERROR', 'A reason code is required for a material or settlement change', 422, { code: 'SETTLEMENT_REASON_REQUIRED' });
    const status = requiresReview ? 'REVIEW_REQUIRED' : 'COMPLETED';
    const result = await store.$transaction(async (tx: Store) => {
      const claimed = await tx.supplyHandover.updateMany({ where: { id: handover.id, status: { in: ['PREPARED', 'COLLECTOR_CONFIRMED'] }, recyclerId: req.identity!.collectorId }, data: { status, recyclerConfirmedAt: new Date(), finalAcceptedKg: accepted, finalRejectedKg: Math.max(0, actual - accepted), finalRatePerKg: rate, finalValue: Number((accepted * rate).toFixed(2)), handoverLocation: input.handoverLocation ?? handover.handoverLocation, reviewReason: requiresReview ? (input.reasonCode ?? (!input.materialMatch ? 'MATERIAL_MISMATCH' : 'SETTLEMENT_VARIANCE')) : null, reviewEvidence: input.evidenceReference ?? null } });
      if (!claimed.count) throw new AppError('CONFLICT', 'Handover was already confirmed', 409, { code: 'HANDOVER_REPLAYED' });
      const finalValue = Number((accepted * rate).toFixed(2));
      await tx.settlementBreakdown.create({ data: { handoverId: handover.id, quotedWeightKg: handover.quotedWeightKg, quotedRatePerKg: handover.quotedRatePerKg, quotedValue: handover.quotedValue, finalAcceptedKg: accepted, finalRejectedKg: Math.max(0, actual - accepted), finalRatePerKg: rate, finalValue, reasonCode: requiresReview ? (input.reasonCode ?? 'SETTLEMENT_VARIANCE') : null, evidenceReference: input.evidenceReference, changedBy: req.identity!.collectorId, status: requiresReview ? 'PENDING_COLLECTOR_CONFIRMATION' : 'COMPLETED' } });
      if (requiresReview) await tx.anomalyFlag.create({ data: { entityType: 'SUPPLY_HANDOVER', entityId: handover.id, ruleCode: variance.ruleCode!, severity: variance.ruleCode === 'MATERIAL_MISMATCH' ? 'HIGH' : 'MEDIUM', details: { quotedWeightKg: handover.quotedWeightKg, actualWeightKg: actual, quotedRatePerKg: handover.quotedRatePerKg, finalRatePerKg: rate, acceptedWeightKg: accepted, reasonCode: input.reasonCode } } });
      if (handover.poolId) {
        const contributions = await tx.poolContribution.findMany({ where: { poolId: handover.poolId, status: { in: ['RESERVED', 'RECEIVED', 'REVIEW_REQUIRED'] } } });
        const total = contributions.reduce((sum: number, row: any) => sum + row.quantityKg, 0) || 1;
        for (const contribution of contributions) {
          const acceptedForContribution = Number((accepted * contribution.quantityKg / total).toFixed(3));
          await tx.poolContribution.update({ where: { id: contribution.id }, data: { finalAcceptedKg: acceptedForContribution, finalPayout: Number((acceptedForContribution * rate).toFixed(2)), status: requiresReview ? 'REVIEW_REQUIRED' : 'SETTLED' } });
          await tx.poolSettlement.upsert({ where: { contributionId: contribution.id }, update: { acceptedWeightKg: acceptedForContribution, finalRatePerKg: rate, finalValue: Number((acceptedForContribution * rate).toFixed(2)), reasonCode: requiresReview ? (input.reasonCode ?? 'SETTLEMENT_VARIANCE') : null, evidenceReference: input.evidenceReference, changedBy: req.identity!.collectorId, status: requiresReview ? 'PENDING_COLLECTOR_CONFIRMATION' : 'COMPLETED' }, create: { contributionId: contribution.id, poolId: handover.poolId, quotedWeightKg: contribution.quantityKg, quotedRatePerKg: contribution.expectedRatePerKg, quotedValue: contribution.expectedPayout, acceptedWeightKg: acceptedForContribution, finalRatePerKg: rate, finalValue: Number((acceptedForContribution * rate).toFixed(2)), reasonCode: requiresReview ? (input.reasonCode ?? 'SETTLEMENT_VARIANCE') : null, evidenceReference: input.evidenceReference, changedBy: req.identity!.collectorId, status: requiresReview ? 'PENDING_COLLECTOR_CONFIRMATION' : 'COMPLETED' } });
          if (!requiresReview) {
            const beforeBalance = await tx.inventoryBalance.findUniqueOrThrow({ where: { id: contribution.inventoryBalanceId } });
            const afterBalance = await tx.inventoryBalance.update({ where: { id: contribution.inventoryBalanceId }, data: { reservedKg: { decrement: contribution.quantityKg }, availableKg: { increment: Math.max(0, contribution.quantityKg - acceptedForContribution) }, soldKg: { increment: acceptedForContribution } } });
            assertInventoryInvariant(afterBalance);
            await recordInventoryMovement(tx, beforeBalance, afterBalance, 'SALE', acceptedForContribution, 'SUPPLY_HANDOVER', handover.id, { poolId: handover.poolId, contributionId: contribution.id });
          }
        }
        await tx.pooledConsignment.update({ where: { id: handover.poolId }, data: { status: requiresReview ? 'REVIEW_REQUIRED' : 'SETTLED' } });
      } else if (handover.bulkLotId && !requiresReview) {
        const lot = await tx.bulkLot.findUnique({ where: { id: handover.bulkLotId } });
        if (lot) {
          const acceptedKg = accepted;
          const beforeBalance = await tx.inventoryBalance.findUniqueOrThrow({ where: { kabadiwalaId_materialCategory_grade: { kabadiwalaId: lot.kabadiwalaId, materialCategory: lot.materialCategory, grade: lot.grade } } });
          const afterBalance = await tx.inventoryBalance.update({ where: { id: beforeBalance.id }, data: { reservedKg: { decrement: lot.quantityKg }, availableKg: { increment: Math.max(0, lot.quantityKg - acceptedKg) }, soldKg: { increment: acceptedKg } } });
          assertInventoryInvariant(afterBalance);
          await recordInventoryMovement(tx, beforeBalance, afterBalance, 'SALE', acceptedKg, 'SUPPLY_HANDOVER', handover.id, { bulkLotId: lot.id });
          await tx.bulkLot.update({ where: { id: lot.id }, data: { status: 'SOLD' } });
        }
      }
      await audit(tx, req.identity!.collectorId, 'RECYCLER', requiresReview ? 'HANDOVER_REVIEW_REQUIRED' : 'HANDOVER_COMPLETED', 'SUPPLY_HANDOVER', handover.id, { actualWeightKg: actual, acceptedWeightKg: accepted, finalRatePerKg: rate, reasonCode: input.reasonCode ?? null });
      const finalHandover = await tx.supplyHandover.findUniqueOrThrow({ where: { id: handover.id } });
      if (idempotency) await tx.idempotencyRecord.create({ data: { actorId: req.identity!.collectorId, operationId: idempotency, action: 'RECYCLER_CONFIRM_HANDOVER', entityId: handover.id, requestHash: reconciliationHash, response: jsonValue(finalHandover) } });
      return finalHandover;
    });
    if (!requiresReview) await store.$transaction(async (tx: Store) => { const pool = handover.poolId ? await tx.poolContribution.findMany({ where: { poolId: handover.poolId }, select: { collectorId: true }, distinct: ['collectorId'] }) : []; for (const row of pool) await refreshPassport(tx, row.collectorId); await refreshPassport(tx, handover.collectorId); });
    res.json({ success: true, data: result, message: requiresReview ? 'Handover needs collector review because settlement changed.' : 'Handover completed and traceability updated.' });
  });

  router.post('/kabadiwala/handovers/:handoverId/settlement', requireAuth(jwt, collectors), async (req, res) => {
    const handoverId = parse(id, req.params.handoverId);
    const decision = parse(z.object({ decision: z.enum(['ACCEPT', 'RAISE_ISSUE']), notes: z.string().trim().max(1000).optional() }), req.body);
    const handover = await store.supplyHandover.findUnique({ where: { id: handoverId } });
    const contribution = handover?.poolId ? await store.poolContribution.findFirst({ where: { poolId: handover.poolId, collectorId: req.identity!.collectorId, handoverId } }) : null;
    if (!handover || (handover.collectorId !== req.identity!.collectorId && !contribution)) throw new AppError('NOT_FOUND', 'Handover settlement not found', 404, { code: 'HANDOVER_NOT_FOUND' });
    const result = await store.$transaction(async (tx: Store) => {
      if (contribution) {
        const settlement = await tx.poolSettlement.updateMany({ where: { contributionId: contribution.id, status: 'PENDING_COLLECTOR_CONFIRMATION' }, data: { status: decision.decision === 'ACCEPT' ? 'COMPLETED' : 'DISPUTED', collectorDecision: decision.decision, updatedAt: new Date() } });
        if (!settlement.count) throw new AppError('CONFLICT', 'This contribution settlement is no longer actionable', 409, { code: 'SETTLEMENT_ALREADY_DECIDED' });
        if (decision.decision === 'ACCEPT') {
          const row = await tx.poolContribution.update({ where: { id: contribution.id }, data: { status: 'SETTLED' } });
          const acceptedKg = contribution.finalAcceptedKg ?? contribution.quantityKg;
          const beforeBalance = await tx.inventoryBalance.findUniqueOrThrow({ where: { id: contribution.inventoryBalanceId } });
          const afterBalance = await tx.inventoryBalance.update({ where: { id: contribution.inventoryBalanceId }, data: { reservedKg: { decrement: contribution.quantityKg }, availableKg: { increment: Math.max(0, contribution.quantityKg - acceptedKg) }, soldKg: { increment: acceptedKg } } });
          assertInventoryInvariant(afterBalance);
          await recordInventoryMovement(tx, beforeBalance, afterBalance, 'SALE', acceptedKg, 'SUPPLY_HANDOVER', handoverId, { poolId: handover.poolId, contributionId: contribution.id });
          await refreshPassport(tx, req.identity!.collectorId);
          await audit(tx, req.identity!.collectorId, 'COLLECTOR', 'POOL_SETTLEMENT_ACCEPTED', 'POOL_CONTRIBUTION', contribution.id, { handoverId, notes: decision.notes ?? null });
          return row;
        }
        await tx.anomalyFlag.create({ data: { entityType: 'POOL_CONTRIBUTION', entityId: contribution.id, ruleCode: 'COLLECTOR_DISPUTE', severity: 'MEDIUM', details: { handoverId, notes: decision.notes ?? null } } });
        await audit(tx, req.identity!.collectorId, 'COLLECTOR', 'POOL_SETTLEMENT_DISPUTED', 'POOL_CONTRIBUTION', contribution.id, { handoverId, notes: decision.notes ?? null });
        return contribution;
      }
      const breakdown = await tx.settlementBreakdown.findUnique({ where: { handoverId } });
      if (!breakdown || breakdown.status !== 'PENDING_COLLECTOR_CONFIRMATION') throw new AppError('CONFLICT', 'This settlement is no longer actionable', 409, { code: 'SETTLEMENT_ALREADY_DECIDED' });
      await tx.settlementBreakdown.update({ where: { handoverId }, data: { status: decision.decision === 'ACCEPT' ? 'COMPLETED' : 'DISPUTED', collectorDecision: decision.decision } });
      if (decision.decision === 'ACCEPT') {
        const lot = handover.bulkLotId ? await tx.bulkLot.findUnique({ where: { id: handover.bulkLotId } }) : null;
        if (lot) {
          const acceptedKg = handover.finalAcceptedKg ?? lot.quantityKg;
          const beforeBalance = await tx.inventoryBalance.findUniqueOrThrow({ where: { kabadiwalaId_materialCategory_grade: { kabadiwalaId: lot.kabadiwalaId, materialCategory: lot.materialCategory, grade: lot.grade } } });
          const afterBalance = await tx.inventoryBalance.update({ where: { id: beforeBalance.id }, data: { reservedKg: { decrement: lot.quantityKg }, availableKg: { increment: Math.max(0, lot.quantityKg - acceptedKg) }, soldKg: { increment: acceptedKg } } });
          assertInventoryInvariant(afterBalance);
          await recordInventoryMovement(tx, beforeBalance, afterBalance, 'SALE', acceptedKg, 'SUPPLY_HANDOVER', handoverId, { bulkLotId: lot.id });
          await tx.bulkLot.update({ where: { id: lot.id }, data: { status: 'SOLD' } });
        }
        await tx.supplyHandover.update({ where: { id: handoverId }, data: { status: 'COMPLETED' } });
        await refreshPassport(tx, req.identity!.collectorId);
      } else {
        await tx.supplyHandover.update({ where: { id: handoverId }, data: { status: 'REVIEW_REQUIRED' } });
        await tx.anomalyFlag.create({ data: { entityType: 'SUPPLY_HANDOVER', entityId: handoverId, ruleCode: 'COLLECTOR_DISPUTE', severity: 'MEDIUM', details: { notes: decision.notes ?? null } } });
      }
      await audit(tx, req.identity!.collectorId, 'COLLECTOR', decision.decision === 'ACCEPT' ? 'SETTLEMENT_ACCEPTED' : 'SETTLEMENT_DISPUTED', 'SUPPLY_HANDOVER', handoverId, { notes: decision.notes ?? null });
      return tx.supplyHandover.findUniqueOrThrow({ where: { id: handoverId } });
    });
    res.json({ success: true, data: result });
  });

  router.get('/kabadiwala/handovers/:handoverId/passport', requireAuth(jwt, collectors), async (req, res) => {
    const handoverId = parse(id, req.params.handoverId);
    const handover = await store.supplyHandover.findUnique({ where: { id: handoverId } });
    const contribution = handover?.poolId ? await store.poolContribution.findFirst({ where: { poolId: handover.poolId, collectorId: req.identity!.collectorId, handoverId } }) : null;
    if (!handover || (handover.collectorId !== req.identity!.collectorId && !contribution)) throw new AppError('NOT_FOUND', 'Material passport not found', 404, { code: 'PASSPORT_NOT_FOUND' });
    const events = await store.materialPassportEvent.findMany({ where: { entityType: 'SUPPLY_HANDOVER', entityId: handoverId }, orderBy: { occurredAt: 'asc' } });
    const settlement = await store.settlementBreakdown.findUnique({ where: { handoverId } });
    const poolSettlements = handover.poolId && contribution ? await store.poolSettlement.findUnique({ where: { contributionId: contribution.id } }) : null;
    res.json({ success: true, data: { handover, contribution, settlement: settlement ?? poolSettlements, events, disclaimer: 'Traceability is platform evidence for this prototype; it is not a government certificate.' } });
  });
  router.get('/kabadiwala/handovers/:handoverId/anomalies', requireAuth(jwt, collectors), async (req, res) => {
    const handoverId = parse(id, req.params.handoverId);
    const handover = await store.supplyHandover.findUnique({ where: { id: handoverId } });
    const contribution = handover?.poolId ? await store.poolContribution.findFirst({ where: { poolId: handover.poolId, collectorId: req.identity!.collectorId, handoverId } }) : null;
    if (!handover || (handover.collectorId !== req.identity!.collectorId && !contribution)) throw new AppError('NOT_FOUND', 'Handover anomalies not found', 404, { code: 'HANDOVER_NOT_FOUND' });
    const flags = await store.anomalyFlag.findMany({ where: { entityType: { in: ['SUPPLY_HANDOVER', 'POOL_CONTRIBUTION'] }, entityId: { in: [handoverId, ...(contribution ? [contribution.id] : [])] } }, orderBy: { createdAt: 'asc' } });
    const riskLevel = flags.length ? riskLevelForFlags(flags) : 'NONE';
    res.json({ success: true, data: { handoverId, riskLevel, flags, deterministic: true, disclaimer: 'Flags are deterministic platform checks, not an AI decision.' } });
  });

  return router;
}
