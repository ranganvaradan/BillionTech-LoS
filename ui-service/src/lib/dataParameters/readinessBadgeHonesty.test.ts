import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  lenderPrimaryStatus,
  lenderSupportLabel,
  sanitizeLenderTechnicalPhrase,
} from '@/lib/policyStudio/lenderUxCopy'

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
    expect(src).toContain('Work it out for me')
    expect(src).toContain('Use this calculation')
    expect(src).not.toMatch(/clean_history_months/)
    expect(src).not.toMatch(/Clean history months/)
  })
})

describe('POLICY-STUDIO-LENDER-UX-SIMPLIFICATION-1', () => {
  it('maps calculation-required to Needs your input', () => {
    const s = lenderPrimaryStatus({ calculationRequired: true, policyTestReady: false })
    expect(s.label).toBe('Needs your input')
    expect(lenderSupportLabel('CALCULATION_NOT_IMPLEMENTED')).toBe('Needs your input')
  })

  it('sanitizes technical jargon from lender copy', () => {
    const out = sanitizeLenderTechnicalPhrase(
      'GACAT REF RAW DERIVED NEEDS_INPUT CALCULATION_NOT_IMPLEMENTED typed expression',
    )
    expect(out).not.toMatch(/\bGACAT\b/)
    expect(out).not.toMatch(/\bREF\b/)
    expect(out).not.toMatch(/\bRAW\b/)
    expect(out).not.toMatch(/\bDERIVED\b/)
    expect(out).not.toMatch(/NEEDS_INPUT/)
    expect(out).not.toMatch(/CALCULATION_NOT_IMPLEMENTED/)
    expect(out).not.toMatch(/typed expression/i)
  })

  it('lender workflow hides typed expression / GACAT from default Layer-1 copy', () => {
    const src = readFileSync(
      resolve(__dirname, '../../components/dataParameters/SuggestCalculationWorkflow.tsx'),
      'utf8',
    )
    // Default CTA / confirmation are business language
    expect(src).toContain('Needs your input')
    expect(src).toContain('Work it out for me')
    expect(src).toContain('Use this calculation')
    expect(src).toContain('Here is how I propose to calculate it')
    expect(src).toContain('Information available')
    // Technical expression editing is under Advanced only
    expect(src).toContain('Advanced details')
    expect(src).toContain('data-lender-ux="layer-1"')
    // Candidates must not be labelled Dependencies in Layer-1
    const layer1Block = src.slice(0, src.indexOf('Advanced details'))
    expect(layer1Block).not.toMatch(/>\s*Dependencies\s*</)
    expect(layer1Block).not.toContain('exact GACAT')
    expect(layer1Block).not.toContain('Suggest calculation')
    expect(layer1Block).not.toContain('Accept &amp; create calculation')
  })

  it('inventory default title drops GACAT jargon', () => {
    const src = readFileSync(
      resolve(__dirname, '../../components/policy/PolicyParameterInventoryPanel.tsx'),
      'utf8',
    )
    expect(src).toContain('Parameters used by this policy')
    expect(src).not.toContain('Policy parameter inventory (GACAT)')
    expect(src).toContain('Complete setup')
    expect(src).toContain('Advanced details')
  })
})
