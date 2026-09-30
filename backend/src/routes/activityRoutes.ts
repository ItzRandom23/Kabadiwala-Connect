import { Router } from 'express';
import type { PrismaClient } from '@prisma/client';
import type { JwtService } from '../services/jwt.js';
import { requireAccount } from '../middleware/auth.js';
import { ActivityService } from '../services/activityService.js';
import { AppError } from '../utils/errors.js';

export const activityRoutes = (jwt: JwtService, db: PrismaClient) => {
  const service = new ActivityService(db);
  return Router()
    .use(requireAccount(jwt, db, false))
    .get('/changes', async (req, res) => {
      const raw = typeof req.query.since === 'string' ? req.query.since : undefined;
      const since = raw ? new Date(raw) : undefined;
      if (since && !Number.isFinite(since.getTime())) throw new AppError('VALIDATION_ERROR', 'Invalid activity cursor', 400, { code: 'INVALID_ACTIVITY_CURSOR' });
      const cursor = typeof req.query.cursor === 'string' ? req.query.cursor : undefined;
      if (cursor && since) throw new AppError('VALIDATION_ERROR', 'Use cursor or since, not both', 400);
      res.json({ success: true, data: await service.changes(req.identity as any, since, cursor), message: 'Activity changes retrieved' });
    });
};
