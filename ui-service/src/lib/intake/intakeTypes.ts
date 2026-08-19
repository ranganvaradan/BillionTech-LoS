import { BORROWER_TYPE_ORDER } from '@/catalog/borrowerTypes'
import { DEFAULT_LMS_TENURE_UNIT } from '@/catalog/lmsTenureUnits'
import type { BorrowerType } from '@/types/createApplication'

export type IntakeMode = 'BORROWER_SELF_SERVICE' | 'SALES_ASSISTED' | 'ADMIN_INTERNAL'

export const BORROWER_TYPES: BorrowerType[] = [...BORROWER_TYPE_ORDER]

export function isBusinessBorrowerType(bt: BorrowerType): boolean {
  return bt === 'PROPRIETOR' || bt === 'PARTNERSHIP' || bt === 'COMPANY'
}

export interface IntakeFormState {
  borrowerType: BorrowerType
  loanProduct: string
  workflowId: string
  customFieldValues: Record<string, string | boolean>
  requestedAmount: string
  tenureMonths: string
  /** Encore LMS product code (prefilled from active workflow). */
  lmsProductCode: string
  /** Encore tenure unit: Day, Month, Week. */
  lmsTenureUnit: string
  /** Pinned External Product Mapping id (category-governed apps only; empty when not yet pinned). */
  externalProductMappingId: string
  /** Pinned External Product Mapping version (as string for UI display; empty when not yet pinned). */
  externalProductMappingVersion: string
  /** External system pinned by External Product Mapping (empty when not yet pinned). */
  externalSystem: string
  /** External product code pinned by External Product Mapping (empty when not yet pinned). */
  externalProductCode: string
  /** Staff only: when loan product is invoice discounting, user must pick Borrower vs Anchor before continuing. */
  invoiceOnboardingChoice: '' | 'BORROWER' | 'ANCHOR'
  /** Invoice discounting borrower: selected PLP sub-program id */
  selectedSubProgramId: string
  /** Invoice discounting borrower: declared dependency on anchor (%) */
  dependencyVintagePercent: string
  /** Invoice discounting borrower: declared anchor relationship vintage (months) */
  anchorRelationshipVintageMonths: string
  purpose: string
  /** Coded loan purpose for scorecard (dropdown). */
  loanPurpose: string
  /** Coded occupation for scorecard (dropdown). */
  occupation: string
  /** Sales-assisted only */
  salesOfficerName: string
  salesOfficerId: string
  borrowerMobile: string
  salesBorrowerAck: boolean
  /** Individual + contact */
  fullName: string
  mobile: string
  email: string
  dateOfBirth: string
  addressLine: string
  city: string
  state: string
  /** Business (basic) */
  businessName: string
  contactPersonName: string
  contactMobile: string
  contactEmail: string
  gstin: string
  udyam: string
  businessAddress: string
  businessCity: string
  businessState: string
  businessPincode: string
  /** KYC */
  panNumber: string
  aadhaar: string
  voterId: string
  dlNumber: string
  mobileLinkedAadhaar: boolean
  cin: string
  /** Borrower self-service (extra; merged into personalInfo / financialInfo) */
  hasExistingLoans: '' | 'yes' | 'no'
  existingLoansDetails: string
  gender: string
  maritalStatus: string
  addressLine2: string
  pincode: string
  addressProofType: string
  bankAccountNumber: string
  ifscCode: string
  bankName: string
  employmentType: string
  employerName: string
  monthlyNetIncome: string
  occupationIndustry: string
  workExperienceYears: string
  /** Consent (stored in financialInfo) */
  consentKyc: boolean
  consentBureau: boolean
  consentAccountAggregator: boolean
  consentComms: boolean
  /** documentType -> uploaded */
  documentUploaded: Record<string, boolean>
  /** Secured products (LAP / LAS / Gold) — persisted under application.collateralInfo.borrowerIntake */
  collateralPropertyType: string
  collateralPropertyAddress: string
  collateralOwnershipType: string
  collateralEstimatedMarketValue: string
  collateralExistingMortgage: '' | 'yes' | 'no'
  collateralSecurityType: string
  collateralIsin: string
  collateralCompanyOrFundName: string
  collateralShareQuantity: string
  collateralShareMarketValue: string
  collateralDematAccountNumber: string
  collateralPledgeConsent: boolean
  collateralGoldType: string
  collateralGoldGrossWeight: string
  collateralGoldNetWeight: string
  collateralGoldPurityKarat: string
  collateralGoldEstimatedValue: string
  collateralGoldOrnamentDescription: string
  /** Vehicle collateral */
  collateralVehicleType: '' | 'TWO_WHEELER' | 'FOUR_WHEELER' | 'COMMERCIAL'
  collateralVehicleMakeModel: string
  collateralVehicleYear: string
  collateralVehicleRegistrationNumber: string
  collateralVehicleEstimatedMarketValue: string
  collateralVehicleExistingLoan: '' | 'yes' | 'no'
  /** Fixed deposit collateral */
  collateralFdBankName: string
  collateralFdAccountNumber: string
  collateralFdAmount: string
  collateralFdMaturityDate: string
  collateralFdReceiptNumber: string
  /** Machinery collateral */
  collateralMachineryTypeDescription: string
  collateralMachineryMakeModel: string
  collateralMachineryYearOfPurchase: string
  collateralMachineryEstimatedValue: string
  collateralMachineryLocationAddress: string
}

export function createEmptyIntakeFormState(): IntakeFormState {
  return {
    borrowerType: 'INDIVIDUAL',
    loanProduct: '',
    workflowId: '',
    customFieldValues: {},
    requestedAmount: '',
    tenureMonths: '',
    lmsProductCode: '',
    lmsTenureUnit: DEFAULT_LMS_TENURE_UNIT,
    externalProductMappingId: '',
    externalProductMappingVersion: '',
    externalSystem: '',
    externalProductCode: '',
    invoiceOnboardingChoice: '',
    selectedSubProgramId: '',
    dependencyVintagePercent: '',
    anchorRelationshipVintageMonths: '',
    purpose: '',
    loanPurpose: '',
    occupation: '',
    salesOfficerName: '',
    salesOfficerId: '',
    borrowerMobile: '',
    salesBorrowerAck: false,
    fullName: '',
    mobile: '',
    email: '',
    dateOfBirth: '',
    addressLine: '',
    city: '',
    state: '',
    businessName: '',
    contactPersonName: '',
    contactMobile: '',
    contactEmail: '',
    gstin: '',
    udyam: '',
    businessAddress: '',
    businessCity: '',
    businessState: '',
    businessPincode: '',
    panNumber: '',
    aadhaar: '',
    voterId: '',
    dlNumber: '',
    mobileLinkedAadhaar: false,
    cin: '',
    hasExistingLoans: '',
    existingLoansDetails: '',
    gender: '',
    maritalStatus: '',
    addressLine2: '',
    pincode: '',
    addressProofType: '',
    bankAccountNumber: '',
    ifscCode: '',
    bankName: '',
    employmentType: '',
    employerName: '',
    monthlyNetIncome: '',
    occupationIndustry: '',
    workExperienceYears: '',
    consentKyc: false,
    consentBureau: false,
    consentAccountAggregator: false,
    consentComms: false,
    documentUploaded: {},
    collateralPropertyType: '',
    collateralPropertyAddress: '',
    collateralOwnershipType: '',
    collateralEstimatedMarketValue: '',
    collateralExistingMortgage: '',
    collateralSecurityType: '',
    collateralIsin: '',
    collateralCompanyOrFundName: '',
    collateralShareQuantity: '',
    collateralShareMarketValue: '',
    collateralDematAccountNumber: '',
    collateralPledgeConsent: false,
    collateralGoldType: '',
    collateralGoldGrossWeight: '',
    collateralGoldNetWeight: '',
    collateralGoldPurityKarat: '',
    collateralGoldEstimatedValue: '',
    collateralGoldOrnamentDescription: '',
    collateralVehicleType: '',
    collateralVehicleMakeModel: '',
    collateralVehicleYear: '',
    collateralVehicleRegistrationNumber: '',
    collateralVehicleEstimatedMarketValue: '',
    collateralVehicleExistingLoan: '',
    collateralFdBankName: '',
    collateralFdAccountNumber: '',
    collateralFdAmount: '',
    collateralFdMaturityDate: '',
    collateralFdReceiptNumber: '',
    collateralMachineryTypeDescription: '',
    collateralMachineryMakeModel: '',
    collateralMachineryYearOfPurchase: '',
    collateralMachineryEstimatedValue: '',
    collateralMachineryLocationAddress: '',
  }
}
