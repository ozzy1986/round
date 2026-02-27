import type { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { getUserId, type AuthenticatedRequest } from '../auth/middleware.js';
import { getPool } from '../db/pool.js';
import * as profilesDb from '../db/profiles.js';
import * as roundsDb from '../db/rounds.js';

const roundBodySchema = {
  type: 'object',
  required: ['name', 'duration_seconds', 'position'],
  properties: {
    name: { type: 'string', minLength: 1, maxLength: 255 },
    duration_seconds: { type: 'integer', minimum: 0, maximum: 86400 },
    warn10sec: { type: 'boolean' },
    position: { type: 'integer', minimum: 0 },
  },
} as const;

const profileIdParamSchema = {
  type: 'object',
  required: ['id'],
  properties: { id: { type: 'string', format: 'uuid' } },
} as const;

const roundIdParamSchema = {
  type: 'object',
  required: ['roundId'],
  properties: { roundId: { type: 'string', format: 'uuid' } },
} as const;

const roundResponseSchema = {
  type: 'object',
  properties: {
    id: { type: 'string' },
    profile_id: { type: 'string' },
    name: { type: 'string' },
    duration_seconds: { type: 'integer' },
    warn10sec: { type: 'boolean' },
    position: { type: 'integer' },
  },
} as const;

export async function roundsRoutes(app: FastifyInstance): Promise<void> {
  const pool = getPool();

  app.get<{ Params: { id: string } }>(
    '/profiles/:id/rounds',
    {
      schema: {
        params: profileIdParamSchema,
        response: {
          200: {
            type: 'array',
            items: roundResponseSchema,
          },
          404: { type: 'object', properties: { message: { type: 'string' } } },
        },
      },
    },
    async (req: FastifyRequest<{ Params: { id: string } }> & AuthenticatedRequest, reply: FastifyReply) => {
      const userId = getUserId(req);
      const { profileFound, rounds } = await roundsDb.getRoundsForProfileOwner(
        pool,
        req.params.id,
        userId
      );
      if (!profileFound) return reply.status(404).send({ message: 'Profile not found' });
      return reply.send(rounds);
    }
  );

  app.post<{
    Params: { id: string };
    Body: { name: string; duration_seconds: number; warn10sec?: boolean; position: number };
  }>(
    '/profiles/:id/rounds',
    {
      schema: {
        params: profileIdParamSchema,
        body: roundBodySchema,
        response: {
          201: roundResponseSchema,
          404: { type: 'object', properties: { message: { type: 'string' } } },
        },
      },
    },
    async (
      req: FastifyRequest<{
        Params: { id: string };
        Body: { name: string; duration_seconds: number; warn10sec?: boolean; position: number };
      }> & AuthenticatedRequest,
      reply: FastifyReply
    ) => {
      const userId = getUserId(req);
      const profile = await profilesDb.getProfileById(pool, req.params.id, userId);
      if (!profile) return reply.status(404).send({ message: 'Profile not found' });
      const round = await roundsDb.createRound(pool, req.params.id, {
        name: req.body.name,
        duration_seconds: req.body.duration_seconds,
        warn10sec: req.body.warn10sec,
        position: req.body.position,
      });
      return reply.status(201).send(round);
    }
  );

  app.patch<{
    Params: { roundId: string };
    Body: { name?: string; duration_seconds?: number; warn10sec?: boolean; position?: number };
  }>(
    '/rounds/:roundId',
    {
      schema: {
        params: roundIdParamSchema,
        body: {
          type: 'object',
          properties: {
            name: { type: 'string', minLength: 1, maxLength: 255 },
            duration_seconds: { type: 'integer', minimum: 0, maximum: 86400 },
            warn10sec: { type: 'boolean' },
            position: { type: 'integer', minimum: 0 },
          },
        },
        response: {
          200: roundResponseSchema,
          404: { type: 'object', properties: { message: { type: 'string' } } },
        },
      },
    },
    async (
      req: FastifyRequest<{
        Params: { roundId: string };
        Body: { name?: string; duration_seconds?: number; warn10sec?: boolean; position?: number };
      }> & AuthenticatedRequest,
      reply: FastifyReply
    ) => {
      const userId = getUserId(req);
      const updated = await roundsDb.updateRoundForOwner(
        pool,
        req.params.roundId,
        userId,
        req.body
      );
      if (!updated) return reply.status(404).send({ message: 'Round not found' });
      return reply.send(updated);
    }
  );

  app.delete<{ Params: { roundId: string } }>(
    '/rounds/:roundId',
    {
      schema: {
        params: roundIdParamSchema,
        response: {
          204: { type: 'null' },
          404: { type: 'object', properties: { message: { type: 'string' } } },
        },
      },
    },
    async (req: FastifyRequest<{ Params: { roundId: string } }> & AuthenticatedRequest, reply: FastifyReply) => {
      const userId = getUserId(req);
      const deleted = await roundsDb.deleteRoundForOwner(pool, req.params.roundId, userId);
      if (!deleted) return reply.status(404).send({ message: 'Round not found' });
      return reply.status(204).send();
    }
  );

  app.put<{
    Params: { id: string };
    Body: { rounds: Array<{ name: string; duration_seconds: number; warn10sec?: boolean; position: number }> };
  }>(
    '/profiles/:id/rounds',
    {
      schema: {
        params: profileIdParamSchema,
        body: {
          type: 'object',
          required: ['rounds'],
          properties: {
            rounds: {
              type: 'array',
              maxItems: 500,
              items: {
                type: 'object',
                required: ['name', 'duration_seconds', 'position'],
                properties: {
                  name: { type: 'string', minLength: 1, maxLength: 255 },
                  duration_seconds: { type: 'integer', minimum: 5, maximum: 86400 },
                  warn10sec: { type: 'boolean' },
                  position: { type: 'integer', minimum: 0 },
                },
              },
            },
          },
        },
        response: {
          200: {
            type: 'array',
            items: roundResponseSchema,
          },
          404: { type: 'object', properties: { message: { type: 'string' } } },
        },
      },
    },
    async (
      req: FastifyRequest<{
        Params: { id: string };
        Body: { rounds: Array<{ name: string; duration_seconds: number; warn10sec?: boolean; position: number }> };
      }> & AuthenticatedRequest,
      reply: FastifyReply
    ) => {
      const userId = getUserId(req);
      const result = await roundsDb.replaceRoundsForProfile(
        pool,
        req.params.id,
        userId,
        req.body.rounds.map((r) => ({
          name: r.name,
          duration_seconds: r.duration_seconds,
          warn10sec: r.warn10sec,
          position: r.position,
        }))
      );
      if (result === null) {
        return reply.status(404).send({ message: 'Profile not found' });
      }
      return reply.send(result);
    }
  );
}
