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
  const row = { id: 'old', referenceId: 'older-than-history', recyclerId: 'r1', status: 'COLLECTOR_CONFIRMED', qrCodeData: qr, qrNonceHash: createHash('sha256').update('nonce1').digest('hex'), expiresAt: new Date(Date.now() + 60000), recyclerQrScannedAt: null as Date | null };
  const updateMany = vi.fn(async ({ where, data }: any) => {
    if (row.id !== where.id || row.recyclerId !== where.recyclerId || row.status !== where.status || row.qrCodeData !== where.qrCodeData || row.expiresAt <= where.expiresAt.gt || row.recyclerQrScannedAt !== null) return { count: 0 };
    row.recyclerQrScannedAt = data.recyclerQrScannedAt;
    return { count: 1 };
  });
  const db: any = { user: { findFirst: vi.fn().mockResolvedValue({ role: 'RECYCLER', accountStatus: 'ACTIVE' }) }, recycler: { findUnique: vi.fn().mockResolvedValue({ authorizationStatus: 'VERIFIED', accountStatus: 'ACTIVE' }) }, supplyHandover: { findUnique: vi.fn().mockResolvedValue(row), findMany: vi.fn(), updateMany } };
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
    expect(response.body.data.recyclerQrScannedAt).toBeTruthy();
    expect(response.body.data.status).toBe('COLLECTOR_CONFIRMED');
    expect(db.supplyHandover.updateMany).toHaveBeenCalledWith(expect.objectContaining({ where: expect.objectContaining({ id: 'old', recyclerId: 'r1', status: 'COLLECTOR_CONFIRMED', qrCodeData: qr, OR: [{ recyclerQrScannedAt: null }, { recyclerQrScannedAt: { isSet: false } }] }), data: { recyclerQrScannedAt: expect.any(Date) } }));
    expect(db.supplyHandover.findMany).not.toHaveBeenCalled();
  });
  it('retains the first scan timestamp on repeated verification without completing receipt', async () => {
    const { app, jwt, row, qr } = fixture();
    const scan = () => request(app).post('/api/v1/recycler/handovers/verify').set('Authorization', `Bearer ${jwt.generateRecyclerToken('r1')}`).send({ qrCodeData: qr });
    const first = await scan();
    const second = await scan();
    expect(first.status).toBe(200);
    expect(second.status).toBe(200);
    expect(second.body.data.recyclerQrScannedAt).toBe(first.body.data.recyclerQrScannedAt);
    expect(row.status).toBe('COLLECTOR_CONFIRMED');
  });
  it.each(['CANCELLED', 'EXPIRED'])('rejects a handover changed to %s during verification', async (status) => {
    const { app, jwt, db, row, qr } = fixture();
    db.supplyHandover.updateMany.mockImplementationOnce(async () => { row.status = status; return { count: 0 }; });
    const response = await request(app).post('/api/v1/recycler/handovers/verify').set('Authorization', `Bearer ${jwt.generateRecyclerToken('r1')}`).send({ qrCodeData: qr });
    expect(response.status).toBe(409);
    expect(row.recyclerQrScannedAt).toBeNull();
  });
  it('rejects another Recycler and an altered QR', async () => {
    const { app, jwt, db, qr } = fixture();
    const wrong = await request(app).post('/api/v1/recycler/handovers/verify').set('Authorization', `Bearer ${jwt.generateRecyclerToken('r2')}`).send({ qrCodeData: qr });
    expect(wrong.status).toBe(404);
    const altered = await request(app).post('/api/v1/recycler/handovers/verify').set('Authorization', `Bearer ${jwt.generateRecyclerToken('r1')}`).send({ qrCodeData: qr + 'x' });
    expect(altered.status).toBe(422);
    expect(db.supplyHandover.updateMany).not.toHaveBeenCalled();
  });
  it('rejects expired and replaced QR records', async () => {
    const { app, jwt, db, row, qr } = fixture();
    row.expiresAt = new Date(0);
    const expired = await request(app).post('/api/v1/recycler/handovers/verify').set('Authorization', `Bearer ${jwt.generateRecyclerToken('r1')}`).send({ qrCodeData: qr });
    expect(expired.status).toBe(409);
    row.expiresAt = new Date(Date.now() + 60000); row.qrNonceHash = 'replaced';
    const replaced = await request(app).post('/api/v1/recycler/handovers/verify').set('Authorization', `Bearer ${jwt.generateRecyclerToken('r1')}`).send({ qrCodeData: qr });
    expect(replaced.status).toBe(409);
    expect(db.supplyHandover.updateMany).not.toHaveBeenCalled();
  });
});
