/**
 * Aligns with backend `CreateApplicationRequest` (JSON field names, enums as strings).
 * Optional maps are omitted when empty.
 */
import type { LoanProductCode } from '@/catalog/loanProducts'

export type { LoanProductCode }

export type BorrowerType = 'INDIVIDUAL' | 'PROPRIETOR' | 'PARTNERSHIP' | 'COMPANY'

export type IntakeSegment = 'BORROWER' | 'ANCHOR'

export interface CreateApplicationRequest {
  borrowerType: BorrowerType
  loanProduct: LoanProductCode
  intakeSegment?: IntakeSegment
  requestedAmount: number
  tenureMonths?: number | null
  personalInfo?: Record<string, unknown> | null
  businessInfo?: Record<string, unknown> | null
  financialInfo?: Record<string, unknown> | null
  collateralInfo?: Record<string, unknown> | null
}
