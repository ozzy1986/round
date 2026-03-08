import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { FastifyInstance } from 'fastify';
import { buildApp } from '../../src/server.js';
import { closeTestApp } from '../helpers/app.js';

describe('rate limit key generator', () => {
  let app: FastifyInstance;
  const savedRateLimitMax = process.env.RATE_LIMIT_MAX;
  const savedRateLimitWindow = process.env.RATE_LIMIT_WINDOW;

  beforeEach(async () => {
    process.env.RATE_LIMIT_MAX = '1';
    process.env.RATE_LIMIT_WINDOW = '1 minute';
    app = await buildApp();
  });

  afterEach(async () => {
    process.env.RATE_LIMIT_MAX = savedRateLimitMax;
    process.env.RATE_LIMIT_WINDOW = savedRateLimitWindow;
    await closeTestApp(app);
  });

  it('falls back to IP when bearer tokens are invalid', async () => {
    const first = await app.inject({
      method: 'GET',
      url: '/',
      headers: { authorization: 'Bearer invalid.token.one' },
    });
    const second = await app.inject({
      method: 'GET',
      url: '/',
      headers: { authorization: 'Bearer invalid.token.two' },
    });

    expect(first.statusCode).toBe(200);
    expect(second.statusCode).toBe(429);
  });

  it('uses verified user ids for signed bearer tokens', async () => {
    const userOneToken = app.jwt.sign({ sub: 'user-one' });
    const userTwoToken = app.jwt.sign({ sub: 'user-two' });

    const first = await app.inject({
      method: 'GET',
      url: '/',
      headers: { authorization: `Bearer ${userOneToken}` },
    });
    const second = await app.inject({
      method: 'GET',
      url: '/',
      headers: { authorization: `Bearer ${userTwoToken}` },
    });

    expect(first.statusCode).toBe(200);
    expect(second.statusCode).toBe(200);
  });
});
