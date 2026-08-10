/**
 * POLICY-UX-SHELL-1 — stable Policy Studio workspace shell helpers.
 * Presentation/IA only; does not change backend semantics.
 */

export const POLICY_STUDIO_WORKFLOW_TABS = [
  { id: 'scope', label: 'Scope' },
  { id: 'rules', label: 'Rules' },
  { id: 'simulation', label: 'Test' },
  { id: 'lifecycle', label: 'Versions' },
] as const

export type PolicyStudioWorkflowTabId = (typeof POLICY_STUDIO_WORKFLOW_TABS)[number]['id']

export const POLICY_STUDIO_DETAILS_SECTIONS = [
  { id: 'overview', label: 'Overview', group: 'POLICY DETAILS' },
  { id: 'kyc-eligibility', label: 'KYC & Eligibility', group: 'POLICY DETAILS' },
  { id: 'structure', label: 'Structure', group: 'POLICY DETAILS' },
  { id: 'ambiguities', label: 'Ambiguous Terms', group: 'REVIEW / READINESS' },
  { id: 'data-readiness', label: 'Data Readiness', group: 'REVIEW / READINESS' },
  { id: 'tests', label: 'Generated Tests', group: 'REVIEW / READINESS' },
  { id: 'approvals', label: 'Approvals', group: 'REVIEW / READINESS' },
] as const

export type PolicyStudioDetailsSectionId = (typeof POLICY_STUDIO_DETAILS_SECTIONS)[number]['id']

export function isWorkflowTab(id: string): id is PolicyStudioWorkflowTabId {
  return POLICY_STUDIO_WORKFLOW_TABS.some((t) => t.id === id)
}

export function workflowTabIds(): string[] {
  return POLICY_STUDIO_WORKFLOW_TABS.map((t) => t.id)
}

/** Underwriting-rule counts for header — never inflate with data/calc/children. */
export function underwritingRuleStats(cards: unknown[]): {
  total: number
  ready: number
  needsInput: number
  ignored: number
} {
  const list = Array.isArray(cards) ? cards : []
  const statusOf = (c: unknown) => {
    const r = c && typeof c === 'object' ? (c as Record<string, unknown>) : {}
    return String(r.status ?? '')
  }
  const total = list.length
  const ready = list.filter((c) =>
    ['Ready', 'Accepted', 'Edited', 'Approved'].includes(statusOf(c)),
  ).length
  const needsInput = list.filter((c) =>
    ['Needs your input', 'Needs Review', 'Blocked', 'Needs configuration', 'Unavailable'].includes(
      statusOf(c),
    ),
  ).length
  const ignored = list.filter((c) => statusOf(c) === 'Ignored').length
  return { total, ready, needsInput, ignored }
}

export function formatShellStatusLine(opts: {
  lifecycleStatus?: string
  ready: number
  needsInput: number
  dirty: boolean
  savedLabel?: string | null
}): string {
  const status = (opts.lifecycleStatus || 'Draft').toString()
  const parts = [
    status.charAt(0) + status.slice(1).toLowerCase().replace(/_/g, ' '),
    `${opts.ready} Ready`,
    `${opts.needsInput} Need input`,
  ]
  if (opts.dirty) parts.push('Unsaved changes')
  else if (opts.savedLabel) parts.push(opts.savedLabel)
  return parts.join(' · ')
}

/** Assert primary tabs never mutate when Advanced/details opens. */
export function primaryTabsSnapshot(): string[] {
  return [...workflowTabIds()]
}
