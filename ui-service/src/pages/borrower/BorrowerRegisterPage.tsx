import { type FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { postRegister } from '@/api/auth'
import { ApiError } from '@/api/http'
import { BrandedAuthFrame } from '@/components/BrandedAuthFrame'
import { useAuth } from '@/auth/useAuth'
import { normalizeRegisterMobile, validateRegisterPassword } from '@/lib/borrowerRegisterValidation'

export function BorrowerRegisterPage() {
  const { login } = useAuth()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [mobile, setMobile] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [err, setErr] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setErr(null)
    const pErr = validateRegisterPassword(password)
    if (pErr) {
      setErr(pErr)
      return
    }
    if (password !== confirm) {
      setErr('Passwords do not match.')
      return
    }
    const m = normalizeRegisterMobile(mobile)
    if (m.length < 10) {
      setErr('Enter a valid mobile number (at least 10 digits).')
      return
    }
    setLoading(true)
    try {
      const u = await postRegister({ name, email, mobile: m, password, confirmPassword: confirm })
      login(u)
      navigate('/borrower/dashboard', { replace: true })
    } catch (ex) {
      setErr(ex instanceof ApiError ? ex.message : 'Registration failed.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <BrandedAuthFrame
      title="Create borrower account"
      subtitle="Email and mobile must be unique. In this demo, OTP and email confirmation are not sent — we only simulate the step."
    >
      {err ? (
        <p className="mt-4 text-sm text-rose-700" role="alert">
          {err}
        </p>
      ) : null}
      <form onSubmit={onSubmit} className="mt-6 space-y-4">
        <label className="block text-sm">
          <span className="text-slate-600">Full name *</span>
          <input
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </label>
        <label className="block text-sm">
          <span className="text-slate-600">Mobile *</span>
          <input
            type="tel"
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
            required
            value={mobile}
            onChange={(e) => setMobile(e.target.value)}
            autoComplete="tel"
          />
        </label>
        <label className="block text-sm">
          <span className="text-slate-600">Email *</span>
          <input
            type="email"
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
          />
        </label>
        <label className="block text-sm">
          <span className="text-slate-600">Password *</span>
          <input
            type="password"
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
          />
        </label>
        <p className="text-xs text-slate-500">At least 8 characters, 1 number, 1 special character.</p>
        <label className="block text-sm">
          <span className="text-slate-600">Confirm password *</span>
          <input
            type="password"
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
            required
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            autoComplete="new-password"
          />
        </label>
        <button
          type="submit"
          disabled={loading}
          className="bt-btn bt-btn-primary w-full justify-center disabled:opacity-50"
        >
          {loading ? 'Please wait…' : 'Register'}
        </button>
      </form>
      <p className="mt-4 text-sm text-slate-600">
        Already have an account?{' '}
        <Link to="/borrower/login" className="text-bl-navy/90 underline">
          Sign in
        </Link>
      </p>
    </BrandedAuthFrame>
  )
}
