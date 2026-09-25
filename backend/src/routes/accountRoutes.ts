import { Router } from 'express';
import { z } from 'zod';
import type { PrismaClient } from '@prisma/client';
import type { JwtService } from '../services/jwt.js';
import { requireAccount } from '../middleware/auth.js';
import { AppError } from '../utils/errors.js';
import { languageSchema } from '../utils/validation.js';
import { AccountProfileService } from '../services/accountProfileService.js';

const updateSchema = z.object({
  displayName: z.string().trim().min(1).max(160).optional(),
  email: z.union([z.string().trim().email().max(254), z.null()]).optional(),
  areaName: z.string().trim().min(1).max(160).optional(),
  address: z.string().trim().max(240).optional(),
  latitude: z.number().finite().min(-90).max(90).nullable().optional(),
  longitude: z.number().finite().min(-180).max(180).nullable().optional(),
  preferredLanguage: z.string().trim().transform(value => value.toUpperCase()).pipe(languageSchema).optional()
});

export const accountRoutes = (jwt: JwtService, db: PrismaClient) => {
  const service = new AccountProfileService(db);
  return Router().put('/account/profile', requireAccount(jwt, db, false), async (req, res) => {
    if (!req.identity || !['HOUSEHOLD', 'COLLECTOR', 'RECYCLER'].includes(req.identity.role)) {
      throw new AppError('AUTHENTICATION_REQUIRED', 'A role account is required', 401);
    }
    const parsed = updateSchema.safeParse(req.body ?? {});
    if (!parsed.success) throw new AppError('VALIDATION_ERROR', 'Enter valid account details', 422, { code: 'INVALID_ACCOUNT_DETAILS' });
    const data = await service.update(req.identity.collectorId, req.identity.role as 'HOUSEHOLD' | 'COLLECTOR' | 'RECYCLER', parsed.data);
    return res.json({ success: true, data, message: 'Account details updated' });
  });
};
