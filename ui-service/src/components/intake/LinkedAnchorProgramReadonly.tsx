import { useEffect, useState } from 'react'
import { getLinkedSubProgramSummary } from '@/api/plp'
import type { PlpLinkedSubProgramSummary } from '@/types/plp'

export function LinkedAnchorProgramReadonly({
  subProgramId,
  className = '',
}: {
  subProgramId: string | null | undefined
  className?: string
}) {
  const [summary, setSummary] = useState<PlpLinkedSubProgramSummary | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!subProgramId) {
      setSummary(null)
      return
    }
    let cancelled = false
    setLoading(true)
    void getLinkedSubProgramSummary(subProgramId)
      .then((s) => {
        if (!cancelled) setSummary(s)
      })
      .catch(() => {
        if (!cancelled) setSummary(null)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [subProgramId])

  if (!subProgramId) return null

  return (
    <div className={`rounded-md border border-slate-200 bg-slate-50 p-3 ${className}`}>
      <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Linked anchor &amp; program</p>
      {loading ? <p className="mt-2 text-sm text-slate-600">Loading…</p> : null}
      {!loading && summary ? (
        <dl className="mt-2 grid gap-2 text-sm sm:grid-cols-2">
          <div>
            <dt className="text-slate-500">Anchor</dt>
            <dd className="font-medium text-slate-900">
              {summary.anchorName} ({summary.anchorCode})
            </dd>
          </div>
          <div>
            <dt className="text-slate-500">Program</dt>
            <dd className="font-medium text-slate-900">{summary.programName}</dd>
          </div>
        </dl>
      ) : null}
      {!loading && !summary ? (
        <p className="mt-2 text-sm text-slate-600">Program details could not be loaded.</p>
      ) : null}
    </div>
  )
}
