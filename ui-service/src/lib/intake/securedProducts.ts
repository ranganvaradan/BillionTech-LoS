/**
 * Secured / collateral-requiring products. All matching uses standard `LoanProductCode` values.
 */
import { isSecuredLoanProductCode } from '@/catalog/loanProducts'

export type SecuredCollateralKind = 'PROPERTY' | 'SHARES' | 'GOLD'

/**
 * @returns collateral kind for secured products, or null if unsecured / unknown.
 */
export function detectSecuredCollateralKind(loanProduct: string): SecuredCollateralKind | null {
  const t = loanProduct.trim()
  if (t === 'LOAN_AGAINST_PROPERTY') return 'PROPERTY'
  if (t === 'LOAN_AGAINST_SECURITIES') return 'SHARES'
  if (t === 'LOAN_AGAINST_GOLD') return 'GOLD'
  return null
}

export function requiresCollateral(loanProduct: string): boolean {
  return isSecuredLoanProductCode(loanProduct)
}

/** Document type constants used for collateral uploads (also listed in DocumentsSection). */
export const COLLATERAL_DOC = {
  PROPERTY_DOCUMENT: 'PROPERTY_DOCUMENT',
  PROPERTY_VALUATION: 'PROPERTY_VALUATION',
  SHARE_HOLDING_STATEMENT: 'SHARE_HOLDING_STATEMENT',
  GOLD_PHOTO: 'GOLD_PHOTO',
  GOLD_VALUATION: 'GOLD_VALUATION',
  COLLATERAL_OTHER: 'COLLATERAL_OTHER',
} as const

export function collateralDocumentTypesForKind(kind: SecuredCollateralKind): string[] {
  if (kind === 'PROPERTY') return [COLLATERAL_DOC.PROPERTY_DOCUMENT, COLLATERAL_DOC.PROPERTY_VALUATION, COLLATERAL_DOC.COLLATERAL_OTHER]
  if (kind === 'SHARES') return [COLLATERAL_DOC.SHARE_HOLDING_STATEMENT, COLLATERAL_DOC.COLLATERAL_OTHER]
  return [COLLATERAL_DOC.GOLD_PHOTO, COLLATERAL_DOC.GOLD_VALUATION, COLLATERAL_DOC.COLLATERAL_OTHER]
}