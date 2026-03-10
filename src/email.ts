import nodemailer from 'nodemailer';
import SMTPTransport from 'nodemailer/lib/smtp-transport/index.js';
import type { BugReport } from './db/types.js';
import { buildBugReportNotificationDetails } from './bugReportNotificationText.js';

interface BugReportEmailConfig {
  from: string;
  recipient: string;
  transport: SMTPTransport.Options;
}

let transporter: ReturnType<typeof nodemailer.createTransport> | null = null;

function parseBooleanFlag(value: string | undefined): boolean {
  return value?.trim().toLowerCase() === 'true';
}

export function getBugReportEmailConfigMissingKeys(
  env: NodeJS.ProcessEnv = process.env
): string[] {
  const missingKeys: string[] = [];
  if (!env.SMTP_HOST?.trim()) missingKeys.push('SMTP_HOST');
  if (!env.BUG_REPORT_RECIPIENT?.trim()) missingKeys.push('BUG_REPORT_RECIPIENT');
  return missingKeys;
}

export function getBugReportEmailConfig(
  env: NodeJS.ProcessEnv = process.env
): BugReportEmailConfig | null {
  const missingKeys = getBugReportEmailConfigMissingKeys(env);
  if (missingKeys.length > 0) {
    return null;
  }
  const host = env.SMTP_HOST!.trim();
  const recipient = env.BUG_REPORT_RECIPIENT!.trim();

  const port = Number(env.SMTP_PORT?.trim() || '587');
  const secure = parseBooleanFlag(env.SMTP_SECURE);
  const user = env.SMTP_USER?.trim();
  const pass = env.SMTP_PASS?.trim();
  const from = env.SMTP_FROM?.trim() || user || `round@${host}`;
  const isLocalhost =
    host === '127.0.0.1' || host === '::1' || host === 'localhost';
  const tlsOptions =
    isLocalhost || parseBooleanFlag(env.SMTP_INSECURE)
      ? { rejectUnauthorized: false }
      : undefined;

  return {
    from,
    recipient,
    transport: {
      host,
      port: Number.isFinite(port) ? port : 587,
      secure,
      ...(tlsOptions ? { tls: tlsOptions } : {}),
      ...(user ? { auth: { user, pass: pass ?? '' } } : {}),
    },
  };
}

function getTransporter(
  config: BugReportEmailConfig
): ReturnType<typeof nodemailer.createTransport> {
  if (!transporter) {
    transporter = nodemailer.createTransport(config.transport);
  }
  return transporter;
}

export async function sendBugReportEmail(report: BugReport): Promise<void> {
  const config = getBugReportEmailConfig();
  if (!config) {
    const missingKeys = getBugReportEmailConfigMissingKeys();
    throw new Error(
      `Bug report email is not configured. Missing: ${missingKeys.join(', ')}`
    );
  }

  const transport = getTransporter(config);
  const subject = `[Round] Новый баг-репорт ${report.id}`;
  const lines = [
    'Поступил новый баг-репорт из приложения.',
    '',
    ...buildBugReportNotificationDetails(report),
  ];
  lines.push('', 'Сообщение:', report.message);
  const text = lines.join('\n');

  await transport.sendMail({
    from: config.from,
    to: config.recipient,
    subject,
    text,
  });
}
