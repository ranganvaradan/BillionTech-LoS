import { describe, expect, it } from 'vitest'
import { canAccessAdminConfigNav, isBorrowerRole } from './types'

describe('canAccessAdminConfigNav', () => {
  it('allows Administrator and Credit Manager', () => {
    expect(canAccessAdminConfigNav('ADMINISTRATOR')).toBe(true)
    expect(canAccessAdminConfigNav('CREDIT_MANAGER')).toBe(true)
  })
  it('hides for other staff roles', () => {
    expect(canAccessAdminConfigNav('CREDIT_OFFICER')).toBe(false)
    expect(canAccessAdminConfigNav('SALES_OFFICER')).toBe(false)
    expect(canAccessAdminConfigNav('BORROWER')).toBe(false)
  })
})

describe('isBorrowerRole', () => {
  it('matches borrower', () => {
    expect(isBorrowerRole('BORROWER')).toBe(true)
    expect(isBorrowerRole('CREDIT_OFFICER')).toBe(false)
  })
})
