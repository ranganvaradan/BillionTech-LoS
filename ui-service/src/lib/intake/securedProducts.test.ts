import { describe, expect, it } from 'vitest'
import { buildBorrowerIntakeCollateralPayload } from './collateralIntakePayload'
import { createEmptyIntakeFormState, type IntakeFormState } from './intakeTypes'
import { detectSecuredCollateralKind, requiresCollateral } from './securedProducts'

describe('requiresCollateral / detectSecuredCollateralKind (codes)', () => {
  it('maps secured product codes to collateral kind', () => {
    expect(requiresCollateral('LOAN_AGAINST_PROPERTY')).toBe(true)
    expect(detectSecuredCollateralKind('LOAN_AGAINST_PROPERTY')).toBe('PROPERTY')
    expect(requiresCollateral('LOAN_AGAINST_SECURITIES')).toBe(true)
    expect(detectSecuredCollateralKind('LOAN_AGAINST_SECURITIES')).toBe('SHARES')
    expect(requiresCollateral('LOAN_AGAINST_GOLD')).toBe(true)
    expect(detectSecuredCollateralKind('LOAN_AGAINST_GOLD')).toBe('GOLD')
  })

  it('returns false for unsecured codes', () => {
    expect(requiresCollateral('PERSONAL_LOAN')).toBe(false)
    expect(requiresCollateral('TERM_LOAN')).toBe(false)
  })
})

describe('collateral payload by product code', () => {
  function withDocs(s: IntakeFormState) {
    return buildBorrowerIntakeCollateralPayload(s, 'ADMIN_INTERNAL', ['d1']) as Record<string, unknown>
  }

  it('produces PROPERTY payload for LOAN_AGAINST_PROPERTY', () => {
    const s: IntakeFormState = {
      ...createEmptyIntakeFormState(),
      loanProduct: 'LOAN_AGAINST_PROPERTY',
      collateralPropertyType: 'Plot',
      collateralPropertyAddress: 'Plot 9',
      collateralOwnershipType: 'Sole',
      collateralEstimatedMarketValue: '1',
      collateralExistingMortgage: 'no',
    }
    const p = withDocs(s)
    const bi = p.borrowerIntake as Record<string, unknown>
    expect(bi?.collateralType).toBe('PROPERTY')
  })

  it('produces SHARES payload for LOAN_AGAINST_SECURITIES', () => {
    const s: IntakeFormState = {
      ...createEmptyIntakeFormState(),
      loanProduct: 'LOAN_AGAINST_SECURITIES',
      collateralSecurityType: 'EQUITY',
      collateralIsin: 'INE000A000',
      collateralCompanyOrFundName: 'X Ltd',
      collateralShareQuantity: '10',
      collateralDematAccountNumber: '123',
      collateralPledgeConsent: true,
      collateralShareMarketValue: '50000',
    }
    const p = withDocs(s)
    const bi = p.borrowerIntake as Record<string, unknown>
    expect(bi?.collateralType).toBe('SHARES')
  })

  it('produces GOLD payload for LOAN_AGAINST_GOLD', () => {
    const s: IntakeFormState = {
      ...createEmptyIntakeFormState(),
      loanProduct: 'LOAN_AGAINST_GOLD',
      collateralGoldType: 'Ornaments',
      collateralGoldGrossWeight: '20g',
      collateralGoldNetWeight: '18g',
      collateralGoldPurityKarat: '22K',
      collateralGoldOrnamentDescription: 'chain',
      collateralGoldEstimatedValue: '200000',
    }
    const p = withDocs(s)
    const bi = p.borrowerIntake as Record<string, unknown>
    expect(bi?.collateralType).toBe('GOLD')
  })

  it('omits payload for unsecured product', () => {
    const s: IntakeFormState = { ...createEmptyIntakeFormState(), loanProduct: 'PERSONAL_LOAN' }
    expect(Object.keys(buildBorrowerIntakeCollateralPayload(s, 'BORROWER_SELF_SERVICE', []))).toHaveLength(0)
  })
})
