import { useEffect, useMemo, useState } from 'react'
import {
  addPlainEnglishPolicyRule,
  getRuleAuthoringSources,
  previewPolicyRule,
} from '@/api/creditIntelligence'
import { ApiError } from '@/api/http'

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

type Path = 'menu' | 'build' | 'describe'

/**
 * POLICY-RULE-AUTHORING-FIX-1 — Build Rule / Describe Rule with mandatory preview.
 */
export function CiRuleAuthoringPanel({
  documentId,
  busy,
  setBusy,
  onError,
  onSession,
  replaceRuleId,
  initialText,
  onClose,
}: {
  documentId: string
  busy: boolean
  setBusy: (v: boolean) => void
  onError: (msg: string | null) => void
  onSession: (data: Record<string, unknown>) => void
  replaceRuleId?: string
  initialText?: string
  onClose?: () => void
}) {
  const [path, setPath] = useState<Path>(replaceRuleId ? 'describe' : 'menu')
  const [sources, setSources] = useState<Record<string, unknown> | null>(null)
  const [source, setSource] = useState('')
  const [parameterId, setParameterId] = useState('')
  const [operator, setOperator] = useState('>=')
  const [value, setValue] = useState('')
  const [period, setPeriod] = useState('')
  const [treatment, setTreatment] = useState('Reject')
  const [text, setText] = useState(initialText ?? '')
  const [preview, setPreview] = useState<Record<string, unknown> | null>(null)
  const [feedback, setFeedback] = useState<string | null>(null)

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
  const treatments = asList(sources?.treatments).map(String)
  const operators = asList(asRecord(sources?.operatorsByType).NUMBER).map(String)

  const runPreview = async () => {
    setBusy(true)
    onError(null)
    setFeedback(null)
    try {
      const body =
        path === 'build'
          ? {
              mode: 'BUILD',
              parameterId,
              operator,
              value: value === '' ? null : Number(value),
              period: period || undefined,
              treatment,
            }
          : {
              mode: 'DESCRIBE',
              text,
              treatment,
              ...(replaceRuleId ? { replaceRuleId } : {}),
            }
      const data = await previewPolicyRule(documentId, body)
      const p = asRecord(data.preview)
      setPreview(p)
      if (p.complete) {
        setFeedback(String(p.message ?? 'Ready to confirm'))
        if (p.parameterId) setParameterId(String(p.parameterId))
        if (p.operator) setOperator(String(p.operator))
        if (p.value != null) setValue(String(p.value))
        if (p.treatment) setTreatment(String(p.treatment))
        if (p.source) setSource(String(p.source))
      } else {
        setFeedback(String(p.message ?? 'Incomplete'))
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
      const body: Record<string, unknown> = {
        confirm: true,
        mode: path === 'build' ? 'BUILD' : 'DESCRIBE',
        text: path === 'describe' ? text : undefined,
        parameterId: parameterId || asRecord(preview).parameterId,
        operator: operator || asRecord(preview).operator,
        value: value === '' ? asRecord(preview).value : Number(value),
        period: period || undefined,
        treatment,
        ...(replaceRuleId ? { replaceRuleId } : {}),
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
          {replaceRuleId ? 'Edit rule' : path === 'build' ? 'Build rule' : 'Describe rule'}
        </h3>
        <div className="flex items-center gap-2">
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

      {path === 'describe' ? (
        <label className="block text-sm">
          <span className="text-slate-600">Describe the rule in plain English</span>
          <textarea
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm"
            rows={3}
            value={text}
            disabled={busy}
            placeholder='e.g. Bureau score should be >= 650'
            onChange={(e) => setText(e.target.value)}
            data-testid="describe-rule-text"
          />
        </label>
      ) : (
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
            <span className="text-slate-600">Operator</span>
            <select
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              value={operator}
              onChange={(e) => setOperator(e.target.value)}
              disabled={busy}
            >
              {(operators.length ? operators : ['>', '>=', '<', '<=', '=']).map((o) => (
                <option key={o} value={o}>
                  {o}
                </option>
              ))}
            </select>
          </label>
          <label className="block text-sm">
            <span className="text-slate-600">Value</span>
            <input
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              value={value}
              onChange={(e) => setValue(e.target.value)}
              disabled={busy}
              data-testid="build-value"
            />
          </label>
          <label className="block text-sm">
            <span className="text-slate-600">Period (optional)</span>
            <input
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              value={period}
              onChange={(e) => setPeriod(e.target.value)}
              disabled={busy}
              placeholder="Not applicable"
            />
          </label>
          <label className="block text-sm">
            <span className="text-slate-600">Treatment</span>
            <select
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              value={treatment}
              onChange={(e) => setTreatment(e.target.value)}
              disabled={busy}
            >
              {(treatments.length ? treatments : ['Reject', 'Manual Review', 'Approve', 'Info']).map((t) => (
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
          <span className="text-slate-600">Treatment</span>
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
          disabled={busy || (path === 'describe' ? !text.trim() : !parameterId || value === '')}
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
          <dl className="mt-1 grid gap-1 sm:grid-cols-2">
            <div>
              <dt className="text-xs text-slate-500">Evaluated from</dt>
              <dd>{String(preview.evaluatedFrom ?? preview.source ?? '—')}</dd>
            </div>
            <div>
              <dt className="text-xs text-slate-500">Parameter</dt>
              <dd>{String(preview.parameter ?? '—')}</dd>
            </div>
            <div>
              <dt className="text-xs text-slate-500">Rule</dt>
              <dd>
                {String(preview.operator ?? '')} {String(preview.value ?? '')}
              </dd>
            </div>
            <div>
              <dt className="text-xs text-slate-500">Treatment</dt>
              <dd>{String(preview.treatment ?? '—')}</dd>
            </div>
            <div>
              <dt className="text-xs text-slate-500">Availability</dt>
              <dd>{String(preview.availability ?? '—')}</dd>
            </div>
            <div>
              <dt className="text-xs text-slate-500">Period</dt>
              <dd>{String(preview.period ?? 'Not applicable')}</dd>
            </div>
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
