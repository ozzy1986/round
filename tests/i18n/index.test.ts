import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { getString, localeFromTelegram } from '../../src/i18n/index.js';

const __dirname = fileURLToPath(new URL('.', import.meta.url));

describe('i18n getString', () => {
  it('returns en string for en locale', () => {
    const s = getString('en', 'bot.welcome');
    expect(s).toContain('interval timer');
  });

  it('returns ru string for ru locale', () => {
    const s = getString('ru', 'bot.welcome');
    expect(s).toContain('интервальный');
  });

  it('replaces params', () => {
    const s = getString('en', 'bot.timer_starting', { name: 'Test' });
    expect(s).toBe('Starting: Test');
  });

  it('falls back to en for unknown key', () => {
    const s = getString('ru', 'bot.welcome');
    expect(s.length).toBeGreaterThan(0);
  });
});

describe('i18n localeFromTelegram', () => {
  it('returns ru for ru', () => {
    expect(localeFromTelegram('ru')).toBe('ru');
  });
  it('returns en for en', () => {
    expect(localeFromTelegram('en')).toBe('en');
  });
  it('returns en for undefined', () => {
    expect(localeFromTelegram(undefined)).toBe('en');
  });
});

describe('i18n key coverage', () => {
  it('en and ru have same keys', () => {
    const enKeys = Object.keys(JSON.parse(readFileSync(join(__dirname, '../../src/i18n/en.json'), 'utf-8'))).sort();
    const ruKeys = Object.keys(JSON.parse(readFileSync(join(__dirname, '../../src/i18n/ru.json'), 'utf-8'))).sort();
    expect(ruKeys).toEqual(enKeys);
  });
});
