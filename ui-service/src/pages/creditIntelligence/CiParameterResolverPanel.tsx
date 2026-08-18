import { useEffect, useState } from 'react'
import {
  getParameterCatalogue,
  proposeParameterDefinition,
  resolveBusinessConcept,
  searchCanonicalParameters,
  type ReviewRuleBody,
} from '@/api/creditIntelligence'
import { SuggestCalculationWorkflow } from '@/components/dataParameters/SuggestCalculationWorkflow'
import { requiresCalculationSetupAction } from '@/lib/policyStudio/lenderTruthDisplay'

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

type Mode = 'source' | 'search' | 'describe' | 'manual'

/**
 * POLICY-PARAMETER-RESOLVER-1 — focused resolver (stays on policy).
 * Options: choose source, search all, describe meaning (proposal), manual input.
 */
export function CiParameterResolverPanel({
  open,
  onClose,
  operand,
  ruleId,
  busy,
  onResolve,
}: {
  open: boolean
  onClose: () => void
  operand: Record<string, unknown>
  ruleId: string
  busy: boolean
  onResolve: (ruleId: string, body: ReviewRuleBody) => Promise<void>
}) {
  const [mode, setMode] = useState<Mode>('source')
  const [sources, setSources] = useState<string[]>([])
  const [source, setSource] = useState('Bank Statement')
  const [browse, setBrowse] = useState<Record<string, unknown>>({})
  const [searchQ, setSearchQ] = useState('')
  const [searchHits, setSearchHits] = useState<unknown[]>([])
  const [preview, setPreview] = useState<Record<string, unknown> | null>(null)
  const [description, setDescription] = useState('')
  const [proposal, setProposal] = useState<Record<string, unknown> | null>(null)
  const [proposing, setProposing] = useState(false)
  const [manualLabel, setManualLabel] = useState(String(operand.businessName ?? operand.label ?? ''))
  const [manualType, setManualType] = useState('Money')
  const [manualUnit, setManualUnit] = useState('INR')
  const [manualActor, setManualActor] = useState('Credit Analyst')
  const [manualGuidance, setManualGuidance] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [suggestion, setSuggestion] = useState<Record<string, unknown> | null>(null)

  const operandKey = String(operand.operandKey ?? 'parameter')
  const term = String(operand.businessName ?? operand.label ?? operandKey)

  useEffect(() => {
    if (!open) return
    setMode('source')
    setPreview(null)
    setProposal(null)
    setError(null)
    setSuggestion(null)
    setManualLabel(term)
    void getParameterCatalogue()
      .then((cat) => {
        const srcs = asList(cat.sources).map(String)
        setSources(srcs.length ? srcs : ['Bank Statement', 'Bureau', 'Application', 'Manual Input'])
        const preferred =
          String(operand.suggestedSource ?? '') ||
          (operandKey.includes('writeoff') || operandKey.includes('write_off') || /write.?off/i.test(term)
            ? 'Bureau'
            : operandKey.includes('edi') && !/credit/i.test(term)
              ? 'Application'
              : operandKey.includes('clean')
                ? 'Bureau'
                : 'Bank Statement')
        setSource(srcs.includes(preferred) ? preferred : srcs[0] || 'Bank Statement')
      })
      .catch(() => setSources(['Bank Statement', 'Bureau', 'Application', 'Manual Input']))
    void resolveBusinessConcept({ concept: term })
      .then((r) => setSuggestion(r))
      .catch(() => setSuggestion(null))
  }, [open, operandKey, term])

  useEffect(() => {
    if (!open || mode !== 'source') return
    void getParameterCatalogue(source)
      .then(setBrowse)
      .catch(() => setBrowse({}))
  }, [open, mode, source])

  if (!open) return null

  const confirmMap = async (param: Record<string, unknown>) => {
    setError(null)
    await onResolve(ruleId, {
      uiAction: 'RESOLVE_PARAMETER_MAP',
      operandKey,
      originalTerm: term,
      parameterId: String(param.id ?? ''),
      reason: `Mapped ${term} → ${String(param.businessName ?? param.id)} (policy draft)`,
    })
    onClose()
  }

  const confirmManual = async () => {
    setError(null)
    await onResolve(ruleId, {
      uiAction: 'RESOLVE_PARAMETER_MANUAL',
      operandKey,
      originalTerm: term,
      manualInputLabel: manualLabel,
      manualInputType: manualType,
      unit: manualUnit,
      requiredActor: manualActor,
      guidance: manualGuidance,
      reason: `Manual input for ${term} (fact source, not Manual Review treatment)`,
    })
    onClose()
  }

  const runPropose = async () => {
    setProposing(true)
    setError(null)
    setProposal(null)
    try {
      setProposal(await proposeParameterDefinition({ term, description }))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not form proposal')
    } finally {
      setProposing(false)
    }
  }

  const acceptProposal = async () => {
    if (!proposal) return
    setError(null)
    await onResolve(ruleId, {
      uiAction: 'RESOLVE_PARAMETER_USE_PROPOSAL',
      operandKey,
      originalTerm: term,
      proposal,
      reason: `CM confirmed proposed definition for ${term} (not auto-accepted)`,
    })
    onClose()
  }

  const runSearch = async () => {
    setError(null)
    try {
      const data = await searchCanonicalParameters(searchQ)
      setSearchHits(asList(data.results))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Search failed')
    }
  }

  const ParamRow = ({ p }: { p: Record<string, unknown> }) => (
    <button
      type="button"
      className="w-full rounded border border-slate-200 bg-white px-3 py-2 text-left text-sm hover:border-sky-300 hover:bg-sky-50"
      onClick={() => setPreview(p)}
    >
      <div className="font-medium text-slate-900">{String(p.businessName ?? p.id)}</div>
      <div className="text-xs text-slate-500">
        {String(p.evaluatedFrom ?? '')} · {String(p.type ?? '')}
        {p.unit ? ` · ${String(p.unit)}` : ''}
      </div>
    </button>
  )

  return (
    <div
      className="fixed inset-0 z-40 flex justify-end bg-slate-900/30"
      role="dialog"
      aria-modal="true"
      aria-label="Resolve parameter"
      data-testid="parameter-resolver-panel"
    >
      <button type="button" className="flex-1 cursor-default" aria-label="Close overlay" onClick={onClose} />
      <div className="flex h-full w-full max-w-lg flex-col border-l border-slate-200 bg-white shadow-xl">
        <div className="flex items-start justify-between gap-2 border-b border-slate-200 px-4 py-3">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">Resolve parameter</h2>
            <p className="text-sm text-slate-600">
              {term}
              <span className="ml-2 text-amber-800">
                ·{' '}
                {operand.existingMappingUnresolved === true
                  ? 'EXISTING_MAPPING_UNRESOLVED'
                  : operand.unresolved === true || !operand.parameterId
                    ? 'Not yet mapped'
                    : String(operand.parameterId)}
              </span>
            </p>
            <p className="mt-1 text-xs text-slate-500">Use in this policy · draft scoped · not production authority</p>
          </div>
          <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" onClick={onClose}>
            Close
          </button>
        </div>

        <div className="flex flex-wrap gap-1 border-b border-slate-100 px-3 py-2">
          {(
            [
              ['source', 'Choose source'],
              ['search', 'Search all'],
              ['describe', 'Describe meaning'],
              ['manual', 'Manual input'],
            ] as const
          ).map(([id, label]) => (
            <button
              key={id}
              type="button"
              onClick={() => {
                setMode(id)
                setPreview(null)
                setProposal(null)
              }}
              className={`rounded-full px-3 py-1 text-xs font-semibold ${
                mode === id ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-700'
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        <div className="flex-1 space-y-3 overflow-y-auto px-4 py-3">
          {error ? (
            <p className="rounded border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">{error}</p>
          ) : null}

          {suggestion ? (
            <div
              className="rounded border border-sky-200 bg-sky-50 px-3 py-2 text-sm text-sky-950"
              data-testid="concept-suggestion"
            >
              <p className="font-semibold">Suggested mapping</p>
              <p className="text-xs mt-0.5">
                We understood: {String(suggestion.businessConcept ?? term)}
              </p>
              <p className="text-xs">
                State: {String(suggestion.resolutionState ?? '—')}
                {suggestion.canonicalParameter
                  ? ` · ${String(suggestion.businessName ?? suggestion.canonicalParameter)}`
                  : ''}
              </p>
              {suggestion.canonicalParameter ? (
                <p className="text-xs mt-1" data-testid="executability-modes">
                  {suggestion.calculationRequired === true
                    ? 'Needs your input'
                    : suggestion.policyTestReady === true
                      ? 'Ready to test'
                      : 'Needs review'}
                </p>
              ) : null}
              {(requiresCalculationSetupAction(
                {
                  businessReadinessReason: String(
                    suggestion.businessReadinessReason ?? '',
                  ),
                  nextAction: String(suggestion.nextAction ?? ''),
                },
                {
                  calculationRequired: suggestion.calculationRequired === true,
                  businessReadinessReason: String(
                    suggestion.businessReadinessReason ?? '',
                  ),
                  nextAction: String(suggestion.nextAction ?? ''),
                },
              ) ||
                String(suggestion.executionState ?? '').includes('CALCULATION') ||
                String(suggestion.supportStatus ?? '') === 'CALCULATION_NOT_IMPLEMENTED') &&
              suggestion.canonicalParameter ? (
                <SuggestCalculationWorkflow
                  canonicalParameterId={String(suggestion.canonicalParameter)}
                  businessName={String(suggestion.businessName ?? suggestion.canonicalParameter)}
                  calculationRequired
                  businessReadinessReason={String(suggestion.businessReadinessReason ?? '')}
                  nextAction={String(suggestion.nextAction ?? 'Set up calculation')}
                />
              ) : null}
              {suggestion.productionReady !== true && suggestion.policyTestReady === true ? (
                <p className="text-xs text-amber-800 mt-1">
                  Ready to test in Studio — not certified for production lending yet.
                </p>
              ) : null}
              {suggestion.mappedToProposedEdi === true ? (
                <p className="text-xs text-rose-800 font-semibold">Refused Proposed EDI mapping</p>
              ) : null}
              {suggestion.canonicalParameter && suggestion.executable === true ? (
                <button
                  type="button"
                  className="mt-2 bt-btn bt-btn-primary bt-btn-sm"
                  data-testid="accept-suggested-parameter"
                  disabled={busy}
                  onClick={() =>
                    void confirmMap({
                      id: String(suggestion.canonicalParameter),
                      businessName: String(suggestion.businessName ?? suggestion.canonicalParameter),
                    })
                  }
                >
                  Use suggested parameter
                </button>
              ) : null}
              {asList(suggestion.candidates).length > 1 ? (
                <ul className="mt-2 space-y-1 text-xs">
                  {asList(suggestion.candidates)
                    .slice(0, 5)
                    .map((c, i) => {
                      const row = asRecord(c)
                      return (
                        <li key={i}>
                          <button
                            type="button"
                            className="text-sky-900 underline"
                            onClick={() =>
                              void confirmMap({
                                id: String(row.parameterId ?? row.id),
                                businessName: String(row.businessName ?? row.parameterId),
                              })
                            }
                          >
                            {String(row.businessName ?? row.parameterId)} ({String(row.evaluatedFrom ?? '')})
                          </button>
                        </li>
                      )
                    })}
                </ul>
              ) : null}
            </div>
          ) : null}

          {mode === 'source' ? (
            <>
              <label className="block text-sm">
                <span className="text-xs font-semibold uppercase text-slate-500">Source</span>
                <select
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm"
                  value={source}
                  onChange={(e) => {
                    const next = e.target.value
                    setSource(next)
                    void resolveBusinessConcept({ concept: term, source: next })
                      .then((r) => setSuggestion(r))
                      .catch(() => null)
                  }}
                  data-testid="resolver-source"
                >
                  {sources.map((s) => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </select>
              </label>
              <p className="text-xs text-slate-500">SOURCE → RAW → DERIVED (known registry only)</p>
              <section>
                <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Raw</h3>
                <div className="mt-1 space-y-1">
                  {asList(browse.raw).length === 0 ? (
                    <p className="text-sm text-slate-500">No raw parameters for this source.</p>
                  ) : (
                    asList(browse.raw).map((row, i) => <ParamRow key={i} p={asRecord(row)} />)
                  )}
                </div>
              </section>
              <section>
                <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Derived</h3>
                <div className="mt-1 space-y-1">
                  {asList(browse.derived).length === 0 ? (
                    <p className="text-sm text-slate-500">No derived parameters for this source.</p>
                  ) : (
                    asList(browse.derived).map((row, i) => <ParamRow key={i} p={asRecord(row)} />)
                  )}
                </div>
              </section>
            </>
          ) : null}

          {mode === 'search' ? (
            <>
              <div className="flex gap-2">
                <input
                  type="search"
                  value={searchQ}
                  onChange={(e) => setSearchQ(e.target.value)}
                  placeholder="Search parameters…"
                  className="min-w-0 flex-1 rounded border border-slate-300 px-3 py-2 text-sm"
                  data-testid="resolver-search"
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') void runSearch()
                  }}
                />
                <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" onClick={() => void runSearch()}>
                  Search
                </button>
              </div>
              <p className="text-xs text-slate-500">Does not create duplicates — registry hits only.</p>
              <div className="space-y-1">
                {searchHits.map((row, i) => (
                  <ParamRow key={i} p={asRecord(row)} />
                ))}
              </div>
            </>
          ) : null}

          {mode === 'describe' ? (
            <>
              <label className="block text-sm">
                <span className="text-xs font-semibold uppercase text-slate-500">
                  Describe what this parameter means
                </span>
                <textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  rows={5}
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm"
                  placeholder="Plain English — e.g. what EDI or CLEAN means in this policy…"
                  data-testid="resolver-describe"
                />
              </label>
              <button
                type="button"
                className="bt-btn bt-btn-secondary bt-btn-sm"
                disabled={proposing || !description.trim()}
                onClick={() => void runPropose()}
              >
                {proposing ? 'Interpreting…' : 'Propose definition'}
              </button>
              {proposal ? (
                <div
                  className="space-y-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-3 text-sm"
                  data-testid="resolver-proposal"
                >
                  <h3 className="font-semibold text-slate-900">Proposed definition</h3>
                  <p className="text-xs font-medium text-amber-900">
                    Proposal only — not accepted until you confirm. Nothing was auto-applied.
                  </p>
                  <p className="font-medium">{String(proposal.businessName ?? term)}</p>
                  <p className="text-slate-700">{String(proposal.proposedCalculation ?? '—')}</p>
                  <p className="text-xs text-slate-600">
                    Result: {String(proposal.result ?? '—')} · {String(proposal.resultAvailability ?? '')}
                  </p>
                  {asList(proposal.knownPrimitives).length > 0 ? (
                    <ul className="list-disc pl-5 text-xs text-slate-700">
                      {asList(proposal.knownPrimitives).map((pr, i) => {
                        const r = asRecord(pr)
                        return (
                          <li key={i}>
                            {String(r.businessName)} — {String(r.evaluatedFrom)} ·{' '}
                            {String(r.primitiveStatus ?? r.type)}
                          </li>
                        )
                      })}
                    </ul>
                  ) : null}
                  <div className="flex flex-wrap gap-2 pt-1">
                    <button
                      type="button"
                      className="bt-btn bt-btn-primary bt-btn-sm"
                      disabled={busy}
                      onClick={() => void acceptProposal()}
                    >
                      Use this definition
                    </button>
                    <button
                      type="button"
                      className="bt-btn bt-btn-secondary bt-btn-sm"
                      onClick={() => setProposal(null)}
                    >
                      Edit
                    </button>
                    <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" onClick={onClose}>
                      Cancel
                    </button>
                  </div>
                </div>
              ) : null}
            </>
          ) : null}

          {mode === 'manual' ? (
            <div className="space-y-2 text-sm">
              <p className="text-xs text-slate-500">
                Manual input is a <strong>fact source</strong> — not the same as Manual Review treatment.
              </p>
              <label className="block">
                <span className="text-xs text-slate-500">Business label</span>
                <input
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                  value={manualLabel}
                  onChange={(e) => setManualLabel(e.target.value)}
                />
              </label>
              <label className="block">
                <span className="text-xs text-slate-500">Data type</span>
                <select
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                  value={manualType}
                  onChange={(e) => setManualType(e.target.value)}
                >
                  <option>Money</option>
                  <option>Number</option>
                  <option>Percent</option>
                  <option>Months</option>
                  <option>Boolean</option>
                  <option>Text</option>
                </select>
              </label>
              <label className="block">
                <span className="text-xs text-slate-500">Unit</span>
                <input
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                  value={manualUnit}
                  onChange={(e) => setManualUnit(e.target.value)}
                />
              </label>
              <label className="block">
                <span className="text-xs text-slate-500">Entered by</span>
                <input
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                  value={manualActor}
                  onChange={(e) => setManualActor(e.target.value)}
                />
              </label>
              <label className="block">
                <span className="text-xs text-slate-500">Guidance (optional)</span>
                <textarea
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                  rows={2}
                  value={manualGuidance}
                  onChange={(e) => setManualGuidance(e.target.value)}
                />
              </label>
              <button
                type="button"
                className="bt-btn bt-btn-primary bt-btn-sm"
                disabled={busy || !manualLabel.trim()}
                onClick={() => void confirmManual()}
              >
                Use manual input
              </button>
            </div>
          ) : null}

          {preview ? (
            <div
              className="rounded-lg border border-sky-200 bg-sky-50 px-3 py-3 text-sm"
              data-testid="resolver-preview"
            >
              <h3 className="font-semibold text-slate-900">Preview</h3>
              <dl className="mt-2 space-y-1 text-slate-800">
                <div>
                  <dt className="text-xs text-slate-500">Parameter</dt>
                  <dd>{String(preview.businessName ?? preview.id)}</dd>
                </div>
                <div>
                  <dt className="text-xs text-slate-500">Source</dt>
                  <dd>{String(preview.evaluatedFrom ?? '—')}</dd>
                </div>
                <div>
                  <dt className="text-xs text-slate-500">Type</dt>
                  <dd>{String(preview.type ?? '—')}</dd>
                </div>
                <div>
                  <dt className="text-xs text-slate-500">Availability</dt>
                  <dd>{String(preview.availability ?? '—')}</dd>
                </div>
                {preview.unit ? (
                  <div>
                    <dt className="text-xs text-slate-500">Unit</dt>
                    <dd>{String(preview.unit)}</dd>
                  </div>
                ) : null}
                {preview.period ? (
                  <div>
                    <dt className="text-xs text-slate-500">Period</dt>
                    <dd>{String(preview.period)}</dd>
                  </div>
                ) : null}
                {preview.calculationSummary ? (
                  <div>
                    <dt className="text-xs text-slate-500">How calculated</dt>
                    <dd>{String(preview.calculationSummary)}</dd>
                  </div>
                ) : null}
              </dl>
              <div className="mt-3 flex gap-2">
                <button
                  type="button"
                  className="bt-btn bt-btn-primary bt-btn-sm"
                  disabled={busy}
                  onClick={() => void confirmMap(preview)}
                >
                  Use in this policy
                </button>
                <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" onClick={() => setPreview(null)}>
                  Cancel
                </button>
              </div>
            </div>
          ) : null}
        </div>
      </div>
    </div>
  )
}
