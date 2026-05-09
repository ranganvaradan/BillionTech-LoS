import { Link, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { isBorrowerRole } from '@/auth/types'

export function BorrowerLayout() {
  const { user, logout } = useAuth()
  const nav = useNavigate()

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-3xl flex-wrap items-center justify-between gap-2 px-4 py-3">
          <div>
            <span className="text-sm font-semibold text-slate-900">Loan application</span>
            {user && isBorrowerRole(user.role) ? (
              <span className="ml-2 text-sm text-slate-600">
                · {user.name} ({user.role.replaceAll('_', ' ')})
              </span>
            ) : null}
          </div>
          <div className="flex items-center gap-3 text-sm">
            {user && isBorrowerRole(user.role) ? (
              <button
                type="button"
                className="text-slate-700 underline"
                onClick={() => {
                  logout()
                  nav('/login', { replace: true })
                }}
              >
                Log out
              </button>
            ) : null}
            <Link to="/login" className="text-slate-600 underline">
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
