import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  matchesDp1Filters,
  overallReadinessLabel,
  parameterSupportBusinessLabel,
  platformIntegrationLabel,
  sourceTypeLabel,
} from './dp1Display'

describe('dp1Display lender UX cleanup-2', () => {
  it('uses business language for support statuses', () => {
    expect(parameterSupportBusinessLabel('SUPPORTED_DERIVED')).toBe('Calculated by BillionTech')
    expect(parameterSupportBusinessLabel('CALCULATION_NOT_IMPLEMENTED')).toBe(
      'Calculation not yet implemented',
    )
    expect(platformIntegrationLabel('PRODUCTION_READY')).toBe('Production Ready')
    expect(overallReadinessLabel('RUNTIME_READY_NONPROD')).toBe('Runtime Ready — Non-Production')
    expect(sourceTypeLabel('APPLICATION_INPUT')).toBe('Application input')
  })

  it('filters by parameterSupport', () => {
    const p = {
      id: 'bureau.accounts.cc_writeoff',
      sourceFamily: 'Bureau Retail',
      sourceType: 'PROVIDER',
      capability: {
        parameterSupport: { status: 'SUPPORTED_DERIVED' },
        policyDesign: { available: true },
        liveUse: { status: 'SUBSCRIPTION_REQUIRED', available: false },
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
  })

  it('DataParametersPage separates policy design vs live use and simplifies source cards', () => {
    const page = readFileSync(resolve(__dirname, '../../pages/DataParametersPage.tsx'), 'utf8')
    expect(page).toContain('dp-detail-policy-design')
    expect(page).toContain('dp-detail-live-use')
    expect(page).toContain('Available for policy design')
    expect(page).toContain('Live use: subscription required')
    expect(page).toContain('dp-source-capability-summary')
    expect(page).toContain('BillionTech integration')
    expect(page).toContain('dp-diagnostics-source-counts')
    expect(page).toContain('dp-advanced-technical')
    expect(page).not.toContain('Policy use: No')
    expect(page).not.toContain('Source N/I')
    expect(page).not.toContain('SurePass')
  })
})
