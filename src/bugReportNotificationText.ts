import type { BugReport } from './db/types.js';
import { getBugReportStatusLabel } from './bugReportStatus.js';

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
    `Производитель: ${report.device_manufacturer}`,
    `Модель: ${report.device_model}`,
    `Android: ${report.os_version}`,
    `SDK: ${report.sdk_int}`,
  ];

  appendOptionalLine(lines, 'Бренд', report.device_brand);
  appendOptionalLine(lines, 'Incremental сборки', report.os_incremental);
  appendOptionalLine(lines, 'Отображаемая сборка', report.build_display);
  appendOptionalLine(lines, 'Патч безопасности', report.security_patch);
  appendOptionalLine(lines, 'Fingerprint сборки', report.build_fingerprint);

  return lines;
}
