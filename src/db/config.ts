/**
 * Database connection config from environment.
 * DATABASE_URL takes precedence; otherwise PG_* vars are used.
 */
export function getDbConfig(): { connectionString: string } {
  const url = process.env.DATABASE_URL;
  if (url) return { connectionString: url };
  const host = process.env.PG_HOST ?? '127.0.0.1';
  const port = process.env.PG_PORT ?? '5432';
  const user = process.env.PG_USER ?? 'postgres';
  const password = String(process.env.PG_PASSWORD ?? '');
  const database = process.env.PG_DATABASE ?? 'round';
  const encoded = encodeURIComponent(password);
  return {
    connectionString: `postgresql://${user}:${encoded}@${host}:${port}/${database}`,
  };
}
