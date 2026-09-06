import { randomUUID } from 'node:crypto';
import type { RequestHandler } from 'express';
export const requestContext: RequestHandler = (req, res, next) => { const id = req.header('x-request-id') ?? randomUUID(); req.requestId = id; res.setHeader('x-request-id', id); const start = Date.now(); res.on('finish', () => console.log(JSON.stringify({ requestId: id, method: req.method, path: req.path, status: res.statusCode, durationMs: Date.now() - start }))); next(); };
