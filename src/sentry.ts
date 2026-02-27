const SENTRY_DSN = process.env.SENTRY_DSN;

export async function initSentry(): Promise<void> {
  if (!SENTRY_DSN || process.env.VITEST === 'true') return;
  const Sentry = await import('@sentry/node');
  Sentry.init({
    dsn: SENTRY_DSN,
    environment: process.env.NODE_ENV ?? 'development',
    tracesSampleRate: 0.1,
  });
}
