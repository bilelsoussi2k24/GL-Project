# Setup guide

A slow, explicit version of the quick start in the root README. Written for Windows and tested on
Windows 11 with Git Bash / Command Prompt; Linux and macOS differences are noted inline.

## 0. What you need

| Component | Minimum | Notes |
| --- | --- | --- |
| JDK | 17 | The backend compiles with `--release 17`; a newer JDK is fine |
| MySQL Server | 8.0 | The schema uses `utf8mb4_0900_ai_ci` and `CHECK`-free enums |
| Node.js | 20.19 or 22.12+ | Vite 8 requirement; LTS 22 recommended |
| Python | 3.10+, 64-bit | Only for the optional AI assistant. A 32-bit interpreter cannot install the `cryptography` dependency chain of `google-genai`. |
| Git | any | For cloning |

Disk: about 500 MB with `node_modules` and a Python virtual environment.

Open a terminal in the repository root for every command unless a step says otherwise. All commands
work in Command Prompt, PowerShell and Git Bash, except where noted.

## 1. Verify the tools

```bat
java -version
javac -version
mysql --version
node -v
npm -v
python --version
```

Expected, approximately:

```text
openjdk version "17" ...        (or newer)
javac 17 ...                    (same major version as java)
mysql  Ver 8.0.4x ...
v22.x.x                         (or >= 20.19)
10.x.x                          (npm 9+ is fine)
Python 3.12.x                   (or 3.10+)
```

| Problem | Fix |
| --- | --- |
| `'java' is not recognized` | Install a JDK (Temurin, Oracle, Microsoft OpenJDK), then add `...\jdk\bin` to `PATH` and reopen the terminal. |
| `javac` missing but `java` works | You installed a JRE. Install a JDK. |
| `'mysql' is not recognized` | Add MySQL's `bin` directory to `PATH` or use MySQL Workbench / the full path to `mysql.exe`. |
| Node reports < 20.19 | Install the current LTS from <https://nodejs.org>. |

## 2. Clone

```bat
git clone https://github.com/bilelsoussi2k24/GL-Project.git
cd GL-Project
```

Expected: `frontend/`, `backend/`, `backend_ai/`, `database/`, `docs/`, `tests/`, `README.md`.

If the folder already exists, skip cloning. Never delete untracked files you did not create.

## 3. Start MySQL and create the database

Make sure the MySQL service is running:

```bat
:: Windows (administrator terminal), if it is not running
net start mysql
```

Create the schema (the API can also do this when its user has the privilege):

```bat
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS hospital_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
```

Expected: no output. Verify:

```bat
mysql -u root -p -e "SHOW DATABASES LIKE 'hospital_db';"
```

```text
+------------------------+
| Database (hospital_db) |
+------------------------+
| hospital_db            |
+------------------------+
```

> Do **not** import `database/hospital_db.sql` here if the database already contains data: that dump
> drops the six tables it defines. Read [`../database/README.md`](../database/README.md) first.

## 4. Configure the backend environment

```bat
cd backend
copy .env.example .env
```

Linux/macOS: `cp .env.example .env`.

Edit `backend/.env`:

```ini
DB_URL=jdbc:mysql://localhost:3306/hospital_db
DB_USER=your-mysql-user
DB_PASSWORD=your-mysql-password

ADMIN_EMAIL=admin@haven.local
ADMIN_PASSWORD=choose-a-strong-development-password
```

Rules that matter:

- `DB_URL` may omit the query string; the API appends `useSSL=false`,
  `allowPublicKeyRetrieval=true`, `serverTimezone=UTC` and `characterEncoding=utf8`.
- `ADMIN_PASSWORD` is used **only** when no usable administrator exists. If it is empty, a random
  password is generated and printed once. It is never stored in plain text.
- Passwords must be at least 8 characters and contain a letter and a digit.
- `.env` is ignored by Git; never commit it.

Optional: change `PORT`, `BIND_ADDRESS`, `SESSION_HOURS`, `CORS_ORIGINS`, `AI_SERVICE_URL`.

## 5. Install frontend dependencies

```bat
cd ..\frontend
npm ci
```

`npm ci` uses `package-lock.json` exactly and fails if the lockfile and `package.json` disagree. Use
`npm install` only when you mean to update dependencies.

Expected: a few hundred packages installed without vulnerabilities that block installation, and a
`node_modules` folder.

| Error | Fix |
| --- | --- |
| `EBADENGINE` / "Vite requires Node.js version 20.19+" | Upgrade Node. |
| `EACCES` (Linux) | Do not use `sudo npm`; fix ownership of the project folder instead. |
| Corporate proxy errors | Configure `npm config set proxy`/`https-proxy`, then retry. |

## 6. Build and start the Java backend

```bat
cd ..\backend
build.bat
```

Expected:

```text
Built to C:\...\GL-Project\backend\out
```

Then:

```bat
run.bat
```

Expected (first start):

```text
Database 'hospital_db' is ready.
Haven Hospital API listening on http://127.0.0.1:8080
Allowed browser origins: http://localhost:5173, http://127.0.0.1:5173, ...
```

and, when no administrator existed, a small block with the sign-in email and either
"the ADMIN_PASSWORD value from your configuration" or the generated password.

In a second terminal, verify:

```bat
curl http://localhost:8080/api/health
```

Expected: `{"status":"ok","database":"ok (MySQL 8.0.46)","time":"..."}`.

| Error | Cause / fix |
| --- | --- |
| `Access denied for user` | Wrong `DB_USER`/`DB_PASSWORD`, or the user cannot connect from `localhost`. |
| `Unknown database 'hospital_db'` | Create it manually (step 3); the account lacks `CREATE`. |
| `Communications link failure` | MySQL is not running or listens on another port. |
| `Address already in use` | Port 8080 is taken: `netstat -ano | findstr :8080`, stop that process, or change `PORT`. |
| `ClassNotFoundException: Main` in Git Bash | Use `MSYS_NO_PATHCONV=1 java -cp "out;lib/*" Main`. |
| `No suitable driver` | The jars in `backend/lib` are missing; restore them from Git. |

Leave this terminal running. `Ctrl+C` stops the API.

## 7. Start the frontend

```bat
cd ..\frontend
npm run dev
```

Expected:

```text
VITE v8.x.x  ready in 420 ms
➜  Local:   http://localhost:5173/
```

Open <http://localhost:5173>. You should see the public hospital page with values from MySQL.

Sign in at <http://localhost:5173/login> with the administrator email and password from step 4.

| Error | Fix |
| --- | --- |
| Page loads but "Cannot reach the server" on login | The Java API is not running on 8080 or crashed; check its terminal. |
| `EADDRINUSE :5173` | Another Vite instance is running; stop it or run `npm run dev -- --port 5174`. |
| White page | Check the terminal for a Vite/JS error, or hard-refresh (`Ctrl+Shift+R`). |

## 8. Start the AI assistant (optional)

```bat
cd ..\backend_ai
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
```

Linux/macOS: `python3 -m venv venv`, `source venv/bin/activate`, `cp .env.example .env`.

Put a key in `backend_ai/.env`:

```ini
GEMINI_API_KEY=your-google-ai-studio-key
GEMINI_MODEL=gemini-2.5-flash
```

Run:

```bat
uvicorn main:app --reload --port 8000
```

Expected:

```text
INFO:     Uvicorn running on http://127.0.0.1:8000 (Press CTRL+C to quit)
INFO:     Application startup complete.
```

Verify: `curl http://127.0.0.1:8000/api/health` →
`{"status":"ok","configured":true,"model":"gemini-2.5-flash"}`.

Try a question:

```bat
curl -X POST http://127.0.0.1:8000/api/chat -H "Content-Type: application/json" -d "{\"message\":\"What are the visiting hours?\"}"
```

| Error | Fix |
| --- | --- |
| `ModuleNotFoundError` | The virtual environment is not activated. |
| `configured: false` | `GEMINI_API_KEY` is empty or `.env` is not in `backend_ai/`. |
| 502 from the assistant | Check the Python console: invalid key, quota, or unavailable model. |
| Port 8000 busy | Run on another port and set `AI_SERVICE_URL` in `backend/.env` to match. |

No key? Skip the service; the assistant button shows "The hospital assistant is offline".

## 9. Verify the whole system

1. `http://localhost:8080/api/health` → `database: ok`.
2. `http://localhost:5173` → hospital name, address, phone and hours from the database.
3. Sign in as admin → `/admin` shows totals (zeros are fine on a fresh database).
4. **Doctors → Add doctor** with an email and a password you choose.
5. Sign out, sign in as that doctor → `/doctor` opens.
6. **New appointment** for a patient (register the patient first in the admin dashboard and assign
   them to the doctor), mark it seen, add consultation notes.
7. Refresh: the appointment and notes are still there.
8. Deactivate the doctor as admin → the doctor session stops working; reactivate to restore.

Automated checks:

```bat
python tests\api_test.py
javac -encoding UTF-8 -d .tmp\test-classes -cp "backend\out" tests\java\PasswordHasherTest.java
java -cp ".tmp\test-classes;backend\out" PasswordHasherTest
```

## 10. Run it in production mode (optional)

```bat
cd frontend
npm run build
npm start
```

Open <http://localhost:3000>. `server.js` serves `dist/`, falls back to `index.html` for client routes
and proxies `/api` to `API_URL` (default `http://localhost:8080`). Override the port or API origin with
`PORT` and `API_URL` environment variables.

## Resetting the local environment

| Goal | Action |
| --- | --- |
| Reset the administrator password | `cd backend` then `java -cp "out;lib/*" tool.SetPassword admin@haven.local "NewPassword123"` |
| Recover accounts from the legacy dump | `java -cp "out;lib/*" tool.DbSetup` lists accounts with placeholder hashes; reset them in **Accounts** or with `tool.SetPassword` |
| Start from an empty database | Back up if needed, then drop and recreate `hospital_db` (the API recreates the schema on start) |
| Delete frontend dependencies | Delete `frontend/node_modules` and run `npm ci` |
| Delete Python environment | Delete `backend_ai/venv` and recreate it |

## Order of startup and shutdown

Start: MySQL → Java API → frontend → (optional) AI service.
Stop: frontend (`Ctrl+C`) → AI service → Java API. MySQL can keep running.

Logs: the Java API writes to its terminal (startup and errors), Vite to its terminal (compile errors),
Uvicorn to its terminal (Gemini errors). Nothing is written to files unless you redirect it.
