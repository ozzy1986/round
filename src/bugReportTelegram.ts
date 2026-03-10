import type { BugReport } from './db/types.js';

const DEFAULT_TELEGRAM_API_BASE = 'https://api.telegram.org';
const DEFAULT_TELEGRAM_TIMEOUT_MS = 5000;
const DEFAULT_TELEGRAM_MESSAGE_LIMIT = 4000;

export interface BugReportTelegramConfig {
  apiBase: string;
  botToken: string;
  chatIds: string[];
  requestTimeoutMs: number;
}

function parsePositiveInteger(value: string | undefined, fallback: number): number {
  const parsed = Number(value?.trim());
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

function parseChatIds(env: NodeJS.ProcessEnv): string[] {
  const rawValues = [env.BUG_REPORT_TELEGRAM_CHAT_ID, env.BUG_REPORT_TELEGRAM_CHAT_IDS];
  const seen = new Set<string>();
  const chatIds: string[] = [];

  for (const rawValue of rawValues) {
    if (!rawValue) continue;
    for (const part of rawValue.split(',')) {
      const chatId = part.trim();
      if (!chatId || seen.has(chatId)) continue;
      seen.add(chatId);
      chatIds.push(chatId);
    }
  }

  return chatIds;
}

function joinTelegramApiUrl(apiBase: string, path: string): string {
  const trimmedBase = apiBase.replace(/\/+$/, '');
  return `${trimmedBase}/${path.replace(/^\/+/, '')}`;
}

function splitTextForTelegram(text: string, maxLength: number): string[] {
  const normalized = text.trim();
  if (!normalized) return [''];

  const chunks: string[] = [];
  let remaining = normalized;

  while (Array.from(remaining).length > maxLength) {
    const chars = Array.from(remaining);
    const candidate = chars.slice(0, maxLength).join('');
    const preferredBreak = Math.max(candidate.lastIndexOf('\n'), candidate.lastIndexOf(' '));
    const sliceLength =
      preferredBreak >= Math.floor(maxLength * 0.7)
        ? Array.from(candidate.slice(0, preferredBreak)).length
        : maxLength;

    chunks.push(chars.slice(0, sliceLength).join('').trimEnd());
    remaining = chars.slice(sliceLength).join('').trimStart();
  }

  if (remaining.length > 0) {
    chunks.push(remaining);
  }

  return chunks;
}

async function fetchWithTimeout(
  url: string,
  init: RequestInit,
  timeoutMs: number,
  fetchImpl: typeof fetch
): Promise<Response> {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

  try {
    return await fetchImpl(url, { ...init, signal: controller.signal });
  } finally {
    clearTimeout(timeoutId);
  }
}

export function getBugReportTelegramConfigMissingKeys(
  env: NodeJS.ProcessEnv = process.env
): string[] {
  const missingKeys: string[] = [];
  if (!env.TELEGRAM_BOT_TOKEN?.trim()) {
    missingKeys.push('TELEGRAM_BOT_TOKEN');
  }
  if (parseChatIds(env).length === 0) {
    missingKeys.push('BUG_REPORT_TELEGRAM_CHAT_ID or BUG_REPORT_TELEGRAM_CHAT_IDS');
  }
  return missingKeys;
}

export function getBugReportTelegramConfig(
  env: NodeJS.ProcessEnv = process.env
): BugReportTelegramConfig | null {
  const missingKeys = getBugReportTelegramConfigMissingKeys(env);
  if (missingKeys.length > 0) {
    return null;
  }

  return {
    apiBase: env.TELEGRAM_API_BASE?.trim() || DEFAULT_TELEGRAM_API_BASE,
    botToken: env.TELEGRAM_BOT_TOKEN!.trim(),
    chatIds: parseChatIds(env),
    requestTimeoutMs: parsePositiveInteger(
      env.BUG_REPORT_TELEGRAM_TIMEOUT_MS,
      DEFAULT_TELEGRAM_TIMEOUT_MS
    ),
  };
}

export function buildBugReportTelegramMessages(
  report: BugReport,
  maxMessageLength = DEFAULT_TELEGRAM_MESSAGE_LIMIT
): string[] {
  const headerLines = [
    '[Round] New bug report',
    `Report ID: ${report.id}`,
    `Created at: ${report.created_at.toISOString()}`,
    `User ID: ${report.user_id}`,
    `Screen: ${report.screen ?? 'unknown'}`,
    `App version: ${report.app_version} (${report.app_build})`,
    `Device: ${report.device_manufacturer} ${report.device_model}`,
    `Android: ${report.os_version} (SDK ${report.sdk_int})`,
  ];
  if (report.build_fingerprint) {
    headerLines.push(`Build fingerprint: ${report.build_fingerprint}`);
  }

  const firstMessagePrefix = `${headerLines.join('\n')}\n\nMessage:\n`;
  const firstMessageBudget = Math.max(
    500,
    maxMessageLength - Array.from(firstMessagePrefix).length
  );
  const messageChunks = splitTextForTelegram(report.message, firstMessageBudget);

  return messageChunks.map((chunk, index) => {
    if (index === 0) {
      return `${firstMessagePrefix}${chunk}`;
    }

    const continuationPrefix = `[Round] Bug report ${report.id} (continued ${index})\n\n`;
    const continuationBudget = Math.max(
      500,
      maxMessageLength - Array.from(continuationPrefix).length
    );
    const continuationChunks = splitTextForTelegram(chunk, continuationBudget);
    return continuationChunks.map((part) => `${continuationPrefix}${part}`);
  }).flat();
}

export async function sendBugReportTelegramNotification(
  report: BugReport,
  options?: {
    env?: NodeJS.ProcessEnv;
    fetchImpl?: typeof fetch;
  }
): Promise<void> {
  const env = options?.env ?? process.env;
  const config = getBugReportTelegramConfig(env);
  if (!config) {
    const missingKeys = getBugReportTelegramConfigMissingKeys(env);
    throw new Error(
      `Bug report Telegram notification is not configured. Missing: ${missingKeys.join(', ')}`
    );
  }

  const fetchImpl = options?.fetchImpl ?? fetch;
  const apiUrl = joinTelegramApiUrl(
    config.apiBase,
    `bot${config.botToken}/sendMessage`
  );
  const messages = buildBugReportTelegramMessages(report);
  const failures: string[] = [];

  for (const chatId of config.chatIds) {
    try {
      for (const text of messages) {
        const response = await fetchWithTimeout(
          apiUrl,
          {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              chat_id: chatId,
              disable_web_page_preview: true,
              text,
            }),
          },
          config.requestTimeoutMs,
          fetchImpl
        );
        const data = (await response.json().catch(() => null)) as
          | { ok?: boolean; description?: string }
          | null;

        if (!response.ok || data?.ok === false) {
          throw new Error(data?.description || `Telegram API returned HTTP ${response.status}`);
        }
      }
    } catch (error) {
      failures.push(`${chatId}: ${(error as Error).message}`);
    }
  }

  if (failures.length > 0) {
    throw new Error(
      `Failed to send bug report Telegram notification: ${failures.join('; ')}`
    );
  }
}
