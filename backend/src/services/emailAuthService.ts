import { createHash, randomBytes, scryptSync, timingSafeEqual } from 'node:crypto';
import type { PrismaClient } from '@prisma/client';
import { AppError } from '../utils/errors.js';
import type { JwtService } from './jwt.js';
import type { SessionService } from './sessionService.js';
import type { AuthenticationRateLimiter } from './rateLimiter.js';

export type EmailAccountInput = {
  email: string;
  password: string;
  role: 'HOUSEHOLD' | 'COLLECTOR' | 'RECYCLER';
  preferredLanguage: 'ENGLISH' | 'HINDI' | 'MARATHI' | 'ASSAMESE' | 'BENGALI' | 'BODO' | 'DOGRI' | 'GUJARATI' | 'KANNADA' | 'KASHMIRI' | 'KONKANI' | 'MAITHILI' | 'MALAYALAM' | 'MANIPURI' | 'NEPALI' | 'ODIA' | 'PUNJABI' | 'SANSKRIT' | 'SANTALI' | 'SINDHI' | 'TAMIL' | 'TELUGU' | 'URDU';
  areaName?: string;
  address?: string;
  latitude?: number;
  longitude?: number;
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
  address: profile?.address ?? null,
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
  constructor(private readonly db: PrismaClient, private readonly jwt: JwtService, private readonly sessions?: SessionService, private readonly limiter?: AuthenticationRateLimiter) {}

  private audit(method: string, outcome: string, ip: string, userAgent?: string, actorId?: string, actorRole?: string) { return this.db.loginAudit.create({ data: { actorId, actorRole, method, outcome, ipHash: createHash('sha256').update(ip).digest('hex'), userAgent: userAgent?.slice(0, 300) } }).catch(() => undefined); }

  async signup(input: EmailAccountInput, ip = 'unknown', userAgent?: string) {
    const email = normalizedEmail(input.email);
    await this.limiter?.check(email, ip, 'signup');
    const existing = await this.db.user.findFirst({ where: { email } });
    if (existing) throw new AppError('CONFLICT', 'An account already exists for this email', 409, { code: 'EMAIL_IN_USE' });

    let created: { user: any; profile: any };
    try {
      created = await this.db.$transaction(async (tx) => {
      if (input.role !== 'RECYCLER') {
        const profile = await tx.collector.create({
          data: { phone: null, email, preferredLanguage: input.preferredLanguage, areaName: input.areaName?.trim() ?? '', address: input.address?.trim() || null, latitude: input.latitude, longitude: input.longitude }
        });
        const user = await tx.user.create({ data: { email, passwordHash: hashPassword(input.password), role: input.role, preferredLanguage: input.preferredLanguage, collectorProfileId: profile.id } });
        return { user, profile };
      }
      const profile = await tx.recycler.create({
        data: {
          email,
          name: input.businessName?.trim() || 'New recycler facility',
          address: input.address?.trim() || input.areaName?.trim() || 'Location to be confirmed',
          areaName: input.areaName?.trim() || 'Location to be confirmed',
          latitude: input.latitude,
          longitude: input.longitude,
          authorizationStatus: 'PENDING',
          licenseNumber: input.authorizationNumber?.trim() || null,
          maxPickupDistanceKm: input.serviceRadiusKm ?? 25,
          pickupAvailability: input.pickupAvailable ? 'FLEXIBLE' : 'THIS_WEEK',
          operatingHours: {}
        }
      });
      const accepted = [...new Set((input.materialsAccepted ?? []).map(value => value.trim().toUpperCase()).filter(value => ['CRT', 'LCD_PANEL', 'PCB', 'CABLE', 'COPPER', 'BATTERY', 'MOTOR', 'MAGNET', 'PLASTIC', 'OTHER'].includes(value)))];
      if (!accepted.length) throw new AppError('VALIDATION_ERROR', 'At least one supported material is required for recycler registration', 422, { code: 'INVALID_RECYCLER_MATERIALS' });
      for (const category of accepted) await tx.recyclerMaterial.create({ data: { recyclerId: profile.id, category: category as any, subcategories: [], acceptedGrades: ['UNSPECIFIED'], minAcceptableWeight: 0.1, maxAcceptableWeight: 500 } });
      const user = await tx.user.create({ data: { email, passwordHash: hashPassword(input.password), role: input.role, preferredLanguage: input.preferredLanguage, recyclerProfileId: profile.id } });
      return { user, profile };
      });
    } catch (error) {
      // The preflight lookup above cannot prevent two simultaneous signups
      // from racing. Surface the unique-email race as the same stable API
      // contract as the normal duplicate path.
      if ((error as { code?: string })?.code === 'P2002') {
        throw new AppError('CONFLICT', 'An account already exists for this email', 409, { code: 'EMAIL_IN_USE' });
      }
      throw error;
    }
    const issued = await this.issue(created.user, created.profile);
    await this.audit('EMAIL_SIGNUP', 'SUCCESS', ip, userAgent, created.user.id, created.user.role);
    return issued;
  }

  async login(emailInput: string, password: string, ip = 'unknown', userAgent?: string) {
    const email = normalizedEmail(emailInput);
    await this.limiter?.check(email, ip, 'login');
    const user = await this.db.user.findFirst({ where: { email } });
    if (!user || !user.passwordHash || !verifyPassword(password, user.passwordHash)) { await this.audit('EMAIL_LOGIN', 'INVALID_CREDENTIALS', ip, userAgent); throw new AppError('AUTHENTICATION_ERROR', 'Email or password is incorrect', 401, { code: 'INVALID_CREDENTIALS' }); }
    if (user.accountStatus === 'SUSPENDED') throw new AppError('ACCOUNT_SUSPENDED', 'This account is suspended', 403);
    if (user.accountStatus === 'DELETED') throw new AppError('ACCOUNT_DELETED', 'This account is deleted', 403);
    const profile = user.role === 'RECYCLER'
      ? await this.db.recycler.findUnique({ where: { id: user.recyclerProfileId ?? '' }, include: { materials: true, rates: true } })
      : await this.db.collector.findUnique({ where: { id: user.collectorProfileId ?? '' } });
    const issued = await this.issue(user, profile);
    await this.audit('EMAIL_LOGIN', 'SUCCESS', ip, userAgent, user.id, user.role);
    return issued;
  }

  async adminLogin(emailInput: string, password: string, ip = 'unknown', userAgent?: string) {
    const email = normalizedEmail(emailInput);
    await this.limiter?.check(email, ip, 'login');
    const admin = await this.db.adminAccount.findUnique({ where: { email } });
    if (!admin || !admin.passwordHash || !verifyPassword(password, admin.passwordHash)) {
      await this.audit('ADMIN_LOGIN', 'INVALID_CREDENTIALS', ip, userAgent);
      throw new AppError('AUTHENTICATION_ERROR', 'Email or password is incorrect', 401, { code: 'INVALID_CREDENTIALS' });
    }
    if (!admin.active) {
      await this.audit('ADMIN_LOGIN', 'INACTIVE_ACCOUNT', ip, userAgent, admin.id, 'ADMIN');
      throw new AppError('ADMIN_SUSPENDED', 'This admin account is inactive', 403);
    }
    await this.db.adminAccount.update({ where: { id: admin.id }, data: { lastLoginAt: new Date() } });
    const issued = this.sessions ? await this.sessions.issue(admin.id, 'ADMIN') : { token: this.jwt.generateAdminToken(admin.id) };
    await this.audit('ADMIN_LOGIN', 'SUCCESS', ip, userAgent, admin.id, 'ADMIN');
    return { ...issued, user: { id: admin.id, email: admin.email, displayName: admin.displayName, role: 'ADMIN', permissions: admin.permissions } };
  }

  async profile(profileId: string, role: 'HOUSEHOLD' | 'COLLECTOR' | 'RECYCLER') {
    const user = await this.db.user.findFirst({ where: role === 'RECYCLER' ? { recyclerProfileId: profileId } : { collectorProfileId: profileId } });
    if (!user) throw new AppError('NOT_FOUND', 'Account profile not found', 404, { code: 'PROFILE_NOT_FOUND' });
    if (user.accountStatus === 'SUSPENDED') throw new AppError('ACCOUNT_SUSPENDED', 'This account is suspended', 403);
    if (user.accountStatus === 'DELETED') throw new AppError('ACCOUNT_DELETED', 'This account is deleted', 403);
    const profile = role === 'RECYCLER'
      ? await this.db.recycler.findUnique({ where: { id: profileId }, include: { materials: true, rates: true } })
      : await this.db.collector.findUnique({ where: { id: profileId } });
    return publicProfile(user, profile);
  }

  private async issue(user: any, profile: any) {
    const profileId = user.role === 'RECYCLER' ? user.recyclerProfileId : user.collectorProfileId;
    if (!profileId) throw new AppError('INTERNAL_SERVER_ERROR', 'Account profile is incomplete', 500);
    const issued = this.sessions ? await this.sessions.issue(profileId, user.role) : { token: user.role === 'RECYCLER' ? this.jwt.generateRecyclerToken(profileId) : user.role === 'HOUSEHOLD' ? this.jwt.generateHouseholdToken(profileId) : this.jwt.generateToken(profileId) };
    return { ...issued, user: publicProfile(user, profile) };
  }
}

export const emailFingerprint = (email: string) => createHash('sha256').update(normalizedEmail(email)).digest('hex').slice(0, 12);
