import { createEmptyIntakeFormState, type IntakeFormState } from '@/lib/intake/intakeTypes'
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
  const str = (o: Record<string, unknown>, k: string) =>
    o[k] != null && o[k] !== undefined ? String(o[k]) : ''

  const toBool = (o: Record<string, unknown>, k: string) => o[k] === true

  return {
    ...base,
    borrowerType: (app.borrowerType as IntakeFormState['borrowerType']) ?? base.borrowerType,
    loanProduct: app.loanProduct ?? base.loanProduct,
    requestedAmount: app.requestedAmount != null ? String(app.requestedAmount) : base.requestedAmount,
    tenureMonths: app.tenureMonths != null ? String(app.tenureMonths) : base.tenureMonths,
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
    businessAddress: str(bi, 'businessAddress') || base.businessAddress,
    panNumber: str(pi, 'panNumber') || str(pi, 'pan') || base.panNumber,
    aadhaar: str(pi, 'aadhaar') || str(pi, 'aadhaarNumber') || base.aadhaar,
    bankAccountNumber: str(pi, 'bankAccountNumber') || str(pi, 'accountNumber') || base.bankAccountNumber,
    ifscCode: str(pi, 'ifscCode') || str(pi, 'ifsc') || base.ifscCode,
    bankName: str(pi, 'bankName') || base.bankName,
    employmentType: str(pi, 'employmentType') || str(fi, 'employmentType') || base.employmentType,
    employerName: str(pi, 'employerName') || str(fi, 'employerName') || base.employerName,
    monthlyNetIncome: str(fi, 'monthlyNetIncome') || str(pi, 'monthlyNetIncome') || base.monthlyNetIncome,
    gender: str(pi, 'gender') || base.gender,
    maritalStatus: str(pi, 'maritalStatus') || base.maritalStatus,
    consentKyc: toBool(fi, 'consentKyc') || toBool(pi, 'consentKyc') || base.consentKyc,
    consentBureau: toBool(fi, 'consentBureau') || toBool(pi, 'consentBureau') || base.consentBureau,
    consentAccountAggregator: toBool(fi, 'consentAccountAggregator') || base.consentAccountAggregator,
    consentComms: toBool(fi, 'consentComms') || toBool(pi, 'consentComms') || base.consentComms,
    collateralPropertyType: str(col, 'propertyType') || str(col, 'collateralPropertyType') || base.collateralPropertyType,
    collateralPropertyAddress: str(col, 'propertyAddress') || str(col, 'collateralPropertyAddress') || base.collateralPropertyAddress,
  }
}
