import { z } from 'zod';

const schema = z.object({
  APP_ENV: z.enum(['testing', 'production']).default('testing'),
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  PORT: z.coerce.number().int().positive().default(4000),
  DATABASE_URL: z.string().min(1),
  JWT_SECRET: z.string().min(32),
  JWT_EXPIRES_IN: z.string().min(1).default('15m'),
  REFRESH_TOKEN_EXPIRES_IN_DAYS: z.coerce.number().int().min(1).max(90).default(30),
  TRACEABILITY_SIGNING_SECRET: z.string().min(32),
  CORS_ORIGIN: z.string().min(1).default('http://localhost:5173'),
  // Keep the health/readiness version aligned with the Android release unless
  // a deployment explicitly supplies a different backend build identifier.
  APP_VERSION: z.string().min(1).default('0.0.42-beta'),
  OTP_PROVIDER: z.enum(['development', 'twilio', 'twofactor']).default('development'),
  TWILIO_ACCOUNT_SID: z.string().optional(),
  TWILIO_AUTH_TOKEN: z.string().optional(),
  TWILIO_VERIFY_SERVICE_SID: z.string().optional(),
  TWOFACTOR_API_KEY: z.string().optional(),
  TWOFACTOR_BASE_URL: z.string().url().optional(),
  NOTIFICATION_SMS_PROVIDER: z.enum(['disabled', 'twofactor']).optional(),
  NOTIFICATION_SMS_ENABLED: z.coerce.boolean().optional(),
  TWOFACTOR_SMS_SENDER_ID: z.string().trim().min(1).max(20).optional(),
  NOTIFICATION_PUSH_PROVIDER: z.enum(['disabled', 'fcm']).optional(),
  NOTIFICATION_PUSH_ENABLED: z.coerce.boolean().optional(),
  FCM_PROJECT_ID: z.string().trim().min(1).optional(),
  FCM_CLIENT_EMAIL: z.string().email().optional(),
  FCM_PRIVATE_KEY: z.string().min(1).optional(),
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
  RATE_LIMIT_STORE: z.enum(['memory', 'database']).default('memory'),
  // Keep OTP request recovery short while retaining a burst limit.
  OTP_REQUEST_WINDOW_MINUTES: z.coerce.number().int().min(1).max(2).default(2)
}).superRefine((value, ctx) => {
  const production = value.APP_ENV === 'production' || value.NODE_ENV === 'production';
  const placeholder = /^(replace-with|generate-a-random|change-me|your[-_])/i;
  try {
    const databaseUrl = new URL(value.DATABASE_URL);
    const databaseName = decodeURIComponent(databaseUrl.pathname.replace(/^\/+/, ''));
    if (!['mongodb:', 'mongodb+srv:'].includes(databaseUrl.protocol)) ctx.addIssue({ code: 'custom', path: ['DATABASE_URL'], message: 'DATABASE_URL must use mongodb:// or mongodb+srv://' });
    if (/\s/.test(databaseName)) ctx.addIssue({ code: 'custom', path: ['DATABASE_URL'], message: 'DATABASE_URL database name must not contain whitespace' });
  } catch {
    ctx.addIssue({ code: 'custom', path: ['DATABASE_URL'], message: 'DATABASE_URL must be a valid MongoDB connection URL' });
  }
  if (placeholder.test(value.JWT_SECRET)) ctx.addIssue({ code: 'custom', path: ['JWT_SECRET'], message: 'JWT_SECRET must be a real random secret, not a template placeholder' });
  if (placeholder.test(value.TRACEABILITY_SIGNING_SECRET)) ctx.addIssue({ code: 'custom', path: ['TRACEABILITY_SIGNING_SECRET'], message: 'TRACEABILITY_SIGNING_SECRET must be a real random secret, not a template placeholder' });
  if (value.JWT_SECRET === value.TRACEABILITY_SIGNING_SECRET) ctx.addIssue({ code: 'custom', path: ['TRACEABILITY_SIGNING_SECRET'], message: 'TRACEABILITY_SIGNING_SECRET must differ from JWT_SECRET' });
  if (value.APP_ENV === 'production' && value.NODE_ENV !== 'production') ctx.addIssue({ code: 'custom', path: ['NODE_ENV'], message: 'APP_ENV=production requires NODE_ENV=production' });
  if (production && value.OTP_PROVIDER === 'development') ctx.addIssue({ code: 'custom', path: ['OTP_PROVIDER'], message: 'Development OTP provider is not allowed in production' });
  if (production && value.CORS_ORIGIN === '*') ctx.addIssue({ code: 'custom', path: ['CORS_ORIGIN'], message: 'Wildcard CORS is not allowed in production' });
  if (production && value.CORS_ORIGIN.split(',').some(origin => !origin.trim().startsWith('https://'))) ctx.addIssue({ code: 'custom', path: ['CORS_ORIGIN'], message: 'Production CORS origins must use HTTPS' });
  if (production && value.JWT_SECRET.length < 32) ctx.addIssue({ code: 'custom', path: ['JWT_SECRET'], message: 'JWT_SECRET must be at least 32 characters in production' });
  if (production && value.TRACEABILITY_SIGNING_SECRET.length < 32) ctx.addIssue({ code: 'custom', path: ['TRACEABILITY_SIGNING_SECRET'], message: 'TRACEABILITY_SIGNING_SECRET must be at least 32 characters in production' });
  if (production && value.STORAGE_PROVIDER === 'local' && value.LOCAL_UPLOAD_PUBLIC) ctx.addIssue({ code: 'custom', path: ['LOCAL_UPLOAD_PUBLIC'], message: 'Public local uploads are not allowed in production; use protected storage or set LOCAL_UPLOAD_PUBLIC=false' });
  if (production && value.STORAGE_PROVIDER === 's3' && value.S3_PUBLIC_BASE_URL) ctx.addIssue({ code: 'custom', path: ['S3_PUBLIC_BASE_URL'], message: 'Public object URLs are not allowed in production; use signed access' });
  if (production && value.RATE_LIMIT_STORE !== 'database') ctx.addIssue({ code: 'custom', path: ['RATE_LIMIT_STORE'], message: 'Production rate limiting must use the shared database store' });
  if (production && (!value.GEMINI_API_KEY || placeholder.test(value.GEMINI_API_KEY))) ctx.addIssue({ code: 'custom', path: ['GEMINI_API_KEY'], message: 'A real GEMINI_API_KEY is required in production' });
  if (production && (!value.GEMINI_MODEL || placeholder.test(value.GEMINI_MODEL))) ctx.addIssue({ code: 'custom', path: ['GEMINI_MODEL'], message: 'A real GEMINI_MODEL is required in production' });
  if (value.OTP_PROVIDER === 'twilio' && (!value.TWILIO_ACCOUNT_SID || !value.TWILIO_AUTH_TOKEN || !value.TWILIO_VERIFY_SERVICE_SID)) ctx.addIssue({ code: 'custom', path: ['TWILIO_*'], message: 'Twilio credentials are required when OTP_PROVIDER=twilio' });
  if (value.OTP_PROVIDER === 'twofactor' && !value.TWOFACTOR_API_KEY) ctx.addIssue({ code: 'custom', path: ['TWOFACTOR_API_KEY'], message: 'TWOFACTOR_API_KEY is required when OTP_PROVIDER=twofactor' });
  if (value.NOTIFICATION_SMS_ENABLED && value.NOTIFICATION_SMS_PROVIDER !== 'twofactor') ctx.addIssue({ code: 'custom', path: ['NOTIFICATION_SMS_PROVIDER'], message: 'Notification SMS requires NOTIFICATION_SMS_PROVIDER=twofactor' });
  if (value.NOTIFICATION_SMS_PROVIDER === 'twofactor' && !value.TWOFACTOR_API_KEY) ctx.addIssue({ code: 'custom', path: ['TWOFACTOR_API_KEY'], message: 'TWOFACTOR_API_KEY is required when notification SMS uses 2Factor' });
  if (value.NOTIFICATION_SMS_PROVIDER === 'twofactor' && !value.TWOFACTOR_SMS_SENDER_ID) ctx.addIssue({ code: 'custom', path: ['TWOFACTOR_SMS_SENDER_ID'], message: 'TWOFACTOR_SMS_SENDER_ID is required when notification SMS uses 2Factor' });
  if (value.NOTIFICATION_PUSH_ENABLED && value.NOTIFICATION_PUSH_PROVIDER !== 'fcm') ctx.addIssue({ code: 'custom', path: ['NOTIFICATION_PUSH_PROVIDER'], message: 'Notification push requires NOTIFICATION_PUSH_PROVIDER=fcm' });
  if (value.NOTIFICATION_PUSH_ENABLED && !value.FCM_PROJECT_ID) ctx.addIssue({ code: 'custom', path: ['FCM_PROJECT_ID'], message: 'FCM_PROJECT_ID is required when notification push is enabled' });
  if (value.NOTIFICATION_PUSH_ENABLED && !value.FCM_CLIENT_EMAIL) ctx.addIssue({ code: 'custom', path: ['FCM_CLIENT_EMAIL'], message: 'FCM_CLIENT_EMAIL is required when notification push is enabled' });
  if (value.NOTIFICATION_PUSH_ENABLED && !value.FCM_PRIVATE_KEY) ctx.addIssue({ code: 'custom', path: ['FCM_PRIVATE_KEY'], message: 'FCM_PRIVATE_KEY is required when notification push is enabled' });
});

export type AppConfig = z.infer<typeof schema>;
export function loadConfig(env: NodeJS.ProcessEnv = process.env): AppConfig { const result = schema.safeParse(env); if (!result.success) throw new Error(`Invalid configuration: ${result.error.issues.map(i => `${i.path.join('.')}: ${i.message}`).join('; ')}`); return result.data; }
