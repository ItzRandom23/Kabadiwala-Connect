import type { LotStatus } from '@prisma/client';
import { AppError } from '../utils/errors.js';

const allowed: Record<LotStatus, LotStatus[]> = {
  CREATED: ['QUOTE_REQUESTED', 'CANCELLED'],
  QUOTE_REQUESTED: ['QUOTE_RECEIVED', 'CANCELLED'],
  QUOTE_RECEIVED: ['COLLECTOR_CONFIRMED', 'CANCELLED'],
  COLLECTOR_CONFIRMED: ['HANDED_OVER', 'CANCELLED', 'DISPUTED'],
  RECYCLER_CONFIRMED: ['HANDED_OVER', 'DISPUTED'],
  HANDED_OVER: ['PAID', 'DISPUTED'],
  PAID: ['DISPUTED'],
  CANCELLED: [],
  DISPUTED: ['COLLECTOR_CONFIRMED', 'HANDED_OVER', 'PAID']
};

export function assertLotTransition(from: LotStatus, to: LotStatus) {
  if (!allowed[from]?.includes(to)) throw new AppError('CONFLICT', `Lot cannot move from ${from} to ${to}`, 409, { code: 'ILLEGAL_LOT_TRANSITION', from, to });
}

export function canTransitionLot(from: LotStatus, to: LotStatus) { return allowed[from]?.includes(to) ?? false; }
