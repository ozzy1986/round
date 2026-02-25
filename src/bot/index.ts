import 'dotenv/config';
import { Telegraf, Markup, type Context } from 'telegraf';
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

const CB = {
  MENU_PROFILES: 'menu:profiles',
  MENU_QUICK: 'menu:quick',
  MENU_MAIN: 'menu:main',
  PROFILE_PREFIX: 'p:',
  TEMPLATE_PREFIX: 'tmpl:',
} as const;

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

function mainMenuKeyboard(locale: Locale) {
  return Markup.inlineKeyboard([
    [Markup.button.callback(getString(locale, 'bot.btn_profiles'), CB.MENU_PROFILES)],
    [Markup.button.callback(getString(locale, 'bot.btn_quick_timer'), CB.MENU_QUICK)],
  ]);
}

function withReplyMarkup(keyboard: ReturnType<typeof Markup.inlineKeyboard>) {
  return { reply_markup: keyboard.reply_markup };
}

function backButton(locale: Locale) {
  return Markup.button.callback(getString(locale, 'bot.btn_back'), CB.MENU_MAIN);
}

async function runTimer(
  ctx: Context,
  profile: TimerProfile,
  locale: Locale,
  userId: number,
): Promise<void> {
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
    const { events, nextState } = advance(profile, state);
    state = nextState;
    const done = await processEvents(events);
    if (done) clearInterval(intervalId);
  };

  const intervalId = setInterval(tick, 1000);
  runningTimers.set(userId, { intervalId, profile });
  await tick();
}

export function createBot(): Telegraf {
  if (!BOT_TOKEN) {
    throw new Error('TELEGRAM_BOT_TOKEN is required');
  }
  const bot = new Telegraf(BOT_TOKEN);

  bot.start((ctx) => {
    const locale = getLocale(ctx);
    ctx.reply(getString(locale, 'bot.welcome'), withReplyMarkup(mainMenuKeyboard(locale))).catch(() => {});
  });

  bot.command('profiles', async (ctx) => {
    const locale = getLocale(ctx);
    const list = await fetchProfiles();
    if (list.length === 0) {
      await ctx.reply(getString(locale, 'bot.profiles_empty'), withReplyMarkup(mainMenuKeyboard(locale)));
      return;
    }
    const lines = list.map((p) => `${p.emoji} ${p.name}`);
    const keyboard = Markup.inlineKeyboard([
      ...list.map((p) => [Markup.button.callback(`${p.emoji} ${p.name}`, CB.PROFILE_PREFIX + p.id)]),
      [backButton(locale)],
    ]);
    await ctx.reply(lines.join('\n'), withReplyMarkup(keyboard));
  });

  bot.command('timer', async (ctx) => {
    const locale = getLocale(ctx);
    const userId = ctx.from?.id;
    if (userId === undefined) return;

    const arg = ctx.message && 'text' in ctx.message
      ? ctx.message.text.replace(/^\/timer\s*/i, '').trim()
      : '';
    if (!arg) {
      await ctx.reply(getString(locale, 'bot.welcome'), withReplyMarkup(mainMenuKeyboard(locale)));
      return;
    }

    if (runningTimers.has(userId)) {
      await ctx.reply(getString(locale, 'bot.timer_already_running'));
      return;
    }

    let profile: TimerProfile | null = parseTimerSpec(arg);
    if (!profile) {
      const list = await fetchProfiles();
      const byName = list.find((p) => p.name.toLowerCase() === arg.toLowerCase());
      if (byName) profile = await fetchProfileWithRounds(byName.id);
      if (!profile) {
        const byId = list.find((p) => p.id === arg);
        if (byId) profile = await fetchProfileWithRounds(byId.id);
      }
    }
    if (!profile || !profile.rounds.length) {
      await ctx.reply(getString(locale, 'bot.profile_not_found'));
      return;
    }

    await runTimer(ctx, profile, locale, userId);
  });

  bot.action(CB.MENU_MAIN, async (ctx) => {
    await ctx.answerCbQuery?.();
    const locale = getLocale(ctx);
    const extra = withReplyMarkup(mainMenuKeyboard(locale));
    await ctx.editMessageText(getString(locale, 'bot.welcome'), extra).catch(() => {
      ctx.reply(getString(locale, 'bot.welcome'), extra).catch(() => {});
    });
  });

  bot.action(CB.MENU_PROFILES, async (ctx) => {
    await ctx.answerCbQuery?.();
    const locale = getLocale(ctx);
    const list = await fetchProfiles();
    if (list.length === 0) {
      await ctx.editMessageText(getString(locale, 'bot.profiles_empty'), withReplyMarkup(mainMenuKeyboard(locale))).catch(() => {});
      return;
    }
    const text = getString(locale, 'bot.profiles_title');
    const keyboard = Markup.inlineKeyboard([
      ...list.map((p) => [Markup.button.callback(`${p.emoji} ${p.name}`, CB.PROFILE_PREFIX + p.id)]),
      [backButton(locale)],
    ]);
    await ctx.editMessageText(text, withReplyMarkup(keyboard)).catch(() => {});
  });

  bot.action(CB.MENU_QUICK, async (ctx) => {
    await ctx.answerCbQuery?.();
    const locale = getLocale(ctx);
    const text = getString(locale, 'bot.quick_title');
    const keyboard = Markup.inlineKeyboard([
      [
        Markup.button.callback(getString(locale, 'bot.template_3x5_1'), CB.TEMPLATE_PREFIX + '3x5 1'),
        Markup.button.callback(getString(locale, 'bot.template_5x3_1'), CB.TEMPLATE_PREFIX + '5x3 1'),
      ],
      [Markup.button.callback(getString(locale, 'bot.template_5run_5walk'), CB.TEMPLATE_PREFIX + '5 run, 5 walk')],
      [backButton(locale)],
    ]);
    await ctx.editMessageText(text, withReplyMarkup(keyboard)).catch(() => {});
  });

  bot.action(new RegExp(`^${CB.PROFILE_PREFIX}`), async (ctx) => {
    const data = 'data' in (ctx.callbackQuery ?? {}) ? (ctx.callbackQuery as { data: string }).data : '';
    const profileId = data.slice(CB.PROFILE_PREFIX.length);
    await ctx.answerCbQuery?.();

    const userId = ctx.from?.id;
    if (userId === undefined) return;

    const locale = getLocale(ctx);
    if (runningTimers.has(userId)) {
      await ctx.reply(getString(locale, 'bot.timer_already_running'));
      return;
    }

    const profile = await fetchProfileWithRounds(profileId);
    if (!profile || !profile.rounds.length) {
      await ctx.reply(getString(locale, 'bot.profile_not_found'));
      return;
    }

    await runTimer(ctx, profile, locale, userId);
  });

  bot.action(new RegExp(`^${CB.TEMPLATE_PREFIX}`), async (ctx) => {
    const data = 'data' in (ctx.callbackQuery ?? {}) ? (ctx.callbackQuery as { data: string }).data : '';
    const spec = data.slice(CB.TEMPLATE_PREFIX.length);
    await ctx.answerCbQuery?.();

    const userId = ctx.from?.id;
    if (userId === undefined) return;

    const locale = getLocale(ctx);
    if (runningTimers.has(userId)) {
      await ctx.reply(getString(locale, 'bot.timer_already_running'));
      return;
    }

    const profile = parseTimerSpec(spec);
    if (!profile || !profile.rounds.length) {
      await ctx.reply(getString(locale, 'bot.profile_not_found'));
      return;
    }

    await runTimer(ctx, profile, locale, userId);
  });

  return bot;
}

export async function startBot(): Promise<void> {
  const bot = createBot();
  await bot.launch();
}
