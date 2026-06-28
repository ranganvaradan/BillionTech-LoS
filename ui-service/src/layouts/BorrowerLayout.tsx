import { Link, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { isBorrowerRole } from '@/auth/types'
import { BrandLogo } from '@/components/BrandLogo'

export function BorrowerLayout() {
  const { user, logout } = useAuth()
  const nav = useNavigate()

  return (
    <div className="bt-app-canvas min-h-screen">
      <header className="bt-app-header border-b bg-white">
        <div className="mx-auto flex max-w-3xl flex-wrap items-center justify-between gap-2 px-4 py-3">
          <div className="flex items-center gap-3">
            <BrandLogo variant="billiontech" tone="dark" className="text-sm" />
            <span className="text-sm font-semibold text-[var(--bt-gray-900)]">Loan application</span>
            {user && isBorrowerRole(user.role) ? (
              <span className="text-sm text-[var(--bt-gray-500)]">
                · {user.name} ({user.role.replaceAll('_', ' ')})
              </span>
            ) : null}
          </div>
          <div className="flex items-center gap-3 text-sm">
            {user && isBorrowerRole(user.role) ? (
              <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" onClick={() => { logout(); nav('/login', { replace: true }) }}>
                Log out
              </button>
            ) : null}
            <Link to="/login" className="text-[var(--bt-orange)] hover:underline">
              Staff sign-in
            </Link>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-3xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
