import { useEffect, useState } from 'react'
import { ErrorState } from '@/components/ErrorState'
import { ProcessOverrideCard } from '@/components/ProcessOverrideCard'
import {
  PHYSICAL_KYC_DOCUMENT_TYPE,
  completePhysicalVkyc,
  generateVkycUrl,
  getVkycEligibility,
  getVkycTimeline,
  resendVkycLink,
  updateVkycStage,
} from '@/api/vkyc'
import type { VkycPkycReasonCode } from '@/api/vkyc'
import { uploadDocument } from '@/api/documents'
import { canCompletePhysicalVkyc, loadSessionUser } from '@/auth/types'
import { formatInstant } from '@/lib/format'
import { applicationPartyLabels } from '@/lib/applicationPartyLabels'
import type { VkycWorkflowGate } from '@/lib/vkycWorkflowGate'
import type { ApplicationIntakeSegment } from '@/types/application'

type VkycStage = 'CUSTOMER_JOINED' | 'URL_GENERATED' | 'AGENT_APPROVED' | 'AUDITOR_APPROVED' | 'COMPLETED' | 'REJECTED' | 'EXPIRED' | 'FAILED'

const PKYC_REASON_OPTIONS: ReadonlyArray<{ value: VkycPkycReasonCode; label: string }> = [
  { value: 'TECHNICAL_ISSUE', label: 'Technical Issue' },
  { value: 'CUSTOMER_REFUSED_VKYC', label: 'Customer Refused VKYC' },
  { value: 'CAMERA_NETWORK_FAILURE', label: 'Camera/Network Failure' },
  { value: 'VKYC_VENDOR_FAILURE', label: 'VKYC Vendor Failure' },
  { value: 'MANUAL_VERIFICATION_APPROVED', label: 'Manual Verification Approved' },
  { value: 'OTHER', label: 'Other' },
]

export function VkycDetailsSection({
  applicationId,
  workflowGate,
  intakeSegment,
  onApplicationRefetch,
  onWorkflowStateRefetch,
}: {
  applicationId: string
  workflowGate: VkycWorkflowGate
  intakeSegment?: ApplicationIntakeSegment | null
  onApplicationRefetch: () => void
  onWorkflowStateRefetch?: () => Promise<void> | void
}) {
  const partyLabels = applicationPartyLabels(intakeSegment)
  const [timeline, setTimeline] = useState<Record<string, unknown> | null>(null)
  const [eligibility, setEligibility] = useState<Record<string, unknown> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [pkycModalOpen, setPkycModalOpen] = useState(false)
  const [pkycReason, setPkycReason] = useState<VkycPkycReasonCode>('TECHNICAL_ISSUE')
  const [pkycComments, setPkycComments] = useState('')
  const [pkycFile, setPkycFile] = useState<File | null>(null)
  const [pkycBusy, setPkycBusy] = useState(false)

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

  async function onPkycSubmit() {
    setPkycBusy(true)
    setError(null)
    setInfo(null)
    try {
      if (!pkycComments.trim()) {
        setError('Comments are required for Physical KYC completion.')
        return
      }
      if (!pkycFile) {
        setError('Upload a PDF, JPG, or PNG document for Physical KYC.')
        return
      }
      const uploaded = await uploadDocument(applicationId, pkycFile, PHYSICAL_KYC_DOCUMENT_TYPE)
      await completePhysicalVkyc(applicationId, {
        reason: pkycReason,
        comments: pkycComments.trim(),
        documentId: uploaded.id,
      })
      setInfo('VKYC completed via Physical KYC.')
      setPkycModalOpen(false)
      setPkycComments('')
      setPkycFile(null)
      await reload()
      await onWorkflowStateRefetch?.()
      onApplicationRefetch()
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : 'Physical KYC completion failed')
    } finally {
      setPkycBusy(false)
    }
  }

  const rows = (timeline?.stages as Array<Record<string, unknown>> | undefined) ?? []
  const isEligible = Boolean(eligibility?.eligible)
  const status = String(timeline?.vkycStatus ?? 'NOT_STARTED').toUpperCase()
  const completionMode = String(timeline?.vkycCompletionMode ?? '').toUpperCase()
  const isPkycPath = completionMode === 'PKYC'
  const userRole = loadSessionUser()?.role ?? ''

  const url = String(timeline?.vkycUrl ?? '')
  const hasUrl = url.trim().length > 0
  const resendBlocked = ['AGENT_APPROVED', 'AUDITOR_APPROVED', 'COMPLETED', 'REJECTED'].includes(status) || isPkycPath
  const terminalStatusBlocked = ['AGENT_APPROVED', 'AUDITOR_APPROVED', 'COMPLETED', 'REJECTED', 'AUDITOR_REJECTED', 'AUTO_DECLINED', 'ERROR'].includes(status) || isPkycPath
  const showGenerate = workflowGate.canGenerate && !hasUrl && !isPkycPath
  const showLinkActions = hasUrl && !['COMPLETED'].includes(status) && !isPkycPath
  const canGenerate = workflowGate.canGenerate && isEligible && !hasUrl && !terminalStatusBlocked && !isPkycPath
  const canMarkAgentApproved = ['URL_GENERATED', 'CUSTOMER_JOINED'].includes(status) && !workflowGate.applicationTerminal
  const canMarkAuditorApproved = status === 'AGENT_APPROVED' && !workflowGate.applicationTerminal
  const canMarkCompleted = status === 'AUDITOR_APPROVED' && !workflowGate.applicationTerminal
  const showOverride = ['REJECTED', 'AUTO_DECLINED', 'AUDITOR_REJECTED', 'FAILED', 'ERROR'].includes(status)
  const allowPhysicalFb = Boolean(timeline?.allowPhysicalKycFallback)
  const auditorClearedUi = ['AUDITOR_APPROVED', 'COMPLETED'].includes(status) || isPkycPath
  const showPkycCta =
    allowPhysicalFb &&
    isEligible &&
    workflowGate.workflowReached &&
    !workflowGate.applicationTerminal &&
    !auditorClearedUi &&
    canCompletePhysicalVkyc(userRole)

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
    <section className="bt-card p-5 mb-0 border-0 p-0 shadow-none">
      <h2 className="mb-1 text-lg font-medium text-slate-900">VKYC</h2>
      <p className="mb-3 text-sm text-slate-600">Video KYC stage progression and review checkpoints.</p>
      {error ? <ErrorState message={error} /> : null}
      {info ? <div className="rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-900">{info}</div> : null}
      <div className="mb-3 rounded-lg border border-slate-200 p-3 text-sm text-slate-700">
        <div className="flex flex-wrap items-center gap-3">
          <span className="font-medium">Status:</span>
          <span className="rounded bg-slate-100 px-2 py-0.5 text-xs">{status}</span>
          {isPkycPath ? (
            <span className="rounded bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-900">
              Completed via Physical KYC
            </span>
          ) : null}
        </div>
        <div className="mt-2 grid gap-2 sm:grid-cols-2 text-xs text-slate-600">
          <div>Generated: {formatInstant((timeline?.vkycUrlGeneratedAt as string | null) ?? null)}</div>
          <div>Expiry: {formatInstant((timeline?.vkycUrlExpiryAt as string | null) ?? null)}</div>
          <div>Resend count: {String(timeline?.vkycResendCount ?? 0)}</div>
          <div>Last resent: {formatInstant((timeline?.vkycLastResentAt as string | null) ?? null)}</div>
          {timeline?.pkycVerifiedAt ? <div className="sm:col-span-2 text-amber-800">PKYC verified at: {formatInstant(String(timeline.pkycVerifiedAt))}</div> : null}
        </div>
      </div>
      {showOverride ? (
        <ProcessOverrideCard
          applicationId={applicationId}
          processCode="VKYC"
          failureCode={status}
          title="Manual override for VKYC rejection/failure"
          onSuccess={onApplicationRefetch}
        />
      ) : null}
      <div className="mb-3 flex flex-wrap gap-2">
        {showGenerate ? (
          <button
            type="button"
            className="bt-btn bt-btn-primary disabled:opacity-50"
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
        {showPkycCta ? (
          <button
            type="button"
            className="rounded-md border border-amber-500 bg-white px-3 py-1.5 text-sm font-medium text-amber-900 shadow-sm hover:bg-amber-50 disabled:opacity-50"
            disabled={busy || pkycBusy}
            onClick={() => {
              setPkycModalOpen(true)
              setError(null)
            }}
          >
            Complete using Physical KYC
          </button>
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
          <tbody className="">
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
      {pkycModalOpen ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="pkyc-modal-title"
        >
          <div className="w-full max-w-lg rounded-lg border border-slate-200 bg-white p-5 shadow-lg">
            <h3 id="pkyc-modal-title" className="text-base font-semibold text-slate-900">
              Complete VKYC via Physical KYC
            </h3>
            <p className="mt-1 text-xs text-slate-600">
              Provide reason, mandatory comments, and upload evidence (PDF/JPG/PNG). Verified-by and timestamp are captured automatically when you submit.
            </p>
            <label className="mt-4 block text-xs text-slate-600">
              <span className="mb-1 block font-medium text-slate-700">Reason *</span>
              <select
                className="w-full rounded border border-slate-300 bg-white px-2 py-1.5 text-sm"
                value={pkycReason}
                onChange={(e) => setPkycReason(e.target.value as VkycPkycReasonCode)}
              >
                {PKYC_REASON_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </label>
            <label className="mt-3 block text-xs text-slate-600">
              <span className="mb-1 block font-medium text-slate-700">Comments *</span>
              <textarea
                className="bt-input w-full text-sm"
                rows={4}
                value={pkycComments}
                onChange={(e) => setPkycComments(e.target.value)}
                placeholder={partyLabels.vkycPkycPlaceholder}
              />
            </label>
            <label className="mt-3 block text-xs text-slate-600">
              <span className="mb-1 block font-medium text-slate-700">Supporting document * (PDF / JPG / PNG)</span>
              <input
                type="file"
                accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg"
                className="block w-full text-sm text-slate-700 file:mr-3 file:rounded-md file:border file:border-slate-300 file:bg-white file:px-2 file:py-1 file:text-xs"
                onChange={(e) => setPkycFile(e.target.files?.[0] ?? null)}
              />
            </label>
            <div className="mt-5 flex flex-wrap justify-end gap-2">
              <button
                type="button"
                className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm text-slate-800 disabled:opacity-50"
                disabled={pkycBusy}
                onClick={() => setPkycModalOpen(false)}
              >
                Cancel
              </button>
              <button
                type="button"
                className="bt-btn bt-btn-primary disabled:opacity-50"
                disabled={pkycBusy}
                onClick={() => void onPkycSubmit()}
              >
                {pkycBusy ? 'Saving…' : 'Complete Physical KYC'}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </section>
  )
}
