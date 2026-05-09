/** Demo session after POST /api/v1/auth/login (not JWT). */
export interface SessionUser {
  userId: string
  name: string
  email: string
  role: string
  institution: string
}

const STORAGE_KEY = 'los_demo_session_v1'

export function loadSessionUser(): SessionUser | null {
  if (typeof window === 'undefined') return null
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const p = JSON.parse(raw) as unknown
    if (!p || typeof p !== 'object') return null
    const o = p as Record<string, unknown>
    if (
      typeof o.userId !== 'string' ||
      typeof o.name !== 'string' ||
      typeof o.email !== 'string' ||
      typeof o.role !== 'string' ||
      typeof o.institution !== 'string'
    ) {
      return null
    }
    return o as unknown as SessionUser
  } catch {
    return null
  }
}

export function saveSessionUser(user: SessionUser): void {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(user))
}

export function clearSessionUser(): void {
  window.localStorage.removeItem(STORAGE_KEY)
}

/** Admin-only sidebar entries (workflows, rules, IAM-style screens). */
export function canAccessAdminConfigNav(role: string): boolean {
  return role === 'ADMINISTRATOR' || role === 'CREDIT_MANAGER'
}

export const BORROWER_ROLE = 'BORROWER'

export function isBorrowerRole(role: string): boolean {
  return role === BORROWER_ROLE
}
