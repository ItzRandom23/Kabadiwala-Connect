import type { PrismaClient } from '@prisma/client';

type RawCommandClient = Pick<PrismaClient, '$runCommandRaw'>;
type MongoIndex = {
  name?: string;
  unique?: boolean;
  partialFilterExpression?: Record<string, unknown>;
};

const optionalUniqueIndexes = [
  { collection: 'Collector', field: 'phone', name: 'Collector_phone_key' },
  { collection: 'Collector', field: 'email', name: 'Collector_email_key' },
  { collection: 'User', field: 'email', name: 'User_email_key' },
  { collection: 'User', field: 'phone', name: 'User_phone_key' },
  { collection: 'User', field: 'collectorProfileId', name: 'User_collectorProfileId_key' },
  { collection: 'User', field: 'recyclerProfileId', name: 'User_recyclerProfileId_key' }
] as const;

function isCorrectPartialIndex(index: MongoIndex | undefined, field: string) {
  const filter = index?.partialFilterExpression?.[field] as { $type?: string } | undefined;
  return index?.unique === true && filter?.$type === 'string';
}

/**
 * Prisma's MongoDB `@unique` indexes include null/missing values. Optional
 * identity fields therefore allow only one phone-only or email-only account.
 * Rebuild those indexes as partial unique indexes before accepting traffic so
 * uniqueness applies only when the field contains an actual string.
 */
export async function ensureOptionalUniqueIndexes(db: RawCommandClient) {
  const collections = [...new Set(optionalUniqueIndexes.map(index => index.collection))];

  for (const collection of collections) {
    const listed = await db.$runCommandRaw({ listIndexes: collection, cursor: {} }) as {
      cursor?: { firstBatch?: MongoIndex[] };
    };
    const existing = listed.cursor?.firstBatch ?? [];

    for (const spec of optionalUniqueIndexes.filter(index => index.collection === collection)) {
      const current = existing.find(index => index.name === spec.name);
      if (isCorrectPartialIndex(current, spec.field)) continue;

      if (current) {
        await db.$runCommandRaw({ dropIndexes: collection, index: spec.name });
      }
      await db.$runCommandRaw({
        createIndexes: collection,
        indexes: [{
          key: { [spec.field]: 1 },
          name: spec.name,
          unique: true,
          partialFilterExpression: { [spec.field]: { $type: 'string' } }
        }]
      });
    }
  }
}
