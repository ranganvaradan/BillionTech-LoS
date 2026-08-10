import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  getPaymentCart,
  getInvoiceDiscounting,
  initiatePayuPayment,
  removePaymentCartLine,
  type BorrowerInvoiceDiscounting,
  type PaymentCartLine,
} from '@/api/borrowerInvoiceDiscounting'
import { PageHeader } from '@/components/PageHeader'
import { LoadingState } from '@/components/LoadingState'

function money(n: number): string {
  return `₹${Number(n).toLocaleString('en-IN', { maximumFractionDigits: 2, minimumFractionDigits: 0 })}`
}

function moneyOrDash(n: number | null | undefined): string {
  if (n == null || Number(n) <= 0) return '—'
  return money(Number(n))
}

export function BorrowerPaymentCartPage() {
  const navigate = useNavigate()
  const [lines, setLines] = useState<PaymentCartLine[]>([])
  const [loading, setLoading] = useState(true)
  const [paying, setPaying] = useState(false)
  const [err, setErr] = useState<string | null>(null)
  const [overview, setOverview] = useState<BorrowerInvoiceDiscounting | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setErr(null)
    try {
      const [cart, invoiceDiscounting] = await Promise.all([getPaymentCart(), getInvoiceDiscounting()])
      setLines(cart)
      setOverview(invoiceDiscounting)
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
  const paymentEnabled = overview?.paymentEnabled === true

  async function pay() {
    if (!paymentEnabled) {
      setErr('Borrower payment is disabled for this program.')
      return
    }
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
      {err && <div className="bt-alert bt-alert-error">{err}</div>}
      <div className="rounded-xl border border-slate-200 bg-white overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 border-b border-slate-200">
            <tr>
              <th className="px-4 py-2 text-left text-xs font-semibold text-slate-500">Invoice</th>
              <th className="px-4 py-2 text-right text-xs font-semibold text-slate-500">Interest</th>
              <th className="px-4 py-2 text-right text-xs font-semibold text-slate-500">Amount</th>
              <th className="px-4 py-2" />
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={4} className="px-4 py-10">
                  <LoadingState label="Loading cart…" />
                </td>
              </tr>
            ) : lines.length === 0 ? (
              <tr>
                <td colSpan={4} className="px-4 py-10 text-center text-sm text-slate-500">
                  Cart is empty.
                </td>
              </tr>
            ) : (
              lines.map((line) => (
                <tr key={line.id} className="border-b border-slate-100">
                  <td className="px-4 py-2 font-mono text-xs">{line.invoiceNumber ?? line.invoiceId}</td>
                  <td className="px-4 py-2 text-right tabular-nums text-slate-600">
                    {moneyOrDash(line.interestAmount)}
                  </td>
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
              ))
            )}
          </tbody>
        </table>
        {!loading && lines.length > 0 ? (
          <div className="px-4 py-3 flex items-center justify-between bg-slate-50 border-t border-slate-200">
            <span className="font-semibold text-slate-800">Total: {money(total)}</span>
            {paymentEnabled ? (
              <button
                type="button"
                disabled={paying}
                onClick={() => void pay()}
                className="rounded-lg bg-slate-900 text-white px-4 py-2 text-sm font-semibold disabled:opacity-50"
              >
                {paying ? 'Starting…' : 'Pay via PayU'}
              </button>
            ) : (
              <span className="text-sm text-slate-500">Borrower payment is disabled for this program.</span>
            )}
          </div>
        ) : null}
      </div>
    </div>
  )
}
