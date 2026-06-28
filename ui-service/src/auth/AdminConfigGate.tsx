import { Navigate } from 'react-router-dom'
import { canAccessAdminConfigNav } from '@/auth/types'
import { useAuth } from '@/auth/useAuth'
import type { ReactNode } from 'react'

/** Hides direct URL access to admin config when role is not allowed (nav is also hidden). */
export function AdminConfigGate({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  if (!user || !canAccessAdminConfigNav(user.role)) {
    return <Navigate to="/dashboard" replace />
  }
  return <>{children}</>
}
