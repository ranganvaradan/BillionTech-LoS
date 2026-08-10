import { useCallback, useEffect, useMemo, useState } from 'react'
import { useAuth } from '@/auth/useAuth'
import { isCamCheckerRole, isCamEditorRole, isCamMakerRole } from '@/auth/types'
import {
  calculateCollateralLtv,
  listCollateralValuations,
  type CollateralLtvResult,
  type CollateralValuation,
} from '@/api/collateral'
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
import { formatCamSectionExtended } from '@/lib/cam/camSectionFormat'
import { formatCamStatusLabel } from '@/lib/credit/assessmentPresentation'
import { camNextActionHint } from '@/lib/decision/decisionPresentation'
import { applicationPartyLabels } from '@/lib/applicationPartyLabels'
import { formatMoneyWithScale as formatMoney } from '@/lib/format'
import { AmountInputHint } from '@/components/ui/AmountInputHint'
import { requiresCollateral } from '@/lib/intake/securedProducts'
import type { ApplicationResponse } from '@/types/application'
import type { CamResponse, CamUpdateRequest } from '@/types/cam'
import { tenureMagnitudeLabel } from '@/catalog/lmsTenureUnits'

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

function ltvDisplayTone(ratio: number): { className: string; label: string } {
  if (ratio <= 60) return { className: 'text-emerald-800', label: 'Conservative LTV' }
  if (ratio <= 80) return { className: 'text-amber-900', label: 'Within policy ceiling' }
  return { className: 'text-rose-800', label: 'Above policy ceiling' }
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
  const [interestType, setInterestType] = useState('')
  const [condPre, setCondPre] = useState('')
  const [condSub, setCondSub] = useState('')
  const [officerRem, setOfficerRem] = useState('')
  const [managerRem, setManagerRem] = useState('')
  const [sendBackReason, setSendBackReason] = useState('')
  const [prefilledFromApplication, setPrefilledFromApplication] = useState(false)
  const [ltv, setLtv] = useState<CollateralLtvResult | null>(null)
  const [valuations, setValuations] = useState<CollateralValuation[]>([])
  const [ltvLoading, setLtvLoading] = useState(false)
  const { user } = useAuth()
  const isMaker = user ? isCamMakerRole(user.role) : false
  const isEditor = user ? isCamEditorRole(user.role) : false
  const isChecker = user ? isCamCheckerRole(user.role) : false

  const loanAmountForLtv = useMemo(() => {
    const fromRec = recAmt.trim() ? Number.parseFloat(recAmt) : NaN
    if (Number.isFinite(fromRec) && fromRec > 0) return fromRec
    return app.requestedAmount != null && app.requestedAmount > 0 ? app.requestedAmount : null
  }, [recAmt, app.requestedAmount])

  const showCollateralLtv = requiresCollateral(app.loanProduct) || valuations.length > 0

  const loadCollateralLtv = useCallback(async () => {
    if (!showCollateralLtv && loanAmountForLtv == null) {
      setLtv(null)
      setValuations([])
      return
    }
    setLtvLoading(true)
    try {
      const rows = await listCollateralValuations(applicationId)
      setValuations(rows)
      if (loanAmountForLtv != null) {
        const result = await calculateCollateralLtv(applicationId, loanAmountForLtv)
        setLtv(result)
      } else {
        setLtv(null)
      }
    } catch {
      setLtv(null)
      setValuations([])
    } finally {
      setLtvLoading(false)
    }
  }, [applicationId, loanAmountForLtv, showCollateralLtv])

  useEffect(() => {
    void loadCollateralLtv()
  }, [loadCollateralLtv])

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
      setInterestType(c.interestType === 'UPFRONT' || c.interestType === 'REDUCING' ? c.interestType : '')
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
        const sectionData = ex[s.key]
        if (typeof ovr === 'string' && ovr.trim() && !ovr.includes('[object Object]')) {
          nxt[s.key] = ovr
        } else if (sectionData && typeof sectionData === 'object' && !Array.isArray(sectionData)) {
          nxt[s.key] = formatCamSectionExtended(sectionData as Record<string, unknown>)
        } else if (typeof ovr === 'string' && ovr.trim()) {
          nxt[s.key] = formatCamSectionExtended(
            sectionData && typeof sectionData === 'object' && !Array.isArray(sectionData)
              ? (sectionData as Record<string, unknown>)
              : {},
          )
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
      const c = await updateCam(applicationId, buildUpdateBody())
      setCam(c)
      setPrefilledFromApplication(false)
      await onRefetch()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Save failed')
    } finally {
      setSaving(false)
    }
  }

  function buildUpdateBody(): CamUpdateRequest {
    const patch: Record<string, unknown> = {}
    for (const s of SECTION_ORDER) {
      const t = (sectionDrafts[s.key] ?? '').trim()
      if (t) {
        patch[`${s.key}Narrative`] = t
      }
    }
    return {
      observations,
      riskAssessment,
      mitigants,
      recommendedDecision: recommendedDecision || undefined,
      creditOfficerRemarks: officerRem || undefined,
      creditManagerRemarks: managerRem || undefined,
      recommendedAmount: recAmt ? Number.parseFloat(recAmt) : undefined,
      recommendedTenureMonths: recTen ? Number.parseInt(recTen, 10) : undefined,
      recommendedRate: recRate ? Number.parseFloat(recRate) : undefined,
      interestType: interestType || undefined,
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
  }

  /** Client-side gate before save+submit — backend also enforces these. */
  function validateSanctionBasisForSubmit(): string | null {
    const amount = Number.parseFloat(recAmt)
    const tenure = Number.parseInt(recTen, 10)
    const rate = Number.parseFloat(recRate)
    const missing: string[] = []
    if (!recAmt.trim() || !Number.isFinite(amount) || amount <= 0) {
      missing.push('Proposed amount (INR)')
    }
    if (!recTen.trim() || !Number.isFinite(tenure) || tenure <= 0) {
      missing.push(`Proposed ${tenureMagnitudeLabel(app.lmsTenureUnit).toLowerCase()}`)
    }
    if (!recRate.trim() || !Number.isFinite(rate) || rate < 0) {
      missing.push('Proposed rate (% p.a.)')
    }
    if (missing.length === 0) return null
    return `Fill mandatory fields before submitting: ${missing.join(', ')}`
  }

  async function onMarkReviewed() {
    setActionBusy(true)
    setError(null)
    try {
      const validationError = validateSanctionBasisForSubmit()
      if (validationError) {
        setError(
          validationError.replace(
            'before submitting',
            'before approving the CAM',
          ),
        )
        return
      }
      // Persist form fields first so fast-path approval does not skip unsaved rate/tenure.
      if (fieldsEditable) {
        const saved = await updateCam(applicationId, buildUpdateBody())
        setCam(saved)
        setPrefilledFromApplication(false)
      }
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
      const validationError = validateSanctionBasisForSubmit()
      if (validationError) {
        setError(validationError)
        return
      }
      // Persist form fields first so submit does not drop unsaved interest/rate/tenure edits.
      const saved = await updateCam(applicationId, buildUpdateBody())
      setCam(saved)
      setPrefilledFromApplication(false)
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
      setCam(await sendBackCam(applicationId, sendBackReason.trim() || undefined))
      setSendBackReason('')
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
  const fieldsEditable = isEditor && !isLocked
  const canSubmit = isMaker && !isLocked && (camStatus === 'DRAFT' || camStatus === 'SENT_BACK' || camStatus === 'REJECTED')
  const isSubmitted = camStatus === 'SUBMITTED'
  const canReturnForRevision = isChecker && camStatus === 'APPROVED'
  const missingSanctionRate = !recRate.trim()
  const camWorkflowOpen =
    !!cam &&
    (app.status === 'CAM_READY' ||
      app.status === 'CAM_SENT_BACK' ||
      app.status === 'CAM_REVIEWED' ||
      app.status === 'SANCTION_PENDING' ||
      app.status === 'APPROVED' ||
      camStatus === 'SENT_BACK' ||
      camStatus === 'SUBMITTED' ||
      camStatus === 'REJECTED' ||
      camStatus === 'APPROVED')
  const canManagerApproveToReviewed =
    isChecker &&
    !isLocked &&
    (camStatus === 'SUBMITTED' ||
      (camStatus === 'DRAFT' && (app.status === 'CAM_READY' || app.status === 'APPROVED')))

  if (loading && !cam) {
    return <p className="text-sm text-slate-600">Loading CAM…</p>
  }

  const camStatusLabel = formatCamStatusLabel(camStatus)
  const nextCamHint = camNextActionHint({ camStatus, isMaker, isChecker })

  return (
    <div className="space-y-6" data-testid="cam-section">
      {cam && (
        <div className="bt-section-card bt-section-card--hero p-3 text-sm text-slate-800">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="flex flex-wrap gap-3 text-xs">
              <span>
                <span className="text-slate-500">Current CAM status:</span>{' '}
                <span className="font-semibold text-slate-900">{camStatusLabel}</span>
              </span>
              <span>
                <span className="text-slate-500">Version:</span> {cam.camVersion ?? 1}
              </span>
              {cam.approvedAt ? (
                <span>
                  <span className="text-slate-500">Approved at:</span> {cam.approvedAt}
                </span>
              ) : null}
            </div>
            {nextCamHint ? (
              <p className="text-xs font-medium text-slate-700">
                <span className="text-slate-500">Next CAM action:</span> {nextCamHint}
              </p>
            ) : null}
          </div>
        </div>
      )}
      {!cam ? (
        <p className="text-sm text-slate-600" data-testid="cam-empty-state">
          Credit appraisal has not been started.
        </p>
      ) : null}

      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={() => void onSave()}
          disabled={saving || !fieldsEditable}
          className="rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50"
        >
          {saving ? 'Saving…' : 'Save draft'}
        </button>
        {camWorkflowOpen && (
          <button
            type="button"
            onClick={() => void onDownloadPdf()}
            disabled={pdfLoading}
            className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-800 disabled:opacity-50"
          >
            {pdfLoading ? 'Preparing…' : 'Download CAM PDF'}
          </button>
        )}
        {camWorkflowOpen && canSubmit && (
          <button
            type="button"
            onClick={() => void onSubmit()}
            disabled={actionBusy}
            className="rounded-md border border-indigo-400 bg-indigo-50 px-3 py-1.5 text-sm font-medium text-indigo-950 disabled:opacity-50"
          >
            {actionBusy ? '…' : 'Submit for manager'}
          </button>
        )}
        {camWorkflowOpen && isSubmitted && isChecker && (
          <>
            <button
              type="button"
              onClick={() => void onSendBack()}
              disabled={actionBusy}
              className="rounded-md border border-amber-500 bg-amber-50 px-3 py-1.5 text-sm font-medium text-amber-950 disabled:opacity-50"
              data-testid="cam-send-back"
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
        {camWorkflowOpen && canReturnForRevision && (
          <button
            type="button"
            onClick={() => void onSendBack()}
            disabled={actionBusy}
            className="rounded-md border border-amber-500 bg-amber-50 px-3 py-1.5 text-sm font-medium text-amber-950 disabled:opacity-50"
            data-testid="cam-return-for-revision"
          >
            {actionBusy ? '…' : 'Return for terms revision'}
          </button>
        )}
        {camWorkflowOpen && canManagerApproveToReviewed && (
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

      {camWorkflowOpen && ((isSubmitted && isChecker) || canReturnForRevision) ? (
        <div className="rounded-md border border-amber-300 bg-amber-50/80 p-3">
          <label className="block text-xs font-medium text-amber-950">
            {canReturnForRevision ? 'Revision message' : 'Send-back message'}{' '}
            <span className="font-normal text-amber-900/80">(optional)</span>
            <textarea
              className="mt-1.5 w-full rounded border border-amber-200 bg-white p-2 text-sm text-slate-800"
              rows={3}
              value={sendBackReason}
              onChange={(e) => setSendBackReason(e.target.value)}
              placeholder={
                canReturnForRevision
                  ? 'e.g. Enter proposed interest rate and interest type before re-approval…'
                  : 'Explain what the credit officer should revise…'
              }
            />
          </label>
        </div>
      ) : null}

      {error && (
        <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-900">{error}</div>
      )}

      {cam?.creditManagerRemarks?.trim() ? (
        <div className="rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-800">
          <span className="font-medium text-slate-900">Credit manager remarks:</span>{' '}
          {cam.creditManagerRemarks}
        </div>
      ) : null}

      {cam && (
        <div className="space-y-4">
          <div className="bt-section-card bt-section-card--warning p-4">
            <h3 className="text-sm font-semibold text-amber-950">Credit officer recommendation (sanctioning basis)</h3>
            {isLocked ? (
              <p className="mt-1 text-xs text-amber-900/90" data-testid="cam-locked-hint">
                CAM is approved — recommendation fields are locked to preserve maker-checker integrity.
                {missingSanctionRate
                  ? ' Proposed rate was not captured before approval. A credit manager must use “Return for terms revision” so the credit officer can enter rate and other terms, then resubmit for approval.'
                  : ' To change terms, a credit manager must return the CAM for revision.'}
              </p>
            ) : null}
            {prefilledFromApplication && !isLocked ? (
              <p className="mt-1 text-xs text-amber-900/80">
                Pre-filled from the {applicationPartyLabels(app.intakeSegment).partyLower} application
                request where available. Adjust before submitting for manager review.
              </p>
            ) : null}
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              <label className="block text-xs text-slate-600">
                Proposed amount (INR) <span className="text-rose-600">*</span>
                <input
                  className="mt-1 w-full rounded border border-slate-200 p-2 text-sm"
                  value={recAmt}
                  onChange={(e) => setRecAmt(e.target.value)}
                  disabled={!fieldsEditable}
                  required
                />
                <AmountInputHint value={recAmt} />
              </label>
              <label className="block text-xs text-slate-600">
                Proposed {tenureMagnitudeLabel(app.lmsTenureUnit).toLowerCase()}{' '}
                <span className="text-rose-600">*</span>
                <input
                  className="mt-1 w-full rounded border border-slate-200 p-2 text-sm"
                  value={recTen}
                  onChange={(e) => setRecTen(e.target.value.replace(/\D/g, ''))}
                  disabled={!fieldsEditable}
                  required
                />
              </label>
              <label className="block text-xs text-slate-600">
                Proposed rate (% p.a.) <span className="text-rose-600">*</span>
                <input
                  className="mt-1 w-full rounded border border-slate-200 p-2 text-sm"
                  value={recRate}
                  onChange={(e) => setRecRate(e.target.value)}
                  disabled={!fieldsEditable}
                  required
                />
              </label>
              <label className="block text-xs text-slate-600">
                Interest type
                <select
                  className="mt-1 w-full rounded border border-slate-200 p-2 text-sm"
                  value={interestType}
                  onChange={(e) => setInterestType(e.target.value)}
                  disabled={!fieldsEditable}
                >
                  <option value="">(select)</option>
                  <option value="UPFRONT">Upfront</option>
                  <option value="REDUCING">Reducing</option>
                </select>
              </label>
              <label className="block text-xs text-slate-600">
                Suggested decision
                <select
                  className="mt-1 w-full rounded border border-slate-200 p-2 text-sm"
                  value={recommendedDecision}
                  onChange={(e) => setRecommendedDecision(e.target.value)}
                  disabled={!fieldsEditable}
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
                  disabled={!fieldsEditable}
                />
              </label>
              <label className="block text-xs text-slate-600">
                Conditions subsequent (one per line)
                <textarea
                  className="mt-1 w-full rounded border border-slate-200 p-2 text-sm"
                  rows={2}
                  value={condSub}
                  onChange={(e) => setCondSub(e.target.value)}
                  disabled={!fieldsEditable}
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
                  disabled={!fieldsEditable}
                />
              </label>
              <label className="block text-xs text-slate-600">
                Credit manager remarks
                <textarea
                  className="mt-1 w-full rounded border border-slate-200 p-2 text-sm"
                  rows={2}
                  value={managerRem}
                  onChange={(e) => setManagerRem(e.target.value)}
                  disabled={!fieldsEditable}
                />
              </label>
            </div>
          </div>

          {showCollateralLtv ? (
            <div className="bt-section-card bt-section-card--success p-4 text-sm text-slate-800">
              <h3 className="text-sm font-semibold text-emerald-950">Collateral &amp; LTV</h3>
              <p className="mt-1 text-xs text-slate-600">
                Based on completed collateral valuations against the proposed / requested loan amount (
                {loanAmountForLtv != null ? formatMoney(loanAmountForLtv) : '—'}).
              </p>
              {ltvLoading ? (
                <p className="mt-3 text-xs text-slate-500">Loading collateral and LTV…</p>
              ) : ltv ? (
                <dl className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-4 text-xs">
                  <div>
                    <dt className="text-slate-500">Total collateral value</dt>
                    <dd className="font-medium tabular-nums text-slate-900">
                      {formatMoney(ltv.totalCollateralValue)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">LTV ratio</dt>
                    <dd className={`font-semibold tabular-nums ${ltvDisplayTone(Number(ltv.ltvRatio)).className}`}>
                      {ltv.ltvRatio}% · {ltvDisplayTone(Number(ltv.ltvRatio)).label}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">Policy acceptable (≤ {ltv.maxAllowedLtv}%)</dt>
                    <dd className="font-medium">
                      {ltv.ltvAcceptable ? (
                        <span className="text-emerald-800">Yes</span>
                      ) : (
                        <span className="text-rose-800">No</span>
                      )}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">Valuations on file</dt>
                    <dd className="font-medium text-slate-900">{ltv.valuationCount}</dd>
                  </div>
                </dl>
              ) : (
                <p className="mt-3 text-xs text-amber-900">
                  No LTV calculation yet — add completed collateral valuations on the Collateral tab.
                </p>
              )}
              {valuations.length > 0 ? (
                <div className="mt-4 overflow-x-auto">
                  <table className="bt-table min-w-full text-xs">
                    <thead>
                      <tr>
                        <th>Type</th>
                        <th>Description</th>
                        <th>Status</th>
                        <th>Accepted value</th>
                      </tr>
                    </thead>
                    <tbody>
                      {valuations.map((v) => (
                        <tr key={v.id}>
                          <td className="text-slate-900">{v.collateralType.replaceAll('_', ' ')}</td>
                          <td className="max-w-[14rem] truncate text-slate-700">{v.description ?? '—'}</td>
                          <td className="text-slate-700">{v.status.replaceAll('_', ' ')}</td>
                          <td className="tabular-nums text-slate-900">
                            {v.status === 'COMPLETED' ? formatMoney(v.valuationAmount) : '—'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : null}
            </div>
          ) : null}

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
                  disabled={!fieldsEditable}
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
                  disabled={!fieldsEditable}
                />
              </label>
              <label className="block text-xs text-slate-600">
                Risk assessment
                <textarea
                  className="mt-1 w-full rounded-md border border-slate-200 p-2 text-sm"
                  rows={2}
                  value={riskAssessment}
                  onChange={(e) => setRiskAssessment(e.target.value)}
                  disabled={!fieldsEditable}
                />
              </label>
              <label className="block text-xs text-slate-600">
                Mitigants
                <textarea
                  className="mt-1 w-full rounded-md border border-slate-200 p-2 text-sm"
                  rows={2}
                  value={mitigants}
                  onChange={(e) => setMitigants(e.target.value)}
                  disabled={!fieldsEditable}
                />
              </label>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
