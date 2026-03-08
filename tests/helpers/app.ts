import type { FastifyInstance } from 'fastify';
import { closePool } from '../../src/db/pool.js';
import { closeRedis } from '../../src/redis.js';

export async function closeTestApp(app: FastifyInstance): Promise<void> {
  await app.close();
  await closePool();
  await closeRedis();
}
