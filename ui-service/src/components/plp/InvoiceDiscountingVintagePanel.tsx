import { useEffect, useState } from 'react'
import { getVintageEligibility } from '@/api/plp'
import type { VintageEligibility } from '@/types/plp'

function statusBadge(status: string | undefined, eligibleLabel: string, lowerLabel: string) {
  if (status === 'ELIGIBLE') {
    return <span className="text-emerald-700">✔ {eligibleLabel}</span>
  }
  if (status === 'LOWER') {
    return <span className="text-amber-800">⚠ {lowerLabel}</span>
  }
  return <span className="text-slate-500">— Not configured on program</span>
}

export function InvoiceDiscountingVintagePanel({
  applicationId,
  compact = false,
}: {
  applicationId: string
  compact?: boolean
}) {
  const [data, setData] = useState<VintageEligibility | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    void getVintageEligibility(applicationId)
      .then((v) => {
        if (!cancelled) setData(v)
      })
      .catch(() => {
        if (!cancelled) setData(null)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [applicationId])

  if (loading) {
    return <p className="text-sm text-slate-600">Checking vintage eligibility…</p>
  }
  if (!data) return null

  const dep =
    data.borrowerDependencyVintagePercent != null
      ? `${Number(data.borrowerDependencyVintagePercent).toFixed(2)} %`
      : '—'
  const anchorMo =
    data.borrowerAnchorRelationshipVintageMonths != null
      ? `${data.borrowerAnchorRelationshipVintageMonths} Months`
      : '—'

  return (
    <div className={compact ? 'space-y-2' : 'rounded-lg border border-slate-200 bg-white p-4'}>
      {!compact ? (
        <h4 className="text-sm font-semibold text-slate-900">Program vintage eligibility</h4>
      ) : null}
      <div className="grid gap-3 sm:grid-cols-2">
        <div className="rounded-md border border-slate-100 bg-slate-50 p-3">
          <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Dependency vintage</p>
          <p className="mt-1 text-lg font-semibold text-slate-900 tabular-nums">{dep}</p>
          <p className="mt-1 text-sm">
            {statusBadge(
              data.dependencyVintageStatus,
              data.dependencyVintageMessage || 'Dependency Vintage Eligible',
              data.dependencyVintageMessage || 'Dependency vintage lower than requirement',
            )}
          </p>
          {data.programDependencyVintagePercent != null ? (
            <p className="mt-1 text-xs text-slate-500">
              Program requirement: {Number(data.programDependencyVintagePercent).toFixed(2)}%
            </p>
          ) : null}
        </div>
        <div className="rounded-md border border-slate-100 bg-slate-50 p-3">
          <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Anchor vintage</p>
          <p className="mt-1 text-lg font-semibold text-slate-900 tabular-nums">{anchorMo}</p>
          <p className="mt-1 text-sm">
            {statusBadge(
              data.anchorVintageStatus,
              data.anchorVintageMessage || 'Anchor relationship vintage eligible',
              data.anchorVintageMessage || 'Anchor relationship lower than requirement',
            )}
          </p>
          {data.programAnchorRelationshipVintageMonths != null ? (
            <p className="mt-1 text-xs text-slate-500">
              Program requirement: {data.programAnchorRelationshipVintageMonths} months
            </p>
          ) : null}
        </div>
      </div>
    </div>
  )
}
