/**
 * Shared Fastify JSON schemas for profiles and rounds routes (DRY).
 */

export const profileIdParamSchema = {
  type: 'object' as const,
  required: ['id'],
  properties: {
    id: { type: 'string', format: 'uuid' },
  },
};

export const profileSchema = {
  type: 'object' as const,
  required: ['name', 'emoji'],
  properties: {
    id: { type: 'string', format: 'uuid' },
    name: { type: 'string', minLength: 1, maxLength: 255 },
    emoji: { type: 'string', minLength: 1, maxLength: 10 },
  },
};

export const roundItemSchema = {
  type: 'object' as const,
  properties: {
    id: { type: 'string' },
    profile_id: { type: 'string' },
    name: { type: 'string' },
    duration_seconds: { type: 'integer' },
    warn10sec: { type: 'boolean' },
    position: { type: 'integer' },
  },
};

export const roundResponseSchema = {
  type: 'object' as const,
  properties: {
    id: { type: 'string' },
    profile_id: { type: 'string' },
    name: { type: 'string' },
    duration_seconds: { type: 'integer' },
    warn10sec: { type: 'boolean' },
    position: { type: 'integer' },
  },
};

export const roundBodySchema = {
  type: 'object' as const,
  required: ['name', 'duration_seconds', 'position'],
  properties: {
    name: { type: 'string', minLength: 1, maxLength: 255 },
    duration_seconds: { type: 'integer', minimum: 0, maximum: 7200 },
    warn10sec: { type: 'boolean' },
    position: { type: 'integer', minimum: 0 },
  },
};

export const roundIdParamSchema = {
  type: 'object' as const,
  required: ['roundId'],
  properties: { roundId: { type: 'string', format: 'uuid' } },
};
