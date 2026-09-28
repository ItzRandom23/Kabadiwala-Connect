import { describe, expect, it, vi } from 'vitest';
import express from 'express';
import request from 'supertest';
import { JwtService } from '../src/services/jwt.js';
import { futureRoutes } from '../src/routes/futureRoutes.js';
import { errorHandler } from '../src/middleware/errors.js';

function fixture() {
  const lot = { id: 'bulk-1', kabadiwalaId: 'collector-1', reservedForId: 'recycler-1' };
  const offer = { id: 'offer-1', bulkLotId: lot.id, recyclerId: 'recycler-1', status: 'ACCEPTED' };
  const conversation = { id: 'trade-chat-1', lotId: lot.id, collectorId: lot.kabadiwalaId, recyclerId: offer.recyclerId, status: 'OPEN', lastMessageAt: null };
  let message: Record<string, unknown> | null = null;
  const db = {
    collector: { findUnique: vi.fn(async () => ({ accountStatus: 'ACTIVE' })) },
    recycler: {
      findUnique: vi.fn(async () => ({ authorizationStatus: 'VERIFIED', accountStatus: 'ACTIVE' })),
      findFirst: vi.fn(async ({ where }: any) => where.id === 'recycler-1' ? { id: 'recycler-1' } : null)
    },
    user: { findFirst: vi.fn(async ({ where }: any) => ({ role: where.recyclerProfileId ? 'RECYCLER' : 'COLLECTOR', accountStatus: 'ACTIVE' })) },
    bulkLot: { findFirst: vi.fn(async ({ where }: any) => where.id === lot.id && where.reservedForId === lot.reservedForId && (!where.kabadiwalaId || where.kabadiwalaId === lot.kabadiwalaId) ? lot : null) },
    bulkOffer: { findFirst: vi.fn(async ({ where }: any) => where.bulkLotId === lot.id && where.recyclerId === offer.recyclerId && offer.status === where.status ? offer : null) },
    conversation: {
      findUnique: vi.fn(async ({ where }: any) => where.id === conversation.id ? conversation : null),
      findMany: vi.fn(async () => [conversation]),
      upsert: vi.fn(async () => conversation),
      update: vi.fn(async ({ data }: any) => Object.assign(conversation, data))
    },
    chatMessage: {
      findFirst: vi.fn(async () => message),
      findMany: vi.fn(async () => message ? [message] : []),
      create: vi.fn(async ({ data }: any) => message = { id: 'message-1', ...data, createdAt: new Date(), readAt: null }),
      updateMany: vi.fn(async () => { if (message) message.readAt = new Date(); return { count: message ? 1 : 0 }; }),
      groupBy: vi.fn(async ({ where }: any) => message && message.readAt == null && message.senderId !== where.senderId.not ? [{ conversationId: conversation.id, _count: { _all: 1 } }] : [])
    },
    notificationEvent: { create: vi.fn(async ({ data }: any) => ({ id: data.id, ...data })), updateMany: vi.fn(async () => ({ count: 1 })) },
    notificationDelivery: { upsert: vi.fn(async () => ({})) },
    pickupConversation: { findUnique: vi.fn(async () => null), findMany: vi.fn(async () => []) },
    pickupRequest: { findMany: vi.fn(async () => []) }
  } as any;
  const jwt = new JwtService({ JWT_SECRET: 'bulk-chat-test-secret', JWT_EXPIRES_IN: '1h' } as never);
  const app = express();
  app.use(express.json());
  app.use('/future', futureRoutes(jwt, db));
  app.use(errorHandler);
  return { app, db, jwt, offer, conversation };
}

describe('accepted bulk trade chat', () => {
  it('lets both transaction parties open the same thread, send, and read', async () => {
    const { app, db, jwt, conversation } = fixture();
    const collectorAuth = { Authorization: `Bearer ${jwt.generateToken('collector-1')}` };
    const recyclerAuth = { Authorization: `Bearer ${jwt.generateRecyclerToken('recycler-1')}` };
    const payload = { bulkLotId: 'bulk-1', recyclerId: 'recycler-1' };
    const opened = await request(app).post('/future/conversations').set(collectorAuth).send(payload);
    expect(opened.status).toBe(201);
    expect(opened.body.data.id).toBe(conversation.id);
    const reopened = await request(app).post('/future/conversations').set(recyclerAuth).send({ bulkLotId: 'bulk-1' });
    expect(reopened.status).toBe(201);
    expect(reopened.body.data.id).toBe(conversation.id);
    expect(db.conversation.upsert).toHaveBeenCalledTimes(2);

    const sent = await request(app).post(`/future/conversations/${conversation.id}/messages`).set(recyclerAuth)
      .send({ clientMessageId: 'bulk-message-0001', body: 'I can receive the lot tomorrow.' });
    expect(sent.status).toBe(201);
    expect(sent.body.data).toMatchObject({ senderRole: 'RECYCLER', senderId: 'recycler-1' });
    expect(db.notificationEvent.create).toHaveBeenCalledWith(expect.objectContaining({ data: expect.objectContaining({ accountId: 'collector-1', route: 'messages/trade-chat-1' }) }));
    const unread = await request(app).get('/future/conversations').set(collectorAuth);
    expect(unread.body.data[0].unreadCount).toBe(1);
    const read = await request(app).get(`/future/conversations/${conversation.id}/messages`).set(collectorAuth);
    expect(read.status).toBe(200);
    expect(read.body.data).toHaveLength(1);
    const outsider = await request(app).get(`/future/conversations/${conversation.id}/messages`)
      .set({ Authorization: `Bearer ${jwt.generateToken('collector-2')}` });
    expect(outsider.status).toBe(404);
  });

  it('rejects unaccepted offers and unrelated collectors', async () => {
    const { app, db, jwt, offer } = fixture();
    offer.status = 'PENDING';
    const collectorAuth = { Authorization: `Bearer ${jwt.generateToken('collector-1')}` };
    const pending = await request(app).post('/future/conversations').set(collectorAuth)
      .send({ bulkLotId: 'bulk-1', recyclerId: 'recycler-1' });
    expect(pending.status).toBe(409);
    const outsider = await request(app).post('/future/conversations')
      .set({ Authorization: `Bearer ${jwt.generateToken('collector-2')}` })
      .send({ bulkLotId: 'bulk-1', recyclerId: 'recycler-1' });
    expect(outsider.status).toBe(404);
    expect(db.conversation.upsert).not.toHaveBeenCalled();
  });
});
