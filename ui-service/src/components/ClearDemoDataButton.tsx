import { useEffect, useState } from 'react'
import { clearDemoApplications, getDemoStatus } from '@/api/demo'
import { ApiError } from '@/api/http'

const DEMO_DISABLED_HINT =
  'Demo mode is not enabled. Please start backend with local profile (or set los.demo.enabled=true).'

function clearErrorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status === 404) {
      const fromBody =
        typeof e.body === 'object' &&
        e.body !== null &&
        'message' in e.body &&
        typeof (e.body as { message?: unknown }).message === 'string'
          ? String((e.body as { message: string }).message).trim()
          : ''
      if (fromBody) return fromBody
      return e.serverMessage?.trim() || DEMO_DISABLED_HINT
    }
    return e.serverMessage?.trim() || e.message
  }
  return e instanceof Error ? e.message : 'Request failed'
}

function successMessage(
  deletedApplications: number,
  deletedBorrowerUsers: number,
  deletedLosPlpMasterRows?: number,
): string {
  if (deletedApplications === 0 && deletedBorrowerUsers === 0 && !(deletedLosPlpMasterRows && deletedLosPlpMasterRows > 0)) {
    return 'No demo data to delete'
  }
  const parts: string[] = []
  if (deletedApplications > 0) {
    parts.push(`${deletedApplications} application${deletedApplications === 1 ? '' : 's'}`)
  }
  if (deletedBorrowerUsers > 0) {
    parts.push(`${deletedBorrowerUsers} borrower account${deletedBorrowerUsers === 1 ? '' : 's'}`)
  }
  if (deletedLosPlpMasterRows && deletedLosPlpMasterRows > 0) {
    parts.push(`${deletedLosPlpMasterRows} PLP program entr${deletedLosPlpMasterRows === 1 ? 'y' : 'ies'} on LOS`)
  }
  if (parts.length === 0) {
    return 'Demo data cleared successfully'
  }
  return `Cleared ${parts.join(', ')}`
}

type ClearDemoDataButtonProps = {
  onCleared: () => void
  className?: string
}

export function ClearDemoDataButton({ onCleared, className }: ClearDemoDataButtonProps) {
  const [demoEnabled, setDemoEnabled] = useState<boolean | null>(null)
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)
  const [resultMsg, setResultMsg] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    void getDemoStatus()
      .then((s) => {
        if (!cancelled) setDemoEnabled(s.demoEnabled)
      })
      .catch(() => {
        if (!cancelled) setDemoEnabled(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  async function confirm() {
    setErr(null)
    setResultMsg(null)
    setBusy(true)
    try {
      const r = await clearDemoApplications()
      setResultMsg(successMessage(r.deletedApplications, r.deletedBorrowerUsers ?? 0, r.deletedLosPlpMasterRows))
      window.dispatchEvent(new CustomEvent('los:demo-data-cleared'))
      onCleared()
      setOpen(false)
    } catch (e) {
      setErr(clearErrorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  if (demoEnabled === null) {
    return null
  }

  if (!demoEnabled) {
    return null
  }

  return (
    <>
      {resultMsg ? (
        <div className="bt-alert bt-alert-success mb-2" role="status">
          {resultMsg}
        </div>
      ) : null}
      <button
        type="button"
        disabled={busy}
        onClick={() => {
          setErr(null)
          setResultMsg(null)
          setOpen(true)
        }}
        className={className ?? 'bt-btn bt-btn-secondary border-amber-300 bg-[var(--bt-amber-bg)] text-[var(--bt-amber)] hover:bg-[var(--bt-amber-bg)]'}
        title="Delete all applications and auto-provisioned borrower accounts"
      >
        {busy ? 'Clearing data…' : 'Reset demo data'}
      </button>
      {open ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="reset-demo-title"
        >
          <div className="bt-card w-full max-w-md border-2 border-[var(--bt-amber)] p-5 shadow-[0_20px_60px_rgba(0,0,0,0.18)]">
            <h2 id="reset-demo-title" className="bt-card-title">
              Reset demo data
            </h2>
            <p className="mt-2 text-sm text-[var(--bt-gray-600)]">
              This will permanently delete all loan applications, related records, auto-provisioned borrower
              accounts, and PLP program/anchor/sub-program entries stored in LOS. Seeded demo users are kept.
              Use the PLP app reset separately to clear remote PLP data. This action cannot be undone.
            </p>
            {err ? <div className="bt-alert bt-alert-error mt-3">{err}</div> : null}
            <div className="mt-4 flex flex-wrap justify-end gap-2">
              <button
                type="button"
                className="bt-btn bt-btn-secondary"
                onClick={() => setOpen(false)}
                disabled={busy}
              >
                Cancel
              </button>
              <button
                type="button"
                className="bt-btn bt-btn-danger disabled:opacity-50"
                onClick={() => void confirm()}
                disabled={busy}
              >
                {busy ? 'Clearing data…' : 'Delete all'}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  )
}
