# Sprint 10 — Full code review, fixes and verification

Date: 2026-11-21 · Branch: `fix/full-code-review` (based on `main` at `9a3627e`)

Scope: every tracked source, configuration, SQL, script, test and documentation file, plus three new
files added by this review. Binaries (two JARs and one PNG) were checked for presence and version, not
audited as source.

## Method

1. Read all 105 tracked files (102 text files fully; 3 binaries inspected).
2. Compared every frontend API call with its Java route, method, field names, status codes and null
   handling.
3. Reviewed authentication, authorization, session lifecycle, password handling, rate limiting, SQL
   transaction boundaries, migration behaviour and error responses.
4. Reproduced defects with tests before/after the fix where practical.
5. Rebuilt, restarted and re-ran the complete suite against MySQL 8.0.46.

## File-by-file checklist

Status values: **No issue** = reviewed, nothing to fix. **Fixed** = reviewed, defect corrected.
**Not verifiable** = cannot be fully exercised in this environment.

### Backend — API and configuration

| File | Status | Finding / action |
| --- | --- | --- |
| `backend/src/Main.java` | No issue | Migration failure is logged; server still starts and reports unavailable DB. |
| `backend/src/api/ApiException.java` | No issue | Status/message mapping is correct. |
| `backend/src/api/AssistantClient.java` | No issue | HTTP/1.1 pin, timeouts and error translation verified. |
| `backend/src/api/CorsFilter.java` | No issue | Allowlist, preflight and `Vary: Origin` correct. |
| `backend/src/api/HospitalServer.java` | Fixed | Password parsing, trusted client identity, atomic patient/doctor/account changes, session revocation, health payload, appointment date range, consultation ownership, cached routes. |
| `backend/src/api/Http.java` | Fixed | Added exact-password parser; `X-Forwarded-For` only with `TRUST_PROXY`; malformed query strings now 400. |
| `backend/src/config/AppConfig.java` | Fixed | Added `TRUST_PROXY`; locale-safe admin email normalisation. |
| `backend/src/config/DatabaseConnection.java` | Fixed | Health returns `ok`/`unavailable`; details go to the server log only. |
| `backend/src/config/DatabaseMigrator.java` | Fixed | Advisory lock; active-admin check; never resets a working admin; never touches a doctor at `ADMIN_EMAIL`; reactivates inactive admin without reset. |
| `backend/src/dao/UserDAO.java` | Fixed | Transactional password change/reset + revocation, profile+email update, activation with doctor sync and last-admin protection; removed dead methods. |
| `backend/src/dao/DoctorDAO.java` | Fixed | Transactional create/update/status/profile with session revocation; inactive doctors created inactive atomically. |
| `backend/src/dao/PatientDAO.java` | Fixed | Added transactional create-with-assignment (no orphan patients). |
| `backend/src/dao/SessionDAO.java` | Fixed | Removed unused per-user delete helper (moved into transactional paths). |
| `backend/src/dao/AppointmentDAO.java` | No issue | Prepared statements; `INSERT ... SELECT`/join enforce patient ownership. |
| `backend/src/dao/ConsultationDAO.java` | No issue | Ownership enforced in SQL for find/update; history intentionally visible to any doctor assigned to the patient. |
| `backend/src/dao/SettingsDAO.java` | No issue | Prepared statements and idempotent upsert. |
| `backend/src/model/Appointment.java` | No issue | Immutable data holder. |
| `backend/src/model/Consultation.java` | No issue | Immutable data holder. |
| `backend/src/model/Doctor.java` | No issue | Immutable data holder with derived full name/account flag. |
| `backend/src/model/Patient.java` | No issue | Immutable data holder with derived full name. |
| `backend/src/model/User.java` | No issue | Immutable data holder. |
| `backend/src/security/AuthService.java` | Fixed | Bounded per-address and per-email+address limiters; exact password use; transactional password change/revocation. |
| `backend/src/security/PasswordHasher.java` | No issue | PBKDF2 parameters, constant-time compare, legacy detection and 200-character bound verified. |
| `backend/src/security/RateLimiter.java` | **New** | Thread-safe sliding window with pruning, periodic sweep and hard key cap. |
| `backend/src/security/Session.java` | No issue | Role helpers only. |
| `backend/src/tool/DbSetup.java` | No issue | Runs migration and reports legacy hashes. |
| `backend/src/tool/SetPassword.java` | Fixed | Password reset + revocation atomic; no longer silently activates; documentation corrected. |

### Backend — scripts and templates

| File | Status | Finding / action |
| --- | --- | --- |
| `backend/build.bat` | No issue | Works with `.\build.bat` (documented). |
| `backend/build.sh` | No issue | `set -euo pipefail`, correct classpath. |
| `backend/run.bat` | No issue | Builds when `out/Main.class` is missing, then starts. |
| `backend/run.sh` | No issue | Same behaviour with `exec`. |
| `backend/.env.example` | Fixed | Added `TRUST_PROXY` with explanation. |
| `backend/lib/gson-2.13.2.jar` | Not verifiable | Binary dependency; version and loading verified by compilation/runtime. |
| `backend/lib/mysql-connector-j-26.7.0.jar` | Not verifiable | Binary dependency; verified by live database access. |

### AI service

| File | Status | Finding / action |
| --- | --- | --- |
| `backend_ai/main.py` | No issue | Key stays server-side; timeout, 503/502 mapping, message length and no medical advice in prompt. |
| `backend_ai/hospital_data.py` | No issue | Development sample documents only. |
| `backend_ai/test_models.py` | Not verifiable | Performs a real Gemini call; requires a key, intentionally not in the automated suite. |
| `backend_ai/test_pinecone.py` | Not verifiable | Optional Pinecone experiment; no Pinecone key available. Statically reviewed. |
| `backend_ai/upload_knowledge.py` | Not verifiable | Optional RAG upload; requires Pinecone + model download. Statically reviewed; key check present. |
| `backend_ai/requirements.txt` | Fixed | Direct dependencies pinned to the versions verified in a clean environment. |
| `backend_ai/requirements-rag.txt` | No issue | Optional extras only, separated from the chat service. |
| `backend_ai/.env.example` | No issue | Placeholders only; no secret. |
| `backend_ai/.gitignore` | No issue | Ignores `.env`, venvs and caches. |
| `backend_ai/README.md` | No issue | Matches implementation, including the prompt-is-not-a-guarantee warning. |

### Database

| File | Status | Finding / action |
| --- | --- | --- |
| `database/hospital_db.sql` | No issue | Destructive legacy dump; warning retained and reinforced. |
| `database/schema.sql` | No issue | Matches the current additive schema and settings seed. |
| `database/README.md` | Fixed | Removed a non-existent backfill claim; documented bootstrap rules, advisory lock and verified migration results. |
| `database/migrations/README.md` | Fixed | Documented the lock, idempotency and administrator bootstrap rules. |

### Frontend

| File | Status | Finding / action |
| --- | --- | --- |
| `frontend/index.html` | No issue | Correct mount point, favicon and font preconnect. |
| `frontend/vite.config.js` | No issue | `/api` proxy for dev and preview. |
| `frontend/server.js` | No issue | Static serving, SPA fallback, traversal guard and API proxy with 502 handling. |
| `frontend/package.json` | No issue | Scripts and dependency set match usage. |
| `frontend/package-lock.json` | Not verifiable | Validated by `npm ci`; not read line by line. |
| `frontend/.oxlintrc.json` | No issue | React hooks and export rules enabled. |
| `frontend/.gitignore` | No issue | Ignores dependencies/build output. |
| `frontend/public/favicon.svg` | No issue | Static asset. |
| `frontend/src/main.jsx` | No issue | Router + reduced-motion config. |
| `frontend/src/App.jsx` | No issue | Lazy routes, role guards, 401 redirect handler. |
| `frontend/src/index.css` | No issue | Design tokens and animations only. |
| `frontend/src/config/hospital.js` | No issue | Fallback content matches the documented placeholders. |
| `frontend/src/services/api.js` | No issue | Relative `/api`, bearer token, 401 clearing, safe messages. |
| `frontend/src/services/auth.js` | No issue | Stores server session, sends the exact password, logs out server-side. |
| `frontend/src/services/hospital.js` | No issue | Public settings cache + refresh event. |
| `frontend/src/utils/date.js` | No issue | ISO/date helpers consistent with the API formats. |
| `frontend/src/hooks/ripple.js` | No issue | Self-removing ripple element. |
| `frontend/src/hooks/useCountUp.js` | No issue | Respects reduced motion; cancels animation frame. |
| `frontend/src/pages/VisitUs.jsx` | No issue | Public data only; visiting-hours logic and copy feedback correct. |
| `frontend/src/pages/StaffLogin.jsx` | No issue | Role selector, client validation, server-enforced role. |
| `frontend/src/components/ChangePassword.jsx` | No issue | Confirmation, strength meter, server error placement. |
| `frontend/src/components/DashShell.jsx` | No issue | Responsive drawer, active-link pill, logout. |
| `frontend/src/components/DataGate.jsx` | No issue | Loading/error/retry states. |
| `frontend/src/components/Footer.jsx` | No issue | Public links and hospital name. |
| `frontend/src/components/Header.jsx` | No issue | Scroll behaviour and progress bar. |
| `frontend/src/components/Highlight.jsx` | No issue | Null-safe for all current call sites (all pass strings). |
| `frontend/src/components/HospitalAssistant.jsx` | No issue | Public gateway call, loading/error states, 1000-character input cap. |
| `frontend/src/components/Icons.jsx` | No issue | Presentational SVGs. |
| `frontend/src/components/Modal.jsx` | No issue | Focus trap, Escape, backdrop, scroll lock, focus restore. |
| `frontend/src/components/RequireRole.jsx` | No issue | Navigation guard; server remains authoritative. |
| `frontend/src/components/Toast.jsx` | No issue | Timed toast with optional action/undo. |
| `frontend/src/components/ui.jsx` | No issue | UI primitives; form/label/error wiring correct. |
| `frontend/src/admin/AdminData.jsx` | No issue | Loads doctors/stats/activity/own account, refreshes after mutations. |
| `frontend/src/admin/AdminLayout.jsx` | No issue | Shared doctor modal and navigation. |
| `frontend/src/admin/Overview.jsx` | No issue | Stats, activity and leave actions match the API. |
| `frontend/src/admin/Doctors.jsx` | No issue | Search, status toggle, deactivate confirmation and undo. |
| `frontend/src/admin/DoctorModal.jsx` | No issue | Create/edit fields match `POST/PUT /admin/doctors`. |
| `frontend/src/admin/Patients.jsx` | No issue | Register/edit/assign; duplicate assignment is idempotent. |
| `frontend/src/admin/Users.jsx` | No issue | Account creation, activation, password reset match the API. |
| `frontend/src/admin/Settings.jsx` | No issue | Hospital details + account + password; validates times/visitors. |
| `frontend/src/doctor/DoctorData.jsx` | No issue | Doctor identity comes from the session; API mapping and null handling correct. |
| `frontend/src/doctor/DoctorLayout.jsx` | No issue | Shared appointment modal and navigation. |
| `frontend/src/doctor/Today.jsx` | No issue | Day view, status updates, consultation handoff. |
| `frontend/src/doctor/Appointments.jsx` | No issue | Search, cancel/undo, consultation access. |
| `frontend/src/doctor/AppointmentModal.jsx` | Fixed | Removed a phone field whose edits were never sent to the server. |
| `frontend/src/doctor/ConsultationModal.jsx` | No issue | Create/edit/history match the API. |
| `frontend/src/doctor/Profile.jsx` | No issue | Profile/hours validation and password change match the API. |

### Tests and repository configuration

| File | Status | Finding / action |
| --- | --- | --- |
| `tests/api_test.py` | Fixed | Expanded from 84 to 123 checks; added whitespace passwords, header spoofing, rollback, validation, revocation and ownership cases. |
| `tests/java/PasswordHasherTest.java` | No issue | Hashing, legacy and malformed-hash checks pass. |
| `tests/java/PasswordInputTest.java` | **New** | Exact whitespace preservation and rejection of empty/non-string/over-long passwords. |
| `tests/java/AdministratorBootstrapTest.java` | **New** | Seven bootstrap scenarios including two concurrent starts. |
| `.gitignore` | No issue | Secrets, build output, caches and local tooling ignored. |
| `.vscode/settings.json` | No issue | Source path, output path and referenced libraries correct. |

### Documentation

| File | Status | Finding / action |
| --- | --- | --- |
| `README.md` | Fixed | Health example, `TRUST_PROXY`, exact-password rules, two-tier throttling, new test commands and verified results. |
| `backend/README.md` | Fixed | Health payload, throttle rules, `TRUST_PROXY`, `SetPassword` behaviour, new tests. |
| `frontend/README.md` | Fixed | Corrected the data-provider description and scoped browser-verification claims to the sprint where they ran. |
| `docs/architecture.md` | Fixed | Throttle description and active-administrator bootstrap rule. |
| `docs/api-documentation.md` | Fixed | Safe health response, exact password rule, two-tier limits. |
| `docs/setup-guide.md` | Fixed | Health output, `TRUST_PROXY`, bootstrap and password rules. |
| `docs/images/architecture-diagram.png` | Not verifiable | Binary diagram; links resolve and Mermaid sources are in `README.md`/architecture. |
| `docs/code-review-sprint10.md` | **New** | This report. |

## Confirmed defects found

1. **Passwords were trimmed** by the shared string parser on login, doctor/account creation, admin
   reset and password change. A password with leading/trailing spaces could never be typed exactly.
2. **`X-Forwarded-For` was trusted unconditionally**, so rotating the header bypassed login and
   assistant rate limits.
3. **Rate-limit maps were unbounded** and the helper maps grew with each new email/address; the
   assistant map grew with every client.
4. **Doctor status changes did not revoke sessions.** `PATCH /admin/doctors/{id}/status` and
   `PUT /admin/doctors/{id}` set the account inactive (tokens were rejected while inactive) but left
   the session rows, so **reactivation made old tokens valid again**.
5. **Account deactivation did not update the doctor profile**: `PATCH /admin/users/{id}/active`
   deactivated the login while the doctor list still showed the doctor as active.
6. **Administrator bootstrap could reset a doctor's password**: if `ADMIN_EMAIL` matched a DOCTOR, the
   code changed that account's password and activated it, leaving no administrator. It also counted
   inactive administrators as usable and could race with a second instance.
7. **Patient creation was not atomic**: the patient row was inserted before the doctor id was
   validated, leaving an orphan patient when the doctor did not exist.
8. **Doctor update was partially applied**: the doctor row was written before the email conflict check
   and before the account status update, so a later failure left the two tables inconsistent.
9. **Inactive doctor creation was two statements** (profile then account), not atomic.
10. **Patient update accepted a future birth date** (create rejected it).
11. **Appointment update accepted dates outside 2000–2100** while create rejected them.
12. **`/api/health` leaked the MySQL version and raw SQL error text** (host/credentials context).
13. **A malformed query string returned 500** instead of a validation error.
14. **Consultation creation leaked another doctor's appointment state**: it returned 409 when a
    foreign appointment already had notes instead of 404.
15. **The appointment form exposed a phone field whose edits were silently discarded.**
16. **Password reset/change and profile/email updates used separate statements**, allowing partial
    application on failure.
17. **`SetPassword` did not update the account atomically with revocation**, and the README claimed it
    activated the account (it does not).
18. **AI dependencies were range-pinned**, so installs could drift between machines.
19. Documentation stated `database: ok (MySQL 8.0.46)` and claimed a migration backfill that does not
    exist.

## Fixes and changed files

| Defect | Files changed |
| --- | --- |
| 1 | `Http.java` (new `password()`), `HospitalServer.java`, `AuthService.java` |
| 2, 3 | `AppConfig.java`, `Http.java`, `AuthService.java`, `RateLimiter.java`, `HospitalServer.java` |
| 4, 5, 16 | `HospitalServer.java`, `DoctorDAO.java`, `UserDAO.java`, `AuthService.java`, `SessionDAO.java` |
| 6 | `DatabaseMigrator.java`, `UserDAO.java` |
| 7, 10 | `HospitalServer.java`, `PatientDAO.java` |
| 8, 9 | `HospitalServer.java`, `DoctorDAO.java` |
| 11 | `HospitalServer.java` |
| 12 | `DatabaseConnection.java` |
| 13 | `Http.java` |
| 14 | `HospitalServer.java` |
| 15 | `AppointmentModal.jsx` |
| 17 | `SetPassword.java`, `UserDAO.java`, `backend/README.md` |
| 18 | `backend_ai/requirements.txt` |
| 19 | `README.md`, `backend/README.md`, `database/README.md`, `database/migrations/README.md`, `docs/*` |

## Security improvements

- Passwords are treated as opaque strings: exact bytes, no trimming, 1–200 characters, JSON string
  required, used identically for login, creation, reset and change.
- Client identity defaults to the TCP peer. `X-Forwarded-For` is honoured only with
  `TRUST_PROXY=true`, using the right-most hop.
- Two login windows: 8 failures per email+address and 50 per address per 10 minutes. A global
  per-account lock is deliberately **not** used, because it would let anyone lock a legitimate user
  out; per-account protection is scoped to the source address.
- Limiter storage is bounded (10k/20k keys), pruned on access and swept periodically; concurrent
  access is synchronised per key.
- Deactivation revokes sessions and reactivation cannot revive them. Password changes keep the current
  session and revoke the others; admin reset revokes all.
- Multi-table operations are transactional: patient+assignment, doctor+account+status+sessions,
  password+revocation, profile+email, account activation + doctor status.
- Administrator bootstrap never resets a working password, never changes a role, ignores inactive
  admins when deciding whether an admin exists, and runs under a MySQL advisory lock.
- Public health output exposes only `status`, `database` (`ok`/`unavailable`) and time.
- Every endpoint still authenticates before the handler; doctor ids come only from the session; SQL
  uses prepared statements; responses remain `no-store` with an origin allowlist.

## Database migration results

Tested with the real `database/hospital_db.sql` and the development database on MySQL 8.0.46:

| Scenario | Result |
| --- | --- |
| Empty database | 8 tables created, settings seeded, active administrator created |
| Original legacy dump | 2 doctors, 3 patients, 1 appointment, 1 consultation before **and** after; staff IDs `DR-0001`/`DR-0002`; all 15 expected columns present; status enums widened to include `inactive`/`missed` |
| Already-migrated development database | 11 users, 10 doctors, 5 patients, 7 appointments, 6 consultations unchanged |
| Second run (idempotency) | No schema or data change |
| Concurrent starts | Serialised by `GET_LOCK`; exactly one administrator |

The legacy dump remains destructive and is still documented as import-only into an empty database.

## Verification results

| Check | Result |
| --- | --- |
| `javac` build | Pass |
| `build.bat` (`.\build.bat`) | Pass |
| `build.sh` | Pass |
| `javac --release 17` | Pass |
| `PasswordHasherTest` | Pass |
| `PasswordInputTest` | Pass |
| `AdministratorBootstrapTest` (7 scenarios) | Pass |
| `npm ci` | Pass (50 packages) |
| `npm run lint` | 0 errors, 4 informational `set-state-in-effect` hints |
| `npm run build` | Pass (Vite 8) |
| `pip install -r requirements.txt` in a clean venv | Pass (Python 3.15, 64-bit) |
| `python -m compileall .` | Pass |
| AI health without key | 200 `{configured:false}` |
| AI chat without key | 503 |
| AI empty message | 422 |
| `tests/api_test.py` | **123/123 passed on three consecutive runs** |
| Live Gemini answer | Not run (no key) |
| Browser click-through | Pass — public page, admin login + Overview/Doctors/Patients/Accounts/Settings, doctor login + Today/Appointments/Profile, appointment dialog, assistant offline state, logout; no console or network errors |

## Remaining issues and recommendations

1. **No live Gemini answer was verified**; the no-key path is verified and the Java proxy returns 503.
2. **Cross-browser and responsive visual review was not repeated.** The UI was clicked through in the
   bundled Chromium at desktop size; other browsers and small-screen layouts were not re-checked.
3. **The developer's MySQL on port 3306 was not used** (credentials unknown); tests ran on an isolated
   MySQL 8.0.46 at 127.0.0.1:3307.
4. **No global per-account login lock.** Distributed guessing from many addresses is slowed per address
   but cannot be blocked without creating a denial-of-service lever against users.
5. **Rate limits are in memory**, per instance, and reset on restart.
6. **Admin doctor/stats queries use N+1 counts.** Acceptable at this scale; a grouped query is the next
   optimisation if the doctor list grows.
7. **The migrator only knows the original dump and the current schema.** A hypothetical intermediate
   schema missing foreign keys/unique constraints is not repaired automatically.
8. **4 lint hints** remain (`set-state-in-effect` in fetch-on-mount effects); they are informational and
   were not suppressed.
9. **Production deployment needs TLS and a dedicated MySQL user**; the API itself listens on
   `127.0.0.1` by default.
10. `Highlight` assumes non-null text; current call sites always pass strings, so this is a latent risk,
    not a confirmed defect.

## Run commands (Windows)

```bat
:: 1. Database (MySQL 8 running on 3306)
cd backend
copy .env.example .env
:: set DB_USER, DB_PASSWORD, ADMIN_EMAIL, ADMIN_PASSWORD

:: 2. Backend
.\build.bat
.\run.bat

:: 3. Frontend
cd ..\frontend
npm ci
npm run dev

:: 4. AI service (optional)
cd ..\backend_ai
py -3.15 -m venv venv
venv\Scripts\python.exe -m pip install -r requirements.txt
venv\Scripts\python.exe -m uvicorn main:app --port 8000
```

Checks:

```bat
python tests\api_test.py
javac -encoding UTF-8 -d .tmp\test-classes -cp "backend\out;backend\lib\*" tests\java\PasswordHasherTest.java tests\java\PasswordInputTest.java tests\java\AdministratorBootstrapTest.java
java -cp ".tmp\test-classes;backend\out;backend\lib\*" PasswordHasherTest
java -cp ".tmp\test-classes;backend\out;backend\lib\*" PasswordInputTest
java -cp ".tmp\test-classes;backend\out;backend\lib\*" AdministratorBootstrapTest
```

## Credentials and data

No production credentials, API keys, session tokens or patient data were added to the repository. The
only environment files committed are `.env.example` templates with placeholders. Test records use
random suffixes and the bootstrap tests use disposable `haven_test_boot_*` databases.
