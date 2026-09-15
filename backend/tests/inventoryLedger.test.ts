import { describe, expect, it, vi } from 'vitest';
import { AppError } from '../src/utils/errors.js';
import { assertInventoryInvariant, ownedKg, recordInventoryMovement } from '../src/services/inventoryLedger.js';

describe('inventory ledger invariants', () => {
  it('treats available plus reserved plus sold as owned quantity', () => {
    expect(ownedKg({ availableKg: 12.25, reservedKg: 3.5, soldKg: 4 })).toBe(19.75);
  });

  it('rejects negative or non-finite balances', () => {
    expect(() => assertInventoryInvariant({ id: 'b', kabadiwalaId: 'c', materialCategory: 'PCB', grade: 'A', availableKg: -1, reservedKg: 0, soldKg: 0 })).toThrowError(AppError);
    expect(() => assertInventoryInvariant({ id: 'b', kabadiwalaId: 'c', materialCategory: 'PCB', grade: 'A', availableKg: Number.NaN, reservedKg: 0, soldKg: 0 })).toThrowError(AppError);
  });

  it('writes an immutable movement with before and after quantities', async () => {
    const create = vi.fn();
    const before = { id: 'balance-1', kabadiwalaId: 'collector-1', materialCategory: 'PCB', grade: 'A', availableKg: 10, reservedKg: 0, soldKg: 0 };
    const after = { ...before, availableKg: 7, reservedKg: 3 };
    await recordInventoryMovement({ inventoryMovement: { create } }, before, after, 'RESERVATION', 3, 'POOL_CONTRIBUTION', 'contribution-1');
    expect(create).toHaveBeenCalledWith({ data: expect.objectContaining({ quantityKg: 3, availableBefore: 10, availableAfter: 7, reservedBefore: 0, reservedAfter: 3, sourceId: 'contribution-1' }) });
  });
});
