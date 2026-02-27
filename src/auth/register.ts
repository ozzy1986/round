import crypto from 'node:crypto';
import type { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { getPool } from '../db/pool.js';
import { createAnonymousUser, findOrCreateUserByTelegramId } from '../db/users.js';
import {
  createRefreshToken,
  findValidRefreshToken,
  revokeRefreshToken,
} from '../db/refreshTokens.js';

const BOT_SECRET = process.env.BOT_SECRET;
const ACCESS_TOKEN_EXPIRY = '15m';

function timingSafeEqual(a: string, b: string): boolean {
  if (!a || !b) return false;
  const bufA = crypto.createHash('sha256').update(a, 'utf8').digest();
  const bufB = crypto.createHash('sha256').update(b, 'utf8').digest();
  return bufA.length === bufB.length && crypto.timingSafeEqual(bufA, bufB);
}

async function sendTokens(
  reply: FastifyReply,
  userId: string,
  statusCode: number = 200
): Promise<void> {
  const token = await reply.jwtSign({ sub: userId }, { expiresIn: ACCESS_TOKEN_EXPIRY });
  const { refreshToken, expiresAt } = await createRefreshToken(getPool(), userId);
  return reply.status(statusCode).send({
    token,
    refresh_token: refreshToken,
    refresh_token_expires_at: expiresAt.toISOString(),
    user_id: userId,
  });
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
            required: ['token', 'refresh_token', 'user_id'],
            properties: {
              token: { type: 'string' },
              refresh_token: { type: 'string' },
              refresh_token_expires_at: { type: 'string', format: 'date-time' },
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
      return sendTokens(reply, user.id);
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
            required: ['token', 'refresh_token', 'user_id'],
            properties: {
              token: { type: 'string' },
              refresh_token: { type: 'string' },
              refresh_token_expires_at: { type: 'string', format: 'date-time' },
              user_id: { type: 'string', format: 'uuid' },
            },
          },
        },
      },
    },
    async (_req: FastifyRequest, reply: FastifyReply) => {
      const user = await createAnonymousUser(pool);
      return sendTokens(reply, user.id, 201);
    }
  );

  app.post(
    '/auth/refresh',
    {
      config: { rateLimit: { max: 30, timeWindow: '1 minute' } },
      schema: {
        body: {
          type: 'object',
          required: ['refresh_token'],
          properties: { refresh_token: { type: 'string' } },
        },
        response: {
          200: {
            type: 'object',
            required: ['token', 'refresh_token', 'user_id'],
            properties: {
              token: { type: 'string' },
              refresh_token: { type: 'string' },
              refresh_token_expires_at: { type: 'string', format: 'date-time' },
              user_id: { type: 'string', format: 'uuid' },
            },
          },
          401: { type: 'object', properties: { message: { type: 'string' } } },
        },
      },
    },
    async (req: FastifyRequest<{ Body: { refresh_token: string } }>, reply: FastifyReply) => {
      const valid = await findValidRefreshToken(pool, req.body.refresh_token);
      if (!valid) {
        return reply.status(401).send({ message: 'Invalid or expired refresh token' });
      }
      await revokeRefreshToken(pool, req.body.refresh_token);
      return sendTokens(reply, valid.userId);
    }
  );

  app.post(
    '/auth/logout',
    {
      config: { rateLimit: { max: 30, timeWindow: '1 minute' } },
      schema: {
        body: {
          type: 'object',
          required: ['refresh_token'],
          properties: { refresh_token: { type: 'string' } },
        },
        response: {
          204: { type: 'null' },
        },
      },
    },
    async (req: FastifyRequest<{ Body: { refresh_token: string } }>, reply: FastifyReply) => {
      await revokeRefreshToken(pool, req.body.refresh_token);
      return reply.status(204).send();
    }
  );
}
