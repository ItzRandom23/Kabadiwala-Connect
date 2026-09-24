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
    const requestedLocation = location?.trim() || null;
    const dataAgeDays = price ? Math.max(0, Math.floor((Date.now() - price.effectiveAt.getTime()) / 86400000)) : null;
    const unavailableReason = !price
      ? 'NO_LOCAL_RATE'
      : price.source === 'SYSTEM'
        ? 'DEMO_DATA'
        : price.qualityStatus !== 'VALIDATED'
          ? 'RATE_NOT_VERIFIED'
          : dataAgeDays != null && dataAgeDays > 7
            ? 'RATE_STALE'
            : null;
    if (!price || unavailableReason) {
      return {
        available: false,
        materialCategory: material,
        location: requestedLocation,
        requestedLocation,
        locationMatched: Boolean(price),
        sourceContext: price ? 'LOCATION_MATCH' : 'NO_LOCAL_RATE',
        priceMin: null,
        priceMax: null,
        marketPrice: null,
        historicalAverage: null,
        unit: 'KILOGRAM',
        source: price ? { type: price.source, organization: price.sourceOrganization, reference: price.sourceReference } : null,
        qualityStatus: unavailableReason === 'RATE_STALE' ? 'STALE' : unavailableReason === 'RATE_NOT_VERIFIED' ? price?.qualityStatus : 'UNAVAILABLE',
        unavailableReason,
        ingestedAt: price?.ingestedAt.toISOString() ?? null,
        dataAgeDays,
        trend: { direction: 'STABLE', percentage: 0 },
        lastUpdated: price?.effectiveAt.toISOString() ?? null,
        complianceRegime: material === 'BATTERY' ? 'BATTERY_WASTE_RULES' : 'E_WASTE_RULES',
        disclaimer: unavailableReason === 'RATE_STALE'
          ? 'The configured rate is out of date and is not shown. Confirm the current rate with a local operator.'
          : 'No verified current price is available for this material and area yet.'
      };
    }

    const history = (await this.repo.history(material, location, new Date(Date.now() - 30 * 86400000)))
      .filter(point => point.source !== 'SYSTEM' && point.qualityStatus === 'VALIDATED');
    const observationCount = history.length;
    const confidence = observationCount >= 20 ? 'HIGH' : observationCount >= 10 ? 'MEDIUM' : observationCount > 0 ? 'LOW' : 'INSUFFICIENT';
    return {
      available: true,
      materialCategory: material,
      location: price.areaName ?? price.city,
      requestedLocation,
      locationMatched: true,
      sourceContext: 'LOCATION_MATCH',
      priceMin: price.priceMin,
      priceMax: price.priceMax,
      marketPrice: price.marketPrice,
      historicalAverage: price.historicalAverage,
      unit: price.unit,
      source: { type: price.source, organization: price.sourceOrganization, reference: price.sourceReference },
      sourceClassification: price.source,
      qualityStatus: price.qualityStatus,
      ingestedAt: price.ingestedAt.toISOString(),
      observationCount,
      confidence,
      dataAgeDays,
      isDemoData: false,
      trend: this.trend(price.marketPrice, history),
      lastUpdated: price.effectiveAt.toISOString(),
      complianceRegime: material === 'BATTERY' ? 'BATTERY_WASTE_RULES' : 'E_WASTE_RULES',
      disclaimer: 'Indicative buying range; final price is confirmed after inspection.'
    };
  }

  async history(material: MaterialCategory, location: string | undefined, days: number) {
    const points = (await this.repo.history(material, location, new Date(Date.now() - days * 86400000)))
      .filter(point => point.source !== 'SYSTEM' && point.qualityStatus === 'VALIDATED');
    const latest = points.length ? points[points.length - 1] : null;
    const requestedLocation = location?.trim() || null;
    const locationMatched = Boolean(requestedLocation && latest);
    const latestAgeDays = latest ? Math.max(0, Math.floor((Date.now() - latest.effectiveAt.getTime()) / 86400000)) : null;
    const observationCount = points.length;
    const confidence = !latest ? 'INSUFFICIENT' : latestAgeDays != null && latestAgeDays > 7 ? 'LOW' : observationCount >= 20 && latest.qualityStatus === 'VALIDATED' ? 'HIGH' : observationCount >= 10 ? 'MEDIUM' : 'LOW';
    return {
      materialCategory: material,
      location: location ?? null,
      days,
      requestedLocation: requestedLocation,
      locationMatched,
      sourceContext: locationMatched ? 'LOCATION_MATCH' : 'MATERIAL_FALLBACK',
      observationCount,
      confidence,
      freshness: { latestDate: latest?.effectiveAt.toISOString() ?? null, latestAgeDays },
      isDemoData: points.length > 0 && points.every(point => point.source === 'SYSTEM'),
      history: points.map(point => ({
        date: point.effectiveAt.toISOString(),
        marketPrice: point.marketPrice,
        unit: point.unit,
        source: point.source,
        sourceClassification: point.source,
        qualityStatus: point.qualityStatus,
        dataAgeDays: Math.max(0, Math.floor((Date.now() - point.effectiveAt.getTime()) / 86400000)),
        isDemoData: point.source === 'SYSTEM'
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
