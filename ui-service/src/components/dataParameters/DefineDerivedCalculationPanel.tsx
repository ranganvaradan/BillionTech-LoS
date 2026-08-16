import { useEffect, useMemo, useState } from 'react'
import {
  getDerivedCalculationLatest,
  markDerivedCalculationProductionReady,
  saveDerivedCalculationDraft,
  testDerivedCalculation,
} from '@/api/derivedCalculations'
import { ApiError } from '@/api/http'

type Props = {
  canonicalParameterId: string
  unit?: string
  primitives?: string[]
  supportStatus?: string
}

/**
 * Safe typed expression authoring for CALCULATION_NOT_IMPLEMENTED (and similar) parameters.
 * No arbitrary code — REF ops must use exact GACAT IDs.
 */
export function DefineDerivedCalculationPanel({
  canonicalParameterId,
  unit,
  primitives = [],
  supportStatus,
}: Props) {
  const [open, setOpen] = useState(false)
  const [leftId, setLeftId] = useState(primitives[0] ?? '')
  const [rightId, setRightId] = useState(primitives[1] ?? '')
  const [op, setOp] = useState<'REF' | 'SUB' | 'ADD' | 'MUL' | 'DIV' | 'GTE' | 'LTE'>('REF')
  const [description, setDescription] = useState('')
  const [resultType, setResultType] = useState('NUMBER')
  const [sampleJson, setSampleJson] = useState('{}')
  const [statusMsg, setStatusMsg] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [definition, setDefinition] = useState<Record<string, unknown> | null>(null)
  const show = supportStatus === 'CALCULATION_NOT_IMPLEMENTED' || definition != null

  useEffect(() => {
    if (!canonicalParameterId) return
    let cancelled = false
    getDerivedCalculationLatest(canonicalParameterId)
      .then((r) => {
        if (cancelled) return
        if (r.found === false) {
          setDefinition(null)
          return
        }
        setDefinition(r)
      })
      .catch(() => {
        if (!cancelled) setDefinition(null)
      })
    return () => {
      cancelled = true
    }
  }, [canonicalParameterId])

  const expression = useMemo(() => {
    if (op === 'REF') {
      return { op: 'REF', id: leftId.trim() }
    }
    return {
      op,
      left: { op: 'REF', id: leftId.trim() },
      right: { op: 'REF', id: rightId.trim() },
    }
  }, [op, leftId, rightId])

  if (!show || !canonicalParameterId) return null

  const governance = String(definition?.status ?? 'NONE')

  async function onSaveDraft() {
    setBusy(true)
    setError(null)
    setStatusMsg(null)
    try {
      const saved = await saveDerivedCalculationDraft({
        canonicalParameterId,
        scope: 'PLATFORM',
        expression,
        resultType,
        unit,
        description: description || undefined,
      })
      setDefinition(saved)
      setStatusMsg(`Saved draft v${String(saved.versionNo ?? '')} — status ${String(saved.status)}`)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  async function onTest() {
    if (!definition?.id) {
      setError('Save a draft first')
      return
    }
    setBusy(true)
    setError(null)
    setStatusMsg(null)
    try {
      let sample: Record<string, unknown> = {}
      try {
        sample = JSON.parse(sampleJson || '{}') as Record<string, unknown>
      } catch {
        throw new Error('Sample inputs must be valid JSON object of GACAT id → value')
      }
      const out = await testDerivedCalculation(String(definition.id), sample)
      setDefinition(out)
      const evalStatus = String(asRec(out.evaluation).status ?? '')
      setStatusMsg(`Test result: ${evalStatus} — definition now ${String(out.status)}`)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  async function onMarkReady() {
    if (!definition?.id) return
    setBusy(true)
    setError(null)
    try {
      const out = await markDerivedCalculationProductionReady(String(definition.id))
      setDefinition(out)
      setStatusMsg('Marked PRODUCTION_READY (requires prior TESTED)')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div
      className="rounded-lg border border-amber-200 bg-amber-50/60 p-3"
      data-testid="dp-define-calculation"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h3 className="text-sm font-semibold text-slate-900">Derived calculation</h3>
          <p className="text-xs text-slate-600">
            Attach a safe typed expression to this exact GACAT id. No JavaScript/SQL. Missing inputs →
            DATA_INSUFFICIENT (never zero).
          </p>
          <p className="mt-1 text-[11px] text-slate-500" data-testid="dp-derived-governance">
            Status: {governance === 'NONE' ? 'Not defined' : governance}
            {definition?.versionNo != null ? ` · v${String(definition.versionNo)}` : ''}
          </p>
        </div>
        <button
          type="button"
          className="bt-btn bt-btn-secondary bt-btn-sm"
          data-testid="dp-define-calculation-toggle"
          onClick={() => setOpen((v) => !v)}
        >
          {open ? 'Hide' : 'Define calculation'}
        </button>
      </div>

      {open ? (
        <div className="mt-3 space-y-2 text-xs">
          {primitives.length > 0 ? (
            <p className="text-slate-600">
              Declared inputs:{' '}
              <span className="font-mono text-[11px]">{primitives.join(', ')}</span>
            </p>
          ) : null}
          <label className="block">
            <span className="text-slate-500">Operation</span>
            <select
              className="mt-0.5 w-full rounded border border-slate-200 px-2 py-1"
              value={op}
              onChange={(e) => setOp(e.target.value as typeof op)}
              data-testid="dp-derived-op"
            >
              <option value="REF">REF (pass-through / single input)</option>
              <option value="SUB">SUB (left − right)</option>
              <option value="ADD">ADD</option>
              <option value="MUL">MUL</option>
              <option value="DIV">DIV</option>
              <option value="GTE">GTE</option>
              <option value="LTE">LTE</option>
            </select>
          </label>
          <label className="block">
            <span className="text-slate-500">Input A (exact GACAT id)</span>
            <input
              className="mt-0.5 w-full rounded border border-slate-200 px-2 py-1 font-mono"
              value={leftId}
              onChange={(e) => setLeftId(e.target.value)}
              data-testid="dp-derived-left"
              list="dp-derived-primitive-options"
            />
          </label>
          {op !== 'REF' ? (
            <label className="block">
              <span className="text-slate-500">Input B (exact GACAT id)</span>
              <input
                className="mt-0.5 w-full rounded border border-slate-200 px-2 py-1 font-mono"
                value={rightId}
                onChange={(e) => setRightId(e.target.value)}
                data-testid="dp-derived-right"
                list="dp-derived-primitive-options"
              />
            </label>
          ) : null}
          <datalist id="dp-derived-primitive-options">
            {primitives.map((p) => (
              <option key={p} value={p} />
            ))}
          </datalist>
          <div className="grid gap-2 sm:grid-cols-2">
            <label className="block">
              <span className="text-slate-500">Result type</span>
              <select
                className="mt-0.5 w-full rounded border border-slate-200 px-2 py-1"
                value={resultType}
                onChange={(e) => setResultType(e.target.value)}
              >
                <option value="NUMBER">Number</option>
                <option value="INTEGER">Integer</option>
                <option value="DECIMAL">Decimal</option>
                <option value="BOOLEAN">Boolean</option>
                <option value="MONTHS">Months</option>
              </select>
            </label>
            <label className="block">
              <span className="text-slate-500">Description</span>
              <input
                className="mt-0.5 w-full rounded border border-slate-200 px-2 py-1"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </label>
          </div>
          <label className="block">
            <span className="text-slate-500">Sample inputs JSON (for Test)</span>
            <textarea
              className="mt-0.5 w-full rounded border border-slate-200 px-2 py-1 font-mono"
              rows={3}
              value={sampleJson}
              onChange={(e) => setSampleJson(e.target.value)}
              data-testid="dp-derived-sample"
            />
          </label>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              className="bt-btn bt-btn-primary bt-btn-sm"
              disabled={busy || !leftId.trim()}
              onClick={() => void onSaveDraft()}
              data-testid="dp-derived-save"
            >
              Save Draft
            </button>
            <button
              type="button"
              className="bt-btn bt-btn-secondary bt-btn-sm"
              disabled={busy || !definition?.id}
              onClick={() => void onTest()}
              data-testid="dp-derived-test"
            >
              Test with sample data
            </button>
            <button
              type="button"
              className="bt-btn bt-btn-secondary bt-btn-sm"
              disabled={busy || String(definition?.status) !== 'TESTED'}
              onClick={() => void onMarkReady()}
              data-testid="dp-derived-prod"
            >
              Mark production ready
            </button>
          </div>
          {statusMsg ? (
            <p className="text-emerald-800" data-testid="dp-derived-status">
              {statusMsg}
            </p>
          ) : null}
          {error ? (
            <p className="text-rose-700" data-testid="dp-derived-error">
              {error}
            </p>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}

function asRec(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}
