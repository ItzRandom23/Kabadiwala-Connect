import type { Request, Response } from 'express';
import type { PrismaClient } from '@prisma/client';
import type { AppConfig } from '../config/env.js';

const databaseStatus = async (db: PrismaClient) => {
  try {
    await db.$runCommandRaw({ ping: 1 });
    return 'connected' as const;
  } catch {
    return 'unavailable' as const;
  }
};

export const healthController = (db: PrismaClient, config: AppConfig) => async (_req: Request, res: Response) => {
  const database = await databaseStatus(db);
  return res.status(database === 'connected' ? 200 : 503).json({
    success: true,
    data: { status: database === 'connected' ? 'healthy' : 'degraded', database, timestamp: new Date().toISOString(), version: config.APP_VERSION },
    message: 'Health check complete'
  });
};

export const readinessController = (db: PrismaClient, config: AppConfig, storageReady: boolean) => async (_req: Request, res: Response) => {
  const database = await databaseStatus(db);
  const checks = {
    database: database === 'connected',
    storage: storageReady,
    otpProvider: config.NODE_ENV !== 'production' || config.OTP_PROVIDER !== 'development',
    rateLimitStore: config.NODE_ENV !== 'production' || config.RATE_LIMIT_STORE === 'database'
  };
  const ready = Object.values(checks).every(Boolean);
  return res.status(ready ? 200 : 503).json({
    success: ready,
    data: { status: ready ? 'ready' : 'not_ready', checks, timestamp: new Date().toISOString(), version: config.APP_VERSION },
    message: ready ? 'Readiness check complete' : 'One or more production dependencies are unavailable'
  });
};
