import { describe, expect, it } from 'vitest'
import {
  classifyCustomerCategory,
  classifyWorkflow,
  isDay1SeedCategory,
  isPlatformDefaultWorkflow,
} from '@/lib/lenderConfigVisibility'

describe('lenderConfigVisibility', () => {
  it('classifies Day-1 seeds as DEMO_TEST', () => {
    expect(isDay1SeedCategory('CC_DAY1_IND_BORROWER')).toBe(true)
    expect(classifyCustomerCategory({ code: 'CC_DAY1_IND_BORROWER' })).toBe('DEMO_TEST')
    expect(classifyCustomerCategory({ code: 'CC_CLEAN_STARTER_LOAN' })).toBe('LENDER_CONFIG')
  })

  it('classifies Default Workflows as SYSTEM_REFERENCE', () => {
    expect(isPlatformDefaultWorkflow('Default Workflow - Individual - Business Term Loan')).toBe(true)
    expect(classifyWorkflow({ name: 'Default Workflow - Individual - Business Term Loan' })).toBe(
      'SYSTEM_REFERENCE',
    )
    expect(classifyWorkflow({ name: 'Bank Starter Journey' })).toBe('LENDER_CONFIG')
  })

  it('retired categories are HISTORICAL', () => {
    expect(classifyCustomerCategory({ code: 'CC_CLEAN_X', status: 'RETIRED' })).toBe('HISTORICAL')
  })
})
