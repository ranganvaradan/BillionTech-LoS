import { describe, expect, it } from 'vitest'
import { validateRegisterPassword } from './borrowerRegisterValidation'

describe('borrowerRegisterValidation', () => {
  it('rejects short password', () => {
    expect(validateRegisterPassword('abc')).toBeTruthy()
  })
  it('requires number and special', () => {
    expect(validateRegisterPassword('abcdefgh') ?? '').toMatch(/number|special|8/i)
    expect(validateRegisterPassword('abcdefgh1')).toMatch(/special/i)
    expect(validateRegisterPassword('Abcdefg1!')).toBeNull()
  })
})
