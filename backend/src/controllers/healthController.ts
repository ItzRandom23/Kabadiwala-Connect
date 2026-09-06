import type { Request, Response } from 'express';
import type { PrismaClient } from '@prisma/client';
import type { AppConfig } from '../config/env.js';
export const healthController = (db: PrismaClient, config: AppConfig) => async (_req: Request, res: Response) => { let database = 'connected'; try { await db.$runCommandRaw({ ping: 1 }); } catch { database = 'unavailable'; } return res.status(database === 'connected' ? 200 : 503).json({ success: true, data: { status: database === 'connected' ? 'healthy' : 'degraded', database, timestamp: new Date().toISOString(), version: config.APP_VERSION }, message: 'Health check complete' }); };
