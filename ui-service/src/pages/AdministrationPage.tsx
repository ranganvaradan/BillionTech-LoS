import { useEffect, useMemo, useState, type ComponentType } from 'react'
import { Link } from 'react-router-dom'
import { PageHeader } from '@/components/PageHeader'
import {
  AssignmentIcon,
  DashboardIcon,
  DocumentsIcon,
  IntegrationsIcon,
  MappingsIcon,
  RulesIcon,
  ScorecardsIcon,
  UnderwritingIcon,
  UsersIcon,
  WorkflowsIcon,
} from '@/components/SidebarNavIcons'
import {
  ADMIN_CATEGORY_STORAGE_KEY,
  DEFAULT_ADMIN_CATEGORY,
  showStagingDemoNav,
} from '@/nav/workspaceNav'

type AdminItemMeta = {
  to: string
  label: string
  description: string
  icon: ComponentType<{ active?: boolean }>
  /** Visual cue only — no new confirmation behaviour. */
  sensitive?: boolean
}

type AdminCategoryMeta = {
  id: string
  label: string
  helper?: string
  stagingOnly?: boolean
  items: AdminItemMeta[]
}

const ADMIN_CATEGORIES: AdminCategoryMeta[] = [
  {
    id: 'Decision Configuration',
    label: 'Decision Configuration',
    helper: 'Current LOS production configuration — not Policy Studio drafts.',
    items: [
      {
        to: '/underwriting-rules',
        label: 'Live Underwriting Rules',
        description: 'Current LOS production decision rules',
        icon: RulesIcon,
      },
      {
        to: '/underwriting-scorecards',
        label: 'Live Scorecards',
        description: 'Current LOS production scorecards',
        icon: ScorecardsIcon,
      },
      {
        to: '/customer-categories',
        label: 'Customer Categories',
        description: 'Lender matching criteria → Policy Set (not live routing yet)',
        icon: UnderwritingIcon,
      },
      {
        to: '/policy-sets',
        label: 'Policy Sets',
        description: 'Compose one underwriting rule set + scorecard for categories',
        icon: RulesIcon,
      },
      {
        to: '/product-configuration',
        label: 'Product Configuration',
        description: 'Compose Product → Workflow → Live Rules → Scorecard and check readiness',
        icon: DashboardIcon,
      },
    ],
  },
  {
    id: 'Credit Setup',
    label: 'Credit Setup',
    items: [
      {
        to: '/repayment-config',
        label: 'Repayment Defaults',
        description: 'Global repayment mechanism per LOS loan product',
        icon: RulesIcon,
      },
      {
        to: '/anchor-rating-templates',
        label: 'Anchor Rating Templates',
        description: 'Due-diligence questions and rating bands for anchor onboarding',
        icon: ScorecardsIcon,
      },
      {
        to: '/assignment-rules',
        label: 'Assignment Rules',
        description: 'Configure how applications are assigned for review',
        icon: AssignmentIcon,
      },
    ],
  },
  {
    id: 'People & Access',
    label: 'People & Access',
    items: [
      {
        to: '/users',
        label: 'Users',
        description: 'Local LOS users for assignment and role mapping',
        icon: UsersIcon,
      },
      {
        to: '/user-role-mappings',
        label: 'User-role Mappings',
        description: 'Who can be assigned by product, segment, amount, and geography',
        icon: MappingsIcon,
      },
    ],
  },
  {
    id: 'Platform',
    label: 'Platform',
    items: [
      {
        to: '/data-parameters',
        label: 'Data & Parameters',
        description: 'CanonicalParameterRegistry — what the institution can know',
        icon: DocumentsIcon,
      },
      {
        to: '/workflows',
        label: 'Workflows',
        description: 'KYC and bureau step templates by product and intake',
        icon: WorkflowsIcon,
      },
      {
        to: '/integrations/provider-matrix',
        label: 'Integrations',
        description: 'Provider priority and fallback matrix',
        icon: IntegrationsIcon,
      },
    ],
  },
  {
    id: 'Governance',
    label: 'Governance',
    items: [
      {
        to: '/audit',
        label: 'Audit Trail',
        description: 'Application events, admin changes, and integration logs',
        icon: DocumentsIcon,
      },
      {
        to: '/application-deletions',
        label: 'Application Deletions',
        description: 'Controlled record of permanently deleted borrower applications',
        icon: UnderwritingIcon,
        sensitive: true,
      },
    ],
  },
  {
    id: 'Technical',
    label: 'Technical',
    helper: 'Staging diagnostics — not day-to-day credit administration.',
    stagingOnly: true,
    items: [
      {
        to: '/credit-intelligence/p2-validation',
        label: 'P2 Validation',
        description: 'Shadow-only certification of policy routing against stored applications',
        icon: DashboardIcon,
      },
      {
        to: '/credit-intelligence/validation',
        label: 'Staging Readiness',
        description: 'Confirm staging review health before Credit Manager walkthroughs',
        icon: DashboardIcon,
      },
    ],
  },
]

function ChevronIcon() {
  return (
    <svg className="h-4 w-4 shrink-0 text-[var(--bt-gray-400)]" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2} aria-hidden>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
    </svg>
  )
}

function availableCategoryIds(includeTechnical: boolean): string[] {
  return ADMIN_CATEGORIES.filter((c) => !c.stagingOnly || includeTechnical).map((c) => c.id)
}

function readStoredCategory(availableIds: string[]): string {
  try {
    const raw = window.localStorage.getItem(ADMIN_CATEGORY_STORAGE_KEY)
    if (raw && availableIds.includes(raw)) return raw
  } catch {
    /* ignore */
  }
  if (availableIds.includes(DEFAULT_ADMIN_CATEGORY)) return DEFAULT_ADMIN_CATEGORY
  return availableIds[0] ?? DEFAULT_ADMIN_CATEGORY
}

/**
 * Compact Administration workspace (UX-2A.1).
 * Routes remain gated by AdminConfigGate / existing permissions on each destination.
 */
export function AdministrationPage() {
  const showTechnical = showStagingDemoNav()

  const categories = useMemo(
    () => ADMIN_CATEGORIES.filter((c) => !c.stagingOnly || showTechnical),
    [showTechnical],
  )

  const [selectedId, setSelectedId] = useState(() =>
    typeof window !== 'undefined' ? readStoredCategory(availableCategoryIds(showStagingDemoNav())) : DEFAULT_ADMIN_CATEGORY,
  )

  useEffect(() => {
    const ids = categories.map((c) => c.id)
    if (!ids.includes(selectedId)) {
      setSelectedId(ids.includes(DEFAULT_ADMIN_CATEGORY) ? DEFAULT_ADMIN_CATEGORY : ids[0]!)
    }
  }, [categories, selectedId])

  function selectCategory(id: string) {
    setSelectedId(id)
    try {
      window.localStorage.setItem(ADMIN_CATEGORY_STORAGE_KEY, id)
    } catch {
      /* ignore */
    }
  }

  const selected = categories.find((c) => c.id === selectedId) ?? categories[0]

  return (
    <div className="space-y-4">
      <PageHeader
        title="Administration"
        description="Credit setup, people and access, platform integrations, and governance. Technical tools stay under Technical when staging is enabled."
      />

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:gap-6">
        <nav className="shrink-0 lg:w-52" aria-label="Administration categories">
          <div className="flex gap-1 overflow-x-auto pb-1 lg:flex-col lg:overflow-visible lg:pb-0">
            {categories.map((cat) => {
              const active = cat.id === selected?.id
              return (
                <button
                  key={cat.id}
                  type="button"
                  onClick={() => selectCategory(cat.id)}
                  className={[
                    'flex min-w-max items-center gap-2 rounded-md px-3 py-2 text-left text-sm transition-colors lg:min-w-0 lg:w-full',
                    active
                      ? 'bg-[var(--bt-orange-light)] font-semibold text-[var(--bt-gray-900)] ring-1 ring-[var(--bt-orange-border)]'
                      : 'text-[var(--bt-gray-600)] hover:bg-[var(--bt-gray-50)] hover:text-[var(--bt-gray-900)]',
                    cat.stagingOnly ? 'text-[var(--bt-gray-500)]' : '',
                  ]
                    .filter(Boolean)
                    .join(' ')}
                  aria-current={active ? 'true' : undefined}
                >
                  <span className="min-w-0 flex-1 truncate">{cat.label}</span>
                  {cat.stagingOnly && showTechnical ? (
                    <span className="shrink-0 rounded border border-amber-300 bg-amber-50 px-1 py-0.5 text-[9px] font-semibold uppercase tracking-wide text-amber-900">
                      Staging
                    </span>
                  ) : null}
                </button>
              )
            })}
          </div>
        </nav>

        <section className="min-w-0 flex-1" aria-labelledby="admin-category-heading">
          <div className="mb-3">
            <h2 id="admin-category-heading" className="text-sm font-semibold text-[var(--bt-gray-900)]">
              {selected?.label}
            </h2>
            {selected?.helper ? (
              <p className="mt-0.5 text-xs text-[var(--bt-gray-500)]">{selected.helper}</p>
            ) : null}
          </div>

          <ul className="divide-y divide-[var(--bt-gray-200)] rounded-lg border border-[var(--bt-gray-200)] bg-white">
            {selected?.items.map((item) => {
              const Icon = item.icon
              return (
                <li key={item.to}>
                  <Link
                    to={item.to}
                    className={[
                      'flex items-start gap-3 px-3 py-3 outline-none transition-colors',
                      'hover:bg-[var(--bt-gray-50)] focus-visible:bg-[var(--bt-gray-50)] focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[var(--bt-orange)]',
                      item.sensitive ? 'bg-[var(--bt-red-bg)]/40' : '',
                    ]
                      .filter(Boolean)
                      .join(' ')}
                  >
                    <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-[var(--bt-gray-50)]">
                      <Icon active />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="flex flex-wrap items-center gap-2">
                        <span className="text-sm font-medium text-[var(--bt-gray-900)]">{item.label}</span>
                        {item.sensitive ? (
                          <span className="rounded border border-[var(--bt-red)]/30 bg-white px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-[var(--bt-red)]">
                            Controlled
                          </span>
                        ) : null}
                      </span>
                      <span className="mt-0.5 block text-xs text-[var(--bt-gray-500)]">{item.description}</span>
                    </span>
                    <span className="mt-2 shrink-0 self-center">
                      <ChevronIcon />
                    </span>
                  </Link>
                </li>
              )
            })}
          </ul>
        </section>
      </div>

      <p className="text-xs text-[var(--bt-gray-500)]">
        Reports remain under Policy &amp; Risk. Broader Risk / Senior Management report access is a later RBAC task —
        permissions are unchanged in this navigation phase.
      </p>
    </div>
  )
}
