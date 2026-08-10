import type { UnderwritingRuleSetResponse } from '@/api/underwritingRules'
import type { ScorecardRow } from '@/api/scorecards'

export type RuleMatchInput = {
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
  if (amount == null) return true
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

/** Active rule sets matching application scope (mirrors backend multi-rule selection). */
export function matchUnderwritingRulesForApplication(
  rules: UnderwritingRuleSetResponse[],
  app: RuleMatchInput,
): UnderwritingRuleSetResponse[] {
  return rules
    .filter((r) => r.active)
    .filter((r) => r.borrowerType === app.borrowerType && r.loanProduct === app.loanProduct)
    .filter((r) => amountInRange(app.requestedAmount, r.minAmount, r.maxAmount))
    .filter((r) => matchesGeography(r.geography, app.personalInfo))
    .sort((a, b) => b.priority - a.priority)
}

/** Synthetic scorecard-like rows from rule hardRules for OTHER/manual pre-run collection. */
export function hardRuleRowsAsScorecardRows(rules: UnderwritingRuleSetResponse[]): ScorecardRow[] {
  const out: ScorecardRow[] = []
  for (const rule of rules) {
    const hard = (rule.rulesJson as { hardRules?: unknown })?.hardRules
    if (!Array.isArray(hard)) continue
    for (const raw of hard) {
      if (!raw || typeof raw !== 'object') continue
      const row = raw as Record<string, unknown>
      const parameter = String(row.parameter ?? '').trim()
      const source = String(row.source ?? 'OTHER').trim() || 'OTHER'
      if (!parameter) continue
      out.push({
        id: String(row.id ?? `${rule.id}-${parameter}`),
        parameter,
        source,
        condition: String(row.condition ?? ''),
        weight: 0,
        score: 0,
      })
    }
  }
  return out
}
