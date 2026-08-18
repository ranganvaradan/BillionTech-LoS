import { describe, expect, it } from 'vitest'
import {
  derivePolicyStudioOperandPresentation,
  persistedMappingFromOperand,
} from './policyStudioResolverState'

describe('policyStudioResolverState golden readiness', () => {
  it('persisted unresolved mapping is EXISTING_MAPPING_UNRESOLVED not Not yet mapped', () => {
    const p = derivePolicyStudioOperandPresentation({
      parameterId: 'bureau.gone',
      existingMappingUnresolved: true,
      unresolved: false,
    })
    expect(p.parameterLabel).toBe('EXISTING_MAPPING_UNRESOLVED')
    expect(p.showMapResolver).toBe(false)
  })

  it('mapped READY_DERIVED stays CURRENT_MAPPING_RESOLVED even if unresolved was forced', () => {
    const op = {
      parameterId: 'bureau.inquiries.current_month',
      unresolved: true,
      evaluatedFrom: 'Bureau Retail',
      resolutionState: 'DERIVED',
      availabilityLabel: 'Derived automatically',
      canonicalParameterState: {
        primaryStatus: 'READY',
        primaryStatusLabel: 'Ready',
        businessReadiness: 'READY',
        execution: { capability: true },
        semantic: { parameterClass: 'BUSINESS_PARAMETER', calculationMode: 'BUILT_IN' },
      },
      policyTestReady: true,
    }
    const mapping = persistedMappingFromOperand(op)
    expect(mapping.state).toBe('CURRENT_MAPPING_RESOLVED')
    expect(mapping.canonicalParameterId).toBe('bureau.inquiries.current_month')
    expect(mapping.source).toBe('Bureau Retail')
    expect(mapping.classification).toBe('DERIVED')
    expect(mapping.headerLabel).not.toBe('Not yet mapped')
    const p = derivePolicyStudioOperandPresentation(op)
    expect(p.case).toBe('EXECUTABLE')
    expect(p.parameterLabel).toBe('Ready')
    expect(p.showMapResolver).toBe(false)
  })

  it('READY_RAW mapped operand is CURRENT_MAPPING_RESOLVED', () => {
    const mapping = persistedMappingFromOperand({
      canonicalParameterId: 'bureau.score',
      evaluatedFrom: 'Bureau Retail',
      resolutionState: 'RAW',
    })
    expect(mapping.state).toBe('CURRENT_MAPPING_RESOLVED')
    expect(mapping.classification).toBe('RAW')
  })

  it('genuinely unmapped operand is Not yet mapped', () => {
    const mapping = persistedMappingFromOperand({
      unresolved: true,
      businessName: 'Unknown term',
    })
    expect(mapping.state).toBe('NOT_YET_MAPPED')
    expect(mapping.headerLabel).toBe('Not yet mapped')
  })

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

  it('missing canonical truth never labels the parameter Needs review', () => {
    const p = derivePolicyStudioOperandPresentation({
      operandKey: 'dpd_30_plus_count_6m',
      unresolved: false,
    })
    expect(p.parameterLabel).not.toBe('Needs review')
  })

  it('proposal ready still exposes Set up calculation as primary outer action', () => {
    const p = derivePolicyStudioOperandPresentation(
      {
        canonicalParameterId: 'bureau.cc_overdue_amount',
        canonicalParameterState: {
          primaryStatus: 'NOT_READY',
          primaryStatusLabel: 'Calculation not defined',
          businessReadiness: 'NOT_READY',
          businessReadinessReason: 'CALCULATION_NOT_DEFINED',
          execution: { capability: false },
          semantic: { parameterClass: 'BUSINESS_PARAMETER', calculationMode: 'AUTHORED' },
        },
        calculationRequired: true,
      },
      { proposalReadyForReview: true },
    )
    expect(p.case).toBe('SETUP_CALCULATION')
    expect(p.resolverActionLabel).toBe('Set up calculation')
    expect(p.setupFlowHint).toBe('Review proposed calculation')
    expect(p.showCalculationResolver).toBe(true)
  })
})
