// Temporary mock login: the Java backend has no login endpoint yet, so any staff ID and password work.
// When it does, replace the body of `login` with a real API call, e.g.:
//   const session = await api('/auth/login', { method: 'POST', body: { role, id, password } })

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

// Mock: the Java backend has no password endpoint yet.
export async function changePassword(current, next) {
  await new Promise((r) => setTimeout(r, 700))
  if (!current || !next) throw new Error('Enter your current password.')
  return { passwordChangedAt: Date.now() }
}
