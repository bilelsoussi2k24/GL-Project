import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import { getSession } from '../services/auth.js'

// Doctor data, held in memory for this session (no backend yet).
// Everything starts empty: the doctor fills in their profile and books appointments.
// When a backend exists, load these from the API and call it inside the actions below.

// Only the staff ID comes from the login; the rest is filled in on the Profile page.
// Working days and hours are starting defaults the doctor can change.
const emptyProfile = () => ({
  name: '',
  specialty: '',
  staffId: getSession()?.id ?? '',
  phone: '',
  email: '',
  office: '',
  workDays: [1, 2, 3, 4, 5], // 0 = Sun … 6 = Sat
  firstAppointment: '08:00',
  lastAppointment: '17:00',
  visitLength: 30,
  passwordChangedAt: null,
})

const DoctorContext = createContext(null)

export function DoctorDataProvider({ children }) {
  const [appointments, setAppointments] = useState([])
  const [profile, setProfile] = useState(emptyProfile)

  const addAppointment = useCallback((data) => {
    const appt = { ...data, id: `a${Date.now()}`, status: 'scheduled' }
    setAppointments((list) => [...list, appt])
    return appt
  }, [])

  const updateAppointment = useCallback((id, patch) => {
    setAppointments((list) => list.map((a) => (a.id === id ? { ...a, ...patch } : a)))
  }, [])

  const value = useMemo(
    () => ({ appointments, profile, setProfile, addAppointment, updateAppointment }),
    [appointments, profile, addAppointment, updateAppointment],
  )

  return <DoctorContext.Provider value={value}>{children}</DoctorContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useDoctorData() {
  const ctx = useContext(DoctorContext)
  if (!ctx) throw new Error('useDoctorData must be used inside <DoctorDataProvider>')
  return ctx
}

// Sort helper: by date, then time.
// eslint-disable-next-line react-refresh/only-export-components
export const byDateTime = (a, b) => (a.date + a.time).localeCompare(b.date + b.time)
