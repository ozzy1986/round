import 'dotenv/config';
import { Telegraf, type Context } from 'telegraf';
import { getString, localeFromTelegram, type Locale } from '../i18n/index.js';
import { parseTimerSpec } from './parser.js';
import { synthesize } from './tts.js';
import {
  getInitialState,
  advance,
  type TimerProfile,
  type TimerState,
  type TimerEvent,
} from '../timer-engine/index.js';

const BOT_TOKEN = process.env.TELEGRAM_BOT_TOKEN;
const API_BASE = process.env.API_URL ?? 'http://127.0.0.1:3001';

interface RunningTimer {
  intervalId: ReturnType<typeof setInterval>;
  profile: TimerProfile;
}

const runningTimers = new Map<number, RunningTimer>();

function getLocale(ctx: Context): Locale {
  const from = ctx.from;
  const langCode = from?.language_code;
  return localeFromTelegram(langCode);
}

async function fetchProfiles(): Promise<Array<{ id: string; name: string; emoji: string }>> {
  try {
    const res = await fetch(`${API_BASE}/profiles`);
    if (!res.ok) return [];
    const list = (await res.json()) as Array<{ id: string; name: string; emoji: string }>;
    return list;
  } catch {
    return [];
  }
}

interface ProfileWithRoundsDto {
  name: string;
  emoji?: string;
  rounds?: Array<{ name: string; duration_seconds: number; warn10sec?: boolean }>;
}

async function fetchProfileWithRounds(id: string): Promise<TimerProfile | null> {
  try {
    const res = await fetch(`${API_BASE}/profiles/${id}`);
    if (!res.ok) return null;
    const data = (await res.json()) as ProfileWithRoundsDto;
    return {
      name: data.name,
      emoji: data.emoji ?? '⏱',
      rounds: (data.rounds ?? []).map((r) => ({
        name: r.name,
        durationSeconds: r.duration_seconds,
        warn10sec: r.warn10sec ?? false,
      })),
    };
  } catch {
    return null;
  }
}

export function createBot(): Telegraf {
  if (!BOT_TOKEN) {
    throw new Error('TELEGRAM_BOT_TOKEN is required');
  }
  const bot = new Telegraf(BOT_TOKEN);

  bot.start((ctx) => {
    const locale = getLocale(ctx);
    ctx.reply(getString(locale, 'bot.welcome')).catch(() => {});
  });

  bot.command('profiles', async (ctx) => {
    const locale = getLocale(ctx);
    const list = await fetchProfiles();
    if (list.length === 0) {
      await ctx.reply(getString(locale, 'bot.profiles_empty'));
      return;
    }
    const lines = list.map((p) => `${p.emoji} ${p.name} (use /timer "${p.name}" or ID)`);
    await ctx.reply(lines.join('\n'));
  });

  bot.command('timer', async (ctx) => {
    const locale = getLocale(ctx);
    const userId = ctx.from?.id;
    if (userId === undefined) return;

    const arg = ctx.message && 'text' in ctx.message
      ? ctx.message.text.replace(/^\/timer\s*/i, '').trim()
      : '';
    if (!arg) {
      await ctx.reply(getString(locale, 'bot.timer_starting', { name: '?' }) + '\nUsage: /timer "3x5 1" or /timer <profile name>');
      return;
    }

    if (runningTimers.has(userId)) {
      await ctx.reply('Timer already running. Wait for it to finish.');
      return;
    }

    let profile: TimerProfile | null = parseTimerSpec(arg);
    if (!profile) {
      const list = await fetchProfiles();
      const byName = list.find((p) => p.name.toLowerCase() === arg.toLowerCase());
      if (byName) {
        profile = await fetchProfileWithRounds(byName.id);
      }
      if (!profile) {
        const byId = list.find((p) => p.id === arg);
        if (byId) profile = await fetchProfileWithRounds(byId.id);
      }
    }
    if (!profile || !profile.rounds.length) {
      await ctx.reply(getString(locale, 'bot.profile_not_found'));
      return;
    }

    await ctx.reply(getString(locale, 'bot.timer_starting', { name: profile.name }));

    let state: TimerState | null = getInitialState(profile);
    const langCode = ctx.from?.language_code;
    const ttsLang = langCode?.startsWith('ru') ? 'ru-RU' : 'en-US';

    const processEvents = async (events: TimerEvent[]): Promise<boolean> => {
      for (const event of events) {
        if (event.type === 'round_start') {
          const text = getString(locale, 'bot.timer_round', { name: event.round.name });
          const buf = await synthesize(text, ttsLang);
          if (buf) {
            await ctx.sendVoice({ source: buf, filename: 'round.ogg' }).catch(() => {});
          }
        } else if (event.type === 'warn10') {
          const text = getString(locale, 'bot.timer_warn10', { name: event.round.name });
          const buf = await synthesize(text, ttsLang);
          if (buf) {
            await ctx.sendVoice({ source: buf, filename: 'warn.ogg' }).catch(() => {});
          }
        } else if (event.type === 'training_end') {
          runningTimers.delete(userId);
          await ctx.reply(getString(locale, 'bot.timer_finished')).catch(() => {});
          return true;
        }
      }
      return false;
    };

    const tick = async () => {
      if (!state) return;
      const { events, nextState } = advance(profile!, state);
      state = nextState;
      const done = await processEvents(events);
      if (done) clearInterval(intervalId);
    };

    const intervalId = setInterval(tick, 1000);
    runningTimers.set(userId, { intervalId, profile });
    await tick();
  });

  return bot;
}

export async function startBot(): Promise<void> {
  const bot = createBot();
  await bot.launch();
}
