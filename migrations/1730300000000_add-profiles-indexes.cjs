/**
 * @param {import('node-pg-migrate').MigrationBuilder} pgm
 */
exports.up = (pgm) => {
  pgm.createIndex('profiles', [{ name: 'updated_at', sort: 'DESC' }], { name: 'idx_profiles_updated_at_desc' });
  pgm.createIndex('profiles', 'user_id', { name: 'idx_profiles_user_id' });
};

/**
 * @param {import('node-pg-migrate').MigrationBuilder} pgm
 */
exports.down = (pgm) => {
  pgm.dropIndex('profiles', [], { name: 'idx_profiles_updated_at_desc' });
  pgm.dropIndex('profiles', [], { name: 'idx_profiles_user_id' });
};
