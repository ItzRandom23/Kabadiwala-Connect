import { randomInt } from 'node:crypto';
import type { MaterialCategory, LotCondition, LocationPrecision, PrismaClient, WeightUnit, ImageProvenance, WasteRegime } from '@prisma/client';
import type { LotRepository } from '../repositories/lotRepository.js';
import { AppError } from '../utils/errors.js';
import type { StorageService } from './storage.js';
import sharp from 'sharp';

export type LotInput = { materialCategory: MaterialCategory; materialSubcategory?: string; sourceType?: string; wasteRegime?: WasteRegime; condition: LotCondition; weight: number; weightUnit?: WeightUnit; imageProvenance?: ImageProvenance; collectionLocation: { latitude?: number; longitude?: number; areaName?: string; precision?: LocationPrecision }; notes?: string };

/** Pricing, quotes, and handovers are expressed per kilogram. Canonicalize
 * input at the boundary so GRAM values cannot silently inflate valuations.
 * PIECE is rejected until a piece-specific pricing model exists. */
export function canonicalWeight(weight: number, unit: WeightUnit = 'KILOGRAM'): number {
  if (!Number.isFinite(weight) || weight <= 0) throw new AppError('VALIDATION_ERROR', 'Weight must be greater than zero', 422, { code: 'INVALID_WEIGHT' });
  if (unit === 'PIECE') throw new AppError('VALIDATION_ERROR', 'Piece counts cannot be valued with per-kilogram rates', 422, { code: 'UNSUPPORTED_WEIGHT_UNIT' });
  const kilograms = unit === 'GRAM' ? weight / 1000 : weight;
  if (!Number.isFinite(kilograms) || kilograms >= 500) throw new AppError('VALIDATION_ERROR', 'Weight must be less than 500 kg', 422, { code: 'INVALID_WEIGHT' });
  return Number(kilograms.toFixed(6));
}

export class LotService {
  constructor(private readonly repo: LotRepository, private readonly storage: StorageService, private readonly db?: PrismaClient) {}

  async create(collectorId: string, input: LotInput, operationId?: string, allowDuplicate = false) {
    if (this.db && operationId) {
      const prior = await this.db.idempotencyRecord.findUnique({ where: { actorId_operationId: { actorId: collectorId, operationId } } });
      if (prior) return prior.response as any;
    }
    const weightKg = canonicalWeight(input.weight, input.weightUnit ?? 'KILOGRAM');
    if (this.db && !allowDuplicate) {
      const recent = await this.db.lot.findFirst({ where: { collectorId, materialCategory: input.materialCategory, condition: input.condition, weight: { gte: weightKg * .98, lte: weightKg * 1.02 }, status: 'CREATED', createdAt: { gte: new Date(Date.now() - 5 * 60 * 1000) } } });
      if (recent) throw new AppError('CONFLICT', 'A similar lot was just created. Confirm that this is a new lot before retrying.', 409, { code: 'DUPLICATE_LOT', duplicateLotId: recent.id });
    }
    const now = Date.now();
    const lot = await this.repo.create({ id: `LOT-${now}-${randomInt(100000, 999999)}`, collectorId, materialCategory: input.materialCategory, materialSubcategory: input.materialSubcategory, sourceType: input.sourceType, wasteRegime: input.wasteRegime ?? (input.materialCategory === 'BATTERY' ? 'BATTERY_WASTE' : 'E_WASTE'), condition: input.condition, weight: weightKg, weightUnit: 'KILOGRAM', originalWeight: input.weight, originalWeightUnit: input.weightUnit ?? 'KILOGRAM', imageProvenance: input.imageProvenance, collectionLatitude: input.collectionLocation.latitude, collectionLongitude: input.collectionLocation.longitude, collectionAreaName: input.collectionLocation.areaName, collectionLocationPrecision: input.collectionLocation.precision, notes: input.notes, status: 'CREATED', estimatedValue: null, quotedPrice: null, finalPrice: null });
    if (this.db && operationId) await this.db.idempotencyRecord.create({ data: { actorId: collectorId, operationId, action: 'CREATE_LOT', entityId: lot.id, response: JSON.parse(JSON.stringify(lot)) } });
    return lot;
  }

  async get(id: string, collectorId: string) { const lot = await this.repo.findOwned(id, collectorId); if (!lot) throw new AppError('NOT_FOUND', 'Lot not found', 404, { code: 'LOT_NOT_FOUND' }); return lot; }
  async photo(id: string, collectorId: string) { const lot = await this.get(id, collectorId); if (!lot.photoPath) throw new AppError('NOT_FOUND', 'Photo not found', 404, { code: 'PHOTO_NOT_FOUND' }); return this.storage.getImage(lot.photoPath); }
  async update(id: string, collectorId: string, version: number, input: { weight?: number; condition?: LotCondition; notes?: string }) {
    if (input.weight !== undefined) canonicalWeight(input.weight);
    const result = await this.repo.updateEditable(id, collectorId, version, input);
    if (!result.count) { const current = await this.repo.findOwned(id, collectorId); if (!current) throw new AppError('NOT_FOUND', 'Lot not found', 404, { code: 'LOT_NOT_FOUND' }); if (current.status !== 'CREATED') throw new AppError('CONFLICT', 'Lot is locked and cannot be edited', 409, { code: 'LOT_LOCKED' }); throw new AppError('CONFLICT', 'Lot has changed. Refresh and try again.', 409, { code: 'LOT_UPDATE_CONFLICT' }); }
    return this.get(id, collectorId);
  }

  async cancel(id: string, collectorId: string) { const result = await this.repo.cancel(id, collectorId); if (!result.count) { const current = await this.repo.findOwned(id, collectorId); if (!current) throw new AppError('NOT_FOUND', 'Lot not found', 404, { code: 'LOT_NOT_FOUND' }); throw new AppError('CONFLICT', 'Lot cannot be cancelled in its current state', 409, { code: 'LOT_LOCKED' }); } return this.get(id, collectorId); }

  async uploadPhoto(id: string, collectorId: string, file: { buffer: Buffer; mimetype: string }) { if (!file) throw new AppError('VALIDATION_ERROR', 'Photo is required', 400, { code: 'PHOTO_REQUIRED' }); if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.mimetype)) throw new AppError('VALIDATION_ERROR', 'Unsupported image type', 400, { code: 'INVALID_PHOTO' }); let metadata; try { metadata = await sharp(file.buffer).metadata(); } catch { throw new AppError('VALIDATION_ERROR', 'Invalid image', 400, { code: 'INVALID_PHOTO' }); } if (!metadata.width || !metadata.height || metadata.width < 300 || metadata.height < 300) throw new AppError('VALIDATION_ERROR', 'Photo must be at least 300 x 300 pixels', 400, { code: 'INVALID_PHOTO' }); const lot = await this.get(id, collectorId); if (lot.status !== 'CREATED') throw new AppError('CONFLICT', 'Lot is locked and cannot accept a photo', 409, { code: 'LOT_LOCKED' }); const key = `lots/${collectorId}/${id}-${Date.now()}.jpg`; const stored = await this.storage.putImage(file.buffer, key); const updated = await this.repo.updatePhoto(id, collectorId, stored.key, `/api/v1/lots/${id}/photo`, lot.imageProvenance ?? 'CAMERA'); if (!updated.count) { await this.storage.delete(stored.key).catch(() => undefined); throw new AppError('CONFLICT', 'Lot changed while uploading photo', 409, { code: 'LOT_UPDATE_CONFLICT' }); } return this.get(id, collectorId); }
}
