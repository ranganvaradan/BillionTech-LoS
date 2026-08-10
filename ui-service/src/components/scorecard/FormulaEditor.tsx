import { useMemo } from 'react'
import {
  parametersForSource,
  scorecardSourceOptionsForLoanProduct,
  type ScorecardParamDef,
} from '@/lib/credit/scorecardConfig'
import type { FormulaDefinition, FormulaOperand } from '@/api/scorecards'
import { BodmasExpressionPreview } from '@/components/credit/BodmasExpressionPreview'

export type { FormulaDefinition, FormulaOperand }

type Props = {
  formula: FormulaDefinition
  onChange: (formula: FormulaDefinition) => void
  loanProduct?: string
  className?: string
}

const OPERATORS = ['+', '-', '*', '/'] as const
type Operator = (typeof OPERATORS)[number]
const OP_LABELS: Record<Operator, string> = { '+': 'Add (+)', '-': 'Subtract (-)', '*': 'Multiply (*)', '/': 'Divide (/)' }

function buildExpression(operands: FormulaOperand[], operators: string[]): string {
  if (operands.length === 0) return ''
  let expr = operands[0]?.parameter || '?'
  for (let i = 1; i < operands.length; i++) {
    const op = operators[i - 1] ?? '+'
    expr += ` ${op} ${operands[i]?.parameter || '?'}`
  }
  return expr
}

function parseOperators(expression: string, operands: FormulaOperand[]): string[] {
  if (operands.length <= 1) return []
  const ops: string[] = []
  const parts = expression.split(/\b/)
  let opIdx = 0
  for (const part of parts) {
    const t = part.trim()
    if (['+', '-', '*', '/'].includes(t)) {
      ops.push(t)
      opIdx++
      if (opIdx >= operands.length - 1) break
    }
  }
  while (ops.length < operands.length - 1) ops.push('+')
  return ops
}

export function FormulaEditor({ formula, onChange, loanProduct = '', className = '' }: Props) {
  const safeFormula: FormulaDefinition = {
    expression: formula?.expression ?? '',
    operands:
      Array.isArray(formula?.operands) && formula.operands.length > 0
        ? formula.operands
        : [
            { parameter: '', source: 'BUREAU' },
            { parameter: '', source: 'BUREAU' },
          ],
  }
  const sourceOptions = scorecardSourceOptionsForLoanProduct(loanProduct || 'PERSONAL_LOAN').filter(
    (s) => s.value !== 'COMPUTED',
  )

  const operators = useMemo(
    () => parseOperators(safeFormula.expression, safeFormula.operands),
    [safeFormula.expression, safeFormula.operands],
  )

  function updateOperand(idx: number, patch: Partial<FormulaOperand>) {
    const next = [...safeFormula.operands]
    next[idx] = { ...next[idx], ...patch }
    const expr = buildExpression(next, operators)
    onChange({ expression: expr, operands: next })
  }

  function updateOperator(idx: number, op: string) {
    const nextOps = [...operators]
    nextOps[idx] = op
    const expr = buildExpression(safeFormula.operands, nextOps)
    onChange({ expression: expr, operands: safeFormula.operands })
  }

  function addOperand() {
    const nextOps = [...operators, '+']
    const next = [...safeFormula.operands, { parameter: '', source: 'BUREAU' }]
    const expr = buildExpression(next, nextOps)
    onChange({ expression: expr, operands: next })
  }

  function removeOperand(idx: number) {
    if (safeFormula.operands.length <= 2) return
    const next = safeFormula.operands.filter((_, i) => i !== idx)
    const nextOps = operators.filter((_, i) => (idx === 0 ? i !== 0 : i !== idx - 1))
    const expr = buildExpression(next, nextOps)
    onChange({ expression: expr, operands: next })
  }

  return (
    <div className={`rounded-lg border border-slate-200 bg-slate-50/70 p-3 ${className}`.trim()}>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Formula</p>
          <p className="text-xs text-slate-600">Choose source fields and operators to derive this parameter.</p>
        </div>
        <button
          type="button"
          className="rounded border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
          onClick={addOperand}
        >
          + Add operand
        </button>
      </div>
      <div className="space-y-3">
      {safeFormula.operands.map((op, idx) => {
        const params: ScorecardParamDef[] = parametersForSource(op.source)
        return (
          <div key={idx} className="space-y-2">
            {idx > 0 ? (
              <div className="flex items-center gap-2">
                <div className="h-px flex-1 bg-slate-200" />
                <select
                  className="bt-input bt-input-sm w-36 text-center font-mono"
                  value={operators[idx - 1] ?? '+'}
                  onChange={(e) => updateOperator(idx - 1, e.target.value)}
                >
                  {OPERATORS.map((o) => (
                    <option key={o} value={o}>
                      {OP_LABELS[o]}
                    </option>
                  ))}
                </select>
                <div className="h-px flex-1 bg-slate-200" />
              </div>
            ) : null}
            <div className="rounded-lg border border-slate-200 bg-white p-3 shadow-sm">
              <div className="grid gap-3 sm:grid-cols-[minmax(9rem,11rem)_1fr_auto] sm:items-end">
                <label className="block">
                  <span className="mb-1 block text-[11px] font-medium text-slate-500">Source</span>
                  <select
                    className="bt-input bt-input-sm w-full"
                    value={op.source}
                    onChange={(e) => {
                      const src = e.target.value
                      const firstParam = parametersForSource(src)[0]?.value ?? ''
                      updateOperand(idx, { source: src, parameter: firstParam })
                    }}
                  >
                    {sourceOptions.map((s) => (
                      <option key={s.value} value={s.value}>
                        {s.label}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="block">
                  <span className="mb-1 block text-[11px] font-medium text-slate-500">Parameter</span>
                  <select
                    className="bt-input bt-input-sm w-full"
                    value={op.parameter}
                    onChange={(e) => updateOperand(idx, { parameter: e.target.value })}
                  >
                    <option value="">— parameter —</option>
                    {params.map((p) => (
                      <option key={p.value} value={p.value}>
                        {p.label}
                      </option>
                    ))}
                  </select>
                </label>
                {safeFormula.operands.length > 2 ? (
                  <button
                    type="button"
                    className="rounded border border-rose-200 px-2 py-1 text-xs font-medium text-rose-700 hover:bg-rose-50"
                    onClick={() => removeOperand(idx)}
                  >
                    Remove
                  </button>
                ) : <div />}
              </div>
            </div>
          </div>
        )
      })}
      </div>
      <BodmasExpressionPreview
        className="mt-3"
        expression={safeFormula.expression}
        title="Expression preview"
      />
      <label className="mt-3 block">
        <span className="mb-1 block text-[11px] font-medium text-slate-500">Expression override</span>
        <input
          className="bt-input bt-input-sm w-full font-mono"
          value={safeFormula.expression}
          onChange={(e) => onChange({ ...safeFormula, expression: e.target.value })}
          placeholder="e.g. (PAT / ANNUAL_GST_TURNOVER) * 100"
        />
      </label>
    </div>
  )
}
