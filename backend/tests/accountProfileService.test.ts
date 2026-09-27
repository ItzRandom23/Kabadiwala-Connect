import { describe, expect, it } from 'vitest';
import { AccountProfileService } from '../src/services/accountProfileService.js';

describe('account profile location updates', () => {
  it('clears old coordinates when the saved address changes', async () => {
    let collectorUpdate: Record<string, unknown> | null = null;
    const user = {
      id: 'user-1', phone: '9876543210', email: null, preferredLanguage: 'ENGLISH', accountStatus: 'ACTIVE',
      createdAt: new Date(), updatedAt: new Date()
    };
    const collector = {
      id: 'profile-1', phone: user.phone, email: null, displayName: 'Household',
      areaName: 'Pune', address: 'Old address', latitude: 18.5, longitude: 73.8
    };
    const tx = {
      user: {
        findFirst: async () => user,
        update: async ({ data }: any) => ({ ...user, ...data })
      },
      collector: {
        update: async ({ data }: any) => {
          collectorUpdate = data;
          return { ...collector, ...data };
        }
      }
    };
    const db = { $transaction: async (work: (value: typeof tx) => unknown) => work(tx) } as any;
    const service = new AccountProfileService(db);

    const result = await service.update('profile-1', 'HOUSEHOLD', {
      address: 'New address',
      clearCoordinates: true
    });

    expect(collectorUpdate).toMatchObject({ latitude: null, longitude: null });
    expect(result.latitude).toBeNull();
    expect(result.longitude).toBeNull();
  });
});
