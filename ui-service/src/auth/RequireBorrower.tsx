import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './useAuth'
import { isBorrowerRole } from './types'

/**
 * Enforces a logged-in user with BORROWER role for borrower portal routes.
 */
export function RequireBorrower({ children }: { children: React.ReactNode }) {
  const { user } = useAuth()
  const location = useLocation()

  if (!user) {
    return <Navigate to="/borrower/login" replace state={{ from: `${location.pathname}${location.search || ''}` }} />
  }
  if (!isBorrowerRole(user.role)) {
    return <Navigate to="/login" replace />
  }
  return <>{children}</>
}
