import { describe, expect, it } from 'vitest'
import {
  activeCatalogHasSecuredProduct,
  dedupeByLoanProduct,
  productsForBorrowerType,
  uniqueActiveWorkflowLoanProducts,
  workflowLoanProductDisplayName,
} from './workflowProducts'
import type { WorkflowConfigResponse } from '@/types/workflow'

function wf(over: Partial<WorkflowConfigResponse> & { loanProduct: string }): WorkflowConfigResponse {
  return {
    id: '0',
    name: 'n',
    borrowerType: 'INDIVIDUAL',
    steps: [],
    version: 1,
    createdAt: null,
    active: over.active !== undefined ? over.active : true,
    ...over,
  } as WorkflowConfigResponse
}

describe('uniqueActiveWorkflowLoanProducts', () => {
  it('includes only active workflow loan products', () => {
    const list: WorkflowConfigResponse[] = [
      wf({ loanProduct: 'A', active: true }),
      wf({ loanProduct: 'B', active: false }),
      wf({ loanProduct: 'A', active: true }),
    ]
    expect(uniqueActiveWorkflowLoanProducts(list)).toEqual(['A'])
  })
})

describe('dedupeByLoanProduct', () => {
  it('keeps the highest version for each product', () => {
    const list: WorkflowConfigResponse[] = [
      wf({ loanProduct: 'A', id: '1', version: 1, active: true }),
      wf({ loanProduct: 'A', id: '2', version: 3, active: true }),
    ]
    const d = dedupeByLoanProduct(list)
    expect(d).toHaveLength(1)
    expect(d[0]!.id).toBe('2')
  })
})

describe('activeCatalogHasSecuredProduct', () => {
  it('is false when the catalog is empty for the borrower class', () => {
    expect(activeCatalogHasSecuredProduct([wf({ loanProduct: 'PERSONAL_LOAN', borrowerType: 'INDIVIDUAL' })], 'COMPANY')).toBe(
      false,
    )
  })

  it('is false when only unsecured products exist for that borrower class', () => {
    const list = [wf({ loanProduct: 'PERSONAL_LOAN', borrowerType: 'INDIVIDUAL' })]
    expect(activeCatalogHasSecuredProduct(list, 'INDIVIDUAL')).toBe(false)
  })

  it('is true when a secured product workflow is active', () => {
    for (const loanProduct of ['LOAN_AGAINST_PROPERTY', 'LOAN_AGAINST_SECURITIES', 'LOAN_AGAINST_GOLD']) {
      const list = [wf({ loanProduct, borrowerType: 'INDIVIDUAL' })]
      expect(activeCatalogHasSecuredProduct(list, 'INDIVIDUAL'), loanProduct).toBe(true)
    }
  })
})

describe('productsForBorrowerType + display', () => {
  it('shows business label for codes in workflowLoanProductDisplayName', () => {
    expect(workflowLoanProductDisplayName('PERSONAL_LOAN')).toBe('Personal Loan')
    expect(workflowLoanProductDisplayName('LOAN_AGAINST_GOLD')).toBe('Loan Against Gold')
  })

  it('exposes each product code from active workflows in the product list', () => {
    const list = productsForBorrowerType(
      [
        wf({ id: 'a', loanProduct: 'PERSONAL_LOAN', borrowerType: 'INDIVIDUAL', version: 1 }),
        wf({ id: 'b', loanProduct: 'LOAN_AGAINST_PROPERTY', borrowerType: 'INDIVIDUAL', version: 1 }),
        wf({ id: 'c', loanProduct: 'LOAN_AGAINST_GOLD', borrowerType: 'INDIVIDUAL', version: 1 }),
      ],
      'INDIVIDUAL',
    )
    const names = list.map((w) => w.loanProduct).sort()
    expect(names).toContain('PERSONAL_LOAN')
    expect(names).toContain('LOAN_AGAINST_GOLD')
  })
})
