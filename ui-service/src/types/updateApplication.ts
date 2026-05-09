/**
 * Subset of backend `UpdateApplicationRequest` for merging JSON maps on the application.
 */
export interface UpdateApplicationRequest {
  requestedAmount?: number | null
  tenureMonths?: number | null
  personalInfo?: Record<string, unknown> | null
  businessInfo?: Record<string, unknown> | null
  financialInfo?: Record<string, unknown> | null
  collateralInfo?: Record<string, unknown> | null
  remarks?: string | null
}
