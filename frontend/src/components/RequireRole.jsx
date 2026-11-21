import { Navigate, useLocation } from 'react-router-dom'
import { getSession } from '../services/auth.js'

// Sends people to the staff login unless they are signed in with `role`.
// The backend repeats this check on every request, so hiding a page is never
// the only protection.
export default function RequireRole({ role, children }) {
  const location = useLocation()
  const session = getSession()
  if (session?.role !== role) {
    return <Navigate to="/login" replace state={{ from: location.pathname, role }} />
  }
  return children
}
