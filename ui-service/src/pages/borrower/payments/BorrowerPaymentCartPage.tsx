import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  getPaymentCart,
  initiatePayuPayment,
  removePaymentCartLine,
  type PaymentCartLine,
} from '@/api/borrowerInvoiceDiscounting'
import { PageHeader } from '@/components/PageHeader'

function money(n: number): string {
  return `₹${Number(n).toLocaleString('en-IN', { maximumFractionDigits: 0 })}`
}

export function BorrowerPaymentCartPage() {
  const navigate = useNavigate()
  const [lines, setLines] = useState<PaymentCartLine[]>([])
  const [loading, setLoading] = useState(true)
  const [paying, setPaying] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setErr(null)
    try {
      setLines(await getPaymentCart())
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Could not load cart')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const total = lines.reduce((s, l) => s + (l.amountToPay || 0), 0)

  async function pay() {
    setPaying(true)
    setErr(null)
    try {
      const payu = await initiatePayuPayment()
      navigate('/borrower/invoice-discounting/payments/payu', { state: { payu } })
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'PayU initiation failed')
    } finally {
      setPaying(false)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Payment cart"
        description="Pay selected invoice loans via PayU. Repayments apply after admin settlement (PRUS)."
      />
      <Link to="/borrower/invoice-discounting" className="text-sm font-medium text-sky-700 hover:underline">
        ← Back to invoice discounting
      </Link>
      {err && <p className="text-sm text-rose-700 bg-rose-50 border border-rose-200 rounded-lg px-3 py-2">{err}</p>}
      {loading ? (
        <p className="text-sm text-slate-500">Loading…</p>
      ) : lines.length === 0 ? (
        <p className="text-sm text-slate-500">Cart is empty.</p>
      ) : (
        <div className="rounded-xl border border-slate-200 bg-white overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 border-b border-slate-200">
              <tr>
                <th className="px-4 py-2 text-left text-xs font-semibold text-slate-500">Invoice</th>
                <th className="px-4 py-2 text-right text-xs font-semibold text-slate-500">Amount</th>
                <th className="px-4 py-2" />
              </tr>
            </thead>
            <tbody>
              {lines.map((line) => (
                <tr key={line.id} className="border-b border-slate-100">
                  <td className="px-4 py-2 font-mono text-xs">{line.invoiceNumber ?? line.invoiceId}</td>
                  <td className="px-4 py-2 text-right font-medium">{money(line.amountToPay)}</td>
                  <td className="px-4 py-2 text-right">
                    <button
                      type="button"
                      className="text-xs text-rose-600 hover:underline"
                      onClick={() => void removePaymentCartLine(line.id).then(load)}
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="px-4 py-3 flex items-center justify-between bg-slate-50 border-t border-slate-200">
            <span className="font-semibold text-slate-800">Total: {money(total)}</span>
            <button
              type="button"
              disabled={paying}
              onClick={() => void pay()}
              className="rounded-lg bg-slate-900 text-white px-4 py-2 text-sm font-semibold disabled:opacity-50"
            >
              {paying ? 'Starting…' : 'Pay via PayU'}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
