import { describe, expect, it } from 'vitest'
import {
  canonicalLosProductOption,
  distinctCanonicalLosProductOptions,
  looksLikeWorkflowNameLabel,
} from './losProductOption'

describe('canonicalLosProductOption', () => {
  it('uses canonical product name from shared catalog, not workflow name', () => {
    const dto = {
      loanProduct: 'TERM_LOAN',
      sampleWorkflowName: 'Default Workflow - Individual - Term Loan',
    }

    const opt = canonicalLosProductOption(dto)
    expect(opt.code).toBe('TERM_LOAN')
    expect(opt.label).toBe('Term Loan')
    expect(opt.label).not.toBe(dto.sampleWorkflowName)
    expect(looksLikeWorkflowNameLabel(opt.label)).toBe(false)
  })

  it('maps BUSINESS_WC_INVOICE_DISCOUNTING to intake-aligned business label', () => {
    const dto = {
      loanProduct: 'BUSINESS_WC_INVOICE_DISCOUNTING',
      sampleWorkflowName: 'Default Workflow - Proprietor - Business Working Capital Loan -- Invoice Discounting',
    }

    const opt = canonicalLosProductOption(dto)
    expect(opt.code).toBe('BUSINESS_WC_INVOICE_DISCOUNTING')
    expect(opt.label).toBe('Business Working Capital Loan -- Invoice Discounting')
    expect(opt.label).not.toContain('Default Workflow')
  })

  it('never renders [object Object]', () => {
    const dto = { loanProduct: 'PERSONAL_LOAN', sampleWorkflowName: 'ignored' }
    expect(String(dto)).toBe('[object Object]')

    const opt = canonicalLosProductOption(dto)
    expect(opt.code).not.toContain('[object Object]')
    expect(opt.label).not.toContain('[object Object]')
    expect(opt.label).toBe('Personal Loan')
  })

  it('never produces blank labels for known canonical codes', () => {
    const dto = { loanProduct: 'BUSINESS_TERM_LOAN' }
    const opt = canonicalLosProductOption(dto)
    expect(opt.label).toBe('Business Term Loan')
    expect(opt.label.trim().length).toBeGreaterThan(0)
  })
})

describe('distinctCanonicalLosProductOptions', () => {
  it('dedupes duplicate workflows for the same canonical product code', () => {
    const rows = [
      { loanProduct: 'TERM_LOAN', borrowerType: 'INDIVIDUAL', sampleWorkflowName: 'Default Workflow - Individual - Term Loan' },
      { loanProduct: 'TERM_LOAN', borrowerType: 'COMPANY', sampleWorkflowName: 'Default Workflow - Company - Term Loan' },
      { loanProduct: 'TERM_LOAN', borrowerType: 'PROPRIETOR', sampleWorkflowName: 'Default Workflow - Proprietor - Term Loan' },
      { loanProduct: 'PERSONAL_LOAN', borrowerType: 'INDIVIDUAL', sampleWorkflowName: 'Default Workflow - Individual - Personal Loan' },
    ]

    const opts = distinctCanonicalLosProductOptions(rows)
    expect(opts.map((o) => o.code).sort()).toEqual(['PERSONAL_LOAN', 'TERM_LOAN'])
    expect(opts.every((o) => !looksLikeWorkflowNameLabel(o.label))).toBe(true)
    expect(opts.find((o) => o.code === 'TERM_LOAN')?.label).toBe('Term Loan')
  })

  it('counts unique product codes with zero duplicates', () => {
    const rows = [
      { loanProduct: 'TERM_LOAN' },
      { loanProduct: 'TERM_LOAN' },
      { loanProduct: 'BUSINESS_TERM_LOAN' },
    ]
    const opts = distinctCanonicalLosProductOptions(rows)
    const codes = opts.map((o) => o.code)
    const unique = new Set(codes)
    expect(opts.length).toBe(unique.size)
    expect(opts.length - unique.size).toBe(0)
  })
})

describe('looksLikeWorkflowNameLabel', () => {
  it('flags typical workflow naming patterns', () => {
    expect(looksLikeWorkflowNameLabel('Default Workflow - Individual - Personal Loan')).toBe(true)
    expect(looksLikeWorkflowNameLabel('Business Term Loan')).toBe(false)
  })
})
