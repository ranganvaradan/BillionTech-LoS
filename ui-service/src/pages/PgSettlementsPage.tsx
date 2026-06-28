import { useCallback, useEffect, useState } from 'react'
import { createLosSettlementBatch, listOpenLosPip, type LosPaymentInProgressRow } from '@/api/pgSettlements'
import { PageHeader } from '@/components/PageHeader'
import { loanProductLabel } from '@/catalog/loanProducts'

export function PgSettlementsPage() {
  const [pipRows, setPipRows] = useState<LosPaymentInProgressRow[]>([])
  const [selected, setSelected] = useState<Set<string>>(() => new Set())
  const [utr, setUtr] = useState('')
  const [settlementDate, setSettlementDate] = useState(() => new Date().toISOString().slice(0, 10))
  const [remarks, setRemarks] = useState('')
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setPipRows(await listOpenLosPip())
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load open PRUS lines')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const formatCurrency = (n: number) =>
    new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(n || 0)

  const toggle = (id: string) => {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const applySettlement = async () => {
    if (selected.size === 0 || !utr.trim()) {
      setError('Select PRUS lines and enter settlement UTR')
      return
    }
    setSubmitting(true)
    setError('')
    try {
      await createLosSettlementBatch({
        settlementDate,
        settlementUtr: utr.trim(),
        pipIds: Array.from(selected),
        remarks: remarks.trim() || undefined,
      })
      setSelected(new Set())
      setUtr('')
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Settlement failed')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div>
      <PageHeader
        title="PG settlements (PRUS)"
        description="Settle successful PayU collections for LOS personal/term loans. Posts repayments to LMS with your bank UTR. Invoice discounting settlements remain in PLP Platform Admin."
      />

      {error ? (
        <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>
      ) : null}

      <div className="mb-6 rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="text-sm font-semibold text-slate-700 mb-3">Create settlement batch</h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3 mb-3">
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Settlement date</label>
            <input
              type="date"
              value={settlementDate}
              onChange={(e) => setSettlementDate(e.target.value)}
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Settlement UTR</label>
            <input
              type="text"
              value={utr}
              onChange={(e) => setUtr(e.target.value)}
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
              placeholder="Bank UTR / reference"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Remarks (optional)</label>
            <input
              type="text"
              value={remarks}
              onChange={(e) => setRemarks(e.target.value)}
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm"
            />
          </div>
        </div>
        <button
          type="button"
          disabled={submitting || selected.size === 0}
          onClick={() => void applySettlement()}
          className="rounded-lg bg-bl-navy px-4 py-2 text-sm font-semibold text-white hover:opacity-90 disabled:opacity-50"
        >
          {submitting ? 'Applying…' : `Apply settlement (${selected.size} selected)`}
        </button>
      </div>

      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm">
        <div className="px-5 py-3 border-b border-slate-100 bg-slate-50/80">
          <span className="text-xs font-semibold text-slate-500 uppercase">Open PRUS / PIP (LOS loans)</span>
        </div>
        {loading ? (
          <p className="p-8 text-sm text-slate-400 text-center">Loading…</p>
        ) : pipRows.length === 0 ? (
          <p className="p-8 text-sm text-slate-400 text-center">No open payment-in-progress lines.</p>
        ) : (
          <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                <th className="px-4 py-2 w-10 align-middle" />
                <th className="px-4 py-2 text-left text-xs font-semibold text-slate-500 uppercase align-middle">Application</th>
                <th className="px-4 py-2 text-left text-xs font-semibold text-slate-500 uppercase align-middle">Product</th>
                <th className="px-4 py-2 text-right text-xs font-semibold text-slate-500 uppercase align-middle whitespace-nowrap tabular-nums">Amount</th>
                <th className="px-4 py-2 text-left text-xs font-semibold text-slate-500 uppercase align-middle">Created</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {pipRows.map((row) => (
                <tr key={row.id} className="hover:bg-slate-50/50">
                  <td className="px-4 py-2 text-center align-middle">
                    <input type="checkbox" checked={selected.has(row.id)} onChange={() => toggle(row.id)} />
                  </td>
                  <td className="px-4 py-2 font-mono text-xs align-middle">{row.applicationNumber ?? row.applicationId}</td>
                  <td className="px-4 py-2 text-xs align-middle">
                    {row.loanProduct ? loanProductLabel(row.loanProduct) : '—'}
                  </td>
                  <td className="px-4 py-2 text-right font-medium tabular-nums whitespace-nowrap align-middle">{formatCurrency(row.principalAmount)}</td>
                  <td className="px-4 py-2 text-xs text-slate-500 align-middle whitespace-nowrap">
                    {row.createdAt ? new Date(row.createdAt).toLocaleString() : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        )}
      </div>
    </div>
  )
}
