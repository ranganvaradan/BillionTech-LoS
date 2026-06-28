import { BORROWER_INTAKE_KEY } from '@/lib/intake/collateralIntakePayload'
import { requiresCollateral } from '@/lib/intake/securedProducts'
import { useCallback, useEffect, useState } from 'react'
import { listDocuments, uploadDocument } from '@/api/documents'
import { getKycOutcome } from '@/api/kyc'
import {
  approveManualUnderwritingFlow,
  pullBureauFlow,
  rejectManualUnderwritingFlow,
  underwriteApplicationFlow,
} from '@/api/flow'
import { openAiLosReview, saveManualBureau } from '@/api/applications'
import { messageForKycAction } from '@/api/kycErrorMessage'
import { ErrorState } from '@/components/ErrorState'
import { ManualCreditInputsSection } from '@/components/ManualCreditInputsSection'
import { ProcessOverrideCard } from '@/components/ProcessOverrideCard'
import { AppSectionCard } from '@/components/ui/AppSectionCard'
import { formatMoney } from '@/lib/format'
import { manualCreditHashForScorecardParameter } from '@/lib/manualCreditParameterAnchors'
import { helpForParameter } from '@/lib/scorecardParameterSources'
import { buildCreditSummary } from '@/lib/credit/creditSummaryBuilder'
import { getVisibleUnderwritingFields } from '@/lib/credit/underwritingFieldVisibility'
import { applicationPartyLabels } from '@/lib/applicationPartyLabels'
import type { ApplicationResponse } from '@/types/application'

export function UnderwritingSection({
  applicationId,
  app,
  onRefetch,
}: {
  applicationId: string
  app: ApplicationResponse
  onRefetch: () => void
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
  const [showParameterReference, setShowParameterReference] = useState(false)
  const [aiLosLoading, setAiLosLoading] = useState(false)
  const [aiLosStatus, setAiLosStatus] = useState<string | null>(null)

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
  const canStartUw = inKyc && kycPass && hasBureau
  const pendingManualReview = app.status === 'UNDERWRITING' && app.creditDecision === 'MANUAL_REVIEW'
  const decisionDone =
    Boolean(app.creditDecision) &&
    !(app.status === 'UNDERWRITING' && app.creditDecision === 'MANUAL_REVIEW')
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
    setUnderwriteLoading(true)
    try {
      await underwriteApplicationFlow(applicationId)
      onRefetch()
      void loadOutcome()
    } catch (e) {
      setActionError(messageForKycAction(e))
    } finally {
      setUnderwriteLoading(false)
    }
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
  const scorecardMap = effView?.effective?.scorecard

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

  return (
    <div className="space-y-5">
      {hasUnderwritingOverride ? (
        <AppSectionCard
          tone="override"
          title="Manual underwriting override applied"
          subtitle="This case was credit-approved via controlled override after an automated rejection. Original policy outcome remains in workflow history."
          badge={<span className="bt-section-card__chip bt-section-card__chip--override">Manually overridden</span>}
        >
          <dl className="grid gap-2 text-xs sm:grid-cols-2">
            {underwritingOverrides.map((row, idx) => (
              <div key={idx} className="sm:col-span-2 rounded-lg border border-indigo-100 bg-white/80 p-3">
                <div className="font-semibold text-indigo-950">
                  Override {idx + 1} · {String(row.previousStatus ?? '—')} → {String(row.newStatus ?? '—')}
                </div>
                <div className="mt-1 text-indigo-900/90">{String(row.reason ?? '—')}</div>
                {row.remarks ? <div className="mt-1 text-indigo-800/80">Remarks: {String(row.remarks)}</div> : null}
                <div className="mt-2 flex flex-wrap gap-3 text-[11px] text-indigo-700">
                  {row.manualBureauScore != null ? (
                    <span>Updated score: {String(row.manualBureauScore)}</span>
                  ) : null}
                  {row.creditRiskScore != null ? (
                    <span>Risk score: {String(row.creditRiskScore)}</span>
                  ) : null}
                  {row.timestamp ? <span>{String(row.timestamp)}</span> : null}
                </div>
              </div>
            ))}
          </dl>
        </AppSectionCard>
      ) : null}
      <div className="bt-section-card bt-section-card--hero space-y-3 p-4 text-sm text-slate-800">
        <h2 className="text-base font-semibold text-slate-900">Credit decision header</h2>
        <dl className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3 text-xs">
          <div>
            <dt className="text-slate-500">Application</dt>
            <dd className="font-mono text-slate-900">{app.applicationNumber}</dd>
          </div>
          <div>
            <dt className="text-slate-500">{partyLabels.primaryParty}</dt>
            <dd className="text-slate-900">{creditSum.borrowerSnapshot['Primary name']}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Product</dt>
            <dd className="text-slate-900">{app.loanProduct}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Requested amount</dt>
            <dd className="text-slate-900">{formatMoney(app.requestedAmount)}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Status</dt>
            <dd className="text-slate-900">{app.status}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Assignment</dt>
            <dd className="text-slate-800">
              {assignInfo
                ? `${assignInfo.assignedRole ?? '—'}${assignInfo.ruleName ? ` · ${assignInfo.ruleName}` : ''}`
                : '—'}
            </dd>
          </div>
          <div>
            <dt className="text-slate-500">System recommendation (policy view)</dt>
            <dd className="font-medium text-slate-900">{rec}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Model / aggregate score (if any)</dt>
            <dd className="text-slate-900">
              {latestEval?.aggregateScore != null ? String(latestEval.aggregateScore) : app.creditRiskScore != null ? String(app.creditRiskScore) : '—'}
            </dd>
          </div>
        </dl>
        <p className="text-xs text-slate-500">
          Decision source (aggregated view): {creditSum.decisionSource} · case completeness: {creditSum.completenessPercent}%
        </p>
      </div>

      <div className="grid gap-3 lg:grid-cols-2">
        <div className="bt-section-card bt-section-card--default p-4 text-xs text-slate-800">
          <h3 className="bt-card-title">{partyLabels.snapshotHeading}</h3>
          <dl className="mt-2 space-y-1">
            {Object.entries(creditSum.borrowerSnapshot).map(([k, v]) => (
              <div key={k} className="flex justify-between gap-2">
                <dt className="text-slate-500">{k}</dt>
                <dd className="text-right text-slate-900">{v}</dd>
              </div>
            ))}
          </dl>
        </div>
        <div className="bt-section-card bt-section-card--default p-4 text-xs text-slate-800">
          <h3 className="bt-card-title">KYC and compliance (summary)</h3>
          <dl className="mt-2 space-y-1">
            {Object.entries(creditSum.kycSummary).map(([k, v]) => (
              <div key={k} className="flex justify-between gap-2">
                <dt className="text-slate-500">{k}</dt>
                <dd className="text-right text-slate-900">{v}</dd>
              </div>
            ))}
          </dl>
        </div>
        <div className="bt-section-card bt-section-card--default p-4 text-xs text-slate-800">
          <h3 className="bt-card-title">Bureau and credit (summary)</h3>
          <dl className="mt-2 space-y-1">
            {Object.entries(creditSum.bureauSummary).map(([k, v]) => (
              <div key={k} className="flex justify-between gap-2">
                <dt className="text-slate-500">{k}</dt>
                <dd className="text-right text-slate-900">{v}</dd>
              </div>
            ))}
          </dl>
        </div>
        <div className="bt-section-card bt-section-card--default p-4 text-xs text-slate-800">
          <h3 className="bt-card-title">Income and cashflow (summary)</h3>
          <dl className="mt-2 space-y-1">
            {Object.entries(creditSum.incomeSummary).map(([k, v]) => (
              <div key={k} className="flex justify-between gap-2">
                <dt className="text-slate-500">{k}</dt>
                <dd className="text-right text-slate-900">{v}</dd>
              </div>
            ))}
          </dl>
        </div>
      </div>

      {creditSum.collateralSummary ? (
        <div className="bt-section-card bt-section-card--success p-4 text-xs text-slate-800">
          <h3 className="text-sm font-semibold text-emerald-950">Collateral (secured product)</h3>
          <dl className="mt-2 space-y-1">
            {Object.entries(creditSum.collateralSummary).map(([k, v]) => (
              <div key={k} className="flex justify-between gap-2">
                <dt className="text-slate-500">{k}</dt>
                <dd className="text-right text-slate-900">{v}</dd>
              </div>
            ))}
          </dl>
        </div>
      ) : null}

      {creditSum.scorecardSummary ? (
        <div className="bt-section-card bt-section-card--violet p-4 text-xs text-slate-800">
          <h3 className="text-sm font-semibold text-violet-950">Scorecard (summary)</h3>
          <p>
            {creditSum.scorecardSummary.name} {creditSum.scorecardSummary.version != null ? `· v${creditSum.scorecardSummary.version}` : ''} ·
            score {creditSum.scorecardSummary.aggregateScore} · outcome {creditSum.scorecardSummary.policyOutcome}
          </p>
        </div>
      ) : null}

      <div className="bt-section-card bt-section-card--warning p-4 text-xs text-slate-800">
        <h3 className="text-sm font-semibold text-amber-950">Risk flags (auto)</h3>
        <ul className="mt-2 list-inside list-disc space-y-1">
          {creditSum.riskFlags.map((f, i) => (
            <li key={i}>
              <span className="font-medium text-amber-950">[{f.severity}]</span> {f.label}
            </li>
          ))}
        </ul>
        {creditSum.missingItems.length > 0 ? (
          <p className="mt-2 text-amber-900">Pending items: {creditSum.missingItems.join(' · ')}</p>
        ) : null}
      </div>

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
      {latestEval && (
        <div className="bt-section-card bt-section-card--violet space-y-3 p-4 text-sm text-slate-800">
          <h3 className="text-sm font-semibold text-violet-950">Matched scorecard (latest underwriting run)</h3>
          <p className="text-xs text-slate-600">
            Scorecards are selected by active policy rows in <strong>underwriting_scorecards</strong> — ordered by
            priority for the same borrower type + loan product, then amount range and optional geography. This is
            separate from generic <strong>underwriting rule sets</strong> (which apply when no scorecard matches).
          </p>
          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3 text-xs">
            <div>
              <div className="text-slate-500">Name</div>
              <div className="font-medium text-slate-900">{latestEval.scorecardName ?? '—'}</div>
            </div>
            <div>
              <div className="text-slate-500">Version</div>
              <div className="font-mono">{latestEval.scorecardVersion ?? '—'}</div>
            </div>
            <div>
              <div className="text-slate-500">Priority / id</div>
              <div className="font-mono text-[11px]">
                p={latestEval.scorecardPriority ?? '—'} · {latestEval.scorecardId ?? '—'}
              </div>
            </div>
            <div>
              <div className="text-slate-500">{partyLabels.partyProductScorecard}</div>
              <div>
                {latestEval.scorecardBorrowerType ?? app.borrowerType} · {latestEval.scorecardLoanProduct ?? app.loanProduct}
              </div>
            </div>
            <div>
              <div className="text-slate-500">Amount window (₹)</div>
              <div className="font-mono text-[11px]">
                {latestEval.scorecardMinAmount ?? '0'} – {latestEval.scorecardMaxAmount ?? '∞'}
              </div>
            </div>
            <div>
              <div className="text-slate-500">This application (used for match)</div>
              <div>
                {formatMoney(app.requestedAmount)} · {app.borrowerType} / {app.loanProduct}
                {app.personalInfo && typeof (app.personalInfo as { state?: string }).state === 'string' ? (
                  <span> · {String((app.personalInfo as { state?: string }).state)}</span>
                ) : null}
              </div>
            </div>
          </div>
          {latestEval.scorecardGeography ? (
            <p className="text-xs text-slate-500">
              Geography filter on scorecard: {JSON.stringify(latestEval.scorecardGeography)}
            </p>
          ) : null}
          <div className="border-t border-violet-200/60 pt-3 text-xs">
            <span className="font-medium text-slate-800">Outcome:</span> {String(latestEval.aggregateDecision ?? '—')}{' '}
            <span className="text-slate-500">(aggregate score {String(latestEval.aggregateScore ?? '—')})</span>
          </div>
        </div>
      )}

      {latestEval && Array.isArray(latestEval.parameterResults) && latestEval.parameterResults.length > 0 ? (
        <div className="bt-card overflow-x-auto p-3">
          <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-600">Parameter results</h4>
          <table className="w-full min-w-0 table-fixed border-collapse text-left text-xs">
            <colgroup>
              <col className="w-[14%]" />
              <col className="w-[10%]" />
              <col className="w-[7%]" />
              <col className="w-[30%]" />
              <col className="w-[7%]" />
              <col className="w-[12%]" />
              <col className="w-[20%]" />
            </colgroup>
            <thead>
              <tr className="border-b border-slate-200 text-slate-500">
                <th className="min-w-0 py-1.5 pr-1">Parameter</th>
                <th className="min-w-0 py-1.5 pr-1">Required line source</th>
                <th className="min-w-0 py-1.5 pr-1">Value used</th>
                <th className="min-w-0 py-1.5 pr-1">Resolved source</th>
                <th className="min-w-0 py-1.5 pr-1 text-right">Points</th>
                <th className="min-w-0 py-1.5 pr-1">Status</th>
                <th className="min-w-0 border-l border-slate-200 py-1.5 pl-1">Actions</th>
              </tr>
            </thead>
            <tbody>
              {latestEval.parameterResults.map((p, i) => {
                const missing = (p.valueUsed == null || p.valueUsed === '') && p.matched === false
                const help = helpForParameter(p.parameter)
                return (
                  <tr key={i} className="border-b border-slate-100 last:border-0">
                    <td className="min-w-0 py-1.5 pr-1 align-top">
                      <div className="break-words font-mono text-[11px] text-slate-900">{p.parameter}</div>
                      <div className="break-words text-[10px] text-slate-500">{help.label}</div>
                    </td>
                    <td className="min-w-0 break-words py-1.5 pr-1 align-top text-[11px] text-slate-700">
                      {p.source}
                    </td>
                    <td className="min-w-0 break-words py-1.5 pr-1 align-top font-mono text-[11px]">
                      {p.valueUsed ?? '—'}
                    </td>
                    <td className="min-w-0 py-1.5 pr-1 align-top text-[11px] text-slate-600">
                      <div className="break-words [overflow-wrap:anywhere]">{p.valueSource ?? '—'}</div>
                    </td>
                    <td className="min-w-0 break-words py-1.5 pr-1 align-top text-right font-mono text-[11px] tabular-nums">
                      {p.pointsEarned != null && p.maxScore != null
                        ? `${p.pointsEarned} / ${p.maxScore}`
                        : '—'}
                    </td>
                    <td className="min-w-0 py-1.5 pr-2 align-top">
                      {missing ? (
                        <div className="text-center text-[10px] leading-tight text-amber-950">
                          <span className="inline-block max-w-full rounded border border-amber-200/80 bg-amber-100 px-1.5 py-0.5">
                            Missing input
                          </span>
                        </div>
                      ) : p.matched ? (
                        <div className="text-center text-[10px] text-emerald-800">Present</div>
                      ) : (
                        <div className="text-center text-[10px] text-slate-600">No match</div>
                      )}
                    </td>
                    <td className="min-w-0 border-l border-slate-100 py-1.5 pl-2 align-top text-[11px]">
                      {missing ? (
                        <div className="flex min-w-0 flex-col gap-1.5 break-words">
                          <a
                            href={manualCreditHashForScorecardParameter(p.parameter)}
                            className="text-indigo-700 underline [overflow-wrap:anywhere] hover:text-indigo-900"
                          >
                            Add manual input
                          </a>
                          <span className="text-slate-500 [overflow-wrap:anywhere]">
                            Or upload on the Documents tab, then link evidence in manual inputs.
                          </span>
                        </div>
                      ) : (
                        '—'
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      ) : null}

      <div className="bt-section-card bt-section-card--default p-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h3 className="bt-card-title">Parameter source reference</h3>
            <p className="text-xs text-slate-500">
              View how scorecard parameters map to provider, KYC, and manual sources.
            </p>
          </div>
          <button
            type="button"
            onClick={() => setShowParameterReference((v) => !v)}
            className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-800"
            aria-expanded={showParameterReference}
          >
            {showParameterReference ? 'Hide parameter references' : 'View parameter references'}
          </button>
        </div>
        {showParameterReference ? (
          <div className="bt-section-card bt-section-card--warning mt-3 p-3 text-xs text-slate-800">
            <p className="text-slate-600">
              How inputs connect to the scorecard is documented below. Data flows from provider pulls, KYC, manual credit
              inputs, and the effective credit-control snapshot (never overwriting raw provider data).
            </p>
            <dl className="mt-2 grid gap-2 sm:grid-cols-2">
              {(
                [
                  'BUREAU_SCORE',
                  'MONTHLY_INCOME',
                  'GST_INCOME',
                  'BANK_STATEMENT_INCOME',
                  'AVERAGE_BANK_BALANCE',
                  'OBLIGATION_RATIO',
                  'KYC_QUALITY',
                  'LTV',
                  'PROPERTY_VALUE',
                ] as const
              ).map((k) => {
                const h = helpForParameter(k)
                return (
                  <div key={k} className="rounded border border-amber-100/80 bg-white/60 p-2">
                    <dt className="font-mono text-[11px] text-amber-950">{k}</dt>
                    <dd className="text-[11px] text-slate-600">{h.sources.join(' · ')}</dd>
                  </div>
                )
              })}
            </dl>
          </div>
        ) : null}
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="space-y-3">
          <div className="bt-section-card bt-section-card--default p-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <h3 className="bt-card-title">Manual credit inputs</h3>
                <p className="text-xs text-slate-500">
                  Expand to add or review manual underwriting inputs.
                </p>
              </div>
              <button
                type="button"
                onClick={() => setShowManualInputs((v) => !v)}
                className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-800"
                aria-expanded={showManualInputs}
              >
                {showManualInputs ? 'Hide manual inputs' : 'Add manual inputs'}
              </button>
            </div>
          </div>
          <div className={showManualInputs ? 'block' : 'hidden'}>
            <ManualCreditInputsSection
              applicationId={applicationId}
              app={app}
              onRefetch={onRefetch}
              bankStatementOnFile={bankStatementOnFile}
            />
          </div>
        </div>
        <div className="space-y-3">
          <div className="bt-section-card bt-section-card--default p-3 text-xs text-slate-800">
            <h3 className="bt-card-title">AI LOS Review</h3>
            <p className="mt-1 text-slate-600">
              AI underwriting summary and risk insights for this case.
            </p>
            <div className="mt-3 flex flex-wrap items-center gap-2">
              <button
                type="button"
                onClick={() => void onOpenAiReview()}
                disabled={aiLosLoading}
                className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {aiLosLoading ? 'Opening AI review…' : 'Open AI Review'}
              </button>
              {aiLosStatus ? <span className="text-[11px] text-emerald-700">{aiLosStatus}</span> : null}
            </div>
          </div>
          {assignInfo ? (
            <div className="bt-section-card bt-section-card--info p-3 text-xs text-slate-800">
              <span className="font-semibold text-blue-950">Assignment</span>: {assignInfo.ruleName ?? 'rule'} → role{' '}
              {assignInfo.assignedRole ?? '—'}
              {assignInfo.assignedUserId ? ` · user ${assignInfo.assignedUserId}` : ''}
              {assignInfo.assignedAt ? ` · ${assignInfo.assignedAt}` : ''}
            </div>
          ) : null}
          {latestEval && Array.isArray(latestEval.ruleResults) && latestEval.ruleResults.length > 0 ? (
            <div className="bt-section-card bt-section-card--default p-3 text-xs text-slate-800 bg-slate-50/60">
              <div className="font-semibold text-slate-900">Rule / engine rows (latest)</div>
              <ul className="mt-1 list-inside list-disc">
                {(latestEval.ruleResults as Record<string, unknown>[]).map((row, i) => (
                  <li key={i}>
                    {(row.ruleName as string) ?? row.ruleId}: {(row.policyDecision as string) ?? '—'} /{' '}
                    {(row.creditDecision as string) ?? '—'}
                    {row.kind ? ` [${String(row.kind)}]` : ''}
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
        </div>
      </div>
      {scorecardMap && Object.keys(scorecardMap).length > 0 ? (
        <div className="bt-section-card bt-section-card--success p-4 text-sm text-slate-800">
          <h3 className="text-sm font-semibold text-emerald-950">Scorecard parameters (effective)</h3>
          <dl className="mt-2 grid gap-1 sm:grid-cols-2">
            {Object.entries(scorecardMap).map(([k, v]) => (
              <div key={k}>
                <dt className="text-xs text-slate-500">{k.replace(/_/g, ' ').toLowerCase()}</dt>
                <dd className="font-mono text-slate-900">{v}</dd>
              </div>
            ))}
          </dl>
        </div>
      ) : null}
      <p className="text-sm text-slate-600">
        <span className="font-medium text-slate-800">Credit Manager setup</span> defines underwriting rules under
        &quot;Underwriting rules&quot; in the sidebar. This tab runs the policy engine or legacy scoring. Underwriting
        runs after <strong>KYC is passed</strong> and a <strong>credit bureau score</strong> is on file (automated pull
        or manual entry, where permitted).
      </p>
      {kycError ? <p className="text-sm text-amber-800">{kycError}</p> : null}
      {inKyc && kycOutcome && !kycPass ? (
        <p className="text-sm text-amber-800">KYC is not PASS yet. Complete KYC checks before bureau and underwriting.</p>
      ) : null}
      {actionError ? <ErrorState message={actionError} /> : null}

      <div className="bt-section-card bt-section-card--default grid gap-3 sm:grid-cols-2 p-4 text-sm">
        <div>
          <div className="text-xs font-medium text-slate-500">Requested amount</div>
          <div className="mt-0.5 font-medium text-slate-900">{formatMoney(app.requestedAmount)}</div>
        </div>
        <div>
          <div className="text-xs font-medium text-slate-500">Bureau score (automated)</div>
          <div className="mt-0.5 text-slate-900">{app.bureauScore != null ? String(app.bureauScore) : '—'}</div>
        </div>
        <div>
          <div className="text-xs font-medium text-slate-500">Bureau score (manual)</div>
          <div className="mt-0.5 text-slate-900">
            {app.manualBureauScore != null ? String(app.manualBureauScore) : '—'}
          </div>
        </div>
        <div>
          <div className="text-xs font-medium text-slate-500">KYC outcome</div>
          <div className="mt-0.5 font-mono text-slate-900">{kycOutcome ? outcomeStr || '—' : '—'}</div>
        </div>
      </div>

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

      {inKyc && kycPass && hasBureau ? (
        <div>
          <button
            type="button"
            onClick={() => void onStartUnderwriting()}
            disabled={underwriteLoading || !canStartUw}
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50"
          >
            {underwriteLoading ? 'Running…' : 'Start underwriting (credit decision)'}
          </button>
        </div>
      ) : null}

      {uwMeta && (
        <div className="bt-section-card bt-section-card--violet p-4 text-sm text-slate-800">
          <h3 className="text-sm font-semibold text-indigo-950">Last underwriting policy snapshot</h3>
          <dl className="mt-2 grid gap-2 sm:grid-cols-2">
            <div>
              <dt className="text-xs text-slate-500">Source</dt>
              <dd>{uwMeta.source ?? '—'}</dd>
            </div>
            <div>
              <dt className="text-xs text-slate-500">Matched rule set</dt>
              <dd>{uwMeta.ruleSetName ?? uwMeta.ruleSetId ?? '—'}</dd>
            </div>
            <div>
              <dt className="text-xs text-slate-500">Requested amount</dt>
              <dd>{formatMoney(app.requestedAmount)}</dd>
            </div>
            <div>
              <dt className="text-xs text-slate-500">Bureau score (used)</dt>
              <dd>{effectiveBureau != null ? String(effectiveBureau) : '—'}</dd>
            </div>
            <div>
              <dt className="text-xs text-slate-500">Recommendation</dt>
              <dd className="font-mono text-xs">{uwMeta.recommendation ?? '—'}</dd>
            </div>
            <div className="sm:col-span-2">
              <dt className="text-xs text-slate-500">Reasons</dt>
              <dd className="whitespace-pre-wrap text-xs">
                {Array.isArray(uwMeta.reasons) ? (uwMeta.reasons as string[]).join('\n') : String(uwMeta.reasons ?? '—')}
              </dd>
            </div>
          </dl>
        </div>
      )}

      {pendingManualReview && (
        <div className="bt-section-card bt-section-card--warning p-4">
          <h3 className="text-sm font-semibold text-amber-950">Manual review required</h3>
          <p className="mb-3 text-sm text-amber-900">
            Policy sent this case to <strong>MANUAL_REVIEW</strong>. Approve or reject as Credit Manager.
          </p>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              disabled={manualResolveLoading}
              onClick={() => void onApproveManual()}
              className="rounded-md border border-emerald-700 bg-emerald-800 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
            >
              {manualResolveLoading ? '…' : 'Approve'}
            </button>
            <button
              type="button"
              disabled={manualResolveLoading}
              onClick={() => void onRejectManual()}
              className="rounded-md border border-rose-600 bg-rose-700 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
            >
              {manualResolveLoading ? '…' : 'Reject'}
            </button>
          </div>
        </div>
      )}

      {decisionDone && (
        <div className="bt-section-card bt-section-card--default p-4">
          <h3 className="bt-card-title">Credit decision</h3>
          <dl className="mt-2 grid gap-2 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-xs text-slate-500">Status</dt>
              <dd className="font-medium text-slate-900">{app.status}</dd>
            </div>
            <div>
              <dt className="text-xs text-slate-500">Decision</dt>
              <dd className="font-medium text-slate-900">{app.creditDecision ?? '—'}</dd>
            </div>
            <div>
              <dt className="text-xs text-slate-500">Risk score</dt>
              <dd className="text-slate-800">{app.creditRiskScore != null ? String(app.creditRiskScore) : '—'}</dd>
            </div>
            <div>
              <dt className="text-xs text-slate-500">Sanctioned amount</dt>
              <dd className="text-slate-800">{formatMoney(app.sanctionedAmount)}</dd>
            </div>
            <div>
              <dt className="text-xs text-slate-500">Approved rate (if any)</dt>
              <dd className="text-slate-800">{app.approvedRate != null ? String(app.approvedRate) : '—'}</dd>
            </div>
          </dl>
        </div>
      )}
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
