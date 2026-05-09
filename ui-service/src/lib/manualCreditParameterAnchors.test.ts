import { describe, expect, it } from 'vitest'
import { manualCreditHashForScorecardParameter } from './manualCreditParameterAnchors'

describe('manualCreditHashForScorecardParameter', () => {
  it('returns row hash for known parameters', () => {
    expect(manualCreditHashForScorecardParameter('BUREAU_SCORE')).toBe('#manual-credit-bureau-score')
    expect(manualCreditHashForScorecardParameter('MONTHLY_INCOME')).toBe('#manual-credit-monthly-income')
  })
  it('normalizes spaces and hyphens to match scorecard keys', () => {
    expect(manualCreditHashForScorecardParameter('monthly income')).toBe('#manual-credit-monthly-income')
    expect(manualCreditHashForScorecardParameter('BANK STATEMENT INCOME')).toBe('#manual-credit-bank-statement-income')
  })
  it('falls back to section top for unknown', () => {
    expect(manualCreditHashForScorecardParameter('UNKNOWN_V2_METRIC')).toBe('#manual-credit-inputs')
  })
})
