import { describe, expect, it } from 'vitest';
import {
  getCorsOrigin,
  getShutdownTimeoutMs,
  withTimeout,
} from '../src/server.js';

describe('server runtime config', () => {
  it('allows all origins by default outside production', () => {
    expect(
      getCorsOrigin({ NODE_ENV: 'development' } as NodeJS.ProcessEnv)
    ).toBe(true);
  });

  it('disables CORS by default in production', () => {
    expect(
      getCorsOrigin({ NODE_ENV: 'production' } as NodeJS.ProcessEnv)
    ).toBe(false);
  });

  it('parses configured CORS origins', () => {
    expect(
      getCorsOrigin({
        NODE_ENV: 'production',
        CORS_ORIGINS: ' https://one.example, https://two.example ',
      } as NodeJS.ProcessEnv)
    ).toEqual(['https://one.example', 'https://two.example']);
  });

  it('uses the default shutdown timeout when unset', () => {
    expect(getShutdownTimeoutMs({} as NodeJS.ProcessEnv)).toBe(10000);
  });

  it('uses a configured shutdown timeout when valid', () => {
    expect(
      getShutdownTimeoutMs({
        SHUTDOWN_TIMEOUT_MS: '2500',
      } as NodeJS.ProcessEnv)
    ).toBe(2500);
  });

  it('returns resolved values before the timeout', async () => {
    await expect(
      withTimeout(Promise.resolve('ok'), 50, 'timeout')
    ).resolves.toBe('ok');
  });

  it('rejects when the timeout elapses first', async () => {
    await expect(
      withTimeout(new Promise(() => {}), 10, 'timeout')
    ).rejects.toThrow('timeout');
  });
});
