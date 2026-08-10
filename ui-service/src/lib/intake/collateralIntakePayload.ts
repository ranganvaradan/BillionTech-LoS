import type { IntakeFormState, IntakeMode } from './intakeTypes'
import { detectSecuredCollateralKind, type SecuredCollateralKind } from './securedProducts'

export const BORROWER_INTAKE_KEY = 'borrowerIntake' as const

function parseAmount(s: string): number | null {
  const n = Number.parseFloat(s.replace(/,/g, '').trim())
  if (Number.isNaN(n) || n < 0) return null
  return n
}

function providedByFromMode(mode: IntakeMode): 'BORROWER' | 'SALES_ASSISTED' | 'ADMIN' {
  if (mode === 'BORROWER_SELF_SERVICE') return 'BORROWER'
  if (mode === 'SALES_ASSISTED') return 'SALES_ASSISTED'
  return 'ADMIN'
}

function buildDetails(kind: SecuredCollateralKind, s: IntakeFormState): Record<string, unknown> {
  if (kind === 'PROPERTY') {
    return {
      propertyType: s.collateralPropertyType.trim(),
      propertyAddress: s.collateralPropertyAddress.trim(),
      ownershipType: s.collateralOwnershipType.trim(),
      existingMortgageOrEncumbrance: s.collateralExistingMortgage || null,
    }
  }
  if (kind === 'SHARES') {
    return {
      securityType: s.collateralSecurityType.trim(),
      isin: s.collateralIsin.trim().toUpperCase(),
      companyOrMutualFundName: s.collateralCompanyOrFundName.trim(),
      quantity: s.collateralShareQuantity.trim(),
      dematAccountNumber: s.collateralDematAccountNumber.trim(),
      pledgeConsent: s.collateralPledgeConsent,
    }
  }
  if (kind === 'GOLD') {
    return {
      goldType: s.collateralGoldType.trim(),
      approxGrossWeight: s.collateralGoldGrossWeight.trim(),
      approxNetWeight: s.collateralGoldNetWeight.trim(),
      purityOrKarat: s.collateralGoldPurityKarat.trim(),
      ornamentDescription: s.collateralGoldOrnamentDescription.trim(),
    }
  }
  if (kind === 'VEHICLE') {
    return {
      vehicleType: s.collateralVehicleType || null,
      makeModel: s.collateralVehicleMakeModel.trim(),
      yearOfManufacture: s.collateralVehicleYear.trim(),
      registrationNumber: s.collateralVehicleRegistrationNumber.trim(),
      existingLoanOnVehicle: s.collateralVehicleExistingLoan || null,
    }
  }
  if (kind === 'FIXED_DEPOSIT') {
    return {
      bankName: s.collateralFdBankName.trim(),
      fdAccountNumber: s.collateralFdAccountNumber.trim(),
      fdAmount: s.collateralFdAmount.trim(),
      maturityDate: s.collateralFdMaturityDate.trim(),
      fdReceiptNumber: s.collateralFdReceiptNumber.trim(),
    }
  }
  return {
    machineryTypeDescription: s.collateralMachineryTypeDescription.trim(),
    makeModel: s.collateralMachineryMakeModel.trim(),
    yearOfPurchase: s.collateralMachineryYearOfPurchase.trim(),
    locationAddress: s.collateralMachineryLocationAddress.trim(),
  }
}

export function estimatedValueForSecuredProduct(s: IntakeFormState, kind: SecuredCollateralKind): number | null {
  if (kind === 'PROPERTY') return parseAmount(s.collateralEstimatedMarketValue)
  if (kind === 'SHARES') return parseAmount(s.collateralShareMarketValue)
  if (kind === 'GOLD') return parseAmount(s.collateralGoldEstimatedValue)
  if (kind === 'VEHICLE') return parseAmount(s.collateralVehicleEstimatedMarketValue)
  if (kind === 'FIXED_DEPOSIT') return parseAmount(s.collateralFdAmount)
  if (kind === 'MACHINERY') return parseAmount(s.collateralMachineryEstimatedValue)
  return null
}

/**
 * Merged into `collateralInfo` as `{ borrowerIntake: { ... } }` to avoid clobbering other collateral modules.
 */
export function buildBorrowerIntakeCollateralPayload(
  s: IntakeFormState,
  mode: IntakeMode,
  supportingDocumentIds: string[],
): Record<string, unknown> {
  const kind = detectSecuredCollateralKind(s.loanProduct)
  if (!kind) return {}
  const ev = estimatedValueForSecuredProduct(s, kind)
  const details = buildDetails(kind, s)
  const payload: Record<string, unknown> = {
    collateralType: kind,
    product: s.loanProduct.trim(),
    estimatedValue: ev,
    detailsJson: JSON.stringify(details),
    supportingDocumentIds,
    providedBy: providedByFromMode(mode),
    providedAt: new Date().toISOString(),
  }
  return { [BORROWER_INTAKE_KEY]: payload }
}
