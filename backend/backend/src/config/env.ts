import { z } from 'zod';

const schema = z.object({
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  PORT: z.coerce.number().int().positive().default(4000),
  DATABASE_URL: z.string().min(1),
  JWT_SECRET: z.string().min(32),
  JWT_EXPIRES_IN: z.string().min(1).default('15m'),
  REFRESH_TOKEN_EXPIRES_IN_DAYS: z.coerce.number().int().min(1).max(90).default(30),
  TRACEABILITY_SIGNING_SECRET: z.string().min(32),
  CORS_ORIGIN: z.string().min(1).default('http://localhost:5173'),
  APP_VERSION: z.string().min(1).default('1.0.0'),
  OTP_PROVIDER: z.enum(['development', 'twilio']).default('development'),
  TWILIO_ACCOUNT_SID: z.string().optional(),
  TWILIO_AUTH_TOKEN: z.string().optional(),
  TWILIO_VERIFY_SERVICE_SID: z.string().optional(),
  DEV_OTP_CODE: z.string().regex(/^\d{6}$/).default('123456'),
  AI_DESCRIPTION_URL: z.string().url().optional().or(z.literal('')),
  AI_DESCRIPTION_API_KEY: z.string().optional(),
  GEMINI_API_KEY: z.string().optional(),
  GEMINI_MODEL: z.string().min(1).optional(),
  STORAGE_PROVIDER: z.enum(['local', 's3']).default('local'),
  LOCAL_UPLOAD_DIR: z.string().min(1).default('uploads'),
  LOCAL_UPLOAD_BASE_URL: z.string().default(''),
  LOCAL_UPLOAD_PUBLIC: z.coerce.boolean().default(false),
  S3_ENDPOINT: z.string().url().optional().or(z.literal('')),
  S3_REGION: z.string().default('ap-south-1'),
  S3_BUCKET: z.string().optional(),
  S3_ACCESS_KEY_ID: z.string().optional(),
  S3_SECRET_ACCESS_KEY: z.string().optional(),
  S3_PUBLIC_BASE_URL: z.string().url().optional().or(z.literal('')),
  RATE_LIMIT_STORE: z.enum(['memory', 'database']).default('memory')
}).superRefine((value, ctx) => {
  const placeholder = /^(replace-with|generate-a-random|change-me|your[-_])/i;
  if (placeholder.test(value.JWT_SECRET)) ctx.addIssue({ code: 'custom', path: ['JWT_SECRET'], message: 'JWT_SECRET must be a real random secret, not a template placeholder' });
  if (placeholder.test(value.TRACEABILITY_SIGNING_SECRET)) ctx.addIssue({ code: 'custom', path: ['TRACEABILITY_SIGNING_SECRET'], message: 'TRACEABILITY_SIGNING_SECRET must be a real random secret, not a template placeholder' });
  if (value.JWT_SECRET === value.TRACEABILITY_SIGNING_SECRET) ctx.addIssue({ code: 'custom', path: ['TRACEABILITY_SIGNING_SECRET'], message: 'TRACEABILITY_SIGNING_SECRET must differ from JWT_SECRET' });
  if (value.NODE_ENV === 'production' && value.OTP_PROVIDER === 'development') ctx.addIssue({ code: 'custom', path: ['OTP_PROVIDER'], message: 'Development OTP provider is not allowed in production' });
  if (value.NODE_ENV === 'production' && value.CORS_ORIGIN === '*') ctx.addIssue({ code: 'custom', path: ['CORS_ORIGIN'], message: 'Wildcard CORS is not allowed in production' });
  if (value.NODE_ENV === 'production' && value.CORS_ORIGIN.split(',').some(origin => !origin.trim().startsWith('https://'))) ctx.addIssue({ code: 'custom', path: ['CORS_ORIGIN'], message: 'Production CORS origins must use HTTPS' });
  if (value.NODE_ENV === 'production' && value.JWT_SECRET.length < 32) ctx.addIssue({ code: 'custom', path: ['JWT_SECRET'], message: 'JWT_SECRET must be at least 32 characters in production' });
  if (value.NODE_ENV === 'production' && value.TRACEABILITY_SIGNING_SECRET.length < 32) ctx.addIssue({ code: 'custom', path: ['TRACEABILITY_SIGNING_SECRET'], message: 'TRACEABILITY_SIGNING_SECRET must be at least 32 characters in production' });
  if (value.NODE_ENV === 'production' && value.STORAGE_PROVIDER === 'local' && value.LOCAL_UPLOAD_PUBLIC) ctx.addIssue({ code: 'custom', path: ['LOCAL_UPLOAD_PUBLIC'], message: 'Public local uploads are not allowed in production; use protected storage or set LOCAL_UPLOAD_PUBLIC=false' });
  if (value.NODE_ENV === 'production' && value.STORAGE_PROVIDER === 's3' && value.S3_PUBLIC_BASE_URL) ctx.addIssue({ code: 'custom', path: ['S3_PUBLIC_BASE_URL'], message: 'Public object URLs are not allowed in production; use signed access' });
  if (value.NODE_ENV === 'production' && value.RATE_LIMIT_STORE !== 'database') ctx.addIssue({ code: 'custom', path: ['RATE_LIMIT_STORE'], message: 'Production rate limiting must use the shared database store' });
  if (value.OTP_PROVIDER === 'twilio' && (!value.TWILIO_ACCOUNT_SID || !value.TWILIO_AUTH_TOKEN || !value.TWILIO_VERIFY_SERVICE_SID)) ctx.addIssue({ code: 'custom', path: ['TWILIO_*'], message: 'Twilio credentials are required when OTP_PROVIDER=twilio' });
});

export type AppConfig = z.infer<typeof schema>;
export function loadConfig(env: NodeJS.ProcessEnv = process.env): AppConfig { const result = schema.safeParse(env); if (!result.success) throw new Error(`Invalid configuration: ${result.error.issues.map(i => `${i.path.join('.')}: ${i.message}`).join('; ')}`); return result.data; }
