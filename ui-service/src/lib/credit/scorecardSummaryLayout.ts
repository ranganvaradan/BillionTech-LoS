import { helpForParameter } from '@/lib/scorecardParameterSources'
import { describeCondition } from '@/lib/credit/scorecardCondition'
import type { DependencyOutcome, FormulaBreakdown } from '@/components/credit/ParameterDerivationDetails'

export type ScorecardParameterRow = {
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
  formulaBreakdown?: FormulaBreakdown | null
  dependencyOutcome?: DependencyOutcome | null
  skippedDueToDependency?: boolean
}

export type ScorecardCategoryGroup = {
  category: string
  categoryScore: number
  rows: Array<{
    criterion: string
    description: string
    weightPercent: number
    score: number
    status: 'pass' | 'fail' | 'missing' | 'no_match'
    statusLabel: string
    valueUsed: string
    valueSource: string
    pointsEarned: number | null
    maxScore: number | null
    row: ScorecardParameterRow
  }>
}

const CATEGORY_BY_PARAMETER: Record<string, string> = {
  BUREAU_SCORE: 'Credit History',
  REPAYMENT_HISTORY: 'Credit History',
  MONTHLY_INCOME: 'Financial Strength',
  MONTHLY_OBLIGATION: 'Financial Strength',
  DTI_RATIO: 'Financial Strength',
  GST_INCOME: 'Financial Strength',
  BANK_STATEMENT_INCOME: 'Financial Strength',
  AVERAGE_BANK_BALANCE: 'Financial Strength',
  avgDailyBalance3m: 'Financial Strength',
  avgMonthlyTransactions3m: 'Financial Strength',
  avgMonthlySettlements3m: 'Financial Strength',
  monthlyTransactions3m: 'Financial Strength',
  inwardChequeReturns3m: 'Financial Strength',
  avgDailySettlements3m: 'Financial Strength',
  noOfTxns60days: 'Financial Strength',
  txnMth1: 'Financial Strength',
  txnMth2: 'Financial Strength',
  txnMth3: 'Financial Strength',
  ANNUAL_BANKING_TURNOVER: 'Financial Strength',
  OBLIGATION_RATIO: 'Financial Strength',
  EMI_OBLIGATION: 'Financial Strength',
  EBITDA_PROXY: 'Financial Strength',
  EBITDA: 'Financial Strength',
  DEBT_SERVICE: 'Financial Strength',
  PAT: 'Financial Strength',
  TOL: 'Financial Strength',
  TNW: 'Financial Strength',
  TOL_TNW: 'Financial Strength',
  INTEREST_COVERAGE: 'Financial Strength',
  DEBT_TO_EQUITY: 'Financial Strength',
  LEVERAGE_RATIO: 'Financial Strength',
  KYC_QUALITY: 'Borrower Profile',
  KYC_PASS: 'Borrower Profile',
  BUSINESS_VINTAGE_MONTHS: 'Borrower Profile',
  INDUSTRY_RISK: 'Borrower Profile',
  residenceOwned: 'Borrower Profile',
  residenceStability: 'Borrower Profile',
  businessStability: 'Borrower Profile',
  existingLoanTrackRecordAll: 'Borrower Profile',
  existingLoanTrackRecord15d: 'Borrower Profile',
  qrTxnEDI: 'Borrower Profile',
  eligibleOnePointFiveX: 'Borrower Profile',
  PROPERTY_VALUE: 'Transaction Risk',
  LTV: 'Transaction Risk',
  REQUESTED_AMOUNT: 'Transaction Risk',
  TENURE_MONTHS: 'Transaction Risk',
}

const CATEGORY_ORDER = [
  'Credit History',
  'Financial Strength',
  'Borrower Profile',
  'Transaction Risk',
  'Other',
] as const

function categoryForParameter(parameter: string | undefined): string {
  if (!parameter) return 'Other'
  return CATEGORY_BY_PARAMETER[parameter] ?? CATEGORY_BY_PARAMETER[parameter.toUpperCase()] ?? 'Other'
}

function rowStatus(row: ScorecardParameterRow): {
  status: 'pass' | 'fail' | 'missing' | 'no_match'
  statusLabel: string
} {
  if (row.skippedDueToDependency) {
    return { status: 'no_match', statusLabel: 'Not considered' }
  }
  const missing = (row.valueUsed == null || row.valueUsed === '') && row.matched === false
  if (row.hardRule && row.matched) {
    return { status: 'fail', statusLabel: 'Hard rule failed' }
  }
  if (missing) {
    return { status: 'missing', statusLabel: 'Missing input' }
  }
  if (row.matched) {
    return { status: 'pass', statusLabel: 'Passed' }
  }
  if (row.valueUsed != null && row.valueUsed !== '') {
    return { status: 'fail', statusLabel: 'Did not meet condition' }
  }
  return { status: 'no_match', statusLabel: 'No match' }
}

function scoringImpact(row: ScorecardParameterRow): string {
  if (row.skippedDueToDependency) {
    return 'Skipped — dependency conditions were not satisfied'
  }
  if (row.hardRule) {
    const reason = row.reason ? `${row.reason}. ` : ''
    if (row.matched) {
      return `${reason}Hard rule condition ${row.condition ?? '—'} was triggered by value ${row.valueUsed ?? '—'}`
    }
    return `${reason}Hard rule condition ${row.condition ?? '—'} was not triggered`
  }
  const earned = row.pointsEarned ?? 0
  const max = row.maxScore ?? 0
  if (max <= 0) return 'No points configured'
  if (row.matched) {
    return `Earned ${earned} of ${max} points`
  }
  if ((row.valueUsed == null || row.valueUsed === '') && row.matched === false) {
    return `0 of ${max} points — input required`
  }
  return `Earned ${earned} of ${max} points — condition not met`
}

function rowDisplayScore(row: ScorecardParameterRow): number {
  const earned = row.pointsEarned ?? 0
  const max = row.maxScore ?? 0
  if (max > 0) {
    return Math.round((earned / max) * 1000) / 10
  }
  return earned
}

function criterionLabel(row: ScorecardParameterRow): string {
  const param = row.parameter ?? '—'
  const help = helpForParameter(param)
  if (row.condition) {
    return `${help.label} (${describeCondition(row.condition)})`
  }
  return help.label
}

function criterionDescription(row: ScorecardParameterRow): string {
  const help = helpForParameter(row.parameter)
  const impact = scoringImpact(row)
  if (help.sources.length > 0) {
    return `${help.sources[0]} · ${impact}`
  }
  const base = row.source ? `Sourced via ${row.source.replace(/_/g, ' ')}` : '—'
  return `${base} · ${impact}`
}

/** Groups scorecard parameter rows into LOS-style categories for underwriting summary display. */
export function buildScorecardCategoryGroups(
  parameterResults: ScorecardParameterRow[] | undefined | null,
): ScorecardCategoryGroup[] {
  if (!parameterResults?.length) return []

  const buckets = new Map<string, ScorecardCategoryGroup['rows']>()
  for (const row of parameterResults) {
    const category = categoryForParameter(row.parameter)
    const list = buckets.get(category) ?? []
    const statusInfo = rowStatus(row)
    list.push({
      criterion: criterionLabel(row),
      description: criterionDescription(row),
      weightPercent: row.weight ?? 0,
      score: rowDisplayScore(row),
      status: statusInfo.status,
      statusLabel: statusInfo.statusLabel,
      valueUsed: row.valueUsed ?? '—',
      valueSource: row.valueSource ?? '—',
      pointsEarned: row.pointsEarned ?? null,
      maxScore: row.maxScore ?? null,
      row,
    })
    buckets.set(category, list)
  }

  return CATEGORY_ORDER.filter((c) => buckets.has(c)).map((category) => {
    const rows = buckets.get(category) ?? []
    const categoryScore = rows.reduce((sum, r) => sum + (r.row.pointsEarned ?? 0), 0)
    return { category, categoryScore, rows }
  })
}

export function aggregateScorecardPoints(parameterResults: ScorecardParameterRow[] | undefined | null): number {
  if (!parameterResults?.length) return 0
  return parameterResults.reduce((sum, r) => sum + (r.pointsEarned ?? 0), 0)
}
