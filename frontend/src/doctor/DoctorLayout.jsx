import { useCallback, useState } from 'react'
import { Link, Outlet } from 'react-router-dom'
import { DoctorDataProvider, useDoctorData } from './DoctorData.jsx'
import AppointmentModal from './AppointmentModal.jsx'
import DashShell from '../components/DashShell.jsx'
import { ToastProvider, useToast } from '../components/Toast.jsx'
import { Avatar } from '../components/ui.jsx'
import { initials } from '../utils/date.js'
import { GridIcon, CalendarIcon, UserIcon } from '../components/Icons.jsx'

const nav = [
  { to: '/doctor', label: 'Today', icon: GridIcon, end: true },
  { to: '/doctor/appointments', label: 'Appointments', icon: CalendarIcon },
  { to: '/doctor/profile', label: 'Profile', icon: UserIcon },
]

function Shell() {
  const { profile } = useDoctorData()
  const toast = useToast()
  // Appointment modal shared by every doctor page. Stays mounted while closing so it can animate out.
  const [modal, setModal] = useState({ open: false, appointment: null, key: 0 })
  const openAppointment = useCallback((appointment = null) => setModal({ open: true, appointment, key: Date.now() }), [])
  const closeModal = useCallback(() => setModal((m) => ({ ...m, open: false })), [])

  const card = (
    <Link
      to="/doctor/profile"
      className="group mt-7 flex items-center gap-3 rounded-[14px] bg-white/10 px-3.5 py-4 transition duration-300 hover:-translate-y-0.5 hover:bg-white/16"
    >
      <Avatar tone="peach" className="group-hover:scale-108 group-hover:-rotate-6">
        {initials(profile.name) || <UserIcon />}
      </Avatar>
      <span className="grid leading-tight">
        <strong className="text-[0.95rem]">{profile.name || 'Your profile'}</strong>
        <small className="text-[0.82rem] text-sky">{profile.specialty || profile.staffId || 'Add your details'}</small>
      </span>
    </Link>
  )

  return (
    <DashShell nav={nav} home="/doctor" card={card} label="Doctor navigation">
      <Outlet context={{ openAppointment }} />
      {modal.key !== 0 && (
        <AppointmentModal
          key={modal.key}
          open={modal.open}
          appointment={modal.appointment}
          onClose={closeModal}
          onSaved={(msg) => toast(msg)}
        />
      )}
    </DashShell>
  )
}

export default function DoctorLayout() {
  return (
    <ToastProvider>
      <DoctorDataProvider>
        <Shell />
      </DoctorDataProvider>
    </ToastProvider>
  )
}
