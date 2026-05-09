import { type FormEvent, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, Navigate } from 'react-router-dom'
import { postLogin } from '@/api/auth'
import { ApiError } from '@/api/http'
import { BrandedAuthFrame } from '@/components/BrandedAuthFrame'
import { useAuth } from '@/auth/useAuth'
import { isBorrowerRole } from '@/auth/types'

function pickDestAfterLogin(from: string | undefined, role: string): string {
  if (isBorrowerRole(role)) return '/borrower/dashboard'
  if (from && from !== '/login' && !from.startsWith('/borrower')) return from
  return '/dashboard'
}

export function LoginPage() {
  const { login, user } = useAuth()
  const nav = useNavigate()
  const loc = useLocation()
  const from = (loc.state as { from?: string; message?: string } | null)?.from
  const note = (loc.state as { message?: string } | null)?.message

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [err, setErr] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const inFlight = useRef(false)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (inFlight.current) return
    setErr(null)
    inFlight.current = true
    setBusy(true)
    try {
      const u = await postLogin(email.trim(), password)
      login(u)
      nav(pickDestAfterLogin(from, u.role), { replace: true })
    } catch (er) {
      if (er instanceof ApiError) {
        setErr(er.message)
      } else {
        setErr('Sign-in failed')
      }
    } finally {
      inFlight.current = false
      setBusy(false)
    }
  }

  if (user) {
    if (isBorrowerRole(user.role)) {
      return <Navigate to="/borrower/dashboard" replace />
    }
    return <Navigate to="/dashboard" replace />
  }

  return (
    <BrandedAuthFrame
      title="Staff sign in"
      subtitle="Use your work email. Production should use IAM / SSO."
      variant="staff"
    >
      {note ? <p className="mt-3 rounded border border-emerald-200 bg-emerald-50 px-2 py-1 text-sm text-emerald-900">{note}</p> : null}
      {err ? (
        <p className="mt-3 text-sm text-rose-700" role="alert">
          {err}
        </p>
      ) : null}
      <form onSubmit={onSubmit} className="mt-4 space-y-3">
        <label className="block text-sm text-slate-700">
          <span className="mb-0.5 block text-xs text-slate-500">Email</span>
          <input
            type="email"
            className="w-full rounded-md border border-slate-300 px-3 py-2 focus:border-bl-primary focus:outline-none focus:ring-1 focus:ring-bl-primary/30"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoComplete="username"
          />
        </label>
        <label className="block text-sm text-slate-700">
          <span className="mb-0.5 block text-xs text-slate-500">Password</span>
          <input
            type="password"
            className="w-full rounded-md border border-slate-300 px-3 py-2 focus:border-bl-primary focus:outline-none focus:ring-1 focus:ring-bl-primary/30"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            autoComplete="current-password"
          />
        </label>
        <button
          type="submit"
          disabled={busy}
          className="w-full rounded-md bg-bl-primary py-2 text-sm font-medium text-white shadow-sm hover:brightness-110 disabled:opacity-50"
        >
          {busy ? 'Signing in…' : 'Log in'}
        </button>
      </form>
      <p className="mt-4 text-sm">
        <Link to="/forgot-password" className="text-bl-navy/90 underline">
          Forgot password
        </Link>
      </p>
      <p className="mt-2 text-sm text-slate-600">
        <Link to="/borrower" className="text-bl-navy/90 underline">
          Apply for a loan (borrower, no sign-in)
        </Link>
      </p>
    </BrandedAuthFrame>
  )
}
