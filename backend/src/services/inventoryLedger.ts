import { AppError } from '../utils/errors.js';

type Balance = {
  id: string;
  kabadiwalaId: string;
  materialCategory: string;
  grade: string;
  availableKg: number;
  reservedKg: number;
  soldKg: number;
};

type Store = any;

export function assertInventoryInvariant(balance: Balance) {
  const values = [balance.availableKg, balance.reservedKg, balance.soldKg];
  if (values.some(value => !Number.isFinite(value) || value < -0.000001)) {
    throw new AppError('INTERNAL_SERVER_ERROR', 'Inventory invariant violated', 500, { code: 'INVENTORY_INVARIANT_VIOLATION' });
  }
}

export async function recordInventoryMovement(
  tx: Store,
  before: Balance,
  after: Balance,
  movementType: string,
  quantityKg: number,
  sourceType: string,
  sourceId: string,
  metadata?: Record<string, unknown>
) {
  assertInventoryInvariant(before);
  assertInventoryInvariant(after);
  await tx.inventoryMovement.create({
    data: {
      inventoryBalanceId: after.id,
      kabadiwalaId: after.kabadiwalaId,
      movementType,
      quantityKg,
      availableBefore: before.availableKg,
      availableAfter: after.availableKg,
      reservedBefore: before.reservedKg,
      reservedAfter: after.reservedKg,
      soldBefore: before.soldKg,
      soldAfter: after.soldKg,
      sourceType,
      sourceId,
      metadata: metadata ?? undefined
    }
  });
}

export function ownedKg(balance: Pick<Balance, 'availableKg' | 'reservedKg' | 'soldKg'>) {
  return Number((balance.availableKg + balance.reservedKg + balance.soldKg).toFixed(3));
}
