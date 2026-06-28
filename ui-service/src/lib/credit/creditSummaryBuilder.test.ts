import { describe, expect, it } from 'vitest'
import { buildCreditSummary } from './creditSummaryBuilder'
import type { ApplicationResponse } from '@/types/application'

const base: ApplicationResponse = {
  id: 'a1',
  applicationNumber: 'N-1',
  customerId: 'c1',
  borrowerType: 'INDIVIDUAL',
  loanProduct: 'PERSONAL_LOAN',
  requestedAmount: 100000,
  interestRate: 12,
  tenureMonths: 12,
  status: 'CAM_READY',
  personalInfo: { fullName: 'Alice', monthlyNetIncome: '50000' },
  businessInfo: null,
  financialInfo: { underwritingMeta: { source: 'SCORECARD' } },
  collateralInfo: null,
  remarks: null,
  assignedTo: null,
  sanctionedAmount: null,
  approvedRate: null,
  disbursedAmount: null,
  disbursedAt: null,
  lmsReferenceId: null,
  esignTransactionId: null,
  bureauScore: 720,
  manualBureauScore: null,
  manualBureauRemarks: null,
  manualBureauDocumentId: null,
  creditDecision: 'APPROVED',
  creditRiskScore: 12,
  createdAt: null,
  updatedAt: null,
  submittedAt: null,
  latestUnderwritingEvaluation: {
    aggregateScore: 80,
    aggregateDecision: 'APPROVED',
    scorecardName: 'Retail PL',
    scorecardVersion: 1,
    parameterResults: [
      { parameter: 'BUREAU_SCORE', matched: true, valueUsed: '720', valueSource: 'BUREAU' },
    ],
  } as unknown as NonNullable<ApplicationResponse['latestUnderwritingEvaluation']>,
}

describe('buildCreditSummary', () => {
  it('exposes borrower snapshot and bureau labels', () => {
    const s = buildCreditSummary({
      app: base,
      creditControlView: { effective: { bureauScoreSource: 'PROVIDER' } },
      latestUnderwritingEvaluation: base.latestUnderwritingEvaluation as Record<string, unknown>,
      kycOutcome: { outcome: 'PASS' },
    })
    expect(s.borrowerSnapshot['Primary name']).toContain('Alice')
    expect(s.bureauSummary['Bureau score (in use)']).toBe('720')
  })

  it('computes completeness percent in range 0-100', () => {
    const s = buildCreditSummary({
      app: base,
      creditControlView: null,
      latestUnderwritingEvaluation: null,
      kycOutcome: { outcome: 'PASS' },
    })
    expect(s.completenessPercent).toBeGreaterThan(0)
    expect(s.completenessPercent).toBeLessThanOrEqual(100)
  })

  it('omits GSTIN / Udyam from KYC summary for individual borrowers', () => {
    const s = buildCreditSummary({
      app: {
        ...base,
        borrowerType: 'INDIVIDUAL',
        businessInfo: { gstin: '22AAAAA0000A1Z5', udyam: 'UDYAM-XX-00-0000000' },
      },
      creditControlView: null,
      latestUnderwritingEvaluation: null,
      kycOutcome: { outcome: 'PASS' },
    })
    expect(s.kycSummary['GSTIN (application)']).toBeUndefined()
    expect(s.kycSummary['Udyam (application)']).toBeUndefined()
  })

  it('includes GSTIN / Udyam in KYC summary for business borrowers when present', () => {
    const s = buildCreditSummary({
      app: {
        ...base,
        borrowerType: 'PROPRIETOR',
        businessInfo: { gstin: '22AAAAA0000A1Z5', udyamNumber: 'UDYAM-YY-11-1111111' },
      },
      creditControlView: null,
      latestUnderwritingEvaluation: null,
      kycOutcome: { outcome: 'PASS' },
    })
    expect(s.kycSummary['GSTIN (application)']).toBe('22AAAAA0000A1Z5')
    expect(s.kycSummary['Udyam (application)']).toBe('UDYAM-YY-11-1111111')
  })

  it('adds demo income preview lines when a bank statement is on file and bank income is missing', () => {
    const appNoIncome: ApplicationResponse = {
      ...base,
      personalInfo: { fullName: 'Bob' },
      financialInfo: {},
    }
    const s = buildCreditSummary({
      app: appNoIncome,
      creditControlView: null,
      latestUnderwritingEvaluation: null,
      kycOutcome: { outcome: 'PASS' },
      bankStatementOnFile: true,
    })
    const keys = Object.keys(s.incomeSummary)
    expect(keys.some((k) => k.includes('demo preview'))).toBe(true)
    expect(keys.some((k) => k.includes('Bank statement income'))).toBe(true)
  })

  it('does not add demo income when bank statement income is already present', () => {
    const appWithBank: ApplicationResponse = {
      ...base,
      personalInfo: { fullName: 'Bob' },
      financialInfo: {
        creditControl: {
          manual: {
            bankStatementIncome: { value: '72000', source: 'MANUAL' },
          },
        },
      },
    }
    const s = buildCreditSummary({
      app: appWithBank,
      creditControlView: null,
      latestUnderwritingEvaluation: null,
      kycOutcome: { outcome: 'PASS' },
      bankStatementOnFile: true,
    })
    expect(Object.keys(s.incomeSummary).some((k) => k.includes('demo preview'))).toBe(false)
  })
})
