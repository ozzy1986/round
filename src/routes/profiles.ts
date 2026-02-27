import type { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { getUserId, type AuthenticatedRequest } from '../auth/middleware.js';
import { getPool } from '../db/pool.js';
import * as profilesDb from '../db/profiles.js';

const profileSchema = {
  type: 'object',
  required: ['name', 'emoji'],
  properties: {
    name: { type: 'string', minLength: 1, maxLength: 255 },
    emoji: { type: 'string', minLength: 1, maxLength: 10 },
  },
} as const;

const profileIdParamSchema = {
  type: 'object',
  required: ['id'],
  properties: {
    id: { type: 'string', format: 'uuid' },
  },
} as const;

export async function profilesRoutes(app: FastifyInstance): Promise<void> {
  const pool = getPool();

  app.get<{ Querystring: { limit?: string; cursor?: string } }>(
    '/profiles',
    {
      schema: {
        querystring: {
          type: 'object',
          properties: {
            limit: { type: 'string', pattern: '^[0-9]+$' },
            cursor: { type: 'string' },
          },
        },
        response: {
          200: {
            type: 'object',
            required: ['data', 'next_cursor'],
            properties: {
              data: {
                type: 'array',
                items: {
                  type: 'object',
                  properties: {
                    id: { type: 'string' },
                    name: { type: 'string' },
                    emoji: { type: 'string' },
                    user_id: { type: ['string', 'null'] },
                    created_at: { type: 'string', format: 'date-time' },
                    updated_at: { type: 'string', format: 'date-time' },
                  },
                },
              },
              next_cursor: { type: ['string', 'null'] },
            },
          },
        },
      },
    },
    async (req: FastifyRequest<{ Querystring: { limit?: string; cursor?: string } }> & AuthenticatedRequest, reply: FastifyReply) => {
      const userId = getUserId(req);
      const limit = req.query.limit != null ? parseInt(req.query.limit, 10) : 20;
      const cursor = req.query.cursor ?? null;
      const { profiles, nextCursor } = await profilesDb.listProfiles(
        pool,
        userId,
        Number.isNaN(limit) ? 20 : limit,
        cursor || undefined
      );
      return reply.send({ data: profiles, next_cursor: nextCursor });
    }
  );

  app.get<{ Params: { id: string } }>(
    '/profiles/:id',
    {
      schema: {
        params: profileIdParamSchema,
        response: {
          200: {
            type: 'object',
            properties: {
              id: { type: 'string' },
              name: { type: 'string' },
              emoji: { type: 'string' },
              user_id: { type: ['string', 'null'] },
              created_at: { type: 'string', format: 'date-time' },
              updated_at: { type: 'string', format: 'date-time' },
              rounds: {
                type: 'array',
                items: {
                  type: 'object',
                  properties: {
                    id: { type: 'string' },
                    profile_id: { type: 'string' },
                    name: { type: 'string' },
                    duration_seconds: { type: 'integer' },
                    warn10sec: { type: 'boolean' },
                    position: { type: 'integer' },
                  },
                },
              },
            },
          },
          404: { type: 'object', properties: { message: { type: 'string' } } },
        },
      },
    },
    async (req: FastifyRequest<{ Params: { id: string } }> & AuthenticatedRequest, reply: FastifyReply) => {
      const userId = getUserId(req);
      const profile = await profilesDb.getProfileWithRoundsById(pool, req.params.id, userId);
      if (!profile) return reply.status(404).send({ message: 'Profile not found' });
      return reply.send(profile);
    }
  );

  app.post<{ Body: { name: string; emoji: string } }>(
    '/profiles',
    {
      schema: {
        body: profileSchema,
        response: {
          201: {
            type: 'object',
            properties: {
              id: { type: 'string' },
              name: { type: 'string' },
              emoji: { type: 'string' },
              user_id: { type: ['string', 'null'] },
              created_at: { type: 'string' },
              updated_at: { type: 'string' },
            },
          },
          400: { type: 'object', properties: { message: { type: 'string' } } },
        },
      },
    },
    async (req: FastifyRequest<{ Body: { name: string; emoji: string } }> & AuthenticatedRequest, reply: FastifyReply) => {
      const userId = getUserId(req);
      const created = await profilesDb.createProfile(pool, { ...req.body, user_id: userId });
      return reply.status(201).send(created);
    }
  );

  app.patch<{
    Params: { id: string };
    Body: { name?: string; emoji?: string };
  }>(
    '/profiles/:id',
    {
      schema: {
        params: profileIdParamSchema,
        body: {
          type: 'object',
          properties: {
            name: { type: 'string', minLength: 1, maxLength: 255 },
            emoji: { type: 'string', minLength: 1, maxLength: 10 },
          },
        },
        response: {
          200: {
            type: 'object',
            properties: {
              id: { type: 'string' },
              name: { type: 'string' },
              emoji: { type: 'string' },
              user_id: { type: ['string', 'null'] },
              created_at: { type: 'string' },
              updated_at: { type: 'string' },
            },
          },
          404: { type: 'object', properties: { message: { type: 'string' } } },
        },
      },
    },
    async (
      req: FastifyRequest<{
        Params: { id: string };
        Body: { name?: string; emoji?: string };
      }> & AuthenticatedRequest,
      reply: FastifyReply
    ) => {
      const userId = getUserId(req);
      const updated = await profilesDb.updateProfile(pool, req.params.id, userId, req.body);
      if (!updated) return reply.status(404).send({ message: 'Profile not found' });
      return reply.send(updated);
    }
  );

  app.delete<{ Params: { id: string } }>(
    '/profiles/:id',
    {
      schema: {
        params: profileIdParamSchema,
        response: {
          204: { type: 'null' },
          404: { type: 'object', properties: { message: { type: 'string' } } },
        },
      },
    },
    async (req: FastifyRequest<{ Params: { id: string } }> & AuthenticatedRequest, reply: FastifyReply) => {
      const userId = getUserId(req);
      const deleted = await profilesDb.deleteProfile(pool, req.params.id, userId);
      if (!deleted) return reply.status(404).send({ message: 'Profile not found' });
      return reply.status(204).send();
    }
  );
}
