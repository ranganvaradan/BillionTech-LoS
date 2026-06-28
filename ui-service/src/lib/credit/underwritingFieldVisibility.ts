import type { BorrowerType } from '@/types/createApplication'
import { isBusinessBorrowerType } from '@/lib/intake/intakeTypes'

/** Normalize API / DB value; default keeps underwriting stable when unset. */
export function normalizeBorrowerType(code: string | null | undefined): BorrowerType {
  if (code === 'INDIVIDUAL' || code === 'PROPRIETOR' || code === 'PARTNERSHIP' || code === 'COMPANY') {
    return code
  }
  return 'INDIVIDUAL'
}

export type UnderwritingFieldVisibility = {
  showGstIncome: boolean
  showGstinKyc: boolean
  showUdyamKyc: boolean
  showEbitdaProxy: boolean
  showBusinessRiskCard: boolean
  showGstinIntakeContext: boolean
  showGstinKycSummary: boolean
  showUdyamKycSummary: boolean
}

/**
 * Reusable visibility for underwriting / manual credit / summaries.
 * Does not alter layout components — callers conditionally render rows.
 */
export function getVisibleUnderwritingFields(borrowerType: string | null | undefined): UnderwritingFieldVisibility {
  const bt = normalizeBorrowerType(borrowerType)
  const business = isBusinessBorrowerType(bt)
  return {
    showGstIncome: business,
    showGstinKyc: business,
    showUdyamKyc: business,
    showEbitdaProxy: business,
    showBusinessRiskCard: business,
    showGstinIntakeContext: business,
    showGstinKycSummary: business,
    showUdyamKycSummary: business,
  }
}
