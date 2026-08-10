import { type FormEvent, useCallback, useState } from 'react'
import {
  getLoanRepayments,
  type BorrowerInvoiceItem,
  type BorrowerInvoiceLoan,
  type BorrowerInvoiceRepayment,
} from '@/api/borrowerInvoiceDiscounting'

function money(n: number | null | undefined): string {
  if (n == null) return '—'
  return `₹${Number(n).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`
}

function loanPrincipal(loan: BorrowerInvoiceLoan): number {
  return Number(loan.disbursedAmount ?? loan.sanctionedAmount ?? loan.requestedAmount ?? 0)
}

function loanInterest(loan: BorrowerInvoiceLoan): number | null {
  const explicit = Number(loan.interestAmount ?? 0)
  if (explicit > 0) return explicit
  const payable = Number(loan.outstandingAmount ?? loan.totalRepayable ?? loanPrincipal(loan))
  const derived = payable - loanPrincipal(loan)
  return derived > 0 ? derived : null
}

function repaymentProgress(totalRepaid: number, outstanding: number): number {
  const repaid = Number.isFinite(totalRepaid) ? totalRepaid : 0
  const out = Number.isFinite(outstanding) ? outstanding : 0
  const total = repaid + out
  if (total <= 0) return repaid > 0 ? 100 : 0
  return Math.min(100, (repaid / total) * 100)
}

function formatPaidAt(iso: string | null): string {
  if (!iso) return '—'
  try {
    return new Date(iso).toLocaleString('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return iso
  }
}

function statusBadge(status: string | null): string {
  const s = (status ?? '').toUpperCase()
  if (s.includes('OVERDUE') || s === 'REJECTED') return 'bg-rose-50 text-rose-700 border-rose-200'
  if (s.includes('DISCOUNTED') || s === 'DISBURSED' || s === 'CLOSED')
    return 'bg-emerald-50 text-emerald-700 border-emerald-200'
  if (s === 'ELIGIBLE' || s === 'BORROWER_ACCEPTED') return 'bg-sky-50 text-sky-700 border-sky-200'
  return 'bg-slate-50 text-slate-600 border-slate-200'
}

type Props = {
  loan: BorrowerInvoiceLoan
  invoice?: BorrowerInvoiceItem
  busyId?: string | null
  repayAmount?: string
  onRepayAmountChange?: (value: string) => void
  onRepay?: (e: FormEvent, loan: BorrowerInvoiceLoan) => void
  /** Hide the inline repay form (e.g. PayU borrowers repay via the payment cart). */
  readOnly?: boolean
}

export function BorrowerInvoiceLoanCard({
  loan,
  invoice,
  busyId,
  repayAmount,
  onRepayAmountChange,
  onRepay,
  readOnly = false,
}: Props) {
  const [expanded, setExpanded] = useState(false)
  const [repayments, setRepayments] = useState<BorrowerInvoiceRepayment[] | null>(null)
  const [repayLoadErr, setRepayLoadErr] = useState<string | null>(null)
  const [repayLoading, setRepayLoading] = useState(false)

  const principal = loanPrincipal(loan)
  const repaid = Number(loan.totalRepaid ?? 0)
  const outstanding = Number(loan.outstandingAmount ?? 0)
  const progress = repaymentProgress(repaid, outstanding)

  const loadRepayments = useCallback(async () => {
    setRepayLoading(true)
    setRepayLoadErr(null)
    try {
      setRepayments(await getLoanRepayments(loan.loanId))
    } catch {
      setRepayLoadErr('Could not load repayment history.')
      setRepayments([])
    } finally {
      setRepayLoading(false)
    }
  }, [loan.loanId])

  async function toggleRepayments() {
    const next = !expanded
    setExpanded(next)
    if (next && repayments === null) {
      await loadRepayments()
    }
  }

  return (
    <article className="w-full overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
      <div className="border-b border-slate-100 bg-gradient-to-r from-slate-50 to-white px-5 py-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="font-mono text-sm font-semibold text-bl-navy">{loan.loanNumber ?? 'Loan'}</h3>
              {invoice?.invoiceNumber ? (
                <span className="rounded-full bg-purple-50 px-2 py-0.5 text-xs font-medium text-purple-700 ring-1 ring-purple-200">
                  Invoice {invoice.invoiceNumber}
                </span>
              ) : null}
            </div>
            <p className="mt-1 text-xs text-slate-500">Due {loan.dueDate ?? '—'}</p>
          </div>
          <span
            className={`inline-block rounded-full border px-2.5 py-0.5 text-xs font-medium ${statusBadge(loan.status)}`}
          >
            {loan.friendlyStatus || loan.status || '—'}
          </span>
        </div>
      </div>

      <div className="grid gap-3 p-5 sm:grid-cols-2 lg:grid-cols-5">
        <Stat label="Financed" value={money(principal)} tone="slate" />
        <Stat label="Interest" value={money(loanInterest(loan))} tone="amber" />
        <Stat label="Total repaid" value={money(repaid)} tone="emerald" />
        <Stat label="Outstanding" value={money(outstanding)} tone="rose" />
        <Stat label="Total obligation" value={money(repaid + outstanding)} tone="sky" />
      </div>

      <div className="px-5 pb-4">
        <div className="mb-1 flex justify-between text-xs text-slate-500">
          <span>Repayment progress</span>
          <span className="font-medium text-slate-700">{progress.toFixed(0)}%</span>
        </div>
        <div className="h-2 overflow-hidden rounded-full bg-slate-100">
          <div
            className={`h-full rounded-full transition-all ${progress >= 100 ? 'bg-emerald-500' : 'bg-bl-primary'}`}
            style={{ width: `${progress}%` }}
          />
        </div>
      </div>

      <div className="border-t border-slate-100 px-5 py-3">
        <button
          type="button"
          onClick={() => void toggleRepayments()}
          className="flex w-full items-center justify-between text-left text-sm font-medium text-bl-navy hover:text-bl-primary"
        >
          <span>Repayment history</span>
          <span className="text-xs text-slate-400">{expanded ? 'Hide' : 'View'}</span>
        </button>

        {expanded ? (
          <div className="mt-3 space-y-3">
            {repayLoading ? <p className="text-xs text-slate-500">Loading repayments…</p> : null}
            {repayLoadErr ? <p className="text-xs text-amber-700">{repayLoadErr}</p> : null}
            {!repayLoading && repayments && repayments.length === 0 ? (
              <p className="rounded-lg border border-dashed border-slate-200 bg-slate-50 px-4 py-6 text-center text-xs text-slate-500">
                No repayments recorded yet for this loan.
              </p>
            ) : null}
            {!repayLoading && repayments && repayments.length > 0 ? (
              <ol className="relative space-y-0 border-l border-slate-200 pl-4">
                {repayments.map((r, idx) => (
                  <li key={r.repaymentId ?? idx} className="relative pb-4 last:pb-0">
                    <span className="absolute -left-[1.35rem] top-1 flex h-2.5 w-2.5 rounded-full bg-emerald-500 ring-4 ring-white" />
                    <div className="rounded-lg border border-slate-100 bg-slate-50/80 px-3 py-2.5">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className="text-sm font-semibold tabular-nums text-emerald-700">
                          {money(r.amount)}
                        </span>
                        <span className="text-[11px] uppercase tracking-wide text-slate-400">
                          {r.friendlySource ?? r.source ?? 'Payment'}
                        </span>
                      </div>
                      <p className="mt-0.5 text-xs text-slate-500">{formatPaidAt(r.paidAt)}</p>
                      {r.reference ? (
                        <p className="mt-1 truncate font-mono text-[10px] text-slate-400">{r.reference}</p>
                      ) : null}
                    </div>
                  </li>
                ))}
              </ol>
            ) : null}
          </div>
        ) : null}
      </div>

      {loan.repayable && !readOnly && onRepay && onRepayAmountChange ? (
        <div className="border-t border-slate-100 bg-slate-50/60 px-5 py-4">
          <form onSubmit={(e) => onRepay(e, loan)} className="flex flex-wrap items-end gap-3">
            <label className="block text-xs font-medium text-slate-600">
              Repay amount
              <input
                type="number"
                min="1"
                step="0.01"
                value={repayAmount ?? ''}
                onChange={(e) => onRepayAmountChange(e.target.value)}
                placeholder={loan.outstandingAmount ? String(loan.outstandingAmount) : '0.00'}
                className="mt-1 block w-36 rounded-md border border-slate-300 px-2 py-1.5 text-sm focus:border-bl-primary focus:outline-none focus:ring-1 focus:ring-bl-primary/30"
              />
            </label>
            <button
              type="submit"
              disabled={busyId === loan.loanId}
              className="bt-btn bt-btn-primary disabled:opacity-50"
            >
              {busyId === loan.loanId ? 'Processing…' : 'Record payment'}
            </button>
          </form>
        </div>
      ) : null}
    </article>
  )
}

function Stat({
  label,
  value,
  tone,
}: {
  label: string
  value: string
  tone: 'slate' | 'emerald' | 'rose' | 'sky' | 'amber'
}) {
  const tones = {
    slate: 'bg-slate-50 text-slate-800',
    emerald: 'bg-emerald-50 text-emerald-800',
    rose: 'bg-rose-50 text-rose-800',
    sky: 'bg-sky-50 text-sky-800',
    amber: 'bg-amber-50 text-amber-900',
  }
  const labelTones = {
    slate: 'text-slate-500',
    emerald: 'text-emerald-600',
    rose: 'text-rose-600',
    sky: 'text-sky-600',
    amber: 'text-amber-700',
  }
  return (
    <div className={`rounded-lg px-3 py-2.5 ${tones[tone]}`}>
      <div className={`text-[11px] font-medium uppercase tracking-wide ${labelTones[tone]}`}>{label}</div>
      <div className="mt-0.5 text-sm font-bold tabular-nums">{value}</div>
    </div>
  )
}
