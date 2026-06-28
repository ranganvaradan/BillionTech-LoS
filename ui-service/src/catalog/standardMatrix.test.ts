import { describe, expect, it } from 'vitest'
import { BORROWER_TYPE_ORDER } from './borrowerTypes'
import { LOAN_PRODUCT_CODES } from './loanProducts'
import { allBorrowerProductSegments } from './standardMatrix'

describe('standard 4×8 matrix', () => {
  it('yields 32 borrower × product segments (default policy grid)', () => {
    const s = allBorrowerProductSegments()
    expect(s).toHaveLength(32)
    expect(BORROWER_TYPE_ORDER.length * LOAN_PRODUCT_CODES.length).toBe(32)
    const keys = new Set(s.map((x) => `${x.borrowerType}::${x.loanProduct}`))
    expect(keys.size).toBe(32)
  })
})
