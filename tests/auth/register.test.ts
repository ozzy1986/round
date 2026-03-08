import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { buildApp } from '../../src/server.js';
import type { FastifyInstance } from 'fastify';
import { closeTestApp } from '../helpers/app.js';

describe('POST /auth/telegram', () => {
  let app: FastifyInstance;
  const savedBotSecret = process.env.BOT_SECRET;

  beforeAll(async () => {
    process.env.BOT_SECRET = 'test-bot-secret-for-telegram-auth';
    app = await buildApp();
  });

  afterAll(async () => {
    process.env.BOT_SECRET = savedBotSecret;
    await closeTestApp(app);
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
    await closeTestApp(app);
  });

  it('returns 201 and token + refresh_token + user_id', async () => {
    const res = await app.inject({ method: 'POST', url: '/auth/register' });
    expect(res.statusCode).toBe(201);
    const body = res.json() as { token: string; refresh_token: string; user_id: string };
    expect(body.token).toBeDefined();
    expect(body.refresh_token).toBeDefined();
    expect(body.user_id).toBeDefined();
    expect(body.token.length).toBeGreaterThan(10);
    expect(body.refresh_token.length).toBeGreaterThan(10);
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

describe('POST /auth/refresh and /auth/logout', () => {
  let app: FastifyInstance;

  beforeAll(async () => {
    app = await buildApp();
  });

  afterAll(async () => {
    await closeTestApp(app);
  });

  it('POST /auth/refresh with valid refresh_token returns 200 and new tokens', async () => {
    const reg = await app.inject({ method: 'POST', url: '/auth/register' });
    expect(reg.statusCode).toBe(201);
    const { refresh_token } = reg.json() as { refresh_token: string };
    const res = await app.inject({
      method: 'POST',
      url: '/auth/refresh',
      headers: { 'Content-Type': 'application/json' },
      payload: { refresh_token },
    });
    expect(res.statusCode).toBe(200);
    const body = res.json() as { token: string; refresh_token: string; user_id: string };
    expect(body.token).toBeDefined();
    expect(body.refresh_token).toBeDefined();
    expect(body.refresh_token).not.toBe(refresh_token);
  });

  it('POST /auth/refresh with invalid refresh_token returns 401', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/auth/refresh',
      headers: { 'Content-Type': 'application/json' },
      payload: { refresh_token: 'invalid' },
    });
    expect(res.statusCode).toBe(401);
  });

  it('POST /auth/logout revokes refresh_token', async () => {
    const reg = await app.inject({ method: 'POST', url: '/auth/register' });
    expect(reg.statusCode).toBe(201);
    const { refresh_token } = reg.json() as { refresh_token: string };
    const logoutRes = await app.inject({
      method: 'POST',
      url: '/auth/logout',
      headers: { 'Content-Type': 'application/json' },
      payload: { refresh_token },
    });
    expect(logoutRes.statusCode).toBe(204);
    const refreshRes = await app.inject({
      method: 'POST',
      url: '/auth/refresh',
      headers: { 'Content-Type': 'application/json' },
      payload: { refresh_token },
    });
    expect(refreshRes.statusCode).toBe(401);
  });
});
