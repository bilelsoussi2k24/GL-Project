import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api } from '../services/api.js'
import DataGate from '../components/DataGate.jsx'

// Doctor workspace data.
// Everything here comes from the Java API and MySQL: the profile, the doctor's
// patients, their appointments and their consultation notes. The doctor is
// identified by the session token on the server, never by an id sent from here.

const emptyProfile = () => ({
  name: '',
  firstName: '',
  lastName: '',
  specialty: '',
  staffId: '',
  phone: '',
  email: '',
  office: '',
  workDays: [1, 2, 3, 4, 5],
  firstAppointment: '08:00',
  lastAppointment: '17:00',
  visitLength: 30,
  passwordChangedAt: null,
})

const profileFromApi = (d) => ({
  name: d.name ?? '',
  firstName: d.firstName ?? '',
  lastName: d.lastName ?? '',
  specialty: d.specialty ?? '',
  staffId: d.staffId ?? '',
  phone: d.phone ?? '',
  email: d.email ?? '',
  office: d.office ?? '',
  workDays: Array.isArray(d.workDays) && d.workDays.length ? d.workDays : [1, 2, 3, 4, 5],
  firstAppointment: d.firstAppointment ?? '08:00',
  lastAppointment: d.lastAppointment ?? '17:00',
  visitLength: d.visitLength ?? 30,
  passwordChangedAt: d.passwordChangedAt ?? null,
})

const patientName = (p) => [p.firstName, p.lastName].filter(Boolean).join(' ')

// Java appointment → screen appointment.
function appointmentFromApi(a, visitLength) {
  const [date, rest = ''] = String(a.appointmentDate).split('T')
  return {
    id: a.id,
    patientId: a.patientId,
    patient: {
      name: a.patient?.name || `Patient #${a.patientId}`,
      hhId: a.patient?.hhId || `HH-${String(a.patientId).padStart(6, '0')}`,
      phone: a.patient?.phone ?? '',
    },
    reason: a.reason ?? '',
    date,
    time: rest.slice(0, 5),
    duration: visitLength,
    status: a.status,
  }
}

function consultationFromApi(c) {
  const [date, rest = ''] = String(c.consultationDate ?? '').split('T')
  return {
    id: c.id,
    appointmentId: c.appointmentId,
    patientId: c.patientId,
    patientName: c.patientName ?? '',
    diagnosis: c.diagnosis ?? '',
    treatment: c.treatment ?? '',
    notes: c.notes ?? '',
    date,
    time: rest.slice(0, 5),
  }
}

// Find the doctor's patient the appointment form refers to: by HH id first, then by full name.
// eslint-disable-next-line react-refresh/only-export-components
export function findPatient(patients, { hhId, name }) {
  const id = Number(/^HH-(\d{6})$/i.exec(hhId?.trim() ?? '')?.[1]) || null
  if (id) return patients.find((p) => p.id === id)
  const n = name.trim().toLowerCase()
  return patients.find((p) => patientName(p).toLowerCase() === n)
}

const DoctorContext = createContext(null)

export function DoctorDataProvider({ children }) {
  const [appointments, setAppointments] = useState([])
  const [patients, setPatients] = useState([])
  const [consultations, setConsultations] = useState([])
  const [profile, setProfileState] = useState(emptyProfile)
  const [status, setStatus] = useState({ loading: true, error: '' })

  const fetchAll = useCallback(async () => {
    const [me, pts, list, notes] = await Promise.all([
      api('/doctors/me'),
      api('/doctors/me/patients'),
      api('/doctors/me/appointments'),
      api('/doctors/me/consultations?limit=100'),
    ])
    const visitLength = me.visitLength ?? 30
    setProfileState(profileFromApi(me))
    setPatients(pts)
    setAppointments(list.map((a) => appointmentFromApi(a, visitLength)))
    setConsultations(notes.map(consultationFromApi))
  }, [])

  // `retry` shows the spinner again first.
  const load = useCallback(
    async (retry) => {
      if (retry) setStatus({ loading: true, error: '' })
      try {
        await fetchAll()
        setStatus({ loading: false, error: '' })
      } catch (err) {
        if (err.status === 401) return
        setStatus({ loading: false, error: err.message })
      }
    },
    [fetchAll],
  )

  useEffect(() => {
    load()
  }, [load])

  const addAppointment = useCallback(
    async (data) => {
      const saved = await api('/doctors/me/appointments', {
        method: 'POST',
        body: {
          patientId: data.patientId,
          appointmentDate: `${data.date}T${data.time}:00`,
          reason: data.reason,
        },
      })
      const appointment = appointmentFromApi(saved, profile.visitLength)
      setAppointments((list) => [...list, appointment])
      return appointment
    },
    [profile.visitLength],
  )

  // Status-only changes use the status endpoint; a reschedule or patient change updates the booking.
  const updateAppointment = useCallback(async (id, patch) => {
    const keys = Object.keys(patch)
    if (keys.length === 1 && patch.status) {
      const saved = await api(`/doctors/me/appointments/${id}/status`, {
        method: 'PATCH',
        body: { status: patch.status },
      })
      setAppointments((list) =>
        list.map((a) => (a.id === id ? { ...a, status: saved.status } : a)),
      )
      return
    }

    const saved = await api(`/doctors/me/appointments/${id}`, {
      method: 'PUT',
      body: {
        patientId: patch.patientId,
        appointmentDate: `${patch.date}T${patch.time}:00`,
        reason: patch.reason,
      },
    })
    setAppointments((list) =>
      list.map((a) => (a.id === id ? appointmentFromApi(saved, a.duration) : a)),
    )
  }, [])

  // Saves the doctor-editable profile fields and returns the stored profile.
  const setProfile = useCallback(async (next) => {
    const saved = await api('/doctors/me', {
      method: 'PATCH',
      body: {
        name: next.name,
        phone: next.phone,
        office: next.office,
        email: next.email,
        workDays: next.workDays,
        firstAppointment: next.firstAppointment,
        lastAppointment: next.lastAppointment,
        visitLength: next.visitLength,
      },
    })
    const stored = profileFromApi(saved)
    setProfileState(stored)
    setAppointments((list) => list.map((a) => ({ ...a, duration: stored.visitLength })))
    return stored
  }, [])

  const patchProfile = useCallback((patch) => setProfileState((p) => ({ ...p, ...patch })), [])

  const addConsultation = useCallback(async ({ appointmentId, diagnosis, treatment, notes }) => {
    const saved = await api('/doctors/me/consultations', {
      method: 'POST',
      body: { appointmentId, diagnosis, treatment, notes },
    })
    const consultation = consultationFromApi(saved)
    setConsultations((list) => [consultation, ...list])
    return consultation
  }, [])

  const updateConsultation = useCallback(async (id, { diagnosis, treatment, notes }) => {
    const saved = await api(`/doctors/me/consultations/${id}`, {
      method: 'PUT',
      body: { diagnosis, treatment, notes },
    })
    const consultation = consultationFromApi(saved)
    setConsultations((list) => list.map((c) => (c.id === id ? consultation : c)))
    return consultation
  }, [])

  const consultationsFor = useCallback(
    (patientId) => consultations.filter((c) => c.patientId === patientId),
    [consultations],
  )

  const value = useMemo(
    () => ({
      appointments,
      patients,
      consultations,
      profile,
      setProfile,
      patchProfile,
      addAppointment,
      updateAppointment,
      addConsultation,
      updateConsultation,
      consultationsFor,
      reload: load,
    }),
    [
      appointments,
      patients,
      consultations,
      profile,
      setProfile,
      patchProfile,
      addAppointment,
      updateAppointment,
      addConsultation,
      updateConsultation,
      consultationsFor,
      load,
    ],
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
