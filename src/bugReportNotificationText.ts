import type { BugReport } from './db/types.js';
import { getBugReportStatusLabel } from './bugReportStatus.js';

/**
 * Field usefulness for debugging:
 * - Essential: device_manufacturer, device_model, os_version, sdk_int, app_version, app_build,
 *   screen, message. These identify device, OS level, and app build.
 * - Very useful for OEM/HiOS issues: build_display (device build string, often the only "OS version"
 *   visible on custom skins), build_fingerprint (exact ROM identity for repro).
 * - Useful but sometimes redundant: device_brand (disambiguates when different from manufacturer),
 *   os_incremental (build identity).
 * - Secondary: security_patch (relevant for security bugs; can be omitted from short notification).
 */
function appendOptionalLine(lines: string[], label: string, value: string | null): void {
  if (!value) return;
  lines.push(`${label}: ${value}`);
}

export function buildBugReportNotificationDetails(report: BugReport): string[] {
  const lines = [
    `ID отчёта: ${report.id}`,
    `Статус: ${getBugReportStatusLabel(report.status)}`,
    `Создан: ${report.created_at.toISOString()}`,
    `Пользователь: ${report.user_id}`,
    `Экран: ${report.screen ?? 'не указан'}`,
    `Версия приложения: ${report.app_version} (${report.app_build})`,
    `Устройство: ${report.device_manufacturer} ${report.device_model}`,
    `ОС: ${report.os_version.startsWith('Android') ? report.os_version : `Android ${report.os_version}`} (API ${report.sdk_int})`,
  ];

  appendOptionalLine(lines, 'Бренд', report.device_brand);
  appendOptionalLine(lines, 'Сборка/прошивка', report.build_display);
  appendOptionalLine(lines, 'Incremental', report.os_incremental);
  appendOptionalLine(lines, 'Fingerprint', report.build_fingerprint);
  appendOptionalLine(lines, 'Патч безопасности', report.security_patch);

  return lines;
}
