import { useState } from 'react'
import { clearDemoApplications } from '@/api/demo'
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

type ClearDemoDataButtonProps = {
  onCleared: () => void
  className?: string
}

export function ClearDemoDataButton({ onCleared, className }: ClearDemoDataButtonProps) {
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)
  const [resultMsg, setResultMsg] = useState<string | null>(null)

  async function confirm() {
    setErr(null)
    setResultMsg(null)
    setBusy(true)
    try {
      const r = await clearDemoApplications()
      if (r.deletedApplications === 0) {
        setResultMsg('No applications to delete')
      } else {
        setResultMsg('All demo applications cleared successfully')
      }
      window.dispatchEvent(new CustomEvent('los:demo-data-cleared'))
      onCleared()
      setOpen(false)
    } catch (e) {
      setErr(clearErrorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      {resultMsg ? (
        <p className="mb-2 text-sm text-emerald-800" role="status">
          {resultMsg}
        </p>
      ) : null}
      <button
        type="button"
        disabled={busy}
        onClick={() => {
          setErr(null)
          setResultMsg(null)
          setOpen(true)
        }}
        className={
          className ??
          'rounded-md border border-amber-700 bg-amber-50 px-3 py-1.5 text-sm font-medium text-amber-950 hover:bg-amber-100 disabled:cursor-not-allowed disabled:opacity-60'
        }
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
          <div className="w-full max-w-md rounded-lg border-2 border-amber-300 bg-amber-50/95 p-5 shadow-lg ring-1 ring-amber-200/80">
            <h2 id="reset-demo-title" className="text-base font-semibold text-amber-950">
              Reset demo data
            </h2>
            <p className="mt-2 text-sm text-amber-950/90">
              This will permanently delete all demo applications and related data. This action cannot be undone.
            </p>
            {err ? (
              <p className="mt-3 rounded border border-rose-300 bg-rose-50 px-2 py-1.5 text-sm text-rose-900">
                {err}
              </p>
            ) : null}
            <div className="mt-4 flex flex-wrap justify-end gap-2">
              <button
                type="button"
                className="rounded-md border border-slate-400 bg-white px-3 py-1.5 text-sm text-slate-800"
                onClick={() => setOpen(false)}
                disabled={busy}
              >
                Cancel
              </button>
              <button
                type="button"
                className="rounded-md border border-amber-800 bg-amber-800 px-3 py-1.5 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50"
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
