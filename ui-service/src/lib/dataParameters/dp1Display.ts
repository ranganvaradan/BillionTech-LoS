/** DP-1 Data & Parameters display helpers (list badges / filters / readiness labels). */

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

export function overallReadinessLabel(code: unknown): string {
  const key = String(code ?? '')
  return OVERALL_READINESS_LABELS[key] ?? (key || '—')
}

export function sourceTypeLabel(code: unknown): string {
  const key = String(code ?? '')
  return SOURCE_TYPE_LABELS[key] ?? (key || '—')
}

export function providerStatusLabel(provider: Record<string, unknown> | null | undefined): string {
  const status = String(provider?.status ?? 'UNKNOWN')
  if (status === 'NOT_APPLICABLE') return 'Provider not applicable'
  if (status === 'REGISTERED') return `Provider registered${provider?.label ? `: ${provider.label}` : ''}`
  if (status === 'INFERRED') return `Provider inferred${provider?.label ? `: ${provider.label}` : ''}`
  return 'Provider unknown'
}

export type Dp1ListFilters = {
  sourceFamily: string
  sourceType: string
  overallReadiness: string
  productionReady: '' | 'true' | 'false'
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
  if (f.q.trim()) {
    const needle = f.q.trim().toLowerCase()
    const hay = [
      p.id,
      p.businessName,
      p.sourceFamily,
      p.source,
      p.evaluatedFrom,
      p.sourceType,
      p.overallReadiness,
      p.calculationSummary,
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
