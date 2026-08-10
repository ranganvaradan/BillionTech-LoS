import { useState } from 'react'

export type UnderwritingRuleRanRow = {
  ruleId?: string
  ruleName?: string
  policyDecision?: string
  creditDecision?: string
  riskScore?: number
  reasons?: string[]
  kind?: string
  matchedConditions?: Record<string, unknown>
  sourceValuesUsed?: Record<string, unknown>
}

export type UnderwritingParameterRanRow = {
  parameter?: string
  rowId?: string
  source?: string
  condition?: string
  weight?: number
  matched?: boolean
  pointsEarned?: number
  maxScore?: number
  valueUsed?: string
  valueSource?: string
  decision?: string
  reason?: string
  hardRule?: boolean
  formulaBreakdown?: unknown
  dependencyOutcome?: unknown
  skippedDueToDependency?: boolean
}

function decisionTone(decision?: string): string {
  const d = (decision ?? '').toUpperCase()
  if (d.includes('APPROVE') || d === 'PASS') return 'bg-emerald-100 text-emerald-900'
  if (d.includes('MANUAL') || d.includes('REVIEW')) return 'bg-amber-100 text-amber-900'
  if (d.includes('REJECT')) return 'bg-rose-100 text-rose-900'
  return 'bg-slate-100 text-slate-800'
}

function formatDisplayValue(v: unknown): string {
  if (v == null) return '—'
  if (typeof v === 'string' || typeof v === 'number' || typeof v === 'boolean') return String(v)
  try {
    return JSON.stringify(v)
  } catch {
    return String(v)
  }
}

function isBulkyValue(v: unknown): boolean {
  if (v == null) return false
  if (Array.isArray(v)) return true
  if (typeof v === 'object') return true
  if (typeof v === 'string' && v.length > 120) return true
  return false
}

function bulkySummary(v: unknown): string {
  if (Array.isArray(v)) return `${v.length} item${v.length === 1 ? '' : 's'}`
  if (v != null && typeof v === 'object')
    return `${Object.keys(v as object).length} field${Object.keys(v as object).length === 1 ? '' : 's'}`
  if (typeof v === 'string') return `${v.length} chars`
  return 'details'
}

function KeyValueBlock({
  title,
  titleClass,
  data,
}: {
  title: string
  titleClass: string
  data?: Record<string, unknown> | null
}) {
  const [expandedKeys, setExpandedKeys] = useState<Record<string, boolean>>({})
  const [showAll, setShowAll] = useState(false)

  if (!data || Object.keys(data).length === 0) return null

  const entries = Object.entries(data)
  const simple = entries.filter(([, v]) => !isBulkyValue(v))
  const bulky = entries.filter(([, v]) => isBulkyValue(v))
  const hasHidden = bulky.length > 0

  return (
    <div className="mt-2 rounded-md border border-slate-100 bg-slate-50 px-2.5 py-2">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className={`text-[10px] font-semibold uppercase tracking-wide ${titleClass}`}>{title}</p>
        {hasHidden ? (
          <button
            type="button"
            className="text-[10px] font-medium text-indigo-700 underline hover:text-indigo-900"
            onClick={() => setShowAll((v) => !v)}
          >
            {showAll ? 'Hide large fields' : `Show large fields (${bulky.length})`}
          </button>
        ) : null}
      </div>

      {simple.length > 0 ? (
        <dl className="mt-1 grid gap-1 sm:grid-cols-2">
          {simple.map(([k, v]) => (
            <div key={k} className="text-[11px]">
              <dt className="inline text-slate-500">{k}: </dt>
              <dd className="inline break-all font-mono text-slate-900">{formatDisplayValue(v)}</dd>
            </div>
          ))}
        </dl>
      ) : !showAll ? (
        <p className="mt-1 text-[11px] text-slate-500">
          Only large nested fields are present — expand if you need the raw details.
        </p>
      ) : null}

      {showAll && bulky.length > 0 ? (
        <ul className="mt-2 space-y-1.5">
          {bulky.map(([k, v]) => {
            const open = Boolean(expandedKeys[k])
            return (
              <li key={k} className="rounded border border-slate-200 bg-white px-2 py-1.5">
                <button
                  type="button"
                  className="flex w-full items-center justify-between gap-2 text-left text-[11px]"
                  onClick={() => setExpandedKeys((prev) => ({ ...prev, [k]: !prev[k] }))}
                >
                  <span>
                    <span className="font-medium text-slate-800">{k}</span>
                    <span className="ml-1.5 text-slate-500">({bulkySummary(v)})</span>
                  </span>
                  <span className="text-[10px] font-medium text-indigo-700">{open ? 'Hide' : 'View'}</span>
                </button>
                {open ? (
                  <pre className="mt-1.5 max-h-40 overflow-auto rounded bg-slate-50 p-2 text-[10px] whitespace-pre-wrap break-words text-slate-800">
                    {formatDisplayValue(v)}
                  </pre>
                ) : null}
              </li>
            )
          })}
        </ul>
      ) : null}
    </div>
  )
}

/**
 * Policy / rule-set snapshot only. Parameter scoring lives in {@link ScorecardSummaryPanel}.
 */
export function UnderwritingRulesRanPanel({
  ruleResults,
  aggregateDecision,
  aggregateScore,
  defaultOpen = true,
}: {
  ruleResults?: UnderwritingRuleRanRow[] | unknown[] | null
  /** @deprecated kept for call-site compatibility; not rendered here (see Application credit score). */
  parameterResults?: UnderwritingParameterRanRow[] | null
  aggregateDecision?: string
  aggregateScore?: number | string | null
  defaultOpen?: boolean
}) {
  const [open, setOpen] = useState(defaultOpen)
  const rules = (Array.isArray(ruleResults) ? ruleResults : []) as UnderwritingRuleRanRow[]
  if (rules.length === 0) return null

  return (
    <div className="bt-section-card overflow-hidden border border-slate-200 bg-white p-0">
      <button
        type="button"
        className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left hover:bg-slate-50/80"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
      >
        <div className="min-w-0">
          <div className="mb-1.5 h-1 w-10 rounded-full bg-orange-500" />
          <div className="text-xs font-semibold uppercase tracking-wide text-slate-700">
            Underwriting policy / rule-set snapshot
          </div>
          <p className="mt-0.5 text-xs text-slate-600">
            {rules.length} rule set{rules.length === 1 ? '' : 's'}
            {aggregateDecision ? (
              <>
                {' '}
                · outcome{' '}
                <span className={`inline-flex rounded-full px-2 py-0.5 text-[10px] font-semibold ${decisionTone(aggregateDecision)}`}>
                  {aggregateDecision}
                </span>
              </>
            ) : null}
            {aggregateScore != null && aggregateScore !== '' ? ` · score ${aggregateScore}` : ''}
          </p>
        </div>
        <span
          className={`shrink-0 text-slate-400 transition-transform duration-200 ${open ? 'rotate-90' : ''}`}
          aria-hidden
        >
          ›
        </span>
      </button>

      {open ? (
        <div className="space-y-3 border-t border-slate-100 bg-gradient-to-b from-slate-50/80 to-white px-4 py-4">
          <p className="text-[11px] text-slate-500">
            Soft scorecard criteria and parameter points are shown once under <strong>Application credit score</strong>.
          </p>
          <div className="space-y-2">
            {rules.map((row, i) => (
              <div key={String(row.ruleId ?? i)} className="rounded-xl border border-slate-200 bg-white p-3 shadow-sm">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <div className="font-medium text-slate-900">{row.ruleName ?? row.ruleId ?? `Rule ${i + 1}`}</div>
                    {row.kind ? <div className="mt-0.5 text-[11px] text-slate-500">Kind: {row.kind}</div> : null}
                  </div>
                  <div className="flex flex-wrap gap-1.5">
                    <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${decisionTone(row.policyDecision)}`}>
                      Policy: {row.policyDecision ?? '—'}
                    </span>
                    <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${decisionTone(row.creditDecision)}`}>
                      Credit: {row.creditDecision ?? '—'}
                    </span>
                    {row.riskScore != null ? (
                      <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-medium text-slate-700">
                        Risk {row.riskScore}
                      </span>
                    ) : null}
                  </div>
                </div>

                {(row.reasons?.length ?? 0) > 0 ? (
                  <ul className="mt-2 list-inside list-disc text-[11px] text-slate-700">
                    {row.reasons!.map((r, idx) => (
                      <li key={idx}>{r}</li>
                    ))}
                  </ul>
                ) : null}

                <KeyValueBlock title="Matched conditions" titleClass="text-sky-800" data={row.matchedConditions} />
                <KeyValueBlock title="Source values used" titleClass="text-slate-500" data={row.sourceValuesUsed} />
              </div>
            ))}
          </div>
        </div>
      ) : null}
    </div>
  )
}
