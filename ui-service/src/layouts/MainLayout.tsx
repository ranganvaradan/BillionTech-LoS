import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { canAccessAdminConfigNav } from '@/auth/types'

const COMPANY_NAME = 'Billionloans Financial Services Private Ltd.'

const linkClass = ({ isActive }: { isActive: boolean }) =>
  [
    'block rounded-md px-3 py-2 text-sm font-medium transition-colors',
    isActive
      ? 'bg-bl-primary text-white shadow-sm'
      : 'text-white/80 hover:bg-white/10 hover:text-white',
  ].join(' ')

function userInitials(name: string) {
  const p = name.trim().split(/\s+/)
  if (p.length >= 2) return (p[0]![0]! + p[1]![0]!).toUpperCase()
  return name.slice(0, 2).toUpperCase() || '—'
}

export function MainLayout() {
  const { user, logout } = useAuth()
  const nav = useNavigate()
  const showAdmin = user ? canAccessAdminConfigNav(user.role) : false

  return (
    <div className="min-h-screen bg-bl-canvas">
      <aside className="fixed left-0 top-0 z-40 flex h-screen w-60 flex-col border-r border-white/5 bg-bl-navy">
        <div className="shrink-0 border-b border-white/10 px-3 py-4">
          <div className="w-full max-w-[150px]">
            <div className="flex min-h-[2.25rem] max-h-11 w-full items-center" title="Billionloans">
              <img
                src="/brand/BillionLoans_Logo_Final_noBG.png"
                alt="Billionloans"
                className="h-auto max-h-9 w-full object-contain object-left drop-shadow-[0_1px_2px_rgba(0,0,0,0.25)]"
              />
            </div>
            <div className="mt-2 text-xs font-medium leading-tight text-white/50">Operations</div>
          </div>
        </div>
        <nav className="flex-1 space-y-0.5 overflow-y-auto p-2" aria-label="Main">
          <NavLink to="/dashboard" className={linkClass} end>
            Dashboard
          </NavLink>
          <NavLink to="/applications" className={linkClass}>
            Applications
          </NavLink>
          <NavLink to="/kyc" className={linkClass}>
            KYC in progress
          </NavLink>
          <NavLink to="/underwriting" className={linkClass}>
            Underwriting
          </NavLink>
          {showAdmin ? (
            <>
              <NavLink to="/workflows" className={linkClass}>
                Workflows
              </NavLink>
              <NavLink to="/integrations/provider-matrix" className={linkClass}>
                Integrations
              </NavLink>
              <NavLink to="/underwriting-rules" className={linkClass}>
                Underwriting rules
              </NavLink>
              <NavLink to="/underwriting-scorecards" className={linkClass}>
                Underwriting scorecards
              </NavLink>
              <NavLink to="/assignment-rules" className={linkClass}>
                Assignment rules
              </NavLink>
              <NavLink to="/users" className={linkClass}>
                Users
              </NavLink>
              <NavLink to="/user-role-mappings" className={linkClass}>
                User–role mappings
              </NavLink>
            </>
          ) : null}
        </nav>
      </aside>
      <div className="flex min-h-screen flex-col pl-60">
        <header className="sticky top-0 z-30 border-b border-slate-200/80 bg-white/95 shadow-sm backdrop-blur">
          <div className="mx-auto flex min-h-14 w-full max-w-7xl flex-wrap items-center justify-between gap-3 px-4 py-2 sm:px-6">
            <div className="min-w-0 flex-1 text-center sm:order-2 sm:flex-[2]">
              <p className="truncate text-sm font-medium text-slate-800">{COMPANY_NAME}</p>
            </div>
            <div className="order-1 flex w-full min-w-0 max-w-sm flex-1 sm:order-1 sm:w-auto">
              <label className="sr-only" htmlFor="los-global-search">
                Search
              </label>
              <input
                id="los-global-search"
                type="search"
                placeholder="Search"
                className="w-full rounded-md border border-slate-200 bg-slate-50/80 px-3 py-1.5 text-sm text-slate-800 placeholder:text-slate-400 focus:border-bl-primary focus:outline-none focus:ring-1 focus:ring-bl-primary/30"
                autoComplete="off"
              />
            </div>
            <div className="order-3 flex items-center justify-end gap-2 sm:order-3 sm:flex-1">
              {user ? (
                <>
                  <div
                    className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-bl-primary text-xs font-semibold text-white"
                    title={user.name}
                  >
                    {userInitials(user.name)}
                  </div>
                  <div className="hidden text-right sm:block">
                    <div className="text-sm font-medium text-slate-900">{user.name}</div>
                    <div className="text-xs text-slate-500">{user.role.replaceAll('_', ' ')}</div>
                  </div>
                </>
              ) : null}
              <button
                type="button"
                className="shrink-0 rounded-md border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-800 hover:bg-slate-50"
                onClick={() => {
                  logout()
                  nav('/login', { replace: true })
                }}
              >
                Log out
              </button>
            </div>
          </div>
        </header>
        <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 sm:px-6 sm:py-8">
          <Outlet />
        </main>
        <footer className="mt-auto border-t border-slate-200/90 bg-white py-1.5">
          <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-center gap-1.5 px-4 sm:px-6">
            <span className="text-[11px] text-slate-500">Powered by</span>
            <img
              src="/brand/BillionTech_Logo_Final.png"
              alt="BillionTech"
              className="h-[18px] w-auto object-contain"
            />
          </div>
        </footer>
      </div>
    </div>
  )
}
