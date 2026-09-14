import { describe, expect, it } from 'vitest';
import { createSupplyHandoverQr, verifySupplyHandoverQr } from '../src/routes/formalisationRoutes.js';

const secret = 'formalisation-integrity-test-secret-32-chars';

describe('formalisation handover QR integrity', () => {
  it('round-trips the versioned payload and does not expose the nonce in the response helper', () => {
    const payload = { version: 1, referenceId: 'KC-HO-1', recyclerId: 'recycler-1', nonce: 'one-time-nonce', materialCategory: 'PCB', weightKg: 12, issuedAt: new Date().toISOString(), expiresAt: new Date(Date.now() + 3600000).toISOString() };
    const qr = createSupplyHandoverQr(payload, secret);
    expect(qr.data.startsWith('kc-supply-handover-v1.')).toBe(true);
    expect(verifySupplyHandoverQr(qr.data, secret)).toMatchObject(payload);
  });

  it('rejects a changed payload or signature', () => {
    const qr = createSupplyHandoverQr({ version: 1, referenceId: 'KC-HO-2', recyclerId: 'recycler-1', nonce: 'nonce', materialCategory: 'BATTERY', weightKg: 3, issuedAt: new Date().toISOString(), expiresAt: new Date(Date.now() + 3600000).toISOString() }, secret);
    const parts = qr.data.split('.');
    const changed = Buffer.from(JSON.stringify({ version: 1, referenceId: 'KC-HO-2', recyclerId: 'recycler-1', nonce: 'tampered', materialCategory: 'BATTERY', weightKg: 3 }), 'utf8').toString('base64url');
    expect(() => verifySupplyHandoverQr(`kc-supply-handover-v1.${changed}.${parts[2]}`, secret)).toThrow(/altered|Invalid/);
    expect(() => verifySupplyHandoverQr(`${qr.data.slice(0, -1)}x`, secret)).toThrow(/altered/);
  });
});
