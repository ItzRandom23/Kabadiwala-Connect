import { createHash, randomBytes, scryptSync, timingSafeEqual } from 'node:crypto';
import type { PrismaClient } from '@prisma/client';
import { AppError } from '../utils/errors.js';
import type { JwtService } from './jwt.js';

export type EmailAccountInput = {
  email: string;
  password: string;
  role: 'COLLECTOR' | 'RECYCLER';
  preferredLanguage: 'ENGLISH' | 'HINDI' | 'MARATHI' | 'ASSAMESE' | 'BENGALI' | 'BODO' | 'DOGRI' | 'GUJARATI' | 'KANNADA' | 'KASHMIRI' | 'KONKANI' | 'MAITHILI' | 'MALAYALAM' | 'MANIPURI' | 'NEPALI' | 'ODIA' | 'PUNJABI' | 'SANSKRIT' | 'SANTALI' | 'SINDHI' | 'TAMIL' | 'TELUGU' | 'URDU';
  areaName?: string;
  businessName?: string;
  authorizationNumber?: string;
  materialsAccepted?: string[];
  pickupAvailable?: boolean;
  serviceRadiusKm?: number;
};

const normalizedEmail = (value: string) => value.trim().toLowerCase();
const passwordDigest = (password: string, salt: Buffer) => scryptSync(password, salt, 64).toString('hex');
const hashPassword = (password: string) => {
  const salt = randomBytes(16);
  return `${salt.toString('hex')}:${passwordDigest(password, salt)}`;
};
const verifyPassword = (password: string, stored: string) => {
  const [saltHex, expectedHex] = stored.split(':');
  if (!saltHex || !expectedHex) return false;
  const actual = Buffer.from(passwordDigest(password, Buffer.from(saltHex, 'hex')), 'hex');
  const expected = Buffer.from(expectedHex, 'hex');
  return actual.length === expected.length && timingSafeEqual(actual, expected);
};

const publicProfile = (user: any, profile: any) => ({
  id: user.id,
  email: user.email ?? profile?.email ?? null,
  phone: user.phone ?? profile?.phone ?? null,
  displayName: user.role === 'RECYCLER' ? profile?.name ?? null : profile?.displayName ?? null,
  areaName: profile?.areaName ?? null,
  role: user.role,
  preferredLanguage: user.preferredLanguage,
  accountStatus: user.accountStatus,
  verificationStatus: user.role === 'RECYCLER' ? profile?.authorizationStatus ?? 'PENDING' : 'VERIFIED',
  profileId: user.role === 'RECYCLER' ? user.recyclerProfileId : user.collectorProfileId,
  latitude: profile?.latitude ?? null,
  longitude: profile?.longitude ?? null,
  profile: profile ?? null,
  createdAt: user.createdAt.toISOString(),
  updatedAt: user.updatedAt.toISOString()
});

export class EmailAuthService {
  constructor(private readonly db: PrismaClient, private readonly jwt: JwtService) {}

  async signup(input: EmailAccountInput) {
    const email = normalizedEmail(input.email);
    const existing = await this.db.user.findUnique({ where: { email } });
    if (existing) throw new AppError('CONFLICT', 'An account already exists for this email', 409, { code: 'EMAIL_IN_USE' });

    const created = await this.db.$transaction(async (tx) => {
      if (input.role === 'COLLECTOR') {
        const profile = await tx.collector.create({
          data: { phone: null, email, preferredLanguage: input.preferredLanguage, areaName: input.areaName?.trim() ?? '' }
        });
        const user = await tx.user.create({ data: { email, passwordHash: hashPassword(input.password), role: input.role, preferredLanguage: input.preferredLanguage, collectorProfileId: profile.id } });
        return { user, profile };
      }
      const profile = await tx.recycler.create({
        data: {
          email,
          name: input.businessName?.trim() || 'New recycler facility',
          address: input.areaName?.trim() || 'Location to be confirmed',
          areaName: input.areaName?.trim() || 'Location to be confirmed',
          authorizationStatus: 'PENDING',
          licenseNumber: input.authorizationNumber?.trim() || null,
          maxPickupDistanceKm: input.serviceRadiusKm ?? 25,
          pickupAvailability: input.pickupAvailable ? 'FLEXIBLE' : 'THIS_WEEK',
          operatingHours: {}
        }
      });
      const accepted = [...new Set((input.materialsAccepted ?? []).map(value => value.trim().toUpperCase()).filter(value => ['CRT', 'LCD_PANEL', 'PCB', 'CABLE', 'BATTERY', 'MOTOR', 'MAGNET', 'PLASTIC', 'OTHER'].includes(value)))];
      for (const category of accepted) await tx.recyclerMaterial.create({ data: { recyclerId: profile.id, category: category as any, subcategories: [], minAcceptableWeight: 0.1, maxAcceptableWeight: 500 } });
      const user = await tx.user.create({ data: { email, passwordHash: hashPassword(input.password), role: input.role, preferredLanguage: input.preferredLanguage, recyclerProfileId: profile.id } });
      return { user, profile };
    });
    return this.issue(created.user, created.profile);
  }

  async login(emailInput: string, password: string) {
    const user = await this.db.user.findUnique({ where: { email: normalizedEmail(emailInput) } });
    if (!user || !user.passwordHash || !verifyPassword(password, user.passwordHash)) throw new AppError('AUTHENTICATION_ERROR', 'Email or password is incorrect', 401, { code: 'INVALID_CREDENTIALS' });
    if (user.accountStatus === 'SUSPENDED') throw new AppError('ACCOUNT_SUSPENDED', 'This account is suspended', 403);
    if (user.accountStatus === 'DELETED') throw new AppError('ACCOUNT_DELETED', 'This account is deleted', 403);
    const profile = user.role === 'RECYCLER'
      ? await this.db.recycler.findUnique({ where: { id: user.recyclerProfileId ?? '' }, include: { materials: true, rates: true } })
      : await this.db.collector.findUnique({ where: { id: user.collectorProfileId ?? '' } });
    return this.issue(user, profile);
  }

  async profile(profileId: string, role: 'COLLECTOR' | 'RECYCLER') {
    const user = await this.db.user.findFirst({ where: role === 'RECYCLER' ? { recyclerProfileId: profileId } : { collectorProfileId: profileId } });
    if (!user) throw new AppError('NOT_FOUND', 'Account profile not found', 404, { code: 'PROFILE_NOT_FOUND' });
    if (user.accountStatus === 'SUSPENDED') throw new AppError('ACCOUNT_SUSPENDED', 'This account is suspended', 403);
    if (user.accountStatus === 'DELETED') throw new AppError('ACCOUNT_DELETED', 'This account is deleted', 403);
    const profile = role === 'RECYCLER'
      ? await this.db.recycler.findUnique({ where: { id: profileId }, include: { materials: true, rates: true } })
      : await this.db.collector.findUnique({ where: { id: profileId } });
    return publicProfile(user, profile);
  }

  private issue(user: any, profile: any) {
    const profileId = user.role === 'RECYCLER' ? user.recyclerProfileId : user.collectorProfileId;
    if (!profileId) throw new AppError('INTERNAL_SERVER_ERROR', 'Account profile is incomplete', 500);
    const token = user.role === 'RECYCLER' ? this.jwt.generateRecyclerToken(profileId) : this.jwt.generateToken(profileId);
    return { token, user: publicProfile(user, profile) };
  }
}

export const emailFingerprint = (email: string) => createHash('sha256').update(normalizedEmail(email)).digest('hex').slice(0, 12);
