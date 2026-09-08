import type { Request, Response } from 'express';
import { z } from 'zod';
import { RecyclerService } from '../services/recyclerService.js';
import { AppError } from '../utils/errors.js';

const mat = z.enum(['CRT', 'LCD_PANEL', 'PCB', 'CABLE', 'BATTERY', 'MOTOR', 'MAGNET', 'PLASTIC', 'OTHER']);
const avail = z.enum(['TODAY', 'THIS_WEEK', 'FLEXIBLE']);
const page = z.coerce.number().int().min(1).default(1);
const limit = z.coerce.number().int().min(1).max(100).default(20);

export const recyclerController = (s: RecyclerService) => ({
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
    const p = z.object({ status: z.enum(['VERIFIED', 'PENDING', 'REJECTED', 'SUSPENDED']), reason: z.string().max(500).optional() }).safeParse(req.body);
    if (!p.success) throw new AppError('VALIDATION_ERROR', 'Invalid authorization status', 422, { code: 'INVALID_AUTHORIZATION_STATUS' });
    return res.json({ success: true, data: await s.authorize(String(req.params.recyclerId), req.identity!.collectorId, p.data.status, p.data.reason), message: 'Recycler authorization updated' });
  }
});
