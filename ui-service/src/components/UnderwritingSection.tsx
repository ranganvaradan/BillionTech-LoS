import { BORROWER_INTAKE_KEY } from '@/lib/intake/collateralIntakePayload'
import { requiresCollateral } from '@/lib/intake/securedProducts'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { listAaConsents, type AaFetchedData } from '@/api/accountAggregator'
import { listDocuments, uploadDocument } from '@/api/documents'
import { getKycOutcome } from '@/api/kyc'
import {
  approveManualUnderwritingFlow,
  pullBureauFlow,
  rejectManualUnderwritingFlow,
  underwriteApplicationFlow,
} from '@/api/flow'
import { openAiLosReview, saveManualBureau, getAdminGstAnalysisStatus } from '@/api/applications'
import { listScorecards } from '@/api/scorecards'
import { listUnderwritingRules } from '@/api/underwritingRules'
import { messageForKycAction } from '@/api/kycErrorMessage'
import { messageForUnderwritingAction } from '@/api/underwritingErrorMessage'
import { ErrorState } from '@/components/ErrorState'
import { ManualCreditInputsSection } from '@/components/ManualCreditInputsSection'
import { ProcessOverrideCard } from '@/components/ProcessOverrideCard'
import { AppSectionCard } from '@/components/ui/AppSectionCard'
import { CollapsibleSection } from '@/components/ui/CollapsibleSection'
import { formatMoneyWithScale, formatSummaryValueDisplay } from '@/lib/format'
import { manualCreditHashForScorecardParameter } from '@/lib/manualCreditParameterAnchors'
import { ScorecardSummaryPanel } from '@/components/credit/ScorecardSummaryPanel'
import { UnderwritingRulesRanPanel } from '@/components/credit/UnderwritingRulesRanPanel'
import { ScorecardPreRunInputsPanel } from '@/components/credit/ScorecardPreRunInputsPanel'
import { buildCreditSummary } from '@/lib/credit/creditSummaryBuilder'
import {
  buildAssessmentConcerns,
  concernSeverityLabel,
  concernSeverityTone,
  formatAssessmentOutcome,
  hasAssessmentResult,
  whyRowsFromParameterResults,
  whyRowsFromReasons,
} from '@/lib/credit/assessmentPresentation'
import { matchScorecardForApplication, scorecardParameterDefsFromJson, scorecardRowsFromJson } from '@/lib/credit/matchScorecard'
import {
  hardRuleRowsAsScorecardRows,
  matchUnderwritingRulesForApplication,
} from '@/lib/credit/matchUnderwritingRules'
import {
  allScorecardInputsReady,
  scorecardManualInputRequirements,
  unionScorecardInputRequirements,
} from '@/lib/credit/scorecardInputRequirements'
import { getVisibleUnderwritingFields } from '@/lib/credit/underwritingFieldVisibility'
import { applicationPartyLabels } from '@/lib/applicationPartyLabels'
import type { ApplicationResponse } from '@/types/application'
import { isInvoiceDiscountingBorrowerApp } from '@/lib/invoiceDiscountingFlow'
import { InvoiceDiscountingVintagePanel } from '@/components/plp/InvoiceDiscountingVintagePanel'

function computeAaFoir(data: AaFetchedData): number | null {
  if (!data.avgMonthlyInflow || data.avgMonthlyInflow <= 0) return null
  return (data.regularEmiOutflows / data.avgMonthlyInflow) * 100
}

function savedScorecardMetricsFromManual(manual: Record<string, unknown> | undefined): Record<string, string> {
  const raw = manual?.scorecardMetrics
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return {}
  const out: Record<string, string> = {}
  for (const [key, cell] of Object.entries(raw as Record<string, unknown>)) {
    const value =
      cell && typeof cell === 'object' && !Array.isArray(cell)
        ? (cell as { value?: unknown }).value
        : cell
    if (value != null && String(value).trim()) {
      out[key] = String(value).trim()
    }
  }
  return out
}

export function UnderwritingSection({
  applicationId,
  app,
  onRefetch,
  allowRunUnderwriting = true,
}: {
  applicationId: string
  app: ApplicationResponse
  onRefetch: () => void
  /** When false (e.g. Relationship Manager), hide run-underwriting actions. */
  allowRunUnderwriting?: boolean
}) {
  const [kycOutcome, setKycOutcome] = useState<Record<string, unknown> | null>(null)
  const [kycError, setKycError] = useState<string | null>(null)
  const [kycReady, setKycReady] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [bureauLoading, setBureauLoading] = useState(false)
  const [underwriteLoading, setUnderwriteLoading] = useState(false)
  const [manualScore, setManualScore] = useState('')
  const [manualRemarks, setManualRemarks] = useState('')
  const [bureauDocUploading, setBureauDocUploading] = useState(false)
  const [bureauFileMessage, setBureauFileMessage] = useState<string | null>(null)
  const [manualLoading, setManualLoading] = useState(false)
  const [manualResolveLoading, setManualResolveLoading] = useState(false)
  const [bankStatementOnFile, setBankStatementOnFile] = useState(false)
  const [showManualInputs, setShowManualInputs] = useState(false)
  const [scorecardFocusParameter, setScorecardFocusParameter] = useState<string | null>(null)
  const [aiLosLoading, setAiLosLoading] = useState(false)
  const [aiLosStatus, setAiLosStatus] = useState<string | null>(null)
  const [aaLoading, setAaLoading] = useState(true)
  const [aaFetchedData, setAaFetchedData] = useState<AaFetchedData | null>(null)
  const [aaVerified, setAaVerified] = useState(false)
  const [gstOnWorkflow, setGstOnWorkflow] = useState(false)
  const [gstReportComplete, setGstReportComplete] = useState(true)
  const [scorecards, setScorecards] = useState<Awaited<ReturnType<typeof listScorecards>>>([])
  const [underwritingRules, setUnderwritingRules] = useState<
    Awaited<ReturnType<typeof listUnderwritingRules>>
  >([])

  const loadAaSummary = useCallback(async () => {
    setAaLoading(true)
    try {
      const consents = await listAaConsents(applicationId)
      const fetched = consents.find((c) => c.status === 'DATA_FETCHED' && c.fetchedDataSummary)
      setAaVerified(Boolean(fetched))
      setAaFetchedData(fetched?.fetchedDataSummary ?? null)
    } catch {
      setAaVerified(false)
      setAaFetchedData(null)
    } finally {
      setAaLoading(false)
    }
  }, [applicationId])

  useEffect(() => {
    void loadAaSummary()
  }, [loadAaSummary, app.updatedAt])

  useEffect(() => {
    let cancelled = false
    void getAdminGstAnalysisStatus(applicationId)
      .then((st) => {
        if (cancelled) return
        const onWf = Boolean(st.onWorkflow)
        setGstOnWorkflow(onWf)
        setGstReportComplete(!onWf || Boolean(st.reportSuccess) || String(st.status).toUpperCase() === 'OVERRIDDEN')
      })
      .catch(() => {
        if (!cancelled) {
          setGstOnWorkflow(false)
          setGstReportComplete(true)
        }
      })
    return () => {
      cancelled = true
    }
  }, [applicationId, app.updatedAt])

  useEffect(() => {
    let cancelled = false
    void listScorecards()
      .then((rows) => {
        if (!cancelled) setScorecards(rows)
      })
      .catch(() => {
        if (!cancelled) setScorecards([])
      })
    void listUnderwritingRules()
      .then((rows) => {
        if (!cancelled) setUnderwritingRules(rows)
      })
      .catch(() => {
        if (!cancelled) setUnderwritingRules([])
      })
    return () => {
      cancelled = true
    }
  }, [])

  const aaFoir = useMemo(
    () => (aaFetchedData ? computeAaFoir(aaFetchedData) : null),
    [aaFetchedData],
  )

  const loadOutcome = useCallback(async (opts?: { silent?: boolean }) => {
    if (!opts?.silent) {
      setKycError(null)
      setKycReady(false)
    }
    try {
      const o = await getKycOutcome(applicationId)
      setKycOutcome(o)
      if (opts?.silent) {
        setKycError(null)
      }
    } catch (e) {
      setKycOutcome(null)
      setKycError(e instanceof Error ? e.message : 'Could not load KYC outcome')
    } finally {
      setKycReady(true)
    }
  }, [applicationId])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- loadOutcome is async; setState after await
    void loadOutcome()
  }, [loadOutcome, app.status])

  useEffect(() => {
    let cancelled = false
    void listDocuments(applicationId)
      .then((docs) => {
        if (cancelled) return
        setBankStatementOnFile(
          docs.some((d) => String(d.documentType ?? '').toUpperCase() === 'BANK_STATEMENT'),
        )
      })
      .catch(() => {
        if (!cancelled) setBankStatementOnFile(false)
      })
    return () => {
      cancelled = true
    }
  }, [applicationId, app.updatedAt])

  const creditControlManual = (
    app.creditControlView as { creditControl?: { manual?: Record<string, unknown> } } | undefined
  )?.creditControl?.manual

  const scorecardMapForMatch = useMemo(() => {
    const effective = (
      app.creditControlView as { effective?: { scorecard?: Record<string, string> } } | undefined
    )?.effective?.scorecard
    const savedCustom = savedScorecardMetricsFromManual(creditControlManual)
    return { ...(effective ?? {}), ...savedCustom }
  }, [app.creditControlView, creditControlManual])

  const matchedScorecard = useMemo(
    () =>
      matchScorecardForApplication(scorecards, {
        borrowerType: app.borrowerType,
        loanProduct: app.loanProduct,
        requestedAmount: app.requestedAmount,
        personalInfo: app.personalInfo as Record<string, unknown> | null,
      }),
    [scorecards, app.borrowerType, app.loanProduct, app.requestedAmount, app.personalInfo],
  )

  const matchedUnderwritingRules = useMemo(
    () =>
      matchUnderwritingRulesForApplication(underwritingRules, {
        borrowerType: app.borrowerType,
        loanProduct: app.loanProduct,
        requestedAmount: app.requestedAmount,
        personalInfo: app.personalInfo as Record<string, unknown> | null,
      }),
    [underwritingRules, app.borrowerType, app.loanProduct, app.requestedAmount, app.personalInfo],
  )

  const scorecardInputRequirements = useMemo(() => {
    const fromScorecard = scorecardManualInputRequirements(
      scorecardRowsFromJson(matchedScorecard),
      scorecardMapForMatch,
      scorecardParameterDefsFromJson(matchedScorecard),
    )
    const fromRules = scorecardManualInputRequirements(
      hardRuleRowsAsScorecardRows(matchedUnderwritingRules),
      scorecardMapForMatch,
    )
    return unionScorecardInputRequirements(fromScorecard, fromRules)
  }, [matchedScorecard, matchedUnderwritingRules, scorecardMapForMatch])

  const outcomeStr = kycOutcome ? String((kycOutcome as { outcome?: unknown }).outcome ?? '') : ''
  const kycPass = outcomeStr.toUpperCase() === 'PASS'
  const inKyc = app.status === 'KYC_IN_PROGRESS'
  const effectiveBureau =
    app.manualBureauScore != null && app.manualBureauScore > 0
      ? app.manualBureauScore
      : app.bureauScore != null && app.bureauScore > 0
        ? app.bureauScore
        : null
  const hasBureau = effectiveBureau != null && effectiveBureau > 0
  const pendingManualReview = app.status === 'UNDERWRITING' && app.creditDecision === 'MANUAL_REVIEW'
  const decisionDone =
    Boolean(app.creditDecision) &&
    !(app.status === 'UNDERWRITING' && app.creditDecision === 'MANUAL_REVIEW')
  const canRunUnderwriting =
    allowRunUnderwriting && kycPass && hasBureau && !decisionDone && (!gstOnWorkflow || gstReportComplete)
  const gstBlocksUnderwriting = gstOnWorkflow && !gstReportComplete
  const manualOverridesRaw = (app.financialInfo as Record<string, unknown> | null)?.manualOverrides
  const underwritingOverrides = Array.isArray(manualOverridesRaw)
    ? (manualOverridesRaw as Array<Record<string, unknown>>).filter(
        (row) => String(row.processCode ?? '').toUpperCase() === 'UNDERWRITING',
      )
    : []
  const hasUnderwritingOverride =
    String((app.financialInfo as Record<string, unknown> | null)?.manualOverrideFlag ?? '').toUpperCase() ===
      'MANUALLY_OVERRIDDEN' && underwritingOverrides.length > 0

  useEffect(() => {
    if (!(inKyc && kycPass && !hasBureau)) {
      return
    }

    let stopped = false
    let attempts = 0
    const maxAttempts = 20
    const intervalMs = 3000

    const refreshForAutoBureau = () => {
      if (stopped) return
      attempts += 1
      onRefetch()
      if (attempts >= maxAttempts) {
        stopped = true
        window.clearInterval(intervalId)
      }
    }

    const intervalId = window.setInterval(refreshForAutoBureau, intervalMs)
    refreshForAutoBureau()

    return () => {
      stopped = true
      window.clearInterval(intervalId)
    }
  }, [hasBureau, inKyc, kycPass, loadOutcome, onRefetch])

  const uwMeta = app.financialInfo?.underwritingMeta as
    | {
        ruleSetName?: string
        ruleSetId?: string
        source?: string
        recommendation?: string
        reasons?: unknown
      }
    | undefined

  async function onPullBureau() {
    setActionError(null)
    setBureauLoading(true)
    try {
      await pullBureauFlow(applicationId)
      onRefetch()
      void loadOutcome()
    } catch (e) {
      setActionError(messageForKycAction(e))
    } finally {
      setBureauLoading(false)
    }
  }

  async function onStartUnderwriting() {
    setActionError(null)
    if (!allScorecardInputsReady(scorecardInputRequirements)) {
      const firstMissing = scorecardInputRequirements.find((r) => !r.ready)
      setActionError(
        `Complete required scorecard inputs before underwriting${firstMissing ? ` (e.g. ${firstMissing.label})` : ''}.`,
      )
      openScorecardInput(firstMissing?.parameter)
      return
    }
    setUnderwriteLoading(true)
    try {
      await underwriteApplicationFlow(applicationId)
      onRefetch()
      void loadOutcome()
    } catch (e) {
      setActionError(messageForUnderwritingAction(e))
    } finally {
      setUnderwriteLoading(false)
    }
  }

  function openScorecardInput(parameter?: string) {
    setScorecardFocusParameter(parameter ?? null)
    window.requestAnimationFrame(() => {
      if (parameter) {
        const req = scorecardInputRequirements.find((r) => r.parameter === parameter)
        const id = req ? `scorecard-input-${req.manualKey}` : 'scorecard-pre-run-inputs'
        document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
      } else {
        document.getElementById('scorecard-pre-run-inputs')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
      }
    })
  }

  function openManualInput(parameter?: string) {
    if (parameter && scorecardInputRequirements.some((r) => r.parameter === parameter && r.source === 'OTHER')) {
      openScorecardInput(parameter)
      return
    }
    setShowManualInputs(true)
    const hash = manualCreditHashForScorecardParameter(parameter)
    window.requestAnimationFrame(() => {
      document.querySelector(hash)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
  }

  async function onApproveManual() {
    setActionError(null)
    setManualResolveLoading(true)
    try {
      await approveManualUnderwritingFlow(applicationId)
      onRefetch()
      void loadOutcome()
    } catch (e) {
      setActionError(messageForKycAction(e))
    } finally {
      setManualResolveLoading(false)
    }
  }

  async function onRejectManual() {
    setActionError(null)
    setManualResolveLoading(true)
    try {
      await rejectManualUnderwritingFlow(applicationId)
      onRefetch()
      void loadOutcome()
    } catch (e) {
      setActionError(messageForKycAction(e))
    } finally {
      setManualResolveLoading(false)
    }
  }

  async function onSaveManualBureau() {
    setActionError(null)
    const n = Number.parseInt(manualScore, 10)
    if (Number.isNaN(n) || n <= 0) {
      setActionError('Enter a positive whole number for the bureau score.')
      return
    }
    setManualLoading(true)
    try {
      await saveManualBureau(applicationId, {
        manualBureauScore: n,
        manualBureauRemarks: manualRemarks.trim() || undefined,
      })
      setManualScore('')
      setManualRemarks('')
      onRefetch()
      void loadOutcome()
    } catch (e) {
      setActionError(messageForKycAction(e))
    } finally {
      setManualLoading(false)
    }
  }

  async function onOpenAiReview() {
    setActionError(null)
    setAiLosStatus(null)
    setAiLosLoading(true)
    try {
      const response = await openAiLosReview(applicationId, {
        returnUrl: window.location.href,
        mode: 'REVIEW',
      })
      setAiLosStatus(response.message || 'AI LOS review is ready.')
      if (response.finalRedirectUrl) {
        window.open(response.finalRedirectUrl, '_blank', 'noopener,noreferrer')
        return
      }
      setActionError('Unable to open AI Review currently.')
    } catch {
      setActionError('Unable to open AI Review currently.')
    } finally {
      setAiLosLoading(false)
    }
  }

  if (!kycReady) {
    return <p className="text-sm text-slate-500">Loading underwriting prerequisites…</p>
  }

  const uwFieldVis = getVisibleUnderwritingFields(app.borrowerType)
  const partyLabels = applicationPartyLabels(app.intakeSegment)
  const creditSum = buildCreditSummary({
    app,
    creditControlView: app.creditControlView,
    latestUnderwritingEvaluation: app.latestUnderwritingEvaluation,
    kycOutcome,
    bankStatementOnFile,
  })
  const rec = app.creditDecision === 'REJECT' || app.creditDecision === 'REJECTED' ? 'Reject' : app.creditDecision === 'APPROVED' ? 'Approve' : app.creditDecision === 'MANUAL_REVIEW' ? 'Manual review' : '—'

  const assignInfo = app.financialInfo?.assignmentInfo as
    | { ruleName?: string; assignedRole?: string; assignedUserId?: string; assignedAt?: string }
    | undefined
  const latestEval = app.latestUnderwritingEvaluation as
    | {
        aggregateDecision?: string
        aggregateScore?: number
        ruleResults?: unknown[]
        effectiveValues?: unknown
        parameterResults?: Array<{
          parameter?: string
          rowId?: string
          source?: string
          condition?: string
          weight?: number
          matched?: boolean
          pointsEarned?: number
          maxScore?: number
          valueUsed?: string
          valueSource?: string
          attachment?: string
          decision?: string
          reason?: string
          hardRule?: boolean
          formulaBreakdown?: {
            expression?: string
            result?: string | null
            operands?: Array<{ source?: string; parameter?: string; valueUsed?: string | null }>
          } | null
          dependencyOutcome?: {
            matched?: boolean
            logic?: string
            conditions?: Array<{
              source?: string
              parameter?: string
              condition?: string
              valueUsed?: string | null
              matched?: boolean
            }>
          } | null
          skippedDueToDependency?: boolean
        }>
        scorecardId?: string
        scorecardName?: string
        scorecardVersion?: number
        scorecardPriority?: number
        scorecardBorrowerType?: string
        scorecardLoanProduct?: string
        scorecardMinAmount?: string
        scorecardMaxAmount?: string
        scorecardGeography?: unknown
      }
    | undefined

  const effView = app.creditControlView as
    | { effective?: { scorecard?: Record<string, string> } }
    | undefined
  const evalEffective = latestEval?.effectiveValues as
    | { scorecard?: Record<string, string> }
    | undefined
  const scorecardMap = effView?.effective?.scorecard ?? evalEffective?.scorecard

  const limitMeta = app.financialInfo?.underwritingMeta as
    | {
        limitSizingPolicy?: Record<string, unknown>
        scfLimitPolicy?: Record<string, unknown>
      }
    | undefined
  const limitPolicy = limitMeta?.limitSizingPolicy ?? limitMeta?.scfLimitPolicy
  const showLimitSizing =
    limitPolicy != null || (scorecardMap != null && scorecardMap.SCF_STANDARD_LIMIT != null)

  const pi = app.personalInfo as Record<string, unknown> | null | undefined
  const bi = app.businessInfo as Record<string, unknown> | null | undefined
  const incomeLine = pi?.monthlyNetIncome != null && String(pi.monthlyNetIncome).trim() !== ''
  const empLine = pi?.employmentType != null && String(pi.employmentType).trim() !== ''
  const showIntakeContext = Boolean(
    incomeLine ||
      empLine ||
      pi?.employerName ||
      pi?.occupationIndustry ||
      (uwFieldVis.showGstinIntakeContext && bi?.gstin),
  )
  const ci = app.collateralInfo as Record<string, unknown> | null | undefined
  const bint = ci?.[BORROWER_INTAKE_KEY] ?? ci?.borrowerIntake
  const collateralIntake = typeof bint === 'object' && bint != null ? (bint as Record<string, unknown>) : null
  const showCollateralIntake = requiresCollateral(app.loanProduct) && Boolean(collateralIntake?.estimatedValue)

  const assessmentRan = hasAssessmentResult({
    uwMeta,
    latestEval,
    creditDecision: app.creditDecision,
  })
  const outcomeLabel = formatAssessmentOutcome(
    (uwMeta?.recommendation as string | undefined) ??
      latestEval?.aggregateDecision ??
      app.creditDecision ??
      null,
  )
  const scoreDisplay =
    latestEval?.aggregateScore != null
      ? String(latestEval.aggregateScore)
      : app.creditRiskScore != null
        ? String(app.creditRiskScore)
        : null
  const whyRows = (() => {
    const fromParams = whyRowsFromParameterResults(latestEval?.parameterResults)
    if (fromParams.length > 0) return fromParams
    return whyRowsFromReasons(uwMeta?.reasons)
  })()
  const failedRuleLabels = Array.isArray(latestEval?.ruleResults)
    ? (latestEval!.ruleResults as Array<{ passed?: boolean; ruleName?: string; name?: string; decision?: string }>)
        .filter((r) => r && (r.passed === false || String(r.decision ?? '').toUpperCase() === 'FAIL'))
        .map((r) => String(r.ruleName || r.name || 'Rule failed'))
    : []
  const assessmentConcerns = buildAssessmentConcerns({
    riskFlags: creditSum.riskFlags,
    missingItems: creditSum.missingItems,
    pendingManualReview,
    failedRuleLabels,
    hasOverride: hasUnderwritingOverride,
  })

  return (
    <div className="space-y-5" data-testid="credit-assessment-experience">
      {/* A. Assessment Summary */}
      <AppSectionCard
        tone="hero"
        title="Assessment Summary"
        subtitle="What the system found for this credit assessment."
        badge={
          assessmentRan ? (
            <span className="bt-section-card__chip bt-section-card__chip--info">{outcomeLabel}</span>
          ) : null
        }
      >
        {!assessmentRan ? (
          <div className="space-y-2" data-testid="assessment-empty-state">
            <p className="text-sm text-slate-600">Credit assessment has not been run yet.</p>
            {canRunUnderwriting ? (
              <p className="text-xs text-slate-500">
                Use <span className="font-medium text-slate-700">Run underwriting</span> under Assessment details when
                KYC and bureau requirements are ready.
              </p>
            ) : null}
          </div>
        ) : (
          <dl className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-3" data-testid="assessment-summary-result">
            <div>
              <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">
                Assessment outcome
              </dt>
              <dd className="mt-0.5 font-medium text-slate-900">{outcomeLabel}</dd>
            </div>
            {scoreDisplay != null ? (
              <div>
                <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Score / grade</dt>
                <dd className="mt-0.5 font-medium tabular-nums text-slate-900">{scoreDisplay}</dd>
              </div>
            ) : null}
            <div>
              <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">
                Key recommendation
              </dt>
              <dd className="mt-0.5 font-medium text-slate-900">
                {formatAssessmentOutcome(rec !== '—' ? rec : app.creditDecision)}
              </dd>
            </div>
            <div>
              <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Review status</dt>
              <dd className="mt-0.5 font-medium text-slate-900">
                {pendingManualReview
                  ? 'Manual review required'
                  : decisionDone
                    ? 'Decision recorded'
                    : 'Assessment complete'}
              </dd>
            </div>
            <div>
              <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">
                Manual review required?
              </dt>
              <dd className="mt-0.5 font-medium text-slate-900">{pendingManualReview ? 'Yes' : 'No'}</dd>
            </div>
            {effectiveBureau != null ? (
              <div>
                <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">
                  Bureau score (used)
                </dt>
                <dd className="mt-0.5 font-medium tabular-nums text-slate-900">{String(effectiveBureau)}</dd>
              </div>
            ) : null}
            {kycOutcome ? (
              <div>
                <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">KYC outcome</dt>
                <dd className="mt-0.5">
                  <span
                    className={
                      outcomeStr.toUpperCase() === 'PASS'
                        ? 'bt-section-card__chip bt-section-card__chip--success'
                        : outcomeStr.toUpperCase() === 'FAIL'
                          ? 'bt-section-card__chip bt-section-card__chip--danger'
                          : 'bt-section-card__chip bt-section-card__chip--warning'
                    }
                  >
                    {outcomeStr || '—'}
                  </span>
                </dd>
              </div>
            ) : null}
          </dl>
        )}
      </AppSectionCard>

      {/* B. Why this outcome */}
      {whyRows.length > 0 ? (
        <AppSectionCard
          tone="default"
          title="Why this outcome"
          subtitle="Business reasons from the latest underwriting result."
        >
          <div className="overflow-x-auto" data-testid="why-this-outcome">
            <table className="w-full min-w-[28rem] text-left text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-[11px] font-semibold uppercase tracking-wide text-slate-500">
                  <th className="py-2 pr-3 font-semibold">Reason</th>
                  <th className="py-2 pr-3 font-semibold">Observed value</th>
                  <th className="py-2 pr-3 font-semibold">Expected / threshold</th>
                  <th className="py-2 font-semibold">Outcome</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {whyRows.map((row, i) => (
                  <tr key={i}>
                    <td className="py-2.5 pr-3 text-slate-900">{row.reason}</td>
                    <td className="py-2.5 pr-3 tabular-nums text-slate-800">{row.observed ?? '—'}</td>
                    <td className="py-2.5 pr-3 text-slate-700">{row.expected ?? '—'}</td>
                    <td className="py-2.5 text-slate-800">{row.outcome ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </AppSectionCard>
      ) : null}

      {/* C. Evidence reviewed */}
      <div className="space-y-3" data-testid="evidence-reviewed">
        <div>
          <h3 className="text-sm font-semibold text-slate-900">Evidence reviewed</h3>
          <p className="text-xs text-slate-500">
            Grouped signals already on this application (LOS underwriting evidence).
          </p>
        </div>
      <div className="grid gap-3 lg:grid-cols-2">
        <AppSectionCard tone="default" title="Application inputs" subtitle={partyLabels.snapshotHeading}>

          <dl className="divide-y divide-slate-100">
            {Object.entries(creditSum.borrowerSnapshot).map(([k, v], idx) => (
              <div
                key={k}
                className={
                  idx === 0
                    ? 'grid gap-1 bg-orange-50/40 py-2.5 sm:grid-cols-[1fr_auto] sm:items-center'
                    : 'grid gap-1 py-2.5 sm:grid-cols-[1fr_auto] sm:items-center'
                }
              >
                <dt
                  className={
                    idx === 0
                      ? 'text-[11px] font-semibold uppercase tracking-wide text-orange-700'
                      : 'text-[11px] font-semibold uppercase tracking-wide text-slate-500'
                  }
                >
                  {k}
                </dt>
                <dd
                  className={
                    idx === 0
                      ? 'break-words text-right text-sm font-semibold tabular-nums text-slate-950'
                      : 'break-words text-right text-sm font-medium tabular-nums text-slate-900'
                  }
                >
                  {formatSummaryValueDisplay(k, v)}
                </dd>
              </div>
            ))}
          </dl>
        </AppSectionCard>
        <AppSectionCard
          tone="default"
          title="KYC / verification"
          subtitle="Identity and compliance checks on file."
        >
          <dl className="divide-y divide-slate-100">
            {Object.entries(creditSum.kycSummary).map(([k, v], idx) => (
              <div
                key={k}
                className={
                  idx === 0
                    ? 'grid gap-1 bg-orange-50/40 py-2.5 sm:grid-cols-[1fr_auto] sm:items-center'
                    : 'grid gap-1 py-2.5 sm:grid-cols-[1fr_auto] sm:items-center'
                }
              >
                <dt
                  className={
                    idx === 0
                      ? 'text-[11px] font-semibold uppercase tracking-wide text-orange-700'
                      : 'text-[11px] font-semibold uppercase tracking-wide text-slate-500'
                  }
                >
                  {k}
                </dt>
                <dd
                  className={
                    idx === 0
                      ? 'break-words text-right text-sm font-semibold tabular-nums text-slate-950'
                      : 'break-words text-right text-sm font-medium tabular-nums text-slate-900'
                  }
                >
                  {v}
                </dd>
              </div>
            ))}
          </dl>
        </AppSectionCard>
      </div>

      {Object.keys(creditSum.bureauSummary).length > 0 ? (
      <CollapsibleSection
        title="Bureau"
        subtitle="Bureau score, enquiry, and unsecured exposure signals."
        badge={
          <span className="rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-[11px] font-semibold text-slate-600">
            {Object.keys(creditSum.bureauSummary).length} checks
          </span>
        }
        defaultOpen={assessmentRan}
      >
        <dl className="divide-y divide-slate-100 text-xs">
          {Object.entries(creditSum.bureauSummary).map(([k, v]) => (
            <div key={k} className="grid gap-2 py-2 sm:grid-cols-[1fr_auto] sm:items-center">
              <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">{k}</dt>
              <dd className="break-words text-right text-sm font-semibold tabular-nums text-slate-900">
                {formatSummaryValueDisplay(k, v)}
              </dd>
            </div>
          ))}
        </dl>
      </CollapsibleSection>
      ) : null}

      {Object.keys(creditSum.incomeSummary).length > 0 ? (
      <CollapsibleSection
        title="ITR / Income"
        subtitle="Income, turnover, repayment capacity, and cashflow indicators."
        badge={
          <span className="rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-[11px] font-semibold text-slate-600">
            {Object.keys(creditSum.incomeSummary).length} lines
          </span>
        }
        defaultOpen={false}
      >
        <dl className="max-h-72 divide-y divide-slate-100 overflow-y-auto text-xs">
          {Object.entries(creditSum.incomeSummary).map(([k, v]) => (
            <div key={k} className="grid gap-2 py-2 sm:grid-cols-[1fr_auto] sm:items-center">
              <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">{k}</dt>
              <dd className="break-words text-right text-sm font-semibold tabular-nums text-slate-900">
                {formatSummaryValueDisplay(k, v)}
              </dd>
            </div>
          ))}
        </dl>
      </CollapsibleSection>
      ) : null}

      <div
        className={
          aaVerified
            ? 'bt-section-card bt-section-card--success p-4 text-xs text-slate-800'
            : 'bt-section-card bt-section-card--warning p-4 text-xs text-slate-800'
        }
      >
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="text-sm font-semibold text-slate-900">Banking</h3>
          <span
            className={
              aaVerified
                ? 'inline-block rounded border border-emerald-200 bg-emerald-50 px-2 py-0.5 text-[11px] font-medium text-emerald-900'
                : 'inline-block rounded border border-amber-200 bg-amber-50 px-2 py-0.5 text-[11px] font-medium text-amber-950'
            }
          >
            {aaVerified ? 'AA Verified' : 'AA Pending'}
          </span>
        </div>
        {aaLoading ? (
          <p className="mt-2 text-slate-500">Loading Account Aggregator data…</p>
        ) : aaVerified && aaFetchedData ? (
          <dl className="mt-3 grid gap-2 sm:grid-cols-3">
            <div>
              <dt className="text-slate-500">Total balance</dt>
              <dd className="font-medium tabular-nums text-slate-900">{formatMoneyWithScale(aaFetchedData.totalBalance)}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Avg monthly inflow</dt>
              <dd className="font-medium tabular-nums text-slate-900">
                {formatMoneyWithScale(aaFetchedData.avgMonthlyInflow)}
              </dd>
            </div>
            <div>
              <dt className="text-slate-500">FOIR (from AA)</dt>
              <dd className="font-medium tabular-nums text-slate-900">
                {aaFoir != null ? `${aaFoir.toFixed(1)}%` : '—'}
              </dd>
            </div>
          </dl>
        ) : (
          <p className="mt-2 text-slate-600">
            No fetched AA bank data yet. Initiate consent on the <strong>Bank Data</strong> tab to pull
            verified balances and cashflow for underwriting.
          </p>
        )}
      </div>

      {creditSum.collateralSummary ? (
        <div className="bt-section-card bt-section-card--success p-4 text-xs text-slate-800">
          <h3 className="text-sm font-semibold text-emerald-950">Collateral</h3>
          <dl className="mt-2 space-y-1">
            {Object.entries(creditSum.collateralSummary).map(([k, v]) => (
              <div key={k} className="flex justify-between gap-2">
                <dt className="text-slate-500">{k}</dt>
                <dd className="text-right text-slate-900">{formatSummaryValueDisplay(k, v)}</dd>
              </div>
            ))}
          </dl>
        </div>
      ) : null}

      {showCollateralIntake ? (
        <div className="bt-section-card bt-section-card--success p-4 text-sm text-slate-800">
          <h3 className="text-sm font-semibold text-emerald-950">{partyLabels.collateralIntakeSectionTitle}</h3>
          <p className="mt-1 text-xs text-slate-600">
            Declared security for this secured product. Official valuation and LTV follow your standard process.
          </p>
          <dl className="mt-3 grid gap-2 sm:grid-cols-2">
            <div>
              <dt className="text-xs text-slate-500">Type</dt>
              <dd className="font-medium text-slate-900">{String(collateralIntake!.collateralType ?? '—').replaceAll('_', ' ')}</dd>
            </div>
            <div>
              <dt className="text-xs text-slate-500">Estimated value (declared)</dt>
              <dd className="font-medium text-slate-900">{String(collateralIntake!.estimatedValue)} INR</dd>
            </div>
          </dl>
        </div>
      ) : null}
      {showIntakeContext ? (
        <div className="bt-section-card bt-section-card--info p-4 text-sm text-slate-800">
          <h3 className="text-sm font-semibold text-sky-950">{partyLabels.declaredIntakeHeading}</h3>
          <p className="mt-1 text-xs text-slate-600">
            Figures from the application journey. Use these as context alongside bureau, bank, and manual credit inputs;
            they do not replace verified income.
          </p>
          <dl className="mt-3 grid gap-2 sm:grid-cols-2">
            {incomeLine ? (
              <div>
                <dt className="text-xs text-slate-500">Monthly income (declared)</dt>
                <dd className="font-medium text-slate-900">{String(pi!.monthlyNetIncome)} INR</dd>
              </div>
            ) : null}
            {empLine ? (
              <div>
                <dt className="text-xs text-slate-500">Employment type</dt>
                <dd className="font-medium text-slate-900">{String(pi!.employmentType).replaceAll('_', ' ')}</dd>
              </div>
            ) : null}
            {pi?.employerName ? (
              <div>
                <dt className="text-xs text-slate-500">Employer / business name</dt>
                <dd className="font-medium text-slate-900">{String(pi.employerName)}</dd>
              </div>
            ) : null}
            {pi?.occupationIndustry ? (
              <div>
                <dt className="text-xs text-slate-500">Occupation / industry</dt>
                <dd className="font-medium text-slate-900">{String(pi.occupationIndustry)}</dd>
              </div>
            ) : null}
            {uwFieldVis.showGstinIntakeContext && bi?.gstin ? (
              <div>
                <dt className="text-xs text-slate-500">GSTIN (on application)</dt>
                <dd className="font-mono text-slate-900">{String(bi.gstin)}</dd>
              </div>
            ) : null}
          </dl>
        </div>
      ) : null}
      </div>

      {/* D. Concerns / Exceptions */}
      {assessmentConcerns.length > 0 ? (
        <AppSectionCard
          tone="warning"
          title="Concerns / Exceptions"
          subtitle="Items that need credit-user attention before or after assessment."
        >
          <ul className="space-y-2" data-testid="assessment-concerns">
            {assessmentConcerns.map((c, i) => {
              const tone = concernSeverityTone(c.severity)
              const chip =
                tone === 'danger'
                  ? 'bt-section-card__chip bt-section-card__chip--danger'
                  : tone === 'info'
                    ? 'bt-section-card__chip bt-section-card__chip--info'
                    : 'bt-section-card__chip bt-section-card__chip--warning'
              return (
                <li
                  key={i}
                  className="flex flex-wrap items-start gap-2 rounded-lg border border-amber-100 bg-amber-50/40 px-3 py-2 text-sm"
                >
                  <span className={chip}>{concernSeverityLabel(c.severity)}</span>
                  <span className="min-w-0 flex-1 text-slate-900">
                    {c.label}
                    {c.detail ? <span className="mt-0.5 block text-xs text-slate-600">{c.detail}</span> : null}
                  </span>
                </li>
              )
            })}
          </ul>
          {hasUnderwritingOverride ? (
            <div className="mt-3 space-y-2 border-t border-amber-100 pt-3">
              {underwritingOverrides.map((row, idx) => (
                <div key={idx} className="rounded-lg border border-indigo-100 bg-white/80 p-3 text-xs">
                  <div className="font-semibold text-indigo-950">
                    Override {idx + 1} · {String(row.previousStatus ?? '—')} → {String(row.newStatus ?? '—')}
                  </div>
                  <div className="mt-1 text-indigo-900/90">{String(row.reason ?? '—')}</div>
                  {row.remarks ? <div className="mt-1 text-indigo-800/80">Remarks: {String(row.remarks)}</div> : null}
                </div>
              ))}
            </div>
          ) : null}
        </AppSectionCard>
      ) : null}

      {/* E. Assessment details */}
      <div className="space-y-3" data-testid="assessment-details">
        <div>
          <h3 className="text-sm font-semibold text-slate-900">Assessment details</h3>
          <p className="text-xs text-slate-500">Run assessment, capture manual inputs, and open AI-assisted review.</p>
        </div>

      <AppSectionCard
        tone="default"
        title="Manual credit inputs"
        subtitle="Add or review manual underwriting inputs for scorecard parameters."
        actions={
          <button
            type="button"
            onClick={() => setShowManualInputs((v) => !v)}
            className="bt-btn bt-btn-secondary bt-btn-sm"
            aria-expanded={showManualInputs}
          >
            {showManualInputs ? 'Hide manual inputs' : 'Add manual inputs'}
          </button>
        }
      >
        <div className={showManualInputs ? 'block' : 'hidden'}>
          <ManualCreditInputsSection
            applicationId={applicationId}
            app={app}
            onRefetch={onRefetch}
            bankStatementOnFile={bankStatementOnFile}
          />
        </div>
        {!showManualInputs ? (
          <p className="text-xs text-slate-500">Expand to manage manual credit inputs for this case.</p>
        ) : null}
      </AppSectionCard>

      <AppSectionCard
        tone="default"
        title="AI Credit Review"
        subtitle="Open AI-assisted application review."
        actions={
          <button
            type="button"
            onClick={() => void onOpenAiReview()}
            disabled={aiLosLoading}
            className="bt-btn bt-btn-secondary bt-btn-sm disabled:cursor-not-allowed disabled:opacity-50"
            data-testid="open-ai-credit-review"
          >
            {aiLosLoading ? 'Opening…' : 'Open AI Credit Review'}
          </button>
        }
      >
        {aiLosStatus ? (
          <p className="text-xs font-medium text-emerald-700">{aiLosStatus}</p>
        ) : (
          <p className="text-xs text-slate-500">
            Secondary helper for an AI-assisted second look. Not an authoritative decision and not Policy Studio.
          </p>
        )}
      </AppSectionCard>

      {isInvoiceDiscountingBorrowerApp(app) ? (
        <CollapsibleSection
          title="Program vintage eligibility"
          subtitle="Invoice discounting program vintage checks (deprioritized vs credit decision)."
          defaultOpen={false}
        >
          <InvoiceDiscountingVintagePanel applicationId={applicationId} />
        </CollapsibleSection>
      ) : null}

      {kycError ? <p className="text-sm text-amber-800">{kycError}</p> : null}
      {inKyc && kycOutcome && !kycPass ? (
        <p className="text-sm text-amber-800">KYC is not PASS yet. Complete KYC checks before bureau and underwriting.</p>
      ) : null}
      {gstBlocksUnderwriting ? (
        <p className="text-sm text-amber-900">
          GST analysis is configured on this workflow. Complete GST analysis (provider report success) on the KYC tab
          before running underwriting.
        </p>
      ) : null}
      {actionError ? <ErrorState message={actionError} /> : null}

      {inKyc && kycPass && !hasBureau ? (
        <div className="bt-section-card bt-section-card--warning p-3 text-sm text-amber-900">
          No bureau score yet. Pull the bureau report (requires provider configuration), or save a manual score if
          your role allows.
        </div>
      ) : null}

      {inKyc && kycPass && !hasBureau ? (
        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={() => void onPullBureau()}
            disabled={bureauLoading}
            className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {bureauLoading ? 'Pulling bureau…' : 'Pull credit bureau report'}
          </button>
        </div>
      ) : null}

      {inKyc && kycPass && !hasBureau ? (
        <div className="bt-section-card bt-section-card--default p-4 bg-slate-50">
          <h3 className="bt-card-title">Manual bureau score (optional)</h3>
          <p className="mb-2 text-xs text-slate-500">Requires an internal role in production. Used when automated pull is unavailable.</p>
          {bureauFileMessage ? (
            <div
              className="mb-2 rounded border border-emerald-200 bg-emerald-50 px-2 py-1.5 text-xs text-emerald-900"
              role="status"
            >
              {bureauFileMessage}
            </div>
          ) : null}
          <div className="flex flex-wrap items-end gap-2">
            <label className="text-sm text-slate-700">
              <span className="mb-0.5 block text-xs text-slate-500">Score</span>
              <input
                className="w-28 rounded border border-slate-300 px-2 py-1 text-sm"
                value={manualScore}
                onChange={(e) => setManualScore(e.target.value.replace(/\D/g, ''))}
                inputMode="numeric"
                placeholder="e.g. 720"
              />
            </label>
            <label className="min-w-[12rem] flex-1 text-sm text-slate-700">
              <span className="mb-0.5 block text-xs text-slate-500">Remarks</span>
              <input
                className="w-full rounded border border-slate-300 px-2 py-1 text-sm"
                value={manualRemarks}
                onChange={(e) => setManualRemarks(e.target.value)}
              />
            </label>
            <label className="inline-flex cursor-pointer items-center rounded border border-slate-400 bg-white px-2 py-1.5 text-xs text-slate-800">
              {bureauDocUploading ? '…' : 'Bureau report file'}
              <input
                type="file"
                className="sr-only"
                disabled={bureauDocUploading}
                onChange={async (e) => {
                  const f = e.target.files?.[0]
                  e.target.value = ''
                  if (!f) return
                  const n = Number.parseInt(manualScore, 10)
                  if (Number.isNaN(n) || n <= 0) {
                    setBureauFileMessage(null)
                    setActionError('Enter a valid bureau score before attaching a file.')
                    return
                  }
                  setBureauDocUploading(true)
                  setBureauFileMessage(null)
                  setActionError(null)
                  try {
                    const doc = await uploadDocument(applicationId, f, 'BUREAU_STATEMENT_EVIDENCE')
                    await saveManualBureau(applicationId, {
                      manualBureauScore: n,
                      manualBureauRemarks: manualRemarks.trim() || undefined,
                      manualBureauDocumentId: doc.id,
                    })
                    onRefetch()
                    void loadOutcome()
                    setBureauFileMessage(
                      doc.fileName
                        ? `Stored “${doc.fileName}” and linked to this manual bureau score.`
                        : 'Bureau file saved and linked to the manual score.',
                    )
                  } catch (err) {
                    setActionError(messageForKycAction(err))
                  } finally {
                    setBureauDocUploading(false)
                  }
                }}
              />
            </label>
            <button
              type="button"
              onClick={() => void onSaveManualBureau()}
              disabled={manualLoading}
              className="rounded-md bg-slate-800 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
            >
              {manualLoading ? 'Saving…' : 'Save manual score'}
            </button>
          </div>
        </div>
      ) : null}

      {canRunUnderwriting && scorecardInputRequirements.length > 0 ? (
        <ScorecardPreRunInputsPanel
          applicationId={applicationId}
          app={app}
          matchedScorecardName={
            matchedScorecard?.name ??
            (matchedUnderwritingRules[0]?.name
              ? `Rules: ${matchedUnderwritingRules[0].name}`
              : 'Underwriting policy')
          }
          matchedScorecardVersion={matchedScorecard?.version ?? matchedUnderwritingRules[0]?.priority ?? 0}
          requirements={scorecardInputRequirements}
          onRefetch={onRefetch}
          focusParameter={scorecardFocusParameter}
        />
      ) : null}

      {canRunUnderwriting ? (
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => void onStartUnderwriting()}
            disabled={underwriteLoading || !allScorecardInputsReady(scorecardInputRequirements)}
            className={
              assessmentRan
                ? 'bt-btn bt-btn-secondary disabled:cursor-not-allowed disabled:opacity-50'
                : 'bt-btn bt-btn-primary disabled:cursor-not-allowed disabled:opacity-50'
            }
            data-testid="run-underwriting-action"
          >
            {underwriteLoading ? 'Running…' : assessmentRan ? 'Re-run underwriting' : 'Run underwriting'}
          </button>
          {!allScorecardInputsReady(scorecardInputRequirements) ? (
            <p className="self-center text-xs text-amber-800">
              Complete and save scorecard inputs above before starting underwriting.
            </p>
          ) : null}
        </div>
      ) : null}
      </div>

      {pendingManualReview ? (
        <AppSectionCard
          tone="warning"
          title="Credit Review Decision"
          subtitle="Human credit conclusion for cases routed to manual review."
        >
          <div data-testid="credit-review-decision">
            <p className="mb-3 text-sm text-amber-900">
              Policy routed this case for manual review. Approve or reject as Credit Manager.
            </p>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                disabled={manualResolveLoading}
                onClick={() => void onApproveManual()}
                className="rounded-md border border-emerald-700 bg-emerald-800 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
                data-testid="manual-approve"
              >
                {manualResolveLoading ? '…' : 'Approve'}
              </button>
              <button
                type="button"
                disabled={manualResolveLoading}
                onClick={() => void onRejectManual()}
                className="rounded-md border border-rose-600 bg-rose-700 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
                data-testid="manual-reject"
              >
                {manualResolveLoading ? '…' : 'Reject'}
              </button>
            </div>
          </div>
        </AppSectionCard>
      ) : null}

      <div data-testid="technical-details">
        <CollapsibleSection
          title="Technical Details"
          subtitle="Engine metadata, raw rules, assignment, and limit-sizing internals for support."
          defaultOpen={false}
        >
          {uwMeta ? (
            <dl className="mb-4 grid gap-3 text-xs sm:grid-cols-2 lg:grid-cols-3">
              <div>
                <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Source</dt>
                <dd className="mt-0.5 font-medium text-slate-900">{uwMeta.source ?? '—'}</dd>
              </div>
              <div>
                <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Matched rule set</dt>
                <dd className="mt-0.5 font-medium text-slate-900">
                  {uwMeta.ruleSetName ?? uwMeta.ruleSetId ?? '—'}
                </dd>
              </div>
              <div>
                <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Decision source</dt>
                <dd className="mt-0.5 font-medium text-slate-900">{creditSum.decisionSource}</dd>
              </div>
              <div>
                <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Completeness</dt>
                <dd className="mt-0.5 font-medium text-slate-900">{creditSum.completenessPercent}%</dd>
              </div>
              {assignInfo ? (
                <div className="sm:col-span-2">
                  <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Assignment</dt>
                  <dd className="mt-0.5 text-slate-800">
                    {assignInfo.ruleName ?? 'rule'} → role {assignInfo.assignedRole ?? '—'}
                    {assignInfo.assignedUserId ? ` · user ${assignInfo.assignedUserId}` : ''}
                    {assignInfo.assignedAt ? ` · ${assignInfo.assignedAt}` : ''}
                  </dd>
                </div>
              ) : null}
            </dl>
          ) : null}

          {latestEval ? (
            <div className="mb-4 space-y-3">
              <UnderwritingRulesRanPanel
                ruleResults={latestEval.ruleResults}
                aggregateDecision={latestEval.aggregateDecision}
                aggregateScore={latestEval.aggregateScore}
                defaultOpen={false}
              />
              <ScorecardSummaryPanel
                evaluation={latestEval}
                rawParameterValues={scorecardMap}
                onOpenManualInput={openManualInput}
                defaultOpen={false}
              />
            </div>
          ) : null}

          {creditSum.scorecardSummary && !latestEval?.aggregateScore ? (
            <AppSectionCard tone="violet" title="Scorecard (summary)">
              <p className="text-xs text-slate-800">
                {creditSum.scorecardSummary.name}{' '}
                {creditSum.scorecardSummary.version != null
                  ? `· v${creditSum.scorecardSummary.version}`
                  : ''}{' '}
                · score {creditSum.scorecardSummary.aggregateScore} · outcome{' '}
                {creditSum.scorecardSummary.policyOutcome}
              </p>
            </AppSectionCard>
          ) : null}

          {showLimitSizing ? (
            <AppSectionCard
              tone="info"
              title="Limit sizing (from underwriting rule config)"
              subtitle="Standard limit = min(turnover % × configured turnover parameter, standard ticket cap)."
            >
              <dl className="grid gap-3 text-xs sm:grid-cols-2 lg:grid-cols-3">
                <div>
                  <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">
                    Annual GST turnover
                  </dt>
                  <dd className="mt-0.5 font-mono font-medium text-slate-900">
                    {formatMoneyWithScale(
                      Number(limitPolicy?.annualGstTurnover ?? scorecardMap?.ANNUAL_GST_TURNOVER ?? 0),
                    )}
                  </dd>
                </div>
                <div>
                  <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">
                    Standard limit
                  </dt>
                  <dd className="mt-0.5 font-mono font-medium text-slate-900">
                    {formatMoneyWithScale(Number(limitPolicy?.standardLimit ?? scorecardMap?.SCF_STANDARD_LIMIT ?? 0))}
                  </dd>
                </div>
                <div>
                  <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">
                    Max deviation cap
                  </dt>
                  <dd className="mt-0.5 font-mono font-medium text-slate-900">
                    {formatMoneyWithScale(
                      Number(limitPolicy?.maxDeviationLimit ?? scorecardMap?.SCF_MAX_DEVIATION_LIMIT ?? 0),
                    )}
                  </dd>
                </div>
                <div>
                  <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Requested</dt>
                  <dd className="mt-0.5 font-mono font-medium text-slate-900">
                    {formatMoneyWithScale(Number(limitPolicy?.requestedAmount ?? app.requestedAmount ?? 0))}
                  </dd>
                </div>
                <div>
                  <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">
                    Capped recommended
                  </dt>
                  <dd className="mt-0.5 font-mono font-medium text-emerald-800">
                    {formatMoneyWithScale(
                      Number(limitPolicy?.cappedRecommendedAmount ?? scorecardMap?.SCF_CAPPED_AMOUNT ?? 0),
                    )}
                  </dd>
                </div>
                <div>
                  <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Band</dt>
                  <dd className="mt-0.5 font-medium text-slate-900">
                    {String(
                      limitPolicy?.band ??
                        (Number(scorecardMap?.SCF_LIMIT_BAND ?? 0) === 2
                          ? 'OVER_ABSOLUTE_CAP'
                          : Number(scorecardMap?.SCF_LIMIT_BAND ?? 0) === 1
                            ? 'SPECIAL_DEVIATION'
                            : 'WITHIN_STANDARD'),
                    )}
                  </dd>
                </div>
              </dl>
            </AppSectionCard>
          ) : null}

          <p className="mt-3 text-xs text-slate-500">
            Underwriting runs after KYC is passed and a credit bureau score is on file. Live rules are configured under
            Administration → Live Underwriting Rules.
          </p>
        </CollapsibleSection>
      </div>

      {app.status === 'REJECTED' ? (
        <ProcessOverrideCard
          applicationId={applicationId}
          processCode="UNDERWRITING"
          failureCode="UNDERWRITING_REJECTED"
          title="Manual override for underwriting rejection"
          onSuccess={onRefetch}
        />
      ) : null}
    </div>
  )
}
