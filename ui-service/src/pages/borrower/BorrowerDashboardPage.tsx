import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { deleteBorrowerDraftApplication, getBorrowerDashboard, getBorrowerNotifications } from '@/api/borrowerPortal'
import { ApiError } from '@/api/http'
import { isBorrowerDeletableApplicationStatus } from '@/lib/borrowerApplicationDeletable'
import { hasLocalDraft } from '@/lib/borrowerWizardDraft'

export function BorrowerDashboardPage() {
  const [data, setData] = useState<Awaited<ReturnType<typeof getBorrowerDashboard>> | null>(null)
  const [notifs, setNotifs] = useState<Awaited<ReturnType<typeof getBorrowerNotifications>>>([])
  const [err, setErr] = useState<string | null>(null)
  const [draftDeleteBusy, setDraftDeleteBusy] = useState<string | null>(null)
  const [actionErr, setActionErr] = useState<string | null>(null)

  const load = useCallback(() => {
    setErr(null)
    setActionErr(null)
    void getBorrowerDashboard()
      .then((d) => {
        setData(d)
        return getBorrowerNotifications()
      })
      .then(setNotifs)
      .catch((e) => setErr(e instanceof ApiError ? e.message : 'Failed to load dashboard'))
  }, [])

  const onDeleteServerDraft = useCallback(
    async (id: string) => {
      if (!window.confirm('Delete this draft? This cannot be undone.')) return
      setDraftDeleteBusy(id)
      setActionErr(null)
      try {
        await deleteBorrowerDraftApplication(id)
        void load()
      } catch (e) {
        setActionErr(e instanceof ApiError ? e.message : 'Delete failed')
      } finally {
        setDraftDeleteBusy(null)
      }
    },
    [load],
  )

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async dashboard + notifications
    load()
  }, [load])

  if (err) {
    return <p className="text-sm text-rose-700">{err}</p>
  }
  if (!data) {
    return <p className="text-sm text-slate-600">Loading your dashboard…</p>
  }

  const hasApps = data.recentApplications.length > 0
  const draft = hasLocalDraft()

  return (
    <div>
      <h1 className="text-2xl font-semibold text-slate-900">Welcome, {data.fullName}</h1>
      {actionErr ? <p className="mt-2 text-sm text-rose-700">{actionErr}</p> : null}
      {data.secondLoanWarning ? (
        <p className="mt-2 rounded border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">{data.secondLoanWarning}</p>
      ) : null}
      {notifs.length > 0 ? (
        <div className="mt-4 rounded border border-slate-200 bg-white p-4 shadow-sm">
          <h2 className="text-sm font-semibold text-slate-800">Updates</h2>
          <ul className="mt-2 space-y-2 text-sm text-slate-700">
            {notifs.map((n) => (
              <li key={n.id}>
                <span className="font-medium text-slate-900">{n.title}</span> — {n.message}{' '}
                {n.applicationId ? (
                  <Link className="text-slate-800 underline" to={`/borrower/applications/${n.applicationId}`}>
                    View
                  </Link>
                ) : null}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
      <div className="mt-8 grid gap-4 md:grid-cols-2">
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-sm font-semibold text-slate-900">Loan accounts</h2>
          <p className="mt-2 text-sm text-slate-600">
            {data.activeLoanCount > 0
              ? `You have ${data.activeLoanCount} disbursed loan(s). Use the menu for repayment details.`
              : 'No active loans yet.'}
          </p>
        </div>
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-sm font-semibold text-slate-900">Recent applications</h2>
          {hasApps ? (
            <ul className="mt-2 space-y-2 text-sm">
              {data.recentApplications.map((a) => (
                <li key={a.applicationId} className="flex flex-wrap items-center justify-between gap-2">
                  <span>
                    {a.applicationNumber} · {a.friendlyStatus}
                  </span>
                  <span className="flex flex-wrap items-center gap-2">
                    {isBorrowerDeletableApplicationStatus(a.status) ? (
                      <button
                        type="button"
                        className="text-xs text-rose-800 underline"
                        onClick={() => void onDeleteServerDraft(a.applicationId)}
                        disabled={draftDeleteBusy === a.applicationId}
                      >
                        {draftDeleteBusy === a.applicationId ? 'Deleting…' : 'Delete draft'}
                      </button>
                    ) : null}
                    <Link to={`/borrower/applications/${a.applicationId}`} className="shrink-0 text-slate-800 underline">
                      Open
                    </Link>
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="mt-2 text-sm text-slate-600">You haven’t applied for a loan yet.</p>
          )}
        </div>
      </div>
      {(data.hasIncompleteDraftHint || draft) && (
        <p className="mt-4 text-sm text-amber-900">
          {draft ? 'You have a saved loan application draft in this browser. ' : null}
          <Link to="/borrower/apply" className="font-medium underline">
            Continue or start an application
          </Link>
        </p>
      )}
      <div className="mt-8">
        <Link
          to="/borrower/apply"
          className="inline-block rounded-md bg-slate-900 px-5 py-2.5 text-sm font-medium text-white"
        >
          Apply for a loan
        </Link>
      </div>
    </div>
  )
}
