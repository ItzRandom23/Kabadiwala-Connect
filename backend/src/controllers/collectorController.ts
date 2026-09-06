import type { Request, Response } from 'express';
import type { CollectorService, ProfileUpdate } from '../services/collectorService.js';
import { languageSchema, latitudeSchema, longitudeSchema, areaNameSchema } from '../utils/validation.js';
import { AppError } from '../utils/errors.js';

function present(c: any) {
  return {
    id: c.id,
    phone: c.phone,
    email: c.email ?? null,
    displayName: c.displayName ?? null,
    preferredLanguage: c.preferredLanguage,
    primaryLocation: {
      latitude: c.latitude === null ? null : Number(c.latitude),
      longitude: c.longitude === null ? null : Number(c.longitude),
      areaName: c.areaName || null
    },
    accountStatus: c.accountStatus,
    createdAt: c.createdAt.toISOString(),
    lastLoginAt: c.lastLoginAt?.toISOString() ?? null
  };
}

export const collectorController = (service: CollectorService) => ({
  me: async (req: Request, res: Response) => res.json({ success: true, data: present(await service.getMe(req.identity!.collectorId)), message: 'Collector retrieved' }),
  update: async (req: Request, res: Response) => {
    const body = req.body ?? {};
    if (body.preferredLanguage !== undefined && !languageSchema.safeParse(body.preferredLanguage).success) throw new AppError('VALIDATION_ERROR', 'Invalid language', 400, { code: 'INVALID_LANGUAGE' });
    if (body.displayName !== undefined && (typeof body.displayName !== 'string' || body.displayName.trim().length > 160)) throw new AppError('VALIDATION_ERROR', 'Invalid name', 400, { code: 'INVALID_NAME' });
    if (body.email !== undefined && body.email !== null && (typeof body.email !== 'string' || !/^\S+@\S+\.\S+$/.test(body.email.trim()) || body.email.trim().length > 254)) throw new AppError('VALIDATION_ERROR', 'Invalid email', 400, { code: 'INVALID_EMAIL' });
    const loc = body.primaryLocation;
    if (loc !== undefined) {
      if (!loc || typeof loc !== 'object' || !areaNameSchema.safeParse(loc.areaName).success || (loc.latitude !== undefined && loc.latitude !== null && !latitudeSchema.safeParse(loc.latitude).success) || (loc.longitude !== undefined && loc.longitude !== null && !longitudeSchema.safeParse(loc.longitude).success)) throw new AppError('VALIDATION_ERROR', 'Invalid location', 400, { code: 'INVALID_LOCATION' });
    }
    const update: ProfileUpdate = { preferredLanguage: body.preferredLanguage, primaryLocation: loc, displayName: body.displayName?.trim(), email: body.email?.trim().toLowerCase() };
    const result = await service.updateProfile(req.identity!.collectorId, update);
    console.log(JSON.stringify({ event: 'profile_updated', collectorId: req.identity!.collectorId }));
    return res.json({ success: true, data: present(result), message: 'Profile updated' });
  }
});
