import type { Response } from 'express';
export function success<T>(res: Response, data: T, message = 'Operation successful', status = 200) { return res.status(status).json({ success: true, data, message }); }
