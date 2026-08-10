import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { listApplications } from '@/api/applications'
import { listOpenLosPip } from '@/api/pgSettlements'
import { listPendingProgramApprovals } from '@/api/workflow'
import { useAuth } from '@/auth/useAuth'
import {
  canCreateOrNotifyBorrowerIntake,
  canRunUnderwriting,
  isRelationshipManager,
} from '@/auth/types'
import { ClearDemoDataButton } from '@/components/ClearDemoDataButton'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import {
  ApplicationsIcon,
  AssignmentIcon,
  KycIcon,
  ProgramsIcon,
  RulesIcon,
  UnderwritingIcon,
} from '@/components/SidebarNavIcons'
import { BtBadge } from '@/components/ui/BtBadge'
import { loanProductLabel } from '@/catalog/loanProducts'
import {
  buildAttentionItems,
  buildPipelineStages,
  parseByStatus,
  summaryTotal,
  welcomeLine,
  type AttentionItem,
  type PipelineStage,
} from '@/lib/dashboard/operationalDashboard'
import { formatStatusLabel } from '@/lib/dashboardLabels'
import { formatInstant, formatMoney } from '@/lib/format'
import { displayBorrowerName } from '@/lib/intake/applicationPartyResolve'
import { showStagingDemoNav } from '@/nav/workspaceNav'
import { useDashboardSummary } from '@/hooks/useDashboardSummary'
import { resolvePresentationProfile } from '@/lib/ux/presentationProfile'
import type { ApplicationResponse } from '@/types/application'

function attentionIcon(id: AttentionItem['id']) {
  switch (id) {
    case 'kyc':
      return KycIcon
    case 'underwriting':
      return UnderwritingIcon
    case 'program_approvals':
      return ProgramsIcon
    case 'settlements':
      return RulesIcon
    case 'sent_back_to_rm':
      return AssignmentIcon
    default:
      return ApplicationsIcon
  }
}

function Chevron() {
  return (
    <svg className="h-4 w-4 shrink-0 text-[var(--bt-gray-400)]" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2} aria-hidden>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
    </svg>
  )
}

export function DashboardPage() {
  const { user } = useAuth()
  const role = user?.role ?? ''
  const canCreate = canCreateOrNotifyBorrowerIntake(role)
  const showStagingTools = showStagingDemoNav()
  const underwritingActionable = canRunUnderwriting(role) && !isRelationshipManager(role)

  const { data: summary, loading: summaryLoading, error: summaryError, refetch: refetchSummary } =
    useDashboardSummary()

  const [recent, setRecent] = useState<ApplicationResponse[] | null>(null)
  const [recentError, setRecentError] = useState<string | null>(null)
  const [recentLoading, setRecentLoading] = useState(true)
  const [programPending, setProgramPending] = useState<number | null>(null)
  const [settlementOpen, setSettlementOpen] = useState<number | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)

  useEffect(() => {
    const onClear = () => setRefreshKey((k) => k + 1)
    window.addEventListener('los:demo-data-cleared', onClear)
    return () => window.removeEventListener('los:demo-data-cleared', onClear)
  }, [])

  useEffect(() => {
    let cancelled = false
    void (async () => {
      setRecentLoading(true)
      setRecentError(null)
      try {
        let page
        try {
          page = await listApplications({ page: 0, size: 6, sort: 'updatedAt,desc' })
        } catch {
          page = await listApplications({ page: 0, size: 6, sort: 'createdAt,desc' })
        }
        if (!cancelled) {
          setRecent(page.content ?? [])
          setRecentLoading(false)
        }
      } catch (e) {
        if (!cancelled) {
          setRecent(null)
          setRecentError(e instanceof Error ? e.message : 'Could not load recent applications')
          setRecentLoading(false)
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [refreshKey])

  useEffect(() => {
    let cancelled = false
    void (async () => {
      try {
        const rows = await listPendingProgramApprovals()
        if (!cancelled) setProgramPending(Array.isArray(rows) ? rows.length : 0)
      } catch {
        if (!cancelled) setProgramPending(null)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [refreshKey])

  useEffect(() => {
    let cancelled = false
    void (async () => {
      try {
        const rows = await listOpenLosPip()
        if (!cancelled) setSettlementOpen(Array.isArray(rows) ? rows.length : 0)
      } catch {
        if (!cancelled) setSettlementOpen(null)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [refreshKey])

  const summaryRecord = (summary ?? null) as Record<string, unknown> | null
  const byStatus = useMemo(() => parseByStatus(summaryRecord ?? undefined), [summaryRecord])
  const total = useMemo(() => summaryTotal(summaryRecord ?? undefined), [summaryRecord])

  const attention = useMemo(
    () =>
      buildAttentionItems({
        role,
        byStatus,
        programApprovalsPending: programPending,
        settlementOpenCount: settlementOpen,
      }),
    [role, byStatus, programPending, settlementOpen],
  )

  const pipeline = useMemo(
    () => buildPipelineStages(byStatus, { underwritingActionable }),
    [byStatus, underwritingActionable],
  )

  if (summaryLoading) return <LoadingState label="Loading dashboard…" />
  if (summaryError) return <ErrorState message={summaryError} />
  if (!summary) return <ErrorState message="Could not load dashboard." />

  if (total === 0) {
    return (
      <div className="space-y-6">
        <DashboardHeader
          canCreate={canCreate}
          name={user?.name}
          focusLine={resolvePresentationProfile(role).dashboardFocus}
        />
        <EmptyApplicationsState canCreate={canCreate} showDemoSamples={showStagingTools} />
        {showStagingTools ? <StagingTools onCleared={refetchSummary} /> : null}
      </div>
    )
  }

  return (
    <div className="space-y-8">
      <DashboardHeader
        canCreate={canCreate}
        name={user?.name}
        focusLine={resolvePresentationProfile(role).dashboardFocus}
      />

      <section aria-labelledby="attention-heading">
        <h2 id="attention-heading" className="mb-3 text-sm font-semibold text-[var(--bt-gray-900)]">
          Needs your attention
        </h2>
        {attention.length === 0 ? (
          <div className="rounded-lg border border-[var(--bt-gray-200)] bg-white px-4 py-5">
            <p className="text-sm font-medium text-[var(--bt-gray-900)]">Nothing needs immediate attention.</p>
            <p className="mt-1 text-xs text-[var(--bt-gray-500)]">
              You&apos;re up to date with the queues available to your role.
            </p>
          </div>
        ) : (
          <ul className="divide-y divide-[var(--bt-gray-200)] rounded-lg border border-[var(--bt-gray-200)] bg-white">
            {attention.map((item) => {
              const Icon = attentionIcon(item.id)
              return (
                <li key={item.id}>
                  <Link
                    to={item.to}
                    className="flex items-start gap-3 px-3 py-3 outline-none transition-colors hover:bg-[var(--bt-gray-50)] focus-visible:bg-[var(--bt-gray-50)] focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[var(--bt-orange)]"
                  >
                    <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-[var(--bt-gray-50)]">
                      <Icon active />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block text-sm font-medium text-[var(--bt-gray-900)]">{item.label}</span>
                      <span className="mt-0.5 block text-xs text-[var(--bt-gray-500)]">{item.description}</span>
                    </span>
                    <span className="mt-1 flex shrink-0 items-center gap-2">
                      <span className="tabular-nums text-sm font-semibold text-[var(--bt-gray-900)]">{item.count}</span>
                      <Chevron />
                    </span>
                  </Link>
                </li>
              )
            })}
          </ul>
        )}
      </section>

      <PipelineSection stages={pipeline} />

      <RecentApplicationsSection
        rows={recent}
        loading={recentLoading}
        error={recentError}
      />

      {showStagingTools ? <StagingTools onCleared={refetchSummary} /> : null}
    </div>
  )
}

function DashboardHeader({
  canCreate,
  name,
  focusLine,
}: {
  canCreate: boolean
  name?: string
  focusLine: string
}) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-3">
      <PageHeader
        title="Dashboard"
        description={
          <>
            <span className="block text-sm font-medium text-[var(--bt-gray-800)]">{welcomeLine(name)}</span>
            <span className="mt-0.5 block">{focusLine}</span>
          </>
        }
      />
      {canCreate ? (
        <Link to="/applications/new" className="bt-btn bt-btn-primary shrink-0">
          New Application
        </Link>
      ) : null}
    </div>
  )
}

function EmptyApplicationsState({
  canCreate,
  showDemoSamples,
}: {
  canCreate: boolean
  showDemoSamples: boolean
}) {
  return (
    <div className="rounded-lg border border-[var(--bt-gray-200)] bg-white px-6 py-10 text-center">
      <h2 className="text-base font-semibold text-[var(--bt-gray-900)]">No applications yet</h2>
      <p className="mx-auto mt-2 max-w-md text-sm text-[var(--bt-gray-500)]">
        Start lending operations by creating an application, or open Applications when work arrives.
      </p>
      <div className="mt-5 flex flex-wrap items-center justify-center gap-2">
        {canCreate ? (
          <Link to="/applications/new" className="bt-btn bt-btn-primary">
            New Application
          </Link>
        ) : null}
        <Link
          to="/applications"
          className={canCreate ? 'bt-btn bt-btn-secondary' : 'bt-btn bt-btn-primary'}
        >
          Open Applications
        </Link>
      </div>
      {showDemoSamples ? (
        <p className="mt-4">
          <Link
            to="/credit-intelligence/applications"
            className="text-xs font-medium text-[var(--bt-gray-500)] underline-offset-2 hover:text-[var(--bt-orange)] hover:underline"
          >
            Demo Samples
          </Link>
        </p>
      ) : null}
    </div>
  )
}

function PipelineSection({ stages }: { stages: PipelineStage[] }) {
  return (
    <section aria-labelledby="pipeline-heading">
      <h2 id="pipeline-heading" className="mb-3 text-sm font-semibold text-[var(--bt-gray-900)]">
        Application pipeline
      </h2>
      <div className="overflow-x-auto rounded-lg border border-[var(--bt-gray-200)] bg-white px-2 py-3">
        <ol className="flex min-w-max items-stretch gap-0 sm:min-w-0 sm:flex-wrap lg:flex-nowrap">
          {stages.map((stage, idx) => (
            <li key={stage.id} className="flex min-w-[6.5rem] flex-1 items-center">
              {idx > 0 ? (
                <span className="mx-1 hidden h-px w-4 shrink-0 bg-[var(--bt-gray-200)] sm:block" aria-hidden />
              ) : null}
              {stage.to ? (
                <Link
                  to={stage.to}
                  className="flex flex-1 flex-col items-center rounded-md px-2 py-2 text-center transition-colors hover:bg-[var(--bt-orange-light)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-[var(--bt-orange)]"
                >
                  <span className="text-xs font-medium text-[var(--bt-gray-500)]">{stage.label}</span>
                  <span className="mt-1 text-lg font-semibold tabular-nums text-[var(--bt-gray-900)]">{stage.count}</span>
                </Link>
              ) : (
                <div className="flex flex-1 flex-col items-center px-2 py-2 text-center" title="Pipeline context">
                  <span className="text-xs font-medium text-[var(--bt-gray-500)]">{stage.label}</span>
                  <span className="mt-1 text-lg font-semibold tabular-nums text-[var(--bt-gray-900)]">{stage.count}</span>
                </div>
              )}
            </li>
          ))}
        </ol>
      </div>
    </section>
  )
}

function RecentApplicationsSection({
  rows,
  loading,
  error,
}: {
  rows: ApplicationResponse[] | null
  loading: boolean
  error: string | null
}) {
  return (
    <section aria-labelledby="recent-heading">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <h2 id="recent-heading" className="text-sm font-semibold text-[var(--bt-gray-900)]">
          Recent applications
        </h2>
        <Link
          to="/applications"
          className="inline-flex items-center gap-1 text-xs font-medium text-[var(--bt-orange)] hover:underline"
        >
          View all applications
          <Chevron />
        </Link>
      </div>

      {loading ? (
        <div className="rounded-lg border border-[var(--bt-gray-200)] bg-white px-4 py-6 text-sm text-[var(--bt-gray-500)]">
          Loading recent applications…
        </div>
      ) : error ? (
        <div className="rounded-lg border border-[var(--bt-gray-200)] bg-white px-4 py-4 text-sm text-[var(--bt-gray-500)]">
          Recent applications could not be loaded. Pipeline and attention above are still available.
        </div>
      ) : !rows || rows.length === 0 ? (
        <div className="rounded-lg border border-[var(--bt-gray-200)] bg-white px-4 py-6 text-sm text-[var(--bt-gray-500)]">
          No recent applications to show.
        </div>
      ) : (
        <>
          <div className="hidden overflow-x-auto rounded-lg border border-[var(--bt-gray-200)] bg-white md:block">
            <table className="bt-table min-w-full">
              <thead>
                <tr>
                  <th>Application</th>
                  <th>Borrower</th>
                  <th>Product</th>
                  <th>Amount</th>
                  <th>Status</th>
                  <th>Updated</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((a) => (
                  <tr key={a.id} className="clickable">
                    <td className="font-medium text-[var(--bt-gray-900)]">
                      <Link to={`/applications/${a.id}`} className="hover:underline">
                        {a.applicationNumber}
                      </Link>
                    </td>
                    <td>{displayBorrowerName(a)}</td>
                    <td>{loanProductLabel(a.loanProduct)}</td>
                    <td className="tabular-nums">{formatMoney(a.requestedAmount)}</td>
                    <td>
                      <BtBadge status={a.status}>{formatStatusLabel(a.status)}</BtBadge>
                    </td>
                    <td className="tabular-nums text-[var(--bt-gray-500)]">
                      {formatInstant(a.updatedAt ?? a.createdAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <ul className="space-y-2 md:hidden">
            {rows.map((a) => (
              <li key={a.id}>
                <Link
                  to={`/applications/${a.id}`}
                  className="block rounded-lg border border-[var(--bt-gray-200)] bg-white px-3 py-3 transition-colors hover:bg-[var(--bt-gray-50)]"
                >
                  <div className="flex items-start justify-between gap-2">
                    <span className="text-sm font-medium text-[var(--bt-gray-900)]">{a.applicationNumber}</span>
                    <BtBadge status={a.status}>{formatStatusLabel(a.status)}</BtBadge>
                  </div>
                  <p className="mt-1 text-xs text-[var(--bt-gray-500)]">
                    {displayBorrowerName(a)} · {loanProductLabel(a.loanProduct)} · {formatMoney(a.requestedAmount)}
                  </p>
                  <p className="mt-0.5 text-xs text-[var(--bt-gray-400)]">
                    Updated {formatInstant(a.updatedAt ?? a.createdAt)}
                  </p>
                </Link>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  )
}

function StagingTools({ onCleared }: { onCleared: () => void }) {
  return (
    <div className="border-t border-[var(--bt-gray-200)] pt-4">
      <p className="mb-2 text-[10px] font-semibold uppercase tracking-wide text-[var(--bt-gray-400)]">
        Staging tools
      </p>
      <ClearDemoDataButton onCleared={onCleared} className="bt-btn bt-btn-secondary bt-btn-sm border-amber-300 bg-[var(--bt-amber-bg)] text-[var(--bt-amber)]" />
    </div>
  )
}
