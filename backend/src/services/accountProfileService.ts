import type { PrismaClient } from '@prisma/client';
import { AppError } from '../utils/errors.js';

export type AccountProfileUpdate = {
  displayName?: string;
  email?: string | null;
  areaName?: string;
  address?: string;
  latitude?: number | null;
  longitude?: number | null;
  preferredLanguage?: string;
};

const normalizedEmail = (value?: string | null) => value?.trim().toLowerCase() || null;

/**
 * Account settings are deliberately scoped by the token identity.  This
 * service never accepts a profile id from the client, so editing one account
 * cannot become an IDOR primitive.
 */
export class AccountProfileService {
  constructor(private readonly db: PrismaClient) {}

  async update(profileId: string, role: 'HOUSEHOLD' | 'COLLECTOR' | 'RECYCLER', input: AccountProfileUpdate) {
    const email = input.email === undefined ? undefined : normalizedEmail(input.email);
    return this.db.$transaction(async tx => {
      const user = role === 'RECYCLER'
        ? await tx.user.findFirst({ where: { recyclerProfileId: profileId, role } })
        : await tx.user.findFirst({ where: { collectorProfileId: profileId, role } });
      if (!user) throw new AppError('NOT_FOUND', 'Account profile not found', 404, { code: 'PROFILE_NOT_FOUND' });

      if (email && email !== user.email) {
        const [emailUser, collectorOwner, recyclerOwner] = await Promise.all([
          tx.user.findFirst({ where: { email } }),
          tx.collector.findFirst({ where: { email } }),
          tx.recycler.findFirst({ where: { email } })
        ]);
        const ownedByThisProfile = emailUser?.id === user.id
          || (role !== 'RECYCLER' && collectorOwner?.id === profileId)
          || (role === 'RECYCLER' && recyclerOwner?.id === profileId);
        if ((emailUser || collectorOwner || recyclerOwner) && !ownedByThisProfile) {
          throw new AppError('CONFLICT', 'This security email is already linked to another account', 409, { code: 'EMAIL_IN_USE' });
        }
      }

      const updatedUser = await tx.user.update({
        where: { id: user.id },
        data: {
          ...(email !== undefined ? { email } : {}),
          ...(input.preferredLanguage ? { preferredLanguage: input.preferredLanguage as any } : {})
        }
      });

      if (role === 'RECYCLER') {
        const profile = await tx.recycler.update({
          where: { id: profileId },
          data: {
            ...(email !== undefined ? { email } : {}),
            ...(input.displayName !== undefined ? { name: input.displayName } : {}),
            ...(input.areaName !== undefined ? { areaName: input.areaName } : {}),
            ...(input.address ? { address: input.address } : {}),
            ...(input.latitude !== undefined ? { latitude: input.latitude } : {}),
            ...(input.longitude !== undefined ? { longitude: input.longitude } : {})
          },
          include: { materials: true, rates: true }
        });
        return present(updatedUser, profile);
      }

      const profile = await tx.collector.update({
        where: { id: profileId },
        data: {
          ...(email !== undefined ? { email } : {}),
          ...(input.displayName !== undefined ? { displayName: input.displayName } : {}),
          ...(input.areaName !== undefined ? { areaName: input.areaName } : {}),
          ...(input.address !== undefined ? { address: input.address || null } : {}),
          ...(input.latitude !== undefined ? { latitude: input.latitude } : {}),
          ...(input.longitude !== undefined ? { longitude: input.longitude } : {}),
          ...(input.preferredLanguage ? { preferredLanguage: input.preferredLanguage as any } : {})
        }
      });
      return present(updatedUser, profile);
    });
  }
}

function present(user: any, profile: any) {
  const recycler = user.role === 'RECYCLER';
  return {
    id: user.id,
    email: user.email ?? profile?.email ?? null,
    phone: user.phone ?? profile?.phone ?? null,
    displayName: recycler ? profile?.name ?? null : profile?.displayName ?? null,
    areaName: profile?.areaName ?? null,
    address: profile?.address ?? null,
    latitude: profile?.latitude ?? null,
    longitude: profile?.longitude ?? null,
    role: user.role,
    preferredLanguage: user.preferredLanguage,
    accountStatus: user.accountStatus,
    verificationStatus: recycler ? profile?.authorizationStatus ?? 'PENDING' : 'VERIFIED',
    profileId: recycler ? user.recyclerProfileId : user.collectorProfileId,
    profile,
    createdAt: user.createdAt.toISOString(),
    updatedAt: user.updatedAt.toISOString()
  };
}
