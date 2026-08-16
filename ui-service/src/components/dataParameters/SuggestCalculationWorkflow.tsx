import { useMemo, useState } from 'react'
import {
  acceptDerivedCalculationProposal,
  editDerivedCalculationProposal,
  getDerivedCalculationLatest,
  rejectDerivedCalculationProposal,
  suggestDerivedCalculation,
} from '@/api/derivedCalculations'
import { ApiError } from '@/api/http'
import { DefineDerivedCalculationPanel } from '@/components/dataParameters/DefineDerivedCalculationPanel'
import {
  LENDER_SETUP_EXAMPLE,
  sanitizeLenderTechnicalPhrase,
} from '@/lib/policyStudio/lenderUxCopy'

type Props = {
  canonicalParameterId: string
  businessName?: string
  /** Catalogue support status — CALCULATION_NOT_IMPLEMENTED triggers setup CTA */
  supportStatus?: string
  calculationRequired?: boolean
  primitives?: string[]
  /** Optional rule statement for lender context */
  ruleStatement?: string
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
 * POLICY-STUDIO-LENDER-UX-SIMPLIFICATION-1
 * Lender Layer-1: business language + Work it out for me.
 * Technical expression / IDs / typed ops live under Advanced details.
 * Accept remains the explicit human approval boundary (no auto-approve).
 */
export function SuggestCalculationWorkflow({
  canonicalParameterId,
  businessName,
  supportStatus,
  calculationRequired,
  primitives = [],
  ruleStatement,
  onChanged,
}: Props) {
  const needsSuggest =
    calculationRequired === true ||
    supportStatus === 'CALCULATION_NOT_IMPLEMENTED' ||
    supportStatus === 'SUPPORT_CALCULATION_NOT_IMPLEMENTED'

  const displayName = businessName?.trim() || 'This value'
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [proposal, setProposal] = useState<Record<string, unknown> | null>(null)
  const [options, setOptions] = useState<Array<Record<string, unknown>>>([])
  const [selectedIdx, setSelectedIdx] = useState(0)
  const [editExpr, setEditExpr] = useState('')
  const [definition, setDefinition] = useState<Record<string, unknown> | null>(null)
  const [msg, setMsg] = useState<string | null>(null)
  const [businessDefinition, setBusinessDefinition] = useState('')
  const [phase, setPhase] = useState<'idle' | 'research' | 'done'>('idle')
  const [showChange, setShowChange] = useState(false)

  const selected = options[selectedIdx] ?? proposal
  const deps = (Array.isArray(selected?.candidateDependencies)
    ? selected!.candidateDependencies
    : []) as Dep[]

  const proposalStatus = String(selected?.proposalStatus ?? '')
  const hasExpression = selected?.proposedExpression != null
  const needsClarification =
    selected != null &&
    (selected.unableToRecommend === true ||
      proposalStatus === 'NEEDS_INPUT' ||
      !hasExpression)

  const plainExplanation = useMemo(() => {
    const raw = String(selected?.humanExplanation ?? msg ?? '')
    return sanitizeLenderTechnicalPhrase(raw)
  }, [selected, msg])

  const dataICanUse = deps
    .map((d) => String(d.displayName || '').trim())
    .filter(Boolean)

  if (!canonicalParameterId) return null

  async function onWorkItOut() {
    setBusy(true)
    setError(null)
    setMsg(null)
    setPhase('research')
    try {
      const res = await suggestDerivedCalculation(canonicalParameterId)
      const opts = Array.isArray(res.options) ? (res.options as Array<Record<string, unknown>>) : [res]
      setOptions(opts)
      setProposal(opts[0] ?? res)
      setSelectedIdx(0)
      if (res.unableToRecommend === true) {
        setMsg(
          sanitizeLenderTechnicalPhrase(
            String(res.humanExplanation ?? 'I need more information before I can propose a calculation.'),
          ),
        )
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
      setPhase('idle')
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
    if (!selected?.proposedExpression) {
      setError('I still need a complete calculation before you can confirm it.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const res = await acceptDerivedCalculationProposal(id)
      setMsg('Calculation saved. This rule is ready to test when Policy Test readiness allows.')
      setProposal(res)
      const def = (res.definition as Record<string, unknown> | undefined) ?? null
      setDefinition(def)
      setPhase('done')
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
      setMsg('Proposal discarded — no calculation was saved.')
      setProposal(null)
      setOptions([])
      setPhase('idle')
      setShowChange(false)
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
      setMsg('Updated — please review the calculation again.')
      setShowChange(false)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  const showHowCalculated = definition != null && definition.found !== false

  return (
    <div
      className="mt-3 space-y-3 rounded-lg border border-slate-200 bg-white p-3"
      data-testid="suggest-calculation-workflow"
      data-lender-ux="layer-1"
    >
      <div>
        <div className="text-sm font-semibold text-slate-900">{displayName}</div>
        {ruleStatement ? (
          <p className="mt-1 text-xs leading-relaxed text-slate-700">{ruleStatement}</p>
        ) : null}
      </div>

      {needsSuggest && !showHowCalculated && phase === 'idle' ? (
        <div
          className="rounded-md border border-amber-200 bg-amber-50/60 p-3 text-sm text-amber-950"
          data-testid="lender-needs-input-panel"
        >
          <div className="font-medium">Needs your input</div>
          <p className="mt-1 text-xs leading-relaxed">
            I have related bureau or application data, but I need to understand what you mean by
            “{displayName}” before I can apply this rule.
          </p>
          <label className="mt-3 block text-xs font-medium text-amber-950">
            How should I calculate it?
          </label>
          <textarea
            className="bt-input mt-1 min-h-[72px] text-xs"
            placeholder="Describe it in your own words…"
            value={businessDefinition}
            onChange={(e) => setBusinessDefinition(e.target.value)}
            data-testid="lender-business-definition"
          />
          <p className="mt-1 text-[11px] text-amber-800/90">
            Example: {LENDER_SETUP_EXAMPLE}
          </p>
          <button
            type="button"
            className="bt-btn bt-btn-primary bt-btn-sm mt-3"
            disabled={busy}
            onClick={() => void onWorkItOut()}
            data-testid="suggest-calculation-btn"
          >
            Work it out for me
          </button>
        </div>
      ) : null}

      {error ? <p className="text-xs text-rose-700">{error}</p> : null}
      {msg && phase !== 'research' ? <p className="text-xs text-slate-700">{msg}</p> : null}

      {selected && !showHowCalculated && phase === 'research' ? (
        <div
          className="space-y-3 rounded-md border border-slate-200 p-3"
          data-testid="suggested-derivation-panel"
        >
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

          {needsClarification ? (
            <div data-testid="lender-clarification-panel">
              <div className="text-sm font-semibold text-slate-900">I need one more detail</div>
              <p className="mt-2 text-xs leading-relaxed text-slate-700 whitespace-pre-wrap">
                {plainExplanation ||
                  'I could not safely determine a calculation from the information available.'}
              </p>
              {businessDefinition.trim() ? (
                <p className="mt-2 text-[11px] text-slate-600">
                  Your description: <em>{businessDefinition.trim()}</em>
                </p>
              ) : null}
              {Array.isArray(selected.missingDependencies) &&
              (selected.missingDependencies as unknown[]).length > 0 ? (
                <ul className="mt-2 list-disc space-y-1 pl-4 text-xs text-slate-700">
                  {(selected.missingDependencies as unknown[]).map((m, i) => (
                    <li key={i}>{sanitizeLenderTechnicalPhrase(String(m))}</li>
                  ))}
                </ul>
              ) : null}
              <p className="mt-2 text-xs text-slate-600">
                Please refine your description above, or ask your credit-policy lead to confirm the
                business meaning — I will not invent a proxy calculation.
              </p>
              <div className="mt-3 flex flex-wrap gap-2">
                <button
                  type="button"
                  className="bt-btn bt-btn-secondary bt-btn-sm"
                  disabled={busy}
                  onClick={() => {
                    setPhase('idle')
                    setProposal(null)
                    setOptions([])
                  }}
                >
                  Revise description
                </button>
                <button
                  type="button"
                  className="bt-btn bt-btn-secondary bt-btn-sm"
                  disabled={busy}
                  onClick={() => void onReject()}
                >
                  Cancel
                </button>
              </div>
            </div>
          ) : (
            <div data-testid="lender-proposal-panel">
              <div className="text-sm font-semibold text-slate-900">
                Here is how I propose to calculate it
              </div>
              <p className="mt-2 text-xs leading-relaxed text-slate-700 whitespace-pre-wrap">
                {plainExplanation}
              </p>
              {dataICanUse.length > 0 ? (
                <details className="mt-3 rounded border border-slate-100 bg-slate-50/80 p-2">
                  <summary className="cursor-pointer text-xs font-medium text-slate-800">
                    How will this work?
                  </summary>
                  <div className="mt-2 text-xs text-slate-700">
                    <div className="font-medium">Information available / Data I can use</div>
                    <ul className="mt-1 list-disc pl-4">
                      {dataICanUse.map((name) => (
                        <li key={name}>{name}</li>
                      ))}
                    </ul>
                    <p className="mt-2 text-[11px] text-slate-500">
                      Research candidates only — not confirmed dependencies until you approve the
                      calculation.
                    </p>
                  </div>
                </details>
              ) : null}

              <div className="mt-3 flex flex-wrap gap-2">
                <button
                  type="button"
                  className="bt-btn bt-btn-primary bt-btn-sm"
                  disabled={busy || !hasExpression}
                  onClick={() => void onAccept()}
                  data-testid="accept-create-calculation"
                >
                  Use this calculation
                </button>
                <button
                  type="button"
                  className="bt-btn bt-btn-secondary bt-btn-sm"
                  disabled={busy}
                  onClick={() => setShowChange((v) => !v)}
                >
                  Change it
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

              {showChange ? (
                <div className="mt-3 space-y-2 rounded border border-slate-200 bg-slate-50 p-2">
                  <p className="text-[11px] text-slate-600">
                    Prefer to describe the change in business terms and ask me to work it out again,
                    or use Advanced details below if you need a technical edit.
                  </p>
                  <button
                    type="button"
                    className="bt-btn bt-btn-secondary bt-btn-sm"
                    disabled={busy}
                    onClick={() => {
                      setPhase('idle')
                      setProposal(null)
                      setOptions([])
                      setShowChange(false)
                    }}
                  >
                    Describe again
                  </button>
                </div>
              ) : null}
            </div>
          )}
        </div>
      ) : null}

      {showHowCalculated ? (
        <div
          className="rounded-md border border-emerald-200 bg-emerald-50/70 p-3"
          data-testid="lender-calculation-ready"
        >
          <div className="text-sm font-semibold text-emerald-950">✓ Calculation ready</div>
          <p className="mt-1 text-xs text-emerald-900">
            This rule is ready to test. Production use still requires separate certification.
          </p>
        </div>
      ) : null}

      <details className="rounded-md border border-slate-200 p-3" data-testid="lender-advanced-details">
        <summary className="cursor-pointer text-xs font-medium text-slate-600">
          Advanced details
        </summary>
        <div className="mt-2 space-y-2 text-[11px] text-slate-600">
          <div>
            Canonical parameter ID:{' '}
            <span className="font-mono text-slate-800">{canonicalParameterId}</span>
          </div>
          {selected ? (
            <>
              <div>
                Proposal status: {String(selected.proposalStatus ?? '—')} · Confidence:{' '}
                {String(selected.confidence ?? '—')}
              </div>
              <div className="font-medium text-slate-800">Research candidates (not confirmed deps)</div>
              <ul className="space-y-1">
                {deps.map((d) => (
                  <li key={d.parameterId} className="rounded bg-slate-50 px-2 py-1">
                    <span className="font-medium">{d.displayName || d.parameterId}</span>
                    <span className="text-slate-500">
                      {' '}
                      · {d.parameterKind} · {d.source} · {d.readiness}
                    </span>
                    <div className="font-mono text-[10px] text-slate-400">{d.parameterId}</div>
                  </li>
                ))}
                {deps.length === 0 ? <li>None</li> : null}
              </ul>
              {selected.proposedExpression ? (
                <pre className="overflow-x-auto rounded bg-slate-900/90 p-2 text-[10px] text-slate-100">
                  {JSON.stringify(selected.proposedExpression, null, 2)}
                </pre>
              ) : (
                <div className="space-y-1">
                  <div className="text-amber-800">
                    No complete typed expression yet — edit only if authorized.
                  </div>
                  <textarea
                    className="bt-input min-h-[80px] font-mono text-[11px]"
                    placeholder='{"op":"REF","id":"exact.parameter.id"}'
                    value={editExpr}
                    onChange={(e) => setEditExpr(e.target.value)}
                  />
                  <button
                    type="button"
                    className="bt-btn bt-btn-secondary bt-btn-sm"
                    disabled={busy || !editExpr.trim()}
                    onClick={() => void onEditSave()}
                  >
                    Save expression edit
                  </button>
                </div>
              )}
            </>
          ) : null}
          {showHowCalculated ? (
            <div className="space-y-1 border-t border-slate-100 pt-2">
              <div>
                Definition status: <strong>{String(definition?.status ?? '—')}</strong>
              </div>
              <div>
                Version: v{String(definition?.versionNo ?? '—')} · Scope:{' '}
                {String(definition?.scope ?? '—')}
              </div>
              <div>Confirmed dependencies:</div>
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
            </div>
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
      </details>
    </div>
  )
}
