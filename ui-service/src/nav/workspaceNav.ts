/**
 * UX-2 navigation helpers — path matching and workspace secondary nav only.
 * Does not change routes, RBAC, or backend behaviour.
 */

export type WorkspaceNavItem = {
  to: string
  label: string
  end?: boolean
}

/** Staging/demo UI affordances (Demo Samples, STAGING badge). Set on staging UI builds. */
export function showStagingDemoNav(): boolean {
  if (import.meta.env.VITE_STAGING_DEMO === 'true') return true
  if (import.meta.env.DEV) return true
  return false
}

export function pathStartsWith(pathname: string, prefix: string): boolean {
  return pathname === prefix || pathname.startsWith(`${prefix}/`)
}

export function isApplicationsWorkspacePath(pathname: string): boolean {
  return (
    pathStartsWith(pathname, '/applications') ||
    pathname === '/borrower-submissions' ||
    pathname === '/kyc' ||
    pathname === '/underwriting'
  )
}

export function isProgramsWorkspacePath(pathname: string): boolean {
  return pathStartsWith(pathname, '/plp')
}

export function isPoliciesWorkspacePath(pathname: string): boolean {
  return (
    pathStartsWith(pathname, '/credit-intelligence/policy-studio') ||
    pathStartsWith(pathname, '/credit-intelligence/policy-catalogue') ||
    pathStartsWith(pathname, '/credit-intelligence/dual-run') ||
    pathStartsWith(pathname, '/credit-intelligence/applications') ||
    pathStartsWith(pathname, '/credit-intelligence/workspace')
  )
}

export function isAdministrationWorkspacePath(pathname: string): boolean {
  return (
    pathStartsWith(pathname, '/administration') ||
    pathStartsWith(pathname, '/workflows') ||
    pathStartsWith(pathname, '/data-parameters') ||
    pathStartsWith(pathname, '/product-configuration') ||
    pathStartsWith(pathname, '/integrations') ||
    pathStartsWith(pathname, '/underwriting-rules') ||
    pathStartsWith(pathname, '/underwriting-scorecards') ||
    pathStartsWith(pathname, '/repayment-config') ||
    pathStartsWith(pathname, '/anchor-rating-templates') ||
    pathStartsWith(pathname, '/assignment-rules') ||
    pathStartsWith(pathname, '/users') ||
    pathStartsWith(pathname, '/user-role-mappings') ||
    pathStartsWith(pathname, '/application-deletions') ||
    pathStartsWith(pathname, '/audit') ||
    pathStartsWith(pathname, '/credit-intelligence/p2-validation') ||
    pathStartsWith(pathname, '/credit-intelligence/validation')
  )
}

export function applicationsWorkspaceItems(opts: { hideUnderwriting: boolean }): WorkspaceNavItem[] {
  const items: WorkspaceNavItem[] = [
    { to: '/applications', label: 'All Applications', end: true },
    { to: '/borrower-submissions', label: 'Borrower Submitted' },
    { to: '/kyc', label: 'KYC' },
  ]
  if (!opts.hideUnderwriting) {
    items.push({ to: '/underwriting', label: 'Credit Assessment' })
  }
  return items
}

export const PROGRAMS_WORKSPACE_ITEMS: WorkspaceNavItem[] = [
  { to: '/plp/programs', label: 'Programs' },
  { to: '/plp/program-approvals', label: 'Program Approvals' },
]

export function policiesWorkspaceItems(opts: { showDemoSamples: boolean }): WorkspaceNavItem[] {
  const items: WorkspaceNavItem[] = [
    { to: '/credit-intelligence/policy-studio', label: 'Policy Studio' },
    { to: '/credit-intelligence/policy-catalogue', label: 'Scheduled Policies' },
    { to: '/credit-intelligence/dual-run', label: 'Compare Impact' },
  ]
  if (opts.showDemoSamples) {
    items.push({ to: '/credit-intelligence/applications', label: 'Demo Samples' })
  }
  return items
}

export type AdminNavGroup = {
  label: string
  helper?: string
  items: WorkspaceNavItem[]
}

/**
 * Administration workspace categories (page grouping / sibling context only).
 * People & Access kept separate so Users ≠ User–role Mappings.
 * Governance kept separate from Platform configuration.
 */
export const ADMINISTRATION_NAV_GROUPS: AdminNavGroup[] = [
  {
    label: 'Decision Configuration',
    helper: 'Current LOS production configuration',
    items: [
      { to: '/underwriting-rules', label: 'Live Underwriting Rules' },
      { to: '/underwriting-scorecards', label: 'Live Scorecards' },
      { to: '/product-configuration', label: 'Product Configuration' },
    ],
  },
  {
    label: 'Credit Setup',
    items: [
      { to: '/repayment-config', label: 'Repayment Defaults' },
      { to: '/anchor-rating-templates', label: 'Anchor Rating Templates' },
      { to: '/assignment-rules', label: 'Assignment Rules' },
    ],
  },
  {
    label: 'People & Access',
    items: [
      { to: '/users', label: 'Users' },
      { to: '/user-role-mappings', label: 'User-role Mappings' },
    ],
  },
  {
    label: 'Platform',
    items: [
      { to: '/data-parameters', label: 'Data & Parameters' },
      { to: '/workflows', label: 'Workflows' },
      { to: '/integrations/provider-matrix', label: 'Integrations' },
    ],
  },
  {
    label: 'Governance',
    items: [
      { to: '/audit', label: 'Audit Trail' },
      { to: '/application-deletions', label: 'Application Deletions' },
    ],
  },
  {
    label: 'Technical',
    items: [
      { to: '/credit-intelligence/p2-validation', label: 'P2 Validation' },
      { to: '/credit-intelligence/validation', label: 'Staging Readiness' },
    ],
  },
]

export const DEFAULT_ADMIN_CATEGORY = 'Credit Setup'
export const ADMIN_CATEGORY_STORAGE_KEY = 'los_admin_workspace_category_v1'

export function administrationSiblingItems(pathname: string): WorkspaceNavItem[] {
  for (const group of ADMINISTRATION_NAV_GROUPS) {
    if (group.items.some((i) => pathStartsWith(pathname, i.to))) {
      return group.items
    }
  }
  return []
}

export function administrationGroupLabel(pathname: string): string | null {
  for (const group of ADMINISTRATION_NAV_GROUPS) {
    if (group.items.some((i) => pathStartsWith(pathname, i.to))) {
      return group.label
    }
  }
  if (pathStartsWith(pathname, '/administration')) return null
  return null
}
