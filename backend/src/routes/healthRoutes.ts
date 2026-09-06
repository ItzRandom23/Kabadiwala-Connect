import { Router } from 'express'; import type { PrismaClient } from '@prisma/client'; import type { AppConfig } from '../config/env.js'; import { healthController } from '../controllers/healthController.js';
export const healthRoutes = (db: PrismaClient, config: AppConfig) => Router().get('/health', healthController(db, config));
