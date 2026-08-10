import { useEffect, useState } from 'react'
import { applyManualOverride, getManualOverrideEligibility } from '@/api/flow'
import { messageForKycAction } from '@/api/kycErrorMessage'
import { AppSectionCard } from '@/components/ui/AppSectionCard'

export function ProcessOverrideCard({
  applicationId,
  processCode,
  failureCode,
  title = 'Manual override',
  onSuccess,
}: {
  applicationId: string
  processCode: string
  failureCode: string
  title?: string
  onSuccess: () => void
}) {
  const [eligibility, setEligibility] = useState<Record<string, unknown> | null>(null)
  const [loading, setLoading] = useState(false)
  const [busy, setBusy] = useState(false)
  const [reason, setReason] = useState('')
  const [remarks, setRemarks] = useState('')
  const [approvalReference, setApprovalReference] = useState('')
  const [manualBureauScore, setManualBureauScore] = useState('')
  const [creditRiskScore, setCreditRiskScore] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    // eslint-disable-next-line react-hooks/set-state-in-effect -- reload eligibility on process/failure changes
    setLoading(true)
    // eslint-disable-next-line react-hooks/set-state-in-effect -- clear stale messages before fetch
    setError(null)
    // eslint-disable-next-line react-hooks/set-state-in-effect -- clear stale messages before fetch
    setInfo(null)
    void getManualOverrideEligibility(applicationId, processCode, failureCode)
      .then((x) => {
        if (!cancelled) setEligibility(x)
      })
      .catch((e) => {
        if (!cancelled) setError(messageForKycAction(e))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [applicationId, processCode, failureCode])

  const canOverride = Boolean(eligibility?.canOverride)
  const requiresRemarks = Boolean(eligibility?.requiresRemarks)
  const requiresApproval = Boolean(eligibility?.requiresApproval)
  const requiresScore =
    Boolean(eligibility?.requiresScore) || processCode.trim().toUpperCase() === 'UNDERWRITING'
  const reasonText = String(eligibility?.reason ?? '')

  async function onOverride() {
    setError(null)
    setInfo(null)
    if (reason.trim().length < 3) {
      setError('Please provide a clear override reason.')
      return
    }
    if (requiresRemarks && remarks.trim().length === 0) {
      setError('Remarks are required for this override.')
      return
    }
    if (requiresApproval && approvalReference.trim().length === 0) {
      setError('Approval reference is required for this override.')
      return
    }
    let bureauScore: number | undefined
    let riskScore: number | undefined
    if (requiresScore) {
      const n = Number.parseInt(manualBureauScore, 10)
      if (!Number.isFinite(n) || n <= 0) {
        setError('Enter a valid updated bureau/credit score (positive whole number).')
        return
      }
      bureauScore = n
      const riskRaw = creditRiskScore.trim()
      if (riskRaw) {
        const r = Number.parseInt(riskRaw, 10)
        if (!Number.isFinite(r) || r <= 0) {
          setError('Risk score must be a positive whole number when provided.')
          return
        }
        riskScore = r
      }
    }
    setBusy(true)
    try {
      await applyManualOverride(applicationId, {
        processCode,
        failureCode,
        overrideReason: reason.trim(),
        remarks: remarks.trim() || undefined,
        approvalReference: approvalReference.trim() || undefined,
        manualBureauScore: bureauScore,
        creditRiskScore: riskScore,
      })
      setInfo(
        requiresScore
          ? 'Manual override applied. Application advanced to CAM ready with updated score.'
          : 'Manual override applied. Workflow can proceed from the overridden state.',
      )
      setReason('')
      setRemarks('')
      setApprovalReference('')
      setManualBureauScore('')
      setCreditRiskScore('')
      onSuccess()
    } catch (e) {
      setError(messageForKycAction(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AppSectionCard
      tone="warning"
      unstyledBody
      className="mt-3 p-4 text-sm text-amber-950"
      title={title}
      badge={<span className="bt-section-card__chip bt-section-card__chip--warning">Controlled override</span>}
    >
      {loading ? <p className="text-xs">Checking override eligibility…</p> : null}
      {!loading && !canOverride ? (
        <p className="text-xs">
          Override unavailable{reasonText ? `: ${reasonText}` : '.'}
        </p>
      ) : null}
      {canOverride ? (
        <div className="space-y-2">
          {requiresScore ? (
            <div className="grid gap-2 sm:grid-cols-2">
              <div>
                <label className="mb-1 block text-xs font-semibold text-amber-950">
                  Updated bureau / credit score (mandatory)
                </label>
                <input
                  type="number"
                  min={1}
                  max={900}
                  step={1}
                  value={manualBureauScore}
                  onChange={(e) => setManualBureauScore(e.target.value)}
                  className="w-full rounded-lg border border-amber-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-[var(--bt-orange)] focus:outline-none focus:ring-2 focus:ring-[var(--bt-orange)]/20"
                  placeholder="e.g. 720"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs font-semibold text-amber-950">
                  Aggregate risk score (optional)
                </label>
                <input
                  type="number"
                  min={1}
                  max={900}
                  step={1}
                  value={creditRiskScore}
                  onChange={(e) => setCreditRiskScore(e.target.value)}
                  className="w-full rounded-lg border border-amber-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-[var(--bt-orange)] focus:outline-none focus:ring-2 focus:ring-[var(--bt-orange)]/20"
                  placeholder="Defaults to bureau score"
                />
              </div>
            </div>
          ) : null}
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={2}
            className="w-full rounded-lg border border-amber-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-[var(--bt-orange)] focus:outline-none focus:ring-2 focus:ring-[var(--bt-orange)]/20"
            placeholder="Override reason (mandatory)"
          />
          <textarea
            value={remarks}
            onChange={(e) => setRemarks(e.target.value)}
            rows={2}
            className="w-full rounded-lg border border-amber-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-[var(--bt-orange)] focus:outline-none focus:ring-2 focus:ring-[var(--bt-orange)]/20"
            placeholder={`Remarks${requiresRemarks ? ' (mandatory)' : ' (optional)'}`}
          />
          <input
            value={approvalReference}
            onChange={(e) => setApprovalReference(e.target.value)}
            className="w-full rounded-lg border border-amber-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-[var(--bt-orange)] focus:outline-none focus:ring-2 focus:ring-[var(--bt-orange)]/20"
            placeholder={`Approval reference${requiresApproval ? ' (mandatory)' : ' (optional)'}`}
          />
          <button
            type="button"
            onClick={() => void onOverride()}
            disabled={busy}
            className="bt-btn bt-btn-primary disabled:opacity-60"
          >
            {busy ? 'Applying override…' : 'Override failure'}
          </button>
        </div>
      ) : null}
      {error ? <p className="mt-2 text-xs text-red-700">{error}</p> : null}
      {info ? <p className="mt-2 text-xs text-emerald-700">{info}</p> : null}
    </AppSectionCard>
  )
}
