import { describe, expect, it, vi } from 'vitest';
import {
  buildBugReportTelegramMessages,
  getBugReportTelegramConfig,
  getBugReportTelegramConfigMissingKeys,
  sendBugReportTelegramNotification,
} from '../src/bugReportTelegram.js';

const sampleReport = {
  id: 'bug-report-id',
  user_id: 'user-id',
  message: 'Enough detail to verify that Telegram delivery receives the bug report payload.',
  screen: 'profile_list_settings',
  device_manufacturer: 'Google',
  device_brand: 'google',
  device_model: 'Pixel 8',
  os_version: 'Android 15',
  os_incremental: 'UP1A.240905.001',
  sdk_int: 35,
  app_version: '1.0.0',
  app_build: '1',
  build_display: 'HiOS 14.6.0 test build',
  build_fingerprint: 'google/pixel/pixel8:15/UP1A/test:user/release-keys',
  security_patch: '2026-03-01',
  status: 'open' as const,
  created_at: new Date('2026-03-09T14:13:50.416Z'),
};

describe('bug report Telegram config', () => {
  it('reports which required env keys are missing', () => {
    expect(
      getBugReportTelegramConfigMissingKeys({
        TELEGRAM_BOT_TOKEN: 'bot-token',
      } as NodeJS.ProcessEnv)
    ).toEqual(['BUG_REPORT_TELEGRAM_CHAT_ID or BUG_REPORT_TELEGRAM_CHAT_IDS']);
    expect(getBugReportTelegramConfigMissingKeys({} as NodeJS.ProcessEnv)).toEqual([
      'TELEGRAM_BOT_TOKEN',
      'BUG_REPORT_TELEGRAM_CHAT_ID or BUG_REPORT_TELEGRAM_CHAT_IDS',
    ]);
  });

  it('builds the Telegram config from single or multiple chat IDs', () => {
    expect(
      getBugReportTelegramConfig({
        TELEGRAM_BOT_TOKEN: 'bot-token',
        BUG_REPORT_TELEGRAM_CHAT_ID: '123456',
      } as NodeJS.ProcessEnv)
    ).toEqual({
      apiBase: 'https://api.telegram.org',
      botToken: 'bot-token',
      chatIds: ['123456'],
      requestTimeoutMs: 5000,
    });

    expect(
      getBugReportTelegramConfig({
        TELEGRAM_BOT_TOKEN: 'bot-token',
        BUG_REPORT_TELEGRAM_CHAT_IDS: '123456, -100987654321, 123456',
        BUG_REPORT_TELEGRAM_TIMEOUT_MS: '7000',
      } as NodeJS.ProcessEnv)
    ).toEqual({
      apiBase: 'https://api.telegram.org',
      botToken: 'bot-token',
      chatIds: ['123456', '-100987654321'],
      requestTimeoutMs: 7000,
    });
  });

  it('splits long bug reports into Telegram-safe message chunks', () => {
    const messages = buildBugReportTelegramMessages({
      ...sampleReport,
      message: `Intro\n\n${'A'.repeat(9000)}`,
    });

    expect(messages.length).toBeGreaterThan(1);
    expect(messages.every((message) => Array.from(message).length <= 4000)).toBe(true);
    expect(messages[0]).toContain('Новый баг-репорт');
    expect(messages[0]).toContain('Статус: Открыт');
    expect(messages[0]).toContain('Отображаемая сборка: HiOS 14.6.0 test build');
  });

  it('sends every message chunk to every configured chat', async () => {
    const longReport = {
      ...sampleReport,
      message: `Intro\n\n${'B'.repeat(9000)}`,
    };
    const messages = buildBugReportTelegramMessages(longReport);
    const fetchMock = vi.fn().mockImplementation(async () => ({
      ok: true,
      status: 200,
      json: async () => ({ ok: true }),
    }));

    await sendBugReportTelegramNotification(longReport, {
      env: {
        TELEGRAM_BOT_TOKEN: 'bot-token',
        BUG_REPORT_TELEGRAM_CHAT_IDS: '111,222',
      } as NodeJS.ProcessEnv,
      fetchImpl: fetchMock as typeof fetch,
    });

    expect(fetchMock).toHaveBeenCalledTimes(messages.length * 2);
    const requestBodies = fetchMock.mock.calls.map(([, init]) =>
      JSON.parse(String(init?.body))
    ) as Array<{ chat_id: string; text: string }>;
    expect(requestBodies.every((body) => Array.from(body.text).length <= 4000)).toBe(true);
    expect(new Set(requestBodies.map((body) => body.chat_id))).toEqual(
      new Set(['111', '222'])
    );
  });

  it('throws a clear error instead of silently skipping when config is missing', async () => {
    await expect(
      sendBugReportTelegramNotification(sampleReport, {
        env: {} as NodeJS.ProcessEnv,
      })
    ).rejects.toThrow(
      'Bug report Telegram notification is not configured. Missing: TELEGRAM_BOT_TOKEN, BUG_REPORT_TELEGRAM_CHAT_ID or BUG_REPORT_TELEGRAM_CHAT_IDS'
    );
  });
});
