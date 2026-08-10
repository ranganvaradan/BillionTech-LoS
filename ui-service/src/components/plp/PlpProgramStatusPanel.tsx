import { useCallback, useEffect, useRef, useState } from 'react'
import {
  getProgramApproval,
  refreshPlpProgramStatus,
  type ProgramApprovalResponse,
  type ProgramApprovalStatus,
} from '@/api/workflow'
import { ApiError } from '@/api/http'

const LOS_STATUS_LABEL: Record<ProgramApprovalStatus, string> = {
  DRAFT: 'Pending PLP approval',
  PENDING_L2: 'Pending L2 in PLP',
  SENT_BACK: 'Sent back in PLP',
  APPROVED: 'Approved (mirrored from PLP)',
  REJECTED: 'Not active in PLP',
}

export function PlpProgramStatusPanel({
  programId,
  onUpdated,
  autoRefreshOnMount = true,
}: {
  programId: string
  onUpdated?: () => void
  /** Pull latest status and commercial fields from PLP when the panel opens (e.g. sanction tab). */
  autoRefreshOnMount?: boolean
}) {
  const [status, setStatus] = useState<ProgramApprovalResponse | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const onUpdatedRef = useRef(onUpdated)
  onUpdatedRef.current = onUpdated

  const load = useCallback(async () => {
    setError(null)
    try {
      setStatus(await getProgramApproval(programId))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to load program status')
      setStatus(null)
    }
  }, [programId])

  const refreshFromPlp = useCallback(async () => {
    setBusy(true)
    setError(null)
    try {
      setStatus(await refreshPlpProgramStatus(programId))
      onUpdatedRef.current?.()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to refresh from PLP')
    } finally {
      setBusy(false)
    }
  }, [programId])

  useEffect(() => {
    let cancelled = false
    void (async () => {
      setLoading(true)
      await load()
      if (cancelled) return
      setLoading(false)
      if (autoRefreshOnMount) {
        await refreshFromPlp()
      }
    })()
    return () => {
      cancelled = true
    }
  }, [load, refreshFromPlp, autoRefreshOnMount])

  async function onRefresh() {
    await refreshFromPlp()
  }

  if (loading) {
    return (
      <div className="mt-4 rounded border border-slate-200 bg-white p-4 text-sm text-slate-600">
        Loading PLP program status…
      </div>
    )
  }

  const st = status?.approvalStatus ?? 'DRAFT'
  const plpSt = status?.plpOperationalStatus

  return (
    <div className="mt-4 rounded border border-slate-200 bg-white p-4">
      <h4 className="bt-card-title mb-2">PLP program status</h4>
      <p className="text-sm text-slate-600 mb-2">
        L1/L2 approval is done in PLP. Refresh pulls status plus commercial fields (interest rate,
        dependency, limits) into LOS for the RM view.
      </p>
      <p className="text-sm text-slate-800 mb-2">
        LOS: <strong>{LOS_STATUS_LABEL[st]}</strong>
        {plpSt ? (
          <>
            {' '}
            · PLP: <strong>{plpSt}</strong>
          </>
        ) : null}
      </p>
      {st === 'SENT_BACK' ? (
        <div className="text-sm text-amber-900 bg-amber-50 border border-amber-200 rounded p-3 mb-2">
          <p className="font-medium mb-1">PLP send-back reason</p>
          <p className="whitespace-pre-wrap">
            {status?.approvalNotes?.trim()
              ? status.approvalNotes
              : 'No send-back reason was provided from PLP.'}
          </p>
        </div>
      ) : status?.approvalNotes ? (
        <p className="text-sm text-slate-700 mb-2 whitespace-pre-wrap">{status.approvalNotes}</p>
      ) : null}
      {error ? <p className="text-sm text-red-600 mb-2">{error}</p> : null}
      <div className="flex justify-end">
        <button
          type="button"
          disabled={busy}
          className="inline-flex h-9 w-9 items-center justify-center rounded-md border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 hover:text-slate-900 disabled:opacity-50"
          onClick={() => void onRefresh()}
          aria-label={busy ? 'Refreshing from PLP' : 'Refresh from PLP'}
          title={busy ? 'Refreshing…' : 'Refresh from PLP'}
        >
          <svg
            className={`h-4 w-4 ${busy ? 'animate-spin' : ''}`}
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth={2}
            aria-hidden
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
            />
          </svg>
        </button>
      </div>
    </div>
  )
}
