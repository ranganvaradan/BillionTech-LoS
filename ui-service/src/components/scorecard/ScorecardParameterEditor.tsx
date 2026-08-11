import { useState } from 'react'
import type { ScorecardParameterDef, ScorecardParamOption, ScorecardRow } from '@/api/scorecards'
import { DependencyConditionsEditor } from '@/components/scorecard/DependencyConditionsEditor'
import { EditorModal } from '@/components/scorecard/EditorModal'
import { ScorecardConditionEditor } from '@/components/scorecard/ScorecardConditionEditor'
import { FormulaEditor, type FormulaDefinition } from '@/components/scorecard/FormulaEditor'
import {
  defaultParameterForSource,
  isKnownParameter,
  paramDef,
  parametersForSource,
  scorecardSourceOptionsForLoanProduct,
  sourceDef,
  type ScorecardParamDef,
} from '@/lib/credit/scorecardConfig'
import { defaultConditionForParam } from '@/lib/credit/scorecardCondition'

const CUSTOM_PARAM = '__custom__'
const MATCH_OPTION = 'MATCH_OPTION'

type Props = {
  rows: ScorecardRow[]
  onChange: (rows: ScorecardRow[]) => void
  parameterDefs: Record<string, ScorecardParameterDef>
  onParameterDefsChange: (defs: Record<string, ScorecardParameterDef>) => void
  showAttachment?: boolean
  loanProduct?: string
}

function effectiveParamDef(source: string, row: ScorecardRow): ScorecardParamDef | undefined {
  const base = paramDef(source, row.parameter)
  const inputType = row.inputType
  if (inputType === 'dropdown') {
    return {
      value: row.parameter || 'custom',
      label: base?.label ?? row.parameter,
      type: 'enum',
      manualKey: row.parameter,
      enumOptions: (row.options ?? []).map((o) => ({ value: o.value, label: o.label })),
    }
  }
  if (inputType === 'text') {
    return {
      value: row.parameter || 'custom',
      label: base?.label ?? row.parameter,
      type: 'text',
      manualKey: row.parameter,
    }
  }
  return base
}

function scoreSummary(row: ScorecardRow, optionScored: boolean): string {
  if (optionScored) {
    const scores = (row.options ?? []).map((o) => o.score)
    if (scores.length === 0) return '—'
    const min = Math.min(...scores)
    const max = Math.max(...scores)
    return min === max ? String(min) : `${min}–${max}`
  }
  return String(row.score ?? 0)
}

/** Rebuild parameterDefs map from custom OTHER / COMPUTED rows (for persistence). */
export function buildParameterDefsFromRows(rows: ScorecardRow[]): Record<string, ScorecardParameterDef> {
  const out: Record<string, ScorecardParameterDef> = {}
  for (const row of rows) {
    const name = row.parameter?.trim()
    if (!name) continue
    if (row.source === 'COMPUTED') {
      const def: ScorecardParameterDef = { inputType: 'formula' }
      const formula = row.formula
      if (formula && Array.isArray(formula.operands) && formula.operands.length > 0) {
        def.formula = formula
      }
      out[name] = def
      continue
    }
    if (!sourceDef(row.source)?.allowCustomParameter) continue
    if (isKnownParameter(row.source, name)) continue
    const inputType = row.inputType ?? 'number'
    const def: ScorecardParameterDef = { inputType }
    if (inputType === 'dropdown' && row.options?.length) def.options = row.options
    else if (inputType === 'dropdown' && !row.options?.length) def.options = [{ value: 'OPTION_1', label: 'Option 1', score: 0 }]
    if (inputType === 'formula' && row.formula) {
      def.formula = row.formula
    }
    out[name] = def
  }
  return out
}

export function ScorecardParameterEditor({
  rows,
  onChange,
  parameterDefs,
  onParameterDefsChange,
  showAttachment = true,
  loanProduct = '',
}: Props) {
  const [formulaRowId, setFormulaRowId] = useState<string | null>(null)
  const [dependencyRowId, setDependencyRowId] = useState<string | null>(null)
  const [expandedRowIds, setExpandedRowIds] = useState<Set<string>>(() => new Set())
  const sourceOptions = scorecardSourceOptionsForLoanProduct(loanProduct || 'PERSONAL_LOAN')

  function syncDefs(nextRows: ScorecardRow[]) {
    onParameterDefsChange(buildParameterDefsFromRows(nextRows))
  }

  function updateRows(next: ScorecardRow[]) {
    onChange(next)
    syncDefs(next)
  }

  function updateRow(index: number, patch: Partial<ScorecardRow>) {
    const next = rows.map((r, i) => (i === index ? { ...r, ...patch } : r))
    updateRows(next)
  }

  function onSourceChange(index: number, source: string) {
    const param = defaultParameterForSource(source)
    const pDef = paramDef(source, param)
    updateRow(index, {
      source,
      parameter: param,
      condition: defaultConditionForParam(pDef),
      inputType: undefined,
      options: undefined,
    })
  }

  function onParameterChange(index: number, source: string, raw: string) {
    if (raw === CUSTOM_PARAM) {
      updateRow(index, {
        parameter: '',
        condition: 'GTE:0',
        inputType: 'number',
        options: undefined,
      })
      return
    }
    const pDef = paramDef(source, raw)
    updateRow(index, {
      parameter: raw,
      condition: defaultConditionForParam(pDef),
      inputType: undefined,
      options: undefined,
    })
  }

  function onCustomInputTypeChange(index: number, inputType: ScorecardParameterDef['inputType']) {
    const row = rows[index]
    if (inputType === 'dropdown') {
      updateRow(index, {
        inputType,
        options: row.options?.length ? row.options : [{ value: 'OPTION_1', label: 'Option 1', score: 0 }],
        condition: MATCH_OPTION,
        score: 0,
      })
      return
    }
    if (inputType === 'text') {
      updateRow(index, {
        inputType,
        options: undefined,
        condition: 'EQ:',
        score: row.score || 20,
      })
      return
    }
    updateRow(index, {
      inputType: 'number',
      options: undefined,
      condition: 'GTE:0',
      score: row.score || 20,
    })
  }

  function updateOptions(index: number, options: ScorecardParamOption[]) {
    updateRow(index, { options })
  }

  function resolvedFormula(row: ScorecardRow): FormulaDefinition {
    const fromRow = row.formula
    const fromDefs = row.parameter ? parameterDefs[row.parameter]?.formula : undefined
    const formula = fromRow ?? fromDefs
    if (formula && Array.isArray(formula.operands) && formula.operands.length > 0) {
      return formula
    }
    return {
      expression: '',
      operands: [
        { parameter: '', source: 'BUREAU' },
        { parameter: '', source: 'BUREAU' },
      ],
    }
  }

  function updateComputedRow(index: number, patch: Partial<ScorecardRow>) {
    const next: ScorecardRow[] = rows.map((r, i) =>
      i === index ? { ...r, ...patch, inputType: 'formula' as const } : r,
    )
    updateRows(next)
  }

  const activeFormulaRow = rows.find((r) => r.id === formulaRowId) ?? null
  const activeDependencyRow = rows.find((r) => r.id === dependencyRowId) ?? null

  return (
    <div className="space-y-3">
      <div className="rounded-lg border border-slate-200 bg-slate-50/70 px-3 py-2 text-xs text-slate-600">
        For <strong>Other (manual)</strong> custom parameters, choose <strong>Number</strong>, <strong>Text</strong>,
        or <strong>Dropdown</strong>. For <strong>Computed (formula)</strong>, define a derived metric from existing
        parameters and then apply the scoring condition below it.
      </div>

      {rows.length === 0 ? (
        <p className="rounded-lg border border-dashed border-slate-200 bg-white px-4 py-6 text-center text-sm text-slate-500">
          No parameters yet. Add one below.
        </p>
      ) : (
        <ul className="space-y-2">
          {rows.map((row, i) => {
            const src = row.source
            const params = parametersForSource(src)
            const allowCustom = sourceDef(src)?.allowCustomParameter
            const known = isKnownParameter(src, row.parameter)
            const selectValue = known || !row.parameter ? row.parameter || CUSTOM_PARAM : CUSTOM_PARAM
            const isCustomMode = Boolean(allowCustom && selectValue === CUSTOM_PARAM)
            const isComputed = src === 'COMPUTED'
            const inputType = isComputed
              ? ('formula' as ScorecardParameterDef['inputType'])
              : row.inputType ?? (isCustomMode ? 'number' : undefined)
            const optionScored = isCustomMode && inputType === 'dropdown'
            const pDef = effectiveParamDef(src, row)
            const dependencyCount = row.dependsOn?.conditions?.length ?? 0
            const expanded = expandedRowIds.has(row.id)
            const parameterLabel = pDef?.label ?? (row.parameter || 'Unnamed parameter')
            const sourceLabel = sourceDef(src)?.label ?? src
            const pointsLabel = scoreSummary(row, optionScored)

            return (
              <li
                key={row.id}
                className={[
                  'overflow-hidden rounded-xl border bg-white shadow-sm transition-shadow',
                  expanded ? 'border-sky-300 shadow-md ring-1 ring-sky-100' : 'border-slate-200 hover:border-slate-300',
                ].join(' ')}
              >
                <button
                  type="button"
                  className="flex w-full items-center gap-3 px-3 py-3 text-left sm:px-4"
                  aria-expanded={expanded}
                  onClick={() =>
                    setExpandedRowIds((prev) => {
                      const next = new Set(prev)
                      if (expanded) next.delete(row.id)
                      else next.add(row.id)
                      return next
                    })
                  }
                >
                  <span
                    className={[
                      'flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-xs font-bold',
                      expanded ? 'bg-sky-100 text-sky-800' : 'bg-slate-100 text-slate-600',
                    ].join(' ')}
                    aria-hidden="true"
                  >
                    {i + 1}
                  </span>

                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-semibold text-slate-900">{parameterLabel}</span>
                    <span className="mt-0.5 block truncate text-xs text-slate-500">
                      {sourceLabel}
                      {row.parameter ? ` · ${row.parameter}` : ''}
                      {dependencyCount > 0 ? ` · ${dependencyCount} dep.` : ''}
                    </span>
                  </span>

                  <span className="hidden shrink-0 items-center gap-2 sm:flex">
                    <span className="rounded-md border border-emerald-200 bg-emerald-50/80 px-2.5 py-1 text-center">
                      <span className="block text-[9px] font-semibold uppercase tracking-wide text-emerald-700/80">
                        Points
                      </span>
                      <span className="block text-sm font-semibold tabular-nums text-emerald-900">
                        {optionScored ? pointsLabel : row.score}
                      </span>
                    </span>
                  </span>

                  <span className="flex shrink-0 flex-col items-end gap-0.5 sm:hidden">
                    <span className="text-[10px] text-emerald-700">
                      Pts <strong>{optionScored ? pointsLabel : row.score}</strong>
                    </span>
                  </span>

                  <span
                    className={`shrink-0 text-slate-400 transition-transform ${expanded ? 'rotate-90' : ''}`}
                    aria-hidden="true"
                  >
                    ›
                  </span>
                </button>

                {expanded ? (
                  <div className="space-y-3 border-t border-slate-200 bg-slate-50/60 px-3 py-3 sm:px-4">
                    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                      <label className="block text-xs font-medium text-slate-700">
                        Source
                        <select
                          className="bt-input bt-input-sm mt-1 w-full"
                          value={src}
                          onChange={(e) => onSourceChange(i, e.target.value)}
                        >
                          {sourceOptions.map((s) => (
                            <option key={s.value} value={s.value}>
                              {s.label}
                            </option>
                          ))}
                        </select>
                      </label>

                      <label className="block text-xs font-medium text-slate-700 sm:col-span-1 lg:col-span-2">
                        Parameter
                        <select
                          className="bt-input bt-input-sm mt-1 w-full"
                          value={selectValue}
                          onChange={(e) => onParameterChange(i, src, e.target.value)}
                        >
                          {params.map((p) => (
                            <option key={p.value} value={p.value}>
                              {p.label}
                            </option>
                          ))}
                          {allowCustom ? <option value={CUSTOM_PARAM}>— Custom parameter —</option> : null}
                        </select>
                      </label>

                      {isCustomMode && !isComputed ? (
                        <>
                          <label className="block text-xs font-medium text-slate-700 sm:col-span-2">
                            Parameter code
                            <input
                              className="bt-input bt-input-sm mt-1 w-full font-mono text-xs"
                              value={row.parameter}
                              onChange={(e) => updateRow(i, { parameter: e.target.value.trim() })}
                              placeholder="Parameter code (e.g. OccupationTier)"
                            />
                          </label>
                          <label className="block text-xs font-medium text-slate-700">
                            Input type
                            <select
                              className="bt-input bt-input-sm mt-1 w-full border-amber-300 bg-amber-50"
                              value={inputType ?? 'number'}
                              onChange={(e) =>
                                onCustomInputTypeChange(i, e.target.value as ScorecardParameterDef['inputType'])
                              }
                            >
                              <option value="number">Number</option>
                              <option value="text">Text</option>
                              <option value="dropdown">Dropdown</option>
                            </select>
                          </label>
                        </>
                      ) : null}

                      {/* SCORECARD-SAFETY-FOUNDATION-1: weight is legacy metadata only — not used in scoring */}
                      <p className="text-[11px] text-slate-500 sm:col-span-2">
                        Scoring uses exclusive-band points only (earned ÷ max × 100). Stored weight is non-scoring metadata.
                      </p>

                      <label className="block text-xs font-medium text-slate-700">
                        Score (points)
                        {optionScored ? (
                          <span className="mt-1 block rounded border border-slate-200 bg-white px-2 py-2 text-xs text-slate-500">
                            From dropdown options
                          </span>
                        ) : (
                          <input
                            type="number"
                            className="bt-input mt-1 w-full"
                            value={row.score}
                            onChange={(e) => updateRow(i, { score: Number(e.target.value) || 0 })}
                          />
                        )}
                      </label>

                      {showAttachment ? (
                        <label className="block text-xs font-medium text-slate-700">
                          Attachment
                          <input
                            className="bt-input bt-input-sm mt-1 w-full"
                            value={row.attachment ?? ''}
                            onChange={(e) => updateRow(i, { attachment: e.target.value })}
                            placeholder="e.g. BUREAU_REPORT"
                          />
                        </label>
                      ) : null}
                    </div>

                    <div className="rounded-lg border border-slate-200 bg-white p-3 shadow-sm">
                      <div className="mb-2 text-[11px] font-semibold uppercase tracking-wide text-slate-500">
                        Condition / options
                      </div>
                      {isComputed ? (
                        <div className="space-y-3">
                          <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                            <div className="flex flex-wrap items-start justify-between gap-3">
                              <div className="min-w-0 flex-1">
                                <div className="mb-1 text-[11px] font-medium text-slate-500">Computed formula</div>
                                <div className="font-mono text-xs text-slate-800">
                                  {resolvedFormula(row).expression || 'No formula defined yet'}
                                </div>
                              </div>
                              <button
                                type="button"
                                className="rounded border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
                                onClick={() => setFormulaRowId(row.id)}
                              >
                                Edit formula
                              </button>
                            </div>
                          </div>
                          <ScorecardConditionEditor
                            value={row.condition}
                            onChange={(condition) => updateRow(i, { condition })}
                            paramDef={{
                              value: row.parameter || 'computed',
                              label: row.parameter || 'Computed',
                              type: 'number',
                            }}
                            loanProduct={loanProduct}
                          />
                        </div>
                      ) : optionScored ? (
                        <div className="space-y-2 rounded border border-amber-200 bg-amber-50/50 p-2">
                          <p className="text-[10px] text-amber-900">
                            Score comes from the matched option (saved as MATCH_OPTION).
                          </p>
                          {(row.options ?? []).map((opt, idx) => (
                            <div key={idx} className="flex flex-wrap items-end gap-1">
                              <input
                                className="bt-input bt-input-sm w-24"
                                placeholder="Label"
                                value={opt.label}
                                onChange={(e) => {
                                  const options = [...(row.options ?? [])]
                                  options[idx] = { ...opt, label: e.target.value }
                                  updateOptions(i, options)
                                }}
                              />
                              <input
                                className="bt-input bt-input-sm w-24 font-mono text-xs"
                                placeholder="Value"
                                value={opt.value}
                                onChange={(e) => {
                                  const options = [...(row.options ?? [])]
                                  options[idx] = { ...opt, value: e.target.value }
                                  updateOptions(i, options)
                                }}
                              />
                              <input
                                type="number"
                                className="bt-input bt-input-sm w-16"
                                placeholder="Score"
                                title="Score for this option"
                                value={opt.score}
                                onChange={(e) => {
                                  const options = [...(row.options ?? [])]
                                  options[idx] = {
                                    ...opt,
                                    score: Number.parseInt(e.target.value, 10) || 0,
                                  }
                                  updateOptions(i, options)
                                }}
                              />
                              <button
                                type="button"
                                className="text-[10px] text-rose-700"
                                onClick={() =>
                                  updateOptions(
                                    i,
                                    (row.options ?? []).filter((_, j) => j !== idx),
                                  )
                                }
                              >
                                Remove
                              </button>
                            </div>
                          ))}
                          <button
                            type="button"
                            className="rounded border border-slate-300 bg-white px-2 py-0.5 text-[10px]"
                            onClick={() =>
                              updateOptions(i, [
                                ...(row.options ?? []),
                                {
                                  value: `OPTION_${(row.options?.length ?? 0) + 1}`,
                                  label: 'New option',
                                  score: 0,
                                },
                              ])
                            }
                          >
                            Add option
                          </button>
                        </div>
                      ) : (
                        <ScorecardConditionEditor
                          value={row.condition}
                          onChange={(condition) => updateRow(i, { condition })}
                          paramDef={pDef}
                          loanProduct={loanProduct}
                        />
                      )}
                    </div>

                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <button
                          type="button"
                          className="rounded border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
                          onClick={() => setDependencyRowId(row.id)}
                        >
                          Dependencies
                          {dependencyCount > 0 ? ` (${dependencyCount})` : ''}
                        </button>
                        <span className="text-[11px] text-slate-500">
                          {dependencyCount > 0
                            ? 'Only score when dependencies match'
                            : 'Always evaluate unless dependencies are added'}
                        </span>
                      </div>
                      <button
                        type="button"
                        className="rounded border border-rose-200 bg-white px-2 py-1 text-xs font-medium text-rose-700 hover:bg-rose-50"
                        onClick={() => updateRows(rows.filter((_, j) => j !== i))}
                      >
                        Remove parameter
                      </button>
                    </div>
                  </div>
                ) : null}
              </li>
            )
          })}
        </ul>
      )}

      <EditorModal
        open={activeFormulaRow != null}
        title="Edit Computed Formula"
        description="Define the source fields and operators used to derive this advanced parameter."
        onClose={() => setFormulaRowId(null)}
      >
        {activeFormulaRow ? (
          <div className="space-y-4">
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-slate-700">Computed parameter code</span>
              <input
                className="bt-input w-full font-mono text-sm"
                value={activeFormulaRow.parameter}
                onChange={(e) => {
                  const idx = rows.findIndex((r) => r.id === activeFormulaRow.id)
                  if (idx >= 0) updateComputedRow(idx, { parameter: e.target.value.trim() })
                }}
                placeholder="e.g. PAT_TURNOVER_RATIO"
              />
            </label>
            <FormulaEditor
              formula={resolvedFormula(activeFormulaRow)}
              onChange={(formula) => {
                const idx = rows.findIndex((r) => r.id === activeFormulaRow.id)
                if (idx >= 0) updateComputedRow(idx, { formula, inputType: 'formula' })
              }}
              loanProduct={loanProduct}
            />
          </div>
        ) : null}
      </EditorModal>
      <EditorModal
        open={activeDependencyRow != null}
        title="Dependency Conditions"
        description="Only evaluate this rule row when these prerequisite conditions match."
        onClose={() => setDependencyRowId(null)}
      >
        {activeDependencyRow ? (
          <DependencyConditionsEditor
            value={activeDependencyRow.dependsOn}
            onChange={(dependsOn) => {
              const idx = rows.findIndex((r) => r.id === activeDependencyRow.id)
              if (idx >= 0) updateRow(idx, { dependsOn })
            }}
            loanProduct={loanProduct}
          />
        ) : null}
      </EditorModal>
    </div>
  )
}
