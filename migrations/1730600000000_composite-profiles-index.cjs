/**
 * Replace two separate indexes with a single composite index.
 * Every profile query does WHERE user_id = $1 ORDER BY updated_at DESC,
 * so (user_id, updated_at DESC) covers both filter and sort in one B-tree scan.
 *
 * @param {import('node-pg-migrate').MigrationBuilder} pgm
 */
exports.up = (pgm) => {
  pgm.createIndex('profiles', ['user_id', { name: 'updated_at', sort: 'DESC' }], {
    name: 'idx_profiles_user_id_updated_at',
  });
  pgm.dropIndex('profiles', [], { name: 'idx_profiles_updated_at_desc' });
  pgm.dropIndex('profiles', [], { name: 'idx_profiles_user_id' });
};

/**
 * @param {import('node-pg-migrate').MigrationBuilder} pgm
 */
exports.down = (pgm) => {
  pgm.createIndex('profiles', [{ name: 'updated_at', sort: 'DESC' }], { name: 'idx_profiles_updated_at_desc' });
  pgm.createIndex('profiles', 'user_id', { name: 'idx_profiles_user_id' });
  pgm.dropIndex('profiles', [], { name: 'idx_profiles_user_id_updated_at' });
};
