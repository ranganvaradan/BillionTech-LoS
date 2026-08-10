import { useEffect, useState } from 'react'
import { Link, NavLink, Navigate, Outlet, useNavigate } from 'react-router-dom'
import { getBorrowerDashboard, getBorrowerNotifications } from '@/api/borrowerPortal'
import { useAuth } from '@/auth/useAuth'
import { isBorrowerRole } from '@/auth/types'
import { BrandLogo } from '@/components/BrandLogo'
import {
  ApplicationsIcon,
  ApplyIcon,
  DashboardIcon,
  DocumentsIcon,
  InvoiceIcon,
  ProfileIcon,
  ProgramsIcon,
} from '@/components/SidebarNavIcons'
import { sidebarLinkClass } from '@/components/ui/btUtils'
import { PoweredByFooter } from '@/components/ui/PoweredByFooter'
import { COMPANY_DISPLAY_NAME } from '@/catalog/brand'

type NavItem = {
  to: string
  label: string
  icon: typeof DashboardIcon
  end?: boolean
}

const overviewNav: NavItem[] = [{ to: '/borrower/dashboard', label: 'Dashboard', icon: DashboardIcon, end: true }]

const programsNav: NavItem[] = [{ to: '/borrower/programs', label: 'Programs', icon: ProgramsIcon }]

const lendingNav: NavItem[] = [
  { to: '/borrower/apply', label: 'Apply for loan', icon: ApplyIcon },
  { to: '/borrower/applications', label: 'Loan applications', icon: ApplicationsIcon },
]

const accountNav: NavItem[] = [
  { to: '/borrower/documents', label: 'Documents', icon: DocumentsIcon },
  { to: '/borrower/profile', label: 'Profile', icon: ProfileIcon },
]

function userInitials(name: string) {
  const p = name.trim().split(/\s+/)
  if (p.length >= 2) return (p[0]![0]! + p[1]![0]!).toUpperCase()
  return name.slice(0, 2).toUpperCase() || '—'
}

function NavItems({ items }: { items: NavItem[] }) {
  return (
    <>
      {items.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          className={({ isActive }) => sidebarLinkClass(isActive)}
          end={item.end}
        >
          {({ isActive }) => (
            <>
              <item.icon active={isActive} />
              <span className="min-w-0 flex-1">{item.label}</span>
            </>
          )}
        </NavLink>
      ))}
    </>
  )
}

export function BorrowerPortalLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [hasDisbursedLoan, setHasDisbursedLoan] = useState(false)
  const [loanId, setLoanId] = useState<string | null>(null)
  const [invoiceDiscountingLinked, setInvoiceDiscountingLinked] = useState(false)
  const [purchaseBillLinked, setPurchaseBillLinked] = useState(false)
  const [salesBillLinked, setSalesBillLinked] = useState(false)
  const [purchaseOrderLinked, setPurchaseOrderLinked] = useState(false)
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
        setInvoiceDiscountingLinked(Boolean(d.invoiceDiscountingLinked))
        setPurchaseBillLinked(Boolean(d.purchaseBillDiscountingLinked ?? d.invoiceDiscountingLinked))
        setSalesBillLinked(Boolean(d.salesBillDiscountingLinked))
        setPurchaseOrderLinked(Boolean(d.purchaseOrderDiscountingLinked))
        const n = await getBorrowerNotifications()
        if (c) setNotifs(n)
      } catch {
        if (c) {
          setHasDisbursedLoan(false)
          setLoanId(null)
          setInvoiceDiscountingLinked(false)
          setPurchaseBillLinked(false)
          setSalesBillLinked(false)
          setPurchaseOrderLinked(false)
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
  if (user.passwordResetRequired) {
    return <Navigate to="/borrower/change-password" replace />
  }

  const overviewItems = invoiceDiscountingLinked ? [...overviewNav, ...programsNav] : overviewNav
  const invoiceNav: NavItem[] = []
  if (purchaseBillLinked) {
    invoiceNav.push({ to: '/borrower/invoice-discounting', label: 'Invoice discounting', icon: InvoiceIcon, end: true })
  }
  if (salesBillLinked) {
    invoiceNav.push({ to: '/borrower/sales-bill-discounting', label: 'Sales Bill Discounting', icon: InvoiceIcon })
  }
  if (purchaseOrderLinked) {
    invoiceNav.push({
      to: '/borrower/purchase-order-discounting',
      label: 'Purchase Order Discounting',
      icon: InvoiceIcon,
    })
  }
  if (purchaseBillLinked || salesBillLinked || purchaseOrderLinked) {
    invoiceNav.push({
      to: '/borrower/invoice-discounting/payments/cart',
      label: 'Payment cart',
      icon: InvoiceIcon,
    })
  }
  const lendingItems = invoiceDiscountingLinked ? [...lendingNav, ...invoiceNav] : lendingNav
  const accountItems: NavItem[] = invoiceDiscountingLinked
    ? [{ to: '/borrower/credit-notes', label: 'Credit notes', icon: InvoiceIcon }, ...accountNav]
    : accountNav

  return (
    <div className="bt-app-canvas bt-app-shell">
      <aside className="bt-sidebar-wide">
        <div className="bt-sidebar-wide-header">
          <BrandLogo variant="billiontech" tone="dark" height={26} />
          <p className="bt-sidebar-wide-subtitle">Borrower portal</p>
        </div>
        <nav className="bt-sidebar-wide-nav flex-1" aria-label="Borrower">
          <div className="mb-1">
            <div className="bt-sidebar-group-label">Overview</div>
            <NavItems items={overviewItems} />
          </div>
          <div className="mb-1">
            <div className="bt-sidebar-group-label">Lending</div>
            <NavItems items={lendingItems} />
          </div>
          <div className="mb-1">
            <div className="bt-sidebar-group-label">Account</div>
            <NavItems items={accountItems} />
          </div>
          {hasDisbursedLoan && loanId ? (
            <>
              <div className="bt-sidebar-group-label">After disbursement</div>
              <NavLink to={`/borrower/loans/${loanId}/repayment`} className={({ isActive }) => sidebarLinkClass(isActive)}>
                Repayment schedule
              </NavLink>
              <NavLink to={`/borrower/loans/${loanId}/statement`} className={({ isActive }) => sidebarLinkClass(isActive)}>
                Statement
              </NavLink>
              <NavLink to={`/borrower/loans/${loanId}/transactions`} className={({ isActive }) => sidebarLinkClass(isActive)}>
                Transactions
              </NavLink>
            </>
          ) : null}
        </nav>
        <div className="shrink-0 border-t border-[var(--bt-gray-200)] p-2">
          <Link to="/" className="bt-sidebar-link">
            Staff sign-in
          </Link>
        </div>
      </aside>
      <div className="bt-app-main">
        <header className="bt-app-header sticky top-0 z-30 shrink-0 bg-white/95 shadow-sm backdrop-blur">
          <div className="flex min-h-14 w-full flex-wrap items-center justify-between gap-3 px-6 py-2">
            <div className="order-2 min-w-0 flex-1 text-center sm:flex-[2]">
              <p className="truncate text-sm font-medium text-[var(--bt-gray-900)]">{COMPANY_DISPLAY_NAME}</p>
            </div>
            <div className="order-1 flex items-center gap-2 sm:flex-1">
              {notifs.length > 0 ? (
                <div className="relative">
                  <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" onClick={() => setNotifOpen((o) => !o)} aria-expanded={notifOpen}>
                    Notifications ({notifs.length})
                  </button>
                  {notifOpen ? (
                    <div className="absolute left-0 z-20 mt-1 w-80 max-w-[90vw] rounded-md border border-[var(--bt-gray-200)] bg-white p-2 shadow-lg">
                      <ul className="max-h-64 space-y-2 overflow-y-auto text-xs">
                        {notifs.map((n) => (
                          <li key={n.id} className="rounded border border-[var(--bt-gray-100)] p-2">
                            <div className="font-medium">{n.title}</div>
                            <div className="text-[var(--bt-gray-600)]">{n.message}</div>
                            {n.applicationId ? (
                              <Link to={`/borrower/applications/${n.applicationId}`} className="mt-1 inline-block underline" onClick={() => setNotifOpen(false)}>
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
            </div>
            <div className="order-3 flex items-center justify-end gap-2 sm:flex-1">
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-bt-primary text-xs font-semibold text-white" title={user.name}>
                {userInitials(user.name)}
              </div>
              <div className="hidden text-right sm:block">
                <div className="text-sm font-medium">{user.name}</div>
                <div className="text-xs text-[var(--bt-gray-500)]">{user.email}</div>
              </div>
              <button type="button" className="bt-btn bt-btn-secondary" onClick={() => { logout(); navigate('/borrower/login', { replace: true }) }}>
                Log out
              </button>
            </div>
          </div>
        </header>
        <main className="bt-main-content flex-1">
          <Outlet />
        </main>
        <PoweredByFooter />
      </div>
    </div>
  )
}
