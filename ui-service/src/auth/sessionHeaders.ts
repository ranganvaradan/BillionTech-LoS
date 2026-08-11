import type { SessionUser } from './types'

/** Build headers used by the axios client after login (Bearer JWT preferred; X-User-* for staging). */
export function xHeadersForUser(u: SessionUser | null): Record<string, string> {
  if (!u?.userId) return {}
  const headers: Record<string, string> = {
    'X-User-Id': u.userId,
    'X-User-Role': u.role,
    ...(u.name ? { 'X-User-Name': u.name } : {}),
  }
  if (u.accessToken?.trim()) {
    headers.Authorization = `Bearer ${u.accessToken.trim()}`
  }
  return headers
}
