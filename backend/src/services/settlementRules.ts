export type SettlementVariance = {
  weightDelta: number;
  rateDelta: number;
  requiresReview: boolean;
  ruleCode: string | null;
};

export function evaluateSettlementVariance(input: {
  quotedWeightKg: number;
  actualWeightKg: number;
  quotedRatePerKg: number;
  finalRatePerKg: number;
  acceptedWeightKg: number;
  materialMatch: boolean;
}) : SettlementVariance {
  const weightDelta = input.quotedWeightKg > 0
    ? Math.abs(input.actualWeightKg - input.quotedWeightKg) / input.quotedWeightKg
    : 0;
  const rateDelta = input.quotedRatePerKg > 0
    ? Math.abs(input.finalRatePerKg - input.quotedRatePerKg) / input.quotedRatePerKg
    : 0;
  const requiresReview = !input.materialMatch || weightDelta > 0.05 || rateDelta > 0.10 || input.acceptedWeightKg < input.actualWeightKg;
  const ruleCode = !requiresReview
    ? null
    : !input.materialMatch
      ? 'MATERIAL_MISMATCH'
      : input.acceptedWeightKg < input.actualWeightKg
        ? 'PARTIAL_ACCEPTANCE'
        : weightDelta > 0.05
          ? 'WEIGHT_OUTSIDE_TOLERANCE'
          : rateDelta > 0.10
            ? 'RATE_CHANGED'
            : 'SETTLEMENT_CHANGED';
  return { weightDelta, rateDelta, requiresReview, ruleCode };
}

export function riskLevelForFlags(flags: Array<{ severity: string }>) {
  const rank: Record<string, number> = { LOW: 1, MEDIUM: 2, HIGH: 3, CRITICAL: 4 };
  return flags.reduce((highest, flag) => (rank[flag.severity] ?? 0) > (rank[highest] ?? 0) ? flag.severity : highest, 'NONE');
}
