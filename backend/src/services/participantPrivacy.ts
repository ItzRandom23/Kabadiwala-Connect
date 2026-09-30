/** Shared route/sync projection: a contributor sees only their material and payout. */
export function poolHandoverForCollector(handover: any, collectorId: string, contribution: any | null) {
  const isOwner = handover.collectorId === collectorId;
  const own = contribution?.collectorId === collectorId ? contribution : null;
  const weight = isOwner ? handover.quotedWeightKg : (own?.quantityKg ?? 0);
  const rate = isOwner ? handover.quotedRatePerKg : (own?.expectedRatePerKg ?? 0);
  const accepted = isOwner ? handover.finalAcceptedKg : (own?.finalAcceptedKg ?? null);
  const value = isOwner ? handover.finalValue : (own?.finalPayout ?? null);
  return {
    id: handover.id, bulkLotId: handover.bulkLotId, poolId: handover.poolId,
    collectorId: isOwner ? handover.collectorId : '', recyclerId: handover.recyclerId,
    referenceId: handover.referenceId, qrCodeData: isOwner ? handover.qrCodeData : null,
    materialCategory: handover.materialCategory, quotedWeightKg: weight, quotedRatePerKg: rate,
    quotedValue: isOwner ? handover.quotedValue : Number((weight * rate).toFixed(2)),
    finalAcceptedKg: accepted, finalRejectedKg: isOwner ? handover.finalRejectedKg : null,
    finalRatePerKg: isOwner ? handover.finalRatePerKg : (accepted && value != null ? Number((value / accepted).toFixed(2)) : null),
    finalValue: value, status: handover.status, collectorConfirmedAt: handover.collectorConfirmedAt,
    recyclerConfirmedAt: handover.recyclerConfirmedAt, preparedAt: handover.preparedAt,
    expiresAt: handover.expiresAt, createdAt: handover.createdAt, updatedAt: handover.updatedAt,
    reviewReason: isOwner ? handover.reviewReason : null, reviewEvidence: isOwner ? handover.reviewEvidence : null,
    payments: (handover.payments ?? []).filter((payment: any) => payment.collectorId === collectorId)
  };
}
