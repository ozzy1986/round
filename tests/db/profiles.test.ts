import { describe, it, expect, afterAll } from 'vitest';
import { getPool, closePool } from '../../src/db/pool.js';
import * as profilesDb from '../../src/db/profiles.js';
import * as roundsDb from '../../src/db/rounds.js';

describe('profiles db', () => {
  const pool = getPool();

  afterAll(async () => {
    await closePool();
  });

  describe('createProfile', () => {
    it('creates a profile and returns it', async () => {
      const created = await profilesDb.createProfile(pool, {
        name: 'Test Profile',
        emoji: '🥊',
      });
      expect(created.id).toBeDefined();
      expect(created.name).toBe('Test Profile');
      expect(created.emoji).toBe('🥊');
      expect(created.user_id).toBeNull();
      expect(created.created_at).toBeInstanceOf(Date);
      expect(created.updated_at).toBeInstanceOf(Date);
      await profilesDb.deleteProfile(pool, created.id);
    });
  });

  describe('listProfiles', () => {
    it('returns empty array when no profiles', async () => {
      const list = await profilesDb.listProfiles(pool);
      expect(Array.isArray(list)).toBe(true);
    });

    it('returns profiles ordered by updated_at desc', async () => {
      const p1 = await profilesDb.createProfile(pool, { name: 'First', emoji: '1' });
      const p2 = await profilesDb.createProfile(pool, { name: 'Second', emoji: '2' });
      const list = await profilesDb.listProfiles(pool);
      const ids = list.map((p) => p.id);
      expect(ids).toContain(p1.id);
      expect(ids).toContain(p2.id);
      const secondIndex = list.findIndex((p) => p.id === p2.id);
      const firstIndex = list.findIndex((p) => p.id === p1.id);
      expect(secondIndex).toBeLessThan(firstIndex);
      await profilesDb.deleteProfile(pool, p1.id);
      await profilesDb.deleteProfile(pool, p2.id);
    });
  });

  describe('getProfileById', () => {
    it('returns null for unknown id', async () => {
      const profile = await profilesDb.getProfileById(
        pool,
        '00000000-0000-0000-0000-000000000000'
      );
      expect(profile).toBeNull();
    });

    it('returns profile when exists', async () => {
      const created = await profilesDb.createProfile(pool, {
        name: 'Get Me',
        emoji: '🔍',
      });
      const found = await profilesDb.getProfileById(pool, created.id);
      expect(found).not.toBeNull();
      expect(found!.id).toBe(created.id);
      expect(found!.name).toBe('Get Me');
      await profilesDb.deleteProfile(pool, created.id);
    });
  });

  describe('getProfileWithRoundsById', () => {
    it('returns profile with empty rounds when no rounds', async () => {
      const created = await profilesDb.createProfile(pool, {
        name: 'No Rounds',
        emoji: '📋',
      });
      const withRounds = await profilesDb.getProfileWithRoundsById(pool, created.id);
      expect(withRounds).not.toBeNull();
      expect(withRounds!.rounds).toEqual([]);
      await profilesDb.deleteProfile(pool, created.id);
    });

    it('returns profile with rounds in order', async () => {
      const created = await profilesDb.createProfile(pool, {
        name: 'With Rounds',
        emoji: '⏱',
      });
      await roundsDb.createRound(pool, created.id, {
        name: 'R1',
        duration_seconds: 60,
        position: 0,
      });
      await roundsDb.createRound(pool, created.id, {
        name: 'R2',
        duration_seconds: 30,
        warn10sec: true,
        position: 1,
      });
      const withRounds = await profilesDb.getProfileWithRoundsById(pool, created.id);
      expect(withRounds).not.toBeNull();
      expect(withRounds!.rounds).toHaveLength(2);
      expect(withRounds!.rounds[0].name).toBe('R1');
      expect(withRounds!.rounds[1].name).toBe('R2');
      expect(withRounds!.rounds[1].warn10sec).toBe(true);
      await profilesDb.deleteProfile(pool, created.id);
    });
  });

  describe('updateProfile', () => {
    it('updates name and emoji', async () => {
      const created = await profilesDb.createProfile(pool, {
        name: 'Original',
        emoji: 'A',
      });
      const updated = await profilesDb.updateProfile(pool, created.id, {
        name: 'Updated',
        emoji: 'B',
      });
      expect(updated).not.toBeNull();
      expect(updated!.name).toBe('Updated');
      expect(updated!.emoji).toBe('B');
      await profilesDb.deleteProfile(pool, created.id);
    });

    it('returns null for unknown id', async () => {
      const updated = await profilesDb.updateProfile(
        pool,
        '00000000-0000-0000-0000-000000000000',
        { name: 'X' }
      );
      expect(updated).toBeNull();
    });
  });

  describe('deleteProfile', () => {
    it('returns true and removes profile', async () => {
      const created = await profilesDb.createProfile(pool, {
        name: 'To Delete',
        emoji: '🗑',
      });
      const deleted = await profilesDb.deleteProfile(pool, created.id);
      expect(deleted).toBe(true);
      const found = await profilesDb.getProfileById(pool, created.id);
      expect(found).toBeNull();
    });

    it('returns false for unknown id', async () => {
      const deleted = await profilesDb.deleteProfile(
        pool,
        '00000000-0000-0000-0000-000000000000'
      );
      expect(deleted).toBe(false);
    });
  });
});
