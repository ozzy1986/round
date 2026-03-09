import type { Pool } from 'pg';
import type { BugReport, CreateBugReportInput } from './types.js';

function bugReportFromRow(row: Record<string, unknown>): BugReport {
  return {
    id: row.id as string,
    user_id: row.user_id as string,
    message: row.message as string,
    screen: (row.screen as string | null) ?? null,
    device_manufacturer: row.device_manufacturer as string,
    device_model: row.device_model as string,
    os_version: row.os_version as string,
    sdk_int: row.sdk_int as number,
    app_version: row.app_version as string,
    app_build: row.app_build as string,
    created_at: row.created_at as Date,
  };
}

export async function createBugReport(
  pool: Pool,
  input: CreateBugReportInput
): Promise<BugReport> {
  const result = await pool.query(
    `INSERT INTO bug_reports (
       user_id,
       message,
       screen,
       device_manufacturer,
       device_model,
       os_version,
       sdk_int,
       app_version,
       app_build
     )
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
     RETURNING
       id,
       user_id,
       message,
       screen,
       device_manufacturer,
       device_model,
       os_version,
       sdk_int,
       app_version,
       app_build,
       created_at`,
    [
      input.user_id,
      input.message,
      input.screen ?? null,
      input.device_manufacturer,
      input.device_model,
      input.os_version,
      input.sdk_int,
      input.app_version,
      input.app_build,
    ]
  );
  return bugReportFromRow(result.rows[0]);
}

export async function getBugReportById(
  pool: Pool,
  id: string
): Promise<BugReport | null> {
  const result = await pool.query(
    `SELECT
       id,
       user_id,
       message,
       screen,
       device_manufacturer,
       device_model,
       os_version,
       sdk_int,
       app_version,
       app_build,
       created_at
     FROM bug_reports
     WHERE id = $1`,
    [id]
  );
  if (result.rows.length === 0) return null;
  return bugReportFromRow(result.rows[0]);
}
