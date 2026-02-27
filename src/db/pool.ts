import { Pool } from 'pg';
import { getDbConfig } from './config.js';

let pool: Pool | null = null;

const POOL_MAX = Number(process.env.PG_POOL_MAX) || 10;

export function getPool(): Pool {
  if (!pool) {
    pool = new Pool({
      ...getDbConfig(),
      max: POOL_MAX,
      idleTimeoutMillis: 30000,
      connectionTimeoutMillis: 5000,
    });
    pool.on('error', (err) => {
      console.error('PG pool error:', err.message);
    });
  }
  return pool;
}

export async function closePool(): Promise<void> {
  if (pool) {
    await pool.end();
    pool = null;
  }
}
