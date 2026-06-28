import type { ComponentType } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { canAccessAdminConfigNav } from '@/auth/types'
import { BrandLogo } from '@/components/BrandLogo'
import {
  ApplicationsIcon,
  AssignmentIcon,
  DashboardIcon,
  IntegrationsIcon,
  KycIcon,
  MappingsIcon,
  ProgramsIcon,
  RulesIcon,
  ScorecardsIcon,
  UnderwritingIcon,
  UsersIcon,
  WorkflowsIcon,
} from '@/components/SidebarNavIcons'
import { sidebarLinkClass } from '@/components/ui/btUtils'
import { PoweredByFooter } from '@/components/ui/PoweredByFooter'

const COMPANY_NAME = 'Credinnov'

function userInitials(name: string) {
  const p = name.trim().split(/\s+/)
  if (p.length >= 2) return (p[0]![0]! + p[1]![0]!).toUpperCase()
  return name.slice(0, 2).toUpperCase() || '—'
}

type NavItem = {
  to: string
  label: string
  icon: ComponentType<{ active?: boolean }>
  end?: boolean
}

type NavGroup = {
  label: string
  items: NavItem[]
  adminOnly?: boolean
}

const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Overview',
    items: [{ to: '/dashboard', label: 'Dashboard', icon: DashboardIcon, end: true }],
  },
  {
    label: 'Operations',
    items: [
      { to: '/applications', label: 'Applications', icon: ApplicationsIcon },
      { to: '/kyc', label: 'KYC in progress', icon: KycIcon },
      { to: '/underwriting', label: 'Underwriting', icon: UnderwritingIcon },
      { to: '/plp/programs', label: 'PLP Programs', icon: ProgramsIcon },
      { to: '/pg-settlements', label: 'PG settlements', icon: RulesIcon },
    ],
  },
  {
    label: 'Configuration',
    adminOnly: true,
    items: [
      { to: '/workflows', label: 'Workflows', icon: WorkflowsIcon },
      { to: '/integrations/provider-matrix', label: 'Integrations', icon: IntegrationsIcon },
      { to: '/underwriting-rules', label: 'Underwriting rules', icon: RulesIcon },
      { to: '/repayment-config', label: 'Repayment defaults', icon: RulesIcon },
      { to: '/underwriting-scorecards', label: 'Underwriting scorecards', icon: ScorecardsIcon },
      { to: '/anchor-rating-templates', label: 'Anchor rating templates', icon: ScorecardsIcon },
      { to: '/assignment-rules', label: 'Assignment rules', icon: AssignmentIcon },
      { to: '/users', label: 'Users', icon: UsersIcon },
      { to: '/user-role-mappings', label: 'User–role mappings', icon: MappingsIcon },
      { to: '/application-deletions', label: 'Application deletions', icon: RulesIcon },
    ],
  },
]

export function MainLayout() {
  const { user, logout } = useAuth()
  const nav = useNavigate()
  const showAdmin = user ? canAccessAdminConfigNav(user.role) : false

  return (
    <div className="bt-app-canvas bt-app-shell">
      <aside className="bt-sidebar-wide">
        <div className="bt-sidebar-wide-header">
          <BrandLogo variant="billiontech" tone="dark" height={26} />
          <p className="bt-sidebar-wide-subtitle">Operations</p>
        </div>
        <nav className="bt-sidebar-wide-nav" aria-label="Main">
          {NAV_GROUPS.filter((g) => !g.adminOnly || showAdmin).map((group) => (
            <div key={group.label} className="mb-1">
              <div className="bt-sidebar-group-label">{group.label}</div>
              {group.items.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) => sidebarLinkClass(isActive)}
                >
                  {({ isActive }) => (
                    <>
                      <item.icon active={isActive} />
                      <span className="min-w-0 flex-1">{item.label}</span>
                    </>
                  )}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
      </aside>
      <div className="bt-app-main">
        <header className="bt-app-header sticky top-0 z-30 shrink-0 bg-white/95 shadow-sm backdrop-blur">
          <div className="flex min-h-14 w-full flex-wrap items-center justify-between gap-3 px-6 py-2">
            <div className="min-w-0 flex-1 text-center sm:order-2 sm:flex-[2]">
              <p className="truncate text-sm font-medium text-[var(--bt-gray-900)]">{COMPANY_NAME}</p>
            </div>
            <div className="order-1 flex w-full min-w-0 max-w-sm flex-1 sm:order-1 sm:w-auto">
              <label className="sr-only" htmlFor="los-global-search">
                Search
              </label>
              <input
                id="los-global-search"
                type="search"
                placeholder="Search"
                className="bt-input w-full"
                autoComplete="off"
              />
            </div>
            <div className="order-3 flex items-center justify-end gap-2 sm:order-3 sm:flex-1">
              {user ? (
                <>
                  <div
                    className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-bt-primary text-xs font-semibold text-white"
                    title={user.name}
                  >
                    {userInitials(user.name)}
                  </div>
                  <div className="hidden text-right sm:block">
                    <div className="text-sm font-medium text-[var(--bt-gray-900)]">{user.name}</div>
                    <div className="text-xs text-[var(--bt-gray-500)]">{user.role.replaceAll('_', ' ')}</div>
                  </div>
                </>
              ) : null}
              <button type="button" className="bt-btn bt-btn-secondary" onClick={() => { logout(); nav('/login', { replace: true }) }}>
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