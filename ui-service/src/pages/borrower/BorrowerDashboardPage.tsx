import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { deleteBorrowerDraftApplication, getBorrowerDashboard, getBorrowerNotifications } from '@/api/borrowerPortal'
import { ApiError } from '@/api/http'
import { PageHeader } from '@/components/PageHeader'
import { BtCard, BtCardHeader } from '@/components/ui/BtCard'
import { BtStatCard } from '@/components/ui/BtStatCard'
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
    return <p className="bt-alert bt-alert-error text-sm">{err}</p>
  }
  if (!data) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-pulse text-[var(--bt-gray-400)] text-sm">Loading your dashboard…</div>
      </div>
    )
  }

  const hasApps = data.recentApplications.length > 0
  const draft = hasLocalDraft()

  return (
    <div className="space-y-8">
      <PageHeader title={`Welcome, ${data.fullName}`} description="Track your loan applications and account activity." />

      {actionErr ? <p className="bt-alert bt-alert-error text-sm">{actionErr}</p> : null}
      {data.secondLoanWarning ? (
        <p className="bt-alert bt-alert-warning text-sm">{data.secondLoanWarning}</p>
      ) : null}

      <div className="grid gap-4 sm:grid-cols-2">
        <BtStatCard
          title="Active loans"
          value={String(data.activeLoanCount)}
          subtitle={data.activeLoanCount > 0 ? 'Disbursed loan accounts' : 'No active loans yet'}
          accent={data.activeLoanCount > 0 ? 'green' : 'gray'}
        />
        <BtStatCard
          title="Recent applications"
          value={String(data.recentApplications.length)}
          subtitle={hasApps ? 'Latest application activity' : 'No applications yet'}
          accent="orange"
        />
      </div>

      {notifs.length > 0 ? (
        <BtCard className="p-0 overflow-hidden">
          <BtCardHeader title="Updates" />
          <ul className="divide-y divide-[var(--bt-gray-100)] px-5 pb-4">
            {notifs.map((n) => (
              <li key={n.id} className="py-3 text-sm text-[var(--bt-gray-700)]">
                <span className="font-medium text-[var(--bt-gray-900)]">{n.title}</span> — {n.message}{' '}
                {n.applicationId ? (
                  <Link className="text-[var(--bt-orange)] hover:underline" to={`/borrower/applications/${n.applicationId}`}>
                    View
                  </Link>
                ) : null}
              </li>
            ))}
          </ul>
        </BtCard>
      ) : null}

      <div className="grid gap-6 md:grid-cols-2">
        <BtCard className="p-5">
          <h2 className="bt-card-title">Loan accounts</h2>
          <p className="mt-3 text-sm leading-relaxed text-[var(--bt-gray-600)]">
            {data.activeLoanCount > 0
              ? `You have ${data.activeLoanCount} disbursed loan(s). Use the menu for repayment details.`
              : 'No active loans yet.'}
          </p>
        </BtCard>

        <BtCard className="p-0 overflow-hidden">
          <BtCardHeader title="Recent applications" />
          {hasApps ? (
            <ul className="divide-y divide-[var(--bt-gray-100)] px-5 pb-4">
              {data.recentApplications.map((a) => (
                <li key={a.applicationId} className="flex flex-wrap items-center justify-between gap-4 py-3">
                  <span className="text-sm text-[var(--bt-gray-700)]">
                    {a.applicationNumber} · {a.friendlyStatus}
                  </span>
                  <span className="flex flex-wrap items-center gap-3">
                    {isBorrowerDeletableApplicationStatus(a.status) ? (
                      <button
                        type="button"
                        className="text-xs text-[var(--bt-red)] hover:underline"
                        onClick={() => void onDeleteServerDraft(a.applicationId)}
                        disabled={draftDeleteBusy === a.applicationId}
                      >
                        {draftDeleteBusy === a.applicationId ? 'Deleting…' : 'Delete draft'}
                      </button>
                    ) : null}
                    <Link to={`/borrower/applications/${a.applicationId}`} className="shrink-0 text-sm text-[var(--bt-orange)] hover:underline">
                      Open
                    </Link>
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="px-5 pb-5 text-sm text-[var(--bt-gray-600)]">You haven’t applied for a loan yet.</p>
          )}
        </BtCard>
      </div>

      {(data.hasIncompleteDraftHint || draft) && (
        <p className="text-sm text-[var(--bt-amber)]">
          {draft ? 'You have a saved loan application draft in this browser. ' : null}
          <Link to="/borrower/apply" className="font-medium text-[var(--bt-orange)] hover:underline">
            Continue or start an application
          </Link>
        </p>
      )}

      <div>
        <Link
          to="/borrower/apply"
          className="bt-btn bt-btn-primary inline-flex"
        >
          Apply for a loan
        </Link>
      </div>
    </div>
  )
}
