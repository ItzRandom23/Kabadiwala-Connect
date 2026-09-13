import { Router } from 'express';
import type { JwtService } from '../services/jwt.js';
import type { CollectorRepository } from '../repositories/collectorRepository.js';
import type { RecyclerService } from '../services/recyclerService.js';
import type { PrismaClient } from '@prisma/client';
import { requireAdmin, requireAuth, requireRecycler } from '../middleware/auth.js';
import { recyclerController } from '../controllers/recyclerController.js';

export const recyclerRoutes = (jwt: JwtService, c: CollectorRepository, s: RecyclerService, db?: PrismaClient) => {
  const x = recyclerController(s);
  return Router()
    // A pending or rejected recycler must be able to read its own profile and
    // submit evidence. Operational recycler actions remain verified-only.
    .get('/recycler/profile', requireRecycler(jwt, db, false), x.selfProfile)
    .post('/recycler/verification-request', requireRecycler(jwt, db, false), x.submitVerificationRequest)
    .patch('/recycler/profile', requireRecycler(jwt, db), x.updateProfile)
    .put('/recycler/rates', requireRecycler(jwt, db), x.updateRates)
    .get('/recyclers', requireAuth(jwt, c), x.list)
    .get('/recyclers/match', requireAuth(jwt, c), x.match)
    .get('/recyclers/:recyclerId', requireAuth(jwt, c), x.detail)
    .get('/admin/recyclers', requireAdmin(jwt, db, 'RECYCLER_REVIEW'), x.adminList)
    .get('/admin/recyclers/:recyclerId', requireAdmin(jwt, db, 'RECYCLER_REVIEW'), x.adminDetail)
    .put('/admin/recyclers/:recyclerId/authorization', requireAdmin(jwt, db, 'RECYCLER_AUTHORIZATION'), x.authorize);
};
