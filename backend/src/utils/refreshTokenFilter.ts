import type { Prisma } from '@prisma/client';

/**
 * MongoDB distinguishes an explicit null from an absent optional field.
 * Existing RefreshToken documents may have no revokedAt field at all, so a
 * plain `revokedAt: null` filter would fail to match those still-valid tokens.
 */
export const unrevokedRefreshTokenWhere = (
  scope: Prisma.RefreshTokenWhereInput
): Prisma.RefreshTokenWhereInput => ({
  ...scope,
  OR: [
    { revokedAt: null },
    { revokedAt: { isSet: false } }
  ]
});
