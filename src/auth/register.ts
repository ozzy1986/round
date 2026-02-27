import crypto from 'node:crypto';
import type { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { getPool } from '../db/pool.js';
import { createAnonymousUser, findOrCreateUserByTelegramId } from '../db/users.js';

const BOT_SECRET = process.env.BOT_SECRET;

function timingSafeEqual(a: string, b: string): boolean {
  if (!a || !b) return false;
  const bufA = crypto.createHash('sha256').update(a, 'utf8').digest();
  const bufB = crypto.createHash('sha256').update(b, 'utf8').digest();
  return bufA.length === bufB.length && crypto.timingSafeEqual(bufA, bufB);
}

export async function authRoutes(app: FastifyInstance): Promise<void> {
  const pool = getPool();

  app.post(
    '/auth/telegram',
    {
      config: { rateLimit: { max: 30, timeWindow: '1 minute' } },
      schema: {
        body: {
          type: 'object',
          required: ['telegram_id'],
          properties: { telegram_id: { type: 'integer' } },
        },
        response: {
          200: {
            type: 'object',
            required: ['token', 'user_id'],
            properties: {
              token: { type: 'string' },
              user_id: { type: 'string', format: 'uuid' },
            },
          },
          401: { type: 'object', properties: { message: { type: 'string' } } },
        },
      },
    },
    async (req: FastifyRequest<{ Body: { telegram_id: number } }>, reply: FastifyReply) => {
      const secret = (req.headers['x-bot-secret'] as string) || '';
      if (!BOT_SECRET || !timingSafeEqual(secret, BOT_SECRET)) {
        return reply.status(401).send({ message: 'Unauthorized' });
      }
      const user = await findOrCreateUserByTelegramId(pool, req.body.telegram_id);
      const token = await reply.jwtSign({ sub: user.id }, { expiresIn: '365d' });
      return reply.send({ token, user_id: user.id });
    }
  );

  app.post(
    '/auth/register',
    {
      config: {
        rateLimit: { max: 5, timeWindow: '1 minute' },
      },
      schema: {
        response: {
          201: {
            type: 'object',
            required: ['token', 'user_id'],
            properties: {
              token: { type: 'string' },
              user_id: { type: 'string', format: 'uuid' },
            },
          },
        },
      },
    },
    async (_req: FastifyRequest, reply: FastifyReply) => {
      const user = await createAnonymousUser(pool);
      const token = await reply.jwtSign(
        { sub: user.id },
        { expiresIn: '365d' }
      );
      return reply.status(201).send({ token, user_id: user.id });
    }
  );
}
