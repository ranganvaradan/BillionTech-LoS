import { useState } from 'react'
import {
  acceptDerivedCalculationProposal,
  editDerivedCalculationProposal,
  getDerivedCalculationLatest,
  rejectDerivedCalculationProposal,
  suggestDerivedCalculation,
} from '@/api/derivedCalculations'
import { ApiError } from '@/api/http'
import { DefineDerivedCalculationPanel } from '@/components/dataParameters/DefineDerivedCalculationPanel'

type Props = {
  canonicalParameterId: string
  businessName?: string
  /** Catalogue support status — CALCULATION_NOT_IMPLEMENTED triggers Suggest CTA */
  supportStatus?: string
  calculationRequired?: boolean
  primitives?: string[]
  onChanged?: () => void
}

type Dep = {
  parameterId?: string
  displayName?: string
  source?: string
  parameterKind?: string
  readiness?: string
  reasonSelected?: string
}

/**
 * Generic Suggest calculation → human review → Accept workflow.
 * Not parameter-specific. Reuses V139 definition persistence on Accept.
 */
export function SuggestCalculationWorkflow({
  canonicalParameterId,
  businessName,
  supportStatus,
  calculationRequired,
  primitives = [],
  onChanged,
}: Props) {
  const needsSuggest =
    calculationRequired === true ||
    supportStatus === 'CALCULATION_NOT_IMPLEMENTED' ||
    supportStatus === 'SUPPORT_CALCULATION_NOT_IMPLEMENTED'

  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [proposal, setProposal] = useState<Record<string, unknown> | null>(null)
  const [options, setOptions] = useState<Array<Record<string, unknown>>>([])
  const [selectedIdx, setSelectedIdx] = useState(0)
  const [editExpr, setEditExpr] = useState('')
  const [definition, setDefinition] = useState<Record<string, unknown> | null>(null)
  const [msg, setMsg] = useState<string | null>(null)

  if (!canonicalParameterId) return null

  const selected = options[selectedIdx] ?? proposal
  const deps = (Array.isArray(selected?.candidateDependencies)
    ? selected!.candidateDependencies
    : []) as Dep[]

  async function onSuggest() {
    setBusy(true)
    setError(null)
    setMsg(null)
    try {
      const res = await suggestDerivedCalculation(canonicalParameterId)
      const opts = Array.isArray(res.options) ? (res.options as Array<Record<string, unknown>>) : [res]
      setOptions(opts)
      setProposal(opts[0] ?? res)
      setSelectedIdx(0)
      if (res.unableToRecommend === true) {
        setMsg(String(res.humanExplanation ?? 'Unable to recommend a calculation'))
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  async function onAccept() {
    const id = String(selected?.id ?? '')
    if (!id) {
      setError('No proposal selected')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const res = await acceptDerivedCalculationProposal(id)
      setMsg('Calculation definition created for existing canonical parameter')
      setProposal(res)
      const def = (res.definition as Record<string, unknown> | undefined) ?? null
      setDefinition(def)
      onChanged?.()
      const latest = await getDerivedCalculationLatest(canonicalParameterId)
      if (latest.found !== false) setDefinition(latest)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  async function onReject() {
    const id = String(selected?.id ?? '')
    if (!id) return
    setBusy(true)
    setError(null)
    try {
      await rejectDerivedCalculationProposal(id)
      setMsg('Proposal rejected — no calculation definition created')
      setProposal(null)
      setOptions([])
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  async function onEditSave() {
    const id = String(selected?.id ?? '')
    if (!id) return
    setBusy(true)
    setError(null)
    try {
      const parsed = JSON.parse(editExpr || '{}') as Record<string, unknown>
      const updated = await editDerivedCalculationProposal(id, parsed)
      setOptions((prev) => prev.map((o, i) => (i === selectedIdx ? updated : o)))
      setProposal(updated)
      setMsg('Proposal expression updated — ready for review')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  const showHowCalculated = definition != null && definition.found !== false

  return (
    <div className="mt-3 space-y-3 rounded-lg border border-slate-200 bg-white p-3" data-testid="suggest-calculation-workflow">
      <div>
        <div className="text-sm font-semibold text-slate-900">
          {businessName || canonicalParameterId}
        </div>
        <div className="font-mono text-[11px] text-slate-500">{canonicalParameterId}</div>
      </div>

      {needsSuggest && !showHowCalculated ? (
        <div className="rounded-md border border-amber-200 bg-amber-50/60 p-3 text-sm text-amber-950">
          <div className="font-medium">Calculation required</div>
          <p className="mt-1 text-xs leading-relaxed">
            This value is not currently available as an executable BillionTech calculation.
            BillionTech can analyse the canonical parameters available to this policy and suggest
            how this value could be derived.
          </p>
          <button
            type="button"
            className="bt-btn bt-btn-primary bt-btn-sm mt-2"
            disabled={busy}
            onClick={() => void onSuggest()}
            data-testid="suggest-calculation-btn"
          >
            Suggest calculation
          </button>
        </div>
      ) : null}

      {error ? <p className="text-xs text-rose-700">{error}</p> : null}
      {msg ? <p className="text-xs text-slate-700">{msg}</p> : null}

      {selected && !showHowCalculated ? (
        <div className="space-y-2 rounded-md border border-slate-200 p-3" data-testid="suggested-derivation-panel">
          <div className="text-sm font-semibold text-slate-900">Suggested derivation</div>
          {options.length > 1 ? (
            <div className="flex flex-wrap gap-2">
              {options.map((o, i) => (
                <button
                  key={String(o.id ?? i)}
                  type="button"
                  className={`rounded border px-2 py-1 text-[11px] ${
                    i === selectedIdx ? 'border-sky-400 bg-sky-50' : 'border-slate-200'
                  }`}
                  onClick={() => {
                    setSelectedIdx(i)
                    setProposal(o)
                  }}
                >
                  Option {String(o.optionIndex ?? i + 1)}
                  {o.recommended === true ? ' · Recommended' : ''}
                </button>
              ))}
            </div>
          ) : null}

          <div className="text-xs text-slate-700 whitespace-pre-wrap">
            {String(selected.humanExplanation ?? '')}
          </div>
          <div className="text-[11px] text-slate-500">
            Confidence: {String(selected.confidence ?? '—')} · Status:{' '}
            {String(selected.proposalStatus ?? '—')}
          </div>

          <div>
            <div className="text-xs font-medium text-slate-800">Dependencies</div>
            <ul className="mt-1 space-y-1">
              {deps.map((d) => (
                <li key={d.parameterId} className="rounded bg-slate-50 px-2 py-1 text-[11px]">
                  <span className="font-medium">{d.displayName || d.parameterId}</span>
                  <span className="text-slate-500">
                    {' '}
                    · {d.parameterKind} · {d.source} · {d.readiness}
                  </span>
                  <div className="font-mono text-[10px] text-slate-400">{d.parameterId}</div>
                  {d.reasonSelected ? (
                    <div className="text-slate-600">{d.reasonSelected}</div>
                  ) : null}
                </li>
              ))}
              {deps.length === 0 ? (
                <li className="text-[11px] text-slate-500">No exact dependencies proposed yet.</li>
              ) : null}
            </ul>
          </div>

          {selected.proposedExpression ? (
            <pre className="overflow-x-auto rounded bg-slate-900/90 p-2 text-[10px] text-slate-100">
              {JSON.stringify(selected.proposedExpression, null, 2)}
            </pre>
          ) : (
            <div className="space-y-1">
              <div className="text-xs text-amber-800">
                No complete expression yet — edit a safe typed expression over exact GACAT IDs.
              </div>
              <textarea
                className="bt-input min-h-[80px] font-mono text-[11px]"
                placeholder='{"op":"REF","id":"exact.gacat.id"}'
                value={editExpr}
                onChange={(e) => setEditExpr(e.target.value)}
              />
              <button
                type="button"
                className="bt-btn bt-btn-secondary bt-btn-sm"
                disabled={busy || !editExpr.trim()}
                onClick={() => void onEditSave()}
              >
                Edit calculation
              </button>
            </div>
          )}

          <div className="flex flex-wrap gap-2 pt-1">
            <button
              type="button"
              className="bt-btn bt-btn-primary bt-btn-sm"
              disabled={busy || !selected.proposedExpression}
              onClick={() => void onAccept()}
              data-testid="accept-create-calculation"
            >
              Accept &amp; create calculation
            </button>
            <button
              type="button"
              className="bt-btn bt-btn-secondary bt-btn-sm"
              disabled={busy}
              onClick={() => void onReject()}
            >
              Reject
            </button>
          </div>
        </div>
      ) : null}

      {showHowCalculated ? (
        <details className="rounded-md border border-slate-200 p-3" open>
          <summary className="cursor-pointer text-sm font-medium text-slate-900">
            How is this calculated?
          </summary>
          <div className="mt-2 space-y-1 text-xs text-slate-700">
            <div>
              Status: <strong>{String(definition?.status ?? '—')}</strong>
            </div>
            <div>
              Version: v{String(definition?.versionNo ?? '—')} · Scope:{' '}
              {String(definition?.scope ?? '—')}
            </div>
            <div>Dependencies:</div>
            <ul className="list-disc pl-4 font-mono text-[10px]">
              {(Array.isArray(definition?.dependencies) ? definition!.dependencies : []).map(
                (d) => (
                  <li key={String(d)}>{String(d)}</li>
                ),
              )}
            </ul>
            <pre className="overflow-x-auto rounded bg-slate-50 p-2 text-[10px]">
              {JSON.stringify(definition?.expression ?? {}, null, 2)}
            </pre>
            <p className="text-[11px] text-slate-500">
              Lineage: inputs → approved typed expression → {businessName || canonicalParameterId} →
              Policy / Scorecard (same canonical id).
            </p>
          </div>
        </details>
      ) : null}

      <DefineDerivedCalculationPanel
        canonicalParameterId={canonicalParameterId}
        primitives={
          primitives.length
            ? primitives
            : deps.map((d) => String(d.parameterId ?? '')).filter(Boolean)
        }
        supportStatus={needsSuggest ? 'CALCULATION_NOT_IMPLEMENTED' : supportStatus}
      />
    </div>
  )
}
