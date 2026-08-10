import { Link, useSearchParams } from 'react-router-dom'

export function BorrowerPaymentResultPage() {
  const [params] = useSearchParams()
  const status = (params.get('status') ?? 'failure').toLowerCase()
  const txnId = params.get('txnId') ?? ''
  const success = status === 'success'

  return (
    <div className="max-w-lg mx-auto py-10">
      <div
        className={`rounded-xl border p-8 text-center ${
          success ? 'bg-emerald-50 border-emerald-200' : 'bg-rose-50 border-rose-200'
        }`}
      >
        <h1 className={`text-xl font-bold ${success ? 'text-emerald-800' : 'text-rose-800'}`}>
          {success ? 'Payment received' : 'Payment failed'}
        </h1>
        <p className="text-sm mt-3 text-slate-600">
          {success
            ? 'Your payment was recorded. Loan repayments apply after settlement (PRUS).'
            : 'Payment could not be completed. Cart items were restored.'}
        </p>
        {txnId ? <p className="text-xs mt-2 font-mono text-slate-500">Txn: {txnId}</p> : null}
        <div className="mt-6 flex flex-col gap-2">
          <Link
            to="/borrower/invoice-discounting"
            className="rounded-lg bg-slate-900 text-white px-4 py-2 text-sm font-semibold"
          >
            Back to invoices
          </Link>
          {!success && (
            <Link to="/borrower/invoice-discounting/payments/cart" className="text-sm text-sky-700 hover:underline">
              View cart
            </Link>
          )}
        </div>
      </div>
    </div>
  )
}
