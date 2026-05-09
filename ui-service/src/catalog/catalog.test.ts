import { describe, expect, it } from 'vitest'
import { BORROWER_TYPE_LABELS, BORROWER_TYPE_ORDER } from './borrowerTypes'
import { LOAN_PRODUCT_CODES, LOAN_PRODUCT_LABELS, isLoanProductCode, loanProductLabel, toLoanProductCodeOrOriginal } from './loanProducts'

describe('loan product catalog', () => {
  it('has 8 codes with business-facing labels (no code in display strings)', () => {
    expect(LOAN_PRODUCT_CODES).toHaveLength(8)
    for (const c of LOAN_PRODUCT_CODES) {
      expect(LOAN_PRODUCT_LABELS[c]).toBeDefined()
      expect(LOAN_PRODUCT_LABELS[c]).not.toMatch(/PERSONAL_LOAN|LOAN_AGAINST/)
    }
  })

  it('maps known legacy labels to a standard code in toLoanProductCodeOrOriginal', () => {
    expect(toLoanProductCodeOrOriginal('Personal')).toBe('PERSONAL_LOAN')
    expect(toLoanProductCodeOrOriginal('LAP')).toBe('LOAN_AGAINST_PROPERTY')
  })
})

describe('borrower type catalog', () => {
  it('aligns 4 types with display labels', () => {
    expect(BORROWER_TYPE_ORDER).toEqual(['INDIVIDUAL', 'PROPRIETOR', 'PARTNERSHIP', 'COMPANY'])
    expect(BORROWER_TYPE_LABELS.INDIVIDUAL).toBe('Individual')
    expect(BORROWER_TYPE_LABELS.PROPRIETOR).toBe('Proprietor')
  })
})

describe('isLoanProductCode', () => {
  it('returns true for standard codes', () => {
    expect(isLoanProductCode('PERSONAL_LOAN')).toBe(true)
    expect(isLoanProductCode('LOAN_AGAINST_GOLD')).toBe(true)
  })
  it('returns false for random text', () => {
    expect(isLoanProductCode('foo')).toBe(false)
  })
})

describe('loanProductLabel', () => {
  it('resolves a code to its label and legacy alias to a label', () => {
    expect(loanProductLabel('PERSONAL_LOAN')).toBe('Personal Loan')
    expect(loanProductLabel('Gold Loan')).toBe('Loan Against Gold')
  })
})
