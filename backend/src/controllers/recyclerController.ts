import type { Request, Response } from 'express';
import { z } from 'zod';
import { RecyclerService } from '../services/recyclerService.js';
import { AppError } from '../utils/errors.js';

const mat = z.enum(['CRT', 'LCD_PANEL', 'PCB', 'CABLE', 'COPPER', 'BATTERY', 'MOTOR', 'MAGNET', 'PLASTIC', 'OTHER']);
const avail = z.enum(['TODAY', 'THIS_WEEK', 'FLEXIBLE']);
const page = z.coerce.number().int().min(1).default(1);
const limit = z.coerce.number().int().min(1).max(100).default(20);
const rateRows = z.array(z.object({ materialCategory: mat, pricePerKg: z.number().finite().positive().lt(1_000_000) }).strict()).max(20);
const profileUpdate = z.object({ pickupAvailability: avail.optional(), maxPickupDistanceKm: z.number().finite().positive().max(200).optional(), operatingHours: z.record(z.string(), z.unknown()).optional() }).strict();
const parseDateOnly = (value: string): Date | null => {
  const [year, month, day] = value.split('-').map(Number);
  if (![year, month, day].every(Number.isInteger)) return null;
  const date = new Date(Date.UTC(year, month - 1, day, 23, 59, 59, 999));
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day ? date : null;
};
const verificationRequest = z.object({
  authority: z.string().trim().min(2).max(160),
  registrationNumber: z.string().trim().min(2).max(160),
  authorizationType: z.string().trim().min(2).max(160),
  evidenceReference: z.string().trim().min(2).max(500),
  verificationSource: z.string().trim().min(2).max(500),
  validUntil: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Use YYYY-MM-DD')
}).superRefine((value, ctx) => {
  if (!parseDateOnly(value.validUntil)) ctx.addIssue({ code: 'custom', path: ['validUntil'], message: 'Enter a valid date' });
  else if (parseDateOnly(value.validUntil)!.getTime() <= Date.now()) ctx.addIssue({ code: 'custom', path: ['validUntil'], message: 'Authorization must expire in the future' });
});

export const recyclerController = (s: RecyclerService) => ({
  selfProfile: async (req: Request, res: Response) => res.json({ success: true, data: await s.selfProfile(req.identity!.collectorId), message: 'Recycler profile retrieved' }),
  submitVerificationRequest: async (req: Request, res: Response) => {
    const p = verificationRequest.safeParse(req.body);
    if (!p.success) throw new AppError('VALIDATION_ERROR', 'Add complete authorization details and a valid expiry date', 422, { code: 'INVALID_VERIFICATION_REQUEST' });
    const { validUntil, ...details } = p.data;
    return res.status(202).json({ success: true, data: await s.submitVerificationRequest(req.identity!.collectorId, { ...details, validUntil: parseDateOnly(validUntil)! }), message: 'Verification request submitted' });
  },
  updateProfile: async (req: Request, res: Response) => { const p = profileUpdate.safeParse(req.body); if (!p.success) throw new AppError('VALIDATION_ERROR', 'Invalid recycler profile settings', 422, { code: 'INVALID_RECYCLER_PROFILE' }); return res.json({ success: true, data: await s.updateProfile(req.identity!.collectorId, p.data as any), message: 'Recycler profile updated' }); },
  updateRates: async (req: Request, res: Response) => { const p = rateRows.safeParse(req.body?.rates ?? req.body); if (!p.success) throw new AppError('VALIDATION_ERROR', 'Add at least one valid buying rate', 422, { code: 'INVALID_RECYCLER_RATES' }); return res.json({ success: true, data: await s.updateRates(req.identity!.collectorId, p.data), message: 'Recycler rates updated' }); },
  list: async (req: Request, res: Response) => {
    const p = page.parse(req.query.page);
    const l = limit.parse(req.query.limit);
    const radius = req.query.radius === undefined ? undefined : Number(req.query.radius);
    if (radius !== undefined && !([5, 10, 25, 50] as number[]).includes(radius)) throw new AppError('VALIDATION_ERROR', 'Radius must be 5, 10, 25, or 50 km', 400, { code: 'INVALID_RADIUS' });
    const latitude = req.query.latitude === undefined ? undefined : Number(req.query.latitude);
    const longitude = req.query.longitude === undefined ? undefined : Number(req.query.longitude);
    if ((latitude !== undefined && !Number.isFinite(latitude)) || (longitude !== undefined && !Number.isFinite(longitude)) || ((latitude === undefined) !== (longitude === undefined))) throw new AppError('VALIDATION_ERROR', 'Both latitude and longitude are required', 400, { code: 'INVALID_LOCATION' });
    if (latitude !== undefined && (latitude < -90 || latitude > 90 || longitude! < -180 || longitude! > 180)) throw new AppError('VALIDATION_ERROR', 'Invalid coordinates', 400, { code: 'INVALID_LOCATION' });
    const material = req.query.materialCategory ? mat.parse(req.query.materialCategory) : undefined;
    const availability = req.query.availability ? avail.parse(req.query.availability) : undefined;
    const sort = req.query.sort === 'rate' ? 'rate' : 'proximity';
    return res.json({ success: true, data: await s.list({ location: typeof req.query.location === 'string' ? req.query.location : undefined, latitude, longitude, radius, material, availability, sort, page: p, limit: l }), message: 'Recyclers retrieved' });
  },
  detail: async (req: Request, res: Response) => res.json({ success: true, data: await s.detail(String(req.params.recyclerId)), message: 'Recycler retrieved' }),
  match: async (req: Request, res: Response) => res.json({ success: true, data: await s.match(String(req.query.lotId), req.identity!.collectorId), message: 'Recycler matches retrieved' }),
  adminList: async (_req: Request, res: Response) => res.json({ success: true, data: await s.adminList(), message: 'Admin recycler list retrieved' }),
  adminDetail: async (req: Request, res: Response) => res.json({ success: true, data: await s.adminDetail(String(req.params.recyclerId)), message: 'Admin recycler retrieved' }),
  authorize: async (req: Request, res: Response) => {
    const p = z.object({ status: z.enum(['VERIFIED', 'PENDING', 'UNDER_REVIEW', 'REJECTED', 'REVIEW_REQUIRED', 'EXPIRED', 'REVOKED', 'SUSPENDED']), reason: z.string().max(500).optional(), authority: z.string().trim().max(160).optional(), registrationNumber: z.string().trim().max(160).optional(), authorizationType: z.string().trim().max(160).optional(), evidenceReference: z.string().trim().max(500).optional(), verificationSource: z.string().trim().max(500).optional(), validUntil: z.string().datetime().optional() }).superRefine((value, ctx) => { if (value.status === 'VERIFIED' && (!value.authority || !value.registrationNumber || !value.authorizationType || !value.evidenceReference || !value.verificationSource || !value.validUntil)) ctx.addIssue({ code: 'custom', path: ['status'], message: 'Verified recyclers require complete registration evidence and validity' }); if (value.status === 'VERIFIED' && value.validUntil && new Date(value.validUntil).getTime() <= Date.now()) ctx.addIssue({ code: 'custom', path: ['validUntil'], message: 'Authorization must expire in the future' }); }).safeParse(req.body);
    if (!p.success) throw new AppError('VALIDATION_ERROR', 'Invalid authorization status', 422, { code: 'INVALID_AUTHORIZATION_STATUS' });
    const { status, reason, validUntil, ...details } = p.data;
    return res.json({ success: true, data: await s.authorize(String(req.params.recyclerId), req.identity!.collectorId, status, reason, { ...details, validUntil: validUntil ? new Date(validUntil) : undefined }), message: 'Recycler authorization updated' });
  }
});
