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
import { RecyclerService } from './services/recyclerService.js';
import { QuoteService } from './services/quoteService.js';
import { HandoverService } from './services/handoverService.js';
import { PaymentService } from './services/paymentService.js';
import { SyncService } from './services/syncService.js';
import { EmailAuthService } from './services/emailAuthService.js';

const config = loadConfig();
const collectors = new CollectorRepository(prisma);
const jwt = new JwtService(config);
let storage: StorageService;

try {
  storage = config.STORAGE_PROVIDER === 'local' ? new LocalStorageService(config) : new S3StorageService(config);
} catch {
  storage = {
    putImage: async () => { throw new Error('Configured photo storage is not available'); },
    delete: async () => undefined
  };
}

const paymentService = new PaymentService(prisma);
const app = createApp(
  config,
  prisma,
  jwt,
  new CollectorService(collectors),
  new AuthService(createOtpProvider(config), collectors, jwt, undefined, prisma),
  collectors,
  new LotService(new LotRepository(prisma), storage, prisma),
  new PriceService(new PriceRepository(prisma), prisma),
  new RecyclerService(prisma),
  new QuoteService(prisma),
  new HandoverService(prisma),
  paymentService,
  new SyncService(prisma, paymentService),
  new EmailAuthService(prisma, jwt)
);

const server = app.listen(config.PORT, () => console.log(`Kabadiwala backend listening on port ${config.PORT}`));
const shutdown = async () => {
  server.close(async () => {
    await prisma.$disconnect();
    process.exit(0);
  });
};
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
