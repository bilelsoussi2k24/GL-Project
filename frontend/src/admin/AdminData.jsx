import { createContext, useCallback, useContext, useMemo, useState } from 'react'

// Admin data, held in memory for this session (no backend yet).
// Everything starts empty: the admin adds doctors and fills in their own account.
// When a backend exists, load these from the API and call it inside the actions below.

const AdminContext = createContext(null)

export function AdminDataProvider({ children }) {
  const [doctors, setDoctors] = useState([])
  const [account, setAccount] = useState({ name: '', email: '' })

  const addDoctor = useCallback((data) => {
    setDoctors((list) => {
      // Staff IDs count up from DR-0001
      const nextNum = Math.max(0, ...list.map((d) => Number(d.staffId.slice(3)) || 0)) + 1
      return [...list, { ...data, id: `d${Date.now()}`, staffId: `DR-${String(nextNum).padStart(4, '0')}` }]
    })
  }, [])

  const updateDoctor = useCallback((id, patch) => {
    setDoctors((list) => list.map((d) => (d.id === id ? { ...d, ...patch } : d)))
  }, [])

  // Returns a function that puts the doctor back at the same position (for Undo).
  const removeDoctor = useCallback((id) => {
    let removed
    let index
    setDoctors((list) => {
      index = list.findIndex((d) => d.id === id)
      removed = list[index]
      return list.filter((d) => d.id !== id)
    })
    return () =>
      setDoctors((list) => {
        if (!removed || list.some((d) => d.id === removed.id)) return list
        const copy = [...list]
        copy.splice(index, 0, removed)
        return copy
      })
  }, [])

  const value = useMemo(
    () => ({ doctors, account, setAccount, addDoctor, updateDoctor, removeDoctor }),
    [doctors, account, addDoctor, updateDoctor, removeDoctor],
  )

  return <AdminContext.Provider value={value}>{children}</AdminContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAdminData() {
  const ctx = useContext(AdminContext)
  if (!ctx) throw new Error('useAdminData must be used inside <AdminDataProvider>')
  return ctx
}

// eslint-disable-next-line react-refresh/only-export-components
export const statusLabel = { active: 'Active', leave: 'On leave' }
