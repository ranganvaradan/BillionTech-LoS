import { http } from './http'

export interface BorrowerProgramEnrollment {
  subProgramId: string
  subProgramName: string
  subProgramCode: string
  subProgramStatus: string
  flowType: string
  subProgramInterestRate: number | null
  subProgramMarginPercent: number | null
  subProgramMaxTenureDays: number | null

  programId: string | null
  programName: string | null
  programCode: string | null
  programStatus: string | null
  productType: string | null
  programLimit: number | null
  programUtilizedLimit: number | null
  programAvailableLimit: number | null
  defaultInterestRate: number | null
  programMarginPercent: number | null
  programMaxTenureDays: number | null

  borrowerLimit: number | null
  borrowerUtilizedLimit: number | null
  borrowerAvailableLimit: number | null
  membershipStatus: string

  programParameters?: Record<string, unknown> | null
  programConfig?: Record<string, unknown> | null
  lmsEntryIn?: string | null
  encoreProductCode?: string | null

  borrowerInterestRate?: number | null
  borrowerDiscountMarginPercent?: number | null
  borrowerCreditPeriodDays?: number | null
  borrowerDiscountHold?: string | null
  borrowerPaymentMethod?: string | null
  borrowerOverdueInterestRate?: number | null
}

export interface BorrowerProgramsResponse {
  linked: boolean
  message: string | null
  enrollments: BorrowerProgramEnrollment[]
}

export async function getBorrowerPrograms(): Promise<BorrowerProgramsResponse> {
  const { data } = await http.get<BorrowerProgramsResponse>('/borrower/programs')
  return data
}
