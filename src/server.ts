import 'dotenv/config';
import Fastify from 'fastify';
import { profilesRoutes } from './routes/profiles.js';
import { roundsRoutes } from './routes/rounds.js';

const PORT = Number(process.env.PORT) || 3001;
const HOST = process.env.HOST ?? '127.0.0.1';

export async function buildApp() {
  const app = Fastify({ logger: true });
  await app.register(profilesRoutes);
  await app.register(roundsRoutes);
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
