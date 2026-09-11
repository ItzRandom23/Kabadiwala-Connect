import 'dotenv/config';
import { prisma } from '../config/prisma.js';
import { ensureOptionalUniqueIndexes } from '../config/mongoIndexes.js';

try {
  await ensureOptionalUniqueIndexes(prisma);
  console.log('MongoDB runtime indexes are ready.');
} finally {
  await prisma.$disconnect();
}
