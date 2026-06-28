import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  getRepaymentSchedule,
  getStatement,
  getTransactions,
  type RepaymentRow,
  type ServicingSource,
  type StatementRow,
  type TransactionRow,
} from '@/api/borrowerPortal'
import { ApiError } from '@/api/http'
import { isUuid } from '@/lib/format'

type Mode = 'repayment' | 'statement' | 'transactions'

type LoadState =
  | { status: 'loading' }
  | { status: 'ok'; kind: 'repayment'; source: ServicingSource; data: RepaymentRow[] }
  | { status: 'ok'; kind: 'statement'; source: ServicingSource; data: StatementRow[] }
  | { status: 'ok'; kind: 'transactions'; source: ServicingSource; data: TransactionRow[] }
  | { status: 'err'; message: string }

function sourceNote(mode: Mode, source: ServicingSource): string {
  if (source === 'LMS') {
    return 'Sourced live from the loan management system (Encore).'
  }
  if (mode === 'repayment') {
    return 'Indicative schedule based on your sanction terms. It will reflect the LMS schedule once the loan management system finishes posting it.'
  }
  return 'Built from your actual disbursal and recorded repayments. The full LMS ledger will appear as the loan management system posts entries.'
}

export function BorrowerLoanSubPage({ mode }: { mode: Mode }) {
  const { loanId } = useParams<{ loanId: string }>()
  const [load, setLoad] = useState<LoadState>({ status: 'loading' })
  useEffect(() => {
    if (!loanId || !isUuid(loanId)) return
    // eslint-disable-next-line react-hooks/set-state-in-effect -- fetch post-disbursement servicing data
    setLoad({ status: 'loading' })
    const p =
      mode === 'repayment'
        ? getRepaymentSchedule(loanId).then((r): LoadState => ({ status: 'ok', kind: 'repayment', source: r.source, data: r.rows }))
        : mode === 'statement'
          ? getStatement(loanId).then((r): LoadState => ({ status: 'ok', kind: 'statement', source: r.source, data: r.rows }))
          : getTransactions(loanId).then((r): LoadState => ({ status: 'ok', kind: 'transactions', source: r.source, data: r.rows }))
    p.then(setLoad).catch((e) => setLoad({ status: 'err', message: e instanceof ApiError ? e.message : 'Failed to load' }))
  }, [loanId, mode])

  if (!loanId || !isUuid(loanId)) {
    return <p className="text-sm text-rose-700">Invalid loan reference.</p>
  }
  if (load.status === 'err') {
    return <p className="text-sm text-amber-800">{load.message}</p>
  }
  if (load.status === 'loading') {
    return <p className="text-sm text-slate-600">Loading…</p>
  }
  return (
    <div className="space-y-6">
      <Link
        to={`/borrower/applications/${loanId}`}
        className="inline-block text-sm font-medium text-slate-600 underline-offset-2 hover:text-slate-900 hover:underline"
      >
        ← Back to application
      </Link>
      <div>
        <h1 className="text-xl font-semibold tracking-tight text-bl-navy">
          {mode === 'repayment' ? 'Repayment schedule' : mode === 'statement' ? 'Statement of account' : 'Transactions'}
        </h1>
        <p className="mt-2 flex flex-wrap items-center gap-2 text-xs text-slate-500">
        <span
          className={`inline-block rounded-full border px-2 py-0.5 ${
            load.source === 'LMS'
              ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
              : 'border-slate-200 bg-slate-50 text-slate-600'
          }`}
        >
          {load.source === 'LMS' ? 'LMS' : 'Indicative'}
        </span>
        {sourceNote(mode, load.source)}
        </p>
      </div>
      {load.kind === 'repayment' ? (
        <div className="bt-card overflow-x-auto text-sm shadow-sm">
          <table className="min-w-full text-left text-slate-800">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs font-medium uppercase tracking-wide text-slate-600">
              <tr>
                <th className="px-5 py-3.5">#</th>
                <th className="px-5 py-3.5">Due</th>
                <th className="px-5 py-3.5">EMI</th>
                <th className="px-5 py-3.5">Principal</th>
                <th className="px-5 py-3.5">Interest</th>
                <th className="px-5 py-3.5">Outstanding</th>
              </tr>
            </thead>
            <tbody className="">
              {load.data.map((r) => (
                <tr key={r.installmentNo} className="">
                  <td className="px-5 py-4 tabular-nums">{r.installmentNo}</td>
                  <td className="whitespace-nowrap px-5 py-4">{r.dueDate}</td>
                  <td className="px-5 py-4 tabular-nums">₹{r.emi}</td>
                  <td className="px-5 py-4 tabular-nums">₹{r.principal}</td>
                  <td className="px-5 py-4 tabular-nums">₹{r.interest}</td>
                  <td className="px-5 py-4 tabular-nums">₹{r.outstandingPrincipal}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
      {load.kind === 'statement' ? (
        <div className="bt-card overflow-x-auto text-sm shadow-sm">
          <table className="min-w-full text-left text-slate-800">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs font-medium uppercase tracking-wide text-slate-600">
              <tr>
                <th className="px-5 py-3.5">Date</th>
                <th className="px-5 py-3.5">Description</th>
                <th className="px-5 py-3.5">Credit</th>
                <th className="px-5 py-3.5">Debit</th>
                <th className="px-5 py-3.5">Balance</th>
              </tr>
            </thead>
            <tbody className="">
              {load.data.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-5 py-8 text-center text-slate-500">
                    No statement entries from LMS yet.
                  </td>
                </tr>
              ) : (
                load.data.map((r, idx) => (
                <tr key={`${r.valueDate}-${r.description}-${idx}`} className="">
                  <td className="whitespace-nowrap px-5 py-4">{r.valueDate}</td>
                  <td className="px-5 py-4">{r.description}</td>
                  <td className="px-5 py-4 tabular-nums">{r.credit != null ? `₹${r.credit}` : '—'}</td>
                  <td className="px-5 py-4 tabular-nums">{r.debit != null ? `₹${r.debit}` : '—'}</td>
                  <td className="px-5 py-4 tabular-nums">{r.balance != null ? `₹${r.balance}` : '—'}</td>
                </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      ) : null}
      {load.kind === 'transactions' ? (
        <ul className="space-y-3 text-sm text-slate-800">
          {load.data.map((r) => (
            <li key={`${r.postedAt}-${r.reference}`} className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
              <div className="font-medium text-slate-900">{r.description}</div>
              <div className="text-xs text-slate-500">
                {r.type} · {new Date(r.postedAt).toLocaleString()} · Ref {r.reference || '—'}
              </div>
              <div className="mt-1 tabular-nums">₹{r.amount}</div>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}
