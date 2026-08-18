import { describe, expect, it } from 'vitest'
import { createEmptyIntakeFormState, type IntakeFormState } from './intakeTypes'
import { productsForBorrowerType, validateConsentStep, validateProductStep } from './intakeValidation'
import type { WorkflowConfigResponse } from '@/types/workflow'

function wf(over: Partial<WorkflowConfigResponse> & { loanProduct: string; borrowerType: string }): WorkflowConfigResponse {
  return {
    id: '0',
    name: 'n',
    steps: [],
    version: 1,
    createdAt: null,
    active: over.active !== undefined ? over.active : true,
    ...over,
  } as WorkflowConfigResponse
}

describe('intakeValidation', () => {
  it('rejects product step when there is no active product for the borrower type', () => {
    const s: IntakeFormState = { ...createEmptyIntakeFormState(), borrowerType: 'INDIVIDUAL', loanProduct: 'PERSONAL_LOAN' }
    const workflows: WorkflowConfigResponse[] = [wf({ loanProduct: 'PERSONAL_LOAN', borrowerType: 'COMPANY', active: true })]
    const err = validateProductStep(s, 'BORROWER_SELF_SERVICE', workflows)
    expect(err).toContain('No active')
  })

  it('accepts an active product for the same borrower type', () => {
    const s: IntakeFormState = {
      ...createEmptyIntakeFormState(),
      borrowerType: 'INDIVIDUAL',
      loanProduct: 'PERSONAL_LOAN',
      requestedAmount: '10000',
      loanPurpose: 'EDUCATION',
      purpose: 'Education',
    }
    const workflows: WorkflowConfigResponse[] = [wf({ loanProduct: 'PERSONAL_LOAN', borrowerType: 'INDIVIDUAL', active: true })]
    const err = validateProductStep(s, 'BORROWER_SELF_SERVICE', workflows)
    expect(err).toBeNull()
  })

  it('accepts invoice discounting on borrower portal without staff onboarding type selection', () => {
    const s: IntakeFormState = {
      ...createEmptyIntakeFormState(),
      borrowerType: 'INDIVIDUAL',
      loanProduct: 'BUSINESS_WC_INVOICE_DISCOUNTING',
      requestedAmount: '10000',
      invoiceOnboardingChoice: '',
      loanPurpose: 'BUSINESS_PURPOSE',
      purpose: 'Business Purpose',
    }
    const workflows: WorkflowConfigResponse[] = [
      wf({
        loanProduct: 'BUSINESS_WC_INVOICE_DISCOUNTING',
        borrowerType: 'INDIVIDUAL',
        active: true,
        intakeSegment: 'BORROWER',
      }),
    ]
    const err = validateProductStep(s, 'BORROWER_SELF_SERVICE', workflows)
    expect(err).toBeNull()
  })

  it('validates all consents before moving past consent step', () => {
    const s = createEmptyIntakeFormState()
    s.consentKyc = true
    s.consentBureau = true
    s.consentAccountAggregator = true
    expect(validateConsentStep(s) ?? '').toMatch(/consent/i)
    s.consentComms = true
    expect(validateConsentStep(s)).toBeNull()
  })

  it('uses productsForBorrowerType with active flag', () => {
    const all: WorkflowConfigResponse[] = [
      wf({ loanProduct: 'A', borrowerType: 'INDIVIDUAL', active: true, id: '1' }),
      wf({ loanProduct: 'A', borrowerType: 'INDIVIDUAL', active: false, id: '2' }),
    ]
    const act = all.filter((w) => w.active)
    expect(productsForBorrowerType(act, 'INDIVIDUAL').map((w) => w.loanProduct)).toEqual(['A'])
  })
})
