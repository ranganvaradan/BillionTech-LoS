import { describe, expect, it } from 'vitest'
import { hydrateIntakeFormFromApplication } from './hydrateIntakeFromApplication'
import type { ApplicationResponse } from '@/types/application'

function app(over: Partial<ApplicationResponse> = {}): ApplicationResponse {
  return {
    id: 'a1',
    applicationNumber: 'LOS-1',
    customerId: 'c1',
    borrowerType: 'PROPRIETOR',
    loanProduct: 'TERM_LOAN',
    requestedAmount: 500_000,
    interestRate: null,
    tenureMonths: 24,
    status: 'CONSENT_PENDING',
    personalInfo: null,
    businessInfo: null,
    financialInfo: null,
    collateralInfo: null,
    remarks: null,
    assignedTo: null,
    sanctionedAmount: null,
    approvedRate: null,
    disbursedAmount: null,
    disbursedAt: null,
    lmsReferenceId: null,
    esignTransactionId: null,
    bureauScore: null,
    manualBureauScore: null,
    manualBureauRemarks: null,
    manualBureauDocumentId: null,
    creditDecision: null,
    creditRiskScore: null,
    createdAt: null,
    updatedAt: null,
    submittedAt: null,
    intakeOwner: 'BORROWER',
    ...over,
  }
}

describe('hydrateIntakeFormFromApplication', () => {
  it('maps proprietor business fields from admin-saved businessInfo and personalInfo', () => {
    const form = hydrateIntakeFormFromApplication(
      app({
        personalInfo: {
          fullName: 'Raj Kumar',
          email: 'raj@example.com',
          mobile: '9876543210',
          phone: '9876543210',
        },
        businessInfo: {
          businessName: 'TEST BORROWER 05',
          contactPersonName: 'Raj Kumar',
          gstin: '33AABCT1234A1Z5',
          udyam: 'UDYAM-TN-00-1234567',
          addressLine: 'TEST',
          city: 'Coimbatore',
          state: 'Tamil Nadu',
          pincode: '654321',
        },
      }),
    )

    expect(form.businessName).toBe('TEST BORROWER 05')
    expect(form.contactPersonName).toBe('Raj Kumar')
    expect(form.contactMobile).toBe('9876543210')
    expect(form.contactEmail).toBe('raj@example.com')
    expect(form.gstin).toBe('33AABCT1234A1Z5')
    expect(form.udyam).toBe('UDYAM-TN-00-1234567')
    expect(form.businessAddress).toBe('TEST')
    expect(form.businessCity).toBe('Coimbatore')
    expect(form.businessState).toBe('Tamil Nadu')
    expect(form.businessPincode).toBe('654321')
  })

  it('falls back to personalInfo when business contact fields are only on personalInfo', () => {
    const form = hydrateIntakeFormFromApplication(
      app({
        personalInfo: {
          fullName: 'Priya S',
          email: 'priya@example.com',
          mobile: '9123456789',
        },
        businessInfo: {
          businessName: 'Acme Traders',
          gstin: '29AAAAA0000A1Z5',
          addressLine: 'MG Road',
          city: 'Bengaluru',
          state: 'Karnataka',
          pincode: '560001',
        },
      }),
    )

    expect(form.contactPersonName).toBe('Priya S')
    expect(form.contactMobile).toBe('9123456789')
    expect(form.contactEmail).toBe('priya@example.com')
    expect(form.gstin).toBe('29AAAAA0000A1Z5')
  })

  it('hydrates PAN from the pan alias onto the canonical panNumber field', () => {
    const form = hydrateIntakeFormFromApplication(
      app({
        personalInfo: { pan: 'GLCC27052Z', fullName: 'Test' },
      }),
    )
    expect(form.panNumber).toBe('GLCC27052Z')
  })
})
