import { useMemo } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ApplicationTable } from '@/components/ApplicationTable'
import { ClearDemoDataButton } from '@/components/ClearDemoDataButton'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { useApplications } from '@/hooks/useApplications'
import type { ApplicationStatus } from '@/types/application'

const STATUS_OPTIONS: (ApplicationStatus | '')[] = [
  '',
  'DRAFT',
  'CONSENT_PENDING',
  'KYC_IN_PROGRESS',
  'KYC_FAILED',
  'UNDERWRITING',
  'APPROVED',
  'REJECTED',
  'SANCTION_ISSUED',
  'ESIGN_PENDING',
  'ESIGN_COMPLETED',
  'DISBURSEMENT_PENDING',
  'DISBURSED',
  'WITHDRAWN',
  'ON_HOLD',
]

function parseStatus(s: string | null): ApplicationStatus | undefined {
  if (!s) return undefined
  if (STATUS_OPTIONS.includes(s as ApplicationStatus | '')) {
    if (s === '') return undefined
    return s as ApplicationStatus
  }
  return undefined
}

export function ApplicationsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const status = useMemo(
    () => parseStatus(searchParams.get('status')),
    [searchParams],
  )
  const { data, loading, error, refetch } = useApplications({ status, page: 0, size: 30 })

  return (
    <div>
      <PageHeader
        title="Applications"
        description="Browse the loan application queue. Filter by processing status to find what to work on next."
      />
      <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-wrap items-end gap-3">
        <label className="block text-sm text-slate-600">
          <span className="mb-1 block text-xs font-medium text-slate-500">Status filter</span>
          <select
            className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm text-slate-900"
            value={searchParams.get('status') ?? ''}
            onChange={(e) => {
              const v = e.target.value
              if (v) setSearchParams({ status: v })
              else setSearchParams({})
            }}
          >
            {STATUS_OPTIONS.map((s) => (
              <option key={s || 'ALL'} value={s}>
                {s || 'All'}
              </option>
            ))}
          </select>
        </label>
        <span className="text-sm text-slate-500">
          {data != null
            ? `${data.numberOfElements} of ${data.totalElements} (page ${data.number + 1} / ${Math.max(1, data.totalPages)})`
            : null}
        </span>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <ClearDemoDataButton onCleared={refetch} />
          <Link
            to="/applications/new"
            className="shrink-0 rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-800"
          >
            New application
          </Link>
        </div>
      </div>
      {loading && <LoadingState label="Loading applications…" />}
      {error && <ErrorState message={error} />}
      {data && !loading && <ApplicationTable rows={data.content} />}
    </div>
  )
}
