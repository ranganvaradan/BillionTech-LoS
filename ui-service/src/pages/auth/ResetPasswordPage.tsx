import { type FormEvent, useMemo, useState } from 'react'
import { Link, useSearchParams, useNavigate } from 'react-router-dom'
import { postResetPassword } from '@/api/auth'
import { ApiError } from '@/api/http'

export function ResetPasswordPage() {
  const [search] = useSearchParams()
  const nav = useNavigate()
  const token = useMemo(() => search.get('token')?.trim() ?? '', [search])

  const [pw, setPw] = useState('')
  const [pw2, setPw2] = useState('')
  const [err, setErr] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setErr(null)
    if (!token) {
      setErr('Missing token in the URL. Use the full link from the forgot-password step.')
      return
    }
    if (pw.length < 8) {
      setErr('Use at least 8 characters for the new password.')
      return
    }
    if (pw !== pw2) {
      setErr('Passwords do not match.')
      return
    }
    setBusy(true)
    try {
      await postResetPassword(token, pw)
      nav('/login', { replace: true, state: { message: 'Password updated. You can sign in with the new password.' } })
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : 'Reset failed')
    } finally {
      setBusy(false)
    }
  }

  if (!token) {
    return (
      <div className="min-h-screen bg-slate-100 px-4 py-12">
        <div className="mx-auto w-full max-w-md rounded-lg border border-slate-200 bg-white p-6">
          <p className="text-sm text-rose-700">Missing or empty <code>token</code> in the URL.</p>
          <p className="mt-2 text-sm">
            <Link to="/forgot-password" className="underline">
              Request a reset
            </Link>
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-slate-100 px-4 py-12">
      <div className="mx-auto w-full max-w-md rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-lg font-semibold text-slate-900">Set a new password</h1>
        {err ? <p className="mt-2 text-sm text-rose-700">{err}</p> : null}
        <form onSubmit={onSubmit} className="mt-4 space-y-3">
          <label className="block text-sm text-slate-700">
            <span className="mb-0.5 block text-xs text-slate-500">New password</span>
            <input
              type="password"
              className="bt-input w-full"
              value={pw}
              onChange={(e) => setPw(e.target.value)}
              minLength={8}
              required
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-0.5 block text-xs text-slate-500">Confirm</span>
            <input
              type="password"
              className="bt-input w-full"
              value={pw2}
              onChange={(e) => setPw2(e.target.value)}
              minLength={8}
              required
            />
          </label>
          <button
            type="submit"
            disabled={busy}
            className="bt-btn bt-btn-primary w-full justify-center disabled:opacity-50"
          >
            {busy ? 'Saving…' : 'Update password'}
          </button>
        </form>
        <p className="mt-4 text-sm">
          <Link to="/login" className="text-slate-800 underline">
            Back to sign in
          </Link>
        </p>
      </div>
    </div>
  )
}
