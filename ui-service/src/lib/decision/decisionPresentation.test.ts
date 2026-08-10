import { describe, expect, it } from 'vitest'
import {
  buildDecisionSummary,
  camNextActionHint,
  completionDisburseActive,
  completionEsignActive,
} from './decisionPresentation'
import {
  showCamInDecision,
  showDisbursementInDecision,
  showEsignInDecision,
  visibleWorkbenchTabs,
} from '@/lib/applicationWorkbench'
import type { ApplicationResponse } from '@/types/application'

function app(partial: Partial<ApplicationResponse>): ApplicationResponse {
  return {
    id: 'a1',
    applicationNumber: 'APP-1',
    status: 'CAM_READY',
    loanProduct: 'PERSONAL_LOAN',
    requestedAmount: 100000,
    ...partial,
  } as ApplicationResponse
}

describe('decisionPresentation', () => {
  it('9–11 Decision summary + CAM/sanction focus without inventing sanctioned amount', () => {
    const model = buildDecisionSummary(
      app({
        creditDecision: 'APPROVE',
        camStatus: 'DRAFT',
        status: 'CAM_READY',
        sanctionedAmount: undefined,
      }),
    )
    expect(model.assessmentOutcome).toBe('Approved')
    expect(model.camStatusLabel).toBe('Draft')
    expect(model.nextFocus).toBe('cam')
    expect(model.nextFocusLabel).toBe('Complete CAM')
    expect(model.sanctionedAmount).toBeUndefined()
  })

  it('points to sanction after CAM reviewed', () => {
    const model = buildDecisionSummary(app({ status: 'CAM_REVIEWED', camStatus: 'APPROVED' }))
    expect(model.nextFocus).toBe('sanction')
    expect(model.camStatusLabel).toBe('Approved')
  })

  it('13 eSign / disbursement stage helpers', () => {
    expect(completionEsignActive('KFS_GENERATED')).toBe(true)
    expect(completionEsignActive('CAM_READY')).toBe(false)
    expect(completionDisburseActive('ESIGN_COMPLETED')).toBe(true)
    expect(completionDisburseActive('ESIGN_PENDING')).toBe(false)
  })

  it('10 CAM next-action hints preserved for maker/checker', () => {
    expect(camNextActionHint({ camStatus: 'DRAFT', isMaker: true, isChecker: false })).toMatch(/submit/i)
    expect(camNextActionHint({ camStatus: 'SUBMITTED', isMaker: false, isChecker: true })).toMatch(/approve/i)
  })

  it('14 role visibility unchanged for RM vs credit roles', () => {
    const a = app({ status: 'CAM_READY' })
    expect(showCamInDecision({ role: 'RELATIONSHIP_MANAGER', app: a })).toBe(false)
    expect(showEsignInDecision({ role: 'RELATIONSHIP_MANAGER' })).toBe(false)
    expect(showDisbursementInDecision({ role: 'RELATIONSHIP_MANAGER', app: a })).toBe(false)
    expect(showCamInDecision({ role: 'CREDIT_OFFICER', app: a })).toBe(true)
    expect(visibleWorkbenchTabs({ role: 'RELATIONSHIP_MANAGER' }).some((t) => t.id === 'credit')).toBe(false)
    expect(visibleWorkbenchTabs({ role: 'CREDIT_OFFICER' }).some((t) => t.id === 'credit')).toBe(true)
  })
})
