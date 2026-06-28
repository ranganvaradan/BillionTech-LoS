import { describe, expect, it } from 'vitest'
import { BORROWER_INTAKE_KEY, buildBorrowerIntakeCollateralPayload } from './collateralIntakePayload'
import { createEmptyIntakeFormState, type IntakeFormState } from './intakeTypes'

describe('collateralIntakePayload', () => {
  it('maps LAP-style property collateral to collateralInfo.borrowerIntake', () => {
    const s: IntakeFormState = {
      ...createEmptyIntakeFormState(),
      loanProduct: 'LOAN_AGAINST_PROPERTY',
      collateralPropertyType: 'Flat',
      collateralPropertyAddress: '1 X St',
      collateralOwnershipType: 'Sole',
      collateralEstimatedMarketValue: '5000000',
      collateralExistingMortgage: 'no',
    }
    const p = buildBorrowerIntakeCollateralPayload(s, 'BORROWER_SELF_SERVICE', ['doc-1']) as Record<string, unknown>
    const bi = p[BORROWER_INTAKE_KEY] as Record<string, unknown>
    expect(bi?.collateralType).toBe('PROPERTY')
    expect(bi?.estimatedValue).toBe(5_000_000)
    expect(bi?.providedBy).toBe('BORROWER')
    expect(Array.isArray(bi?.supportingDocumentIds)).toBe(true)
    expect(JSON.parse(String(bi?.detailsJson)).propertyType).toBe('Flat')
  })

  it('returns empty object when product is not secured', () => {
    const s: IntakeFormState = { ...createEmptyIntakeFormState(), loanProduct: 'PERSONAL_LOAN' }
    const p = buildBorrowerIntakeCollateralPayload(s, 'BORROWER_SELF_SERVICE', [])
    expect(Object.keys(p)).toHaveLength(0)
  })
})
