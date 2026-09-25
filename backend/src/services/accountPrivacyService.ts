import type { PrismaClient } from '@prisma/client';
import { AppError } from '../utils/errors.js';

export type PrivacyAccountRole = 'HOUSEHOLD' | 'COLLECTOR' | 'RECYCLER';

/**
 * Account privacy operations deliberately use soft deletion. Financial,
 * dispute, and traceability records remain available to authorized operators,
 * while the account's direct contact and authentication data are removed.
 */
export class AccountPrivacyService {
  constructor(private readonly db: PrismaClient) {}

  private async account(profileId: string, role: PrivacyAccountRole) {
    const user = await this.db.user.findFirst({
      where: role === 'RECYCLER' ? { recyclerProfileId: profileId } : { collectorProfileId: profileId }
    });
    if (!user || user.role !== role) throw new AppError('NOT_FOUND', 'Account not found', 404, { code: 'ACCOUNT_NOT_FOUND' });
    return user;
  }

  async exportAccount(profileId: string, role: PrivacyAccountRole) {
    const user = await this.account(profileId, role);
    const [notifications, loginAudits] = await Promise.all([
      this.db.notificationEvent.findMany({ where: { accountId: profileId }, orderBy: { createdAt: 'asc' } }),
      this.db.loginAudit.findMany({ where: { OR: [{ actorId: profileId }, { actorId: user.id }] }, orderBy: { createdAt: 'asc' } })
    ]);

    if (role === 'RECYCLER') {
      const [profile, quoteRequests, quotes, handovers, disputes, bulkOffers, requirements] = await Promise.all([
        this.db.recycler.findUnique({ where: { id: profileId }, include: { materials: true, rates: true } }),
        this.db.quoteRequest.findMany({ where: { recyclerId: profileId }, orderBy: { createdAt: 'asc' } }),
        this.db.quote.findMany({ where: { recyclerId: profileId }, orderBy: { createdAt: 'asc' } }),
        this.db.handover.findMany({ where: { recyclerId: profileId }, orderBy: { createdAt: 'asc' } }),
        this.db.dispute.findMany({ where: { recyclerId: profileId }, orderBy: { createdAt: 'asc' } }),
        this.db.bulkOffer.findMany({ where: { recyclerId: profileId }, orderBy: { createdAt: 'asc' } }),
        this.db.procurementRequirement.findMany({ where: { recyclerId: profileId }, orderBy: { createdAt: 'asc' } })
      ]);
      return this.envelope(user, profileId, role, { profile, quoteRequests, quotes, handovers, disputes, bulkOffers, requirements, notifications, loginAudits });
    }

    const [profile, lots, payments, handovers, disputes, listings, pickups, inventory, bulkLots, poolContributions, supplyPayments] = await Promise.all([
      this.db.collector.findUnique({ where: { id: profileId } }),
      this.db.lot.findMany({ where: { collectorId: profileId }, orderBy: { createdAt: 'asc' } }),
      this.db.payment.findMany({ where: { collectorId: profileId }, orderBy: { createdAt: 'asc' } }),
      this.db.handover.findMany({ where: { collectorId: profileId }, orderBy: { createdAt: 'asc' } }),
      this.db.dispute.findMany({ where: { collectorId: profileId }, orderBy: { createdAt: 'asc' } }),
      role === 'HOUSEHOLD' ? this.db.householdListing.findMany({ where: { householdId: profileId }, orderBy: { createdAt: 'asc' } }) : [],
      this.db.pickupRequest.findMany({ where: role === 'HOUSEHOLD' ? { householdId: profileId } : { kabadiwalaId: profileId }, orderBy: { createdAt: 'asc' } }),
      role === 'COLLECTOR' ? this.db.inventoryBalance.findMany({ where: { kabadiwalaId: profileId }, orderBy: { updatedAt: 'asc' } }) : [],
      role === 'COLLECTOR' ? this.db.bulkLot.findMany({ where: { kabadiwalaId: profileId }, orderBy: { createdAt: 'asc' } }) : [],
      role === 'COLLECTOR' ? this.db.poolContribution.findMany({ where: { collectorId: profileId }, orderBy: { createdAt: 'asc' } }) : [],
      role === 'COLLECTOR' ? this.db.supplyPayment.findMany({ where: { collectorId: profileId }, orderBy: { createdAt: 'asc' } }) : []
    ]);
    return this.envelope(user, profileId, role, { profile, lots, payments, handovers, disputes, listings, pickups, inventory, bulkLots, poolContributions, supplyPayments, notifications, loginAudits });
  }

  private envelope(user: any, profileId: string, role: PrivacyAccountRole, data: Record<string, unknown>) {
    return {
      schemaVersion: 1,
      exportedAt: new Date().toISOString(),
      account: {
        id: user.id,
        profileId,
        role,
        email: user.email,
        phone: user.phone,
        preferredLanguage: user.preferredLanguage,
        accountStatus: user.accountStatus,
        createdAt: user.createdAt,
        updatedAt: user.updatedAt
      },
      // Deliberately absent: passwordHash, refresh tokens, OTP challenges and
      // server secrets. The export contains account data, not credentials.
      data
    };
  }

  async deleteAccount(profileId: string, role: PrivacyAccountRole) {
    const user = await this.account(profileId, role);
    if (user.accountStatus === 'DELETED') return { deleted: true, alreadyDeleted: true, profileId };

    const now = new Date();
    await this.db.$transaction(async (tx: any) => {
      await tx.user.update({
        where: { id: user.id },
        data: { email: null, phone: null, passwordHash: null, accountStatus: 'DELETED' }
      });

      if (role === 'RECYCLER') {
        await tx.recycler.update({
          where: { id: profileId },
          data: {
            name: 'Deleted recycler account',
            address: 'WITHDRAWN_ACCOUNT',
            areaName: 'WITHDRAWN_ACCOUNT',
            latitude: null,
            longitude: null,
            phone: null,
            email: null,
            alternatePhone: null,
            authorizationStatus: 'REVOKED',
            authorizationEvidenceReference: null,
            verifiedBy: null,
            verifiedAt: null,
            authorizationValidUntil: null
          }
        });
      } else {
        await tx.collector.update({
          where: { id: profileId },
          data: {
            phone: null,
            email: null,
            displayName: null,
            address: null,
            latitude: null,
            longitude: null,
            areaName: 'WITHDRAWN_ACCOUNT',
            accountStatus: 'DELETED'
          }
        });
        if (role === 'HOUSEHOLD') {
          await tx.householdListing.updateMany({ where: { householdId: profileId }, data: { pickupAddress: null, latitude: null, longitude: null } });
        }
      }

      await tx.refreshToken.updateMany({ where: { actorId: profileId, revokedAt: null }, data: { revokedAt: now } });
      await tx.notificationEvent.deleteMany({ where: { accountId: profileId } });
      await tx.notificationDelivery?.deleteMany({ where: { accountId: profileId } });
      await tx.notificationDeliveryTarget?.deleteMany({ where: { accountId: profileId } });
      // Keep tests and older disposable schemas tolerant during rolling
      // deployment; production db:prepare creates this collection/index.
      await tx.notificationDevice?.deleteMany({ where: { accountId: profileId } });
      await tx.otpChallenge.deleteMany({ where: { phone: user.phone ?? '__deleted_account__' } });
      await tx.auditEvent.create({
        data: {
          actorId: profileId,
          actorRole: role,
          event: 'ACCOUNT_DELETED',
          entityType: 'ACCOUNT',
          entityId: profileId,
          metadata: { selfService: true, preservedTransactionalRecords: true, occurredAt: now.toISOString() }
        }
      });
    });

    return { deleted: true, alreadyDeleted: false, profileId };
  }
}
