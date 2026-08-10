import { describe, expect, it } from 'vitest'
import { INVOICE_DISCOUNTING_PRODUCT_CODE } from '@/catalog/loanProducts'
import { createEmptyIntakeFormState } from './intakeTypes'
import { applyHydratedIntakeDefaults, inferFirstIncompleteIntakeStep, staffCanContinueIntake } from './intakeResume'
import { staffIntakeStepIndices } from './staffIntakeSteps'
import { validateProductStep } from './intakeValidation'
import type { ApplicationResponse } from '@/types/application'
import type { WorkflowConfigResponse } from '@/types/workflow'

function app(over: Partial<ApplicationResponse>): ApplicationResponse {
  return {
    id: 'a1',
    applicationNumber: 'LOS-1',
    customerId: 'c1',
    borrowerType: 'INDIVIDUAL',
    loanProduct: INVOICE_DISCOUNTING_PRODUCT_CODE,
    status: 'CONSENT_PENDING',
    intakeSegment: 'BORROWER',
    intakeOwner: 'BORROWER',
    intakeCompletedStep: 3,
    requestedAmount: 500000,
    interestRate: null,
    tenureMonths: 12,
    personalInfo: { fullName: 'Test', mobile: '9665596655', email: 't@example.com' },
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
    ...over,
  } as ApplicationResponse
}

const workflows: WorkflowConfigResponse[] = [
  {
    id: 'w1',
    name: 'ID',
    loanProduct: INVOICE_DISCOUNTING_PRODUCT_CODE,
    borrowerType: 'INDIVIDUAL',
    intakeSegment: 'BORROWER',
    active: true,
    version: 1,
    steps: [],
    createdAt: null,
  } as WorkflowConfigResponse,
]

describe('intakeResume', () => {
  it('applyHydratedIntakeDefaults sets borrower invoice onboarding for portal', () => {
    const form = createEmptyIntakeFormState()
    form.loanProduct = INVOICE_DISCOUNTING_PRODUCT_CODE
    const next = applyHydratedIntakeDefaults(form, app({}), 'borrower')
    expect(next.invoiceOnboardingChoice).toBe('BORROWER')
  })

  it('validateProductStep allows invoice discounting for borrower self-service without staff onboarding pick', () => {
    const form = {
      ...createEmptyIntakeFormState(),
      borrowerType: 'INDIVIDUAL' as const,
      loanProduct: INVOICE_DISCOUNTING_PRODUCT_CODE,
      requestedAmount: '500000',
      tenureMonths: '12',
      invoiceOnboardingChoice: '' as const,
      loanPurpose: 'BUSINESS_PURPOSE',
      purpose: 'Business Purpose',
    }
    expect(validateProductStep(form, 'BORROWER_SELF_SERVICE', workflows)).toBeNull()
  })

  it('inferFirstIncompleteIntakeStep lands on consent before KYC when consents incomplete', () => {
    const steps = staffIntakeStepIndices(false, false)
    const form = applyHydratedIntakeDefaults(
      {
        ...createEmptyIntakeFormState(),
        borrowerType: 'INDIVIDUAL',
        loanProduct: INVOICE_DISCOUNTING_PRODUCT_CODE,
        requestedAmount: '500000',
        tenureMonths: '12',
        loanPurpose: 'BUSINESS_PURPOSE',
        purpose: 'Business Purpose',
        occupation: 'SALARIED_PRIVATE',
        fullName: 'Test',
        mobile: '9665596655',
        email: 't@example.com',
        addressLine: 'Line 1',
        city: 'Chennai',
        state: 'Tamil Nadu',
        pincode: '600001',
        dependencyVintagePercent: '10',
        anchorRelationshipVintageMonths: '12',
        selectedSubProgramId: 'sp-1',
      },
      app({ intakeCompletedStep: steps.consent }),
      'borrower',
    )
    const step = inferFirstIncompleteIntakeStep(
      form,
      steps,
      'BORROWER_SELF_SERVICE',
      workflows,
      false,
      false,
    )
    expect(step).toBe(steps.consent)
    expect(step).toBeLessThan(steps.kyc)
  })

  it('staffCanContinueIntake blocks credit officers', () => {
    const draft = app({ status: 'DRAFT', intakeOwner: 'STAFF' })
    expect(staffCanContinueIntake(draft, 'RELATIONSHIP_MANAGER')).toBe(true)
    expect(staffCanContinueIntake(draft, 'CREDIT_OFFICER')).toBe(false)
    expect(staffCanContinueIntake(draft, 'ADMIN')).toBe(true)
  })

  it('inferFirstIncompleteIntakeStep does not jump past KYC when KYC is empty', () => {
    const steps = staffIntakeStepIndices(false, false)
    const form = applyHydratedIntakeDefaults(
      {
        ...createEmptyIntakeFormState(),
        borrowerType: 'INDIVIDUAL',
        loanProduct: INVOICE_DISCOUNTING_PRODUCT_CODE,
        requestedAmount: '500000',
        tenureMonths: '12',
        loanPurpose: 'BUSINESS_PURPOSE',
        purpose: 'Business Purpose',
        occupation: 'SALARIED_PRIVATE',
        fullName: 'Test',
        mobile: '9665596655',
        email: 't@example.com',
        addressLine: 'Line 1',
        city: 'Chennai',
        state: 'Tamil Nadu',
        pincode: '600001',
        dependencyVintagePercent: '10',
        anchorRelationshipVintageMonths: '12',
        selectedSubProgramId: 'sp-1',
        consentKyc: true,
        consentBureau: true,
        consentAccountAggregator: true,
        consentComms: true,
      },
      app({ intakeCompletedStep: steps.kyc }),
      'borrower',
    )
    const step = inferFirstIncompleteIntakeStep(
      form,
      steps,
      'BORROWER_SELF_SERVICE',
      workflows,
      false,
      false,
    )
    expect(step).toBe(steps.kyc)
  })
})
