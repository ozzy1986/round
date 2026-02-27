import crypto from 'node:crypto';
import type { Pool } from 'pg';

const REFRESH_TOKEN_BYTES = 32;
const REFRESH_TOKEN_TTL_SEC = 30 * 24 * 60 * 60; // 30 days

export function hashRefreshToken(token: string): string {
  return crypto.createHash('sha256').update(token, 'utf8').digest('hex');
}

function generateRefreshToken(): string {
  return crypto.randomBytes(REFRESH_TOKEN_BYTES).toString('hex');
}

export async function createRefreshToken(
  pool: Pool,
  userId: string
): Promise<{ refreshToken: string; expiresAt: Date }> {
  const refreshToken = generateRefreshToken();
  const tokenHash = hashRefreshToken(refreshToken);
  const expiresAt = new Date(Date.now() + REFRESH_TOKEN_TTL_SEC * 1000);
  await pool.query(
    `INSERT INTO refresh_tokens (token_hash, user_id, expires_at)
     VALUES ($1, $2, $3)`,
    [tokenHash, userId, expiresAt]
  );
  return { refreshToken, expiresAt };
}

export interface ValidRefreshToken {
  userId: string;
}

export async function findValidRefreshToken(
  pool: Pool,
  token: string
): Promise<ValidRefreshToken | null> {
  const tokenHash = hashRefreshToken(token);
  const result = await pool.query(
    `SELECT user_id FROM refresh_tokens
     WHERE token_hash = $1 AND revoked_at IS NULL AND expires_at > current_timestamp`,
    [tokenHash]
  );
  if (result.rows.length === 0) return null;
  return { userId: result.rows[0].user_id as string };
}

export async function revokeRefreshToken(pool: Pool, token: string): Promise<boolean> {
  const tokenHash = hashRefreshToken(token);
  const result = await pool.query(
    `UPDATE refresh_tokens SET revoked_at = current_timestamp
     WHERE token_hash = $1 AND revoked_at IS NULL
     RETURNING id`,
    [tokenHash]
  );
  return result.rowCount !== null && result.rowCount > 0;
}

export async function revokeRefreshTokenByHash(pool: Pool, tokenHash: string): Promise<boolean> {
  const result = await pool.query(
    `UPDATE refresh_tokens SET revoked_at = current_timestamp
     WHERE token_hash = $1 AND revoked_at IS NULL
     RETURNING id`,
    [tokenHash]
  );
  return result.rowCount !== null && result.rowCount > 0;
}
