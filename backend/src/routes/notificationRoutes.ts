import { Router } from 'express';
import type { PrismaClient } from '@prisma/client';
import type { JwtService } from '../services/jwt.js';
import { requireAccount } from '../middleware/auth.js';
import { NotificationService } from '../services/notificationService.js';
import { NotificationDeviceService, parseNotificationDeviceInput } from '../services/notificationDeviceService.js';

export const notificationRoutes = (jwt: JwtService, db: PrismaClient) => {
  const service = new NotificationService(db);
  const devices = new NotificationDeviceService(db);
  return Router()
    .use(requireAccount(jwt, db, false))
    .post('/devices', async (req, res) => {
      const device = await devices.register(req.identity!.collectorId, parseNotificationDeviceInput(req.body));
      res.status(201).json({ success: true, data: device, message: 'Notification device registered' });
    })
    .get('/devices', async (req, res) => {
      res.json({ success: true, data: await devices.list(req.identity!.collectorId), message: 'Notification devices retrieved' });
    })
    .post('/devices/unregister', async (req, res) => {
      const input = parseNotificationDeviceInput(req.body);
      res.json({ success: true, data: await devices.unregister(req.identity!.collectorId, input.token), message: 'Notification device unregistered' });
    })
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
