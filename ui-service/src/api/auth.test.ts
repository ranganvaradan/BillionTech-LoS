import { describe, expect, it, vi } from 'vitest'
import { postLogin } from './auth'
import { http } from './http'

vi.mock('./http', () => ({
  http: {
    post: vi.fn(),
  },
}))

describe('postLogin', () => {
  it('maps API body to SessionUser', async () => {
    vi.mocked(http.post).mockResolvedValue({
      data: {
        userId: 'a1000000-0000-0000-0000-000000000001',
        name: 'Test',
        email: 't@b.com',
        role: 'BORROWER',
        institution: 'Credinnov',
      },
    })
    const u = await postLogin('t@b.com', 'x')
    expect(u.userId).toBe('a1000000-0000-0000-0000-000000000001')
    expect(u.role).toBe('BORROWER')
  })
})
