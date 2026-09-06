import { describe, expect, it } from 'vitest';
import { assertLotTransition, canTransitionLot } from '../src/services/lotStateMachine.js';

describe('lot state machine', () => {
  it('allows the supported economic lifecycle', () => {
    expect(canTransitionLot('CREATED', 'QUOTE_REQUESTED')).toBe(true);
    expect(canTransitionLot('QUOTE_RECEIVED', 'COLLECTOR_CONFIRMED')).toBe(true);
    expect(canTransitionLot('HANDED_OVER', 'PAID')).toBe(true);
  });

  it('rejects illegal transitions server-side', () => {
    expect(() => assertLotTransition('CREATED', 'PAID')).toThrowError(/cannot move/);
    expect(canTransitionLot('CANCELLED', 'QUOTE_REQUESTED')).toBe(false);
  });
});
