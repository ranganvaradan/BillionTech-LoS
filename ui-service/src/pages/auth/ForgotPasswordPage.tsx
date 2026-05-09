import { type FormEvent, useState } from 'react'
import { Link } from 'react-router-dom'
import { postForgotPassword } from '@/api/auth'
import { ApiError } from '@/api/http'
import { BrandedAuthFrame } from '@/components/BrandedAuthFrame'

type ForgotPasswordVariant = 'staff' | 'borrower'

type ForgotPasswordPageProps = {
  variant?: ForgotPasswordVariant
}

export function ForgotPasswordPage({ variant = 'staff' }: ForgotPasswordPageProps) {
  const [email, setEmail] = useState('')
  const [res, setRes] = useState<Awaited<ReturnType<typeof postForgotPassword>> | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const backTo = variant === 'borrower' ? '/borrower/login' : '/login'
  const sub =
    variant === 'borrower'
      ? 'For borrower accounts. No email is sent in this build — the API may return a reset path to use locally.'
      : 'No email is sent in this build. The API may return a reset path you can use locally.'

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setErr(null)
    setRes(null)
    setBusy(true)
    try {
      const r = await postForgotPassword(email.trim())
      setRes(r)
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : 'Request failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <BrandedAuthFrame
      title="Forgot password (demo)"
      subtitle={sub}
      variant={variant === 'borrower' ? 'borrower' : 'staff'}
    >
        {err ? <p className="mt-3 text-sm text-rose-700" role="alert">{err}</p> : null}
        <form onSubmit={onSubmit} className="mt-4 space-y-3">
          <label className="block text-sm text-slate-700">
            <span className="mb-0.5 block text-xs text-slate-500">Email</span>
            <input
              type="email"
              className="w-full rounded-md border border-slate-300 px-3 py-2 focus:border-bl-primary focus:outline-none focus:ring-1 focus:ring-bl-primary/30"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </label>
          <button
            type="submit"
            disabled={busy}
            className="w-full rounded-md bg-bl-primary py-2 text-sm font-medium text-white shadow-sm hover:brightness-110 disabled:opacity-50"
          >
            {busy ? 'Working…' : 'Continue'}
          </button>
        </form>
        {res ? (
          <div className="mt-4 rounded border border-amber-200 bg-amber-50 p-3 text-sm text-amber-950">
            <p className="font-medium">Demo response</p>
            <p className="mt-1 whitespace-pre-wrap">{res.message}</p>
            {res.resetToken ? (
              <p className="mt-2 break-all font-mono text-xs">
                <span className="font-sans text-amber-900">Token: </span>
                {res.resetToken}
              </p>
            ) : null}
            {res.resetPath || res.resetLink ? (
              <p className="mt-2">
                <span className="text-amber-900">Open: </span>
                <Link
                  to={res.resetPath ?? res.resetLink ?? '/reset-password'}
                  className="break-all font-mono text-xs text-amber-950 underline"
                >
                  {res.resetPath ?? res.resetLink}
                </Link>
              </p>
            ) : null}
            {res.expiresAt ? <p className="mt-1 text-xs text-amber-900">Expires: {res.expiresAt}</p> : null}
          </div>
        ) : null}
        <p className="mt-4 text-sm">
          <Link to={backTo} className="text-bl-navy/90 underline">
            Back to sign in
          </Link>
        </p>
    </BrandedAuthFrame>
  )
}
