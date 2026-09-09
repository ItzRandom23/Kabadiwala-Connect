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

  constructor(private readonly db: PrismaClient) {}

  private verifiedWhere(
    opts: Pick<ListOptions, 'location' | 'material' | 'availability'>
  ): Prisma.RecyclerWhereInput {
    return {
      authorizationStatus: 'VERIFIED',
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
      facilityLocation: {
        latitude: recycler.latitude,
        longitude: recycler.longitude,
        address: recycler.address,
        areaName: recycler.areaName
      },
      authorizationStatus: recycler.authorizationStatus,
      authorizationDetails: {
        authority: recycler.authorizationAuthority,
        validUntil: recycler.authorizationValidUntil
      },
      materialsAccepted: recycler.materials.map((material: any) => ({
        category: material.category,
        subcategories: material.subcategories,
        minAcceptableWeight: material.minAcceptableWeight,
        maxAcceptableWeight: material.maxAcceptableWeight
      })),
      rates: recycler.rates.map((rate: any) => ({
        materialCategory: rate.materialCategory,
        pricePerKg: rate.pricePerKg,
        updatedAt: rate.updatedAt
      })),
      pickupAvailability: recycler.pickupAvailability ?? 'FLEXIBLE',
      serviceArea: { maxPickupDistanceKm: recycler.maxPickupDistanceKm },
      operatingHours: recycler.operatingHours,
      averageHandoverTime: recycler.averageHandoverTime,
      rating: recycler.rating,
      reviewCount: recycler.reviewCount,
      completedHandovers: recycler._count?.handovers ?? null,
      lastUpdated: recycler.updatedAt,
      contact: {
        phone: recycler.phone,
        email: recycler.email,
        alternatePhone: recycler.alternatePhone
      },
      ...(distanceKm === undefined ? {} : { distanceKm: Number(distanceKm.toFixed(1)) })
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
      .filter(({ recycler, distanceKm }) => opts.radius === undefined
        || distanceKm === undefined
        || distanceKm <= Math.min(opts.radius, recycler.maxPickupDistanceKm));

    candidates.sort((left, right) => {
      if (opts.sort === 'rate') {
        const rateDifference = (right.recycler.rates[0]?.pricePerKg ?? 0)
          - (left.recycler.rates[0]?.pricePerKg ?? 0);
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
      where: { id, authorizationStatus: 'VERIFIED' },
      include: this.include
    });
    if (!recycler) {
      throw new AppError('NOT_FOUND', 'Recycler not found', 404, { code: 'RECYCLER_NOT_FOUND' });
    }
    return this.publicView(recycler);
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
        return {
          recycler: this.publicView(recycler, distanceKm),
          offeredRatePerKg: rate?.pricePerKg ?? null,
          matchScore,
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
    const recycler = await this.db.recycler.findUnique({ where: { id }, include: this.include });
    if (!recycler) {
      throw new AppError('NOT_FOUND', 'Recycler not found', 404, { code: 'RECYCLER_NOT_FOUND' });
    }
    return this.publicView(recycler);
  }

  async authorize(
    id: string,
    actorId: string,
    status: RecyclerAuthorizationStatus,
    reason?: string
  ) {
    return this.db.$transaction(async transaction => {
      const previous = await transaction.recycler.findUnique({ where: { id } });
      if (!previous) {
        throw new AppError('NOT_FOUND', 'Recycler not found', 404, { code: 'RECYCLER_NOT_FOUND' });
      }
      const recycler = await transaction.recycler.update({
        where: { id },
        data: { authorizationStatus: status },
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
      return this.publicView(recycler);
    });
  }
}
