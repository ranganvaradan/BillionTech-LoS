import { describe, expect, it } from 'vitest'
import { borrowerStatusPath, buildWhatsAppStatusShareUrl } from './borrowerShare'

describe('borrowerStatusPath', () => {
  it('builds path-only URL', () => {
    expect(borrowerStatusPath('6ba7b810-9dad-11d1-80b4-00c04fd430c8')).toBe(
      '/borrower/status/6ba7b810-9dad-11d1-80b4-00c04fd430c8',
    )
  })

  it('strips origin trailing slash', () => {
    expect(borrowerStatusPath('6ba7b810-9dad-11d1-80b4-00c04fd430c8', 'https://los.example.com/')).toBe(
      'https://los.example.com/borrower/status/6ba7b810-9dad-11d1-80b4-00c04fd430c8',
    )
  })
})

describe('buildWhatsAppStatusShareUrl', () => {
  it('formats wa.me with digits and encoded text', () => {
    const u = new URL(
      buildWhatsAppStatusShareUrl('919811112222', 'https://app.example.com/borrower/status/x'),
    )
    expect(u.host).toBe('wa.me')
    expect(u.pathname).toBe('/919811112222')
    expect(u.searchParams.get('text')).toMatch(/Track your application/)
  })

  it('falls back when no digits', () => {
    const u = new URL(buildWhatsAppStatusShareUrl('', 'https://a/b'))
    expect(u.pathname).toBe('/')
    expect(u.searchParams.get('text')).toBeDefined()
  })
})
