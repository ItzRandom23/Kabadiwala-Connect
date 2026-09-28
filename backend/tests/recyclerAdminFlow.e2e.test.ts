import { randomBytes, scryptSync, randomUUID } from 'node:crypto';
import { afterAll, describe, expect, it } from 'vitest';
import request from 'supertest';
import { PrismaClient } from '@prisma/client';
import { createApp } from '../src/app.js';
import { loadConfig } from '../src/config/env.js';
import { JwtService } from '../src/services/jwt.js';
import { CollectorRepository } from '../src/repositories/collectorRepository.js';
import { CollectorService } from '../src/services/collectorService.js';
import { RecyclerService } from '../src/services/recyclerService.js';
import { EmailAuthService } from '../src/services/emailAuthService.js';

const databaseUrl = process.env.RECYCLER_E2E_DATABASE_URL;
const isolated = databaseUrl && new URL(databaseUrl).pathname.startsWith('/kc_recycler_audit_');

describe.skipIf(!isolated)('Recycler → Admin → Recycler database flow', () => {
  if (!isolated) return;
  const db = new PrismaClient({ datasources: { db: { url: databaseUrl! } } });
  const config = loadConfig({ ...process.env, DATABASE_URL: databaseUrl!, APP_ENV: 'testing', NODE_ENV: 'test', OTP_PROVIDER: 'development', STORAGE_PROVIDER: 'local', RATE_LIMIT_STORE: 'memory' });
  const jwt = new JwtService(config);
  const collectorRepository = new CollectorRepository(db);
  const recyclerService = new RecyclerService(db);
  const app = createApp(config, db, jwt, new CollectorService(collectorRepository), {} as never, collectorRepository,
    undefined, undefined, recyclerService, undefined, undefined, undefined, undefined,
    new EmailAuthService(db, jwt));

  afterAll(async () => { await db.$disconnect(); });

  it('preserves submitted data and supports review, rejection, resubmission, verification, and profile edit', async () => {
    const unique = randomUUID().slice(0, 8);
    const password = 'RecyclerAudit!2026';
    const email = `recycler-${unique}@example.test`;
    const adminEmail = `admin-${unique}@example.test`;
    const salt = randomBytes(16);
    const adminPasswordHash = `${salt.toString('hex')}:${scryptSync(password, salt, 64).toString('hex')}`;
    await db.adminAccount.create({ data: { email: adminEmail, passwordHash: adminPasswordHash, active: true, displayName: 'Audit operator', permissions: ['RECYCLER_REVIEW', 'RECYCLER_AUTHORIZATION'] } });

    const categories = await request(app).get('/api/v1/materials/categories');
    expect(categories.status).toBe(200);
    expect(categories.body.data).toEqual(expect.arrayContaining(['PCB', 'CABLE', 'PLASTIC', 'OTHER']));

    const signup = await request(app).post('/api/v1/auth/signup').send({
      email, password, role: 'RECYCLER', preferredLanguage: 'ENGLISH',
      businessName: 'Green Loop Facility', areaName: 'Geeta Colony, Delhi', address: '13/352, Shastri Nagar, Delhi',
      latitude: 28.6485, longitude: 77.2754, authorizationNumber: `REG-${unique}`,
      authorizationAuthority: 'Delhi Pollution Control Committee', authorizationType: 'Recycler registration',
      authorizationEvidenceReference: `https://example.test/evidence/${unique}.pdf`,
      materialsAccepted: ['PCB', 'CABLE', 'PLASTIC'], pickupAvailable: true,
      pickupAvailability: 'FLEXIBLE', serviceRadiusKm: 35, pickupFee: 75,
      alternatePhone: '9876543212', operatingHours: { description: 'Mon–Sat 10 AM–6 PM' }
    });
    expect(signup.status).toBe(201);
    const recyclerId = signup.body.data.user.profileId as string;
    const recyclerToken = signup.body.data.token as string;
    expect(recyclerId).toBeTruthy();
    const self = await request(app).get('/api/v1/recycler/profile').auth(recyclerToken, { type: 'bearer' });
    expect(self.status).toBe(200);
    expect(self.body.data).toMatchObject({ name: 'Green Loop Facility', authorizationStatus: 'PENDING', pickupAvailable: true, pickupFee: 75, pickupAvailability: 'FLEXIBLE' });
    expect(self.body.data.materialsAccepted.map((row: { category: string }) => row.category)).toEqual(expect.arrayContaining(['PCB', 'CABLE', 'PLASTIC']));

    const adminLogin = await request(app).post('/api/v1/auth/admin-login').send({ email: adminEmail, password });
    expect(adminLogin.status).toBe(200);
    const adminToken = adminLogin.body.data.token as string;
    const admin = (method: 'get' | 'put', path: string) => request(app)[method](path).auth(adminToken, { type: 'bearer' });
    const queue = await admin('get', '/api/v1/admin/recyclers');
    expect(queue.status).toBe(200);
    expect(queue.body.data.some((row: { id: string }) => row.id === recyclerId)).toBe(true);
    const review = await admin('get', `/api/v1/admin/recyclers/${recyclerId}`);
    expect(review.status).toBe(200);
    expect(review.body.data).toMatchObject({
      name: 'Green Loop Facility', authorizationStatus: 'PENDING', pickupAvailable: true,
      authorizationDetails: { registrationNumber: `REG-${unique}`, authority: 'Delhi Pollution Control Committee', type: 'Recycler registration' },
      contact: { email, alternatePhone: '9876543212' },
      serviceArea: { maxPickupDistanceKm: 35 }
    });

    const underReview = await admin('put', `/api/v1/admin/recyclers/${recyclerId}/authorization`).send({ status: 'UNDER_REVIEW', reason: 'Checking submitted registration' });
    expect(underReview.status).toBe(200);
    const viewedUnderReview = await request(app).get('/api/v1/recycler/profile').auth(recyclerToken, { type: 'bearer' });
    expect(viewedUnderReview.body.data.authorizationStatus).toBe('UNDER_REVIEW');

    const reject = await admin('put', `/api/v1/admin/recyclers/${recyclerId}/authorization`).send({ status: 'REJECTED', reason: 'Please provide an updated permit scan' });
    expect(reject.status).toBe(200);
    const viewedRejected = await request(app).get('/api/v1/recycler/profile').auth(recyclerToken, { type: 'bearer' });
    expect(viewedRejected.body.data.authorizationDetails.reviewReason).toBe('Please provide an updated permit scan');

    const resubmit = await request(app).post('/api/v1/recycler/verification-request').auth(recyclerToken, { type: 'bearer' }).send({
      authority: 'Delhi Pollution Control Committee', registrationNumber: `REG-${unique}`, authorizationType: 'Recycler registration',
      evidenceReference: `https://example.test/evidence/${unique}-updated.pdf`
    });
    expect(resubmit.status).toBe(202);
    const reviewedAgain = await admin('get', `/api/v1/admin/recyclers/${recyclerId}`);
    expect(reviewedAgain.body.data.authorizationDetails.evidenceReference).toBe(`https://example.test/evidence/${unique}-updated.pdf`);

    const verify = await admin('put', `/api/v1/admin/recyclers/${recyclerId}/authorization`).send({ status: 'VERIFIED', verificationSource: 'Official register check' });
    expect(verify.status).toBe(200);
    const viewedVerified = await request(app).get('/api/v1/recycler/profile').auth(recyclerToken, { type: 'bearer' });
    expect(viewedVerified.body.data.authorizationStatus).toBe('VERIFIED');
    const operational = await request(app).get('/api/v1/recycler/profile').auth(recyclerToken, { type: 'bearer' });
    expect(operational.status).toBe(200);

    const edit = await request(app).put('/api/v1/account/profile').auth(recyclerToken, { type: 'bearer' }).send({ displayName: 'Green Loop Updated', email, areaName: 'Geeta Colony, Delhi', address: 'Updated facility address, Delhi' });
    expect(edit.status).toBe(200);
    const finalReview = await admin('get', `/api/v1/admin/recyclers/${recyclerId}`);
    expect(finalReview.body.data).toMatchObject({ name: 'Green Loop Updated', facilityLocation: { address: 'Updated facility address, Delhi' } });
  }, 120_000);

  it('shows a nearby household pickup to multiple collectors, locks the first claim, and allows a new request after cancellation', async () => {
    const unique = randomUUID().slice(0, 8);
    const password = 'PickupAudit!2026';
    const signup = async (role: 'HOUSEHOLD' | 'COLLECTOR', suffix: string) => {
      const response = await request(app).post('/api/v1/auth/signup').send({
        email: `${suffix}-${unique}@example.test`, password, role, preferredLanguage: 'ENGLISH',
        areaName: 'Geeta Colony, Delhi', address: 'Geeta Colony, Delhi', latitude: 28.6485, longitude: 77.2754
      });
      expect(response.status).toBe(201);
      return { token: response.body.data.token as string, profileId: response.body.data.user.profileId as string };
    };
    const household = await signup('HOUSEHOLD', 'household');
    const first = await signup('COLLECTOR', 'collector-a');
    const second = await signup('COLLECTOR', 'collector-b');
    const listing = await db.householdListing.create({ data: {
      householdId: household.profileId, materialCategory: 'PCB', estimatedWeight: 2, condition: 'INTACT',
      areaName: 'Geeta Colony, Delhi', pickupAddress: '13/352, Shastri Nagar, Delhi',
      latitude: 28.6485, longitude: 77.2754, photoReference: 'synthetic-audit-photo', status: 'POSTED'
    } });
    const open = await request(app).post(`/api/v1/household/listings/${listing.id}/pickups`).auth(household.token, { type: 'bearer' }).send({});
    expect(open.status).toBe(201);
    expect(open.body.data.status).toBe('WAITING_FOR_PICKUP');
    for (const collector of [first, second]) {
      const queue = await request(app).get('/api/v1/kabadiwala/pickups').auth(collector.token, { type: 'bearer' });
      expect(queue.status).toBe(200);
      expect(queue.body.data.some((row: { id: string }) => row.id === open.body.data.id)).toBe(true);
    }
    const accepted = await Promise.all([first, second].map(collector =>
      request(app).post(`/api/v1/kabadiwala/listings/${listing.id}/accept`).auth(collector.token, { type: 'bearer' })
    ));
    expect(accepted.map(response => response.status).sort()).toEqual([200, 409]);
    const winner = await db.pickupRequest.findUniqueOrThrow({ where: { id: open.body.data.id } });
    expect(winner.status).toBe('ACCEPTED');
    expect([first.profileId, second.profileId]).toContain(winner.kabadiwalaId);
    const loser = winner.kabadiwalaId === first.profileId ? second : first;
    const loserQueue = await request(app).get('/api/v1/kabadiwala/pickups').auth(loser.token, { type: 'bearer' });
    expect(loserQueue.body.data.some((row: { id: string }) => row.id === winner.id)).toBe(false);

    const cancelled = await request(app).post(`/api/v1/household/pickups/${winner.id}/cancel`).auth(household.token, { type: 'bearer' }).send({ reason: 'Plans changed' });
    expect(cancelled.status).toBe(200);
    expect((await db.householdListing.findUniqueOrThrow({ where: { id: listing.id } })).status).toBe('POSTED');
    const reopened = await request(app).post(`/api/v1/household/listings/${listing.id}/pickups`).auth(household.token, { type: 'bearer' }).send({});
    expect(reopened.status).toBe(201);
    expect(reopened.body.data).toMatchObject({ status: 'WAITING_FOR_PICKUP', kabadiwalaId: null });
  }, 120_000);
});
