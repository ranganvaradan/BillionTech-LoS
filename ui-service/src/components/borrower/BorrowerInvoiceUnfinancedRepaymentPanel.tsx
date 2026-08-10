import { useCallback, useEffect, useState } from 'react'
import {
  getInvoiceUnfinancedRepayments,
  type BorrowerInvoiceRepayment,
} from '@/api/borrowerInvoiceDiscounting'

function money(n: number | null | undefined): string {
  if (n == null) return '—'
  return `₹${Number(n).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`
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

type Props = {
  invoiceId: string
  invoiceNumber?: string | null
  /** When true, loads immediately (used in expanded table row). */
  embedded?: boolean
}

export function BorrowerInvoiceUnfinancedRepaymentPanel({
  invoiceId,
  invoiceNumber,
  embedded = false,
}: Props) {
  const [expanded, setExpanded] = useState(embedded)
  const [repayments, setRepayments] = useState<BorrowerInvoiceRepayment[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [loadErr, setLoadErr] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setLoadErr(null)
    try {
      setRepayments(await getInvoiceUnfinancedRepayments(invoiceId))
    } catch {
      setLoadErr('Could not load repayment history.')
      setRepayments([])
    } finally {
      setLoading(false)
    }
  }, [invoiceId])

  useEffect(() => {
    if (embedded) {
      void load()
    }
  }, [embedded, load])

  async function toggle() {
    const next = !expanded
    setExpanded(next)
    if (next && repayments === null) {
      await load()
    }
  }

  const body = (
    <div className={embedded ? '' : 'border-t border-slate-100 px-4 py-3'}>
      {loading ? <p className="text-xs text-slate-500">Loading repayments…</p> : null}
      {loadErr ? <p className="text-xs text-amber-700">{loadErr}</p> : null}
      {!loading && repayments && repayments.length === 0 ? (
        <p className="rounded-lg border border-dashed border-slate-200 bg-slate-50 px-4 py-5 text-center text-xs text-slate-500">
          No unfinanced repayments recorded for this invoice.
        </p>
      ) : null}
      {!loading && repayments && repayments.length > 0 ? (
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
  )

  if (embedded) {
    return (
      <div className="rounded-xl border border-slate-200 bg-white shadow-sm p-4">
        <h4 className="mb-3 text-sm font-semibold text-bl-navy">
          Unfinanced repayments{invoiceNumber ? ` — ${invoiceNumber}` : ''}
        </h4>
        {body}
      </div>
    )
  }

  return (
    <div className="rounded-xl border border-slate-200 bg-white shadow-sm">
      <button
        type="button"
        onClick={() => void toggle()}
        className="flex w-full items-center justify-between px-4 py-3 text-left text-sm font-medium text-bl-navy hover:text-bl-primary"
      >
        <span>
          Unfinanced repayments
          {invoiceNumber ? <span className="text-slate-500 font-normal"> — {invoiceNumber}</span> : null}
        </span>
        <span className="text-xs text-slate-400">{expanded ? 'Hide' : 'View'}</span>
      </button>
      {expanded ? body : null}
    </div>
  )
}

export function shouldShowUnfinancedRepayments(inv: {
  status?: string | null
  netAmount?: number | null
  invoiceAmount?: number | null
  balDueAmount?: number | null
}): boolean {
  const totalObligation = Number(inv.netAmount ?? inv.invoiceAmount ?? 0)
  if (totalObligation <= 0) return false
  const status = (inv.status ?? '').toUpperCase()
  if (status === 'CLOSED') return true
  if (inv.balDueAmount != null && inv.balDueAmount < totalObligation) return true
  return false
}
