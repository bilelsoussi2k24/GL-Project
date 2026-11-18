import { useEffect, useState } from 'react'
import { hospital as defaults } from '../config/hospital.js'

// Hospital details edited by the admin in Settings and shown on the public pages.
// Mock storage: saved in this browser's localStorage until the backend exists.
// Later, replace loadHospital/saveHospital with GET/PUT calls to your API.

const KEY = 'haven.hospital'
const EVENT = 'haven:hospital'

export function loadHospital() {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY))
    if (!saved) return defaults
    return { ...defaults, ...saved, visiting: { ...defaults.visiting, ...saved.visiting } }
  } catch {
    return defaults
  }
}

export function saveHospital(data) {
  try {
    localStorage.setItem(KEY, JSON.stringify(data))
  } catch {
    // Storage blocked: the change lasts until the page reloads.
  }
  window.dispatchEvent(new Event(EVENT))
}

// Current hospital details; re-renders when Settings saves (in this tab or another).
export function useHospital() {
  const [data, setData] = useState(loadHospital)
  useEffect(() => {
    const refresh = () => setData(loadHospital())
    window.addEventListener(EVENT, refresh)
    window.addEventListener('storage', refresh)
    return () => {
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
