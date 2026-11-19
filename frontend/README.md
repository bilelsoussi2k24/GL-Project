# Haven Hospital — frontend

React + Vite app for the hospital: a public "Visit us" homepage, a staff login, and dashboards for doctors and administrators.

## Run it

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173
```

Other scripts: `npm run build` (production build into `dist/`), `npm run preview` (serve the build), `npm run lint`.

## Pages

| Path | Who | What |
| --- | --- | --- |
| `/` | Public | Visit us: map, emergency number, visiting hours, reception, departments |
| `/login` | Staff | Doctor / administrator sign-in |
| `/doctor` | Doctor | Today: schedule, next patient, mark visits as seen |
| `/doctor/appointments` | Doctor | Search, book, edit and cancel appointments |
| `/doctor/profile` | Doctor | Personal details, working days and hours, password |
| `/admin` | Admin | Overview: staff numbers, doctors on leave |
| `/admin/doctors` | Admin | Add, edit, delete doctors; toggle Active / On leave |
| `/admin/settings` | Admin | Hospital details shown on the public page, visiting hours, account |

## Stack

- React 19, React Router 7, Vite
- Tailwind CSS v4 (design tokens in `src/index.css`)
- Motion (`motion/react`) and `tw-animate-css` for animations

## Project layout

```
src/
  pages/        public homepage and staff login
  doctor/       doctor dashboard pages and their data store
  admin/        admin dashboard pages and their data store
  components/   shared UI (ui.jsx building blocks, Modal, Toast, DashShell sidebar…)
  services/     auth.js (login/session), hospital.js (hospital details)
  config/       default hospital details and department list
```

## Backend

The doctor dashboard talks to the Java API in `../backend` (`HospitalServer`, port 8080). Start it before `npm run dev`; Vite forwards `/api` requests to `http://localhost:8080`. All calls go through `src/services/api.js`.

| Screen | Data source |
| --- | --- |
| Doctor appointments (Today, Appointments) | Java API: `GET/POST /api/doctors/me/appointments`, `PATCH …/{id}/status`, `GET /api/doctors/me/patients` |
| Login | Mock in `src/services/auth.js` (any staff ID and password). No login endpoint yet. |
| Doctor profile | In memory in `src/doctor/DoctorData.jsx`. No profile endpoint yet. |
| Admin doctors and account | In memory in `src/admin/AdminData.jsx`. No admin endpoints yet. |
| Hospital details | Browser localStorage in `src/services/hospital.js`. The `[PLACEHOLDER]` values in `src/config/hospital.js` are the defaults. |

Booking notes:

- A booking must be for one of the doctor's patients (`doctor_patients`). Type the patient's full name, or their HH number: patient id 7 is `HH-000007`.
- The API can only change an appointment's status (complete, cancel, undo), so editing a booking's patient, date, time or reason is blocked in the form.
- The API has no visit length, so appointments use the doctor's profile setting (30 minutes by default).
