import { isInvoiceDiscountingProduct, isLoanProductCode } from '@/catalog/loanProducts'
import { isCityInIndianState, isKnownIndianState } from '@/data/geo/indiaGeo'
import { estimatedValueForSecuredProduct } from './collateralIntakePayload'
import type { IntakeFormState, IntakeMode } from './intakeTypes'
import { isBusinessBorrowerType } from './intakeTypes'
import { allDocumentSlotsForIntake, documentSlotsForBorrowerType } from './intakeDocumentSlots'
import { COLLATERAL_DOC, collateralDocumentTypesForKind, detectSecuredCollateralKind } from './securedProducts'
import type { WorkflowConfigResponse } from '@/types/workflow'
import { matchingWorkflowsForBorrowerType, matchingWorkflowsForProduct, uniqueActiveWorkflowLoanProducts, productsForBorrowerType } from '@/utils/workflowProducts'
import { ANCHOR_BORROWER_TYPE } from '@/lib/intake/anchorIntakeConstants'
import {
  activeWorkflowForProduct,
  isWorkflowDrivenIntake,
  missingRequiredWorkflowDocuments,
  resolveWorkflowAllowedStates,
  isStateAllowedByWorkflow,
  shouldCollectLoanPurposeField,
  validateWorkflowAge,
  validateWorkflowKycStep,
  validateWorkflowLoanPurpose,
  validateWorkflowOccupation,
  validateWorkflowPersonalFields,
  validateWorkflowTenure,
} from '@/lib/workflow/workflowIntakeRules'
import { resolveLoanPurposeOptions, resolveOccupationOptions } from '@/lib/intake/intakeOptionCatalogs'

export { productsForBorrowerType }
export type { WorkflowConfigResponse }

const PAN_RE = /^[A-Z]{5}[0-9]{4}[A-Z]$/i
const INDIAN_PIN_RE = /^\d{6}$/
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export type IntakeLocationStrictness = 'staff_basic' | 'borrower_address'

/** PIN + state/city rules for intake (staff vs borrower address step). */
export function validateIntakeLocation(
  state: string,
  city: string,
  pincode: string,
  strictness: IntakeLocationStrictness,
  allowedStates?: string[] | null,
): string | null {
  const pc = pincode.replace(/\D/g, '')
  if (!INDIAN_PIN_RE.test(pc)) {
    return 'Enter a valid 6-digit Indian PIN code.'
  }
  const st = state.trim()
  const ct = city.trim()

  if (strictness === 'borrower_address') {
    if (!st) return 'Select your state.'
    if (!ct) return 'Select your city.'
  } else {
    if (ct && !st) return 'Select a state before choosing a city.'
  }

  if (st && !isStateAllowedByWorkflow(st, allowedStates ?? null)) {
    return 'Select a state configured for this workflow.'
  }

  if (st && isKnownIndianState(st)) {
    if (!ct) {
      return strictness === 'borrower_address' ? 'Select your city.' : 'Select a city for the chosen state.'
    }
    if (!isCityInIndianState(st, ct)) {
      return 'Choose a city from the list for the selected state.'
    }
  } else if (st && strictness === 'staff_basic' && !ct) {
    return 'Enter a city, or choose a standard state from the list to pick a city.'
  }

  return null
}

function normalizeMobile(s: string): string {
  return s.replace(/\D/g, '')
}

function parseAmount(s: string): number | null {
  const n = Number.parseFloat(s)
  if (Number.isNaN(n)) return null
  return n
}

function validateRequiredEmail(email: string): string | null {
  const trimmed = email.trim()
  if (!trimmed) {
    return 'Email is required.'
  }
  if (!EMAIL_RE.test(trimmed)) {
    return 'Please enter a valid email address.'
  }
  return null
}

export function validateProductStep(
  s: IntakeFormState,
  mode: IntakeMode,
  activeWorkflows: WorkflowConfigResponse[],
): string | null {
  const globalProducts = uniqueActiveWorkflowLoanProducts(activeWorkflows)
  if (globalProducts.length === 0) {
    return 'No active loan product is available. Ask an admin to configure a workflow.'
  }
  if (!s.loanProduct || !globalProducts.includes(s.loanProduct)) {
    return 'Select a loan product from the list of active workflows.'
  }
  if (!isLoanProductCode(s.loanProduct)) {
    return 'Select a valid loan product.'
  }

  if (isInvoiceDiscountingProduct(s.loanProduct)) {
    const onboardingChoice =
      mode === 'BORROWER_SELF_SERVICE' ? 'BORROWER' : s.invoiceOnboardingChoice
    if (mode !== 'BORROWER_SELF_SERVICE' && onboardingChoice !== 'BORROWER' && onboardingChoice !== 'ANCHOR') {
      return 'For invoice discounting, select onboarding type: Borrower or Anchor.'
    }
    if (onboardingChoice === 'BORROWER' || mode === 'BORROWER_SELF_SERVICE') {
      const list = productsForBorrowerType(activeWorkflows, s.borrowerType)
      if (list.length === 0) {
        return 'No active borrower workflow for invoice discounting and this borrower type. Ask an admin to activate a BORROWER-segment workflow.'
      }
      if (!list.some((w) => w.loanProduct === s.loanProduct)) {
        return 'The selected borrower type does not have an active borrower workflow for invoice discounting.'
      }
    } else if (onboardingChoice === 'ANCHOR') {
      const list = matchingWorkflowsForProduct(
        activeWorkflows,
        ANCHOR_BORROWER_TYPE,
        s.loanProduct,
        'ANCHOR',
      )
      if (list.length === 0) {
        return 'No active anchor workflow for invoice discounting. Ask an admin to activate an ANCHOR-segment workflow.'
      }
    }
  } else {
    const list = productsForBorrowerType(activeWorkflows, s.borrowerType)
    if (list.length === 0) {
      return 'No active loan product is available for the selected borrower type. Ask an admin to configure a workflow.'
    }
    if (!list.some((w) => w.loanProduct === s.loanProduct)) {
      return 'Select a loan product from the list of active workflows for this borrower type.'
    }
  }

  const intakeSegment: 'BORROWER' | 'ANCHOR' =
    isInvoiceDiscountingProduct(s.loanProduct) &&
    mode !== 'BORROWER_SELF_SERVICE' &&
    s.invoiceOnboardingChoice === 'ANCHOR'
      ? 'ANCHOR'
      : 'BORROWER'
  const workflowBorrowerType = intakeSegment === 'ANCHOR' ? ANCHOR_BORROWER_TYPE : s.borrowerType
  const matchingWorkflows = matchingWorkflowsForProduct(
    activeWorkflows,
    workflowBorrowerType,
    s.loanProduct,
    intakeSegment,
  )
  if (matchingWorkflows.length === 0) {
    return 'No active workflow catalogue exists for this product. Ask an admin to configure Customer Category.'
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
  const workflow = activeWorkflowForProduct(
    activeWorkflows,
    workflowBorrowerType,
    s.loanProduct,
    s.workflowId,
    intakeSegment,
  )
  const tenureErr = validateWorkflowTenure(s, workflow)
  if (tenureErr) return tenureErr
  if (intakeSegment !== 'ANCHOR') {
    const loanPurposeErr = validateLoanPurposeSelection(s, workflow)
    if (loanPurposeErr) return loanPurposeErr
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

/**
 * Minimal fields for staff "Save draft & notify borrower" — does not require full address / KYC / docs.
 */
export function validateNotifyBasics(
  s: IntakeFormState,
  mode: IntakeMode,
  activeWorkflows: WorkflowConfigResponse[],
  opts?: { needPlpProgram?: boolean },
): string | null {
  const globalProducts = uniqueActiveWorkflowLoanProducts(activeWorkflows)
  if (globalProducts.length === 0) {
    return 'No active loan product is available. Ask an admin to configure a workflow.'
  }
  if (!s.loanProduct || !globalProducts.includes(s.loanProduct)) {
    return 'Select a loan product before notifying the borrower.'
  }
  if (!s.borrowerType) {
    return 'Select a borrower type before notifying the borrower.'
  }
  const matchingWorkflows = matchingWorkflowsForBorrowerType(activeWorkflows, s.borrowerType).filter(
    (w) => w.loanProduct === s.loanProduct,
  )
  if (matchingWorkflows.length === 0) {
    return 'No active workflow catalogue exists for this product. Ask an admin to configure Customer Category.'
  }

  if (isInvoiceDiscountingProduct(s.loanProduct)) {
    const onboardingChoice =
      mode === 'BORROWER_SELF_SERVICE' ? 'BORROWER' : s.invoiceOnboardingChoice
    if (mode !== 'BORROWER_SELF_SERVICE' && onboardingChoice !== 'BORROWER' && onboardingChoice !== 'ANCHOR') {
      return 'Select onboarding type (Borrower or Anchor) before notifying.'
    }
    if (onboardingChoice === 'ANCHOR') {
      return 'Anchor onboarding cannot be delegated to the borrower portal.'
    }
    if (opts?.needPlpProgram !== false && !s.selectedSubProgramId?.trim()) {
      return 'Select an anchor program before notifying the borrower.'
    }
  }

  if (s.borrowerType === 'INDIVIDUAL') {
    if (!s.fullName.trim()) return 'Enter the borrower’s name before notifying.'
    const mobile =
      normalizeMobile(s.mobile) || (mode === 'SALES_ASSISTED' ? normalizeMobile(s.borrowerMobile) : '')
    if (mobile.length < 10) {
      return 'Enter a valid mobile number before notifying.'
    }
    const emailError = validateRequiredEmail(s.email)
    if (emailError) return emailError
  } else {
    if (!s.businessName.trim()) return 'Enter the business or entity name before notifying.'
    if (!s.contactPersonName.trim()) return 'Enter a contact person name before notifying.'
    const m = normalizeMobile(s.contactMobile)
    if (m.length < 10) {
      return 'Enter a valid contact mobile before notifying.'
    }
    const email = s.contactEmail.trim()
    if (!email) return 'Enter a contact email before notifying.'
    if (!EMAIL_RE.test(email)) return 'Please enter a valid contact email address.'
  }
  return null
}

export function validateBorrowerStep(
  s: IntakeFormState,
  mode: IntakeMode,
  workflow?: WorkflowConfigResponse | null,
): string | null {
  if (s.borrowerType === 'INDIVIDUAL') {
    if (!s.fullName.trim()) return 'Enter the borrower’s full name as per PAN.'
    const mobile =
      normalizeMobile(s.mobile) || (mode === 'SALES_ASSISTED' ? normalizeMobile(s.borrowerMobile) : '')
    if (mobile.length < 10) {
      return 'Enter a valid mobile number for the borrower (at least 10 digits).'
    }
    const emailError = validateRequiredEmail(s.email)
    if (emailError) return emailError
    const allowedStates = resolveWorkflowAllowedStates(workflow)
    const loc = validateIntakeLocation(s.state, s.city, s.pincode, 'staff_basic', allowedStates)
    if (loc) return loc
    const personalErr = validateWorkflowPersonalFields(s, workflow)
    if (personalErr) return personalErr
    const ageErr = validateWorkflowAge(s, workflow)
    if (ageErr) return ageErr
    const occupationErr = validateOccupationSelection(s, workflow)
    if (occupationErr) return occupationErr
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
    const allowedStates = resolveWorkflowAllowedStates(workflow)
    const loc = validateIntakeLocation(s.businessState, s.businessCity, s.businessPincode, 'staff_basic', allowedStates)
    if (loc) return loc
  }
  const vintageErr = validateInvoiceDiscountingVintageStep(s, mode)
  if (vintageErr) return vintageErr
  return null
}

export function validateKycStep(
  s: IntakeFormState,
  workflow?: WorkflowConfigResponse | null,
): string | null {
  if (workflow && isWorkflowDrivenIntake(workflow)) {
    return validateWorkflowKycStep(s, workflow)
  }
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
    .filter((slot) => slot.required !== false && slot.documentType !== 'OTHER')
    .filter((slot) => !s.documentUploaded[slot.documentType])
    .map((slot) => slot.documentType)
}

/** Includes collateral document slots when the selected product is secured. */
export function missingIntakeDocumentTypes(
  s: IntakeFormState,
  workflow?: WorkflowConfigResponse | null,
): string[] {
  if (workflow && isWorkflowDrivenIntake(workflow)) {
    return missingRequiredWorkflowDocuments(s, workflow, s.borrowerType)
  }
  const slots = allDocumentSlotsForIntake(s)
  return slots
    .filter((slot) => slot.required !== false && slot.documentType !== 'OTHER' && slot.documentType !== 'COLLATERAL_OTHER')
    .filter((slot) => !s.documentUploaded[slot.documentType])
    .map((slot) => slot.documentType)
}

const IFSC_RE = /^[A-Z]{4}0[A-Z0-9]{6}$/i

export function validateLoanPurposeSelection(
  s: IntakeFormState,
  workflow: WorkflowConfigResponse | null | undefined,
): string | null {
  if (isInvoiceDiscountingProduct(s.loanProduct) && s.invoiceOnboardingChoice === 'ANCHOR') {
    return null
  }
  if (isWorkflowDrivenIntake(workflow)) {
    return validateWorkflowLoanPurpose(s, workflow)
  }
  if (!shouldCollectLoanPurposeField(workflow, true)) {
    return null
  }
  if (!s.loanPurpose.trim()) {
    return 'Select a loan purpose.'
  }
  const ok = resolveLoanPurposeOptions(workflow).some((o) => o.value === s.loanPurpose.trim())
  if (!ok) {
    return 'Select a valid loan purpose option.'
  }
  return null
}

export function validateOccupationSelection(
  s: IntakeFormState,
  workflow: WorkflowConfigResponse | null | undefined,
): string | null {
  if (s.borrowerType !== 'INDIVIDUAL') {
    return null
  }
  if (isWorkflowDrivenIntake(workflow)) {
    return validateWorkflowOccupation(s, workflow)
  }
  if (!s.occupation.trim()) {
    return 'Select occupation.'
  }
  const ok = resolveOccupationOptions(workflow).some((o) => o.value === s.occupation.trim())
  if (!ok) {
    return 'Select a valid occupation option.'
  }
  return null
}

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
  return null
}

export function validateBorrowerPersonalAddressStep(
  s: IntakeFormState,
  workflow?: WorkflowConfigResponse | null,
): string | null {
  if (workflow?.intakeConfig?.policy === 'WORKFLOW_DRIVEN') {
    const personalErr = validateWorkflowPersonalFields(s, workflow)
    if (personalErr) return personalErr
    const ageErr = validateWorkflowAge(s, workflow)
    if (ageErr) return ageErr
    if (!s.fullName.trim()) return 'Enter your full name as per PAN.'
    if (normalizeMobile(s.mobile).length < 10) {
      return 'Enter a valid mobile number (at least 10 digits).'
    }
    const emailError = validateRequiredEmail(s.email)
    if (emailError) return emailError
    if (!s.addressLine.trim()) return 'Enter your address (line 1).'
    const loc = validateIntakeLocation(
      s.state,
      s.city,
      s.pincode,
      'borrower_address',
      resolveWorkflowAllowedStates(workflow),
    )
    if (loc) return loc
    if (!s.maritalStatus.trim()) return 'Select your marital status.'
    if (!s.addressProofType.trim()) return 'Select the type of address proof you can provide.'
    const occupationErr = validateOccupationSelection(s, workflow)
    if (occupationErr) return occupationErr
    return null
  }
  if (!s.fullName.trim()) return 'Enter your full name as per PAN.'
  if (normalizeMobile(s.mobile).length < 10) {
    return 'Enter a valid mobile number (at least 10 digits).'
  }
  if (!s.dateOfBirth.trim()) return 'Enter your date of birth.'
  const emailError = validateRequiredEmail(s.email)
  if (emailError) return emailError
  if (!s.addressLine.trim()) return 'Enter your address (line 1).'
  const loc = validateIntakeLocation(
    s.state,
    s.city,
    s.pincode,
    'borrower_address',
    resolveWorkflowAllowedStates(workflow),
  )
  if (loc) return loc
  if (!s.gender.trim()) return 'Select your gender (or “prefer not to say”).'
  if (!s.maritalStatus.trim()) return 'Select your marital status.'
  if (!s.addressProofType.trim()) return 'Select the type of address proof you can provide.'
  const occupationErr = validateOccupationSelection(s, workflow)
  if (occupationErr) return occupationErr
  return null
}

/** Aadhaar last 4 or full 12, or left blank in borrower journey. */
export function validateBorrowerBankKycStep(
  s: IntakeFormState,
  workflow?: WorkflowConfigResponse | null,
): string | null {
  if (workflow && isWorkflowDrivenIntake(workflow)) {
    return validateWorkflowKycStep(s, workflow)
  }
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
  if (kind === 'VEHICLE') {
    if (!s.collateralVehicleType) return 'Select the vehicle type.'
    if (!s.collateralVehicleMakeModel.trim()) return 'Enter the vehicle make and model.'
    if (!s.collateralVehicleYear.trim()) return 'Enter the year of manufacture.'
    if (!s.collateralVehicleRegistrationNumber.trim()) return 'Enter the vehicle registration number.'
    if (!s.collateralVehicleExistingLoan) return 'Indicate if there is an existing loan on this vehicle.'
  }
  if (kind === 'FIXED_DEPOSIT') {
    if (!s.collateralFdBankName.trim()) return 'Enter the bank name for the fixed deposit.'
    if (!s.collateralFdAccountNumber.trim()) return 'Enter the FD account number.'
    if (!s.collateralFdMaturityDate.trim()) return 'Enter the FD maturity date.'
    if (!s.collateralFdReceiptNumber.trim()) return 'Enter the FD receipt number.'
  }
  if (kind === 'MACHINERY') {
    if (!s.collateralMachineryTypeDescription.trim()) return 'Enter the machinery type or description.'
    if (!s.collateralMachineryMakeModel.trim()) return 'Enter the machinery make and model.'
    if (!s.collateralMachineryYearOfPurchase.trim()) return 'Enter the year of purchase.'
    if (!s.collateralMachineryLocationAddress.trim()) return 'Enter the machinery location or address.'
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

export function validateInvoiceDiscountingVintageStep(s: IntakeFormState, mode: IntakeMode): string | null {
  if (!isInvoiceDiscountingProduct(s.loanProduct)) return null
  const onboardingChoice = mode === 'BORROWER_SELF_SERVICE' ? 'BORROWER' : s.invoiceOnboardingChoice
  if (onboardingChoice === 'ANCHOR') return null
  const dep = s.dependencyVintagePercent.trim()
  if (!dep) return 'Enter dependency vintage (%).'
  const depN = Number.parseFloat(dep)
  if (Number.isNaN(depN) || depN < 0) return 'Dependency vintage must be 0 or greater.'
  const anchorMo = s.anchorRelationshipVintageMonths.trim()
  if (!anchorMo) return 'Enter anchor relationship vintage (months).'
  const anchorN = Number.parseInt(anchorMo, 10)
  if (Number.isNaN(anchorN) || anchorN < 0) {
    return 'Anchor relationship vintage must be a whole number of months (0 or greater).'
  }
  return null
}
