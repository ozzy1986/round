import crypto from 'node:crypto';
import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { getPool } from '../db/pool.js';
import { listBugReports } from '../db/bugReports.js';
import type { BugReport } from '../db/types.js';

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
  const dd = String(d).padStart(2, '0');
  const mm = String(m).padStart(2, '0');
  return `${dd}.${mm}.${y}`;
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
}): string {
  const params = new URLSearchParams();
  params.set('limit', String(filters.limit));
  if (filters.query) params.set('q', filters.query);
  if (filters.userId) params.set('user_id', filters.userId);
  if (filters.before) params.set('before', filters.before);
  const queryString = params.toString();
  return queryString ? `/admin/bug-reports?${queryString}` : '/admin/bug-reports';
}

export function renderBugReportsAdminPage(input: {
  bugReports: BugReport[];
  limit: number;
  query: string;
  userId: string;
  nextBefore: string | null;
}): string {
  const { bugReports, limit, query, userId, nextBefore } = input;
  const cardsHtml =
    bugReports.length === 0
      ? '<div class="empty">No bug reports found for the current filters.</div>'
      : bugReports
          .map((report) => {
            const createdAtDisplay = formatDateDdMmYyyy(report.created_at);
            const userFilterHref = buildAdminHref({
              limit,
              query,
              userId: report.user_id,
            });
            return `
              <article class="card">
                <div class="card-head">
                  <div>
                    <div class="report-id">${escapeHtml(report.id)}</div>
                    <div class="meta-row">
                      <span>${escapeHtml(createdAtDisplay)}</span>
                      <span>Screen: ${escapeHtml(report.screen ?? 'unknown')}</span>
                    </div>
                  </div>
                  <a class="user-link" href="${escapeHtml(userFilterHref)}">${escapeHtml(
                    report.user_id
                  )}</a>
                </div>
                <div class="preview">${escapeHtml(formatPreview(report.message))}</div>
                <details>
                  <summary>Open details</summary>
                  <div class="details-grid">
                    <div><strong>App:</strong> ${escapeHtml(
                      `${report.app_version} (${report.app_build})`
                    )}</div>
                    <div><strong>Device:</strong> ${escapeHtml(
                      `${report.device_manufacturer} ${report.device_model}`
                    )}</div>
                    <div><strong>Android:</strong> ${escapeHtml(
                      `${report.os_version} (SDK ${String(report.sdk_int)})`
                    )}</div>
                    ${report.build_fingerprint ? `<div><strong>Build fingerprint:</strong> ${escapeHtml(report.build_fingerprint)}</div>` : ''}
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
        before: nextBefore,
      })
    : null;
  const clearHref = buildAdminHref({ limit });

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Round Admin - Bug Reports</title>
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
      grid-template-columns: minmax(0, 1.7fr) minmax(220px, 1fr) 110px auto auto;
      gap: 12px;
      margin: 24px 0;
      padding: 16px;
      background: var(--card);
      border: 1px solid var(--card-border);
      border-radius: 16px;
    }
    input, button, a.button-link {
      border-radius: 10px;
      border: 1px solid var(--card-border);
      font: inherit;
    }
    input {
      padding: 12px;
      background: #11161d;
      color: var(--text);
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
    <h1>Bug Reports</h1>
    <p>Quick admin inspector for the latest app bug reports.</p>
    <form method="get" action="/admin/bug-reports">
      <input type="search" name="q" value="${escapeHtml(
        query
      )}" placeholder="Search message, screen, device, app version, user ID">
      <input type="text" name="user_id" value="${escapeHtml(
        userId
      )}" placeholder="Filter by exact user ID">
      <input type="number" name="limit" min="1" max="${MAX_LIMIT}" value="${String(limit)}">
      <button type="submit">Apply</button>
      <a class="button-link secondary" href="${escapeHtml(clearHref)}">Clear</a>
    </form>
    <div class="results-meta">
      <span>Showing ${String(bugReports.length)} report(s).</span>
      ${
        nextBefore
          ? `<a class="button-link secondary" href="${escapeHtml(olderHref ?? clearHref)}">Older</a>`
          : '<span>No older page.</span>'
      }
    </div>
    <section class="cards">${cardsHtml}</section>
    ${
      nextBefore
        ? `<div class="pager"><a class="button-link" href="${escapeHtml(
            olderHref ?? clearHref
          )}">Load older reports</a></div>`
        : ''
    }
  </main>
</body>
</html>`;
}

function sendAdminAuthChallenge(reply: FastifyReply): FastifyReply {
  return reply
    .header('WWW-Authenticate', `Basic realm="${ADMIN_REALM}", charset="UTF-8"`)
    .type('text/plain; charset=utf-8')
    .status(401)
    .send('Unauthorized');
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
          },
        },
      },
    },
    async (req: FastifyRequest<{ Querystring: AdminBugReportsQuerystring }>, reply) => {
      if (!getAdminBasicAuthConfig()) {
        return reply.status(404).send({ message: 'Not found' });
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
      const bugReports = await listBugReports(pool, {
        limit,
        search: query || undefined,
        userId: userId || undefined,
        before: req.query.before || undefined,
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
            nextBefore,
          })
        );
    }
  );
}
