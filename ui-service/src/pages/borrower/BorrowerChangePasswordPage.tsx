import { type FormEvent, useRef, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { postChangePassword } from '@/api/auth'
import { ApiError } from '@/api/http'
import { BrandedAuthFrame } from '@/components/BrandedAuthFrame'
import { useAuth } from '@/auth/useAuth'
import { isBorrowerRole } from '@/auth/types'

/**
 * Forced password reset for borrower accounts that were auto-provisioned from a staff application
 * submission (temporary mobile-based password). Shown right after sign-in when
 * {@code passwordResetRequired} is set; updates the session and continues to the dashboard.
 */
export function BorrowerChangePasswordPage() {
  const { user, login } = useAuth()
  const nav = useNavigate()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [err, setErr] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const inFlight = useRef(false)

  if (!user) {
    return <Navigate to="/borrower/login" replace />
  }
  if (!isBorrowerRole(user.role)) {
    return <Navigate to="/" replace />
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (inFlight.current) return
    setErr(null)
    if (newPassword !== confirmPassword) {
      setErr('Passwords do not match')
      return
    }
    inFlight.current = true
    setBusy(true)
    try {
      const updated = await postChangePassword({ currentPassword, newPassword, confirmPassword })
      login(updated)
      nav('/borrower/dashboard', { replace: true })
    } catch (ex) {
      setErr(ex instanceof ApiError ? ex.message : 'Could not update password')
    } finally {
      inFlight.current = false
      setBusy(false)
    }
  }

  return (
    <BrandedAuthFrame
      title="Set a new password"
      subtitle="Your account was created with a temporary password. Set your own password to continue."
      variant="borrower"
    >
      {err ? (
        <p className="mt-4 text-sm text-rose-700" role="alert">
          {err}
        </p>
      ) : null}
      <form onSubmit={onSubmit} className="mt-4 space-y-4">
        <label className="block text-sm">
          <span className="text-slate-600">Temporary / current password</span>
          <input
            type="password"
            required
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2 focus:border-bl-primary focus:outline-none focus:ring-1 focus:ring-bl-primary/30"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            autoComplete="current-password"
          />
        </label>
        <label className="block text-sm">
          <span className="text-slate-600">New password</span>
          <input
            type="password"
            required
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2 focus:border-bl-primary focus:outline-none focus:ring-1 focus:ring-bl-primary/30"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            autoComplete="new-password"
          />
        </label>
        <label className="block text-sm">
          <span className="text-slate-600">Confirm new password</span>
          <input
            type="password"
            required
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2 focus:border-bl-primary focus:outline-none focus:ring-1 focus:ring-bl-primary/30"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            autoComplete="new-password"
          />
        </label>
        <button
          type="submit"
          disabled={busy}
          className="bt-btn bt-btn-primary w-full justify-center disabled:opacity-50"
        >
          {busy ? 'Saving…' : 'Set password and continue'}
        </button>
      </form>
      <p className="mt-4 text-center text-xs text-slate-500">
        Use at least 8 characters including a number and a special character.
      </p>
    </BrandedAuthFrame>
  )
}
