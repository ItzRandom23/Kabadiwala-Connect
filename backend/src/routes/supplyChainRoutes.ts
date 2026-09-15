import { Router } from 'express';
import { createHash } from 'node:crypto';
import { z } from 'zod';
import type { PrismaClient } from '@prisma/client';
import type { JwtService } from '../services/jwt.js';
import type { CollectorRepository } from '../repositories/collectorRepository.js';
import { requireAuth, requireHousehold, requireRecycler } from '../middleware/auth.js';
import { AppError } from '../utils/errors.js';
import { assertInventoryInvariant, ownedKg, recordInventoryMovement } from '../services/inventoryLedger.js';

const material = z.enum(['CRT', 'LCD_PANEL', 'PCB', 'CABLE', 'COPPER', 'BATTERY', 'MOTOR', 'MAGNET', 'PLASTIC', 'OTHER']);
const condition = z.enum(['INTACT', 'DAMAGED', 'PARTIAL']);
const positive = z.number().finite().positive();
const id = z.string().regex(/^[A-Za-z0-9_-]{1,80}$/);
const listingInput = z.object({ materialCategory: material, estimatedWeight: positive.max(500), condition, notes: z.string().trim().max(1000).optional(), photoReference: z.string().trim().max(500).optional(), areaName: z.string().trim().min(1).max(160), latitude: z.number().finite().min(-90).max(90).optional(), longitude: z.number().finite().min(-180).max(180).optional(), estimatedPriceMin: positive.max(100000000).optional(), estimatedPriceMax: positive.max(100000000).optional(), dataBearingDevice: z.boolean().default(false), ownerPreparationCompleted: z.boolean().default(false), dataDestructionRequested: z.boolean().default(false) }).refine(value => value.estimatedPriceMin == null || value.estimatedPriceMax == null || value.estimatedPriceMin <= value.estimatedPriceMax, { message: 'Estimated minimum cannot exceed estimated maximum', path: ['estimatedPriceMax'] }).refine(value => !value.ownerPreparationCompleted || value.dataBearingDevice, { message: 'Owner preparation only applies to data-bearing devices', path: ['ownerPreparationCompleted'] }).refine(value => !value.dataDestructionRequested || value.dataBearingDevice, { message: 'Destruction requests only apply to data-bearing devices', path: ['dataDestructionRequested'] });
const pickupRequest = z.object({ kabadiwalaId: id, requestedSlot: z.string().datetime().optional() });
const cancellationInput = z.object({ reason: z.string().trim().max(500).optional() }).default({});
const activePickupStatuses = ['REQUESTED', 'ACCEPTED', 'SCHEDULED', 'IN_TRANSIT', 'ARRIVED', 'WEIGHED'] as const;
const bulkInput = z.object({ materialCategory: material, grade: z.string().trim().min(1).max(80).default('UNSPECIFIED'), quantityKg: positive.max(100000), askingRatePerKg: positive.max(1000000), minimumRatePerKg: positive.max(1000000).optional(), areaName: z.string().trim().min(1).max(160), latitude: z.number().min(-90).max(90).optional(), longitude: z.number().min(-180).max(180).optional(), notes: z.string().trim().max(1000).optional() }).refine(value => !value.minimumRatePerKg || value.minimumRatePerKg <= value.askingRatePerKg, { message: 'Minimum rate cannot exceed asking rate' });
const settlementDecision = z.object({ decision: z.enum(['ACCEPT', 'RAISE_ISSUE']), reasonCode: z.string().trim().min(2).max(120).optional(), evidenceReference: z.string().trim().max(500).optional(), notes: z.string().trim().max(1000).optional() }).superRefine((value, ctx) => { if (value.decision === 'RAISE_ISSUE' && !value.reasonCode) ctx.addIssue({ code: 'custom', path: ['reasonCode'], message: 'A reason code is required when raising an issue' }); });

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

async function auditSupplyEvent(tx: any, actorId: string, actorRole: string, event: string, entityType: string, entityId: string, metadata: Record<string, unknown>) {
  await tx.auditEvent.create({ data: { actorId, actorRole, event, entityType, entityId, metadata } });
  await tx.materialPassportEvent.create({ data: { entityType, entityId, eventType: event, actorId, actorRole, metadata, occurredAt: new Date() } });
}

/** The closed-loop marketplace. Every endpoint is role-gated here, rather than
 * trusting the Android navigation layer. */
export function supplyChainRoutes(jwt: JwtService, collectors: CollectorRepository, db: PrismaClient) {
  const router = Router();
  const store: any = db;

  router.post('/household/listings', requireHousehold(jwt, collectors), async (req, res) => {
    const input = parse(listingInput, req.body);
    const destructionEvidenceStatus = !input.dataBearingDevice
      ? 'NOT_APPLICABLE'
      : input.dataDestructionRequested
        ? 'DESTRUCTION_REQUESTED'
        : input.ownerPreparationCompleted
          ? 'OWNER_PREPARATION_COMPLETED'
          : 'OWNER_PREPARATION_PENDING';
    const listing = await store.$transaction(async (tx: any) => {
      const created = await tx.householdListing.create({ data: { ...input, destructionEvidenceStatus, householdId: req.identity!.collectorId, status: 'POSTED' } });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'HOUSEHOLD', 'HOUSEHOLD_LISTED', 'HOUSEHOLD_LISTING', created.id, { materialCategory: created.materialCategory, estimatedWeight: created.estimatedWeight, areaName: created.areaName, dataBearingDevice: created.dataBearingDevice, dataDestructionRequested: created.dataDestructionRequested });
      return created;
    });
    res.status(201).json({ success: true, data: listing });
  });
  router.get('/household/listings', requireHousehold(jwt, collectors), async (req, res) => {
    res.json({ success: true, data: await store.householdListing.findMany({ where: { householdId: req.identity!.collectorId }, orderBy: { createdAt: 'desc' } }) });
  });
  router.get('/household/listings/:listingId', requireHousehold(jwt, collectors), async (req, res) => {
    const listingId = parse(id, req.params.listingId);
    const listing = await store.householdListing.findFirst({ where: { id: listingId, householdId: req.identity!.collectorId } });
    if (!listing) throw new AppError('NOT_FOUND', 'Listing not found', 404, { code: 'LISTING_NOT_FOUND' });
    const pickups = await store.pickupRequest.findMany({ where: { listingId, householdId: req.identity!.collectorId }, orderBy: { createdAt: 'desc' } });
    res.json({ success: true, data: { ...listing, pickups } });
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
    res.json({ success: true, data: updated });
  });
  router.get('/household/kabadiwalas', requireHousehold(jwt, collectors), async (req, res) => {
    const users = await store.user.findMany({ where: { role: 'COLLECTOR', accountStatus: 'ACTIVE', collectorProfileId: { not: null } }, select: { collectorProfileId: true } });
    const ids = users.map((user: { collectorProfileId: string | null }) => user.collectorProfileId).filter(Boolean);
    const profiles = await store.collector.findMany({ where: { id: { in: ids }, accountStatus: 'ACTIVE' }, select: { id: true, displayName: true, areaName: true, latitude: true, longitude: true } });
    res.json({ success: true, data: profiles });
  });
  router.post('/household/listings/:listingId/pickups', requireHousehold(jwt, collectors), async (req, res) => {
    const listingId = parse(id, req.params.listingId); const input = parse(pickupRequest, req.body);
    const operationId = operationKey(req);
    const operationHash = requestHash({ action: 'REQUEST_PICKUP', listingId, input });
    const kabadiwala = await store.user.findFirst({ where: { collectorProfileId: input.kabadiwalaId, role: 'COLLECTOR', accountStatus: 'ACTIVE' } });
    if (!kabadiwala) throw new AppError('NOT_FOUND', 'Kabadiwala not found', 404);
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
        update: { requestedSlot: input.requestedSlot ? new Date(input.requestedSlot) : undefined, status: 'REQUESTED', cancelledBy: null, cancellationReason: null },
        create: { listingId, householdId: req.identity!.collectorId, kabadiwalaId: input.kabadiwalaId, requestedSlot: input.requestedSlot ? new Date(input.requestedSlot) : undefined }
      });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'HOUSEHOLD', 'PICKUP_REQUESTED', 'PICKUP_REQUEST', pickup.id, { listingId, kabadiwalaId: input.kabadiwalaId, requestedSlot: input.requestedSlot ?? null });
      if (operationId) await tx.idempotencyRecord.create({ data: { actorId: req.identity!.collectorId, operationId, action: 'REQUEST_PICKUP', entityId: pickup.id, requestHash: operationHash, response: jsonValue(pickup) } });
      return { pickup, created: !existing, replayed: false };
    });
    res.status(result.created ? 201 : 200).json({ success: true, data: result.pickup, ...(result.replayed ? { message: 'Pickup request already processed' } : {}) });
  });
  router.get('/household/pickups', requireHousehold(jwt, collectors), async (req, res) => {
    res.json({ success: true, data: await store.pickupRequest.findMany({ where: { householdId: req.identity!.collectorId }, orderBy: { createdAt: 'desc' } }) });
  });
  router.get('/household/pickups/:pickupId', requireHousehold(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId);
    const pickup = await store.pickupRequest.findFirst({ where: { id: pickupId, householdId: req.identity!.collectorId } });
    if (!pickup) throw new AppError('NOT_FOUND', 'Pickup not found', 404, { code: 'PICKUP_NOT_FOUND' });
    const listing = await store.householdListing.findFirst({ where: { id: pickup.listingId, householdId: req.identity!.collectorId } });
    res.json({ success: true, data: { pickup, listing } });
  });
  router.get('/household/pickups/:pickupId/passport', requireHousehold(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId);
    const pickup = await store.pickupRequest.findFirst({ where: { id: pickupId, householdId: req.identity!.collectorId } });
    if (!pickup) throw new AppError('NOT_FOUND', 'Material passport not found', 404, { code: 'PASSPORT_NOT_FOUND' });
    const listing = await store.householdListing.findFirst({ where: { id: pickup.listingId, householdId: req.identity!.collectorId } });
    const [events, movements] = await Promise.all([
      store.materialPassportEvent.findMany({ where: { OR: [{ entityType: 'HOUSEHOLD_LISTING', entityId: pickup.listingId }, { entityType: 'PICKUP_REQUEST', entityId: pickupId }] }, orderBy: { occurredAt: 'asc' } }),
      store.inventoryMovement.findMany({ where: { sourceType: 'PICKUP_REQUEST', sourceId: pickupId }, orderBy: { createdAt: 'asc' } })
    ]);
    res.json({ success: true, data: { pickup, listing, events, inventoryMovements: movements, disclaimer: 'Traceability is platform evidence for this prototype; it is not a government certificate.' } });
  });
  router.post('/household/pickups/:pickupId/reschedule', requireHousehold(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId);
    const input = parse(z.object({ scheduledSlot: z.string().datetime() }), req.body);
    const scheduledSlot = new Date(input.scheduledSlot);
    const pickup = await store.pickupRequest.findFirst({ where: { id: pickupId, householdId: req.identity!.collectorId, status: { in: ['REQUESTED', 'ACCEPTED', 'SCHEDULED', 'REASSIGNMENT_REQUIRED'] } } });
    if (!pickup) throw new AppError('CONFLICT', 'This pickup cannot be rescheduled', 409, { code: 'PICKUP_NOT_RESCHEDULABLE' });
    const updated = await store.$transaction(async (tx: any) => {
      const row = await tx.pickupRequest.update({ where: { id: pickupId }, data: { requestedSlot: scheduledSlot, scheduledSlot, status: pickup.status === 'REASSIGNMENT_REQUIRED' ? 'REQUESTED' : 'SCHEDULED', reassignmentReason: null, lateCancellation: false } });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'HOUSEHOLD', 'PICKUP_RESCHEDULED', 'PICKUP_REQUEST', pickupId, { scheduledSlot: scheduledSlot.toISOString() });
      return row;
    });
    res.json({ success: true, data: updated });
  });
  router.post('/household/pickups/:pickupId/settlement', requireHousehold(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId);
    const decision = parse(settlementDecision, req.body ?? {});
    const pickup = await store.pickupRequest.findFirst({ where: { id: pickupId, householdId: req.identity!.collectorId, status: 'COMPLETED', settlementStatus: 'PENDING_HOUSEHOLD_CONFIRMATION' } });
    if (!pickup) throw new AppError('CONFLICT', 'This pickup settlement is no longer actionable', 409, { code: 'SETTLEMENT_ALREADY_DECIDED' });
    const updated = await store.$transaction(async (tx: any) => {
      const row = await tx.pickupRequest.updateMany({ where: { id: pickupId, householdId: req.identity!.collectorId, settlementStatus: 'PENDING_HOUSEHOLD_CONFIRMATION' }, data: { settlementStatus: decision.decision === 'ACCEPT' ? 'ACCEPTED' : 'DISPUTED', householdDecision: decision.decision, settlementReasonCode: decision.reasonCode ?? null, settlementEvidenceReference: decision.evidenceReference ?? null, settlementDisputeNotes: decision.notes ?? null, settlementDecisionAt: new Date() } });
      if (!row.count) throw new AppError('CONFLICT', 'This pickup settlement was already decided', 409, { code: 'SETTLEMENT_ALREADY_DECIDED' });
      if (decision.decision === 'RAISE_ISSUE') await tx.anomalyFlag.create({ data: { entityType: 'PICKUP_REQUEST', entityId: pickupId, ruleCode: decision.reasonCode ?? 'HOUSEHOLD_DISPUTE', severity: 'MEDIUM', details: { notes: decision.notes ?? null, evidenceReference: decision.evidenceReference ?? null } } });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'HOUSEHOLD', decision.decision === 'ACCEPT' ? 'SETTLEMENT_ACCEPTED' : 'SETTLEMENT_DISPUTED', 'PICKUP_REQUEST', pickupId, { reasonCode: decision.reasonCode ?? null, notes: decision.notes ?? null });
      return tx.pickupRequest.findUniqueOrThrow({ where: { id: pickupId } });
    });
    res.json({ success: true, data: updated });
  });
  router.get('/household/listings/:listingId/passport', requireHousehold(jwt, collectors), async (req, res) => {
    const listingId = parse(id, req.params.listingId);
    const listing = await store.householdListing.findFirst({ where: { id: listingId, householdId: req.identity!.collectorId } });
    if (!listing) throw new AppError('NOT_FOUND', 'Material passport not found', 404, { code: 'PASSPORT_NOT_FOUND' });
    const pickups = await store.pickupRequest.findMany({ where: { listingId, householdId: req.identity!.collectorId }, select: { id: true } });
    const pickupIds = pickups.map((row: { id: string }) => row.id);
    const [events, movements] = await Promise.all([
      store.materialPassportEvent.findMany({ where: { OR: [{ entityType: 'HOUSEHOLD_LISTING', entityId: listingId }, ...(pickupIds.length ? [{ entityType: 'PICKUP_REQUEST', entityId: { in: pickupIds } }] : [])] }, orderBy: { occurredAt: 'asc' } }),
      pickupIds.length ? store.inventoryMovement.findMany({ where: { sourceType: 'PICKUP_REQUEST', sourceId: { in: pickupIds } }, orderBy: { createdAt: 'asc' } }) : []
    ]);
    res.json({ success: true, data: { listing, pickupIds, events, inventoryMovements: movements, disclaimer: 'Traceability is platform evidence for this prototype; it is not a government certificate.' } });
  });
  router.post('/household/listings/:listingId/cancel', requireHousehold(jwt, collectors), async (req, res) => {
    const listingId = parse(id, req.params.listingId); const input = parse(cancellationInput, req.body ?? {});
    await store.$transaction(async (tx: any) => {
      const listing = await tx.householdListing.findFirst({ where: { id: listingId, householdId: req.identity!.collectorId, status: { in: ['POSTED', 'MATCHED'] } } });
      if (!listing) throw new AppError('CONFLICT', 'Only an open listing can be cancelled', 409);
      await tx.householdListing.update({ where: { id: listingId }, data: { status: 'CANCELLED' } });
      const now = new Date();
      const activePickups = await tx.pickupRequest.findMany({ where: { listingId, householdId: req.identity!.collectorId, status: { in: activePickupStatuses } }, select: { id: true, scheduledSlot: true } });
      await Promise.all(activePickups.map((pickup: { id: string; scheduledSlot: Date | null }) => tx.pickupRequest.update({ where: { id: pickup.id }, data: { status: 'CANCELLED', cancelledBy: 'HOUSEHOLD', cancellationReason: input.reason ?? null, cancelledAt: now, lateCancellation: Boolean(pickup.scheduledSlot && pickup.scheduledSlot.getTime() - now.getTime() < 24 * 60 * 60 * 1000) } })));
      await auditSupplyEvent(tx, req.identity!.collectorId, 'HOUSEHOLD', 'LISTING_CANCELLED', 'HOUSEHOLD_LISTING', listingId, { reason: input.reason ?? null, cancelledPickupCount: activePickups.length });
    });
    res.json({ success: true });
  });
  router.post('/household/pickups/:pickupId/cancel', requireHousehold(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId); const input = parse(cancellationInput, req.body ?? {});
    await store.$transaction(async (tx: any) => {
      const pickup = await tx.pickupRequest.findFirst({ where: { id: pickupId, householdId: req.identity!.collectorId, status: { in: ['REQUESTED', 'ACCEPTED', 'SCHEDULED', 'REASSIGNMENT_REQUIRED'] } } });
      if (!pickup) throw new AppError('CONFLICT', 'This pickup can no longer be cancelled', 409);
      const updated = await tx.pickupRequest.updateMany({ where: { id: pickupId, householdId: req.identity!.collectorId, status: { in: ['REQUESTED', 'ACCEPTED', 'SCHEDULED', 'REASSIGNMENT_REQUIRED'] } }, data: { status: 'CANCELLED', cancelledBy: 'HOUSEHOLD', cancellationReason: input.reason ?? null, cancelledAt: new Date(), lateCancellation: Boolean(pickup.scheduledSlot && pickup.scheduledSlot.getTime() - Date.now() < 24 * 60 * 60 * 1000) } });
      if (!updated.count) throw new AppError('CONFLICT', 'This pickup was already updated', 409);
      const remaining = await tx.pickupRequest.count({ where: { listingId: pickup.listingId, status: { in: activePickupStatuses } } });
      if (!remaining) await tx.householdListing.updateMany({ where: { id: pickup.listingId, householdId: req.identity!.collectorId, status: 'MATCHED' }, data: { status: 'POSTED' } });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'HOUSEHOLD', 'PICKUP_CANCELLED', 'PICKUP_REQUEST', pickupId, { reason: input.reason ?? null });
    });
    res.json({ success: true });
  });

  router.get('/kabadiwala/listings', requireAuth(jwt, collectors), async (req, res) => {
    const assigned = await store.pickupRequest.findMany({ where: { kabadiwalaId: req.identity!.collectorId }, select: { listingId: true } });
    const assignedIds = assigned.map((pickup: { listingId: string }) => pickup.listingId);
    res.json({ success: true, data: await store.householdListing.findMany({ where: { OR: [{ status: 'POSTED' }, ...(assignedIds.length ? [{ id: { in: assignedIds } }] : [])] }, select: { id: true, materialCategory: true, estimatedWeight: true, condition: true, notes: true, areaName: true, estimatedPriceMin: true, estimatedPriceMax: true, status: true, createdAt: true, updatedAt: true }, orderBy: { createdAt: 'desc' }, take: 100 }) });
  });
  router.get('/kabadiwala/pickups', requireAuth(jwt, collectors), async (req, res) => {
    res.json({ success: true, data: await store.pickupRequest.findMany({ where: { kabadiwalaId: req.identity!.collectorId }, select: { id: true, listingId: true, kabadiwalaId: true, status: true, requestedSlot: true, scheduledSlot: true, actualWeight: true, finalCategory: true, grade: true, ratePerKg: true, finalAmount: true, completedAt: true, createdAt: true, updatedAt: true }, orderBy: { createdAt: 'desc' } }) });
  });
  router.post('/kabadiwala/listings/:listingId/accept', requireAuth(jwt, collectors), async (req, res) => {
    const listingId = parse(id, req.params.listingId);
    await store.$transaction(async (tx: any) => {
      const updated = await tx.pickupRequest.updateMany({ where: { listingId, kabadiwalaId: req.identity!.collectorId, status: 'REQUESTED' }, data: { status: 'ACCEPTED', acceptedAt: new Date() } });
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
    const updated = await store.pickupRequest.updateMany({ where: { id: pickupId, kabadiwalaId: req.identity!.collectorId, status: { in: ['ACCEPTED', 'SCHEDULED'] } }, data: { availabilityConfirmedAt: new Date() } });
    if (!updated.count) throw new AppError('CONFLICT', 'Pickup is not awaiting availability confirmation', 409, { code: 'PICKUP_NOT_CONFIRMABLE' });
    res.json({ success: true, data: await store.pickupRequest.findUnique({ where: { id: pickupId } }) });
  });
  router.post('/kabadiwala/pickups/:pickupId/schedule', requireAuth(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId); const scheduledSlot = parse(z.object({ scheduledSlot: z.string().datetime() }), req.body).scheduledSlot;
    await store.$transaction(async (tx: any) => {
      const updated = await tx.pickupRequest.updateMany({ where: { id: pickupId, kabadiwalaId: req.identity!.collectorId, status: { in: ['ACCEPTED', 'SCHEDULED', 'REASSIGNMENT_REQUIRED'] } }, data: { status: 'SCHEDULED', scheduledSlot: new Date(scheduledSlot), reassignmentReason: null } });
      if (!updated.count) throw new AppError('CONFLICT', 'Pickup cannot be scheduled', 409);
      await auditSupplyEvent(tx, req.identity!.collectorId, 'COLLECTOR', 'PICKUP_SCHEDULED', 'PICKUP_REQUEST', pickupId, { scheduledSlot });
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
      await tx.householdListing.updateMany({ where: { id: pickup.listingId, status: 'MATCHED' }, data: { status: 'POSTED' } });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'COLLECTOR', 'PICKUP_CANCELLED', 'PICKUP_REQUEST', pickupId, { reason: input.reason ?? null });
    });
    res.json({ success: true });
  });
  router.post('/kabadiwala/pickups/:pickupId/reassign', requireAuth(jwt, collectors), async (req, res) => {
    const pickupId = parse(id, req.params.pickupId);
    const input = parse(z.object({ reason: z.string().trim().min(2).max(500), noShow: z.boolean().default(false) }), req.body ?? {});
    const result = await store.$transaction(async (tx: any) => {
      const updated = await tx.pickupRequest.updateMany({ where: { id: pickupId, kabadiwalaId: req.identity!.collectorId, status: { in: ['ACCEPTED', 'SCHEDULED', 'IN_TRANSIT', 'ARRIVED'] } }, data: { status: 'REASSIGNMENT_REQUIRED', reassignmentReason: input.reason, noShow: input.noShow, cancelledBy: 'COLLECTOR', cancellationReason: input.reason, cancelledAt: new Date() } });
      if (!updated.count) throw new AppError('CONFLICT', 'Pickup is not eligible for reassignment', 409, { code: 'PICKUP_NOT_REASSIGNABLE' });
      const pickup = await tx.pickupRequest.findUniqueOrThrow({ where: { id: pickupId } });
      await tx.householdListing.updateMany({ where: { id: pickup.listingId, status: 'MATCHED' }, data: { status: 'POSTED' } });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'COLLECTOR', 'PICKUP_REASSIGNMENT_REQUIRED', 'PICKUP_REQUEST', pickupId, { reason: input.reason, noShow: input.noShow });
      return pickup;
    });
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
    const result = await store.$transaction(async (tx: any) => {
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
      return tx.pickupRequest.findUniqueOrThrow({ where: { id: pickupId } });
    });
    res.json({ success: true, data: result });
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
    const lot = await store.$transaction(async (tx: any) => {
      const before = await tx.inventoryBalance.findUnique({ where: { kabadiwalaId_materialCategory_grade: { kabadiwalaId: req.identity!.collectorId, materialCategory: input.materialCategory, grade: input.grade } } });
      const reserved = await tx.inventoryBalance.updateMany({ where: { kabadiwalaId: req.identity!.collectorId, materialCategory: input.materialCategory, grade: input.grade, availableKg: { gte: input.quantityKg } }, data: { availableKg: { decrement: input.quantityKg }, reservedKg: { increment: input.quantityKg } } });
      if (!reserved.count) throw new AppError('CONFLICT', 'Insufficient available inventory for this bulk lot', 409);
      const after = await tx.inventoryBalance.findUniqueOrThrow({ where: { kabadiwalaId_materialCategory_grade: { kabadiwalaId: req.identity!.collectorId, materialCategory: input.materialCategory, grade: input.grade } } });
      assertInventoryInvariant(after);
      const created = await tx.bulkLot.create({ data: { ...input, kabadiwalaId: req.identity!.collectorId, status: 'LISTED' } });
      await recordInventoryMovement(tx, before ?? { ...after, availableKg: after.availableKg + input.quantityKg, reservedKg: after.reservedKg - input.quantityKg }, after, 'RESERVATION', input.quantityKg, 'BULK_LOT', created.id, { bulkLotId: created.id });
      await auditSupplyEvent(tx, req.identity!.collectorId, 'COLLECTOR', 'BULK_LOT_LISTED', 'BULK_LOT', created.id, { materialCategory: created.materialCategory, grade: created.grade, quantityKg: created.quantityKg, askingRatePerKg: created.askingRatePerKg });
      return created;
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
    const offer = await store.bulkOffer.upsert({ where: { bulkLotId_recyclerId: { bulkLotId: lotId, recyclerId: req.identity!.collectorId } }, update: { offeredRatePerKg, status: 'PENDING' }, create: { bulkLotId: lotId, recyclerId: req.identity!.collectorId, offeredRatePerKg } });
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
    const updated = await store.bulkOffer.update({ where: { id: offerId }, data: { offeredRatePerKg } });
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
      if (lot.minimumRatePerKg && offer.offeredRatePerKg < lot.minimumRatePerKg) throw new AppError('CONFLICT', 'Offer is below the lot minimum', 409);
      await tx.bulkOffer.update({ where: { id: offerId }, data: { status: 'ACCEPTED' } });
      await tx.bulkOffer.updateMany({ where: { bulkLotId: lot.id, id: { not: offerId }, status: 'PENDING' }, data: { status: 'REJECTED' } });
      await tx.bulkLot.update({ where: { id: lot.id }, data: { status: 'RESERVED', reservedForId: offer.recyclerId } });
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
    const requirement = await store.procurementRequirement.create({ data: { ...input, deadline: input.deadline ? new Date(input.deadline) : undefined, recyclerId: req.identity!.collectorId } });
    res.status(201).json({ success: true, data: requirement });
  });
  router.patch('/recycler/procurement-requirements/:requirementId', requireRecycler(jwt, db), async (req, res) => {
    const requirementId = parse(id, req.params.requirementId);
    const input = parse(z.object({ minimumLotKg: positive.max(100000).optional(), requiredQuantityKg: positive.max(1000000).optional(), preferredGrade: z.string().trim().max(80).nullable().optional(), maxRatePerKg: positive.max(1000000).nullable().optional(), procurementRadiusKm: positive.max(1000).optional(), deadline: z.string().datetime().nullable().optional(), status: z.enum(['OPEN', 'PAUSED', 'FULFILLED', 'CANCELLED']).optional() }).strict(), req.body);
    const current = await store.procurementRequirement.findFirst({ where: { id: requirementId, recyclerId: req.identity!.collectorId } });
    if (!current) throw new AppError('NOT_FOUND', 'Procurement requirement not found', 404, { code: 'DEMAND_NOT_FOUND' });
    const minimumLotKg = input.minimumLotKg ?? current.minimumLotKg;
    const requiredQuantityKg = input.requiredQuantityKg ?? current.requiredQuantityKg;
    if (minimumLotKg > requiredQuantityKg) throw new AppError('VALIDATION_ERROR', 'Minimum lot cannot exceed required quantity', 422, { code: 'INVALID_DEMAND_QUANTITY' });
    const updated = await store.procurementRequirement.update({ where: { id: requirementId }, data: { ...input, minimumLotKg, requiredQuantityKg, deadline: input.deadline === undefined ? undefined : input.deadline ? new Date(input.deadline) : null } });
    res.json({ success: true, data: updated });
  });
  router.get('/recycler/procurement-requirements', requireRecycler(jwt, db), async (req, res) => {
    res.json({ success: true, data: await store.procurementRequirement.findMany({ where: { recyclerId: req.identity!.collectorId }, orderBy: { createdAt: 'desc' } }) });
  });
  router.get('/kabadiwala/procurement-requirements', requireAuth(jwt, collectors), async (_req, res) => {
    res.json({ success: true, data: await store.procurementRequirement.findMany({ where: { status: 'OPEN', OR: [{ deadline: null }, { deadline: { gt: new Date() } }] }, orderBy: { createdAt: 'desc' } }) });
  });
  return router;
}
