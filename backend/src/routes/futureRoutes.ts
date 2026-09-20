import { Router } from 'express';
import type { Request } from 'express';
import multer from 'multer';
import type { AccountRole as AccountRoleType, PrismaClient } from '@prisma/client';
import prismaPackage from '@prisma/client';
import { createHash, randomUUID } from 'node:crypto';
import { z } from 'zod';
import type { JwtService } from '../services/jwt.js';
import { requireAccount } from '../middleware/auth.js';
import { AppError } from '../utils/errors.js';

const { AccountRole, DescriptionSource, MessageStatus, PreferredLanguage, RewardStatus } = prismaPackage;

const languageSchema = z.nativeEnum(PreferredLanguage);
const appearanceSchema = z.enum(['SYSTEM', 'LIGHT', 'DARK']);
const materialUpload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 5 * 1024 * 1024, files: 1 },
  fileFilter: (_req, file, callback) => callback(null, ['image/jpeg', 'image/png', 'image/webp'].includes(file.mimetype))
});
const materialCategories = ['CRT', 'LCD_PANEL', 'PCB', 'CABLE', 'COPPER', 'BATTERY', 'MOTOR', 'MAGNET', 'PLASTIC', 'OTHER'] as const;

function indiaMonthKey(date: Date) {
  const parts = new Intl.DateTimeFormat('en-US', { timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit' }).formatToParts(date);
  const year = parts.find(part => part.type === 'year')?.value ?? '0000';
  const month = parts.find(part => part.type === 'month')?.value ?? '01';
  return `${year}-${month}`;
}

function actor(req: Request) {
  if (!req.identity) throw new AppError('AUTHENTICATION_REQUIRED', 'Sign in to continue', 401);
  return req.identity;
}

function requireRole(req: Request, ...roles: Array<'COLLECTOR' | 'RECYCLER' | 'HOUSEHOLD'>) {
  const identity = actor(req);
  if (!roles.includes(identity.role as 'COLLECTOR' | 'RECYCLER' | 'HOUSEHOLD')) {
    const label = roles.includes('COLLECTOR') ? 'Kabadiwala' : roles.includes('RECYCLER') ? 'Recycler' : 'Household';
    throw new AppError('AUTHORIZATION_ERROR', `${label} access required`, 403);
  }
  return identity.collectorId;
}

function requireLegacyTransactionParticipant(req: Request) {
  const identity = actor(req);
  if (identity.role !== 'COLLECTOR' && identity.role !== 'RECYCLER') throw new AppError('AUTHORIZATION_ERROR', 'This conversation belongs to a kabadiwala-to-recycler transaction', 403);
  return identity;
}

function pageNumber(value: unknown, fallback: number, max: number) {
  const parsed = Number(value ?? fallback);
  return Number.isFinite(parsed) ? Math.min(max, Math.max(1, Math.floor(parsed))) : fallback;
}

async function refreshRewards(db: PrismaClient, collectorId: string) {
  const now = new Date();
  const programs = await db.rewardProgram.findMany({ where: { active: true, startsAt: { lte: now }, OR: [{ endsAt: null }, { endsAt: { gte: now } }] } });
  const payments = await db.payment.findMany({ where: { collectorId, status: { in: ['RECORDED', 'VERIFIED'] } }, select: { amount: true } });
  const handovers = await db.handover.findMany({ where: { collectorId, status: 'COMPLETED' }, select: { id: true, weight: true, actualWeight: true } });
  const totalRupees = payments.reduce((sum, item) => sum + item.amount, 0);
  const totalKg = handovers.reduce((sum, item) => sum + (item.actualWeight ?? item.weight), 0);
  const periodKey = indiaMonthKey(now);
  for (const program of programs) {
    const qualifies = (program.thresholdKg == null || totalKg >= program.thresholdKg) && (program.thresholdRupees == null || totalRupees >= program.thresholdRupees);
    await db.rewardLedger.upsert({
      where: { programId_collectorId_periodKey: { programId: program.id, collectorId, periodKey } },
      update: { qualifyingKg: totalKg, qualifyingRupees: totalRupees, rewardAmount: qualifies ? program.rewardAmount : 0, status: qualifies ? RewardStatus.EARNED : RewardStatus.PROGRESS, earnedAt: qualifies ? new Date() : null, sourceHandoverId: handovers.at(-1)?.id },
      create: { programId: program.id, collectorId, periodKey, qualifyingKg: totalKg, qualifyingRupees: totalRupees, rewardAmount: qualifies ? program.rewardAmount : 0, status: qualifies ? RewardStatus.EARNED : RewardStatus.PROGRESS, earnedAt: qualifies ? new Date() : null, sourceHandoverId: handovers.at(-1)?.id, expiresAt: program.endsAt }
    });
  }
  return { programs, ledgers: await db.rewardLedger.findMany({ where: { collectorId }, orderBy: { updatedAt: 'desc' } }) };
}

function schemeResult(rules: unknown, answers: Record<string, unknown>) {
  if (!rules || typeof rules !== 'object' || Array.isArray(rules)) return 'POSSIBLY_ELIGIBLE';
  const entries = Object.entries(rules as Record<string, unknown>);
  if (entries.some(([key]) => answers[key] === undefined || answers[key] === null || answers[key] === '')) return 'NOT_ENOUGH_INFORMATION';
  const matches = entries.every(([key, allowed]) => {
    const answer = answers[key];
    return Array.isArray(allowed) ? allowed.map(String).includes(String(answer)) : String(answer).toLowerCase() === String(allowed).toLowerCase();
  });
  return matches ? 'LIKELY_ELIGIBLE' : 'NOT_ELIGIBLE';
}

function templateDescription(input: { material?: string; condition?: string; weight?: number; notes?: string }) {
  const material = input.material?.replace(/_/g, ' ').toLowerCase() || 'mixed electronic material';
  const condition = input.condition?.toLowerCase() || 'used';
  const weight = Number.isFinite(input.weight) && input.weight ? ` approximately ${input.weight} kg` : '';
  const notes = input.notes?.trim() ? ` Notes: ${input.notes.trim().slice(0, 220)}.` : '';
  return `A ${condition} lot of ${material}${weight}.${notes}`.slice(0, 500);
}

function geminiPrompt(input: { material?: string; condition?: string; weight?: number; notes?: string; language?: string }) {
  const language = input.language || 'English';
  return [
    'Write one concise, factual recycling-lot description for a field worker.',
    `Write in ${language}. Return only the description, without headings, markdown, prices, or claims not present in the input. Keep it under 400 characters.`,
    `Material: ${input.material || 'not specified'}`,
    `Condition: ${input.condition || 'not specified'}`,
    `Weight kg: ${Number.isFinite(input.weight) ? input.weight : 'not specified'}`,
    `Collector notes: ${(input.notes || 'none').slice(0, 220)}`
  ].join('\n');
}

function geminiText(body: unknown): string | null {
  if (!body || typeof body !== 'object') return null;
  const candidates = (body as { candidates?: unknown }).candidates;
  if (!Array.isArray(candidates)) return null;
  const parts = candidates.flatMap(candidate => {
    if (!candidate || typeof candidate !== 'object') return [];
    const content = (candidate as { content?: unknown }).content;
    if (!content || typeof content !== 'object') return [];
    const candidateParts = (content as { parts?: unknown }).parts;
    return Array.isArray(candidateParts) ? candidateParts : [];
  });
  const text = parts
    .map(part => part && typeof part === 'object' ? (part as { text?: unknown }).text : null)
    .find(value => typeof value === 'string' && value.trim().length >= 8);
  return typeof text === 'string' ? text.trim() : null;
}

function geminiModelName() {
  return (process.env.GEMINI_MODEL || 'gemini-2.5-flash').replace(/^models\//, '').replace(/[^A-Za-z0-9._-]/g, '') || 'gemini-2.5-flash';
}

async function callGemini(parts: unknown[], maxOutputTokens = 220) {
  const key = process.env.GEMINI_API_KEY?.trim();
  if (!key) return null;
  const model = geminiModelName();
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 5500);
  try {
    const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'x-goog-api-key': key },
      body: JSON.stringify({ contents: [{ role: 'user', parts }], generationConfig: { temperature: 0.1, maxOutputTokens } }),
      signal: controller.signal
    });
    if (!response.ok) return null;
    const text = geminiText(await response.json());
    return text ? { text, model } : null;
  } catch {
    return null;
  } finally {
    clearTimeout(timeout);
  }
}

function parseJsonObject(text: string) {
  const cleaned = text.trim().replace(/^```(?:json)?\s*/i, '').replace(/\s*```$/i, '');
  try {
    const parsed = JSON.parse(cleaned);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed as Record<string, unknown> : null;
  } catch {
    return null;
  }
}

export async function maybeAiDescription(input: { material?: string; condition?: string; weight?: number; notes?: string; language?: string }) {
  const fallback = templateDescription(input);
  const url = process.env.AI_DESCRIPTION_URL;
  const geminiKey = process.env.GEMINI_API_KEY?.trim();
  const geminiModel = geminiModelName();
  if (!url && !geminiKey) return { text: fallback, source: DescriptionSource.TEMPLATE, model: null };
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 3500);
  try {
    const response = url
      ? await fetch(url, { method: 'POST', headers: { 'content-type': 'application/json', ...(process.env.AI_DESCRIPTION_API_KEY ? { authorization: `Bearer ${process.env.AI_DESCRIPTION_API_KEY}` } : {}) }, body: JSON.stringify(input), signal: controller.signal })
      : await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${geminiModel}:generateContent`, {
        method: 'POST',
        headers: { 'content-type': 'application/json', 'x-goog-api-key': geminiKey! },
        body: JSON.stringify({ contents: [{ role: 'user', parts: [{ text: geminiPrompt(input) }] }], generationConfig: { temperature: 0.2, maxOutputTokens: 160 } }),
        signal: controller.signal
      });
    if (!response.ok) return { text: fallback, source: DescriptionSource.TEMPLATE, model: null };
    const body = await response.json();
    const text = url ? (body as { text?: unknown }).text : geminiText(body);
    if (typeof text !== 'string' || text.trim().length < 8) return { text: fallback, source: DescriptionSource.TEMPLATE, model: null };
    return { text: text.trim().slice(0, 500), source: DescriptionSource.AI, model: url ? (typeof (body as { model?: unknown }).model === 'string' ? (body as { model: string }).model : 'server-provider') : geminiModel };
  } catch {
    return { text: fallback, source: DescriptionSource.TEMPLATE, model: null };
  } finally {
    clearTimeout(timeout);
  }
}

async function assertConversationParticipant(db: PrismaClient, conversationId: string, accountId: string) {
  const conversation = await db.conversation.findUnique({ where: { id: conversationId } });
  if (!conversation || (conversation.collectorId !== accountId && conversation.recyclerId !== accountId)) throw new AppError('NOT_FOUND', 'Conversation not found', 404);
  return conversation;
}

export const futureRoutes = (jwt: JwtService, db: PrismaClient) => {
  const router = Router();
  router.use(requireAccount(jwt, db));

  router.get('/preferences', async (req, res) => {
    const identity = actor(req);
    const user = identity.role === 'COLLECTOR' || identity.role === 'HOUSEHOLD'
      ? await db.user.findFirst({ where: { collectorProfileId: identity.collectorId }, select: { preferredLanguage: true, appearanceMode: true, smsNotificationsEnabled: true, pushNotificationsEnabled: true } })
      : await db.user.findFirst({ where: { recyclerProfileId: identity.collectorId }, select: { preferredLanguage: true, appearanceMode: true, smsNotificationsEnabled: true, pushNotificationsEnabled: true } });
    return res.json({ success: true, data: {
      preferredLanguage: user?.preferredLanguage ?? 'ENGLISH',
      appearanceMode: user?.appearanceMode ?? 'SYSTEM',
      smsNotificationsEnabled: user?.smsNotificationsEnabled ?? true,
      pushNotificationsEnabled: user?.pushNotificationsEnabled ?? true
    }, message: 'Preferences retrieved' });
  });

  router.patch('/preferences', async (req, res) => {
    const identity = actor(req);
    const parsed = z.object({ preferredLanguage: languageSchema.optional(), appearanceMode: appearanceSchema.optional(), smsNotificationsEnabled: z.boolean().optional(), pushNotificationsEnabled: z.boolean().optional() }).safeParse(req.body);
    if (!parsed.success) throw new AppError('VALIDATION_ERROR', 'Invalid preferences', 422);
    const user = identity.role === 'COLLECTOR' || identity.role === 'HOUSEHOLD'
      ? await db.user.findFirst({ where: { collectorProfileId: identity.collectorId } })
      : await db.user.findFirst({ where: { recyclerProfileId: identity.collectorId } });
    if (!user) throw new AppError('NOT_FOUND', 'Account not found', 404);
    const data: { preferredLanguage?: any; appearanceMode?: string; smsNotificationsEnabled?: boolean; pushNotificationsEnabled?: boolean } = {};
    if (parsed.data.preferredLanguage) data.preferredLanguage = parsed.data.preferredLanguage;
    if (parsed.data.appearanceMode) data.appearanceMode = parsed.data.appearanceMode;
    if (parsed.data.smsNotificationsEnabled !== undefined) data.smsNotificationsEnabled = parsed.data.smsNotificationsEnabled;
    if (parsed.data.pushNotificationsEnabled !== undefined) data.pushNotificationsEnabled = parsed.data.pushNotificationsEnabled;
    const updated = await db.user.update({ where: { id: user.id }, data, select: { preferredLanguage: true, appearanceMode: true, smsNotificationsEnabled: true, pushNotificationsEnabled: true } });
    return res.json({ success: true, data: {
      preferredLanguage: updated.preferredLanguage ?? 'ENGLISH',
      appearanceMode: updated.appearanceMode ?? 'SYSTEM',
      smsNotificationsEnabled: updated.smsNotificationsEnabled ?? true,
      pushNotificationsEnabled: updated.pushNotificationsEnabled ?? true
    }, message: 'Preferences saved' });
  });

  router.get('/rewards', async (req, res) => {
    const collectorId = requireRole(req, 'COLLECTOR');
    const result = await refreshRewards(db, collectorId);
    const programsById = new Map(result.programs.map((item) => [item.id, item]));
    return res.json({ success: true, data: result.ledgers.map((ledger) => ({ ...ledger, program: programsById.get(ledger.programId) ?? null })), message: 'Rewards retrieved' });
  });

  router.get('/schemes', async (_req, res) => {
    const schemes = await db.governmentScheme.findMany({ where: { active: true }, orderBy: { lastVerifiedAt: 'desc' } });
    return res.json({ success: true, data: schemes, message: 'Government schemes retrieved' });
  });

  router.post('/schemes/check', async (req, res) => {
    const parsed = z.object({ schemeId: z.string().min(1), answers: z.record(z.string(), z.unknown()) }).safeParse(req.body);
    if (!parsed.success) throw new AppError('VALIDATION_ERROR', 'Answer the eligibility questions', 422);
    const scheme = await db.governmentScheme.findUnique({ where: { id: parsed.data.schemeId } });
    if (!scheme || !scheme.active) throw new AppError('NOT_FOUND', 'Scheme not found', 404);
    return res.json({ success: true, data: { schemeId: scheme.id, result: schemeResult(scheme.eligibilityRules, parsed.data.answers), sourceUrl: scheme.sourceUrl, lastVerifiedAt: scheme.lastVerifiedAt }, message: 'Eligibility checked' });
  });

  router.get('/activities', async (_req, res) => {
    const activities = await db.diyActivity.findMany({ where: { active: true }, orderBy: [{ difficulty: 'asc' }, { minutes: 'asc' }] });
    return res.json({ success: true, data: activities, message: 'Activities retrieved' });
  });

  router.get('/activities/:slug', async (req, res) => {
    const activity = await db.diyActivity.findFirst({ where: { slug: req.params.slug, active: true } });
    if (!activity) throw new AppError('NOT_FOUND', 'Activity not found', 404);
    return res.json({ success: true, data: activity, message: 'Activity retrieved' });
  });

  router.post('/lots/description-suggestion', async (req, res) => {
    const collectorId = requireRole(req, 'COLLECTOR');
    const parsed = z.object({ lotId: z.string().optional(), material: z.string().max(100).optional(), condition: z.string().max(50).optional(), weight: z.number().finite().nonnegative().optional(), notes: z.string().max(500).optional(), language: z.string().max(12).optional(), consentForTraining: z.boolean().default(false) }).safeParse(req.body);
    if (!parsed.success) throw new AppError('VALIDATION_ERROR', 'Description details are invalid', 422);
    if (parsed.data.lotId) {
      const ownedLot = await db.lot.findFirst({ where: { id: parsed.data.lotId, collectorId }, select: { id: true } });
      if (!ownedLot) throw new AppError('NOT_FOUND', 'Lot not found', 404, { code: 'LOT_NOT_FOUND' });
    }
    const suggestion = await maybeAiDescription(parsed.data);
    if (parsed.data.lotId) await db.lotDescription.create({ data: { lotId: parsed.data.lotId, text: suggestion.text, source: suggestion.source, model: suggestion.model } });
    await db.aiInference.create({ data: { lotId: parsed.data.lotId, feature: 'LOT_DESCRIPTION', modelProvider: suggestion.source === DescriptionSource.AI ? (process.env.AI_DESCRIPTION_URL ? 'CUSTOM' : 'GOOGLE_GEMINI') : 'TEMPLATE', modelVersion: suggestion.model ?? 'deterministic-template-v1', inputProvenance: { material: parsed.data.material, condition: parsed.data.condition, hasWeight: parsed.data.weight !== undefined, hasNotes: Boolean(parsed.data.notes), language: parsed.data.language }, prediction: { text: suggestion.text, source: suggestion.source }, consentForTraining: parsed.data.consentForTraining } });
    return res.json({ success: true, data: suggestion, message: suggestion.source === DescriptionSource.AI ? 'Description suggestion generated' : 'Offline description suggestion generated' });
  });

  router.post('/lots/material-suggestion', materialUpload.single('photo'), async (req, res) => {
    // Both field collectors and households can use the same assistive
    // classifier. Ownership-sensitive lot/listing mutations remain guarded by
    // their dedicated route handlers.
    requireRole(req, 'COLLECTOR', 'HOUSEHOLD');
    const language = typeof req.body?.language === 'string' ? req.body.language.slice(0, 24) : 'English';
    const photo = req.file;
    if (!photo) throw new AppError('VALIDATION_ERROR', 'A JPEG, PNG, or WebP photo is required', 422);
    const fallback = { materialCategory: 'OTHER', confidence: 0, alternatives: [], rationale: 'The photo could not be identified with enough confidence. Choose the material yourself.', source: 'TEMPLATE', model: null };
    const result = await callGemini([
      { text: [
        'Identify the most likely recyclable material in this photo for a collector. This is only a suggestion; never invent certainty.',
        `Write the response in ${language} for the rationale, but keep materialCategory as an English enum.`,
        `Return JSON only with exactly these keys: materialCategory, confidence, alternatives, rationale.`,
        `materialCategory must be one of: ${materialCategories.join(', ')}. confidence must be a number from 0 to 1. alternatives must be an array of at most 2 allowed categories. If uncertain, use OTHER and confidence below 0.5.`,
        'Do not identify brands, people, addresses, or safety compliance. Do not make pricing claims.'
      ].join('\n') },
      { inline_data: { mime_type: photo.mimetype, data: photo.buffer.toString('base64') } }
    ], 220);
    if (!result) {
      throw new AppError('SERVICE_UNAVAILABLE', 'Material detection is temporarily unavailable. Choose the material manually.', 503, { code: 'GEMINI_UNAVAILABLE' });
    }
    const parsed = parseJsonObject(result.text);
    const category = typeof parsed?.materialCategory === 'string' && materialCategories.includes(parsed.materialCategory as typeof materialCategories[number]) ? parsed.materialCategory : 'OTHER';
    const confidence = typeof parsed?.confidence === 'number' && Number.isFinite(parsed.confidence) ? Math.max(0, Math.min(1, parsed.confidence)) : 0;
    const alternatives = Array.isArray(parsed?.alternatives) ? parsed.alternatives.filter((item): item is string => typeof item === 'string' && materialCategories.includes(item as typeof materialCategories[number])).slice(0, 2) : [];
    const rationale = typeof parsed?.rationale === 'string' && parsed.rationale.trim() ? parsed.rationale.trim().slice(0, 300) : fallback.rationale;
    const data = { materialCategory: category, confidence, alternatives, rationale, source: 'AI', model: result.model };
    await db.aiInference.create({ data: { feature: 'MATERIAL_CLASSIFICATION', modelProvider: 'GOOGLE_GEMINI', modelVersion: result.model, inputProvenance: { imageSha256: createHash('sha256').update(photo.buffer).digest('hex'), mimeType: photo.mimetype, bytes: photo.size, language }, prediction: data, confidence, consentForTraining: String(req.body?.consentForTraining).toLowerCase() === 'true' } });
    return res.json({ success: true, data, message: 'Material suggestion generated' });
  });

  router.get('/recyclers/:recyclerId/reviews', async (req, res) => {
    const reviews = await db.recyclerReview.findMany({ where: { recyclerId: req.params.recyclerId, verified: true }, orderBy: { createdAt: 'desc' }, take: 50, select: { id: true, rating: true, pickupReliability: true, paymentClarity: true, comment: true, createdAt: true, verified: true } });
    return res.json({ success: true, data: reviews, message: 'Verified reviews retrieved' });
  });

  router.post('/reviews', async (req, res) => {
    const collectorId = requireRole(req, 'COLLECTOR');
    const parsed = z.object({ handoverId: z.string().min(1), rating: z.number().int().min(1).max(5), pickupReliability: z.number().int().min(1).max(5).optional(), paymentClarity: z.number().int().min(1).max(5).optional(), comment: z.string().max(500).optional() }).safeParse(req.body);
    if (!parsed.success) throw new AppError('VALIDATION_ERROR', 'Rating details are invalid', 422);
    const handover = await db.handover.findFirst({ where: { id: parsed.data.handoverId, collectorId, status: 'COMPLETED' }, select: { recyclerId: true } });
    if (!handover) throw new AppError('CONFLICT', 'A verified completed handover is required', 409);
    const payment = await db.payment.findFirst({ where: { collectorId, lot: { handovers: { some: { id: parsed.data.handoverId } } }, status: { in: ['RECORDED', 'VERIFIED'] } } });
    if (!payment) throw new AppError('CONFLICT', 'A recorded payment is required before rating', 409);
    try {
      const review = await db.recyclerReview.create({ data: { ...parsed.data, collectorId, recyclerId: handover.recyclerId, verified: true } });
      const aggregate = await db.recyclerReview.aggregate({ where: { recyclerId: handover.recyclerId, verified: true }, _avg: { rating: true }, _count: { rating: true } });
      await db.recycler.update({ where: { id: handover.recyclerId }, data: { rating: aggregate._avg.rating ?? undefined, reviewCount: aggregate._count.rating } });
      return res.status(201).json({ success: true, data: review, message: 'Verified rating submitted' });
    } catch (error: any) {
      if (error?.code === 'P2002') throw new AppError('CONFLICT', 'This handover has already been rated', 409);
      throw error;
    }
  });

  router.get('/conversations', async (req, res) => {
    const identity = requireLegacyTransactionParticipant(req);
    const conversations = await db.conversation.findMany({ where: identity.role === 'COLLECTOR' ? { collectorId: identity.collectorId } : { recyclerId: identity.collectorId }, orderBy: { lastMessageAt: 'desc' } });
    return res.json({ success: true, data: conversations, message: 'Conversations retrieved' });
  });

  router.post('/conversations', async (req, res) => {
    const identity = requireLegacyTransactionParticipant(req);
    const parsed = z.object({ lotId: z.string().min(1), quoteId: z.string().optional(), collectorId: z.string().optional(), recyclerId: z.string().optional() }).safeParse(req.body);
    if (!parsed.success) throw new AppError('VALIDATION_ERROR', 'Conversation details are invalid', 422);
    const collectorId = identity.role === 'COLLECTOR' ? identity.collectorId : parsed.data.collectorId;
    const recyclerId = identity.role === 'RECYCLER' ? identity.collectorId : parsed.data.recyclerId;
    if (!collectorId || !recyclerId) throw new AppError('VALIDATION_ERROR', 'Both transaction participants are required', 422);
    const lot = await db.lot.findFirst({ where: { id: parsed.data.lotId, ...(identity.role === 'COLLECTOR' ? { collectorId } : {}) } });
    if (!lot) throw new AppError('NOT_FOUND', 'Lot not found', 404);
    const recycler = await db.recycler.findFirst({ where: { id: recyclerId, authorizationStatus: 'VERIFIED' }, select: { id: true } });
    if (!recycler) throw new AppError('CONFLICT', 'A verified recycler is required for this conversation', 409);
    const relatedQuote = await db.quote.findFirst({ where: { lotId: lot.id, recyclerId, ...(parsed.data.quoteId ? { id: parsed.data.quoteId } : {}), lot: { collectorId } }, select: { id: true } });
    if (!relatedQuote) throw new AppError('CONFLICT', 'A quote for this lot is required before messaging', 409);
    const conversation = await db.conversation.upsert({ where: { lotId_collectorId_recyclerId: { lotId: parsed.data.lotId, collectorId, recyclerId } }, update: { quoteId: parsed.data.quoteId ?? undefined, status: 'OPEN' }, create: { lotId: parsed.data.lotId, quoteId: parsed.data.quoteId, collectorId, recyclerId } });
    return res.status(201).json({ success: true, data: conversation, message: 'Conversation ready' });
  });

  router.get('/conversations/:conversationId/messages', async (req, res) => {
    const identity = requireLegacyTransactionParticipant(req);
    await assertConversationParticipant(db, req.params.conversationId, identity.collectorId);
    const limit = pageNumber(req.query.limit, 50, 100);
    const messages = await db.chatMessage.findMany({ where: { conversationId: req.params.conversationId }, orderBy: { createdAt: 'desc' }, take: limit });
    await db.chatMessage.updateMany({ where: { conversationId: req.params.conversationId, senderId: { not: identity.collectorId }, readAt: null }, data: { status: MessageStatus.READ, readAt: new Date() } });
    return res.json({ success: true, data: messages.reverse(), message: 'Messages retrieved' });
  });

  router.post('/conversations/:conversationId/messages', async (req, res) => {
    const identity = requireLegacyTransactionParticipant(req);
    const conversation = await assertConversationParticipant(db, req.params.conversationId, identity.collectorId);
    if (conversation.status !== 'OPEN') throw new AppError('CONFLICT', 'This conversation is closed', 409);
    const parsed = z.object({ clientMessageId: z.string().min(8).max(120), body: z.string().trim().min(1).max(1000) }).safeParse(req.body);
    if (!parsed.success) throw new AppError('VALIDATION_ERROR', 'Message must be between 1 and 1000 characters', 422);
    const existing = await db.chatMessage.findFirst({ where: { conversationId: conversation.id, clientMessageId: parsed.data.clientMessageId } });
    if (existing) return res.json({ success: true, data: existing, message: 'Message already sent' });
    const message = await db.chatMessage.create({ data: { conversationId: conversation.id, senderId: identity.collectorId, senderRole: identity.role as AccountRoleType, clientMessageId: parsed.data.clientMessageId, body: parsed.data.body, status: MessageStatus.SENT } });
    await db.conversation.update({ where: { id: conversation.id }, data: { lastMessageAt: message.createdAt } });
    return res.status(201).json({ success: true, data: message, message: 'Message sent' });
  });

  router.post('/conversations/:conversationId/draft-reply', async (req, res) => {
    const identity = requireLegacyTransactionParticipant(req);
    const conversation = await assertConversationParticipant(db, req.params.conversationId, identity.collectorId);
    const parsed = z.object({ language: z.string().max(24).optional(), instruction: z.string().trim().max(240).optional() }).safeParse(req.body);
    if (!parsed.success) throw new AppError('VALIDATION_ERROR', 'Draft instructions are invalid', 422);
    const messages = await db.chatMessage.findMany({ where: { conversationId: conversation.id }, orderBy: { createdAt: 'desc' }, take: 20 });
    const transcript = messages.reverse().map(message => `${message.senderRole}: ${message.body.slice(0, 600)}`).join('\n');
    const fallback = { text: 'Please confirm the material, weight, pickup time, and payment details before we proceed.', source: 'TEMPLATE', model: null };
    const result = await callGemini([{ text: [
      'Draft one concise reply for a private recycling transaction chat.',
      `The current user is a ${identity.role.toLowerCase()}. Write in ${parsed.data.language || 'English'}.`,
      'Return only the reply text, under 320 characters. Do not impersonate the other participant, make promises, change prices, request sensitive credentials, or send the message automatically.',
      parsed.data.instruction ? `User intent: ${parsed.data.instruction}` : 'Keep the reply focused on clarifying the next safe transaction step.',
      `Conversation:\n${transcript || '(no messages yet)'}`
    ].join('\n') }], 160);
    const data = result?.text?.trim() ? { text: result.text.trim().slice(0, 320), source: 'AI', model: result.model } : fallback;
    return res.json({ success: true, data, message: result ? 'Reply draft generated' : 'Offline reply draft generated' });
  });

  router.post('/lots/:lotId/repeat', async (req, res) => {
    const collectorId = requireRole(req, 'COLLECTOR');
    const original = await db.lot.findFirst({ where: { id: req.params.lotId, collectorId } });
    if (!original) throw new AppError('NOT_FOUND', 'Lot not found', 404);
    if (!['PAID', 'HANDED_OVER'].includes(original.status)) throw new AppError('CONFLICT', 'Only completed lots can be repeated', 409);
    const operationId = req.header('idempotency-key');
    const operationHash = operationId ? createHash('sha256').update(JSON.stringify({ action: 'REPEAT_LOT', lotId: original.id })).digest('hex') : undefined;
    if (operationId) {
      const previous = await db.idempotencyRecord.findUnique({ where: { actorId_operationId: { actorId: collectorId, operationId } } });
      if (previous) {
        if (previous.action !== 'REPEAT_LOT' || (previous.requestHash && previous.requestHash !== operationHash)) throw new AppError('CONFLICT', 'Idempotency key was already used for a different operation', 409, { code: 'IDEMPOTENCY_KEY_REUSED' });
        return res.status(201).json({ success: true, data: previous.response, message: 'Repeat lot already created' });
      }
    }
    const newLot = await db.lot.create({ data: { id: randomUUID(), collectorId, materialCategory: original.materialCategory, materialSubcategory: original.materialSubcategory, sourceType: original.sourceType, wasteRegime: original.wasteRegime, condition: original.condition, weight: original.weight, weightUnit: original.weightUnit, originalWeight: original.originalWeight, originalWeightUnit: original.originalWeightUnit, photoPath: null, photoUrl: null, imageProvenance: null, imageQualityStatus: 'UNVERIFIED', collectionLatitude: original.collectionLatitude, collectionLongitude: original.collectionLongitude, collectionAreaName: original.collectionAreaName, collectionLocationPrecision: original.collectionLocationPrecision, notes: original.notes, status: 'CREATED', version: 1 } });
    if (operationId) await db.idempotencyRecord.create({ data: { actorId: collectorId, operationId, action: 'REPEAT_LOT', entityId: newLot.id, requestHash: operationHash, response: newLot } });
    return res.status(201).json({ success: true, data: newLot, message: 'New lot created from history' });
  });

  router.get('/disputes/analytics', async (req, res) => {
    const identity = actor(req);
    const where = identity.role === 'COLLECTOR' ? { collectorId: identity.collectorId } : { recyclerId: identity.collectorId };
    const months = pageNumber(req.query.months, 6, 24);
    const since = new Date(); since.setMonth(since.getMonth() - months + 1); since.setDate(1); since.setHours(0, 0, 0, 0);
    const disputes = await db.dispute.findMany({ where: { ...where, createdAt: { gte: since } }, select: { type: true, status: true, resolution: true, createdAt: true, resolvedAt: true } });
    const byType: Record<string, number> = {}; const byStatus: Record<string, number> = {}; const byMonth: Record<string, number> = {}; const resolutions: Record<string, number> = {}; let resolutionMs = 0; let resolvedCount = 0;
    for (const dispute of disputes) { byType[dispute.type] = (byType[dispute.type] ?? 0) + 1; byStatus[dispute.status] = (byStatus[dispute.status] ?? 0) + 1; const key = indiaMonthKey(dispute.createdAt); byMonth[key] = (byMonth[key] ?? 0) + 1; if (dispute.resolution) resolutions[dispute.resolution] = (resolutions[dispute.resolution] ?? 0) + 1; if (dispute.resolvedAt) { resolutionMs += dispute.resolvedAt.getTime() - dispute.createdAt.getTime(); resolvedCount++; } }
    return res.json({ success: true, data: { months, total: disputes.length, byType, byStatus, byMonth, resolutions, averageResolutionHours: resolvedCount ? Math.round(resolutionMs / resolvedCount / 360000) / 10 : null, insufficientData: disputes.length < 3 }, message: 'Dispute analytics retrieved' });
  });

  return router;
};
