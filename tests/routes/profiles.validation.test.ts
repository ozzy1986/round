import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import type { FastifyInstance } from 'fastify';
import { buildApp } from '../../src/server.js';
import { closePool } from '../../src/db/pool.js';
import { closeRedis } from '../../src/redis.js';

describe('profiles route validation', () => {
  let app: FastifyInstance;
  let authHeaders: { authorization: string };

  beforeAll(async () => {
    app = await buildApp();
    authHeaders = {
      authorization: `Bearer ${app.jwt.sign({ sub: 'validation-user' })}`,
    };
  });

  afterAll(async () => {
    await app.close();
    await closePool();
    await closeRedis();
  });

  it('rejects an invalid cursor before hitting the database', async () => {
    const res = await app.inject({
      method: 'GET',
      url: '/profiles?cursor=not-a-timestamp',
      headers: authHeaders,
    });

    expect(res.statusCode).toBe(400);
  });

  it('rejects an invalid updated_since before hitting the database', async () => {
    const res = await app.inject({
      method: 'GET',
      url: '/profiles?updated_since=still-not-a-timestamp',
      headers: authHeaders,
    });

    expect(res.statusCode).toBe(400);
  });
});
