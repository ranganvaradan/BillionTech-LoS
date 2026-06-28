import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  fetchCamPdfBlob,
  getCam,
  rejectCamMemorandum,
  sendBackCam,
  submitCam,
  updateCam,
} from '@/api/cam'
import { markCamReviewedFlow } from '@/api/flow'
import { ApiError } from '@/api/http'
import { applicationPartyLabels } from '@/lib/applicationPartyLabels'
import type { ApplicationResponse } from '@/types/application'
import type { CamResponse, CamUpdateRequest } from '@/types/cam'

const SECTION_ORDER = [
  { key: 'executiveSummary', label: 'Executive summary' },
  { key: 'borrowerProfile', label: 'Borrower profile' },
  { key: 'loanRequest', label: 'Loan request' },
  { key: 'kycCompliance', label: 'KYC and compliance' },
  { key: 'bureauCredit', label: 'Bureau and credit' },
  { key: 'incomeCashflow', label: 'Income and cashflow' },
  { key: 'collateral', label: 'Collateral' },
  { key: 'scorecardEvaluation', label: 'Policy / scorecard' },
  { key: 'assignment', label: 'Assignment' },
  { key: 'riskFlags', label: 'Risk flags' },
] as const

function asRecord(v: unknown): Record<string, string> {
  if (!v || typeof v !== 'object' || Array.isArray(v)) return {}
  const out: Record<string, string> = {}
  for (const [k, val] of Object.entries(v as Record<string, unknown>)) {
    if (typeof val === 'string' || typeof val === 'number' || typeof val === 'boolean') {
      out[k] = String(val)
    }
  }
  return out
}

/** Pre-fill sanctioning basis from application intake when CAM fields are still empty. */
function resolveSanctioningDefaults(
  cam: CamResponse,
  app: ApplicationResponse,
): {
  amount: string
  tenure: string
  rate: string
  decision: string
  fromApplication: boolean
} {
  const amount =
    cam.recommendedAmount != null
      ? String(cam.recommendedAmount)
      : app.requestedAmount != null
        ? String(app.requestedAmount)
        : ''
  const tenure =
    cam.recommendedTenureMonths != null
      ? String(cam.recommendedTenureMonths)
      : app.tenureMonths != null
        ? String(app.tenureMonths)
        : ''
  const rate =
    cam.recommendedRate != null
      ? String(cam.recommendedRate)
      : app.interestRate != null
        ? String(app.interestRate)
        : ''
  const decision = cam.section6RecommendedDecision ?? ''
  const fromApplication =
    (cam.recommendedAmount == null && app.requestedAmount != null) ||
    (cam.recommendedTenureMonths == null && app.tenureMonths != null) ||
    (cam.recommendedRate == null && app.interestRate != null)
  return { amount, tenure, rate, decision, fromApplication }
}

export function CamSection({
  applicationId,
  app,
  onRefetch,
}: {
  applicationId: string
  app: ApplicationResponse
  onRefetch: () => void | Promise<unknown>
}) {
  const [cam, setCam] = useState<CamResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [actionBusy, setActionBusy] = useState(false)
  const [pdfLoading, setPdfLoading] = useState(false)
  const [sectionDrafts, setSectionDrafts] = useState<Record<string, string>>({})
  const [observations, setObservations] = useState('')
  const [riskAssessment, setRiskAssessment] = useState('')
  const [mitigants, setMitigants] = useState('')
  const [recommendedDecision, setRecommendedDecision] = useState('')
  const [recAmt, setRecAmt] = useState('')
  const [recTen, setRecTen] = useState('')
  const [recRate, setRecRate] = useState('')
  const [condPre, setCondPre] = useState('')
  const [condSub, setCondSub] = useState('')
  const [officerRem, setOfficerRem] = useState('')
  const [managerRem, setManagerRem] = useState('')
  const [prefilledFromApplication, setPrefilledFromApplication] = useState(false)

  const sectionOrder = useMemo(() => {
    const profileLabel = applicationPartyLabels(app.intakeSegment).camProfileSection
    return SECTION_ORDER.map((s) =>
      s.key === 'borrowerProfile' ? { ...s, label: profileLabel } : s,
    )
  }, [app.intakeSegment])

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const c = await getCam(applicationId)
      setCam(c)
      setObservations(c.section5Observations ?? '')
      setRiskAssessment(c.section5RiskAssessment ?? '')
      setMitigants(c.section5Mitigants ?? '')
      const sanctionDefaults = resolveSanctioningDefaults(c, app)
      setRecommendedDecision(sanctionDefaults.decision)
      setRecAmt(sanctionDefaults.amount)
      setRecTen(sanctionDefaults.tenure)
      setRecRate(sanctionDefaults.rate)
      setPrefilledFromApplication(sanctionDefaults.fromApplication)
      setCondPre((c.conditionsPrecedent ?? []).join('\n'))
      setCondSub((c.conditionsSubsequent ?? []).join('\n'))
      setOfficerRem(c.creditOfficerRemarks ?? '')
      setManagerRem(c.creditManagerRemarks ?? '')
      const ex = c.sectionExtended ?? {}
      const nxt: Record<string, string> = {}
      for (const s of SECTION_ORDER) {
        const esc = c.editableSections as Record<string, unknown> | null | undefined
        const ovr = esc?.[`${s.key}Narrative`]
        if (typeof ovr === 'string') {
          nxt[s.key] = ovr
        } else {
          const o = ex[s.key]
          if (o && typeof o === 'object' && !Array.isArray(o)) {
            nxt[s.key] = Object.entries(o as Record<string, unknown>)
              .map(([k, v]) => `${k}: ${String(v)}`)
              .join('\n')
          }
        }
      }
      setSectionDrafts(nxt)
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) {
        setError('CAM is created when underwriting moves the case to CAM ready.')
        setCam(null)
        return
      }
      setError(e instanceof ApiError ? e.message : 'Failed to load CAM')
    } finally {
      setLoading(false)
    }
  }, [applicationId, app])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async CAM load on mount
    void load()
  }, [load])

  async function onSave() {
    setSaving(true)
    setError(null)
    try {
      const patch: Record<string, unknown> = {}
      for (const s of SECTION_ORDER) {
        const t = (sectionDrafts[s.key] ?? '').trim()
        if (t) {
          patch[`${s.key}Narrative`] = t
        }
      }
      const body: CamUpdateRequest = {
        observations,
        riskAssessment,
        mitigants,
        recommendedDecision: recommendedDecision || undefined,
        creditOfficerRemarks: officerRem || undefined,
        creditManagerRemarks: managerRem || undefined,
        recommendedAmount: recAmt ? Number.parseFloat(recAmt) : undefined,
        recommendedTenureMonths: recTen ? Number.parseInt(recTen, 10) : undefined,
        recommendedRate: recRate ? Number.parseFloat(recRate) : undefined,
        conditionsPrecedent: condPre
          ? condPre
              .split('\n')
              .map((x) => x.trim())
              .filter(Boolean)
          : undefined,
        conditionsSubsequent: condSub
          ? condSub
              .split('\n')
              .map((x) => x.trim())
              .filter(Boolean)
          : undefined,
        editableSectionsPatch: Object.keys(patch).length ? patch : undefined,
      }
      const c = await updateCam(applicationId, body)
      setCam(c)
      await onRefetch()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Save failed')
    } finally {
      setSaving(false)
    }
  }

  async function onMarkReviewed() {
    setActionBusy(true)
    setError(null)
    try {
      await markCamReviewedFlow(applicationId)
      await load()
      await onRefetch()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not approve CAM')
    } finally {
      setActionBusy(false)
    }
  }

  async function onSubmit() {
    setActionBusy(true)
    setError(null)
    try {
      setCam(await submitCam(applicationId))
      await onRefetch()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Submit failed')
    } finally {
      setActionBusy(false)
    }
  }

  async function onSendBack() {
    setActionBusy(true)
    setError(null)
    try {
      setCam(await sendBackCam(applicationId))
      await onRefetch()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Send back failed')
    } finally {
      setActionBusy(false)
    }
  }

  async function onRejectMem() {
    setActionBusy(true)
    setError(null)
    try {
      setCam(await rejectCamMemorandum(applicationId))
      await onRefetch()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Reject failed')
    } finally {
      setActionBusy(false)
    }
  }

  async function onDownloadPdf() {
    setPdfLoading(true)
    setError(null)
    try {
      const blob = await fetchCamPdfBlob(applicationId)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `CAM-${applicationId}.pdf`
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'PDF download failed')
    } finally {
      setPdfLoading(false)
    }
  }

  const camStatus = cam?.camStatus ?? 'DRAFT'
  const isLocked = camStatus === 'APPROVED'
  const canSubmit = !isLocked && (camStatus === 'DRAFT' || camStatus === 'SENT_BACK' || camStatus === 'REJECTED')
  const isSubmitted = camStatus === 'SUBMITTED'
  const camReady = app.status === 'CAM_READY' || app.status === 'CAM_REVIEWED' || app.status === 'SANCTION_PENDING' || app.status === 'APPROVED'
  const canManagerApproveToReviewed =
    (app.status === 'CAM_READY' || app.status === 'APPROVED') &&
    !isLocked &&
    (camStatus === 'SUBMITTED' || (camStatus === 'DRAFT' && app.status === 'CAM_READY'))

  if (loading && !cam) {
    return <p className="text-sm text-slate-600">Loading CAM…</p>
  }

  return (
    <div className="space-y-6">
      {cam && (
        <div className="bt-section-card bt-section-card--hero p-3 text-sm text-slate-800">
          <div className="flex flex-wrap gap-3 text-xs">
            <span>
              <span className="text-slate-500">CAM status:</span>{' '}
              <span className="font-semibold text-slate-900">{camStatus}</span>
            </span>
            <span>
              <span className="text-slate-500">Version:</span> {cam.camVersion ?? 1}
            </span>
            {cam.approvedAt ? (
              <span>
                <span className="text-slate-500">Approved at:</span> {cam.approvedAt}
              </span>
            ) : null}
            {cam.approvedByUserId ? (
              <span>
                <span className="text-slate-500">Approver (id):</span>{' '}
                <span className="font-mono text-[11px]">{cam.approvedByUserId}</span>
              </span>
            ) : null}
            <span>
              <span className="text-slate-500">Application status:</span> {app.status}
            </span>
          </div>
        </div>
      )}

      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={() => void onSave()}
          disabled={saving || isLocked}
          className="rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50"
        >
          {saving ? 'Saving…' : 'Save draft'}
        </button>
        {camReady && (
          <button
            type="button"
            onClick={() => void onDownloadPdf()}
            disabled={pdfLoading}
            className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-800 disabled:opacity-50"
          >
            {pdfLoading ? 'Preparing…' : 'Download CAM PDF'}
          </button>
        )}
        {camReady && canSubmit && (
          <button
            type="button"
            onClick={() => void onSubmit()}
            disabled={actionBusy}
            className="rounded-md border border-indigo-400 bg-indigo-50 px-3 py-1.5 text-sm font-medium text-indigo-950 disabled:opacity-50"
          >
            {actionBusy ? '…' : 'Submit for manager'}
          </button>
        )}
        {camReady && isSubmitted && (
          <>
            <button
              type="button"
              onClick={() => void onSendBack()}
              disabled={actionBusy}
              className="rounded-md border border-amber-500 bg-amber-50 px-3 py-1.5 text-sm font-medium text-amber-950 disabled:opacity-50"
            >
              Send back
            </button>
            <button
              type="button"
              onClick={() => void onRejectMem()}
              disabled={actionBusy}
              className="rounded-md border border-rose-500 bg-rose-50 px-3 py-1.5 text-sm font-medium text-rose-900 disabled:opacity-50"
            >
              Reject memorandum
            </button>
          </>
        )}
        {camReady && canManagerApproveToReviewed && (
          <button
            type="button"
            onClick={() => void onMarkReviewed()}
            disabled={actionBusy}
            className="bt-btn bt-btn-primary disabled:opacity-50"
          >
            {actionBusy ? '…' : isSubmitted ? 'Approve CAM' : 'Approve CAM to reviewed (fast path)'}
          </button>
        )}
      </div>

      {error && (
        <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-900">{error}</div>
      )}

      {cam && (
        <div className="space-y-4">
          <div className="bt-section-card bt-section-card--warning p-4">
            <h3 className="text-sm font-semibold text-amber-950">Credit officer recommendation (sanctioning basis)</h3>
            {prefilledFromApplication ? (
              <p className="mt-1 text-xs text-amber-900/80">
                Pre-filled from the {applicationPartyLabels(app.intakeSegment).partyLower} application
                request where available. Adjust before submitting for manager review.
              </p>
            ) : null}
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              <label className="block text-xs text-slate-600">
                Proposed amount (INR)
                <input
                  className="mt-1 w-full rounded border border-slate-200 p-2 text-sm"
                  value={recAmt}
                  onChange={(e) => setRecAmt(e.target.value)}
                  disabled={isLocked}
                />
              </label>
              <label className="block text-xs text-slate-600">
                Proposed tenure (months)
                <input
                  className="mt-1 w-full rounded border border-slate-200 p-2 text-sm"
                  value={recTen}
                  onChange={(e) => setRecTen(e.target.value.replace(/\D/g, ''))}
                  disabled={isLocked}
                />
              </label>
              <label className="block text-xs text-slate-600">
                Proposed rate (% p.a.)
                <input
                  className="mt-1 w-full rounded border border-slate-200 p-2 text-sm"
                  value={recRate}
                  onChange={(e) => setRecRate(e.target.value)}
                  disabled={isLocked}
                />
              </label>
              <label className="block text-xs text-slate-600">
                Suggested decision
                <select
                  className="mt-1 w-full rounded border border-slate-200 p-2 text-sm"
                  value={recommendedDecision}
                  onChange={(e) => setRecommendedDecision(e.target.value)}
                  disabled={isLocked}
                >
                  <option value="">(select)</option>
                  <option value="APPROVE">Approve</option>
                  <option value="REJECT">Reject</option>
                  <option value="MANUAL_REVIEW">Manual review</option>
                </select>
              </label>
            </div>
            <div className="mt-2 grid gap-2 sm:grid-cols-2">
              <label className="block text-xs text-slate-600">
                Conditions precedent (one per line)
                <textarea
                  className="mt-1 w-full rounded border border-slate-200 p-2 text-sm"
                  rows={2}
                  value={condPre}
                  onChange={(e) => setCondPre(e.target.value)}
                  disabled={isLocked}
                />
              </label>
              <label className="block text-xs text-slate-600">
                Conditions subsequent (one per line)
                <textarea
                  className="mt-1 w-full rounded border border-slate-200 p-2 text-sm"
                  rows={2}
                  value={condSub}
                  onChange={(e) => setCondSub(e.target.value)}
                  disabled={isLocked}
                />
              </label>
            </div>
            <div className="mt-2 grid gap-2 sm:grid-cols-2">
              <label className="block text-xs text-slate-600">
                Credit officer remarks
                <textarea
                  className="mt-1 w-full rounded border border-slate-200 p-2 text-sm"
                  rows={2}
                  value={officerRem}
                  onChange={(e) => setOfficerRem(e.target.value)}
                  disabled={isLocked}
                />
              </label>
              <label className="block text-xs text-slate-600">
                Credit manager remarks
                <textarea
                  className="mt-1 w-full rounded border border-slate-200 p-2 text-sm"
                  rows={2}
                  value={managerRem}
                  onChange={(e) => setManagerRem(e.target.value)}
                  disabled={isLocked}
                />
              </label>
            </div>
          </div>

          {sectionOrder.map((s) => {
            if (s.key === 'collateral' && !cam.sectionExtended?.[s.key]) {
              return null
            }
            return (
              <div key={s.key} className="bt-section-card bt-section-card--default p-3">
                <h4 className="bt-card-title">{s.label} — adjust narrative</h4>
                <p className="text-xs text-slate-500">Save draft updates what appears in the PDF for this block.</p>
                <textarea
                  className="mt-2 w-full rounded border border-slate-200 p-2 text-sm"
                  rows={3}
                  value={sectionDrafts[s.key] ?? ''}
                  onChange={(e) => setSectionDrafts((d) => ({ ...d, [s.key]: e.target.value }))}
                  disabled={isLocked}
                />
                <div className="mt-1 text-xs text-slate-500">
                  <span className="font-medium">Source fields (read-only):</span>{' '}
                  {Object.keys(asRecord(cam.sectionExtended?.[s.key])).length
                    ? 'See structured map from engine below on screen (API sectionExtended).'
                    : '—'}
                </div>
              </div>
            )
          })}

          <div className="bt-section-card bt-section-card--default p-3 text-xs text-slate-600 bg-slate-50/60">
            <p className="font-semibold text-slate-800">Structured snapshot (read-only, from engine)</p>
            <p className="mt-1">
              Extended sections: data is not shown as raw JSON in the product UI here — it is used for the PDF and API
              consumers. Use the text areas above to override narrative text in the generated PDF.
            </p>
          </div>

          <div className="bt-section-card bt-section-card--warning p-4">
            <h3 className="text-sm font-semibold text-amber-950">Observations, risk, mitigants (legacy / merged)</h3>
            <div className="mt-2 space-y-2">
              <label className="block text-xs text-slate-600">
                Observations
                <textarea
                  className="mt-1 w-full rounded-md border border-slate-200 p-2 text-sm"
                  rows={2}
                  value={observations}
                  onChange={(e) => setObservations(e.target.value)}
                  disabled={isLocked}
                />
              </label>
              <label className="block text-xs text-slate-600">
                Risk assessment
                <textarea
                  className="mt-1 w-full rounded-md border border-slate-200 p-2 text-sm"
                  rows={2}
                  value={riskAssessment}
                  onChange={(e) => setRiskAssessment(e.target.value)}
                  disabled={isLocked}
                />
              </label>
              <label className="block text-xs text-slate-600">
                Mitigants
                <textarea
                  className="mt-1 w-full rounded-md border border-slate-200 p-2 text-sm"
                  rows={2}
                  value={mitigants}
                  onChange={(e) => setMitigants(e.target.value)}
                  disabled={isLocked}
                />
              </label>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
