import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ prepare: vi.fn(), disconnect: vi.fn() }));
vi.mock('../src/config/prisma.js', () => ({ prisma: { $disconnect: mocks.disconnect } }));
vi.mock('../src/config/prepareMongoIndexes.js', () => ({ prepareMongoIndexes: mocks.prepare }));

describe('explicit database preparation', () => {
  beforeEach(() => { vi.resetModules(); vi.clearAllMocks(); });
  it('fails deployment preparation if mandatory indexes could not be prepared', async () => {
    mocks.prepare.mockResolvedValue(false);
    await expect(import('../src/scripts/prepareDatabase.js')).rejects.toThrow('MongoDB runtime indexes were not prepared');
    expect(mocks.disconnect).toHaveBeenCalledOnce();
  });
  it('disconnects after successful preparation', async () => {
    mocks.prepare.mockResolvedValue(true);
    await import('../src/scripts/prepareDatabase.js');
    expect(mocks.disconnect).toHaveBeenCalledOnce();
  });
});
