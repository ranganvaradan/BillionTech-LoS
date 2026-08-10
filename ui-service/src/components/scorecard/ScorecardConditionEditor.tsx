import { useId, useMemo } from 'react'
import { AmountPreviewPane } from '@/components/ui/AmountInputHint'
import {
  conditionOpLabel,
  defaultConditionForParam,
  formatCondition,
  isParamRef,
  opsForParamType,
  parseCondition,
  type ConditionOp,
} from '@/lib/credit/scorecardCondition'
import {
  parametersForSource,
  scorecardSourceOptionsForLoanProduct,
  type ScorecardParamDef,
} from '@/lib/credit/scorecardConfig'

type Props = {
  value: string
  onChange: (encoded: string) => void
  paramDef?: ScorecardParamDef
  className?: string
  loanProduct?: string
}

function looksLikeAmount(raw: string | undefined): boolean {
  if (raw == null || raw === '') return false
  const n = Number(String(raw).replace(/,/g, '').trim())
  return Number.isFinite(n) && n >= 1000
}

export function ScorecardConditionEditor({ value, onChange, paramDef, className = '', loanProduct = '' }: Props) {
  const groupId = useId()
  const type = paramDef?.type ?? 'number'
  const parsed = useMemo(() => parseCondition(value) ?? parseCondition(defaultConditionForParam(paramDef)), [value, paramDef])
  const ops = opsForParamType(type)
  const paramRefMode = isParamRef(parsed)

  const sourceOptions = useMemo(
    () => scorecardSourceOptionsForLoanProduct(loanProduct || 'PERSONAL_LOAN').filter((s) => s.value !== 'COMPUTED'),
    [loanProduct],
  )

  function updateOp(op: ConditionOp) {
    if (!parsed) return
    if (paramRefMode && isParamRef(parsed)) {
      onChange(
        formatCondition({
          op: op as Exclude<ConditionOp, 'BETWEEN'>,
          refSource: parsed.refSource,
          refParam: parsed.refParam,
          paramRef: true,
        }),
      )
      return
    }
    if (op === 'BETWEEN') {
      onChange(formatCondition({ op: 'BETWEEN', min: '0', max: '100' }))
      return
    }
    const currentVal = parsed.op === 'BETWEEN' ? '0' : 'value' in parsed ? parsed.value : '0'
    onChange(formatCondition({ op, value: currentVal }))
  }

  function updateValue(newVal: string) {
    if (!parsed || parsed.op === 'BETWEEN') return
    onChange(formatCondition({ op: parsed.op, value: newVal }))
  }

  function updateBetween(min: string, max: string) {
    onChange(formatCondition({ op: 'BETWEEN', min, max }))
  }

  function toggleParamRef(useRef: boolean) {
    const op: Exclude<ConditionOp, 'BETWEEN'> = parsed?.op && parsed.op !== 'BETWEEN' ? parsed.op : 'GTE'
    if (useRef) {
      const firstSource = sourceOptions[0]?.value ?? 'BUREAU'
      const firstParam = parametersForSource(firstSource)[0]?.value ?? ''
      onChange(formatCondition({ op, refSource: firstSource, refParam: firstParam, paramRef: true }))
    } else {
      onChange(formatCondition({ op, value: '0' }))
    }
  }

  function updateRefSource(src: string) {
    if (!parsed || !isParamRef(parsed)) return
    const firstParam = parametersForSource(src)[0]?.value ?? ''
    onChange(formatCondition({ op: parsed.op, refSource: src, refParam: firstParam, paramRef: true }))
  }

  function updateRefParam(param: string) {
    if (!parsed || !isParamRef(parsed)) return
    onChange(formatCondition({ op: parsed.op, refSource: parsed.refSource, refParam: param, paramRef: true }))
  }

  const op = parsed?.op ?? 'GTE'
  const fixedValue =
    parsed && !isParamRef(parsed) && parsed.op !== 'BETWEEN' && 'value' in parsed ? parsed.value : ''
  const betweenMin = parsed?.op === 'BETWEEN' && 'min' in parsed ? parsed.min : ''
  const betweenMax = parsed?.op === 'BETWEEN' && 'max' in parsed ? parsed.max : ''
  const showAmountPreview =
    type === 'number' &&
    !paramRefMode &&
    (op === 'BETWEEN'
      ? looksLikeAmount(betweenMin) || looksLikeAmount(betweenMax)
      : looksLikeAmount(fixedValue))

  return (
    <div className={`space-y-1.5 ${className}`.trim()}>
      <div className="flex flex-wrap items-center gap-3 rounded border border-slate-200 bg-slate-50 px-2.5 py-2">
        <label className="flex items-center gap-1 text-[11px] text-slate-500">
          <input
            type="radio"
            name={`cmp-${groupId}`}
            checked={!paramRefMode}
            onChange={() => toggleParamRef(false)}
          />
          Fixed value
        </label>
        <label className="flex items-center gap-1 text-[11px] text-slate-500">
          <input
            type="radio"
            name={`cmp-${groupId}`}
            checked={paramRefMode}
            onChange={() => toggleParamRef(true)}
          />
          Compare to parameter
        </label>
      </div>

      <div
        className={
          showAmountPreview
            ? 'grid grid-cols-1 gap-2 sm:grid-cols-[minmax(0,1fr)_minmax(9.5rem,12rem)] sm:items-stretch'
            : undefined
        }
      >
        <div className="flex flex-wrap items-center gap-1.5">
          <select
            className="bt-input bt-input-sm min-w-[9rem]"
            value={op}
            onChange={(e) => updateOp(e.target.value as ConditionOp)}
            aria-label="Condition operator"
          >
            {ops.map((o) => (
              <option key={o} value={o}>
                {conditionOpLabel(o, type)}
              </option>
            ))}
          </select>

          {paramRefMode && isParamRef(parsed) ? (
            <>
              <select
                className="bt-input bt-input-sm min-w-[7rem]"
                value={parsed.refSource}
                onChange={(e) => updateRefSource(e.target.value)}
                aria-label="Reference source"
              >
                {sourceOptions.map((s) => (
                  <option key={s.value} value={s.value}>
                    {s.label}
                  </option>
                ))}
              </select>
              <select
                className="bt-input bt-input-sm min-w-[9rem]"
                value={parsed.refParam}
                onChange={(e) => updateRefParam(e.target.value)}
                aria-label="Reference parameter"
              >
                {parametersForSource(parsed.refSource).map((p) => (
                  <option key={p.value} value={p.value}>
                    {p.label}
                  </option>
                ))}
              </select>
            </>
          ) : op === 'BETWEEN' && parsed?.op === 'BETWEEN' && 'min' in parsed ? (
            <>
              <input
                type="number"
                className="bt-input bt-input-sm w-24"
                value={(parsed as { min: string }).min}
                onChange={(e) => updateBetween(e.target.value, (parsed as { max: string }).max)}
                placeholder="Min"
                aria-label="Minimum value"
              />
              <span className="text-xs text-slate-500">and</span>
              <input
                type="number"
                className="bt-input bt-input-sm w-24"
                value={(parsed as { max: string }).max}
                onChange={(e) => updateBetween((parsed as { min: string }).min, e.target.value)}
                placeholder="Max"
                aria-label="Maximum value"
              />
            </>
          ) : type === 'yesno' ? (
            <select
              className="bt-input bt-input-sm min-w-[5rem]"
              value={
                parsed && !isParamRef(parsed) && parsed.op !== 'BETWEEN'
                  ? 'value' in parsed && parsed.value === '0'
                    ? '0'
                    : '1'
                  : '1'
              }
              onChange={(e) => updateValue(e.target.value)}
              aria-label="Yes or no"
            >
              <option value="1">Yes</option>
              <option value="0">No</option>
            </select>
          ) : type === 'enum' && paramDef?.enumOptions?.length ? (
            <select
              className="bt-input bt-input-sm min-w-[6rem]"
              value={
                parsed && !isParamRef(parsed) && parsed.op !== 'BETWEEN' && 'value' in parsed
                  ? parsed.value
                  : paramDef.enumOptions[0].value
              }
              onChange={(e) => updateValue(e.target.value)}
              aria-label="Enum value"
            >
              {paramDef.enumOptions.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          ) : type === 'text' ? (
            <input
              type="text"
              className="bt-input bt-input-sm w-28"
              value={
                parsed && !isParamRef(parsed) && parsed.op !== 'BETWEEN' && 'value' in parsed
                  ? parsed.value
                  : ''
              }
              onChange={(e) => updateValue(e.target.value)}
              placeholder="Text value"
              aria-label="Text value"
            />
          ) : (
            <input
              type="number"
              className="bt-input bt-input-sm w-28"
              value={fixedValue}
              onChange={(e) => updateValue(e.target.value)}
              placeholder="Value"
              aria-label="Threshold value"
            />
          )}
        </div>

        {showAmountPreview ? (
          <AmountPreviewPane
            value={op === 'BETWEEN' ? (looksLikeAmount(betweenMax) ? betweenMax : betweenMin) : fixedValue}
            compact
          />
        ) : null}
      </div>
    </div>
  )
}
