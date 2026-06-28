import { BORROWER_TYPE_ORDER } from './borrowerTypes'
import { LOAN_PRODUCT_CODES, type LoanProductCode } from './loanProducts'

/** All 4 × 8 borrower × product segments (for default policy / tests). */
export function allBorrowerProductSegments(): { borrowerType: string; loanProduct: LoanProductCode }[] {
  const out: { borrowerType: string; loanProduct: LoanProductCode }[] = []
  for (const bt of BORROWER_TYPE_ORDER) {
    for (const lp of LOAN_PRODUCT_CODES) {
      out.push({ borrowerType: bt, loanProduct: lp })
    }
  }
  return out
}
