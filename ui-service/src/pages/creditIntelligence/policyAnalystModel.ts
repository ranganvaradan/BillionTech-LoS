import type { StagingPolicyStudio } from '@/api/creditIntelligence'

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

/** Preferred discovery card order for the analyst experience. */
export const DISCOVERY_CARD_ORDER = [
  'KYC & Eligibility',
  'Credit Rules',
  'Business Rules',
  'Definitions',
  'Eligibility Rules',
  'Banking Rules',
  'Bureau Rules',
  'GST Rules',
  'ITR Rules',
  'Tax / ITR Rules',
  'Pricing Rules',
  'Limit Rules',
  'Authority Matrix',
  'Authority Rules',
  'Exceptions',
  'Information Requirements',
  'Information Required',
  'Manual Verification',
  'Ambiguous Terms',
  'Ambiguities',
  'Missing Metrics',
] as const

export type DiscoveryCard = {
  key: string
  label: string
  count: number
}

export type AnalystTimelineStep = {
  key: string
  label: string
  state: 'DONE' | 'CURRENT' | 'PENDING'
}

export type AnalystMessage = {
  id: string
  kind: 'greeting' | 'progress' | 'find' | 'note' | 'complete'
  text: string
}

function normalizeLabel(label: string): string {
  const t = label.trim()
  if (t === 'Ambiguities') return 'Ambiguous Terms'
  if (t === 'Information Requirements') return 'Information Required'
  if (t === 'Authority Rules') return 'Authority Matrix'
  if (t === 'Tax / ITR Rules') return 'ITR Rules'
  return t
}

export function firstNameFromUser(name: string | null | undefined): string {
  if (!name || !name.trim()) return 'there'
  const part = name.trim().split(/\s+/)[0]
  return part || 'there'
}

export function discoveryCardsFromSession(session: StagingPolicyStudio): DiscoveryCard[] {
  const cards = asList(session.summaryCards)
  const byLabel = new Map<string, number>()
  for (const raw of cards) {
    const c = asRecord(raw)
    const label = normalizeLabel(String(c.label ?? ''))
    const count = Number(c.count ?? 0)
    if (!label || count <= 0) continue
    byLabel.set(label, (byLabel.get(label) ?? 0) + count)
  }

  // Ensure Ambiguous Terms / Missing Metrics from counts when summary cards omit them
  const counts = asRecord(session.counts)
  const readiness = asRecord(session.readinessBanner)
  const amb =
    Number(readiness.ambiguities ?? counts.openAmbiguities ?? counts.ambiguities ?? 0) || 0
  if (amb > 0 && !byLabel.has('Ambiguous Terms')) {
    byLabel.set('Ambiguous Terms', amb)
  }
  const missing = Number(readiness.missingMetrics ?? counts.missingMetrics ?? 0) || 0
  if (missing > 0 && !byLabel.has('Missing Metrics')) {
    byLabel.set('Missing Metrics', missing)
  }

  // Aggregate Business Rules = sum of hard rule-like categories when present
  const ruleLike = ['Banking Rules', 'Bureau Rules', 'Eligibility Rules', 'Pricing Rules', 'Limit Rules']
  const ruleSum = ruleLike.reduce((n, k) => n + (byLabel.get(k) ?? 0), 0)
  if (ruleSum > 0 && !byLabel.has('Business Rules')) {
    byLabel.set('Business Rules', ruleSum)
  }

  const ordered: DiscoveryCard[] = []
  const seen = new Set<string>()
  for (const pref of DISCOVERY_CARD_ORDER) {
    const label = normalizeLabel(pref)
    if (seen.has(label)) continue
    const count = byLabel.get(label)
    if (count != null && count > 0) {
      ordered.push({ key: label, label, count })
      seen.add(label)
    }
  }
  for (const [label, count] of byLabel) {
    if (seen.has(label) || count <= 0) continue
    ordered.push({ key: label, label, count })
    seen.add(label)
  }
  return ordered
}

export function timelineFromSession(
  session: StagingPolicyStudio | null,
  phase: 'waiting' | 'revealing' | 'summary',
  revealIndex: number,
  totalReveals: number,
): AnalystTimelineStep[] {
  const base: { key: string; label: string }[] = [
    { key: 'uploaded', label: 'Document uploaded' },
    { key: 'extracted', label: 'Text extracted' },
    { key: 'structure', label: 'Structure understood' },
    { key: 'clauses', label: 'Clauses identified' },
    { key: 'interpreted', label: 'Rules understood' },
    { key: 'metrics', label: 'Business measures mapped' },
    { key: 'ambiguities', label: 'Ambiguous terms detected' },
    { key: 'tests', label: 'Tests generated' },
    { key: 'draft', label: 'Draft policy created' },
  ]

  if (phase === 'waiting' || !session) {
    return base.map((s, i) => ({
      ...s,
      state: i === 0 ? 'DONE' : i === 1 ? 'CURRENT' : 'PENDING',
    }))
  }

  const counts = asRecord(session.counts)
  const pipeline = asRecord(session.pipeline)
  const progressPct = Number(pipeline.progressPercent ?? 0)
  const hasClauses = Number(counts.totalClauses ?? 0) > 0
  const hasInterp = Number(counts.interpretedClauses ?? 0) > 0
  const hasRules = Number(counts.rules ?? 0) > 0
  const hasMetrics = Number(counts.metrics ?? 0) > 0
  const hasAmb = Number(counts.ambiguities ?? counts.openAmbiguities ?? 0) > 0
    || Number(asRecord(session.readinessBanner).ambiguities ?? 0) > 0
  const hasTests = Number(counts.tests ?? 0) > 0
  const doneFlags = [
    true, // uploaded
    true, // extracted (response received)
    hasClauses || progressPct >= 20,
    hasClauses,
    hasInterp || hasRules,
    hasMetrics || hasRules,
    hasAmb || hasRules,
    hasTests,
    phase === 'summary' || revealIndex >= totalReveals,
  ]

  let currentSet = false
  return base.map((s, i) => {
    if (doneFlags[i]) {
      return { ...s, state: 'DONE' as const }
    }
    if (!currentSet) {
      currentSet = true
      return { ...s, state: 'CURRENT' as const }
    }
    return { ...s, state: 'PENDING' as const }
  })
}

export function waitingStatusLabels(elapsedMs: number): { headline: string; detail: string; progress: number } {
  // Honest waiting UX while the single upload API runs — labels track elapsed work, not fake completions.
  if (elapsedMs < 1200) {
    return { headline: 'Reading your policy…', detail: 'Opening the document and preparing extraction.', progress: 12 }
  }
  if (elapsedMs < 2800) {
    return { headline: 'Understanding document structure…', detail: 'Parsing sections and clause boundaries.', progress: 28 }
  }
  if (elapsedMs < 4500) {
    return {
      headline: 'Finding business rules…',
      detail: 'Identifying KYC & Eligibility requirements, plus credit, banking and bureau conditions.',
      progress: 44,
    }
  }
  if (elapsedMs < 6500) {
    return { headline: 'Identifying definitions…', detail: 'Locating business measure definitions and adjustments.', progress: 58 }
  }
  if (elapsedMs < 9000) {
    return { headline: 'Looking for exceptions…', detail: 'Scanning exception and exclusion language.', progress: 70 }
  }
  if (elapsedMs < 12000) {
    return { headline: 'Finding ambiguous business terms…', detail: 'Flagging terms that need Credit Head confirmation.', progress: 82 }
  }
  return {
    headline: 'Preparing draft policy…',
    detail: 'Generating proposed business rules, measures and boundary tests.',
    progress: 92,
  }
}

export function buildRevealScript(
  session: StagingPolicyStudio,
  analystFirstName: string,
): AnalystMessage[] {
  const cards = discoveryCardsFromSession(session)
  const counts = asRecord(session.counts)
  const readiness = asRecord(session.readinessBanner)
  const messages: AnalystMessage[] = []
  let n = 0
  const push = (kind: AnalystMessage['kind'], text: string) => {
    messages.push({ id: `m-${n++}`, kind, text })
  }

  push(
    'greeting',
    `Hello ${analystFirstName},\n\nI'm analysing your credit policy.`,
  )

  const highlights = cards.filter((c) =>
    ['Banking Rules', 'Bureau Rules', 'Information Required', 'Exceptions', 'Definitions', 'Eligibility Rules'].includes(
      c.label,
    ),
  )
  if (highlights.length > 0) {
    const lines = highlights.map((c) => `✓ ${c.count} ${c.label}`).join('\n')
    push('progress', `I have already identified\n\n${lines}`)
  } else {
    const total = Number(counts.totalClauses ?? 0)
    const rules = Number(counts.rules ?? 0)
    push(
      'progress',
      `I have structured the document into ${total} clauses and prepared ${rules} proposed business rules for review.`,
    )
  }

  push('progress', "I'm now walking through what the policy contains, using only what was extracted from your document.")

  for (const card of cards) {
    const lower = card.label.toLowerCase()
    if (lower.includes('ambiguous')) {
      push(
        'find',
        `I found ${card.count} ambiguous business term${card.count === 1 ? '' : 's'} that may require confirmation.`,
      )
    } else if (lower.includes('missing')) {
      push(
        'find',
        `I found ${card.count} missing business measure${card.count === 1 ? '' : 's'} referenced by the policy language.`,
      )
    } else if (lower.includes('kyc')) {
      push(
        'find',
        `I found ${card.count} KYC & Eligibility requirement${card.count === 1 ? '' : 's'}.`,
      )
    } else if (lower.includes('credit rules')) {
      push('find', `I found ${card.count} credit rule${card.count === 1 ? '' : 's'}.`)
    } else if (lower.includes('bureau')) {
      push('find', `I found a bureau rule set — ${card.count} bureau-related clause${card.count === 1 ? '' : 's'}.`)
    } else if (lower.includes('banking')) {
      push('find', `I found a capacity / banking rule set — ${card.count} banking clause${card.count === 1 ? '' : 's'}.`)
    } else if (lower.includes('exception')) {
      push('find', `I found ${card.count} exception clause${card.count === 1 ? '' : 's'}.`)
    } else if (lower.includes('pricing')) {
      push('find', `I discovered a pricing condition cluster (${card.count}).`)
    } else if (lower.includes('definition')) {
      push('find', `This clause group appears to define business measures or adjustments (${card.count}).`)
    } else if (lower.includes('eligibility')) {
      push('find', `I found ${card.count} eligibility rule${card.count === 1 ? '' : 's'}.`)
    } else if (lower.includes('information')) {
      push('find', `I found ${card.count} information requirement${card.count === 1 ? '' : 's'}.`)
    } else {
      push('find', `I found ${card.count} item${card.count === 1 ? '' : 's'} under ${card.label}.`)
    }
  }

  const tests = Number(counts.tests ?? readiness.testsGenerated ?? 0)
  if (tests > 0) {
    push('note', `I generated ${tests} boundary and missing-information test${tests === 1 ? '' : 's'} from the understood rules.`)
  }

  const openAmb = Number(readiness.ambiguities ?? counts.openAmbiguities ?? 0)
  if (openAmb > 0) {
    push(
      'note',
      `This definition set may require confirmation — ${openAmb} open ambiguous terms remain for Credit Head review.`,
    )
  }

  const impl = asRecord(session.implementability)
  const implSummary = asRecord(impl.summary ?? session.implementabilitySummary)
  const implPct = Number(implSummary.implementationReadinessPercent ?? 0)
  const rulesId = Number(implSummary.rulesIdentified ?? 0)
  const distinct = Number(implSummary.distinctDataElements ?? 0)
  const readyAuto =
    Number(implSummary.ready ?? 0) + Number(implSummary.readyWithFallback ?? 0)
  const manual = Number(implSummary.manualVerification ?? 0)
  const clarify = Number(implSummary.needsClarification ?? 0)
  const missing = Number(implSummary.missingData ?? 0) + Number(implSummary.blocked ?? 0)

  if (rulesId > 0) {
    push(
      'progress',
      "I've now checked the policy against your configured data sources.",
    )
    push(
      'note',
      "I've also checked whether BillionTech knows how to calculate every business measure the policy requires.",
    )
    const analystMsg = String(impl.analystMessage ?? '').trim()
    if (analystMsg) {
      push('note', analystMsg)
    } else {
      push(
        'note',
        `We identified ${rulesId} rules requiring ${distinct} distinct business data elements.\n\n` +
          `Your configured data sources can execute ${readyAuto} rules automatically.` +
          (manual > 0 ? `\n${manual} rule${manual === 1 ? '' : 's'} require manual verification.` : '') +
          (clarify > 0 ? `\n${clarify} rule${clarify === 1 ? '' : 's'} need clarification.` : '') +
          (missing > 0
            ? `\n${missing} rule${missing === 1 ? '' : 's'} cannot currently be fully implemented with available data.`
            : '') +
          (implPct > 0 ? `\n\nImplementation readiness: ${implPct}%.` : ''),
      )
    }
  }

  const readinessPct = Number(readiness.policyReadinessPercent ?? 0)
  if (readinessPct > 0 && readinessPct < 55) {
    push('note', 'This policy appears to need careful review before draft approval — readiness is still building.')
  } else if (readinessPct >= 70) {
    push('note', 'This policy appears relatively complete for drafting, subject to remaining human confirmations.')
  }

  push('complete', 'Analysis complete. A draft policy has been prepared for review — nothing is active in production.')
  return messages
}

export function policySummaryFromSession(session: StagingPolicyStudio): {
  lines: { label: string; count: number }[]
  estimatedMinutes: number
  policyName: string
} {
  const cards = discoveryCardsFromSession(session)
  const counts = asRecord(session.counts)
  const readiness = asRecord(session.readinessBanner)
  const header = asRecord(session.policyHeader)

  const preferred = [
    'Banking Rules',
    'Bureau Rules',
    'Exceptions',
    'Definitions',
    'Ambiguous Terms',
    'Missing Metrics',
    'Information Required',
    'Eligibility Rules',
  ]
  const lines: { label: string; count: number }[] = []
  for (const p of preferred) {
    const hit = cards.find((c) => c.label === p)
    if (hit) {
      const label =
        hit.label === 'Missing Metrics'
          ? 'Missing Business Measures'
          : hit.label === 'Ambiguous Terms'
            ? 'Ambiguous Terms'
            : hit.label
      lines.push({ label, count: hit.count })
    }
  }
  const tests = Number(counts.tests ?? readiness.testsGenerated ?? 0)
  if (tests > 0) {
    lines.push({ label: 'Generated Tests', count: tests })
  }

  const implSummary = asRecord(asRecord(session.implementability).summary ?? session.implementabilitySummary)
  const readyAuto = Number(implSummary.ready ?? 0) + Number(implSummary.readyWithFallback ?? 0)
  const implRules = Number(implSummary.rulesIdentified ?? 0)
  if (implRules > 0) {
    lines.push({ label: 'Automatable Rules', count: readyAuto })
    const attention =
      Number(implSummary.needsClarification ?? 0) +
      Number(implSummary.manualVerification ?? 0) +
      Number(implSummary.missingData ?? 0) +
      Number(implSummary.blocked ?? 0)
    if (attention > 0) {
      lines.push({ label: 'Data Attention Items', count: attention })
    }
  }

  const openAmb = Number(readiness.ambiguities ?? counts.openAmbiguities ?? 0)
  const needReview = Number(readiness.rulesNeedReview ?? counts.rulesNeedReview ?? 0)
  const estimatedMinutes = Math.min(
    45,
    Math.max(5, Math.round(openAmb * 1.5 + needReview * 0.4 + Number(counts.rules ?? 0) * 0.15 + 4)),
  )

  return {
    lines,
    estimatedMinutes,
    policyName: String(header.policyName ?? 'Credit policy'),
  }
}
