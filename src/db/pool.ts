import { Pool } from 'pg';
import { getDbConfig } from './config.js';

let pool: Pool | null = null;

// With PgBouncer use 20–50 per process; without, keep lower to not exhaust DB (e.g. 10).
const POOL_MAX = Number(process.env.PG_POOL_MAX) || 10;
const STMT_TIMEOUT_MS = process.env.PG_STMT_TIMEOUT_MS
  ? Number(process.env.PG_STMT_TIMEOUT_MS)
  : undefined;

export function getPool(): Pool {
  if (!pool) {
    pool = new Pool({
      ...getDbConfig(),
      max: POOL_MAX,
      idleTimeoutMillis: 30000,
      connectionTimeoutMillis: 5000,
      ...(STMT_TIMEOUT_MS != null && STMT_TIMEOUT_MS > 0
        ? { statement_timeout: STMT_TIMEOUT_MS }
        : {}),
    });
    pool.on('error', (err) => {
      console.error('PG pool error:', err.message);
    });
    const warnThreshold = Math.max(1, Math.floor(POOL_MAX * 0.8));
    pool.on('acquire', () => {
      const waiting = pool!.waitingCount;
      if (waiting > 0) {
        console.warn(
          `PG pool: ${waiting} queries queued (total=${pool!.totalCount}, idle=${pool!.idleCount}, max=${POOL_MAX})`
        );
      } else if (pool!.totalCount >= warnThreshold && pool!.idleCount === 0) {
        console.warn(
          `PG pool near capacity: total=${pool!.totalCount}/${POOL_MAX}, idle=0`
        );
      }
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
