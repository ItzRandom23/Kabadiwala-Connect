import { describe, expect, it } from 'vitest';
import { loadConfig } from '../src/config/env.js';

const production = {
  NODE_ENV:'production', PORT:'4000', DATABASE_URL:'mongodb://example.invalid/db',
  JWT_SECRET:'j'.repeat(40), TRACEABILITY_SIGNING_SECRET:'t'.repeat(40),
  OTP_PROVIDER:'twilio', TWILIO_ACCOUNT_SID:'sid', TWILIO_AUTH_TOKEN:'token', TWILIO_VERIFY_SERVICE_SID:'verify',
  CORS_ORIGIN:'https://app.example.org', STORAGE_PROVIDER:'s3', S3_BUCKET:'private', S3_ACCESS_KEY_ID:'id', S3_SECRET_ACCESS_KEY:'secret',
  RATE_LIMIT_STORE:'database', GEMINI_API_KEY:'real-provider-key', GEMINI_MODEL:'approved-model'
};

describe('production configuration boundary', () => {
  it('defaults OTP request rate-limit recovery to two minutes and rejects longer windows', () => {
    expect(loadConfig(production).OTP_REQUEST_WINDOW_MINUTES).toBe(2);
    expect(() => loadConfig({ ...production, OTP_REQUEST_WINDOW_MINUTES: '3' })).toThrow();
  });

  it('accepts explicit HTTPS/private/shared-store configuration', () => expect(loadConfig(production).NODE_ENV).toBe('production'));
  it('accepts 2Factor credentials for production OTP', () => expect(loadConfig({ ...production, OTP_PROVIDER:'twofactor', TWILIO_ACCOUNT_SID:undefined, TWILIO_AUTH_TOKEN:undefined, TWILIO_VERIFY_SERVICE_SID:undefined, TWOFACTOR_API_KEY:'test-key' }).OTP_PROVIDER).toBe('twofactor'));
  it('requires a 2Factor API key when selected', () => expect(() => loadConfig({ ...production, OTP_PROVIDER:'twofactor', TWILIO_ACCOUNT_SID:undefined, TWILIO_AUTH_TOKEN:undefined, TWILIO_VERIFY_SERVICE_SID:undefined })).toThrow(/TWOFACTOR_API_KEY/));
  it('requires an approved sender when notification SMS is enabled', () => {
    expect(() => loadConfig({ ...production, NOTIFICATION_SMS_ENABLED:'true', NOTIFICATION_SMS_PROVIDER:'disabled' })).toThrow(/NOTIFICATION_SMS_PROVIDER/);
    expect(() => loadConfig({ ...production, NOTIFICATION_SMS_ENABLED:'true', NOTIFICATION_SMS_PROVIDER:'twofactor', TWOFACTOR_API_KEY:'test-key' })).toThrow(/TWOFACTOR_SMS_SENDER_ID/);
    expect(loadConfig({ ...production, NOTIFICATION_SMS_ENABLED:'true', NOTIFICATION_SMS_PROVIDER:'twofactor', TWOFACTOR_API_KEY:'test-key', TWOFACTOR_SMS_SENDER_ID:'KABADI' }).NOTIFICATION_SMS_ENABLED).toBe(true);
  });
  it('requires a complete FCM server configuration when push is enabled', () => {
    expect(() => loadConfig({ ...production, NOTIFICATION_PUSH_ENABLED: 'true', NOTIFICATION_PUSH_PROVIDER: 'disabled' })).toThrow(/NOTIFICATION_PUSH_PROVIDER/);
    expect(() => loadConfig({ ...production, NOTIFICATION_PUSH_ENABLED: 'true', NOTIFICATION_PUSH_PROVIDER: 'fcm' })).toThrow(/FCM_PROJECT_ID/);
    expect(loadConfig({ ...production, NOTIFICATION_PUSH_ENABLED: 'true', NOTIFICATION_PUSH_PROVIDER: 'fcm', FCM_PROJECT_ID: 'project', FCM_CLIENT_EMAIL: 'firebase-adminsdk@example.iam.gserviceaccount.com', FCM_PRIVATE_KEY: 'private-key' }).NOTIFICATION_PUSH_ENABLED).toBe(true);
  });
  it('rejects wildcard or cleartext origins', () => {
    expect(() => loadConfig({ ...production, CORS_ORIGIN:'*' })).toThrow(/CORS/);
    expect(() => loadConfig({ ...production, CORS_ORIGIN:'http:\/\/app.example.org' })).toThrow(/HTTPS/);
  });
  it('rejects public storage and process-local rate limiting', () => {
    expect(() => loadConfig({ ...production, S3_PUBLIC_BASE_URL:'https://public.example.org' })).toThrow(/Public object URLs/);
    expect(() => loadConfig({ ...production, RATE_LIMIT_STORE:'memory' })).toThrow(/shared database/);
  });
  it('requires a real Gemini credential and model in production', () => {
    expect(() => loadConfig({ ...production, GEMINI_API_KEY:undefined })).toThrow(/GEMINI_API_KEY/);
    expect(() => loadConfig({ ...production, GEMINI_API_KEY:'replace-with-production-gemini-key' })).toThrow(/GEMINI_API_KEY/);
    expect(() => loadConfig({ ...production, GEMINI_MODEL:undefined })).toThrow(/GEMINI_MODEL/);
  });
  it('rejects malformed Mongo database names before Prisma starts', () => {
    expect(() => loadConfig({ ...production, DATABASE_URL:'mongodb+srv://cluster.example/kabadiwala%20_connect' })).toThrow(/database name must not contain whitespace/);
  });
});
