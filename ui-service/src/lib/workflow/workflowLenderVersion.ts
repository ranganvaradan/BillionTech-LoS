/**
 * Lender-facing workflow version presentation.
 *
 * Persisted `version` is the content identity used by Category → Workflow Version locking.
 * Create starts at 1. Draft (inactive) updates must not inflate it (see backend).
 * Active updates still increment for content-identity tracking.
 */

export function lenderFacingWorkflowVersion(w: {
  version?: number | null
  active?: boolean | null
}): number {
  const v = Number(w.version)
  if (!Number.isFinite(v) || v < 1) return 1
  return Math.floor(v)
}

export function formatLenderWorkflowVersionLabel(w: {
  version?: number | null
  active?: boolean | null
}): string {
  const ver = lenderFacingWorkflowVersion(w)
  const status = w.active ? 'Active' : 'Draft'
  return `Version ${ver} · ${status}`
}
