import { AppError } from '../utils/errors.js';

export type SourceListingAllocationType = 'BULK_LOT' | 'POOL_CONTRIBUTION';

/**
 * Claim completed household listings inside the transaction that creates the
 * formal stock record. The listing documents are the shared write point for
 * BulkLot and PoolContribution, so concurrent transactions conflict instead
 * of allocating the same source twice.
 */
export async function claimSourceListings(
  tx: any,
  sourceListingIds: string[],
  allocationType: SourceListingAllocationType,
  allocationId: string
) {
  if (!sourceListingIds.length) return;
  const uniqueIds = [...new Set(sourceListingIds)];
  if (uniqueIds.length !== sourceListingIds.length) {
    throw new AppError('VALIDATION_ERROR', 'sourceListingIds must be unique', 422, { code: 'DUPLICATE_SOURCE_LISTING' });
  }

  const claimed = await tx.householdListing.updateMany({
    where: {
      id: { in: uniqueIds },
      OR: [
        { formalAllocationId: null },
        { formalAllocationId: { isSet: false } }
      ]
    },
    data: { formalAllocationId: allocationId, formalAllocationType: allocationType }
  });
  if (claimed.count !== uniqueIds.length) {
    throw new AppError('CONFLICT', 'A source listing is already allocated to formal stock', 409, { code: 'SOURCE_LISTING_ALREADY_ALLOCATED' });
  }
}

/** Release only claims owned by the exact record being cancelled/released. */
export async function releaseSourceListings(
  tx: any,
  sourceListingIds: string[],
  allocationType: SourceListingAllocationType,
  allocationId: string
) {
  if (!sourceListingIds.length) return;
  await tx.householdListing.updateMany({
    where: {
      id: { in: [...new Set(sourceListingIds)] },
      formalAllocationId: allocationId,
      formalAllocationType: allocationType
    },
    data: { formalAllocationId: null, formalAllocationType: null }
  });
}
