import { describe, expect, it, vi } from 'vitest';
import express from 'express';
import request from 'supertest';
import { JwtService } from '../src/services/jwt.js';
import { futureRoutes } from '../src/routes/futureRoutes.js';
import { errorHandler } from '../src/middleware/errors.js';

const pickup = {
  id: 'pickup-1',
  listingId: 'listing-1',
  householdId: 'household-1',
  kabadiwalaId: 'collector-1',
  status: 'ACCEPTED'
};

function fixture() {
  const conversation = {
    id: 'pickup-conversation-1', pickupRequestId: pickup.id,
    householdId: pickup.householdId, kabadiwalaId: pickup.kabadiwalaId,
    status: 'OPEN', lastMessageAt: null
  };
  let storedMessage: Record<string, unknown> | null = null;
  const db = {
    collector: { findUnique: vi.fn(async () => ({ accountStatus: 'ACTIVE' })) },
    recycler: { findUnique: vi.fn(async () => ({ authorizationStatus: 'VERIFIED' })) },
    user: { findFirst: vi.fn(async ({ where }: any) => ({ role: where.collectorProfileId.startsWith('household-') ? 'HOUSEHOLD' : 'COLLECTOR', accountStatus: 'ACTIVE' })) },
    pickupRequest: { findUnique: vi.fn(async () => pickup), findMany: vi.fn(async () => [pickup]) },
    pickupConversation: {
      findMany: vi.fn(async () => [conversation]),
      findUnique: vi.fn(async () => conversation),
      upsert: vi.fn(async () => conversation),
      update: vi.fn(async ({ data }: any) => Object.assign(conversation, data))
    },
    pickupChatMessage: {
      findMany: vi.fn(async () => storedMessage ? [storedMessage] : []),
      findFirst: vi.fn(async () => storedMessage),
      create: vi.fn(async ({ data }: any) => storedMessage = { id: 'message-1', ...data, createdAt: new Date(), readAt: null }),
      updateMany: vi.fn(async () => ({ count: 0 }))
    },
    conversation: { findUnique: vi.fn(async () => null) },
    chatMessage: { findFirst: vi.fn(async () => null), findMany: vi.fn(async () => []), updateMany: vi.fn(async () => ({ count: 0 })) }
  } as any;
  const jwt = new JwtService({ JWT_SECRET: 'pickup-chat-test-secret', JWT_EXPIRES_IN: '1h' } as never);
  const app = express();
  app.use(express.json());
  app.use('/future', futureRoutes(jwt, db));
  app.use(errorHandler);
  return { app, db, jwt };
}

describe('household pickup chat', () => {
  it('lets only the assigned household and Kabadiwala create and use the pickup thread', async () => {
    const { app, db, jwt } = fixture();
    const householdAuth = { Authorization: `Bearer ${jwt.generateHouseholdToken('household-1')}` };
    const collectorAuth = { Authorization: `Bearer ${jwt.generateToken('collector-1')}` };
    const otherHouseholdAuth = { Authorization: `Bearer ${jwt.generateHouseholdToken('household-2')}` };

    const created = await request(app).post('/future/pickup-conversations').set(householdAuth).send({ pickupRequestId: pickup.id });
    expect(created.status).toBe(201);
    expect(created.body.data).toMatchObject({ id: 'pickup-conversation-1', type: 'PICKUP', pickupRequestId: pickup.id });
    expect(db.pickupConversation.upsert).toHaveBeenCalledTimes(1);

    const sent = await request(app).post(`/future/conversations/${created.body.data.id}/messages`)
      .set(householdAuth).send({ clientMessageId: 'client-message-0001', body: 'I will be home at 5 PM.' });
    expect(sent.status).toBe(201);
    expect(sent.body.data).toMatchObject({ senderId: 'household-1', senderRole: 'HOUSEHOLD', body: 'I will be home at 5 PM.' });

    const replay = await request(app).post(`/future/conversations/${created.body.data.id}/messages`)
      .set(householdAuth).send({ clientMessageId: 'client-message-0001', body: 'I will be home at 5 PM.' });
    expect(replay.status).toBe(200);
    expect(db.pickupChatMessage.create).toHaveBeenCalledTimes(1);

    const read = await request(app).get(`/future/conversations/${created.body.data.id}/messages`).set(collectorAuth);
    expect(read.status).toBe(200);
    expect(read.body.data).toHaveLength(1);
    expect(db.pickupChatMessage.updateMany).toHaveBeenCalled();

    const denied = await request(app).get(`/future/conversations/${created.body.data.id}/messages`).set(otherHouseholdAuth);
    expect(denied.status).toBe(404);
  });

  it('refuses to open chat until the Kabadiwala accepts and assignment exists', async () => {
    const { app, jwt, db } = fixture();
    db.pickupRequest.findUnique.mockResolvedValueOnce({ ...pickup, status: 'REQUESTED' });
    const result = await request(app)
      .post('/future/pickup-conversations')
      .set({ Authorization: `Bearer ${jwt.generateHouseholdToken('household-1')}` })
      .send({ pickupRequestId: pickup.id });
    expect(result.status).toBe(409);
    expect(db.pickupConversation.upsert).not.toHaveBeenCalled();
  });
});
