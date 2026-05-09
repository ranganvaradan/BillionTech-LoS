import { describe, expect, it } from 'vitest'
import { isBorrowerDeletableApplicationStatus } from './borrowerApplicationDeletable'

describe('isBorrowerDeletableApplicationStatus', () => {
  it('allows DRAFT and CONSENT_PENDING', () => {
    expect(isBorrowerDeletableApplicationStatus('DRAFT')).toBe(true)
    expect(isBorrowerDeletableApplicationStatus('CONSENT_PENDING')).toBe(true)
  })
  it('rejects KYC and later', () => {
    expect(isBorrowerDeletableApplicationStatus('KYC_IN_PROGRESS')).toBe(false)
    expect(isBorrowerDeletableApplicationStatus('DISBURSED')).toBe(false)
  })
})
