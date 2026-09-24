import jwt from 'jsonwebtoken';
import { randomUUID } from 'node:crypto';
import type { AppConfig } from '../config/env.js';
export type AuthIdentity = { collectorId: string; role: 'HOUSEHOLD'|'COLLECTOR'|'ADMIN'|'RECYCLER'; iat?: number; exp?: number };
export class JwtService {
  constructor(private readonly config: AppConfig) {}
  generateToken(collectorId: string) { return this.generate(collectorId, 'COLLECTOR'); }
  generateHouseholdToken(collectorId: string) { return this.generate(collectorId, 'HOUSEHOLD'); }
  generateAdminToken(adminId: string) { return this.generate(adminId, 'ADMIN'); }
  generateRecyclerToken(recyclerId: string) { return this.generate(recyclerId, 'RECYCLER'); }
  generateHouseholdPickupQrToken(pickupId: string, householdId: string) {
    return jwt.sign(
      { purpose: 'HOUSEHOLD_PICKUP_QR', pickupId, nonce: randomUUID() },
      this.config.JWT_SECRET,
      { subject: householdId, expiresIn: '10m' }
    );
  }
  verifyHouseholdPickupQrToken(token: string, pickupId: string, householdId: string) {
    try {
      const decoded = jwt.verify(token, this.config.JWT_SECRET);
      return typeof decoded === 'object' &&
        decoded.purpose === 'HOUSEHOLD_PICKUP_QR' &&
        decoded.pickupId === pickupId &&
        decoded.sub === householdId;
    } catch {
      return false;
    }
  }
  private generate(id: string, role: AuthIdentity['role']) {
    return jwt.sign({ sub: id, role }, this.config.JWT_SECRET, { expiresIn: this.config.JWT_EXPIRES_IN as jwt.SignOptions['expiresIn'] });
  }
  verifyToken(token: string): AuthIdentity {
    const decoded = jwt.verify(token, this.config.JWT_SECRET);
    if (typeof decoded !== 'object' || !['HOUSEHOLD','COLLECTOR','ADMIN','RECYCLER'].includes(String(decoded.role)) || typeof decoded.sub !== 'string') throw new Error('Invalid token claims');
    return { collectorId: decoded.sub, role: decoded.role as AuthIdentity['role'], iat: decoded.iat, exp: decoded.exp };
  }
}
