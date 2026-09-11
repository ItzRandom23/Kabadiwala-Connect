import { Router } from 'express';
import type { JwtService } from '../services/jwt.js';
import type { CollectorRepository } from '../repositories/collectorRepository.js';
import type { HandoverService } from '../services/handoverService.js';
import type { PrismaClient } from '@prisma/client';
import { requireAdmin, requireAuth, requireRecycler } from '../middleware/auth.js';
import { handoverController } from '../controllers/handoverController.js';

export const handoverRoutes = (j: JwtService, c: CollectorRepository, s: HandoverService, db?: PrismaClient) => {
  const x = handoverController(s);
  return Router()
    .post('/verify/handover', x.verifyPublic)
    .post('/handovers', requireAuth(j, c), x.create)
    .get('/handovers/reference/:referenceId', requireAuth(j, c), x.getReference)
    .get('/handovers/:handoverId', requireAuth(j, c), x.get)
    .post('/handovers/:handoverId/mark-handed-over', requireAuth(j, c), x.mark)
    .put('/handovers/:handoverId/evidence', requireAuth(j, c), x.evidence)
    .post('/handovers/:handoverId/dispute', requireAuth(j, c), x.dispute)
    .get('/recycler/handovers', requireRecycler(j, db), x.recyclerList)
    .get('/recycler/handovers/:handoverId', requireRecycler(j, db), x.recyclerGet)
    .post('/recycler/handovers/:handoverId/confirm', requireRecycler(j, db), x.confirm)
    .post('/recycler/handovers/:handoverId/reject', requireRecycler(j, db), x.reject)
    .get('/admin/disputes', requireAdmin(j), x.adminList)
    .get('/admin/disputes/:disputeId', requireAdmin(j), x.adminDetail)
    .post('/admin/disputes/:disputeId/resolve', requireAdmin(j), x.resolve);
};
