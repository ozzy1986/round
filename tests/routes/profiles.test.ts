import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { buildApp } from '../../src/server.js';
import type { FastifyInstance } from 'fastify';
import { getPool } from '../../src/db/pool.js';
import * as profilesDb from '../../src/db/profiles.js';
import { closeTestApp } from '../helpers/app.js';

describe('profiles routes', () => {
  let app: FastifyInstance;
  let authHeaders: { authorization: string };
  let testUserId: string;
  const pool = getPool();

  beforeAll(async () => {
    app = await buildApp();
    const registerRes = await app.inject({ method: 'POST', url: '/auth/register' });
    expect(registerRes.statusCode).toBe(201);
    const { token, user_id } = registerRes.json() as { token: string; user_id: string };
    authHeaders = { authorization: `Bearer ${token}` };
    testUserId = user_id;
  });

  afterAll(async () => {
    await closeTestApp(app);
  });

  describe('GET /profiles', () => {
    it('returns 200 and paginated response', async () => {
      const res = await app.inject({ method: 'GET', url: '/profiles', headers: authHeaders });
      expect(res.statusCode).toBe(200);
      const body = res.json() as { data: unknown[]; next_cursor: string | null };
      expect(Array.isArray(body.data)).toBe(true);
      expect(body).toHaveProperty('next_cursor');
      expect(body.next_cursor).toBeNull();
    });
  });

  describe('GET /profiles/:id', () => {
    it('returns 404 for unknown id', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/profiles/00000000-0000-0000-0000-000000000000',
        headers: authHeaders,
      });
      expect(res.statusCode).toBe(404);
      expect(res.json()).toMatchObject({ message: 'Profile not found' });
    });

    it('returns 200 and profile with rounds', async () => {
      const created = await profilesDb.createProfile(pool, {
        name: 'Route Get',
        emoji: '📡',
        user_id: testUserId,
      });
      const res = await app.inject({
        method: 'GET',
        url: `/profiles/${created.id}`,
        headers: authHeaders,
      });
      expect(res.statusCode).toBe(200);
      const body = res.json();
      expect(body.id).toBe(created.id);
      expect(body.name).toBe('Route Get');
      expect(body.emoji).toBe('📡');
      expect(Array.isArray(body.rounds)).toBe(true);
      await profilesDb.deleteProfile(pool, created.id, testUserId);
    });
  });

  describe('POST /profiles', () => {
    it('returns 201 and created profile', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/profiles',
        headers: authHeaders,
        payload: { name: 'New Profile', emoji: '✨' },
      });
      expect(res.statusCode).toBe(201);
      const body = res.json();
      expect(body.name).toBe('New Profile');
      expect(body.emoji).toBe('✨');
      expect(body.id).toBeDefined();
      await profilesDb.deleteProfile(pool, body.id, testUserId);
    });

    it('returns 400 for invalid body', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/profiles',
        headers: authHeaders,
        payload: { name: '' },
      });
      expect(res.statusCode).toBe(400);
    });
  });

  describe('PATCH /profiles/:id', () => {
    it('returns 404 for unknown id', async () => {
      const res = await app.inject({
        method: 'PATCH',
        url: '/profiles/00000000-0000-0000-0000-000000000000',
        headers: authHeaders,
        payload: { name: 'X' },
      });
      expect(res.statusCode).toBe(404);
    });

    it('returns 200 and updated profile', async () => {
      const created = await profilesDb.createProfile(pool, {
        name: 'Patch Me',
        emoji: '🔧',
        user_id: testUserId,
      });
      const res = await app.inject({
        method: 'PATCH',
        url: `/profiles/${created.id}`,
        headers: authHeaders,
        payload: { name: 'Patched', emoji: '✅' },
      });
      expect(res.statusCode).toBe(200);
      const body = res.json();
      expect(body.name).toBe('Patched');
      expect(body.emoji).toBe('✅');
      await profilesDb.deleteProfile(pool, created.id, testUserId);
    });
  });

  describe('DELETE /profiles/:id', () => {
    it('returns 404 for unknown id', async () => {
      const res = await app.inject({
        method: 'DELETE',
        url: '/profiles/00000000-0000-0000-0000-000000000000',
        headers: authHeaders,
      });
      expect(res.statusCode).toBe(404);
    });

    it('returns 204 and deletes profile', async () => {
      const created = await profilesDb.createProfile(pool, {
        name: 'Delete Me',
        emoji: '🗑',
        user_id: testUserId,
      });
      const res = await app.inject({
        method: 'DELETE',
        url: `/profiles/${created.id}`,
        headers: authHeaders,
      });
      expect(res.statusCode).toBe(204);
      const found = await profilesDb.getProfileById(pool, created.id, testUserId);
      expect(found).toBeNull();
    });
  });
});
