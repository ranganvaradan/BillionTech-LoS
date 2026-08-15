export type BtRuntimeEnv = {
  surface?: string
  label?: string | null
  internalToken?: string | null
}

declare global {
  interface Window {
    __BT_RUNTIME__?: BtRuntimeEnv
  }
}

/** Deploy-time overlay (same JS artifact; different runtime-env.js per surface). */
export function readBtRuntime(): BtRuntimeEnv {
  return typeof window !== 'undefined' ? window.__BT_RUNTIME__ ?? {} : {}
}

/**
 * True on Client / Client-Test staging surfaces.
 * Used to hide transitional lender-facing concepts (Live UW Rules, Policy Sets)
 * without removing Internal/debug access or backend runtime tables.
 */
export function isClientLenderSurface(): boolean {
  const rt = readBtRuntime()
  const surface = (rt.surface || '').toUpperCase()
  if (surface === 'CLIENT' || surface === 'CLIENT_TEST') return true
  if (typeof window === 'undefined') return false
  // Staging Client nginx often serves on :8085
  if (window.location.port === '8085') return true
  return false
}

/** Hide Live Underwriting Rules + Policy Sets from normal Client lender navigation. */
export function hideLegacyDecisionConfigFromLenderNav(): boolean {
  return isClientLenderSurface()
}

/**
 * Visible staging identity. Prefer deploy-time label; fall back to port topology.
 * Does not change business behaviour.
 */
export function stagingEnvironmentLabel(): string | null {
  const rt = readBtRuntime()
  const explicit = typeof rt.label === 'string' ? rt.label.trim() : ''
  if (explicit) return explicit
  const surface = (rt.surface || '').toUpperCase()
  if (surface === 'CLIENT' || surface === 'CLIENT_TEST') return 'STAGING — CLIENT TEST'
  if (surface === 'INTERNAL') return 'STAGING — INTERNAL'
  if (typeof window === 'undefined') return null
  const port = window.location.port
  if (port === '8085') return 'STAGING — CLIENT TEST'
  // Host nginx :80 / empty port on staging IP
  if (port === '' || port === '80' || port === '443') return 'STAGING — INTERNAL'
  return null
}

export function runtimeInternalToken(): string | null {
  const t = readBtRuntime().internalToken
  if (typeof t === 'string' && t.trim()) return t.trim()
  const baked = (import.meta.env.VITE_CREDIT_INTELLIGENCE_INTERNAL_TOKEN as string | undefined)?.trim()
  return baked || null
}
