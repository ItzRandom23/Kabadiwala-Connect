import 'dotenv/config';
import { prisma } from '../config/prisma.js';
import { ensureOptionalUniqueIndexes } from '../config/mongoIndexes.js';

try {
  const ready = await ensureOptionalUniqueIndexes(prisma);
  if (ready) console.log('MongoDB runtime indexes are ready.');
  else console.warn('MongoDB runtime index preparation was skipped because Prisma could not decode listIndexes output.');
} finally {
  await prisma.$disconnect();
}
