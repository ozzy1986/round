import crypto from 'node:crypto';
import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { getPool } from '../db/pool.js';
import { listBugReports, updateBugReportStatus } from '../db/bugReports.js';
import type { BugReport } from '../db/types.js';
import {
  BUG_REPORT_STATUS_VALUES,
  getBugReportStatusLabel,
  isBugReportStatus,
  type BugReportStatus,
} from '../bugReportStatus.js';

const ADMIN_REALM = 'Round Admin';
const DEFAULT_LIMIT = 50;
const MAX_LIMIT = 200;

interface AdminBasicAuthConfig {
  username: string;
  password: string;
}

interface AdminBugReportsQuerystring {
  limit?: string;
  q?: string;
  user_id?: string;
  before?: string;
  status?: string;
}

interface AdminBugReportStatusBody {
  status: BugReportStatus;
}

function timingSafeEqual(a: string, b: string): boolean {
  if (!a || !b) return false;
  const bufA = crypto.createHash('sha256').update(a, 'utf8').digest();
  const bufB = crypto.createHash('sha256').update(b, 'utf8').digest();
  return bufA.length === bufB.length && crypto.timingSafeEqual(bufA, bufB);
}

export function getAdminBasicAuthConfig(
  env: NodeJS.ProcessEnv = process.env
): AdminBasicAuthConfig | null {
  const username = env.ADMIN_BASIC_USER?.trim();
  const password = env.ADMIN_BASIC_PASSWORD?.trim();
  if (!username || !password) return null;
  return { username, password };
}

export function parseBasicAuthorizationHeader(
  authorizationHeader: string | undefined
): { username: string; password: string } | null {
  if (!authorizationHeader?.startsWith('Basic ')) return null;
  try {
    const decoded = Buffer.from(authorizationHeader.slice(6), 'base64').toString('utf8');
    const separatorIndex = decoded.indexOf(':');
    if (separatorIndex < 0) return null;
    return {
      username: decoded.slice(0, separatorIndex),
      password: decoded.slice(separatorIndex + 1),
    };
  } catch {
    return null;
  }
}

export function isAuthorizedAdminRequest(
  authorizationHeader: string | undefined,
  env: NodeJS.ProcessEnv = process.env
): boolean {
  const config = getAdminBasicAuthConfig(env);
  const credentials = parseBasicAuthorizationHeader(authorizationHeader);
  if (!config || !credentials) return false;
  return (
    timingSafeEqual(credentials.username, config.username) &&
    timingSafeEqual(credentials.password, config.password)
  );
}

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function formatDateDdMmYyyy(date: Date): string {
  const d = date.getDate();
  const m = date.getMonth() + 1;
  const y = date.getFullYear();
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const dd = String(d).padStart(2, '0');
  const mm = String(m).padStart(2, '0');
  return `${dd}.${mm}.${y} ${hours}:${minutes}`;
}

function formatPreview(message: string): string {
  const normalized = message.replace(/\s+/g, ' ').trim();
  if (normalized.length <= 160) return normalized;
  return `${normalized.slice(0, 157)}...`;
}

function buildAdminHref(filters: {
  limit: number;
  query?: string;
  userId?: string;
  before?: string;
  status?: string;
}): string {
  const params = new URLSearchParams();
  params.set('limit', String(filters.limit));
  if (filters.query) params.set('q', filters.query);
  if (filters.userId) params.set('user_id', filters.userId);
  if (filters.before) params.set('before', filters.before);
  if (filters.status) params.set('status', filters.status);
  const queryString = params.toString();
  return queryString ? `/admin/bug-reports?${queryString}` : '/admin/bug-reports';
}

function renderStatusOptions(selectedStatus: string, includeAllOption: boolean): string {
  const options: Array<{ value: string; label: string }> = includeAllOption
    ? [{ value: '', label: 'Все статусы' }]
    : [];
  options.push(
    ...BUG_REPORT_STATUS_VALUES.map((status) => ({
      value: status,
      label: getBugReportStatusLabel(status),
    }))
  );

  return options
    .map(
      (option) =>
        `<option value="${escapeHtml(option.value)}"${
          option.value === selectedStatus ? ' selected' : ''
        }>${escapeHtml(option.label)}</option>`
    )
    .join('');
}

function buildStatusLabelMap(): Record<BugReportStatus, string> {
  return BUG_REPORT_STATUS_VALUES.reduce(
    (map, status) => {
      map[status] = getBugReportStatusLabel(status);
      return map;
    },
    {} as Record<BugReportStatus, string>
  );
}

export function renderBugReportsAdminPage(input: {
  bugReports: BugReport[];
  limit: number;
  query: string;
  userId: string;
  status: string;
  nextBefore: string | null;
}): string {
  const { bugReports, limit, query, userId, status, nextBefore } = input;
  const statusLabels = buildStatusLabelMap();
  const cardsHtml =
    bugReports.length === 0
      ? '<div class="empty">По текущим фильтрам баг-репорты не найдены.</div>'
      : bugReports
          .map((report) => {
            const createdAtDisplay = formatDateDdMmYyyy(report.created_at);
            const userFilterHref = buildAdminHref({
              limit,
              query,
              userId: report.user_id,
              status,
            });
            return `
              <article class="card">
                <div class="card-head">
                  <div>
                    <div class="report-id">${escapeHtml(report.id)}</div>
                    <div class="meta-row">
                      <span>${escapeHtml(createdAtDisplay)}</span>
                      <span>Экран: ${escapeHtml(report.screen ?? 'не указан')}</span>
                      <span class="status-badge status-${escapeHtml(report.status)}" data-status-label>${escapeHtml(
                        getBugReportStatusLabel(report.status)
                      )}</span>
                    </div>
                  </div>
                  <a class="user-link" href="${escapeHtml(userFilterHref)}">${escapeHtml(
                    report.user_id
                  )}</a>
                </div>
                <div class="preview">${escapeHtml(formatPreview(report.message))}</div>
                <form class="status-form" data-report-id="${escapeHtml(report.id)}">
                  <label>
                    <span>Статус</span>
                    <select name="status">
                      ${renderStatusOptions(report.status, false)}
                    </select>
                  </label>
                  <button type="submit">Сохранить</button>
                  <span class="status-save-result" aria-live="polite"></span>
                </form>
                <details>
                  <summary>Показать детали</summary>
                  <div class="details-grid">
                    <div><strong>Версия приложения:</strong> ${escapeHtml(
                      `${report.app_version} (${report.app_build})`
                    )}</div>
                    <div><strong>Производитель:</strong> ${escapeHtml(report.device_manufacturer)}</div>
                    ${report.device_brand ? `<div><strong>Бренд:</strong> ${escapeHtml(report.device_brand)}</div>` : ''}
                    <div><strong>Модель:</strong> ${escapeHtml(report.device_model)}</div>
                    <div><strong>Android:</strong> ${escapeHtml(report.os_version)}</div>
                    <div><strong>SDK:</strong> ${escapeHtml(String(report.sdk_int))}</div>
                    ${report.os_incremental ? `<div><strong>Incremental сборки:</strong> ${escapeHtml(report.os_incremental)}</div>` : ''}
                    ${report.build_display ? `<div><strong>Сборка/прошивка:</strong> ${escapeHtml(report.build_display)}</div>` : ''}
                    ${report.security_patch ? `<div><strong>Патч безопасности:</strong> ${escapeHtml(report.security_patch)}</div>` : ''}
                    ${report.build_fingerprint ? `<div><strong>Fingerprint сборки:</strong> ${escapeHtml(report.build_fingerprint)}</div>` : ''}
                  </div>
                  <pre class="message">${escapeHtml(report.message)}</pre>
                </details>
              </article>
            `;
          })
          .join('');

  const olderHref = nextBefore
    ? buildAdminHref({
        limit,
        query,
        userId,
        status,
        before: nextBefore,
      })
    : null;
  const clearHref = buildAdminHref({ limit });
  const statusLabelsJson = JSON.stringify(statusLabels);

  return `<!DOCTYPE html>
<html lang="ru">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Round Admin - Баг-репорты</title>
  <style>
    :root {
      color-scheme: light dark;
      --bg: #0f1115;
      --card: #181c22;
      --card-border: #2c3440;
      --text: #e8edf2;
      --muted: #9fb0c0;
      --accent: #8b5cf6;
      --accent-text: #ffffff;
    }
    body {
      margin: 0;
      font-family: system-ui, sans-serif;
      background: var(--bg);
      color: var(--text);
    }
    main {
      max-width: 1080px;
      margin: 0 auto;
      padding: 24px 16px 64px;
    }
    h1 {
      margin: 0 0 8px;
      font-size: 28px;
    }
    p {
      margin: 0;
      color: var(--muted);
    }
    form {
      display: grid;
      grid-template-columns: minmax(0, 1.5fr) minmax(220px, 1fr) minmax(160px, 0.8fr) 110px auto auto;
      gap: 12px;
      margin: 24px 0;
      padding: 16px;
      background: var(--card);
      border: 1px solid var(--card-border);
      border-radius: 16px;
    }
    input, button, a.button-link {
      box-sizing: border-box;
      border-radius: 10px;
      border: 1px solid var(--card-border);
      font: inherit;
    }
    input, select {
      padding: 12px;
      background: #11161d;
      color: var(--text);
      border-radius: 10px;
      border: 1px solid var(--card-border);
      font: inherit;
    }
    button, a.button-link {
      padding: 12px 14px;
      background: var(--accent);
      color: var(--accent-text);
      text-decoration: none;
      text-align: center;
      cursor: pointer;
    }
    a.button-link.secondary {
      background: transparent;
      color: var(--text);
    }
    .results-meta {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      margin-bottom: 16px;
      color: var(--muted);
    }
    .cards {
      display: grid;
      gap: 12px;
    }
    .card, .empty {
      background: var(--card);
      border: 1px solid var(--card-border);
      border-radius: 16px;
      padding: 16px;
    }
    .card-head {
      display: flex;
      justify-content: space-between;
      gap: 12px;
      align-items: flex-start;
      margin-bottom: 12px;
    }
    .report-id {
      font-weight: 700;
      word-break: break-all;
    }
    .meta-row {
      display: flex;
      flex-wrap: wrap;
      gap: 8px 16px;
      color: var(--muted);
      font-size: 14px;
      margin-top: 6px;
    }
    .user-link {
      color: var(--text);
      text-decoration: none;
      font-family: ui-monospace, SFMono-Regular, monospace;
      font-size: 13px;
      word-break: break-all;
    }
    .preview {
      font-size: 15px;
      line-height: 1.5;
      margin-bottom: 10px;
    }
    .status-badge {
      display: inline-flex;
      align-items: center;
      padding: 2px 10px;
      border-radius: 999px;
      font-size: 12px;
      font-weight: 700;
      letter-spacing: 0.02em;
    }
    .status-open {
      background: rgba(59, 130, 246, 0.18);
      color: #93c5fd;
    }
    .status-in_progress {
      background: rgba(245, 158, 11, 0.2);
      color: #fcd34d;
    }
    .status-fixed {
      background: rgba(34, 197, 94, 0.18);
      color: #86efac;
    }
    .status-closed {
      background: rgba(148, 163, 184, 0.18);
      color: #cbd5e1;
    }
    .status-form {
      display: grid;
      grid-template-columns: minmax(180px, 240px) auto minmax(0, 1fr);
      align-items: end;
      gap: 10px;
      margin: 0 0 12px;
      padding: 12px;
      background: #11161d;
      border: 1px solid var(--card-border);
      border-radius: 12px;
    }
    .status-form label {
      display: grid;
      gap: 6px;
      color: var(--muted);
      font-size: 14px;
    }
    .status-save-result {
      min-height: 20px;
      color: var(--muted);
      font-size: 14px;
      align-self: center;
    }
    details summary {
      cursor: pointer;
      color: var(--muted);
      margin-bottom: 10px;
    }
    .details-grid {
      display: grid;
      gap: 8px;
      margin-bottom: 12px;
      color: var(--muted);
      font-size: 14px;
    }
    .message {
      margin: 0;
      padding: 12px;
      border-radius: 12px;
      background: #11161d;
      border: 1px solid var(--card-border);
      white-space: pre-wrap;
      overflow-x: auto;
      font: inherit;
      line-height: 1.5;
    }
    .pager {
      margin-top: 20px;
    }
    @media (max-width: 860px) {
      form {
        grid-template-columns: 1fr;
      }
      .results-meta,
      .card-head {
        flex-direction: column;
        align-items: stretch;
      }
    }
  </style>
</head>
<body>
  <main>
    <h1>Баг-репорты</h1>
    <p>Быстрый админ-инспектор для входящих отчётов из приложения.</p>
    <form method="get" action="/admin/bug-reports">
      <input type="search" name="q" value="${escapeHtml(
        query
      )}" placeholder="Поиск по сообщению, экрану, устройству, версии, статусу, user ID">
      <input type="text" name="user_id" value="${escapeHtml(
        userId
      )}" placeholder="Фильтр по точному user ID">
      <select name="status">
        ${renderStatusOptions(status, true)}
      </select>
      <input type="number" name="limit" min="1" max="${MAX_LIMIT}" value="${String(limit)}">
      <button type="submit">Применить</button>
      <a class="button-link secondary" href="${escapeHtml(clearHref)}">Сбросить</a>
    </form>
    <div class="results-meta">
      <span>Показано отчётов: ${String(bugReports.length)}.</span>
      ${
        nextBefore
          ? `<a class="button-link secondary" href="${escapeHtml(olderHref ?? clearHref)}">Старше</a>`
          : '<span>Более старых страниц нет.</span>'
      }
    </div>
    <section class="cards">${cardsHtml}</section>
    ${
      nextBefore
        ? `<div class="pager"><a class="button-link" href="${escapeHtml(
            olderHref ?? clearHref
          )}">Загрузить более старые отчёты</a></div>`
        : ''
    }
  </main>
  <script>
    const statusLabels = JSON.parse('${statusLabelsJson}');
    for (const form of document.querySelectorAll('.status-form')) {
      form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const reportId = form.getAttribute('data-report-id');
        const select = form.querySelector('select[name="status"]');
        const result = form.querySelector('.status-save-result');
        const badge = form.parentElement?.querySelector('[data-status-label]');
        if (!reportId || !select || !result || !badge) return;
        result.textContent = 'Сохраняю...';
        try {
          const response = await fetch('/admin/bug-reports/' + encodeURIComponent(reportId) + '/status', {
            method: 'PATCH',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: select.value }),
          });
          if (!response.ok) {
            throw new Error('Не удалось сохранить статус');
          }
          const data = await response.json();
          badge.textContent = statusLabels[data.status] ?? data.status;
          badge.className = 'status-badge status-' + data.status;
          result.textContent = 'Сохранено';
        } catch (error) {
          result.textContent = error instanceof Error ? error.message : 'Не удалось сохранить статус';
        }
      });
    }
  </script>
</body>
</html>`;
}

function sendAdminAuthChallenge(reply: FastifyReply): FastifyReply {
  return reply
    .header('WWW-Authenticate', `Basic realm="${ADMIN_REALM}", charset="UTF-8"`)
    .type('text/plain; charset=utf-8')
    .status(401)
    .send('Требуется авторизация');
}

export async function adminBugReportsRoutes(app: FastifyInstance): Promise<void> {
  const pool = getPool();

  app.get<{ Querystring: AdminBugReportsQuerystring }>(
    '/admin/bug-reports',
    {
      config: { rateLimit: { max: 60, timeWindow: '1 minute' } },
      schema: {
        querystring: {
          type: 'object',
          properties: {
            limit: { type: 'string', pattern: '^[0-9]+$' },
            q: { type: 'string', maxLength: 200 },
            user_id: { type: 'string', format: 'uuid' },
            before: { type: 'string', format: 'date-time' },
            status: { type: 'string', enum: ['', ...BUG_REPORT_STATUS_VALUES] },
          },
        },
      },
    },
    async (req: FastifyRequest<{ Querystring: AdminBugReportsQuerystring }>, reply) => {
      if (!getAdminBasicAuthConfig()) {
        return reply.status(404).send({ message: 'Не найдено' });
      }
      if (!isAuthorizedAdminRequest(req.headers.authorization)) {
        return sendAdminAuthChallenge(reply);
      }

      const limit = Math.min(
        Math.max(Number.parseInt(req.query.limit ?? String(DEFAULT_LIMIT), 10) || DEFAULT_LIMIT, 1),
        MAX_LIMIT
      );
      const query = req.query.q?.trim() ?? '';
      const userId = req.query.user_id?.trim() ?? '';
      const status = req.query.status?.trim() ?? '';
      const bugReports = await listBugReports(pool, {
        limit,
        search: query || undefined,
        userId: userId || undefined,
        before: req.query.before || undefined,
        status: isBugReportStatus(status) ? status : undefined,
      });
      const nextBefore =
        bugReports.length === limit
          ? bugReports[bugReports.length - 1]?.created_at.toISOString() ?? null
          : null;

      return reply
        .header('Cache-Control', 'no-store')
        .type('text/html; charset=utf-8')
        .send(
          renderBugReportsAdminPage({
            bugReports,
            limit,
            query,
            userId,
            status,
            nextBefore,
          })
        );
    }
  );

  app.patch<{ Params: { id: string }; Body: AdminBugReportStatusBody }>(
    '/admin/bug-reports/:id/status',
    {
      config: { rateLimit: { max: 120, timeWindow: '1 minute' } },
      schema: {
        params: {
          type: 'object',
          required: ['id'],
          properties: {
            id: { type: 'string', format: 'uuid' },
          },
        },
        body: {
          type: 'object',
          required: ['status'],
          additionalProperties: false,
          properties: {
            status: { type: 'string', enum: [...BUG_REPORT_STATUS_VALUES] },
          },
        },
        response: {
          200: {
            type: 'object',
            required: ['id', 'status'],
            properties: {
              id: { type: 'string', format: 'uuid' },
              status: { type: 'string', enum: [...BUG_REPORT_STATUS_VALUES] },
            },
          },
          404: {
            type: 'object',
            required: ['message'],
            properties: { message: { type: 'string' } },
          },
        },
      },
    },
    async (req, reply) => {
      if (!getAdminBasicAuthConfig()) {
        return reply.status(404).send({ message: 'Не найдено' });
      }
      if (!isAuthorizedAdminRequest(req.headers.authorization)) {
        return sendAdminAuthChallenge(reply);
      }

      const bugReport = await updateBugReportStatus(pool, {
        id: req.params.id,
        status: req.body.status,
      });
      if (!bugReport) {
        return reply.status(404).send({ message: 'Баг-репорт не найден' });
      }

      return reply.send({
        id: bugReport.id,
        status: bugReport.status,
      });
    }
  );
}
