import { describe, expect, it } from 'vitest';
import {
  getBugReportEmailConfig,
  getBugReportEmailConfigMissingKeys,
  sendBugReportEmail,
} from '../src/email.js';

describe('bug report email config', () => {
  it('reports which required env keys are missing', () => {
    expect(
      getBugReportEmailConfigMissingKeys({ SMTP_HOST: 'smtp.gmail.com' } as NodeJS.ProcessEnv)
    ).toEqual(['BUG_REPORT_RECIPIENT']);
    expect(getBugReportEmailConfigMissingKeys({} as NodeJS.ProcessEnv)).toEqual([
      'SMTP_HOST',
      'BUG_REPORT_RECIPIENT',
    ]);
  });

  it('builds bug report email config when the required keys are present', () => {
    expect(
      getBugReportEmailConfig({
        SMTP_HOST: 'smtp.gmail.com',
        SMTP_PORT: '587',
        SMTP_SECURE: 'false',
        SMTP_USER: 'sender@example.com',
        SMTP_PASS: 'secret',
        SMTP_FROM: 'round@example.com',
        BUG_REPORT_RECIPIENT: 'ozeritski@gmail.com',
      } as NodeJS.ProcessEnv)
    ).toEqual({
      from: 'round@example.com',
      recipient: 'ozeritski@gmail.com',
      transport: {
        host: 'smtp.gmail.com',
        port: 587,
        secure: false,
        auth: {
          user: 'sender@example.com',
          pass: 'secret',
        },
      },
    });
  });

  it('throws a clear error instead of silently skipping when config is missing', async () => {
    await expect(
      sendBugReportEmail({
        id: 'bug-report-id',
        user_id: 'user-id',
        message: 'Enough detail to satisfy validation and reach the mail sender.',
        screen: 'profile_list_settings',
        device_manufacturer: 'Google',
        device_model: 'Pixel 8',
        os_version: 'Android 15',
        sdk_int: 35,
        app_version: '1.0.0',
        app_build: '1',
        build_fingerprint: null,
        created_at: new Date('2026-03-09T14:13:50.416Z'),
      })
    ).rejects.toThrow('Bug report email is not configured. Missing: SMTP_HOST, BUG_REPORT_RECIPIENT');
  });
});
