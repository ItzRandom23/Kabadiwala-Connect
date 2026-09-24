import 'dotenv/config';
import { randomBytes, scryptSync } from 'node:crypto';
import { prisma } from '../config/prisma.js';

const permissions = [
  'RECYCLER_REVIEW',
  'RECYCLER_AUTHORIZATION',
  'DISPUTE_RESOLUTION',
  'PAYMENT_VERIFICATION',
  'PARTNER_VERIFICATION',
  'PRICE_MANAGEMENT',
  'DATASET_EXPORT'
];

const email = (process.env.ADMIN_BOOTSTRAP_EMAIL ?? process.env.ADMIN_SEED_EMAIL ?? '').trim().toLowerCase();
const password = process.env.ADMIN_BOOTSTRAP_PASSWORD ?? process.env.ADMIN_SEED_PASSWORD ?? '';
const appEnv = (process.env.APP_ENV ?? '').trim().toLowerCase();
const productionConfirmation = process.env.ADMIN_BOOTSTRAP_CONFIRM === 'I_UNDERSTAND_ADMIN_BOOTSTRAP';

if (appEnv !== 'testing' && !productionConfirmation) {
  throw new Error(
    'Production admin provisioning requires ADMIN_BOOTSTRAP_CONFIRM=I_UNDERSTAND_ADMIN_BOOTSTRAP.'
  );
}

if (!email || !email.includes('@')) throw new Error('ADMIN_BOOTSTRAP_EMAIL is required.');
if (password.length < 8) throw new Error('ADMIN_BOOTSTRAP_PASSWORD must be at least 8 characters.');

const salt = randomBytes(16);
const passwordHash = `${salt.toString('hex')}:${scryptSync(password, salt, 64).toString('hex')}`;

try {
  const admin = await prisma.adminAccount.upsert({
    where: { email },
    update: {
      passwordHash,
      active: true,
      permissions,
      displayName: process.env.ADMIN_BOOTSTRAP_DISPLAY_NAME?.trim() || 'Operations admin'
    },
    create: {
      email,
      passwordHash,
      active: true,
      permissions,
      displayName: process.env.ADMIN_BOOTSTRAP_DISPLAY_NAME?.trim() || 'Operations admin'
    }
  });
  console.log(`Admin account provisioned: ${admin.email}`);
} finally {
  await prisma.$disconnect();
}
