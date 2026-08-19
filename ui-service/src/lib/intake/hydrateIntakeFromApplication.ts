import { createEmptyIntakeFormState, type IntakeFormState } from '@/lib/intake/intakeTypes'
import {
  inferLoanPurposeCodeFromLegacyText,
  inferOccupationCodeFromLegacyText,
} from '@/lib/intake/intakeOptionCatalogs'
import type { ApplicationResponse } from '@/types/application'

/**
 * Fills the borrower intake form from a GET /applications response (own application only).
 * Unknown keys in JSON are ignored; missing fields default from empty state.
 */
export function hydrateIntakeFormFromApplication(
  app: ApplicationResponse,
  base: IntakeFormState = createEmptyIntakeFormState(),
): IntakeFormState {
  const pi = (app.personalInfo ?? {}) as Record<string, unknown>
  const bi = (app.businessInfo ?? {}) as Record<string, unknown>
  const fi = (app.financialInfo ?? {}) as Record<string, unknown>
  const col = (app.collateralInfo ?? {}) as Record<string, unknown>
  const customFields =
    pi.customFields && typeof pi.customFields === 'object' && !Array.isArray(pi.customFields)
      ? (pi.customFields as Record<string, unknown>)
      : {}
  const str = (o: Record<string, unknown>, k: string) =>
    o[k] != null && o[k] !== undefined ? String(o[k]) : ''

  const toBool = (o: Record<string, unknown>, k: string) => o[k] === true
  const toChoice = (v: unknown): IntakeFormState['hasExistingLoans'] =>
    v === 'yes' || v === 'no' ? v : ''

  const invoiceOnboardingChoice: IntakeFormState['invoiceOnboardingChoice'] =
    app.intakeSegment === 'ANCHOR' ? 'ANCHOR' : app.intakeSegment === 'BORROWER' ? 'BORROWER' : ''

  // Category-governed lifecycle: external product mapping is pinned before openLoanAccount.
  // If that pin isn't present yet, clear any draft workflow-derived LMS code for UI authority separation.
  const categoryGoverned =
    app.workflowResolutionSource === 'CATEGORY_SELECTION' || app.categorySelectionState === 'CATEGORY_SELECTED'
  const externalMappingPinned = Boolean(app.externalProductMappingId)

  return {
    ...base,
    borrowerType: (app.borrowerType as IntakeFormState['borrowerType']) ?? base.borrowerType,
    loanProduct: app.loanProduct ?? base.loanProduct,
    workflowId: app.workflowId ?? base.workflowId,
    customFieldValues: Object.fromEntries(
      Object.entries(customFields).map(([k, v]) => [k, typeof v === 'boolean' ? v : String(v ?? '')]),
    ),
    invoiceOnboardingChoice,
    purpose: str(pi, 'purpose') || base.purpose,
    loanPurpose:
      str(pi, 'loanPurpose') ||
      inferLoanPurposeCodeFromLegacyText(str(pi, 'purpose')) ||
      base.loanPurpose,
    occupation:
      str(pi, 'occupation') ||
      inferOccupationCodeFromLegacyText(str(pi, 'occupationIndustry')) ||
      base.occupation,
    requestedAmount: app.requestedAmount != null ? String(app.requestedAmount) : base.requestedAmount,
    tenureMonths: app.tenureMonths != null ? String(app.tenureMonths) : base.tenureMonths,
    lmsProductCode:
      categoryGoverned && !externalMappingPinned ? '' : app.lmsProductCode?.trim() || base.lmsProductCode,
    lmsTenureUnit: app.lmsTenureUnit?.trim() || base.lmsTenureUnit,
    externalProductMappingId: app.externalProductMappingId ?? base.externalProductMappingId,
    externalProductMappingVersion:
      app.externalProductMappingVersion != null ? String(app.externalProductMappingVersion) : base.externalProductMappingVersion,
    externalSystem: app.externalSystem?.trim() || base.externalSystem,
    externalProductCode: app.externalProductCode?.trim() || base.externalProductCode,
    fullName: str(pi, 'fullName') || base.fullName,
    mobile: str(pi, 'mobile') || base.mobile,
    email: str(pi, 'email') || base.email,
    dateOfBirth: str(pi, 'dateOfBirth') || base.dateOfBirth,
    addressLine: str(pi, 'addressLine') || str(pi, 'address') || base.addressLine,
    city: str(pi, 'city') || base.city,
    state: str(pi, 'state') || base.state,
    pincode: str(pi, 'pincode') || str(pi, 'postalCode') || base.pincode,
    addressLine2: str(pi, 'addressLine2') || base.addressLine2,
    businessName: str(bi, 'businessName') || base.businessName,
    contactPersonName:
      str(bi, 'contactPersonName') || str(pi, 'fullName') || str(pi, 'name') || base.contactPersonName,
    contactMobile:
      str(bi, 'contactMobile') ||
      str(pi, 'mobile') ||
      str(pi, 'phone') ||
      str(pi, 'borrowerMobile') ||
      base.contactMobile,
    contactEmail:
      str(bi, 'contactEmail') ||
      str(pi, 'email') ||
      str(pi, 'contactEmail') ||
      str(pi, 'borrowerEmail') ||
      base.contactEmail,
    gstin: str(bi, 'gstin') || str(pi, 'gstin') || base.gstin,
    udyam: str(bi, 'udyam') || str(bi, 'udyamNumber') || str(pi, 'udyam') || base.udyam,
    businessAddress: str(bi, 'addressLine') || str(bi, 'businessAddress') || base.businessAddress,
    businessCity: str(bi, 'city') || base.businessCity,
    businessState: str(bi, 'state') || base.businessState,
    businessPincode: str(bi, 'pincode') || base.businessPincode,
    panNumber: str(pi, 'panNumber') || str(pi, 'pan') || base.panNumber,
    voterId: str(pi, 'voterId') || str(pi, 'epicNo') || base.voterId,
    dlNumber: str(pi, 'dlNumber') || str(pi, 'dlNo') || base.dlNumber,
    aadhaar:
      str(pi, 'aadhaar') ||
      str(pi, 'aadhaarNumber') ||
      (str(pi, 'aadhaarLast4') ? `XXXX-XXXX-${str(pi, 'aadhaarLast4')}` : '') ||
      base.aadhaar,
    cin: str(bi, 'cin') || base.cin,
    mobileLinkedAadhaar: toBool(pi, 'mobileLinkedAadhaar') || base.mobileLinkedAadhaar,
    occupationIndustry: str(pi, 'occupationIndustry') || base.occupationIndustry,
    workExperienceYears: str(pi, 'workExperienceYears') || base.workExperienceYears,
    bankAccountNumber: str(pi, 'bankAccountNumber') || str(pi, 'accountNumber') || base.bankAccountNumber,
    ifscCode: str(pi, 'ifscCode') || str(pi, 'ifsc') || base.ifscCode,
    bankName: str(pi, 'bankName') || base.bankName,
    employmentType: str(pi, 'employmentType') || str(fi, 'employmentType') || base.employmentType,
    employerName: str(pi, 'employerName') || str(fi, 'employerName') || base.employerName,
    monthlyNetIncome: str(fi, 'monthlyNetIncome') || str(pi, 'monthlyNetIncome') || base.monthlyNetIncome,
    gender: str(pi, 'gender') || base.gender,
    maritalStatus: str(pi, 'maritalStatus') || base.maritalStatus,
    addressProofType: str(pi, 'addressProofType') || base.addressProofType,
    hasExistingLoans: toChoice(pi.hasExistingLoans) || base.hasExistingLoans,
    existingLoansDetails: str(pi, 'existingLoansDetails') || base.existingLoansDetails,
    consentKyc: toBool(fi, 'consentKyc') || toBool(pi, 'consentKyc') || base.consentKyc,
    consentBureau: toBool(fi, 'consentBureau') || toBool(pi, 'consentBureau') || base.consentBureau,
    consentAccountAggregator: toBool(fi, 'consentAccountAggregator') || base.consentAccountAggregator,
    consentComms: toBool(fi, 'consentComms') || toBool(pi, 'consentComms') || base.consentComms,
    collateralPropertyType: str(col, 'propertyType') || str(col, 'collateralPropertyType') || base.collateralPropertyType,
    collateralPropertyAddress: str(col, 'propertyAddress') || str(col, 'collateralPropertyAddress') || base.collateralPropertyAddress,
    selectedSubProgramId: app.subProgramId ?? base.selectedSubProgramId,
    dependencyVintagePercent: str(bi, 'dependencyVintagePercent') || base.dependencyVintagePercent,
    anchorRelationshipVintageMonths:
      str(bi, 'anchorRelationshipVintageMonths') || base.anchorRelationshipVintageMonths,
  }
}
