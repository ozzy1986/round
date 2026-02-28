import './auth/types.js';
import 'dotenv/config';
import { initSentry } from './sentry.js';
import Fastify from 'fastify';
import fjwt from '@fastify/jwt';
import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import rateLimit from '@fastify/rate-limit';
import { getPool } from './db/pool.js';
import { getRedis } from './redis.js';
import { authRoutes } from './auth/register.js';
import { authVerify } from './auth/middleware.js';
import { profilesRoutes } from './routes/profiles.js';
import { roundsRoutes } from './routes/rounds.js';

const PORT = Number(process.env.PORT) || 3001;
const HOST = process.env.HOST ?? '127.0.0.1';

const JWT_SECRET =
  process.env.JWT_SECRET ||
  (process.env.VITEST === 'true' ? 'test-secret-min-32-characters-long' : '');
if (!JWT_SECRET || JWT_SECRET.length < 32) {
  throw new Error('JWT_SECRET must be set and at least 32 characters');
}

function getCorsOrigin(): boolean | string | string[] {
  const raw = process.env.CORS_ORIGINS;
  if (!raw || raw.trim() === '') return true;
  return raw.split(',').map((o) => o.trim()).filter(Boolean);
}

export async function buildApp() {
  const app = Fastify({ logger: true, trustProxy: true });

  await app.register(cors, { origin: getCorsOrigin() });
  await app.register(helmet, { contentSecurityPolicy: false });
  const redis = getRedis();
  await app.register(rateLimit, {
    max: Number(process.env.RATE_LIMIT_MAX) || 200,
    timeWindow: process.env.RATE_LIMIT_WINDOW ?? '1 minute',
    keyGenerator: (request: { ip: string; headers: { authorization?: string } }) => {
      const auth = request.headers.authorization;
      if (auth?.startsWith('Bearer ')) {
        try {
          const payload = auth.slice(7).split('.')[1];
          if (payload) {
            const decoded = JSON.parse(Buffer.from(payload, 'base64url').toString('utf8')) as { sub?: string };
            if (decoded?.sub) return `user:${decoded.sub}`;
          }
        } catch {
          // fall back to IP
        }
      }
      return request.ip;
    },
    ...(redis ? { redis } : {}),
  });

  await app.register(fjwt, {
    secret: JWT_SECRET,
    sign: { expiresIn: '15m' },
    formatUser: (payload: { sub: string }) => ({ id: payload.sub }),
  });

  await app.register(authRoutes);

  // Liveness: no DB. Use for load balancer / process manager.
  app.get('/', async (_req, reply) => reply.send({ status: 'ok' }));

  app.get('/health', async (_req, reply) => {
    try {
      await getPool().query('SELECT 1');
      return reply.send({ status: 'ok' });
    } catch {
      return reply.status(503).send({ status: 'unavailable' });
    }
  });

  await app.register(
    async (instance) => {
      instance.addHook('onRequest', authVerify);
      await instance.register(profilesRoutes);
      await instance.register(roundsRoutes);
    },
    { prefix: '' }
  );

  app.setErrorHandler((error: unknown, request, reply) => {
    request.log.error(error);
    const status = error && typeof error === 'object' && 'statusCode' in error ? (error as { statusCode: number }).statusCode : 500;
    if (process.env.SENTRY_DSN && status >= 500) {
      import('@sentry/node').then((S) => { if (S && S.captureException) S.captureException(error); }).catch(() => {});
    }
    const statusCode = status;
    void reply.status(statusCode).send({
      message: statusCode >= 500 ? 'Internal server error' : (error as Error).message,
    });
  });

  return app;
}

export async function start() {
  await initSentry();
  const app = await buildApp();
  const { closePool } = await import('./db/pool.js');
  const { closeRedis } = await import('./redis.js');
  const shutdown = async (signal: string) => {
    app.log.info({ signal }, 'shutting down');
    try {
      await app.close();
      await closePool();
      await closeRedis();
      process.exit(0);
    } catch (err) {
      app.log.error(err);
      process.exit(1);
    }
  };
  process.on('SIGTERM', () => void shutdown('SIGTERM'));
  process.on('SIGINT', () => void shutdown('SIGINT'));
  try {
    await app.listen({ port: PORT, host: HOST });
  } catch (err) {
    app.log.error(err);
    process.exit(1);
  }
}

if (process.env.VITEST !== 'true') {
  start().catch((err) => {
    console.error(err);
    process.exit(1);
  });
}
