import { describe, expect, it } from 'vitest';
import { loadConfig } from '../src/config/env.js';

const production = {
  NODE_ENV:'production', PORT:'4000', DATABASE_URL:'mongodb://example.invalid/db',
  JWT_SECRET:'j'.repeat(40), TRACEABILITY_SIGNING_SECRET:'t'.repeat(40),
  OTP_PROVIDER:'twilio', TWILIO_ACCOUNT_SID:'sid', TWILIO_AUTH_TOKEN:'token', TWILIO_VERIFY_SERVICE_SID:'verify',
  CORS_ORIGIN:'https://app.example.org', STORAGE_PROVIDER:'s3', S3_BUCKET:'private', S3_ACCESS_KEY_ID:'id', S3_SECRET_ACCESS_KEY:'secret',
  RATE_LIMIT_STORE:'database'
};

describe('production configuration boundary', () => {
  it('accepts explicit HTTPS/private/shared-store configuration', () => expect(loadConfig(production).NODE_ENV).toBe('production'));
  it('rejects wildcard or cleartext origins', () => {
    expect(() => loadConfig({ ...production, CORS_ORIGIN:'*' })).toThrow(/CORS/);
    expect(() => loadConfig({ ...production, CORS_ORIGIN:'http:\/\/app.example.org' })).toThrow(/HTTPS/);
  });
  it('rejects public storage and process-local rate limiting', () => {
    expect(() => loadConfig({ ...production, S3_PUBLIC_BASE_URL:'https://public.example.org' })).toThrow(/Public object URLs/);
    expect(() => loadConfig({ ...production, RATE_LIMIT_STORE:'memory' })).toThrow(/shared database/);
  });
});
