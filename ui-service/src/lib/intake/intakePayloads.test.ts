import { describe, expect, it } from 'vitest'
import {
  buildBorrowerEmploymentUpdate,
  buildConsentUpdate,
  buildIntakeCreateRequest,
  buildKycUpdate,
} from './intakePayloads'
import { createEmptyIntakeFormState, type IntakeFormState } from './intakeTypes'

describe('intakePayloads', () => {
  it('maps consent to financialInfo', () => {
    const s: IntakeFormState = {
      ...createEmptyIntakeFormState(),
      consentKyc: true,
      consentBureau: true,
      consentAccountAggregator: true,
      consentComms: true,
    }
    const u = buildConsentUpdate(s, 'BORROWER_SELF_SERVICE', null)
    const fi = u.financialInfo as Record<string, string>
    expect(fi.consentBureau).toBe('true')
  })

  it('buildIntakeCreateRequest persists top-level and individual personal + business info', () => {
    const s: IntakeFormState = {
      ...createEmptyIntakeFormState(),
      borrowerType: 'INDIVIDUAL',
      loanProduct: 'TERM',
      requestedAmount: '100000',
      tenureMonths: '12',
      purpose: 'Working capital',
      fullName: 'Jane Doe',
      email: 'j@ex.com',
      mobile: '9000000000',
      addressLine: '1 Main St',
      addressLine2: 'Apt 2',
      city: 'Mumbai',
      state: 'MH',
      pincode: '400001',
      gender: 'FEMALE',
      maritalStatus: 'SINGLE',
      hasExistingLoans: 'no',
    }
    const created = buildIntakeCreateRequest(s, 'BORROWER_SELF_SERVICE', null)
    expect(created.requestedAmount).toBe(100_000)
    expect(created.tenureMonths).toBe(12)
    const pi = created.personalInfo as Record<string, string>
    expect(pi.purpose).toBe('Working capital')
    expect(pi.intakeMode).toBe('BORROWER_SELF_SERVICE')
    expect(pi.fullName).toBe('Jane Doe')
    expect(pi.pincode).toBe('400001')
  })

  it('buildKycUpdate stores PAN, bank, Aadhaar last4/12, and CIN for company', () => {
    const s: IntakeFormState = {
      ...createEmptyIntakeFormState(),
      borrowerType: 'COMPANY',
      panNumber: 'abcde1234f',
      aadhaar: '1234',
      bankAccountNumber: '111',
      ifscCode: 'hdfc0001234',
      bankName: 'HDFC',
      gstin: '22AAAAA0000A1Z5',
      cin: 'L00000MH2000PLC000000',
    }
    const u = buildKycUpdate(s)
    const pi = u.personalInfo as Record<string, string | boolean>
    expect(pi.panNumber).toBe('ABCDE1234F')
    expect(pi.aadhaarLast4).toBe('1234')
    expect(pi.ifsc).toBe('HDFC0001234')
    expect(pi.bankName).toBe('HDFC')
    const bi = u.businessInfo as Record<string, string>
    expect(bi.cin).toBe('L00000MH2000PLC000000')
  })

  it('buildBorrowerEmploymentUpdate maps income and employment to personalInfo', () => {
    const s: IntakeFormState = {
      ...createEmptyIntakeFormState(),
      employmentType: 'SALARIED',
      employerName: 'Co',
      monthlyNetIncome: '60000',
      occupationIndustry: 'Retail',
      workExperienceYears: '5',
    }
    const u = buildBorrowerEmploymentUpdate(s)
    const pi = u.personalInfo as Record<string, string>
    expect(pi.employmentType).toBe('SALARIED')
    expect(pi.monthlyNetIncome).toBe('60000')
  })
})
