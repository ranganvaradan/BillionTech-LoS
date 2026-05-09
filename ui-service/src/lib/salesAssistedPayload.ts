import type { BorrowerType, CreateApplicationRequest, LoanProductCode } from '@/types/createApplication'

export interface SalesAssistedFormInput {
  borrowerType: BorrowerType
  loanProduct: LoanProductCode
  requestedAmount: number
  tenureMonths: number | null
  salesOfficerName: string
  salesOfficerId: string
  borrowerMobile: string
  consentAccepted: boolean
  fullName: string
  email: string
  phone: string
  purpose: string
  businessName: string
  gstin: string
}

/**
 * Create-application payload for sales-assisted origination. Stores channel metadata in personalInfo
 * (schema tolerates additional keys; does not require backend metadata field).
 */
export function buildSalesAssistedCreatePayload(input: SalesAssistedFormInput): CreateApplicationRequest {
  const {
    borrowerType,
    loanProduct,
    requestedAmount,
    tenureMonths,
    salesOfficerName,
    salesOfficerId,
    borrowerMobile,
    consentAccepted,
    fullName,
    email,
    phone,
    purpose,
    businessName,
    gstin,
  } = input

  const personal: Record<string, string> = {
    assistedBy: [salesOfficerName, salesOfficerId].filter(Boolean).join(' · ').trim() || 'Sales',
    assistedChannel: 'SALES_ASSISTED',
    journeyChannel: 'SALES_ASSISTED',
    intakeMode: 'SALES_ASSISTED',
    borrowerConsentAt: String(consentAccepted),
  }
  for (const [k, v] of Object.entries({
    fullName,
    email,
    phone: phone || borrowerMobile,
    purpose,
    salesOfficerName,
    salesOfficerId,
    borrowerMobile,
  })) {
    if (v.trim()) personal[k] = v.trim()
  }

  const business: Record<string, string> = {}
  if (businessName.trim()) business.businessName = businessName.trim()
  if (gstin.trim()) business.gstin = gstin.trim()

  const payload: CreateApplicationRequest = {
    borrowerType,
    loanProduct,
    requestedAmount,
    personalInfo: personal,
  }
  if (tenureMonths != null && tenureMonths > 0) {
    payload.tenureMonths = tenureMonths
  }
  if (Object.keys(business).length) {
    payload.businessInfo = business
  }
  return payload
}
