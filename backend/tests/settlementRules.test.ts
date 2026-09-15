import { describe, expect, it } from 'vitest';
import { evaluateSettlementVariance, riskLevelForFlags } from '../src/services/settlementRules.js';

describe('deterministic settlement fairness rules', () => {
  it('requires review for material, weight, rate, or partial-acceptance changes', () => {
    expect(evaluateSettlementVariance({ quotedWeightKg: 100, actualWeightKg: 100, quotedRatePerKg: 10, finalRatePerKg: 10, acceptedWeightKg: 100, materialMatch: true }).requiresReview).toBe(false);
    expect(evaluateSettlementVariance({ quotedWeightKg: 100, actualWeightKg: 90, quotedRatePerKg: 10, finalRatePerKg: 10, acceptedWeightKg: 90, materialMatch: true })).toMatchObject({ requiresReview: true, ruleCode: 'WEIGHT_OUTSIDE_TOLERANCE' });
    expect(evaluateSettlementVariance({ quotedWeightKg: 100, actualWeightKg: 100, quotedRatePerKg: 10, finalRatePerKg: 8, acceptedWeightKg: 100, materialMatch: true }).ruleCode).toBe('RATE_CHANGED');
    expect(evaluateSettlementVariance({ quotedWeightKg: 100, actualWeightKg: 100, quotedRatePerKg: 10, finalRatePerKg: 10, acceptedWeightKg: 0, materialMatch: false }).ruleCode).toBe('MATERIAL_MISMATCH');
  });

  it('returns the highest deterministic flag severity', () => {
    expect(riskLevelForFlags([{ severity: 'LOW' }, { severity: 'HIGH' }, { severity: 'MEDIUM' }])).toBe('HIGH');
    expect(riskLevelForFlags([])).toBe('NONE');
  });
});
