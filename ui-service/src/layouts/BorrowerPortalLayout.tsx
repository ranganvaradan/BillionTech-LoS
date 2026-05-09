import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet } from 'react-router-dom'
import { getBorrowerDashboard, getBorrowerNotifications } from '@/api/borrowerPortal'
import { useAuth } from '@/auth/useAuth'
import { isBorrowerRole } from '@/auth/types'

const nav = [
  { to: '/borrower/dashboard', label: 'Dashboard' },
  { to: '/borrower/apply', label: 'Apply for loan' },
  { to: '/borrower/applications', label: 'Loan applications' },
  { to: '/borrower/documents', label: 'Signed documents' },
  { to: '/borrower/profile', label: 'Profile' },
] as const

export function BorrowerPortalLayout() {
  const { user, logout } = useAuth()
  const [hasDisbursedLoan, setHasDisbursedLoan] = useState(false)
  const [loanId, setLoanId] = useState<string | null>(null)
  const [notifOpen, setNotifOpen] = useState(false)
  const [notifs, setNotifs] = useState<Awaited<ReturnType<typeof getBorrowerNotifications>>>([])

  useEffect(() => {
    let c = true
    void (async () => {
      try {
        const d = await getBorrowerDashboard()
        if (!c) return
        setHasDisbursedLoan(d.activeLoanCount > 0)
        setLoanId(d.primaryDisbursedApplicationId)
        const n = await getBorrowerNotifications()
        if (c) setNotifs(n)
      } catch {
        if (c) {
          setHasDisbursedLoan(false)
          setLoanId(null)
          setNotifs([])
        }
      }
    })()
    return () => {
      c = false
    }
  }, [user?.userId])

  if (!user || !isBorrowerRole(user.role)) {
    return null
  }
  return (
    <div className="flex min-h-screen flex-col bg-bl-canvas">
      <header className="border-b border-slate-200 bg-white shadow-sm">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-3 px-4 py-3">
          <div className="flex min-w-0 items-center gap-2">
            <img
              src="/brand/BillionLoans_Logo_Final_noBG.png"
              alt="Billionloans"
              className="h-7 w-auto max-w-[140px] shrink-0 object-contain drop-shadow-sm"
            />
            <span className="truncate text-sm font-semibold text-bl-navy">Borrower portal</span>
          </div>
          {user ? (
            <span className="text-sm text-slate-600">
              {user.name} · {user.email}
            </span>
          ) : null}
          <div className="relative flex flex-wrap items-center gap-3 text-sm">
            {notifs.length > 0 ? (
              <div>
                <button
                  type="button"
                  className="rounded-md border border-slate-200 bg-slate-50 px-2 py-1 text-slate-800"
                  onClick={() => setNotifOpen((o) => !o)}
                  aria-expanded={notifOpen}
                >
                  Notifications ({notifs.length})
                </button>
                {notifOpen ? (
                  <div className="absolute right-0 z-20 mt-1 w-80 max-w-[90vw] rounded border border-slate-200 bg-white p-2 shadow-lg">
                    <ul className="max-h-64 space-y-2 overflow-y-auto text-xs text-slate-800">
                      {notifs.map((n) => (
                        <li key={n.id} className="rounded border border-slate-100 p-2">
                          <div className="font-medium">{n.title}</div>
                          <div className="text-slate-600">{n.message}</div>
                          {n.applicationId ? (
                            <Link
                              to={`/borrower/applications/${n.applicationId}`}
                              className="mt-1 inline-block text-slate-800 underline"
                              onClick={() => setNotifOpen(false)}
                            >
                              Open application
                            </Link>
                          ) : null}
                        </li>
                      ))}
                    </ul>
                  </div>
                ) : null}
              </div>
            ) : null}
            <Link to="/" className="text-slate-500 underline">
              Staff sign-in
            </Link>
            <button
              type="button"
              className="text-slate-700 underline"
              onClick={() => {
                logout()
                window.location.href = '/borrower/login'
              }}
            >
              Log out
            </button>
          </div>
        </div>
        <nav className="border-t border-slate-100 bg-white">
          <ul className="mx-auto flex max-w-5xl flex-wrap gap-1 px-2 py-2 text-sm">
            {nav.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  className={({ isActive }) =>
                    [
                      'block rounded-md px-3 py-1.5',
                      isActive ? 'bg-bl-primary text-white' : 'text-slate-700 hover:bg-slate-100',
                    ].join(' ')
                  }
                  end={item.to === '/borrower/dashboard'}
                >
                  {item.label}
                </NavLink>
              </li>
            ))}
            {hasDisbursedLoan && loanId ? (
              <>
                <li>
                  <span className="px-2 text-xs font-medium uppercase text-slate-400">After disbursement</span>
                </li>
                <li>
                  <NavLink
                    to={`/borrower/loans/${loanId}/repayment`}
                    className={({ isActive }) =>
                      isActive
                        ? 'block rounded-md bg-bl-primary px-3 py-1.5 text-white'
                        : 'block rounded-md px-3 py-1.5 text-slate-700 hover:bg-slate-100'
                    }
                  >
                    Repayment schedule
                  </NavLink>
                </li>
                <li>
                  <NavLink
                    to={`/borrower/loans/${loanId}/statement`}
                    className={({ isActive }) =>
                      isActive
                        ? 'block rounded-md bg-bl-primary px-3 py-1.5 text-white'
                        : 'block rounded-md px-3 py-1.5 text-slate-700 hover:bg-slate-100'
                    }
                  >
                    Statement
                  </NavLink>
                </li>
                <li>
                  <NavLink
                    to={`/borrower/loans/${loanId}/transactions`}
                    className={({ isActive }) =>
                      isActive
                        ? 'block rounded-md bg-bl-primary px-3 py-1.5 text-white'
                        : 'block rounded-md px-3 py-1.5 text-slate-700 hover:bg-slate-100'
                    }
                  >
                    Transactions
                  </NavLink>
                </li>
              </>
            ) : null}
          </ul>
        </nav>
      </header>
      <main className="mx-auto max-w-5xl flex-1 px-4 py-8">
        <Outlet />
      </main>
      <footer className="mt-auto border-t border-slate-200/90 bg-white py-1.5">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center justify-center gap-1.5 px-4">
          <span className="text-[11px] text-slate-500">Powered by</span>
          <img
            src="/brand/BillionTech_Logo_Final.png"
            alt="BillionTech"
            className="h-[18px] w-auto object-contain"
          />
        </div>
      </footer>
    </div>
  )
}
