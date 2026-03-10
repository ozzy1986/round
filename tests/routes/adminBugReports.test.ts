import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

const { listBugReportsMock, updateBugReportStatusMock } = vi.hoisted(() => ({
  listBugReportsMock: vi.fn(),
  updateBugReportStatusMock: vi.fn(),
}));

vi.mock('../../src/db/bugReports.js', async () => {
  const actual = await vi.importActual<typeof import('../../src/db/bugReports.js')>(
    '../../src/db/bugReports.js'
  );
  return {
    ...actual,
    listBugReports: listBugReportsMock,
    updateBugReportStatus: updateBugReportStatusMock,
  };
});

import type { FastifyInstance } from 'fastify';
import { buildApp } from '../../src/server.js';
import { closeTestApp } from '../helpers/app.js';

function basicAuthHeader(username: string, password: string): string {
  return `Basic ${Buffer.from(`${username}:${password}`, 'utf8').toString('base64')}`;
}

describe('admin bug reports route', () => {
  let app: FastifyInstance;
  const originalAdminUser = process.env.ADMIN_BASIC_USER;
  const originalAdminPassword = process.env.ADMIN_BASIC_PASSWORD;

  beforeAll(async () => {
    process.env.ADMIN_BASIC_USER = 'admin';
    process.env.ADMIN_BASIC_PASSWORD = 'top-secret-password';
    app = await buildApp();
  });

  afterAll(async () => {
    if (originalAdminUser == null) delete process.env.ADMIN_BASIC_USER;
    else process.env.ADMIN_BASIC_USER = originalAdminUser;
    if (originalAdminPassword == null) delete process.env.ADMIN_BASIC_PASSWORD;
    else process.env.ADMIN_BASIC_PASSWORD = originalAdminPassword;
    await closeTestApp(app);
  });

  beforeEach(() => {
    listBugReportsMock.mockReset();
    listBugReportsMock.mockResolvedValue([]);
    updateBugReportStatusMock.mockReset();
  });

  it('requires HTTP Basic auth', async () => {
    const res = await app.inject({
      method: 'GET',
      url: '/admin/bug-reports',
    });

    expect(res.statusCode).toBe(401);
    expect(res.headers['www-authenticate']).toContain('Basic realm="Round Admin"');
  });

  it('returns 404 when admin auth is not configured', async () => {
    delete process.env.ADMIN_BASIC_USER;
    delete process.env.ADMIN_BASIC_PASSWORD;

    const res = await app.inject({
      method: 'GET',
      url: '/admin/bug-reports',
      headers: {
        authorization: basicAuthHeader('admin', 'top-secret-password'),
      },
    });

    process.env.ADMIN_BASIC_USER = 'admin';
    process.env.ADMIN_BASIC_PASSWORD = 'top-secret-password';

    expect(res.statusCode).toBe(404);
  });

  it('renders bug reports with escaped content and forwards filters to the DB layer', async () => {
    listBugReportsMock.mockResolvedValue([
      {
        id: 'report-2',
        user_id: '11111111-1111-1111-1111-111111111111',
        message: 'Second message with <script>alert(1)</script>',
        screen: 'timer_screen',
        device_manufacturer: 'Google',
        device_brand: 'google',
        device_model: 'Pixel 9',
        os_version: 'Android 15',
        os_incremental: 'UP1A.240905.001',
        sdk_int: 35,
        app_version: '1.2.3',
        app_build: '42',
        build_display: 'HiOS 14.6.0 test build',
        build_fingerprint: 'TECNO/BG6/HiOS-15-test',
        security_patch: '2026-03-01',
        status: 'fixed',
        created_at: new Date('2026-03-09T10:59:00.000Z'),
      },
      {
        id: 'report-1',
        user_id: '11111111-1111-1111-1111-111111111111',
        message: 'First message',
        screen: null,
        device_manufacturer: 'Samsung',
        device_brand: 'samsung',
        device_model: 'S24',
        os_version: 'Android 14',
        os_incremental: 'UP1A.240801.001',
        sdk_int: 34,
        app_version: '1.2.2',
        app_build: '41',
        build_display: 'One UI 6.1',
        build_fingerprint: null,
        security_patch: '2026-02-01',
        status: 'open',
        created_at: new Date('2026-03-09T10:58:00.000Z'),
      },
    ]);

    const res = await app.inject({
      method: 'GET',
      url: '/admin/bug-reports?limit=2&q=crash&user_id=11111111-1111-1111-1111-111111111111&status=fixed&before=2026-03-09T12:00:00.000Z',
      headers: {
        authorization: basicAuthHeader('admin', 'top-secret-password'),
      },
    });

    expect(res.statusCode).toBe(200);
    expect(res.headers['content-type']).toContain('text/html');
    expect(listBugReportsMock).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({
        limit: 2,
        search: 'crash',
        userId: '11111111-1111-1111-1111-111111111111',
        status: 'fixed',
        before: '2026-03-09T12:00:00.000Z',
      })
    );
    expect(res.body).toContain('Баг-репорты');
    expect(res.body).toContain('report-2');
    expect(res.body).toContain('&lt;script&gt;alert(1)&lt;/script&gt;');
    expect(res.body).toContain('Загрузить более старые отчёты');
    expect(res.body).toContain('11111111-1111-1111-1111-111111111111');
    expect(res.body).toMatch(/\b\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}\b/);
    expect(res.body).toContain('Fingerprint сборки:');
    expect(res.body).toContain('TECNO/BG6/HiOS-15-test');
    expect(res.body).toContain('Исправлен');
    expect(res.body).toContain('HiOS 14.6.0 test build');
  });

  it('updates a bug report status via the admin route', async () => {
    updateBugReportStatusMock.mockResolvedValue({
      id: 'report-1',
      user_id: '11111111-1111-1111-1111-111111111111',
      message: 'First message',
      screen: null,
      device_manufacturer: 'Samsung',
      device_brand: 'samsung',
      device_model: 'S24',
      os_version: 'Android 14',
      os_incremental: 'UP1A.240801.001',
      sdk_int: 34,
      app_version: '1.2.2',
      app_build: '41',
      build_display: 'One UI 6.1',
      build_fingerprint: null,
      security_patch: '2026-02-01',
      status: 'fixed',
      created_at: new Date('2026-03-09T10:58:00.000Z'),
    });

    const res = await app.inject({
      method: 'PATCH',
      url: '/admin/bug-reports/11111111-1111-1111-1111-111111111111/status',
      headers: {
        authorization: basicAuthHeader('admin', 'top-secret-password'),
      },
      payload: {
        status: 'fixed',
      },
    });

    expect(res.statusCode).toBe(200);
    expect(updateBugReportStatusMock).toHaveBeenCalledWith(expect.anything(), {
      id: '11111111-1111-1111-1111-111111111111',
      status: 'fixed',
    });
    expect(res.json()).toEqual({
      id: 'report-1',
      status: 'fixed',
    });
  });
});
