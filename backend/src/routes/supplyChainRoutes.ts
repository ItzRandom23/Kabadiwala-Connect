import { Router } from 'express';
import multer from 'multer';
import { createHash } from 'node:crypto';
import { z } from 'zod';
import sharp from 'sharp';
import type { PrismaClient } from '@prisma/client';
import type { JwtService } from '../services/jwt.js';
import type { CollectorRepository } from '../repositories/collectorRepository.js';
import type { StorageService } from '../services/storage.js';
import { requireAuth as baseRequireAuth, requireHousehold as baseRequireHousehold, requireRecycler, requireAdmin } from '../middleware/auth.js';
import { AppError } from '../utils/errors.js';
import { assertInventoryInvariant, ownedKg, recordInventoryMovement } from '../services/inventoryLedger.js';
import { emitNotification } from '../services/notificationService.js';
import { movePickupDay, releasePickupDay, validatePickupSlot } from '../services/pickupSchedulingService.js';

const material = z.enum(['CRT', 'LCD_PANEL', 'PCB', 'CABLE', 'COPPER', 'BATTERY', 'MOTOR', 'MAGNET', 'PLASTIC', 'OTHER']);
const condition = z.enum(['INTACT', 'DAMAGED', 'PARTIAL']);
const positive = z.number().finite().positive();
const id = z.string().regex(/^[A-Za-z0-9_-]{1,80}$/);
const listingInput = z.object({ materialCategory: material, estimatedWeight: positive.max(500), condition, notes: z.string().trim().max(1000).optional(), photoReference: z.string().trim().max(500).optional(), areaName: z.string().trim().min(1).max(160), latitude: z.number().finite().min(-90).max(90).optional(), longitude: z.number().finite().min(-180).max(180).optional(), estimatedPriceMin: positive.max(100000000).optional(), estimatedPriceMax: positive.max(100000000).optional(), dataBearingDevice: z.boolean().default(false), ownerPreparationCompleted: z.boolean().default(false), dataDestructionRequested: z.boolean().default(false) }).refine(value => value.estimatedPriceMin == null || value.estimatedPriceMax == null || value.estimatedPriceMin <= value.estimatedPriceMax, { message: 'Estimated minimum cannot exceed estimated maximum', path: ['estimatedPriceMax'] }).refine(value => !value.ownerPreparationCompleted || value.dataBearingDevice, { message: 'Owner preparation only applies to data-bearing devices', path: ['ownerPreparationCompleted'] }).refine(value => !value.dataDestructionRequested || value.dataBearingDevice, { message: 'Destruction requests only apply to data-bearing devices', path: ['dataDestructionRequested'] });
const pickupRequest = z.object({ kabadiwalaId: id.optional(), requestedSlot: z.string().datetime().optional() });
const cancellationInput = z.object({ reason: z.string().trim().max(500).optional() }).default({});
const activePickupStatuses = ['WAITING_FOR_PICKUP', 'REQUESTED', 'ACCEPTED', 'SCHEDULED', 'IN_TRANSIT', 'ARRIVED', 'WEIGHED'] as const;
const sourceListingIdsInput = z.array(id).max(100).default([]);
const bulkInput = z.object({ materialCategory: material, grade: z.string().trim().min(1).max(80).default('UNSPECIFIED'), quantityKg: positive.max(100000), askingRatePerKg: positive.max(1000000), minimumRatePerKg: positive.max(1000000).optional(), areaName: z.string().trim().min(1).max(160), latitude: z.number().min(-90).max(90).optional(), longitude: z.number().min(-180).max(180).optional(), notes: z.string().trim().max(1000).optional(), sourceListingIds: sourceListingIdsInput }).refine(value => !value.minimumRatePerKg || value.minimumRatePerKg <= value.askingRatePerKg, { message: 'Minimum rate cannot exceed asking rate' });
const settlementDecision = z.object({ decision: z.enum(['ACCEPT', 'RAISE_ISSUE']), reasonCode: z.string().trim().min(2).max(120).optional(), evidenceReference: z.string().trim().max(500).optional(), notes: z.string().trim().max(1000).optional() }).superRefine((value, ctx) => { if (value.decision === 'RAISE_ISSUE' && !value.reasonCode) ctx.addIssue({ code: 'custom', path: ['reasonCode'], message: 'A reason code is required when raising an issue' }); });
const listingPhotoUpload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 5 * 1024 * 1024, files: 6 } });

const parse = <T>(schema: z.ZodType<T>, value: unknown): T => {
  const result = schema.safeParse(value);
  if (!result.success) throw new AppError('VALIDATION_ERROR', 'Invalid request', 422, { details: result.error.flatten() });
  return result.data;
};

const requestHash = (value: unknown) => createHash('sha256').update(JSON.stringify(value)).digest('hex');
const operationKey = (req: any) => {
  const value = req.header('idempotency-key')?.trim();
  if (value && !/^[A-Za-z0-9._:-]{8,160}$/.test(value)) throw new AppError('VALIDATION_ERROR', 'Invalid idempotency key', 422, { code: 'INVALID_IDEMPOTENCY_KEY' });
  return value || null;
};
const jsonValue = (value: unknown) => JSON.parse(JSON.stringify(value));

function distanceKm(aLat?: number | null, aLng?: number | null, bLat?: number | null, bLng?: number | null) {
  if ([aLat, aLng, bLat, bLng].some(value => value == null)) return null;
  const radians = (value: number) => value * Math.PI / 180;
  const dLat = radians((bLat as number) - (aLat as number));
  const dLng = radians((bLng as number) - (aLng as number));
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(radians(aLat as number)) * Math.cos(radians(bLat as number)) * Math.sin(dLng / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

/**
 * Waiting pickups are discoverable only inside the same service boundary used
 * by the collector feed. The accept endpoint must repeat this check because a
 * caller can otherwise bypass the feed by guessing a listing ID.
 */
function collectorCanSeeWaitingPickup(collector: any, listing: any, maxDistanceKm = 25): boolean {
  if (!collector || !listing) return false;
  const distance = distanceKm(collector.latitude, collector.longitude, listing.latitude, listing.longitude);
  const sameArea = Boolean(
    collector.areaName &&
      listing.areaName &&
      String(collector.areaName).toLowerCase() === String(listing.areaName).toLowerCase()
  );
  return sameArea || (distance != null && distance <= maxDistanceKm) || (collector.latitude == null && collector.longitude == null);
}

async function validateSourceListings(store: any, collectorId: string, sourceListingIds: string[], materialCategory: string) {
  if (!sourceListingIds.length) return;
  const uniqueIds = [...new Set(sourceListingIds)];
  if (uniqueIds.length !== sourceListingIds.length) throw new AppError('VALIDATION_ERROR', 'sourceListingIds must be unique', 422, { code: 'DUPLICATE_SOURCE_LISTING' });
  const pickups = await store.pickupRequest.findMany({ where: { listingId: { in: uniqueIds }, kabadiwalaId: collectorId, status: 'COMPLETED' }, select: { listingId: true, finalCategory: true } });
  if (new Set(pickups.map((row: any) => row.listingId)).size !== uniqueIds.length || pickups.some((row: any) => row.finalCategory !== materialCategory)) throw new AppError('CONFLICT', 'Every source listing must be a completed pickup of the selected material', 409, { code: 'INVALID_SOURCE_LISTINGS' });
  const [allocatedLots, allocatedContributions] = await Promise.all([
    store.bulkLot.findMany({ where: { sourceListingIds: { hasSome: uniqueIds }, status: { not: 'CANCELLED' } }, select: { id: true, sourceListingIds: true, status: true } }),
    store.poolContribution.findMany({ where: { sourceListingIds: { hasSome: uniqueIds }, status: { not: 'RELEASED' } }, select: { id: true, sourceListingIds: true, status: true } })
  ]);
  if (allocatedLots.length || allocatedContributions.length) throw new AppError('CONFLICT', 'A source listing is already allocated to an active or completed formal stock record', 409, { code: 'SOURCE_LISTING_ALREADY_ALLOCATED' });
}

async function auditSupplyEvent(tx: any, actorId: string, actorRole: string, event: string, entityType: string, entityId: string, metadata: Record<string, unknown>) {
  await tx.auditEvent.create({ data: { actorId, actorRole, event, entityType, entityId, metadata } });
  await tx.materialPassportEvent.create({ data: { entityType, entityId, eventType: event, actorId, actorRole, metadata, occurredAt: new Date() } });
}

async function sendPrivateListingPhoto(storage: StorageService | undefined, listing: { photoReference?: string | null; photoReferences?: string[] | null } | null | undefined, photoIndex: number, res: any) {
  if (!storage) throw new AppError('INTERNAL_SERVER_ERROR', 'Photo storage is not configured', 503, { code: 'PHOTO_STORAGE_UNAVAILABLE' });
  const references = Array.isArray(listing?.photoReferences) && listing.photoReferences.length
    ? listing.photoReferences
    : [listing?.photoReference].filter((reference): reference is string => Boolean(reference));
  const photoReference = references[photoIndex];
  if (!photoReference) throw new AppError('NOT_FOUND', 'Listing photo is not available', 404, { code: 'PHOTO_NOT_FOUND' });
  const image = await storage.getImage(photoReference);
  res.set({ 'Content-Type': image.contentType, 'Cache-Control': 'private, max-age=300', 'X-Content-Type-Options': 'nosniff' }).status(200).send(image.body);
}

const listingPhotoIndex = z.coerce.number().int().min(0).max(5);

function uploadedPhotos(req: any): Array<{ buffer?: Buffer; mimetype?: string }> {
  const files = req.files as Record<string, Array<{ buffer?: Buffer; mimetype?: string }>> | undefined;
  return [...(files?.photos ?? []), ...(files?.photo ?? [])].slice(0, 6);
}

/** Keep storage keys private while giving Android a stable, non-null photo contract. */
function householdListingDto(listing: any) {
  const { photoReference, photoReferences, ...safeListing } = listing ?? {};
  const references = Array.isArray(photoReferences) ? photoReferences : [];
  return {
    ...safeListing,
    photoAttached: Boolean(photoReference || references.length),
    photoCount: references.length || (photoReference ? 1 : 0)
  };
}

function hasListingPhoto(listing: any): boolean {
  return Boolean(listing?.photoReference || (Array.isArray(listing?.photoReferences) && listing.photoReferences.length));
}

async function validateAndStorePhotos(storage: StorageService, files: Array<{ buffer?: Buffer; mimetype?: string }>, keyPrefix: string) {
  if (!files.length) throw new AppError('VALIDATION_ERROR', 'Photo is required', 400, { code: 'PHOTO_REQUIRED' });
  const stored: string[] = [];
  try {
    for (const [index, file] of files.entries()) {
      if (!file.buffer || !file.mimetype) throw new AppError('VALIDATION_ERROR', 'Photo is required', 400, { code: 'PHOTO_REQUIRED' });
      if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.mimetype)) throw new AppError('VALIDATION_ERROR', 'Unsupported image type', 400, { code: 'INVALID_PHOTO' });
      let metadata;
      try { metadata = await sharp(file.buffer).metadata(); } catch { throw new AppError('VALIDATION_ERROR', 'Invalid image', 400, { code: 'INVALID_PHOTO' }); }
      if (!metadata.width || !metadata.height || metadata.width < 300 || metadata.height < 300) throw new AppError('VALIDATION_ERROR', 'Photo must be at least 300 x 300 pixels', 400, { code: 'INVALID_PHOTO' });
      const key = `${keyPrefix}${index ? `-${index}` : ''}.jpg`;
      stored.push((await storage.putImage(file.buffer, key)).key);
    }
    return stored;
  } catch (error) {
    await Promise.all(stored.map(key => storage.delete(key).catch(() => undefined)));
    throw error;
  }
}

/** The closed-loop marketplace. Every endpoint is role-gated here, rather than
 * trusting the Android navigation layer. */
export function supplyChainRoutes(jwt: JwtService, collectors: CollectorRepository, db: PrismaClient, storage?: StorageService) {
  const router = Router();
  const store: any = db;
  const requireAuth = (_jwt: JwtService, _collectors: CollectorRepository) => baseRequireAuth(jwt, collectors, db);
  const requireHousehold = (_jwt: JwtService, _collectors: CollectorRepository) => baseRequireHousehold(jwt, collectors, db);
  const publicPartnerSummaries = async (profiles: any[], latitude?: number, longitude?: number) => {
    if (!profiles.length) return [];
    const ids = profiles.map(profile => profile.id);
    const [completed, reviews, pickupDays] = await Promise.all([
      store.pickupRequest.findMany({ where: { kabadiwalaId: { in: ids }, status: 'COMPLETED' }, select: { kabadiwalaId: true, actualWeight: true, finalCategory: true } }),
      store.householdPickupReview?.findMany
        ? store.householdPickupReview.findMany({ where: { kabadiwalaId: { in: ids }, verified: true }, select: { kabadiwalaId: true, rating: true } })
        : Promise.resolve([]),
      store.collectorPickupDay?.findMany
        ? store.collectorPickupDay.findMany({ where: { collectorId: { in: ids }, dayKey: new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' }).format(new Date()) }, select: { collectorId: true, capacity: true, bookedCount: true } })
        : Promise.resolve([])
    ]);
    const todayByCollector = new Map(pickupDays.map((row: any) => [row.collectorId, row]));
    return profiles.map(profile => {
      const partnerPickups = completed.filter((row: any) => row.kabadiwalaId === profile.id);
      const partnerReviews = reviews.filter((row: any) => row.kabadiwalaId === profile.id);
      const today: any = todayByCollector.get(profile.id);
      const capacity = today?.capacity ?? profile.dailyPickupCapacity ?? 8;
      const slots = Math.max(0, capacity - (today?.bookedCount ?? 0));
      const distance = distanceKm(latitude, longitude, profile.latitude, profile.longitude);
      return {
        id: profile.id,
        displayName: profile.displayName,
        areaName: profile.areaName,
        verified: profile.pilotVerifiedAt != null,
        distanceKm: distance == null ? null : Number(distance.toFixed(1)),
        acceptingPickups: slots > 0,
        availablePickupSlots: slots,
        completedPickupCount: partnerPickups.length,
        acceptedWeightKg: Number(partnerPickups.reduce((sum: number, row: any) => sum + (row.actualWeight ?? 0), 0).toFixed(2)),
        collectedMaterials: [...new Set(partnerPickups.map((row: any) => row.finalCategory).filter(Boolean))],
        ratingAverage: partnerReviews.length ? Number((partnerReviews.reduce((sum: number, row: any) => sum + row.rating, 0) / partnerReviews.length).toFixed(2)) : null,
        reviewCount: partnerReviews.length
      };
    });
  };

  router.post('/household/listings', requireHousehold(jwt, collectors), async (req, res) => {
    const input = parse(listingInput, req.body);
    const operationId = operationKey(req);
    const operationHash = requestHash({ action: 'CREATE_HOUSEHOLD_LISTING', input });
    const destructionEvidenceStatus = !input.dataBearingDevice
      ? 'NOT_APPLICABLE'
      : input.dataDestructionRequested
        ? 'DESTRUCTION_REQUESTED'
        : input.ownerPreparationCompleted
          ? 'OWNER_PREPARATION_COMPLETED'
          : 'OWNER_PREPARATION_PENDING';
    const result = await store.$transaction(async (tx: any) => {
      if (operationId) {
        const replay = await tx.idempotencyRecord.findUnique({ where: { actorId_operationId: { actorId: req.identity!.collectorId, operationId } } });
        if (replay) {
          if (replay.requestHash && replay.requestHash !== operationHash) throw new AppError('CONFLICT', 'Idempotency key was already used for a different listing', 409, { code: 'IDEMPOTENCY_KEY_REUSED' });
          return { listing: replay.response, replayed: true };
        }
      }
      // Photo upload is a separate authenticated multipart mutation. Keep the
      // record as a draft until that mutation succeeds so a caller cannot
      // publish an image-less listing by calling this JSON endpoint directly.
      const created = await tx.householdListing.create({ data: { ...input, photoReference: null, photoReferences: [], destructionEvidenceStatus, householdId: req.identity!.collectorId, status: 'DRAFT' } });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'HOUSEHOLD', 'HOUSEHOLD_LISTING_DRAFTED', 'HOUSEHOLD_LISTING', created.id, { materialCategory: created.materialCategory, estimatedWeight: created.estimatedWeight, areaName: created.areaName, dataBearingDevice: created.dataBearingDevice, dataDestructionRequested: created.dataDestructionRequested });
      if (operationId) await tx.idempotencyRecord.create({ data: { actorId: req.identity!.collectorId, operationId, action: 'CREATE_HOUSEHOLD_LISTING', entityId: created.id, requestHash: operationHash, response: jsonValue(created) } });
      return { listing: created, replayed: false };
    });
    res.status(result.replayed ? 200 : 201).json({ success: true, data: householdListingDto(result.listing), ...(result.replayed ? { message: 'Listing already created' } : {}) });
  });
  router.post('/household/listings/:listingId/photo', requireHousehold(jwt, collectors), listingPhotoUpload.fields([{ name: 'photos', maxCount: 6 }, { name: 'photo', maxCount: 1 }]), async (req, res) => {
    if (!storage) throw new AppError('INTERNAL_SERVER_ERROR', 'Photo storage is not configured', 503, { code: 'PHOTO_STORAGE_UNAVAILABLE' });
    const listingId = parse(id, req.params.listingId);
    const files = uploadedPhotos(req);
    const listing = await store.householdListing.findFirst({ where: { id: listingId, householdId: req.identity!.collectorId, status: { in: ['DRAFT', 'POSTED'] } } });
    if (!listing) throw new AppError('CONFLICT', 'Only an open listing can accept a photo', 409, { code: 'LISTING_NOT_EDITABLE' });
    // Stable indexed keys make retries converge on the same objects instead of
    // creating unreferenced uploads for every network retry.
    const keys = await validateAndStorePhotos(storage, files, `household-listings/${req.identity!.collectorId}/${listingId}`);
    try {
      const updated = await store.$transaction(async (tx: any) => {
        const changed = await tx.householdListing.updateMany({ where: { id: listingId, householdId: req.identity!.collectorId, status: { in: ['DRAFT', 'POSTED'] } }, data: { photoReference: keys[0], photoReferences: keys, status: 'POSTED' } });
        if (!changed.count) throw new AppError('CONFLICT', 'Listing changed while uploading photo', 409, { code: 'LISTING_UPDATE_CONFLICT' });
        const row = await tx.householdListing.findUnique({ where: { id: listingId } });
        await auditSupplyEvent(tx, req.identity!.collectorId, 'HOUSEHOLD', 'HOUSEHOLD_LISTING_PHOTO_ATTACHED', 'HOUSEHOLD_LISTING', listingId, { contentType: 'image/jpeg', source: 'authenticated-upload', photoCount: keys.length });
        await auditSupplyEvent(tx, req.identity!.collectorId, 'HOUSEHOLD', 'HOUSEHOLD_LISTED', 'HOUSEHOLD_LISTING', listingId, { photoCount: keys.length });
        return row;
      });
      res.json({ success: true, data: householdListingDto(updated) });
    } catch (error) {
      await Promise.all(keys.map(key => storage.delete(key).catch(() => undefined)));
      throw error;
    }
  });
  router.get('/household/listings/:listingId/photo', requireHousehold(jwt, collectors), async (req, res) => {
    const listingId = parse(id, req.params.listingId);
    const listing = await store.householdListing.findFirst({ where: { id: listingId, householdId: req.identity!.collectorId }, select: { photoReference: true, photoReferences: true } });
    if (!listing) throw new AppError('NOT_FOUND', 'Listing not found', 404, { code: 'LISTING_NOT_FOUND' });
    await sendPrivateListingPhoto(storage, listing, 0, res);
  });
  router.get('/household/listings/:listingId/photo/:photoIndex', requireHousehold(jwt, collectors), async (req, res) => {
    const listingId = parse(id, req.params.listingId);
    const photoIndex = parse(listingPhotoIndex, req.params.photoIndex);
    const listing = await store.householdListing.findFirst({ where: { id: listingId, householdId: req.identity!.collectorId }, select: { photoReference: true, photoReferences: true } });
    if (!listing) throw new AppError('NOT_FOUND', 'Listing not found', 404, { code: 'LISTING_NOT_FOUND' });
    await sendPrivateListingPhoto(storage, listing, photoIndex, res);
  });
  router.get('/household/listings', requireHousehold(jwt, collectors), async (req, res) => {
    const listings = await store.householdListing.findMany({ where: { householdId: req.identity!.collectorId }, orderBy: { createdAt: 'desc' } });
    res.json({ success: true, data: listings.map(householdListingDto) });
  });
  router.get('/household/listings/:listingId', requireHousehold(jwt, collectors), async (req, res) => {
    const listingId = parse(id, req.params.listingId);
    const listing = await store.householdListing.findFirst({ where: { id: listingId, householdId: req.identity!.collectorId } });
    if (!listing) throw new AppError('NOT_FOUND', 'Listing not found', 404, { code: 'LISTING_NOT_FOUND' });
    const pickups = await store.pickupRequest.findMany({ where: { listingId, householdId: req.identity!.collectorId }, orderBy: { createdAt: 'desc' } });
    res.json({ success: true, data: { ...householdListingDto(listing), pickups } });
  });
  router.patch('/household/listings/:listingId', requireHousehold(jwt, collectors), async (req, res) => {
    const listingId = parse(id, req.params.listingId);
    const input = parse(listingInput.partial(), req.body);
    const current = await store.householdListing.findFirst({ where: { id: listingId, householdId: req.identity!.collectorId, status: { in: ['DRAFT', 'POSTED'] } } });
    if (!current) throw new AppError('CONFLICT', 'Only an open listing can be edited', 409, { code: 'LISTING_NOT_EDITABLE' });
    const merged = { ...current, ...input };
    const checked = parse(listingInput, merged);
    const destructionEvidenceStatus = !checked.dataBearingDevice
      ? 'NOT_APPLICABLE'
      : checked.dataDestructionRequested
        ? 'DESTRUCTION_REQUESTED'
        : checked.ownerPreparationCompleted
          ? 'OWNER_PREPARATION_COMPLETED'
          : 'OWNER_PREPARATION_PENDING';
    const updated = await store.$transaction(async (tx: any) => {
      const row = await tx.householdListing.update({ where: { id: listingId }, data: { ...input, destructionEvidenceStatus, dataBearingDevice: checked.dataBearingDevice, ownerPreparationCompleted: checked.ownerPreparationCompleted, dataDestructionRequested: checked.dataDestructionRequested } });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'HOUSEHOLD', 'HOUSEHOLD_LISTING_UPDATED', 'HOUSEHOLD_LISTING', listingId, { dataBearingDevice: row.dataBearingDevice, dataDestructionRequested: row.dataDestructionRequested });
      return row;
    });
    res.json({ success: true, data: householdListingDto(updated) });
  });
  router.get('/admin/kabadiwala-cohort', requireAdmin(jwt, db, 'PARTNER_VERIFICATION'), async (req, res) => {
    const status = parse(z.enum(['PENDING', 'VERIFIED']).default('PENDING'), req.query.status);
    const users = await store.user.findMany({ where: { role: 'COLLECTOR', accountStatus: 'ACTIVE', collectorProfileId: { not: null } }, select: { collectorProfileId: true } });
    const ids = users.map((user: { collectorProfileId: string | null }) => user.collectorProfileId).filter(Boolean);
    const profiles = ids.length ? await store.collector.findMany({
      where: { id: { in: ids }, accountStatus: 'ACTIVE', pilotVerifiedAt: status === 'VERIFIED' ? { not: null } : null },
      orderBy: { createdAt: 'asc' },
      select: { id: true, displayName: true, areaName: true, createdAt: true, pilotVerifiedAt: true }
    }) : [];
    res.json({ success: true, data: profiles.map((profile: any) => ({ id: profile.id, displayName: profile.displayName, areaName: profile.areaName, createdAt: profile.createdAt, verifiedAt: profile.pilotVerifiedAt })) });
  });
  router.post('/admin/kabadiwala-cohort/:kabadiwalaId/verification', requireAdmin(jwt, db, 'PARTNER_VERIFICATION'), async (req, res) => {
    const kabadiwalaId = parse(id, req.params.kabadiwalaId);
    const input = parse(z.object({ decision: z.enum(['APPROVE', 'REVOKE']), notes: z.string().trim().min(8).max(500) }).strict(), req.body);
    const account = await store.user.findFirst({ where: { collectorProfileId: kabadiwalaId, role: 'COLLECTOR', accountStatus: 'ACTIVE' }, select: { id: true } });
    const profile = account ? await store.collector.findFirst({ where: { id: kabadiwalaId, accountStatus: 'ACTIVE' }, select: { id: true, displayName: true, areaName: true } }) : null;
    if (!profile) throw new AppError('NOT_FOUND', 'Active Kabadiwala profile not found', 404, { code: 'KABADIWALA_NOT_FOUND' });
    const verifiedAt = input.decision === 'APPROVE' ? new Date() : null;
    const updated = await store.$transaction(async (tx: any) => {
      const row = await tx.collector.update({ where: { id: kabadiwalaId }, data: { pilotVerifiedAt: verifiedAt, pilotVerifiedBy: verifiedAt ? req.identity!.collectorId : null } });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'ADMIN', input.decision === 'APPROVE' ? 'KABADIWALA_PILOT_APPROVED' : 'KABADIWALA_PILOT_REVOKED', 'COLLECTOR', kabadiwalaId, { notes: input.notes });
      return row;
    });
    res.json({ success: true, data: { id: updated.id, displayName: updated.displayName, areaName: updated.areaName, verifiedAt: updated.pilotVerifiedAt } });
  });
  router.get('/household/kabadiwalas', requireHousehold(jwt, collectors), async (req, res) => {
    const locationQuery = parse(z.object({
      latitude: z.coerce.number().finite().min(-90).max(90).optional(),
      longitude: z.coerce.number().finite().min(-180).max(180).optional(),
      area: z.string().trim().max(160).optional(),
      radiusKm: z.coerce.number().finite().positive().max(200).default(25),
      page: z.coerce.number().int().min(1).max(10000).default(1),
      limit: z.coerce.number().int().min(1).max(50).default(20)
    }).refine(value => (value.latitude === undefined) === (value.longitude === undefined), { message: 'Both latitude and longitude are required' }), req.query);
    const areaQuery = locationQuery.area?.trim() || '';
    if (locationQuery.latitude === undefined && !areaQuery) {
      return res.json({ success: true, data: { items: [], pagination: { page: locationQuery.page, limit: locationQuery.limit, total: 0, totalPages: 0 }, requiresLocation: true } });
    }
    const users = await store.user.findMany({ where: { role: 'COLLECTOR', accountStatus: 'ACTIVE', collectorProfileId: { not: null } }, select: { collectorProfileId: true } });
    const ids = users.map((user: { collectorProfileId: string | null }) => user.collectorProfileId).filter(Boolean);
    const profiles = ids.length ? await store.collector.findMany({ where: { id: { in: ids }, accountStatus: 'ACTIVE', pilotVerifiedAt: { not: null } }, select: { id: true, displayName: true, areaName: true, latitude: true, longitude: true, dailyPickupCapacity: true, pilotVerifiedAt: true } }) : [];
    const matching = profiles.map((profile: any) => ({ profile, distance: distanceKm(locationQuery.latitude, locationQuery.longitude, profile.latitude, profile.longitude) }))
      .filter(({ profile, distance }: any) => {
        if (locationQuery.latitude !== undefined) return distance != null && distance <= locationQuery.radiusKm;
        return String(profile.areaName ?? '').toLocaleLowerCase().includes(areaQuery.toLocaleLowerCase());
      })
      .sort((left: any, right: any) => (left.distance ?? Number.POSITIVE_INFINITY) - (right.distance ?? Number.POSITIVE_INFINITY) || left.profile.id.localeCompare(right.profile.id));
    const total = matching.length;
    const pageRows = matching.slice((locationQuery.page - 1) * locationQuery.limit, locationQuery.page * locationQuery.limit);
    const items = await publicPartnerSummaries(pageRows.map((row: any) => row.profile), locationQuery.latitude, locationQuery.longitude);
    res.json({ success: true, data: {
      items,
      pagination: { page: locationQuery.page, limit: locationQuery.limit, total, totalPages: Math.ceil(total / locationQuery.limit) },
      requiresLocation: false,
      locationFilter: { area: areaQuery || null, radiusKm: locationQuery.latitude === undefined ? null : locationQuery.radiusKm }
    } });
  });
  router.get('/household/kabadiwalas/:kabadiwalaId', requireHousehold(jwt, collectors), async (req, res) => {
    const kabadiwalaId = parse(id, req.params.kabadiwalaId);
    const location = parse(z.object({ latitude: z.coerce.number().finite().min(-90).max(90).optional(), longitude: z.coerce.number().finite().min(-180).max(180).optional() }).refine(value => (value.latitude === undefined) === (value.longitude === undefined), { message: 'Both latitude and longitude are required' }), req.query);
    const account = await store.user.findFirst({ where: { collectorProfileId: kabadiwalaId, role: 'COLLECTOR', accountStatus: 'ACTIVE' }, select: { id: true } });
    const profile = account ? await store.collector.findFirst({ where: { id: kabadiwalaId, accountStatus: 'ACTIVE', pilotVerifiedAt: { not: null } }, select: { id: true, displayName: true, areaName: true, latitude: true, longitude: true, dailyPickupCapacity: true, createdAt: true, pilotVerifiedAt: true } }) : null;
    if (!profile) throw new AppError('NOT_FOUND', 'Active Kabadiwala profile not found', 404, { code: 'KABADIWALA_NOT_FOUND' });
    const [summary] = await publicPartnerSummaries([profile], location.latitude, location.longitude);
    res.json({ success: true, data: { ...summary, memberSince: profile.createdAt } });
  });
  router.post('/household/pickups/:pickupId/review', requireHousehold(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId);
    const input = parse(z.object({ rating: z.number().int().min(1).max(5) }).strict(), req.body);
    const pickup = await store.pickupRequest.findFirst({ where: { id: pickupId, householdId: req.identity!.collectorId, status: 'COMPLETED', kabadiwalaId: { not: null } }, select: { id: true, kabadiwalaId: true } });
    if (!pickup) throw new AppError('CONFLICT', 'A rating is available only after a completed pickup', 409, { code: 'PICKUP_NOT_REVIEWABLE' });
    const prior = await store.householdPickupReview.findUnique({ where: { pickupId } });
    if (prior) throw new AppError('CONFLICT', 'This completed pickup has already been rated', 409, { code: 'PICKUP_ALREADY_REVIEWED' });
    try {
      const review = await store.$transaction(async (tx: any) => {
        const created = await tx.householdPickupReview.create({ data: { pickupId, householdId: req.identity!.collectorId, kabadiwalaId: pickup.kabadiwalaId, rating: input.rating, verified: true } });
        await auditSupplyEvent(tx, req.identity!.collectorId, 'HOUSEHOLD', 'HOUSEHOLD_PICKUP_RATED', 'PICKUP_REQUEST', pickupId, { rating: input.rating, kabadiwalaId: pickup.kabadiwalaId });
        return created;
      });
      const ratings = await store.householdPickupReview.findMany({ where: { kabadiwalaId: pickup.kabadiwalaId, verified: true }, select: { rating: true } });
      res.status(201).json({ success: true, data: { id: review.id, pickupId, rating: review.rating, verified: true, ratingAverage: Number((ratings.reduce((sum: number, row: any) => sum + row.rating, 0) / ratings.length).toFixed(2)), reviewCount: ratings.length } });
    } catch (error: any) {
      if (error?.code === 'P2002') throw new AppError('CONFLICT', 'This completed pickup has already been rated', 409, { code: 'PICKUP_ALREADY_REVIEWED' });
      throw error;
    }
  });
  router.get('/household/pickups/:pickupId/reassignment-options', requireHousehold(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId);
    const pickup = await store.pickupRequest.findFirst({ where: { id: pickupId, householdId: req.identity!.collectorId, status: 'REASSIGNMENT_REQUIRED' }, select: { id: true, kabadiwalaId: true, listingId: true } });
    if (!pickup) throw new AppError('NOT_FOUND', 'Reassignment options are not available for this pickup', 404, { code: 'REASSIGNMENT_NOT_AVAILABLE' });
    const listing = await store.householdListing.findFirst({ where: { id: pickup.listingId, householdId: req.identity!.collectorId }, select: { materialCategory: true, latitude: true, longitude: true, areaName: true } });
    const users = await store.user.findMany({ where: { role: 'COLLECTOR', accountStatus: 'ACTIVE', collectorProfileId: { not: null } }, select: { collectorProfileId: true } });
    const ids = users.map((row: any) => row.collectorProfileId).filter((value: any): value is string => Boolean(value) && value !== pickup.kabadiwalaId);
    const profiles = ids.length ? await store.collector.findMany({ where: { id: { in: ids }, accountStatus: 'ACTIVE', pilotVerifiedAt: { not: null } }, select: { id: true, displayName: true, areaName: true, latitude: true, longitude: true } }) : [];
    const options = profiles.map((profile: any) => {
      const distance = distanceKm(listing?.latitude, listing?.longitude, profile.latitude, profile.longitude);
      return { id: profile.id, displayName: profile.displayName, areaName: profile.areaName, distanceKm: distance == null ? null : Number(distance.toFixed(1)), sameArea: Boolean(listing?.areaName && profile.areaName && listing.areaName.toLowerCase() === profile.areaName.toLowerCase()) };
    }).sort((a: any, b: any) => (a.distanceKm ?? Number.POSITIVE_INFINITY) - (b.distanceKm ?? Number.POSITIVE_INFINITY));
    res.json({ success: true, data: { pickupId, materialCategory: listing?.materialCategory ?? null, options, privacy: 'Only active Kabadiwala profiles are shown; private inventory and reliability details are not disclosed.' } });
  });
  router.post('/household/pickups/:pickupId/reassign', requireHousehold(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId);
    const input = parse(z.object({ kabadiwalaId: id, requestedSlot: z.string().datetime().optional() }).strict(), req.body);
    const operationId = operationKey(req);
    const operationHash = requestHash({ action: 'HOUSEHOLD_REASSIGN_PICKUP', pickupId, input });
    const result = await store.$transaction(async (tx: any) => {
      if (operationId) {
        const replay = await tx.idempotencyRecord.findUnique({ where: { actorId_operationId: { actorId: req.identity!.collectorId, operationId } } });
        if (replay) {
          if (replay.requestHash && replay.requestHash !== operationHash) throw new AppError('CONFLICT', 'Idempotency key was already used for a different reassignment', 409, { code: 'IDEMPOTENCY_KEY_REUSED' });
          return { pickup: replay.response, replayed: true, targetAccountId: null };
        }
      }
      const pickup = await tx.pickupRequest.findFirst({ where: { id: pickupId, householdId: req.identity!.collectorId, status: 'REASSIGNMENT_REQUIRED' } });
      if (!pickup) throw new AppError('CONFLICT', 'This pickup is not awaiting reassignment', 409, { code: 'PICKUP_NOT_REASSIGNABLE' });
      if (pickup.kabadiwalaId === input.kabadiwalaId) throw new AppError('VALIDATION_ERROR', 'Choose a different Kabadiwala', 422, { code: 'SAME_KABADIWALA_SELECTED' });
      const target = await tx.user.findFirst({ where: { collectorProfileId: input.kabadiwalaId, role: 'COLLECTOR', accountStatus: 'ACTIVE' }, select: { collectorProfileId: true } });
      if (!target) throw new AppError('NOT_FOUND', 'Kabadiwala not found', 404, { code: 'KABADIWALA_NOT_FOUND' });
      const targetProfile = await tx.collector.findFirst({ where: { id: input.kabadiwalaId, accountStatus: 'ACTIVE', pilotVerifiedAt: { not: null } }, select: { id: true } });
      if (!targetProfile) throw new AppError('NOT_FOUND', 'Kabadiwala not found', 404, { code: 'KABADIWALA_NOT_FOUND' });

      // A previous cancelled/rejected request for this same listing and
      // collector may already occupy the compound unique key. Reuse that
      // historical row and close the old assignment, otherwise claim the
      // existing reassignment row with a conditional update.
      const priorTarget = await tx.pickupRequest.findUnique({ where: { listingId_kabadiwalaId: { listingId: pickup.listingId, kabadiwalaId: input.kabadiwalaId } } });
      let reassigned;
      const lifecycleReset = { status: 'REQUESTED', requestedSlot: input.requestedSlot ? new Date(input.requestedSlot) : null, scheduledSlot: null, acceptedAt: null, availabilityConfirmedAt: null, inTransitAt: null, arrivedAt: null, weighedAt: null, cancelledAt: null, noShow: false, lateCancellation: false, reassignmentReason: null, actualWeight: null, finalCategory: null, grade: null, ratePerKg: null, finalAmount: null, settlementStatus: null, settlementBeforeValue: null, settlementAfterValue: null, settlementReasonCode: null, settlementEvidenceReference: null, householdDecision: null, settlementDecisionAt: null, settlementDisputeNotes: null, cancelledBy: null, cancellationReason: null, completedAt: null };
      if (priorTarget && priorTarget.id !== pickup.id) {
        if (activePickupStatuses.includes(priorTarget.status) || priorTarget.status === 'REASSIGNMENT_REQUIRED') throw new AppError('CONFLICT', 'This Kabadiwala already has an active request for the listing', 409, { code: 'PICKUP_TARGET_ALREADY_ASSIGNED' });
        const closed = await tx.pickupRequest.updateMany({ where: { id: pickup.id, householdId: req.identity!.collectorId, status: 'REASSIGNMENT_REQUIRED' }, data: { status: 'CANCELLED', cancelledBy: 'HOUSEHOLD', cancellationReason: 'HOUSEHOLD_SELECTED_REPLACEMENT', cancelledAt: new Date() } });
        if (!closed.count) throw new AppError('CONFLICT', 'Pickup reassignment was already completed', 409, { code: 'PICKUP_REASSIGNMENT_CONFLICT' });
        const claimedPrior = await tx.pickupRequest.updateMany({ where: { id: priorTarget.id, householdId: req.identity!.collectorId, status: { in: ['CANCELLED', 'REJECTED'] } }, data: lifecycleReset });
        if (!claimedPrior.count) throw new AppError('CONFLICT', 'The selected Kabadiwala request was already updated', 409, { code: 'PICKUP_TARGET_UPDATE_CONFLICT' });
        reassigned = await tx.pickupRequest.findUniqueOrThrow({ where: { id: priorTarget.id } });
      } else {
        const claimed = await tx.pickupRequest.updateMany({ where: { id: pickupId, householdId: req.identity!.collectorId, status: 'REASSIGNMENT_REQUIRED' }, data: { ...lifecycleReset, kabadiwalaId: input.kabadiwalaId } });
        if (!claimed.count) throw new AppError('CONFLICT', 'Pickup reassignment was already completed', 409, { code: 'PICKUP_REASSIGNMENT_CONFLICT' });
        reassigned = await tx.pickupRequest.findUniqueOrThrow({ where: { id: pickupId } });
      }
      await tx.householdListing.updateMany({ where: { id: pickup.listingId, householdId: req.identity!.collectorId, status: 'POSTED' }, data: { status: 'MATCHED' } });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'HOUSEHOLD', 'PICKUP_REASSIGNED', 'PICKUP_REQUEST', reassigned.id, { previousKabadiwalaId: pickup.kabadiwalaId, kabadiwalaId: input.kabadiwalaId, requestedSlot: input.requestedSlot ?? null });
      if (operationId) await tx.idempotencyRecord.create({ data: { actorId: req.identity!.collectorId, operationId, action: 'HOUSEHOLD_REASSIGN_PICKUP', entityId: reassigned.id, requestHash: operationHash, response: jsonValue(reassigned) } });
      return { pickup: reassigned, replayed: false, targetAccountId: target.collectorProfileId };
    });
    if (!result.replayed && result.targetAccountId) await emitNotification(store, { accountId: result.targetAccountId, type: 'PICKUP_REASSIGNED_TO_COLLECTOR', title: 'New pickup request', body: 'A household selected you for a pickup request. Review and accept it when available.', route: `kabadiwala/pickups/${result.pickup.id}`, dedupeKey: `PICKUP_REASSIGNED_TO_COLLECTOR:${result.pickup.id}` });
    res.status(result.replayed ? 200 : 201).json({ success: true, data: result.pickup, ...(result.replayed ? { message: 'Pickup reassignment already processed' } : {}) });
  });
  router.post('/household/listings/:listingId/pickups', requireHousehold(jwt, collectors), async (req, res) => {
    const listingId = parse(id, req.params.listingId); const input = parse(pickupRequest, req.body);
    const requestedSlot = input.requestedSlot ? validatePickupSlot(input.requestedSlot) : null;
    const operationId = operationKey(req);
    const operationHash = requestHash({ action: 'REQUEST_PICKUP', listingId, input });
    const listingForPickup = await store.householdListing.findFirst({ where: { id: listingId, householdId: req.identity!.collectorId }, select: { photoReference: true, photoReferences: true } });
    if (!listingForPickup) throw new AppError('NOT_FOUND', 'Listing not found', 404, { code: 'LISTING_NOT_FOUND' });
    if (!hasListingPhoto(listingForPickup)) throw new AppError('CONFLICT', 'Add at least one photo before requesting pickup', 409, { code: 'PHOTO_REQUIRED' });
    if (!input.kabadiwalaId) {
      const result = await store.$transaction(async (tx: any) => {
        if (operationId) {
          const replay = await tx.idempotencyRecord.findUnique({ where: { actorId_operationId: { actorId: req.identity!.collectorId, operationId } } });
          if (replay) {
            if (replay.requestHash && replay.requestHash !== operationHash) throw new AppError('CONFLICT', 'Idempotency key was already used for a different pickup request', 409, { code: 'IDEMPOTENCY_KEY_REUSED' });
            return { pickup: replay.response, created: false, replayed: true };
          }
        }
        const existing = await tx.pickupRequest.findFirst({ where: { listingId, householdId: req.identity!.collectorId, status: { in: activePickupStatuses } }, orderBy: { createdAt: 'desc' } });
        if (existing) {
          if (existing.status !== 'WAITING_FOR_PICKUP') throw new AppError('CONFLICT', 'A pickup request is already active for this listing', 409);
          if (operationId) await tx.idempotencyRecord.create({ data: { actorId: req.identity!.collectorId, operationId, action: 'REQUEST_PICKUP', entityId: existing.id, requestHash: operationHash, response: jsonValue(existing) } });
          return { pickup: existing, created: false, replayed: false };
        }
        const claimedListing = await tx.householdListing.updateMany({ where: { id: listingId, householdId: req.identity!.collectorId, status: 'POSTED' }, data: { status: 'MATCHED' } });
        if (!claimedListing.count) throw new AppError('CONFLICT', 'This listing is no longer available for a pickup request', 409);
        const pickup = await tx.pickupRequest.create({ data: { listingId, householdId: req.identity!.collectorId, kabadiwalaId: null, status: 'WAITING_FOR_PICKUP', requestedSlot } });
        await auditSupplyEvent(tx, req.identity!.collectorId, 'HOUSEHOLD', 'PICKUP_WAITING_FOR_PICKUP', 'PICKUP_REQUEST', pickup.id, { listingId, requestedSlot: requestedSlot?.toISOString() ?? null });
        if (operationId) await tx.idempotencyRecord.create({ data: { actorId: req.identity!.collectorId, operationId, action: 'REQUEST_PICKUP', entityId: pickup.id, requestHash: operationHash, response: jsonValue(pickup) } });
        return { pickup, created: true, replayed: false };
      });
      if (result.created && !result.replayed) {
        const listing = await store.householdListing.findFirst({ where: { id: listingId, householdId: req.identity!.collectorId }, select: { areaName: true, latitude: true, longitude: true } });
        const users = await store.user.findMany({ where: { role: 'COLLECTOR', accountStatus: 'ACTIVE', collectorProfileId: { not: null } }, select: { collectorProfileId: true } });
        const ids = users.map((row: { collectorProfileId: string | null }) => row.collectorProfileId).filter((value: string | null): value is string => Boolean(value));
        const profiles = ids.length ? await store.collector.findMany({ where: { id: { in: ids }, accountStatus: 'ACTIVE', pilotVerifiedAt: { not: null } }, select: { id: true, areaName: true, latitude: true, longitude: true } }) : [];
        const matching = profiles.filter((profile: any) => {
          const distance = distanceKm(listing?.latitude, listing?.longitude, profile.latitude, profile.longitude);
          const sameArea = Boolean(listing?.areaName && profile.areaName && listing.areaName.toLowerCase() === profile.areaName.toLowerCase());
          return sameArea || (distance != null && distance <= 25) || (listing?.latitude == null && listing?.longitude == null);
        });
        await Promise.allSettled(matching.map((profile: any) => emitNotification(store, { accountId: profile.id, type: 'PICKUP_WAITING_FOR_PICKUP', title: 'Pickup needed nearby', body: 'A household is waiting for a Kabadiwala. Open pickups to claim it.', route: `kabadiwala/pickups/${result.pickup.id}`, dedupeKey: `PICKUP_WAITING_FOR_PICKUP:${result.pickup.id}:${profile.id}` })));
      }
      return res.status(result.created ? 201 : 200).json({ success: true, data: result.pickup, ...(result.replayed ? { message: 'Pickup request already processed' } : {}) });
    }
    const kabadiwala = await store.user.findFirst({ where: { collectorProfileId: input.kabadiwalaId, role: 'COLLECTOR', accountStatus: 'ACTIVE' } });
    if (!kabadiwala) throw new AppError('NOT_FOUND', 'Kabadiwala not found', 404);
    const verifiedKabadiwala = await store.collector.findFirst({ where: { id: input.kabadiwalaId, accountStatus: 'ACTIVE', pilotVerifiedAt: { not: null } }, select: { id: true } });
    if (!verifiedKabadiwala) throw new AppError('NOT_FOUND', 'Kabadiwala not found', 404, { code: 'KABADIWALA_NOT_FOUND' });
    // Claim the listing and create the request in one transaction. The
    // conditional POSTED -> MATCHED update is the single-winner guard when
    // two kabadiwalas are selected concurrently.
    const result = await store.$transaction(async (tx: any) => {
      if (operationId) {
        const replay = await tx.idempotencyRecord.findUnique({ where: { actorId_operationId: { actorId: req.identity!.collectorId, operationId } } });
        if (replay) {
          if (replay.requestHash && replay.requestHash !== operationHash) throw new AppError('CONFLICT', 'Idempotency key was already used for a different pickup request', 409, { code: 'IDEMPOTENCY_KEY_REUSED' });
          return { pickup: replay.response, created: false, replayed: true };
        }
      }
      const existing = await tx.pickupRequest.findUnique({ where: { listingId_kabadiwalaId: { listingId, kabadiwalaId: input.kabadiwalaId } } });
      if (existing && activePickupStatuses.includes(existing.status)) {
        if (operationId) await tx.idempotencyRecord.create({ data: { actorId: req.identity!.collectorId, operationId, action: 'REQUEST_PICKUP', entityId: existing.id, requestHash: operationHash, response: jsonValue(existing) } });
        return { pickup: existing, created: false, replayed: false };
      }
      const claimedListing = await tx.householdListing.updateMany({ where: { id: listingId, householdId: req.identity!.collectorId, status: 'POSTED' }, data: { status: 'MATCHED' } });
      if (!claimedListing.count) {
        const active = await tx.pickupRequest.findFirst({ where: { listingId, status: { in: activePickupStatuses } }, select: { kabadiwalaId: true } });
        if (active?.kabadiwalaId === input.kabadiwalaId) {
          const retry = await tx.pickupRequest.findUniqueOrThrow({ where: { listingId_kabadiwalaId: { listingId, kabadiwalaId: input.kabadiwalaId } } });
          if (operationId) await tx.idempotencyRecord.create({ data: { actorId: req.identity!.collectorId, operationId, action: 'REQUEST_PICKUP', entityId: retry.id, requestHash: operationHash, response: jsonValue(retry) } });
          return { pickup: retry, created: false, replayed: false };
        }
        throw new AppError('CONFLICT', 'A pickup request is already active for this listing', 409);
      }
      const pickup = await tx.pickupRequest.upsert({
        where: { listingId_kabadiwalaId: { listingId, kabadiwalaId: input.kabadiwalaId } },
        update: { requestedSlot: requestedSlot ?? undefined, status: 'REQUESTED', cancelledBy: null, cancellationReason: null },
        create: { listingId, householdId: req.identity!.collectorId, kabadiwalaId: input.kabadiwalaId, requestedSlot }
      });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'HOUSEHOLD', 'PICKUP_REQUESTED', 'PICKUP_REQUEST', pickup.id, { listingId, kabadiwalaId: input.kabadiwalaId, requestedSlot: requestedSlot?.toISOString() ?? null });
      if (operationId) await tx.idempotencyRecord.create({ data: { actorId: req.identity!.collectorId, operationId, action: 'REQUEST_PICKUP', entityId: pickup.id, requestHash: operationHash, response: jsonValue(pickup) } });
      return { pickup, created: !existing, replayed: false };
    });
    res.status(result.created ? 201 : 200).json({ success: true, data: result.pickup, ...(result.replayed ? { message: 'Pickup request already processed' } : {}) });
  });
  router.get('/household/pickups', requireHousehold(jwt, collectors), async (req, res) => {
    const pickups = await store.pickupRequest.findMany({ where: { householdId: req.identity!.collectorId }, orderBy: { createdAt: 'desc' } });
    const ids = pickups.map((pickup: any) => pickup.id);
    const [reviews, payments] = await Promise.all([
      ids.length && store.householdPickupReview?.findMany ? store.householdPickupReview.findMany({ where: { pickupId: { in: ids } }, select: { pickupId: true, rating: true } }) : Promise.resolve([]),
      ids.length && store.pickupSettlementPayment?.findMany ? store.pickupSettlementPayment.findMany({ where: { pickupId: { in: ids } }, select: { pickupId: true, amount: true, paymentMethod: true, recordedAt: true, reference: true, status: true } }) : Promise.resolve([])
    ]);
    const reviewByPickup = new Map(reviews.map((review: any) => [review.pickupId, review.rating]));
    const paymentByPickup = new Map(payments.map((payment: any) => [payment.pickupId, payment]));
    res.json({ success: true, data: pickups.map((pickup: any) => ({
      ...pickup,
      householdReviewRating: reviewByPickup.get(pickup.id) ?? null,
      settlementPayment: paymentByPickup.get(pickup.id) ?? null
    })) });
  });
  router.get('/household/pickups/:pickupId', requireHousehold(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId);
    const pickup = await store.pickupRequest.findFirst({ where: { id: pickupId, householdId: req.identity!.collectorId } });
    if (!pickup) throw new AppError('NOT_FOUND', 'Pickup not found', 404, { code: 'PICKUP_NOT_FOUND' });
    const listing = await store.householdListing.findFirst({ where: { id: pickup.listingId, householdId: req.identity!.collectorId } });
    const settlementPayment = await store.pickupSettlementPayment.findUnique({ where: { pickupId } });
    res.json({ success: true, data: { pickup, listing, settlementPayment } });
  });
  router.get('/household/pickups/:pickupId/passport', requireHousehold(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId);
    const pickup = await store.pickupRequest.findFirst({ where: { id: pickupId, householdId: req.identity!.collectorId } });
    if (!pickup) throw new AppError('NOT_FOUND', 'Material passport not found', 404, { code: 'PASSPORT_NOT_FOUND' });
    const listing = await store.householdListing.findFirst({ where: { id: pickup.listingId, householdId: req.identity!.collectorId } });
    const [events, movements, settlementPayment] = await Promise.all([
      store.materialPassportEvent.findMany({ where: { OR: [{ entityType: 'HOUSEHOLD_LISTING', entityId: pickup.listingId }, { entityType: 'PICKUP_REQUEST', entityId: pickupId }] }, orderBy: { occurredAt: 'asc' } }),
      store.inventoryMovement.findMany({ where: { sourceType: 'PICKUP_REQUEST', sourceId: pickupId }, orderBy: { createdAt: 'asc' } }),
      store.pickupSettlementPayment.findUnique({ where: { pickupId } })
    ]);
    res.json({ success: true, data: { pickup, listing, events, inventoryMovements: movements, settlementPayment, disclaimer: 'Traceability is platform evidence for this prototype; it is not a government certificate.' } });
  });
  router.post('/household/pickups/:pickupId/reschedule', requireHousehold(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId);
    const input = parse(z.object({ scheduledSlot: z.string().datetime() }), req.body);
    const scheduledSlot = validatePickupSlot(input.scheduledSlot);
    const pickup = await store.pickupRequest.findFirst({ where: { id: pickupId, householdId: req.identity!.collectorId, status: { in: ['REQUESTED', 'ACCEPTED', 'SCHEDULED', 'REASSIGNMENT_REQUIRED'] } } });
    if (!pickup) throw new AppError('CONFLICT', 'This pickup cannot be rescheduled', 409, { code: 'PICKUP_NOT_RESCHEDULABLE' });
    const updated = await store.$transaction(async (tx: any) => {
      const current = await tx.pickupRequest.findFirst({ where: { id: pickupId, householdId: req.identity!.collectorId, status: { in: ['REQUESTED', 'ACCEPTED', 'SCHEDULED', 'REASSIGNMENT_REQUIRED'] } } });
      if (!current) throw new AppError('CONFLICT', 'This pickup was already updated', 409, { code: 'PICKUP_RESCHEDULE_CONFLICT' });
      await movePickupDay(tx, current.kabadiwalaId, scheduledSlot, current.scheduledSlot);
      const row = await tx.pickupRequest.update({ where: { id: pickupId }, data: { requestedSlot: scheduledSlot, scheduledSlot, status: current.status === 'REASSIGNMENT_REQUIRED' ? 'REQUESTED' : 'SCHEDULED', reassignmentReason: null, lateCancellation: false } });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'HOUSEHOLD', 'PICKUP_RESCHEDULED', 'PICKUP_REQUEST', pickupId, { scheduledSlot: scheduledSlot.toISOString() });
      return row;
    });
    res.json({ success: true, data: updated });
  });
  router.post('/household/pickups/:pickupId/settlement', requireHousehold(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId);
    const decision = parse(settlementDecision, req.body ?? {});
    const operationId = operationKey(req);
    const operationHash = requestHash({ action: 'HOUSEHOLD_PICKUP_SETTLEMENT', pickupId, decision });
    const pickup = await store.pickupRequest.findFirst({ where: { id: pickupId, householdId: req.identity!.collectorId, status: 'COMPLETED', settlementStatus: 'PENDING_HOUSEHOLD_CONFIRMATION' } });
    if (!pickup) throw new AppError('CONFLICT', 'This pickup settlement is no longer actionable', 409, { code: 'SETTLEMENT_ALREADY_DECIDED' });
    const result = await store.$transaction(async (tx: any) => {
      if (operationId) {
        const replay = await tx.idempotencyRecord.findUnique({ where: { actorId_operationId: { actorId: req.identity!.collectorId, operationId } } });
        if (replay) {
          if (replay.requestHash && replay.requestHash !== operationHash) throw new AppError('CONFLICT', 'Idempotency key was already used for a different settlement decision', 409, { code: 'IDEMPOTENCY_KEY_REUSED' });
          return { pickup: replay.response, replayed: true };
        }
      }
      const row = await tx.pickupRequest.updateMany({ where: { id: pickupId, householdId: req.identity!.collectorId, settlementStatus: 'PENDING_HOUSEHOLD_CONFIRMATION' }, data: { settlementStatus: decision.decision === 'ACCEPT' ? 'ACCEPTED' : 'DISPUTED', householdDecision: decision.decision, settlementReasonCode: decision.reasonCode ?? null, settlementEvidenceReference: decision.evidenceReference ?? null, settlementDisputeNotes: decision.notes ?? null, settlementDecisionAt: new Date() } });
      if (!row.count) throw new AppError('CONFLICT', 'This pickup settlement was already decided', 409, { code: 'SETTLEMENT_ALREADY_DECIDED' });
      if (decision.decision === 'RAISE_ISSUE') await tx.anomalyFlag.create({ data: { entityType: 'PICKUP_REQUEST', entityId: pickupId, ruleCode: decision.reasonCode ?? 'HOUSEHOLD_DISPUTE', severity: 'MEDIUM', details: { notes: decision.notes ?? null, evidenceReference: decision.evidenceReference ?? null } } });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'HOUSEHOLD', decision.decision === 'ACCEPT' ? 'SETTLEMENT_ACCEPTED' : 'SETTLEMENT_DISPUTED', 'PICKUP_REQUEST', pickupId, { reasonCode: decision.reasonCode ?? null, notes: decision.notes ?? null });
      const updated = await tx.pickupRequest.findUniqueOrThrow({ where: { id: pickupId } });
      if (operationId) await tx.idempotencyRecord.create({ data: { actorId: req.identity!.collectorId, operationId, action: 'HOUSEHOLD_PICKUP_SETTLEMENT', entityId: pickupId, requestHash: operationHash, response: jsonValue(updated) } });
      return { pickup: updated, replayed: false };
    });
    if (!result.replayed) await emitNotification(store, { accountId: pickup.kabadiwalaId, type: decision.decision === 'ACCEPT' ? 'PICKUP_SETTLEMENT_ACCEPTED' : 'PICKUP_SETTLEMENT_DISPUTED', title: decision.decision === 'ACCEPT' ? 'Household accepted settlement' : 'Pickup settlement needs review', body: decision.decision === 'ACCEPT' ? 'The household accepted the final pickup settlement.' : 'The household raised an issue with the final pickup settlement.', route: `kabadiwala/pickups/${pickupId}`, dedupeKey: `PICKUP_SETTLEMENT_DECISION:${pickupId}:${decision.decision}` });
    res.json({ success: true, data: result.pickup, ...(result.replayed ? { message: 'Settlement decision already processed' } : {}) });
  });
  router.get('/household/listings/:listingId/passport', requireHousehold(jwt, collectors), async (req, res) => {
    const listingId = parse(id, req.params.listingId);
    const listing = await store.householdListing.findFirst({ where: { id: listingId, householdId: req.identity!.collectorId } });
    if (!listing) throw new AppError('NOT_FOUND', 'Material passport not found', 404, { code: 'PASSPORT_NOT_FOUND' });
    const pickups = await store.pickupRequest.findMany({ where: { listingId, householdId: req.identity!.collectorId }, select: { id: true } });
    const pickupIds = pickups.map((row: { id: string }) => row.id);
    const [events, movements, bulkLots, poolContributions] = await Promise.all([
      store.materialPassportEvent.findMany({ where: { OR: [{ entityType: 'HOUSEHOLD_LISTING', entityId: listingId }, ...(pickupIds.length ? [{ entityType: 'PICKUP_REQUEST', entityId: { in: pickupIds } }] : [])] }, orderBy: { occurredAt: 'asc' } }),
      pickupIds.length ? store.inventoryMovement.findMany({ where: { sourceType: 'PICKUP_REQUEST', sourceId: { in: pickupIds } }, orderBy: { createdAt: 'asc' } }) : [],
      store.bulkLot.findMany({ where: { sourceListingIds: { has: listingId } }, select: { id: true, status: true, quantityKg: true, materialCategory: true, grade: true } }),
      store.poolContribution.findMany({ where: { sourceListingIds: { has: listingId } }, select: { id: true, poolId: true, handoverId: true, status: true, quantityKg: true, finalAcceptedKg: true } })
    ]);
    const bulkLotIds = bulkLots.map((row: any) => row.id);
    const poolIds = [...new Set(poolContributions.map((row: any) => row.poolId))];
    const handovers = await store.supplyHandover.findMany({ where: { OR: [...(bulkLotIds.length ? [{ bulkLotId: { in: bulkLotIds } }] : []), { sourceListingIds: { has: listingId } }] }, select: { id: true, bulkLotId: true, poolId: true, recyclerId: true, materialCategory: true, quotedWeightKg: true, finalAcceptedKg: true, finalValue: true, status: true, destructionEvidenceStatus: true, recyclerEvidenceReference: true, sourceListingIds: true, createdAt: true, updatedAt: true } });
    const handoverIds = handovers.map((row: any) => row.id);
    const downstreamEvents = handoverIds.length ? await store.materialPassportEvent.findMany({ where: { entityType: 'SUPPLY_HANDOVER', entityId: { in: handoverIds } }, orderBy: { occurredAt: 'asc' } }) : [];
    const [settlementBreakdowns, poolSettlements, anomalies] = await Promise.all([
      handoverIds.length ? store.settlementBreakdown.findMany({ where: { handoverId: { in: handoverIds } }, orderBy: { updatedAt: 'asc' } }) : [],
      poolContributions.length ? store.poolSettlement.findMany({ where: { contributionId: { in: poolContributions.map((row: any) => row.id) } }, orderBy: { updatedAt: 'asc' } }) : [],
      (handoverIds.length || poolContributions.length) ? store.anomalyFlag.findMany({ where: { OR: [...handoverIds.map((id: string) => ({ entityType: 'SUPPLY_HANDOVER', entityId: id })), ...poolContributions.map((row: any) => ({ entityType: 'POOL_CONTRIBUTION', entityId: row.id }))] }, orderBy: { createdAt: 'asc' } }) : []
    ]);
    const visibleHandovers = handovers.map((row: any) => ({ ...row, sourceListingIds: [listingId] }));
    res.json({ success: true, data: { listing, pickupIds, events: [...events, ...downstreamEvents].sort((left: any, right: any) => new Date(left.occurredAt).getTime() - new Date(right.occurredAt).getTime()), inventoryMovements: movements, bulkLots, poolContributions, handovers: visibleHandovers, settlementBreakdowns, poolSettlements, anomalies, disclaimer: 'Traceability is platform evidence for this prototype; it is not a government certificate.' } });
  });
  router.post('/household/listings/:listingId/cancel', requireHousehold(jwt, collectors), async (req, res) => {
    const listingId = parse(id, req.params.listingId); const input = parse(cancellationInput, req.body ?? {});
    await store.$transaction(async (tx: any) => {
      const listing = await tx.householdListing.findFirst({ where: { id: listingId, householdId: req.identity!.collectorId, status: { in: ['POSTED', 'MATCHED'] } } });
      if (!listing) throw new AppError('CONFLICT', 'Only an open listing can be cancelled', 409);
      await tx.householdListing.update({ where: { id: listingId }, data: { status: 'CANCELLED' } });
      const now = new Date();
      const activePickups = await tx.pickupRequest.findMany({ where: { listingId, householdId: req.identity!.collectorId, status: { in: activePickupStatuses } }, select: { id: true, kabadiwalaId: true, scheduledSlot: true } });
      await Promise.all(activePickups.map(async (pickup: { id: string; kabadiwalaId: string | null; scheduledSlot: Date | null }) => {
        if (pickup.kabadiwalaId) await releasePickupDay(tx, pickup.kabadiwalaId, pickup.scheduledSlot);
        return tx.pickupRequest.update({ where: { id: pickup.id }, data: { status: 'CANCELLED', cancelledBy: 'HOUSEHOLD', cancellationReason: input.reason ?? null, cancelledAt: now, lateCancellation: Boolean(pickup.scheduledSlot && pickup.scheduledSlot.getTime() - now.getTime() < 24 * 60 * 60 * 1000) } });
      }));
      await auditSupplyEvent(tx, req.identity!.collectorId, 'HOUSEHOLD', 'LISTING_CANCELLED', 'HOUSEHOLD_LISTING', listingId, { reason: input.reason ?? null, cancelledPickupCount: activePickups.length });
    });
    res.json({ success: true });
  });
  router.post('/household/pickups/:pickupId/cancel', requireHousehold(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId); const input = parse(cancellationInput, req.body ?? {});
    await store.$transaction(async (tx: any) => {
      const pickup = await tx.pickupRequest.findFirst({ where: { id: pickupId, householdId: req.identity!.collectorId, status: { in: ['WAITING_FOR_PICKUP', 'REQUESTED', 'ACCEPTED', 'SCHEDULED', 'REASSIGNMENT_REQUIRED'] } } });
      if (!pickup) throw new AppError('CONFLICT', 'This pickup can no longer be cancelled', 409);
      const updated = await tx.pickupRequest.updateMany({ where: { id: pickupId, householdId: req.identity!.collectorId, status: { in: ['WAITING_FOR_PICKUP', 'REQUESTED', 'ACCEPTED', 'SCHEDULED', 'REASSIGNMENT_REQUIRED'] } }, data: { status: 'CANCELLED', cancelledBy: 'HOUSEHOLD', cancellationReason: input.reason ?? null, cancelledAt: new Date(), lateCancellation: Boolean(pickup.scheduledSlot && pickup.scheduledSlot.getTime() - Date.now() < 24 * 60 * 60 * 1000) } });
      if (!updated.count) throw new AppError('CONFLICT', 'This pickup was already updated', 409);
      if (pickup.kabadiwalaId) await releasePickupDay(tx, pickup.kabadiwalaId, pickup.scheduledSlot);
      const remaining = await tx.pickupRequest.count({ where: { listingId: pickup.listingId, status: { in: activePickupStatuses } } });
      if (!remaining) await tx.householdListing.updateMany({ where: { id: pickup.listingId, householdId: req.identity!.collectorId, status: 'MATCHED' }, data: { status: 'POSTED' } });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'HOUSEHOLD', 'PICKUP_CANCELLED', 'PICKUP_REQUEST', pickupId, { reason: input.reason ?? null });
    });
    res.json({ success: true });
  });

  router.get('/kabadiwala/listings', requireAuth(jwt, collectors), async (req, res) => {
    const assigned = await store.pickupRequest.findMany({ where: { kabadiwalaId: req.identity!.collectorId }, select: { listingId: true } });
    const own = store.collector?.findUnique ? await store.collector.findUnique({ where: { id: req.identity!.collectorId }, select: { areaName: true, latitude: true, longitude: true } }) : null;
    const waiting = own ? await store.pickupRequest.findMany({ where: { status: 'WAITING_FOR_PICKUP', kabadiwalaId: null }, select: { listingId: true } }) : [];
    const waitingListings = waiting.length ? await store.householdListing.findMany({ where: { id: { in: waiting.map((pickup: { listingId: string }) => pickup.listingId) } }, select: { id: true, areaName: true, latitude: true, longitude: true } }) : [];
    const visibleWaitingIds = waitingListings.filter((listing: any) => {
      return collectorCanSeeWaitingPickup(own, listing);
    }).map((listing: any) => listing.id);
    const assignedIds = [...new Set([...assigned.map((pickup: { listingId: string }) => pickup.listingId), ...visibleWaitingIds])];
    const listings = assignedIds.length
      ? await store.householdListing.findMany({ where: { id: { in: assignedIds } }, select: { id: true, materialCategory: true, estimatedWeight: true, condition: true, notes: true, photoReference: true, photoReferences: true, areaName: true, latitude: true, longitude: true, estimatedPriceMin: true, estimatedPriceMax: true, status: true, createdAt: true, updatedAt: true }, orderBy: { createdAt: 'desc' }, take: 100 })
      : [];
    // Photo references are private storage keys. The collector receives only a
    // presence flag; the image itself remains behind the assigned-pickup photo
    // endpoint below.
    res.json({ success: true, data: listings.map(({ photoReference, photoReferences, ...listing }: { photoReference?: string | null; photoReferences?: string[]; [key: string]: unknown }) => ({ ...listing, photoAttached: Boolean(photoReference || photoReferences?.length), photoCount: photoReferences?.length || (photoReference ? 1 : 0) })) });
  });
  router.get('/kabadiwala/listings/:listingId/photo', requireAuth(jwt, collectors), async (req, res) => {
    const listingId = parse(id, req.params.listingId);
    const pickup = await store.pickupRequest.findFirst({ where: { listingId, kabadiwalaId: req.identity!.collectorId, status: { notIn: ['CANCELLED', 'REJECTED'] } }, select: { id: true } });
    if (!pickup) throw new AppError('NOT_FOUND', 'Listing photo is not available to this Kabadiwala', 404, { code: 'PHOTO_NOT_FOUND' });
    const listing = await store.householdListing.findUnique({ where: { id: listingId }, select: { photoReference: true, photoReferences: true } });
    await sendPrivateListingPhoto(storage, listing, 0, res);
  });
  router.get('/kabadiwala/listings/:listingId/photo/:photoIndex', requireAuth(jwt, collectors), async (req, res) => {
    const listingId = parse(id, req.params.listingId);
    const photoIndex = parse(listingPhotoIndex, req.params.photoIndex);
    const pickup = await store.pickupRequest.findFirst({ where: { listingId, kabadiwalaId: req.identity!.collectorId, status: { notIn: ['CANCELLED', 'REJECTED'] } }, select: { id: true } });
    if (!pickup) throw new AppError('NOT_FOUND', 'Listing photo is not available to this Kabadiwala', 404, { code: 'PHOTO_NOT_FOUND' });
    const listing = await store.householdListing.findUnique({ where: { id: listingId }, select: { photoReference: true, photoReferences: true } });
    await sendPrivateListingPhoto(storage, listing, photoIndex, res);
  });
  router.get('/kabadiwala/pickups', requireAuth(jwt, collectors), async (req, res) => {
    const own = store.collector?.findUnique ? await store.collector.findUnique({ where: { id: req.identity!.collectorId }, select: { areaName: true, latitude: true, longitude: true } }) : null;
    const assigned = await store.pickupRequest.findMany({ where: { kabadiwalaId: req.identity!.collectorId }, select: { id: true, listingId: true, kabadiwalaId: true, status: true, requestedSlot: true, scheduledSlot: true, actualWeight: true, finalCategory: true, grade: true, ratePerKg: true, finalAmount: true, completedAt: true, createdAt: true, updatedAt: true }, orderBy: { createdAt: 'desc' } });
    const waiting = own ? await store.pickupRequest.findMany({ where: { status: 'WAITING_FOR_PICKUP', kabadiwalaId: null }, select: { id: true, listingId: true, kabadiwalaId: true, status: true, requestedSlot: true, scheduledSlot: true, actualWeight: true, finalCategory: true, grade: true, ratePerKg: true, finalAmount: true, completedAt: true, createdAt: true, updatedAt: true }, orderBy: { createdAt: 'desc' } }) : [];
    const waitingListings = waiting.length ? await store.householdListing.findMany({ where: { id: { in: waiting.map((pickup: any) => pickup.listingId) } }, select: { id: true, areaName: true, latitude: true, longitude: true } }) : [];
    const listingById = new Map<string, any>(waitingListings.map((listing: any) => [listing.id, listing] as [string, any]));
    const visibleWaiting = waiting.filter((pickup: any) => {
      const listing = listingById.get(pickup.listingId);
      return collectorCanSeeWaitingPickup(own, listing);
    });
    const visible = [...assigned, ...visibleWaiting];
    const payments = visible.length && store.pickupSettlementPayment?.findMany
      ? await store.pickupSettlementPayment.findMany({ where: { pickupId: { in: visible.map((pickup: any) => pickup.id) } }, select: { pickupId: true, amount: true, paymentMethod: true, recordedAt: true, reference: true, status: true } })
      : [];
    const paymentByPickup = new Map(payments.map((payment: any) => [payment.pickupId, payment]));
    res.json({ success: true, data: visible.map((pickup: any) => ({ ...pickup, settlementPayment: paymentByPickup.get(pickup.id) ?? null })) });
  });
  router.post('/kabadiwala/listings/:listingId/accept', requireAuth(jwt, collectors), async (req, res) => {
    const listingId = parse(id, req.params.listingId);
    await store.$transaction(async (tx: any) => {
      let updated = await tx.pickupRequest.updateMany({ where: { listingId, kabadiwalaId: req.identity!.collectorId, status: 'REQUESTED' }, data: { status: 'ACCEPTED', acceptedAt: new Date() } });
      if (!updated.count) {
        const [collector, listing] = await Promise.all([
          tx.collector.findUnique({ where: { id: req.identity!.collectorId }, select: { areaName: true, latitude: true, longitude: true } }),
          tx.householdListing.findUnique({ where: { id: listingId }, select: { areaName: true, latitude: true, longitude: true } })
        ]);
        if (!collectorCanSeeWaitingPickup(collector, listing)) {
          throw new AppError('AUTHORIZATION_ERROR', 'This waiting pickup is outside your service area', 403, { code: 'PICKUP_OUTSIDE_SERVICE_AREA' });
        }
        updated = await tx.pickupRequest.updateMany({ where: { listingId, kabadiwalaId: null, status: 'WAITING_FOR_PICKUP' }, data: { kabadiwalaId: req.identity!.collectorId, status: 'ACCEPTED', acceptedAt: new Date() } });
      }
      if (!updated.count) throw new AppError('CONFLICT', 'Pickup is not available to accept', 409);
      await tx.householdListing.updateMany({ where: { id: listingId, status: 'POSTED' }, data: { status: 'MATCHED' } });
      const pickup = await tx.pickupRequest.findFirstOrThrow({ where: { listingId, kabadiwalaId: req.identity!.collectorId } });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'COLLECTOR', 'PICKUP_ACCEPTED', 'PICKUP_REQUEST', pickup.id, { listingId });
    });
    res.json({ success: true });
  });
  router.post('/kabadiwala/pickups/:pickupId/reject', requireAuth(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId);
    const input = parse(cancellationInput, req.body ?? {});
    await store.$transaction(async (tx: any) => {
      const updated = await tx.pickupRequest.updateMany({ where: { id: pickupId, kabadiwalaId: req.identity!.collectorId, status: 'REQUESTED' }, data: { status: 'REJECTED', cancelledBy: 'COLLECTOR', cancellationReason: input.reason ?? null, cancelledAt: new Date() } });
      if (!updated.count) throw new AppError('CONFLICT', 'Pickup is not available to reject', 409, { code: 'PICKUP_NOT_REJECTABLE' });
      const pickup = await tx.pickupRequest.findUniqueOrThrow({ where: { id: pickupId } });
      await tx.householdListing.updateMany({ where: { id: pickup.listingId, status: 'MATCHED' }, data: { status: 'POSTED' } });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'COLLECTOR', 'PICKUP_REJECTED', 'PICKUP_REQUEST', pickupId, { reason: input.reason ?? null });
    });
    res.json({ success: true });
  });
  router.post('/kabadiwala/pickups/:pickupId/confirm-availability', requireAuth(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId);
    const input = parse(z.object({ availabilityConfirmed: z.boolean().default(true), scheduledSlot: z.string().datetime().optional() }), req.body ?? {});
    const scheduledSlot = input.scheduledSlot ? validatePickupSlot(input.scheduledSlot) : null;
    const result = await store.$transaction(async (tx: any) => {
      const pickup = await tx.pickupRequest.findFirst({ where: { id: pickupId, kabadiwalaId: req.identity!.collectorId, status: { in: ['ACCEPTED', 'SCHEDULED'] } } });
      if (!pickup) throw new AppError('CONFLICT', 'Pickup is not awaiting availability confirmation', 409, { code: 'PICKUP_NOT_CONFIRMABLE' });
      if (scheduledSlot) await movePickupDay(tx, req.identity!.collectorId, scheduledSlot, pickup.scheduledSlot);
      const updated = await tx.pickupRequest.update({ where: { id: pickupId }, data: { availabilityConfirmedAt: new Date(), ...(scheduledSlot ? { scheduledSlot, requestedSlot: scheduledSlot, status: 'SCHEDULED' } : {}) } });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'COLLECTOR', 'PICKUP_AVAILABILITY_CONFIRMED', 'PICKUP_REQUEST', pickupId, { scheduledSlot: scheduledSlot?.toISOString() ?? pickup.scheduledSlot?.toISOString() ?? null });
      return updated;
    });
    res.json({ success: true, data: result });
  });
  router.post('/kabadiwala/pickups/:pickupId/schedule', requireAuth(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId); const scheduledSlot = validatePickupSlot(parse(z.object({ scheduledSlot: z.string().datetime() }), req.body).scheduledSlot);
    await store.$transaction(async (tx: any) => {
      const pickup = await tx.pickupRequest.findFirst({ where: { id: pickupId, kabadiwalaId: req.identity!.collectorId, status: { in: ['ACCEPTED', 'SCHEDULED', 'REASSIGNMENT_REQUIRED'] } } });
      if (!pickup) throw new AppError('CONFLICT', 'Pickup cannot be scheduled', 409, { code: 'PICKUP_NOT_SCHEDULABLE' });
      await movePickupDay(tx, req.identity!.collectorId, scheduledSlot, pickup.scheduledSlot);
      const updated = await tx.pickupRequest.updateMany({ where: { id: pickupId, kabadiwalaId: req.identity!.collectorId, status: { in: ['ACCEPTED', 'SCHEDULED', 'REASSIGNMENT_REQUIRED'] } }, data: { status: 'SCHEDULED', scheduledSlot, requestedSlot: scheduledSlot, reassignmentReason: null } });
      if (!updated.count) throw new AppError('CONFLICT', 'Pickup changed before scheduling', 409, { code: 'PICKUP_SCHEDULE_CONFLICT' });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'COLLECTOR', 'PICKUP_SCHEDULED', 'PICKUP_REQUEST', pickupId, { scheduledSlot: scheduledSlot.toISOString() });
    });
    res.json({ success: true });
  });
  router.post('/kabadiwala/pickups/:pickupId/cancel', requireAuth(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId);
    const input = parse(cancellationInput, req.body ?? {});
    await store.$transaction(async (tx: any) => {
      const pickup = await tx.pickupRequest.findFirst({ where: { id: pickupId, kabadiwalaId: req.identity!.collectorId, status: { in: ['ACCEPTED', 'SCHEDULED'] } } });
      if (!pickup) throw new AppError('CONFLICT', 'Pickup cannot be cancelled by this collector', 409, { code: 'PICKUP_NOT_CANCELLABLE' });
      const updated = await tx.pickupRequest.updateMany({ where: { id: pickupId, kabadiwalaId: req.identity!.collectorId, status: { in: ['ACCEPTED', 'SCHEDULED'] } }, data: { status: 'CANCELLED', cancelledBy: 'COLLECTOR', cancellationReason: input.reason ?? null, cancelledAt: new Date(), lateCancellation: Boolean(pickup.scheduledSlot && pickup.scheduledSlot.getTime() - Date.now() < 24 * 60 * 60 * 1000) } });
      if (!updated.count) throw new AppError('CONFLICT', 'Pickup was already updated', 409);
      await releasePickupDay(tx, req.identity!.collectorId, pickup.scheduledSlot);
      await tx.householdListing.updateMany({ where: { id: pickup.listingId, status: 'MATCHED' }, data: { status: 'POSTED' } });
      const recentCancellations = await tx.pickupRequest.count({ where: { kabadiwalaId: req.identity!.collectorId, status: { in: ['CANCELLED', 'REASSIGNMENT_REQUIRED'] }, updatedAt: { gte: new Date(Date.now() - 90 * 24 * 60 * 60 * 1000) } } });
      if (recentCancellations >= 3 || pickup.scheduledSlot && pickup.scheduledSlot.getTime() - Date.now() < 24 * 60 * 60 * 1000) await tx.anomalyFlag.create({ data: { entityType: 'COLLECTOR', entityId: req.identity!.collectorId, ruleCode: recentCancellations >= 3 ? 'REPEATED_PICKUP_CANCELLATION' : 'LATE_PICKUP_CANCELLATION', severity: recentCancellations >= 3 ? 'MEDIUM' : 'LOW', details: { pickupId, recentCancellations, scheduledSlot: pickup.scheduledSlot, reason: input.reason ?? null } } });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'COLLECTOR', 'PICKUP_CANCELLED', 'PICKUP_REQUEST', pickupId, { reason: input.reason ?? null });
    });
    res.json({ success: true });
  });
  router.post('/kabadiwala/pickups/:pickupId/reassign', requireAuth(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId);
    const input = parse(z.object({ reason: z.string().trim().min(2).max(500), noShow: z.boolean().default(false) }), req.body ?? {});
    const result = await store.$transaction(async (tx: any) => {
      const pickupBefore = await tx.pickupRequest.findFirst({ where: { id: pickupId, kabadiwalaId: req.identity!.collectorId, status: { in: ['ACCEPTED', 'SCHEDULED', 'IN_TRANSIT', 'ARRIVED'] } } });
      if (!pickupBefore) throw new AppError('CONFLICT', 'Pickup is not eligible for reassignment', 409, { code: 'PICKUP_NOT_REASSIGNABLE' });
      const updated = await tx.pickupRequest.updateMany({ where: { id: pickupId, kabadiwalaId: req.identity!.collectorId, status: { in: ['ACCEPTED', 'SCHEDULED', 'IN_TRANSIT', 'ARRIVED'] } }, data: { status: 'REASSIGNMENT_REQUIRED', reassignmentReason: input.reason, noShow: input.noShow, cancelledBy: 'COLLECTOR', cancellationReason: input.reason, cancelledAt: new Date() } });
      if (!updated.count) throw new AppError('CONFLICT', 'Pickup is not eligible for reassignment', 409, { code: 'PICKUP_NOT_REASSIGNABLE' });
      await releasePickupDay(tx, req.identity!.collectorId, pickupBefore.scheduledSlot);
      const pickup = await tx.pickupRequest.findUniqueOrThrow({ where: { id: pickupId } });
      await tx.householdListing.updateMany({ where: { id: pickup.listingId, status: 'MATCHED' }, data: { status: 'POSTED' } });
      const recentCancellations = await tx.pickupRequest.count({ where: { kabadiwalaId: req.identity!.collectorId, status: { in: ['CANCELLED', 'REASSIGNMENT_REQUIRED'] }, updatedAt: { gte: new Date(Date.now() - 90 * 24 * 60 * 60 * 1000) } } });
      if (recentCancellations >= 3 || input.noShow) await tx.anomalyFlag.create({ data: { entityType: 'COLLECTOR', entityId: req.identity!.collectorId, ruleCode: recentCancellations >= 3 ? 'REPEATED_PICKUP_CANCELLATION' : 'PICKUP_NO_SHOW', severity: recentCancellations >= 3 ? 'MEDIUM' : 'MEDIUM', details: { pickupId, recentCancellations, noShow: input.noShow, reason: input.reason } } });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'COLLECTOR', 'PICKUP_REASSIGNMENT_REQUIRED', 'PICKUP_REQUEST', pickupId, { reason: input.reason, noShow: input.noShow });
      return pickup;
    });
    await emitNotification(store, { accountId: result.householdId, type: 'PICKUP_REASSIGNMENT_REQUIRED', title: 'Choose another Kabadiwala', body: input.noShow ? 'The assigned Kabadiwala could not complete this pickup. Choose another available Kabadiwala.' : 'This pickup needs a new Kabadiwala. Choose another available Kabadiwala.', route: `household/pickups/${pickupId}/reassignment-options`, dedupeKey: `PICKUP_REASSIGNMENT_REQUIRED:${pickupId}` });
    res.json({ success: true, data: result, message: 'Find another Kabadiwala is now available for this request' });
  });
  router.post('/kabadiwala/pickups/:pickupId/status', requireAuth(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId); const next = parse(z.object({ status: z.enum(['IN_TRANSIT', 'ARRIVED']) }), req.body).status;
    // A household may request an immediate collection without selecting a
    // time slot. In that case ACCEPTED can move straight to IN_TRANSIT;
    // scheduled pickups still follow the same transition. The conditional
    // update keeps the mutation race-safe and idempotent.
    const allowed = next === 'IN_TRANSIT' ? ['ACCEPTED', 'SCHEDULED'] : ['IN_TRANSIT'];
    await store.$transaction(async (tx: any) => {
      const updated = await tx.pickupRequest.updateMany({ where: { id: pickupId, kabadiwalaId: req.identity!.collectorId, status: { in: allowed } }, data: { status: next, ...(next === 'IN_TRANSIT' ? { inTransitAt: new Date() } : { arrivedAt: new Date() }) } });
      if (!updated.count) throw new AppError('CONFLICT', 'Invalid pickup transition', 409);
      await auditSupplyEvent(tx, req.identity!.collectorId, 'COLLECTOR', `PICKUP_${next}`, 'PICKUP_REQUEST', pickupId, { status: next });
    });
    res.json({ success: true });
  });
  router.post('/kabadiwala/pickups/:pickupId/complete', requireAuth(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId);
    const input = parse(z.object({ actualWeight: positive.max(500), finalCategory: material, grade: z.string().trim().min(1).max(80).default('UNSPECIFIED'), ratePerKg: positive.max(1000000), reasonCode: z.string().trim().max(120).optional(), evidenceReference: z.string().trim().max(500).optional() }), req.body);
    const operationId = operationKey(req);
    const operationHash = requestHash({ action: 'COMPLETE_PICKUP', pickupId, input });
    const result = await store.$transaction(async (tx: any) => {
      if (operationId) {
        const replay = await tx.idempotencyRecord.findUnique({ where: { actorId_operationId: { actorId: req.identity!.collectorId, operationId } } });
        if (replay) {
          if (replay.requestHash && replay.requestHash !== operationHash) throw new AppError('CONFLICT', 'Idempotency key was already used for a different pickup completion', 409, { code: 'IDEMPOTENCY_KEY_REUSED' });
          return { pickup: replay.response, replayed: true };
        }
      }
      const pickupBefore = await tx.pickupRequest.findFirst({ where: { id: pickupId, kabadiwalaId: req.identity!.collectorId, status: 'ARRIVED' } });
      if (!pickupBefore) throw new AppError('CONFLICT', 'Pickup cannot be weighed', 409, { code: 'PICKUP_NOT_WEIGHABLE' });
      const listing = await tx.householdListing.findUnique({ where: { id: pickupBefore.listingId } });
      const finalAmount = Number((input.actualWeight * input.ratePerKg).toFixed(2));
      const estimatedReference = listing?.estimatedPriceMax ?? listing?.estimatedPriceMin ?? null;
      const materialChanged = Boolean(listing && listing.materialCategory !== input.finalCategory);
      const weightChanged = Boolean(listing && Math.abs(input.actualWeight - listing.estimatedWeight) / listing.estimatedWeight > 0.2);
      const valueChanged = Boolean(estimatedReference != null && Math.abs(finalAmount - estimatedReference) / Math.max(estimatedReference, 1) > 0.2);
      const materialChangeNeedsReason = materialChanged || weightChanged || valueChanged;
      if (materialChangeNeedsReason && !input.reasonCode) throw new AppError('VALIDATION_ERROR', 'A reason code is required for a material settlement change', 422, { code: 'SETTLEMENT_REASON_REQUIRED' });
      const claimed = await tx.pickupRequest.updateMany({ where: { id: pickupId, kabadiwalaId: req.identity!.collectorId, status: 'ARRIVED' }, data: { status: 'WEIGHED', actualWeight: input.actualWeight, finalCategory: input.finalCategory, grade: input.grade, ratePerKg: input.ratePerKg, finalAmount, weighedAt: new Date(), settlementStatus: 'PENDING_HOUSEHOLD_CONFIRMATION', settlementBeforeValue: estimatedReference, settlementAfterValue: finalAmount, settlementReasonCode: input.reasonCode ?? null, settlementEvidenceReference: input.evidenceReference ?? null } });
      if (!claimed.count) throw new AppError('CONFLICT', 'Pickup cannot be weighed', 409);
      const before = await tx.inventoryBalance.findUnique({ where: { kabadiwalaId_materialCategory_grade: { kabadiwalaId: req.identity!.collectorId, materialCategory: input.finalCategory, grade: input.grade } } });
      const balance = await tx.inventoryBalance.upsert({ where: { kabadiwalaId_materialCategory_grade: { kabadiwalaId: req.identity!.collectorId, materialCategory: input.finalCategory, grade: input.grade } }, update: { availableKg: { increment: input.actualWeight }, purchaseCost: { increment: finalAmount } }, create: { kabadiwalaId: req.identity!.collectorId, materialCategory: input.finalCategory, grade: input.grade, availableKg: input.actualWeight, purchaseCost: finalAmount } });
      const beforeBalance = before ?? { ...balance, availableKg: 0, reservedKg: 0, soldKg: 0 };
      assertInventoryInvariant(beforeBalance);
      assertInventoryInvariant(balance);
      await recordInventoryMovement(tx, beforeBalance, balance, 'ACQUISITION', input.actualWeight, 'PICKUP_REQUEST', pickupId, { listingId: pickupBefore.listingId, ratePerKg: input.ratePerKg });
      const pickup = await tx.pickupRequest.findUniqueOrThrow({ where: { id: pickupId } });
      await tx.pickupRequest.update({ where: { id: pickupId }, data: { status: 'COMPLETED', completedAt: new Date() } });
      await tx.householdListing.updateMany({ where: { id: pickup.listingId, status: { in: ['MATCHED', 'POSTED'] } }, data: { status: 'COMPLETED' } });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'COLLECTOR', 'PICKUP_COMPLETED', 'PICKUP_REQUEST', pickupId, { listingId: pickup.listingId, actualWeight: input.actualWeight, finalCategory: input.finalCategory, grade: input.grade, ratePerKg: input.ratePerKg });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'COLLECTOR', 'COLLECTED', 'HOUSEHOLD_LISTING', pickup.listingId, { pickupId, actualWeight: input.actualWeight });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'COLLECTOR', 'INVENTORY_ADDED', 'HOUSEHOLD_LISTING', pickup.listingId, { pickupId, materialCategory: input.finalCategory, quantityKg: input.actualWeight });
      const completed = await tx.pickupRequest.findUniqueOrThrow({ where: { id: pickupId } });
      if (operationId) await tx.idempotencyRecord.create({ data: { actorId: req.identity!.collectorId, operationId, action: 'COMPLETE_PICKUP', entityId: pickupId, requestHash: operationHash, response: jsonValue(completed) } });
      return { pickup: completed, replayed: false };
    });
    res.status(result.replayed ? 200 : 201).json({ success: true, data: result.pickup, ...(result.replayed ? { message: 'Pickup completion already processed' } : {}) });
  });
  router.post('/kabadiwala/pickups/:pickupId/settlement-payment', requireAuth(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId);
    const input = parse(z.object({ amount: positive.max(100000000), method: z.enum(['CASH', 'BANK_TRANSFER', 'DIGITAL_WALLET', 'UPI']), recordedAt: z.string().datetime().optional(), reference: z.string().trim().max(200).optional(), notes: z.string().trim().max(1000).optional() }).strict(), req.body);
    const pickup = await store.pickupRequest.findFirst({ where: { id: pickupId, kabadiwalaId: req.identity!.collectorId, status: 'COMPLETED', settlementStatus: { in: ['ACCEPTED', 'COMPLETED', 'DISPUTED'] } } });
    if (!pickup) throw new AppError('CONFLICT', 'Household must accept the pickup settlement before payment', 409, { code: 'PICKUP_SETTLEMENT_NOT_ACCEPTED' });
    const expectedAmount = Number((pickup.finalAmount ?? 0).toFixed(2));
    if (expectedAmount <= 0 || input.amount > expectedAmount + 0.01) throw new AppError('VALIDATION_ERROR', 'Payment cannot exceed the accepted pickup settlement', 422, { code: 'PICKUP_PAYMENT_EXCEEDS_SETTLEMENT' });
    const recordedAt = input.recordedAt ? new Date(input.recordedAt) : new Date();
    if (recordedAt > new Date()) throw new AppError('VALIDATION_ERROR', 'Payment date cannot be in the future', 422, { code: 'PAYMENT_DATE_INVALID' });
    const sourceKey = `PICKUP_SETTLEMENT:${pickupId}`;
    const operationId = operationKey(req);
    const hash = requestHash({ pickupId, input });
    const result = await store.$transaction(async (tx: any) => {
      if (operationId) {
        const replay = await tx.idempotencyRecord.findUnique({ where: { actorId_operationId: { actorId: req.identity!.collectorId, operationId } } });
        if (replay) {
          if (replay.requestHash && replay.requestHash !== hash) throw new AppError('CONFLICT', 'Idempotency key was already used for a different pickup payment', 409, { code: 'IDEMPOTENCY_KEY_REUSED' });
          return { payment: replay.response, replayed: true };
        }
      }
      const prior = await tx.pickupSettlementPayment.findUnique({ where: { sourceKey } });
      if (prior) {
        if (prior.requestHash && prior.requestHash !== hash) throw new AppError('CONFLICT', 'A different pickup payment is already recorded', 409, { code: 'PICKUP_PAYMENT_PAYLOAD_MISMATCH' });
        if (operationId) await tx.idempotencyRecord.create({ data: { actorId: req.identity!.collectorId, operationId, action: 'RECORD_PICKUP_SETTLEMENT_PAYMENT', entityId: prior.id, requestHash: hash, response: jsonValue(prior) } });
        return { payment: prior, replayed: true };
      }
      const anomaly = Math.abs(input.amount - expectedAmount) > 0.01;
      const payment = await tx.pickupSettlementPayment.create({ data: { sourceKey, requestHash: hash, pickupId, householdId: pickup.householdId, collectorId: req.identity!.collectorId, amount: Number(input.amount.toFixed(2)), paymentMethod: input.method, recordedAt, reference: input.reference ?? null, notes: input.notes ?? null, anomaly, anomalyReason: anomaly ? 'Payment differs from the household-accepted pickup settlement' : null, status: anomaly ? 'DISPUTED' : 'RECORDED' } });
      if (anomaly) await tx.anomalyFlag.create({ data: { entityType: 'PICKUP_REQUEST', entityId: pickupId, ruleCode: 'PICKUP_PAYMENT_AMOUNT_DIFFERENCE', severity: 'MEDIUM', details: { expectedAmount, amount: payment.amount, paymentId: payment.id } } });
      const updated = await tx.pickupRequest.updateMany({ where: { id: pickupId, kabadiwalaId: req.identity!.collectorId, status: 'COMPLETED', settlementStatus: 'ACCEPTED' }, data: { settlementStatus: anomaly ? 'DISPUTED' : 'ACCEPTED' } });
      if (!updated.count && !anomaly) throw new AppError('CONFLICT', 'Pickup settlement changed before payment was recorded', 409, { code: 'PICKUP_SETTLEMENT_CONFLICT' });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'COLLECTOR', anomaly ? 'PICKUP_PAYMENT_DISPUTED' : 'PICKUP_PAYMENT_RECORDED', 'PICKUP_REQUEST', pickupId, { paymentId: payment.id, expectedAmount, amount: payment.amount, method: payment.paymentMethod });
      if (operationId) await tx.idempotencyRecord.create({ data: { actorId: req.identity!.collectorId, operationId, action: 'RECORD_PICKUP_SETTLEMENT_PAYMENT', entityId: payment.id, requestHash: hash, response: jsonValue(payment) } });
      return { payment, replayed: false };
    });
    if (!result.replayed) await emitNotification(store, { accountId: pickup.householdId, type: result.payment.anomaly ? 'PICKUP_PAYMENT_DISPUTED' : 'PICKUP_PAYMENT_RECORDED', title: result.payment.anomaly ? 'Pickup payment needs review' : 'Pickup payment recorded', body: result.payment.anomaly ? 'The recorded pickup payment differs from the accepted settlement.' : 'The Kabadiwala recorded this payment. An operator will reconcile it.', route: `household/pickups/${pickupId}`, dedupeKey: `PICKUP_SETTLEMENT_PAYMENT:${result.payment.id}` });
    res.status(result.replayed ? 200 : 201).json({ success: true, data: result.payment, ...(result.replayed ? { message: 'Pickup payment already recorded' } : {}) });
  });
  router.get('/admin/household-pickup-payments', requireAdmin(jwt, db, 'PAYMENT_VERIFICATION'), async (req, res) => {
    const status = parse(z.enum(['RECORDED', 'DISPUTED', 'VERIFIED', 'REVERSED']).default('RECORDED'), req.query.status);
    const payments = await store.pickupSettlementPayment.findMany({ where: { status }, orderBy: { recordedAt: 'asc' }, take: 100, select: { id: true, pickupId: true, householdId: true, collectorId: true, amount: true, paymentMethod: true, recordedAt: true, reference: true, status: true, anomaly: true, anomalyReason: true } });
    res.json({ success: true, data: payments.map((payment: any) => ({ ...payment, kind: 'HOUSEHOLD_PICKUP_SETTLEMENT' })) });
  });
  router.post('/admin/household-pickup-payments/:paymentId/reconcile', requireAdmin(jwt, db, 'PAYMENT_VERIFICATION'), async (req, res) => {
    const paymentId = parse(id, req.params.paymentId);
    const input = parse(z.object({ decision: z.enum(['VERIFY', 'DISPUTE']), notes: z.string().trim().max(1000).optional() }).strict(), req.body);
    if (input.decision === 'DISPUTE' && !input.notes) throw new AppError('VALIDATION_ERROR', 'Add a note explaining the payment mismatch', 422, { code: 'PAYMENT_RECONCILIATION_NOTE_REQUIRED' });
    const result = await store.$transaction(async (tx: any) => {
      const payment = await tx.pickupSettlementPayment.findUnique({ where: { id: paymentId } });
      if (!payment) throw new AppError('NOT_FOUND', 'Pickup payment not found', 404, { code: 'PICKUP_PAYMENT_NOT_FOUND' });
      const nextStatus = input.decision === 'VERIFY' ? 'VERIFIED' : 'DISPUTED';
      const updated = await tx.pickupSettlementPayment.updateMany({ where: { id: paymentId, status: 'RECORDED' }, data: { status: nextStatus, confirmedAt: new Date(), anomaly: input.decision === 'DISPUTE' ? true : payment.anomaly, anomalyReason: input.notes ?? payment.anomalyReason } });
      if (!updated.count) throw new AppError('CONFLICT', 'Pickup payment is no longer awaiting reconciliation', 409, { code: 'PICKUP_PAYMENT_ALREADY_RECONCILED' });
      if (input.decision === 'VERIFY') {
        await tx.pickupRequest.updateMany({ where: { id: payment.pickupId, householdId: payment.householdId, kabadiwalaId: payment.collectorId, status: 'COMPLETED', settlementStatus: 'ACCEPTED' }, data: { settlementStatus: 'COMPLETED' } });
      } else {
        await tx.pickupRequest.updateMany({ where: { id: payment.pickupId, householdId: payment.householdId, kabadiwalaId: payment.collectorId, status: 'COMPLETED' }, data: { settlementStatus: 'DISPUTED', settlementDisputeNotes: input.notes } });
        await tx.anomalyFlag.create({ data: { entityType: 'PICKUP_REQUEST', entityId: payment.pickupId, ruleCode: 'PICKUP_PAYMENT_OPERATOR_DISPUTE', severity: 'MEDIUM', details: { paymentId, notes: input.notes } } });
      }
      await auditSupplyEvent(tx, req.identity!.collectorId, 'ADMIN', input.decision === 'VERIFY' ? 'PICKUP_PAYMENT_RECONCILED' : 'PICKUP_PAYMENT_DISPUTED_BY_OPERATOR', 'PICKUP_REQUEST', payment.pickupId, { paymentId, decision: input.decision, notes: input.notes ?? null });
      return tx.pickupSettlementPayment.findUniqueOrThrow({ where: { id: paymentId } });
    });
    const recipients = [...new Set([result.householdId, result.collectorId])];
    await Promise.all(recipients.map(accountId => emitNotification(store, { accountId, type: input.decision === 'VERIFY' ? 'PICKUP_PAYMENT_RECONCILED' : 'PICKUP_PAYMENT_DISPUTED', title: input.decision === 'VERIFY' ? 'Pickup payment reconciled' : 'Pickup payment needs review', body: input.decision === 'VERIFY' ? 'An operator reconciled the pickup payment record.' : 'An operator flagged the pickup payment for follow-up.', route: `household/pickups/${result.pickupId}`, dedupeKey: `PICKUP_PAYMENT_RECONCILED:${result.id}:${input.decision}:${accountId}` })));
    res.json({ success: true, data: { ...result, kind: 'HOUSEHOLD_PICKUP_SETTLEMENT' } });
  });
  router.get('/kabadiwala/inventory', requireAuth(jwt, collectors), async (req, res) => {
    const balances = await store.inventoryBalance.findMany({ where: { kabadiwalaId: req.identity!.collectorId }, orderBy: { updatedAt: 'desc' } });
    res.json({ success: true, data: balances.map((balance: any) => ({ ...balance, ownedKg: ownedKg(balance), invariant: { availableNonNegative: balance.availableKg >= 0, reservedNonNegative: balance.reservedKg >= 0, reservedWithinOwned: balance.reservedKg <= ownedKg(balance) } })) });
  });
  router.get('/kabadiwala/inventory/movements', requireAuth(jwt, collectors), async (req, res) => {
    const limit = Math.min(100, Math.max(1, Number(req.query.limit ?? 50) || 50));
    const movements = await store.inventoryMovement.findMany({ where: { kabadiwalaId: req.identity!.collectorId }, orderBy: { createdAt: 'desc' }, take: limit });
    res.json({ success: true, data: movements });
  });
  router.post('/kabadiwala/bulk-lots', requireAuth(jwt, collectors), async (req, res) => {
    const input = parse(bulkInput, req.body);
    const operationId = operationKey(req);
    const operationHash = requestHash({ action: 'CREATE_BULK_LOT', input });
    const result = await store.$transaction(async (tx: any) => {
      if (operationId) {
        const replay = await tx.idempotencyRecord.findUnique({ where: { actorId_operationId: { actorId: req.identity!.collectorId, operationId } } });
        if (replay) {
          if (replay.requestHash && replay.requestHash !== operationHash) throw new AppError('CONFLICT', 'Idempotency key was already used for a different bulk lot', 409, { code: 'IDEMPOTENCY_KEY_REUSED' });
          return { lot: replay.response, replayed: true };
        }
      }
      await validateSourceListings(tx, req.identity!.collectorId, input.sourceListingIds, input.materialCategory);
      const before = await tx.inventoryBalance.findUnique({ where: { kabadiwalaId_materialCategory_grade: { kabadiwalaId: req.identity!.collectorId, materialCategory: input.materialCategory, grade: input.grade } } });
      const reserved = await tx.inventoryBalance.updateMany({ where: { kabadiwalaId: req.identity!.collectorId, materialCategory: input.materialCategory, grade: input.grade, availableKg: { gte: input.quantityKg } }, data: { availableKg: { decrement: input.quantityKg }, reservedKg: { increment: input.quantityKg } } });
      if (!reserved.count) throw new AppError('CONFLICT', 'Insufficient available inventory for this bulk lot', 409);
      const after = await tx.inventoryBalance.findUniqueOrThrow({ where: { kabadiwalaId_materialCategory_grade: { kabadiwalaId: req.identity!.collectorId, materialCategory: input.materialCategory, grade: input.grade } } });
      assertInventoryInvariant(after);
      const created = await tx.bulkLot.create({ data: { ...input, kabadiwalaId: req.identity!.collectorId, status: 'LISTED' } });
      await recordInventoryMovement(tx, before ?? { ...after, availableKg: after.availableKg + input.quantityKg, reservedKg: after.reservedKg - input.quantityKg }, after, 'RESERVATION', input.quantityKg, 'BULK_LOT', created.id, { bulkLotId: created.id });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'COLLECTOR', 'BULK_LOT_LISTED', 'BULK_LOT', created.id, { materialCategory: created.materialCategory, grade: created.grade, quantityKg: created.quantityKg, askingRatePerKg: created.askingRatePerKg });
      if (operationId) await tx.idempotencyRecord.create({ data: { actorId: req.identity!.collectorId, operationId, action: 'CREATE_BULK_LOT', entityId: created.id, requestHash: operationHash, response: jsonValue(created) } });
      return { lot: created, replayed: false };
    });
    res.status(result.replayed ? 200 : 201).json({ success: true, data: result.lot, ...(result.replayed ? { message: 'Bulk lot already created' } : {}) });
  });
  router.get('/kabadiwala/bulk-lots', requireAuth(jwt, collectors), async (req, res) => {
    res.json({ success: true, data: await store.bulkLot.findMany({ where: { kabadiwalaId: req.identity!.collectorId }, orderBy: { createdAt: 'desc' } }) });
  });
  router.post('/kabadiwala/bulk-lots/:lotId/cancel', requireAuth(jwt, collectors), async (req, res) => {
    const lotId = parse(id, req.params.lotId);
    await store.$transaction(async (tx: any) => {
      const lot = await tx.bulkLot.findFirst({ where: { id: lotId, kabadiwalaId: req.identity!.collectorId, status: 'LISTED' } });
      if (!lot) throw new AppError('CONFLICT', 'Only an unreserved listed lot can be cancelled', 409);
      const claimed = await tx.bulkLot.updateMany({ where: { id: lotId, kabadiwalaId: req.identity!.collectorId, status: 'LISTED' }, data: { status: 'CANCELLED' } });
      if (!claimed.count) throw new AppError('CONFLICT', 'Bulk lot was already updated', 409, { code: 'BULK_LOT_ALREADY_UPDATED' });
      await tx.bulkOffer.updateMany({ where: { bulkLotId: lot.id, status: 'PENDING' }, data: { status: 'CANCELLED' } });
      const before = await tx.inventoryBalance.findUniqueOrThrow({ where: { kabadiwalaId_materialCategory_grade: { kabadiwalaId: lot.kabadiwalaId, materialCategory: lot.materialCategory, grade: lot.grade } } });
      const updated = await tx.inventoryBalance.update({ where: { id: before.id }, data: { availableKg: { increment: lot.quantityKg }, reservedKg: { decrement: lot.quantityKg } } });
      assertInventoryInvariant(updated);
      await recordInventoryMovement(tx, before, updated, 'RELEASE', lot.quantityKg, 'BULK_LOT', lot.id, { reason: 'COLLECTOR_CANCELLED' });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'COLLECTOR', 'BULK_LOT_CANCELLED', 'BULK_LOT', lot.id, { quantityKg: lot.quantityKg, reason: 'COLLECTOR_CANCELLED' });
    });
    res.json({ success: true });
  });
  router.get('/kabadiwala/bulk-offers', requireAuth(jwt, collectors), async (req, res) => {
    const lots = await store.bulkLot.findMany({ where: { kabadiwalaId: req.identity!.collectorId }, select: { id: true } });
    const offers = await store.bulkOffer.findMany({ where: { bulkLotId: { in: lots.map((lot: { id: string }) => lot.id) } }, orderBy: { createdAt: 'desc' } });
    res.json({ success: true, data: offers });
  });

  router.get('/recycler/bulk-lots', requireRecycler(jwt, db), async (_req, res) => {
    res.json({ success: true, data: await store.bulkLot.findMany({ where: { status: 'LISTED' }, select: { id: true, materialCategory: true, grade: true, quantityKg: true, askingRatePerKg: true, minimumRatePerKg: true, areaName: true, notes: true, status: true, createdAt: true, updatedAt: true }, orderBy: { createdAt: 'desc' } }) });
  });
  router.get('/recycler/bulk-lots/:lotId', requireRecycler(jwt, db), async (req, res) => {
    const lotId = parse(id, req.params.lotId);
    const lot = await store.bulkLot.findFirst({ where: { id: lotId, status: { in: ['LISTED', 'RESERVED'] } }, select: { id: true, materialCategory: true, grade: true, quantityKg: true, askingRatePerKg: true, minimumRatePerKg: true, areaName: true, latitude: true, longitude: true, notes: true, status: true, createdAt: true, updatedAt: true } });
    if (!lot) throw new AppError('NOT_FOUND', 'Bulk lot not found', 404, { code: 'BULK_LOT_NOT_FOUND' });
    res.json({ success: true, data: lot });
  });
  router.post('/recycler/bulk-lots/:lotId/offers', requireRecycler(jwt, db), async (req, res) => {
    const lotId = parse(id, req.params.lotId); const offeredRatePerKg = parse(z.object({ offeredRatePerKg: positive.max(1000000) }), req.body).offeredRatePerKg;
    const lot = await store.bulkLot.findFirst({ where: { id: lotId, status: 'LISTED' } });
    if (!lot) throw new AppError('NOT_FOUND', 'Available bulk lot not found', 404);
    const recycler = await store.recycler.findFirst({ where: { id: req.identity!.collectorId, authorizationStatus: 'VERIFIED', OR: [{ authorizationValidUntil: null }, { authorizationValidUntil: { gt: new Date() } }], materials: { some: { category: lot.materialCategory } } }, include: { materials: true } });
    const capability = recycler?.materials.find((row: any) => row.category === lot.materialCategory);
    if (!recycler || !capability || (capability.minAcceptableWeight != null && lot.quantityKg < capability.minAcceptableWeight) || (capability.maxAcceptableWeight != null && lot.quantityKg > capability.maxAcceptableWeight) || (capability.acceptedGrades?.length && !capability.acceptedGrades.includes('UNSPECIFIED') && !capability.acceptedGrades.includes(lot.grade))) throw new AppError('CONFLICT', 'This Recycler is not currently eligible for the lot grade or quantity', 409, { code: 'RECYCLER_LOT_INELIGIBLE' });
    const offer = await store.$transaction(async (tx: any) => {
      const current = await tx.bulkLot.findFirst({ where: { id: lotId, status: 'LISTED' } });
      if (!current) throw new AppError('CONFLICT', 'Bulk lot is no longer available for offers', 409, { code: 'BULK_LOT_NOT_AVAILABLE' });
      const created = await tx.bulkOffer.upsert({ where: { bulkLotId_recyclerId: { bulkLotId: lotId, recyclerId: req.identity!.collectorId } }, update: { offeredRatePerKg, status: 'PENDING' }, create: { bulkLotId: lotId, recyclerId: req.identity!.collectorId, offeredRatePerKg } });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'RECYCLER', 'BULK_OFFER_SUBMITTED', 'BULK_OFFER', created.id, { bulkLotId: lotId, offeredRatePerKg });
      return created;
    });
    res.status(201).json({ success: true, data: offer });
  });
  router.get('/recycler/offers', requireRecycler(jwt, db), async (req, res) => {
    res.json({ success: true, data: await store.bulkOffer.findMany({ where: { recyclerId: req.identity!.collectorId }, orderBy: { createdAt: 'desc' } }) });
  });
  router.post('/recycler/offers/:offerId/withdraw', requireRecycler(jwt, db), async (req, res) => {
    const offerId = parse(id, req.params.offerId);
    const updated = await store.bulkOffer.updateMany({ where: { id: offerId, recyclerId: req.identity!.collectorId, status: 'PENDING' }, data: { status: 'CANCELLED' } });
    if (!updated.count) throw new AppError('CONFLICT', 'Offer is not withdrawable', 409, { code: 'OFFER_NOT_WITHDRAWABLE' });
    res.json({ success: true, data: await store.bulkOffer.findUnique({ where: { id: offerId } }) });
  });
  router.post('/kabadiwala/bulk-offers/:offerId/reject', requireAuth(jwt, collectors), async (req, res) => {
    const offerId = parse(id, req.params.offerId);
    const offer = await store.bulkOffer.findFirst({ where: { id: offerId, status: 'PENDING' } });
    const lot = offer ? await store.bulkLot.findFirst({ where: { id: offer.bulkLotId, kabadiwalaId: req.identity!.collectorId, status: 'LISTED' } }) : null;
    if (!offer || !lot) throw new AppError('CONFLICT', 'Offer is not rejectable', 409, { code: 'OFFER_NOT_REJECTABLE' });
    const updated = await store.bulkOffer.updateMany({ where: { id: offerId, status: 'PENDING' }, data: { status: 'REJECTED' } });
    if (!updated.count) throw new AppError('CONFLICT', 'Offer was already updated', 409);
    res.json({ success: true, data: await store.bulkOffer.findUnique({ where: { id: offerId } }) });
  });
  router.post('/kabadiwala/bulk-offers/:offerId/counter', requireAuth(jwt, collectors), async (req, res) => {
    const offerId = parse(id, req.params.offerId);
    const offeredRatePerKg = parse(z.object({ offeredRatePerKg: positive.max(1000000) }), req.body).offeredRatePerKg;
    const offer = await store.bulkOffer.findFirst({ where: { id: offerId, status: 'PENDING' } });
    const lot = offer ? await store.bulkLot.findFirst({ where: { id: offer.bulkLotId, kabadiwalaId: req.identity!.collectorId, status: 'LISTED' } }) : null;
    if (!offer || !lot) throw new AppError('CONFLICT', 'Offer is not counterable', 409, { code: 'OFFER_NOT_COUNTERABLE' });
    const updatedCount = await store.bulkOffer.updateMany({ where: { id: offerId, status: 'PENDING' }, data: { offeredRatePerKg } });
    if (!updatedCount.count) throw new AppError('CONFLICT', 'Offer was already updated', 409, { code: 'OFFER_ALREADY_UPDATED' });
    const updated = await store.bulkOffer.findUniqueOrThrow({ where: { id: offerId } });
    res.json({ success: true, data: updated });
  });
  router.post('/kabadiwala/bulk-offers/:offerId/accept', requireAuth(jwt, collectors), async (req, res) => {
    const offerId = parse(id, req.params.offerId);
    await store.$transaction(async (tx: any) => {
      const offer = await tx.bulkOffer.findUnique({ where: { id: offerId } });
      if (!offer || offer.status !== 'PENDING') throw new AppError('CONFLICT', 'Offer is not actionable', 409);
      const lot = await tx.bulkLot.findFirst({ where: { id: offer.bulkLotId, kabadiwalaId: req.identity!.collectorId, status: 'LISTED' } });
      if (!lot) throw new AppError('NOT_FOUND', 'Listed bulk lot not found', 404);
      const recycler = await tx.recycler.findUnique({ where: { id: offer.recyclerId }, select: { authorizationStatus: true, authorizationValidUntil: true } });
      if (!recycler || recycler.authorizationStatus !== 'VERIFIED' || (recycler.authorizationValidUntil && recycler.authorizationValidUntil <= new Date())) throw new AppError('CONFLICT', 'Recycler authorization is not current', 409, { code: 'RECYCLER_NOT_VERIFIED' });
      const capability = await tx.recyclerMaterial.findFirst({ where: { recyclerId: offer.recyclerId, category: lot.materialCategory } });
      if (!capability || (capability.minAcceptableWeight != null && lot.quantityKg < capability.minAcceptableWeight) || (capability.maxAcceptableWeight != null && lot.quantityKg > capability.maxAcceptableWeight) || (capability.acceptedGrades?.length && !capability.acceptedGrades.includes('UNSPECIFIED') && !capability.acceptedGrades.includes(lot.grade))) throw new AppError('CONFLICT', 'Recycler is no longer eligible for this lot grade or quantity', 409, { code: 'RECYCLER_LOT_INELIGIBLE' });
      if (lot.minimumRatePerKg && offer.offeredRatePerKg < lot.minimumRatePerKg) throw new AppError('CONFLICT', 'Offer is below the lot minimum', 409);
      const claimedLot = await tx.bulkLot.updateMany({ where: { id: lot.id, kabadiwalaId: req.identity!.collectorId, status: 'LISTED' }, data: { status: 'RESERVED', reservedForId: offer.recyclerId } });
      if (!claimedLot.count) throw new AppError('CONFLICT', 'Bulk lot was already reserved by another offer', 409, { code: 'BULK_LOT_ALREADY_RESERVED' });
      const claimedOffer = await tx.bulkOffer.updateMany({ where: { id: offerId, status: 'PENDING' }, data: { status: 'ACCEPTED' } });
      if (!claimedOffer.count) throw new AppError('CONFLICT', 'Offer was already updated', 409, { code: 'OFFER_ALREADY_UPDATED' });
      await tx.bulkOffer.updateMany({ where: { bulkLotId: lot.id, id: { not: offerId }, status: 'PENDING' }, data: { status: 'REJECTED' } });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'COLLECTOR', 'BULK_OFFER_ACCEPTED', 'BULK_OFFER', offer.id, { bulkLotId: lot.id, recyclerId: offer.recyclerId, offeredRatePerKg: offer.offeredRatePerKg });
    });
    res.json({ success: true });
  });
  router.post('/recycler/bulk-lots/:lotId/receive', requireRecycler(jwt, db), async (req, res) => {
    parse(id, req.params.lotId);
    throw new AppError('CONFLICT', 'Formal QR handover is required before a bulk lot can be received', 409, { code: 'FORMAL_HANDOVER_REQUIRED' });
  });
  router.post('/recycler/procurement-requirements', requireRecycler(jwt, db), async (req, res) => {
    const input = parse(z.object({ materialCategory: material, minimumLotKg: positive.max(100000), requiredQuantityKg: positive.max(1000000), preferredGrade: z.string().trim().max(80).optional(), maxRatePerKg: positive.max(1000000).optional(), procurementRadiusKm: positive.max(1000), deadline: z.string().datetime().optional() }), req.body);
    if (input.minimumLotKg > input.requiredQuantityKg) throw new AppError('VALIDATION_ERROR', 'Minimum lot cannot exceed required quantity', 422, { code: 'INVALID_DEMAND_QUANTITY' });
    const operationId = operationKey(req);
    const operationHash = requestHash({ action: 'CREATE_PROCUREMENT_REQUIREMENT', input });
    const result = await store.$transaction(async (tx: any) => {
      if (operationId) {
        const replay = await tx.idempotencyRecord.findUnique({ where: { actorId_operationId: { actorId: req.identity!.collectorId, operationId } } });
        if (replay) {
          if (replay.requestHash && replay.requestHash !== operationHash) throw new AppError('CONFLICT', 'Idempotency key was already used for a different demand', 409, { code: 'IDEMPOTENCY_KEY_REUSED' });
          return { requirement: replay.response, replayed: true };
        }
      }
      const requirement = await tx.procurementRequirement.create({ data: { ...input, deadline: input.deadline ? new Date(input.deadline) : undefined, recyclerId: req.identity!.collectorId } });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'RECYCLER', 'PROCUREMENT_REQUIREMENT_CREATED', 'PROCUREMENT_REQUIREMENT', requirement.id, { materialCategory: requirement.materialCategory, minimumLotKg: requirement.minimumLotKg, requiredQuantityKg: requirement.requiredQuantityKg });
      if (operationId) await tx.idempotencyRecord.create({ data: { actorId: req.identity!.collectorId, operationId, action: 'CREATE_PROCUREMENT_REQUIREMENT', entityId: requirement.id, requestHash: operationHash, response: jsonValue(requirement) } });
      return { requirement, replayed: false };
    });
    res.status(result.replayed ? 200 : 201).json({ success: true, data: result.requirement, ...(result.replayed ? { message: 'Demand already created' } : {}) });
  });
  router.patch('/recycler/procurement-requirements/:requirementId', requireRecycler(jwt, db), async (req, res) => {
    const requirementId = parse(id, req.params.requirementId);
    const input = parse(z.object({ minimumLotKg: positive.max(100000).optional(), requiredQuantityKg: positive.max(1000000).optional(), preferredGrade: z.string().trim().max(80).nullable().optional(), maxRatePerKg: positive.max(1000000).nullable().optional(), procurementRadiusKm: positive.max(1000).optional(), deadline: z.string().datetime().nullable().optional(), status: z.enum(['OPEN', 'PAUSED', 'FULFILLED', 'CANCELLED']).optional() }).strict(), req.body);
    const current = await store.procurementRequirement.findFirst({ where: { id: requirementId, recyclerId: req.identity!.collectorId } });
    if (!current) throw new AppError('NOT_FOUND', 'Procurement requirement not found', 404, { code: 'DEMAND_NOT_FOUND' });
    if (['FULFILLED', 'CANCELLED'].includes(current.status) && input.status !== current.status) throw new AppError('CONFLICT', 'A fulfilled or cancelled demand cannot be reopened', 409, { code: 'DEMAND_TERMINAL' });
    const minimumLotKg = input.minimumLotKg ?? current.minimumLotKg;
    const requiredQuantityKg = input.requiredQuantityKg ?? current.requiredQuantityKg;
    if (minimumLotKg > requiredQuantityKg) throw new AppError('VALIDATION_ERROR', 'Minimum lot cannot exceed required quantity', 422, { code: 'INVALID_DEMAND_QUANTITY' });
    const updated = await store.$transaction(async (tx: any) => {
      const changed = await tx.procurementRequirement.updateMany({ where: { id: requirementId, recyclerId: req.identity!.collectorId, status: current.status }, data: { ...input, minimumLotKg, requiredQuantityKg, deadline: input.deadline === undefined ? undefined : input.deadline ? new Date(input.deadline) : null } });
      if (!changed.count) throw new AppError('CONFLICT', 'Demand was updated concurrently', 409, { code: 'DEMAND_UPDATE_CONFLICT' });
      const row = await tx.procurementRequirement.findUniqueOrThrow({ where: { id: requirementId } });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'RECYCLER', 'PROCUREMENT_REQUIREMENT_UPDATED', 'PROCUREMENT_REQUIREMENT', requirementId, { previousStatus: current.status, nextStatus: row.status, minimumLotKg: row.minimumLotKg, requiredQuantityKg: row.requiredQuantityKg });
      return row;
    });
    res.json({ success: true, data: updated });
  });
  router.get('/recycler/procurement-requirements', requireRecycler(jwt, db), async (req, res) => {
    res.json({ success: true, data: await store.procurementRequirement.findMany({ where: { recyclerId: req.identity!.collectorId }, orderBy: { createdAt: 'desc' } }) });
  });
  router.get('/kabadiwala/procurement-requirements', requireAuth(jwt, collectors), async (_req, res) => {
    const requirements = await store.procurementRequirement.findMany({ where: { status: 'OPEN', OR: [{ deadline: null }, { deadline: { gt: new Date() } }] }, orderBy: { createdAt: 'desc' }, take: 100 });
    const recyclerIds = [...new Set(requirements.map((row: any) => row.recyclerId))];
    const recyclers = recyclerIds.length ? await store.recycler.findMany({ where: { id: { in: recyclerIds }, authorizationStatus: 'VERIFIED', OR: [{ authorizationValidUntil: null }, { authorizationValidUntil: { gt: new Date() } }] }, select: { id: true, materials: { select: { category: true } } } }) : [];
    const visible = requirements.filter((requirement: any) => recyclers.some((recycler: any) => recycler.id === requirement.recyclerId && recycler.materials.some((row: any) => row.category === requirement.materialCategory)));
    res.json({ success: true, data: visible.map((requirement: any) => ({ id: requirement.id, materialCategory: requirement.materialCategory, minimumLotKg: requirement.minimumLotKg, requiredQuantityKg: requirement.requiredQuantityKg, preferredGrade: requirement.preferredGrade, maxRatePerKg: requirement.maxRatePerKg, procurementRadiusKm: requirement.procurementRadiusKm, deadline: requirement.deadline, status: requirement.status })) });
  });
  return router;
}
