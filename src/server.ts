import './auth/types.js';
import 'dotenv/config';
import Fastify from 'fastify';
import fjwt from '@fastify/jwt';
import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import rateLimit from '@fastify/rate-limit';
import { getPool } from './db/pool.js';
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

export async function buildApp() {
  const app = Fastify({ logger: true, trustProxy: true });

  await app.register(cors, { origin: true });
  await app.register(helmet, { contentSecurityPolicy: false });
  await app.register(rateLimit, {
    max: 100,
    timeWindow: '1 minute',
  });

  await app.register(fjwt, {
    secret: JWT_SECRET,
    sign: { expiresIn: '365d' },
    formatUser: (payload: { sub: string }) => ({ id: payload.sub }),
  });

  await app.register(authRoutes);

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

  app.setErrorHandler((error, request, reply) => {
    request.log.error(error);
    const statusCode = (error as { statusCode?: number }).statusCode ?? 500;
    void reply.status(statusCode).send({
      message: statusCode >= 500 ? 'Internal server error' : (error as Error).message,
    });
  });

  return app;
}

export async function start() {
  const app = await buildApp();
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
