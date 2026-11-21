import { useCallback, useState } from 'react'
import { Outlet } from 'react-router-dom'
import { AdminDataProvider } from './AdminData.jsx'
import DoctorModal from './DoctorModal.jsx'
import DashShell from '../components/DashShell.jsx'
import { ToastProvider, useToast } from '../components/Toast.jsx'
import { GridIcon, StethoscopeIcon, UserIcon, GearIcon, ShieldIcon } from '../components/Icons.jsx'

const nav = [
  { to: '/admin', label: 'Overview', icon: GridIcon, end: true },
  { to: '/admin/doctors', label: 'Doctors', icon: StethoscopeIcon },
  { to: '/admin/patients', label: 'Patients', icon: UserIcon },
  { to: '/admin/users', label: 'Accounts', icon: ShieldIcon },
  { to: '/admin/settings', label: 'Settings', icon: GearIcon },
]

function Shell() {
  const toast = useToast()
  // Doctor modal shared by every admin page. Stays mounted while closing so it can animate out.
  const [modal, setModal] = useState({ open: false, doctor: null, key: 0 })
  const openDoctor = useCallback((doctor = null) => setModal({ open: true, doctor, key: Date.now() }), [])
  const closeModal = useCallback(() => setModal((m) => ({ ...m, open: false })), [])

  return (
    <DashShell nav={nav} home="/admin" subtitle="Administrator" label="Admin navigation">
      <Outlet context={{ openDoctor }} />
      {modal.key !== 0 && (
        <DoctorModal key={modal.key} open={modal.open} doctor={modal.doctor} onClose={closeModal} onSaved={(msg) => toast(msg)} />
      )}
    </DashShell>
  )
}

export default function AdminLayout() {
  return (
    <ToastProvider>
      <AdminDataProvider>
        <Shell />
      </AdminDataProvider>
    </ToastProvider>
  )
}
