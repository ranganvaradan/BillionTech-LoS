import type { DependencyCondition, DependencyGroup } from '@/api/scorecards'
import { ScorecardConditionEditor } from '@/components/scorecard/ScorecardConditionEditor'
import { defaultConditionForParam } from '@/lib/credit/scorecardCondition'
import { defaultParameterForSource, paramDef, parametersForSourceWithCurrent, scorecardSourceOptionsForLoanProduct } from '@/lib/credit/scorecardConfig'

function emptyCondition(): DependencyCondition {
  const source = 'BUREAU'
  const parameter = defaultParameterForSource(source)
  return { source, parameter, condition: defaultConditionForParam(paramDef(source, parameter)) }
}

export function DependencyConditionsEditor({
  value,
  onChange,
  loanProduct = '',
}: {
  value?: DependencyGroup
  onChange: (next?: DependencyGroup) => void
  loanProduct?: string
}) {
  const current: DependencyGroup = value?.conditions?.length
    ? value
    : { logic: 'ALL', conditions: [emptyCondition()] }
  const sourceOptions = scorecardSourceOptionsForLoanProduct(loanProduct || 'PERSONAL_LOAN').filter((s) => s.value !== 'COMPUTED')

  function updateCondition(index: number, patch: Partial<DependencyCondition>) {
    const conditions = current.conditions.map((c, i) => (i === index ? { ...c, ...patch } : c))
    onChange({ logic: current.logic ?? 'ALL', conditions })
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-3">
        <label className="text-sm text-slate-700">
          Logic
          <select
            className="bt-input bt-input-sm ml-2"
            value={current.logic ?? 'ALL'}
            onChange={(e) => onChange({ ...current, logic: e.target.value as 'ALL' | 'ANY' })}
          >
            <option value="ALL">All conditions must match</option>
            <option value="ANY">Any one condition can match</option>
          </select>
        </label>
        <button
          type="button"
          className="rounded border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
          onClick={() => onChange({ ...current, conditions: [...current.conditions, emptyCondition()] })}
        >
          + Add dependency
        </button>
        <button
          type="button"
          className="rounded border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
          onClick={() => onChange(undefined)}
        >
          Clear dependencies
        </button>
      </div>
      {current.conditions.map((dep, idx) => {
        const paramOptions = parametersForSourceWithCurrent(dep.source, dep.parameter)
        const pDef = paramDef(dep.source, dep.parameter)
        return (
          <div key={idx} className="rounded-lg border border-slate-200 bg-white p-3 shadow-sm">
            <div className="grid gap-3 lg:grid-cols-[12rem_1fr]">
              <div className="space-y-3">
                <label className="block">
                  <span className="mb-1 block text-[11px] font-medium text-slate-500">Source</span>
                  <select
                    className="bt-input bt-input-sm w-full"
                    value={dep.source}
                    onChange={(e) => {
                      const source = e.target.value
                      const parameter = defaultParameterForSource(source)
                      updateCondition(idx, {
                        source,
                        parameter,
                        condition: defaultConditionForParam(paramDef(source, parameter)),
                      })
                    }}
                  >
                    {sourceOptions.map((s) => (
                      <option key={s.value} value={s.value}>{s.label}</option>
                    ))}
                  </select>
                </label>
                <label className="block">
                  <span className="mb-1 block text-[11px] font-medium text-slate-500">Parameter</span>
                  <select
                    className="bt-input bt-input-sm w-full"
                    value={dep.parameter}
                    onChange={(e) =>
                      updateCondition(idx, {
                        parameter: e.target.value,
                        condition: defaultConditionForParam(paramDef(dep.source, e.target.value)),
                      })
                    }
                  >
                    {paramOptions.map((p) => (
                      <option key={p.value} value={p.value}>{p.label}</option>
                    ))}
                  </select>
                </label>
              </div>
              <div className="space-y-2">
                <div className="text-[11px] font-medium text-slate-500">Condition</div>
                <ScorecardConditionEditor
                  value={dep.condition}
                  onChange={(condition) => updateCondition(idx, { condition })}
                  paramDef={pDef}
                  loanProduct={loanProduct}
                />
                {current.conditions.length > 1 ? (
                  <button
                    type="button"
                    className="text-xs font-medium text-rose-700 hover:text-rose-900"
                    onClick={() =>
                      onChange({ ...current, conditions: current.conditions.filter((_, i) => i !== idx) })
                    }
                  >
                    Remove this dependency
                  </button>
                ) : null}
              </div>
            </div>
          </div>
        )
      })}
    </div>
  )
}
