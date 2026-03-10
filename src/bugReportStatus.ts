export const BUG_REPORT_STATUS_VALUES = [
  'open',
  'in_progress',
  'fixed',
  'closed',
] as const;

export type BugReportStatus = (typeof BUG_REPORT_STATUS_VALUES)[number];

export function isBugReportStatus(value: string): value is BugReportStatus {
  return BUG_REPORT_STATUS_VALUES.includes(value as BugReportStatus);
}

export function getBugReportStatusLabel(status: BugReportStatus): string {
  switch (status) {
    case 'open':
      return 'Открыт';
    case 'in_progress':
      return 'В работе';
    case 'fixed':
      return 'Исправлен';
    case 'closed':
      return 'Закрыт';
  }
}
