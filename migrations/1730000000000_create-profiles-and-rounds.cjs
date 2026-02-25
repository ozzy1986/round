/**
 * @param {import('node-pg-migrate').MigrationBuilder} pgm
 */
exports.up = (pgm) => {
  pgm.createExtension('pgcrypto', { ifNotExists: true });

  pgm.createTable('profiles', {
    id: {
      type: 'uuid',
      primaryKey: true,
      default: pgm.func('gen_random_uuid()'),
    },
    name: { type: 'text', notNull: true },
    emoji: { type: 'text', notNull: true, default: '⏱' },
    user_id: { type: 'text', notNull: false },
    created_at: { type: 'timestamptz', notNull: true, default: pgm.func('current_timestamp') },
    updated_at: { type: 'timestamptz', notNull: true, default: pgm.func('current_timestamp') },
  });

  pgm.createTable('rounds', {
    id: {
      type: 'uuid',
      primaryKey: true,
      default: pgm.func('gen_random_uuid()'),
    },
    profile_id: {
      type: 'uuid',
      notNull: true,
      references: 'profiles',
      onDelete: 'CASCADE',
    },
    name: { type: 'text', notNull: true },
    duration_seconds: { type: 'integer', notNull: true },
    warn10sec: { type: 'boolean', notNull: true, default: false },
    position: { type: 'integer', notNull: true },
  });

  pgm.createIndex('rounds', 'profile_id');
  pgm.createIndex('rounds', ['profile_id', 'position'], { unique: true });
};

/**
 * @param {import('node-pg-migrate').MigrationBuilder} pgm
 */
exports.down = (pgm) => {
  pgm.dropTable('rounds');
  pgm.dropTable('profiles');
  pgm.dropExtension('pgcrypto', { ifExists: true });
};
