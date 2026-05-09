import { isLoanProductCode } from '@/catalog/loanProducts'
import { estimatedValueForSecuredProduct } from './collateralIntakePayload'
import type { IntakeFormState, IntakeMode } from './intakeTypes'
import { isBusinessBorrowerType } from './intakeTypes'
import { allDocumentSlotsForIntake, documentSlotsForBorrowerType } from './intakeDocumentSlots'
import { COLLATERAL_DOC, collateralDocumentTypesForKind, detectSecuredCollateralKind } from './securedProducts'
import type { WorkflowConfigResponse } from '@/types/workflow'
import { productsForBorrowerType } from '@/utils/workflowProducts'

export { productsForBorrowerType }
export type { WorkflowConfigResponse }

const PAN_RE = /^[A-Z]{5}[0-9]{4}[A-Z]$/i

function normalizeMobile(s: string): string {
  return s.replace(/\D/g, '')
}

function parseAmount(s: string): number | null {
  const n = Number.parseFloat(s)
  if (Number.isNaN(n)) return null
  return n
}

export function validateProductStep(
  s: IntakeFormState,
  mode: IntakeMode,
  activeWorkflows: WorkflowConfigResponse[],
): string | null {
  const list = productsForBorrowerType(activeWorkflows, s.borrowerType)
  if (list.length === 0) {
    return 'No active loan product is available for the selected borrower type. Ask an admin to configure a workflow.'
  }
  if (!s.loanProduct || !list.some((w) => w.loanProduct === s.loanProduct)) {
    return 'Select a loan product from the list of active workflows.'
  }
  if (!isLoanProductCode(s.loanProduct)) {
    return 'Select a valid loan product.'
  }
  const amount = parseAmount(s.requestedAmount)
  if (amount == null || amount <= 0) {
    return 'Enter a valid requested amount greater than zero.'
  }
  const tm = s.tenureMonths.trim()
  if (tm) {
    const t = Number.parseInt(tm, 10)
    if (Number.isNaN(t) || t <= 0) {
      return 'Tenure must be a positive whole number of months, or leave it blank.'
    }
  }
  if (mode === 'SALES_ASSISTED') {
    if (!s.salesOfficerName.trim()) {
      return 'Enter the assisting sales officer name.'
    }
    if (normalizeMobile(s.borrowerMobile).length < 10) {
      return 'Enter the borrower’s mobile number (at least 10 digits).'
    }
    if (!s.salesBorrowerAck) {
      return 'Confirm that the borrower has agreed to start this application and share details with the lender.'
    }
  }
  return null
}

export function validateBorrowerStep(s: IntakeFormState, mode: IntakeMode): string | null {
  if (s.borrowerType === 'INDIVIDUAL') {
    if (!s.fullName.trim()) return 'Enter the borrower’s full name as per PAN.'
    const mobile =
      normalizeMobile(s.mobile) || (mode === 'SALES_ASSISTED' ? normalizeMobile(s.borrowerMobile) : '')
    if (mobile.length < 10) {
      return 'Enter a valid mobile number for the borrower (at least 10 digits).'
    }
  } else {
    if (!s.businessName.trim()) {
      return 'Enter the business or entity name.'
    }
    if (!s.contactPersonName.trim()) {
      return 'Enter a contact person name for this business application.'
    }
    const m = normalizeMobile(s.contactMobile)
    if (m.length < 10) {
      return 'Enter a valid mobile number for the contact person (at least 10 digits).'
    }
  }
  return null
}

export function validateKycStep(s: IntakeFormState): string | null {
  if (!s.panNumber.trim()) {
    return 'PAN is required.'
  }
  if (!PAN_RE.test(s.panNumber.trim())) {
    return 'Enter a valid 10-character PAN (e.g. ABCDE1234F).'
  }
  const a = s.aadhaar.replace(/\D/g, '')
  if (a.length !== 4 && a.length !== 12) {
    return 'Enter the last 4 digits of Aadhaar, or the full 12-digit number (internal entry).'
  }
  if (isBusinessBorrowerType(s.borrowerType)) {
    if (!s.gstin.trim()) {
      return 'Enter GSTIN for this business (required for business borrowers).'
    }
    if (s.borrowerType === 'COMPANY' && !s.cin.trim()) {
      return 'Enter CIN / MCA company identification for a company application.'
    }
  }
  return null
}

export function validateConsentStep(s: IntakeFormState): string | null {
  if (!s.consentKyc || !s.consentBureau || !s.consentAccountAggregator || !s.consentComms) {
    return 'Please accept all consent items before continuing. We need these to run verification and to contact you about this application.'
  }
  return null
}

export function allConsentsChecked(s: IntakeFormState): boolean {
  return s.consentKyc && s.consentBureau && s.consentAccountAggregator && s.consentComms
}

export function missingDocumentTypes(s: IntakeFormState): string[] {
  const slots = documentSlotsForBorrowerType(s.borrowerType)
  return slots
    .filter((slot) => slot.documentType !== 'OTHER')
    .filter((slot) => !s.documentUploaded[slot.documentType])
    .map((slot) => slot.documentType)
}

/** Includes collateral document slots when the selected product is secured. */
export function missingIntakeDocumentTypes(s: IntakeFormState): string[] {
  const slots = allDocumentSlotsForIntake(s)
  return slots
    .filter((slot) => slot.documentType !== 'OTHER' && slot.documentType !== 'COLLATERAL_OTHER')
    .filter((slot) => !s.documentUploaded[slot.documentType])
    .map((slot) => slot.documentType)
}

const IFSC_RE = /^[A-Z]{4}0[A-Z0-9]{6}$/i

/**
 * Product step + “existing loans” for borrower 6-step journey (INDIVIDUAL only).
 */
export function validateBorrowerProductStep(
  s: IntakeFormState,
  activeWorkflows: WorkflowConfigResponse[],
): string | null {
  const v = validateProductStep(s, 'BORROWER_SELF_SERVICE', activeWorkflows)
  if (v) return v
  if (s.hasExistingLoans === 'yes' && !s.existingLoansDetails.trim()) {
    return 'Please describe your existing loan(s) or set “no existing loans”.'
  }
  if (!s.hasExistingLoans) {
    return 'Indicate whether you have other loans running.'
  }
  if (!s.purpose.trim()) {
    return 'Enter a short purpose for the loan (how you plan to use the amount).'
  }
  return null
}

export function validateBorrowerPersonalAddressStep(s: IntakeFormState): string | null {
  if (!s.fullName.trim()) return 'Enter your full name as per PAN.'
  if (normalizeMobile(s.mobile).length < 10) {
    return 'Enter a valid mobile number (at least 10 digits).'
  }
  if (!s.dateOfBirth.trim()) return 'Enter your date of birth.'
  if (!s.email.trim() || !s.email.includes('@')) {
    return 'Enter a valid email address.'
  }
  if (!s.addressLine.trim()) return 'Enter your address (line 1).'
  if (!s.city.trim()) return 'Enter your city.'
  if (!s.state.trim()) return 'Enter your state.'
  const pc = s.pincode.replace(/\D/g, '')
  if (pc.length !== 6) return 'Enter a 6-digit PIN code.'
  if (!s.gender.trim()) return 'Select your gender (or “prefer not to say”).'
  if (!s.maritalStatus.trim()) return 'Select your marital status.'
  if (!s.addressProofType.trim()) return 'Select the type of address proof you can provide.'
  return null
}

/** Aadhaar last 4 or full 12, or left blank in borrower journey. */
export function validateBorrowerBankKycStep(s: IntakeFormState): string | null {
  if (!s.panNumber.trim()) {
    return 'PAN is required.'
  }
  if (!PAN_RE.test(s.panNumber.trim())) {
    return 'Enter a valid 10-character PAN (e.g. ABCDE1234F).'
  }
  const a = s.aadhaar.replace(/\D/g, '')
  if (a.length > 0 && a.length !== 4 && a.length !== 12) {
    return 'Enter the last 4 digits of Aadhaar, the full 12-digit number, or leave blank for now.'
  }
  const acct = s.bankAccountNumber.replace(/\D/g, '')
  if (acct.length < 5) {
    return 'Enter a valid bank account number (at least 5 digits).'
  }
  if (!IFSC_RE.test(s.ifscCode.trim())) {
    return 'Enter a valid 11-character IFSC (e.g. HDFC0XXXXXX).'
  }
  if (!s.bankName.trim()) {
    return 'Enter the name of your bank as printed on the cheque or passbook.'
  }
  return null
}

/** For LAP / Loan Against Shares / Gold Loan when `loanProduct` matches {@link detectSecuredCollateralKind}. */
export function validateCollateralIntakeStep(s: IntakeFormState): string | null {
  const kind = detectSecuredCollateralKind(s.loanProduct)
  if (!kind) return null
  const ev = estimatedValueForSecuredProduct(s, kind)
  if (ev == null || ev <= 0) {
    return 'Enter a valid estimated value for the collateral in INR.'
  }
  if (kind === 'PROPERTY') {
    if (!s.collateralPropertyType.trim()) return 'Enter the type of property offered as security.'
    if (!s.collateralPropertyAddress.trim()) return 'Enter the full address of the property.'
    if (!s.collateralOwnershipType.trim()) return 'Enter how the property is held (e.g. sole, joint, leasehold).'
    if (!s.collateralExistingMortgage) return 'Indicate if there is an existing mortgage or encumbrance on the property.'
  }
  if (kind === 'SHARES') {
    if (!s.collateralSecurityType.trim()) return 'Enter the type of security (e.g. listed equity, mutual fund units).'
    if (!s.collateralIsin.trim()) return 'Enter the ISIN of the security.'
    if (!s.collateralCompanyOrFundName.trim()) return 'Enter the company or fund name.'
    if (!s.collateralShareQuantity.trim()) return 'Enter the quantity of units or shares.'
    if (!s.collateralDematAccountNumber.trim()) return 'Enter your demat account number.'
    if (!s.collateralPledgeConsent) return 'Please confirm consent to pledge the securities for this loan.'
  }
  if (kind === 'GOLD') {
    if (!s.collateralGoldType.trim()) return 'Enter the type of gold (e.g. jewellery, coin, bar).'
    const w = s.collateralGoldGrossWeight.trim() || s.collateralGoldNetWeight.trim()
    if (!w) return 'Enter approximate gross or net weight.'
    if (!s.collateralGoldPurityKarat.trim()) return 'Enter purity (e.g. 22K) or karat.'
    if (!s.collateralGoldOrnamentDescription.trim()) return 'Describe the item(s) offered as security.'
  }
  return null
}

/** Optional warning: no collateral document uploaded yet. */
export function collateralDocumentMissingWarning(s: IntakeFormState): string | null {
  const kind = detectSecuredCollateralKind(s.loanProduct)
  if (!kind) return null
  const types = collateralDocumentTypesForKind(kind).filter((t) => t !== COLLATERAL_DOC.COLLATERAL_OTHER)
  const any = types.some((t) => s.documentUploaded[t])
  if (!any) {
    return 'We recommend uploading at least one collateral document (e.g. property papers, share holding, or photos). You can still continue.'
  }
  return null
}

export function validateBorrowerIncomeStep(s: IntakeFormState): string | null {
  if (!s.employmentType.trim()) {
    return 'Select how you earn a living (employment type).'
  }
  if (!s.employerName.trim()) {
    return 'Enter your employer or business name.'
  }
  const inc = parseAmount(s.monthlyNetIncome)
  if (inc == null || inc <= 0) {
    return 'Enter a valid monthly take-home or net income in INR.'
  }
  if (!s.occupationIndustry.trim()) {
    return 'Enter your role or industry (brief).'
  }
  const w = s.workExperienceYears.trim()
  if (w) {
    const n = Number.parseInt(w, 10)
    if (Number.isNaN(n) || n < 0) {
      return 'Work experience should be a whole number of years, or leave blank.'
    }
  }
  return null
}
