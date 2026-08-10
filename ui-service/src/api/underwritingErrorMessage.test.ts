import { describe, expect, it } from 'vitest'
import { ApiError } from './http'
import { messageForUnderwritingAction } from './underwritingErrorMessage'

describe('messageForUnderwritingAction', () => {
  it('hides ClassCastException / HashMap cast text', () => {
    const err = new ApiError(
      'class java.util.HashMap cannot be cast to class java.lang.String',
      500,
      {},
      {
        serverMessage: 'class java.util.HashMap cannot be cast to class java.lang.String',
      },
    )
    const msg = messageForUnderwritingAction(err)
    expect(msg.toLowerCase()).not.toContain('hashmap')
    expect(msg.toLowerCase()).not.toContain('classcast')
    expect(msg).toContain("couldn't complete the credit assessment")
  })

  it('maps missing-information reason to business copy', () => {
    const err = new ApiError('Incomplete', 422, {}, { reason: 'MISSING_INFORMATION' })
    expect(messageForUnderwritingAction(err)).toContain('required information is missing')
  })

  it('maps unexpected 500 generic message to support copy', () => {
    const err = new ApiError('An unexpected error occurred', 500, {}, {
      serverMessage: 'An unexpected error occurred',
    })
    expect(messageForUnderwritingAction(err)).toContain("couldn't complete the credit assessment")
  })

  it('does not treat missing info as borrower FAIL wording', () => {
    const err = new ApiError('scorecard inputs incomplete', 422, {}, {
      serverMessage: 'scorecard inputs incomplete',
    })
    const msg = messageForUnderwritingAction(err)
    expect(msg.toLowerCase()).not.toContain('declined')
    expect(msg.toLowerCase()).not.toMatch(/\bfail\b/)
    expect(msg).toContain('required information is missing')
  })
})
