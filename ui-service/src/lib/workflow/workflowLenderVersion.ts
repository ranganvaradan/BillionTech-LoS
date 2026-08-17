/**
 * Lender-facing workflow version presentation.
 *
 * `id` is the immutable Workflow Version identity.
 * `workflowFamilyId` is the stable journey identity.
 * `version` is a human-facing monotonic number within the family — not identity authority.
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
  publicationStatus?: string | null
}): string {
  const ver = lenderFacingWorkflowVersion(w)
  const raw = String(w.publicationStatus || (w.active ? 'ACTIVE' : 'DRAFT')).toUpperCase()
  const pretty =
    raw === 'DRAFT'
      ? 'Draft'
      : raw === 'ACTIVE'
        ? 'Active'
        : raw === 'SUPERSEDED'
          ? 'Superseded'
          : raw === 'RETIRED'
            ? 'Retired'
            : raw
  return `Version ${ver} · ${pretty}`
}
