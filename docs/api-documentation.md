# API documentation

REST API of the Haven Hospital Management System.

- **Base URL:** `http://localhost:8080/api`
- **Content type:** `application/json; charset=UTF-8` on requests and responses
- **Authentication:** `Authorization: Bearer <token>` for staff routes
- **No caching:** every response carries `Cache-Control: no-store`

The frontend calls this API through its own origin (`/api/...`), which Vite (development) or
`frontend/server.js` (production) proxies to port 8080.

## Conventions

### Access levels

| Level | Meaning |
| --- | --- |
| Public | No token required |
| Staff | Any valid session (`ADMIN` or `DOCTOR`) |
| Doctor | Valid session whose role is `DOCTOR` and whose profile is active |
| Admin | Valid session whose role is `ADMIN` |

### Success status codes

| Code | Used for |
| --- | --- |
| 200 | Reads and updates |
| 201 | Created resources (doctor, patient, appointment, consultation, account) |
| 204 | `POST /auth/logout` (no body) |

### Errors

All errors have the shape:

```json
{ "error": "Human readable message." }
```

| Code | Meaning |
| --- | --- |
| 400 | Invalid input, invalid state (e.g. consultation before completion), or forbidden self-action |
| 401 | Missing, expired or revoked session; wrong email/password |
| 403 | Authenticated but wrong role, inactive account, or role mismatch at login |
| 404 | Resource missing **or not owned by the calling doctor** |
| 405 | Path exists with a different method |
| 409 | Duplicate email, duplicate consultation, conflicting record |
| 429 | Too many failed logins (8 per email+address or 50 per address in 10 min) or too many assistant questions (15 per minute) |
| 500 | Unexpected server error |
| 503 | Database unavailable or AI service offline/not configured |
| 502 | AI service returned an error or an empty answer |
| 504 | AI service timed out |

### Conventions

- Identifiers are positive integers in the path (`/patients/12`).
- Dates are ISO: `1998-05-24`. Date-times are `2026-11-25T10:00:00` (no timezone).
- Times are `HH:MM` on output and accept `HH:MM` or `HH:MM:SS` on input.
- Weekdays are numbers, `0` = Sunday … `6` = Saturday.
- `q` search parameters are literal, case-insensitive substrings.
- `limit` is a positive integer, capped at 200.
- Unknown JSON fields are ignored; unknown enum values are rejected.
- Request bodies are limited to 64 KiB.

## Common objects

```json
// user (login / me)
{
  "id": 1,
  "email": "admin@haven.local",
  "role": "ADMIN",
  "fullName": "Administrator",
  "doctorId": null,
  "passwordChangedAt": null
}

// doctor
{
  "id": 1, "userId": 2, "staffId": "DR-0001",
  "firstName": "Amine", "lastName": "Khelifi", "name": "Amine Khelifi",
  "specialty": "Cardiology", "phone": "22111222", "email": "amine@haven.local",
  "office": "Room 204", "status": "active", "accountActive": true,
  "workDays": [1, 2, 3, 4, 5], "firstAppointment": "08:00", "lastAppointment": "17:00",
  "visitLength": 30, "patients": 4
}

// patient
{
  "id": 1, "firstName": "Leila", "lastName": "Mansouri", "name": "Leila Mansouri",
  "hhId": "HH-000001", "birthDate": "1990-04-12", "gender": "Female",
  "phone": "22445566", "address": "Sousse"
}

// appointment
{
  "id": 12, "doctorId": 1, "patientId": 1,
  "patient": { "id": 1, "name": "Leila Mansouri", "hhId": "HH-000001", "phone": "22445566" },
  "appointmentDate": "2026-12-01T10:00", "status": "scheduled", "reason": "Checkup"
}

// consultation
{
  "id": 4, "appointmentId": 12, "patientId": 1, "patientName": "Leila Mansouri",
  "diagnosis": "Tension headache", "treatment": "Rest and hydration",
  "notes": "Follow up in two weeks", "consultationDate": "2026-11-21T09:40:00"
}

// hospital settings
{
  "name": "Haven Hospital", "phone": "22000000", "erNumber": "190",
  "email": "hello@haven.local", "street": "12 Avenue Habib Bourguiba",
  "cityState": "Sousse, Tunisia", "parking": "Free patient parking",
  "visiting": { "start": "10:00", "end": "19:00", "maxVisitors": 3,
                "label": "Mon–Sun, 10:00 to 19:00" }
}
```

## Public endpoints

### `GET /health`

Reports API and database status. Always 200 if the process is up.

```bash
curl http://localhost:8080/api/health
```

```json
{ "status": "ok", "database": "ok", "time": "2026-11-21T10:15:00.123" }
```

`database` is `ok` or `unavailable`. It deliberately carries no SQL message, version, host or
connection string; the detailed error is written to the backend log.

### `GET /public/hospital`

Hospital details from the `hospital_settings` table. Never includes private data.

### `POST /assistant/chat`

Public, rate limited to 15 questions per minute per client address.

```bash
curl -X POST http://localhost:8080/api/assistant/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"When can I visit a patient?"}'
```

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `message` | string | yes | 1–1000 characters |

Success: `{ "response": "Visiting hours are 10:00 to 19:00…" }`. Errors: `429` (throttled),
`503` (Python service offline or key missing), `502` (Gemini error/empty answer), `504` (timeout).

## Authentication

### `POST /auth/login`

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `email` | string | yes | valid email, case-insensitive |
| `password` | string | yes | ≤ 200 characters |
| `role` | string | no | `admin` or `doctor`; when present, the account role must match |

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@haven.local","password":"your-password","role":"admin"}'
```

```json
{
  "token": "3b7f0c…",
  "expiresAt": "2026-11-22T10:15:00",
  "user": { "id": 1, "email": "admin@haven.local", "role": "ADMIN",
            "fullName": "Administrator", "doctorId": null, "passwordChangedAt": null }
}
```

Errors: `400` invalid input, `401` wrong credentials, `403` inactive account or role mismatch,
`429` too many attempts.

### `POST /auth/logout` — staff

Revokes the presented token. Returns `204` regardless of whether it was still valid.

### `GET /auth/me` — staff

Returns the current user; for doctors it also adds `profile` (the doctor object) so the UI can render
the workspace without a second call.

### `PATCH /auth/account` — staff

| Field | Type | Required |
| --- | --- | --- |
| `fullName` | string | no (1–120) |
| `email` | string | no (valid, unique) |

Returns `{ "fullName": "...", "email": "..." }`. `409` if the email is used by another account.

### `POST /auth/password` — staff

| Field | Type | Required |
| --- | --- | --- |
| `currentPassword` | string | yes |
| `newPassword` | string | yes (8–200, letter + digit) |

Returns `{ "passwordChangedAt": 1732260000000, "changedAt": "2026-11-21T10:20:00" }`. Every other
session of that account is revoked; the current one stays valid. `400` for a wrong current password
or a weak new one.

## Doctor endpoints

All doctor routes derive the doctor from the session token. A doctor's account must be active and
their profile status must not be `inactive` (`403` otherwise).

### `GET /doctors/me`

Doctor profile plus `passwordChangedAt`. Includes `patients` (number of assigned patients).

### `PATCH /doctors/me`

Updates the doctor's own profile and, optionally, the linked account.

| Field | Type | Notes |
| --- | --- | --- |
| `firstName`, `lastName` | string | 1–50 each |
| `name` | string | alternative to first/last; "Dr." prefix is stripped |
| `phone` | string | ≤ 20; send `null`/`""` to clear |
| `office` | string | ≤ 80; clear with `null` |
| `email` | string | new login email, unique |
| `workDays` | number[] | at least one day, `0`–`6` |
| `firstAppointment`, `lastAppointment` | time | `last` must be after `first` |
| `visitLength` | integer | 5–180 minutes |

Returns the updated doctor object.

### `GET /doctors/me/patients?q=`

Patients assigned to the doctor, optionally filtered by name, HH id or phone.

### `GET /doctors/me/patients/{id}`

One assigned patient plus their consultation count. `404` if the patient is not assigned to the
doctor.

### `GET /doctors/me/patients/{id}/consultations`

That patient's consultation history for this doctor, newest first.

### `GET /doctors/me/appointments?from=&to=&status=`

| Query | Meaning |
| --- | --- |
| `from`, `to` | inclusive ISO dates (`2026-11-01`) |
| `status` | `scheduled`, `completed`, `cancelled`, `missed` |

### `POST /doctors/me/appointments`

| Field | Type | Required |
| --- | --- | --- |
| `patientId` | integer | yes, must be assigned to the doctor |
| `appointmentDate` | date-time | yes, year 2000–2100 |
| `reason` | string | no, ≤ 255 |

Success `201` with the appointment. Errors: `400` (unassigned patient, bad date), `401`, `403`.

### `PUT /doctors/me/appointments/{id}`

Same body as create; reschedules or changes the patient. `404` when the appointment is not the
doctor's; `400` when the new patient is not assigned.

### `PATCH /doctors/me/appointments/{id}/status`

```json
{ "status": "completed" }
```

Allowed: `scheduled`, `completed`, `cancelled`, `missed`. Returns the appointment.

### `GET /doctors/me/consultations?limit=100`

The doctor's consultation notes, newest first. `limit` defaults to 100, max 200.

### `POST /doctors/me/consultations`

| Field | Type | Required |
| --- | --- | --- |
| `appointmentId` | integer | yes, must be a completed appointment of this doctor |
| `diagnosis` | string | yes, 1–2000 |
| `treatment` | string | no, ≤ 2000 |
| `notes` | string | no, ≤ 4000 |

`201` with the consultation; `400` if the appointment is not completed or not the doctor's; `409` if
notes already exist for that appointment.

### `PUT /doctors/me/consultations/{id}`

Same fields as create except `appointmentId`. `404` when it is not the doctor's consultation.

## Administrator endpoints

Every route in this section requires an `ADMIN` session and answers `403` otherwise.

### `GET /admin/stats`

```json
{
  "doctors": 6, "activeDoctors": 5, "doctorsOnLeave": 1, "inactiveDoctors": 0,
  "patients": 3, "appointments": 12, "appointmentsToday": 2,
  "upcomingAppointments": 7, "completedAppointments": 4,
  "consultations": 4, "doctorAccounts": 6, "adminAccounts": 1
}
```

### `GET /admin/activity`

Up to 10 recent events, newest first, each `{ type, title, detail, at }` with `type` one of
`appointment`, `consultation`, `account`.

### Doctors

| Method | Path | Body / query | Result |
| --- | --- | --- | --- |
| GET | `/admin/doctors?q=&status=` | `status`: `active`, `leave`, `inactive` | array of doctor objects |
| POST | `/admin/doctors` | see below | `201` doctor |
| GET | `/admin/doctors/{id}` | — | doctor + `patientNames` |
| PUT | `/admin/doctors/{id}` | doctor fields, optional `email`, `status` | updated doctor |
| DELETE | `/admin/doctors/{id}` | — | `{ deactivated, message, doctor }` |
| PATCH | `/admin/doctors/{id}/status` | `{ "status": "leave" }` | updated doctor |

`POST /admin/doctors`:

| Field | Type | Required |
| --- | --- | --- |
| `firstName`, `lastName` | string | yes (1–50) |
| `specialty` | string | yes (1–100) |
| `email` | string | yes, unique |
| `password` | string | yes (8–200, letter + digit) |
| `phone` | string | no (≤ 20) |
| `office` | string | no (≤ 80) |
| `status` | string | no, defaults to `active` |

`DELETE` marks the doctor `inactive`, deactivates the account and revokes its sessions. Records are
kept; the doctor can be reactivated with `PATCH .../status {"status":"active"}` or by reactivating the
account.

### Accounts

| Method | Path | Body | Result |
| --- | --- | --- | --- |
| GET | `/admin/users?q=&role=` | `role`: `ADMIN`, `DOCTOR` | array of accounts (`id`, `email`, `role`, `fullName`, `active`, `phone`, `doctorId`, `staffId`, `createdAt`) |
| POST | `/admin/users` | see below | `201` account |
| PATCH | `/admin/users/{id}/active` | `{ "active": false }` | `{ id, active }` |
| POST | `/admin/users/{id}/password` | `{ "password": "..." }` | `{ id, passwordReset: true }` |

`POST /admin/users` requires `email`, `password`, `role` (`ADMIN`|`DOCTOR`), `fullName`; for
`DOCTOR` it also requires `specialty` and accepts `office`. Creating a doctor account also creates the
doctor profile.

Rules: you cannot deactivate your own account (`400`), the last active administrator cannot be
deactivated (`400`), and a reset password must satisfy the strength rule (`400`). Deactivation revokes
that account's sessions.

### Patients

| Method | Path | Body / query | Result |
| --- | --- | --- | --- |
| GET | `/admin/patients?q=` | search by name/HH id/phone | array with `appointments`, `consultations`, `doctors` |
| POST | `/admin/patients` | see below | `201 { id, hhId }` |
| PUT | `/admin/patients/{id}` | demographic fields | updated patient |
| PUT | `/admin/patients/{id}/doctor` | `{ "doctorId": 3 }` | `{ assigned, patientId, doctorId }` |

`POST /admin/patients` requires `firstName`, `lastName`, `birthDate` (not in the future); optional
`gender`, `phone`, `address`, `doctorId`. Assigning to an `inactive` doctor answers `400`.

### Activity lists

| Method | Path | Query |
| --- | --- | --- |
| GET | `/admin/appointments` | `limit` (default 50) |
| GET | `/admin/consultations` | `limit` (default 50) |

### Settings

`GET /admin/settings` returns the hospital settings object.

`PUT /admin/settings`:

| Field | Type | Required |
| --- | --- | --- |
| `name` | string | yes, 1–120 |
| `phone`, `erNumber` | string | no, ≤ 30 |
| `street` | string | no, ≤ 160 |
| `cityState` | string | no, ≤ 120 |
| `parking` | string | no, ≤ 200 |
| `email` | string | no, valid email or a `[...]` placeholder |
| `visiting.start`, `visiting.end` | time | both or neither; `end` after `start` |
| `visiting.maxVisitors` | integer | 1–10, defaults to 2 |

Returns the saved settings. `400` for an invalid email, half-set visiting times or an `end` before
`start`.

## Complete route list

| Method | Path | Access |
| --- | --- | --- |
| GET | `/api/health` | public |
| GET | `/api/public/hospital` | public |
| POST | `/api/assistant/chat` | public (15/min) |
| POST | `/api/auth/login` | public (8 failures/10 min) |
| POST | `/api/auth/logout` | staff |
| GET | `/api/auth/me` | staff |
| PATCH | `/api/auth/account` | staff |
| POST | `/api/auth/password` | staff |
| GET, PATCH | `/api/doctors/me` | doctor |
| GET | `/api/doctors/me/patients` | doctor |
| GET | `/api/doctors/me/patients/{id}` | doctor |
| GET | `/api/doctors/me/patients/{id}/consultations` | doctor |
| GET, POST | `/api/doctors/me/appointments` | doctor |
| PUT | `/api/doctors/me/appointments/{id}` | doctor |
| PATCH | `/api/doctors/me/appointments/{id}/status` | doctor |
| GET, POST | `/api/doctors/me/consultations` | doctor |
| PUT | `/api/doctors/me/consultations/{id}` | doctor |
| GET | `/api/admin/stats` | admin |
| GET | `/api/admin/activity` | admin |
| GET, POST | `/api/admin/doctors` | admin |
| GET, PUT, DELETE | `/api/admin/doctors/{id}` | admin |
| PATCH | `/api/admin/doctors/{id}/status` | admin |
| GET, POST | `/api/admin/users` | admin |
| PATCH | `/api/admin/users/{id}/active` | admin |
| POST | `/api/admin/users/{id}/password` | admin |
| GET, POST | `/api/admin/patients` | admin |
| PUT | `/api/admin/patients/{id}` | admin |
| PUT | `/api/admin/patients/{id}/doctor` | admin |
| GET | `/api/admin/appointments` | admin |
| GET | `/api/admin/consultations` | admin |
| GET, PUT | `/api/admin/settings` | admin |

## Quick authentication example

```bash
# 1. sign in and keep the token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@haven.local","password":"your-password"}' \
  | python -c "import sys,json;print(json.load(sys.stdin)['token'])")

# 2. call a protected route
curl -s http://localhost:8080/api/admin/stats -H "Authorization: Bearer $TOKEN"
```

## Rate limits and sessions

| Limit | Value |
| --- | --- |
| Failed logins | 8 per email + client address per 10 minutes, plus 50 per address per 10 minutes → `429` |
| Assistant questions | 15 per client address per minute → `429` |
| Session lifetime | `SESSION_HOURS`, default 12 hours |
| Request body | 64 KiB |

Both limits are held in memory, so they reset when the API restarts and apply per instance. The
client address is the TCP peer unless `TRUST_PROXY=true` is set behind a reverse proxy; without that
setting, `X-Forwarded-For` is ignored.
