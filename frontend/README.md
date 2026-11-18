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

## Not connected to a backend yet

- **Login** (`src/services/auth.js`) accepts any staff ID and password. Replace `login()` with a call to the backend.
- **Doctors, appointments and profiles** start empty and are kept in memory, so they reset on refresh. Connect them in `src/doctor/DoctorData.jsx` and `src/admin/AdminData.jsx`.
- **Hospital details** (phone, ER number, address, visiting hours) are saved in the browser's localStorage by `src/services/hospital.js`. The `[PLACEHOLDER]` values in `src/config/hospital.js` are the defaults until the admin changes them in Settings.
