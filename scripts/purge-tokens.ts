/**
 * Purge expired / long-revoked refresh tokens.
 * Usage: tsx scripts/purge-tokens.ts
 * Cron:  0 3 * * * cd /var/www/round && node dist/scripts/purge-tokens.js
 */
import 'dotenv/config';
import { getPool, closePool } from '../src/db/pool.js';
import { purgeStaleTokens } from '../src/db/refreshTokens.js';

const pool = getPool();
try {
  const deleted = await purgeStaleTokens(pool);
  console.log(`Purged ${deleted} stale refresh token(s).`);
} finally {
  await closePool();
}
