import { describe, expect, it } from 'vitest'
import { applicationPartyLabels, getPartyNoun, remapPartySnapshotKeys, resolveIntakeSegment } from './applicationPartyLabels'

describe('applicationPartyLabels', () => {
  it('defaults null/undefined segment to borrower', () => {
    expect(resolveIntakeSegment(null)).toBe('BORROWER')
    expect(getPartyNoun(undefined)).toBe('Borrower')
    expect(applicationPartyLabels().profileTab).toBe('Borrower profile')
  })

  it('uses anchor terminology when segment is ANCHOR', () => {
    const L = applicationPartyLabels('ANCHOR')
    expect(L.party).toBe('Anchor')
    expect(L.profileTab).toBe('Anchor profile')
    expect(L.submittedDetailsTitle).toBe('Anchor submitted details')
    expect(L.entityClassRow).toBe('Entity class')
  })

  it('remaps borrower snapshot type key for anchor', () => {
    expect(remapPartySnapshotKeys({ 'Borrower type': 'COMPANY' }, 'ANCHOR')).toEqual({
      'Anchor type': 'COMPANY',
    })
    expect(remapPartySnapshotKeys({ 'Borrower type': 'INDIVIDUAL' }, 'BORROWER')).toEqual({
      'Borrower type': 'INDIVIDUAL',
    })
  })
})
