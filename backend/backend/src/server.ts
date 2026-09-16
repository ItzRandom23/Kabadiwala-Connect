import 'dotenv/config';
import { loadConfig } from './config/env.js';
import { prisma } from './config/prisma.js';
import { JwtService } from './services/jwt.js';
import { CollectorRepository } from './repositories/collectorRepository.js';
import { CollectorService } from './services/collectorService.js';
import { createApp } from './app.js';
import { createOtpProvider } from './services/otp.js';
import { AuthService } from './services/authService.js';
import { LotRepository } from './repositories/lotRepository.js';
import { LotService } from './services/lotService.js';
import { LocalStorageService, S3StorageService, type StorageService } from './services/storage.js';
import { PriceRepository } from './repositories/priceRepository.js';
import { PriceService } from './services/priceService.js';
import { RecyclerService, expireStaleRecyclerAuthorizations } from './services/recyclerService.js';
import { QuoteService } from './services/quoteService.js';
import { HandoverService } from './services/handoverService.js';
import { PaymentService } from './services/paymentService.js';
import { SyncService } from './services/syncService.js';
import { EmailAuthService } from './services/emailAuthService.js';
import { ensureOptionalUniqueIndexes } from './config/mongoIndexes.js';
import { DatabaseAuthenticationRateLimiter, OtpRateLimiter } from './services/rateLimiter.js';
import { SessionService } from './services/sessionService.js';

const config = loadConfig();
// Index maintenance is a best-effort startup task. Some MongoDB deployments
// return BSON types from listIndexes that Prisma's raw-command decoder cannot
// deserialize (for example, a tagged cursor id). Do not prevent the HTTP API
// from starting when that optional maintenance step fails; the database remains
// usable and the index migration can be retried separately.
try {
  await ensureOptionalUniqueIndexes(prisma);
} catch (error) {
  console.warn('Optional MongoDB index maintenance skipped:', error);
}
const collectors = new CollectorRepository(prisma);
const jwt = new JwtService(config);
let storage: StorageService;

try {
  storage = config.STORAGE_PROVIDER === 'local' ? new LocalStorageService(config) : new S3StorageService(config);
} catch {
  storage = {
    putImage: async () => { throw new Error('Configured photo storage is not available'); },
    getImage: async () => { throw new Error('Configured photo storage is not available'); },
    delete: async () => undefined
  };
}

const paymentService = new PaymentService(prisma);
const authenticationRateLimiter = config.RATE_LIMIT_STORE === 'database' ? new DatabaseAuthenticationRateLimiter(prisma) : new OtpRateLimiter();
const sessionService = new SessionService(prisma, jwt, config);
const app = createApp(
  config,
  prisma,
  jwt,
  new CollectorService(collectors),
  new AuthService(createOtpProvider(config), collectors, jwt, authenticationRateLimiter, prisma, sessionService),
  collectors,
  new LotService(new LotRepository(prisma), storage, prisma),
  new PriceService(new PriceRepository(prisma), prisma),
  new RecyclerService(prisma),
  new QuoteService(prisma),
  new HandoverService(prisma, config.TRACEABILITY_SIGNING_SECRET, authenticationRateLimiter, storage),
  paymentService,
  new SyncService(prisma, paymentService, config.TRACEABILITY_SIGNING_SECRET),
  new EmailAuthService(prisma, jwt, sessionService, authenticationRateLimiter)
);

const runRecyclerFreshnessSweep = () => expireStaleRecyclerAuthorizations(prisma)
  .then(expired => { if (expired) console.log(`Expired ${expired} stale Recycler authorization(s)`); })
  .catch(error => console.warn('Recycler authorization freshness sweep skipped:', error));
void runRecyclerFreshnessSweep();
const recyclerFreshnessTimer = setInterval(runRecyclerFreshnessSweep, 60 * 60 * 1000);
recyclerFreshnessTimer.unref();

// Bind explicitly to IPv4 so Android emulators can reach the local development
// server through 10.0.2.2. This remains a local/SIH prototype server; deployment
// exposure is controlled separately by the hosting environment.
const server = app.listen(config.PORT, '0.0.0.0', () => console.log(`Kabadiwala backend listening on port ${config.PORT}`));
const shutdown = async () => {
  clearInterval(recyclerFreshnessTimer);
  server.close(async () => {
    await prisma.$disconnect();
    process.exit(0);
  });
};
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
