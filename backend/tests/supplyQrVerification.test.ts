import express from 'express';
import request from 'supertest';
import { createHash } from 'node:crypto';
import { describe, expect, it, vi } from 'vitest';
import { createSupplyHandoverQr, formalisationRoutes } from '../src/routes/formalisationRoutes.js';
import { JwtService } from '../src/services/jwt.js';
import { errorHandler } from '../src/middleware/errors.js';

function fixture() {
  const secret = 'qr-verification-test-secret';
  const qr = createSupplyHandoverQr({ version: 1, referenceId: 'older-than-history', recyclerId: 'r1', nonce: 'nonce1' }, secret).data;
  const row = { id: 'old', referenceId: 'older-than-history', recyclerId: 'r1', status: 'COLLECTOR_CONFIRMED', qrCodeData: qr, qrNonceHash: createHash('sha256').update('nonce1').digest('hex'), expiresAt: new Date(Date.now() + 60000) };
  const db: any = { user: { findFirst: vi.fn().mockResolvedValue({ role: 'RECYCLER', accountStatus: 'ACTIVE' }) }, recycler: { findUnique: vi.fn().mockResolvedValue({ authorizationStatus: 'VERIFIED', accountStatus: 'ACTIVE' }) }, supplyHandover: { findUnique: vi.fn().mockResolvedValue(row), findMany: vi.fn() } };
  const jwt = new JwtService({ JWT_SECRET: 'qr-route-test-jwt-secret', JWT_EXPIRES_IN: '1h' } as any);
  const app = express(); app.use(express.json()); app.use('/api/v1', formalisationRoutes(jwt, {} as any, db, secret)); app.use(errorHandler);
  return { app, jwt, db, row, qr };
}

describe('exact authenticated QR verification', () => {
  it('finds a valid handover without reading a history page', async () => {
    const { app, jwt, db, qr } = fixture();
    const response = await request(app).post('/api/v1/recycler/handovers/verify').set('Authorization', `Bearer ${jwt.generateRecyclerToken('r1')}`).send({ qrCodeData: qr });
    expect(response.status).toBe(200);
    expect(response.body.data.id).toBe('old');
    expect(db.supplyHandover.findMany).not.toHaveBeenCalled();
  });
  it('rejects another Recycler and an altered QR', async () => {
    const { app, jwt, qr } = fixture();
    const wrong = await request(app).post('/api/v1/recycler/handovers/verify').set('Authorization', `Bearer ${jwt.generateRecyclerToken('r2')}`).send({ qrCodeData: qr });
    expect(wrong.status).toBe(404);
    const altered = await request(app).post('/api/v1/recycler/handovers/verify').set('Authorization', `Bearer ${jwt.generateRecyclerToken('r1')}`).send({ qrCodeData: qr + 'x' });
    expect(altered.status).toBe(422);
  });
  it('rejects expired and replaced QR records', async () => {
    const { app, jwt, row, qr } = fixture();
    row.expiresAt = new Date(0);
    const expired = await request(app).post('/api/v1/recycler/handovers/verify').set('Authorization', `Bearer ${jwt.generateRecyclerToken('r1')}`).send({ qrCodeData: qr });
    expect(expired.status).toBe(409);
    row.expiresAt = new Date(Date.now() + 60000); row.qrNonceHash = 'replaced';
    const replaced = await request(app).post('/api/v1/recycler/handovers/verify').set('Authorization', `Bearer ${jwt.generateRecyclerToken('r1')}`).send({ qrCodeData: qr });
    expect(replaced.status).toBe(409);
  });
});
