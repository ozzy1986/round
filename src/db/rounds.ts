import type { Pool } from 'pg';
import type {
  Round,
  CreateRoundInput,
  UpdateRoundInput,
} from './types.js';

export function roundFromRow(row: Record<string, unknown>): Round {
  return {
    id: row.id as string,
    profile_id: row.profile_id as string,
    name: row.name as string,
    duration_seconds: row.duration_seconds as number,
    warn10sec: row.warn10sec as boolean,
    position: row.position as number,
  };
}

export async function getRoundsByProfileId(
  pool: Pool,
  profileId: string
): Promise<Round[]> {
  const result = await pool.query(
    `SELECT id, profile_id, name, duration_seconds, warn10sec, position
     FROM rounds WHERE profile_id = $1 ORDER BY position ASC`,
    [profileId]
  );
  return result.rows.map((row) => roundFromRow(row));
}

export interface RoundsForProfileResult {
  profileFound: boolean;
  rounds: Round[];
}

export async function getRoundsForProfileOwner(
  pool: Pool,
  profileId: string,
  userId: string
): Promise<RoundsForProfileResult> {
  const result = await pool.query(
    `SELECT (SELECT 1 FROM profiles WHERE id = $1 AND user_id = $2) AS profile_found,
            (SELECT json_agg(r ORDER BY r.position) FROM (
              SELECT id, profile_id, name, duration_seconds, warn10sec, position
              FROM rounds WHERE profile_id = $1
            ) r) AS rounds_json`,
    [profileId, userId]
  );
  const row = result.rows[0];
  const profileFound = row?.profile_found != null;
  const raw = row?.rounds_json;
  const rounds = Array.isArray(raw)
    ? (raw as Array<Record<string, unknown>>).map((r) => roundFromRow(r))
    : [];
  return { profileFound, rounds };
}

export async function getRoundById(
  pool: Pool,
  id: string
): Promise<Round | null> {
  const result = await pool.query(
    `SELECT id, profile_id, name, duration_seconds, warn10sec, position
     FROM rounds WHERE id = $1`,
    [id]
  );
  if (result.rows.length === 0) return null;
  return roundFromRow(result.rows[0]);
}

export async function createRound(
  pool: Pool,
  profileId: string,
  input: CreateRoundInput
): Promise<Round> {
  const warn10sec = input.warn10sec ?? false;
  const result = await pool.query(
    `INSERT INTO rounds (profile_id, name, duration_seconds, warn10sec, position)
     VALUES ($1, $2, $3, $4, $5)
     RETURNING id, profile_id, name, duration_seconds, warn10sec, position`,
    [profileId, input.name, input.duration_seconds, warn10sec, input.position]
  );
  return roundFromRow(result.rows[0]);
}

export async function updateRound(
  pool: Pool,
  id: string,
  input: UpdateRoundInput
): Promise<Round | null> {
  const updates: string[] = [];
  const values: unknown[] = [];
  let i = 1;
  if (input.name !== undefined) {
    updates.push(`name = $${i++}`);
    values.push(input.name);
  }
  if (input.duration_seconds !== undefined) {
    updates.push(`duration_seconds = $${i++}`);
    values.push(input.duration_seconds);
  }
  if (input.warn10sec !== undefined) {
    updates.push(`warn10sec = $${i++}`);
    values.push(input.warn10sec);
  }
  if (input.position !== undefined) {
    updates.push(`position = $${i++}`);
    values.push(input.position);
  }
  if (updates.length === 0) return getRoundById(pool, id);
  values.push(id);
  const result = await pool.query(
    `UPDATE rounds SET ${updates.join(', ')} WHERE id = $${i}
     RETURNING id, profile_id, name, duration_seconds, warn10sec, position`,
    values
  );
  if (result.rows.length === 0) return null;
  return roundFromRow(result.rows[0]);
}

export async function updateRoundForOwner(
  pool: Pool,
  roundId: string,
  userId: string,
  input: UpdateRoundInput
): Promise<Round | null> {
  const updates: string[] = [];
  const values: unknown[] = [];
  let i = 1;
  if (input.name !== undefined) {
    updates.push(`name = $${i++}`);
    values.push(input.name);
  }
  if (input.duration_seconds !== undefined) {
    updates.push(`duration_seconds = $${i++}`);
    values.push(input.duration_seconds);
  }
  if (input.warn10sec !== undefined) {
    updates.push(`warn10sec = $${i++}`);
    values.push(input.warn10sec);
  }
  if (input.position !== undefined) {
    updates.push(`position = $${i++}`);
    values.push(input.position);
  }
  if (updates.length === 0) {
    const r = await pool.query(
      `SELECT r.id, r.profile_id, r.name, r.duration_seconds, r.warn10sec, r.position
       FROM rounds r JOIN profiles p ON r.profile_id = p.id
       WHERE r.id = $1 AND p.user_id = $2`,
      [roundId, userId]
    );
    return r.rows.length === 0 ? null : roundFromRow(r.rows[0]);
  }
  values.push(roundId, userId);
  const result = await pool.query(
    `UPDATE rounds SET ${updates.join(', ')}
     WHERE id = $${i} AND profile_id IN (SELECT id FROM profiles WHERE user_id = $${i + 1})
     RETURNING id, profile_id, name, duration_seconds, warn10sec, position`,
    values
  );
  if (result.rows.length === 0) return null;
  return roundFromRow(result.rows[0]);
}

export async function deleteRoundForOwner(
  pool: Pool,
  roundId: string,
  userId: string
): Promise<boolean> {
  const result = await pool.query(
    `DELETE FROM rounds WHERE id = $1
     AND profile_id IN (SELECT id FROM profiles WHERE user_id = $2)
     RETURNING id`,
    [roundId, userId]
  );
  return result.rowCount !== null && result.rowCount > 0;
}

export async function deleteRound(pool: Pool, id: string): Promise<boolean> {
  const result = await pool.query(
    'DELETE FROM rounds WHERE id = $1 RETURNING id',
    [id]
  );
  return result.rowCount !== null && result.rowCount > 0;
}
