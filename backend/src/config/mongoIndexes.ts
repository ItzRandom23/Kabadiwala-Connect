import type { PrismaClient } from '@prisma/client';

type RawCommandClient = Pick<PrismaClient, '$runCommandRaw'>;
type MongoIndex = {
  name?: string;
  unique?: boolean;
  partialFilterExpression?: Record<string, unknown>;
};

const requiredIndexes = [
  { collection: 'AiInference', name: 'AiInference_lotId_createdAt_idx', key: { lotId: 1, createdAt: 1 } },
  { collection: 'AiInference', name: 'AiInference_feature_modelVersion_createdAt_idx', key: { feature: 1, modelVersion: 1, createdAt: 1 } },
  { collection: 'RefreshToken', name: 'RefreshToken_tokenHash_key', key: { tokenHash: 1 }, unique: true },
  { collection: 'RefreshToken', name: 'RefreshToken_actorId_revokedAt_expiresAt_idx', key: { actorId: 1, revokedAt: 1, expiresAt: 1 } },
  { collection: 'RefreshToken', name: 'RefreshToken_familyId_createdAt_idx', key: { familyId: 1, createdAt: 1 } },
  { collection: 'LoginAudit', name: 'LoginAudit_actorId_createdAt_idx', key: { actorId: 1, createdAt: 1 } },
  { collection: 'LoginAudit', name: 'LoginAudit_outcome_createdAt_idx', key: { outcome: 1, createdAt: 1 } },
  { collection: 'RequestRateLimit', name: 'RequestRateLimit_resetAt_idx', key: { resetAt: 1 } }
] as const;

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
export async function ensureOptionalUniqueIndexes(db: RawCommandClient): Promise<boolean> {
  const collections = [...new Set([...optionalUniqueIndexes, ...requiredIndexes].map(index => index.collection))];

  for (const collection of collections) {
    const existing = await listIndexesOrEmpty(db, collection);
    // Prisma 6.12 cannot deserialize the BSON Long cursor id returned by
    // some MongoDB deployments for listIndexes. Index maintenance is
    // best-effort at runtime; leave the database untouched rather than
    // treating an unreadable listing as an empty collection and attempting
    // destructive drops/duplicate creates.
    if (existing === undefined) return false;

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

    for (const spec of requiredIndexes.filter(index => index.collection === collection)) {
      if (existing.some(index => index.name === spec.name)) continue;
      await db.$runCommandRaw({
        createIndexes: collection,
        indexes: [{ key: spec.key, name: spec.name, ...('unique' in spec && spec.unique ? { unique: true } : {}) }]
      });
    }
  }
  return true;
}

async function listIndexesOrEmpty(db: RawCommandClient, collection: string): Promise<MongoIndex[] | undefined> {
  try {
    const listed = await db.$runCommandRaw({ listIndexes: collection, cursor: {} }) as { cursor?: { firstBatch?: MongoIndex[] } };
    return listed.cursor?.firstBatch ?? [];
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    if (message.includes('NamespaceNotFound') || message.includes('ns does not exist') || message.includes('code 26')) return [];
    if (message.includes('Unknown tagged value') || message.includes('deserializeTaggedValue')) {
      console.warn(`Skipping runtime index maintenance for ${collection}: Prisma could not decode MongoDB listIndexes output.`);
      return undefined;
    }
    throw error;
  }
}
