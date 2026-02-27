import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { buildApp } from '../../src/server.js';
import type { FastifyInstance } from 'fastify';
import { closePool } from '../../src/db/pool.js';

describe('POST /auth/telegram', () => {
  let app: FastifyInstance;
  const savedBotSecret = process.env.BOT_SECRET;

  beforeAll(async () => {
    process.env.BOT_SECRET = 'test-bot-secret-for-telegram-auth';
    app = await buildApp();
  });

  afterAll(async () => {
    process.env.BOT_SECRET = savedBotSecret;
    await app.close();
    await closePool();
  });

  it('returns 401 when X-Bot-Secret is missing', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/auth/telegram',
      headers: { 'Content-Type': 'application/json' },
      payload: { telegram_id: 12345 },
    });
    expect(res.statusCode).toBe(401);
  });

  it('returns 401 when X-Bot-Secret is wrong (timing-safe)', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/auth/telegram',
      headers: {
        'Content-Type': 'application/json',
        'X-Bot-Secret': 'wrong-secret',
      },
      payload: { telegram_id: 12345 },
    });
    expect(res.statusCode).toBe(401);
  });
});

describe('POST /auth/register', () => {
  let app: FastifyInstance;

  beforeAll(async () => {
    app = await buildApp();
  });

  afterAll(async () => {
    await app.close();
    await closePool();
  });

  it('returns 201 and token + user_id', async () => {
    const res = await app.inject({ method: 'POST', url: '/auth/register' });
    expect(res.statusCode).toBe(201);
    const body = res.json() as { token: string; user_id: string };
    expect(body.token).toBeDefined();
    expect(body.user_id).toBeDefined();
    expect(body.token.length).toBeGreaterThan(10);
    expect(body.user_id).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
    );
  });

  it('returns different user_id on each call', async () => {
    const res1 = await app.inject({ method: 'POST', url: '/auth/register' });
    const res2 = await app.inject({ method: 'POST', url: '/auth/register' });
    expect(res1.statusCode).toBe(201);
    expect(res2.statusCode).toBe(201);
    const body1 = res1.json() as { user_id: string };
    const body2 = res2.json() as { user_id: string };
    expect(body1.user_id).not.toBe(body2.user_id);
  });
});
