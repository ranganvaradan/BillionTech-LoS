import { type FormEvent, useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  getLoanAccount,
  initiateLoanPayuPayment,
  postRepayment,
  type BorrowerLoanAccount,
} from '@/api/borrowerPortal'
import { ApiError } from '@/api/http'
import { isUuid } from '@/lib/format'

function money(n: number | null | undefined): string {
  if (n == null) return '—'
  return `₹${Number(n).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</div>
      <div className="mt-2 text-sm font-semibold text-bl-navy tabular-nums">{value}</div>
    </div>
  )
}

/**
 * Post-disbursement loan account view. PayU when configured on the loan product; otherwise LMS manual repay.
 */
export function BorrowerLoanAccountPage() {
  const { loanId } = useParams<{ loanId: string }>()
  const navigate = useNavigate()
  const [account, setAccount] = useState<BorrowerLoanAccount | null>(null)
  const [loadErr, setLoadErr] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [amount, setAmount] = useState('')
  const [payErr, setPayErr] = useState<string | null>(null)
  const [payOk, setPayOk] = useState<string | null>(null)
  const [paying, setPaying] = useState(false)

  const usePayu = account?.payuCheckoutAvailable === true || account?.repaymentMechanism === 'PAYU_PG'

  const refresh = useCallback(async () => {
    if (!loanId || !isUuid(loanId)) return
    setLoading(true)
    setLoadErr(null)
    try {
      setAccount(await getLoanAccount(loanId))
    } catch (e) {
      setLoadErr(e instanceof ApiError ? e.message : 'Failed to load loan account')
    } finally {
      setLoading(false)
    }
  }, [loanId])

  useEffect(() => {
    void refresh()
  }, [refresh])

  async function onPay(e: FormEvent) {
    e.preventDefault()
    if (!loanId) return
    setPayErr(null)
    setPayOk(null)
    const value = Number(amount)
    if (!Number.isFinite(value) || value <= 0) {
      setPayErr('Enter a valid amount greater than 0')
      return
    }
    setPaying(true)
    try {
      if (usePayu) {
        const payu = await initiateLoanPayuPayment(loanId, value)
        navigate(`/borrower/loans/${loanId}/payments/payu`, { state: { payu } })
        return
      }
      const updated = await postRepayment(loanId, value)
      setAccount(updated)
      setAmount('')
      setPayOk(`Repayment of ${money(value)} recorded successfully.`)
    } catch (ex) {
      setPayErr(ex instanceof ApiError ? ex.message : 'Repayment failed')
    } finally {
      setPaying(false)
    }
  }

  if (!loanId || !isUuid(loanId)) {
    return <p className="text-sm text-rose-700">Invalid loan reference.</p>
  }

  return (
    <div className="space-y-6">
      <Link
        to={`/borrower/applications/${loanId}`}
        className="inline-block text-sm font-medium text-slate-600 underline-offset-2 hover:text-slate-900 hover:underline"
      >
        ← Back to application
      </Link>
      <h1 className="text-xl font-semibold tracking-tight text-bl-navy">Loan account</h1>

      {loadErr ? <p className="text-sm text-amber-800">{loadErr}</p> : null}
      {loading ? <p className="text-sm text-slate-600">Loading…</p> : null}

      {account ? (
        <>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            <Stat label="Loan account" value={account.loanAccountNumber} />
            <Stat label="Status" value={account.loanStatus} />
            <Stat label="Outstanding principal" value={money(account.outstandingPrincipal)} />
            <Stat label="Total paid" value={money(account.totalPaid)} />
            <Stat label="Sanctioned" value={money(account.sanctionedAmount)} />
            <Stat label="Disbursed" value={money(account.disbursedAmount)} />
            <Stat label="Overdue amount" value={money(account.overdueAmount)} />
            <Stat label="EMIs paid" value={`${account.paidEmis} / ${account.totalEmis}`} />
            <Stat label="Next EMI date" value={account.nextEmiDate ?? '—'} />
            <Stat label="Next EMI amount" value={money(account.nextEmiAmount)} />
            <Stat label="Last payment" value={account.lastPaymentDate ?? '—'} />
            <Stat label="Days past due" value={String(account.dpd)} />
          </div>

          <div className="max-w-lg bt-card p-5 sm:p-6">
            {account.repaymentMechanism === 'PAYU_PG' && !account.payuCheckoutAvailable ? (
              <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
                PayU is configured for this product but merchant credentials are missing on LOS (
                <code className="text-xs">los.payu.*</code> / <code className="text-xs">PAYU_MERCHANT_KEY</code>).
                Contact your administrator.
              </div>
            ) : null}
            <h2 className="text-sm font-semibold text-bl-navy">
              {usePayu ? 'Pay via PayU' : 'Make a repayment'}
            </h2>
            <p className="mt-2 text-xs leading-relaxed text-slate-500">
              {usePayu
                ? 'You will be redirected to PayU. Repayment posts to your loan after admin settlement (PRUS).'
                : 'Amount is posted to the loan management system and your outstanding balance is updated.'}
            </p>
            {payErr ? <p className="mt-3 text-sm text-rose-700">{payErr}</p> : null}
            {payOk ? <p className="mt-3 text-sm text-emerald-700">{payOk}</p> : null}
            <form onSubmit={onPay} className="mt-4 flex flex-wrap items-end gap-3">
              <label className="flex-1 text-sm">
                <span className="text-slate-600">Amount (₹)</span>
                <input
                  type="number"
                  min="1"
                  step="0.01"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2 focus:border-bl-primary focus:outline-none focus:ring-1 focus:ring-bl-primary/30"
                  placeholder={account.nextEmiAmount ? String(account.nextEmiAmount) : '0.00'}
                />
              </label>
              <button
                type="submit"
                disabled={paying}
                className="bt-btn bt-btn-primary disabled:opacity-50"
              >
                {paying ? 'Processing…' : usePayu ? 'Pay via PayU' : 'Pay now'}
              </button>
            </form>
          </div>

          <div className="flex flex-wrap gap-3">
            <Link
              to={`/borrower/loans/${loanId}/repayment`}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-800 hover:bg-slate-50"
            >
              Repayment schedule
            </Link>
            <Link
              to={`/borrower/loans/${loanId}/statement`}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-800 hover:bg-slate-50"
            >
              Statement of account
            </Link>
            <Link
              to={`/borrower/loans/${loanId}/transactions`}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-800 hover:bg-slate-50"
            >
              Transactions
            </Link>
          </div>
        </>
      ) : null}
    </div>
  )
}
