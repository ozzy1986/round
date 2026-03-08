const SENTRY_DSN = process.env.SENTRY_DSN;
let sentryInitialized = false;

export async function initSentry(): Promise<void> {
  if (!SENTRY_DSN || process.env.VITEST === 'true') return;
  const Sentry = await import('@sentry/node');
  Sentry.init({
    dsn: SENTRY_DSN,
    environment: process.env.NODE_ENV ?? 'development',
    tracesSampleRate: 0.1,
  });
  sentryInitialized = true;
}

export async function closeSentry(timeoutMs: number = 2000): Promise<void> {
  if (!sentryInitialized || process.env.VITEST === 'true') return;
  const Sentry = await import('@sentry/node');
  await Sentry.close(timeoutMs);
}
