/** Data & Parameters lender-facing display helpers (capability semantics + UX cleanup-2). */

export const OVERALL_READINESS_LABELS: Record<string, string> = {
  // Wave-9 primary vocabulary (preferred)
  APPROVED_FOR_LIVE_USE: 'Approved for live use',
  READY_TO_TEST: 'Ready to test',
  CALCULATION_NEEDS_SETUP: 'Calculation needs setup',
  CAN_CALCULATE_WHEN_DATA_AVAILABLE: 'Can calculate when data is available',
  NEEDS_MANUAL_INPUT: 'Needs your input',
  NOT_YET_SUPPORTED: 'Not yet supported',
  APPROVAL_REVOKED: 'Approval revoked',
  // Legacy overall codes — never imply live certification
  PRODUCTION_READY: 'Listed — not certification',
  RUNTIME_READY_NONPROD: 'Ready to test',
  POLICY_TEST_ONLY: 'Ready to test',
  CATALOGUE_ONLY: 'Listed — setup incomplete',
  READINESS_UNKNOWN: 'Needs review',
}

export const SOURCE_TYPE_LABELS: Record<string, string> = {
  PROVIDER: 'Provider',
  APPLICATION_INPUT: 'Application input',
  WORKFLOW: 'Workflow',
  INTERNAL_SYSTEM: 'Internal system',
  MANUAL: 'Manual',
  DERIVED: 'Derived',
  UNKNOWN: 'Unknown',
}

export const PLATFORM_INTEGRATION_LABELS: Record<string, string> = {
  PRODUCTION_READY: 'Connected',
  NOT_INTEGRATED: 'Not Integrated',
  NOT_APPLICABLE: 'Not applicable',
}

/** Lender-facing business labels (prefer over enum codes). */
export const PARAMETER_SUPPORT_BUSINESS_LABELS: Record<string, string> = {
  SUPPORTED_RAW: 'Provided directly',
  SUPPORTED_DERIVED: 'Calculated by BillionTech',
  PROVIDER_DOES_NOT_SUPPORT: 'Data not available from provider',
  CALCULATION_NOT_IMPLEMENTED: 'Needs your input',
  SOURCE_NOT_INTEGRATED: 'Data source not connected yet',
  NOT_APPLICABLE: 'Application or internal input',
}

export const PARAMETER_SUPPORT_LABELS: Record<string, string> = {
  SUPPORTED_RAW: 'Supported — Raw',
  SUPPORTED_DERIVED: 'Supported — Derived',
  PROVIDER_DOES_NOT_SUPPORT: 'Provider Does Not Support',
  CALCULATION_NOT_IMPLEMENTED: 'Calculation Not Implemented',
  SOURCE_NOT_INTEGRATED: 'Source Not Integrated',
  NOT_APPLICABLE: 'Not applicable (non-provider)',
}

export const LENDER_ORG_LABELS: Record<string, string> = {
  SUBSCRIBED: 'Subscribed',
  NOT_YET_SUBSCRIBED: 'Not Yet Subscribed',
  SUBSCRIPTION_SETUP_PENDING: 'Subscription Setup Pending',
  NOT_APPLICABLE: 'Not applicable',
}

export function overallReadinessLabel(code: unknown): string {
  const key = String(code ?? '')
  return OVERALL_READINESS_LABELS[key] ?? (key || '—')
}

export function sourceTypeLabel(code: unknown): string {
  const key = String(code ?? '')
  return SOURCE_TYPE_LABELS[key] ?? (key || '—')
}

export function platformIntegrationLabel(code: unknown): string {
  const key = String(code ?? '')
  return PLATFORM_INTEGRATION_LABELS[key] ?? (key || '—')
}

export function parameterSupportLabel(code: unknown): string {
  const key = String(code ?? '')
  return PARAMETER_SUPPORT_LABELS[key] ?? (key || '—')
}

export function parameterSupportBusinessLabel(code: unknown): string {
  const key = String(code ?? '')
  return PARAMETER_SUPPORT_BUSINESS_LABELS[key] ?? parameterSupportLabel(key)
}

export function lenderOrgLabel(code: unknown): string {
  const key = String(code ?? '')
  return LENDER_ORG_LABELS[key] ?? (key || '—')
}

export function providerStatusLabel(provider: Record<string, unknown> | null | undefined): string {
  const status = String(provider?.status ?? 'UNKNOWN')
  if (status === 'NOT_APPLICABLE') return 'Provider not applicable'
  if (status === 'REGISTERED') return `Provider registered${provider?.label ? `: ${provider.label}` : ''}`
  if (status === 'INFERRED') return `Provider inferred${provider?.label ? `: ${provider.label}` : ''}`
  return 'Provider unknown'
}

export function capabilityFromParameter(p: Record<string, unknown>): Record<string, unknown> {
  const cap = p.capability
  if (cap && typeof cap === 'object' && !Array.isArray(cap)) return cap as Record<string, unknown>
  return {}
}

export function nestStatus(obj: unknown, key = 'status'): string {
  if (obj && typeof obj === 'object' && !Array.isArray(obj)) {
    return String((obj as Record<string, unknown>)[key] ?? '')
  }
  return ''
}

export function isSupportedSupport(status: unknown): boolean {
  const s = String(status ?? '')
  return s === 'SUPPORTED_RAW' || s === 'SUPPORTED_DERIVED' || s === 'NOT_APPLICABLE'
}

export type Dp1ListFilters = {
  sourceFamily: string
  sourceType: string
  overallReadiness: string
  productionReady: '' | 'true' | 'false'
  parameterSupport: string
  q: string
}

export function matchesDp1Filters(p: Record<string, unknown>, f: Dp1ListFilters): boolean {
  if (f.sourceFamily && String(p.sourceFamily ?? p.source ?? p.evaluatedFrom ?? '') !== f.sourceFamily) {
    return false
  }
  if (f.sourceType && String(p.sourceType ?? '') !== f.sourceType) {
    return false
  }
  if (f.overallReadiness && String(p.overallReadiness ?? '') !== f.overallReadiness) {
    return false
  }
  if (f.productionReady === 'true' && p.productionReady !== true) return false
  if (f.productionReady === 'false' && p.productionReady !== false) return false
  if (f.parameterSupport) {
    const cap = capabilityFromParameter(p)
    const support = nestStatus(cap.parameterSupport ?? p.parameterSupport)
    if (support !== f.parameterSupport) return false
  }
  if (f.q.trim()) {
    const needle = f.q.trim().toLowerCase()
    const cap = capabilityFromParameter(p)
    const hay = [
      p.id,
      p.businessName,
      p.sourceFamily,
      p.source,
      p.evaluatedFrom,
      p.sourceType,
      p.overallReadiness,
      p.calculationSummary,
      nestStatus(cap.parameterSupport ?? p.parameterSupport),
      nestStatus(cap.platformIntegration ?? p.platformIntegration),
      nestStatus(cap.yourOrganisation ?? p.yourOrganisation),
    ]
      .map((x) => String(x ?? '').toLowerCase())
      .join(' ')
    if (!hay.includes(needle)) return false
  }
  return true
}

export function readinessBadgeClass(overall: unknown): string {
  switch (String(overall ?? '')) {
    case 'PRODUCTION_READY':
      return 'bg-emerald-100 text-emerald-900 border-emerald-200'
    case 'RUNTIME_READY_NONPROD':
      return 'bg-amber-100 text-amber-900 border-amber-200'
    case 'POLICY_TEST_ONLY':
      return 'bg-sky-100 text-sky-900 border-sky-200'
    case 'CATALOGUE_ONLY':
      return 'bg-slate-100 text-slate-700 border-slate-200'
    case 'READINESS_UNKNOWN':
      return 'bg-rose-100 text-rose-900 border-rose-200'
    default:
      return 'bg-slate-50 text-slate-600 border-slate-200'
  }
}

export function supportBadgeClass(status: unknown): string {
  switch (String(status ?? '')) {
    case 'SUPPORTED_RAW':
    case 'SUPPORTED_DERIVED':
    case 'NOT_APPLICABLE':
      return 'bg-emerald-100 text-emerald-900 border-emerald-200'
    case 'CALCULATION_NOT_IMPLEMENTED':
    case 'PROVIDER_DOES_NOT_SUPPORT':
      return 'bg-amber-100 text-amber-900 border-amber-200'
    case 'SOURCE_NOT_INTEGRATED':
      return 'bg-rose-100 text-rose-900 border-rose-200'
    default:
      return 'bg-slate-50 text-slate-600 border-slate-200'
  }
}

export function platformBadgeClass(status: unknown): string {
  switch (String(status ?? '')) {
    case 'PRODUCTION_READY':
      return 'bg-emerald-100 text-emerald-900 border-emerald-200'
    case 'NOT_INTEGRATED':
      return 'bg-rose-100 text-rose-900 border-rose-200'
    case 'NOT_APPLICABLE':
      return 'bg-slate-100 text-slate-700 border-slate-200'
    default:
      return 'bg-slate-50 text-slate-600 border-slate-200'
  }
}
