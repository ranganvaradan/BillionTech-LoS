import { describe, expect, it } from 'vitest'
import { buildIntakeReadback } from './intakeReadback'
import type { ApplicationResponse } from '@/types/application'

function baseApp(over: Partial<ApplicationResponse> = {}): ApplicationResponse {
  return {
    id: 'a1',
    applicationNumber: 'APP-1',
    customerId: 'c1',
    borrowerType: 'INDIVIDUAL',
    loanProduct: 'TERM_LOAN',
    requestedAmount: 500_000,
    interestRate: null,
    tenureMonths: 24,
    status: 'KYC_IN_PROGRESS',
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
    ...over,
  }
}

describe('buildIntakeReadback', () => {
  it('maps product, tenure, and purpose with friendly section titles and loan product label (not code)', () => {
    const sections = buildIntakeReadback(
      baseApp({
        loanProduct: 'PERSONAL_LOAN',
        personalInfo: { purpose: 'Working capital' },
      }),
    )
    const titles = sections.map((s) => s.title)
    expect(titles).toContain('Product & request')
    const pr = sections.find((s) => s.title === 'Product & request')!
    const labels = pr.rows.map((r) => r.label)
    expect(labels).toContain('Loan product')
    expect(labels).toContain('Tenure (months)')
    expect(labels).toContain('Purpose of loan')
    const byL = Object.fromEntries(pr.rows.map((r) => [r.label, r.value]))
    expect(byL['Loan product']).toBe('Personal Loan')
  })

  it('shows KYC, bank, and employment with business-friendly labels', () => {
    const sections = buildIntakeReadback(
      baseApp({
        businessInfo: { gstin: '22AAAAA0000A1Z5', udyam: 'UDYAM-XX-00-0000000' },
        personalInfo: {
          panNumber: 'ABCDE1234F',
          aadhaarLast4: '1234',
          mobile: '9876543210',
          bankAccountNumber: '1234567890',
          ifsc: 'HDFC0001234',
          bankName: 'HDFC Bank',
          employmentType: 'SALARIED',
          employerName: 'Acme',
          monthlyNetIncome: '75000',
          occupationIndustry: 'IT',
        },
        financialInfo: { consentBureau: 'true', consentKyc: 'true' },
      }),
    )
    const flat = sections.flatMap((s) => s.rows)
    const byLabel = Object.fromEntries(flat.map((r) => [r.label, r.value]))
    expect(byLabel['PAN']).toBe('ABCDE1234F')
    expect(byLabel['Aadhaar (last 4 digits)']).toBe('1234')
    expect(byLabel['GSTIN']).toBe('22AAAAA0000A1Z5')
    expect(byLabel['Udyam']).toBe('UDYAM-XX-00-0000000')
    expect(byLabel['IFSC']).toBe('HDFC0001234')
    expect(byLabel['Monthly net income (INR, declared)']).toBe('75000')
    expect(byLabel['Credit bureau pull']).toBe('Yes')
  })

  it('includes collateral borrower intake with friendly labels', () => {
    const sections = buildIntakeReadback(
      baseApp({
        loanProduct: 'LOAN_AGAINST_PROPERTY',
        collateralInfo: {
          borrowerIntake: {
            collateralType: 'PROPERTY',
            product: 'LOAN_AGAINST_PROPERTY',
            estimatedValue: 1_000_000,
            detailsJson: JSON.stringify({ propertyType: 'Residential' }),
            supportingDocumentIds: ['u1'],
            providedBy: 'BORROWER',
            providedAt: '2026-01-01T00:00:00.000Z',
          },
        },
      }),
    )
    const titles = sections.map((s) => s.title)
    expect(titles).toContain('Collateral (borrower intake)')
  })

  it('formats intake mode and consents; does not surface password-like keys in extra', () => {
    const sections = buildIntakeReadback(
      baseApp({
        personalInfo: {
          intakeMode: 'BORROWER_SELF_SERVICE',
          fullName: 'Test User',
          customField: 'x',
        },
        financialInfo: {
          consentBureau: 'false',
        },
      }),
    )
    const ch = sections.find((s) => s.title === 'Intake, ownership & assistance')
    expect(ch?.rows.some((r) => r.value === 'Borrower self-service')).toBe(true)
    const allLabels = sections.flatMap((s) => s.rows.map((r) => r.label))
    expect(allLabels.some((l) => l.toLowerCase().includes('password'))).toBe(false)
  })
})
