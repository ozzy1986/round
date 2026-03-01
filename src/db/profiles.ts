import type { Pool } from 'pg';
import type {
  Profile,
  ProfileWithRounds,
  CreateProfileInput,
  UpdateProfileInput,
} from './types.js';
import { roundFromRow } from './rounds.js';

function profileFromRow(row: Record<string, unknown>): Profile {
  return {
    id: row.id as string,
    name: row.name as string,
    emoji: row.emoji as string,
    user_id: (row.user_id as string) ?? null,
    created_at: row.created_at as Date,
    updated_at: row.updated_at as Date,
  };
}

export interface ListProfilesResult {
  profiles: Profile[];
  nextCursor: string | null;
}

export interface ListProfilesWithRoundsResult {
  profiles: ProfileWithRounds[];
  nextCursor: string | null;
}

export async function listProfiles(
  pool: Pool,
  userId: string,
  limit: number = 20,
  cursor?: string | null
): Promise<ListProfilesResult> {
  const safeLimit = Math.min(Math.max(1, limit), 100);
  let result;
  if (cursor) {
    result = await pool.query(
      `SELECT id, name, emoji, user_id, created_at, updated_at
       FROM profiles
       WHERE user_id = $1 AND updated_at < $2
       ORDER BY updated_at DESC
       LIMIT $3`,
      [userId, cursor, safeLimit + 1]
    );
  } else {
    result = await pool.query(
      `SELECT id, name, emoji, user_id, created_at, updated_at
       FROM profiles
       WHERE user_id = $1
       ORDER BY updated_at DESC
       LIMIT $2`,
      [userId, safeLimit + 1]
    );
  }
  const rows = result.rows;
  const hasMore = rows.length > safeLimit;
  const profiles = (hasMore ? rows.slice(0, safeLimit) : rows).map((row) => profileFromRow(row));
  const nextCursor =
    profiles.length > 0 && hasMore
      ? (profiles[profiles.length - 1].updated_at as Date).toISOString()
      : null;
  return { profiles, nextCursor };
}

export async function listProfilesWithRounds(
  pool: Pool,
  userId: string,
  limit: number = 20,
  cursor?: string | null,
  updatedSince?: string | null
): Promise<ListProfilesWithRoundsResult> {
  const safeLimit = Math.min(Math.max(1, limit), 100);
  const params: unknown[] = [userId];
  let place = 2;
  const cursorCond = cursor ? `AND p.updated_at < $${place++}` : '';
  const sinceCond = updatedSince ? `AND p.updated_at >= $${place++}` : '';
  if (cursor) params.push(cursor);
  if (updatedSince) params.push(updatedSince);
  params.push(safeLimit + 1);
  const limitParam = place;

  const result = await pool.query(
    `SELECT p.id, p.name, p.emoji, p.user_id, p.created_at, p.updated_at,
            COALESCE(
              (SELECT json_agg(r ORDER BY r.position) FROM rounds r WHERE r.profile_id = p.id),
              '[]'::json
            ) AS rounds_json
     FROM profiles p
     WHERE p.user_id = $1 ${cursorCond} ${sinceCond}
     ORDER BY p.updated_at DESC
     LIMIT $${limitParam}`,
    params
  );
  const rows = result.rows;
  const hasMore = rows.length > safeLimit;
  const slice = hasMore ? rows.slice(0, safeLimit) : rows;
  const profiles: ProfileWithRounds[] = slice.map((row) => {
    const profile = profileFromRow(row);
    const rounds = (row.rounds_json as Array<Record<string, unknown>>).map((r) => roundFromRow(r));
    return { ...profile, rounds };
  });
  const nextCursor =
    profiles.length > 0 && hasMore
      ? (profiles[profiles.length - 1].updated_at as Date).toISOString()
      : null;
  return { profiles, nextCursor };
}

export async function getProfileById(
  pool: Pool,
  id: string,
  userId: string
): Promise<Profile | null> {
  const result = await pool.query(
    `SELECT id, name, emoji, user_id, created_at, updated_at
     FROM profiles WHERE id = $1 AND user_id = $2`,
    [id, userId]
  );
  if (result.rows.length === 0) return null;
  return profileFromRow(result.rows[0]);
}

export async function getProfileWithRoundsById(
  pool: Pool,
  id: string,
  userId: string
): Promise<ProfileWithRounds | null> {
  const result = await pool.query(
    `SELECT p.id, p.name, p.emoji, p.user_id, p.created_at, p.updated_at,
            COALESCE(
              (SELECT json_agg(r ORDER BY r.position) FROM rounds r WHERE r.profile_id = p.id),
              '[]'::json
            ) AS rounds_json
     FROM profiles p
     WHERE p.id = $1 AND p.user_id = $2`,
    [id, userId]
  );
  if (result.rows.length === 0) return null;
  const row = result.rows[0];
  const rounds = (row.rounds_json as Array<Record<string, unknown>>).map((r) => roundFromRow(r));
  return {
    ...profileFromRow(row),
    rounds,
  };
}

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export async function createProfile(
  pool: Pool,
  input: CreateProfileInput
): Promise<Profile> {
  const useId = input.id && UUID_REGEX.test(input.id) ? input.id : null;
  const result = useId
    ? await pool.query(
        `INSERT INTO profiles (id, name, emoji, user_id)
         VALUES ($1, $2, $3, $4)
         RETURNING id, name, emoji, user_id, created_at, updated_at`,
        [useId, input.name, input.emoji, input.user_id]
      )
    : await pool.query(
        `INSERT INTO profiles (name, emoji, user_id)
         VALUES ($1, $2, $3)
         RETURNING id, name, emoji, user_id, created_at, updated_at`,
        [input.name, input.emoji, input.user_id]
      );
  return profileFromRow(result.rows[0]);
}

export async function updateProfile(
  pool: Pool,
  id: string,
  userId: string,
  input: UpdateProfileInput
): Promise<Profile | null> {
  const updates: string[] = [];
  const values: unknown[] = [];
  let i = 1;
  if (input.name !== undefined) {
    updates.push(`name = $${i++}`);
    values.push(input.name);
  }
  if (input.emoji !== undefined) {
    updates.push(`emoji = $${i++}`);
    values.push(input.emoji);
  }
  if (updates.length === 0) return getProfileById(pool, id, userId);
  updates.push(`updated_at = current_timestamp`);
  values.push(id, userId);
  const result = await pool.query(
    `UPDATE profiles SET ${updates.join(', ')}
     WHERE id = $${i} AND user_id = $${i + 1}
     RETURNING id, name, emoji, user_id, created_at, updated_at`,
    values
  );
  if (result.rows.length === 0) return null;
  return profileFromRow(result.rows[0]);
}

export async function deleteProfile(
  pool: Pool,
  id: string,
  userId: string
): Promise<boolean> {
  const result = await pool.query(
    'DELETE FROM profiles WHERE id = $1 AND user_id = $2 RETURNING id',
    [id, userId]
  );
  return result.rowCount !== null && result.rowCount > 0;
}
