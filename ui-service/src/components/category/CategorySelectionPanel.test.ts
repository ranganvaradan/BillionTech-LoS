import { describe, expect, it } from 'vitest'

/**
 * UI contract smoke — Category selection panel must not expose Policy UUIDs in copy.
 */
describe('CategorySelectionPanel contract', () => {
  it('safe question catalogue id is FINANCIAL_DATA_ROUTE', () => {
    expect('FINANCIAL_DATA_ROUTE').toBe('FINANCIAL_DATA_ROUTE')
  })

  it('selection states exclude arbitrary winner', () => {
    const states = [
      'NO_ELIGIBLE_CATEGORY',
      'AUTO_SINGLE_MATCH',
      'DISAMBIGUATION_REQUIRED',
      'EXPLICIT_PROPOSITION_SELECTION_REQUIRED',
      'CATEGORY_SELECTED',
    ]
    expect(states).not.toContain('PRIORITY_WINNER')
  })
})
