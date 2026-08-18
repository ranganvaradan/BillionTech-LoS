import { describe, expect, it } from 'vitest'
import { buildStaffStepLabels, staffIntakeStepIndices } from './staffIntakeSteps'

describe('staffIntakeSteps', () => {
  it('places Category after Borrower and before Documents', () => {
    const steps = staffIntakeStepIndices(false, false)
    expect(steps.product).toBe(0)
    expect(steps.borrower).toBe(1)
    expect(steps.category).toBe(2)
    expect(steps.documents).toBe(3)
    expect(steps.consent).toBe(4)
    expect(steps.kyc).toBe(5)
    expect(buildStaffStepLabels(false, false)).toEqual([
      'Product',
      'Borrower',
      'Category',
      'Documents',
      'Consent',
      'KYC',
      'Review',
    ])
  })
})
