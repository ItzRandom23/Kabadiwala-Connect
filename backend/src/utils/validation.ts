import { z } from 'zod';
export const phoneSchema = z.string().regex(/^[6-9]\d{9}$/, 'Invalid Indian mobile number');
export const languageSchema = z.enum(['ENGLISH','MARATHI','HINDI','ASSAMESE','BENGALI','BODO','DOGRI','GUJARATI','KANNADA','KASHMIRI','KONKANI','MAITHILI','MALAYALAM','MANIPURI','NEPALI','ODIA','PUNJABI','SANSKRIT','SANTALI','SINDHI','TAMIL','TELUGU','URDU']);
export const latitudeSchema = z.coerce.number().min(-90).max(90);
export const longitudeSchema = z.coerce.number().min(-180).max(180);
export const areaNameSchema = z.string().trim().min(1).max(160);
export const idSchema = z.string().trim().min(1).max(64);
export const paginationSchema = z.object({ page: z.coerce.number().int().min(1).default(1), limit: z.coerce.number().int().min(1).max(100).default(20) });
