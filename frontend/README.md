# Haven Hospital — frontend

React single-page application for the Haven Hospital Management System: a public information page, a
staff login, an administrator dashboard and a doctor workspace. Every screen reads and writes real
data through the Java API — there is no mock authentication, demo data or dead button.

## Requirements

| Tool | Version |
| --- | --- |
| Node.js | **20.19+ or 22.12+** (Vite 8 requirement; LTS 22 recommended). Verified on Node 26.13.1 |
| npm | 9 or newer (verified with npm 11) |

The backend must be running for any sign-in or data screen (see [`../backend/README.md`](../backend/README.md)).

## Install

```bat
cd frontend
npm ci
```

`npm ci` installs exactly `package-lock.json`; use `npm install` only when changing dependencies.

## Run

```bat
npm run dev
```

- URL: <http://localhost:5173>
- Vite proxies `/api/*` to `http://localhost:8080` (see `vite.config.js`), so the browser only talks to
  one origin and no CORS configuration is needed in development.

Do not delete the API proxy: the app calls relative `/api` paths.

## Production build

```bat
npm run build
npm start
```

- `npm run build` writes `dist/`.
- `npm start` runs `server.js`: serves `dist/`, answers client routes (`/doctor`, `/admin`, …) with
  `index.html`, returns 404 for missing files with an extension, and proxies `/api` to the Java API.
- URL: <http://localhost:3000>

| Variable | Used by | Default | Meaning |
| --- | --- | --- | --- |
| `PORT` | `server.js` | `3000` | Static server port |
| `API_URL` | `server.js` | `http://localhost:8080` | Java API origin behind `/api` |

Example:

```bat
set PORT=4000
set API_URL=http://127.0.0.1:8080
npm start
```

There is no `.env` file for the frontend: Vite only needs the proxy, and `server.js` reads real
environment variables. The React bundle contains no secrets.

## Scripts

| Command | What it does |
| --- | --- |
| `npm run dev` | Vite development server with hot reload |
| `npm run build` | Production build into `dist/` |
| `npm start` | Serves the build and proxies `/api` (requires `npm run build` first) |
| `npm run lint` | `oxlint` over `src/` |
| `npm run preview` | Serves `dist/` with Vite's preview server (same `/api` proxy) |

## Dependencies

| Package | Why it is here |
| --- | --- |
| `react`, `react-dom` | UI runtime |
| `react-router-dom` | Client routing, route groups, redirects |
| `motion` | Page/element transitions, respecting `prefers-reduced-motion` |
| `tailwindcss`, `@tailwindcss/vite` | Styling and design tokens |
| `tw-animate-css` | Small animation utilities imported by `src/index.css` |
| `vite`, `@vitejs/plugin-react` | Build tool and React plugin |
| `oxlint` | Linting (`npm run lint`) |
| `@types/react`, `@types/react-dom` | Editor type hints (no TypeScript source) |

## API configuration

All requests go through `src/services/api.js`:

- relative base path `/api`;
- `Authorization: Bearer <token>` when a session exists;
- JSON parsing and user-safe error messages;
- on `401`: clears the stored session and emits `haven:unauthorized`, which returns the app to
  `/login`.

Sessions are stored in `localStorage` when "Keep me signed in" is checked, otherwise in
`sessionStorage`, under the key `haven.session`. Expiry is checked on read.

To point the app at another API origin without a proxy, change the proxy target in `vite.config.js`
and `API_URL` for `server.js` — the source code itself stays origin-relative.

## Structure

```text
frontend/
├── index.html
├── server.js               production static server + /api proxy
├── vite.config.js          React + Tailwind plugins, /api proxy
├── .oxlintrc.json
├── package.json / package-lock.json
├── public/favicon.svg
└── src/
    ├── main.jsx            React root
    ├── App.jsx             routes, providers, unauthorized handling
    ├── index.css           Tailwind theme tokens and base styles
    ├── pages/
    │   ├── VisitUs.jsx     public hospital page + assistant
    │   └── StaffLogin.jsx  role selector, email/password sign-in
    ├── admin/
    │   ├── AdminLayout.jsx sidebar and shell
    │   ├── AdminData.jsx   API data provider (doctors, stats, activity, own account)
    │   ├── Overview.jsx    totals, doctors snapshot, activity
    │   ├── Doctors.jsx     list, filters, create/edit/status/deactivate
    │   ├── DoctorModal.jsx doctor form
    │   ├── Patients.jsx    register, edit, assign doctor
    │   ├── Users.jsx       staff accounts, activation, password reset
    │   └── Settings.jsx    hospital details + own account
    ├── doctor/
    │   ├── DoctorLayout.jsx
    │   ├── DoctorData.jsx  profile, patients, appointments, consultations, actions
    │   ├── Today.jsx       day view, next patient, mark as seen, notes
    │   ├── Appointments.jsx
    │   ├── AppointmentModal.jsx
    │   ├── ConsultationModal.jsx
    │   └── Profile.jsx
    ├── components/         UI kit (Button, Field, Badge, Panel, Modal, Toast, DataGate),
    │                       DashShell, RequireRole, ChangePassword, HospitalAssistant, Icons
    ├── services/           api.js, auth.js, hospital.js
    ├── hooks/              ripple.js, useCountUp.js
    ├── config/hospital.js  fallback text and department list
    └── utils/date.js       date/time helpers
```

## Pages and access

| Route | Access | Content |
| --- | --- | --- |
| `/` | public | Hospital details from the API, map card, emergency number, hours, departments, assistant |
| `/login` | public | Staff sign-in with a role selector |
| `/admin` | `ADMIN` | Totals, doctors, activity, quick actions |
| `/admin/doctors` | `ADMIN` | Search/filter, add, edit, leave, deactivate/reactivate |
| `/admin/patients` | `ADMIN` | Register, edit, assign |
| `/admin/users` | `ADMIN` | Create accounts, activate/deactivate, reset passwords |
| `/admin/settings` | `ADMIN` | Hospital details, visiting hours, own name/email, password |
| `/doctor` | `DOCTOR` | Today, next patient, week strip, notes |
| `/doctor/appointments` | `DOCTOR` | Search, book, reschedule, cancel, status, notes |
| `/doctor/profile` | `DOCTOR` | Contact, office, working days/hours, visit length, password |

`components/RequireRole.jsx` redirects when there is no session or the role does not match. This is a
navigation convenience: every protected route is enforced again by the API.

## UI system

- Tailwind CSS 4 tokens live in `src/index.css` (primary blue, peach accent, soft shadows, rounded
  cards).
- `components/ui.jsx`: `Button`, `Field`, `Badge`, `Panel`, `PillButton`, `IconButton`, `EmptyState`,
  helpers.
- `components/Modal.jsx`: focus trap, Escape to close, backdrop click, body scroll lock.
- `components/Toast.jsx`: success/error toasts, optional undo.
- `components/DataGate.jsx`: loading skeleton and error state with retry.
- `SearchBox`: `/` focuses search; tables collapse into cards on small screens.

## Checks

```bat
npm run lint
npm run build
```

Expected: 0 lint errors (`oxlint` currently prints 4 informational `react(set-state-in-effect)` hints
for fetch-on-mount effects) and a successful build. Both were run for this version.

Browser flows were verified against the real API in the previous sprint: administrator and doctor
sign-in, dashboards, patient and account lists, settings loaded from MySQL, the public page, the
assistant's offline state, booking, "mark as seen", consultation notes persisting after reload, logout
and the 401 redirect. Sprint 10 rebuilt and linted the frontend and exercised every API contract
through `tests/api_test.py`; it did not repeat the click-through.

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `EBADENGINE` / Vite version error | Upgrade Node to 20.19+ or 22.12+. |
| `Cannot reach the server` | Start the Java API on port 8080. |
| Login succeeds but every screen is empty | The backend database is unavailable; check `/api/health`. |
| `EADDRINUSE` on 5173 | Another dev server is running: stop it or `npm run dev -- --port 5174`. |
| `npm start` says "No build found" | Run `npm run build` first. |
| Styles look broken after an update | Restart Vite; delete `node_modules/.vite` if it persists. |
