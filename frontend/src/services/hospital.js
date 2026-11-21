import { useEffect, useState } from 'react'
import { api } from './api.js'
import { hospital as defaults } from '../config/hospital.js'

// Hospital details shown on the public page. They live in MySQL
// (hospital_settings) and are edited by the administrator in Settings.

const EVENT = 'haven:hospital'
let cache = null

export function loadHospital() {
  return cache ?? defaults
}

export async function fetchHospital() {
  try {
    const data = await api('/public/hospital', { auth: false })
    cache = data
    return data
  } catch {
    return loadHospital()
  }
}

/** Saves the hospital details (administrator only). */
export async function saveHospital(data) {
  const saved = await api('/admin/settings', { method: 'PUT', body: data })
  cache = saved
  window.dispatchEvent(new Event(EVENT))
  return saved
}

// Current hospital details; re-renders when Settings saves (in this tab or another).
export function useHospital() {
  const [data, setData] = useState(loadHospital)
  useEffect(() => {
    let alive = true
    fetchHospital().then((value) => alive && setData(value))
    const refresh = () => setData(loadHospital())
    window.addEventListener(EVENT, refresh)
    window.addEventListener('storage', refresh)
    return () => {
      alive = false
      window.removeEventListener(EVENT, refresh)
      window.removeEventListener('storage', refresh)
    }
  }, [])
  return data
}

// "Mon–Sun, 10:00 to 19:00", or the placeholder label while hours are not set.
export function visitingLabel(h) {
  const { start, end, label } = h.visiting
  return start && end ? `Mon–Sun, ${start} to ${end}` : label
}
