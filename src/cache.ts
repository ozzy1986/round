import { getRedis } from './redis.js';

const MEMORY_MAX = 10000;
const memory = new Map<string, { value: string; expiresAt?: number }>();
const memoryKeys: string[] = [];

/**
 * Get a string value. Uses Redis when REDIS_URL is set, else in-memory with TTL.
 */
export async function cacheGet(key: string): Promise<string | null> {
  const redis = getRedis();
  if (redis) {
    try {
      return await redis.get(key);
    } catch {
      return null;
    }
  }
  const entry = memory.get(key);
  if (!entry) return null;
  if (entry.expiresAt != null && entry.expiresAt < Date.now()) {
    memory.delete(key);
    return null;
  }
  return entry.value;
}

/**
 * Set a string value with optional TTL in seconds. Uses Redis when REDIS_URL is set.
 */
export async function cacheSet(key: string, value: string, ttlSeconds?: number): Promise<void> {
  const redis = getRedis();
  if (redis) {
    try {
      if (ttlSeconds != null) await redis.setex(key, ttlSeconds, value);
      else await redis.set(key, value);
    } catch {
      // ignore
    }
    return;
  }
  const expiresAt = ttlSeconds != null ? Date.now() + ttlSeconds * 1000 : undefined;
  if (memory.size >= MEMORY_MAX && !memory.has(key)) {
    const k = memoryKeys.shift();
    if (k) memory.delete(k);
  }
  if (!memory.has(key)) memoryKeys.push(key);
  memory.set(key, { value, expiresAt });
}

const TOKEN_TTL_SEC = 86400; // 24h
const FILE_ID_TTL_SEC = 604800; // 7 days

export const CacheKeys = {
  telegramToken: (telegramId: number) => `tg:token:${telegramId}`,
  fileId: (text: string, lang: string) => `tg:fileid:${text}:${lang}`,
  tts: (text: string, lang: string) => `tts:${text}:${lang}`,
} as const;

export const CacheTTL = { TOKEN_TTL_SEC, FILE_ID_TTL_SEC, TTS_TTL_SEC: 604800 } as const;
