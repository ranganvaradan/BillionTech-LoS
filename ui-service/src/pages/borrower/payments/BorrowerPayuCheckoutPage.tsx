import { useEffect, useRef } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import type { PayuInitiatePayload } from '@/api/borrowerInvoiceDiscounting'

export function BorrowerPayuCheckoutPage() {
  const location = useLocation()
  const formRef = useRef<HTMLFormElement>(null)
  const payu = (location.state as { payu?: PayuInitiatePayload } | null)?.payu

  useEffect(() => {
    if (payu && formRef.current) formRef.current.submit()
  }, [payu])

  if (!payu) return <Navigate to="/borrower/invoice-discounting/payments/cart" replace />

  return (
    <div className="py-16 text-center text-sm text-slate-500">
      <p className="mb-4">Redirecting to PayU…</p>
      <form ref={formRef} method="post" action={payu.baseUrl}>
        <input type="hidden" name="key" value={payu.key} />
        <input type="hidden" name="txnid" value={payu.txnid} />
        <input type="hidden" name="amount" value={payu.amount} />
        <input type="hidden" name="productinfo" value={payu.productinfo} />
        <input type="hidden" name="firstname" value={payu.firstname} />
        <input type="hidden" name="email" value={payu.email} />
        {payu.phone ? <input type="hidden" name="phone" value={payu.phone} /> : null}
        {payu.udf1 ? <input type="hidden" name="udf1" value={payu.udf1} /> : null}
        <input type="hidden" name="surl" value={payu.surl} />
        <input type="hidden" name="furl" value={payu.furl} />
        <input type="hidden" name="hash" value={payu.hash} />
      </form>
    </div>
  )
}
