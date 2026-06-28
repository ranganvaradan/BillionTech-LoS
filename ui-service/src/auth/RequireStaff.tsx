import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import type { ReactNode } from 'react'

/** Staff-only area: borrowers are redirected to the borrower experience. */
export function RequireStaff({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const loc = useLocation()
  if (!user) {
    return <Navigate to="/login" replace state={{ from: `${loc.pathname}${loc.search || ''}` }} />
  }
  if (user.role === 'BORROWER') {
    return <Navigate to="/borrower" replace />
  }
  if (user.passwordResetRequired) {
    return <Navigate to="/change-password" replace />
  }
  return <>{children}</>
}
