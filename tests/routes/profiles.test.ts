import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { buildApp } from '../../src/server.js';
import type { FastifyInstance } from 'fastify';
import { getPool, closePool } from '../../src/db/pool.js';
import * as profilesDb from '../../src/db/profiles.js';

describe('profiles routes', () => {
  let app: FastifyInstance;
  const pool = getPool();

  beforeAll(async () => {
    app = await buildApp();
  });

  afterAll(async () => {
    await app.close();
    await closePool();
  });

  describe('GET /profiles', () => {
    it('returns 200 and array', async () => {
      const res = await app.inject({ method: 'GET', url: '/profiles' });
      expect(res.statusCode).toBe(200);
      const body = res.json();
      expect(Array.isArray(body)).toBe(true);
    });
  });

  describe('GET /profiles/:id', () => {
    it('returns 404 for unknown id', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/profiles/00000000-0000-0000-0000-000000000000',
      });
      expect(res.statusCode).toBe(404);
      expect(res.json()).toMatchObject({ message: 'Profile not found' });
    });

    it('returns 200 and profile with rounds', async () => {
      const created = await profilesDb.createProfile(pool, {
        name: 'Route Get',
        emoji: '📡',
      });
      const res = await app.inject({
        method: 'GET',
        url: `/profiles/${created.id}`,
      });
      expect(res.statusCode).toBe(200);
      const body = res.json();
      expect(body.id).toBe(created.id);
      expect(body.name).toBe('Route Get');
      expect(body.emoji).toBe('📡');
      expect(Array.isArray(body.rounds)).toBe(true);
      await profilesDb.deleteProfile(pool, created.id);
    });
  });

  describe('POST /profiles', () => {
    it('returns 201 and created profile', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/profiles',
        payload: { name: 'New Profile', emoji: '✨' },
      });
      expect(res.statusCode).toBe(201);
      const body = res.json();
      expect(body.name).toBe('New Profile');
      expect(body.emoji).toBe('✨');
      expect(body.id).toBeDefined();
      await profilesDb.deleteProfile(pool, body.id);
    });

    it('returns 400 for invalid body', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/profiles',
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
        payload: { name: 'X' },
      });
      expect(res.statusCode).toBe(404);
    });

    it('returns 200 and updated profile', async () => {
      const created = await profilesDb.createProfile(pool, {
        name: 'Patch Me',
        emoji: '🔧',
      });
      const res = await app.inject({
        method: 'PATCH',
        url: `/profiles/${created.id}`,
        payload: { name: 'Patched', emoji: '✅' },
      });
      expect(res.statusCode).toBe(200);
      const body = res.json();
      expect(body.name).toBe('Patched');
      expect(body.emoji).toBe('✅');
      await profilesDb.deleteProfile(pool, created.id);
    });
  });

  describe('DELETE /profiles/:id', () => {
    it('returns 404 for unknown id', async () => {
      const res = await app.inject({
        method: 'DELETE',
        url: '/profiles/00000000-0000-0000-0000-000000000000',
      });
      expect(res.statusCode).toBe(404);
    });

    it('returns 204 and deletes profile', async () => {
      const created = await profilesDb.createProfile(pool, {
        name: 'Delete Me',
        emoji: '🗑',
      });
      const res = await app.inject({
        method: 'DELETE',
        url: `/profiles/${created.id}`,
      });
      expect(res.statusCode).toBe(204);
      const found = await profilesDb.getProfileById(pool, created.id);
      expect(found).toBeNull();
    });
  });
});
