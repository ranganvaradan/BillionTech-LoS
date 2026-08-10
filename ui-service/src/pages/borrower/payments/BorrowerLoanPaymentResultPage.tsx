import { Link, useParams, useSearchParams } from 'react-router-dom'

export function BorrowerLoanPaymentResultPage() {
  const { loanId } = useParams<{ loanId: string }>()
  const [params] = useSearchParams()
  const status = (params.get('status') ?? 'failure').toLowerCase()
  const txnId = params.get('txnId') ?? ''
  const success = status === 'success'
  const accountPath = loanId ? `/borrower/loans/${loanId}/account` : '/borrower/dashboard'

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
            ? 'Your PayU payment was recorded. The loan repayment applies after admin settlement (PRUS).'
            : 'Payment could not be completed. You can try again from your loan account.'}
        </p>
        {txnId ? <p className="text-xs mt-2 font-mono text-slate-500">Txn: {txnId}</p> : null}
        <div className="mt-6">
          <Link
            to={accountPath}
            className="inline-block rounded-lg bg-slate-900 text-white px-4 py-2 text-sm font-semibold"
          >
            Back to loan account
          </Link>
        </div>
      </div>
    </div>
  )
}
