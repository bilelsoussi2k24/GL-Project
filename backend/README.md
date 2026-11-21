# Haven Hospital — Java backend

REST API for the Haven Hospital Management System. It owns authentication, authorization, validation
and every SQL statement. The React app talks only to this API; the browser never reaches MySQL.

There is no Maven/Gradle build: the API compiles with `javac` against two jars in `lib/`. That keeps the
project runnable with nothing but a JDK and MySQL.

## Requirements

| Item | Version | Notes |
| --- | --- | --- |
| JDK | **17 or newer** | Compiled with `--release 17`; tested on JDK 27 |
| MySQL Server | 8.0+ | See [`../database/README.md`](../database/README.md) |
| Free port | 8080 by default | Configurable with `PORT` |

Dependencies (committed in `lib/`, no download step):

| Jar | Version | Used for |
| --- | --- | --- |
| `gson-2.13.2.jar` | 2.13.2 | JSON parsing and responses |
| `mysql-connector-j-26.7.0.jar` | 26.7.0 | JDBC driver and connection metadata |

## Configure

```bat
cd backend
copy .env.example .env
```

| Variable | Default | Meaning |
| --- | --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/hospital_db` | JDBC URL; flags are appended when absent |
| `DB_USER` | `root` | MySQL user |
| `DB_PASSWORD` | *(empty)* | MySQL password |
| `BIND_ADDRESS` | `127.0.0.1` | Listening interface |
| `PORT` | `8080` | HTTP port (`0` or invalid falls back to 8080) |
| `CORS_ORIGINS` | localhost/127.0.0.1 on 5173 and 3000 | Comma-separated allowlist |
| `SESSION_HOURS` | `12` | Session lifetime |
| `ADMIN_EMAIL` | `admin@haven.local` | Bootstrap administrator email |
| `ADMIN_PASSWORD` | random, printed once | Bootstrap administrator password |
| `AI_SERVICE_URL` | `http://127.0.0.1:8000/api/chat` | Python assistant endpoint |
| `AI_TIMEOUT_SECONDS` | `25` | Assistant timeout |
| `AUTO_MIGRATE` | `true` | Run the add-only migration on start |

Real environment variables override `.env`. `.env` is ignored by Git.

## Compile and run

Windows:

```bat
cd backend
build.bat
run.bat
```

Linux / macOS / Git Bash:

```bash
cd backend
./build.sh
./run.sh
```

Direct commands (what the scripts run):

```bat
javac -encoding UTF-8 -d out -cp "lib/*" <all .java files under src>
java -cp "out;lib/*" Main
```

On Linux/macOS the classpath separator is `:`. In Git Bash on Windows use
`MSYS_NO_PATHCONV=1 java -cp "out;lib/*" Main` so the `;` is not rewritten.

`run.bat`/`run.sh` build first when `out/Main.class` is missing. If Command Prompt reports that
`build.bat` is not recognized, prefix it: `.\build.bat` (some environments disable running programs
from the current directory).

Expected output:

```text
Database 'hospital_db' is ready.
Haven Hospital API listening on http://127.0.0.1:8080
Allowed browser origins: http://localhost:5173, ...
```

Verify:

```bash
curl http://localhost:8080/api/health
# {"status":"ok","database":"ok (MySQL 8.0.46)","time":"..."}
```

The process migrates the schema, creates the first administrator if none can sign in, then serves
`/api/*`. `Ctrl+C` stops it.

## Architecture

```text
src/
├── Main.java              entry point: run migration, then start the server
├── api/
│   ├── HospitalServer     routes, access levels, handlers, JSON shapes, dispatch/error mapping
│   ├── Http               body parsing, validation helpers, query params, JSON responses
│   ├── CorsFilter         origin allowlist and OPTIONS preflight
│   ├── AssistantClient    server-side call to the Python service, error translation
│   └── ApiException       error carrying an HTTP status
├── config/
│   ├── AppConfig          environment/.env loading (environment wins)
│   ├── DatabaseConnection connection + health status
│   └── DatabaseMigrator   add-only schema, settings seed, admin bootstrap
├── dao/                   UserDAO, DoctorDAO, PatientDAO, AppointmentDAO,
│                          ConsultationDAO, SessionDAO, SettingsDAO
├── model/                 User, Doctor, Patient, Appointment, Consultation
├── security/
│   ├── PasswordHasher     PBKDF2-HMAC-SHA256, SHA-256 token hashing, strength rule
│   ├── AuthService        login, throttling, sessions, password change
│   └── Session            authenticated caller
└── tool/
    ├── DbSetup            run migration and report unusable accounts
    └── SetPassword        set a password from the command line
```

- **Routes** are declared once in `HospitalServer.routes()` with an access level (`null`, `AUTH`,
  `ADMIN`, `DOCTOR`). The dispatcher authenticates and authorizes before the handler runs.
- **DAOs** hold all SQL, use prepared statements, and include the doctor id in the `WHERE` clause for
  doctor-owned data.
- **Validation** lives in `Http`; handlers stay small. Errors are translated centrally: `ApiException`,
  SQL constraint violations → 409, connection failures → 503, everything else → 500.
- **Migration** is additive and idempotent; details in [`../database/README.md`](../database/README.md).

Request/response lifecycle diagrams and the data model are in
[`../docs/architecture.md`](../docs/architecture.md).

## API endpoints

Full reference with payloads: [`../docs/api-documentation.md`](../docs/api-documentation.md).

| Area | Endpoints |
| --- | --- |
| Public | `GET /api/health`, `GET /api/public/hospital`, `POST /api/assistant/chat` |
| Auth | `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`, `PATCH /api/auth/account`, `POST /api/auth/password` |
| Doctor | `GET/PATCH /api/doctors/me`, `GET /api/doctors/me/patients[...]`, `GET/POST/PUT/PATCH /api/doctors/me/appointments[...]`, `GET/POST/PUT /api/doctors/me/consultations[...]` |
| Admin | `GET /api/admin/stats`, `GET /api/admin/activity`, doctors CRUD + status, users + activation + password reset, patients + assignment, appointments, consultations, settings |

Access rules: `/admin/*` requires an `ADMIN` session; `/doctors/me/*` requires an active `DOCTOR`
profile. Wrong role → 403; another doctor's record → 404.

## Security model

- PBKDF2-HMAC-SHA256, 210 000 iterations, random 16-byte salt, stored as
  `pbkdf2_sha256$iterations$salt$hash`.
- Login returns a random token; `auth_sessions` stores only its SHA-256 hash and expiry.
- Logout deletes the row; password changes revoke all other sessions; deactivation revokes all
  sessions immediately.
- Failed logins: 8 per email + client per 10 minutes → 429.
- All responses use `Cache-Control: no-store`; CORS is an explicit allowlist.
- The Gemini key never reaches this process: the AI call is forwarded to `AI_SERVICE_URL`.

## Command-line tools

```bat
cd backend
java -cp "out;lib/*" tool.DbSetup
java -cp "out;lib/*" tool.SetPassword ahmed@hospital.com "NewPassword123"
```

- `DbSetup` applies the migration and lists accounts that still have legacy placeholder hashes.
- `SetPassword` writes a PBKDF2 hash, activates the account and revokes its sessions. Passwords must
  satisfy the same strength rule as the API.

On Linux/macOS use `out:lib/*`.

## Tests

End-to-end API checks (run against a live backend):

```bat
python tests\api_test.py
```

Environment: `API_BASE`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`. Uniquely named records make it rerunnable.

Unit checks for hashing:

```bat
javac -encoding UTF-8 -d .tmp\test-classes -cp "backend\out" tests\java\PasswordHasherTest.java
java -cp ".tmp\test-classes;backend\out" PasswordHasherTest
```

From `backend/`, adjust the relative paths (`../tests/...`, `../.tmp/...`).

## Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| `Access denied for user` | Wrong `DB_USER`/`DB_PASSWORD`, or the user may not connect from this host. |
| `Unknown database 'hospital_db'` | Create it manually; the account lacks `CREATE`. |
| `Communications link failure` | MySQL is not running or listens elsewhere. |
| `Public Key Retrieval is not allowed` | Keep the default `DB_URL` (it appends `allowPublicKeyRetrieval=true`). |
| `Address already in use` | Port taken: change `PORT` and the frontend proxy target, or stop the other process. |
| `ClassNotFoundException: Main` | Wrong separator (`;` on Windows, `:`) or Git Bash path conversion (`MSYS_NO_PATHCONV=1`). |
| 503 on data routes | The database is unreachable; `/api/health` shows the reason. |
| Everyone gets 401 | Sessions expired (`SESSION_HOURS`) or the database was restored from an older backup without `auth_sessions`. |
| Doctor cannot sign in after a legacy import | Placeholder hash: reset the password (`tool.SetPassword` or Accounts). |
| Assistant 503/502/504 | Python service offline / Gemini error / timeout; check its console. |
| Startup says "Created the first administrator" repeatedly | The configured admin still has an unusable hash; check `ADMIN_EMAIL` matches the account you expect. |

## Code conventions

- 4 spaces, one class per file, package matches folder.
- Handlers validate then delegate to a DAO; no SQL in `api/`.
- Responses are camelCase maps/records; timestamps are ISO strings.
- New endpoints need: DAO method, validation, route with an access level, and a test case in
  `tests/api_test.py`.
