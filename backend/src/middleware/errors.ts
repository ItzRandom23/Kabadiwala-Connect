import type { ErrorRequestHandler, RequestHandler } from 'express';
import { ZodError } from 'zod';
import { AppError } from '../utils/errors.js';
export const notFound: RequestHandler = (_req, _res, next) => next(new AppError('NOT_FOUND', 'Route not found', 404));
export const errorHandler: ErrorRequestHandler = (error, req, res, _next) => { const isProd = process.env.NODE_ENV === 'production'; const requestId = req.requestId; if (error instanceof ZodError) return res.status(400).json({ success: false, error: { code: 'VALIDATION_ERROR', message: 'Invalid request', details: error.flatten() } }); const appError = error instanceof AppError ? error : new AppError('INTERNAL_SERVER_ERROR', 'Internal server error', 500); if (!isProd) console.error(`[${requestId ?? 'no-request-id'}]`, error); return res.status(appError.status).json({ success: false, error: { code: appError.code, message: appError.message, ...(appError.details ? { details: appError.details } : {}) } }); };
