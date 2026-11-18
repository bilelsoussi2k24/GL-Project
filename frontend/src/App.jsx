import { Suspense, lazy, useEffect } from 'react'
import { Routes, Route, useLocation } from 'react-router-dom'
import Header from './components/Header.jsx'
import Footer from './components/Footer.jsx'
import RequireRole from './components/RequireRole.jsx'
import VisitUs from './pages/VisitUs.jsx'

// Staff pages load on demand, so public visitors only download the homepage.
const StaffLogin = lazy(() => import('./pages/StaffLogin.jsx'))
const DoctorLayout = lazy(() => import('./doctor/DoctorLayout.jsx'))
const Today = lazy(() => import('./doctor/Today.jsx'))
const Appointments = lazy(() => import('./doctor/Appointments.jsx'))
const Profile = lazy(() => import('./doctor/Profile.jsx'))
const AdminLayout = lazy(() => import('./admin/AdminLayout.jsx'))
const Overview = lazy(() => import('./admin/Overview.jsx'))
const Doctors = lazy(() => import('./admin/Doctors.jsx'))
const Settings = lazy(() => import('./admin/Settings.jsx'))

// Pages that use their own full-screen layout (no public header/footer).
const isFullScreen = (path) => path === '/login' || path.startsWith('/doctor') || path.startsWith('/admin')

function PageLoader() {
  return (
    <div className="grid min-h-screen place-items-center bg-canvas" role="status" aria-label="Loading">
      <span className="size-9 animate-spin rounded-full border-4 border-primary-soft border-t-primary" />
    </div>
  )
}

export default function App() {
  const { pathname, hash } = useLocation()
  useEffect(() => {
    if (!hash) window.scrollTo(0, 0)
  }, [pathname, hash])

  if (isFullScreen(pathname)) {
    return (
      <Suspense fallback={<PageLoader />}>
        <Routes>
          <Route path="/login" element={<StaffLogin />} />
          <Route
            path="/doctor"
            element={
              <RequireRole role="doctor">
                <DoctorLayout />
              </RequireRole>
            }
          >
            <Route index element={<Today />} />
            <Route path="appointments" element={<Appointments />} />
            <Route path="profile" element={<Profile />} />
          </Route>
          <Route
            path="/admin"
            element={
              <RequireRole role="admin">
                <AdminLayout />
              </RequireRole>
            }
          >
            <Route index element={<Overview />} />
            <Route path="doctors" element={<Doctors />} />
            <Route path="settings" element={<Settings />} />
          </Route>
        </Routes>
      </Suspense>
    )
  }

  return (
    <div className="flex min-h-screen flex-col">
      <Header />
      <main key={pathname} className="flex-1">
        <Routes>
          <Route path="/" element={<VisitUs />} />
        </Routes>
      </main>
      <Footer />
    </div>
  )
}
