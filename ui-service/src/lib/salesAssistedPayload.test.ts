import { describe, expect, it } from 'vitest'
import { buildSalesAssistedCreatePayload } from '@/lib/salesAssistedPayload'

describe('buildSalesAssistedCreatePayload', () => {
  it('tags sales-assisted intake for ownership + metadata on the server', () => {
    const p = buildSalesAssistedCreatePayload({
      borrowerType: 'INDIVIDUAL',
      loanProduct: 'PERSONAL_LOAN',
      requestedAmount: 100_000,
      tenureMonths: 12,
      salesOfficerName: 'Rep',
      salesOfficerId: 'BR1',
      borrowerMobile: '9876543210',
      consentAccepted: true,
      fullName: 'Sahil C',
      email: 'sahil@gmail.com',
      phone: '9876543210',
      purpose: 'Test',
      businessName: '',
      gstin: '',
    })
    expect(p.personalInfo?.intakeMode).toBe('SALES_ASSISTED')
    expect(p.personalInfo?.journeyChannel).toBe('SALES_ASSISTED')
    expect(p.personalInfo?.assistedChannel).toBe('SALES_ASSISTED')
  })
})
