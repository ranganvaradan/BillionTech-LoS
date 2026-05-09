import { describe, expect, it } from 'vitest'
import { createEmptyIntakeFormState } from '@/lib/intake/intakeTypes'
import {
  validateBorrowerBankKycStep,
  validateBorrowerIncomeStep,
  validateBorrowerPersonalAddressStep,
  validateBorrowerProductStep,
  validateConsentStep,
} from '@/lib/intake/intakeValidation'
import type { WorkflowConfigResponse } from '@/types/workflow'

const mockWf: WorkflowConfigResponse[] = [
  {
    id: '1',
    name: 'Demo',
    version: 1,
    loanProduct: 'PERSONAL_LOAN',
    borrowerType: 'INDIVIDUAL',
    steps: [],
    active: true,
    createdAt: null,
  },
]

function baseForm() {
  const f = createEmptyIntakeFormState
  return { ...f(), borrowerType: 'INDIVIDUAL' as const, loanProduct: 'PERSONAL_LOAN' }
}

describe('validateBorrowerProductStep', () => {
  it('requires purpose and existing-loan choice', () => {
    const f = baseForm()
    f.requestedAmount = '100000'
    f.purpose = ''
    f.hasExistingLoans = 'no'
    expect(validateBorrowerProductStep(f, mockWf)).toMatch(/purpose/i)
    f.purpose = 'Education'
    f.hasExistingLoans = ''
    expect(validateBorrowerProductStep(f, mockWf)).toMatch(/other loans running/i)
  })

  it('requires details when existing loans is yes', () => {
    const f = baseForm()
    f.requestedAmount = '100000'
    f.purpose = 'Home'
    f.hasExistingLoans = 'yes'
    f.existingLoansDetails = ''
    expect(validateBorrowerProductStep(f, mockWf)).toMatch(/describe|existing/i)
  })
})

describe('validateBorrowerPersonalAddressStep', () => {
  it('accepts a complete individual address', () => {
    const f = baseForm()
    f.fullName = 'Test User'
    f.mobile = '9876543210'
    f.email = 'a@b.co'
    f.dateOfBirth = '1990-01-01'
    f.gender = 'FEMALE'
    f.maritalStatus = 'SINGLE'
    f.addressLine = '1 Main St'
    f.city = 'Mumbai'
    f.state = 'MH'
    f.pincode = '400001'
    f.addressProofType = 'UTILITY_BILL'
    expect(validateBorrowerPersonalAddressStep(f)).toBeNull()
  })
})

describe('validateBorrowerBankKycStep', () => {
  it('requires valid IFSC and bank', () => {
    const f = baseForm()
    f.panNumber = 'ABCDE1234F'
    f.bankAccountNumber = '123456789'
    f.ifscCode = 'INVALID'
    f.bankName = ''
    expect(validateBorrowerBankKycStep(f)).toMatch(/IFSC/i)
    f.ifscCode = 'HDFC0001234'
    f.bankName = 'HDFC Bank'
    expect(validateBorrowerBankKycStep(f)).toBeNull()
  })
})

describe('validateBorrowerIncomeStep', () => {
  it('requires income fields', () => {
    const f = baseForm()
    f.employmentType = 'SALARIED'
    f.employerName = 'Acme'
    f.monthlyNetIncome = '50000'
    f.occupationIndustry = 'IT'
    expect(validateBorrowerIncomeStep(f)).toBeNull()
  })
})

describe('validateConsentStep (borrower journey)', () => {
  it('requires all consents', () => {
    const f = baseForm()
    f.consentKyc = true
    f.consentBureau = true
    f.consentAccountAggregator = true
    f.consentComms = false
    expect(validateConsentStep(f)).not.toBeNull()
  })
})
