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
    expect(parameterSupportBusinessLabel('CALCULATION_NOT_IMPLEMENTED')).toBe('Calculation needs setup')
    expect(parameterSupportBusinessLabel('SUPPORTED_RAW')).toBe('Provided directly')
    expect(platformIntegrationLabel('PRODUCTION_READY')).toBe('Connected')
    expect(overallReadinessLabel('RUNTIME_READY_NONPROD')).toBe('Ready to test')
    expect(overallReadinessLabel('PRODUCTION_READY')).toBe('Listed — not certification')
    expect(overallReadinessLabel('APPROVED_FOR_LIVE_USE')).toBe('Approved for live use')
    expect(overallReadinessLabel('CALCULATION_NEEDS_SETUP')).toBe('Calculation needs setup')
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

  it('DataParametersPage uses canonical primary status and isolates legacy metadata', () => {
    const page = readFileSync(resolve(__dirname, '../../pages/DataParametersPage.tsx'), 'utf8')
    expect(page).toContain('dp-primary-status')
    expect(page).toContain('dp-detail-primary-status')
    expect(page).toContain('dp-detail-live-use')
    expect(page).toContain('lenderPrimaryFromTruth')
    expect(page).toContain('dp-source-capability-summary')
    expect(page).toContain('dp-source-summary-line')
    expect(page).toContain('BillionTech integration')
    expect(page).toContain('dp-legacy-filters-advanced')
    expect(page).toContain('Advanced / Technical — Legacy metadata')
    expect(page).toContain('CanonicalParameterTruthProjection')
    expect(page).not.toContain('Legacy readiness (all)')
    // Catalogue Production Ready only under Advanced details, not primary option label alone
    expect(page).toContain('dp-legacy-filters-advanced')
    expect(page).not.toContain('Policy use: No')
    expect(page).not.toContain('Source N/I')
    expect(page).not.toContain('SurePass')
  })
})
