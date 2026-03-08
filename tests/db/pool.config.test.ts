import { describe, expect, it } from 'vitest';
import { getStatementTimeoutMs } from '../../src/db/pool.js';

describe('database pool config', () => {
  it('omits a statement timeout by default outside production', () => {
    expect(
      getStatementTimeoutMs({ NODE_ENV: 'development' } as NodeJS.ProcessEnv)
    ).toBeUndefined();
  });

  it('uses the production default when unset', () => {
    expect(
      getStatementTimeoutMs({ NODE_ENV: 'production' } as NodeJS.ProcessEnv)
    ).toBe(10000);
  });

  it('uses an explicit positive statement timeout when configured', () => {
    expect(
      getStatementTimeoutMs({
        NODE_ENV: 'production',
        PG_STMT_TIMEOUT_MS: '2500',
      } as NodeJS.ProcessEnv)
    ).toBe(2500);
  });

  it('falls back to the production default for invalid values', () => {
    expect(
      getStatementTimeoutMs({
        NODE_ENV: 'production',
        PG_STMT_TIMEOUT_MS: 'invalid',
      } as NodeJS.ProcessEnv)
    ).toBe(10000);
  });
});
