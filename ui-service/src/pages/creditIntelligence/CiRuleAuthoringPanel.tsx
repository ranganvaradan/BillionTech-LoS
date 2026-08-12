import { useEffect, useMemo, useState } from 'react'
import {
  addPlainEnglishPolicyRule,
  getRuleAuthoringSources,
  previewPolicyRule,
} from '@/api/creditIntelligence'
import { ApiError } from '@/api/http'
import {
  asList,
  asRecord,
  defaultOperator,
  hasVisibleCanonicalValue,
  operatorsForParam,
  toCanonicalBuildValue,
  valueControlOf,
  type ValueControl,
} from '@/lib/ux/ruleAuthoringTypedValue'

type Path = 'menu' | 'build' | 'describe' | 'compound' | 'group'

type CompoundBranch = {
  label?: string
  whenParameterId?: string
  whenParameterName?: string
  whenOperator: string
  whenValue: string | number
  thenParameterId?: string
  thenParameterName?: string
  thenOperator: string
  thenValue: string | number
  thenUnit?: string
}

type GroupCondition = {
  parameterId: string
  parameterName?: string
  operator: string
  value: string | number | boolean
  leftKind?: string
  valueControl?: string
  valueLabel?: string
}

/**
 * POLICY-TYPED-RULE-AUTHORING-1 / POLICY-RULE-EDITOR-ROUNDTRIP-P0 —
 * Build / Describe / Edit with typed values + lossless compound IF editing.
 */
export function CiRuleAuthoringPanel({
  documentId,
  busy,
  setBusy,
  onError,
  onSession,
  replaceRuleId,
  initialText,
  initialExpression,
  initialEditableModel,
  onClose,
}: {
  documentId: string
  busy: boolean
  setBusy: (v: boolean) => void
  onError: (msg: string | null) => void
  onSession: (data: Record<string, unknown>) => void
  replaceRuleId?: string
  initialText?: string
  initialExpression?: Record<string, unknown> | null
  initialEditableModel?: Record<string, unknown> | null
  onClose?: () => void
}) {
  const isCompoundIf = Boolean(
    initialEditableModel?.kind === 'COMPOUND_IF'
      || String(asRecord(initialExpression).op ?? '').toUpperCase() === 'IF'
      || String(initialText ?? '').includes(';'),
  )
  const isCompoundGroup = Boolean(
    initialEditableModel?.kind === 'COMPOUND_GROUP'
      || ['OR', 'AND'].includes(String(asRecord(initialExpression).op ?? '').toUpperCase()),
  )
  const [path, setPath] = useState<Path>(
    replaceRuleId ? (isCompoundIf ? 'compound' : isCompoundGroup ? 'group' : 'build') : 'menu',
  )
  const [sources, setSources] = useState<Record<string, unknown> | null>(null)
  const [source, setSource] = useState('')
  const [parameterId, setParameterId] = useState('')
  const [operator, setOperator] = useState('>=')
  const [value, setValue] = useState('')
  const [durationUnit, setDurationUnit] = useState('Months')
  const [valueMode, setValueMode] = useState<'FIXED' | 'PARAMETER'>('FIXED')
  const [rightParameterId, setRightParameterId] = useState('')
  const [treatment, setTreatment] = useState('Reject')
  const [text, setText] = useState(initialText ?? '')
  const [preview, setPreview] = useState<Record<string, unknown> | null>(null)
  const [feedback, setFeedback] = useState<string | null>(null)
  const seedBranches = asList(initialEditableModel?.branches).map((b) => {
    const r = asRecord(b)
    return {
      label: String(r.label ?? ''),
      whenParameterId: String(r.whenParameterId ?? 'banking.transaction_count.total_3m'),
      whenParameterName: String(r.whenParameterName ?? 'Transaction count'),
      whenOperator: String(r.whenOperator ?? '>'),
      whenValue: (r.whenValue as string | number) ?? 100,
      thenParameterId: String(r.thenParameterId ?? ''),
      thenParameterName: String(r.thenParameterName ?? ''),
      thenOperator: String(r.thenOperator ?? '<='),
      thenValue: (r.thenValue as string | number) ?? 5,
      thenUnit: String(r.thenUnit ?? ''),
    } satisfies CompoundBranch
  })
  const [branches, setBranches] = useState<CompoundBranch[]>(
    seedBranches.length >= 2
      ? seedBranches
      : [
          {
            label: 'Branch 1',
            whenParameterName: 'Transaction count',
            whenOperator: '>',
            whenValue: 100,
            thenParameterName: 'Inward return ratio',
            thenParameterId: 'banking.inward_return.ratio_3m',
            thenOperator: '<=',
            thenValue: 5,
            thenUnit: '%',
          },
          {
            label: 'Branch 2',
            whenParameterName: 'Transaction count',
            whenOperator: '<=',
            whenValue: 100,
            thenParameterName: 'Inward return count',
            thenParameterId: 'banking.inward_return.count_3m',
            thenOperator: '<=',
            thenValue: 5,
            thenUnit: 'count',
          },
        ],
  )
  const seedGroup = asList(initialEditableModel?.conditions).map((c) => {
    const r = asRecord(c)
    return {
      parameterId: String(r.parameterId ?? ''),
      parameterName: String(r.parameterName ?? r.parameterId ?? ''),
      operator: String(r.operator ?? '='),
      value: (r.value as string | number | boolean) ?? '',
      leftKind: r.leftKind ? String(r.leftKind) : undefined,
      valueControl: r.valueControl ? String(r.valueControl) : undefined,
      valueLabel: r.valueLabel ? String(r.valueLabel) : undefined,
    } satisfies GroupCondition
  })
  const [combinator, setCombinator] = useState<'ANY' | 'ALL'>(
    String(initialEditableModel?.combinator ?? 'ANY').toUpperCase() === 'ALL' ? 'ALL' : 'ANY',
  )
  const [groupConditions, setGroupConditions] = useState<GroupCondition[]>(seedGroup)
  const [proposedModel, setProposedModel] = useState<Record<string, unknown> | null>(
    isCompoundGroup ? asRecord(initialEditableModel) : null,
  )

  useEffect(() => {
    void getRuleAuthoringSources()
      .then((d) => setSources(d))
      .catch(() => setSources(null))
  }, [])

  const bySource = asRecord(sources?.bySource)
  const sourceNames = asList(sources?.sources).map(String)
  const paramsForSource = useMemo(
    () => asList(bySource[source]).map(asRecord),
    [bySource, source],
  )
  const selectedParam = useMemo(
    () => paramsForSource.find((p) => String(p.parameterId) === parameterId) ?? null,
    [paramsForSource, parameterId],
  )
  const control: ValueControl = valueControlOf(selectedParam)
  const operators = operatorsForParam(selectedParam, asRecord(sources?.operatorsByType))
  const treatments = asList(sources?.treatments).map(String)
  const treatmentLabel = String(sources?.treatmentLabel ?? 'If rule fails')
  const allowedValues = asList(selectedParam?.allowedValues).map(asRecord)
  const durationUnits = asList(selectedParam?.durationUnits).map(String)
  const showPeriod = Boolean(selectedParam?.showPeriod)
  const supportsParamRef = Boolean(selectedParam?.supportsParameterReference)
  const allParams = useMemo(() => {
    const out: Record<string, unknown>[] = []
    for (const s of sourceNames) {
      for (const p of asList(bySource[s]).map(asRecord)) out.push(p)
    }
    return out
  }, [bySource, sourceNames])

  useEffect(() => {
    if (!parameterId) return
    setOperator(defaultOperator(control))
    setValueMode('FIXED')
    setRightParameterId('')
    if (control === 'BOOLEAN') setValue('Yes')
    else if (control === 'ENUM' && allowedValues[0]) setValue(String(allowedValues[0].value ?? ''))
    else setValue('')
    setPreview(null)
    // eslint-disable-next-line react-hooks/exhaustive-deps -- reset when parameter/control changes
  }, [parameterId, control])

  const buildBody = (): Record<string, unknown> => {
    if (valueMode === 'PARAMETER') {
      return {
        mode: 'BUILD',
        parameterId,
        operator,
        valueMode: 'PARAMETER',
        rightParameterId,
        treatment,
      }
    }
    return {
      mode: 'BUILD',
      parameterId,
      operator,
      value: toCanonicalBuildValue(control, value, durationUnit),
      durationUnit: control === 'DURATION' ? durationUnit : undefined,
      valueMode: 'FIXED',
      treatment,
    }
  }

  const canPreviewBuild = hasVisibleCanonicalValue(control, value, valueMode, rightParameterId)
    && Boolean(parameterId)

  const runPreview = async () => {
    setBusy(true)
    onError(null)
    setFeedback(null)
    try {
      const body =
        path === 'compound'
          ? {
              mode: 'COMPOUND',
              branches,
              treatment,
              ...(replaceRuleId ? { replaceRuleId } : {}),
              ...(initialExpression ? { existingExpression: initialExpression } : {}),
            }
          : path === 'group'
            ? {
                mode: 'COMPOUND_GROUP',
                combinator,
                conditions: groupConditions,
                treatment,
                ...(replaceRuleId ? { replaceRuleId } : {}),
              }
            : path === 'build'
              ? buildBody()
              : {
                  mode: 'DESCRIBE',
                  text,
                  treatment,
                  ...(replaceRuleId ? { replaceRuleId } : {}),
                  ...(proposedModel
                    ? { proposedModel, amendment: text }
                    : {}),
                }
      const data = await previewPolicyRule(documentId, body)
      const p = asRecord(data.preview ?? data)
      setPreview(p)
      if (p.compoundGroup || p.mode === 'COMPOUND_GROUP') {
        const conds = asList(p.conditions).map((c) => {
          const r = asRecord(c)
          return {
            parameterId: String(r.parameterId ?? ''),
            parameterName: String(r.parameterName ?? ''),
            operator: String(r.operator ?? '='),
            value: (r.value as string | number | boolean) ?? '',
            leftKind: r.leftKind ? String(r.leftKind) : undefined,
            valueControl: r.valueControl ? String(r.valueControl) : undefined,
            valueLabel: r.valueLabel ? String(r.valueLabel) : undefined,
          } satisfies GroupCondition
        })
        if (conds.length) setGroupConditions(conds)
        if (p.combinator) setCombinator(String(p.combinator).toUpperCase() === 'ALL' ? 'ALL' : 'ANY')
        setProposedModel({
          kind: 'COMPOUND_GROUP',
          combinator: p.combinator ?? combinator,
          conditions: conds,
        })
      }
      if (p.complete) {
        setFeedback(String(p.message ?? 'Ready to confirm'))
        if (p.parameterId) setParameterId(String(p.parameterId))
        if (p.operator) setOperator(String(p.operator))
        if (p.value != null && p.valueMode !== 'PARAMETER') {
          const vc = String(p.valueControl ?? control)
          if (vc === 'BOOLEAN') setValue(p.value === true || p.value === 'true' ? 'Yes' : 'No')
          else setValue(String(p.value))
        }
        if (p.treatment) setTreatment(String(p.treatment))
        if (p.source) setSource(String(p.source))
        if (p.durationUnit) setDurationUnit(String(p.durationUnit))
        if (p.rightParameterId) {
          setValueMode('PARAMETER')
          setRightParameterId(String(p.rightParameterId))
        }
      } else {
        const unresolved = asList(p.unresolved).map(String)
        setFeedback(
          unresolved.length
            ? `${String(p.message ?? 'Some parts of this rule have not been mapped yet.')} Unresolved: ${unresolved.join(', ')}`
            : String(p.message ?? 'Incomplete'),
        )
      }
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : 'Could not preview rule'
      setFeedback(msg)
      onError(msg)
    } finally {
      setBusy(false)
    }
  }

  const confirm = async () => {
    setBusy(true)
    onError(null)
    setFeedback(null)
    try {
      const p = asRecord(preview)
      const body: Record<string, unknown> =
        path === 'compound'
          ? {
              confirm: true,
              mode: 'COMPOUND',
              branches,
              treatment,
              ...(replaceRuleId ? { replaceRuleId } : {}),
              ...(initialExpression ? { existingExpression: initialExpression } : {}),
            }
          : path === 'group' || p.compoundGroup || p.mode === 'COMPOUND_GROUP'
            ? {
                confirm: true,
                mode: 'COMPOUND_GROUP',
                combinator: p.combinator ?? combinator,
                conditions: asList(p.conditions).length ? p.conditions : groupConditions,
                expression: p.expression,
                text: path === 'describe' ? text : undefined,
                treatment,
                ...(replaceRuleId ? { replaceRuleId } : {}),
              }
            : {
                confirm: true,
                mode: path === 'build' ? 'BUILD' : 'DESCRIBE',
                text: path === 'describe' ? text : undefined,
                parameterId: parameterId || p.parameterId,
                operator: operator || p.operator,
                treatment,
                ...(replaceRuleId ? { replaceRuleId } : {}),
                ...(path === 'build' ? buildBody() : {}),
              }
      // Prefer preview canonical value when describe path filled it
      if (path === 'describe' && !p.compoundGroup && p.value != null) {
        body.value = p.value
        body.parameterId = p.parameterId
        body.operator = p.operator
      }
      const data = await addPlainEnglishPolicyRule(documentId, body)
      onSession(data as Record<string, unknown>)
      setFeedback(String(asRecord(data).message ?? 'Rule saved'))
      setPreview(null)
      setText('')
      setPath('menu')
      onClose?.()
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : 'Could not save rule'
      setFeedback(msg)
      onError(msg)
    } finally {
      setBusy(false)
    }
  }

  const saveUnchanged = async () => {
    if (!replaceRuleId || !initialExpression) return
    setBusy(true)
    onError(null)
    try {
      const data = await addPlainEnglishPolicyRule(documentId, {
        confirm: true,
        mode: 'PRESERVE',
        noChange: true,
        replaceRuleId,
        existingExpression: initialExpression,
      })
      onSession(data as Record<string, unknown>)
      onClose?.()
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : 'Could not save rule'
      setFeedback(msg)
      onError(msg)
    } finally {
      setBusy(false)
    }
  }

  if (path === 'menu') {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-3" data-testid="rule-authoring-menu">
        <p className="text-sm font-medium text-slate-900">Add underwriting rule</p>
        <div className="mt-2 flex flex-wrap gap-2">
          <button type="button" className="bt-btn bt-btn-primary bt-btn-sm" onClick={() => setPath('build')}>
            Build rule
          </button>
          <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" onClick={() => setPath('describe')}>
            Describe rule
          </button>
          {onClose ? (
            <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" onClick={onClose}>
              Cancel
            </button>
          ) : null}
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-3 rounded-lg border border-slate-200 bg-slate-50 p-3" data-testid="rule-authoring-panel">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-slate-900">
          {replaceRuleId
            ? path === 'compound' || path === 'group'
              ? 'Edit compound rule'
              : 'Edit rule'
            : path === 'build'
              ? 'Build rule'
              : path === 'compound' || path === 'group'
                ? 'Build compound rule'
                : 'Describe rule'}
        </h3>
        <div className="flex items-center gap-2">
          {isCompoundIf ? (
            <button
              type="button"
              className="text-xs text-sky-800 hover:underline"
              onClick={() => {
                setPath(path === 'compound' ? 'describe' : 'compound')
                setPreview(null)
                setFeedback(null)
              }}
            >
              Switch to {path === 'compound' ? 'Describe' : 'Build'}
            </button>
          ) : isCompoundGroup || path === 'group' || Boolean(asRecord(preview).compoundGroup) ? (
            <button
              type="button"
              className="text-xs text-sky-800 hover:underline"
              onClick={() => {
                setPath(path === 'group' ? 'describe' : 'group')
                setPreview(null)
                setFeedback(null)
              }}
            >
              Switch to {path === 'group' ? 'Describe' : 'Build'}
            </button>
          ) : (
            <button
              type="button"
              className="text-xs text-sky-800 hover:underline"
              onClick={() => {
                setPath(path === 'build' ? 'describe' : 'build')
                setPreview(null)
                setFeedback(null)
              }}
            >
              Switch to {path === 'build' ? 'Describe' : 'Build'}
            </button>
          )}
          <button
            type="button"
            className="text-xs text-slate-600 hover:underline"
            onClick={() => {
              setPath('menu')
              setPreview(null)
              onClose?.()
            }}
          >
            Close
          </button>
        </div>
      </div>

      {path === 'group' ? (
        <div className="space-y-3 text-sm" data-testid="compound-group-editor">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-slate-600">Match</span>
            <select
              className="rounded border border-slate-300 px-2 py-1"
              value={combinator}
              disabled={busy}
              data-testid="group-combinator"
              onChange={(e) => {
                setCombinator(e.target.value === 'ALL' ? 'ALL' : 'ANY')
                setPreview(null)
              }}
            >
              <option value="ANY">ANY (OR)</option>
              <option value="ALL">ALL (AND)</option>
            </select>
            <span className="text-xs text-slate-500">of these conditions</span>
          </div>
          {groupConditions.map((c, idx) => (
            <div key={idx} className="flex flex-wrap items-center gap-2 rounded border border-slate-200 bg-white px-3 py-2">
              <select
                className="rounded border border-slate-300 px-2 py-1 min-w-[10rem]"
                value={c.parameterId}
                disabled={busy}
                onChange={(e) => {
                  const pid = e.target.value
                  const meta = allParams.find((p) => String(p.parameterId) === pid)
                  const next = [...groupConditions]
                  next[idx] = {
                    ...next[idx],
                    parameterId: pid,
                    parameterName: String(meta?.businessName ?? pid),
                    leftKind: pid === 'bureau.status_ntc' ? 'FACT' : 'METRIC',
                    value: pid === 'bureau.status_ntc' ? true : next[idx].value,
                    operator: pid === 'bureau.status_ntc' ? '=' : next[idx].operator,
                  }
                  setGroupConditions(next)
                  setPreview(null)
                }}
              >
                <option value="bureau.score">Bureau score</option>
                <option value="bureau.status_ntc">Bureau status (NTC)</option>
                {allParams
                  .filter((p) => !['bureau.score', 'bureau.status_ntc'].includes(String(p.parameterId)))
                  .slice(0, 40)
                  .map((p) => (
                    <option key={String(p.parameterId)} value={String(p.parameterId)}>
                      {String(p.businessName)}
                    </option>
                  ))}
              </select>
              <select
                className="rounded border border-slate-300 px-2 py-1"
                value={c.operator}
                disabled={busy}
                onChange={(e) => {
                  const next = [...groupConditions]
                  next[idx] = { ...next[idx], operator: e.target.value }
                  setGroupConditions(next)
                  setPreview(null)
                }}
              >
                <option value="=">=</option>
                <option value="!=">≠</option>
                <option value=">">&gt;</option>
                <option value=">=">≥</option>
                <option value="<">&lt;</option>
                <option value="<=">≤</option>
              </select>
              {c.parameterId === 'bureau.status_ntc' ? (
                <select
                  className="rounded border border-slate-300 px-2 py-1"
                  value={String(c.value)}
                  disabled={busy}
                  onChange={(e) => {
                    const next = [...groupConditions]
                    next[idx] = { ...next[idx], value: e.target.value === 'true', valueLabel: 'NTC' }
                    setGroupConditions(next)
                    setPreview(null)
                  }}
                >
                  <option value="true">NTC</option>
                  <option value="false">Not NTC</option>
                </select>
              ) : (
                <input
                  className="w-24 rounded border border-slate-300 px-2 py-1"
                  value={String(c.value)}
                  disabled={busy}
                  onChange={(e) => {
                    const next = [...groupConditions]
                    const raw = e.target.value
                    const num = Number(raw)
                    next[idx] = { ...next[idx], value: raw === '' || Number.isNaN(num) ? raw : num }
                    setGroupConditions(next)
                    setPreview(null)
                  }}
                />
              )}
              <button
                type="button"
                className="text-xs text-rose-700 hover:underline"
                disabled={busy || groupConditions.length <= 1}
                onClick={() => {
                  setGroupConditions(groupConditions.filter((_, i) => i !== idx))
                  setPreview(null)
                }}
              >
                Remove
              </button>
            </div>
          ))}
          <button
            type="button"
            className="text-xs text-sky-800 hover:underline"
            disabled={busy}
            onClick={() => {
              setGroupConditions([
                ...groupConditions,
                { parameterId: 'bureau.score', parameterName: 'Bureau score', operator: '>=', value: 650 },
              ])
              setPreview(null)
            }}
          >
            Add condition
          </button>
          <label className="block text-sm sm:w-60">
            <span className="text-slate-600">If rule fails</span>
            <select
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              value={treatment}
              onChange={(e) => setTreatment(e.target.value)}
              disabled={busy}
            >
              {(treatments.length ? treatments : ['Reject', 'Manual Review', 'Refer', 'Info']).map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </label>
        </div>
      ) : null}

      {path === 'compound' ? (
        <div className="space-y-3 text-sm" data-testid="compound-rule-editor">
          <p className="text-xs text-slate-500">
            Period: Last 3 months · Source: Bank Statement · Mapped parameters preserved
          </p>
          {branches.map((b, idx) => (
            <div key={idx} className="rounded border border-slate-200 bg-white px-3 py-2 space-y-2">
              <p className="text-xs font-semibold uppercase text-slate-500">{b.label || `Branch ${idx + 1}`}</p>
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-medium">IF {b.whenParameterName || 'Transaction count'}</span>
                <select
                  className="rounded border border-slate-300 px-2 py-1"
                  value={b.whenOperator}
                  disabled={busy}
                  data-testid={`compound-when-op-${idx}`}
                  onChange={(e) => {
                    const next = [...branches]
                    next[idx] = { ...next[idx], whenOperator: e.target.value }
                    if (idx === 0 && next[1]) {
                      const comp =
                        e.target.value === '>'
                          ? '<='
                          : e.target.value === '>='
                            ? '<'
                            : e.target.value === '<'
                              ? '>='
                              : '>'
                      next[1] = { ...next[1], whenOperator: comp }
                    }
                    setBranches(next)
                    setPreview(null)
                  }}
                >
                  <option value=">">&gt;</option>
                  <option value=">=">≥</option>
                  <option value="<">&lt;</option>
                  <option value="<=">≤</option>
                </select>
                <input
                  className="w-20 rounded border border-slate-300 px-2 py-1"
                  value={String(b.whenValue)}
                  disabled={busy}
                  onChange={(e) => {
                    const next = [...branches]
                    next[idx] = { ...next[idx], whenValue: e.target.value }
                    if (next[1] && idx === 0) next[1] = { ...next[1], whenValue: e.target.value }
                    setBranches(next)
                    setPreview(null)
                  }}
                />
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-medium">THEN {b.thenParameterName || 'Parameter'}</span>
                <select
                  className="rounded border border-slate-300 px-2 py-1"
                  value={b.thenOperator}
                  disabled={busy}
                  data-testid={`compound-then-op-${idx}`}
                  onChange={(e) => {
                    const next = [...branches]
                    next[idx] = { ...next[idx], thenOperator: e.target.value }
                    setBranches(next)
                    setPreview(null)
                  }}
                >
                  <option value="<=">≤</option>
                  <option value="<">&lt;</option>
                  <option value=">=">≥</option>
                  <option value=">">&gt;</option>
                </select>
                <input
                  className="w-20 rounded border border-slate-300 px-2 py-1"
                  value={String(b.thenValue)}
                  disabled={busy}
                  onChange={(e) => {
                    const next = [...branches]
                    next[idx] = { ...next[idx], thenValue: e.target.value }
                    setBranches(next)
                    setPreview(null)
                  }}
                />
                {b.thenUnit ? <span className="text-slate-500">{b.thenUnit}</span> : null}
              </div>
            </div>
          ))}
          <label className="block text-sm sm:w-60">
            <span className="text-slate-600">If rule fails</span>
            <select
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              value={treatment}
              onChange={(e) => setTreatment(e.target.value)}
              disabled={busy}
            >
              {(treatments.length ? treatments : ['Reject', 'Manual Review', 'Refer', 'Info']).map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </label>
          {replaceRuleId && initialExpression ? (
            <button
              type="button"
              className="text-xs text-sky-800 underline"
              disabled={busy}
              onClick={() => void saveUnchanged()}
              data-testid="compound-save-unchanged"
            >
              Save without changes (round-trip preserve)
            </button>
          ) : null}
        </div>
      ) : null}

      {path === 'describe' ? (
        <label className="block text-sm">
          <span className="text-slate-600">
            {proposedModel
              ? 'Amend the proposed rule in plain English'
              : 'Describe the rule in plain English'}
          </span>
          <textarea
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm"
            rows={3}
            value={text}
            disabled={busy}
            placeholder={
              proposedModel
                ? 'e.g. I also want to add Bureau Score of -1 and NTC'
                : 'e.g. Bureau Score of -1, NTC and 650 & above only will be allowed'
            }
            onChange={(e) => setText(e.target.value)}
            data-testid="describe-rule-text"
          />
          {isCompoundIf ? (
            <p className="mt-1 text-xs text-amber-800">
              This is a compound rule. Prefer Build to edit branches — free-text rewrite cannot safely keep both branches.
            </p>
          ) : proposedModel ? (
            <p className="mt-1 text-xs text-slate-600">
              Amendments update the current proposed conditions (also add / remove / change / make it…).
            </p>
          ) : null}
        </label>
      ) : path === 'compound' || path === 'group' ? null : (
        <div className="grid gap-2 sm:grid-cols-2">
          <label className="block text-sm">
            <span className="text-slate-600">Source</span>
            <select
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              value={source}
              onChange={(e) => {
                setSource(e.target.value)
                setParameterId('')
              }}
              disabled={busy}
              data-testid="build-source"
            >
              <option value="">Select source</option>
              {sourceNames.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </label>
          <label className="block text-sm">
            <span className="text-slate-600">Parameter</span>
            <select
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              value={parameterId}
              onChange={(e) => setParameterId(e.target.value)}
              disabled={busy || !source}
              data-testid="build-parameter"
            >
              <option value="">Select parameter</option>
              {paramsForSource.map((p) => (
                <option key={String(p.parameterId)} value={String(p.parameterId)}>
                  {String(p.businessName)}
                  {p.kind ? ` (${String(p.kind)})` : ''}
                </option>
              ))}
            </select>
          </label>
          <label className="block text-sm">
            <span className="text-slate-600">Condition</span>
            <select
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              value={operator}
              onChange={(e) => setOperator(e.target.value)}
              disabled={busy}
              data-testid="build-operator"
            >
              {(operators.length ? operators : ['>', '>=', '<', '<=', '=']).map((o) => (
                <option key={o} value={o}>
                  {o}
                </option>
              ))}
            </select>
          </label>

          {supportsParamRef ? (
            <label className="block text-sm">
              <span className="text-slate-600">Value type</span>
              <select
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                value={valueMode}
                onChange={(e) => setValueMode(e.target.value as 'FIXED' | 'PARAMETER')}
                disabled={busy}
                data-testid="build-value-mode"
              >
                <option value="FIXED">Fixed value</option>
                <option value="PARAMETER">Parameter</option>
              </select>
            </label>
          ) : null}

          {valueMode === 'PARAMETER' ? (
            <label className="block text-sm sm:col-span-2">
              <span className="text-slate-600">Right parameter</span>
              <select
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                value={rightParameterId}
                onChange={(e) => setRightParameterId(e.target.value)}
                disabled={busy}
                data-testid="build-right-parameter"
              >
                <option value="">Select parameter</option>
                {allParams
                  .filter((p) => String(p.parameterId) !== parameterId)
                  .map((p) => (
                    <option key={String(p.parameterId)} value={String(p.parameterId)}>
                      {String(p.businessName)} · {String(p.source)}
                    </option>
                  ))}
              </select>
            </label>
          ) : control === 'BOOLEAN' ? (
            <label className="block text-sm">
              <span className="text-slate-600">Value</span>
              <select
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                value={value}
                onChange={(e) => setValue(e.target.value)}
                disabled={busy}
                data-testid="build-value"
              >
                <option value="Yes">Yes</option>
                <option value="No">No</option>
              </select>
            </label>
          ) : control === 'ENUM' ? (
            <label className="block text-sm">
              <span className="text-slate-600">Value</span>
              <select
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                value={value}
                onChange={(e) => setValue(e.target.value)}
                disabled={busy}
                data-testid="build-value"
              >
                <option value="">Select value</option>
                {allowedValues.map((opt) => (
                  <option key={String(opt.value)} value={String(opt.value)}>
                    {String(opt.label ?? opt.value)}
                  </option>
                ))}
              </select>
            </label>
          ) : control === 'DURATION' ? (
            <>
              <label className="block text-sm">
                <span className="text-slate-600">Value</span>
                <input
                  type="number"
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                  value={value}
                  onChange={(e) => setValue(e.target.value)}
                  disabled={busy}
                  data-testid="build-value"
                />
              </label>
              <label className="block text-sm">
                <span className="text-slate-600">Unit</span>
                <select
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                  value={durationUnit}
                  onChange={(e) => setDurationUnit(e.target.value)}
                  disabled={busy}
                  data-testid="build-duration-unit"
                >
                  {(durationUnits.length ? durationUnits : ['Months', 'Years']).map((u) => (
                    <option key={u} value={u}>
                      {u}
                    </option>
                  ))}
                </select>
              </label>
            </>
          ) : control === 'PERCENTAGE' ? (
            <label className="block text-sm">
              <span className="text-slate-600">Value (%)</span>
              <div className="mt-1 flex items-center gap-1">
                <input
                  type="number"
                  min={0}
                  max={100}
                  className="w-full rounded border border-slate-300 px-3 py-2"
                  value={value}
                  onChange={(e) => setValue(e.target.value)}
                  disabled={busy}
                  data-testid="build-value"
                />
                <span className="text-slate-600">%</span>
              </div>
            </label>
          ) : control === 'MONEY' ? (
            <label className="block text-sm">
              <span className="text-slate-600">Value (₹)</span>
              <input
                type="text"
                inputMode="decimal"
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                value={value}
                placeholder="e.g. 1000000 or 10,00,000"
                onChange={(e) => setValue(e.target.value)}
                disabled={busy}
                data-testid="build-value"
              />
            </label>
          ) : control === 'DATE' ? (
            <label className="block text-sm">
              <span className="text-slate-600">Value</span>
              <input
                type="date"
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                value={value}
                onChange={(e) => setValue(e.target.value)}
                disabled={busy}
                data-testid="build-value"
              />
            </label>
          ) : (
            <label className="block text-sm">
              <span className="text-slate-600">Value</span>
              <input
                type={control === 'INTEGER' || control === 'NUMBER' ? 'number' : 'text'}
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                value={value}
                onChange={(e) => setValue(e.target.value)}
                disabled={busy}
                data-testid="build-value"
              />
            </label>
          )}

          {showPeriod ? (
            <label className="block text-sm">
              <span className="text-slate-600">Period</span>
              <input
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                value={String(selectedParam?.period ?? '')}
                readOnly
                disabled
              />
            </label>
          ) : null}

          <label className="block text-sm">
            <span className="text-slate-600">{treatmentLabel}</span>
            <select
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              value={treatment}
              onChange={(e) => setTreatment(e.target.value)}
              disabled={busy}
              data-testid="build-treatment"
            >
              {(treatments.length ? treatments : ['Reject', 'Manual Review', 'Refer', 'Info']).map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </label>
        </div>
      )}

      {path === 'describe' ? (
        <label className="block text-sm sm:w-60">
          <span className="text-slate-600">{treatmentLabel}</span>
          <select
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
            value={treatment}
            onChange={(e) => setTreatment(e.target.value)}
            disabled={busy}
          >
            {(treatments.length ? treatments : ['Reject', 'Manual Review']).map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
        </label>
      ) : null}

      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          className="bt-btn bt-btn-secondary bt-btn-sm"
          disabled={
            busy ||
            (path === 'describe'
              ? !text.trim()
              : path === 'compound'
                ? branches.length < 2
                : path === 'group'
                  ? groupConditions.length < 1
                  : !canPreviewBuild)
          }
          onClick={() => void runPreview()}
          data-testid="preview-rule"
        >
          Preview
        </button>
        <button
          type="button"
          className="bt-btn bt-btn-primary bt-btn-sm"
          disabled={busy || !preview || !preview.complete}
          onClick={() => void confirm()}
          data-testid="confirm-add-rule"
        >
          Confirm &amp; {replaceRuleId ? 'Update' : 'Add'}
        </button>
      </div>

      {feedback ? (
        <p className="text-sm text-slate-800" data-testid="authoring-feedback">
          {feedback}
        </p>
      ) : null}

      {preview ? (
        <div className="rounded border border-slate-200 bg-white px-3 py-2 text-sm" data-testid="rule-preview">
          <div className="font-medium text-slate-900">Preview</div>
          {asList(preview.previewLines).length ? (
            <div className="mt-1 space-y-0.5 font-semibold text-slate-900" data-testid="rule-preview-display">
              {asList(preview.previewLines).map((line, i) => (
                <p key={i}>{String(line)}</p>
              ))}
            </div>
          ) : (
            <p className="mt-1 font-semibold text-slate-900" data-testid="rule-preview-display">
              {String(preview.ruleDisplay ?? `${preview.parameter ?? ''} ${preview.operator ?? ''} ${preview.valueDisplay ?? preview.value ?? ''}`)}
            </p>
          )}
          {!asList(preview.previewLines).length ? (
            <p className="text-slate-700" data-testid="rule-preview-failure">
              {String(preview.failureDisplay ?? `${treatmentLabel} → ${preview.treatment ?? treatment}`)}
            </p>
          ) : null}
          {asList(preview.unresolved).length ? (
            <div className="mt-2 rounded border border-amber-200 bg-amber-50 px-2 py-1 text-xs text-amber-950" data-testid="rule-preview-unresolved">
              <p className="font-semibold">Some parts of this rule have not been mapped yet.</p>
              <p>Unresolved: {asList(preview.unresolved).map(String).join(', ')}</p>
            </div>
          ) : null}
          {preview.compoundGroup ? (
            <button
              type="button"
              className="mt-2 text-xs text-sky-800 hover:underline"
              onClick={() => setPath('group')}
            >
              Switch to Build (edit conditions)
            </button>
          ) : null}
          <dl className="mt-2 grid gap-1 sm:grid-cols-2">
            <div>
              <dt className="text-xs text-slate-500">Evaluated from</dt>
              <dd>{String(preview.evaluatedFrom ?? preview.source ?? '—')}</dd>
            </div>
            <div>
              <dt className="text-xs text-slate-500">Parameter</dt>
              <dd>{String(preview.parameter ?? '—')}</dd>
            </div>
            <div>
              <dt className="text-xs text-slate-500">Condition</dt>
              <dd>
                {String(preview.operator ?? '')}{' '}
                {String(preview.valueDisplay ?? preview.value ?? preview.rightParameter ?? '')}
              </dd>
            </div>
            <div>
              <dt className="text-xs text-slate-500">{treatmentLabel}</dt>
              <dd>{String(preview.treatment ?? '—')}</dd>
            </div>
            <div>
              <dt className="text-xs text-slate-500">Availability</dt>
              <dd>{String(preview.availability ?? '—')}</dd>
            </div>
            {preview.showPeriod ? (
              <div>
                <dt className="text-xs text-slate-500">Period</dt>
                <dd>{String(preview.period)}</dd>
              </div>
            ) : null}
          </dl>
          {asList(preview.candidates).length ? (
            <div className="mt-2">
              <p className="text-xs font-semibold text-amber-900">Choose a parameter</p>
              <ul className="mt-1 space-y-1">
                {asList(preview.candidates).map((c, i) => {
                  const p = asRecord(c)
                  return (
                    <li key={i}>
                      <button
                        type="button"
                        className="text-sky-800 hover:underline"
                        onClick={() => {
                          setParameterId(String(p.parameterId))
                          setSource(String(p.source ?? ''))
                          setPath('build')
                        }}
                      >
                        {String(p.businessName)} · {String(p.source)}
                      </button>
                    </li>
                  )
                })}
              </ul>
            </div>
          ) : null}
          {preview.needsResolver ? (
            <p className="mt-2 text-xs text-amber-900">
              Parameter not yet mapped — use Resolve parameter on Rules, or pick a source/parameter above.
            </p>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
