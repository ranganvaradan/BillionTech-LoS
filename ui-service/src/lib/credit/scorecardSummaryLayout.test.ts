import { describe, expect, it } from 'vitest'
import { buildScorecardCategoryGroups } from '@/lib/credit/scorecardSummaryLayout'

describe('buildScorecardCategoryGroups', () => {
  it('groups parameter rows by category and sums category scores', () => {
    const groups = buildScorecardCategoryGroups([
      { parameter: 'BUREAU_SCORE', weight: 50, pointsEarned: 35, maxScore: 35, matched: true },
      { parameter: 'MONTHLY_INCOME', weight: 25, pointsEarned: 25, maxScore: 25, matched: true },
      { parameter: 'OBLIGATION_RATIO', weight: 20, pointsEarned: 20, maxScore: 20, matched: true },
    ])
    expect(groups.map((g) => g.category)).toEqual(['Credit History', 'Financial Strength'])
    expect(groups[0]?.categoryScore).toBe(35)
    expect(groups[1]?.categoryScore).toBe(45)
    expect(groups[0]?.rows[0]?.status).toBe('pass')
  })

  it('marks missing parameter rows', () => {
    const groups = buildScorecardCategoryGroups([
      { parameter: 'residenceOwned', matched: false, valueUsed: '', maxScore: 10, pointsEarned: 0 },
    ])
    expect(groups[0]?.rows[0]?.status).toBe('missing')
    expect(groups[0]?.rows[0]?.statusLabel).toBe('Missing input')
  })
})
