import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listBorrowerCreditNotes, type BorrowerCreditNoteRow } from '@/api/borrowerInvoiceDiscounting'

type Tab = 'open' | 'closed'

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

export function BorrowerCreditNotesPage() {
  const [tab, setTab] = useState<Tab>('open')
  const [rows, setRows] = useState<BorrowerCreditNoteRow[]>([])
  const [loading, setLoading] = useState(true)
  const [err, setErr] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setErr(null)
    try {
      if (tab === 'closed') {
        const data = await listBorrowerCreditNotes('CLOSED')
        data.sort((a, b) => (b.updatedAt ?? b.createdAt ?? '').localeCompare(a.updatedAt ?? a.createdAt ?? ''))
        setRows(data)
      } else {
        const [pending, processing] = await Promise.all([
          listBorrowerCreditNotes('PENDING'),
          listBorrowerCreditNotes('PROCESSING'),
        ])
        const combined = [...pending, ...processing]
        combined.sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''))
        setRows(combined)
      }
    } catch {
      setErr('Could not load credit notes.')
      setRows([])
    } finally {
      setLoading(false)
    }
  }, [tab])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <div className="bt-page">
      <header className="mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Credit notes</h1>
        <p className="text-sm text-slate-500 mt-1">
          Cash discount credit notes from your anchor and how they were allocated to your invoices
        </p>
      </header>

      <div className="flex flex-wrap gap-2 mb-6">
        <button
          type="button"
          className={tab === 'open' ? 'bt-btn bt-btn-primary bt-btn-sm' : 'bt-btn bt-btn-secondary bt-btn-sm'}
          onClick={() => setTab('open')}
        >
          Open
        </button>
        <button
          type="button"
          className={tab === 'closed' ? 'bt-btn bt-btn-primary bt-btn-sm' : 'bt-btn bt-btn-secondary bt-btn-sm'}
          onClick={() => setTab('closed')}
        >
          Closed
        </button>
      </div>

      {err ? <p className="text-sm text-red-600 mb-4">{err}</p> : null}

      <div className="bt-card overflow-hidden p-0">
        <table className="bt-table w-full">
          <thead>
            <tr>
              <th>Note ref</th>
              <th>Issue date</th>
              <th className="text-right">Amount</th>
              <th className="text-right">Allocated</th>
              <th>Status</th>
              <th>{tab === 'closed' ? 'Closed' : 'Created'}</th>
              <th className="text-center">Detail</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={7} className="px-5 py-12 text-center text-sm text-slate-500">
                  Loading…
                </td>
              </tr>
            ) : rows.length === 0 ? (
              <tr>
                <td colSpan={7} className="px-5 py-12 text-center text-sm text-slate-500">
                  No {tab === 'closed' ? 'closed' : 'open'} credit notes
                </td>
              </tr>
            ) : (
              rows.map((row) => (
                <tr key={row.id} className="hover:bg-slate-50/80">
                  <td className="px-5 py-4 font-mono text-sm">{row.noteRefNo}</td>
                  <td className="px-5 py-4">{formatDate(row.noteIssuedDate)}</td>
                  <td className="px-5 py-4 text-right tabular-nums">{money(row.amountReceived)}</td>
                  <td className="px-5 py-4 text-right tabular-nums">{money(allocated(row))}</td>
                  <td className="px-5 py-4">{row.status}</td>
                  <td className="px-5 py-4 text-sm text-slate-600">
                    {formatDateTime(tab === 'closed' ? (row.updatedAt ?? row.createdAt) : row.createdAt)}
                  </td>
                  <td className="px-5 py-4 text-center">
                    <Link to={`/borrower/credit-notes/${row.id}`} className="bt-btn bt-btn-secondary bt-btn-sm">
                      View
                    </Link>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
