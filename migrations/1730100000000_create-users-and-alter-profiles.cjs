/**
 * Creates users table and migrates profiles.user_id from text to uuid FK.
 * @param {import('node-pg-migrate').MigrationBuilder} pgm
 */
exports.up = (pgm) => {
  pgm.createTable('users', {
    id: {
      type: 'uuid',
      primaryKey: true,
      default: pgm.func('gen_random_uuid()'),
    },
    is_anonymous: { type: 'boolean', notNull: true, default: true },
    created_at: { type: 'timestamptz', notNull: true, default: pgm.func('current_timestamp') },
  });

  pgm.addColumns('profiles', {
    user_id_new: { type: 'uuid', references: 'users', onDelete: 'RESTRICT' },
  });

  pgm.sql('INSERT INTO users (is_anonymous) VALUES (true)');
  pgm.sql('UPDATE profiles SET user_id_new = (SELECT id FROM users LIMIT 1)');

  pgm.dropColumn('profiles', 'user_id');
  pgm.renameColumn('profiles', 'user_id_new', 'user_id');
  pgm.alterColumn('profiles', 'user_id', { notNull: true });
};

/**
 * @param {import('node-pg-migrate').MigrationBuilder} pgm
 */
exports.down = (pgm) => {
  pgm.alterColumn('profiles', 'user_id', { notNull: false });
  pgm.addColumns('profiles', { user_id_old: { type: 'text' } });
  pgm.sql('UPDATE profiles SET user_id_old = user_id::text');
  pgm.dropColumn('profiles', 'user_id');
  pgm.renameColumn('profiles', 'user_id_old', 'user_id');
  pgm.dropTable('users');
};
