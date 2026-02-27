import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));

function loadBundle(file: string): Record<string, string> {
  return JSON.parse(readFileSync(join(__dirname, file), 'utf-8')) as Record<string, string>;
}

const en = loadBundle('en.json');
const ru = loadBundle('ru.json');
const uz = loadBundle('uz.json');
const kk = loadBundle('kk.json');
const az = loadBundle('az.json');
const tg = loadBundle('tg.json');
const tt = loadBundle('tt.json');
const zh = loadBundle('zh.json');

export type Locale = 'en' | 'ru' | 'uz' | 'kk' | 'az' | 'tg' | 'tt' | 'zh';

const bundles: Record<Locale, Record<string, string>> = { en, ru, uz, kk, az, tg, tt, zh };

const FALLBACK: Locale = 'en';

/**
 * Returns a string for the given locale and key. Replaces {name} etc. with params.
 */
export function getString(
  locale: Locale,
  key: string,
  params?: Record<string, string>
): string {
  const bundle = bundles[locale] ?? bundles[FALLBACK];
  let value = bundle[key] ?? bundles[FALLBACK][key] ?? key;
  if (params) {
    for (const [k, v] of Object.entries(params)) {
      value = value.replace(new RegExp(`\\{${k}\\}`, 'g'), v);
    }
  }
  return value;
}

/**
 * Normalizes Telegram language_code to our Locale.
 */
const LOCALE_MAP: Record<string, Locale> = {
  ru: 'ru', uz: 'uz', kk: 'kk', az: 'az', tg: 'tg', tt: 'tt', zh: 'zh',
};

export function localeFromTelegram(langCode: string | undefined): Locale {
  if (!langCode) return FALLBACK;
  const lower = langCode.slice(0, 2).toLowerCase();
  return LOCALE_MAP[lower] ?? FALLBACK;
}
