import { describe, expect, it } from 'vitest'
import { documentSlotsForBorrowerType } from './intakeDocumentSlots'

describe('documentSlotsForBorrowerType', () => {
  it('includes GST_RETURN for a company borrower', () => {
    const types = documentSlotsForBorrowerType('COMPANY').map((s) => s.documentType)
    expect(types).toContain('GST_RETURN')
    expect(types).toContain('PAN_CARD')
  })

  it('includes INCOME_PROOF for individuals', () => {
    const types = documentSlotsForBorrowerType('INDIVIDUAL').map((s) => s.documentType)
    expect(types).toContain('INCOME_PROOF')
  })
})
