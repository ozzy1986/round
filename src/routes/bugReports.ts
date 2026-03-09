import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { getUserId, type AuthenticatedRequest } from '../auth/middleware.js';
import { getPool } from '../db/pool.js';
import * as bugReportsDb from '../db/bugReports.js';
import { sendBugReportEmail } from '../email.js';
import { bugReportBodySchema, bugReportResponseSchema } from './schemas.js';

interface BugReportBody {
  message: string;
  screen?: string;
  device_manufacturer: string;
  device_model: string;
  os_version: string;
  sdk_int: number;
  app_version: string;
  app_build: string;
}

export async function bugReportsRoutes(app: FastifyInstance): Promise<void> {
  const pool = getPool();

  app.post<{ Body: BugReportBody }>(
    '/bug-reports',
    {
      config: {
        rateLimit: { max: 5, timeWindow: '15 minutes' },
      },
      schema: {
        body: bugReportBodySchema,
        response: {
          201: bugReportResponseSchema,
          400: { type: 'object', properties: { message: { type: 'string' } } },
          401: { type: 'object', properties: { message: { type: 'string' } } },
        },
      },
    },
    async (
      req: FastifyRequest<{ Body: BugReportBody }> & AuthenticatedRequest,
      reply: FastifyReply
    ) => {
      const userId = getUserId(req);
      const message = req.body.message.trim();
      const deviceManufacturer = req.body.device_manufacturer.trim();
      const deviceModel = req.body.device_model.trim();
      const osVersion = req.body.os_version.trim();
      const appVersion = req.body.app_version.trim();
      const appBuild = req.body.app_build.trim();

      if (
        message.length < 10 ||
        deviceManufacturer.length === 0 ||
        deviceModel.length === 0 ||
        osVersion.length === 0 ||
        appVersion.length === 0 ||
        appBuild.length === 0
      ) {
        return reply.status(400).send({ message: 'Bug report payload is invalid' });
      }

      const bugReport = await bugReportsDb.createBugReport(pool, {
        user_id: userId,
        message,
        screen: req.body.screen?.trim() || null,
        device_manufacturer: deviceManufacturer,
        device_model: deviceModel,
        os_version: osVersion,
        sdk_int: req.body.sdk_int,
        app_version: appVersion,
        app_build: appBuild,
      });

      try {
        await sendBugReportEmail(bugReport);
      } catch (error) {
        req.log.warn(
          { err: error, bugReportId: bugReport.id, userId },
          'failed to send bug report email'
        );
      }

      return reply.status(201).send({
        id: bugReport.id,
        created_at: bugReport.created_at.toISOString(),
      });
    }
  );
}
