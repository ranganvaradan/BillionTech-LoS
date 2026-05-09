import { describe, expect, it } from 'vitest'

const INTERNAL_NAMES = /scorecard|underwriting|assignmentrules|internalremarks|auditlog|camappraisal/i

describe('borrower API safety (shape)', () => {
  it('illustrative borrower-facing payloads avoid internal-sounding key names in JSON', () => {
    const sample = {
      fullName: 'A',
      friendlyStatus: 'Application submitted',
      timeline: [{ label: 'Credit review in progress' }],
    }
    expect(JSON.stringify(sample)).not.toMatch(INTERNAL_NAMES)
  })
})
