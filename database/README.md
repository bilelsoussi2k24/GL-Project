# Database

The application uses a single MySQL 8 database, `hospital_db`. The Java API owns all access: it
creates the schema, upgrades it in place and reads/writes everything through prepared statements.

## Requirements

| Item | Requirement |
| --- | --- |
| MySQL Server | 8.0 or newer (verified on 8.0.46) |
| Character set | `utf8mb4` / `utf8mb4_0900_ai_ci` |
| Engine | InnoDB (foreign keys are used) |
| Account privileges | `SELECT`, `INSERT`, `UPDATE`, `DELETE` on `hospital_db`; `CREATE` only for the first start if you want the API to create the database/tables |

MySQL 5.7 is not supported (the schema uses MySQL 8 defaults and `utf8mb4_0900_ai_ci`).

## Files in this folder

| File | What it is | Safe to import into a populated database? |
| --- | --- | --- |
| `hospital_db.sql` | The original MySQL dump from the first version of the project (6 tables, seed rows, placeholder password hashes) | **No.** It contains six `DROP TABLE IF EXISTS` statements and would erase the tables it defines. |
| `schema.sql` | Readable reference of the current schema. Uses `CREATE TABLE IF NOT EXISTS` and `INSERT IGNORE` only. | Yes, but you normally do not need it: the API applies the same schema on start. |
| `migrations/README.md` | Where schema upgrades live and how they are applied | — |

> ### ⚠ Destructive script warning
>
> `hospital_db.sql` begins each table section with `DROP TABLE IF EXISTS 'appointments'`,
> `'consultations'`, `'doctor_patients'`, `'doctors'`, `'patients'` and `'users'` (and disables
> foreign-key checks around them). Importing it into `hospital_db` deletes all existing rows in those
> tables, including users, patients, appointments and consultations.
>
> **Never run it against a database that holds data you care about.** Use one of the safe procedures
> below.

## Safe setup procedures

### A. New installation (recommended)

1. Create the database (or let the API do it):

   ```bat
   mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS hospital_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
   ```

2. Configure `backend/.env` (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`).
3. Start the API: `cd backend && run.bat` (Linux/macOS: `./run.sh`).

The migration creates the eight tables (`users`, `doctors`, `patients`, `doctor_patients`,
`appointments`, `consultations`, `auth_sessions`, `hospital_settings`), seeds the default hospital
settings and creates the first administrator. No SQL import is required.

### B. Import the original dump into an **empty** database (optional)

Only if you want the original sample doctors/patients/appointments for demonstration:

```bat
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS hospital_legacy CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
mysql -u root -p hospital_legacy < database/hospital_db.sql
```

The dump contains `CREATE DATABASE IF NOT EXISTS hospital_db` and `USE hospital_db`, so importing it
into `hospital_legacy` still targets `hospital_db`. To keep it truly separate, edit those two lines in
a **copy** of the file, or import it only when `hospital_db` does not exist yet.

Then point the API at that database and let the migration adapt it:

```ini
DB_URL=jdbc:mysql://localhost:3306/hospital_db
```

On the next start the migration:

- adds the missing `auth_sessions` and `hospital_settings` tables,
- adds profile/status/staff-id/working-hour columns to `doctors`, `patients`, `appointments`,
  `consultations` and `users`,
- widens the status enums,
- assigns staff IDs (`DR-0001`, …),
- backfills the link between legacy appointments and `doctor_patients`,
- creates an administrator because the dump only has placeholder hashes.

Existing rows are kept. Accounts with placeholder hashes (`test_hash_*`) cannot sign in; see
[Legacy accounts](#legacy-accounts).

### C. Restore a backup

```bat
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS hospital_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
mysql -u root -p hospital_db < backup.sql
```

A `mysqldump` backup contains `DROP TABLE IF EXISTS` for every table it covers. Restoring it replaces
the current contents of those tables — that is the point of a restore, but confirm you are restoring
into the intended database.

## Tables and relationships

```
users 1 ──── 0..1 doctors          (doctor profile; staff_id like DR-0001)
users 1 ──── *    auth_sessions    (hashed tokens, expiry)
doctors * ── * patients            via doctor_patients (composite PK)
doctor_patients 1 ── * appointments (appointment's (doctor_id, patient_id) FKs this table)
appointments 1 ──── 0..1 consultations (unique appointment_id)
hospital_settings                  (key/value public hospital details)
```

| Table | Key columns | Purpose |
| --- | --- | --- |
| `users` | `email` unique | Staff accounts: password hash, role (`ADMIN`/`DOCTOR`), active flag, name, phone, `password_changed_at` |
| `doctors` | `user_id` unique, `staff_id` unique | Specialty, office, status (`active`/`leave`/`inactive`), working days, hours, visit length |
| `patients` | — | Names, birth date, gender, phone, address. The public identifier `HH-000001` is derived from the id |
| `doctor_patients` | `(doctor_id, patient_id)` PK | Which doctor may see which patient |
| `appointments` | `(doctor_id, patient_id)` FK | Date/time, status (`scheduled`/`completed`/`cancelled`/`missed`), reason |
| `consultations` | `appointment_id` unique | Diagnosis, treatment, notes, timestamp |
| `auth_sessions` | `token_hash` unique | SHA-256 of the session token and its expiry |
| `hospital_settings` | `setting_key` PK | Public details: name, phone, emergency number, email, address, parking, visiting times, visitor limit |

The composite foreign key from `appointments` to `doctor_patients` is what makes "a doctor can only
book their own patients" a database guarantee, not just an API check.

## Configuration

Connection settings live in `backend/.env` (or real environment variables, which take precedence):

| Variable | Default | Notes |
| --- | --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/hospital_db` | Connection flags are appended when the URL has no `?` |
| `DB_USER` | `root` | Use a dedicated user outside development |
| `DB_PASSWORD` | empty | Never commit it |
| `AUTO_MIGRATE` | `true` | Apply the additive migration on start |

Recommended dedicated account for a real deployment (least privilege):

```sql
CREATE USER 'haven_app'@'localhost' IDENTIFIED BY 'a-strong-password';
GRANT SELECT, INSERT, UPDATE, DELETE ON hospital_db.* TO 'haven_app'@'localhost';
-- Add CREATE on the first start only if you want the API to create tables:
-- GRANT CREATE ON hospital_db.* TO 'haven_app'@'localhost';
FLUSH PRIVILEGES;
```

## Migrations

There are no `.sql` migration files to apply by hand. `backend/src/config/DatabaseMigrator.java` runs
on every start when `AUTO_MIGRATE=true` and is **additive and idempotent**:

- `CREATE DATABASE IF NOT EXISTS`, `CREATE TABLE IF NOT EXISTS`;
- adds missing columns (checked against `information_schema`) with defaults;
- widens enum values;
- backfills `doctors.staff_id` (`DR-0001`, `DR-0002`, …);
- adds missing indexes;
- `INSERT IGNORE`s default settings;
- creates/unlocks the configured administrator only when no usable administrator exists, and renames
  the default "Hospital Administrator" title to "Administrator".

It never drops tables, columns or rows and never overwrites an existing profile or password. See
[`migrations/README.md`](migrations/README.md).

You can run the migration without starting the HTTP server:

```bat
cd backend
java -cp "out;lib/*" tool.DbSetup
```

## Backup and restore

### Backup

```bat
:: schema + data
mysqldump -u root -p --single-transaction --routines --triggers hospital_db > backup-hospital.sql
```

`--single-transaction` gives a consistent InnoDB snapshot without locking the tables.

### Restore

```bat
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS hospital_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
mysql -u root -p hospital_db < backup-hospital.sql
```

Always test a restore on another database name first if the data matters.

### Scheduled backups (Windows example)

```bat
schtasks /create /tn "Hospital DB backup" /tr "cmd /c mysqldump -u root -pYOURPASSWORD --single-transaction hospital_db > C:\backups\hospital-%DATE%.sql" /sc daily /st 02:00
```

Putting a password on the command line is convenient but visible; use a MySQL option file
(`--defaults-extra-file`) instead when the machine is shared.

## Legacy accounts

The original dump stores `test_hash_admin`, `test_hash_ahmed` and `test_hash_sara` as password
hashes — placeholders, not real hashes. Those accounts (`admin@hospital.com`, `ahmed@hospital.com`,
`sara@hospital.com`) cannot sign in.

Recovery options:

1. **Administrator UI:** sign in as a working administrator → **Accounts** → **Reset password**.
2. **Command line** (works even when locked out):

   ```bat
   cd backend
   java -cp "out;lib/*" tool.SetPassword ahmed@hospital.com "NewPassword123"
   ```

   This writes a PBKDF2 hash and revokes that account's sessions.
3. **List affected accounts:** `java -cp "out;lib/*" tool.DbSetup`.

The bootstrap administrator (`ADMIN_EMAIL`, default `admin@haven.local`) is created automatically when
no administrator has a usable hash, so a fresh install always has a way in. Set `ADMIN_PASSWORD` in
`backend/.env` before the first start to choose it; otherwise a random one is printed once.

## Common database errors

| Error | Meaning / fix |
| --- | --- |
| `Access denied for user 'x'@'localhost'` | Wrong credentials or missing grant; check `DB_USER`/`DB_PASSWORD` and the user's host. |
| `Unknown database 'hospital_db'` | Create it (least-privilege user cannot). |
| `Public Key Retrieval is not allowed` | Keep `allowPublicKeyRetrieval=true` (added by default when `DB_URL` has no query string). |
| `Cannot add or update a child row` | A referenced doctor/patient does not exist — the API normally prevents this; check the ids. |
| `Duplicate entry` | Unique email/staff id; the API returns 409 for these. |
| `Data too long for column` | Input passed validation but exceeds a column: report it, do not enlarge columns blindly. |
| `Too many connections` | The API pool is small; usually another client exhausted MySQL's limit. |

## Data handling notes

- There is no delete endpoint for patients or doctors in the application: doctors are deactivated and
  records are kept. Deleting rows directly can break the composite foreign keys.
- Consultations are tied to a completed appointment and are unique per appointment.
- `auth_sessions` can be pruned safely at any time; expired rows are ignored and removed on login.
  Doing so signs everyone out.
- Never share a dump of this database publicly: even though passwords are hashed, it contains patient
  names, phones and clinical notes.
