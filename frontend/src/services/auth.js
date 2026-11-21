import { api, SESSION_KEY, readSession, clearSession } from './api.js'

// Real authentication against the Java API. The server issues an opaque token,
// stores only its hash, and enforces expiry and role checks on every request.
// The session below mirrors the server response for the UI.

function store(session, remember) {
  try {
    clearSession()
    const target = remember ? localStorage : sessionStorage
    target.setItem(SESSION_KEY, JSON.stringify(session))
  } catch {
    // Storage can be blocked (private mode); the session then lasts for this page only.
  }
}

export async function login({ role, email, password, remember }) {
  if (!email || !password) throw new Error('Enter your email and password.')

  const data = await api('/auth/login', {
    method: 'POST',
    auth: false,
    body: { email: email.trim(), password, role },
  })

  const session = {
    token: data.token,
    expiresAt: data.expiresAt,
    role: String(data.user.role).toLowerCase(),
    id: data.user.id,
    email: data.user.email,
    name: data.user.fullName,
    doctorId: data.user.doctorId,
    signedInAt: Date.now(),
  }
  store(session, remember)
  return session
}

export function getSession() {
  return readSession()
}

export async function logout() {
  try {
    if (readSession()) await api('/auth/logout', { method: 'POST' })
  } catch {
    // The token may already be invalid; clearing the local session is what matters.
  } finally {
    clearSession()
  }
}

export function changePassword(current, next) {
  return api('/auth/password', {
    method: 'POST',
    body: { currentPassword: current, newPassword: next },
  })
}

export function updateAccount({ fullName, email }) {
  return api('/auth/account', { method: 'PATCH', body: { fullName, email } })
}
