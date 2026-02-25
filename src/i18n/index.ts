import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));
const en = JSON.parse(readFileSync(join(__dirname, 'en.json'), 'utf-8')) as Record<string, string>;
const ru = JSON.parse(readFileSync(join(__dirname, 'ru.json'), 'utf-8')) as Record<string, string>;

export type Locale = 'en' | 'ru';

const bundles: Record<Locale, Record<string, string>> = { en, ru };

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
export function localeFromTelegram(langCode: string | undefined): Locale {
  if (!langCode) return FALLBACK;
  const lower = langCode.slice(0, 2).toLowerCase();
  if (lower === 'ru') return 'ru';
  return 'en';
}
