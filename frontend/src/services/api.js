// Thin JSON client for the Java API (backend/, port 8080).
// In development Vite forwards /api to it (see vite.config.js); in production
// server.js does the same. Requests carry the staff session token.

export const SESSION_KEY = 'haven.session'

// Reads the stored session from either storage. Kept here so api.js can attach
// the token without importing auth.js (which imports this module).
export function readSession() {
  try {
    const raw = localStorage.getItem(SESSION_KEY) ?? sessionStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const session = JSON.parse(raw)
    if (!session?.token) return null
    if (session.expiresAt && new Date(session.expiresAt).getTime() < Date.now()) {
      clearSession()
      return null
    }
    return session
  } catch {
    return null
  }
}

export function clearSession() {
  try {
    localStorage.removeItem(SESSION_KEY)
    sessionStorage.removeItem(SESSION_KEY)
  } catch {
    // Storage can be blocked; there is simply nothing to clear.
  }
}

function unauthorized() {
  clearSession()
  window.dispatchEvent(new Event('haven:unauthorized'))
}

/**
 * Calls the API. Throws an Error whose message is safe to show to the user.
 * The thrown error also carries `status` for callers that need it.
 */
export async function api(path, { method = 'GET', body, auth = true } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (auth) {
    const session = readSession()
    if (session?.token) headers.Authorization = `Bearer ${session.token}`
  }

  let res
  try {
    res = await fetch(`/api${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw Object.assign(new Error('Cannot reach the server. Check that the Java backend is running.'), { status: 0 })
  }

  if (res.status === 204) return null

  const data = await res.json().catch(() => null)
  if (!res.ok) {
    if (res.status === 401 && auth) {
      unauthorized()
      throw Object.assign(new Error(data?.error || 'Your session has expired. Sign in again.'), { status: 401 })
    }
    const message =
      data?.error ||
      (res.status === 502 || res.status === 503
        ? 'The server is unavailable. Check that the backend is running.'
        : `The request failed (${res.status}).`)
    throw Object.assign(new Error(message), { status: res.status })
  }
  return data
}
