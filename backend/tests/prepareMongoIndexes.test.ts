import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ prepare: vi.fn(), connect: vi.fn(), close: vi.fn(), command: vi.fn(), constructed: vi.fn() }));
vi.mock('../src/config/mongoIndexes.js', () => ({ ensureOptionalUniqueIndexes: mocks.prepare }));
vi.mock('mongodb', () => ({ MongoClient: class {
  constructor(url: string) { mocks.constructed(url); }
  connect = mocks.connect;
  close = mocks.close;
  db() { return { command: mocks.command }; }
} }));
import { prepareMongoIndexes } from '../src/config/prepareMongoIndexes.js';

describe('explicit MongoDB index preparation fallback', () => {
  beforeEach(() => { vi.clearAllMocks(); mocks.prepare.mockReset(); });
  it('keeps working Prisma deployments on the existing path', async () => {
    mocks.prepare.mockResolvedValue(true);
    expect(await prepareMongoIndexes({} as never, 'mongodb://example.test/audit')).toBe(true);
    expect(mocks.constructed).not.toHaveBeenCalled();
  });
  it('uses the official driver only when Prisma cannot decode indexes and closes it', async () => {
    mocks.prepare.mockResolvedValueOnce(false).mockImplementationOnce(async adapter => {
      await adapter.$runCommandRaw({ listIndexes: 'Collector' });
      return true;
    });
    mocks.command.mockResolvedValue({ cursor: { firstBatch: [] } });
    expect(await prepareMongoIndexes({} as never, 'mongodb://example.test/audit')).toBe(true);
    expect(mocks.command).toHaveBeenCalledWith({ listIndexes: 'Collector' });
    expect(mocks.close).toHaveBeenCalledOnce();
  });
  it('closes the fallback client if setup fails and does not hide the failure', async () => {
    mocks.prepare.mockResolvedValueOnce(false).mockRejectedValueOnce(new Error('index conflict'));
    await expect(prepareMongoIndexes({} as never, 'mongodb://example.test/audit')).rejects.toThrow('index conflict');
    expect(mocks.close).toHaveBeenCalledOnce();
  });
});
