/**
 * Business-facing presentation helpers for Credit Assessment (UX-4B4).
 * Display-only — does not invent thresholds or underwriting outcomes.
 */

export type ConcernSeverity = 'needs_attention' | 'manual_review' | 'failed' | 'missing'

export type AssessmentConcern = {
  severity: ConcernSeverity
  label: string
  detail?: string
}

export type WhyOutcomeRow = {
  reason: string
  observed?: string
  expected?: string
  outcome?: string
}

const OUTCOME_LABELS: Record<string, string> = {
  APPROVE: 'Approved',
  APPROVED: 'Approved',
  REJECT: 'Declined',
  REJECTED: 'Declined',
  MANUAL_REVIEW: 'Manual Credit Review',
  REFER: 'Needs Attention',
  DECLINE: 'Declined',
  PASS: 'Pass',
  FAIL: 'Fail',
  DATA_INSUFFICIENT: 'Missing Information',
}

export function formatAssessmentOutcome(raw: string | null | undefined): string {
  if (raw == null || String(raw).trim() === '') return '—'
  const key = String(raw).trim().toUpperCase()
  if (OUTCOME_LABELS[key]) return OUTCOME_LABELS[key]
  return String(raw)
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/\b\w/g, (c) => c.toUpperCase())
}

export function formatCamStatusLabel(raw: string | null | undefined): string {
  const key = String(raw ?? '').toUpperCase()
  switch (key) {
    case 'DRAFT':
      return 'Draft'
    case 'SUBMITTED':
      return 'Submitted for Review'
    case 'SENT_BACK':
      return 'Sent Back'
    case 'APPROVED':
      return 'Approved'
    case 'REJECTED':
      return 'Rejected'
    case '':
    case 'NULL':
    case 'UNDEFINED':
      return 'Not started'
    default:
      return formatAssessmentOutcome(raw)
  }
}

export function concernSeverityLabel(severity: ConcernSeverity): string {
  switch (severity) {
    case 'needs_attention':
      return 'Needs Attention'
    case 'manual_review':
      return 'Manual Review'
    case 'failed':
      return 'Failed'
    case 'missing':
      return 'Missing Information'
  }
}

export function concernSeverityTone(severity: ConcernSeverity): 'warning' | 'danger' | 'info' {
  if (severity === 'failed') return 'danger'
  if (severity === 'missing') return 'info'
  return 'warning'
}

/** Build concise why-rows from underwriting meta reasons (strings only — no invented thresholds). */
export function whyRowsFromReasons(reasons: unknown): WhyOutcomeRow[] {
  if (!Array.isArray(reasons)) {
    if (typeof reasons === 'string' && reasons.trim()) {
      return [{ reason: reasons.trim() }]
    }
    return []
  }
  return reasons
    .map((r) => String(r ?? '').trim())
    .filter(Boolean)
    .map((reason) => ({ reason }))
}

type ParamLike = {
  parameter?: string
  valueUsed?: string
  condition?: string
  decision?: string
  reason?: string
  hardRule?: boolean
  matched?: boolean
}

/**
 * Prefer hard-rule / decision-bearing parameter rows when present.
 * Only surfaces fields already on the evaluation row — never invents thresholds.
 */
export function whyRowsFromParameterResults(parameterResults: unknown): WhyOutcomeRow[] {
  if (!Array.isArray(parameterResults)) return []
  const rows = parameterResults as ParamLike[]
  const interesting = rows.filter(
    (r) =>
      r &&
      (r.hardRule === true ||
        (r.decision != null && String(r.decision).trim() !== '') ||
        (r.reason != null && String(r.reason).trim() !== '')),
  )
  const source = interesting.length > 0 ? interesting : rows.slice(0, 8)
  const out: WhyOutcomeRow[] = []
  for (const r of source) {
    const reason = String(r.reason || r.parameter || '').trim()
    if (!reason && !r.valueUsed && !r.condition && !r.decision) continue
    out.push({
      reason: reason || 'Rule check',
      observed: r.valueUsed != null && String(r.valueUsed).trim() !== '' ? String(r.valueUsed) : undefined,
      expected: r.condition != null && String(r.condition).trim() !== '' ? String(r.condition) : undefined,
      outcome: r.decision != null ? formatAssessmentOutcome(String(r.decision)) : undefined,
    })
    if (out.length >= 12) break
  }
  return out
}

export function buildAssessmentConcerns(opts: {
  riskFlags: Array<{ severity?: string; label: string }>
  missingItems: string[]
  pendingManualReview: boolean
  failedRuleLabels?: string[]
  hasOverride?: boolean
}): AssessmentConcern[] {
  const out: AssessmentConcern[] = []
  if (opts.pendingManualReview) {
    out.push({
      severity: 'manual_review',
      label: 'Manual review required',
      detail: 'Policy routed this case for a credit review decision.',
    })
  }
  for (const f of opts.failedRuleLabels ?? []) {
    const label = String(f).trim()
    if (label) out.push({ severity: 'failed', label })
  }
  for (const f of opts.riskFlags) {
    const sev = String(f.severity ?? '').toUpperCase()
    const severity: ConcernSeverity =
      sev === 'HIGH' || sev === 'CRITICAL' || sev === 'FAIL' || sev === 'FAILED'
        ? 'failed'
        : 'needs_attention'
    out.push({ severity, label: f.label })
  }
  for (const m of opts.missingItems) {
    const label = String(m).trim()
    if (label) out.push({ severity: 'missing', label })
  }
  if (opts.hasOverride) {
    out.push({
      severity: 'needs_attention',
      label: 'Manual underwriting override applied',
      detail: 'Original policy outcome remains in History.',
    })
  }
  return out
}

export function hasAssessmentResult(opts: {
  uwMeta: unknown
  latestEval: unknown
  creditDecision: string | null | undefined
}): boolean {
  return Boolean(opts.uwMeta || opts.latestEval || (opts.creditDecision && String(opts.creditDecision).trim()))
}
