/**
 * POLICY-DATA-CALC-CONVERGENCE-1 — ONE policy item → ONE primary display group.
 *
 * Backend groupCards is authoritative when session arrays are present (including empty []).
 * Do NOT treat empty otherPolicyContent as "missing" and rebuild from classificationOnly —
 * that re-includes Data & calculations stubs and shows "Replace with underwriting rule".
 */

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

function sessionHasArray(session: Record<string, unknown> | null | undefined, key: string): boolean {
  return session != null && Array.isArray(session[key])
}

export function resolveUnderwritingRules(
  session: Record<string, unknown> | null | undefined,
  allRuleCards: unknown[],
): unknown[] {
  if (sessionHasArray(session, 'underwritingRules')) {
    return asList(session!.underwritingRules)
  }
  return allRuleCards.filter((c) => {
    const r = asRecord(c)
    const s = String(r.status ?? '')
    const group = String(r.businessGroup ?? '')
    return (
      s !== 'Data requirement' &&
      s !== 'Metric adjustment' &&
      s !== 'Non-underwriting' &&
      group !== 'Other policy content' &&
      group !== 'Narrative / Excluded' &&
      !r.compoundChild &&
      !r.classificationOnly &&
      !r.dataRequirementOnly &&
      !r.metricAdjustment
    )
  })
}

export function resolveDataAndCalculations(
  session: Record<string, unknown> | null | undefined,
  allRuleCards: unknown[],
): unknown[] {
  if (sessionHasArray(session, 'dataAndCalculations')) {
    return asList(session!.dataAndCalculations)
  }
  return allRuleCards.filter((c) => {
    const r = asRecord(c)
    const s = String(r.status ?? '')
    return (
      s === 'Data requirement' ||
      s === 'Metric adjustment' ||
      s === 'Non-underwriting' ||
      Boolean(r.dataRequirementOnly) ||
      Boolean(r.metricAdjustment)
    )
  })
}

export function resolveOtherPolicyContent(
  session: Record<string, unknown> | null | undefined,
  allRuleCards: unknown[],
): unknown[] {
  if (sessionHasArray(session, 'otherPolicyContent')) {
    return asList(session!.otherPolicyContent)
  }
  // Legacy fallback only when backend did not send the key at all.
  // Exclude anything that belongs in Data & calculations.
  return allRuleCards.filter((c) => {
    const r = asRecord(c)
    const s = String(r.status ?? '')
    const group = String(r.businessGroup ?? '')
    if (s === 'Data requirement' || s === 'Metric adjustment' || s === 'Non-underwriting') return false
    if (r.dataRequirementOnly || r.metricAdjustment) return false
    return group === 'Other policy content' || group === 'Narrative / Excluded' || Boolean(r.classificationOnly)
  })
}

/** Invariant helper for tests: no id appears in more than one group. */
export function duplicateIdsAcrossGroups(
  underwriting: unknown[],
  dataCalc: unknown[],
  other: unknown[],
): string[] {
  const seen = new Map<string, string>()
  const dups: string[] = []
  const visit = (list: unknown[], bucket: string) => {
    for (const raw of list) {
      const r = asRecord(raw)
      const id = String(r.id ?? r.systemRuleId ?? '')
      if (!id) continue
      const prev = seen.get(id)
      if (prev && prev !== bucket) dups.push(id)
      else seen.set(id, bucket)
    }
  }
  visit(underwriting, 'uw')
  visit(dataCalc, 'data')
  visit(other, 'other')
  return dups
}
