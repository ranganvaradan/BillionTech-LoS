/**
 * POLICY-DATA-UX-1 — parameter-oriented grouping for Policy Studio Data & calculations.
 * Display/read-model only. Does not mutate enterprise CanonicalParameterRegistry definitions.
 */

export type CmParamStatus =
  | 'READY'
  | 'NEEDS_YOUR_INPUT'
  | 'MANUAL_INPUT'
  | 'NOT_CURRENTLY_AVAILABLE'
  | 'NEEDS_CONFIGURATION'

export type DataCalcItemKind =
  | 'RAW_DATA_REQUIRED'
  | 'DERIVED_PARAMETER'
  | 'CALCULATION_ADJUSTMENT'
  | 'MANUAL_PARAMETER'
  | 'REPORT_ANALYST_INFORMATION'
  | 'UNRESOLVED'

export type PolicyAdjustment = {
  id: string
  title: string
  sourceClause: string
  status: CmParamStatus
  reason?: string
  missingDefinition?: Record<string, unknown>
  raw: Record<string, unknown>
}

export type ParameterGroup = {
  parameterId: string
  name: string
  source: string
  type: string
  period: string
  itemKind: DataCalcItemKind
  cmStatus: CmParamStatus
  statusReason?: string
  missingDefinition?: Record<string, unknown>
  howCalculated: Record<string, unknown> | null
  policyAdjustments: PolicyAdjustment[]
  sourceClauses: string[]
  usedByRules: { id: string; name: string }[]
  usedFor?: string
  unresolved: boolean
  members: Record<string, unknown>[]
}

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

function clauseText(r: Record<string, unknown>): string {
  return String(r.sourceClause ?? r.businessRule ?? r.ruleName ?? '').trim()
}

/** Never render [object Object] for howCalculated. */
export function formatHowCalculated(raw: unknown): Record<string, unknown> | null {
  if (raw == null) return null
  if (typeof raw === 'string') {
    const s = raw.trim()
    if (!s || s === '[object Object]') return null
    return { calculation: s }
  }
  if (typeof raw === 'object' && !Array.isArray(raw)) {
    const o = raw as Record<string, unknown>
    const out: Record<string, unknown> = {}
    for (const [k, v] of Object.entries(o)) {
      if (v == null) continue
      if (typeof v === 'object') out[k] = JSON.stringify(v)
      else out[k] = v
    }
    return Object.keys(out).length ? out : null
  }
  return { calculation: String(raw) }
}

export function resolveParameterKey(r: Record<string, unknown>): {
  parameterId: string
  confidence: 'exact' | 'heuristic' | 'unresolved'
} {
  const meta = asRecord(r.metadata)
  const canon = asRecord(r.canonicalParameter)
  const exact =
    String(r.canonicalParameterId ?? r.parameterId ?? r.affectedParameterId ?? meta.affectedMetric ?? canon.id ?? '').trim()
  if (exact && exact !== 'null') {
    return { parameterId: exact, confidence: 'exact' }
  }
  const text = clauseText(r).toLowerCase()
  if (text.includes('average daily balance') || text.includes('removed from average daily')) {
    return { parameterId: 'banking.avg_daily_balance_3m', confidence: 'heuristic' }
  }
  if (text.includes('average monthly settlement') || text.includes('settlement count')) {
    return { parameterId: 'banking.settlement.count_monthly_avg_3m', confidence: 'heuristic' }
  }
  if (text.includes('average daily qr') || text.includes('daily qr') || text.includes('daily settlement')) {
    return { parameterId: 'banking.settlement.avg_daily_3m', confidence: 'heuristic' }
  }
  if (text.includes('average monthly transaction')) {
    return { parameterId: 'banking.transaction_count.average_monthly_3m', confidence: 'heuristic' }
  }
  if (text.includes('inward cheque') || text.includes('ecs') || text.includes('enach')) {
    return { parameterId: 'banking.inward_return.ratio_3m', confidence: 'heuristic' }
  }
  if (text.includes('emi bounce')) {
    return { parameterId: 'banking.emi_bounce_count_3m', confidence: 'heuristic' }
  }
  if (text.includes('large credit')) {
    return { parameterId: 'banking.large_credit_transactions', confidence: 'heuristic' }
  }
  if (text.includes('intercompany') || text.includes('merchant group')) {
    return { parameterId: 'banking.intercompany_transactions', confidence: 'heuristic' }
  }
  if (text.includes('online gaming') && !text.includes('removed from')) {
    return { parameterId: 'bank.transaction.classification', confidence: 'heuristic' }
  }
  if (text.includes('loans disbursed') && !text.includes('removed from')) {
    return { parameterId: 'bank.transaction.classification', confidence: 'heuristic' }
  }
  return { parameterId: `__unresolved__:${String(r.id ?? r.systemRuleId ?? text.slice(0, 40))}`, confidence: 'unresolved' }
}

function businessNameFor(parameterId: string, members: Record<string, unknown>[]): string {
  for (const m of members) {
    const canon = asRecord(m.canonicalParameter)
    if (canon.businessName) return String(canon.businessName)
  }
  const map: Record<string, string> = {
    'banking.avg_daily_balance_3m': 'Average Daily Balance',
    'banking.transaction_count.average_monthly_3m': 'Average monthly transactions',
    'banking.settlement.avg_daily_3m': 'Average Daily QR Settlement',
    'banking.settlement.count_monthly_avg_3m': 'Average Monthly Settlement count',
    'banking.inward_return.ratio_3m': 'Inward Cheque / ECS / ENACH returns',
    'banking.emi_bounce_count_3m': 'EMI bounce count',
    'banking.large_credit_transactions': 'Large Credit Transactions',
    'banking.intercompany_transactions': 'Intercompany / Merchant-group transactions',
    'bank.transaction.classification': 'Transaction classification (report fields)',
  }
  if (map[parameterId]) return map[parameterId]
  if (parameterId.startsWith('__unresolved__')) {
    const t = clauseText(members[0] ?? {})
    return t ? (t.length > 80 ? `${t.slice(0, 77)}…` : t) : 'Unresolved data item'
  }
  return parameterId
}

function adjustmentStatus(r: Record<string, unknown>): { status: CmParamStatus; reason?: string } {
  const text = clauseText(r).toLowerCase()
  const missing = asRecord(r.missingDefinition)
  if (text.includes('loan') && text.includes('removed')) {
    return { status: 'READY', reason: 'Loan-disbursement classifier exists in bank transaction taxonomy' }
  }
  if (text.includes('gaming') && text.includes('removed')) {
    return { status: 'READY', reason: 'Online-gaming classifier exists in bank transaction taxonomy' }
  }
  if (text.includes('bulk') || text.includes('10 times') || text.includes('10×')) {
    return {
      status: 'NEEDS_CONFIGURATION',
      reason: String(missing.hint ?? 'Bulk >10× exclusion needs confirmed average-deposit baseline wiring'),
    }
  }
  if (missing.action === 'CONFIGURE') return { status: 'NEEDS_CONFIGURATION', reason: String(missing.hint ?? missing.question ?? '') }
  if (missing.action === 'DEFINE' || missing.action === 'CONFIRM') {
    return { status: 'NEEDS_YOUR_INPUT', reason: String(missing.question ?? missing.hint ?? '') }
  }
  return { status: 'NEEDS_CONFIGURATION' }
}

function groupCmStatus(parameterId: string, members: Record<string, unknown>[], adjustments: PolicyAdjustment[]): {
  status: CmParamStatus
  reason?: string
  missingDefinition?: Record<string, unknown>
} {
  const texts = members.map(clauseText).join(' ').toLowerCase()
  const missing = members.map((m) => asRecord(m.missingDefinition)).find((m) => Object.keys(m).length > 0)

  if (parameterId === 'banking.large_credit_transactions' || texts.includes('large credit')) {
    return {
      status: 'NEEDS_YOUR_INPUT',
      reason: 'What qualifies as "Large"?',
      missingDefinition: missing && Object.keys(missing).length ? missing : {
        question: 'What qualifies as a "Large" credit?',
        hint: 'Define an amount threshold (₹).',
        action: 'DEFINE',
      },
    }
  }
  if (parameterId === 'banking.intercompany_transactions' || texts.includes('intercompany')) {
    return {
      status: 'NEEDS_YOUR_INPUT',
      reason: 'Merchant-group / intercompany relationship definition is not confirmed',
      missingDefinition: missing && Object.keys(missing).length ? missing : {
        question: 'How should intercompany / merchant-group relationships be identified?',
        action: 'DEFINE',
      },
    }
  }
  if (parameterId === 'banking.emi_bounce_count_3m' || texts.includes('emi bounce')) {
    return {
      status: 'NEEDS_CONFIGURATION',
      reason: 'EMI and bounce classifiers exist separately; combined EMI-bounce metric is not production-bound',
      missingDefinition: missing,
    }
  }
  if (adjustments.some((a) => a.status === 'NEEDS_YOUR_INPUT')) {
    return { status: 'NEEDS_YOUR_INPUT', reason: 'One or more policy adjustments need your input' }
  }
  if (adjustments.some((a) => a.status === 'NEEDS_CONFIGURATION')) {
    // ADB still READY for base calc; surface config on adjustments — primary stays READY if base known
    if (parameterId === 'banking.avg_daily_balance_3m') {
      return { status: 'READY', reason: 'Base ADB implemented; review policy adjustments' }
    }
    return { status: 'NEEDS_CONFIGURATION', reason: 'Capability or wiring incomplete' }
  }
  if (parameterId.startsWith('__unresolved__')) {
    return { status: 'NEEDS_YOUR_INPUT', reason: 'Could not map clause to a canonical parameter' }
  }
  // Known derived banking metrics with GACAT lineage
  if (
    parameterId.startsWith('banking.') ||
    parameterId === 'bank.transaction.classification' ||
    parameterId.startsWith('bureau.') ||
    parameterId.startsWith('gst.')
  ) {
    return { status: 'READY' }
  }
  return { status: 'NEEDS_CONFIGURATION' }
}

function itemKindFor(parameterId: string, members: Record<string, unknown>[]): DataCalcItemKind {
  const kinds = members.map((m) => String(m.itemKind ?? ''))
  if (kinds.includes('CALCULATION_ADJUSTMENT') && members.every((m) => m.metricAdjustment)) {
    return 'CALCULATION_ADJUSTMENT'
  }
  if (kinds.includes('REPORT_ANALYST_INFORMATION') || parameterId === 'banking.large_credit_transactions') {
    return 'REPORT_ANALYST_INFORMATION'
  }
  if (parameterId.startsWith('__unresolved__')) return 'UNRESOLVED'
  if (kinds.includes('DERIVED_PARAMETER') || parameterId.includes('avg_') || parameterId.includes('average') || parameterId.includes('ratio')) {
    return 'DERIVED_PARAMETER'
  }
  if (kinds.includes('RAW_DATA_REQUIRED')) return 'RAW_DATA_REQUIRED'
  // Defaults for known ids
  if (parameterId.startsWith('banking.') && !parameterId.includes('transaction.classification')) {
    return 'DERIVED_PARAMETER'
  }
  return 'RAW_DATA_REQUIRED'
}

function usedByRules(parameterId: string, underwritingRules: unknown[]): { id: string; name: string }[] {
  const out: { id: string; name: string }[] = []
  const needle = parameterId.toLowerCase()
  const short = needle.replace(/^banking\./, '').replace(/^bureau\./, '')
  for (const raw of underwritingRules) {
    const r = asRecord(raw)
    const blob = JSON.stringify(r).toLowerCase()
    if (blob.includes(needle) || (short.length > 8 && blob.includes(short))) {
      out.push({
        id: String(r.id ?? r.systemRuleId ?? ''),
        name: String(r.ruleName ?? r.systemRuleId ?? 'Rule'),
      })
    }
  }
  return out
}

export function groupDataCalculations(
  dataAndCalculations: unknown[],
  underwritingRules: unknown[] = [],
): ParameterGroup[] {
  const buckets = new Map<string, Record<string, unknown>[]>()
  for (const raw of dataAndCalculations) {
    const r = asRecord(raw)
    const { parameterId, confidence } = resolveParameterKey(r)
    const key = confidence === 'unresolved' && Boolean(r.metricAdjustment)
      ? String(asRecord(r.metadata).affectedMetric || 'banking.avg_daily_balance_3m')
      : parameterId
    // Metric adjustments always nest under affected parameter when known
    const nestKey =
      Boolean(r.metricAdjustment) || String(r.itemKind) === 'CALCULATION_ADJUSTMENT'
        ? String(r.affectedParameterId ?? asRecord(r.metadata).affectedMetric ?? key)
        : key
    const list = buckets.get(nestKey) ?? []
    list.push({ ...r, _resolveConfidence: confidence })
    buckets.set(nestKey, list)
  }

  const groups: ParameterGroup[] = []
  for (const [parameterId, members] of buckets) {
    const adjustments: PolicyAdjustment[] = []
    const primaryMembers: Record<string, unknown>[] = []
    for (const m of members) {
      if (Boolean(m.metricAdjustment) || String(m.itemKind) === 'CALCULATION_ADJUSTMENT') {
        const st = adjustmentStatus(m)
        adjustments.push({
          id: String(m.id ?? m.systemRuleId ?? Math.random()),
          title: String(m.ruleName ?? clauseText(m)),
          sourceClause: clauseText(m),
          status: st.status,
          reason: st.reason,
          missingDefinition: asRecord(m.missingDefinition),
          raw: m,
        })
      } else {
        primaryMembers.push(m)
      }
    }
    const statusInfo = groupCmStatus(parameterId, members, adjustments)
    const howFrom =
      primaryMembers.map((m) => formatHowCalculated(m.howCalculated)).find(Boolean) ||
      members.map((m) => formatHowCalculated(m.howCalculated)).find(Boolean) ||
      null
    const canon = asRecord((primaryMembers[0] ?? members[0])?.canonicalParameter)
    const source = String(
      canon.evaluatedFrom ??
        (primaryMembers[0] ?? members[0])?.evaluatedFrom ??
        (primaryMembers[0] ?? members[0])?.dataSource ??
        'Bank Statement',
    )
    const type = String(canon.type ?? (primaryMembers[0] ?? members[0])?.parameterType ?? 'DERIVED')
    const period = String(
      (primaryMembers[0] ?? members[0])?.period ??
        (canon.period === 'TRAILING_3M' ? 'Last 3 months' : canon.period) ??
        '',
    )
    const clauses = members.map(clauseText).filter(Boolean)
    const kind = itemKindFor(parameterId, members)
    const rules = usedByRules(parameterId, underwritingRules)
    groups.push({
      parameterId,
      name: businessNameFor(parameterId, members),
      source,
      type: type === 'RAW' || type === 'DERIVED' || type === 'MANUAL' ? type : type.includes('DERIVED') ? 'DERIVED' : type,
      period: period === 'TRAILING_3M' ? 'Last 3 months' : period,
      itemKind: kind,
      cmStatus: statusInfo.status,
      statusReason: statusInfo.reason,
      missingDefinition: statusInfo.missingDefinition,
      howCalculated: howFrom,
      policyAdjustments: adjustments,
      sourceClauses: clauses,
      usedByRules: rules,
      usedFor: kind === 'REPORT_ANALYST_INFORMATION' ? 'Analyst review / Policy information' : undefined,
      unresolved: parameterId.startsWith('__unresolved__') || statusInfo.status === 'NEEDS_YOUR_INPUT',
      members,
    })
  }

  // Stable sort: READY first, then needs input; ADB near top for banking
  const rank = (g: ParameterGroup) => {
    if (g.parameterId === 'banking.avg_daily_balance_3m') return 0
    if (g.cmStatus === 'READY') return 1
    if (g.cmStatus === 'NEEDS_CONFIGURATION') return 2
    if (g.cmStatus === 'NEEDS_YOUR_INPUT') return 3
    return 4
  }
  groups.sort((a, b) => rank(a) - rank(b) || a.name.localeCompare(b.name))
  return groups
}

export function filterParameterGroups(
  groups: ParameterGroup[],
  filter: 'ALL' | 'READY' | 'NEEDS_INPUT' | 'MANUAL' | 'UNAVAILABLE',
  sourceFilter?: string,
): ParameterGroup[] {
  return groups.filter((g) => {
    if (sourceFilter && sourceFilter !== 'ALL' && !g.source.toLowerCase().includes(sourceFilter.toLowerCase())) {
      return false
    }
    switch (filter) {
      case 'READY':
        return g.cmStatus === 'READY'
      case 'NEEDS_INPUT':
        return g.cmStatus === 'NEEDS_YOUR_INPUT' || g.cmStatus === 'NEEDS_CONFIGURATION'
      case 'MANUAL':
        return g.cmStatus === 'MANUAL_INPUT' || g.type === 'MANUAL'
      case 'UNAVAILABLE':
        return g.cmStatus === 'NOT_CURRENTLY_AVAILABLE'
      default:
        return true
    }
  })
}

export function cmStatusLabel(s: CmParamStatus): string {
  switch (s) {
    case 'READY':
      return 'Ready'
    case 'NEEDS_YOUR_INPUT':
      return 'Needs your input'
    case 'MANUAL_INPUT':
      return 'Manual input'
    case 'NOT_CURRENTLY_AVAILABLE':
      return 'Not currently available'
    case 'NEEDS_CONFIGURATION':
      return 'Needs configuration'
    default:
      return s
  }
}

export function itemKindLabel(k: DataCalcItemKind): string {
  switch (k) {
    case 'RAW_DATA_REQUIRED':
      return 'Raw data required'
    case 'DERIVED_PARAMETER':
      return 'Derived parameter'
    case 'CALCULATION_ADJUSTMENT':
      return 'Calculation adjustment'
    case 'MANUAL_PARAMETER':
      return 'Manual parameter'
    case 'REPORT_ANALYST_INFORMATION':
      return 'Report / analyst information'
    case 'UNRESOLVED':
      return 'Unresolved'
    default:
      return k
  }
}

/** Invariant helpers for tests */
export function adjustmentPeerCount(groups: ParameterGroup[]): number {
  // adjustments that incorrectly appear as their own top-level group with only adjustments and wrong id
  return groups.filter(
    (g) =>
      g.members.length > 0 &&
      g.members.every((m) => m.metricAdjustment) &&
      g.parameterId !== 'banking.avg_daily_balance_3m' &&
      String(asRecord(g.members[0]).metadata).includes('avg_daily_balance'),
  ).length
}

export function duplicateClauseCount(groups: ParameterGroup[]): number {
  const seen = new Set<string>()
  let dups = 0
  for (const g of groups) {
    for (const m of g.members) {
      const id = String(m.id ?? m.systemRuleId ?? '')
      if (!id) continue
      if (seen.has(id)) dups++
      else seen.add(id)
    }
  }
  return dups
}
