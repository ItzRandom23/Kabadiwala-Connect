import type { PrismaClient } from '@prisma/client';
import { MongoClient } from 'mongodb';
import { ensureOptionalUniqueIndexes } from './mongoIndexes.js';

/** Explicit deployment preparation only; normal API traffic stays on Prisma. */
export async function prepareMongoIndexes(prisma: Pick<PrismaClient, '$runCommandRaw'>, databaseUrl?: string) {
  if (await ensureOptionalUniqueIndexes(prisma)) return true;
  if (!databaseUrl) throw new Error('DATABASE_URL is required for explicit index preparation');
  // Some Prisma/MongoDB combinations cannot decode BSON Long listIndexes
  // cursors. The official driver preserves BSON types and lets the existing
  // index checks run unchanged, instead of treating an unreadable list as empty.
  const client = new MongoClient(databaseUrl, { serverSelectionTimeoutMS: 10_000, connectTimeoutMS: 10_000 });
  try {
    await client.connect();
    const database = client.db();
    const adapter = { $runCommandRaw: (command: object) => database.command(command) };
    return await ensureOptionalUniqueIndexes(adapter as unknown as Pick<PrismaClient, '$runCommandRaw'>);
  } finally {
    await client.close();
  }
}
