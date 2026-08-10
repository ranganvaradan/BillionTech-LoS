import { useState } from 'react'
import { describeCondition } from '@/lib/credit/scorecardCondition'
import { BodmasExpressionPreview } from '@/components/credit/BodmasExpressionPreview'

export type FormulaBreakdown = {
  expression?: string
  result?: string | null
  operands?: Array<{
    source?: string
    parameter?: string
    valueUsed?: string | null
  }>
}

export type DependencyOutcome = {
  matched?: boolean
  logic?: 'ALL' | 'ANY' | string
  conditions?: Array<{
    source?: string
    parameter?: string
    condition?: string
    valueUsed?: string | null
    matched?: boolean
  }>
}

export function ParameterDerivationDetails({
  formulaBreakdown,
  dependencyOutcome,
  skippedDueToDependency,
}: {
  formulaBreakdown?: FormulaBreakdown | null
  dependencyOutcome?: DependencyOutcome | null
  skippedDueToDependency?: boolean
}) {
  const [showFormula, setShowFormula] = useState(false)
  const [showDeps, setShowDeps] = useState(false)

  const hasFormula = Boolean(formulaBreakdown?.expression || (formulaBreakdown?.operands?.length ?? 0) > 0)
  const hasDeps = Boolean((dependencyOutcome?.conditions?.length ?? 0) > 0)
  if (!hasFormula && !hasDeps) return null

  return (
    <div className="mt-1.5 space-y-1.5">
      {hasFormula ? (
        <div>
          <button
            type="button"
            className="text-[11px] font-medium text-indigo-700 underline hover:text-indigo-900"
            onClick={() => setShowFormula((v) => !v)}
          >
            {showFormula ? 'Hide formula' : 'View formula derivation'}
          </button>
          {showFormula ? (
            <BodmasExpressionPreview
              className="mt-1"
              compact
              title="Formula derivation"
              expression={formulaBreakdown?.expression}
              operands={formulaBreakdown?.operands}
              result={formulaBreakdown?.result}
            />
          ) : null}
        </div>
      ) : null}

      {hasDeps ? (
        <div>
          <button
            type="button"
            className="text-[11px] font-medium text-slate-700 underline hover:text-slate-900"
            onClick={() => setShowDeps((v) => !v)}
          >
            {showDeps
              ? 'Hide dependencies'
              : skippedDueToDependency
                ? 'Why this was not considered'
                : 'View dependency check'}
          </button>
          {showDeps ? (
            <div
              className={[
                'mt-1 rounded-md border px-2.5 py-2 text-[11px]',
                skippedDueToDependency
                  ? 'border-amber-200 bg-amber-50 text-amber-950'
                  : 'border-emerald-100 bg-emerald-50/70 text-slate-700',
              ].join(' ')}
            >
              <p className="font-medium">
                {skippedDueToDependency
                  ? 'Not considered — dependency conditions were not satisfied.'
                  : 'Considered — dependency conditions were satisfied.'}
              </p>
              <p className="mt-0.5 text-[10px] opacity-80">
                Logic: {dependencyOutcome?.logic === 'ANY' ? 'Any one condition' : 'All conditions'}
              </p>
              <ul className="mt-2 space-y-1.5">
                {(dependencyOutcome?.conditions ?? []).map((c, idx) => (
                  <li key={`${c.parameter}-${idx}`} className="rounded border border-white/70 bg-white/70 px-2 py-1">
                    <div className="flex flex-wrap items-center gap-x-2">
                      <span className="font-medium text-slate-800">{c.parameter}</span>
                      <span className="text-slate-500">[{c.source}]</span>
                      <span
                        className={[
                          'rounded px-1.5 py-0.5 text-[10px] font-medium',
                          c.matched ? 'bg-emerald-100 text-emerald-800' : 'bg-rose-100 text-rose-800',
                        ].join(' ')}
                      >
                        {c.matched ? 'Passed' : 'Failed'}
                      </span>
                    </div>
                    <div className="mt-0.5 text-slate-600">
                      {describeCondition(c.condition ?? '')} · value {c.valueUsed ?? 'missing'}
                    </div>
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
