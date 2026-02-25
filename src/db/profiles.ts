import type { Pool } from 'pg';
import type {
  Profile,
  ProfileWithRounds,
  CreateProfileInput,
  UpdateProfileInput,
} from './types.js';
import { getRoundsByProfileId } from './rounds.js';

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

export async function listProfiles(pool: Pool): Promise<Profile[]> {
  const result = await pool.query(
    `SELECT id, name, emoji, user_id, created_at, updated_at
     FROM profiles ORDER BY updated_at DESC`
  );
  return result.rows.map((row) => profileFromRow(row));
}

export async function getProfileById(
  pool: Pool,
  id: string
): Promise<Profile | null> {
  const result = await pool.query(
    `SELECT id, name, emoji, user_id, created_at, updated_at
     FROM profiles WHERE id = $1`,
    [id]
  );
  if (result.rows.length === 0) return null;
  return profileFromRow(result.rows[0]);
}

export async function getProfileWithRoundsById(
  pool: Pool,
  id: string
): Promise<ProfileWithRounds | null> {
  const profile = await getProfileById(pool, id);
  if (!profile) return null;
  const rounds = await getRoundsByProfileId(pool, id);
  return { ...profile, rounds };
}

export async function createProfile(
  pool: Pool,
  input: CreateProfileInput
): Promise<Profile> {
  const result = await pool.query(
    `INSERT INTO profiles (name, emoji)
     VALUES ($1, $2)
     RETURNING id, name, emoji, user_id, created_at, updated_at`,
    [input.name, input.emoji]
  );
  return profileFromRow(result.rows[0]);
}

export async function updateProfile(
  pool: Pool,
  id: string,
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
  if (updates.length === 0) return getProfileById(pool, id);
  updates.push(`updated_at = current_timestamp`);
  values.push(id);
  const result = await pool.query(
    `UPDATE profiles SET ${updates.join(', ')}
     WHERE id = $${i}
     RETURNING id, name, emoji, user_id, created_at, updated_at`,
    values
  );
  if (result.rows.length === 0) return null;
  return profileFromRow(result.rows[0]);
}

export async function deleteProfile(
  pool: Pool,
  id: string
): Promise<boolean> {
  const result = await pool.query(
    'DELETE FROM profiles WHERE id = $1 RETURNING id',
    [id]
  );
  return result.rowCount !== null && result.rowCount > 0;
}
