import { describe, expect, it, vi } from 'vitest';
import { QuoteService } from '../src/services/quoteService.js';

describe('collector quote privacy boundary', () => {
  it('omits recycler contact and exact facility data from quote responses', async () => {
    const quote = {
      id: 'quote-1',
      quoteRequestId: 'request-1',
      lotId: 'lot-1',
      recyclerId: 'recycler-1',
      pricePerKg: 300,
      totalQuotedPrice: 600,
      validUntil: new Date(Date.now() + 60_000),
      status: 'SENT',
      recyclerNotes: null,
      comparison: 'FAIR',
      anomaly: false,
      createdAt: new Date(),
      sentAt: new Date(),
      respondedAt: new Date(),
      acceptedAt: null,
      rejectedAt: null,
      recycler: {
        id: 'recycler-1',
        name: 'Green Loop',
        areaName: 'Pune',
        address: 'Private street address',
        latitude: 18.52,
        longitude: 73.85,
        phone: '+919999999999',
        email: 'private@example.com',
        authorizationStatus: 'VERIFIED',
        authorizationAuthority: 'MPCB',
        authorizationType: 'LICENSE',
        authorizationValidUntil: null,
        materials: [],
        rates: [],
        operatingHours: {},
        maxPickupDistanceKm: 25,
        pickupAvailability: 'TODAY',
        averageHandoverTime: null,
        rating: 4.5,
        reviewCount: 2,
        updatedAt: new Date(),
        _count: { handovers: 3 }
      }
    };
    const db = {
      quoteRequest: {
        findMany: vi.fn().mockResolvedValue([{ quotes: [quote] }])
      }
    } as never;

    const result = await new QuoteService(db).pending('collector-1', 'lot-1');
    const recycler = result[0].recycler;

    expect(recycler).toMatchObject({ id: 'recycler-1', name: 'Green Loop', authorizationStatus: 'VERIFIED' });
    expect(recycler).not.toHaveProperty('contact');
    expect(recycler?.facilityLocation).toEqual({ latitude: null, longitude: null, address: null, areaName: 'Pune' });
  });
});
