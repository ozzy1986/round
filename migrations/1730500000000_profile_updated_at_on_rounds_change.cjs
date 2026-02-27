/**
 * When rounds are inserted/updated/deleted, update the parent profile's updated_at
 * so delta sync (updated_since) can detect changes.
 * @param {import('node-pg-migrate').MigrationBuilder} pgm
 */
exports.up = (pgm) => {
  pgm.sql(`
    CREATE OR REPLACE FUNCTION set_profile_updated_at_on_rounds_change()
    RETURNS TRIGGER AS $$
    BEGIN
      IF TG_OP = 'DELETE' THEN
        UPDATE profiles SET updated_at = current_timestamp WHERE id = OLD.profile_id;
        RETURN OLD;
      END IF;
      UPDATE profiles SET updated_at = current_timestamp WHERE id = NEW.profile_id;
      RETURN NEW;
    END;
    $$ LANGUAGE plpgsql;
  `);
  pgm.sql(`
    CREATE TRIGGER rounds_set_profile_updated_at
    AFTER INSERT OR UPDATE OR DELETE ON rounds
    FOR EACH ROW EXECUTE FUNCTION set_profile_updated_at_on_rounds_change();
  `);
};

/**
 * @param {import('node-pg-migrate').MigrationBuilder} pgm
 */
exports.down = (pgm) => {
  pgm.sql('DROP TRIGGER IF EXISTS rounds_set_profile_updated_at ON rounds');
  pgm.sql('DROP FUNCTION IF EXISTS set_profile_updated_at_on_rounds_change()');
};
