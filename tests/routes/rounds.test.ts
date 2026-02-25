import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { buildApp } from '../../src/server.js';
import type { FastifyInstance } from 'fastify';
import { getPool, closePool } from '../../src/db/pool.js';
import * as profilesDb from '../../src/db/profiles.js';
import * as roundsDb from '../../src/db/rounds.js';

describe('rounds routes', () => {
  let app: FastifyInstance;
  const pool = getPool();

  beforeAll(async () => {
    app = await buildApp();
  });

  afterAll(async () => {
    await app.close();
    await closePool();
  });

  describe('GET /profiles/:id/rounds', () => {
    it('returns 404 for unknown profile', async () => {
      const res = await app.inject({
        method: 'GET',
        url: '/profiles/00000000-0000-0000-0000-000000000000/rounds',
      });
      expect(res.statusCode).toBe(404);
    });

    it('returns 200 and rounds array', async () => {
      const profile = await profilesDb.createProfile(pool, {
        name: 'Rounds Route',
        emoji: '📋',
      });
      const res = await app.inject({
        method: 'GET',
        url: `/profiles/${profile.id}/rounds`,
      });
      expect(res.statusCode).toBe(200);
      expect(res.json()).toEqual([]);
      await profilesDb.deleteProfile(pool, profile.id);
    });
  });

  describe('POST /profiles/:id/rounds', () => {
    it('returns 404 for unknown profile', async () => {
      const res = await app.inject({
        method: 'POST',
        url: '/profiles/00000000-0000-0000-0000-000000000000/rounds',
        payload: {
          name: 'R1',
          duration_seconds: 60,
          position: 0,
        },
      });
      expect(res.statusCode).toBe(404);
    });

    it('returns 201 and created round', async () => {
      const profile = await profilesDb.createProfile(pool, {
        name: 'Add Round',
        emoji: '➕',
      });
      const res = await app.inject({
        method: 'POST',
        url: `/profiles/${profile.id}/rounds`,
        payload: {
          name: 'Work',
          duration_seconds: 180,
          warn10sec: true,
          position: 0,
        },
      });
      expect(res.statusCode).toBe(201);
      const body = res.json();
      expect(body.profile_id).toBe(profile.id);
      expect(body.name).toBe('Work');
      expect(body.duration_seconds).toBe(180);
      expect(body.warn10sec).toBe(true);
      expect(body.position).toBe(0);
      await profilesDb.deleteProfile(pool, profile.id);
    });
  });

  describe('PATCH /rounds/:roundId', () => {
    it('returns 404 for unknown round', async () => {
      const res = await app.inject({
        method: 'PATCH',
        url: '/rounds/00000000-0000-0000-0000-000000000000',
        payload: { name: 'X' },
      });
      expect(res.statusCode).toBe(404);
    });

    it('returns 200 and updated round', async () => {
      const profile = await profilesDb.createProfile(pool, {
        name: 'Patch Round',
        emoji: '🔧',
      });
      const round = await roundsDb.createRound(pool, profile.id, {
        name: 'Old',
        duration_seconds: 60,
        position: 0,
      });
      const res = await app.inject({
        method: 'PATCH',
        url: `/rounds/${round.id}`,
        payload: { name: 'New', duration_seconds: 120 },
      });
      expect(res.statusCode).toBe(200);
      const body = res.json();
      expect(body.name).toBe('New');
      expect(body.duration_seconds).toBe(120);
      await profilesDb.deleteProfile(pool, profile.id);
    });
  });

  describe('DELETE /rounds/:roundId', () => {
    it('returns 404 for unknown round', async () => {
      const res = await app.inject({
        method: 'DELETE',
        url: '/rounds/00000000-0000-0000-0000-000000000000',
      });
      expect(res.statusCode).toBe(404);
    });

    it('returns 204 and deletes round', async () => {
      const profile = await profilesDb.createProfile(pool, {
        name: 'Del Round',
        emoji: '🗑',
      });
      const round = await roundsDb.createRound(pool, profile.id, {
        name: 'R',
        duration_seconds: 10,
        position: 0,
      });
      const res = await app.inject({
        method: 'DELETE',
        url: `/rounds/${round.id}`,
      });
      expect(res.statusCode).toBe(204);
      const found = await roundsDb.getRoundById(pool, round.id);
      expect(found).toBeNull();
      await profilesDb.deleteProfile(pool, profile.id);
    });
  });
});
