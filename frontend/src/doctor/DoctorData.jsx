import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { api } from '../services/api.js'
import { getSession } from '../services/auth.js'
import { useToast } from '../components/Toast.jsx'
import DataGate from '../components/DataGate.jsx'

// Doctor data.
// - Appointments and patients come from the Java backend (/api/doctors/me/...).
// - The profile is kept in memory: the backend has no profile endpoint yet.
// Appointment changes show straight away and then save to the server;
// if the server refuses, the list is reloaded and an error toast is shown.

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

// ---------- mapping between the Java API and the screens ----------

// Patient database id 7 is shown as "HH-000007" (the format the appointment form uses).
const toHhId = (id) => `HH-${String(id).padStart(6, '0')}`
const fromHhId = (hhId) => Number(/^HH-(\d{6})$/i.exec(hhId?.trim() ?? '')?.[1]) || null

const patientName = (p) => [p.firstName, p.lastName].filter(Boolean).join(' ')

// Java appointment → screen appointment.
function fromApi(a, patientsById, visitLength) {
  const p = patientsById.get(a.patientId)
  // LocalDateTime.toString(): "2026-11-25T10:00" or "2026-11-25T10:00:30"
  const [date, rest = ''] = a.appointmentDate.split('T')
  return {
    id: a.id,
    patientId: a.patientId,
    patient: {
      name: p ? patientName(p) : `Patient #${a.patientId}`,
      hhId: toHhId(a.patientId),
      phone: p?.phone ?? '',
    },
    reason: a.reason ?? '',
    date,
    time: rest.slice(0, 5),
    duration: visitLength,
    status: a.status,
  }
}

// Find the doctor's patient that the form refers to: by HH id first, then by full name.
// eslint-disable-next-line react-refresh/only-export-components
export function findPatient(patients, { hhId, name }) {
  const id = fromHhId(hhId)
  if (id) return patients.find((p) => p.id === id)
  const n = name.trim().toLowerCase()
  return patients.find((p) => patientName(p).toLowerCase() === n)
}

const DoctorContext = createContext(null)

export function DoctorDataProvider({ children }) {
  const toast = useToast()
  const [appointments, setAppointments] = useState([])
  const [patients, setPatients] = useState([])
  const [profile, setProfileState] = useState(emptyProfile)
  const [status, setStatus] = useState({ loading: true, error: '' })
  const latest = useRef({ patients, profile })
  useEffect(() => {
    latest.current = { patients, profile }
  })

  const fetchAll = useCallback(async () => {
    const [pts, list] = await Promise.all([api('/doctors/me/patients'), api('/doctors/me/appointments')])
    const byId = new Map(pts.map((p) => [p.id, p]))
    setPatients(pts)
    setAppointments(list.map((a) => fromApi(a, byId, latest.current.profile.visitLength)))
  }, [])

  // `retry` shows the spinner again first.
  const load = useCallback(
    async (retry) => {
      if (retry) setStatus({ loading: true, error: '' })
      try {
        await fetchAll()
        setStatus({ loading: false, error: '' })
      } catch (err) {
        setStatus({ loading: false, error: err.message })
      }
    },
    [fetchAll],
  )

  useEffect(() => {
    load()
  }, [load])

  const fail = useCallback(
    (err) => {
      toast(err.message, { tone: 'error' })
      fetchAll().catch(() => {})
    },
    [toast, fetchAll],
  )

  const addAppointment = useCallback(
    (data) => {
      const tempId = `tmp${Date.now()}`
      const patient = findPatient(latest.current.patients, data.patient)
      const appt = { ...data, id: tempId, status: 'scheduled' }
      if (patient) {
        appt.patientId = patient.id
        appt.patient = { name: patientName(patient), hhId: toHhId(patient.id), phone: patient.phone ?? data.patient.phone }
      }
      setAppointments((list) => [...list, appt])
      if (!patient) {
        // Deferred so it shows after the form's own toast.
        queueMicrotask(() => fail(new Error(`${data.patient.name} is not one of your patients.`)))
        return appt
      }
      api('/doctors/me/appointments', {
        method: 'POST',
        body: { patientId: patient.id, appointmentDate: `${data.date}T${data.time}:00`, reason: data.reason },
      })
        .then((saved) => setAppointments((list) => list.map((a) => (a.id === tempId ? { ...a, id: saved.id } : a))))
        .catch(fail)
      return appt
    },
    [fail],
  )

  // The backend can only change an appointment's status; other edits are refused.
  const updateAppointment = useCallback(
    (id, patch) => {
      setAppointments((list) => list.map((a) => (a.id === id ? { ...a, ...patch } : a)))
      const { status: newStatus, ...rest } = patch
      if (Object.keys(rest).length) {
        queueMicrotask(() => fail(new Error('The server can’t change a booking yet.')))
        return
      }
      if (typeof id !== 'number') return // still being created
      api(`/doctors/me/appointments/${id}/status`, { method: 'PATCH', body: { status: newStatus } }).catch(fail)
    },
    [fail],
  )

  // Profile: kept in memory. Accepts an object or an updater function. Returns a promise.
  const setProfile = useCallback(async (next) => {
    const value = typeof next === 'function' ? next(latest.current.profile) : next
    setProfileState(value)
    return value
  }, [])

  const patchProfile = useCallback((patch) => setProfileState((p) => ({ ...p, ...patch })), [])

  const value = useMemo(
    () => ({ appointments, patients, profile, setProfile, patchProfile, addAppointment, updateAppointment, reload: load }),
    [appointments, patients, profile, setProfile, patchProfile, addAppointment, updateAppointment, load],
  )

  if (status.loading || status.error) return <DataGate error={status.error} onRetry={() => load(true)} />
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
