import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

const { sendBugReportEmailMock } = vi.hoisted(() => ({
  sendBugReportEmailMock: vi.fn(),
}));

vi.mock('../../src/email.js', async () => {
  const actual = await vi.importActual<typeof import('../../src/email.js')>(
    '../../src/email.js'
  );
  return {
    ...actual,
    sendBugReportEmail: sendBugReportEmailMock,
  };
});

import type { FastifyInstance } from 'fastify';
import { buildApp } from '../../src/server.js';
import { getPool } from '../../src/db/pool.js';
import { getBugReportById } from '../../src/db/bugReports.js';
import { closeTestApp } from '../helpers/app.js';

describe('bug reports routes', () => {
  let app: FastifyInstance;
  let authHeaders: { authorization: string };
  let testUserId: string;
  const pool = getPool();
  const validPayload = {
    message: 'The bug report dialog should save after I explain how to reproduce it.',
    screen: 'profile_list_settings',
    device_manufacturer: 'Google',
    device_model: 'Pixel 8',
    os_version: 'Android 15',
    sdk_int: 35,
    app_version: '1.0.0',
    app_build: '1',
  };

  beforeAll(async () => {
    app = await buildApp();
    const registerRes = await app.inject({ method: 'POST', url: '/auth/register' });
    expect(registerRes.statusCode).toBe(201);
    const { token, user_id } = registerRes.json() as { token: string; user_id: string };
    authHeaders = { authorization: `Bearer ${token}` };
    testUserId = user_id;
  });

  afterAll(async () => {
    await closeTestApp(app);
  });

  beforeEach(() => {
    sendBugReportEmailMock.mockReset();
    sendBugReportEmailMock.mockResolvedValue(undefined);
  });

  it('returns 201, stores the bug report, and sends an email alert', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/bug-reports',
      headers: authHeaders,
      payload: validPayload,
    });

    expect(res.statusCode).toBe(201);
    const body = res.json() as { id: string; created_at: string };
    expect(body.id).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
    );
    expect(body.created_at).toBeTruthy();

    const saved = await getBugReportById(pool, body.id);
    expect(saved).not.toBeNull();
    expect(saved?.user_id).toBe(testUserId);
    expect(saved?.message).toBe(validPayload.message);
    expect(saved?.screen).toBe(validPayload.screen);
    expect(saved?.device_model).toBe(validPayload.device_model);
    expect(sendBugReportEmailMock).toHaveBeenCalledTimes(1);
    expect(sendBugReportEmailMock).toHaveBeenCalledWith(
      expect.objectContaining({
        id: body.id,
        user_id: testUserId,
        message: validPayload.message,
      })
    );
  });

  it('returns 401 when authorization is missing', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/bug-reports',
      payload: validPayload,
    });

    expect(res.statusCode).toBe(401);
  });

  it('rejects a message shorter than 10 characters', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/bug-reports',
      headers: authHeaders,
      payload: {
        ...validPayload,
        message: 'too short',
      },
    });

    expect(res.statusCode).toBe(400);
  });

  it('still stores the report when sending the email fails', async () => {
    sendBugReportEmailMock.mockRejectedValueOnce(new Error('smtp unavailable'));

    const res = await app.inject({
      method: 'POST',
      url: '/bug-reports',
      headers: authHeaders,
      payload: {
        ...validPayload,
        message: 'Email delivery can fail, but the report must still be saved in the database.',
      },
    });

    expect(res.statusCode).toBe(201);
    const body = res.json() as { id: string };
    const saved = await getBugReportById(pool, body.id);
    expect(saved).not.toBeNull();
    expect(saved?.message).toContain('report must still be saved');
  });

  it('applies the per-user rate limit', async () => {
    const registerRes = await app.inject({ method: 'POST', url: '/auth/register' });
    expect(registerRes.statusCode).toBe(201);
    const { token } = registerRes.json() as { token: string };
    const freshHeaders = { authorization: `Bearer ${token}` };

    for (let attempt = 0; attempt < 5; attempt += 1) {
      const res = await app.inject({
        method: 'POST',
        url: '/bug-reports',
        headers: freshHeaders,
        payload: {
          ...validPayload,
          message: `Bug report attempt ${attempt} with enough detail to satisfy validation.`,
        },
      });
      expect(res.statusCode).toBe(201);
    }

    const blocked = await app.inject({
      method: 'POST',
      url: '/bug-reports',
      headers: freshHeaders,
      payload: {
        ...validPayload,
        message: 'Bug report attempt 6 should be rejected by the tighter route-specific limit.',
      },
    });

    expect(blocked.statusCode).toBe(429);
  });
});
