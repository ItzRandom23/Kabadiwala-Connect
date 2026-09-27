import { describe, expect, it, vi } from 'vitest';
import { RecyclerService } from '../src/services/recyclerService.js';

describe('Recycler verification submission lifecycle', () => {
  it('allows the first submission for an empty pending profile and conditionally claims it', async () => {
    const initial = {
      id: 'recycler-1',
      authorizationStatus: 'PENDING',
      // MongoDB documents created by signup omit most nullable evidence fields.
    };
    const updated = {
      ...initial,
      name: 'Green Loop',
      latitude: null,
      longitude: null,
      address: 'Pune',
      areaName: 'Pune',
      authorizationAuthority: 'MPCB',
      licenseNumber: 'REG-123',
      authorizationType: 'CTO',
      authorizationEvidenceReference: 'https://example.test/evidence',
      verificationSource: 'Public register',
      authorizationValidUntil: new Date('2028-12-31T00:00:00Z'),
      verifiedAt: null,
      verifiedBy: null,
      materials: [],
      rates: [],
      authorizationAudits: [],
      pickupAvailability: 'FLEXIBLE',
      maxPickupDistanceKm: 10,
      operatingHours: {},
      averageHandoverTime: null,
      rating: null,
      reviewCount: 0,
      updatedAt: new Date('2026-09-27T00:00:00Z'),
      phone: null,
      email: null,
      alternatePhone: null,
      pickupIncluded: false,
      pickupFee: null,
      logisticsCostPerKm: null
    };
    const updateMany = vi.fn().mockResolvedValue({ count: 1 });
    const auditCreate = vi.fn().mockResolvedValue({});
    const findUnique = vi.fn().mockResolvedValueOnce(initial).mockResolvedValueOnce(updated);
    const service = new RecyclerService({
      $transaction: async (work: (tx: unknown) => unknown) => work({
        recycler: { findUnique, updateMany },
        recyclerAuthorizationAudit: { create: auditCreate }
      })
    } as never);

    const result = await service.submitVerificationRequest('recycler-1', {
      authority: 'MPCB',
      registrationNumber: 'REG-123',
      authorizationType: 'CTO',
      evidenceReference: 'https://example.test/evidence',
      verificationSource: 'Public register',
      validUntil: new Date('2028-12-31T00:00:00Z')
    });

    expect(updateMany).toHaveBeenCalledWith(expect.objectContaining({
      where: expect.objectContaining({
        id: 'recycler-1', authorizationStatus: 'PENDING',
        AND: expect.arrayContaining([
          { OR: [{ authorizationEvidenceReference: null }, { authorizationEvidenceReference: { isSet: false } }, { authorizationEvidenceReference: '' }] },
          { OR: [{ authorizationValidUntil: null }, { authorizationValidUntil: { isSet: false } }] }
        ])
      }),
      data: expect.objectContaining({ authorizationStatus: 'PENDING', authorizationEvidenceReference: 'https://example.test/evidence' })
    }));
    expect(auditCreate).toHaveBeenCalledOnce();
    expect(result.authorizationStatus).toBe('PENDING');
  });

  it('rejects a second evidence submission while the existing request is pending', async () => {
    const updateMany = vi.fn();
    const auditCreate = vi.fn();
    const previous = {
      id: 'recycler-1',
      authorizationStatus: 'PENDING',
      authorizationAuthority: 'MPCB',
      licenseNumber: 'REG-123',
      authorizationType: 'CTO',
      authorizationEvidenceReference: 'https://example.test/evidence',
      verificationSource: 'Public register',
      authorizationValidUntil: new Date('2028-12-31T00:00:00Z')
    };
    const service = new RecyclerService({
      $transaction: async (work: (tx: unknown) => unknown) => work({
        recycler: {
          findUnique: vi.fn().mockResolvedValue(previous),
          updateMany
        },
        recyclerAuthorizationAudit: { create: auditCreate }
      })
    } as never);

    await expect(service.submitVerificationRequest('recycler-1', {
      authority: 'MPCB',
      registrationNumber: 'REG-456',
      authorizationType: 'CTO',
      evidenceReference: 'https://example.test/new-evidence',
      verificationSource: 'Public register',
      validUntil: new Date('2029-12-31T00:00:00Z')
    })).rejects.toMatchObject({ code: 'CONFLICT', details: { code: 'RECYCLER_VERIFICATION_ALREADY_PENDING' }, status: 409 });

    expect(updateMany).not.toHaveBeenCalled();
    expect(auditCreate).not.toHaveBeenCalled();
  });

  it('re-reads the winner after a concurrent first submission conflicts', async () => {
    const initial = { id: 'recycler-1', authorizationStatus: 'PENDING' };
    const submitted = {
      ...initial,
      authorizationAuthority: 'MPCB',
      licenseNumber: 'REG-123',
      authorizationType: 'CTO',
      authorizationEvidenceReference: 'https://example.test/evidence',
      verificationSource: 'Public register',
      authorizationValidUntil: new Date('2028-12-31T00:00:00Z')
    };
    let committedByConcurrentRequest = false;
    const updateMany = vi.fn(async () => {
      committedByConcurrentRequest = true;
      const conflict = new Error('Transaction failed due to a write conflict') as Error & { code?: string };
      conflict.code = 'P2034';
      throw conflict;
    });
    const auditCreate = vi.fn();
    const service = new RecyclerService({
      $transaction: async (work: (tx: unknown) => unknown) => work({
        recycler: {
          findUnique: vi.fn().mockImplementation(async () => committedByConcurrentRequest ? submitted : initial),
          updateMany
        },
        recyclerAuthorizationAudit: { create: auditCreate }
      })
    } as never);

    await expect(service.submitVerificationRequest('recycler-1', {
      authority: 'MPCB',
      registrationNumber: 'REG-456',
      authorizationType: 'CTO',
      evidenceReference: 'https://example.test/new-evidence',
      verificationSource: 'Public register',
      validUntil: new Date('2029-12-31T00:00:00Z')
    })).rejects.toMatchObject({ code: 'CONFLICT', details: { code: 'RECYCLER_VERIFICATION_ALREADY_PENDING' }, status: 409 });

    expect(updateMany).toHaveBeenCalledOnce();
    expect(auditCreate).not.toHaveBeenCalled();
  });
});
