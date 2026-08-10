import { useEffect, useState } from 'react'
import {
  createEarlyPayRequest,
  getEarlyPayTodayParameter,
  type BorrowerInvoiceItem,
} from '@/api/borrowerInvoiceDiscounting'

type Props = {
  invoice: BorrowerInvoiceItem
  onSuccess: () => void
  onClose: () => void
}

export function LosEarlyPayRequestModal({ invoice, onSuccess, onClose }: Props) {
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [valid, setValid] = useState(false)
  const [validationMsg, setValidationMsg] = useState('')
  const [paramId, setParamId] = useState('')
  const [discountPct, setDiscountPct] = useState(0)
  const [requestedAmount, setRequestedAmount] = useState(0)
  const [discountAmount, setDiscountAmount] = useState(0)

  const balDue = invoice.balDueAmount ?? invoice.netAmount ?? 0

  useEffect(() => {
    void loadParameter()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [invoice.invoiceId, invoice.subProgramId])

  async function loadParameter() {
    if (!invoice.subProgramId) {
      setValidationMsg('Invoice is not linked to a sub-program.')
      setValid(false)
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      const data = await getEarlyPayTodayParameter(invoice.subProgramId)
      const id = String(data.id ?? '')
      const pct = Number(data.discountPercentage ?? 0)
      const totalMargin = Number(data.totalMarginAmount ?? 0)
      const consumed = Number(data.consumedMarginAmount ?? 0)
      if (!id) {
        setValidationMsg('No Early Pay parameter for today.')
        setValid(false)
        return
      }
      const disc = balDue * (pct / 100)
      const requested = balDue - disc
      const available = totalMargin - consumed
      setParamId(id)
      setDiscountPct(pct)
      setDiscountAmount(disc)
      setRequestedAmount(requested)
      if (requested > available) {
        setValidationMsg("Requested amount exceeds the anchor's available Early Pay limit for today.")
        setValid(false)
      } else {
        setValid(true)
      }
    } catch {
      setValidationMsg('Failed to load Early Pay parameters.')
      setValid(false)
    } finally {
      setLoading(false)
    }
  }

  async function submit() {
    if (!valid || !paramId) return
    setSubmitting(true)
    try {
      await createEarlyPayRequest({
        invoiceId: invoice.invoiceId,
        epParameterId: paramId,
        requestedAmount,
      })
      onSuccess()
      onClose()
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="w-full max-w-lg rounded-xl bg-white shadow-lg border border-slate-200">
        <div className="px-5 py-4 border-b border-slate-100">
          <h2 className="text-base font-semibold text-slate-800">Request Early Pay</h2>
          <p className="text-xs text-slate-500 mt-1">Invoice {invoice.invoiceNumber}</p>
        </div>
        <div className="px-5 py-4 space-y-2 text-sm">
          {loading ? (
            <p className="text-slate-500">Loading…</p>
          ) : (
            <>
              <p>Balance due: ₹{balDue.toFixed(2)}</p>
              {paramId && (
                <>
                  <p>Discount: {discountPct}% (₹{discountAmount.toFixed(2)})</p>
                  <p className="font-semibold text-sky-800">Requested: ₹{requestedAmount.toFixed(2)}</p>
                </>
              )}
              {!valid && validationMsg && (
                <p className="text-amber-800 bg-amber-50 border border-amber-200 rounded px-3 py-2 text-xs">
                  {validationMsg}
                </p>
              )}
            </>
          )}
        </div>
        <div className="px-5 py-4 border-t flex justify-end gap-2">
          <button type="button" onClick={onClose} className="px-3 py-1.5 text-sm border rounded-lg">
            Cancel
          </button>
          <button
            type="button"
            disabled={!valid || submitting || loading}
            onClick={() => void submit()}
            className="px-3 py-1.5 text-sm bg-slate-900 text-white rounded-lg disabled:opacity-50"
          >
            {submitting ? 'Submitting…' : 'Submit'}
          </button>
        </div>
      </div>
    </div>
  )
}
