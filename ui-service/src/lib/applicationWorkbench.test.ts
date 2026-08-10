import { describe, expect, it } from 'vitest'
import {
  defaultWorkbenchTab,
  nextActionUsesIntakeHref,
  resolveWorkbenchNextAction,
  showCamInDecision,
  showDisbursementInDecision,
  showEsignInDecision,
  visibleWorkbenchTabs,
} from '@/lib/applicationWorkbench'
import type { ApplicationResponse } from '@/types/application'

function app(partial: Partial<ApplicationResponse> & { status: ApplicationResponse['status'] }): ApplicationResponse {
  return {
    id: '11111111-1111-1111-1111-111111111111',
    applicationNumber: 'APP-1',
    customerId: 'c1',
    borrowerType: 'INDIVIDUAL',
    loanProduct: 'PERSONAL_LOAN',
    requestedAmount: 100000,
    interestRate: null,
    tenureMonths: 12,
    personalInfo: { fullName: 'Ravi Kumar' },
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

describe('visibleWorkbenchTabs', () => {
  it('exposes six primary tabs for full-access credit officer', () => {
    const tabs = visibleWorkbenchTabs({ role: 'CREDIT_OFFICER' })
    expect(tabs.map((t) => t.id)).toEqual([
      'overview',
      'kyc',
      'credit',
      'decision',
      'documents',
      'history',
    ])
  })

  it('hides Credit Assessment for Relationship Manager', () => {
    const tabs = visibleWorkbenchTabs({ role: 'RELATIONSHIP_MANAGER' })
    expect(tabs.map((t) => t.id)).toEqual(['overview', 'kyc', 'decision', 'documents', 'history'])
    expect(tabs.some((t) => t.id === 'credit')).toBe(false)
  })
})

describe('defaultWorkbenchTab', () => {
  it('maps stages to workbench tabs', () => {
    const all = ['overview', 'kyc', 'credit', 'decision', 'documents', 'history'] as const
    expect(defaultWorkbenchTab('KYC_IN_PROGRESS', [...all])).toBe('kyc')
    expect(defaultWorkbenchTab('UNDERWRITING', [...all])).toBe('credit')
    expect(defaultWorkbenchTab('CAM_READY', [...all])).toBe('decision')
    expect(defaultWorkbenchTab('BORROWER_SUBMITTED', [...all])).toBe('overview')
  })

  it('falls back when preferred tab unavailable to role', () => {
    const rm = ['overview', 'kyc', 'decision', 'documents', 'history'] as const
    expect(defaultWorkbenchTab('UNDERWRITING', [...rm])).toBe('overview')
  })
})

describe('resolveWorkbenchNextAction', () => {
  it('routes KYC CTA for credit officer', () => {
    const a = resolveWorkbenchNextAction({
      app: app({ status: 'KYC_IN_PROGRESS' }),
      role: 'CREDIT_OFFICER',
    })
    expect(a?.label).toBe('Run KYC')
    expect(a?.targetTab).toBe('kyc')
  })

  it('does not offer underwriting CTA to RM', () => {
    const a = resolveWorkbenchNextAction({
      app: app({ status: 'UNDERWRITING' }),
      role: 'RELATIONSHIP_MANAGER',
    })
    expect(a?.targetTab).not.toBe('credit')
  })

  it('uses intake href for draft RM continue', () => {
    const draft = app({ status: 'DRAFT', intakeOwner: 'STAFF' })
    expect(nextActionUsesIntakeHref(draft, 'RELATIONSHIP_MANAGER')).toBe(true)
    const action = resolveWorkbenchNextAction({ app: draft, role: 'RELATIONSHIP_MANAGER' })
    expect(action?.label).toBe('Continue intake')
  })

  it('points CAM_READY to Decision', () => {
    const a = resolveWorkbenchNextAction({
      app: app({ status: 'CAM_READY' }),
      role: 'CREDIT_MANAGER',
    })
    expect(a?.targetTab).toBe('decision')
    expect(a?.label).toMatch(/CAM/i)
  })
})

describe('decision section gating', () => {
  it('hides CAM and eSign/disburse for RM; keeps sanction surface via Decision tab', () => {
    const a = app({ status: 'CAM_READY' })
    expect(showCamInDecision({ role: 'RELATIONSHIP_MANAGER', app: a })).toBe(false)
    expect(showEsignInDecision({ role: 'RELATIONSHIP_MANAGER' })).toBe(false)
    expect(showDisbursementInDecision({ role: 'RELATIONSHIP_MANAGER', app: a })).toBe(false)
  })

  it('hides disbursement for ID borrower product', () => {
    const a = app({
      status: 'ESIGN_COMPLETED',
      loanProduct: 'BUSINESS_WC_INVOICE_DISCOUNTING',
      intakeSegment: 'BORROWER',
    })
    expect(showDisbursementInDecision({ role: 'CREDIT_OFFICER', app: a })).toBe(false)
  })

  it('hides CAM for ID anchor', () => {
    const a = app({
      status: 'SANCTION_PENDING',
      loanProduct: 'BUSINESS_WC_INVOICE_DISCOUNTING',
      intakeSegment: 'ANCHOR',
    })
    expect(showCamInDecision({ role: 'CREDIT_OFFICER', app: a })).toBe(false)
  })
})

describe('no fake CI cards', () => {
  it('workbench helpers do not reference CI application cards', () => {
    // Structural guard: next-action targets are only workbench tabs
    const a = resolveWorkbenchNextAction({
      app: app({ status: 'UNDERWRITING' }),
      role: 'CREDIT_OFFICER',
    })
    expect(['overview', 'kyc', 'credit', 'decision', 'documents', 'history']).toContain(a?.targetTab)
  })
})
