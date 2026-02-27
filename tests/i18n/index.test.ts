import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { getString, localeFromTelegram } from '../../src/i18n/index.js';

const __dirname = fileURLToPath(new URL('.', import.meta.url));

const ALL_LOCALES = ['en', 'ru', 'uz', 'kk', 'az', 'tg', 'tt', 'zh'] as const;

function loadKeys(locale: string): string[] {
  return Object.keys(
    JSON.parse(readFileSync(join(__dirname, `../../src/i18n/${locale}.json`), 'utf-8'))
  ).sort();
}

describe('i18n getString', () => {
  it('returns en string for en locale', () => {
    const s = getString('en', 'bot.welcome');
    expect(s).toContain('interval timer');
  });

  it('returns ru string for ru locale', () => {
    const s = getString('ru', 'bot.welcome');
    expect(s).toContain('интервальный');
  });

  it('returns uz string for uz locale', () => {
    const s = getString('uz', 'bot.welcome');
    expect(s).toContain('interval taymer');
  });

  it('returns kk string for kk locale', () => {
    const s = getString('kk', 'bot.welcome');
    expect(s).toContain('интервалды таймер');
  });

  it('returns az string for az locale', () => {
    const s = getString('az', 'bot.welcome');
    expect(s).toContain('interval taymer');
  });

  it('returns tg string for tg locale', () => {
    const s = getString('tg', 'bot.welcome');
    expect(s).toContain('таймери интервалӣ');
  });

  it('returns tt string for tt locale', () => {
    const s = getString('tt', 'bot.welcome');
    expect(s).toContain('интервал таймер');
  });

  it('returns zh string for zh locale', () => {
    const s = getString('zh', 'bot.welcome');
    expect(s).toContain('间歇计时器');
  });

  it('replaces params', () => {
    const s = getString('en', 'bot.timer_starting', { name: 'Test' });
    expect(s).toBe('Starting: Test');
  });

  it('falls back to en for unknown locale', () => {
    const s = getString('en', 'bot.welcome');
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
  it('returns uz for uz', () => {
    expect(localeFromTelegram('uz')).toBe('uz');
  });
  it('returns kk for kk', () => {
    expect(localeFromTelegram('kk')).toBe('kk');
  });
  it('returns az for az', () => {
    expect(localeFromTelegram('az')).toBe('az');
  });
  it('returns tg for tg', () => {
    expect(localeFromTelegram('tg')).toBe('tg');
  });
  it('returns tt for tt', () => {
    expect(localeFromTelegram('tt')).toBe('tt');
  });
  it('returns zh for zh', () => {
    expect(localeFromTelegram('zh')).toBe('zh');
  });
  it('returns en for undefined', () => {
    expect(localeFromTelegram(undefined)).toBe('en');
  });
  it('returns en for unknown language', () => {
    expect(localeFromTelegram('fr')).toBe('en');
  });
});

describe('i18n key coverage', () => {
  const enKeys = loadKeys('en');

  for (const locale of ALL_LOCALES) {
    if (locale === 'en') continue;
    it(`${locale} has same keys as en`, () => {
      expect(loadKeys(locale)).toEqual(enKeys);
    });
  }
});
