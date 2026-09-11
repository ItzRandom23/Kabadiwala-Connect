import type { PrismaClient } from '@prisma/client';
import { AppError } from '../utils/errors.js';
import type { CollectorRepository } from '../repositories/collectorRepository.js';
import type { JwtService } from './jwt.js';
import type { OtpProvider } from './otp.js';
import { OtpRateLimiter, type AuthenticationRateLimiter } from './rateLimiter.js';
import type { SessionService } from './sessionService.js';

export type PhoneAccountInput = {
  role?: 'COLLECTOR' | 'RECYCLER';
  preferredLanguage?: string;
  areaName?: string;
  latitude?: number;
  longitude?: number;
  displayName?: string;
  email?: string;
  businessName?: string;
  authorizationNumber?: string;
  materialsAccepted?: string[];
  pickupAvailable?: boolean;
  serviceRadiusKm?: number;
};

const normalizedEmail = (value?: string | null) => value?.trim().toLowerCase() || null;
const acceptedMaterials = new Set(['CRT', 'LCD_PANEL', 'PCB', 'CABLE', 'BATTERY', 'MOTOR', 'MAGNET', 'PLASTIC', 'OTHER']);
const supportedLanguages = new Set([
  'ENGLISH', 'ASSAMESE', 'BENGALI', 'BODO', 'DOGRI', 'GUJARATI', 'HINDI', 'KANNADA',
  'KASHMIRI', 'KONKANI', 'MAITHILI', 'MALAYALAM', 'MANIPURI', 'MARATHI', 'NEPALI',
  'ODIA', 'PUNJABI', 'SANSKRIT', 'SANTALI', 'SINDHI', 'TAMIL', 'TELUGU', 'URDU'
]);

function publicPhoneProfile(user: any, profile: any) {
  const isRecycler = user.role === 'RECYCLER';
  return {
    id: user.id,
    email: user.email ?? profile?.email ?? null,
    phone: user.phone ?? profile?.phone ?? null,
    displayName: isRecycler ? profile?.name ?? null : profile?.displayName ?? null,
    areaName: profile?.areaName ?? null,
    role: user.role,
    preferredLanguage: user.preferredLanguage,
    accountStatus: user.accountStatus,
    verificationStatus: isRecycler ? profile?.authorizationStatus ?? 'PENDING' : 'VERIFIED',
    profileId: isRecycler ? user.recyclerProfileId : user.collectorProfileId,
    latitude: profile?.latitude ?? null,
    longitude: profile?.longitude ?? null,
    profile: profile ?? null,
    createdAt: user.createdAt.toISOString(),
    updatedAt: user.updatedAt.toISOString()
  };
}

export class AuthService {
  constructor(
    private readonly otp: OtpProvider,
    private readonly collectors: CollectorRepository,
    private readonly jwt: JwtService,
    private readonly limiter: AuthenticationRateLimiter = new OtpRateLimiter(),
    private readonly db?: PrismaClient,
    private readonly sessions?: SessionService
  ) {}

  async requestOtp(phone: string, ip: string) {
    await this.limiter.check(phone, ip, 'request');
    await this.otp.request(phone);
    return 'OTP sent successfully';
  }

  async verifyOtp(phone: string, code: string, ip: string, input?: PhoneAccountInput) {
    await this.limiter.check(phone, ip, 'verify');
    const result = await this.otp.verify(phone, code);
    if (result === 'expired') throw new AppError('OTP_EXPIRED', 'OTP has expired', 400);
    if (result === 'locked') throw new AppError('OTP_ATTEMPTS_EXCEEDED', 'Too many verification attempts', 429);
    if (result !== 'approved') throw new AppError('OTP_INVALID', 'Invalid OTP', 400);

    // Keep the repository-backed path for unit tests and older callers.
    // Production phone signup passes input and uses the atomic Prisma path.
    if (!this.db || !input) {
      let collector = await this.collectors.findByPhone(phone);
      const created = !collector;
      collector ??= await this.collectors.create(phone);
      collector = await this.collectors.touchLogin(collector.id);
      console.log(JSON.stringify({ event: created ? 'collector_created' : 'collector_login', collectorId: collector.id }));
      const issued = this.sessions ? await this.sessions.issue(collector.id, 'COLLECTOR') : { token: this.jwt.generateToken(collector.id) };
      return { ...issued, collector, user: null };
    }

    try {
      return await this.verifyPhoneAccount(phone, input);
    } catch (error) {
      const details = error instanceof AppError && typeof error.details === 'object' && error.details !== null
        ? error.details as { code?: string }
        : undefined;
      const isEmailConflict = error instanceof AppError && error.code === 'CONFLICT' && details?.code === 'EMAIL_IN_USE';
      const isUniqueConstraint = (error as { code?: string })?.code === 'P2002';
      if (isEmailConflict || isUniqueConstraint) {
        // A double tap or a retry from a second device can race the first
        // transaction. Once the phone row exists, the verified phone is the
        // identity boundary: return the account that won the insert instead
        // of surfacing a misleading 409 to the user.
        if (isUniqueConstraint) {
          const existing = await this.findExistingPhoneAccount(phone);
          if (existing) return existing;
        }
        // The phone OTP is the verified identity boundary. Email is optional
        // for phone accounts, so a duplicate email must not block signup;
        // retry without attaching that email. This also makes a concurrent
        // double-tap/retry idempotent after the first transaction commits.
        try {
          return await this.verifyPhoneAccount(phone, { ...input, email: undefined });
        } catch (retryError) {
          if (retryError instanceof AppError) throw retryError;
          if ((retryError as { code?: string })?.code === 'P2002') {
            const existing = await this.findExistingPhoneAccount(phone);
            if (existing) return existing;
            throw new AppError('CONFLICT', 'This mobile number is already linked to another account', 409, { code: 'ACCOUNT_CONFLICT' });
          }
          throw retryError;
        }
      }
      if (error instanceof AppError) throw error;
      throw error;
    }
  }

  private async findExistingPhoneAccount(phone: string) {
    if (!this.db) return null;
    const user = await this.db.user.findUnique({ where: { phone } });
    if (!user || user.accountStatus === 'SUSPENDED' || user.accountStatus === 'DELETED') return null;
    const profile = user.role === 'RECYCLER'
      ? await this.db.recycler.findUnique({ where: { id: user.recyclerProfileId ?? '' }, include: { materials: true, rates: true } })
      : await this.db.collector.findUnique({ where: { id: user.collectorProfileId ?? '' } });
    return this.issuePhone(user, profile);
  }

  private async verifyPhoneAccount(phone: string, input: PhoneAccountInput) {
    const requestedRole = input.role ?? 'COLLECTOR';
    const email = normalizedEmail(input.email);
    const preferredLanguage = (input.preferredLanguage ?? 'ENGLISH').trim().toUpperCase();
    if (!supportedLanguages.has(preferredLanguage)) {
      throw new AppError('VALIDATION_ERROR', 'Unsupported language', 422, { code: 'INVALID_LANGUAGE' });
    }
    const areaName = input.areaName?.trim() ?? '';
    const latitude = input.latitude;
    const longitude = input.longitude;
    const displayName = input.displayName?.trim() ?? '';

    return this.db!.$transaction(async (tx) => {
      // Email is an optional recovery detail. A verified phone must remain
      // sufficient to sign in, even when the submitted email belongs to a
      // different account. In that case, leave the existing email untouched
      // or create the phone account without attaching the duplicate email.
      const usableEmail = async (candidate: string | null, existingUserId?: string, existingCollectorId?: string) => {
        if (!candidate) return null;
        const emailOwner = await tx.user.findUnique({ where: { email: candidate } });
        const collectorEmailOwner = await tx.collector.findUnique({ where: { email: candidate } });
        if ((emailOwner && emailOwner.id !== existingUserId) || (collectorEmailOwner && collectorEmailOwner.id !== existingCollectorId)) return null;
        return candidate;
      };

      let user = await tx.user.findUnique({ where: { phone } });

      // Link legacy OTP-created records to the account identity model without
      // creating a second account for the same verified mobile number.
      if (!user) {
        const legacyCollector = await tx.collector.findFirst({ where: { phone } });
        if (legacyCollector) {
          user = await tx.user.findFirst({ where: { collectorProfileId: legacyCollector.id } });
          if (!user) {
            const accountEmail = await usableEmail(email, undefined, legacyCollector.id);
            user = await tx.user.create({
              data: {
                phone,
                email: accountEmail,
                passwordHash: null,
                role: 'COLLECTOR',
                preferredLanguage: preferredLanguage as any,
                collectorProfileId: legacyCollector.id
              }
            });
          }
        }
      }

      if (!user && requestedRole === 'RECYCLER') {
        const legacyRecycler = await tx.recycler.findFirst({ where: { phone } });
        if (legacyRecycler) {
          user = await tx.user.findFirst({ where: { recyclerProfileId: legacyRecycler.id } });
          if (!user) {
            const accountEmail = await usableEmail(email);
            user = await tx.user.create({
              data: {
                phone,
                email: accountEmail,
                passwordHash: null,
                role: 'RECYCLER',
                preferredLanguage: preferredLanguage as any,
                recyclerProfileId: legacyRecycler.id
              }
            });
          }
        }
      }

      if (user) {
        if (user.accountStatus === 'SUSPENDED') throw new AppError('ACCOUNT_SUSPENDED', 'This account is suspended', 403);
        if (user.accountStatus === 'DELETED') throw new AppError('ACCOUNT_DELETED', 'This account is deleted', 403);
        const accountEmail = await usableEmail(email, user.id, user.collectorProfileId ?? undefined);
        if (accountEmail && accountEmail !== user.email) {
          user = await tx.user.update({ where: { id: user.id }, data: { email: accountEmail } });
        }

        if (user.role === 'COLLECTOR') {
          await tx.collector.update({
            where: { id: user.collectorProfileId ?? '' },
            data: {
              ...(accountEmail ? { email: accountEmail } : {}),
              ...(displayName ? { displayName } : {}),
              ...(areaName ? { areaName } : {}),
              ...(latitude !== undefined ? { latitude } : {}),
              ...(longitude !== undefined ? { longitude } : {}),
              preferredLanguage: preferredLanguage as any,
              lastLoginAt: new Date()
            }
          });
        }
        const profile = user.role === 'RECYCLER'
          ? await tx.recycler.findUnique({ where: { id: user.recyclerProfileId ?? '' }, include: { materials: true, rates: true } })
          : await tx.collector.findUnique({ where: { id: user.collectorProfileId ?? '' } });
        return this.issuePhone(user, profile);
      }

      if (requestedRole === 'COLLECTOR' && !displayName) {
        throw new AppError('VALIDATION_ERROR', 'Name is required for collector registration', 422, { code: 'DISPLAY_NAME_REQUIRED' });
      }

      const accountEmail = await usableEmail(email);

      if (requestedRole === 'COLLECTOR') {
        const profile = await tx.collector.create({
          data: {
            phone,
            email: accountEmail,
            displayName: displayName || null,
            preferredLanguage: preferredLanguage as any,
            areaName,
            latitude,
            longitude,
            accountStatus: 'ACTIVE'
          }
        });
        user = await tx.user.create({
          data: {
            phone,
            email: accountEmail,
            passwordHash: null,
            role: 'COLLECTOR',
            preferredLanguage: preferredLanguage as any,
            collectorProfileId: profile.id
          }
        });
        return this.issuePhone(user, profile);
      }

      const profile = await tx.recycler.create({
        data: {
          phone,
          email: accountEmail,
          name: input.businessName?.trim() || displayName || 'New recycler facility',
          address: areaName || 'Location to be confirmed',
          areaName: areaName || 'Location to be confirmed',
          latitude,
          longitude,
          authorizationStatus: 'PENDING',
          licenseNumber: input.authorizationNumber?.trim() || null,
          maxPickupDistanceKm: input.serviceRadiusKm ?? 25,
          pickupAvailability: input.pickupAvailable ? 'FLEXIBLE' : 'THIS_WEEK',
          operatingHours: {}
        }
      });
      const materials = [...new Set((input.materialsAccepted ?? []).map(value => value.trim().toUpperCase()).filter(value => acceptedMaterials.has(value)))];
      for (const category of materials) {
        await tx.recyclerMaterial.create({ data: { recyclerId: profile.id, category: category as any, subcategories: [], minAcceptableWeight: 0.1, maxAcceptableWeight: 500 } });
      }
      user = await tx.user.create({
        data: {
          phone,
          email: accountEmail,
          passwordHash: null,
          role: 'RECYCLER',
          preferredLanguage: preferredLanguage as any,
          recyclerProfileId: profile.id
        }
      });
      return this.issuePhone(user, { ...profile, materials, rates: [] });
    });
  }

  private async issuePhone(user: any, profile: any) {
    const profileId = user.role === 'RECYCLER' ? user.recyclerProfileId : user.collectorProfileId;
    if (!profileId) throw new AppError('INTERNAL_SERVER_ERROR', 'Account profile is incomplete', 500);
    const issued = this.sessions ? await this.sessions.issue(profileId, user.role) : { token: user.role === 'RECYCLER' ? this.jwt.generateRecyclerToken(profileId) : this.jwt.generateToken(profileId) };
    const account = publicPhoneProfile(user, profile);
    return { ...issued, user: account, collector: user.role === 'COLLECTOR' ? profile : null };
  }

  async refresh(refreshToken: string, ip: string) { if (!this.sessions) throw new AppError('INTERNAL_SERVER_ERROR', 'Session rotation is unavailable', 503); await this.limiter.check(refreshToken, ip, 'login'); return this.sessions.rotate(refreshToken); }
  logout(refreshToken?: string) { return this.sessions?.revoke(refreshToken) ?? Promise.resolve(); }
}
