import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import { resolve } from 'node:path';
import type { PrismaClient } from '@prisma/client';
import type { AppConfig } from './config/env.js';
import { requestContext } from './middleware/request.js';
import { notFound, errorHandler } from './middleware/errors.js';
import { healthRoutes } from './routes/healthRoutes.js';
import { collectorRoutes } from './routes/collectorRoutes.js';
import { authRoutes } from './routes/authRoutes.js';
import { lotRoutes } from './routes/lotRoutes.js';
import { priceRoutes } from './routes/priceRoutes.js';
import { recyclerRoutes } from './routes/recyclerRoutes.js';
import { quoteRoutes } from './routes/quoteRoutes.js';
import { handoverRoutes } from './routes/handoverRoutes.js';
import { paymentRoutes } from './routes/paymentRoutes.js';
import { syncRoutes } from './routes/syncRoutes.js';
import { futureRoutes } from './routes/futureRoutes.js';
import { datasetRoutes } from './routes/datasetRoutes.js';
import { notificationRoutes } from './routes/notificationRoutes.js';
import { transactionRoutes } from './routes/transactionRoutes.js';
import { activityRoutes } from './routes/activityRoutes.js';
import { supplyChainRoutes } from './routes/supplyChainRoutes.js';
import type { JwtService } from './services/jwt.js';
import type { CollectorService } from './services/collectorService.js';
import type { AuthService } from './services/authService.js';
import type { CollectorRepository } from './repositories/collectorRepository.js';
import type { LotService } from './services/lotService.js';
import type { PriceService } from './services/priceService.js';
import type { RecyclerService } from './services/recyclerService.js';
import type { QuoteService } from './services/quoteService.js';
import type { HandoverService } from './services/handoverService.js';
import type { PaymentService } from './services/paymentService.js';
import type { SyncService } from './services/syncService.js';
import type { EmailAuthService } from './services/emailAuthService.js';

export function createApp(
  config: AppConfig,
  db: PrismaClient,
  jwt: JwtService,
  collectorService: CollectorService,
  authService?: AuthService,
  collectorRepository?: CollectorRepository,
  lotService?: LotService,
  priceService?: PriceService,
  recyclerService?: RecyclerService,
  quoteService?: QuoteService,
  handoverService?: HandoverService,
  paymentService?: PaymentService,
  syncService?: SyncService,
  emailAuthService?: EmailAuthService
) {
  const app = express();
  app.disable('x-powered-by');
  app.use(helmet());
  const allowedOrigins = config.CORS_ORIGIN.split(',').map(origin => origin.trim()).filter(Boolean);
  app.use(cors({
    origin: (origin, callback) => {
      const allowed = !origin
        || (config.NODE_ENV !== 'production' && config.CORS_ORIGIN === '*')
        || allowedOrigins.includes(origin);
      if (allowed) return callback(null, true);
      return callback(null, false);
    },
    credentials: false,
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS']
  }));
  if (config.STORAGE_PROVIDER === 'local' && config.LOCAL_UPLOAD_PUBLIC) {
    app.use('/uploads', express.static(resolve(process.cwd(), config.LOCAL_UPLOAD_DIR), { index: false }));
  }
  // Optional APK update channel. Deployers can place update.json and the APK
  // named by it in backend/app-update without exposing the rest of the server.
  app.use('/app', express.static(resolve(process.cwd(), 'app-update'), { index: false }));
  app.use(express.json({ limit: '256kb' }));
  app.use(requestContext);

  const api = express.Router();
  api.use(healthRoutes(db, config));
  if (authService) api.use('/auth', authRoutes(authService, emailAuthService, jwt, db));
  if (collectorRepository) api.use('/collectors', collectorRoutes(jwt, collectorRepository, collectorService));
  if (collectorRepository) api.use(supplyChainRoutes(jwt, collectorRepository, db));
  if (lotService && collectorRepository) api.use('/lots', lotRoutes(jwt, collectorRepository, lotService));
  if (priceService && collectorRepository) api.use(priceRoutes(jwt, collectorRepository, priceService, db));
  if (recyclerService && collectorRepository) api.use(recyclerRoutes(jwt, collectorRepository, recyclerService, db));
  if (quoteService && collectorRepository) api.use(quoteRoutes(jwt, collectorRepository, quoteService, db));
  if (handoverService && collectorRepository) api.use(handoverRoutes(jwt, collectorRepository, handoverService, db));
  if (paymentService && collectorRepository) api.use(paymentRoutes(jwt, collectorRepository, paymentService, db));
  if (syncService && collectorRepository) api.use(syncRoutes(jwt, collectorRepository, syncService));
  if (collectorRepository) api.use(transactionRoutes(jwt, collectorRepository, db));
  api.use('/future', futureRoutes(jwt, db));
  api.use('/notifications', notificationRoutes(jwt, db));
  api.use('/activity', activityRoutes(jwt, db));
  api.use(datasetRoutes(jwt, db));
  app.use('/api/v1', api);
  app.use(notFound);
  app.use(errorHandler);
  return app;
}
