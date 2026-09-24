import type { MaterialCategory, PrismaClient } from '@prisma/client';

const locationCandidates = (location?: string) => {
  const normalized = location?.trim();
  if (!normalized) return [];
  // Users may enter a locality, city, state and PIN code together. Resolve
  // only exact configured labels, preferring the most specific entered part.
  // Never silently borrow another city's latest price.
  const parts = normalized.split(',').map(part => part.trim()).filter(Boolean);
  return Array.from(new Set([normalized, ...parts].filter(Boolean)));
};

export class PriceRepository {
  constructor(private readonly db: PrismaClient) {}

  async latest(material: MaterialCategory, location?: string) {
    for (const candidate of locationCandidates(location)) {
      const scoped = await this.db.price.findFirst({
        where: { materialCategory: material, OR: [{ areaName: { equals: candidate, mode: 'insensitive' } }, { city: { equals: candidate, mode: 'insensitive' } }] },
        orderBy: { effectiveAt: 'desc' }
      });
      if (scoped) return scoped;
    }

    return null;
  }

  async history(material: MaterialCategory, location: string | undefined, since: Date) {
    for (const candidate of locationCandidates(location)) {
      const scoped = await this.db.priceHistory.findMany({
        where: { materialCategory: material, effectiveAt: { gte: since }, OR: [{ areaName: { equals: candidate, mode: 'insensitive' } }, { city: { equals: candidate, mode: 'insensitive' } }] },
        orderBy: { effectiveAt: 'asc' }
      });
      if (scoped.length) return scoped;
    }

    return [];
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
