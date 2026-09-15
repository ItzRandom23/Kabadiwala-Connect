import type { MaterialCategory, PrismaClient, LotCondition } from '@prisma/client'; import { AppError } from '../utils/errors.js'; import { PriceRepository } from '../repositories/priceRepository.js'; import { canonicalWeight } from './lotService.js';
const multipliers: Record<LotCondition, number> = { INTACT: 1, DAMAGED: .7, PARTIAL: .4 };
const validMoney = (n:number) => Number.isFinite(n) && n > 0 && n < 1000000;
export class PriceService {
  constructor(private readonly repo: PriceRepository, private readonly db: PrismaClient) {}

  private async selected(material: MaterialCategory, location?: string) {
    const price = await this.repo.latest(material, location);
    if (!price) throw new AppError('NOT_FOUND', 'No price available for this material and location', 404, { code: 'PRICE_NOT_FOUND' });
    return price;
  }

  private trend(current: number, history: { marketPrice: number }[]) {
    const prior = history.slice(0, -1);
    if (!prior.length) return { direction: 'STABLE', percentage: 0 };
    const avg = prior.reduce((sum, point) => sum + point.marketPrice, 0) / prior.length;
    const percentage = avg ? Number((((current - avg) / avg) * 100).toFixed(2)) : 0;
    return { direction: percentage > 1 ? 'UP' : percentage < -1 ? 'DOWN' : 'STABLE', percentage };
  }

  async board(material: MaterialCategory, location?: string) {
    const price = await this.repo.latest(material, location);
    if (!price) {
      // A missing market-data row is a normal catalogue state, not a server
      // failure. Return a typed unavailable board so clients can show an
      // empty/offline state without generating repeated 404 error logs.
      return {
        available: false,
        materialCategory: material,
        location: location?.trim() || null,
        priceMin: null,
        priceMax: null,
        marketPrice: null,
        historicalAverage: null,
        unit: 'KILOGRAM',
        source: null,
        qualityStatus: 'UNAVAILABLE',
        ingestedAt: null,
        trend: { direction: 'STABLE', percentage: 0 },
        lastUpdated: null,
        complianceRegime: material === 'BATTERY' ? 'BATTERY_WASTE_RULES' : 'E_WASTE_RULES',
        disclaimer: 'No verified price is available for this material yet. Confirm the current rate before sale.'
      };
    }

    const history = await this.repo.history(material, location, new Date(Date.now() - 30 * 86400000));
    const stale = Date.now() - price.effectiveAt.getTime() > 7 * 86400000;
    const observationCount = history.length;
    const confidence = stale ? 'LOW' : observationCount >= 20 && price.qualityStatus === 'VALIDATED' ? 'HIGH' : observationCount >= 10 ? 'MEDIUM' : observationCount > 0 ? 'LOW' : 'INSUFFICIENT';
    return {
      available: true,
      materialCategory: material,
      location: price.areaName ?? price.city,
      priceMin: price.priceMin,
      priceMax: price.priceMax,
      marketPrice: price.marketPrice,
      historicalAverage: price.historicalAverage,
      unit: price.unit,
      source: { type: price.source, organization: price.sourceOrganization, reference: price.sourceReference },
      sourceClassification: price.source,
      qualityStatus: stale ? 'STALE' : price.qualityStatus,
      ingestedAt: price.ingestedAt.toISOString(),
      observationCount,
      dataAgeDays: Math.max(0, Math.floor((Date.now() - price.effectiveAt.getTime()) / 86400000)),
      confidence,
      isDemoData: price.source === 'SYSTEM',
      trend: this.trend(price.marketPrice, history),
      lastUpdated: price.effectiveAt.toISOString(),
      complianceRegime: material === 'BATTERY' ? 'BATTERY_WASTE_RULES' : 'E_WASTE_RULES',
      disclaimer: stale ? 'This price is more than 7 days old. Confirm the current rate before sale.' : 'Indicative buying range; final price is confirmed after inspection.'
    };
  }

  async history(material: MaterialCategory, location: string | undefined, days: number) {
    return {
      materialCategory: material,
      location: location ?? null,
      days,
      history: (await this.repo.history(material, location, new Date(Date.now() - days * 86400000))).map(point => ({
        date: point.effectiveAt.toISOString(),
        marketPrice: point.marketPrice,
        unit: point.unit,
        source: point.source,
        qualityStatus: point.qualityStatus
      }))
    };
  }

  async valuation(lotId: string, collectorId: string) {
    const lot = await this.db.lot.findFirst({ where: { id: lotId, collectorId } });
    if (!lot) throw new AppError('NOT_FOUND', 'Lot not found', 404, { code: 'LOT_NOT_FOUND' });
    const price = await this.selected(lot.materialCategory, lot.collectionAreaName ?? undefined);
    const weightKg = canonicalWeight(lot.weight, lot.weightUnit);
    const value = Number((price.marketPrice * weightKg * multipliers[lot.condition]).toFixed(2));
    await this.db.lot.update({ where: { id: lot.id }, data: { estimatedValue: value } });
    return {
      lotId,
      basePricePerKg: price.marketPrice,
      weight: weightKg,
      weightUnit: 'KILOGRAM',
      conditionMultiplier: multipliers[lot.condition],
      qualityAdjustment: 1,
      estimatedValue: value,
      priceDate: price.effectiveAt.toISOString(),
      complianceRegime: lot.wasteRegime,
      disclaimer: 'Estimate only; the recycler confirms material, weight and final value after inspection.'
    };
  }

  async update(id: string, actor: string, data: { priceMin: number; priceMax: number; marketPrice: number; reason?: string }) {
    if (!validMoney(data.priceMin) || !validMoney(data.priceMax) || !validMoney(data.marketPrice) || data.priceMin > data.priceMax || data.marketPrice < data.priceMin || data.marketPrice > data.priceMax) {
      throw new AppError('VALIDATION_ERROR', 'Invalid price range', 422, { code: 'INVALID_PRICE' });
    }
    const result = await this.repo.update(id, actor, data);
    if (!result) throw new AppError('NOT_FOUND', 'Price not found', 404, { code: 'PRICE_NOT_FOUND' });
    return result;
  }
}
