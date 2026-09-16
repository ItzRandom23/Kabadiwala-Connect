import type { MaterialCategory, PrismaClient } from '@prisma/client';

const locationCandidates = (location?: string) => {
  const normalized = location?.trim();
  if (!normalized) return [];
  const parts = normalized.split(',').map(part => part.trim()).filter(Boolean);
  const city = parts[parts.length - 1];
  return Array.from(new Set([normalized, city].filter(Boolean) as string[]));
};

export class PriceRepository {
  constructor(private readonly db: PrismaClient) {}

  async latest(material: MaterialCategory, location?: string) {
    for (const candidate of locationCandidates(location)) {
      const scoped = await this.db.price.findFirst({
        where: { materialCategory: material, OR: [{ areaName: candidate }, { city: candidate }] },
        orderBy: { effectiveAt: 'desc' }
      });
      if (scoped) return scoped;
    }

    // A new area may not have a local row yet. Use the latest known rate for
    // the same material rather than turning the prices screen into a 404.
    // The service labels the response as an indicative reference when this
    // fallback is outside the requested location.
    return this.db.price.findFirst({ where: { materialCategory: material }, orderBy: { effectiveAt: 'desc' } });
  }

  async history(material: MaterialCategory, location: string | undefined, since: Date) {
    for (const candidate of locationCandidates(location)) {
      const scoped = await this.db.priceHistory.findMany({
        where: { materialCategory: material, effectiveAt: { gte: since }, OR: [{ areaName: candidate }, { city: candidate }] },
        orderBy: { effectiveAt: 'asc' }
      });
      if (scoped.length) return scoped;
    }

    return this.db.priceHistory.findMany({
      where: { materialCategory: material, effectiveAt: { gte: since } },
      orderBy: { effectiveAt: 'asc' }
    });
  }

  get(id: string) {
    return this.db.price.findUnique({ where: { id } });
  }

  async update(id: string, actorId: string, data: { priceMin: number; priceMax: number; marketPrice: number; reason?: string }) {
    return this.db.$transaction(async tx => {
      const old = await tx.price.findUnique({ where: { id } });
      if (!old) return null;
      const { reason: _reason, ...values } = data;
      const updated = await tx.price.update({ where: { id }, data: { ...values, source: 'ADMIN', qualityStatus: 'VALIDATED', ingestedAt: new Date(), effectiveAt: new Date() } });
      await tx.priceHistory.create({ data: { priceId: id, materialCategory: updated.materialCategory, city: updated.city, areaName: updated.areaName, priceMin: updated.priceMin, priceMax: updated.priceMax, marketPrice: updated.marketPrice, unit: updated.unit, source: updated.source, sourceOrganization: updated.sourceOrganization, sourceReference: updated.sourceReference, ingestedAt: updated.ingestedAt, qualityStatus: updated.qualityStatus, effectiveAt: updated.effectiveAt } });
      await tx.priceAudit.create({ data: { priceId: id, actorId, actorRole: 'ADMIN', oldValues: { priceMin: old.priceMin, priceMax: old.priceMax, marketPrice: old.marketPrice }, newValues: { priceMin: updated.priceMin, priceMax: updated.priceMax, marketPrice: updated.marketPrice }, reason: _reason } });
      return updated;
    });
  }
}
