import type { SessionUser } from './types'

/** Build headers used by the axios client after demo login. */
export function xHeadersForUser(u: SessionUser | null): Record<string, string> {
  if (!u?.userId) return {}
  return {
    'X-User-Id': u.userId,
    'X-User-Role': u.role,
    ...(u.name ? { 'X-User-Name': u.name } : {}),
  }
}
