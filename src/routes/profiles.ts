import type { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { getUserId, type AuthenticatedRequest } from '../auth/middleware.js';
import { getPool } from '../db/pool.js';
import * as profilesDb from '../db/profiles.js';
import {
  profileSchema,
  profileIdParamSchema,
  roundItemSchema,
} from './schemas.js';

export async function profilesRoutes(app: FastifyInstance): Promise<void> {
  const pool = getPool();

  app.get<{ Querystring: { limit?: string; cursor?: string; include?: string; updated_since?: string } }>(
    '/profiles',
    {
      schema: {
        querystring: {
          type: 'object',
          properties: {
            limit: { type: 'string', pattern: '^[0-9]+$' },
            cursor: { type: 'string', format: 'date-time' },
            include: { type: 'string', enum: ['rounds'] },
            updated_since: { type: 'string', format: 'date-time' },
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
                    rounds: {
                      type: 'array',
                      items: roundItemSchema,
                    },
                  },
                },
              },
              next_cursor: { type: ['string', 'null'] },
            },
          },
        },
      },
    },
    async (req: FastifyRequest<{ Querystring: { limit?: string; cursor?: string; include?: string; updated_since?: string } }> & AuthenticatedRequest, reply: FastifyReply) => {
      const userId = getUserId(req);
      const limit = req.query.limit != null ? parseInt(req.query.limit, 10) : 20;
      const cursor = req.query.cursor ?? null;
      const includeRounds = req.query.include === 'rounds';
      const updatedSince = req.query.updated_since ?? null;

      if (includeRounds) {
        const { profiles, nextCursor } = await profilesDb.listProfilesWithRounds(
          pool,
          userId,
          Number.isNaN(limit) ? 20 : limit,
          cursor || undefined,
          updatedSince || undefined
        );
        const data = profiles.map((p) => ({
          id: p.id,
          name: p.name,
          emoji: p.emoji,
          user_id: p.user_id,
          created_at: (p.created_at as Date).toISOString(),
          updated_at: (p.updated_at as Date).toISOString(),
          rounds: p.rounds.map((r) => ({
            id: r.id,
            profile_id: r.profile_id,
            name: r.name,
            duration_seconds: r.duration_seconds,
            warn10sec: r.warn10sec,
            position: r.position,
          })),
        }));
        return reply.send({ data, next_cursor: nextCursor });
      }

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

  app.post<{ Body: { name: string; emoji: string; id?: string } }>(
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
    async (req: FastifyRequest<{ Body: { name?: string; emoji: string; id?: string } }> & AuthenticatedRequest, reply: FastifyReply) => {
      const userId = getUserId(req);
      const created = await profilesDb.createProfile(pool, {
        name: req.body.name!,
        emoji: req.body.emoji,
        user_id: userId,
        ...(req.body.id ? { id: req.body.id } : {}),
      });
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
