import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { deleteBorrowerDraftApplication, listBorrowerApplications, type BorrowerAppSummary } from '@/api/borrowerPortal'
import { ApiError } from '@/api/http'
import { PageHeader } from '@/components/PageHeader'
import { isBorrowerDeletableApplicationStatus } from '@/lib/borrowerApplicationDeletable'
import { loanProductLabel } from '@/catalog/loanProducts'
import { formatInstant } from '@/lib/format'

export function BorrowerApplicationsListPage() {
  const [list, setList] = useState<BorrowerAppSummary[]>([])
  const [err, setErr] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)

  const load = useCallback(() => {
    void listBorrowerApplications(0, 50)
      .then((p) => setList(p.content))
      .catch((e) => setErr(e instanceof ApiError ? e.message : 'Failed to list'))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  async function onDeleteDraft(id: string) {
    if (!window.confirm('Delete this draft application? This cannot be undone.')) return
    setBusyId(id)
    setErr(null)
    try {
      await deleteBorrowerDraftApplication(id)
      setList((prev) => prev.filter((a) => a.applicationId !== id))
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : 'Delete failed')
    } finally {
      setBusyId(null)
    }
  }

  if (err && list.length === 0) {
    return <p className="text-sm text-rose-700">{err}</p>
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Loan applications" description="View your submitted and draft loan applications." />
      {err ? (
        <p className="text-sm text-rose-700" role="alert">
          {err}
        </p>
      ) : null}
      {list.length === 0 ? (
        <div className="rounded-lg border border-slate-200 bg-white p-10 text-center text-sm text-slate-600 shadow-sm">
          No applications found.
        </div>
      ) : (
        <div className="bt-card overflow-x-auto shadow-sm">
          <table className="bt-table min-w-full">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs font-medium uppercase tracking-wide text-slate-600">
              <tr>
                <th className="whitespace-nowrap px-5 py-3.5">Application</th>
                <th className="whitespace-nowrap px-5 py-3.5">Product</th>
                <th className="whitespace-nowrap px-5 py-3.5">Status</th>
                <th className="whitespace-nowrap px-5 py-3.5">Updated</th>
                <th className="whitespace-nowrap px-5 py-3.5 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="">
              {list.map((a) => (
                <tr key={a.applicationId} className="">
                  <td className="whitespace-nowrap px-5 py-4 font-medium text-slate-900">{a.applicationNumber}</td>
                  <td className="px-5 py-4 text-slate-700">{loanProductLabel(a.product)}</td>
                  <td className="px-5 py-4">
                    <span className="inline-flex rounded-md border border-slate-200 bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-800">
                      {a.friendlyStatus}
                    </span>
                  </td>
                  <td className="whitespace-nowrap px-5 py-4 text-slate-600 tabular-nums">{formatInstant(a.updatedAt)}</td>
                  <td className="px-5 py-4">
                    <div className="flex items-center justify-end gap-4">
                      {isBorrowerDeletableApplicationStatus(a.status) ? (
                        <button
                          type="button"
                          className="rounded-md border border-rose-200 bg-rose-50 px-3 py-1.5 text-xs font-medium text-rose-900 hover:bg-rose-100 disabled:opacity-50"
                          onClick={() => void onDeleteDraft(a.applicationId)}
                          disabled={busyId === a.applicationId}
                        >
                          {busyId === a.applicationId ? 'Deleting…' : 'Delete draft'}
                        </button>
                      ) : null}
                      <Link
                        to={`/borrower/applications/${a.applicationId}`}
                        className="text-sm font-medium text-slate-800 underline-offset-2 hover:underline"
                      >
                        Open
                      </Link>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
