import { Router } from 'express';
import { z } from 'zod';
import type { PrismaClient } from '@prisma/client';
import type { JwtService } from '../services/jwt.js';
import type { CollectorRepository } from '../repositories/collectorRepository.js';
import { requireAuth, requireHousehold, requireRecycler } from '../middleware/auth.js';
import { AppError } from '../utils/errors.js';

const material = z.enum(['CRT', 'LCD_PANEL', 'PCB', 'CABLE', 'COPPER', 'BATTERY', 'MOTOR', 'MAGNET', 'PLASTIC', 'OTHER']);
const condition = z.enum(['INTACT', 'DAMAGED', 'PARTIAL']);
const positive = z.number().finite().positive();
const id = z.string().regex(/^[A-Za-z0-9_-]{1,80}$/);
const listingInput = z.object({ materialCategory: material, estimatedWeight: positive.max(500), condition, notes: z.string().trim().max(1000).optional(), photoReference: z.string().trim().max(500).optional(), areaName: z.string().trim().min(1).max(160), latitude: z.number().min(-90).max(90).optional(), longitude: z.number().min(-180).max(180).optional() });
const pickupRequest = z.object({ kabadiwalaId: id, requestedSlot: z.string().datetime().optional() });
const bulkInput = z.object({ materialCategory: material, grade: z.string().trim().min(1).max(80).default('UNSPECIFIED'), quantityKg: positive.max(100000), askingRatePerKg: positive.max(1000000), minimumRatePerKg: positive.max(1000000).optional(), areaName: z.string().trim().min(1).max(160), latitude: z.number().min(-90).max(90).optional(), longitude: z.number().min(-180).max(180).optional(), notes: z.string().trim().max(1000).optional() }).refine(value => !value.minimumRatePerKg || value.minimumRatePerKg <= value.askingRatePerKg, { message: 'Minimum rate cannot exceed asking rate' });

const parse = <T>(schema: z.ZodType<T>, value: unknown): T => {
  const result = schema.safeParse(value);
  if (!result.success) throw new AppError('VALIDATION_ERROR', 'Invalid request', 422, { details: result.error.flatten() });
  return result.data;
};

/** The closed-loop marketplace. Every endpoint is role-gated here, rather than
 * trusting the Android navigation layer. */
export function supplyChainRoutes(jwt: JwtService, collectors: CollectorRepository, db: PrismaClient) {
  const router = Router();
  const store: any = db;

  router.post('/household/listings', requireHousehold(jwt, collectors), async (req, res) => {
    const input = parse(listingInput, req.body);
    const listing = await store.householdListing.create({ data: { ...input, householdId: req.identity!.collectorId, status: 'POSTED' } });
    res.status(201).json({ success: true, data: listing });
  });
  router.get('/household/listings', requireHousehold(jwt, collectors), async (req, res) => {
    res.json({ success: true, data: await store.householdListing.findMany({ where: { householdId: req.identity!.collectorId }, orderBy: { createdAt: 'desc' } }) });
  });
  router.get('/household/kabadiwalas', requireHousehold(jwt, collectors), async (req, res) => {
    const users = await store.user.findMany({ where: { role: 'COLLECTOR', accountStatus: 'ACTIVE', collectorProfileId: { not: null } }, select: { collectorProfileId: true } });
    const ids = users.map((user: { collectorProfileId: string | null }) => user.collectorProfileId).filter(Boolean);
    const profiles = await store.collector.findMany({ where: { id: { in: ids }, accountStatus: 'ACTIVE' }, select: { id: true, displayName: true, areaName: true, latitude: true, longitude: true } });
    res.json({ success: true, data: profiles });
  });
  router.post('/household/listings/:listingId/pickups', requireHousehold(jwt, collectors), async (req, res) => {
    const listingId = parse(id, req.params.listingId); const input = parse(pickupRequest, req.body);
    const listing = await store.householdListing.findFirst({ where: { id: listingId, householdId: req.identity!.collectorId, status: 'POSTED' } });
    if (!listing) throw new AppError('NOT_FOUND', 'Posted household listing not found', 404);
    const kabadiwala = await store.user.findFirst({ where: { collectorProfileId: input.kabadiwalaId, role: 'COLLECTOR', accountStatus: 'ACTIVE' } });
    if (!kabadiwala) throw new AppError('NOT_FOUND', 'Kabadiwala not found', 404);
    const pickup = await store.pickupRequest.upsert({ where: { listingId_kabadiwalaId: { listingId, kabadiwalaId: input.kabadiwalaId } }, update: { requestedSlot: input.requestedSlot ? new Date(input.requestedSlot) : undefined, status: 'REQUESTED' }, create: { listingId, householdId: req.identity!.collectorId, kabadiwalaId: input.kabadiwalaId, requestedSlot: input.requestedSlot ? new Date(input.requestedSlot) : undefined } });
    res.status(201).json({ success: true, data: pickup });
  });
  router.get('/household/pickups', requireHousehold(jwt, collectors), async (req, res) => {
    res.json({ success: true, data: await store.pickupRequest.findMany({ where: { householdId: req.identity!.collectorId }, orderBy: { createdAt: 'desc' } }) });
  });

  router.get('/kabadiwala/listings', requireAuth(jwt, collectors), async (req, res) => {
    const assigned = await store.pickupRequest.findMany({ where: { kabadiwalaId: req.identity!.collectorId }, select: { listingId: true } });
    const assignedIds = assigned.map((pickup: { listingId: string }) => pickup.listingId);
    res.json({ success: true, data: await store.householdListing.findMany({ where: { OR: [{ status: 'POSTED' }, ...(assignedIds.length ? [{ id: { in: assignedIds } }] : [])] }, orderBy: { createdAt: 'desc' }, take: 100 }) });
  });
  router.get('/kabadiwala/pickups', requireAuth(jwt, collectors), async (req, res) => {
    res.json({ success: true, data: await store.pickupRequest.findMany({ where: { kabadiwalaId: req.identity!.collectorId }, orderBy: { createdAt: 'desc' } }) });
  });
  router.post('/kabadiwala/listings/:listingId/accept', requireAuth(jwt, collectors), async (req, res) => {
    const listingId = parse(id, req.params.listingId);
    const updated = await store.pickupRequest.updateMany({ where: { listingId, kabadiwalaId: req.identity!.collectorId, status: 'REQUESTED' }, data: { status: 'ACCEPTED' } });
    if (!updated.count) throw new AppError('CONFLICT', 'Pickup is not available to accept', 409);
    await store.householdListing.updateMany({ where: { id: listingId, status: 'POSTED' }, data: { status: 'MATCHED' } });
    res.json({ success: true });
  });
  router.post('/kabadiwala/pickups/:pickupId/schedule', requireAuth(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId); const scheduledSlot = parse(z.object({ scheduledSlot: z.string().datetime() }), req.body).scheduledSlot;
    const updated = await store.pickupRequest.updateMany({ where: { id: pickupId, kabadiwalaId: req.identity!.collectorId, status: { in: ['ACCEPTED', 'SCHEDULED'] } }, data: { status: 'SCHEDULED', scheduledSlot: new Date(scheduledSlot) } });
    if (!updated.count) throw new AppError('CONFLICT', 'Pickup cannot be scheduled', 409);
    res.json({ success: true });
  });
  router.post('/kabadiwala/pickups/:pickupId/status', requireAuth(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId); const next = parse(z.object({ status: z.enum(['IN_TRANSIT', 'ARRIVED']) }), req.body).status;
    // A household may request an immediate collection without selecting a
    // time slot. In that case ACCEPTED can move straight to IN_TRANSIT;
    // scheduled pickups still follow the same transition. The conditional
    // update keeps the mutation race-safe and idempotent.
    const allowed = next === 'IN_TRANSIT' ? ['ACCEPTED', 'SCHEDULED'] : ['IN_TRANSIT'];
    const updated = await store.pickupRequest.updateMany({ where: { id: pickupId, kabadiwalaId: req.identity!.collectorId, status: { in: allowed } }, data: { status: next } });
    if (!updated.count) throw new AppError('CONFLICT', 'Invalid pickup transition', 409);
    res.json({ success: true });
  });
  router.post('/kabadiwala/pickups/:pickupId/complete', requireAuth(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId);
    const input = parse(z.object({ actualWeight: positive.max(500), finalCategory: material, grade: z.string().trim().min(1).max(80).default('UNSPECIFIED'), ratePerKg: positive.max(1000000) }), req.body);
    const result = await store.$transaction(async (tx: any) => {
      const claimed = await tx.pickupRequest.updateMany({ where: { id: pickupId, kabadiwalaId: req.identity!.collectorId, status: 'ARRIVED' }, data: { status: 'WEIGHED', actualWeight: input.actualWeight, finalCategory: input.finalCategory, grade: input.grade, ratePerKg: input.ratePerKg, finalAmount: Number((input.actualWeight * input.ratePerKg).toFixed(2)) } });
      if (!claimed.count) throw new AppError('CONFLICT', 'Pickup cannot be weighed', 409);
      const pickup = await tx.pickupRequest.findUniqueOrThrow({ where: { id: pickupId } });
      await tx.inventoryBalance.upsert({ where: { kabadiwalaId_materialCategory_grade: { kabadiwalaId: req.identity!.collectorId, materialCategory: input.finalCategory, grade: input.grade } }, update: { availableKg: { increment: input.actualWeight }, purchaseCost: { increment: Number((input.actualWeight * input.ratePerKg).toFixed(2)) } }, create: { kabadiwalaId: req.identity!.collectorId, materialCategory: input.finalCategory, grade: input.grade, availableKg: input.actualWeight, purchaseCost: Number((input.actualWeight * input.ratePerKg).toFixed(2)) } });
      await tx.pickupRequest.update({ where: { id: pickupId }, data: { status: 'COMPLETED', completedAt: new Date() } });
      await tx.householdListing.updateMany({ where: { id: pickup.listingId }, data: { status: 'COMPLETED' } });
      return tx.pickupRequest.findUniqueOrThrow({ where: { id: pickupId } });
    });
    res.json({ success: true, data: result });
  });
  router.get('/kabadiwala/inventory', requireAuth(jwt, collectors), async (req, res) => {
    res.json({ success: true, data: await store.inventoryBalance.findMany({ where: { kabadiwalaId: req.identity!.collectorId }, orderBy: { updatedAt: 'desc' } }) });
  });
  router.post('/kabadiwala/bulk-lots', requireAuth(jwt, collectors), async (req, res) => {
    const input = parse(bulkInput, req.body);
    const lot = await store.$transaction(async (tx: any) => {
      const reserved = await tx.inventoryBalance.updateMany({ where: { kabadiwalaId: req.identity!.collectorId, materialCategory: input.materialCategory, grade: input.grade, availableKg: { gte: input.quantityKg } }, data: { availableKg: { decrement: input.quantityKg }, reservedKg: { increment: input.quantityKg } } });
      if (!reserved.count) throw new AppError('CONFLICT', 'Insufficient available inventory for this bulk lot', 409);
      return tx.bulkLot.create({ data: { ...input, kabadiwalaId: req.identity!.collectorId, status: 'LISTED' } });
    });
    res.status(201).json({ success: true, data: lot });
  });
  router.get('/kabadiwala/bulk-lots', requireAuth(jwt, collectors), async (req, res) => {
    res.json({ success: true, data: await store.bulkLot.findMany({ where: { kabadiwalaId: req.identity!.collectorId }, orderBy: { createdAt: 'desc' } }) });
  });
  router.post('/kabadiwala/bulk-lots/:lotId/cancel', requireAuth(jwt, collectors), async (req, res) => {
    const lotId = parse(id, req.params.lotId);
    await store.$transaction(async (tx: any) => {
      const lot = await tx.bulkLot.findFirst({ where: { id: lotId, kabadiwalaId: req.identity!.collectorId, status: 'LISTED' } });
      if (!lot) throw new AppError('CONFLICT', 'Only an unreserved listed lot can be cancelled', 409);
      await tx.bulkLot.update({ where: { id: lotId }, data: { status: 'CANCELLED' } });
      await tx.bulkOffer.updateMany({ where: { bulkLotId: lot.id, status: 'PENDING' }, data: { status: 'CANCELLED' } });
      await tx.inventoryBalance.update({ where: { kabadiwalaId_materialCategory_grade: { kabadiwalaId: lot.kabadiwalaId, materialCategory: lot.materialCategory, grade: lot.grade } }, data: { availableKg: { increment: lot.quantityKg }, reservedKg: { decrement: lot.quantityKg } } });
    });
    res.json({ success: true });
  });
  router.get('/kabadiwala/bulk-offers', requireAuth(jwt, collectors), async (req, res) => {
    const lots = await store.bulkLot.findMany({ where: { kabadiwalaId: req.identity!.collectorId }, select: { id: true } });
    const offers = await store.bulkOffer.findMany({ where: { bulkLotId: { in: lots.map((lot: { id: string }) => lot.id) } }, orderBy: { createdAt: 'desc' } });
    res.json({ success: true, data: offers });
  });

  router.get('/recycler/bulk-lots', requireRecycler(jwt, db), async (_req, res) => {
    res.json({ success: true, data: await store.bulkLot.findMany({ where: { status: 'LISTED' }, orderBy: { createdAt: 'desc' } }) });
  });
  router.post('/recycler/bulk-lots/:lotId/offers', requireRecycler(jwt, db), async (req, res) => {
    const lotId = parse(id, req.params.lotId); const offeredRatePerKg = parse(z.object({ offeredRatePerKg: positive.max(1000000) }), req.body).offeredRatePerKg;
    const lot = await store.bulkLot.findFirst({ where: { id: lotId, status: 'LISTED' } });
    if (!lot) throw new AppError('NOT_FOUND', 'Available bulk lot not found', 404);
    const offer = await store.bulkOffer.upsert({ where: { bulkLotId_recyclerId: { bulkLotId: lotId, recyclerId: req.identity!.collectorId } }, update: { offeredRatePerKg, status: 'PENDING' }, create: { bulkLotId: lotId, recyclerId: req.identity!.collectorId, offeredRatePerKg } });
    res.status(201).json({ success: true, data: offer });
  });
  router.get('/recycler/offers', requireRecycler(jwt, db), async (req, res) => {
    res.json({ success: true, data: await store.bulkOffer.findMany({ where: { recyclerId: req.identity!.collectorId }, orderBy: { createdAt: 'desc' } }) });
  });
  router.post('/kabadiwala/bulk-offers/:offerId/accept', requireAuth(jwt, collectors), async (req, res) => {
    const offerId = parse(id, req.params.offerId);
    await store.$transaction(async (tx: any) => {
      const offer = await tx.bulkOffer.findUnique({ where: { id: offerId } });
      if (!offer || offer.status !== 'PENDING') throw new AppError('CONFLICT', 'Offer is not actionable', 409);
      const lot = await tx.bulkLot.findFirst({ where: { id: offer.bulkLotId, kabadiwalaId: req.identity!.collectorId, status: 'LISTED' } });
      if (!lot) throw new AppError('NOT_FOUND', 'Listed bulk lot not found', 404);
      if (lot.minimumRatePerKg && offer.offeredRatePerKg < lot.minimumRatePerKg) throw new AppError('CONFLICT', 'Offer is below the lot minimum', 409);
      await tx.bulkOffer.update({ where: { id: offerId }, data: { status: 'ACCEPTED' } });
      await tx.bulkOffer.updateMany({ where: { bulkLotId: lot.id, id: { not: offerId }, status: 'PENDING' }, data: { status: 'REJECTED' } });
      await tx.bulkLot.update({ where: { id: lot.id }, data: { status: 'RESERVED', reservedForId: offer.recyclerId } });
    });
    res.json({ success: true });
  });
  router.post('/recycler/bulk-lots/:lotId/receive', requireRecycler(jwt, db), async (req, res) => {
    const lotId = parse(id, req.params.lotId);
    await store.$transaction(async (tx: any) => {
      const lot = await tx.bulkLot.findFirst({ where: { id: lotId, status: 'RESERVED', reservedForId: req.identity!.collectorId } });
      if (!lot) throw new AppError('NOT_FOUND', 'Reserved bulk lot not found', 404);
      await tx.inventoryBalance.update({ where: { kabadiwalaId_materialCategory_grade: { kabadiwalaId: lot.kabadiwalaId, materialCategory: lot.materialCategory, grade: lot.grade } }, data: { reservedKg: { decrement: lot.quantityKg }, soldKg: { increment: lot.quantityKg } } });
      await tx.bulkLot.update({ where: { id: lot.id }, data: { status: 'SOLD' } });
      await tx.bulkOffer.updateMany({ where: { bulkLotId: lot.id, recyclerId: req.identity!.collectorId, status: 'ACCEPTED' }, data: { status: 'COMPLETED' } });
    });
    res.json({ success: true });
  });
  router.post('/recycler/procurement-requirements', requireRecycler(jwt, db), async (req, res) => {
    const input = parse(z.object({ materialCategory: material, minimumLotKg: positive.max(100000), requiredQuantityKg: positive.max(1000000), preferredGrade: z.string().trim().max(80).optional(), maxRatePerKg: positive.max(1000000).optional(), procurementRadiusKm: positive.max(1000), deadline: z.string().datetime().optional() }), req.body);
    const requirement = await store.procurementRequirement.create({ data: { ...input, deadline: input.deadline ? new Date(input.deadline) : undefined, recyclerId: req.identity!.collectorId } });
    res.status(201).json({ success: true, data: requirement });
  });
  router.get('/recycler/procurement-requirements', requireRecycler(jwt, db), async (req, res) => {
    res.json({ success: true, data: await store.procurementRequirement.findMany({ where: { recyclerId: req.identity!.collectorId }, orderBy: { createdAt: 'desc' } }) });
  });
  router.get('/kabadiwala/procurement-requirements', requireAuth(jwt, collectors), async (_req, res) => {
    res.json({ success: true, data: await store.procurementRequirement.findMany({ where: { status: 'OPEN' }, orderBy: { createdAt: 'desc' } }) });
  });
  return router;
}
