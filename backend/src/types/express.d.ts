import type { AuthIdentity } from './auth.js';
declare global { namespace Express { interface Request { requestId?: string; identity?: AuthIdentity; } } }
export {};
