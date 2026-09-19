import type {
  MaterialCategory,
  PickupAvailability,
  Prisma,
  PrismaClient,
  RecyclerAuthorizationStatus
} from '@prisma/client';
import { AppError } from '../utils/errors.js';

const EARTH_RADIUS_KM = 6371;
const DISTANCE_FALLBACK_KM = Number.POSITIVE_INFINITY;

/**
 * Expire time-bounded Recycler authorizations without waiting for a request
 * from that Recycler. The conditional update makes this safe to run more
 * than once and preserves an operator-visible lifecycle audit.
 */
export async function expireStaleRecyclerAuthorizations(db: PrismaClient, now = new Date()) {
  const candidates = await db.recycler.findMany({
    where: { authorizationStatus: 'VERIFIED', authorizationValidUntil: { not: null, lte: now } },
    select: { id: true, authorizationValidUntil: true }
  });
  let expired = 0;
  for (const candidate of candidates) {
    const changed = await db.$transaction(async tx => {
      const updated = await tx.recycler.updateMany({
        where: { id: candidate.id, authorizationStatus: 'VERIFIED', authorizationValidUntil: candidate.authorizationValidUntil },
        data: { authorizationStatus: 'EXPIRED' }
      });
      if (!updated.count) return false;
      await tx.recyclerAuthorizationAudit.create({
        data: {
          recyclerId: candidate.id,
          actorId: 'SYSTEM_FRESHNESS_SWEEP',
          previousStatus: 'VERIFIED',
          newStatus: 'EXPIRED',
          reason: 'Authorization validity period elapsed'
        }
      });
      const notification = await tx.notificationEvent.create({
        data: {
          accountId: candidate.id,
          type: 'RECYCLER_AUTHORIZATION_EXPIRED',
          title: 'Authorization review required',
          body: 'Your Recycler authorization has expired. Submit current evidence before using formal procurement actions.',
          route: 'recycler/profile'
        }
      });
      try {
        await tx.notificationDelivery?.upsert({
          where: { notificationId_channel: { notificationId: notification.id, channel: 'SMS' } },
          update: {},
          create: { notificationId: notification.id, accountId: candidate.id, channel: 'SMS', status: 'PENDING', attempts: 0, nextAttemptAt: new Date() }
        });
      } catch {
        // SMS outbox persistence must never roll back an authorization expiry.
      }
      return true;
    });
    if (changed) expired += 1;
  }
  return expired;
}

const haversineDistanceKm = (
  fromLatitude: number,
  fromLongitude: number,
  toLatitude: number,
  toLongitude: number
) => {
  const latitudeDelta = (toLatitude - fromLatitude) * Math.PI / 180;
  const longitudeDelta = (toLongitude - fromLongitude) * Math.PI / 180;
  const haversine = Math.sin(latitudeDelta / 2) ** 2
    + Math.cos(fromLatitude * Math.PI / 180)
    * Math.cos(toLatitude * Math.PI / 180)
    * Math.sin(longitudeDelta / 2) ** 2;
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(haversine));
};

type ListOptions = {
  location?: string;
  latitude?: number;
  longitude?: number;
  radius?: number;
  material?: MaterialCategory;
  availability?: PickupAvailability;
  sort: 'proximity' | 'rate';
  page: number;
  limit: number;
};

export class RecyclerService {
  private readonly include = {
    materials: true,
    rates: true,
    _count: { select: { handovers: true } }
  } as const;

  private readonly ownerInclude = {
    ...this.include,
    authorizationAudits: {
      orderBy: { createdAt: 'desc' as const },
      take: 1,
      select: { reason: true, newStatus: true, createdAt: true }
    }
  } as const;

  constructor(private readonly db: PrismaClient) {}

  private verifiedWhere(
    opts: Pick<ListOptions, 'location' | 'material' | 'availability'>
  ): Prisma.RecyclerWhereInput {
    return {
      authorizationStatus: 'VERIFIED',
      OR: [{ authorizationValidUntil: null }, { authorizationValidUntil: { gte: new Date() } }],
      ...(opts.location?.trim()
        ? { areaName: { contains: opts.location.trim(), mode: 'insensitive' } }
        : {}),
      ...(opts.material ? { materials: { some: { category: opts.material } } } : {}),
      ...(opts.availability ? { pickupAvailability: opts.availability } : {})
    };
  }

  private publicView(recycler: any, distanceKm?: number) {
    return {
      id: recycler.id,
      name: recycler.name,
      // Discovery is intentionally area-level. Exact coordinates and street
      // address are reserved for an authorized operational workflow.
      facilityLocation: { latitude: null, longitude: null, address: null, areaName: recycler.areaName },
      authorizationStatus: recycler.authorizationStatus,
      authorizationDetails: {
        authority: recycler.authorizationAuthority,
        type: recycler.authorizationType,
        verifiedAt: recycler.verifiedAt,
        validUntil: recycler.authorizationValidUntil
      },
      materialsAccepted: recycler.materials.map((material: any) => ({
        category: material.category,
        subcategories: material.subcategories,
        acceptedGrades: material.acceptedGrades ?? [],
        minAcceptableWeight: material.minAcceptableWeight,
        maxAcceptableWeight: material.maxAcceptableWeight
      })),
      rates: recycler.rates.map((rate: any) => ({
        materialCategory: rate.materialCategory,
        pricePerKg: rate.pricePerKg,
        unit: rate.unit,
        sourceReference: rate.sourceReference,
        qualityStatus: rate.qualityStatus,
        effectiveAt: rate.effectiveAt,
        updatedAt: rate.updatedAt
      })),
      pickupAvailability: recycler.pickupAvailability ?? 'FLEXIBLE',
      serviceArea: { maxPickupDistanceKm: recycler.maxPickupDistanceKm, logisticsCostPerKm: recycler.logisticsCostPerKm ?? null },
      pickupIncluded: recycler.pickupIncluded ?? false,
      pickupFee: recycler.pickupFee ?? null,
      operatingHours: recycler.operatingHours,
      averageHandoverTime: recycler.averageHandoverTime,
      rating: recycler.rating,
      reviewCount: recycler.reviewCount,
      completedHandovers: recycler._count?.handovers ?? null,
      lastUpdated: recycler.updatedAt,
      ...(distanceKm === undefined ? {} : { distanceKm: Number(distanceKm.toFixed(1)) })
    };
  }

  private ownerView(recycler: any, distanceKm?: number) {
    return {
      ...this.publicView(recycler, distanceKm),
      facilityLocation: {
        latitude: recycler.latitude,
        longitude: recycler.longitude,
        address: recycler.address,
        areaName: recycler.areaName
      },
      contact: {
        phone: recycler.phone,
        email: recycler.email,
        alternatePhone: recycler.alternatePhone
      },
      authorizationDetails: {
        authority: recycler.authorizationAuthority,
        type: recycler.authorizationType,
        registrationNumber: recycler.licenseNumber,
        evidenceReference: recycler.authorizationEvidenceReference,
        verificationSource: recycler.verificationSource,
        verifiedBy: recycler.verifiedBy,
        verifiedAt: recycler.verifiedAt,
        validUntil: recycler.authorizationValidUntil,
        reviewReason: recycler.authorizationAudits?.[0]?.newStatus === 'REJECTED' ? recycler.authorizationAudits[0].reason : null
      }
    };
  }

  async list(opts: ListOptions) {
    const hasCoordinates = opts.latitude !== undefined && opts.longitude !== undefined;
    const offset = (opts.page - 1) * opts.limit;

    // Proximity without coordinates is deterministic by id. Let MongoDB filter,
    // count and paginate rather than hydrating the entire recycler catalogue.
    if (!hasCoordinates && opts.sort === 'proximity') {
      const where = this.verifiedWhere(opts);
      const [total, recyclers] = await Promise.all([
        this.db.recycler.count({ where }),
        this.db.recycler.findMany({
          where,
          include: this.include,
          orderBy: { id: 'asc' },
          skip: offset,
          take: opts.limit
        })
      ]);
      return {
        items: recyclers.map(recycler => this.publicView(recycler)),
        pagination: {
          page: opts.page,
          limit: opts.limit,
          total,
          totalPages: Math.ceil(total / opts.limit)
        }
      };
    }

    // Coordinate distance and relation-based rate sorting cannot be expressed
    // portably through Prisma/MongoDB, but cheap scalar/relation filters still can.
    const where = this.verifiedWhere({
      location: hasCoordinates ? undefined : opts.location,
      material: opts.material,
      availability: opts.availability
    });
    const recyclers = await this.db.recycler.findMany({ where, include: this.include });
    const candidates = recyclers
      .map(recycler => {
        const distanceKm = hasCoordinates
          && recycler.latitude != null
          && recycler.longitude != null
          ? haversineDistanceKm(
            opts.latitude!,
            opts.longitude!,
            recycler.latitude,
            recycler.longitude
          )
          : undefined;
        return { recycler, distanceKm };
      })
      .filter(({ recycler, distanceKm }) => (!hasCoordinates || distanceKm !== undefined)
        && (opts.radius === undefined || distanceKm === undefined || distanceKm <= Math.min(opts.radius, recycler.maxPickupDistanceKm)));

    candidates.sort((left, right) => {
      if (opts.sort === 'rate') {
        const rightRate = opts.material
          ? right.recycler.rates.find((rate: any) => rate.materialCategory === opts.material)?.pricePerKg
          : Math.max(...right.recycler.rates.map((rate: any) => rate.pricePerKg), 0);
        const leftRate = opts.material
          ? left.recycler.rates.find((rate: any) => rate.materialCategory === opts.material)?.pricePerKg
          : Math.max(...left.recycler.rates.map((rate: any) => rate.pricePerKg), 0);
        const rateDifference = (rightRate ?? 0) - (leftRate ?? 0);
        if (rateDifference !== 0) return rateDifference;
      } else {
        const distanceDifference = (left.distanceKm ?? DISTANCE_FALLBACK_KM)
          - (right.distanceKm ?? DISTANCE_FALLBACK_KM);
        if (distanceDifference !== 0) return distanceDifference;
      }
      return left.recycler.id.localeCompare(right.recycler.id);
    });

    const total = candidates.length;
    return {
      items: candidates
        .slice(offset, offset + opts.limit)
        .map(({ recycler, distanceKm }) => this.publicView(recycler, distanceKm)),
      pagination: {
        page: opts.page,
        limit: opts.limit,
        total,
        totalPages: Math.ceil(total / opts.limit)
      }
    };
  }

  async detail(id: string) {
    const recycler = await this.db.recycler.findFirst({
      where: { id, authorizationStatus: 'VERIFIED', OR: [{ authorizationValidUntil: null }, { authorizationValidUntil: { gte: new Date() } }] },
      include: this.include
    });
    if (!recycler) {
      throw new AppError('NOT_FOUND', 'Recycler not found', 404, { code: 'RECYCLER_NOT_FOUND' });
    }
    return this.publicView(recycler);
  }

  async selfProfile(id: string) {
    const recycler = await this.db.recycler.findUnique({ where: { id }, include: this.ownerInclude });
    if (!recycler) throw new AppError('NOT_FOUND', 'Recycler not found', 404, { code: 'RECYCLER_NOT_FOUND' });
    return this.ownerView(recycler);
  }

  async submitVerificationRequest(
    id: string,
    input: {
      authority: string;
      registrationNumber: string;
      authorizationType: string;
      evidenceReference: string;
      verificationSource: string;
      validUntil: Date;
    }
  ) {
    return this.db.$transaction(async transaction => {
      const previous = await transaction.recycler.findUnique({ where: { id } });
      if (!previous) {
        throw new AppError('NOT_FOUND', 'Recycler not found', 404, { code: 'RECYCLER_NOT_FOUND' });
      }
      if (previous.authorizationStatus === 'SUSPENDED') {
        throw new AppError('CONFLICT', 'A suspended recycler must contact support before resubmitting verification', 409, { code: 'RECYCLER_VERIFICATION_SUSPENDED' });
      }
      if (previous.authorizationStatus === 'VERIFIED') {
        throw new AppError('CONFLICT', 'This recycler profile is already verified', 409, { code: 'RECYCLER_ALREADY_VERIFIED' });
      }

      const recycler = await transaction.recycler.update({
        where: { id },
        data: {
          authorizationStatus: 'PENDING',
          authorizationAuthority: input.authority,
          licenseNumber: input.registrationNumber,
          authorizationType: input.authorizationType,
          authorizationEvidenceReference: input.evidenceReference,
          verificationSource: input.verificationSource,
          authorizationValidUntil: input.validUntil,
          verifiedAt: null,
          verifiedBy: null
        },
        include: this.include
      });
      await transaction.recyclerAuthorizationAudit.create({
        data: {
          recyclerId: id,
          actorId: id,
          previousStatus: previous.authorizationStatus,
          newStatus: 'PENDING',
          reason: 'Recycler submitted authorization evidence for review'
        }
      });
      return this.ownerView(recycler);
    });
  }

  async updateProfile(id: string, input: { pickupAvailability?: PickupAvailability; maxPickupDistanceKm?: number; logisticsCostPerKm?: number; pickupFee?: number; pickupIncluded?: boolean; operatingHours?: Prisma.InputJsonValue }) {
    const recycler = await this.db.recycler.update({ where: { id }, data: input, include: this.include }).catch(error => {
      if (error?.code === 'P2025') throw new AppError('NOT_FOUND', 'Recycler not found', 404, { code: 'RECYCLER_NOT_FOUND' });
      throw error;
    });
    return this.ownerView(recycler);
  }

  async updateRates(id: string, rates: Array<{ materialCategory: MaterialCategory; pricePerKg: number }>) {
    const exists = await this.db.recycler.findUnique({ where: { id } });
    if (!exists) throw new AppError('NOT_FOUND', 'Recycler not found', 404, { code: 'RECYCLER_NOT_FOUND' });
    await this.db.$transaction(async tx => {
      const categories = rates.map(rate => rate.materialCategory);
      await tx.recyclerRate.deleteMany({ where: { recyclerId: id, ...(categories.length ? { materialCategory: { notIn: categories } } : {}) } });
      for (const rate of rates) {
        await tx.recyclerRate.upsert({
          where: { recyclerId_materialCategory: { recyclerId: id, materialCategory: rate.materialCategory } },
          create: { recyclerId: id, materialCategory: rate.materialCategory, pricePerKg: rate.pricePerKg, unit: 'KILOGRAM', qualityStatus: 'UNVERIFIED', effectiveAt: new Date() },
          update: { pricePerKg: rate.pricePerKg, effectiveAt: new Date() }
        });
      }
    });
    return this.selfProfile(id);
  }

  async match(lotId: string, collectorId: string) {
    const [lot, collector] = await Promise.all([
      this.db.lot.findFirst({ where: { id: lotId, collectorId } }),
      this.db.collector.findUnique({ where: { id: collectorId } })
    ]);
    if (!lot) {
      throw new AppError('NOT_FOUND', 'Lot not found', 404, { code: 'LOT_NOT_FOUND' });
    }

    // Discard recyclers that cannot accept the material before loading their
    // relations. Weight and distance constraints remain in memory.
    const recyclers = await this.db.recycler.findMany({
      where: this.verifiedWhere({ material: lot.materialCategory }),
      include: this.include
    });
    const matches = recyclers
      .map(recycler => {
        const material = recycler.materials.find(item => item.category === lot.materialCategory);
        if (!material) return null;
        if (material.minAcceptableWeight && lot.weight < material.minAcceptableWeight) return null;
        if (material.maxAcceptableWeight && lot.weight > material.maxAcceptableWeight) return null;

        const distanceKm = collector?.latitude != null
          && collector.longitude != null
          && recycler.latitude != null
          && recycler.longitude != null
          ? haversineDistanceKm(
            collector.latitude,
            collector.longitude,
            recycler.latitude,
            recycler.longitude
          )
          : undefined;
        if (distanceKm !== undefined && distanceKm > recycler.maxPickupDistanceKm) return null;

        const rate = recycler.rates.find(item => item.materialCategory === lot.materialCategory);
        const distancePoints = distanceKm === undefined ? 0 : distanceKm < 10 ? 20 : distanceKm <= 25 ? 10 : 0;
        const ratingPoints = recycler.rating && recycler.rating > 4 ? 5 : 0;
        const matchScore = 50 + distancePoints + (rate ? 20 : 0) + ratingPoints;
        const reasons = [
          'Current platform authorization is verified.',
          `Accepts ${lot.materialCategory}.`,
          ...(rate ? [`Current indicative rate is ₹${rate.pricePerKg}/kg.`] : ['No current recycler rate was supplied.']),
          ...(distanceKm === undefined ? ['Distance is unavailable from the saved coordinates.'] : [`${distanceKm.toFixed(1)} km from the collector profile.`]),
          ...(recycler.pickupAvailability === 'TODAY' || recycler.pickupAvailability === 'THIS_WEEK' ? ['Pickup availability is listed.'] : ['Pickup timing is flexible and must be confirmed.']),
          ...(recycler.rating ? [`${recycler.rating.toFixed(1)}/5 from ${recycler.reviewCount} verified reviews.`] : ['No verified recycler rating is available.'])
        ];
        return {
          recycler: this.publicView(recycler, distanceKm),
          offeredRatePerKg: rate?.pricePerKg ?? null,
          matchScore,
          score: matchScore,
          reasons,
          whyThisMatch: reasons,
          explanation: {
            materialMatch: true,
            distancePoints,
            availabilityPoints: 10,
            ratePoints: rate ? 20 : 0,
            authorizationPoints: 10,
            ratingPoints
          }
        };
      })
      .filter(match => match !== null)
      .sort((left, right) => right.matchScore - left.matchScore
        || left.recycler.id.localeCompare(right.recycler.id))
      .slice(0, 10);

    return { lotId, matches };
  }

  async adminList() {
    return this.db.recycler.findMany({ include: this.include });
  }

  async adminDetail(id: string) {
    const recycler = await this.db.recycler.findUnique({ where: { id }, include: this.ownerInclude });
    if (!recycler) {
      throw new AppError('NOT_FOUND', 'Recycler not found', 404, { code: 'RECYCLER_NOT_FOUND' });
    }
    return this.ownerView(recycler);
  }

  async authorize(
    id: string,
    actorId: string,
    status: RecyclerAuthorizationStatus,
    reason?: string,
    details?: { authority?: string; registrationNumber?: string; authorizationType?: string; evidenceReference?: string; verificationSource?: string; validUntil?: Date }
  ) {
    if (status === 'VERIFIED') {
      const complete = details?.authority && details.registrationNumber && details.authorizationType
        && details.evidenceReference && details.verificationSource && details.validUntil;
      if (!complete || details.validUntil! <= new Date()) {
        throw new AppError('VALIDATION_ERROR', 'Verified recyclers require complete, current authorization evidence', 422, { code: 'VERIFICATION_EVIDENCE_REQUIRED' });
      }
    }
    return this.db.$transaction(async transaction => {
      const previous = await transaction.recycler.findUnique({ where: { id } });
      if (!previous) {
        throw new AppError('NOT_FOUND', 'Recycler not found', 404, { code: 'RECYCLER_NOT_FOUND' });
      }
      const recycler = await transaction.recycler.update({
        where: { id },
        data: {
          authorizationStatus: status,
          ...(details?.authority ? { authorizationAuthority: details.authority } : {}),
          ...(details?.registrationNumber ? { licenseNumber: details.registrationNumber } : {}),
          ...(details?.authorizationType ? { authorizationType: details.authorizationType } : {}),
          ...(details?.evidenceReference ? { authorizationEvidenceReference: details.evidenceReference } : {}),
          ...(details?.verificationSource ? { verificationSource: details.verificationSource } : {}),
          ...(details?.validUntil ? { authorizationValidUntil: details.validUntil } : {}),
          ...(status === 'VERIFIED' ? { verifiedAt: new Date(), verifiedBy: actorId } : {}),
          ...(status !== 'VERIFIED' ? { verifiedAt: null, verifiedBy: null } : {})
        },
        include: this.include
      });
      await transaction.recyclerAuthorizationAudit.create({
        data: {
          recyclerId: id,
          actorId,
          previousStatus: previous.authorizationStatus,
          newStatus: status,
          reason
        }
      });
      return this.ownerView(recycler);
    });
  }
}
