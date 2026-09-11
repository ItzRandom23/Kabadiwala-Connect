import type { RequestHandler } from 'express';
import { AppError } from '../utils/errors.js';
import type { JwtService } from '../services/jwt.js';
import type { CollectorRepository } from '../repositories/collectorRepository.js';
import type { PrismaClient } from '@prisma/client';
export const requireAuth = (jwtService: JwtService, collectors: CollectorRepository): RequestHandler => async (req, _res, next) => { const header = req.header('authorization'); if (!header?.startsWith('Bearer ')) return next(new AppError('AUTHENTICATION_REQUIRED', 'Bearer token required', 401)); try { const identity = jwtService.verifyToken(header.slice(7)); if (identity.role !== 'COLLECTOR') return next(new AppError('AUTHORIZATION_ERROR', 'Collector access required', 403)); const collector = await collectors.findById(identity.collectorId); if (!collector) return next(new AppError('COLLECTOR_NOT_FOUND', 'Collector not found', 404)); if (collector.accountStatus === 'SUSPENDED') return next(new AppError('ACCOUNT_SUSPENDED', 'Account is suspended', 403)); if (collector.accountStatus === 'DELETED') return next(new AppError('ACCOUNT_DELETED', 'Account is deleted', 403)); req.identity = identity; next(); } catch { next(new AppError('TOKEN_INVALID', 'Invalid or expired token', 401)); } };
export const requireRole = (role: 'ADMIN'): RequestHandler => (req, _res, next) => { if (req.identity?.role !== role) return next(new AppError('AUTHORIZATION_ERROR', 'Admin access required', 403)); next(); };
export const requireAdmin = (jwtService: JwtService): RequestHandler => (req, _res, next) => { const header = req.header('authorization'); if (!header?.startsWith('Bearer ')) return next(new AppError('AUTHENTICATION_REQUIRED', 'Bearer token required', 401)); try { const identity = jwtService.verifyToken(header.slice(7)); if (identity.role !== 'ADMIN') return next(new AppError('AUTHORIZATION_ERROR', 'Admin access required', 403)); req.identity = identity; next(); } catch { next(new AppError('TOKEN_INVALID', 'Invalid or expired token', 401)); } };
export const requireRecycler = (jwtService: JwtService, db?: PrismaClient): RequestHandler => async (req, _res, next) => {
  const header = req.header('authorization');
  if (!header?.startsWith('Bearer ')) return next(new AppError('AUTHENTICATION_REQUIRED', 'Bearer token required', 401));
  try {
    const identity = jwtService.verifyToken(header.slice(7));
    if (identity.role !== 'RECYCLER') return next(new AppError('AUTHORIZATION_ERROR', 'Recycler access required', 403));
    if (db) {
      const [user, recycler] = await Promise.all([
        db.user.findFirst({ where: { recyclerProfileId: identity.collectorId }, select: { accountStatus: true } }),
        db.recycler.findUnique({ where: { id: identity.collectorId }, select: { authorizationStatus: true } })
      ]);
      if (!user || !recycler) return next(new AppError('RECYCLER_NOT_FOUND', 'Recycler not found', 404));
      if (user.accountStatus === 'SUSPENDED') return next(new AppError('ACCOUNT_SUSPENDED', 'Account is suspended', 403));
      if (user.accountStatus === 'DELETED') return next(new AppError('ACCOUNT_DELETED', 'Account is deleted', 403));
      if (recycler.authorizationStatus !== 'VERIFIED') return next(new AppError('RECYCLER_NOT_VERIFIED', 'Verified recycler access required', 403));
    }
    req.identity = identity;
    next();
  } catch {
    next(new AppError('TOKEN_INVALID', 'Invalid or expired token', 401));
  }
};
export const requireAccount = (jwtService: JwtService, db?: PrismaClient): RequestHandler => async (req, _res, next) => {
  const header = req.header('authorization');
  if (!header?.startsWith('Bearer ')) return next(new AppError('AUTHENTICATION_REQUIRED', 'Bearer token required', 401));
  try {
    const identity = jwtService.verifyToken(header.slice(7));
    if (!['COLLECTOR', 'RECYCLER'].includes(identity.role)) return next(new AppError('AUTHORIZATION_ERROR', 'Role account required', 403));
    if (db) {
      if (identity.role === 'COLLECTOR') {
        const collector = await db.collector.findUnique({ where: { id: identity.collectorId }, select: { accountStatus: true } });
        if (!collector) return next(new AppError('COLLECTOR_NOT_FOUND', 'Collector not found', 404));
        if (collector.accountStatus === 'SUSPENDED') return next(new AppError('ACCOUNT_SUSPENDED', 'Account is suspended', 403));
        if (collector.accountStatus === 'DELETED') return next(new AppError('ACCOUNT_DELETED', 'Account is deleted', 403));
      } else {
        const user = await db.user.findFirst({ where: { recyclerProfileId: identity.collectorId }, select: { accountStatus: true } });
        const recycler = await db.recycler.findUnique({ where: { id: identity.collectorId }, select: { authorizationStatus: true } });
        if (!user || !recycler) return next(new AppError('RECYCLER_NOT_FOUND', 'Recycler not found', 404));
        if (user.accountStatus === 'SUSPENDED') return next(new AppError('ACCOUNT_SUSPENDED', 'Account is suspended', 403));
        if (user.accountStatus === 'DELETED') return next(new AppError('ACCOUNT_DELETED', 'Account is deleted', 403));
        if (recycler.authorizationStatus !== 'VERIFIED') return next(new AppError('RECYCLER_NOT_VERIFIED', 'Verified recycler access required', 403));
      }
    }
    req.identity = identity;
    next();
  } catch {
    next(new AppError('TOKEN_INVALID', 'Invalid or expired token', 401));
  }
};
