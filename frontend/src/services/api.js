// Calls the Java backend (GL_project/backend, HospitalServer on port 8080).
// In development Vite forwards /api to it (see vite.config.js).

export async function api(path, { method = 'GET', body } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  let res
  try {
    res = await fetch(`/api${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  } catch {
    throw new Error('Cannot reach the server. Check that the backend is running and try again.')
  }

  if (res.status === 204) return null
  const data = await res.json().catch(() => null)
  if (!res.ok) {
    if (res.status >= 500 && !data?.error) throw new Error('Cannot reach the server. Check that the backend is running and try again.')
    throw new Error(data?.error || 'Something went wrong. Try again.')
  }
  return data
}
