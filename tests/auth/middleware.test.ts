import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { buildApp } from '../../src/server.js';
import type { FastifyInstance } from 'fastify';
import { closePool } from '../../src/db/pool.js';

describe('auth middleware', () => {
  let app: FastifyInstance;
  let token: string;

  beforeAll(async () => {
    app = await buildApp();
    const registerRes = await app.inject({ method: 'POST', url: '/auth/register' });
    expect(registerRes.statusCode).toBe(201);
    token = (registerRes.json() as { token: string }).token;
  });

  afterAll(async () => {
    await app.close();
    await closePool();
  });

  it('GET /profiles without Authorization returns 401', async () => {
    const res = await app.inject({ method: 'GET', url: '/profiles' });
    expect(res.statusCode).toBe(401);
    expect(res.json()).toMatchObject({ message: 'Unauthorized' });
  });

  it('GET /profiles with invalid token returns 401', async () => {
    const res = await app.inject({
      method: 'GET',
      url: '/profiles',
      headers: { authorization: 'Bearer invalid-token' },
    });
    expect(res.statusCode).toBe(401);
  });

  it('GET /profiles with valid token returns 200', async () => {
    const res = await app.inject({
      method: 'GET',
      url: '/profiles',
      headers: { authorization: `Bearer ${token}` },
    });
    expect(res.statusCode).toBe(200);
    const body = res.json() as { data: unknown[]; next_cursor: string | null };
    expect(Array.isArray(body.data)).toBe(true);
  });

  it('/health does not require auth', async () => {
    const res = await app.inject({ method: 'GET', url: '/health' });
    expect(res.statusCode).toBe(200);
  });
});
