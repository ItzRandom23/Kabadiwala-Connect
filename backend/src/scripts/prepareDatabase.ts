import 'dotenv/config';
import { prisma } from '../config/prisma.js';
import { prepareMongoIndexes } from '../config/prepareMongoIndexes.js';

try {
  const ready = await prepareMongoIndexes(prisma, process.env.DATABASE_URL);
  if (ready) console.log('MongoDB runtime indexes are ready.');
  else throw new Error('MongoDB runtime indexes were not prepared because listIndexes could not be decoded. Resolve the database/Prisma compatibility issue before deployment.');
} finally {
  await prisma.$disconnect();
}
