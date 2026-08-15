/** Data & Parameters capability semantics display helpers (lender SETUP catalogue). */

export const OVERALL_READINESS_LABELS: Record<string, string> = {
  PRODUCTION_READY: 'Production Ready',
  RUNTIME_READY_NONPROD: 'Runtime Ready — Non-Production',
  POLICY_TEST_ONLY: 'Policy Test Only',
  CATALOGUE_ONLY: 'Catalogue Only',
  READINESS_UNKNOWN: 'Readiness Unknown',
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
  PRODUCTION_READY: 'Production Ready',
  NOT_INTEGRATED: 'Not Integrated',
  NOT_APPLICABLE: 'Not applicable',
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
      return 'bg-emerald-100 text-emerald-900 border-emerald-200'
    case 'CALCULATION_NOT_IMPLEMENTED':
    case 'PROVIDER_DOES_NOT_SUPPORT':
      return 'bg-amber-100 text-amber-900 border-amber-200'
    case 'SOURCE_NOT_INTEGRATED':
      return 'bg-rose-100 text-rose-900 border-rose-200'
    case 'NOT_APPLICABLE':
      return 'bg-slate-100 text-slate-700 border-slate-200'
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
