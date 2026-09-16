import { Router, type Request, type Response } from 'express';
import { createHash } from 'node:crypto';
import { z } from 'zod';
import type { PrismaClient } from '@prisma/client';
import type { JwtService } from '../services/jwt.js';
import { requireAdmin } from '../middleware/auth.js';
import { AppError } from '../utils/errors.js';

const material = z.enum(['CRT', 'LCD_PANEL', 'PCB', 'CABLE', 'COPPER', 'BATTERY', 'MOTOR', 'MAGNET', 'PLASTIC', 'OTHER']);
const priceRow = z.object({
  externalId: z.string().trim().min(1).max(120), materialCategory: material,
  city: z.string().trim().min(1).max(120), areaName: z.string().trim().max(160).optional(),
  priceMin: z.number().finite().positive(), priceMax: z.number().finite().positive(), marketPrice: z.number().finite().positive(),
  unit: z.enum(['KILOGRAM', 'GRAM', 'PIECE']).default('KILOGRAM'), sourceOrganization: z.string().trim().min(1).max(200),
  sourceReference: z.string().trim().min(1).max(500), effectiveAt: z.string().datetime()
}).refine(row => row.priceMin <= row.marketPrice && row.marketPrice <= row.priceMax, { message: 'Market price must be within the range' });

export const datasetRoutes = (jwt: JwtService, db: PrismaClient) => Router()
  .post('/admin/datasets/prices/import', requireAdmin(jwt, db, 'DATASET_EXPORT'), async (req: Request, res: Response) => {
    const parsed = z.object({ rows: z.array(priceRow).min(1).max(500) }).safeParse(req.body);
    if (!parsed.success) throw new AppError('VALIDATION_ERROR', 'Price import contains invalid rows', 422, { code: 'INVALID_PRICE_IMPORT', details: parsed.error.flatten() });
    const imported: string[] = [];
    for (const row of parsed.data.rows) {
      const { externalId, effectiveAt, ...data } = row;
      const current = await db.price.upsert({ where: { id: externalId }, update: { ...data, source: 'ADMIN', qualityStatus: 'VALIDATED', ingestedAt: new Date(), effectiveAt: new Date(effectiveAt) }, create: { id: externalId, ...data, source: 'ADMIN', qualityStatus: 'VALIDATED', ingestedAt: new Date(), effectiveAt: new Date(effectiveAt) } });
      await db.priceHistory.create({ data: { priceId: current.id, materialCategory: current.materialCategory, city: current.city, areaName: current.areaName, priceMin: current.priceMin, priceMax: current.priceMax, marketPrice: current.marketPrice, unit: current.unit, source: current.source, sourceOrganization: current.sourceOrganization, sourceReference: current.sourceReference, ingestedAt: current.ingestedAt, qualityStatus: current.qualityStatus, effectiveAt: current.effectiveAt } });
      imported.push(current.id);
    }
    return res.status(201).json({ success: true, data: { imported, count: imported.length }, message: 'Validated price rows imported' });
  })
  .get('/admin/datasets/export', requireAdmin(jwt, db, 'DATASET_EXPORT'), async (req: Request, res: Response) => {
    const from = typeof req.query.from === 'string' ? new Date(req.query.from) : new Date(0);
    if (!Number.isFinite(from.getTime())) throw new AppError('VALIDATION_ERROR', 'Invalid export start date', 400, { code: 'INVALID_EXPORT_RANGE' });
    const exportSalt = process.env.DATASET_EXPORT_SALT?.trim();
    if (!exportSalt) throw new AppError('INTERNAL_SERVER_ERROR', 'Dataset export is not configured', 503, { code: 'DATASET_EXPORT_UNAVAILABLE' });
    const pseudonym = (prefix: string, value: string) => `${prefix}_${createHash('sha256').update(`${exportSalt}:${value}`).digest('hex').slice(0, 20)}`;
    const [materials, prices, recyclers, transactions, traceability, collectors, ai] = await Promise.all([
      db.lot.findMany({ where: { updatedAt: { gte: from } }, select: { id: true, collectorId: true, materialCategory: true, materialSubcategory: true, sourceType: true, wasteRegime: true, condition: true, weight: true, weightUnit: true, imageProvenance: true, imageQualityStatus: true, estimatedValue: true, updatedAt: true } }),
      db.priceHistory.findMany({ where: { effectiveAt: { gte: from } } }),
      // No contact data, exact location, license/evidence references, or
      // internal verifier identity leaves the administrative boundary.
      db.recycler.findMany({ select: { id: true, areaName: true, authorizationStatus: true, authorizationAuthority: true, authorizationType: true, verifiedAt: true, authorizationValidUntil: true, pickupAvailability: true, maxPickupDistanceKm: true, materials: { select: { category: true } }, rates: { select: { materialCategory: true, pricePerKg: true, unit: true, qualityStatus: true, effectiveAt: true } }, updatedAt: true } }),
      db.lot.findMany({ where: { updatedAt: { gte: from } }, select: { id: true, collectorId: true, materialCategory: true, weight: true, weightUnit: true, quotedPrice: true, finalPrice: true, collectionAreaName: true, createdAt: true, updatedAt: true, status: true, quotes: { select: { recyclerId: true, totalQuotedPrice: true, status: true, sentAt: true } }, payments: { select: { amount: true, paymentMethod: true, status: true, recordedAt: true } } } }),
      db.handover.findMany({ where: { updatedAt: { gte: from } }, select: { lotId: true, weight: true, actualWeight: true, timestamp: true, recyclerId: true, recyclerConfirmedAt: true, status: true, photoReference: true, actualWeightPhotoReference: true, updatedAt: true } }),
      db.collector.findMany({ select: { id: true, preferredLanguage: true, areaName: true, createdAt: true, lots: { select: { id: true, status: true, finalPrice: true, updatedAt: true } }, payments: { select: { amount: true, status: true, recordedAt: true } } } }),
      db.aiInference.findMany({ where: { createdAt: { gte: from } } })
    ]);
    const anonymizedMaterials = materials.map(({ id, collectorId, ...row }) => ({ lotKey: pseudonym('lot', id), collectorKey: pseudonym('collector', collectorId), ...row }));
    const anonymizedRecyclers = recyclers.map(({ id, ...row }) => ({ recyclerKey: pseudonym('recycler', id), ...row }));
    const anonymizedTransactions = transactions.map(({ id, collectorId, quotes, ...row }) => ({ lotKey: pseudonym('lot', id), collectorKey: pseudonym('collector', collectorId), ...row, quotes: quotes.map(({ recyclerId, ...quote }) => ({ recyclerKey: pseudonym('recycler', recyclerId), ...quote })) }));
    const anonymizedTraceability = traceability.map(({ lotId, recyclerId, photoReference, actualWeightPhotoReference, ...row }) => ({ lotKey: pseudonym('lot', lotId), recyclerKey: pseudonym('recycler', recyclerId), hasCollectionPhoto: Boolean(photoReference), hasScalePhoto: Boolean(actualWeightPhotoReference), ...row }));
    const anonymizedCollectors = collectors.map(({ id, lots, payments, ...row }) => ({ collectorKey: pseudonym('collector', id), ...row, lots: lots.map(({ id: lotId, ...lot }) => ({ lotKey: pseudonym('lot', lotId), ...lot })), payments }));
    const anonymizedAi = ai.map(({ inputProvenance, ...row }) => ({ ...row, inputProvenance: { hasImageHash: Boolean((inputProvenance as any)?.imageSha256), mimeType: (inputProvenance as any)?.mimeType ?? null } }));
    return res.json({ success: true, data: { schemaVersion: 2, generatedAt: new Date().toISOString(), from: from.toISOString(), materials: anonymizedMaterials, prices, recyclers: anonymizedRecyclers, transactions: anonymizedTransactions, traceability: anonymizedTraceability, collectors: anonymizedCollectors, ai: anonymizedAi }, message: 'Pseudonymized operational dataset exported' });
  });
