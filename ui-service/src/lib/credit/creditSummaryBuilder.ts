import { requiresCollateral } from '@/lib/intake/securedProducts'
import type { ApplicationResponse } from '@/types/application'
import { mergeDemoIncomeSummaryLines } from '@/lib/credit/demoBankIncomeDisplay'
import { getVisibleUnderwritingFields, normalizeBorrowerType } from '@/lib/credit/underwritingFieldVisibility'

const BORROWER_INTAKE = 'borrowerIntake' as const

function str(v: unknown): string | null {
  if (v == null) return null
  const s = String(v).trim()
  return s === '' ? null : s
}

function pickEffective(view: Record<string, unknown> | null | undefined): Record<string, unknown> | null {
  const o = view?.effective
  if (o && typeof o === 'object' && o !== null && !Array.isArray(o)) {
    return o as Record<string, unknown>
  }
  return null
}

export type RiskMitigantRow = { text: string; severity: 'Low' | 'Medium' | 'High' }

export type CreditSummaryBuilt = {
  borrowerSnapshot: Record<string, string>
  kycSummary: Record<string, string>
  bureauSummary: Record<string, string>
  incomeSummary: Record<string, string>
  collateralSummary: Record<string, string> | null
  scorecardSummary: {
    name: string
    version: string | null
    aggregateScore: string
    policyOutcome: string
  } | null
  riskFlags: Array<{ code: string; label: string; severity: string }>
  missingItems: string[]
  completenessPercent: number
  decisionSource: string
}

/**
 * Transforms live application, credit-control, underwriting evaluation, and KYC data into presentation maps
 * with business labels (not raw API keys) for the underwriting workbench and CAM.
 */
export function buildCreditSummary(input: {
  app: ApplicationResponse
  creditControlView: Record<string, unknown> | null | undefined
  latestUnderwritingEvaluation: Record<string, unknown> | null | undefined
  kycOutcome: Record<string, unknown> | null
  /** When true, bank statement document exists on file (from documents API). */
  bankStatementOnFile?: boolean
}): CreditSummaryBuilt {
  const { app, creditControlView, latestUnderwritingEvaluation, kycOutcome, bankStatementOnFile = false } = input
  const uwVis = getVisibleUnderwritingFields(app.borrowerType)
  const pi = (app.personalInfo ?? null) as Record<string, unknown> | null
  const bi = (app.businessInfo ?? null) as Record<string, unknown> | null
  const name =
    str(pi?.fullName) ||
    [str(pi?.firstName), str(pi?.lastName)].filter(Boolean).join(' ').trim() ||
    str(pi?.name)

  const eff = pickEffective(creditControlView)
  const scorecard = eff?.scorecard
  const scoreMap =
    scorecard && typeof scorecard === 'object' && !Array.isArray(scorecard)
      ? (scorecard as Record<string, string>)
      : null

  const bureauUsed =
    app.manualBureauScore != null && app.manualBureauScore > 0
      ? app.manualBureauScore
      : app.bureauScore != null && app.bureauScore > 0
        ? app.bureauScore
        : null

  const kycEx = kycOutcome?.exceptions
  const exList = Array.isArray(kycEx) ? kycEx.map((x) => String(x)) : []

  const ev = latestUnderwritingEvaluation
  const scoreName = str(ev?.scorecardName) ?? '—'
  const scoreVersion = str(ev?.scorecardVersion) ?? null
  const agg = ev?.aggregateScore
  const aggS = agg != null ? String(agg) : '—'
  const pol = str(ev?.aggregateDecision) ?? '—'

  const fi = (app.financialInfo ?? null) as Record<string, unknown> | null
  const cc = fi?.creditControl
  const manual =
    cc && typeof cc === 'object' && (cc as Record<string, unknown>).manual
      ? ((cc as Record<string, unknown>).manual as Record<string, unknown>)
      : null
  const incomeSummary: Record<string, string> = {}
  if (str(pi?.monthlyNetIncome)) {
    incomeSummary['Declared monthly income'] = str(pi?.monthlyNetIncome) as string
  }
  if (manual) {
    for (const k of ['monthlyIncome', 'gstIncome', 'bankStatementIncome', 'averageBankBalance', 'obligationRatio'] as const) {
      if (!uwVis.showGstIncome && k === 'gstIncome') {
        continue
      }
      const cell = manual[k]
      const val =
        cell && typeof cell === 'object' && (cell as Record<string, unknown>).value != null
          ? String((cell as Record<string, unknown>).value)
          : null
      if (val) {
        const label =
          k === 'monthlyIncome'
            ? 'Verified monthly income (layer)'
            : k === 'gstIncome'
              ? 'GST-based income (layer)'
              : k === 'bankStatementIncome'
                ? 'Bank statement income (layer)'
                : k === 'averageBankBalance'
                  ? 'Average bank balance (layer)'
                  : 'Obligation / ratio (layer)'
        incomeSummary[label] = val
      }
    }
  }
  if (scoreMap) {
    for (const [k, v] of Object.entries(scoreMap)) {
      const upper = k.toUpperCase()
      if (!uwVis.showGstIncome && upper === 'GST_INCOME') {
        continue
      }
      if (!incomeSummary[k]) {
        incomeSummary[`${k.replace(/_/g, ' ')} (effective policy)`] = v
      }
    }
  }
  mergeDemoIncomeSummaryLines(incomeSummary, app, bankStatementOnFile)
  if (Object.keys(incomeSummary).length === 0) {
    incomeSummary['Note'] = 'No income or ratio lines populated yet. Use manual credit inputs and documents as required.'
  }

  let collateralSummary: Record<string, string> | null = null
  if (requiresCollateral(app.loanProduct)) {
    const ci = (app.collateralInfo ?? null) as Record<string, unknown> | null
    const bint = ci?.[BORROWER_INTAKE]
    if (bint && typeof bint === 'object') {
      const c = bint as Record<string, unknown>
      collateralSummary = {
        'Collateral type': str(c.collateralType)?.replaceAll('_', ' ') ?? '—',
        'Estimated value (INR)': str(c.estimatedValue) ?? '—',
        Notes: str(c.notes) ?? '—',
      }
    } else {
      collateralSummary = { 'Collateral on file': 'Not captured in intake for this product yet.' }
    }
  }

  const borrowerSnapshot: Record<string, string> = {
    'Borrower type': normalizeBorrowerType(app.borrowerType).replaceAll('_', ' '),
    'Primary name': name ?? '—',
    'Mobile': str(pi?.mobile) ?? str(pi?.phoneNumber) ?? '—',
    'Email': str(pi?.email) ?? '—',
    'City / state': [str(pi?.city), str(pi?.state)].filter(Boolean).join(' · ') || '—',
    'Employment / business': [str(pi?.employmentType), str(pi?.occupationIndustry), str(pi?.employerName)]
      .filter(Boolean)
      .join(' · ') || '—',
    'Turnover (if captured)': str(bi?.annualTurnover) ?? '—',
  }

  const kycSummary: Record<string, string> = {
    'KYC outcome': str(kycOutcome?.outcome) ?? '—',
    'PAN (status / flag)': str(pi?.panVerified) ?? '—',
    'Aadhaar (status / flag)': str(pi?.aadhaarVerified) ?? '—',
    'Open exceptions / messages': exList.length ? exList.join(' · ') : 'None listed',
  }
  if (uwVis.showGstinKycSummary) {
    kycSummary['GSTIN (application)'] = str(bi?.gstin) ?? '—'
  }
  if (uwVis.showUdyamKycSummary) {
    kycSummary['Udyam (application)'] = str(bi?.udyamNumber ?? bi?.udyam) ?? '—'
  }

  const bureauSummary: Record<string, string> = {
    'Bureau score (in use)': bureauUsed != null ? String(bureauUsed) : '—',
    'Provider automated score (on file)': app.bureauScore != null ? String(app.bureauScore) : '—',
    'Manual bureau score (override)': app.manualBureauScore != null ? String(app.manualBureauScore) : '—',
    'Data source (effective)': str(eff?.bureauScoreSource) ?? '—',
  }

  const uwMeta = fi?.underwritingMeta as
    | { source?: string; ruleSetName?: string; recommendation?: string }
    | undefined
  const decisionSource = [uwMeta?.source, uwMeta?.ruleSetName, ev != null ? 'Latest underwriting run' : '']
    .filter(Boolean)
    .join(' · ') || '—'

  const riskFlags: Array<{ code: string; label: string; severity: string }> = []
  if (bureauUsed != null && bureauUsed < 600) {
    riskFlags.push({
      code: 'LOW_BUREAU',
      label: 'Bureau score is in a sub-prime band — price and policy review recommended.',
      severity: 'Medium',
    })
  }
  const kycOut = str(kycOutcome?.outcome)
  if (kycOut && kycOut.toUpperCase() !== 'PASS' && kycOut.toUpperCase() !== 'UNAVAILABLE') {
    riskFlags.push({
      code: 'KYC',
      label: `KYC outcome is not a clean pass (${kycOut}).`,
      severity: 'High',
    })
  }
  const params = ev?.parameterResults
  if (Array.isArray(params)) {
    for (const p of params as Array<Record<string, unknown>>) {
      if (p?.matched === false && (p.valueUsed == null || p.valueUsed === '')) {
        const par = str(p.parameter) ?? 'parameter'
        riskFlags.push({
          code: 'MISSING_SCORE_PARAM',
          label: `Scorecard is missing a value for ${par} — add source input or link evidence.`,
          severity: 'Medium',
        })
      }
    }
  }
  if (requiresCollateral(app.loanProduct) && !collateralSummary?.['Estimated value (INR)']?.match(/\d/)) {
    riskFlags.push({
      code: 'COLLATERAL',
      label: 'Secured product without a clear declared value in intake.',
      severity: 'Medium',
    })
  }
  if (riskFlags.length === 0) {
    riskFlags.push({
      code: 'REVIEW',
      label: 'No system blockers at this snapshot — complete normal credit review.',
      severity: 'Low',
    })
  }

  const missingItems: string[] = []
  if (!name) missingItems.push('Complete borrower / business name on profile')
  if (bureauUsed == null || bureauUsed <= 0) missingItems.push('Bureau score (pull or permitted manual entry)')
  if (kycOut == null || kycOut === 'UNAVAILABLE' || kycOut === 'INCOMPLETE') {
    missingItems.push('Acceptable KYC pass outcome and checks')
  }
  if (Array.isArray(params)) {
    for (const p of params as Array<Record<string, unknown>>) {
      if (p?.matched === false && (p.valueUsed == null || p.valueUsed === '')) {
        const par = str(p.parameter)
        if (par) missingItems.push(`Input for score parameter: ${par}`)
      }
    }
  }

  let filled = 0
  const total = 8
  if (name) filled++
  if (bureauUsed != null && bureauUsed > 0) filled++
  if (kycOut && kycOut !== 'UNAVAILABLE') filled++
  if (str(pi?.panVerified)) filled++
  if (app.requestedAmount != null && app.requestedAmount > 0) filled++
  if (str(pi?.monthlyNetIncome)) filled++
  if (ev != null) filled++
  if (Array.isArray(params) && params.length > 0) filled++
  const completenessPercent = Math.round((100 * filled) / total)

  return {
    borrowerSnapshot,
    kycSummary,
    bureauSummary,
    incomeSummary,
    collateralSummary,
    scorecardSummary:
      ev != null
        ? {
            name: scoreName,
            version: scoreVersion,
            aggregateScore: aggS,
            policyOutcome: pol,
          }
        : null,
    riskFlags,
    missingItems,
    completenessPercent,
    decisionSource,
  }
}
