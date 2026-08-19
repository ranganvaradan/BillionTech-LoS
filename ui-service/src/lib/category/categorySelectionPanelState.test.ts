import { describe, expect, it } from 'vitest'
import {
  buildCategorySelectedResult,
  categoryPinnedSelectionFromApplication,
  hasCategoryPin,
  mergeCategoryEvaluateResult,
  shouldPreservePinOnEvaluateError,
} from './categorySelectionPanelState'

const PINNED: NonNullable<ReturnType<typeof buildCategorySelectedResult>['selected']> = {
  applicationId: 'app-1',
  categoryId: 'cat-1',
  categoryCode: 'STARTER_LOAN',
  categoryVersion: 1,
  categoryDisplayName: 'STARTER LOAN (v1)',
  workflowId: 'wf-1',
  workflowVersion: 2,
  workflowName: 'Default Workflow - Individual - Business Term Loan (v2)',
  selectionSource: 'RM_SELECTED',
}

describe('categorySelectionPanelState', () => {
  it('builds CATEGORY_SELECTED from application pin', () => {
    const pin = categoryPinnedSelectionFromApplication({
      id: 'app-1',
      applicationNumber: 'A1',
      customerId: 'c1',
      borrowerType: 'INDIVIDUAL',
      loanProduct: 'BUSINESS_TERM_LOAN',
      requestedAmount: 100000,
      interestRate: null,
      tenureMonths: 12,
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
      workflowResolutionSource: 'CATEGORY_SELECTION',
      categorySelectionState: 'CATEGORY_SELECTED',
      selectedCustomerCategoryId: 'cat-1',
      selectedCustomerCategoryCode: 'STARTER_LOAN',
      selectedCustomerCategoryVersion: 1,
      selectedCustomerCategoryDisplayName: 'STARTER LOAN (v1)',
      workflowId: 'wf-1',
      workflowVersion: 2,
      workflowName: 'Default Workflow - Individual - Business Term Loan (v2)',
      categorySelectionSource: 'RM_SELECTED',
    })
    expect(pin).toMatchObject({ categoryCode: 'STARTER_LOAN', selectionSource: 'RM_SELECTED' })
    expect(buildCategorySelectedResult(pin!).state).toBe('CATEGORY_SELECTED')
  })

  it('does not replace CATEGORY_SELECTED with proposition resolution state', () => {
    const current = buildCategorySelectedResult(PINNED)
    const incoming = {
      state: 'EXPLICIT_PROPOSITION_SELECTION_REQUIRED' as const,
      eligible: [],
      propositions: [{ categoryId: 'x', name: 'Other', code: 'X', versionNo: 1 }],
    }
    const merged = mergeCategoryEvaluateResult(current, incoming, 1, 1)
    expect(merged?.state).toBe('CATEGORY_SELECTED')
  })

  it('accepts fresh CATEGORY_SELECTED from evaluate', () => {
    const incoming = buildCategorySelectedResult({
      ...PINNED,
      categoryDisplayName: 'STARTER LOAN (v1) refreshed',
    })
    const merged = mergeCategoryEvaluateResult(null, incoming, 2, 2)
    expect(merged?.selected?.categoryDisplayName).toContain('refreshed')
  })

  it('drops stale async responses', () => {
    const incoming = buildCategorySelectedResult(PINNED)
    expect(mergeCategoryEvaluateResult(null, incoming, 1, 2)).toBeNull()
  })

  it('preserves pin on evaluate error when pin exists', () => {
    expect(shouldPreservePinOnEvaluateError(PINNED, null)).toBe(true)
    expect(
      shouldPreservePinOnEvaluateError(null, buildCategorySelectedResult(PINNED)),
    ).toBe(true)
    expect(shouldPreservePinOnEvaluateError(null, null)).toBe(false)
  })

  it('detects complete category pin', () => {
    expect(hasCategoryPin(PINNED)).toBe(true)
    expect(hasCategoryPin({ ...PINNED, workflowId: undefined })).toBe(false)
  })
})
