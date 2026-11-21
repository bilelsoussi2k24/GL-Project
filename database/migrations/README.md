# Migrations

Schema changes are applied by the Java application, not by SQL files in this folder.

- **Implementation:** `backend/src/config/DatabaseMigrator.java`
- **When:** on every backend start when `AUTO_MIGRATE=true` (the default)
- **Manual run:** `cd backend && java -cp "out;lib/*" tool.DbSetup`

## Rules the migrator follows

1. Additive only: `CREATE TABLE IF NOT EXISTS`, `ALTER TABLE ... ADD COLUMN` for columns that do not
   exist yet, `CREATE INDEX` for missing indexes.
2. Idempotent: running it twice changes nothing, and the whole run is serialised with a MySQL
   advisory lock (`GET_LOCK`) so two starting instances cannot race.
3. Non-destructive: no `DROP TABLE`, `DROP COLUMN`, `TRUNCATE` or data rewrites of existing profile
   fields.
4. Safe on legacy data: the original dump is upgraded in place, staff IDs are backfilled and legacy
   password placeholders are detected rather than trusted.
5. Administrator bootstrap: only an active account with a real hash counts as usable. An inactive
   administrator is reactivated without a password reset, a placeholder hash is replaced once, and an
   existing DOCTOR holding `ADMIN_EMAIL` is never modified (the migration reports the conflict).

## Adding a schema change

1. Add the table/column/index to the corresponding step in `DatabaseMigrator`
   (`createTables`, `addMissingColumns`, `addIndexes`, …).
2. Give new columns a default or make them nullable so existing rows remain valid.
3. Extend `database/schema.sql` (the reference schema) to match.
4. Test against both a fresh database and a copy of an older one before committing.

Keep this folder for future versioned SQL migrations if the project ever moves to Flyway/Liquibase;
today the reference schema and the Java migrator are the source of truth.
