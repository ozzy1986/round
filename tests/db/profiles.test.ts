import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { getPool, closePool } from '../../src/db/pool.js';
import * as profilesDb from '../../src/db/profiles.js';
import type { Profile } from '../../src/db/types.js';
import * as roundsDb from '../../src/db/rounds.js';
import { createAnonymousUser } from '../../src/db/users.js';

describe('profiles db', () => {
  const pool = getPool();
  let testUserId: string;

  beforeAll(async () => {
    const user = await createAnonymousUser(pool);
    testUserId = user.id;
  });

  afterAll(async () => {
    await closePool();
  });

  describe('createProfile', () => {
    it('creates a profile and returns it', async () => {
      const created = await profilesDb.createProfile(pool, {
        name: 'Test Profile',
        emoji: '🥊',
        user_id: testUserId,
      });
      expect(created.id).toBeDefined();
      expect(created.name).toBe('Test Profile');
      expect(created.emoji).toBe('🥊');
      expect(created.user_id).toBe(testUserId);
      expect(created.created_at).toBeInstanceOf(Date);
      expect(created.updated_at).toBeInstanceOf(Date);
      await profilesDb.deleteProfile(pool, created.id, testUserId);
    });
  });

  describe('listProfiles', () => {
    it('returns empty array when no profiles', async () => {
      const { profiles: list } = await profilesDb.listProfiles(pool, testUserId);
      expect(Array.isArray(list)).toBe(true);
    });

    it('returns profiles ordered by updated_at desc', async () => {
      const p1 = await profilesDb.createProfile(pool, { name: 'First', emoji: '1', user_id: testUserId });
      const p2 = await profilesDb.createProfile(pool, { name: 'Second', emoji: '2', user_id: testUserId });
      const { profiles: list } = await profilesDb.listProfiles(pool, testUserId);
      const ids = list.map((p: Profile) => p.id);
      expect(ids).toContain(p1.id);
      expect(ids).toContain(p2.id);
      const secondIndex = list.findIndex((p: Profile) => p.id === p2.id);
      const firstIndex = list.findIndex((p: Profile) => p.id === p1.id);
      expect(secondIndex).toBeLessThan(firstIndex);
      await profilesDb.deleteProfile(pool, p1.id, testUserId);
      await profilesDb.deleteProfile(pool, p2.id, testUserId);
    });
  });

  describe('getProfileById', () => {
    it('returns null for unknown id', async () => {
      const profile = await profilesDb.getProfileById(
        pool,
        '00000000-0000-0000-0000-000000000000',
        testUserId
      );
      expect(profile).toBeNull();
    });

    it('returns profile when exists', async () => {
      const created = await profilesDb.createProfile(pool, {
        name: 'Get Me',
        emoji: '🔍',
        user_id: testUserId,
      });
      const found = await profilesDb.getProfileById(pool, created.id, testUserId);
      expect(found).not.toBeNull();
      expect(found!.id).toBe(created.id);
      expect(found!.name).toBe('Get Me');
      await profilesDb.deleteProfile(pool, created.id, testUserId);
    });
  });

  describe('getProfileWithRoundsById', () => {
    it('returns profile with empty rounds when no rounds', async () => {
      const created = await profilesDb.createProfile(pool, {
        name: 'No Rounds',
        emoji: '📋',
        user_id: testUserId,
      });
      const withRounds = await profilesDb.getProfileWithRoundsById(pool, created.id, testUserId);
      expect(withRounds).not.toBeNull();
      expect(withRounds!.rounds).toEqual([]);
      await profilesDb.deleteProfile(pool, created.id, testUserId);
    });

    it('returns profile with rounds in order', async () => {
      const created = await profilesDb.createProfile(pool, {
        name: 'With Rounds',
        emoji: '⏱',
        user_id: testUserId,
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
      const withRounds = await profilesDb.getProfileWithRoundsById(pool, created.id, testUserId);
      expect(withRounds).not.toBeNull();
      expect(withRounds!.rounds).toHaveLength(2);
      expect(withRounds!.rounds[0].name).toBe('R1');
      expect(withRounds!.rounds[1].name).toBe('R2');
      expect(withRounds!.rounds[1].warn10sec).toBe(true);
      await profilesDb.deleteProfile(pool, created.id, testUserId);
    });
  });

  describe('updateProfile', () => {
    it('updates name and emoji', async () => {
      const created = await profilesDb.createProfile(pool, {
        name: 'Original',
        emoji: 'A',
        user_id: testUserId,
      });
      const updated = await profilesDb.updateProfile(pool, created.id, testUserId, {
        name: 'Updated',
        emoji: 'B',
      });
      expect(updated).not.toBeNull();
      expect(updated!.name).toBe('Updated');
      expect(updated!.emoji).toBe('B');
      await profilesDb.deleteProfile(pool, created.id, testUserId);
    });

    it('returns null for unknown id', async () => {
      const updated = await profilesDb.updateProfile(
        pool,
        '00000000-0000-0000-0000-000000000000',
        testUserId,
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
        user_id: testUserId,
      });
      const deleted = await profilesDb.deleteProfile(pool, created.id, testUserId);
      expect(deleted).toBe(true);
      const found = await profilesDb.getProfileById(pool, created.id, testUserId);
      expect(found).toBeNull();
    });

    it('returns false for unknown id', async () => {
      const deleted = await profilesDb.deleteProfile(
        pool,
        '00000000-0000-0000-0000-000000000000',
        testUserId
      );
      expect(deleted).toBe(false);
    });
  });
});
