import type { ScorecardParameterDef, ScorecardRow, UnderwritingScorecardResponse } from '@/api/scorecards'

export type ScorecardMatchInput = {
  borrowerType: string
  loanProduct: string
  requestedAmount: number | null | undefined
  personalInfo?: Record<string, unknown> | null
}

function amountInRange(
  amount: number | null | undefined,
  min: number | null,
  max: number | null,
): boolean {
  if (amount == null) return false
  if (min != null && amount < min) return false
  if (max != null && amount > max) return false
  return true
}

function matchesGeography(
  filter: Record<string, unknown> | null | undefined,
  personalInfo?: Record<string, unknown> | null,
): boolean {
  if (!filter || Object.keys(filter).length === 0) return true
  const pi = personalInfo ?? {}
  if (filter.state != null && String(filter.state).trim()) {
    const have = String(pi.state ?? '').trim()
    if (!have || have.toLowerCase() !== String(filter.state).trim().toLowerCase()) {
      return false
    }
  }
  if (filter.city != null && String(filter.city).trim()) {
    const have = String(pi.city ?? '').trim()
    if (!have || have.toLowerCase() !== String(filter.city).trim().toLowerCase()) {
      return false
    }
  }
  return true
}

/** Mirrors backend scorecard scope matching for pre-run underwriting input collection. */
export function matchScorecardForApplication(
  scorecards: UnderwritingScorecardResponse[],
  app: ScorecardMatchInput,
): UnderwritingScorecardResponse | null {
  const candidates = scorecards
    .filter((s) => s.active)
    .filter((s) => s.borrowerType === app.borrowerType && s.loanProduct === app.loanProduct)
    .filter((s) => amountInRange(app.requestedAmount, s.minAmount, s.maxAmount))
    .filter((s) => matchesGeography(s.geography, app.personalInfo))
    .sort((a, b) => b.priority - a.priority)
  return candidates[0] ?? null
}

export function scorecardRowsFromJson(scorecard: UnderwritingScorecardResponse | null): ScorecardRow[] {
  if (!scorecard?.scorecardJson) return []
  const raw = scorecard.scorecardJson.rows
  if (!Array.isArray(raw)) return []
  return raw.filter((r): r is ScorecardRow => Boolean(r && typeof r === 'object' && 'parameter' in r))
}

export function scorecardParameterDefsFromJson(
  scorecard: UnderwritingScorecardResponse | null,
): Record<string, ScorecardParameterDef> {
  if (!scorecard?.scorecardJson) return {}
  const raw = scorecard.scorecardJson.parameterDefs
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return {}
  return raw as Record<string, ScorecardParameterDef>
}
