import { afterEach, describe, expect, it, vi } from 'vitest';
import express from 'express';
import request from 'supertest';
import { JwtService } from '../src/services/jwt.js';
import { futureRoutes } from '../src/routes/futureRoutes.js';
import { errorHandler } from '../src/middleware/errors.js';

const jwt = new JwtService({ JWT_SECRET: 'material-suggestion-test-secret', JWT_EXPIRES_IN: '1h' } as never);

function materialSuggestionApp() {
  const aiInference = { create: vi.fn().mockResolvedValue({ id: 'inference-1' }) };
  const db = {
    collector: { findUnique: vi.fn().mockResolvedValue({ accountStatus: 'ACTIVE' }) },
    user: { findFirst: vi.fn().mockResolvedValue({ role: 'HOUSEHOLD' }) },
    aiInference
  } as any;
  const app = express();
  app.use('/future', futureRoutes(jwt, db));
  app.use(errorHandler);
  return { app, aiInference };
}

afterEach(() => {
  delete process.env.GEMINI_API_KEY;
  delete process.env.GEMINI_MODEL;
  vi.unstubAllGlobals();
});

describe('material suggestion photo contract', () => {
  it('rejects a missing photo with a safe validation response', async () => {
    const { app } = materialSuggestionApp();

    const response = await request(app)
      .post('/future/lots/material-suggestion')
      .set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-1')}`)
      .field('language', 'English');

    expect(response.status).toBe(422);
    expect(response.body.error).toMatchObject({
      code: 'VALIDATION_ERROR',
      message: 'A JPEG, PNG, or WebP photo is required'
    });
    expect(JSON.stringify(response.body)).not.toContain('Gemini');
  });

  it('accepts a correctly formed multipart photo and returns a manual-fallback service error when Gemini is unavailable', async () => {
    const { app, aiInference } = materialSuggestionApp();

    const response = await request(app)
      .post('/future/lots/material-suggestion')
      .set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-1')}`)
      .attach('photo', Buffer.from('normalized-image-bytes'), { filename: 'photo.jpg', contentType: 'image/jpeg' });

    expect(response.status).toBe(503);
    expect(response.body.error).toMatchObject({
      code: 'SERVICE_UNAVAILABLE',
      message: 'Material detection is temporarily unavailable. Choose the material manually.',
      details: { code: 'GEMINI_UNAVAILABLE' }
    });
    expect(aiInference.create).not.toHaveBeenCalled();
  });

  it('parses a valid provider response into a constrained result and records metadata only', async () => {
    process.env.GEMINI_API_KEY = 'gemini-test-key';
    process.env.GEMINI_MODEL = 'gemini-test-model';
    const fetchMock = vi.fn(async (input: string | URL, init?: RequestInit) => {
      expect(String(input)).toBe('https://generativelanguage.googleapis.com/v1beta/models/gemini-test-model:generateContent');
      expect((init?.headers as Record<string, string>)['x-goog-api-key']).toBe('gemini-test-key');
      const body = JSON.parse(String(init?.body)) as { contents: Array<{ parts: Array<{ inline_data: { mime_type: string; data: string } }> }> };
      expect(body.contents[0].parts[1].inline_data.mime_type).toBe('image/jpeg');
      expect(body.contents[0].parts[1].inline_data.data).toBe(Buffer.from('normalized-image-bytes').toString('base64'));
      return new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: JSON.stringify({ materialCategory: 'COPPER', confidence: 0.91, alternatives: ['CABLE', 'OTHER'], rationale: 'Visible copper wiring.' }) }] } }] }), { status: 200 });
    });
    vi.stubGlobal('fetch', fetchMock);
    const { app, aiInference } = materialSuggestionApp();

    const response = await request(app)
      .post('/future/lots/material-suggestion')
      .set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-1')}`)
      .field('language', 'English')
      .attach('photo', Buffer.from('normalized-image-bytes'), { filename: 'photo.jpg', contentType: 'image/jpeg' });

    expect(response.status).toBe(200);
    expect(response.body.data).toEqual({
      materialCategory: 'COPPER',
      confidence: 0.91,
      alternatives: ['CABLE', 'OTHER'],
      rationale: 'Visible copper wiring.',
      source: 'AI',
      model: 'gemini-test-model'
    });
    expect(response.body).not.toHaveProperty('candidates');
    expect(aiInference.create).toHaveBeenCalledWith(expect.objectContaining({
      data: expect.objectContaining({
        feature: 'MATERIAL_CLASSIFICATION',
        modelProvider: 'GOOGLE_GEMINI',
        inputProvenance: expect.objectContaining({ mimeType: 'image/jpeg', bytes: Buffer.byteLength('normalized-image-bytes') })
      })
    }));
  });

  it('normalizes provider category casing and LCD aliases', async () => {
    process.env.GEMINI_API_KEY = 'gemini-test-key';
    process.env.GEMINI_MODEL = 'gemini-test-model';
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: JSON.stringify({ materialCategory: 'lcd', confidence: 0.84, alternatives: ['plastic', 'LCD_PANEL'], rationale: 'A flat display panel is visible.' }) }] } }] }), { status: 200 })));
    const { app } = materialSuggestionApp();

    const response = await request(app)
      .post('/future/lots/material-suggestion')
      .set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-1')}`)
      .attach('photo', Buffer.from('normalized-image-bytes'), { filename: 'photo.jpg', contentType: 'image/jpeg' });

    expect(response.status).toBe(200);
    expect(response.body.data).toMatchObject({ materialCategory: 'LCD_PANEL', alternatives: ['PLASTIC'] });
  });

  it('does not record malformed provider output as an AI result', async () => {
    process.env.GEMINI_API_KEY = 'gemini-test-key';
    process.env.GEMINI_MODEL = 'gemini-test-model';
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: 'not-json' }] } }] }), { status: 200 })));
    const { app, aiInference } = materialSuggestionApp();

    const response = await request(app)
      .post('/future/lots/material-suggestion')
      .set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-1')}`)
      .attach('photo', Buffer.from('normalized-image-bytes'), { filename: 'photo.jpg', contentType: 'image/jpeg' });

    expect(response.status).toBe(503);
    expect(response.body.error).toMatchObject({
      code: 'SERVICE_UNAVAILABLE',
      message: 'Material detection is temporarily unavailable. Choose the material manually.',
      details: { code: 'GEMINI_INVALID_RESPONSE' }
    });
    expect(aiInference.create).not.toHaveBeenCalled();
  });

  it('logs only safe provider status metadata for an unauthorized Gemini key', async () => {
    process.env.GEMINI_API_KEY = 'do-not-log-this-key';
    process.env.GEMINI_MODEL = 'gemini-test-model';
    vi.stubGlobal('fetch', vi.fn(async () => new Response('provider body must not be logged', { status: 401 })));
    const warning = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    const { app } = materialSuggestionApp();

    const response = await request(app)
      .post('/future/lots/material-suggestion')
      .set('Authorization', `Bearer ${jwt.generateHouseholdToken('household-1')}`)
      .attach('photo', Buffer.from('normalized-image-bytes'), { filename: 'photo.jpg', contentType: 'image/jpeg' });

    expect(response.status).toBe(503);
    const providerLog = warning.mock.calls
      .map(([value]) => String(value))
      .find(value => value.includes('gemini_provider_unavailable'));
    expect(providerLog).toBeDefined();
    expect(providerLog).toContain('"status":401');
    expect(providerLog).toContain('"operation":"material_suggestion"');
    expect(providerLog).not.toContain('do-not-log-this-key');
    expect(providerLog).not.toContain('provider body must not be logged');
    warning.mockRestore();
  });
});
