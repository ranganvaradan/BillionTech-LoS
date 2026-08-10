import { useMemo } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { canCreateOrNotifyBorrowerIntake } from '@/auth/types'
import { ApplicationTable } from '@/components/ApplicationTable'
import { ClearDemoDataButton } from '@/components/ClearDemoDataButton'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { ApplicationsWorkspaceNav } from '@/components/workspace/ApplicationsWorkspaceNav'
import { useApplications } from '@/hooks/useApplications'
import { APPLICATION_STATUS_FILTER_OPTIONS, formatStatusLabel } from '@/lib/dashboardLabels'
import { showStagingDemoNav } from '@/nav/workspaceNav'
import type { ApplicationStatus } from '@/types/application'

const STATUS_OPTIONS: (ApplicationStatus | '')[] = APPLICATION_STATUS_FILTER_OPTIONS as (
  | ApplicationStatus
  | ''
)[]

const INTAKE_OPTIONS: { value: '' | 'BORROWER' | 'ANCHOR'; label: string }[] = [
  { value: '', label: 'All intakes' },
  { value: 'BORROWER', label: 'Borrower only' },
  { value: 'ANCHOR', label: 'Anchor only' },
]

function parseStatus(s: string | null): ApplicationStatus | undefined {
  if (!s) return undefined
  if (STATUS_OPTIONS.includes(s as ApplicationStatus | '')) {
    if (s === '') return undefined
    return s as ApplicationStatus
  }
  return undefined
}

function parseIntakeSegment(s: string | null): string | undefined {
  if (!s) return undefined
  if (s === 'BORROWER' || s === 'ANCHOR') return s
  return undefined
}

export function ApplicationsPage() {
  const { user } = useAuth()
  const canCreate = canCreateOrNotifyBorrowerIntake(user?.role ?? '')
  const [searchParams, setSearchParams] = useSearchParams()
  const status = useMemo(() => parseStatus(searchParams.get('status')), [searchParams])
  const intakeSegment = useMemo(() => parseIntakeSegment(searchParams.get('intakeSegment')), [searchParams])
  const { data, loading, error, refetch } = useApplications({ status, intakeSegment, page: 0, size: 30 })

  function setParam(key: string, value: string) {
    const next = new URLSearchParams(searchParams)
    if (value) next.set(key, value)
    else next.delete(key)
    setSearchParams(next)
  }

  const countLabel =
    data != null
      ? `${data.numberOfElements} of ${data.totalElements} (page ${data.number + 1} / ${Math.max(1, data.totalPages)})`
      : null

  return (
    <div className="space-y-4">
      <PageHeader
        title="Applications"
        description="Browse and open loan applications. Use workspace queues for common work, or filter by status."
        actions={
          <>
            {showStagingDemoNav() ? <ClearDemoDataButton onCleared={refetch} /> : null}
            {canCreate ? (
              <Link to="/applications/new" className="bt-btn bt-btn-primary">
                New Application
              </Link>
            ) : null}
          </>
        }
      />
      <ApplicationsWorkspaceNav />

      <div className="bt-toolbar-card">
        <div className="flex flex-wrap items-end gap-4">
          <label className="min-w-[11rem] flex-1">
            <span className="bt-label">Status</span>
            <select className="bt-input w-full" value={searchParams.get('status') ?? ''} onChange={(e) => setParam('status', e.target.value)}>
              {STATUS_OPTIONS.map((s) => (
                <option key={s || 'ALL'} value={s}>
                  {s ? formatStatusLabel(s) : 'All statuses'}
                </option>
              ))}
            </select>
          </label>
          <label className="min-w-[11rem] flex-1">
            <span className="bt-label">Intake</span>
            <select className="bt-input w-full" value={searchParams.get('intakeSegment') ?? ''} onChange={(e) => setParam('intakeSegment', e.target.value)}>
              {INTAKE_OPTIONS.map((o) => (
                <option key={o.value || 'ALL'} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
          {countLabel ? <div className="pb-2 text-sm text-[var(--bt-gray-500)]">{countLabel}</div> : null}
        </div>
      </div>

      {loading && <LoadingState label="Loading applications…" />}
      {error && <ErrorState message={error} />}
      {data && !loading && <ApplicationTable rows={data.content} />}
    </div>
  )
}
