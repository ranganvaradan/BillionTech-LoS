import { useEffect, useState } from 'react'
import { ErrorState } from '@/components/ErrorState'
import { generateVkycUrl, getVkycEligibility, getVkycTimeline, resendVkycLink, updateVkycStage } from '@/api/vkyc'
import { formatInstant } from '@/lib/format'
import type { VkycWorkflowGate } from '@/lib/vkycWorkflowGate'

type VkycStage = 'CUSTOMER_JOINED' | 'URL_GENERATED' | 'AGENT_APPROVED' | 'AUDITOR_APPROVED' | 'COMPLETED' | 'REJECTED' | 'EXPIRED' | 'FAILED'

export function VkycDetailsSection({
  applicationId,
  workflowGate,
  onApplicationRefetch,
  onWorkflowStateRefetch,
}: {
  applicationId: string
  workflowGate: VkycWorkflowGate
  onApplicationRefetch: () => void
  onWorkflowStateRefetch?: () => Promise<void> | void
}) {
  const [timeline, setTimeline] = useState<Record<string, unknown> | null>(null)
  const [eligibility, setEligibility] = useState<Record<string, unknown> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function reload() {
    try {
      const [t, e] = await Promise.all([getVkycTimeline(applicationId), getVkycEligibility(applicationId)])
      setTimeline(t)
      setEligibility(e)
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : 'Failed to load VKYC details')
    }
  }

  useEffect(() => {
    void reload()
  }, [applicationId])

  async function onGenerateUrl() {
    setBusy(true)
    setError(null)
    setInfo(null)
    try {
      const latest = await getVkycTimeline(applicationId)
      const latestStatus = String(latest.vkycStatus ?? 'NOT_STARTED').toUpperCase()
      if (['AGENT_APPROVED', 'AUDITOR_APPROVED', 'COMPLETED'].includes(latestStatus)) {
        setError(`Generate blocked at status ${latestStatus}`)
        return
      }
      await generateVkycUrl(applicationId)
      setInfo('VKYC link generated successfully')
      await reload()
      await onWorkflowStateRefetch?.()
      onApplicationRefetch()
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : 'URL generation failed')
    } finally {
      setBusy(false)
    }
  }

  async function onStage(status: VkycStage) {
    setBusy(true)
    setError(null)
    setInfo(null)
    try {
      await updateVkycStage(applicationId, status)
      setInfo(`VKYC stage updated: ${status}`)
      await reload()
      await onWorkflowStateRefetch?.()
      onApplicationRefetch()
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : 'Stage update failed')
    } finally {
      setBusy(false)
    }
  }

  const rows = (timeline?.stages as Array<Record<string, unknown>> | undefined) ?? []
  const isEligible = Boolean(eligibility?.eligible)
  const status = String(timeline?.vkycStatus ?? 'NOT_STARTED').toUpperCase()
  const url = String(timeline?.vkycUrl ?? '')
  const hasUrl = url.trim().length > 0
  const resendBlocked = ['AGENT_APPROVED', 'AUDITOR_APPROVED', 'COMPLETED', 'REJECTED'].includes(status)
  const terminalStatusBlocked = ['AGENT_APPROVED', 'AUDITOR_APPROVED', 'COMPLETED', 'REJECTED', 'AUDITOR_REJECTED', 'AUTO_DECLINED', 'ERROR'].includes(status)
  const showGenerate = workflowGate.canGenerate && !hasUrl
  const showLinkActions = hasUrl && !['COMPLETED'].includes(status)
  const canGenerate = workflowGate.canGenerate && isEligible && !hasUrl && !terminalStatusBlocked
  const canMarkAgentApproved = ['URL_GENERATED', 'CUSTOMER_JOINED'].includes(status) && !workflowGate.applicationTerminal
  const canMarkAuditorApproved = status === 'AGENT_APPROVED' && !workflowGate.applicationTerminal
  const canMarkCompleted = status === 'AUDITOR_APPROVED' && !workflowGate.applicationTerminal

  async function onResend() {
    setBusy(true)
    setError(null)
    setInfo(null)
    try {
      const latest = await getVkycTimeline(applicationId)
      const latestStatus = String(latest.vkycStatus ?? 'NOT_STARTED').toUpperCase()
      if (['AGENT_APPROVED', 'AUDITOR_APPROVED', 'COMPLETED', 'REJECTED', 'AUDITOR_REJECTED', 'AUTO_DECLINED', 'ERROR'].includes(latestStatus)) {
        setError(`Resend blocked at status ${latestStatus}`)
        return
      }
      await resendVkycLink(applicationId)
      setInfo('VKYC link resent')
      await reload()
      await onWorkflowStateRefetch?.()
      onApplicationRefetch()
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : 'VKYC resend failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm mb-0 border-0 p-0 shadow-none">
      <h2 className="mb-1 text-lg font-medium text-slate-900">VKYC</h2>
      <p className="mb-3 text-sm text-slate-600">Video KYC stage progression and review checkpoints.</p>
      {error ? <ErrorState message={error} /> : null}
      {info ? <div className="rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-900">{info}</div> : null}
      <div className="mb-3 rounded-lg border border-slate-200 p-3 text-sm text-slate-700">
        <div className="flex flex-wrap items-center gap-3">
          <span className="font-medium">Status:</span>
          <span className="rounded bg-slate-100 px-2 py-0.5 text-xs">{status}</span>
        </div>
        <div className="mt-2 grid gap-2 sm:grid-cols-2 text-xs text-slate-600">
          <div>Generated: {formatInstant((timeline?.vkycUrlGeneratedAt as string | null) ?? null)}</div>
          <div>Expiry: {formatInstant((timeline?.vkycUrlExpiryAt as string | null) ?? null)}</div>
          <div>Resend count: {String(timeline?.vkycResendCount ?? 0)}</div>
          <div>Last resent: {formatInstant((timeline?.vkycLastResentAt as string | null) ?? null)}</div>
        </div>
      </div>
      <div className="mb-3 flex flex-wrap gap-2">
        {showGenerate ? (
          <button
            type="button"
            className="rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
            disabled={!canGenerate || busy}
            onClick={() => void onGenerateUrl()}
          >
            Generate VKYC Link
          </button>
        ) : null}
        {showLinkActions ? (
          <>
            <button
              type="button"
              className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm text-slate-800"
              onClick={() => void navigator.clipboard.writeText(url)}
              disabled={busy}
            >
              Copy Link
            </button>
            <a
              href={url}
              target="_blank"
              rel="noreferrer"
              className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm text-slate-800"
            >
              Open Link
            </a>
            <button
              type="button"
              className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm text-slate-800 disabled:opacity-50"
              disabled={busy || resendBlocked}
              onClick={() => void onResend()}
            >
              Resend Link
            </button>
          </>
        ) : null}
        <button type="button" className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm disabled:opacity-50" disabled={busy || !canMarkAgentApproved} onClick={() => void onStage('AGENT_APPROVED')}>Agent approved</button>
        <button type="button" className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm disabled:opacity-50" disabled={busy || !canMarkAuditorApproved} onClick={() => void onStage('AUDITOR_APPROVED')}>Auditor approved</button>
        <button type="button" className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm disabled:opacity-50" disabled={busy || !canMarkCompleted} onClick={() => void onStage('COMPLETED')}>Mark completed</button>
      </div>
      <p className="mb-2 text-xs text-slate-500">
        Eligibility: {String(eligibility?.eligible ?? false)} · Workflow reached: {String(workflowGate.workflowReached)} · KYC complete: {String(workflowGate.kycComplete)} · Auditor approved: {String(workflowGate.auditorApproved)}
      </p>
      {workflowGate.applicable && !workflowGate.auditorApproved ? (
        <div
          role="status"
          className="mb-3 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900"
        >
          <strong className="font-semibold">Auditor approval required.</strong>{' '}
          VKYC is treated as completed only after the auditor approves it. URL generation,
          customer joining, or agent approval do <em>not</em> unblock downstream workflow steps.
          Steps after VKYC (per workflow position) remain disabled until then.
        </div>
      ) : null}
      <div className="overflow-x-auto">
        <table className="min-w-full text-left text-xs">
          <thead className="border-b border-slate-200 bg-slate-50 text-slate-600">
            <tr>
              <th className="px-2 py-1.5">Stage</th>
              <th className="px-2 py-1.5">Status</th>
              <th className="px-2 py-1.5">Timestamp</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {rows.length === 0 ? (
              <tr>
                <td className="px-2 py-1.5 text-slate-500" colSpan={3}>No VKYC history yet.</td>
              </tr>
            ) : rows.map((r, i) => (
              <tr key={`${String(r.stage)}-${i}`}>
                <td className="px-2 py-1.5">{String(r.stage ?? '')}</td>
                <td className="px-2 py-1.5">{String(r.status ?? '')}</td>
                <td className="px-2 py-1.5">{String(r.timestamp ?? '')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
