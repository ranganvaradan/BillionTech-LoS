import { type FormEvent, useRef, useState } from 'react'
import { Link, useNavigate, useLocation, Navigate } from 'react-router-dom'
import { postLogin } from '@/api/auth'
import { ApiError } from '@/api/http'
import { BrandedAuthFrame } from '@/components/BrandedAuthFrame'
import { useAuth } from '@/auth/useAuth'
import { isBorrowerRole, BORROWER_ROLE } from '@/auth/types'

export function BorrowerLoginPage() {
  const { login, user } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [err, setErr] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const inFlight = useRef(false)
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/borrower/dashboard'

  if (user) {
    if (isBorrowerRole(user.role)) {
      return <Navigate to={from === '/borrower/login' ? '/borrower/dashboard' : from} replace />
    }
    return (
      <BrandedAuthFrame
        title="Wrong sign-in page"
        subtitle="This account is a staff account. Use lender sign-in instead."
        variant="staff"
      >
        <p className="mt-2 text-sm text-slate-600">
          <Link to="/login" className="font-medium text-bl-primary underline">
            Go to staff sign in
          </Link>
        </p>
      </BrandedAuthFrame>
    )
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (inFlight.current) return
    setErr(null)
    inFlight.current = true
    setLoading(true)
    try {
      const u = await postLogin(email.trim(), password)
      if (!isBorrowerRole(u.role)) {
        setErr('This sign-in is for borrowers only. Use the staff sign-in for internal access.')
        return
      }
      login(u)
      navigate(from.startsWith('/borrower') ? from : '/borrower/dashboard', { replace: true })
    } catch (ex) {
      setErr(ex instanceof ApiError ? ex.message : 'Sign-in failed.')
    } finally {
      inFlight.current = false
      setLoading(false)
    }
  }

  return (
    <BrandedAuthFrame
      title="Borrower sign in"
      subtitle="Access your applications and loan account with your registered email and password."
      variant="borrower"
    >
      {err ? (
        <p className="mt-4 text-sm text-rose-700" role="alert">
          {err}
        </p>
      ) : null}
      <form onSubmit={onSubmit} className="mt-4 space-y-4">
        <label className="block text-sm">
          <span className="text-slate-600">Email</span>
          <input
            type="email"
            required
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2 focus:border-bl-primary focus:outline-none focus:ring-1 focus:ring-bl-primary/30"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
          />
        </label>
        <label className="block text-sm">
          <span className="text-slate-600">Password</span>
          <input
            type="password"
            required
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2 focus:border-bl-primary focus:outline-none focus:ring-1 focus:ring-bl-primary/30"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
          />
        </label>
        <button
          type="submit"
          disabled={loading}
          className="w-full rounded-md bg-bl-primary py-2 text-sm font-medium text-white shadow-sm hover:brightness-110 disabled:opacity-50"
        >
          {loading ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
      <p className="mt-4 text-sm text-slate-600">
        <Link to="/borrower/register" className="text-bl-navy/90 underline">
          Create an account
        </Link>
        {' · '}
        <Link to="/borrower/forgot-password" className="underline">
          Forgot password
        </Link>
      </p>
      <p className="mt-4 text-center text-xs text-slate-500">
        Staff: use{' '}
        <Link to="/login" className="text-bl-navy/90 underline">
          lender sign-in
        </Link>{' '}
        instead. ({BORROWER_ROLE})
      </p>
    </BrandedAuthFrame>
  )
}
