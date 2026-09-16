import { describe, expect, it } from 'vitest';
import { createSignedTraceabilityQr, verifySignedTraceabilityQr } from '../src/services/traceability.js';

const secret = 'traceability-test-secret-that-is-long-enough';
const payload = {
  version: 1 as const,
  referenceId: 'HOV-20260911-ABCDE',
  lotId: 'lot-1',
  recyclerId: 'recycler-1',
  materialCategory: 'PCB',
  weight: 12.5,
  issuedAt: '2026-09-11T10:00:00.000Z',
  expiresAt: '2026-09-18T10:00:00.000Z'
};

describe('signed traceability QR', () => {
  it('round-trips an untampered handover payload', () => {
    const signed = createSignedTraceabilityQr(payload, secret);
    expect(verifySignedTraceabilityQr(signed.qrCodeData, secret)).toEqual(payload);
  });

  it('rejects a modified payload and a modified signature', () => {
    const signed = createSignedTraceabilityQr(payload, secret).qrCodeData;
    const parts = signed.split('.');
    expect(() => verifySignedTraceabilityQr(`${parts[0]}.${parts[1]}A.${parts[2]}`, secret)).toThrow();
    expect(() => verifySignedTraceabilityQr(`${parts[0]}.${parts[1]}.${parts[2]}A`, secret)).toThrow();
  });
});
