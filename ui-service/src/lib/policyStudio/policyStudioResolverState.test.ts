import { describe, expect, it } from 'vitest'
import { derivePolicyStudioOperandPresentation } from './policyStudioResolverState'

describe('policyStudioResolverState golden readiness', () => {
  it('DPD30-like executable operand is Ready without calc resolver', () => {
    const p = derivePolicyStudioOperandPresentation({
      canonicalParameterId: 'bureau.dpd_30_plus_count_6m',
      canonicalParameterState: {
        primaryStatus: 'READY',
        primaryStatusLabel: 'Ready',
        businessReadiness: 'READY',
        execution: { capability: true },
        semantic: { parameterClass: 'BUSINESS_PARAMETER', calculationMode: 'AUTHORED' },
      },
      policyTestReady: true,
      calculationRequired: false,
    })
    expect(p.case).toBe('EXECUTABLE')
    expect(p.showCalculationResolver).toBe(false)
    expect(p.parameterLabel).toBe('Ready')
  })

  it('CC overdue-like non-executable says Calculation not defined', () => {
    const p = derivePolicyStudioOperandPresentation({
      canonicalParameterId: 'bureau.cc_overdue_amount',
      primaryStatus: 'NOT_READY',
      primaryStatusLabel: 'Calculation not defined',
      businessReadinessReason: 'CALCULATION_NOT_DEFINED',
      canonicalParameterState: {
        primaryStatus: 'NOT_READY',
        primaryStatusLabel: 'Calculation not defined',
        businessReadiness: 'NOT_READY',
        businessReadinessReason: 'CALCULATION_NOT_DEFINED',
        execution: { capability: false },
        semantic: { parameterClass: 'BUSINESS_PARAMETER', calculationMode: 'AUTHORED' },
      },
      calculationRequired: true,
      policyTestReady: false,
    })
    expect(p.case).toBe('SETUP_CALCULATION')
    expect(p.showCalculationResolver).toBe(true)
    expect(p.parameterLabel).toBe('Calculation not defined')
    expect(p.parameterLabel).not.toMatch(/Supported|Available for policy|Ready to test/i)
  })

  it('ingredient never shows calculation resolver', () => {
    const p = derivePolicyStudioOperandPresentation({
      canonicalParameterState: {
        primaryStatus: 'NOT_READY',
        primaryStatusLabel: 'Raw field not available',
        execution: { capability: false },
        semantic: { parameterClass: 'INGREDIENT' },
      },
    })
    expect(p.showCalculationResolver).toBe(false)
  })

  it('manual shows manual resolver', () => {
    const p = derivePolicyStudioOperandPresentation({
      primaryStatus: 'READY',
      primaryStatusLabel: 'Needs manual input',
      canonicalParameterState: {
        primaryStatus: 'READY',
        primaryStatusLabel: 'Needs manual input',
        businessReadinessReason: 'MANUAL_INPUT',
        execution: { capability: true },
        semantic: { parameterClass: 'MANUAL_INPUT', calculationMode: 'MANUAL' },
      },
    })
    expect(p.case).toBe('MANUAL_INPUT')
    expect(p.showManualResolver).toBe(true)
    expect(p.showCalculationResolver).toBe(false)
  })
})
