import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  matchesDp1Filters,
  overallReadinessLabel,
  parameterSupportLabel,
  platformIntegrationLabel,
  sourceTypeLabel,
} from './dp1Display'

describe('dp1Display capability semantics', () => {
  it('labels legacy readiness and new capability statuses', () => {
    expect(overallReadinessLabel('RUNTIME_READY_NONPROD')).toBe('Runtime Ready — Non-Production')
    expect(platformIntegrationLabel('PRODUCTION_READY')).toBe('Production Ready')
    expect(platformIntegrationLabel('NOT_INTEGRATED')).toBe('Not Integrated')
    expect(parameterSupportLabel('SUPPORTED_DERIVED')).toBe('Supported — Derived')
    expect(parameterSupportLabel('CALCULATION_NOT_IMPLEMENTED')).toBe('Calculation Not Implemented')
    expect(sourceTypeLabel('APPLICATION_INPUT')).toBe('Application input')
  })

  it('filters by parameterSupport and source family', () => {
    const p = {
      id: 'bureau.accounts.cc_writeoff',
      sourceFamily: 'Bureau Retail',
      sourceType: 'PROVIDER',
      overallReadiness: 'RUNTIME_READY_NONPROD',
      productionReady: false,
      capability: {
        parameterSupport: { status: 'SUPPORTED_DERIVED' },
        platformIntegration: { status: 'PRODUCTION_READY' },
      },
    }
    expect(
      matchesDp1Filters(p, {
        sourceFamily: 'Bureau Retail',
        sourceType: '',
        overallReadiness: '',
        productionReady: '',
        parameterSupport: 'SUPPORTED_DERIVED',
        q: '',
      }),
    ).toBe(true)
    expect(
      matchesDp1Filters(p, {
        sourceFamily: '',
        sourceType: '',
        overallReadiness: '',
        productionReady: '',
        parameterSupport: 'SUPPORTED_RAW',
        q: '',
      }),
    ).toBe(false)
  })

  it('DataParametersPage wires capability UX and advanced technical details', () => {
    const page = readFileSync(resolve(__dirname, '../../pages/DataParametersPage.tsx'), 'utf8')
    expect(page).toContain('dp-lender-capability')
    expect(page).toContain('dp-source-capability-summary')
    expect(page).toContain('dp-advanced-technical')
    expect(page).toContain('Platform Integration')
    expect(page).toContain('Your Organisation')
    expect(page).toContain('Available for Production Policy Use')
    expect(page).toContain('getDataParametersDetail')
    // Primary readiness facts moved under Advanced
    expect(page).toContain('Provider Bound')
    expect(page.indexOf('dp-lender-capability')).toBeLessThan(page.indexOf('dp-advanced-technical'))
  })
})
