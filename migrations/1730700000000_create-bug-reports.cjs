/**
 * @param {import('node-pg-migrate').MigrationBuilder} pgm
 */
exports.up = (pgm) => {
  pgm.createTable('bug_reports', {
    id: { type: 'uuid', primaryKey: true, default: pgm.func('gen_random_uuid()') },
    user_id: { type: 'uuid', notNull: true, references: 'users', onDelete: 'CASCADE' },
    message: { type: 'text', notNull: true },
    screen: { type: 'text' },
    device_manufacturer: { type: 'text', notNull: true },
    device_model: { type: 'text', notNull: true },
    os_version: { type: 'text', notNull: true },
    sdk_int: { type: 'integer', notNull: true },
    app_version: { type: 'text', notNull: true },
    app_build: { type: 'text', notNull: true },
    created_at: { type: 'timestamptz', notNull: true, default: pgm.func('current_timestamp') },
  });
  pgm.createIndex('bug_reports', 'user_id', { name: 'idx_bug_reports_user_id' });
  pgm.createIndex('bug_reports', 'created_at', { name: 'idx_bug_reports_created_at' });
};

/**
 * @param {import('node-pg-migrate').MigrationBuilder} pgm
 */
exports.down = (pgm) => {
  pgm.dropTable('bug_reports');
};
