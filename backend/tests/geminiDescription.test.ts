import { afterEach, describe, expect, it, vi } from 'vitest';
import prismaPackage from '@prisma/client';
import { maybeAiDescription } from '../src/routes/futureRoutes.js';

const { DescriptionSource } = prismaPackage;

afterEach(() => {
  delete process.env.AI_DESCRIPTION_URL;
  delete process.env.AI_DESCRIPTION_API_KEY;
  delete process.env.GEMINI_API_KEY;
  delete process.env.GEMINI_MODEL;
  vi.unstubAllGlobals();
});

describe('Gemini lot descriptions', () => {
  it('calls Gemini with a server-side API key and maps its response', async () => {
    process.env.GEMINI_API_KEY = 'gemini-test-key';
    process.env.GEMINI_MODEL = 'gemini-test-model';
    const fetchMock = vi.fn(async (input: string | URL, init?: RequestInit) => {
      expect(String(input)).toBe('https://generativelanguage.googleapis.com/v1beta/models/gemini-test-model:generateContent');
      expect((init?.headers as Record<string, string>)['x-goog-api-key']).toBe('gemini-test-key');
      const request = JSON.parse(String(init?.body)) as { contents: Array<{ parts: Array<{ text: string }> }> };
      expect(request.contents[0].parts[0].text).toContain('Material: Copper Cable');
      return new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: 'Clean copper cable ready for sorting.' }] } }] }), { status: 200 });
    });
    vi.stubGlobal('fetch', fetchMock);

    const result = await maybeAiDescription({ material: 'Copper Cable', condition: 'INTACT', weight: 4.5, language: 'English' });

    expect(result).toEqual({ text: 'Clean copper cable ready for sorting.', source: DescriptionSource.AI, model: 'gemini-test-model' });
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it('falls back to the deterministic template when Gemini fails', async () => {
    process.env.GEMINI_API_KEY = 'gemini-test-key';
    vi.stubGlobal('fetch', vi.fn(async () => new Response('unavailable', { status: 503 })));

    const result = await maybeAiDescription({ material: 'Battery', condition: 'DAMAGED', weight: 2 });

    expect(result.source).toBe(DescriptionSource.TEMPLATE);
    expect(result.text).toContain('damaged lot of battery');
  });
});
