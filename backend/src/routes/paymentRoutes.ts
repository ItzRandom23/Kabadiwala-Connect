import { Router } from 'express';
import type { JwtService } from '../services/jwt.js';
import type { CollectorRepository } from '../repositories/collectorRepository.js';
import type { PaymentService } from '../services/paymentService.js';
import { requireAdmin, requireAuth } from '../middleware/auth.js';
import { paymentController } from '../controllers/paymentController.js';

export const paymentRoutes = (jwt: JwtService, c: CollectorRepository, s: PaymentService, db?: import('@prisma/client').PrismaClient) => {
  const x = paymentController(s, db);
  return Router()
    .post('/payments/record', requireAuth(jwt, c, db), x.record)
    .get('/payments', requireAuth(jwt, c, db), x.list)
    .get('/payments/:paymentId', requireAuth(jwt, c, db), x.get)
    .put('/payments/:paymentId', requireAuth(jwt, c, db), x.edit)
    .post('/payments/:paymentId/dispute', requireAuth(jwt, c, db), x.dispute)
    .get('/earnings/ledger', requireAuth(jwt, c, db), x.ledger)
    .get('/admin/payments', requireAdmin(jwt, db, 'PAYMENT_VERIFICATION'), x.adminList)
    .get('/admin/payments/:paymentId', requireAdmin(jwt, db, 'PAYMENT_VERIFICATION'), x.adminGet)
    .post('/admin/payments/:paymentId/verify', requireAdmin(jwt, db, 'PAYMENT_VERIFICATION'), x.verify);
};
