import { Link } from 'react-router-dom'
import { formatStatusLabel } from '@/lib/dashboardLabels'
import { ClearDemoDataButton } from '@/components/ClearDemoDataButton'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { BtCard, BtCardHeader } from '@/components/ui/BtCard'
import { BtStatCard, statAccentAt, btStatLabelClass, type BtStatAccent } from '@/components/ui/BtStatCard'
import { useDashboardSummary } from '@/hooks/useDashboardSummary'
import type { DashboardSummary } from '@/types/api'

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
            className="bt-btn bt-btn-primary shrink-0"
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
    <div className="space-y-8">
      <div className="flex flex-wrap gap-2 text-sm text-[var(--bt-gray-600)]">
        <span className="text-[var(--bt-gray-500)]">Go to</span>
        <Link to="/kyc" className="font-medium text-[var(--bt-orange)] hover:underline">
          KYC in progress
        </Link>
        <span>·</span>
        <Link to="/underwriting" className="font-medium text-[var(--bt-orange)] hover:underline">
          Underwriting
        </Link>
        <span>·</span>
        <Link to="/applications" className="font-medium text-[var(--bt-orange)] hover:underline">
          All applications
        </Link>
      </div>

      <div className="bt-stat-sub text-sm text-[var(--bt-gray-600)] -mt-4">
        <strong className="text-[var(--bt-gray-800)]">Queues:</strong> {kycQ} in KYC, {uwQ} in underwriting (from API counts). Use the links above to work the queue.
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {topLevel.map(([key, value], i) => (
          <BtStatCard
            key={key}
            title={formatStatusLabel(key)}
            value={String(value)}
            accent={statAccentAt(i)}
          />
        ))}
      </div>

      {byStatus != null && typeof byStatus === 'object' && !Array.isArray(byStatus) ? (
        <BtCard className="overflow-hidden p-0">
          <BtCardHeader title="By status" />
          <div className="grid gap-3 p-5 sm:grid-cols-2 lg:grid-cols-4">
            {Object.entries(byStatus as Record<string, unknown>).map(([key, value], i) => (
              <StatusCountTile
                key={key}
                label={formatStatusLabel(key)}
                value={String(value)}
                accent={statAccentAt(i)}
              />
            ))}
          </div>
        </BtCard>
      ) : null}
    </div>
  )
}

const statusBorder: Record<BtStatAccent, string> = {
  orange: 'border-l-[var(--bt-orange)]',
  green: 'border-l-[var(--bt-green)]',
  red: 'border-l-[var(--bt-red)]',
  gray: 'border-l-[var(--bt-gray-300)]',
  blue: 'border-l-[var(--bt-blue)]',
  amber: 'border-l-[var(--bt-amber)]',
  purple: 'border-l-[#722ed1]',
}

const statusValueTone: Record<BtStatAccent, string> = {
  orange: 'orange',
  green: 'green',
  red: 'red',
  gray: '',
  blue: '',
  amber: '',
  purple: '',
}

function StatusCountTile({
  label,
  value,
  accent,
}: {
  label: string
  value: string
  accent: BtStatAccent
}) {
  const tone = statusValueTone[accent]
  return (
    <div className={`bt-stat border-l-4 ${statusBorder[accent]}`}>
      <div className={btStatLabelClass}>{label}</div>
      <div className={`bt-stat-value ${tone}`.trim()}>{value}</div>
    </div>
  )
}
