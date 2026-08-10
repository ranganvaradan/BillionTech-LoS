import { useEffect, useState } from 'react'
import { listApplicationDeletionLogs, type ApplicationDeletionLogEntry } from '@/api/applications'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { AdministrationWorkspaceNav } from '@/components/workspace/AdministrationWorkspaceNav'
import { formatInstant } from '@/lib/format'
import { loanProductLabel } from '@/catalog/loanProducts'

export function ApplicationDeletionsPage() {
  const [rows, setRows] = useState<ApplicationDeletionLogEntry[] | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    void listApplicationDeletionLogs(page, 20)
      .then((data) => {
        if (!cancelled) {
          setRows(data.content)
          setTotalPages(data.totalPages)
        }
      })
      .catch((e: unknown) => {
        if (!cancelled) {
          setRows(null)
          setError(e instanceof Error ? e.message : 'Failed to load deletion log')
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [page])

  return (
    <div>
      <PageHeader
        title="Application Deletions"
        description="Audit trail of borrower applications permanently deleted by staff, including PLP cleanup summary when applicable."
      />
      <AdministrationWorkspaceNav />
      {loading ? <LoadingState label="Loading deletion log…" /> : null}
      {error ? <ErrorState message={error} /> : null}
      {rows && !loading ? (
        <div className="bt-card overflow-hidden">
          <table className="min-w-full text-sm">
            <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3">Deleted at</th>
                <th className="px-4 py-3">Application</th>
                <th className="px-4 py-3">Product</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Deleted by</th>
                <th className="px-4 py-3">Reason</th>
                <th className="px-4 py-3">PLP cleanup</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-4 py-8 text-center text-slate-500">
                    No deletions recorded yet.
                  </td>
                </tr>
              ) : (
                rows.map((row) => (
                  <tr key={row.id} className="align-top">
                    <td className="px-4 py-3 whitespace-nowrap">{formatInstant(row.deletedAt)}</td>
                    <td className="px-4 py-3">
                      <div className="font-medium text-slate-800">{row.applicationNumber}</div>
                      <div className="text-xs text-slate-500">{row.borrowerEmail || '—'}</div>
                    </td>
                    <td className="px-4 py-3">{loanProductLabel(row.loanProduct)}</td>
                    <td className="px-4 py-3">{row.applicationStatus}</td>
                    <td className="px-4 py-3">
                      <div>{row.deletedByEmail || '—'}</div>
                      <div className="text-xs text-slate-500">{row.deletedByRole || ''}</div>
                    </td>
                    <td className="px-4 py-3 max-w-xs">{row.reason || '—'}</td>
                    <td className="px-4 py-3 max-w-sm text-xs text-slate-600">
                      {row.plpCleanupAttempted ? row.plpCleanupSummary || 'Attempted' : '—'}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
          {totalPages > 1 ? (
            <div className="flex items-center justify-end gap-2 border-t border-slate-100 px-4 py-3">
              <button
                type="button"
                className="bt-btn bt-btn-secondary bt-btn-sm"
                disabled={page <= 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                Previous
              </button>
              <span className="text-xs text-slate-500">
                Page {page + 1} of {totalPages}
              </span>
              <button
                type="button"
                className="bt-btn bt-btn-secondary bt-btn-sm"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </button>
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
