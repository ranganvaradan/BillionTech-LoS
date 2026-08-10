/**
 * Day 6 — Policy Comparison business model.
 * Derives Credit Head–facing summaries from existing staging payloads only.
 * Does not invent impact beyond what simulation / dual-run / draft-diff provide.
 */

import type { ApprovalsContext, SimulationResult, StagingPolicyStudio } from '@/api/creditIntelligence'
import {
  asList,
  asRecord,
  businessOutcomeLabel,
} from '@/lib/creditIntelligence/businessLexicon'

export type PolicySideMeta = {
  role: 'current' | 'draft' | 'uploaded' | 'previous'
  name: string
  version: string
  createdBy: string
  createdDate: string
  status: string
  simulationStatus: string
  approvalStatus: string
}

export type PolicyRisk = 'More Conservative' | 'More Liberal' | 'No Material Change'
export type Confidence = 'High' | 'Medium' | 'Low'
export type OverallRec = 'Ready for Review' | 'Needs Clarification' | 'Needs Simulation' | 'Blocked'

export type BusinessChangeSection =
  | 'Eligibility'
  | 'Banking'
  | 'Bureau'
  | 'GST'
  | 'ITR'
  | 'Pricing'
  | 'Loan Limits'
  | 'Collateral'
  | 'Authority'
  | 'Exceptions'
  | 'Definitions'
  | 'Data Requirements'
  | 'Other'

export type BusinessChangeCard = {
  id: string
  section: BusinessChangeSection
  title: string
  currentValue: string
  proposedValue: string
  businessImpact: string
  affectedProducts: string[]
  estimatedImpact: string | null
  differenceClass?: string
  explanation?: string
}

export type ChangedApplication = {
  code: string
  name: string
  oldDecision: string
  newDecision: string
  businessReason: string
  product?: string
}

export type ProductImpact = {
  product: string
  rulesAdded: number
  rulesRemoved: number
  thresholdChanges: number
  businessImpact: string
}

export type DefinitionChange = {
  term: string
  previousMeaning: string
  newMeaning: string
  rulesDepending: string
  simulationImpact: string
}

export type AskAnswer = { question: string; answer: string }

export function differenceClassLabel(raw: unknown): string {
  const u = String(raw ?? '')
    .toUpperCase()
    .replace(/[\s-]+/g, '_')
  const map: Record<string, string> = {
    MATCH: 'No change',
    SAME: 'No change',
    CANONICAL_STRICTER: 'Proposed policy is stricter',
    CANONICAL_MORE_PERMISSIVE: 'Proposed policy is more permissive',
    CANONICAL_DATA_INSUFFICIENT: 'Proposed policy needs more information',
    LEGACY_DEFAULT_DEPENDENT: 'Current LOS relies on a silent default',
    LEGACY_MANUAL_DEPENDENT: 'Current LOS relies on manual judgement',
    CANONICAL_EVIDENCE_CONFLICT: 'Evidence conflict under proposed policy',
    POLICY_BINDING_MISSING: 'Rule not bound in proposed policy',
    OTHER: 'Other difference',
    MORE_STRICT: 'More strict',
    MORE_PERMISSIVE: 'More permissive',
    REFER_DUE_TO_DATA_GAP: 'Manual review due to data gap',
  }
  return map[u] || businessOutcomeLabel(raw)
}

export function friendlyRuleName(ruleIdOrName: unknown): string {
  const s = String(ruleIdOrName ?? '').trim()
  if (!s) return 'Business rule'
  if (!/^[A-Z0-9_]+$/.test(s) || !s.includes('_')) return s
  return s
    .split('_')
    .map((w) => w.charAt(0) + w.slice(1).toLowerCase())
    .join(' ')
}

function sectionForRule(title: string, explanation = ''): BusinessChangeSection {
  const t = `${title} ${explanation}`.toLowerCase()
  if (/\bbureau|cibil|score|dpd|write.?off|dbt\b/.test(t)) return 'Bureau'
  if (/\bbank|emi|bounce|inward|salary|turnover|abb\b/.test(t)) return 'Banking'
  if (/\bgst\b/.test(t)) return 'GST'
  if (/\bitr|income.?tax|tax\b/.test(t)) return 'ITR'
  if (/\bpric|roi|interest|fee\b/.test(t)) return 'Pricing'
  if (/\blimit|amount|tenor|ltv\b/.test(t)) return 'Loan Limits'
  if (/\bcollateral|security|hypothec\b/.test(t)) return 'Collateral'
  if (/\bauthorit|delegation|maker|checker\b/.test(t)) return 'Authority'
  if (/\bexception|waiver|deviation\b/.test(t)) return 'Exceptions'
  if (/\bdefin|meaning|metric|measure\b/.test(t)) return 'Definitions'
  if (/\bdata|document|information|missing|insufficient\b/.test(t)) return 'Data Requirements'
  if (/\beligib|age|kyc|product\b/.test(t)) return 'Eligibility'
  return 'Other'
}

function impactFromClass(diffClass: string): string {
  const u = diffClass.toUpperCase()
  if (u.includes('STRICTER') || u.includes('MORE_STRICT')) {
    return 'Fewer borrowers are likely to qualify under the proposed policy.'
  }
  if (u.includes('PERMISSIVE')) {
    return 'More borrowers may qualify under the proposed policy.'
  }
  if (u.includes('DATA_INSUFFICIENT') || u.includes('DATA_GAP')) {
    return 'More applications may need additional information before a decision.'
  }
  if (u.includes('DEFAULT_DEPENDENT')) {
    return 'Current LOS may be approving or declining based on a silent default — proposed policy makes the requirement explicit.'
  }
  if (u.includes('MANUAL')) {
    return 'Current LOS relies on manual judgement where the proposed policy states an explicit rule.'
  }
  if (u.includes('MATCH') || u.includes('SAME')) {
    return 'No material change for this rule.'
  }
  return 'Credit Head review required for this difference.'
}

export function buildVersionMeta(args: {
  session: StagingPolicyStudio | null
  approvals: ApprovalsContext | null
  simulation: SimulationResult | null
  draftDiff: Record<string, unknown> | null
}): { current: PolicySideMeta; draft: PolicySideMeta; previous: PolicySideMeta | null } {
  const header = asRecord(args.session?.policyHeader)
  const draftSummary = asRecord(args.approvals?.draftSummary)
  const simAgg = asRecord(args.simulation?.aggregates)
  const tested = Number(simAgg.applicationsTested ?? asList(args.simulation?.applications).length)

  const cm = Boolean(args.approvals?.creditManagerApproved)
  const ck = Boolean(args.approvals?.checkerApproved)
  const approvalStatus = ck
    ? 'Checker approved'
    : cm
      ? 'Credit Manager approved'
      : 'Pending approval'

  const draft: PolicySideMeta = {
    role: 'draft',
    name: String(draftSummary.policyName ?? header.policyName ?? args.session?.fileName ?? 'Draft policy'),
    version: String(draftSummary.versionLabel ?? header.version ?? 'Draft v1'),
    createdBy: String(draftSummary.createdBy ?? 'Policy Studio'),
    createdDate: header.uploadDate
      ? new Date(String(header.uploadDate)).toLocaleString()
      : String(args.simulation?.runDate ? new Date(String(args.simulation.runDate)).toLocaleString() : '—'),
    status: String(draftSummary.status ?? header.status ?? 'Draft'),
    simulationStatus: tested > 0 ? `Simulated (${tested} apps)` : 'Not simulated',
    approvalStatus,
  }

  const current: PolicySideMeta = {
    role: 'current',
    name: 'Current LOS policy',
    version: 'Production (authoritative)',
    createdBy: 'Live underwriting',
    createdDate: '—',
    status: 'Active in production',
    simulationStatus: tested > 0 ? 'Compared in last simulation' : 'Awaiting simulation',
    approvalStatus: 'Authoritative',
  }

  let previous: PolicySideMeta | null = null
  if (args.draftDiff?.available && args.draftDiff.fromVersion != null) {
    previous = {
      role: 'previous',
      name: draft.name,
      version: String(args.draftDiff.fromVersion),
      createdBy: 'Earlier draft build',
      createdDate: '—',
      status: 'Superseded draft',
      simulationStatus: '—',
      approvalStatus: '—',
    }
  }

  return { current, draft, previous }
}

export function buildChangeCards(args: {
  dualComparisons: Record<string, unknown>[]
  draftDiff: Record<string, unknown> | null
  simulation: SimulationResult | null
  ruleCards: unknown[]
}): BusinessChangeCard[] {
  const cards: BusinessChangeCard[] = []
  const ruleImpact = asList(args.simulation?.ruleImpact).map(asRecord)
  const apps = asList(args.simulation?.applications).map(asRecord)

  const productHits = (ruleName: string): string[] => {
    const products = new Set<string>()
    for (const a of apps) {
      const outcomes = asList(asRecord(a.drillDown).ruleOutcomes).map(asRecord)
      const hit = outcomes.some((o) => String(o.ruleName ?? '').includes(ruleName) || ruleName.includes(String(o.ruleName ?? '')))
      if (hit && a.product) products.add(String(a.product))
      // also check top reason
      if (String(a.topReason ?? '').toLowerCase().includes(ruleName.toLowerCase().slice(0, 12)) && a.product) {
        products.add(String(a.product))
      }
    }
    // from rule cards product scope
    for (const raw of args.ruleCards) {
      const r = asRecord(raw)
      if (String(r.ruleName ?? '').toLowerCase() === ruleName.toLowerCase() || String(r.systemRuleId ?? '') === ruleName) {
        const scope = String(r.productScope ?? '')
        if (scope && scope !== 'ALL' && scope !== '—') {
          scope.split(/[,;/]/).map((s) => s.trim()).filter(Boolean).forEach((p) => products.add(p))
        }
      }
    }
    return [...products]
  }

  const estimatedFor = (ruleName: string): string | null => {
    const hit = ruleImpact.find((r) => String(r.ruleName ?? '') === ruleName)
    if (hit && Number(hit.count ?? 0) > 0) {
      return `${hit.count} application${Number(hit.count) === 1 ? '' : 's'} affected in the current simulation.`
    }
    // count apps where outcome changed and reason mentions rule
    let n = 0
    for (const a of apps) {
      const oldD = String(a.currentLosOutcome ?? asRecord(asRecord(a.drillDown).legacyComparison).currentLos ?? '')
      const newD = String(a.draftPolicyOutcome ?? a.policyResult ?? '')
      if (oldD && newD && businessOutcomeLabel(oldD) !== businessOutcomeLabel(newD)) {
        if (String(a.topReason ?? a.comparisonReason ?? '').toLowerCase().includes(ruleName.toLowerCase().slice(0, 10))) {
          n++
        }
      }
    }
    return n > 0 ? `${n} application${n === 1 ? '' : 's'} changed decision in the current simulation.` : null
  }

  for (const row of args.dualComparisons) {
    const cls = String(row.differenceClass ?? '')
    if (!cls || /^(MATCH|SAME|NONE)$/i.test(cls)) continue
    const title = friendlyRuleName(row.ruleName ?? row.businessRule ?? row.ruleId)
    const explanation = String(row.explanation ?? '')
    cards.push({
      id: `dual-${String(row.ruleId ?? title)}`,
      section: sectionForRule(title, explanation),
      title,
      currentValue: businessOutcomeLabel(row.legacyOutcome),
      proposedValue: businessOutcomeLabel(row.canonicalOutcome),
      businessImpact: impactFromClass(cls),
      affectedProducts: productHits(title),
      estimatedImpact: estimatedFor(title),
      differenceClass: cls,
      explanation: explanation || undefined,
    })
  }

  // Draft structural diff
  const biz = asList(args.draftDiff?.businessDiff).map(asRecord)
  const tech = asRecord(args.draftDiff?.technicalDiff)
  for (const b of biz) {
    const type = String(b.type ?? '')
    if (/no material/i.test(type) || /no material/i.test(String(b.detail ?? ''))) continue
    const detail = String(b.detail ?? '—')
    const title = type || friendlyRuleName(detail)
    cards.push({
      id: `diff-${type}-${detail}`.slice(0, 80),
      section: sectionForRule(type, detail),
      title,
      currentValue: type.includes('added') ? 'Not present' : 'Prior draft',
      proposedValue: detail === '—' ? 'Changed' : detail,
      businessImpact: type.toLowerCase().includes('removed')
        ? 'A control present earlier is no longer in the draft package.'
        : type.toLowerCase().includes('added')
          ? 'A new control appears in the draft package.'
          : type.toLowerCase().includes('threshold')
            ? 'A numeric threshold changed between draft versions.'
            : 'Draft package content changed — review before approval.',
      affectedProducts: [],
      estimatedImpact: null,
    })
  }

  // Threshold changes from technicalDiff when businessDiff is sparse
  for (const raw of asList(tech.changedThreshold)) {
    const t = asRecord(raw)
    const rule = friendlyRuleName(t.rule)
    cards.push({
      id: `thr-${rule}`,
      section: sectionForRule(rule),
      title: rule,
      currentValue: String(t.from ?? '—'),
      proposedValue: String(t.to ?? '—'),
      businessImpact: 'Threshold change may alter who qualifies.',
      affectedProducts: productHits(rule),
      estimatedImpact: estimatedFor(rule),
    })
  }

  // Dedupe by title+current+proposed
  const seen = new Set<string>()
  return cards.filter((c) => {
    const k = `${c.title}|${c.currentValue}|${c.proposedValue}`
    if (seen.has(k)) return false
    seen.add(k)
    return true
  })
}

export function groupChangesBySection(cards: BusinessChangeCard[]): { section: BusinessChangeSection; cards: BusinessChangeCard[] }[] {
  const order: BusinessChangeSection[] = [
    'Eligibility',
    'Banking',
    'Bureau',
    'GST',
    'ITR',
    'Pricing',
    'Loan Limits',
    'Collateral',
    'Authority',
    'Exceptions',
    'Definitions',
    'Data Requirements',
    'Other',
  ]
  return order
    .map((section) => ({ section, cards: cards.filter((c) => c.section === section) }))
    .filter((g) => g.cards.length > 0)
}

export function buildExecutiveSummary(args: {
  cards: BusinessChangeCard[]
  simulation: SimulationResult | null
  dualComparisons: Record<string, unknown>[]
  approvals: ApprovalsContext | null
  openAmbiguities: number
}): {
  risk: PolicyRisk
  businessImpactLines: string[]
  confidence: Confidence
  recommendation: OverallRec
  aiExplanation: string
} {
  const stricter = args.cards.filter((c) =>
    /STRICTER|MORE_STRICT|stricter/i.test(String(c.differenceClass ?? c.businessImpact)),
  ).length
  const permissive = args.cards.filter((c) => /PERMISSIVE|permissive/i.test(String(c.differenceClass ?? c.businessImpact))).length
  const material = args.cards.length

  let risk: PolicyRisk = 'No Material Change'
  if (stricter > permissive && material > 0) risk = 'More Conservative'
  else if (permissive > stricter && material > 0) risk = 'More Liberal'
  else if (material > 0) {
    const pi = asList(args.simulation?.policyImpact).map(asRecord)
    const moreStrict = Number(pi.find((p) => /strict/i.test(String(p.class ?? '')))?.count ?? 0)
    const morePerm = Number(pi.find((p) => /permiss/i.test(String(p.class ?? '')))?.count ?? 0)
    if (moreStrict > morePerm) risk = 'More Conservative'
    else if (morePerm > moreStrict) risk = 'More Liberal'
    else {
      const fewer = args.cards.filter((c) => /Fewer borrowers/i.test(c.businessImpact)).length
      const more = args.cards.filter((c) => /More borrowers/i.test(c.businessImpact)).length
      if (fewer > more) risk = 'More Conservative'
      else if (more > fewer) risk = 'More Liberal'
      // Material diffs without a clear liberal/conservative stance stay No Material Change;
      // cards and simulation still surface the detail.
    }
  }

  if (material === 0) risk = 'No Material Change'

  const agg = asRecord(args.simulation?.aggregates)
  const tested = Number(agg.applicationsTested ?? asList(args.simulation?.applications).length)
  const passed = Number(agg.passed ?? 0)
  const referred = Number(agg.referred ?? 0)
  const failed = Number(agg.failed ?? 0)
  const di = Number(agg.dataInsufficient ?? 0)

  const businessImpactLines: string[] = []
  if (!args.simulation || tested === 0) {
    businessImpactLines.push('Simulation not yet run — decision impact not measured.')
  } else {
    if (risk === 'More Conservative') {
      businessImpactLines.push('Approvals likely to decrease relative to current LOS on sampled applications.')
    } else if (risk === 'More Liberal') {
      businessImpactLines.push('Approvals may increase relative to current LOS on sampled applications.')
    } else {
      businessImpactLines.push('No material approval-mix change detected in the available comparison.')
    }
    if (referred > 0) {
      businessImpactLines.push(`Applications needing manual review in sample: ${referred} of ${tested}.`)
    }
    if (di > 0) {
      businessImpactLines.push(`Data requirements surfaced missing information on ${di} of ${tested} sample applications.`)
    }
    if (failed > 0 && risk === 'More Conservative') {
      businessImpactLines.push(`Declines in sample: ${failed} of ${tested}.`)
    }
  }

  let confidence: Confidence = 'Medium'
  if (args.simulation && tested >= 8 && args.openAmbiguities === 0) confidence = 'High'
  else if (!args.simulation || args.openAmbiguities > 3) confidence = 'Low'

  let recommendation: OverallRec = 'Ready for Review'
  const blocking = asList(args.approvals?.blockingItems)
  if (blocking.length > 0 || args.openAmbiguities > 0) recommendation = 'Needs Clarification'
  if (!args.simulation || tested === 0) recommendation = 'Needs Simulation'
  if (Boolean(args.approvals?.approvalInvalidated)) recommendation = 'Blocked'

  // Grounded AI explanation — only facts present
  const parts: string[] = []
  if (risk === 'More Conservative') {
    parts.push('This policy appears more conservative')
    if (stricter > 0) parts.push(`because ${stricter} rule comparison${stricter === 1 ? '' : 's'} show a stricter proposed outcome`)
    else parts.push('based on the material differences identified')
  } else if (risk === 'More Liberal') {
    parts.push('This policy appears more liberal')
    if (permissive > 0) parts.push(`because ${permissive} rule comparison${permissive === 1 ? '' : 's'} show a more permissive proposed outcome`)
  } else {
    parts.push('No material policy stance change is evident from the available comparisons')
  }
  if (args.simulation && tested > 0) {
    parts.push(
      `Simulation on ${tested} sample applications shows ${passed} approved, ${referred} manual review, ${failed} declined, and ${di} missing information`,
    )
  }
  if (args.openAmbiguities > 0) {
    parts.push(`${args.openAmbiguities} ambiguous term${args.openAmbiguities === 1 ? '' : 's'} remain open`)
  }

  return {
    risk,
    businessImpactLines,
    confidence,
    recommendation,
    aiExplanation: `${parts.join('. ')}.`,
  }
}

export function buildChangedApplications(simulation: SimulationResult | null): ChangedApplication[] {
  if (!simulation) return []
  const rows: ChangedApplication[] = []
  for (const raw of asList(simulation.applications)) {
    const a = asRecord(raw)
    const legacy = asRecord(asRecord(a.drillDown).legacyComparison)
    const oldD = String(a.currentLosOutcome ?? legacy.currentLos ?? '')
    const newD = String(a.draftPolicyOutcome ?? legacy.draftPolicy ?? a.policyResult ?? '')
    if (!oldD && !newD) continue
    const oldL = businessOutcomeLabel(oldD)
    const newL = businessOutcomeLabel(newD)
    if (oldL === newL) continue
    rows.push({
      code: String(a.applicationCode ?? ''),
      name: String(a.displayName ?? a.applicationCode ?? 'Application'),
      oldDecision: oldL,
      newDecision: newL,
      businessReason: String(a.comparisonReason ?? legacy.reason ?? a.topReason ?? 'Decision differs between current LOS and draft policy.'),
      product: a.product ? String(a.product) : undefined,
    })
  }
  return rows
}

export function buildTopDrivers(args: {
  cards: BusinessChangeCard[]
  simulation: SimulationResult | null
  changedApps: ChangedApplication[]
}): { largestRuleChanges: string[]; largestDecisionChanges: string[]; largestDataGaps: string[]; largestExceptionChanges: string[] } {
  const ruleImpact = asList(args.simulation?.ruleImpact).map(asRecord)
  const missing = asList(args.simulation?.missingDataSummary).map(asRecord)
  const largestRuleChanges = [
    ...ruleImpact.slice(0, 5).map((r) => `${String(r.ruleName)} — ${r.count} hits`),
    ...args.cards.slice(0, 3).map((c) => `${c.title} — ${differenceClassLabel(c.differenceClass ?? 'Other')}`),
  ].slice(0, 5)

  const decisionCounts = new Map<string, number>()
  for (const a of args.changedApps) {
    const k = `${a.oldDecision} → ${a.newDecision}`
    decisionCounts.set(k, (decisionCounts.get(k) ?? 0) + 1)
  }
  const largestDecisionChanges = [...decisionCounts.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 5)
    .map(([k, n]) => `${k} (${n})`)

  const largestDataGaps = missing.slice(0, 5).map((m) => `${String(m.family)} — ${m.count}`)
  const largestExceptionChanges = args.cards
    .filter((c) => c.section === 'Exceptions')
    .slice(0, 5)
    .map((c) => `${c.title}: ${c.currentValue} → ${c.proposedValue}`)

  return { largestRuleChanges, largestDecisionChanges, largestDataGaps, largestExceptionChanges }
}

export function buildProductImpacts(args: {
  cards: BusinessChangeCard[]
  ruleCards: unknown[]
  changedApps: ChangedApplication[]
  draftDiff: Record<string, unknown> | null
}): ProductImpact[] {
  const products = new Set<string>()
  for (const c of args.cards) c.affectedProducts.forEach((p) => products.add(p))
  for (const a of args.changedApps) if (a.product) products.add(a.product)
  for (const raw of args.ruleCards) {
    const scope = String(asRecord(raw).productScope ?? '')
    scope.split(/[,;/]/).map((s) => s.trim()).filter((s) => s && s !== 'ALL').forEach((p) => products.add(p))
  }
  // Always show common demo products if we have any data
  ;['Starter', 'DigiLeap', 'Smart Switch', 'ReBoost'].forEach((p) => {
    if (args.changedApps.some((a) => String(a.product ?? '').toLowerCase().includes(p.toLowerCase().split(' ')[0]))) {
      products.add(p)
    }
  })

  const tech = asRecord(args.draftDiff?.technicalDiff)
  const added = asList(tech.addedRule).length
  const removed = asList(tech.removedRule).length
  const thresholds = asList(tech.changedThreshold).length

  if (products.size === 0) {
    // Aggregate portfolio view
    if (args.cards.length === 0 && args.changedApps.length === 0) return []
    return [
      {
        product: 'All compared products',
        rulesAdded: added,
        rulesRemoved: removed,
        thresholdChanges: thresholds || args.cards.filter((c) => c.currentValue !== 'Not present' && /\d/.test(c.currentValue + c.proposedValue)).length,
        businessImpact:
          args.changedApps.length > 0
            ? `${args.changedApps.length} sample application${args.changedApps.length === 1 ? '' : 's'} changed decision.`
            : `${args.cards.length} material rule difference${args.cards.length === 1 ? '' : 's'} identified.`,
      },
    ]
  }

  return [...products].sort().map((product) => {
    const relatedCards = args.cards.filter(
      (c) => c.affectedProducts.includes(product) || c.affectedProducts.length === 0,
    )
    const relatedApps = args.changedApps.filter((a) => String(a.product ?? '') === product)
    return {
      product,
      rulesAdded: relatedCards.filter((c) => c.currentValue === 'Not present').length || (relatedCards.length ? 0 : added),
      rulesRemoved: relatedCards.filter((c) => /removed/i.test(c.title)).length || 0,
      thresholdChanges: relatedCards.filter((c) => /\d/.test(c.currentValue) && /\d/.test(c.proposedValue)).length,
      businessImpact:
        relatedApps.length > 0
          ? `${relatedApps.length} sample application${relatedApps.length === 1 ? '' : 's'} changed decision for this product.`
          : relatedCards.length > 0
            ? `${relatedCards.length} material difference${relatedCards.length === 1 ? '' : 's'} touch this product scope.`
            : 'No sample decision changes recorded for this product.',
    }
  })
}

export function buildDefinitionChanges(args: {
  draftDiff: Record<string, unknown> | null
  ambiguityCards: unknown[]
  cards: BusinessChangeCard[]
}): DefinitionChange[] {
  const out: DefinitionChange[] = []
  const tech = asRecord(args.draftDiff?.technicalDiff)
  for (const raw of asList(tech.changedMetricMapping)) {
    const m = asRecord(raw)
    out.push({
      term: String(m.term ?? m.metric ?? m.rule ?? 'Business measure'),
      previousMeaning: String(m.from ?? m.previous ?? '—'),
      newMeaning: String(m.to ?? m.next ?? '—'),
      rulesDepending: String(m.rules ?? 'See proposed business rules'),
      simulationImpact: 'Included in draft package comparison only — confirm via simulation.',
    })
  }
  for (const raw of asList(tech.changedAmbiguityResolution)) {
    const m = asRecord(raw)
    out.push({
      term: String(m.term ?? m.unclearTerm ?? 'Ambiguous term'),
      previousMeaning: String(m.from ?? 'Unresolved / prior'),
      newMeaning: String(m.to ?? m.resolvedOption ?? '—'),
      rulesDepending: String(m.rules ?? 'Rules using this term'),
      simulationImpact: 'Resolution may change rule behaviour on re-simulation.',
    })
  }
  // Ambiguities that are definitions section
  for (const c of args.cards.filter((x) => x.section === 'Definitions')) {
    out.push({
      term: c.title,
      previousMeaning: c.currentValue,
      newMeaning: c.proposedValue,
      rulesDepending: c.affectedProducts.length ? c.affectedProducts.join(', ') : 'See related rules',
      simulationImpact: c.estimatedImpact ?? 'Not isolated in simulation summary.',
    })
  }
  // Open/resolved ambiguity cards as definition watchlist when no draft diff
  if (out.length === 0) {
    for (const raw of args.ambiguityCards.slice(0, 8)) {
      const a = asRecord(raw)
      if (!a.unclearTerm) continue
      out.push({
        term: String(a.unclearTerm),
        previousMeaning: String(a.systemInterpretation ?? 'AI understanding pending confirmation'),
        newMeaning: String(a.resolvedOption ?? a.recommendedLabel ?? 'Awaiting Credit Head decision'),
        rulesDepending: String(a.category ?? 'Related policy clauses'),
        simulationImpact: a.open
          ? 'Open ambiguity — simulation may change after resolution.'
          : 'Resolved — re-run simulation to confirm impact.',
      })
    }
  }
  return out
}

export function buildApprovalReadiness(approvals: ApprovalsContext | null, simulation: SimulationResult | null, openAmbiguities: number): {
  items: { label: string; done: boolean }[]
  recommendation: 'Ready' | 'Needs work' | 'Blocked'
} {
  const checklist = asList(approvals?.readinessChecklist).map(asRecord)
  const find = (re: RegExp) => checklist.find((c) => re.test(String(c.label ?? c.display ?? c.key ?? '')))

  const simDone = Boolean(simulation?.runId) || Boolean(find(/simulation/i) && String(find(/simulation/i)?.tone) === 'GREEN')
  const ambDone = openAmbiguities === 0
  const tests = find(/test/i)
  const testsDone = tests ? String(tests.tone) === 'GREEN' : Number(asRecord(approvals?.draftSummary).testsApproved ?? 0) > 0
  const maker = Boolean(approvals?.creditManagerApproved)
  const checker = Boolean(approvals?.checkerApproved)
  const draftReady = Boolean(asRecord(approvals?.draftSummary).policyName) || Boolean(approvals?.canBuildDraft)

  const items = [
    { label: 'Simulation completed', done: simDone },
    { label: 'Critical ambiguities resolved', done: ambDone },
    { label: 'Tests approved', done: testsDone },
    { label: 'Maker approved', done: maker },
    { label: 'Checker approved', done: checker },
    { label: 'Draft package ready', done: draftReady },
  ]

  let recommendation: 'Ready' | 'Needs work' | 'Blocked' = 'Needs work'
  if (Boolean(approvals?.approvalInvalidated) || asList(approvals?.blockingItems).length > 0) {
    recommendation = 'Blocked'
  } else if (simDone && ambDone && (maker || draftReady)) {
    recommendation = 'Ready'
  }

  return { items, recommendation }
}

export function answerComparisonQuestions(args: {
  cards: BusinessChangeCard[]
  changedApps: ChangedApplication[]
  executive: ReturnType<typeof buildExecutiveSummary>
  products: ProductImpact[]
  simulation: SimulationResult | null
}): AskAnswer[] {
  const stricter = args.cards.filter((c) => /strict|conservative|Fewer borrowers/i.test(c.businessImpact + String(c.differenceClass)))
  const removed = args.cards.filter((c) => /removed|Not present/i.test(c.title + c.currentValue) && /removed/i.test(c.title))
  const dataHeavy = args.cards.filter((c) => c.section === 'Data Requirements' || /information|data/i.test(c.businessImpact))
  const productsChanged = args.products.filter((p) => p.rulesAdded + p.rulesRemoved + p.thresholdChanges > 0 || /changed decision/i.test(p.businessImpact))

  const agg = asRecord(args.simulation?.aggregates)
  const tested = Number(agg.applicationsTested ?? 0)

  return [
    {
      question: 'What became stricter?',
      answer:
        stricter.length > 0
          ? stricter.slice(0, 6).map((c) => `${c.title}: ${c.currentValue} → ${c.proposedValue}. ${c.businessImpact}`).join(' ')
          : args.executive.risk === 'More Conservative'
            ? args.executive.aiExplanation
            : 'No stricter rule differences were identified in the available comparison data.',
    },
    {
      question: 'Which products changed?',
      answer:
        productsChanged.length > 0
          ? productsChanged.map((p) => `${p.product}: ${p.businessImpact}`).join(' ')
          : args.cards.some((c) => c.affectedProducts.length)
            ? [...new Set(args.cards.flatMap((c) => c.affectedProducts))].join(', ')
            : 'Product-level changes were not isolated beyond the overall policy comparison.',
    },
    {
      question: 'Which borrowers are affected?',
      answer:
        args.changedApps.length > 0
          ? `${args.changedApps.length} sample application${args.changedApps.length === 1 ? '' : 's'} changed decision: ${args.changedApps
              .slice(0, 5)
              .map((a) => `${a.name} (${a.oldDecision} → ${a.newDecision})`)
              .join('; ')}${args.changedApps.length > 5 ? '…' : ''}.`
          : tested > 0
            ? 'No sample applications changed decision between current LOS and draft policy in the last simulation.'
            : 'Run a simulation to identify which sample borrowers are affected.',
    },
    {
      question: 'Which rules were removed?',
      answer:
        removed.length > 0
          ? removed.map((c) => c.title).join('; ')
          : 'No removed rules were listed in the draft package or dual-run comparison.',
    },
    {
      question: 'Which rules require more data?',
      answer:
        dataHeavy.length > 0
          ? dataHeavy.slice(0, 6).map((c) => c.title).join('; ')
          : asList(args.simulation?.missingDataSummary).length > 0
            ? asList(args.simulation?.missingDataSummary)
                .map(asRecord)
                .map((m) => `${m.family} (${m.count})`)
                .join('; ')
            : 'No additional data-requirement drivers were highlighted in this comparison.',
    },
    {
      question: 'Why is approval reducing?',
      answer:
        args.executive.risk === 'More Conservative'
          ? args.executive.aiExplanation
          : 'Available comparison data does not show a clear reduction in approvals. Review simulation mix and changed applications for detail.',
    },
  ]
}

export function simulationMix(simulation: SimulationResult | null): {
  passed: number
  referred: number
  failed: number
  di: number
  tested: number
} {
  const agg = asRecord(simulation?.aggregates)
  const tested = Number(agg.applicationsTested ?? asList(simulation?.applications).length)
  return {
    tested,
    passed: Number(agg.passed ?? 0),
    referred: Number(agg.referred ?? 0),
    failed: Number(agg.failed ?? 0),
    di: Number(agg.dataInsufficient ?? 0),
  }
}

export function buildBusinessReportHtml(args: {
  title: string
  executive: ReturnType<typeof buildExecutiveSummary>
  current: PolicySideMeta
  draft: PolicySideMeta
  cards: BusinessChangeCard[]
  changedApps: ChangedApplication[]
  readiness: ReturnType<typeof buildApprovalReadiness>
  aiExplanation: string
}): string {
  const esc = (s: string) =>
    s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  return `<!DOCTYPE html><html><head><meta charset="utf-8"/><title>${esc(args.title)}</title>
  <style>
    body{font-family:Georgia,serif;max-width:900px;margin:40px auto;color:#0f172a;line-height:1.45}
    h1{font-size:28px} h2{font-size:18px;margin-top:28px;border-bottom:1px solid #e2e8f0;padding-bottom:6px}
    .card{border:1px solid #e2e8f0;padding:12px 14px;margin:8px 0;border-radius:8px}
    .muted{color:#64748b;font-size:13px} table{width:100%;border-collapse:collapse;font-size:14px}
    td,th{border:1px solid #e2e8f0;padding:8px;text-align:left}
  </style></head><body>
  <h1>${esc(args.title)}</h1>
  <p class="muted">Draft comparison for Credit Head review — not a live underwriting decision. Production authority remains off.</p>
  <h2>Executive summary</h2>
  <div class="card"><strong>Policy risk:</strong> ${esc(args.executive.risk)}<br/>
  <strong>Confidence:</strong> ${esc(args.executive.confidence)}<br/>
  <strong>Recommendation:</strong> ${esc(args.executive.recommendation)}<br/>
  <p>${esc(args.aiExplanation)}</p>
  <ul>${args.executive.businessImpactLines.map((l) => `<li>${esc(l)}</li>`).join('')}</ul></div>
  <h2>Policies compared</h2>
  <div class="card"><strong>Current:</strong> ${esc(args.current.name)} · ${esc(args.current.version)} · ${esc(args.current.status)}</div>
  <div class="card"><strong>Proposed:</strong> ${esc(args.draft.name)} · ${esc(args.draft.version)} · ${esc(args.draft.status)}</div>
  <h2>Policy changes</h2>
  ${args.cards
    .map(
      (c) =>
        `<div class="card"><strong>${esc(c.title)}</strong> (${esc(c.section)})<br/>Current: ${esc(c.currentValue)} → Proposed: ${esc(c.proposedValue)}<br/>${esc(c.businessImpact)}${c.estimatedImpact ? `<br/><span class="muted">${esc(c.estimatedImpact)}</span>` : ''}</div>`,
    )
    .join('') || '<p class="muted">No material changes listed.</p>'}
  <h2>Simulation impact — changed applications</h2>
  <table><thead><tr><th>Application</th><th>Old</th><th>New</th><th>Reason</th></tr></thead><tbody>
  ${args.changedApps
    .map(
      (a) =>
        `<tr><td>${esc(a.name)}</td><td>${esc(a.oldDecision)}</td><td>${esc(a.newDecision)}</td><td>${esc(a.businessReason)}</td></tr>`,
    )
    .join('') || '<tr><td colspan="4">No decision changes in sample.</td></tr>'}
  </tbody></table>
  <h2>Approval readiness</h2>
  <ul>${args.readiness.items.map((i) => `<li>${i.done ? '✓' : '○'} ${esc(i.label)}</li>`).join('')}</ul>
  <p><strong>Business recommendation:</strong> ${esc(args.readiness.recommendation)}</p>
  <h2>Open questions</h2>
  <p class="muted">Confirm remaining ambiguous terms and re-run simulation after any material draft change.</p>
  </body></html>`
}

export function buildChangesCsv(cards: BusinessChangeCard[], changedApps: ChangedApplication[]): string {
  const lines = ['Type,Title,Section,Current,Proposed,Impact,Products,Estimated']
  for (const c of cards) {
    lines.push(
      [
        'Change',
        c.title,
        c.section,
        c.currentValue,
        c.proposedValue,
        c.businessImpact,
        c.affectedProducts.join('; '),
        c.estimatedImpact ?? '',
      ]
        .map((x) => `"${String(x).replace(/"/g, '""')}"`)
        .join(','),
    )
  }
  for (const a of changedApps) {
    lines.push(
      ['Application', a.name, a.product ?? '', a.oldDecision, a.newDecision, a.businessReason, '', '']
        .map((x) => `"${String(x).replace(/"/g, '""')}"`)
        .join(','),
    )
  }
  return lines.join('\n')
}
