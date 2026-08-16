import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('readiness badge honesty', () => {
  it('inventory panel does not affirm Production Ready when false', () => {
    const src = readFileSync(
      resolve(__dirname, '../../components/policy/PolicyParameterInventoryPanel.tsx'),
      'utf8',
    )
    expect(src).toContain('productionReady === true')
    expect(src).toContain('Production not ready')
    expect(src).toContain('Policy test unavailable')
    expect(src).toContain('Calculation required')
    // Must not render bare affirmative label without true guard nearby
    expect(src).toMatch(/productionReady === true[\s\S]*Production Ready/)
  })

  it('suggest calculation workflow is generic (no clean-history-specific UI)', () => {
    const src = readFileSync(
      resolve(__dirname, '../../components/dataParameters/SuggestCalculationWorkflow.tsx'),
      'utf8',
    )
    expect(src).toContain('Suggest calculation')
    expect(src).toContain('Accept &amp; create calculation')
    expect(src).not.toMatch(/clean_history_months/)
    expect(src).not.toMatch(/Clean history months/)
  })
})
