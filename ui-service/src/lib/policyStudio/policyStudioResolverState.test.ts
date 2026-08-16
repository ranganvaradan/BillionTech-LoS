import { describe, expect, it } from 'vitest'
import {
  derivePolicyStudioOperandPresentation,
  ruleLifecycleNeedsReview,
} from './policyStudioResolverState'

describe('Wave10A policyStudioResolverState', () => {
  it('DPD30-like executable operand is Ready to test without calc resolver', () => {
    const p = derivePolicyStudioOperandPresentation(
      {
        parameterId: 'bureau.dpd_30_plus_count_6m',
        calculationRequired: true, // stale catalogue flag must not win
        policyTestReady: true,
        canonicalTruth: {
          primaryStatus: 'READY_TO_TEST',
          primaryStatusLabel: 'Ready to test',
          execution: { capability: true, status: 'DATA_NOT_AVAILABLE' },
          calculation: { required: false },
        },
      },
      { ruleNeedsReview: true },
    )
    expect(p.parameterLabel).toBe('Ready to test')
    expect(p.showCalculationResolver).toBe(false)
    expect(p.case).toBe('RULE_REVIEW_ONLY')
  })

  it('CC overdue-like non-executable says Calculation needs setup', () => {
    const p = derivePolicyStudioOperandPresentation({
      parameterId: 'bureau.cc_overdue_amount',
      calculationRequired: true,
      canonicalTruth: {
        primaryStatus: 'CALCULATION_NEEDS_SETUP',
        primaryStatusLabel: 'Calculation needs setup',
        execution: { capability: false, status: 'NOT_EXECUTABLE' },
        calculation: { required: true },
      },
    })
    expect(p.parameterLabel).toBe('Calculation needs setup')
    expect(p.showCalculationResolver).toBe(true)
    expect(p.parameterLabel).not.toMatch(/Supported|Available for policy|Ready to test/i)
  })

  it('proposal ready for review keeps setup truth + review action', () => {
    const p = derivePolicyStudioOperandPresentation(
      {
        canonicalTruth: {
          primaryStatus: 'CALCULATION_NEEDS_SETUP',
          primaryStatusLabel: 'Calculation needs setup',
          execution: { capability: false },
          calculation: { required: true },
        },
      },
      { proposalReadyForReview: true },
    )
    expect(p.resolverActionLabel).toBe('Review proposed calculation')
    expect(p.capability).toBe(false)
  })

  it('manual input case', () => {
    const p = derivePolicyStudioOperandPresentation({
      manualInput: true,
      canonicalTruth: {
        primaryStatus: 'NEEDS_MANUAL_INPUT',
        primaryStatusLabel: 'Needs manual input',
        execution: { capability: false },
      },
    })
    expect(p.parameterLabel).toBe('Needs manual input')
    expect(p.showManualResolver).toBe(true)
  })

  it('rule lifecycle needs review separate from parameter', () => {
    expect(
      ruleLifecycleNeedsReview({
        lenderState: 'READY_FOR_CONFIRMATION',
        showAcceptRule: true,
        ruleLifecycleLabel: 'Needs review',
      }),
    ).toBe(true)
  })
})
