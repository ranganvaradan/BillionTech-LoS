import { describe, expect, it } from 'vitest'
import { canonicalLosProductOption } from './losProductOption'

describe('canonicalLosProductOption', () => {
  it('maps canonical backend DTO shape into code+human label', () => {
    // Backend shape comes from /admin/live-readiness/product-configuration/options
    // (ProductConfigurationComposeService#distinctProducts)
    const dto = {
      borrowerType: 'COMPANY',
      loanProduct: 'TERM_LOAN',
      intakeSegment: 'BORROWER',
      sampleWorkflowId: '00000000-0000-0000-0000-000000000001',
      sampleWorkflowName: 'Term Loan',
    }

    expect(String(dto)).toBe('[object Object]') // guard: would be the bug

    const opt = canonicalLosProductOption(dto)
    expect(opt.code).toBe('TERM_LOAN')
    expect(opt.label).toBe('Term Loan')
    expect(opt.code).not.toContain('[object Object]')
    expect(opt.label).not.toContain('[object Object]')
  })

  it('falls back to a non-empty label when backend does not include sampleWorkflowName', () => {
    const dto = {
      borrowerType: 'COMPANY',
      loanProduct: 'BUSINESS_TERM_LOAN',
      intakeSegment: 'BORROWER',
      sampleWorkflowId: '00000000-0000-0000-0000-000000000002',
    }

    const opt = canonicalLosProductOption(dto)
    expect(opt.code).toBe('BUSINESS_TERM_LOAN')
    expect(opt.label).toBe('Business Term Loan')
    expect(opt.label).not.toContain('[object Object]')
  })
})

