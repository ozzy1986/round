import nodemailer from 'nodemailer';
import SMTPTransport from 'nodemailer/lib/smtp-transport/index.js';
import type { BugReport } from './db/types.js';

interface BugReportEmailConfig {
  from: string;
  recipient: string;
  transport: SMTPTransport.Options;
}

let transporter: ReturnType<typeof nodemailer.createTransport> | null = null;

function parseBooleanFlag(value: string | undefined): boolean {
  return value?.trim().toLowerCase() === 'true';
}

export function getBugReportEmailConfig(
  env: NodeJS.ProcessEnv = process.env
): BugReportEmailConfig | null {
  const host = env.SMTP_HOST?.trim();
  const recipient = env.BUG_REPORT_RECIPIENT?.trim();
  if (!host || !recipient) {
    return null;
  }

  const port = Number(env.SMTP_PORT?.trim() || '587');
  const secure = parseBooleanFlag(env.SMTP_SECURE);
  const user = env.SMTP_USER?.trim();
  const pass = env.SMTP_PASS?.trim();
  const from = env.SMTP_FROM?.trim() || user || `round@${host}`;

  return {
    from,
    recipient,
    transport: {
      host,
      port: Number.isFinite(port) ? port : 587,
      secure,
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
    return;
  }

  const transport = getTransporter(config);
  const createdAt = report.created_at.toISOString();
  const subject = `[Round] Bug report ${report.id}`;
  const text = [
    'A new bug report was submitted.',
    '',
    `Report ID: ${report.id}`,
    `Created at: ${createdAt}`,
    `User ID: ${report.user_id}`,
    `Screen: ${report.screen ?? 'unknown'}`,
    `App version: ${report.app_version} (${report.app_build})`,
    `Device: ${report.device_manufacturer} ${report.device_model}`,
    `Android: ${report.os_version} (SDK ${report.sdk_int})`,
    '',
    'Message:',
    report.message,
  ].join('\n');

  await transport.sendMail({
    from: config.from,
    to: config.recipient,
    subject,
    text,
  });
}
