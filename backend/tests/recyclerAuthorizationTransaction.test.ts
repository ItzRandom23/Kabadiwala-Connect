import { describe, expect, it, vi } from 'vitest';
import { RecyclerService } from '../src/services/recyclerService.js';

describe('Recycler authorization transaction scope', () => {
  it('commits authorization and audit before fetching display relations', async () => {
    let committed = false;
    const tx = { recycler: { findUnique: vi.fn().mockResolvedValue({ authorizationStatus: 'PENDING' }), update: vi.fn().mockResolvedValue({ id: 'recycler-1' }) }, recyclerAuthorizationAudit: { create: vi.fn().mockResolvedValue({}) } };
    const db = {
      $transaction: vi.fn(async (work: (value: typeof tx) => unknown) => { const value = await work(tx); committed = true; return value; }),
      recycler: { findUnique: vi.fn(async () => {
        expect(committed).toBe(true);
        return { id: 'recycler-1', authorizationStatus: 'REJECTED', materials: [], rates: [], authorizationAudits: [{ newStatus: 'REJECTED', reason: 'Updated evidence needed' }] };
      }) }
    } as never;
    const result = await new RecyclerService(db).authorize('recycler-1', 'admin-1', 'REJECTED', 'Updated evidence needed');
    expect(result.authorizationDetails.reviewReason).toBe('Updated evidence needed');
    expect(tx.recycler.update).toHaveBeenCalledWith(expect.objectContaining({ select: { id: true } }));
    expect(tx.recyclerAuthorizationAudit.create).toHaveBeenCalledOnce();
  });
});
