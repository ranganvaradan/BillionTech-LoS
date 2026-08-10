import { buildSalesAssistedCreatePayload } from '@/lib/salesAssistedPayload'
import type { CreateApplicationRequest, LoanProductCode } from '@/types/createApplication'
import type { UpdateApplicationRequest } from '@/types/updateApplication'
import type { IntakeFormState, IntakeMode } from './intakeTypes'
import { isBusinessBorrowerType } from './intakeTypes'
import type { SessionUser } from '@/auth/types'
import { isInvoiceDiscountingProduct } from '@/catalog/loanProducts'
import {
  labelForLoanPurpose,
  labelForOccupation,
} from '@/lib/intake/intakeOptionCatalogs'

function appendLmsConfigToCreate(s: IntakeFormState, payload: CreateApplicationRequest): CreateApplicationRequest {
  const withWorkflow = {
    ...payload,
    ...(s.workflowId ? { workflowId: s.workflowId } : {}),
  }
  if (isInvoiceDiscountingProduct(s.loanProduct)) {
    return withWorkflow
  }
  const code = s.lmsProductCode.trim()
  const unit = s.lmsTenureUnit.trim()
  return {
    ...withWorkflow,
    ...(code ? { lmsProductCode: code } : {}),
    ...(unit ? { lmsTenureUnit: unit } : {}),
  }
}

function appendLmsConfigToUpdate(s: IntakeFormState, payload: UpdateApplicationRequest): UpdateApplicationRequest {
  const withWorkflow = {
    ...payload,
    ...(s.workflowId ? { workflowId: s.workflowId } : {}),
  }
  if (isInvoiceDiscountingProduct(s.loanProduct)) {
    return withWorkflow
  }
  const code = s.lmsProductCode.trim()
  const unit = s.lmsTenureUnit.trim()
  return {
    ...withWorkflow,
    ...(code ? { lmsProductCode: code } : {}),
    ...(unit ? { lmsTenureUnit: unit } : {}),
  }
}

function trimStringRecord(rec: Record<string, string | boolean | number | null | undefined>): Record<string, string> {
  const out: Record<string, string> = {}
  for (const [k, v] of Object.entries(rec)) {
    if (typeof v === 'boolean') {
      out[k] = v ? 'true' : 'false'
    } else if (v != null && String(v).trim() !== '') {
      out[k] = String(v).trim()
    }
  }
  return out
}

function normalizeCustomFieldValues(values: Record<string, string | boolean>): Record<string, string | boolean> {
  const out: Record<string, string | boolean> = {}
  for (const [k, v] of Object.entries(values)) {
    if (!k.trim()) continue
    if (typeof v === 'boolean') {
      out[k] = v
      continue
    }
    const trimmed = String(v ?? '').trim()
    if (trimmed) out[k] = trimmed
  }
  return out
}

function appendCodedIntakePersonalFields(
  s: IntakeFormState,
  personal: Record<string, string>,
  workflow?: import('@/types/workflow').WorkflowConfigResponse | null,
): void {
  if (s.loanPurpose.trim()) {
    personal.loanPurpose = s.loanPurpose.trim()
    personal.purpose = labelForLoanPurpose(s.loanPurpose, workflow)
  } else if (s.purpose.trim()) {
    personal.purpose = s.purpose.trim()
  }
  if (s.occupation.trim()) {
    personal.occupation = s.occupation.trim()
    personal.occupationIndustry = labelForOccupation(s.occupation, workflow)
  } else if (s.occupationIndustry.trim()) {
    personal.occupationIndustry = s.occupationIndustry.trim()
  }
}

function parseTenure(s: string): number | null {
  const t = s.trim()
  if (!t) return null
  const n = Number.parseInt(t, 10)
  if (Number.isNaN(n) || n <= 0) return null
  return n
}

function journeyChannel(mode: IntakeMode): string {
  if (mode === 'BORROWER_SELF_SERVICE') return 'BORROWER_SELF_SERVICE'
  if (mode === 'SALES_ASSISTED') return 'SALES_ASSISTED'
  return 'INTERNAL'
}

function primaryPhone(s: IntakeFormState): string {
  if (s.borrowerType === 'INDIVIDUAL') return s.mobile || s.borrowerMobile
  return s.contactMobile
}

/**
 * After borrower details (step 1): first create, or re-submit borrower maps when editing.
 * Sales mode reuses the audited sales payload builder for the initial create.
 */
export function buildIntakeCreateRequest(s: IntakeFormState, mode: IntakeMode, staff: SessionUser | null): CreateApplicationRequest {
  const amount = Number.parseFloat(s.requestedAmount)
  if (Number.isNaN(amount) || amount <= 0) {
    throw new Error('Invalid amount')
  }
  const tenure = parseTenure(s.tenureMonths)

  if (mode === 'SALES_ASSISTED') {
    const base = buildSalesAssistedCreatePayload({
      borrowerType: s.borrowerType,
      loanProduct: s.loanProduct as LoanProductCode,
      requestedAmount: amount,
      tenureMonths: tenure,
      salesOfficerName: s.salesOfficerName,
      salesOfficerId: s.salesOfficerId,
      borrowerMobile: s.borrowerMobile,
      consentAccepted: s.salesBorrowerAck,
      fullName: s.borrowerType === 'INDIVIDUAL' ? s.fullName : s.contactPersonName,
      email: s.borrowerType === 'INDIVIDUAL' ? s.email : s.contactEmail,
      phone:
        s.borrowerType === 'INDIVIDUAL' ? s.mobile || s.borrowerMobile : s.contactMobile,
      purpose: s.purpose,
      businessName: s.businessName,
      gstin: s.gstin,
    })
    const pi: Record<string, string> = { ...(base.personalInfo as Record<string, string>) }
    const customFieldValues = normalizeCustomFieldValues(s.customFieldValues)
    if (Object.keys(customFieldValues).length) {
      ;(pi as Record<string, unknown>).customFields = customFieldValues
    }
    if (s.borrowerType === 'INDIVIDUAL') {
      if (s.dateOfBirth.trim()) pi.dateOfBirth = s.dateOfBirth.trim()
      if (s.addressLine.trim()) pi.addressLine = s.addressLine.trim()
      if (s.city.trim()) pi.city = s.city.trim()
      if (s.state.trim()) pi.state = s.state.trim()
      if (s.pincode.replace(/\D/g, '').length === 6) pi.pincode = s.pincode.replace(/\D/g, '')
    }
    if (isBusinessBorrowerType(s.borrowerType)) {
      const bi: Record<string, string> = { ...((base.businessInfo as Record<string, string> | undefined) ?? {}) }
      if (s.udyam.trim()) bi.udyam = s.udyam.trim()
      if (s.businessAddress.trim()) bi.addressLine = s.businessAddress.trim()
      if (s.businessCity.trim()) bi.city = s.businessCity.trim()
      if (s.businessState.trim()) bi.state = s.businessState.trim()
      if (s.businessPincode.replace(/\D/g, '').length === 6) bi.pincode = s.businessPincode.replace(/\D/g, '')
      appendInvoiceVintageFields(s, bi)
      return withInvoiceBorrowerSegment(s, appendLmsConfigToCreate(s, { ...base, personalInfo: pi, businessInfo: Object.keys(bi).length ? bi : base.businessInfo }))
    }
    return withInvoiceBorrowerSegment(s, appendLmsConfigToCreate(s, { ...base, personalInfo: pi }))
  }

  const phone = primaryPhone(s)
  const personal: Record<string, string> = trimStringRecord({
    fullName: s.borrowerType === 'INDIVIDUAL' ? s.fullName : s.contactPersonName,
    email: s.borrowerType === 'INDIVIDUAL' ? s.email : s.contactEmail,
    phone,
    mobile: phone,
    purpose: s.purpose,
    journeyChannel: journeyChannel(mode),
    intakeMode: mode,
    dateOfBirth: s.borrowerType === 'INDIVIDUAL' ? s.dateOfBirth : undefined,
    addressLine: s.borrowerType === 'INDIVIDUAL' ? s.addressLine : s.businessAddress,
    city: s.borrowerType === 'INDIVIDUAL' ? s.city : s.businessCity,
    state: s.borrowerType === 'INDIVIDUAL' ? s.state : s.businessState,
  })

  if (mode === 'ADMIN_INTERNAL' && staff) {
    personal.createdByName = staff.name
    personal.createdByUserId = staff.userId
  }

  if (s.borrowerType === 'INDIVIDUAL' && s.dateOfBirth.trim()) {
    personal.dateOfBirth = s.dateOfBirth.trim()
  }
  if (s.borrowerType === 'INDIVIDUAL') {
    if (s.addressLine.trim()) personal.addressLine = s.addressLine.trim()
    if (s.city.trim()) personal.city = s.city.trim()
    if (s.state.trim()) personal.state = s.state.trim()
    if (s.addressLine2.trim()) personal.addressLine2 = s.addressLine2.trim()
    if (s.pincode.replace(/\D/g, '').length === 6) personal.pincode = s.pincode.replace(/\D/g, '')
    if (s.gender.trim()) personal.gender = s.gender.trim()
    if (s.maritalStatus.trim()) personal.maritalStatus = s.maritalStatus.trim()
    if (s.hasExistingLoans) personal.hasExistingLoans = s.hasExistingLoans
    if (s.existingLoansDetails.trim()) personal.existingLoansDetails = s.existingLoansDetails.trim()
    if (s.addressProofType.trim()) personal.addressProofType = s.addressProofType.trim()
  }
  appendCodedIntakePersonalFields(s, personal)
  const customFieldValues = normalizeCustomFieldValues(s.customFieldValues)
  if (Object.keys(customFieldValues).length) {
    ;(personal as Record<string, unknown>).customFields = customFieldValues
  }

  const business: Record<string, string> = {}
  if (isBusinessBorrowerType(s.borrowerType)) {
    if (s.businessName.trim()) business.businessName = s.businessName.trim()
    if (s.gstin.trim()) business.gstin = s.gstin.trim()
    if (s.udyam.trim()) business.udyam = s.udyam.trim()
    if (s.contactPersonName.trim()) business.contactPersonName = s.contactPersonName.trim()
    if (s.contactMobile.trim()) business.contactMobile = s.contactMobile.trim()
    if (s.contactEmail.trim()) business.contactEmail = s.contactEmail.trim()
    if (s.businessAddress.trim()) business.addressLine = s.businessAddress.trim()
    if (s.businessCity.trim()) business.city = s.businessCity.trim()
    if (s.businessState.trim()) business.state = s.businessState.trim()
    if (s.businessPincode.replace(/\D/g, '').length === 6) business.pincode = s.businessPincode.replace(/\D/g, '')
  }
  appendInvoiceVintageFields(s, business)

  const payload: CreateApplicationRequest = {
    borrowerType: s.borrowerType,
    loanProduct: s.loanProduct as LoanProductCode,
    ...(s.workflowId ? { workflowId: s.workflowId } : {}),
    requestedAmount: amount,
    personalInfo: personal,
  }
  if (tenure != null) payload.tenureMonths = tenure
  if (Object.keys(business).length) payload.businessInfo = business
  return withInvoiceBorrowerSegment(s, appendLmsConfigToCreate(s, payload))
}

function withInvoiceBorrowerSegment(s: IntakeFormState, r: CreateApplicationRequest): CreateApplicationRequest {
  if (isInvoiceDiscountingProduct(s.loanProduct) && s.invoiceOnboardingChoice === 'BORROWER') {
    return { ...r, intakeSegment: 'BORROWER' }
  }
  return r
}

function appendInvoiceVintageFields(s: IntakeFormState, business: Record<string, string>): void {
  if (!isInvoiceDiscountingProduct(s.loanProduct) || s.invoiceOnboardingChoice === 'ANCHOR') {
    return
  }
  const dep = s.dependencyVintagePercent.trim()
  const anchorMo = s.anchorRelationshipVintageMonths.trim()
  if (dep) business.dependencyVintagePercent = dep
  if (anchorMo) business.anchorRelationshipVintageMonths = anchorMo
}

/** Patch borrower / business / amount after step 0 edited while id exists, or re-save after step 1 edits. */
export function buildIntakeBorrowerUpdate(s: IntakeFormState, mode: IntakeMode, staff: SessionUser | null): UpdateApplicationRequest {
  const amount = Number.parseFloat(s.requestedAmount)
  const tenure = parseTenure(s.tenureMonths)

  const out: UpdateApplicationRequest = {}
  if (!Number.isNaN(amount) && amount > 0) out.requestedAmount = amount
  if (tenure != null) out.tenureMonths = tenure
  if (s.workflowId) out.workflowId = s.workflowId

  const p = primaryPhone(s)
  const personal: Record<string, string> = trimStringRecord({
    fullName: s.borrowerType === 'INDIVIDUAL' ? s.fullName : s.contactPersonName,
    email: s.borrowerType === 'INDIVIDUAL' ? s.email : s.contactEmail,
    phone: p,
    mobile: p,
    purpose: s.purpose,
    journeyChannel: journeyChannel(mode),
    intakeMode: mode,
    dateOfBirth: s.borrowerType === 'INDIVIDUAL' ? s.dateOfBirth : undefined,
    addressLine: s.borrowerType === 'INDIVIDUAL' ? s.addressLine : s.businessAddress,
    city: s.borrowerType === 'INDIVIDUAL' ? s.city : s.businessCity,
    state: s.borrowerType === 'INDIVIDUAL' ? s.state : s.businessState,
  })
  if (mode === 'SALES_ASSISTED') {
    personal.assistedBy = [s.salesOfficerName, s.salesOfficerId].filter(Boolean).join(' · ').trim() || 'Sales'
    personal.assistedChannel = 'SALES_ASSISTED'
    personal.salesOfficerName = s.salesOfficerName
    personal.salesOfficerId = s.salesOfficerId
    personal.borrowerMobile = s.borrowerMobile
    personal.borrowerConsentToProceed = s.salesBorrowerAck ? 'true' : 'false'
  }
  if (mode === 'ADMIN_INTERNAL' && staff) {
    personal.lastSavedByName = staff.name
    personal.lastSavedByUserId = staff.userId
  }

  if (s.borrowerType === 'INDIVIDUAL') {
    if (s.dateOfBirth.trim()) personal.dateOfBirth = s.dateOfBirth.trim()
    if (s.addressLine.trim()) personal.addressLine = s.addressLine.trim()
    if (s.city.trim()) personal.city = s.city.trim()
    if (s.state.trim()) personal.state = s.state.trim()
    if (s.addressLine2.trim()) personal.addressLine2 = s.addressLine2.trim()
    if (s.pincode.replace(/\D/g, '').length === 6) personal.pincode = s.pincode.replace(/\D/g, '')
    if (s.gender.trim()) personal.gender = s.gender.trim()
    if (s.maritalStatus.trim()) personal.maritalStatus = s.maritalStatus.trim()
    if (s.hasExistingLoans) personal.hasExistingLoans = s.hasExistingLoans
    if (s.existingLoansDetails.trim()) personal.existingLoansDetails = s.existingLoansDetails.trim()
    if (s.addressProofType.trim()) personal.addressProofType = s.addressProofType.trim()
  }
  appendCodedIntakePersonalFields(s, personal)
  const customFieldValues = normalizeCustomFieldValues(s.customFieldValues)
  if (Object.keys(customFieldValues).length) {
    ;(personal as Record<string, unknown>).customFields = customFieldValues
  }

  const business: Record<string, string> = {}
  if (isBusinessBorrowerType(s.borrowerType)) {
    if (s.businessName.trim()) business.businessName = s.businessName.trim()
    if (s.gstin.trim()) business.gstin = s.gstin.trim()
    if (s.udyam.trim()) business.udyam = s.udyam.trim()
    if (s.contactPersonName.trim()) business.contactPersonName = s.contactPersonName.trim()
    if (s.contactMobile.trim()) business.contactMobile = s.contactMobile.trim()
    if (s.contactEmail.trim()) business.contactEmail = s.contactEmail.trim()
    if (s.businessAddress.trim()) business.addressLine = s.businessAddress.trim()
    if (s.businessCity.trim()) business.city = s.businessCity.trim()
    if (s.businessState.trim()) business.state = s.businessState.trim()
    if (s.businessPincode.replace(/\D/g, '').length === 6) business.pincode = s.businessPincode.replace(/\D/g, '')
  }
  appendInvoiceVintageFields(s, business)

  out.personalInfo = personal
  if (Object.keys(business).length) out.businessInfo = business
  return appendLmsConfigToUpdate(s, out)
}

export function buildKycUpdate(s: IntakeFormState): UpdateApplicationRequest {
  const aadhaarDigits = s.aadhaar.replace(/\D/g, '')
  const personal: Record<string, string | boolean> = {
    panNumber: s.panNumber.trim().toUpperCase(),
    mobileLinkedAadhaar: s.mobileLinkedAadhaar,
  }
  if (aadhaarDigits.length === 4) {
    personal.aadhaarLast4 = aadhaarDigits
  } else if (aadhaarDigits.length === 12) {
    personal.aadhaarNumber = aadhaarDigits
  }
  if (s.bankAccountNumber.trim()) personal.bankAccountNumber = s.bankAccountNumber.trim()
  if (s.ifscCode.trim()) personal.ifsc = s.ifscCode.trim().toUpperCase()
  if (s.bankName.trim()) personal.bankName = s.bankName.trim()
  if (s.voterId.trim()) personal.voterId = s.voterId.trim().toUpperCase()
  if (s.dlNumber.trim()) personal.dlNumber = s.dlNumber.trim().toUpperCase()

  const business: Record<string, string> = {}
  if (isBusinessBorrowerType(s.borrowerType)) {
    if (s.gstin.trim()) business.gstin = s.gstin.trim()
    if (s.udyam.trim()) business.udyam = s.udyam.trim()
    if (s.borrowerType === 'COMPANY' && s.cin.trim()) {
      business.cin = s.cin.trim()
    }
  }

  return {
    personalInfo: personal as unknown as Record<string, unknown>,
    businessInfo: Object.keys(business).length ? (business as unknown as Record<string, unknown>) : undefined,
  }
}

export function buildConsentUpdate(s: IntakeFormState, mode: IntakeMode, staff: SessionUser | null): UpdateApplicationRequest {
  const financial: Record<string, string> = {
    consentKyc: s.consentKyc ? 'true' : 'false',
    consentBureau: s.consentBureau ? 'true' : 'false',
    consentAccountAggregator: s.consentAccountAggregator ? 'true' : 'false',
    consentComms: s.consentComms ? 'true' : 'false',
  }
  if (mode === 'SALES_ASSISTED' && staff) {
    financial.consentCapturedByName = staff.name
    financial.consentCapturedByUserId = staff.userId
  }
  if (mode === 'ADMIN_INTERNAL' && staff) {
    financial.consentRecordedByName = staff.name
    financial.consentRecordedByUserId = staff.userId
  }
  return { financialInfo: financial as unknown as Record<string, unknown> }
}

/** Income & employment (borrower self-service step). */
export function buildBorrowerEmploymentUpdate(s: IntakeFormState): UpdateApplicationRequest {
  const personal: Record<string, string> = trimStringRecord({
    employmentType: s.employmentType,
    employerName: s.employerName,
    monthlyNetIncome: s.monthlyNetIncome,
    occupationIndustry: s.occupationIndustry,
    workExperienceYears: s.workExperienceYears,
    occupation: s.occupation,
  })
  appendCodedIntakePersonalFields(s, personal)
  return { personalInfo: personal as unknown as Record<string, unknown> }
}
