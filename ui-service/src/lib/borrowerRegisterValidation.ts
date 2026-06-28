/** Client-side rules aligned with server registration validation. */
export function validateRegisterPassword(p: string): string | null {
  if (p.length < 8) return 'Password must be at least 8 characters.'
  if (!/[0-9]/.test(p)) return 'Password must include at least one number.'
  if (!/[^A-Za-z0-9\s]/.test(p)) return 'Password must include at least one special character.'
  return null
}

export function normalizeRegisterMobile(m: string): string {
  return m.replace(/\D/g, '')
}
