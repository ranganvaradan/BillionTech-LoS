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
  businessFacingInputLabels,
  formatCalculationResultLabel,
  formatCanCalculateNarrative,
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
  /** When true, parent already shows the parameter title — avoid duplicate heading */
  hideTitle?: boolean
  onChanged?: () => void
}

type Dep = {
  parameterId?: string
  displayName?: string
  source?: string
  parameterKind?: string
  readiness?: string
  reasonSelected?: string
  role?: string
}

type ClarificationChoice = { id?: string; label?: string }
type ClarificationQuestion = {
  id?: string
  prompt?: string
  choices?: ClarificationChoice[]
}

/**
 * POLICY-DERIVED-CALCULATION-BUSINESS-ASSISTANT-1
 * One primary working card. Business language by default; Advanced for diagnostics.
 * "Use this calculation" remains the only approval boundary.
 */
export function SuggestCalculationWorkflow({
  canonicalParameterId,
  businessName,
  supportStatus,
  calculationRequired,
  primitives = [],
  ruleStatement,
  hideTitle = false,
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
  const [clarificationAnswers, setClarificationAnswers] = useState<Record<string, string>>({})
  const [phase, setPhase] = useState<'idle' | 'research' | 'done'>('idle')
  const [showChange, setShowChange] = useState(false)

  const selected = options[selectedIdx] ?? proposal
  const deps = (Array.isArray(selected?.candidateDependencies)
    ? selected!.candidateDependencies
    : Array.isArray(selected?.dataICanUse)
      ? selected!.dataICanUse
      : []) as Dep[]

  const proposalStatus = String(selected?.proposalStatus ?? '')
  const businessOutcome = String(selected?.businessOutcome ?? '')
  const hasExpression = selected?.proposedExpression != null
  const clarificationQuestions = (Array.isArray(selected?.clarificationQuestions)
    ? selected!.clarificationQuestions
    : []) as ClarificationQuestion[]

  const isCanCalculate =
    businessOutcome === 'CAN_CALCULATE' ||
    (hasExpression && proposalStatus === 'READY_FOR_REVIEW')
  const isNeedsClarification =
    businessOutcome === 'NEEDS_CLARIFICATION' ||
    (clarificationQuestions.length > 0 && !hasExpression)
  const isMissingData =
    businessOutcome === 'MISSING_DATA' ||
    (!isCanCalculate &&
      !isNeedsClarification &&
      selected != null &&
      (selected.unableToRecommend === true ||
        proposalStatus === 'NEEDS_INPUT' ||
        !hasExpression))

  const plainExplanation = useMemo(() => {
    const raw = String(selected?.humanExplanation ?? msg ?? '')
    return sanitizeLenderTechnicalPhrase(raw)
  }, [selected, msg])

  const canCalculateNarrative = useMemo(
    () => formatCanCalculateNarrative(String(selected?.humanExplanation ?? '')),
    [selected],
  )

  const dataICanUse = useMemo(() => businessFacingInputLabels(deps), [deps])

  const resultLabel = formatCalculationResultLabel(displayName)

  if (!canonicalParameterId) return null

  async function runResearch(answers?: Record<string, string>) {
    setBusy(true)
    setError(null)
    setMsg(null)
    setPhase('research')
    try {
      const merged = { ...clarificationAnswers, ...(answers ?? {}) }
      setClarificationAnswers(merged)
      const res = await suggestDerivedCalculation(canonicalParameterId, {
        businessDescription: businessDefinition.trim() || undefined,
        clarificationAnswers: Object.keys(merged).length ? merged : undefined,
      })
      const opts = Array.isArray(res.options) ? (res.options as Array<Record<string, unknown>>) : [res]
      setOptions(opts)
      setProposal(opts[0] ?? res)
      setSelectedIdx(0)
      if (res.unableToRecommend === true) {
        setMsg(
          sanitizeLenderTechnicalPhrase(
            String(res.humanExplanation ?? "I can't calculate this yet."),
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

  async function onWorkItOut() {
    await runResearch()
  }

  async function onClarificationChoice(questionId: string, choiceId: string) {
    await runResearch({ [questionId]: choiceId })
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
      setClarificationAnswers({})
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
  const showAssistant = needsSuggest && !showHowCalculated

  return (
    <div
      className="mt-3 space-y-3 rounded-lg border border-slate-200 bg-white p-3"
      data-testid="suggest-calculation-workflow"
      data-lender-ux="layer-1"
      data-business-assistant="1"
    >
      {!hideTitle ? (
        <div>
          <div className="text-sm font-semibold text-slate-900">{displayName}</div>
          {ruleStatement ? (
            <p className="mt-1 text-xs leading-relaxed text-slate-700">{ruleStatement}</p>
          ) : null}
        </div>
      ) : null}

      {showAssistant ? (
        <div
          className="rounded-md border border-amber-200 bg-amber-50/60 p-3 text-sm text-amber-950"
          data-testid="lender-needs-input-panel"
        >
          <div className="font-medium">Needs your input</div>

          {phase === 'idle' ? (
            <>
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
            </>
          ) : null}

          {error ? <p className="mt-2 text-xs text-rose-700">{error}</p> : null}

          {selected && phase === 'research' ? (
            <div className="mt-3 space-y-3" data-testid="suggested-derivation-panel">
              {isCanCalculate ? (
                <div data-testid="lender-proposal-panel">
                  <div className="text-sm font-semibold text-slate-900">I can calculate this</div>
                  {canCalculateNarrative ? (
                    <p className="mt-2 text-xs leading-relaxed text-slate-800 whitespace-pre-wrap">
                      {canCalculateNarrative}
                    </p>
                  ) : null}
                  {dataICanUse.length > 0 ? (
                    <div className="mt-3 text-xs text-slate-800" data-testid="lender-ill-use-list">
                      <div className="font-medium">I&apos;ll use:</div>
                      <ul className="mt-1 list-disc pl-4">
                        {dataICanUse.map((name) => (
                          <li key={name}>{name}</li>
                        ))}
                      </ul>
                    </div>
                  ) : null}
                  <p className="mt-3 text-xs font-medium text-slate-900">
                    Result: {resultLabel}
                  </p>
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
                      Change
                    </button>
                  </div>
                  {showChange ? (
                    <div className="mt-3 space-y-2 rounded border border-amber-100 bg-white/80 p-2">
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
                      <button
                        type="button"
                        className="bt-btn bt-btn-secondary bt-btn-sm ml-2"
                        disabled={busy}
                        onClick={() => void onReject()}
                      >
                        Discard
                      </button>
                    </div>
                  ) : null}
                </div>
              ) : null}

              {isNeedsClarification ? (
                <div data-testid="lender-clarification-panel">
                  <div className="text-sm font-semibold text-slate-900">I need one detail</div>
                  <p className="mt-2 text-xs leading-relaxed text-slate-800 whitespace-pre-wrap">
                    {clarificationQuestions[0]?.prompt
                      ? sanitizeLenderTechnicalPhrase(String(clarificationQuestions[0].prompt))
                      : plainExplanation}
                  </p>
                  {clarificationQuestions[0]?.choices &&
                  clarificationQuestions[0].choices.length > 0 ? (
                    <div className="mt-3 flex flex-wrap gap-2">
                      {clarificationQuestions[0].choices.map((c) => (
                        <button
                          key={String(c.id)}
                          type="button"
                          className="bt-btn bt-btn-secondary bt-btn-sm"
                          disabled={busy}
                          data-testid={`clarification-choice-${c.id}`}
                          onClick={() =>
                            void onClarificationChoice(
                              String(clarificationQuestions[0].id ?? 'overdue_threshold'),
                              String(c.id ?? ''),
                            )
                          }
                        >
                          {c.label}
                        </button>
                      ))}
                    </div>
                  ) : (
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
                    </div>
                  )}
                </div>
              ) : null}

              {isMissingData && !isNeedsClarification && !isCanCalculate ? (
                <div data-testid="lender-missing-data-panel">
                  <div className="text-sm font-semibold text-slate-900">
                    I can&apos;t calculate this yet
                  </div>
                  <p className="mt-2 text-xs leading-relaxed text-slate-800 whitespace-pre-wrap">
                    {plainExplanation}
                  </p>
                  {Array.isArray(selected.missingDependencies) &&
                  (selected.missingDependencies as unknown[]).length > 0 ? (
                    <div className="mt-3 text-xs text-slate-800">
                      <div className="font-medium">To calculate this I need</div>
                      <ul className="mt-1 list-disc pl-4">
                        {(selected.missingDependencies as unknown[]).map((m, i) => (
                          <li key={i}>{sanitizeLenderTechnicalPhrase(String(m))}</li>
                        ))}
                      </ul>
                    </div>
                  ) : null}
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
              ) : null}
            </div>
          ) : null}

          <details className="mt-3" data-testid="lender-advanced-details">
            <summary className="cursor-pointer text-xs font-medium text-slate-600">Advanced &gt;</summary>
            <div className="mt-2 space-y-2 text-[11px] text-slate-600">
              <div>
                Canonical parameter ID:{' '}
                <span className="font-mono text-slate-800">{canonicalParameterId}</span>
              </div>
              {selected ? (
                <>
                  <div>
                    Business outcome: {String(selected.businessOutcome ?? '—')} · Status:{' '}
                    {String(selected.proposalStatus ?? '—')} · Confidence:{' '}
                    {String(selected.confidence ?? '—')}
                  </div>
                  {selected.evaluationDateAuthority ? (
                    <div>Evaluation date: {String(selected.evaluationDateAuthority)}</div>
                  ) : null}
                  <div className="font-medium text-slate-800">Candidate inputs</div>
                  <ul className="space-y-1">
                    {deps.map((d) => (
                      <li key={d.parameterId} className="rounded bg-white/80 px-2 py-1">
                        <span className="font-medium">{d.displayName || d.parameterId}</span>
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
                      <div className="text-amber-800">No complete expression yet.</div>
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
          <details className="mt-3" data-testid="lender-advanced-details">
            <summary className="cursor-pointer text-xs font-medium text-slate-600">Advanced &gt;</summary>
            <div className="mt-2 space-y-1 text-[11px] text-slate-600">
              <div>
                Definition status: <strong>{String(definition?.status ?? '—')}</strong>
              </div>
              <div>
                Version: v{String(definition?.versionNo ?? '—')} · Scope:{' '}
                {String(definition?.scope ?? '—')}
              </div>
              <pre className="overflow-x-auto rounded bg-slate-50 p-2 text-[10px]">
                {JSON.stringify(definition?.expression ?? {}, null, 2)}
              </pre>
            </div>
          </details>
        </div>
      ) : null}
    </div>
  )
}
