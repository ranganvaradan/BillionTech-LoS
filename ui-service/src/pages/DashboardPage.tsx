import { Link } from 'react-router-dom'
import { formatStatusLabel } from '@/lib/dashboardLabels'
import { ClearDemoDataButton } from '@/components/ClearDemoDataButton'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { useDashboardSummary } from '@/hooks/useDashboardSummary'
import type { DashboardSummary } from '@/types/api'

const KPI_STRIP: readonly { border: string; ring: string }[] = [
  { border: 'border-l-[#1890ff]', ring: 'ring-[#1890ff]/15' },
  { border: 'border-l-[#52c41a]', ring: 'ring-[#52c41a]/12' },
  { border: 'border-l-[#faad14]', ring: 'ring-[#faad14]/15' },
  { border: 'border-l-[#f5222d]', ring: 'ring-[#f5222d]/12' },
  { border: 'border-l-[#722ed1]', ring: 'ring-[#722ed1]/12' },
  { border: 'border-l-[#13c2c2]', ring: 'ring-[#13c2c2]/12' },
] as const

function stripAt(i: number) {
  return KPI_STRIP[i % KPI_STRIP.length]!
}

export function DashboardPage() {
  const { data, loading, error, refetch } = useDashboardSummary()

  if (loading) return <LoadingState label="Loading dashboard…" />
  if (error) return <ErrorState message={error} />
  if (!data) return <ErrorState message="No data." />

  return (
    <div>
      <div className="mb-2 flex flex-wrap items-start justify-between gap-3">
        <PageHeader
          title="Dashboard"
          description="Pipeline overview and high-level application counts."
        />
        <div className="mt-1 flex flex-wrap items-center gap-2">
          <ClearDemoDataButton onCleared={refetch} />
          <Link
            to="/applications/new"
            className="shrink-0 rounded-md bg-bl-primary px-3 py-1.5 text-sm font-medium text-white shadow-sm hover:brightness-110"
          >
            New application
          </Link>
        </div>
      </div>
      <SummaryBody data={data} />
    </div>
  )
}

function SummaryBody({ data }: { data: DashboardSummary }) {
  const byStatus = data.byStatus
  const topLevel = Object.entries(data).filter(([k]) => k !== 'byStatus')
  const kycQ = typeof data.kycInProgress === 'number' ? data.kycInProgress : 0
  const uwQ = typeof data.underwritingInProgress === 'number' ? data.underwritingInProgress : 0

  return (
    <>
      <div className="mb-4 flex flex-wrap gap-2 text-sm text-slate-600">
        <span className="text-slate-500">Go to</span>
        <Link
          to="/kyc"
          className="font-medium text-slate-800 underline"
        >
          KYC in progress
        </Link>
        <span>·</span>
        <Link
          to="/underwriting"
          className="font-medium text-slate-800 underline"
        >
          Underwriting
        </Link>
        <span>·</span>
        <Link
          to="/applications"
          className="font-medium text-slate-800 underline"
        >
          All applications
        </Link>
      </div>
      <p className="mb-3 text-sm text-slate-600">
        <strong>Queues:</strong> {kycQ} in KYC, {uwQ} in underwriting (from API counts). Use the links above to work the
        queue.
      </p>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {topLevel.map(([key, value]) => (
          <div
            key={key}
            className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm"
          >
            <div className="text-xs font-medium uppercase text-slate-500">
              {formatStatusLabel(key)}
            </div>
            <div className="mt-1 text-2xl font-semibold tabular-nums text-slate-900">{String(value)}</div>
          </div>
        ))}
      </div>

      {byStatus != null && typeof byStatus === 'object' && !Array.isArray(byStatus) ? (
        <div className="mt-8">
          <h2 className="mb-3 text-sm font-semibold text-bl-navy">By status</h2>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {Object.entries(byStatus as Record<string, unknown>).map(([key, value], i) => {
              const s = stripAt(i)
              return (
                <div
                  key={key}
                  className={`rounded-lg border border-slate-200/80 border-l-4 ${s.border} ${s.ring} bg-white p-3 shadow-sm ring-1 ring-inset`}
                >
                  <div className="text-xs font-medium text-slate-500">{formatStatusLabel(key)}</div>
                  <div className="mt-0.5 text-lg font-semibold tabular-nums text-bl-navy">{String(value)}</div>
                </div>
              )
            })}
          </div>
        </div>
      ) : null}
    </>
  )
}
