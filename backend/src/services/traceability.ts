import { createHmac, timingSafeEqual } from 'node:crypto';
import { AppError } from '../utils/errors.js';

export type TraceabilityPayload = {
  version: 1;
  referenceId: string;
  lotId: string;
  recyclerId: string;
  materialCategory: string;
  weight: number;
  issuedAt: string;
  expiresAt: string;
};

const prefix = 'kc-handover-v1';

function signature(encodedPayload: string, secret: string) {
  return createHmac('sha256', secret).update(`${prefix}.${encodedPayload}`).digest('base64url');
}

export function createSignedTraceabilityQr(payload: TraceabilityPayload, secret: string) {
  const encoded = Buffer.from(JSON.stringify(payload), 'utf8').toString('base64url');
  const signed = signature(encoded, secret);
  return { qrCodeData: `${prefix}.${encoded}.${signed}`, qrSignature: signed };
}

export function verifySignedTraceabilityQr(qrCodeData: string, secret: string): TraceabilityPayload {
  const parts = qrCodeData.split('.');
  const [tokenPrefix, encoded, supplied] = parts;
  if (parts.length !== 3 || tokenPrefix !== prefix || !encoded || !supplied) {
    throw new AppError('VALIDATION_ERROR', 'Invalid handover verification code', 422, { code: 'INVALID_HANDOVER_QR' });
  }
  const expected = signature(encoded, secret);
  const suppliedBytes = Buffer.from(supplied);
  const expectedBytes = Buffer.from(expected);
  if (suppliedBytes.length !== expectedBytes.length || !timingSafeEqual(suppliedBytes, expectedBytes)) {
    throw new AppError('VALIDATION_ERROR', 'Handover verification code has been altered', 422, { code: 'INVALID_HANDOVER_SIGNATURE' });
  }
  try {
    const parsed = JSON.parse(Buffer.from(encoded, 'base64url').toString('utf8')) as Partial<TraceabilityPayload>;
    if (parsed.version !== 1 || !parsed.referenceId || !parsed.lotId || !parsed.recyclerId || !parsed.materialCategory || !parsed.issuedAt || !parsed.expiresAt || !Number.isFinite(parsed.weight)) throw new Error('invalid payload');
    if (!Number.isFinite(Date.parse(parsed.issuedAt)) || !Number.isFinite(Date.parse(parsed.expiresAt))) throw new Error('invalid timestamps');
    return parsed as TraceabilityPayload;
  } catch {
    throw new AppError('VALIDATION_ERROR', 'Invalid handover verification payload', 422, { code: 'INVALID_HANDOVER_QR' });
  }
}
