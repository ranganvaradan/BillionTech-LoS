import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  isCategoryGovernedApplication,
  pinnedWorkflowDisplayFromApplication,
  pinnedWorkflowDisplayFromCategoryHandoff,
} from './pinnedWorkflowDisplay'

const ROOT = resolve(__dirname, '../..')

function read(rel: string): string {
  return readFileSync(resolve(ROOT, rel), 'utf8')
}

describe('APPLICATION-CANONICAL-WORKFLOW-VISIBILITY-1', () => {
  it('CATEGORY_SELECTED_WORKFLOW_PINNED = YES via handoff mapping', () => {
    const pinned = pinnedWorkflowDisplayFromCategoryHandoff({
      applicationId: 'app-1',
      categoryId: 'cat-1',
      categoryCode: 'STARTER',
      categoryVersion: 2,
      categoryDisplayName: 'Starter journey',
      workflowId: 'wf-pinned',
      workflowVersion: 4,
      workflowName: 'Starter WF',
    })
    expect(pinned).not.toBeNull()
    expect(pinned!.workflowId).toBe('wf-pinned')
    expect(pinned!.workflowVersion).toBe(4)
  })

  it('DISPLAYED_WORKFLOW_ID_EQUALS_APPLICATION_PINNED_WORKFLOW_ID = YES', () => {
    const pinned = pinnedWorkflowDisplayFromApplication({
      id: 'app-1',
      applicationNumber: 'A1',
      customerId: 'c1',
      borrowerType: 'INDIVIDUAL',
      loanProduct: 'TERM',
      requestedAmount: 1000,
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
      workflowId: 'wf-pinned',
      workflowVersion: 4,
      workflowName: 'Starter WF',
      workflowResolutionSource: 'CATEGORY_SELECTION',
      categorySelectionState: 'CATEGORY_SELECTED',
      selectedCustomerCategoryCode: 'STARTER',
      selectedCustomerCategoryVersion: 2,
      selectedCustomerCategoryDisplayName: 'Starter journey',
    })
    expect(pinned!.workflowId).toBe('wf-pinned')
  })

  it('DISPLAYED_WORKFLOW_VERSION_EQUALS_APPLICATION_PINNED_WORKFLOW_VERSION = YES', () => {
    const pinned = pinnedWorkflowDisplayFromApplication({
      id: 'app-1',
      applicationNumber: 'A1',
      customerId: 'c1',
      borrowerType: 'INDIVIDUAL',
      loanProduct: 'TERM',
      requestedAmount: 1000,
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
      workflowId: 'wf-pinned',
      workflowVersion: 4,
      workflowName: 'Starter WF',
      workflowResolutionSource: 'CATEGORY_SELECTION',
    })
    expect(pinned!.workflowVersion).toBe(4)
  })

  it('LATEST_WORKFLOW_LOOKUP_FOR_DISPLAY_COUNT = 0 in canonical display surfaces', () => {
    const panel = read('components/category/CategorySelectionPanel.tsx')
    const summary = read('components/workflow/PinnedWorkflowSummary.tsx')
    const pinnedLib = read('lib/workflow/pinnedWorkflowDisplay.ts')

    expect(panel).not.toMatch(/getActiveWorkflow/)
    expect(panel).not.toMatch(/listWorkflows/)
    expect(panel).not.toMatch(/matchingWorkflowsForProduct/)
    expect(panel).not.toMatch(/workflowById/)
    expect(summary).not.toMatch(/getActiveWorkflow/)
    expect(pinnedLib).not.toMatch(/getActiveWorkflow/)
    expect(pinnedLib).not.toMatch(/matchingWorkflowsForProduct/)
  })

  it('DEFAULT_WORKFLOW_LOOKUP_FOR_DISPLAY_COUNT = 0 in canonical display surfaces', () => {
    const panel = read('components/category/CategorySelectionPanel.tsx')
    const wizard = read('components/intake/ApplicationIntakeWizard.tsx')

    expect(panel).not.toMatch(/resolveWorkflowIdForProduct/)
    expect(panel).not.toMatch(/defaultWorkflow/)
    expect(wizard).toMatch(/pinnedWorkflowDisplayFromApplication/)
    expect(wizard).toMatch(/pinnedWorkflowDisplayFromCategoryHandoff/)
  })

  it('LEGACY_RUNTIME_WORKFLOW_LOOKUP_FOR_CANONICAL_DISPLAY_COUNT = 0', () => {
    const panel = read('components/category/CategorySelectionPanel.tsx')
    const summary = read('components/workflow/PinnedWorkflowSummary.tsx')

    expect(panel).not.toMatch(/activeWorkflows/)
    expect(summary).not.toMatch(/activeWorkflows/)
    expect(panel).toMatch(/PinnedWorkflowSummary/)
  })

  it('HISTORICAL_APPLICATION_PIN_REWRITE_COUNT = 0 — non-category apps skip canonical display helper', () => {
    expect(
      pinnedWorkflowDisplayFromApplication({
        id: 'app-1',
        applicationNumber: 'A1',
        customerId: 'c1',
        borrowerType: 'INDIVIDUAL',
        loanProduct: 'TERM',
        requestedAmount: 1000,
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
        workflowId: 'wf-legacy',
        workflowVersion: 1,
        workflowName: 'Legacy WF',
        workflowResolutionSource: 'LEGACY_PRODUCT_CONFIG',
      }),
    ).toBeNull()
  })

  it('PLATFORM_GENERICITY_INVARIANT = PASS — no client/product-specific display branches', () => {
    const files = [
      'components/category/CategorySelectionPanel.tsx',
      'components/workflow/PinnedWorkflowSummary.tsx',
      'lib/workflow/pinnedWorkflowDisplay.ts',
    ]
    for (const f of files) {
      const src = read(f)
      expect(src).not.toMatch(/Vikasam/i)
      expect(src).not.toMatch(/Golden/i)
      expect(src).not.toMatch(/ANCHOR_NAME/i)
    }
  })

  it('isCategoryGovernedApplication detects category pin authority', () => {
    expect(isCategoryGovernedApplication({ workflowResolutionSource: 'CATEGORY_SELECTION' })).toBe(true)
    expect(isCategoryGovernedApplication({ categorySelectionState: 'CATEGORY_SELECTED' })).toBe(true)
    expect(isCategoryGovernedApplication({ workflowResolutionSource: 'LEGACY_PRODUCT_CONFIG' })).toBe(false)
  })
})
