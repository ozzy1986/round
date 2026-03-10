/**
 * @param {import('node-pg-migrate').MigrationBuilder} pgm
 */
exports.up = (pgm) => {
  pgm.addColumn('bug_reports', {
    device_brand: { type: 'text' },
    os_incremental: { type: 'text' },
    build_display: { type: 'text' },
    security_patch: { type: 'text' },
    status: { type: 'text', notNull: true, default: 'open' },
  });
  pgm.addConstraint(
    'bug_reports',
    'bug_reports_status_check',
    "CHECK (status IN ('open', 'in_progress', 'fixed', 'closed'))"
  );
  pgm.createIndex('bug_reports', ['status', 'created_at'], {
    name: 'idx_bug_reports_status_created_at',
  });
};

/**
 * @param {import('node-pg-migrate').MigrationBuilder} pgm
 */
exports.down = (pgm) => {
  pgm.dropIndex('bug_reports', ['status', 'created_at'], {
    name: 'idx_bug_reports_status_created_at',
  });
  pgm.dropConstraint('bug_reports', 'bug_reports_status_check');
  pgm.dropColumns('bug_reports', [
    'device_brand',
    'os_incremental',
    'build_display',
    'security_patch',
    'status',
  ]);
};
