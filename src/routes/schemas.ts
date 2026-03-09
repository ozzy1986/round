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

export const bugReportBodySchema = {
  type: 'object' as const,
  required: [
    'message',
    'device_manufacturer',
    'device_model',
    'os_version',
    'sdk_int',
    'app_version',
    'app_build',
  ],
  additionalProperties: false,
  properties: {
    message: { type: 'string', minLength: 10, maxLength: 5000 },
    screen: { type: 'string', minLength: 1, maxLength: 64 },
    device_manufacturer: { type: 'string', minLength: 1, maxLength: 120 },
    device_model: { type: 'string', minLength: 1, maxLength: 120 },
    os_version: { type: 'string', minLength: 1, maxLength: 120 },
    sdk_int: { type: 'integer', minimum: 1, maximum: 1000 },
    app_version: { type: 'string', minLength: 1, maxLength: 64 },
    app_build: { type: 'string', minLength: 1, maxLength: 64 },
    build_fingerprint: { type: 'string', minLength: 1, maxLength: 256 },
  },
};

export const bugReportResponseSchema = {
  type: 'object' as const,
  required: ['id', 'created_at'],
  properties: {
    id: { type: 'string', format: 'uuid' },
    created_at: { type: 'string', format: 'date-time' },
  },
};
