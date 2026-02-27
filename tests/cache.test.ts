import { describe, it, expect, beforeEach } from 'vitest';
import { cacheGet, cacheSet, CacheKeys, CacheTTL } from '../src/cache.js';

describe('cache', () => {
  beforeEach(() => {
    delete process.env.REDIS_URL;
  });

  it('cacheSet and cacheGet round-trip (in-memory when REDIS_URL unset)', async () => {
    await cacheSet('test:key', 'test-value', 60);
    const v = await cacheGet('test:key');
    expect(v).toBe('test-value');
  });

  it('cacheGet returns null for missing key', async () => {
    const v = await cacheGet('nonexistent');
    expect(v).toBeNull();
  });

  it('CacheKeys and CacheTTL are defined', () => {
    expect(CacheKeys.telegramToken(123)).toBe('tg:token:123');
    expect(CacheKeys.fileId('Round 1', 'en')).toBe('tg:fileid:Round 1:en');
    expect(CacheKeys.tts('text', 'ru')).toBe('tts:text:ru');
    expect(CacheTTL.TOKEN_TTL_SEC).toBe(86400);
    expect(CacheTTL.FILE_ID_TTL_SEC).toBe(604800);
  });
});
