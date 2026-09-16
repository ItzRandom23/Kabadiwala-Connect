import { describe, expect, it, vi } from 'vitest';
import { NotificationService, emitNotification } from '../src/services/notificationService.js';

describe('notification inbox service', () => {
  it('scopes reads and unread counts to the account', async () => {
    const findMany = vi.fn().mockResolvedValue([]);
    const count = vi.fn().mockResolvedValue(2);
    const db = { notificationEvent: { findMany, count } } as never;
    const service = new NotificationService(db);

    await service.list('account-1', true, 500);
    await service.unreadCount('account-1');

    expect(findMany).toHaveBeenCalledWith(expect.objectContaining({
      where: { accountId: 'account-1', readAt: null }, take: 100
    }));
    expect(count).toHaveBeenCalledWith({ where: { accountId: 'account-1', readAt: null } });
  });

  it('keeps a notification failure from breaking a business operation', async () => {
    const create = vi.fn().mockRejectedValue(new Error('notification store unavailable'));
    const result = await emitNotification({ notificationEvent: { create } } as never, {
      accountId: 'account-1', type: 'QUOTE_RECEIVED', title: 'Quote', body: 'A quote arrived'
    });
    expect(result).toBeNull();
  });
});
