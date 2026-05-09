import { useState } from 'react'
import { disburseApplicationFlow, markReadyForDisbursementFlow } from '@/api/flow'
import { ApiError } from '@/api/http'
import { disburseChecklist } from '@/lib/postCreditGates'
import type { ApplicationResponse } from '@/types/application'

function CheckRow({ done, label }: { done: boolean; label: string }) {
  return (
    <div className="flex items-center gap-2 text-sm">
      <span
        className={[
          'inline-flex h-5 w-5 items-center justify-center rounded-full text-xs font-bold',
          done ? 'bg-emerald-100 text-emerald-900' : 'bg-slate-200 text-slate-600',
        ].join(' ')}
        aria-label={done ? 'Done' : 'Not done'}
      >
        {done ? '✓' : '·'}
      </span>
      <span className={done ? 'text-slate-900' : 'text-slate-600'}>{label}</span>
    </div>
  )
}

export function DisbursementSection({
  applicationId,
  app,
  onRefetch,
}: {
  applicationId: string
  app: ApplicationResponse
  onRefetch: () => void | Promise<unknown>
}) {
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const g = disburseChecklist(app)
  const allGreen =
    g.kyc && g.underwriting && g.cam && g.sanction && g.kfs && g.esign

  const canMarkReady = app.status === 'ESIGN_COMPLETED'
  const canDisburse = app.status === 'READY_FOR_DISBURSEMENT' || app.status === 'ESIGN_COMPLETED'

  async function onReady() {
    setBusy(true)
    setError(null)
    try {
      await markReadyForDisbursementFlow(applicationId)
      await onRefetch()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not mark ready')
    } finally {
      setBusy(false)
    }
  }

  async function onDisburse() {
    setBusy(true)
    setError(null)
    try {
      await disburseApplicationFlow(applicationId)
      await onRefetch()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Disbursement failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-4">
      <h3 className="text-sm font-semibold text-slate-900">Disbursement readiness</h3>
      {error && (
        <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-900">{error}</div>
      )}

      <div className="max-w-md space-y-2 rounded-lg border border-slate-200 bg-slate-50/80 p-4">
        <CheckRow done={g.kyc} label="KYC complete" />
        <CheckRow done={g.underwriting} label="Underwriting complete" />
        <CheckRow done={g.cam} label="CAM reviewed" />
        <CheckRow done={g.sanction} label="Sanction issued" />
        <CheckRow done={g.kfs} label="KFS generated" />
        <CheckRow done={g.esign} label="eSign completed" />
      </div>

      {allGreen && app.status !== 'DISBURSED' && (
        <p className="text-sm text-emerald-800">All checks satisfied for the current application status path.</p>
      )}

      <div className="flex flex-wrap gap-2">
        {canMarkReady && (
          <button
            type="button"
            disabled={busy}
            onClick={() => void onReady()}
            className="rounded-md border border-slate-400 bg-white px-3 py-1.5 text-sm font-medium text-slate-900"
          >
            Mark ready for disbursement
          </button>
        )}
        {canDisburse && (
          <button
            type="button"
            disabled={busy}
            onClick={() => void onDisburse()}
            className="rounded-md bg-emerald-800 px-3 py-1.5 text-sm font-medium text-white"
          >
            {busy ? 'Processing…' : 'Disburse loan'}
          </button>
        )}
      </div>
      {app.status === 'DISBURSED' && (
        <p className="text-sm text-slate-600">This application is already disbursed.</p>
      )}
    </div>
  )
}
