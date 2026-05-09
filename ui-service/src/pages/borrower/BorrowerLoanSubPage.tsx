import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getRepaymentScheduleDemo, getStatementDemo, getTransactionsDemo, type RepaymentRow, type StatementRow, type TransactionRow } from '@/api/borrowerPortal'
import { ApiError } from '@/api/http'
import { isUuid } from '@/lib/format'
import { BORROWER_LOAN_DEMO_SOURCE_NOTE } from '@/lib/borrower/borrowerLoanDemoData'

type Mode = 'repayment' | 'statement' | 'transactions'

type LoadState =
  | { status: 'loading' }
  | { status: 'ok'; kind: 'repayment'; data: RepaymentRow[] }
  | { status: 'ok'; kind: 'statement'; data: StatementRow[] }
  | { status: 'ok'; kind: 'transactions'; data: TransactionRow[] }
  | { status: 'err'; message: string }

export function BorrowerLoanSubPage({ mode }: { mode: Mode }) {
  const { loanId } = useParams<{ loanId: string }>()
  const [load, setLoad] = useState<LoadState>({ status: 'loading' })
  useEffect(() => {
    if (!loanId || !isUuid(loanId)) return
    // eslint-disable-next-line react-hooks/set-state-in-effect -- fetch post-disbursement demo data
    setLoad({ status: 'loading' })
    const p =
      mode === 'repayment'
        ? getRepaymentScheduleDemo(loanId).then((data): LoadState => ({ status: 'ok', kind: 'repayment', data }))
        : mode === 'statement'
          ? getStatementDemo(loanId).then((data): LoadState => ({ status: 'ok', kind: 'statement', data }))
          : getTransactionsDemo(loanId).then((data): LoadState => ({ status: 'ok', kind: 'transactions', data }))
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
    <div>
      <p className="text-sm text-slate-500">
        <Link to={`/borrower/applications/${loanId}`} className="text-slate-800 underline">
          ← Back to application
        </Link>
      </p>
      <h1 className="mt-2 text-xl font-semibold text-slate-900">
        {mode === 'repayment' ? 'Repayment schedule' : mode === 'statement' ? 'Statement of account' : 'Transactions'}
      </h1>
      <p className="mt-1 text-xs text-amber-800">{BORROWER_LOAN_DEMO_SOURCE_NOTE} PDF/CSV download is a placeholder in this build.</p>
      {load.kind === 'repayment' ? (
        <div className="mt-4 overflow-x-auto rounded border border-slate-200 bg-white text-sm">
          <table className="min-w-full text-left text-slate-800">
            <thead className="bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-3 py-2">#</th>
                <th className="px-3 py-2">Due</th>
                <th className="px-3 py-2">EMI</th>
                <th className="px-3 py-2">Principal</th>
                <th className="px-3 py-2">Interest</th>
                <th className="px-3 py-2">Outstanding</th>
              </tr>
            </thead>
            <tbody>
              {load.data.map((r) => (
                <tr key={r.installmentNo} className="border-t border-slate-100">
                  <td className="px-3 py-2">{r.installmentNo}</td>
                  <td className="px-3 py-2">{r.dueDate}</td>
                  <td className="px-3 py-2">₹{r.emi}</td>
                  <td className="px-3 py-2">₹{r.principal}</td>
                  <td className="px-3 py-2">₹{r.interest}</td>
                  <td className="px-3 py-2">₹{r.outstandingPrincipal}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
      {load.kind === 'statement' ? (
        <div className="mt-4 overflow-x-auto rounded border border-slate-200 bg-white text-sm">
          <table className="min-w-full text-left text-slate-800">
            <thead className="bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-3 py-2">Date</th>
                <th className="px-3 py-2">Description</th>
                <th className="px-3 py-2">Credit</th>
                <th className="px-3 py-2">Debit</th>
                <th className="px-3 py-2">Balance</th>
              </tr>
            </thead>
            <tbody>
              {load.data.map((r) => (
                <tr key={`${r.valueDate}-${r.description}`} className="border-t border-slate-100">
                  <td className="px-3 py-2 whitespace-nowrap">{r.valueDate}</td>
                  <td className="px-3 py-2">{r.description}</td>
                  <td className="px-3 py-2 tabular-nums">{r.credit != null ? `₹${r.credit}` : '—'}</td>
                  <td className="px-3 py-2 tabular-nums">{r.debit != null ? `₹${r.debit}` : '—'}</td>
                  <td className="px-3 py-2 tabular-nums">₹{r.balance}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
      {load.kind === 'transactions' ? (
        <ul className="mt-4 space-y-2 text-sm text-slate-800">
          {load.data.map((r) => (
            <li key={`${r.postedAt}-${r.reference}`} className="rounded border border-slate-100 bg-white p-3">
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
