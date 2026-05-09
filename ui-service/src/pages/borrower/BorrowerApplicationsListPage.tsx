import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { deleteBorrowerDraftApplication, listBorrowerApplications, type BorrowerAppSummary } from '@/api/borrowerPortal'
import { ApiError } from '@/api/http'
import { isBorrowerDeletableApplicationStatus } from '@/lib/borrowerApplicationDeletable'

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
    <div>
      <h1 className="text-2xl font-semibold text-bl-navy">Loan applications</h1>
      {err ? (
        <p className="mt-2 text-sm text-rose-700" role="alert">
          {err}
        </p>
      ) : null}
      {list.length === 0 ? (
        <p className="mt-2 text-sm text-slate-600">No applications found.</p>
      ) : (
        <ul className="mt-4 space-y-2 text-sm">
          {list.map((a) => (
            <li key={a.applicationId} className="flex flex-wrap items-center justify-between gap-2 rounded border border-slate-200 bg-white p-3 shadow-sm">
              <span>
                {a.applicationNumber} — {a.friendlyStatus}
              </span>
              <span className="flex flex-wrap items-center gap-2">
                {isBorrowerDeletableApplicationStatus(a.status) ? (
                  <button
                    type="button"
                    className="rounded border border-rose-200 bg-rose-50 px-2 py-1 text-xs text-rose-900"
                    onClick={() => void onDeleteDraft(a.applicationId)}
                    disabled={busyId === a.applicationId}
                  >
                    {busyId === a.applicationId ? 'Deleting…' : 'Delete draft'}
                  </button>
                ) : null}
                <Link to={`/borrower/applications/${a.applicationId}`} className="text-bl-navy/90 underline">
                  Open
                </Link>
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
