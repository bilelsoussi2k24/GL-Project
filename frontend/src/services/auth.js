// Temporary mock login so the frontend works before the backend exists.
// Replace the body of `login` with a real API call, e.g.:
//   const res = await fetch('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ role, id, password }) })
//   if (!res.ok) throw new Error('Wrong staff ID or password.')
//   const session = await res.json()

const KEY = 'haven.session'

export async function login({ role, id, password, remember }) {
  await new Promise((r) => setTimeout(r, 900)) // simulate network delay
  if (!id || !password) throw new Error('Enter your staff ID and password.')

  const session = { role, id, signedInAt: Date.now() }
  try {
    const store = remember ? localStorage : sessionStorage
    store.setItem(KEY, JSON.stringify(session))
  } catch {
    // Storage can be blocked (private mode); the session just won't persist.
  }
  return session
}

export function getSession() {
  try {
    const raw = localStorage.getItem(KEY) ?? sessionStorage.getItem(KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export function logout() {
  try {
    localStorage.removeItem(KEY)
    sessionStorage.removeItem(KEY)
  } catch {
    /* ignore */
  }
}
