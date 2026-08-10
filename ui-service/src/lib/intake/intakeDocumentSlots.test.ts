import { describe, expect, it } from 'vitest'
import {
  allDocumentSlotsForIntake,
  documentSlotsForAnchorIntake,
  documentSlotsForBorrowerType,
  documentSlotsForInvoiceDiscountingBorrower,
} from './intakeDocumentSlots'
import { createEmptyIntakeFormState } from './intakeTypes'

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

  it('excludes Aadhaar and photograph for anchor intake', () => {
    const types = documentSlotsForAnchorIntake('COMPANY').map((s) => s.documentType)
    expect(types).not.toContain('AADHAAR')
    expect(types).not.toContain('PHOTOGRAPH')
    expect(types).toContain('PAN_CARD')
    expect(types).toContain('GST_RETURN')
  })
})

describe('documentSlotsForInvoiceDiscountingBorrower', () => {
  it('includes SCF docs and skips Aadhaar for company', () => {
    const types = documentSlotsForInvoiceDiscountingBorrower('COMPANY').map((s) => s.documentType)
    expect(types).toContain('ITR')
    expect(types).toContain('CONSTITUTION_DOCS')
    expect(types).toContain('BUYER_NOC')
    expect(types).not.toContain('AADHAAR')
  })

  it('keeps Aadhaar for proprietor ID borrower', () => {
    const types = documentSlotsForInvoiceDiscountingBorrower('PROPRIETOR').map((s) => s.documentType)
    expect(types).toContain('AADHAAR')
    expect(types).toContain('NACH_MANDATE')
  })

  it('allDocumentSlotsForIntake uses ID slots for invoice discounting borrower', () => {
    const form = {
      ...createEmptyIntakeFormState(),
      loanProduct: 'BUSINESS_WC_INVOICE_DISCOUNTING',
      invoiceOnboardingChoice: 'BORROWER' as const,
      borrowerType: 'PROPRIETOR' as const,
    }
    const types = allDocumentSlotsForIntake(form).map((s) => s.documentType)
    expect(types).toContain('PURCHASE_ORDER')
    expect(types).toContain('ITR')
  })
})
