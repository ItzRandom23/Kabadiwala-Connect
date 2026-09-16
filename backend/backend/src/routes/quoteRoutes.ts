import { Router } from 'express';
import type { JwtService } from '../services/jwt.js';
import type { CollectorRepository } from '../repositories/collectorRepository.js';
import type { PrismaClient } from '@prisma/client';
import type { QuoteService } from '../services/quoteService.js';
import { requireAuth, requireRecycler } from '../middleware/auth.js';
import { quoteController } from '../controllers/quoteController.js';

export const quoteRoutes = (jwt: JwtService, c: CollectorRepository, s: QuoteService, db?: PrismaClient) => {
  const x = quoteController(s, db);
  return Router()
    .post('/quotes/request', requireAuth(jwt, c, db), x.request)
    .post('/quotes/request-batch', requireAuth(jwt, c, db), x.requestBatch)
    .get('/quotes/pending', requireAuth(jwt, c, db), x.pending)
    .get('/quotes/:quoteId', requireAuth(jwt, c, db), x.detail)
    .post('/quotes/:quoteId/accept', requireAuth(jwt, c, db), x.accept)
    .post('/quotes/:quoteId/reject', requireAuth(jwt, c, db), x.reject)
    .get('/recycler/quote-requests', requireRecycler(jwt, db), x.recyclerRequests)
    .get('/recycler/quote-requests/:requestId', requireRecycler(jwt, db), x.recyclerDetail)
    .post('/recycler/quotes', requireRecycler(jwt, db), x.submit);
};
