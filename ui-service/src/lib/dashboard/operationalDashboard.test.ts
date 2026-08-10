import { describe, expect, it } from 'vitest'
import { canCreateOrNotifyBorrowerIntake } from '@/auth/types'
import {
  buildAttentionItems,
  buildPipelineStages,
  canSeeUnderwritingAttention,
  firstNameFromDisplayName,
  parseByStatus,
  summaryTotal,
  welcomeLine,
} from '@/lib/dashboard/operationalDashboard'
import { displayBorrowerName } from '@/lib/intake/applicationPartyResolve'
import { showStagingDemoNav } from '@/nav/workspaceNav'
import type { ApplicationResponse } from '@/types/application'

function app(partial: Partial<ApplicationResponse>): ApplicationResponse {
  return {
    id: '1',
    applicationNumber: 'APP-1',
    customerId: 'c1',
    borrowerType: 'INDIVIDUAL',
    loanProduct: 'PERSONAL_LOAN',
    requestedAmount: 100000,
    interestRate: null,
    tenureMonths: null,
    status: 'DRAFT',
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
    ...partial,
  }
}

describe('operationalDashboard', () => {
  it('summaryTotal reads total and falls back to byStatus sum', () => {
    expect(summaryTotal({ total: 0, byStatus: {} })).toBe(0)
    expect(summaryTotal({ total: 5 })).toBe(5)
    expect(
      summaryTotal({
        byStatus: { DRAFT: 2, KYC_IN_PROGRESS: 3 },
      }),
    ).toBe(5)
  })

  it('parseByStatus ignores non-objects', () => {
    expect(parseByStatus(null)).toEqual({})
    expect(parseByStatus({ byStatus: 'x' })).toEqual({})
  })

  it('aggregates pipeline stages from byStatus', () => {
    const stages = buildPipelineStages(
      {
        DRAFT: 2,
        BORROWER_SUBMITTED: 1,
        KYC_IN_PROGRESS: 4,
        KYC_FAILED: 1,
        UNDERWRITING: 3,
        APPROVED: 2,
        REJECTED: 1,
        DISBURSED: 10,
        WITHDRAWN: 1,
      },
      { underwritingActionable: true },
    )
    expect(stages.find((s) => s.id === 'intake')?.count).toBe(3)
    expect(stages.find((s) => s.id === 'kyc')?.count).toBe(5)
    expect(stages.find((s) => s.id === 'underwriting')?.count).toBe(3)
    expect(stages.find((s) => s.id === 'decision')?.count).toBe(3)
    expect(stages.find((s) => s.id === 'completed')?.count).toBe(11)
    expect(stages.find((s) => s.id === 'underwriting')?.to).toBe('/underwriting')
  })

  it('RM pipeline underwriting has no actionable link', () => {
    const stages = buildPipelineStages({ UNDERWRITING: 2 }, { underwritingActionable: false })
    expect(stages.find((s) => s.id === 'underwriting')?.to).toBeNull()
    expect(stages.find((s) => s.id === 'underwriting')?.count).toBe(2)
  })

  it('hides zero attention rows and surfaces non-zero ones', () => {
    const items = buildAttentionItems({
      role: 'CREDIT_OFFICER',
      byStatus: {
        BORROWER_SUBMITTED: 2,
        PENDING_CREDIT_OFFICER: 0,
        KYC_IN_PROGRESS: 4,
        UNDERWRITING: 3,
        SENT_BACK_TO_RM: 0,
      },
      programApprovalsPending: 0,
      settlementOpenCount: 0,
    })
    expect(items.map((i) => i.id)).toEqual(['borrower_submitted', 'kyc', 'underwriting'])
    expect(items.every((i) => i.count > 0)).toBe(true)
  })

  it('RM does not get actionable Underwriting attention', () => {
    expect(canSeeUnderwritingAttention('RELATIONSHIP_MANAGER')).toBe(false)
    const items = buildAttentionItems({
      role: 'RELATIONSHIP_MANAGER',
      byStatus: {
        BORROWER_SUBMITTED: 1,
        SENT_BACK_TO_RM: 2,
        KYC_IN_PROGRESS: 9,
        UNDERWRITING: 9,
        PENDING_CREDIT_OFFICER: 5,
      },
      programApprovalsPending: null,
      settlementOpenCount: null,
    })
    expect(items.some((i) => i.id === 'underwriting')).toBe(false)
    expect(items.some((i) => i.id === 'kyc')).toBe(false)
    expect(items.some((i) => i.id === 'pending_credit_officer')).toBe(false)
    expect(items.map((i) => i.id)).toEqual(['borrower_submitted', 'sent_back_to_rm'])
  })

  it('includes program approvals and settlement items only when count > 0', () => {
    const withOptional = buildAttentionItems({
      role: 'ADMINISTRATOR',
      byStatus: {},
      programApprovalsPending: 1,
      settlementOpenCount: 2,
    })
    expect(withOptional.map((i) => i.id)).toEqual(['program_approvals', 'settlements'])
    const zeros = buildAttentionItems({
      role: 'ADMINISTRATOR',
      byStatus: {},
      programApprovalsPending: 0,
      settlementOpenCount: 0,
    })
    expect(zeros).toEqual([])
  })

  it('welcome and firstName helpers', () => {
    expect(firstNameFromDisplayName('Priya Sharma')).toBe('Priya')
    expect(welcomeLine('Priya Sharma')).toBe('Welcome, Priya')
    expect(welcomeLine('')).toBe('Welcome, there')
  })

  it('New Application permission follows existing RBAC', () => {
    expect(canCreateOrNotifyBorrowerIntake('RELATIONSHIP_MANAGER')).toBe(true)
    expect(canCreateOrNotifyBorrowerIntake('ADMINISTRATOR')).toBe(true)
    expect(canCreateOrNotifyBorrowerIntake('CREDIT_OFFICER')).toBe(false)
  })

  it('zero-application empty state is driven by total === 0', () => {
    expect(summaryTotal({ total: 0, byStatus: {} })).toBe(0)
    expect(summaryTotal({ total: 1 })).toBe(1)
  })

  it('optional null program/settlement counts do not invent attention rows', () => {
    const items = buildAttentionItems({
      role: 'ADMINISTRATOR',
      byStatus: { KYC_IN_PROGRESS: 1 },
      programApprovalsPending: null,
      settlementOpenCount: null,
    })
    expect(items.map((i) => i.id)).toEqual(['kyc'])
  })

  it('staging demo samples flag is boolean from env helper', () => {
    expect(typeof showStagingDemoNav()).toBe('boolean')
  })
})

describe('displayBorrowerName', () => {
  it('reads personalInfo fullName', () => {
    expect(displayBorrowerName(app({ personalInfo: { fullName: 'Raj Kumar' } }))).toBe('Raj Kumar')
  })

  it('falls back to — when unavailable', () => {
    expect(displayBorrowerName(app({ personalInfo: null, businessInfo: null }))).toBe('—')
  })

  it('uses anchor business name', () => {
    expect(
      displayBorrowerName(
        app({
          intakeSegment: 'ANCHOR',
          businessInfo: { corporateName: 'Acme Traders' },
        }),
      ),
    ).toBe('Acme Traders')
  })
})
