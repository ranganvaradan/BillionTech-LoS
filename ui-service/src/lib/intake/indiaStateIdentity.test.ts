import { describe, expect, it } from 'vitest'
import { indiaStateIdentity, indiaStateMatchesAllowed } from './indiaStateIdentity'

describe('indiaStateIdentity', () => {
  it('treats TN, TAMIL_NADU, and Tamil Nadu as the same identity', () => {
    expect(indiaStateIdentity('TN')).toBe(indiaStateIdentity('Tamil Nadu'))
    expect(indiaStateIdentity('TAMIL_NADU')).toBe(indiaStateIdentity('Tamil Nadu'))
    expect(indiaStateMatchesAllowed('TN', ['Tamil Nadu'])).toBe(true)
    expect(indiaStateMatchesAllowed('KL', ['Tamil Nadu'])).toBe(false)
  })
})
