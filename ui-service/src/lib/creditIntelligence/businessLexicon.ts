/**
 * Business-facing labels for Credit Intelligence / Policy Studio.
 * Engineering enums stay in API payloads; UI must display these labels.
 */

export function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

export function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

/** Map internal decision / simulation outcomes to Credit Manager language. */
export function businessOutcomeLabel(raw: unknown): string {
  if (raw == null || raw === '') return '—'
  const s = String(raw).trim()
  const u = s.toUpperCase().replace(/[\s-]+/g, '_')

  const map: Record<string, string> = {
    PASS: 'Approved',
    PASSED: 'Approved',
    APPROVE: 'Approved',
    APPROVED: 'Approved',
    APPROVE_WITH_CONDITIONS: 'Approved with Conditions',
    APPROVE_WITH: 'Approved with Conditions',
    FAIL: 'Declined',
    FAILED: 'Declined',
    DECLINE: 'Declined',
    DECLINED: 'Declined',
    REJECT: 'Declined',
    REJECTED: 'Declined',
    REFER: 'Manual Credit Review',
    REFERRED: 'Manual Credit Review',
    RECOMMEND_REFER: 'Manual Credit Review',
    DATA_INSUFFICIENT: 'Missing Information',
    DATAINSUFFICIENT: 'Missing Information',
    MISSING_INFORMATION: 'Missing Information',
    DI: 'Missing Information',
    INSUFFICIENT: 'Missing Information',
    COUNTER_OFFER: 'Counter Offer',
    MATCH: 'Matches Expected',
    PENDING: 'Pending Review',
    NEEDS_REVIEW: 'Needs Review',
    READY: 'Ready',
    BLOCKED: 'Action Required',
    DRAFT: 'Draft',
    DRAFT_ONLY: 'Draft Only',
    DONE: 'Complete',
    CURRENT: 'In Progress',
    OPEN: 'Open',
    RESOLVED: 'Resolved',
  }

  if (map[u]) return map[u]
  if (u.includes('INSUFFICIENT') || u === 'DI') return 'Missing Information'
  if (u.includes('REFER')) return 'Manual Credit Review'
  if (u.includes('PASS') || u.includes('APPROVE')) return s.includes('Condition') ? 'Approved with Conditions' : 'Approved'
  if (u.includes('FAIL') || u.includes('DECLINE')) return 'Declined'
  // Soften SCREAMING_SNAKE without inventing meaning
  if (/^[A-Z0-9_]+$/.test(s) && s.includes('_')) {
    return s
      .split('_')
      .map((w) => w.charAt(0) + w.slice(1).toLowerCase())
      .join(' ')
  }
  return s
}

/** Tailwind chip classes — green approved, amber review, blue info, red action, grey technical. */
export function businessOutcomeTone(raw: unknown): 'approved' | 'review' | 'info' | 'action' | 'technical' {
  const u = String(raw ?? '')
    .toUpperCase()
    .replace(/[\s-]+/g, '_')
  if (
    u.includes('PASS') ||
    u.includes('APPROVE') ||
    u === 'MATCH' ||
    u === 'DONE' ||
    u === 'READY' ||
    u === 'RESOLVED' ||
    u === 'GREEN'
  ) {
    return 'approved'
  }
  if (u.includes('FAIL') || u.includes('DECLINE') || u.includes('REJECT') || u === 'BLOCKED' || u === 'RED') {
    return 'action'
  }
  if (u.includes('INSUFFICIENT') || u === 'DI' || u.includes('MISSING')) {
    return 'info'
  }
  if (u.includes('REFER') || u.includes('REVIEW') || u === 'CURRENT' || u === 'AMBER' || u === 'PENDING') {
    return 'review'
  }
  return 'technical'
}

export function outcomeChipClass(raw: unknown): string {
  switch (businessOutcomeTone(raw)) {
    case 'approved':
      return 'bg-emerald-100 text-emerald-900'
    case 'review':
      return 'bg-amber-100 text-amber-900'
    case 'info':
      return 'bg-sky-100 text-sky-900'
    case 'action':
      return 'bg-rose-100 text-rose-900'
    default:
      return 'bg-slate-100 text-slate-700'
  }
}

export function softFixtureLabel(text?: string | null): string {
  if (!text) return 'Demo sample — not real borrower data'
  const t = String(text)
  if (/VALIDATION\s*FIXTURE/i.test(t) || /NOT REAL BORROWER/i.test(t)) {
    return 'Demo sample — not real borrower data'
  }
  return t.replace(/VALIDATION\s*FIXTURE/gi, 'Demo sample').replace(/CANONICAL/gi, 'verified')
}

export function draftOnlyBanner(prospectDemoMode?: boolean): string {
  if (prospectDemoMode) {
    return 'Draft for review only — not live in production lending.'
  }
  return 'Draft / simulation only — not live in production lending.'
}

export function humanizeDataSource(value: string): string {
  if (value === 'VALIDATION_FIXTURES') return 'Demo cases'
  if (value === 'STAGING_APPLICATIONS') return 'Staging applications'
  return businessOutcomeLabel(value)
}

export function formatBusinessInputs(inputs: unknown): string {
  const m = asRecord(inputs)
  const keys = Object.keys(m)
  if (keys.length === 0) return '— (information not provided)'
  return keys
    .slice(0, 4)
    .map((k) => {
      const label = k
        .split('.')
        .slice(-1)[0]
        .replace(/([A-Z])/g, ' $1')
        .replace(/_/g, ' ')
        .trim()
      return `${label}: ${String(m[k])}`
    })
    .join(' · ')
}

/** Pull nested display strings without exposing raw keys as headings. */
export function pickDisplay(obj: unknown, keys: string[]): string | null {
  const r = asRecord(obj)
  for (const k of keys) {
    const v = r[k]
    if (v != null && String(v).trim() && typeof v !== 'object') return String(v)
  }
  return null
}

export function listFromUnknown(v: unknown): string[] {
  if (Array.isArray(v)) return v.map((x) => (typeof x === 'object' ? JSON.stringify(x) : String(x))).filter(Boolean)
  if (typeof v === 'string' && v.trim()) return [v]
  return []
}

export function caseFriendlyTitle(caseCode: string, title?: string | null): string {
  if (title && title.trim()) return title.trim()
  const map: Record<string, string> = {
    CASE_A: 'Strong profile — expected approval',
    CASE_B: 'Legacy default dependence',
    CASE_C: 'Borderline capacity',
    CASE_D: 'Bureau concern',
    CASE_E: 'Missing information',
  }
  return map[caseCode] ?? caseCode.replace(/_/g, ' ')
}

/** Decision Policy domain labels (KYC-2 foundation — presentation only). */
export function decisionPolicyDomainLabel(raw: unknown): string {
  const u = String(raw ?? '')
    .trim()
    .toUpperCase()
    .replace(/[\s-]+/g, '_')
  const map: Record<string, string> = {
    KYC: 'KYC',
    ELIGIBILITY: 'Eligibility',
    CREDIT: 'Credit Underwriting',
    RISK_SCORE: 'Risk / Score',
    LIMIT: 'Limit',
    PRICING: 'Pricing',
    DECISION_REVIEW: 'Decision / Review',
    KYC_ELIGIBILITY: 'KYC & Eligibility',
    CREDIT_UNDERWRITING: 'Credit Underwriting',
    LIMIT_PRICING: 'Limit / Pricing',
    IDENTITY_KYC: 'KYC & Eligibility',
  }
  return map[u] || businessOutcomeLabel(raw)
}

/** KYC requirement-type labels for Data Readiness / future Studio. */
export function kycRequirementTypeLabel(raw: unknown): string {
  const u = String(raw ?? '')
    .trim()
    .toUpperCase()
    .replace(/[\s-]+/g, '_')
  const map: Record<string, string> = {
    VERIFICATION: 'Verification',
    INFORMATION_REQUIREMENT: 'Information Required',
    MATCH_REQUIREMENT: 'Match Requirement',
    COMPLETION_REQUIREMENT: 'Completion Required',
    MANUAL_VERIFICATION: 'Manual Verification',
    ELIGIBILITY_CONDITION: 'Eligibility Condition',
    BOUNDARY_CONDITION: 'Boundary Condition',
    REGULATORY_GUARDRAIL: 'Platform Guardrail',
  }
  return map[u] || businessOutcomeLabel(raw)
}

export function progressStageLabels(): { key: string; label: string }[] {
  return [
    { key: 'upload', label: 'Upload' },
    { key: 'understanding', label: 'AI Understanding' },
    { key: 'ambiguities', label: 'Ambiguous Terms' },
    { key: 'rules', label: 'Business Rules' },
    { key: 'data-readiness', label: 'Data Readiness' },
    { key: 'tests', label: 'Tests' },
    { key: 'simulation', label: 'Simulation' },
    { key: 'approval', label: 'Approval' },
    { key: 'draft', label: 'Draft Ready' },
  ]
}

export function derivePolicyNextStep(input: {
  openAmbiguities: number
  rulesNeedReview: number
  testsGenerated: number
  dataReadinessBlocked?: boolean
  dataReadinessNeedsAttention?: boolean
  canApprove?: boolean
}): { title: string; detail: string; tabHint: string } {
  if (input.openAmbiguities > 0) {
    return {
      title: 'Resolve remaining issues',
      detail: `${input.openAmbiguities} ambiguous term${input.openAmbiguities === 1 ? '' : 's'} still need Credit Head confirmation.`,
      tabHint: 'ambiguities',
    }
  }
  if (input.rulesNeedReview > 0) {
    return {
      title: 'Review proposed business rules',
      detail: `${input.rulesNeedReview} proposed rule${input.rulesNeedReview === 1 ? '' : 's'} await approval.`,
      tabHint: 'rules',
    }
  }
  if (input.dataReadinessBlocked || input.dataReadinessNeedsAttention) {
    return {
      title: 'Check data readiness',
      detail: input.dataReadinessBlocked
        ? 'Critical rules have unresolved data requirements — review Data Readiness before draft approval.'
        : 'Confirm whether configured data sources can execute the remaining policy rules.',
      tabHint: 'data-readiness',
    }
  }
  if (input.testsGenerated > 0) {
    return {
      title: 'Run policy simulation',
      detail: 'Confirm how this draft behaves on sample applications before approval.',
      tabHint: 'simulation',
    }
  }
  return {
    title: 'Send for approval',
    detail: 'Policy understanding looks ready for Credit Manager review.',
    tabHint: 'approvals',
  }
}
