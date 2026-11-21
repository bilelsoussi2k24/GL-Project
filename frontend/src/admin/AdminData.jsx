import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api } from '../services/api.js'
import { updateAccount } from '../services/auth.js'
import DataGate from '../components/DataGate.jsx'

// Administrator data, loaded from the Java API and MySQL.
// Every action calls the backend and then refreshes the affected data, so the
// screens always show what is actually stored.

const AdminContext = createContext(null)

export function AdminDataProvider({ children }) {
  const [doctors, setDoctors] = useState([])
  const [stats, setStats] = useState(null)
  const [activity, setActivity] = useState([])
  const [account, setAccountState] = useState({ name: '', email: '' })
  const [status, setStatus] = useState({ loading: true, error: '' })

  const load = useCallback(async (initial = false) => {
    if (initial) setStatus({ loading: true, error: '' })
    try {
      const [doctorList, statsData, activityData, me] = await Promise.all([
        api('/admin/doctors'),
        api('/admin/stats'),
        api('/admin/activity'),
        api('/auth/me'),
      ])
      setDoctors(doctorList)
      setStats(statsData)
      setActivity(activityData)
      setAccountState({ name: me.fullName ?? '', email: me.email })
      setStatus({ loading: false, error: '' })
    } catch (err) {
      if (err.status === 401) return
      setStatus({ loading: false, error: err.message })
    }
  }, [])

  useEffect(() => {
    load(true)
  }, [load])

  const refresh = useCallback(() => load(false), [load])

  // Creates the login account and doctor profile on the server.
  const addDoctor = useCallback(
    async (data) => {
      const created = await api('/admin/doctors', {
        method: 'POST',
        body: {
          firstName: data.firstName,
          lastName: data.lastName,
          specialty: data.specialty,
          phone: data.phone || null,
          office: data.office || null,
          email: data.email,
          password: data.password,
          status: data.status,
        },
      })
      await refresh()
      return created
    },
    [refresh],
  )

  // A status-only change uses the dedicated endpoint; anything else updates the profile.
  const updateDoctor = useCallback(
    async (id, patch) => {
      if (patch && Object.keys(patch).length === 1 && patch.status) {
        await api(`/admin/doctors/${id}/status`, { method: 'PATCH', body: { status: patch.status } })
      } else {
        await api(`/admin/doctors/${id}`, { method: 'PUT', body: patch })
      }
      await refresh()
    },
    [refresh],
  )

  // Deactivation keeps every medical record; the account can no longer sign in.
  const removeDoctor = useCallback(
    async (id) => {
      const result = await api(`/admin/doctors/${id}`, { method: 'DELETE' })
      await refresh()
      return result
    },
    [refresh],
  )

  const setAccount = useCallback(async (next) => {
    const saved = await updateAccount({ fullName: next.name, email: next.email })
    setAccountState({ name: saved.fullName, email: saved.email })
    return { name: saved.fullName, email: saved.email }
  }, [])

  const value = useMemo(
    () => ({ doctors, stats, activity, account, setAccount, addDoctor, updateDoctor, removeDoctor, refresh }),
    [doctors, stats, activity, account, setAccount, addDoctor, updateDoctor, removeDoctor, refresh],
  )

  if (status.loading || status.error) {
    return <DataGate error={status.error} onRetry={() => load(true)} />
  }
  return <AdminContext.Provider value={value}>{children}</AdminContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAdminData() {
  const ctx = useContext(AdminContext)
  if (!ctx) throw new Error('useAdminData must be used inside <AdminDataProvider>')
  return ctx
}

// eslint-disable-next-line react-refresh/only-export-components
export const statusLabel = { active: 'Active', leave: 'On leave', inactive: 'Deactivated' }
