import { useEffect, useState } from 'react'
import {
  approveProgram,
  getProgramApproval,
  sendBackProgram,
  submitProgramToL2,
  type ProgramApprovalResponse,
  type ProgramApprovalStatus,
} from '@/api/workflow'
import { ApiError } from '@/api/http'

const STATUS_LABEL: Record<ProgramApprovalStatus, string> = {
  DRAFT: 'Draft (L1)',
  PENDING_L2: 'Pending L2 approval',
  SENT_BACK: 'Sent back to L1',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
}

export function ProgramApprovalPanel({
  programId,
  onUpdated,
}: {
  programId: string
  onUpdated?: () => void
}) {
  const [approval, setApproval] = useState<ProgramApprovalResponse | null>(null)
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    void (async () => {
      setLoading(true)
      try {
        setApproval(await getProgramApproval(programId))
      } catch {
        setApproval(null)
      } finally {
        setLoading(false)
      }
    })()
  }, [programId])

  async function load() {
    try {
      setApproval(await getProgramApproval(programId))
    } catch {
      setApproval(null)
    }
  }

  if (loading) {
    return (
      <div className="mt-4 rounded border border-slate-200 bg-white p-4 text-sm text-slate-600">
        Loading approval status…
      </div>
    )
  }

  if (!approval) {
    return (
      <div className="mt-4 rounded border border-slate-200 bg-white p-4">
        <button type="button" className="bt-btn bt-btn--secondary" onClick={() => void load()}>
          Load approval status
        </button>
      </div>
    )
  }

  async function act(fn: () => Promise<ProgramApprovalResponse>) {
    setBusy(true)
    setError(null)
    try {
      setApproval(await fn())
      onUpdated?.()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Action failed')
    } finally {
      setBusy(false)
    }
  }

  const st = approval.approvalStatus
  return (
    <div className="mt-4 rounded border border-slate-200 bg-white p-4">
      <h4 className="bt-card-title mb-2">Program approval (L1 / L2)</h4>
      <p className="text-sm text-slate-600 mb-2">
        Status: <strong>{STATUS_LABEL[st]}</strong>
        {approval.assignedL1UserName ? ` · L1: ${approval.assignedL1UserName}` : null}
        {approval.assignedL2UserName ? ` · L2: ${approval.assignedL2UserName}` : null}
      </p>
      {st === 'SENT_BACK' ? (
        <div className="text-sm text-amber-900 bg-amber-50 border border-amber-200 rounded p-3 mb-2">
          <p className="font-medium mb-1">Sent back — reason for RM</p>
          <p className="whitespace-pre-wrap">
            {approval.approvalNotes?.trim()
              ? approval.approvalNotes
              : 'No send-back reason was provided from PLP.'}
          </p>
        </div>
      ) : approval.approvalNotes ? (
        <p className="text-sm text-amber-800 bg-amber-50 rounded p-2 mb-2 whitespace-pre-wrap">
          {approval.approvalNotes}
        </p>
      ) : null}
      {error ? <p className="text-sm text-red-600 mb-2">{error}</p> : null}
      <div className="flex flex-wrap gap-2">
        {(st === 'DRAFT' || st === 'SENT_BACK') && (
          <button
            type="button"
            disabled={busy}
            className="bt-btn bt-btn--primary"
            onClick={() => void act(() => submitProgramToL2(programId))}
          >
            Submit to L2
          </button>
        )}
        {st === 'PENDING_L2' && (
          <>
            <button
              type="button"
              disabled={busy}
              className="bt-btn bt-btn--primary"
              onClick={() => void act(() => approveProgram(programId))}
            >
              Approve (L2)
            </button>
            <input
              className="bt-input flex-1 min-w-[200px]"
              placeholder="Send-back notes"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
            />
            <button
              type="button"
              disabled={busy || !notes.trim()}
              className="bt-btn bt-btn--secondary"
              onClick={() => void act(() => sendBackProgram(programId, notes.trim()))}
            >
              Send back to L1
            </button>
          </>
        )}
      </div>
    </div>
  )
}
