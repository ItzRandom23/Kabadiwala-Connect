import express from 'express';
import request from 'supertest';
import { describe, expect, it, vi } from 'vitest';
import { JwtService } from '../src/services/jwt.js';
import { supplyChainRoutes } from '../src/routes/supplyChainRoutes.js';
import { formalisationRoutes } from '../src/routes/formalisationRoutes.js';
import { errorHandler } from '../src/middleware/errors.js';

const config = { JWT_SECRET: 'passport-privacy-test-secret', JWT_EXPIRES_IN: '1h' } as any;

function appFor(routes: express.Router) {
  const app = express();
  app.use(express.json());
  app.use('/api/v1', routes);
  app.use(errorHandler);
  return app;
}

describe('cross-account passport privacy', () => {
  it('keeps household listing passports scoped to that listing and its pickups', async () => {
    const listing = { id: 'household-listing-1', householdId: 'household-1', materialCategory: 'COPPER', estimatedWeight: 4 };
    const db: any = {
      user: { findFirst: vi.fn().mockResolvedValue({ role: 'HOUSEHOLD', accountStatus: 'ACTIVE' }) },
      householdListing: { findFirst: vi.fn().mockResolvedValue(listing) },
      pickupRequest: { findMany: vi.fn().mockResolvedValue([{ id: 'owned-pickup-1' }]) },
      materialPassportEvent: { findMany: vi.fn().mockResolvedValue([{ id: 'listing-event-1', entityType: 'HOUSEHOLD_LISTING', entityId: listing.id }]) },
      inventoryMovement: { findMany: vi.fn().mockResolvedValue([{ id: 'owned-movement-1', sourceId: 'owned-pickup-1' }]) }
    };
    const jwt = new JwtService(config);
    const app = appFor(supplyChainRoutes(jwt, { findById: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE' }) } as any, db));

    const response = await request(app)
      .get(`/api/v1/household/listings/${listing.id}/passport`)
      .set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-1')}`);

    expect(response.status).toBe(200);
    expect(response.body.data).toMatchObject({ listing, pickupIds: ['owned-pickup-1'] });
    for (const key of ['bulkLots', 'poolContributions', 'handovers', 'settlementBreakdowns', 'poolSettlements', 'anomalies']) {
      expect(response.body.data).not.toHaveProperty(key);
    }
    expect(db.supplyHandover).toBeUndefined();
    expect(db.poolContribution).toBeUndefined();
  });

  it('returns only a pool contributor share in the collector handover list', async () => {
    const handover = {
      id: 'pool-handover-1', collectorId: 'collector-owner', poolId: 'pool-1', recyclerId: 'recycler-1',
      referenceId: 'REF-1', qrCodeData: 'bearer-qr-secret', qrNonceHash: 'secret-hash',
      consignmentHash: 'private-consignment-hash', sourceListingIds: ['household-a', 'household-b'],
      handoverLocation: { latitude: 18.123, longitude: 73.456 }, authorizationSnapshot: { authorizationNumber: 'private-number' },
      materialCategory: 'COPPER', quotedWeightKg: 10, quotedRatePerKg: 50, quotedValue: 500,
      finalAcceptedKg: 9, finalValue: 450, status: 'COMPLETED', createdAt: new Date()
    };
    const contribution = {
      id: 'contribution-2', collectorId: 'collector-2', handoverId: handover.id,
      quantityKg: 3, expectedRatePerKg: 50, expectedPayout: 150, finalAcceptedKg: 2.7, finalPayout: 135
    };
    const findManyContribution = vi.fn()
      .mockResolvedValueOnce([{ handoverId: handover.id }])
      .mockResolvedValueOnce([contribution]);
    const db: any = {
      user: { findFirst: vi.fn().mockResolvedValue({ role: 'COLLECTOR', accountStatus: 'ACTIVE' }) },
      poolContribution: { findMany: findManyContribution },
      supplyHandover: { findMany: vi.fn().mockResolvedValue([handover]) }
    };
    const jwt = new JwtService(config);
    const app = appFor(formalisationRoutes(jwt, { findById: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE' }) } as any, db, 'handover-test-signing-secret'));

    const response = await request(app)
      .get('/api/v1/kabadiwala/handovers')
      .set('Authorization', `Bearer ${jwt.generateToken('collector-2')}`);

    expect(response.status).toBe(200);
    expect(response.body.data).toHaveLength(1);
    expect(response.body.data[0]).toMatchObject({
      id: handover.id, quotedWeightKg: 3, quotedRatePerKg: 50, quotedValue: 150,
      finalAcceptedKg: 2.7, finalValue: 135, qrCodeData: null
    });
    for (const key of ['qrNonceHash', 'consignmentHash', 'sourceListingIds', 'handoverLocation', 'authorizationSnapshot', 'payload']) {
      expect(response.body.data[0]).not.toHaveProperty(key);
    }
    expect(JSON.stringify(response.body)).not.toContain('bearer-qr-secret');
    expect(JSON.stringify(response.body)).not.toContain('household-a');
    expect(JSON.stringify(response.body)).not.toContain('household-b');
  });

  it('limits a pool passport to the requesting collector contribution and its source records', async () => {
    const handover = {
      id: 'pool-handover-2', collectorId: 'collector-owner', poolId: 'pool-2', recyclerId: 'recycler-1',
      referenceId: 'REF-2', qrCodeData: 'another-bearer-qr', sourceListingIds: ['household-a', 'household-b'],
      materialCategory: 'COPPER', quotedWeightKg: 8, quotedRatePerKg: 40, quotedValue: 320,
      finalAcceptedKg: 7, finalValue: 280, status: 'COMPLETED'
    };
    const contribution = {
      id: 'contribution-3', collectorId: 'collector-3', poolId: 'pool-2', handoverId: handover.id,
      sourceListingIds: ['household-c'], quantityKg: 2, expectedRatePerKg: 40, expectedPayout: 80,
      finalAcceptedKg: 1.75, finalPayout: 70
    };
    const eventFindMany = vi.fn().mockResolvedValue([
      { id: 'shared-event', entityType: 'SUPPLY_HANDOVER', entityId: handover.id, eventType: 'HANDOVER_COMPLETED', actorId: 'other-collector', actorRole: 'COLLECTOR', metadata: { sourceListingIds: ['household-a', 'household-b'], payout: 210 } },
      { id: 'own-event', entityType: 'HOUSEHOLD_LISTING', entityId: 'household-c', eventType: 'HOUSEHOLD_LISTED', actorId: 'household-c', actorRole: 'HOUSEHOLD', metadata: { materialCategory: 'COPPER' } }
    ]);
    const settlementBreakdown = { findUnique: vi.fn() };
    const db: any = {
      user: { findFirst: vi.fn().mockResolvedValue({ role: 'COLLECTOR', accountStatus: 'ACTIVE' }) },
      supplyHandover: { findUnique: vi.fn().mockResolvedValue(handover) },
      poolContribution: { findFirst: vi.fn().mockResolvedValue(contribution) },
      pickupRequest: { findMany: vi.fn().mockResolvedValue([{ id: 'own-pickup', listingId: 'household-c' }]) },
      materialPassportEvent: { findMany: eventFindMany },
      inventoryMovement: { findMany: vi.fn().mockResolvedValue([{ id: 'own-movement', sourceId: 'own-pickup' }]) },
      settlementBreakdown,
      poolSettlement: { findUnique: vi.fn().mockResolvedValue({ contributionId: contribution.id, payout: 70 }) },
      supplyPayment: { findMany: vi.fn().mockResolvedValue([{ id: 'own-payment', collectorId: 'collector-3', amount: 70 }]) }
    };
    const jwt = new JwtService(config);
    const app = appFor(formalisationRoutes(jwt, { findById: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE' }) } as any, db, 'handover-test-signing-secret'));

    const response = await request(app)
      .get(`/api/v1/kabadiwala/handovers/${handover.id}/passport`)
      .set('Authorization', `Bearer ${jwt.generateToken('collector-3')}`);

    expect(response.status).toBe(200);
    expect(response.body.data.sourceListingIds).toEqual(['household-c']);
    expect(response.body.data.handover).toMatchObject({ quotedWeightKg: 2, quotedValue: 80, finalAcceptedKg: 1.75, finalValue: 70, qrCodeData: null, sourceListingIds: ['household-c'] });
    expect(response.body.data.settlement).toEqual({ contributionId: contribution.id, payout: 70 });
    expect(settlementBreakdown.findUnique).not.toHaveBeenCalled();
    expect(response.body.data.events.find((event: any) => event.id === 'shared-event')).toMatchObject({ actorRole: 'COLLECTOR', metadata: null });
    expect(response.body.data.events.find((event: any) => event.id === 'shared-event')).not.toHaveProperty('actorId');
    expect(JSON.stringify(response.body)).not.toContain('another-bearer-qr');
    expect(JSON.stringify(response.body)).not.toContain('household-a');
    expect(JSON.stringify(response.body)).not.toContain('household-b');
    expect(JSON.stringify(response.body)).not.toContain('other-collector');
    expect(JSON.stringify(response.body)).not.toContain('210');
  });
});
