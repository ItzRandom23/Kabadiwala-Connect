import type { RequestHandler } from 'express';
import { AppError } from '../utils/errors.js';
import type { JwtService } from '../services/jwt.js';
import type { CollectorRepository } from '../repositories/collectorRepository.js';
import type { PrismaClient } from '@prisma/client';

const isCurrentRecyclerAuthorization = (authorizationStatus: string, authorizationValidUntil: Date | null | undefined) =>
  authorizationStatus === 'VERIFIED' && (!authorizationValidUntil || authorizationValidUntil > new Date());
/**
 * Legacy collector resources are kabadiwala-owned resources.  A household has
 * a Collector profile for storage compatibility, but that must never turn a
 * household token into a kabadiwala credential.
 */
export const requireAuth = (jwtService: JwtService, collectors: CollectorRepository, db?: PrismaClient): RequestHandler => async (req, _res, next) => { const header = req.header('authorization'); if (!header?.startsWith('Bearer ')) return next(new AppError('AUTHENTICATION_REQUIRED', 'Bearer token required', 401)); try { const identity = jwtService.verifyToken(header.slice(7)); if (identity.role !== 'COLLECTOR') return next(new AppError('AUTHORIZATION_ERROR', 'Kabadiwala access required', 403)); const collector = await collectors.findById(identity.collectorId); if (!collector) return next(new AppError('COLLECTOR_NOT_FOUND', 'Kabadiwala profile not found', 404)); if (collector.accountStatus === 'SUSPENDED') return next(new AppError('ACCOUNT_SUSPENDED', 'Account is suspended', 403)); if (collector.accountStatus === 'DELETED') return next(new AppError('ACCOUNT_DELETED', 'Account is deleted', 403)); if (db) { const user = await db.user.findFirst({ where: { collectorProfileId: identity.collectorId }, select: { role: true, accountStatus: true } }); if (user && user.role !== 'COLLECTOR') return next(new AppError('AUTHORIZATION_ERROR', 'Kabadiwala account linkage is invalid', 403)); if (user?.accountStatus === 'SUSPENDED') return next(new AppError('ACCOUNT_SUSPENDED', 'Account is suspended', 403)); if (user?.accountStatus === 'DELETED') return next(new AppError('ACCOUNT_DELETED', 'Account is deleted', 403)); } req.identity = identity; next(); } catch { next(new AppError('TOKEN_INVALID', 'Invalid or expired token', 401)); } };
export const requireHousehold = (jwtService: JwtService, collectors: CollectorRepository, db?: PrismaClient): RequestHandler => async (req, _res, next) => { const header = req.header('authorization'); if (!header?.startsWith('Bearer ')) return next(new AppError('AUTHENTICATION_REQUIRED', 'Bearer token required', 401)); try { const identity = jwtService.verifyToken(header.slice(7)); if (identity.role !== 'HOUSEHOLD') return next(new AppError('AUTHORIZATION_ERROR', 'Household access required', 403)); const profile = await collectors.findById(identity.collectorId); if (!profile) return next(new AppError('COLLECTOR_NOT_FOUND', 'Household profile not found', 404)); if (profile.accountStatus !== 'ACTIVE') return next(new AppError('ACCOUNT_SUSPENDED', 'Account is unavailable', 403)); if (db) { const user = await db.user.findFirst({ where: { collectorProfileId: identity.collectorId }, select: { role: true, accountStatus: true } }); if (user && user.role !== 'HOUSEHOLD') return next(new AppError('AUTHORIZATION_ERROR', 'Household account linkage is invalid', 403)); if (user && (user.accountStatus === 'SUSPENDED' || user.accountStatus === 'DELETED')) return next(new AppError('ACCOUNT_SUSPENDED', 'Account is unavailable', 403)); } req.identity = identity; next(); } catch { next(new AppError('TOKEN_INVALID', 'Invalid or expired token', 401)); } };
export const requireRole = (role: 'ADMIN'): RequestHandler => (req, _res, next) => { if (req.identity?.role !== role) return next(new AppError('AUTHORIZATION_ERROR', 'Admin access required', 403)); next(); };
export const requireAdmin = (jwtService: JwtService, db?: PrismaClient, permission?: string): RequestHandler => async (req, _res, next) => { const header = req.header('authorization'); if (!header?.startsWith('Bearer ')) return next(new AppError('AUTHENTICATION_REQUIRED', 'Bearer token required', 401)); try { const identity = jwtService.verifyToken(header.slice(7)); if (identity.role !== 'ADMIN') return next(new AppError('AUTHORIZATION_ERROR', 'Admin access required', 403)); if (db) { const admin = await db.adminAccount.findUnique({ where: { id: identity.collectorId }, select: { active: true, permissions: true } }); if (!admin) return next(new AppError('ADMIN_NOT_FOUND', 'Admin account not found', 403)); if (!admin.active) return next(new AppError('ADMIN_SUSPENDED', 'Admin account is inactive', 403)); if (permission && !admin.permissions.includes('*') && !admin.permissions.includes(permission)) return next(new AppError('ADMIN_PERMISSION_REQUIRED', 'Admin permission required for this action', 403, { code: permission })); } req.identity = identity; next(); } catch { next(new AppError('TOKEN_INVALID', 'Invalid or expired token', 401)); } };
export const requireRecycler = (jwtService: JwtService, db?: PrismaClient, requireVerified = true): RequestHandler => async (req, _res, next) => {
  const header = req.header('authorization');
  if (!header?.startsWith('Bearer ')) return next(new AppError('AUTHENTICATION_REQUIRED', 'Bearer token required', 401));
  try {
    const identity = jwtService.verifyToken(header.slice(7));
    if (identity.role !== 'RECYCLER') return next(new AppError('AUTHORIZATION_ERROR', 'Recycler access required', 403));
    if (db) {
      const [user, recycler] = await Promise.all([
        db.user.findFirst({ where: { recyclerProfileId: identity.collectorId }, select: { role: true, accountStatus: true } }),
        db.recycler.findUnique({ where: { id: identity.collectorId }, select: { authorizationStatus: true, authorizationValidUntil: true } })
      ]);
      if (!user || !recycler) return next(new AppError('RECYCLER_NOT_FOUND', 'Recycler not found', 404));
      if (user.role !== 'RECYCLER') return next(new AppError('AUTHORIZATION_ERROR', 'Recycler account linkage is invalid', 403));
      if (user.accountStatus === 'SUSPENDED') return next(new AppError('ACCOUNT_SUSPENDED', 'Account is suspended', 403));
      if (user.accountStatus === 'DELETED') return next(new AppError('ACCOUNT_DELETED', 'Account is deleted', 403));
      if (recycler.authorizationStatus === 'VERIFIED' && recycler.authorizationValidUntil && recycler.authorizationValidUntil <= new Date()) {
        await db.recycler.updateMany({ where: { id: identity.collectorId, authorizationStatus: 'VERIFIED', authorizationValidUntil: recycler.authorizationValidUntil }, data: { authorizationStatus: 'EXPIRED' } });
      }
      if (requireVerified && !isCurrentRecyclerAuthorization(recycler.authorizationStatus, recycler.authorizationValidUntil)) return next(new AppError('RECYCLER_NOT_VERIFIED', 'Verified recycler access required', 403));
    }
    req.identity = identity;
    next();
  } catch {
    next(new AppError('TOKEN_INVALID', 'Invalid or expired token', 401));
  }
};
export const requireAccount = (jwtService: JwtService, db?: PrismaClient, requireVerifiedRecycler = true): RequestHandler => async (req, _res, next) => {
  const header = req.header('authorization');
  if (!header?.startsWith('Bearer ')) return next(new AppError('AUTHENTICATION_REQUIRED', 'Bearer token required', 401));
  try {
    const identity = jwtService.verifyToken(header.slice(7));
    if (!['COLLECTOR', 'HOUSEHOLD', 'RECYCLER'].includes(identity.role)) return next(new AppError('AUTHORIZATION_ERROR', 'Role account required', 403));
    if (db) {
      if (identity.role === 'COLLECTOR' || identity.role === 'HOUSEHOLD') {
        const [collector, user] = await Promise.all([
          db.collector.findUnique({ where: { id: identity.collectorId }, select: { accountStatus: true } }),
          db.user.findFirst({ where: { collectorProfileId: identity.collectorId }, select: { role: true } })
        ]);
        if (!collector) return next(new AppError('COLLECTOR_NOT_FOUND', 'Collector not found', 404));
        if (user && user.role !== identity.role) return next(new AppError('AUTHORIZATION_ERROR', 'Account role linkage is invalid', 403));
        if (collector.accountStatus === 'SUSPENDED') return next(new AppError('ACCOUNT_SUSPENDED', 'Account is suspended', 403));
        if (collector.accountStatus === 'DELETED') return next(new AppError('ACCOUNT_DELETED', 'Account is deleted', 403));
      } else {
        const user = await db.user.findFirst({ where: { recyclerProfileId: identity.collectorId }, select: { role: true, accountStatus: true } });
        const recycler = await db.recycler.findUnique({ where: { id: identity.collectorId }, select: { authorizationStatus: true, authorizationValidUntil: true } });
        if (!user || !recycler) return next(new AppError('RECYCLER_NOT_FOUND', 'Recycler not found', 404));
        if (user.role !== 'RECYCLER') return next(new AppError('AUTHORIZATION_ERROR', 'Recycler account linkage is invalid', 403));
        if (user.accountStatus === 'SUSPENDED') return next(new AppError('ACCOUNT_SUSPENDED', 'Account is suspended', 403));
        if (user.accountStatus === 'DELETED') return next(new AppError('ACCOUNT_DELETED', 'Account is deleted', 403));
        if (recycler.authorizationStatus === 'VERIFIED' && recycler.authorizationValidUntil && recycler.authorizationValidUntil <= new Date()) {
          await db.recycler.updateMany({ where: { id: identity.collectorId, authorizationStatus: 'VERIFIED', authorizationValidUntil: recycler.authorizationValidUntil }, data: { authorizationStatus: 'EXPIRED' } });
        }
        if (requireVerifiedRecycler && !isCurrentRecyclerAuthorization(recycler.authorizationStatus, recycler.authorizationValidUntil)) return next(new AppError('RECYCLER_NOT_VERIFIED', 'Verified recycler access required', 403));
      }
    }
    req.identity = identity;
    next();
  } catch {
    next(new AppError('TOKEN_INVALID', 'Invalid or expired token', 401));
  }
};
