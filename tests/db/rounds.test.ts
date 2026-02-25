import { describe, it, expect, afterAll } from 'vitest';
import { getPool, closePool } from '../../src/db/pool.js';
import * as profilesDb from '../../src/db/profiles.js';
import * as roundsDb from '../../src/db/rounds.js';

describe('rounds db', () => {
  const pool = getPool();

  afterAll(async () => {
    await closePool();
  });

  async function createTestProfile() {
    return profilesDb.createProfile(pool, { name: 'Rounds Test', emoji: '⏱' });
  }

  describe('createRound', () => {
    it('creates a round with defaults', async () => {
      const profile = await createTestProfile();
      const round = await roundsDb.createRound(pool, profile.id, {
        name: 'Work',
        duration_seconds: 180,
        position: 0,
      });
      expect(round.profile_id).toBe(profile.id);
      expect(round.name).toBe('Work');
      expect(round.duration_seconds).toBe(180);
      expect(round.warn10sec).toBe(false);
      expect(round.position).toBe(0);
      await profilesDb.deleteProfile(pool, profile.id);
    });

    it('creates a round with warn10sec true', async () => {
      const profile = await createTestProfile();
      const round = await roundsDb.createRound(pool, profile.id, {
        name: 'Rest',
        duration_seconds: 60,
        warn10sec: true,
        position: 0,
      });
      expect(round.warn10sec).toBe(true);
      await profilesDb.deleteProfile(pool, profile.id);
    });
  });

  describe('getRoundsByProfileId', () => {
    it('returns rounds ordered by position', async () => {
      const profile = await createTestProfile();
      await roundsDb.createRound(pool, profile.id, {
        name: 'Second',
        duration_seconds: 30,
        position: 1,
      });
      await roundsDb.createRound(pool, profile.id, {
        name: 'First',
        duration_seconds: 60,
        position: 0,
      });
      const rounds = await roundsDb.getRoundsByProfileId(pool, profile.id);
      expect(rounds).toHaveLength(2);
      expect(rounds[0].name).toBe('First');
      expect(rounds[1].name).toBe('Second');
      await profilesDb.deleteProfile(pool, profile.id);
    });
  });

  describe('getRoundById', () => {
    it('returns null for unknown id', async () => {
      const round = await roundsDb.getRoundById(
        pool,
        '00000000-0000-0000-0000-000000000000'
      );
      expect(round).toBeNull();
    });
  });

  describe('updateRound', () => {
    it('updates fields', async () => {
      const profile = await createTestProfile();
      const round = await roundsDb.createRound(pool, profile.id, {
        name: 'Old',
        duration_seconds: 45,
        position: 0,
      });
      const updated = await roundsDb.updateRound(pool, round.id, {
        name: 'New',
        duration_seconds: 90,
        warn10sec: true,
      });
      expect(updated).not.toBeNull();
      expect(updated!.name).toBe('New');
      expect(updated!.duration_seconds).toBe(90);
      expect(updated!.warn10sec).toBe(true);
      await profilesDb.deleteProfile(pool, profile.id);
    });
  });

  describe('deleteRound', () => {
    it('returns true and removes round', async () => {
      const profile = await createTestProfile();
      const round = await roundsDb.createRound(pool, profile.id, {
        name: 'Del',
        duration_seconds: 10,
        position: 0,
      });
      const deleted = await roundsDb.deleteRound(pool, round.id);
      expect(deleted).toBe(true);
      const found = await roundsDb.getRoundById(pool, round.id);
      expect(found).toBeNull();
      await profilesDb.deleteProfile(pool, profile.id);
    });
  });
});
