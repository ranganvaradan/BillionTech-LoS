import type { ComponentType } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { canAccessAdminConfigNav } from '@/auth/types'
import { BrandLogo } from '@/components/BrandLogo'
import {
  ApplicationsIcon,
  DashboardIcon,
  ProgramsIcon,
  RulesIcon,
  ScorecardsIcon,
  UsersIcon,
} from '@/components/SidebarNavIcons'
import { sidebarLinkClass } from '@/components/ui/btUtils'
import { PoweredByFooter } from '@/components/ui/PoweredByFooter'
import {
  isAdministrationWorkspacePath,
  isApplicationsWorkspacePath,
  isPoliciesWorkspacePath,
  isProgramsWorkspacePath,
  showStagingDemoNav,
} from '@/nav/workspaceNav'
import { formatRoleLabel } from '@/lib/ux/presentationProfile'

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
  /** Custom active match for workspace parents. */
  isActivePath?: (pathname: string) => boolean
  /** When true, only ADMINISTRATOR | CREDIT_MANAGER (unchanged RBAC). */
  adminOnly?: boolean
}

type NavGroup = {
  label: string
  items: NavItem[]
}

const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Overview',
    items: [{ to: '/dashboard', label: 'Dashboard', icon: DashboardIcon, end: true }],
  },
  {
    label: 'Credit',
    items: [
      {
        to: '/applications',
        label: 'Applications',
        icon: ApplicationsIcon,
        isActivePath: isApplicationsWorkspacePath,
      },
      {
        to: '/plp/programs',
        label: 'Programs',
        icon: ProgramsIcon,
        isActivePath: isProgramsWorkspacePath,
      },
      {
        to: '/pg-settlements',
        label: 'Settlements',
        icon: RulesIcon,
      },
    ],
  },
  {
    label: 'Policy & Risk',
    items: [
      {
        to: '/credit-intelligence/policy-studio',
        label: 'Policies',
        icon: ScorecardsIcon,
        isActivePath: isPoliciesWorkspacePath,
      },
      {
        to: '/reports',
        label: 'Reports',
        icon: DashboardIcon,
        adminOnly: true,
      },
    ],
  },
  {
    label: 'Administration',
    items: [
      {
        to: '/administration',
        label: 'Administration',
        icon: UsersIcon,
        adminOnly: true,
        isActivePath: isAdministrationWorkspacePath,
      },
    ],
  },
]

export function MainLayout() {
  const { user, logout } = useAuth()
  const nav = useNavigate()
  const { pathname } = useLocation()
  const showAdmin = user ? canAccessAdminConfigNav(user.role) : false
  const institutionLabel = user?.institution?.trim() || 'Institution'
  const showStagingBadge = showStagingDemoNav()

  return (
    <div className="bt-app-canvas bt-app-shell">
      <aside className="bt-sidebar-wide">
        <div className="bt-sidebar-wide-header">
          <BrandLogo variant="billiontech" tone="dark" height={26} />
          <p className="bt-sidebar-wide-subtitle">Loan operations</p>
        </div>
        <nav className="bt-sidebar-wide-nav" aria-label="Main">
          {NAV_GROUPS.map((group) => {
            const items = group.items.filter((item) => !item.adminOnly || showAdmin)
            if (items.length === 0) return null
            return (
              <div key={group.label} className="mb-1">
                <div className="bt-sidebar-group-label">{group.label}</div>
                {items.map((item) => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.end}
                    onClick={(e) => {
                      if (item.to !== '/credit-intelligence/policy-studio') return
                      e.preventDefault()
                      nav('/credit-intelligence/policy-studio', {
                        state: { openPoliciesLanding: true, ts: Date.now() },
                      })
                    }}
                    className={() => {
                      const active = item.isActivePath
                        ? item.isActivePath(pathname)
                        : item.end
                          ? pathname === item.to
                          : pathname === item.to || pathname.startsWith(`${item.to}/`)
                      return sidebarLinkClass(active)
                    }}
                  >
                    {() => {
                      const active = item.isActivePath
                        ? item.isActivePath(pathname)
                        : item.end
                          ? pathname === item.to
                          : pathname === item.to || pathname.startsWith(`${item.to}/`)
                      return (
                        <>
                          <item.icon active={active} />
                          <span className="min-w-0 flex-1">{item.label}</span>
                        </>
                      )
                    }}
                  </NavLink>
                ))}
              </div>
            )
          })}
        </nav>
      </aside>
      <div className="bt-app-main">
        <header className="bt-app-header sticky top-0 z-30 shrink-0 bg-white/95 shadow-sm backdrop-blur">
          <div className="flex min-h-14 w-full flex-wrap items-center justify-between gap-3 px-6 py-2">
            <div className="min-w-0 flex-1">
              <div className="flex min-w-0 flex-wrap items-center gap-2">
                <p className="truncate text-sm font-semibold text-[var(--bt-gray-900)]" title={institutionLabel}>
                  {institutionLabel}
                </p>
                {showStagingBadge ? (
                  <span
                    className="shrink-0 rounded border border-amber-300 bg-amber-50 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-amber-900"
                    title="Staging / demo build"
                  >
                    Staging
                  </span>
                ) : null}
              </div>
            </div>
            <div className="flex items-center justify-end gap-2">
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
                    <div className="text-xs text-[var(--bt-gray-500)]">{formatRoleLabel(user.role)}</div>
                  </div>
                </>
              ) : null}
              <button
                type="button"
                className="bt-btn bt-btn-secondary"
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
        <main className="bt-main-content flex-1">
          <Outlet />
        </main>
        <PoweredByFooter />
      </div>
    </div>
  )
}
