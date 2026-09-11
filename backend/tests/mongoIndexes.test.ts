import { describe, expect, it } from 'vitest';
import { ensureOptionalUniqueIndexes } from '../src/config/mongoIndexes.js';

describe('Mongo optional unique indexes', () => {
  it('rebuilds regular nullable unique indexes as partial indexes', async () => {
    const commands: any[] = [];
    const db = {
      $runCommandRaw: async (command: any) => {
        commands.push(command);
        if (command.listIndexes === 'Collector') {
          return { cursor: { firstBatch: [
            { name: 'Collector_phone_key', unique: true },
            { name: 'Collector_email_key', unique: true }
          ] } };
        }
        if (command.listIndexes === 'User') {
          return { cursor: { firstBatch: [
            { name: 'User_email_key', unique: true },
            { name: 'User_phone_key', unique: true },
            { name: 'User_collectorProfileId_key', unique: true },
            { name: 'User_recyclerProfileId_key', unique: true }
          ] } };
        }
        return { ok: 1 };
      }
    } as any;

    await ensureOptionalUniqueIndexes(db);

    const creates = commands.filter(command => command.createIndexes);
    const drops = commands.filter(command => command.dropIndexes);
    expect(creates).toHaveLength(14);
    expect(drops).toHaveLength(6);
    expect(creates).toContainEqual(expect.objectContaining({
      createIndexes: 'User',
      indexes: [expect.objectContaining({
        name: 'User_collectorProfileId_key',
        unique: true,
        partialFilterExpression: { collectorProfileId: { $type: 'string' } }
      })]
    }));
    expect(creates).toContainEqual(expect.objectContaining({
      createIndexes: 'RefreshToken',
      indexes: [expect.objectContaining({ name: 'RefreshToken_tokenHash_key', unique: true })]
    }));
  });

  it('leaves already-correct partial indexes unchanged', async () => {
    const commands: any[] = [];
    const indexes = (collection: string) => [
      ...(collection === 'Collector' ? ['phone', 'email'] : ['email', 'phone', 'collectorProfileId', 'recyclerProfileId'])
    ].map(field => ({
      name: `${collection}_${field}_key`,
      unique: true,
      partialFilterExpression: { [field]: { $type: 'string' } }
    }));
    const db = {
      $runCommandRaw: async (command: any) => {
        commands.push(command);
        return { cursor: { firstBatch: indexes(command.listIndexes) } };
      }
    } as any;

    await ensureOptionalUniqueIndexes(db);

    expect(commands.filter(command => command.dropIndexes)).toHaveLength(0);
    expect(commands.filter(command => command.createIndexes)).toHaveLength(8);
  });
});
