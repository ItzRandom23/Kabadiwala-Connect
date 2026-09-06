import type { PrismaClient } from '@prisma/client';
import { AppError } from '../utils/errors.js';
import type { CollectorRepository } from '../repositories/collectorRepository.js';
import type { JwtService } from './jwt.js';
import type { OtpProvider } from './otp.js';
import { OtpRateLimiter } from './rateLimiter.js';

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
    private readonly limiter = new OtpRateLimiter(),
    private readonly db?: PrismaClient
  ) {}

  async requestOtp(phone: string, ip: string) {
    this.limiter.check(phone, ip, 'request');
    await this.otp.request(phone);
    return 'OTP sent successfully';
  }

  async verifyOtp(phone: string, code: string, ip: string, input?: PhoneAccountInput) {
    this.limiter.check(phone, ip, 'verify');
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
      return { token: this.jwt.generateToken(collector.id), collector, user: null };
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
        // The phone OTP is the verified identity boundary. Email is optional
        // for phone accounts, so a duplicate email must not block signup;
        // retry without attaching that email. This also makes a concurrent
        // double-tap/retry idempotent after the first transaction commits.
        try {
          return await this.verifyPhoneAccount(phone, { ...input, email: undefined });
        } catch (retryError) {
          if (retryError instanceof AppError) throw retryError;
          if ((retryError as { code?: string })?.code === 'P2002') {
            throw new AppError('CONFLICT', 'This mobile number is already linked to another account', 409, { code: 'ACCOUNT_CONFLICT' });
          }
          throw retryError;
        }
      }
      if (error instanceof AppError) throw error;
      throw error;
    }
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
      let user = await tx.user.findUnique({ where: { phone } });

      // Link legacy OTP-created records to the account identity model without
      // creating a second account for the same verified mobile number.
      if (!user) {
        const legacyCollector = await tx.collector.findFirst({ where: { phone } });
        if (legacyCollector) {
          user = await tx.user.findFirst({ where: { collectorProfileId: legacyCollector.id } });
          if (!user) {
            if (email) {
              const emailOwner = await tx.user.findUnique({ where: { email } });
              const collectorEmailOwner = await tx.collector.findUnique({ where: { email } });
              if (emailOwner || (collectorEmailOwner && collectorEmailOwner.id !== legacyCollector.id)) {
                throw new AppError('CONFLICT', 'An account already exists for this email', 409, { code: 'EMAIL_IN_USE' });
              }
            }
            user = await tx.user.create({
              data: {
                phone,
                email,
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
            user = await tx.user.create({
              data: {
                phone,
                email,
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
        if (email && email !== user.email) {
          const emailOwner = await tx.user.findUnique({ where: { email } });
          if (emailOwner && emailOwner.id !== user.id) throw new AppError('CONFLICT', 'An account already exists for this email', 409, { code: 'EMAIL_IN_USE' });
          const collectorEmailOwner = await tx.collector.findUnique({ where: { email } });
          if (collectorEmailOwner && collectorEmailOwner.id !== user.collectorProfileId) throw new AppError('CONFLICT', 'An account already exists for this email', 409, { code: 'EMAIL_IN_USE' });
          user = await tx.user.update({ where: { id: user.id }, data: { email } });
        }

        if (user.role === 'COLLECTOR') {
          await tx.collector.update({
            where: { id: user.collectorProfileId ?? '' },
            data: {
              ...(email ? { email } : {}),
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

      if (email) {
        const emailOwner = await tx.user.findUnique({ where: { email } });
        if (emailOwner) throw new AppError('CONFLICT', 'An account already exists for this email', 409, { code: 'EMAIL_IN_USE' });
        const collectorEmailOwner = await tx.collector.findUnique({ where: { email } });
        if (collectorEmailOwner) throw new AppError('CONFLICT', 'An account already exists for this email', 409, { code: 'EMAIL_IN_USE' });
      }

      if (requestedRole === 'COLLECTOR') {
        const profile = await tx.collector.create({
          data: {
            phone,
            email,
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
            email,
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
          email,
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
          email,
          passwordHash: null,
          role: 'RECYCLER',
          preferredLanguage: preferredLanguage as any,
          recyclerProfileId: profile.id
        }
      });
      return this.issuePhone(user, { ...profile, materials, rates: [] });
    });
  }

  private issuePhone(user: any, profile: any) {
    const profileId = user.role === 'RECYCLER' ? user.recyclerProfileId : user.collectorProfileId;
    if (!profileId) throw new AppError('INTERNAL_SERVER_ERROR', 'Account profile is incomplete', 500);
    const token = user.role === 'RECYCLER' ? this.jwt.generateRecyclerToken(profileId) : this.jwt.generateToken(profileId);
    const account = publicPhoneProfile(user, profile);
    return { token, user: account, collector: user.role === 'COLLECTOR' ? profile : null };
  }
}
