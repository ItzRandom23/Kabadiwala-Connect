import express from 'express';
import request from 'supertest';
import { describe, expect, it } from 'vitest';
import { recyclerController } from '../src/controllers/recyclerController.js';
import { requireRecycler } from '../src/middleware/auth.js';
import { JwtService } from '../src/services/jwt.js';

const jwt = new JwtService({ JWT_SECRET: 'recycler-verification-test-secret', JWT_EXPIRES_IN: '1h' } as any);

function verificationApp() {
  const service = {
    submitVerificationRequest: async (_id: string, input: unknown) => ({ authorizationStatus: 'PENDING', submitted: input })
  } as any;
  const controller = recyclerController(service);
  const app = express();
  app.use(express.json());
  app.post('/recycler/verification-request', requireRecycler(jwt), controller.submitVerificationRequest);
  app.use((error: any, _req: any, res: any, _next: any) => res.status(error.status ?? 500).json({ code: error.code }));
  return app;
}

const validRequest = {
  authority: 'MPCB',
  registrationNumber: 'MPCB/REC/123',
  authorizationType: 'SPCB authorization',
  evidenceReference: 'https://example.gov/records/123',
  verificationSource: 'MPCB public records',
  validUntil: '2027-12-31'
};

describe('recycler verification submission', () => {
  it('allows a recycler to submit complete evidence for review', async () => {
    const response = await request(verificationApp())
      .post('/recycler/verification-request')
      .set('Authorization', `Bearer ${jwt.generateRecyclerToken('recycler-1')}`)
      .send(validRequest);

    expect(response.status).toBe(202);
    expect(response.body.data.authorizationStatus).toBe('PENDING');
  });

  it('rejects impossible calendar dates before persistence', async () => {
    const response = await request(verificationApp())
      .post('/recycler/verification-request')
      .set('Authorization', `Bearer ${jwt.generateRecyclerToken('recycler-1')}`)
      .send({ ...validRequest, validUntil: '2027-02-31' });

    expect(response.status).toBe(422);
    expect(response.body.code).toBe('VALIDATION_ERROR');
  });
});
