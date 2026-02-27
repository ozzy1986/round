import Redis from 'ioredis';

let client: Redis | null = null;

/**
 * Returns a Redis client when REDIS_URL is set; otherwise null.
 * Callers must fall back to in-memory when null (e.g. local dev).
 */
export function getRedis(): Redis | null {
  const url = process.env.REDIS_URL;
  if (!url || url.trim() === '') return null;
  if (client) return client;
  try {
    client = new Redis(url, { maxRetriesPerRequest: 3 });
    return client;
  } catch {
    return null;
  }
}

export async function closeRedis(): Promise<void> {
  if (client) {
    await client.quit();
    client = null;
  }
}
