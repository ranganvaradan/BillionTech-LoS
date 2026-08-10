import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  getBorrowerCreditNote,
  type BorrowerCreditNoteAllocationRow,
  type BorrowerCreditNoteRow,
} from '@/api/borrowerInvoiceDiscounting'

function money(n: number | null | undefined): string {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 }).format(
    n ?? 0,
  )
}

function formatDate(iso?: string | null): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' })
}

function formatDateTime(iso?: string | null): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function allocated(row: BorrowerCreditNoteRow): number {
  return Math.max(0, (row.amountReceived ?? 0) - (row.amountUnallocated ?? 0))
}

export function BorrowerCreditNoteViewPage() {
  const { id = '' } = useParams()
  const [cn, setCn] = useState<BorrowerCreditNoteRow | null>(null)
  const [allocations, setAllocations] = useState<BorrowerCreditNoteAllocationRow[]>([])
  const [loading, setLoading] = useState(true)
  const [err, setErr] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (!id) return
    setLoading(true)
    setErr(null)
    try {
      const data = await getBorrowerCreditNote(id)
      setCn(data.creditNote)
      setAllocations(data.allocations ?? [])
    } catch {
      setErr('Credit note not found.')
      setCn(null)
      setAllocations([])
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => {
    void load()
  }, [load])

  if (loading) {
    return <p className="text-sm text-slate-500">Loading credit note…</p>
  }
  if (err || !cn) {
    return <p className="text-sm text-slate-500">{err ?? 'Credit note not found.'}</p>
  }

  return (
    <div className="bt-page">
      <header className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Credit note allocations</h1>
          <p className="text-sm text-slate-500 mt-1">
            {cn.noteRefNo} — issued {formatDate(cn.noteIssuedDate)}
          </p>
        </div>
        <Link to="/borrower/credit-notes" className="bt-btn bt-btn-secondary bt-btn-sm">
          Back to credit notes
        </Link>
      </header>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
        <div className="bt-card p-4">
          <p className="text-xs text-slate-500 uppercase tracking-wide">Credit amount</p>
          <p className="text-xl font-semibold mt-1">{money(cn.amountReceived)}</p>
        </div>
        <div className="bt-card p-4">
          <p className="text-xs text-slate-500 uppercase tracking-wide">Allocated</p>
          <p className="text-xl font-semibold mt-1">{money(allocated(cn))}</p>
        </div>
        <div className="bt-card p-4">
          <p className="text-xs text-slate-500 uppercase tracking-wide">Unallocated</p>
          <p className="text-xl font-semibold mt-1">{money(cn.amountUnallocated)}</p>
        </div>
      </div>

      {cn.remarks ? (
        <div className="bt-card p-4 mb-6">
          <p className="text-xs text-slate-500 uppercase tracking-wide">Remarks</p>
          <p className="text-sm mt-1">{cn.remarks}</p>
        </div>
      ) : null}

      <div className="bt-card overflow-hidden p-0">
        <div className="px-5 pt-5 pb-3 font-semibold text-slate-800">Invoice allocations</div>
        <table className="bt-table w-full">
          <thead>
            <tr>
              <th>Invoice</th>
              <th className="text-right">Allocated</th>
              <th className="text-right">Balance due</th>
              <th>Status</th>
              <th>Due date</th>
              <th>Type</th>
              <th>Posted at</th>
            </tr>
          </thead>
          <tbody>
            {allocations.length === 0 ? (
              <tr>
                <td colSpan={7} className="px-5 py-12 text-center text-sm text-slate-500">
                  No allocations yet
                </td>
              </tr>
            ) : (
              allocations.map((row) => (
                <tr key={row.id}>
                  <td className="px-5 py-4">{row.invoiceNumber ?? row.invoiceId}</td>
                  <td className="px-5 py-4 text-right tabular-nums">{money(row.allocatedAmount)}</td>
                  <td className="px-5 py-4 text-right tabular-nums">
                    {row.balanceDueAmount != null ? money(row.balanceDueAmount) : '—'}
                  </td>
                  <td className="px-5 py-4">{row.invoiceStatus ?? '—'}</td>
                  <td className="px-5 py-4">{formatDate(row.dueDate)}</td>
                  <td className="px-5 py-4">{row.allocationType}</td>
                  <td className="px-5 py-4 text-sm">{formatDateTime(row.createdAt)}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
