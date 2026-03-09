import './auth/types.js';
import 'dotenv/config';
import { initSentry, closeSentry } from './sentry.js';
import Fastify, { type FastifyInstance, type FastifyRequest } from 'fastify';
import fjwt from '@fastify/jwt';
import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import rateLimit from '@fastify/rate-limit';
import { getPool } from './db/pool.js';
import { getBugReportEmailConfigMissingKeys } from './email.js';
import { getRedis } from './redis.js';
import { authRoutes } from './auth/register.js';
import { authVerify } from './auth/middleware.js';
import { adminBugReportsRoutes } from './routes/adminBugReports.js';
import { bugReportsRoutes } from './routes/bugReports.js';
import { privacyRoutes } from './routes/privacy.js';
import { profilesRoutes } from './routes/profiles.js';
import { roundsRoutes } from './routes/rounds.js';

const PORT = Number(process.env.PORT) || 3001;
const HOST = process.env.HOST ?? '127.0.0.1';
const DEFAULT_SHUTDOWN_TIMEOUT_MS = 10000;

const JWT_SECRET =
  process.env.JWT_SECRET ||
  (process.env.VITEST === 'true' ? 'test-secret-min-32-characters-long' : '');
if (!JWT_SECRET || JWT_SECRET.length < 32) {
  throw new Error('JWT_SECRET must be set and at least 32 characters');
}

export function getCorsOrigin(
  env: NodeJS.ProcessEnv = process.env
): boolean | string | string[] {
  const raw = env.CORS_ORIGINS;
  if (!raw || raw.trim() === '') {
    return env.NODE_ENV === 'production' ? false : true;
  }
  return raw.split(',').map((o) => o.trim()).filter(Boolean);
}

export function getShutdownTimeoutMs(
  env: NodeJS.ProcessEnv = process.env
): number {
  const raw = env.SHUTDOWN_TIMEOUT_MS?.trim();
  if (raw) {
    const value = Number(raw);
    if (Number.isFinite(value) && value > 0) return value;
  }
  return DEFAULT_SHUTDOWN_TIMEOUT_MS;
}

export async function withTimeout<T>(
  promise: Promise<T>,
  timeoutMs: number,
  message: string
): Promise<T> {
  let timeoutId: NodeJS.Timeout | undefined;
  const timeoutPromise = new Promise<never>((_resolve, reject) => {
    timeoutId = setTimeout(() => {
      reject(new Error(message));
    }, timeoutMs);
    timeoutId.unref?.();
  });
  try {
    return await Promise.race([promise, timeoutPromise]);
  } finally {
    if (timeoutId) clearTimeout(timeoutId);
  }
}

function getRateLimitKey(app: FastifyInstance, request: FastifyRequest): string {
  const auth = request.headers.authorization;
  if (!auth?.startsWith('Bearer ')) return request.ip;

  try {
    const decoded = app.jwt.verify<{ sub?: string }>(auth.slice(7));
    if (decoded?.sub) return `user:${decoded.sub}`;
  } catch {
    // fall back to IP when the token is invalid
  }

  return request.ip;
}

async function reportFatalError(error: unknown): Promise<void> {
  if (!process.env.SENTRY_DSN) {
    return;
  }

  try {
    const S = await import('@sentry/node');
    S.captureException(error);
    await S.flush(2000);
  } catch {
    // Best-effort fatal reporting only.
  }
}

export async function buildApp() {
  const app = Fastify({ logger: true, trustProxy: true });

  await app.register(fjwt, {
    secret: JWT_SECRET,
    sign: { expiresIn: '15m' },
    formatUser: (payload: { sub: string }) => ({ id: payload.sub }),
  });

  await app.register(cors, { origin: getCorsOrigin() });
  await app.register(helmet, { contentSecurityPolicy: false });
  const redis = getRedis();
  await app.register(rateLimit, {
    max: Number(process.env.RATE_LIMIT_MAX) || 200,
    timeWindow: process.env.RATE_LIMIT_WINDOW ?? '1 minute',
    keyGenerator: (request) => getRateLimitKey(app, request),
    ...(redis ? { redis } : {}),
  });
  if (process.env.NODE_ENV === 'production' && !process.env.REDIS_URL?.trim()) {
    app.log.warn(
      'REDIS_URL is not set; rate limits and caches stay per-instance in production'
    );
  }
  const bugReportEmailMissingKeys = getBugReportEmailConfigMissingKeys();
  if (bugReportEmailMissingKeys.length > 0) {
    app.log.warn(
      { missingKeys: bugReportEmailMissingKeys },
      'bug report email notifications are disabled'
    );
  }

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

  await app.register(privacyRoutes);
  await app.register(adminBugReportsRoutes);

  await app.register(
    async (instance) => {
      instance.addHook('onRequest', authVerify);
      await instance.register(bugReportsRoutes);
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
  const shutdownTimeoutMs = getShutdownTimeoutMs();
  const shutdown = async (signal: string) => {
    app.log.info({ signal, shutdownTimeoutMs }, 'shutting down');
    try {
      await withTimeout(
        (async () => {
          await app.close();
          await closeSentry();
          await closePool();
          await closeRedis();
        })(),
        shutdownTimeoutMs,
        `Graceful shutdown timed out after ${shutdownTimeoutMs}ms`
      );
      process.exit(0);
    } catch (err) {
      app.log.error(err);
      process.exit(1);
    }
  };
  const handleFatalProcessError = async (
    type: 'uncaughtException' | 'unhandledRejection',
    error: unknown
  ) => {
    app.log.error({ err: error, type }, 'fatal process error');
    await reportFatalError(error);
    process.exit(1);
  };
  process.on('SIGTERM', () => void shutdown('SIGTERM'));
  process.on('SIGINT', () => void shutdown('SIGINT'));
  process.on('uncaughtException', (error) => {
    void handleFatalProcessError('uncaughtException', error);
  });
  process.on('unhandledRejection', (reason) => {
    void handleFatalProcessError('unhandledRejection', reason);
  });
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
