import type { Pool } from 'pg';

export interface User {
  id: string;
  is_anonymous: boolean;
  created_at: Date;
  telegram_id?: number | null;
}

function userFromRow(row: Record<string, unknown>): User {
  return {
    id: row.id as string,
    is_anonymous: row.is_anonymous as boolean,
    created_at: row.created_at as Date,
    telegram_id: row.telegram_id != null ? (row.telegram_id as number) : null,
  };
}

export async function createAnonymousUser(pool: Pool): Promise<User> {
  const result = await pool.query(
    `INSERT INTO users (is_anonymous) VALUES (true)
     RETURNING id, is_anonymous, created_at`
  );
  return userFromRow(result.rows[0]);
}

export async function findOrCreateUserByTelegramId(
  pool: Pool,
  telegramId: number
): Promise<User> {
  const result = await pool.query(
    `INSERT INTO users (is_anonymous, telegram_id) VALUES (true, $1)
     ON CONFLICT (telegram_id) DO UPDATE SET telegram_id = users.telegram_id
     RETURNING id, is_anonymous, created_at, telegram_id`,
    [telegramId]
  );
  return userFromRow(result.rows[0]);
}
