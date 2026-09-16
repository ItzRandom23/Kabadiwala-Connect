import { randomUUID } from 'node:crypto';
import type { RequestHandler } from 'express';

const requestIdPattern = /^[A-Za-z0-9._:-]{1,96}$/;

export const requestContext: RequestHandler = (req, res, next) => {
  const supplied = req.header('x-request-id');
  const id = supplied && requestIdPattern.test(supplied) ? supplied : randomUUID();
  req.requestId = id;
  res.setHeader('x-request-id', id);
  const start = Date.now();
  res.on('finish', () => console.log(JSON.stringify({ requestId: id, method: req.method, path: req.path, status: res.statusCode, durationMs: Date.now() - start })));
  next();
};
