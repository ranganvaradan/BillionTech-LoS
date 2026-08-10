import { useState } from 'react'
import { ParameterDerivationDetails } from '@/components/credit/ParameterDerivationDetails'
import { formatSummaryValueDisplay } from '@/lib/format'
import {
  buildScorecardCategoryGroups,
  type ScorecardParameterRow,
} from '@/lib/credit/scorecardSummaryLayout'

type EvalShape = {
  aggregateScore?: number
  aggregateDecision?: string
  scorecardName?: string
  scorecardVersion?: number
  parameterResults?: ScorecardParameterRow[]
  ruleResults?: unknown[]
}

function outcomeBadgeClass(decision: string | undefined): string {
  const d = (decision ?? '').toUpperCase()
  if (d === 'APPROVED' || d === 'APPROVE') {
    return 'bg-emerald-100 text-emerald-900 border-emerald-200'
  }
  if (d === 'MANUAL_REVIEW' || d === 'MANUAL') {
    return 'bg-amber-100 text-amber-950 border-amber-200'
  }
  if (d === 'REJECTED' || d === 'REJECT') {
    return 'bg-rose-100 text-rose-950 border-rose-200'
  }
  return 'bg-slate-100 text-slate-800 border-slate-200'
}

function statusBadgeClass(status: string): string {
  if (status === 'pass') return 'bg-emerald-100 text-emerald-900 border-emerald-200'
  if (status === 'missing') return 'bg-amber-100 text-amber-950 border-amber-200'
  if (status === 'fail') return 'bg-rose-100 text-rose-950 border-rose-200'
  return 'bg-slate-100 text-slate-700 border-slate-200'
}

function hardFailureLine(row: ScorecardParameterRow): string {
  const bits = [
    row.reason,
    row.parameter ? `${row.parameter} value ${row.valueUsed ?? '—'}` : null,
    row.condition ? `triggered condition ${row.condition}` : null,
  ].filter(Boolean)
  return bits.join(' — ')
}

/** Parse engine messages like: "TOL/TNW exceeds policy — TOL_TNW value 8 triggered condition GT:7" */
function parseHardRuleReason(text: string): ScorecardParameterRow {
  const trimmed = text.trim()
  const m = trimmed.match(
    /^(.+?)\s+—\s+([A-Za-z0-9_]+)\s+value\s+(\S+?)(?:\s+triggered condition\s+(\S+))?\s*$/,
  )
  if (m) {
    return {
      hardRule: true,
      matched: true,
      reason: m[1],
      parameter: m[2],
      valueUsed: m[3],
      condition: m[4],
    }
  }
  return {
    hardRule: true,
    matched: true,
    reason: trimmed,
  }
}

function hardFailuresFromRuleResults(ruleResults: unknown[] | undefined): ScorecardParameterRow[] {
  if (!Array.isArray(ruleResults)) return []
  const out: ScorecardParameterRow[] = []
  for (const raw of ruleResults) {
    if (!raw || typeof raw !== 'object') continue
    const r = raw as Record<string, unknown>
    if (String(r.kind ?? '').toUpperCase() !== 'HARD_RULE') continue
    const reasons = Array.isArray(r.reasons) ? r.reasons : []
    if (reasons.length === 0) {
      out.push({
        hardRule: true,
        matched: true,
        parameter: typeof r.ruleName === 'string' ? r.ruleName : undefined,
        reason: 'Hard rule triggered',
      })
      continue
    }
    for (const reason of reasons) {
      out.push(parseHardRuleReason(String(reason ?? '')))
    }
  }
  return out
}

export function ScorecardSummaryPanel({
  evaluation,
  rawParameterValues,
  onOpenManualInput,
  defaultOpen = true,
}: {
  evaluation: EvalShape
  /** Effective scorecard map — shown collapsed for audit only. */
  rawParameterValues?: Record<string, string> | null
  onOpenManualInput?: (parameter?: string) => void
  defaultOpen?: boolean
}) {
  const [open, setOpen] = useState(defaultOpen)
  const [rawOpen, setRawOpen] = useState(false)
  /** Category overview table: expanded by default. */
  const [overviewOpen, setOverviewOpen] = useState(true)
  /** Per-category detail: collapsed until opened. */
  const [openCategories, setOpenCategories] = useState<Record<string, boolean>>({})
  const rows = evaluation.parameterResults
  const groups = buildScorecardCategoryGroups(rows)
  const aggregate = evaluation.aggregateScore
  const paramHardFailures = (rows ?? []).filter((r) => r.hardRule && r.matched)
  const ruleHardFailures = hardFailuresFromRuleResults(evaluation.ruleResults)
  // Prefer scorecard hard-rule trace rows; fall back to rule-set HARD_RULE reasons.
  const hardFailures = paramHardFailures.length > 0 ? paramHardFailures : ruleHardFailures
  const onlyHardRuleTrace = Boolean(rows?.length) && rows!.every((r) => r.hardRule)
  // Rule-set hard rules decide before soft scoring is used — treat as scoring skipped for display.
  const scoringSkipped =
    hardFailures.length > 0 && (onlyHardRuleTrace || ruleHardFailures.length > 0)
  const displayGroups = scoringSkipped && ruleHardFailures.length > 0 && !onlyHardRuleTrace ? [] : groups

  if (aggregate == null && displayGroups.length === 0 && hardFailures.length === 0) {
    return null
  }

  const titleBits = [
    evaluation.scorecardName,
    evaluation.scorecardVersion != null ? `v${evaluation.scorecardVersion}` : null,
  ].filter(Boolean)

  const hardBannerTitle = (() => {
    const d = (evaluation.aggregateDecision ?? '').toUpperCase()
    if (d === 'REJECTED' || d === 'REJECT') return 'Rejected by hard rule'
    if (d === 'MANUAL_REVIEW' || d === 'MANUAL') return 'Hard rule requires review'
    return 'Stopped by hard rule'
  })()

  const rawEntries = rawParameterValues ? Object.entries(rawParameterValues) : []

  return (
    <div className="bt-section-card bt-section-card--violet overflow-hidden p-0">
      <button
        type="button"
        className="flex w-full items-start justify-between gap-3 px-4 py-3 text-left hover:bg-violet-50/50"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        <div className="min-w-0 flex-1">
          <div className="mb-1.5 h-1 w-10 rounded-full bg-orange-500" />
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-sm font-semibold text-violet-950">Application credit score</h3>
            {evaluation.aggregateDecision ? (
              <span
                className={`inline-flex rounded-full border px-2.5 py-0.5 text-xs font-medium ${outcomeBadgeClass(evaluation.aggregateDecision)}`}
              >
                {String(evaluation.aggregateDecision).replace(/_/g, ' ')}
              </span>
            ) : null}
          </div>
          {titleBits.length > 0 ? (
            <p className="mt-1 text-xs text-slate-600">{titleBits.join(' · ')}</p>
          ) : null}
        </div>
        <span
          className={`mt-1 shrink-0 text-slate-400 transition-transform duration-200 ${open ? 'rotate-90' : ''}`}
          aria-hidden
        >
          ›
        </span>
      </button>

      {open ? (
        <div className="space-y-6 border-t border-violet-100 px-4 py-4">
          {hardFailures.length > 0 ? (
            <div className="rounded-xl border border-rose-200 bg-rose-50 p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h4 className="text-sm font-semibold text-rose-950">{hardBannerTitle}</h4>
                  <p className="mt-1 text-sm text-rose-900">
                    {scoringSkipped
                      ? 'Soft scorecard scoring was not run because a mandatory policy rule failed first.'
                      : 'A mandatory policy rule failed during underwriting.'}
                  </p>
                </div>
                <span className="rounded-full border border-rose-200 bg-white px-2.5 py-1 text-xs font-semibold text-rose-800">
                  Hard stop
                </span>
              </div>
              <ul className="mt-3 space-y-2">
                {hardFailures.map((row, idx) => (
                  <li
                    key={`${row.rowId ?? row.parameter ?? 'hard'}-${idx}`}
                    className="rounded-lg border border-rose-100 bg-white px-3 py-2 text-sm text-rose-950"
                  >
                    <span className="font-semibold">{row.parameter ?? 'Policy rule'}:</span> {hardFailureLine(row)}
                  </li>
                ))}
              </ul>
            </div>
          ) : null}

          {aggregate != null && !scoringSkipped ? (
            <div className="flex flex-wrap items-end gap-4 border-b border-violet-200/60 pb-5">
              <div>
                <p className="text-xs font-medium uppercase tracking-wide text-violet-800/80">Composite score</p>
                <p className="text-5xl font-semibold tabular-nums leading-none text-violet-950">{aggregate}</p>
              </div>
              <p className="max-w-md text-sm text-slate-600">
                Single source of truth for scorecard parameters: status, value used, required/resolved source, weight,
                and points per criterion.
              </p>
            </div>
          ) : scoringSkipped ? (
            <div className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-700">
              <span className="font-medium text-slate-900">Score not calculated.</span> Hard-rule checks are shown below
              for audit clarity; weighted scoring rows were skipped after the rejection.
            </div>
          ) : null}

          {displayGroups.length > 0 ? (
            <div className="space-y-3">
              <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
                <button
                  type="button"
                  className="flex w-full items-center justify-between gap-2 px-3 py-2.5 text-left hover:bg-slate-50"
                  aria-expanded={overviewOpen}
                  onClick={() => setOverviewOpen((v) => !v)}
                >
                  <h4 className="text-sm font-semibold text-slate-900">
                    {scoringSkipped ? 'Hard-rule check trace' : 'Category overview'}
                  </h4>
                  <span
                    className={`text-slate-400 transition-transform ${overviewOpen ? 'rotate-90' : ''}`}
                    aria-hidden
                  >
                    ›
                  </span>
                </button>
                {overviewOpen ? (
                  <div className="border-t border-slate-100">
                    {!scoringSkipped ? (
                      <>
                        <div className="grid grid-cols-[minmax(0,1fr)_7.5rem_6.5rem] gap-x-3 border-b border-slate-200 bg-slate-50 px-3 py-2 text-[11px] font-semibold uppercase tracking-wide text-slate-500 sm:grid-cols-[minmax(0,1fr)_9rem_7.5rem]">
                          <span>Category</span>
                          <span className="text-right">Points earned</span>
                          <span className="text-right">Normalized %</span>
                        </div>
                        <div className="divide-y divide-slate-100">
                          {displayGroups.map((g) => {
                            const maxPts = g.rows.reduce((sum, r) => sum + (r.maxScore ?? 0), 0)
                            const norm = maxPts > 0 ? Math.round((g.categoryScore / maxPts) * 1000) / 10 : 0
                            return (
                              <div
                                key={g.category}
                                className="grid grid-cols-[minmax(0,1fr)_7.5rem_6.5rem] gap-x-3 px-3 py-2.5 text-sm sm:grid-cols-[minmax(0,1fr)_9rem_7.5rem]"
                              >
                                <div className="min-w-0 font-medium text-slate-900">{g.category}</div>
                                <div className="text-right font-mono tabular-nums text-slate-900">
                                  {g.categoryScore.toFixed(0)}
                                  {maxPts > 0 ? ` / ${maxPts.toFixed(0)}` : ''}
                                </div>
                                <div className="text-right font-mono tabular-nums text-slate-700">
                                  {norm.toFixed(1)}%
                                </div>
                              </div>
                            )
                          })}
                        </div>
                      </>
                    ) : (
                      <p className="px-3 py-2.5 text-sm text-slate-600">
                        These rows show every hard rule evaluated before the first failing rule stopped score
                        calculation.
                      </p>
                    )}
                  </div>
                ) : null}
              </div>

              {displayGroups.map((g) => {
                const catOpen = openCategories[g.category] === true
                const maxPts = g.rows.reduce((sum, r) => sum + (r.maxScore ?? 0), 0)
                return (
                  <div key={g.category} className="overflow-hidden rounded-lg border border-slate-200 bg-white">
                    <button
                      type="button"
                      className="flex w-full items-center justify-between gap-2 px-3 py-2.5 text-left hover:bg-slate-50"
                      aria-expanded={catOpen}
                      onClick={() =>
                        setOpenCategories((prev) => ({
                          ...prev,
                          [g.category]: !prev[g.category],
                        }))
                      }
                    >
                      <div className="min-w-0">
                        <h4 className="text-sm font-semibold text-violet-950">{g.category}</h4>
                        <p className="mt-0.5 text-[11px] text-slate-500">
                          {g.rows.length} criteria · {g.categoryScore.toFixed(0)}
                          {maxPts > 0 ? ` / ${maxPts.toFixed(0)}` : ''} pts
                        </p>
                      </div>
                      <span
                        className={`shrink-0 text-slate-400 transition-transform ${catOpen ? 'rotate-90' : ''}`}
                        aria-hidden
                      >
                        ›
                      </span>
                    </button>
                    {catOpen ? (
                      <div className="space-y-2 border-t border-slate-100 p-2">
                        <div className="hidden grid-cols-[minmax(0,1.6fr)_5rem_minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)_4rem_5rem] gap-2 border-b border-slate-100 px-2 pb-2 text-[10px] font-semibold uppercase tracking-wide text-slate-500 lg:grid">
                          <span>Criterion</span>
                          <span>Status</span>
                          <span>Value used</span>
                          <span>Required source</span>
                          <span>Resolved source</span>
                          <span className="text-right">Weight</span>
                          <span className="text-right">Points</span>
                        </div>
                        {g.rows.map((r, idx) => {
                          const missing =
                            (r.row.valueUsed == null || r.row.valueUsed === '') &&
                            r.row.matched === false &&
                            !r.row.skippedDueToDependency
                          return (
                            <div
                              key={`${r.row.rowId ?? r.criterion}-${idx}`}
                              className="rounded-md border border-slate-100 bg-slate-50/60 px-2 py-2"
                            >
                              <div className="grid grid-cols-1 gap-2 lg:grid-cols-[minmax(0,1.6fr)_5rem_minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)_4rem_5rem] lg:items-start">
                                <div className="min-w-0">
                                  <div className="text-[10px] font-semibold uppercase tracking-wide text-slate-500 lg:hidden">
                                    Criterion
                                  </div>
                                  <div className="text-xs font-medium text-slate-900">{r.criterion}</div>
                                  <div className="mt-0.5 text-[10px] text-slate-500">{r.description}</div>
                                </div>
                                <div className="min-w-0">
                                  <span
                                    className={`inline-flex rounded border px-1.5 py-0.5 text-[10px] font-medium ${statusBadgeClass(r.status)}`}
                                  >
                                    {r.statusLabel}
                                  </span>
                                </div>
                                <div className="min-w-0 break-words font-mono text-[11px] text-slate-800">
                                  {formatSummaryValueDisplay(r.row.parameter ?? r.criterion, r.valueUsed)}
                                </div>
                                <div className="min-w-0 break-words text-[11px] text-slate-600">
                                  {r.row.source ?? '—'}
                                </div>
                                <div className="min-w-0 break-words text-[11px] text-slate-600" title={r.valueSource}>
                                  {r.valueSource}
                                </div>
                                <div className="min-w-0 font-mono text-[11px] tabular-nums text-slate-700 lg:text-right">
                                  {r.row.hardRule ? '—' : r.weightPercent > 0 ? `${r.weightPercent.toFixed(1)}%` : '—'}
                                </div>
                                <div className="min-w-0 font-mono text-[11px] tabular-nums text-slate-900 lg:text-right">
                                  {r.row.hardRule
                                    ? 'Not scored'
                                    : r.pointsEarned != null && r.maxScore != null
                                      ? `${r.pointsEarned} / ${r.maxScore}`
                                      : '—'}
                                </div>
                              </div>
                              <ParameterDerivationDetails
                                formulaBreakdown={r.row.formulaBreakdown}
                                dependencyOutcome={r.row.dependencyOutcome}
                                skippedDueToDependency={r.row.skippedDueToDependency}
                              />
                              {missing && onOpenManualInput ? (
                                <div className="mt-2 border-t border-slate-100 pt-2 text-[11px]">
                                  <button
                                    type="button"
                                    onClick={() => onOpenManualInput(r.row.parameter)}
                                    className="text-indigo-700 underline hover:text-indigo-900"
                                  >
                                    Add manual input
                                  </button>
                                  <span className="ml-1 text-slate-500">
                                    Or upload on Documents, then link evidence in manual inputs.
                                  </span>
                                </div>
                              ) : null}
                            </div>
                          )
                        })}
                      </div>
                    ) : null}
                  </div>
                )
              })}
            </div>
          ) : null}

          {rawEntries.length > 0 ? (
            <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
              <button
                type="button"
                className="flex w-full items-center justify-between gap-2 px-3 py-2.5 text-left text-sm hover:bg-slate-50"
                aria-expanded={rawOpen}
                onClick={() => setRawOpen((v) => !v)}
              >
                <span className="font-medium text-slate-800">
                  Raw parameter values
                  <span className="ml-2 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-normal text-slate-600">
                    {rawEntries.length}
                  </span>
                </span>
                <span className={`text-slate-400 transition-transform ${rawOpen ? 'rotate-90' : ''}`} aria-hidden>
                  ›
                </span>
              </button>
              {rawOpen ? (
                <dl className="max-h-64 overflow-y-auto border-t border-slate-100 px-3 py-2 text-xs sm:grid sm:grid-cols-2 sm:gap-x-4">
                  {rawEntries.map(([k, v]) => (
                    <div key={k} className="border-b border-slate-50 py-1.5 sm:border-0">
                      <dt className="text-slate-500">{k.replace(/_/g, ' ').toLowerCase()}</dt>
                      <dd className="font-mono text-slate-900">{formatSummaryValueDisplay(k, String(v))}</dd>
                    </div>
                  ))}
                </dl>
              ) : null}
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
