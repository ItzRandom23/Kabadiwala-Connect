import { Router } from 'express';
import type { PrismaClient } from '@prisma/client';
import type { JwtService } from '../services/jwt.js';
import { requireAccount } from '../middleware/auth.js';
import { NotificationService } from '../services/notificationService.js';

export const notificationRoutes = (jwt: JwtService, db: PrismaClient) => {
  const service = new NotificationService(db);
  return Router()
    .use(requireAccount(jwt, db, false))
    .get('/', async (req, res) => {
      const unreadOnly = req.query.unread === 'true';
      const limit = typeof req.query.limit === 'string' ? Number(req.query.limit) : 50;
      const items = await service.list(req.identity!.collectorId, unreadOnly, Number.isFinite(limit) ? limit : 50);
      res.json({ success: true, data: items, message: 'Notifications retrieved' });
    })
    .get('/unread-count', async (req, res) => {
      res.json({ success: true, data: { count: await service.unreadCount(req.identity!.collectorId) }, message: 'Unread count retrieved' });
    })
    .post('/:notificationId/read', async (req, res) => {
      const ok = await service.markRead(req.identity!.collectorId, String(req.params.notificationId));
      res.json({ success: true, data: { marked: ok }, message: ok ? 'Notification marked read' : 'Notification already read' });
    })
    .post('/read-all', async (req, res) => {
      const count = await service.markAllRead(req.identity!.collectorId);
      res.json({ success: true, data: { marked: count > 0, count }, message: 'Notifications marked read' });
    });
};
