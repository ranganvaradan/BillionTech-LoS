import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { downloadKfsPdfBlob } from '@/api/kfsApi'
import { getBorrowerApplicationDetail, getBorrowerKfsSummary } from '@/api/borrowerPortal'
import { listEsignRequests } from '@/api/esignRequests'
import { ApiError } from '@/api/http'
import { isUuid } from '@/lib/format'

type Tab = 'overview' | 'kfs' | 'loan'

export function BorrowerApplicationDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [tab, setTab] = useState<Tab>('overview')
  const [data, setData] = useState<Awaited<ReturnType<typeof getBorrowerApplicationDetail>> | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const [kfs, setKfs] = useState<Awaited<ReturnType<typeof getBorrowerKfsSummary>> | null>(null)
  const [kfsErr, setKfsErr] = useState<string | null>(null)
  const [esign, setEsign] = useState<Awaited<ReturnType<typeof listEsignRequests>>>([])

  useEffect(() => {
    if (!id || !isUuid(id)) return
    let c = true
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async load detail
    setErr(null)
    void getBorrowerApplicationDetail(id)
      .then((d) => {
        if (c) setData(d)
      })
      .catch((e) => {
        if (c) setErr(e instanceof ApiError ? e.message : 'Could not load application')
      })
    return () => {
      c = false
    }
  }, [id])

  useEffect(() => {
    if (tab !== 'kfs' || !id || !isUuid(id)) return
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async load KFS + eSign
    setKfsErr(null)
    void getBorrowerKfsSummary(id)
      .then((k) => {
        setKfs(k)
        return listEsignRequests(id)
      })
      .then(setEsign)
      .catch((e) => {
        setKfs(null)
        setKfsErr(e instanceof ApiError ? e.message : 'KFS is not available yet')
      })
  }, [tab, id])

  if (!id || !isUuid(id)) {
    return <p className="text-sm text-rose-700">Invalid application link.</p>
  }
  if (err) {
    return <p className="text-sm text-rose-700">{err}</p>
  }
  if (!data) {
    return <p className="text-sm text-slate-600">Loading…</p>
  }

  const disbursed = data.status === 'DISBURSED'
  const showLoan = disbursed

  return (
    <div>
      <p className="text-sm text-slate-500">
        <Link to="/borrower/applications" className="text-slate-800 underline">
          ← All applications
        </Link>
      </p>
      <h1 className="mt-2 text-2xl font-semibold text-slate-900">{data.friendlyStatusHeadline}</h1>
      <p className="text-sm text-slate-600">
        {data.applicationNumber} · {data.product}
      </p>
      <p className="mt-2 text-sm text-slate-800">{data.currentStageMessage}</p>
      <p className="text-xs text-slate-500">{data.estimatedProcessingHint}</p>

      <div className="mt-4 flex flex-wrap gap-2 border-b border-slate-200 pb-2 text-sm">
        <button
          type="button"
          className={tab === 'overview' ? 'font-semibold text-slate-900' : 'text-slate-600'}
          onClick={() => setTab('overview')}
        >
          Overview
        </button>
        <button
          type="button"
          className={tab === 'kfs' ? 'font-semibold text-slate-900' : 'text-slate-600'}
          onClick={() => setTab('kfs')}
        >
          KFS &amp; agreement
        </button>
        {showLoan ? (
          <button
            type="button"
            className={tab === 'loan' ? 'font-semibold text-slate-900' : 'text-slate-600'}
            onClick={() => setTab('loan')}
          >
            Disbursement &amp; loan
          </button>
        ) : null}
      </div>

      {tab === 'overview' ? (
        <div className="mt-4 space-y-4">
          {data.rejectionMessage ? (
            <div className="rounded border border-rose-200 bg-rose-50 p-4 text-sm text-rose-900">
              <p className="font-medium">We could not proceed</p>
              <p className="mt-1">{data.rejectionMessage}</p>
              <div className="mt-3 flex flex-wrap gap-3">
                <a href="mailto:support@example.com" className="text-rose-950 underline">
                  Contact support
                </a>
                {data.reapplyVisible ? (
                  <Link to="/borrower/apply" className="text-rose-950 underline">
                    Re-apply
                  </Link>
                ) : null}
              </div>
            </div>
          ) : null}
          {data.collateralSummary && data.collateralSummary.length > 0 ? (
            <div className="rounded border border-slate-200 bg-slate-50/80 p-4 text-sm text-slate-800">
              <h2 className="font-medium text-slate-900">Security / collateral (your submission)</h2>
              <p className="mt-1 text-xs text-slate-600">What you declared for this secured loan. Official valuation and approval follow our usual checks.</p>
              <ul className="mt-2 space-y-1.5">
                {data.collateralSummary.map((l, i) => (
                  <li key={`${l.label}-${i}`}>
                    <span className="text-slate-500">{l.label}</span>
                    <span className="text-slate-900">: {l.value}</span>
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
          <ol className="space-y-3">
            {data.timeline.map((s) => (
              <li
                key={s.id}
                className="flex gap-3 rounded border border-slate-100 bg-white p-3 text-sm shadow-sm"
              >
                <span
                  className={[
                    'mt-0.5 h-6 w-6 shrink-0 rounded-full text-center text-xs font-bold leading-6',
                    s.state === 'completed'
                      ? 'bg-emerald-100 text-emerald-900'
                      : s.state === 'in_progress'
                        ? 'bg-amber-100 text-amber-900'
                        : s.state === 'locked'
                          ? 'bg-slate-200 text-slate-500'
                          : 'bg-slate-100 text-slate-500',
                  ].join(' ')}
                >
                  {s.state === 'completed' ? '✓' : s.state === 'in_progress' ? '…' : s.state === 'locked' ? '—' : '○'}
                </span>
                <div>
                  <div className="font-medium text-slate-900">{s.label}</div>
                  <div className="text-slate-600">{s.description}</div>
                  {s.completedAt ? (
                    <div className="text-xs text-slate-500">Updated {new Date(s.completedAt).toLocaleString()}</div>
                  ) : null}
                </div>
              </li>
            ))}
          </ol>
        </div>
      ) : null}

      {tab === 'kfs' ? (
        <div className="mt-4">
          {kfsErr ? <p className="text-sm text-amber-800">{kfsErr}</p> : null}
          {kfs ? (
            <div className="rounded border border-slate-200 bg-white p-4 text-sm">
              <h2 className="font-medium text-slate-900">Key Fact Statement (summary)</h2>
              <ul className="mt-2 grid gap-1 sm:grid-cols-2">
                <li>Sanctioned: ₹{kfs.sanctionedAmount}</li>
                <li>Interest: {kfs.interestRate}%</li>
                <li>APR: {kfs.apr}%</li>
                <li>Tenure: {kfs.tenureMonths} mo</li>
                <li>EMI: ₹{kfs.emiAmount}</li>
                <li>Total repayment: ₹{kfs.totalRepayment}</li>
              </ul>
              <p className="mt-2 text-xs text-slate-500">Cooling-off: {kfs.coolingOffComplete ? 'Complete' : 'In progress'}</p>
              <div className="mt-3 flex flex-wrap gap-2">
                <button
                  type="button"
                  className="rounded border border-slate-300 bg-white px-3 py-1.5 text-sm"
                  onClick={() => {
                    void (async () => {
                      if (!id) return
                      const b = await downloadKfsPdfBlob(id)
                      const a = document.createElement('a')
                      a.href = URL.createObjectURL(b)
                      a.download = `kfs-${id}.pdf`
                      a.click()
                    })()
                  }}
                >
                  Download KFS
                </button>
                {kfs.signPending ? (
                  <a
                    href={esign[0]?.signingUrl ?? '#'}
                    className="rounded bg-slate-900 px-3 py-1.5 text-sm text-white"
                    {...(esign[0]?.signingUrl ? { target: '_blank', rel: 'noreferrer' } : { 'aria-disabled': true })}
                  >
                    Sign KFS
                  </a>
                ) : null}
              </div>
            </div>
          ) : null}
        </div>
      ) : null}

      {tab === 'loan' && showLoan ? (
        <div className="mt-4 rounded border border-slate-200 bg-white p-4 text-sm text-slate-800">
          <h2 className="font-medium text-slate-900">Disbursement</h2>
          <p>Amount: ₹{data.disbursedAmount != null ? data.disbursedAmount : '—'}</p>
          <p>Date: {data.disbursedAt ? new Date(data.disbursedAt).toLocaleString() : '—'}</p>
          <p>Account: {data.disbursementAccountMask}</p>
          <p>Loan reference: {data.loanAccountNumber}</p>
          <Link to={`/borrower/loans/${id}/repayment`} className="mt-2 inline-block text-slate-900 underline">
            View repayment schedule
          </Link>
        </div>
      ) : null}
    </div>
  )
}
