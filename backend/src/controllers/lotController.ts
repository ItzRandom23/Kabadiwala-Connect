import type { Request, Response } from 'express';
import { z } from 'zod';
import type { LotService } from '../services/lotService.js';
import { AppError } from '../utils/errors.js';
import { paginationSchema } from '../utils/validation.js';

const location = z.object({ latitude: z.number().finite().min(-90).max(90).optional(), longitude: z.number().finite().min(-180).max(180).optional(), areaName: z.string().trim().min(1).max(160).optional(), precision: z.enum(['GPS', 'MANUAL']).optional() }).refine((x) => (x.latitude !== undefined && x.longitude !== undefined) || Boolean(x.areaName), { message: 'GPS coordinates or areaName is required' });
const createSchema = z.object({ materialCategory: z.enum(['CRT', 'LCD_PANEL', 'PCB', 'CABLE', 'BATTERY', 'MOTOR', 'MAGNET', 'PLASTIC', 'OTHER']), materialSubcategory: z.string().trim().max(160).optional(), sourceType: z.string().trim().max(80).optional(), condition: z.enum(['INTACT', 'DAMAGED', 'PARTIAL']), weight: z.number().finite().positive().lt(500), weightUnit: z.enum(['KILOGRAM', 'GRAM', 'PIECE']).default('KILOGRAM'), imageProvenance: z.enum(['CAMERA', 'GALLERY', 'IMPORTED', 'LEGACY']).optional(), collectionLocation: location, notes: z.string().max(1000).optional() });
const updateSchema = z.object({ weight: z.number().finite().positive().lt(500).optional(), condition: z.enum(['INTACT', 'DAMAGED', 'PARTIAL']).optional(), notes: z.string().max(1000).optional(), version: z.number().int().positive() }).strict();
const parseId = (value: string) => { if (!/^[A-Za-z0-9_-]{1,80}$/.test(value)) throw new AppError('VALIDATION_ERROR', 'Invalid lot ID', 400, { code: 'INVALID_LOT_ID' }); return value; };

export const lotController = (service: LotService) => ({
  create: async (req: Request, res: Response) => {
    const parsed = createSchema.safeParse(req.body);
    if (!parsed.success) throw new AppError('VALIDATION_ERROR', 'Invalid lot data', 400, { code: 'INVALID_LOT', details: parsed.error.flatten() });
    const lot = await service.create(req.identity!.collectorId, parsed.data, req.header('idempotency-key') ?? undefined, req.header('x-allow-duplicate') === 'true');
    res.status(201).json({ success: true, data: lot, message: 'Lot created' });
  },
  list: async (req: Request, res: Response) => {
    const page = paginationSchema.parse(req.query);
    const status = req.query.status ? z.enum(['CREATED', 'QUOTE_REQUESTED', 'QUOTE_RECEIVED', 'COLLECTOR_CONFIRMED', 'RECYCLER_CONFIRMED', 'HANDED_OVER', 'PAID', 'CANCELLED', 'DISPUTED']).parse(req.query.status) : undefined;
    const materialCategory = req.query.materialCategory ? z.enum(['CRT', 'LCD_PANEL', 'PCB', 'CABLE', 'BATTERY', 'MOTOR', 'MAGNET', 'PLASTIC', 'OTHER']).parse(req.query.materialCategory) : undefined;
    const [items, total] = await (service as any).repo.list(req.identity!.collectorId, (page.page - 1) * page.limit, page.limit, { ...(status ? { status } : {}), ...(materialCategory ? { materialCategory } : {}) });
    res.json({ success: true, data: { items, pagination: { page: page.page, limit: page.limit, total, totalPages: Math.ceil(total / page.limit) } }, message: 'Lots retrieved' });
  },
  get: async (req: Request, res: Response) => res.json({ success: true, data: await service.get(parseId(String(req.params.lotId)), req.identity!.collectorId), message: 'Lot retrieved' }),
  getPhoto: async (req: Request, res: Response) => { const photo = await service.photo(parseId(String(req.params.lotId)), req.identity!.collectorId); res.setHeader('Content-Type', photo.contentType); res.setHeader('Cache-Control', 'private, max-age=300'); res.setHeader('X-Content-Type-Options', 'nosniff'); return res.send(photo.body); },
  update: async (req: Request, res: Response) => { const parsed = updateSchema.safeParse(req.body); if (!parsed.success) throw new AppError('VALIDATION_ERROR', 'Invalid lot update', 400, { code: 'INVALID_LOT_UPDATE' }); const { version, ...data } = parsed.data; res.json({ success: true, data: await service.update(parseId(String(req.params.lotId)), req.identity!.collectorId, version, data), message: 'Lot updated' }); },
  cancel: async (req: Request, res: Response) => res.json({ success: true, data: await service.cancel(parseId(String(req.params.lotId)), req.identity!.collectorId), message: 'Lot cancelled' }),
  photo: async (req: Request, res: Response) => res.json({ success: true, data: await service.uploadPhoto(parseId(String(req.params.lotId)), req.identity!.collectorId, (req as any).file), message: 'Photo uploaded' })
});
