import { useEffect, useMemo, useState } from 'react'
import {
  acceptDerivedCalculationProposal,
  editDerivedCalculationProposal,
  getDerivedCalculationLatest,
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
import {
  SETUP_CALCULATION_ACTION,
  requiresCalculationSetupAction,
} from '@/lib/policyStudio/lenderTruthDisplay'

type Props = {
  canonicalParameterId: string
  businessName?: string
  supportStatus?: string
  calculationRequired?: boolean
  /** Canonical businessReadinessReason — sole setup authority with allowedActions/nextAction */
  businessReadinessReason?: string | null
  nextAction?: string | null
  allowedActions?: unknown
  /** Already-implemented calculation: show How I'll calculate it + Accept/Change */
  knownExisting?: boolean
  existingExplanation?: string
  primitives?: string[]
  ruleStatement?: string
  hideTitle?: boolean
  /** Wave 10A — preferred lender action label (e.g. Review proposed calculation) */
  resolverActionHint?: string | null
  onChanged?: () => void
  /** Called when lender Accepts meaning of an existing implemented calculation */
  onMeaningAccepted?: () => void
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
 * POLICY-DERIVED-CALCULATION-UNIVERSAL-LENDER-FLOW-1
 * States: UNDERSTOOD (Accept/Change) | Work it out | Clarification | Conflict
 */
export function SuggestCalculationWorkflow({
  canonicalParameterId,
  businessName,
  supportStatus,
  calculationRequired,
  businessReadinessReason,
  nextAction,
  allowedActions,
  knownExisting = false,
  existingExplanation,
  primitives = [],
  ruleStatement,
  hideTitle = false,
  resolverActionHint,
  onChanged,
  onMeaningAccepted,
}: Props) {
  // CALCULATION-SETUP-ACTION-INVARIANT: canonical readiness/actions own setup visibility.
  // Legacy supportStatus is never sufficient alone to suppress setup when CALCULATION_NOT_DEFINED.
  const needsSetup =
    requiresCalculationSetupAction(
      {
        businessReadinessReason,
        nextAction,
        presentation: { allowedActions, businessReadinessReason },
      },
      { calculationRequired, allowedActions, businessReadinessReason, nextAction },
    ) ||
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
  const [phase, setPhase] = useState<'idle' | 'research' | 'change' | 'done'>(
    knownExisting ? 'research' : 'idle',
  )
  const [autoLoaded, setAutoLoaded] = useState(false)
  const [showChangeSurface, setShowChangeSurface] = useState(false)

  const selected = options[selectedIdx] ?? proposal
  const deps = (Array.isArray(selected?.candidateDependencies)
    ? selected!.candidateDependencies
    : Array.isArray(selected?.dataICanUse)
      ? selected!.dataICanUse
      : []) as Dep[]

  const proposalStatus = String(selected?.proposalStatus ?? '')
  const businessOutcome = String(selected?.businessOutcome ?? '')
  const proposalKind = String(selected?.proposalKind ?? '')
  const knownExistingCalc =
    knownExisting ||
    selected?.knownExistingCalculation === true ||
    proposalKind === 'CONFIRM_EXISTING'
  const hasExpression = selected?.proposedExpression != null
  const clarificationQuestions = (Array.isArray(selected?.clarificationQuestions)
    ? selected!.clarificationQuestions
    : []) as ClarificationQuestion[]
  const conflictChoices = (Array.isArray(selected?.conflictChoices)
    ? selected!.conflictChoices
    : []) as Array<{ id?: string; label?: string }>

  const isSemanticConflict = businessOutcome === 'SEMANTIC_CONFLICT'
  const isCanCalculate =
    !isSemanticConflict &&
    (businessOutcome === 'CAN_CALCULATE' ||
      (hasExpression && proposalStatus === 'READY_FOR_REVIEW') ||
      (knownExistingCalc && selected != null && proposalStatus === 'READY_FOR_REVIEW'))
  const isNeedsClarification =
    businessOutcome === 'NEEDS_CLARIFICATION' ||
    (clarificationQuestions.length > 0 && !hasExpression && !knownExistingCalc)
  const isMissingData =
    businessOutcome === 'MISSING_DATA' ||
    (!isCanCalculate &&
      !isNeedsClarification &&
      !isSemanticConflict &&
      selected != null &&
      (selected.unableToRecommend === true ||
        (proposalStatus === 'NEEDS_INPUT' && !knownExistingCalc)))

  const plainExplanation = useMemo(() => {
    const raw = String(selected?.humanExplanation ?? existingExplanation ?? msg ?? '')
    return sanitizeLenderTechnicalPhrase(raw)
  }, [selected, msg, existingExplanation])

  const canCalculateNarrative = useMemo(() => {
    const raw = String(selected?.humanExplanation ?? existingExplanation ?? '')
    const cleaned = formatCanCalculateNarrative(raw)
    return cleaned || sanitizeLenderTechnicalPhrase(raw)
  }, [selected, existingExplanation])

  const dataICanUse = useMemo(() => businessFacingInputLabels(deps), [deps])
  const resultLabel = formatCalculationResultLabel(displayName)

  const showCard = needsSetup || knownExisting

  useEffect(() => {
    if (!canonicalParameterId || !knownExisting || autoLoaded) return
    setAutoLoaded(true)
    void runResearch()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canonicalParameterId, knownExisting])

  if (!canonicalParameterId || !showCard) return null

  async function runResearch(answers?: Record<string, string>, descriptionOverride?: string) {
    setBusy(true)
    setError(null)
    setMsg(null)
    setPhase('research')
    try {
      const merged = { ...clarificationAnswers, ...(answers ?? {}) }
      setClarificationAnswers(merged)
      const desc = (descriptionOverride ?? businessDefinition).trim()
      const res = await suggestDerivedCalculation(canonicalParameterId, {
        businessDescription: desc || undefined,
        clarificationAnswers: Object.keys(merged).length ? merged : undefined,
      })
      const opts = Array.isArray(res.options) ? (res.options as Array<Record<string, unknown>>) : [res]
      setOptions(opts)
      setProposal(opts[0] ?? res)
      setSelectedIdx(0)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
      setPhase(knownExisting ? 'research' : 'idle')
    } finally {
      setBusy(false)
    }
  }

  function openChangeFlow() {
    setShowChangeSurface(true)
    setPhase('change')
    setProposal(null)
    setOptions([])
  }

  async function onAccept() {
    const id = String(selected?.id ?? '')
    if (!id) {
      // Known existing without proposal yet — still allow meaning accept via parent
      if (knownExistingCalc) {
        setPhase('done')
        setMsg('Accepted ✓')
        onMeaningAccepted?.()
        onChanged?.()
        return
      }
      setError('No proposal selected')
      return
    }
    if (!selected?.proposedExpression && !knownExistingCalc) {
      setError('I still need a complete calculation before you can confirm it.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const res = await acceptDerivedCalculationProposal(id)
      setMsg(knownExistingCalc ? 'Accepted ✓' : 'Calculation ready ✓')
      setProposal(res)
      const def = (res.definition as Record<string, unknown> | undefined) ?? null
      setDefinition(def)
      setPhase('done')
      onMeaningAccepted?.()
      onChanged?.()
      if (!knownExistingCalc) {
        const latest = await getDerivedCalculationLatest(canonicalParameterId)
        if (latest.found !== false) setDefinition(latest)
      }
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
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  async function onClarificationChoice(questionId: string, choiceId: string) {
    if (choiceId === 'other' || choiceId === 'something_else') {
      openChangeFlow()
      return
    }
    await runResearch({ [questionId]: choiceId })
  }

  const showHowCalculated = definition != null && definition.found !== false
  const acceptLabel = knownExistingCalc ? 'Accept' : 'Use this calculation'
  const heading =
    knownExistingCalc && isCanCalculate ? "How I'll calculate it" : 'I can calculate this'

  return (
    <div
      className="mt-3 space-y-3 rounded-lg border border-slate-200 bg-white p-3"
      data-testid="suggest-calculation-workflow"
      data-lender-ux="layer-1"
      data-business-assistant="1"
      data-universal-flow="1"
    >
      {!hideTitle ? (
        <div>
          <div className="text-sm font-semibold text-slate-900">{displayName}</div>
          {ruleStatement ? (
            <p className="mt-1 text-xs leading-relaxed text-slate-700">{ruleStatement}</p>
          ) : null}
        </div>
      ) : null}

      {phase === 'done' || showHowCalculated ? (
        <div
          className="rounded-md border border-emerald-200 bg-emerald-50/70 p-3"
          data-testid="lender-calculation-ready"
        >
          <div className="text-sm font-semibold text-emerald-950">
            {msg?.includes('Accepted') ? 'Accepted ✓' : 'Calculation ready ✓'}
          </div>
          <p className="mt-1 text-xs text-emerald-900">
            {knownExistingCalc
              ? 'You confirmed this calculation for the rule.'
              : 'This rule is ready to test. Production use still requires separate certification.'}
          </p>
          <button
            type="button"
            className="bt-btn bt-btn-secondary bt-btn-sm mt-3"
            disabled={busy}
            onClick={() => openChangeFlow()}
            data-testid="change-after-accept"
          >
            Change
          </button>
          <details className="mt-3" data-testid="lender-advanced-details">
            <summary className="cursor-pointer text-xs font-medium text-slate-600">Advanced &gt;</summary>
            <div className="mt-2 space-y-1 text-[11px] text-slate-600">
              <div>
                Canonical parameter ID:{' '}
                <span className="font-mono">{canonicalParameterId}</span>
              </div>
              {definition ? (
                <pre className="overflow-x-auto rounded bg-slate-50 p-2 text-[10px]">
                  {JSON.stringify(definition?.expression ?? {}, null, 2)}
                </pre>
              ) : null}
            </div>
          </details>
        </div>
      ) : null}

      {phase !== 'done' && !showHowCalculated ? (
        <div
          className="rounded-md border border-amber-200 bg-amber-50/60 p-3 text-sm text-amber-950"
          data-testid="lender-needs-input-panel"
        >
          {needsSetup && phase === 'idle' ? (
            <>
              <div className="font-medium" data-testid="lender-calc-setup-title">
                {SETUP_CALCULATION_ACTION}
              </div>
              <p className="mt-1 text-xs leading-relaxed">
                {resolverActionHint === 'Review proposed calculation'
                  ? `A proposed calculation for “${displayName}” may be ready for review after you open setup. It is not executable until you accept it.`
                  : `“${displayName}” is not ready because its calculation is not defined yet. Set up the calculation to continue.`}
              </p>
              <button
                type="button"
                className="bt-btn bt-btn-primary bt-btn-sm mt-3"
                disabled={busy}
                onClick={() => {
                  // Enter setup workflow; clarification / proposal review happen inside.
                  if (resolverActionHint === 'Review proposed calculation') {
                    setPhase('research')
                    void runResearch()
                    return
                  }
                  setPhase('change')
                  setShowChangeSurface(true)
                }}
                data-testid="suggest-calculation-btn"
              >
                {SETUP_CALCULATION_ACTION}
              </button>
            </>
          ) : null}

          {phase === 'change' || showChangeSurface ? (
            <div data-testid="lender-change-flow">
              <div className="font-medium">How should I calculate it?</div>
              <textarea
                className="bt-input mt-2 min-h-[88px] text-xs"
                placeholder="Describe the calculation in business terms…"
                value={businessDefinition}
                onChange={(e) => setBusinessDefinition(e.target.value)}
                data-testid="lender-change-definition"
              />
              <div className="mt-3 flex flex-wrap gap-2">
                <button
                  type="button"
                  className="bt-btn bt-btn-primary bt-btn-sm"
                  disabled={busy || !businessDefinition.trim()}
                  onClick={() => {
                    setShowChangeSurface(false)
                    void runResearch(undefined, businessDefinition)
                  }}
                  data-testid="change-work-it-out"
                >
                  Work it out for me
                </button>
                <button
                  type="button"
                  className="bt-btn bt-btn-secondary bt-btn-sm"
                  disabled={busy}
                  onClick={() => {
                    setShowChangeSurface(false)
                    setPhase(knownExisting ? 'research' : 'idle')
                    if (knownExisting) void runResearch()
                  }}
                >
                  Cancel
                </button>
              </div>
            </div>
          ) : null}

          {error ? <p className="mt-2 text-xs text-rose-700">{error}</p> : null}

          {selected && phase === 'research' && !showChangeSurface ? (
            <div className="mt-3 space-y-3" data-testid="suggested-derivation-panel">
              {isSemanticConflict ? (
                <div data-testid="lender-semantic-conflict">
                  <div className="text-sm font-semibold text-slate-900">This would change the meaning</div>
                  <p className="mt-2 text-xs leading-relaxed text-slate-800 whitespace-pre-wrap">
                    {plainExplanation}
                  </p>
                  <div className="mt-3 flex flex-wrap gap-2">
                    {conflictChoices.map((c) => (
                      <button
                        key={String(c.id)}
                        type="button"
                        className="bt-btn bt-btn-secondary bt-btn-sm"
                        disabled={busy}
                        onClick={() => {
                          if (c.id === 'keep_canonical') {
                            setBusinessDefinition('')
                            void runResearch()
                          } else {
                            openChangeFlow()
                          }
                        }}
                      >
                        {c.label}
                      </button>
                    ))}
                    {conflictChoices.length === 0 ? (
                      <button
                        type="button"
                        className="bt-btn bt-btn-secondary bt-btn-sm"
                        onClick={() => openChangeFlow()}
                      >
                        Revise description
                      </button>
                    ) : null}
                  </div>
                </div>
              ) : null}

              {isCanCalculate ? (
                <div data-testid="lender-proposal-panel">
                  <div className="text-sm font-semibold text-slate-900">{heading}</div>
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
                  <p className="mt-3 text-xs font-medium text-slate-900">Result: {resultLabel}</p>
                  <div className="mt-3 flex flex-wrap gap-2">
                    <button
                      type="button"
                      className="bt-btn bt-btn-primary bt-btn-sm"
                      disabled={busy || (!hasExpression && !knownExistingCalc)}
                      onClick={() => void onAccept()}
                      data-testid="accept-create-calculation"
                    >
                      {acceptLabel}
                    </button>
                    <button
                      type="button"
                      className="bt-btn bt-btn-secondary bt-btn-sm"
                      disabled={busy}
                      onClick={() => openChangeFlow()}
                      data-testid="change-calculation"
                    >
                      Change
                    </button>
                  </div>
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
                    <button
                      type="button"
                      className="bt-btn bt-btn-secondary bt-btn-sm mt-3"
                      onClick={() => openChangeFlow()}
                    >
                      Revise description
                    </button>
                  )}
                </div>
              ) : null}

              {isMissingData && !isNeedsClarification && !isCanCalculate && !isSemanticConflict ? (
                <div data-testid="lender-missing-data-panel">
                  <div className="text-sm font-semibold text-slate-900">
                    I can&apos;t calculate this yet
                  </div>
                  <p className="mt-2 text-xs leading-relaxed text-slate-800 whitespace-pre-wrap">
                    {plainExplanation}
                  </p>
                  {Array.isArray(selected.missingDependencies) &&
                  (selected.missingDependencies as unknown[]).length > 0 ? (
                    <ul className="mt-2 list-disc pl-4 text-xs">
                      {(selected.missingDependencies as unknown[]).map((m, i) => (
                        <li key={i}>{sanitizeLenderTechnicalPhrase(String(m))}</li>
                      ))}
                    </ul>
                  ) : null}
                  <button
                    type="button"
                    className="bt-btn bt-btn-secondary bt-btn-sm mt-3"
                    onClick={() => openChangeFlow()}
                  >
                    Revise description
                  </button>
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
                    Business outcome: {String(selected.businessOutcome ?? '—')} · Kind:{' '}
                    {String(selected.proposalKind ?? '—')}
                  </div>
                  {selected.proposedExpression ? (
                    <pre className="overflow-x-auto rounded bg-slate-900/90 p-2 text-[10px] text-slate-100">
                      {JSON.stringify(selected.proposedExpression, null, 2)}
                    </pre>
                  ) : (
                    <div className="space-y-1">
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
                supportStatus={needsSetup ? 'CALCULATION_NOT_IMPLEMENTED' : supportStatus}
              />
            </div>
          </details>
        </div>
      ) : null}
    </div>
  )
}
