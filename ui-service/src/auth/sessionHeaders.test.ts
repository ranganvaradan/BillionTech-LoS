import { describe, expect, it } from 'vitest'
import { xHeadersForUser } from './sessionHeaders'

describe('xHeadersForUser', () => {
  it('sends X-User-Id and X-User-Role when session exists', () => {
    const h = xHeadersForUser({
      userId: 'a1000000-0000-0000-0000-000000000001',
      name: 'A',
      email: 'a@b.com',
      role: 'CREDIT_MANAGER',
      institution: 'X',
    })
    expect(h['X-User-Id']).toBe('a1000000-0000-0000-0000-000000000001')
    expect(h['X-User-Role']).toBe('CREDIT_MANAGER')
  })
  it('returns empty object when not logged in', () => {
    expect(xHeadersForUser(null)).toEqual({})
  })
  it('sends Authorization Bearer when accessToken present', () => {
    const h = xHeadersForUser({
      userId: 'a1000000-0000-0000-0000-000000000001',
      name: 'A',
      email: 'a@b.com',
      role: 'CREDIT_MANAGER',
      institution: 'X',
      accessToken: 'eyJ.test.token',
    })
    expect(h.Authorization).toBe('Bearer eyJ.test.token')
  })
})
