import { useMemo, useState } from 'react'
import {
  CiEmptyState,
  CiExecutiveSummary,
  CiSection,
  CiTechnicalDetails,
} from '@/components/creditIntelligence/CiSection'

type Filter = 'All' | 'Blocking' | 'Non-blocking' | 'Resolved'

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

export function CiPolicyAmbiguitiesTab({
  cards,
  categories,
  busy,
  onResolve,
  prospectDemoMode = false,
}: {
  cards: unknown[]
  categories: unknown[]
  busy: boolean
  onResolve: (ambiguityId: string, body: Record<string, unknown>) => Promise<void>
  prospectDemoMode?: boolean
}) {
  const [filter, setFilter] = useState<Filter>('All')
  const [category, setCategory] = useState<string>('All')
  const [selected, setSelected] = useState<Record<string, string>>({})
  const [remember, setRemember] = useState<Record<string, boolean>>({})
  const [scope, setScope] = useState<Record<string, string>>({})
  const [clauseOpen, setClauseOpen] = useState<Record<string, boolean>>({})

  const openCount = useMemo(
    () => cards.filter((c) => Boolean(asRecord(c).open)).length,
    [cards],
  )

  const filtered = useMemo(() => {
    return cards.filter((raw) => {
      const c = asRecord(raw)
      const isOpen = Boolean(c.open)
      const blocking = Boolean(c.blocking)
      if (filter === 'Blocking' && !(isOpen && blocking)) return false
      if (filter === 'Non-blocking' && !(isOpen && !blocking)) return false
      if (filter === 'Resolved' && isOpen) return false
      if (category !== 'All' && String(c.category ?? '') !== category) return false
      return true
    })
  }, [cards, filter, category])

  const categoryChips = useMemo(() => {
    const rows = categories.map((c) => asRecord(c))
    return rows.filter((r) => Number(r.count ?? 0) > 0)
  }, [categories])

  return (
    <div className="space-y-4">
      <CiExecutiveSummary title="Summary">
        <p>
          {openCount === 0
            ? 'No open items — proceed to proposed business rules or simulation.'
            : `${openCount} item${openCount === 1 ? '' : 's'} need Credit Head confirmation before rules can be approved.`}
        </p>
        {openCount > 0 ? (
          <p className="mt-2 text-sm font-medium text-amber-900">Next: resolve blocking items first.</p>
        ) : null}
      </CiExecutiveSummary>

      <CiSection
        title="Ambiguous business terms"
        description="Confirm unclear policy language. Decision required on each open item."
      >

        <div className="mb-3 flex flex-wrap gap-2">
          {categoryChips.map((c) => (
            <button
              key={String(c.category)}
              type="button"
              onClick={() =>
                setCategory((prev) => (prev === String(c.category) ? 'All' : String(c.category)))
              }
              className={`rounded-full px-3 py-1 text-xs font-semibold ${
                category === String(c.category)
                  ? 'bg-slate-900 text-white'
                  : 'bg-slate-100 text-slate-700'
              }`}
            >
              {String(c.category)} · {String(c.count)}
            </button>
          ))}
        </div>

        <div className="mb-4 flex flex-wrap gap-2">
          {(['All', 'Blocking', 'Non-blocking', 'Resolved'] as Filter[]).map((f) => (
            <button
              key={f}
              type="button"
              onClick={() => setFilter(f)}
              className={`rounded-md px-3 py-1.5 text-sm font-medium ${
                filter === f ? 'bg-sky-700 text-white' : 'bg-slate-100 text-slate-700'
              }`}
            >
              {f}
            </button>
          ))}
        </div>

        <ul className="space-y-4">
          {filtered.map((raw) => {
            const c = asRecord(raw)
            const id = String(c.id ?? '')
            const choices = asList(c.choices)
            const blocking = Boolean(c.blocking)
            const open = Boolean(c.open)
            const selectedValue =
              selected[id] ??
              String(c.recommendedOption ?? asRecord(choices[0]).value ?? '')
            const ctx = asRecord(c.policyContext)
            const bullets = asList(ctx.bullets)

            return (
              <li
                key={id || String(c.unclearTerm)}
                className={`rounded-xl border bg-white p-4 shadow-sm ${
                  blocking && open
                    ? 'border-rose-300 ring-1 ring-rose-100'
                    : open
                      ? 'border-amber-200'
                      : 'border-slate-200 opacity-90'
                }`}
              >
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                      Business term
                    </div>
                    <div className="mt-0.5 text-lg font-semibold text-slate-900">
                      {String(c.unclearTerm ?? '—')}
                    </div>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {blocking && open ? (
                      <span className="rounded-full bg-rose-100 px-2 py-0.5 text-xs font-semibold text-rose-800">
                        Blocking
                      </span>
                    ) : null}
                    <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-700">
                      {String(c.category ?? '')}
                    </span>
                    <span className="rounded-full bg-sky-50 px-2 py-0.5 text-xs font-semibold text-sky-800">
                      {String(c.typeLabel ?? '')}
                    </span>
                    {!open ? (
                      <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-semibold text-emerald-800">
                        Resolved
                      </span>
                    ) : null}
                  </div>
                </div>

                <div className="mt-4 grid gap-4 lg:grid-cols-2">
                  <div>
                    <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                      Source clause
                    </div>
                    <blockquote className="mt-1 rounded-lg border border-slate-100 bg-slate-50 px-3 py-2 text-sm text-slate-800">
                      {String(c.sourceClause ?? '—')}
                    </blockquote>
                    {bullets.length > 0 ? (
                      <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950">
                        <div className="font-semibold">{String(ctx.headline ?? '')}</div>
                        <ul className="mt-1 list-disc space-y-1 pl-5">
                          {bullets.map((b, i) => (
                            <li key={i}>{String(b)}</li>
                          ))}
                        </ul>
                      </div>
                    ) : null}
                  </div>
                  <div className="space-y-3 text-sm">
                    <div>
                      <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                        Why AI is unsure
                      </div>
                      <p className="mt-1 text-slate-800">{String(c.whyConfirmationNeeded ?? '')}</p>
                    </div>
                    <div>
                      <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                        AI understanding
                      </div>
                      <p className="mt-1 text-slate-800">{String(c.systemInterpretation ?? '')}</p>
                    </div>
                    <div className="flex flex-wrap gap-3 text-xs text-slate-600">
                      <span>
                        Recommended interpretation:{' '}
                        <strong className="text-slate-900">{String(c.recommendedLabel ?? '—')}</strong>
                      </span>
                      <span>
                        Confidence:{' '}
                        <strong className="text-slate-900">
                          {Math.round(Number(c.confidence ?? 0) * 100)}%
                        </strong>
                      </span>
                    </div>
                    <div>
                      <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                        Verified business mapping
                      </div>
                      <p className="mt-1 text-slate-800">{String(c.canonicalMappingBusiness ?? '—')}</p>
                    </div>
                    <div>
                      <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                        Business impact if unresolved
                      </div>
                      <p className="mt-1 text-rose-900">{String(c.impactIfUnresolved ?? '')}</p>
                    </div>
                    <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs font-semibold text-amber-950">
                      Decision required from Credit Head
                    </div>
                  </div>
                </div>

                {open ? (
                  <div className="mt-4 border-t border-slate-100 pt-4">
                    <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                      Alternative interpretations
                    </div>
                    <div className="mt-2 space-y-2">
                      {choices.map((ch) => {
                        const choice = asRecord(ch)
                        const value = String(choice.value ?? '')
                        return (
                          <label
                            key={value}
                            className={`flex cursor-pointer items-start gap-2 rounded-lg border px-3 py-2 text-sm ${
                              selectedValue === value
                                ? 'border-sky-400 bg-sky-50'
                                : 'border-slate-200 bg-white'
                            }`}
                          >
                            <input
                              type="radio"
                              name={`amb-${id}`}
                              className="mt-1"
                              checked={selectedValue === value}
                              onChange={() => setSelected((prev) => ({ ...prev, [id]: value }))}
                            />
                            <span>
                              <span className="font-medium text-slate-900">{String(choice.label ?? value)}</span>
                              {choice.recommended ? (
                                <span className="ml-2 rounded bg-emerald-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-emerald-800">
                                  Recommended
                                </span>
                              ) : null}
                              {choice.businessMapping ? (
                                <div className="mt-0.5 text-xs text-slate-500">
                                  {String(choice.businessMapping)}
                                </div>
                              ) : null}
                            </span>
                          </label>
                        )
                      })}
                    </div>

                    <label className="mt-4 flex items-start gap-2 text-sm text-slate-700">
                      <input
                        type="checkbox"
                        className="mt-1"
                        checked={Boolean(remember[id])}
                        onChange={(e) => setRemember((prev) => ({ ...prev, [id]: e.target.checked }))}
                      />
                      <span>
                        Remember this definition for future policies
                        <div className="mt-1 text-xs text-slate-500">
                          {String(
                            c.vocabularyNote ??
                              'Appears as a previous approved suggestion — not automatically authoritative.',
                          )}
                        </div>
                      </span>
                    </label>
                    {remember[id] ? (
                      <select
                        className="mt-2 rounded border border-slate-300 px-2 py-1.5 text-sm"
                        value={scope[id] ?? 'TENANT'}
                        onChange={(e) => setScope((prev) => ({ ...prev, [id]: e.target.value }))}
                      >
                        <option value="DOCUMENT">This Policy Only</option>
                        <option value="PRODUCT">This Product</option>
                        <option value="TENANT">This Lender/Tenant</option>
                      </select>
                    ) : null}

                    <div className="mt-4 flex flex-wrap gap-2">
                      <button
                        type="button"
                        disabled={busy}
                        className="bt-btn bt-btn-primary bt-btn-sm"
                        onClick={() =>
                          void onResolve(id, {
                            uiAction: 'ACCEPT_RECOMMENDATION',
                            resolvedOption: String(c.recommendedOption ?? selectedValue),
                            unclearTerm: c.unclearTerm,
                            rememberDefinition: Boolean(remember[id]),
                            rememberScope: scope[id] ?? 'TENANT',
                            notes: 'Accepted recommendation',
                          })
                        }
                      >
                        Accept Recommendation
                      </button>
                      <button
                        type="button"
                        disabled={busy || !selectedValue}
                        className="bt-btn bt-btn-secondary bt-btn-sm"
                        onClick={() =>
                          void onResolve(id, {
                            uiAction: 'SELECT_DIFFERENT_MEANING',
                            resolvedOption: selectedValue,
                            unclearTerm: c.unclearTerm,
                            rememberDefinition: Boolean(remember[id]),
                            rememberScope: scope[id] ?? 'TENANT',
                            notes: 'Selected alternate meaning',
                          })
                        }
                      >
                        Select Different Meaning
                      </button>
                      <button
                        type="button"
                        disabled={busy}
                        className="bt-btn bt-btn-secondary bt-btn-sm"
                        onClick={() =>
                          void onResolve(id, {
                            uiAction: 'CREATE_NEW_METRIC',
                            resolvedOption: 'CREATE_NEW_METRIC',
                            unclearTerm: c.unclearTerm,
                            notes:
                              'Business measure requested — open Data Readiness → Define Business Measure. Not implemented until executable & approved.',
                          })
                        }
                      >
                        Request business measure (opens designer)
                      </button>
                      <button
                        type="button"
                        disabled={busy}
                        className="bt-btn bt-btn-secondary bt-btn-sm"
                        onClick={() =>
                          void onResolve(id, {
                            uiAction: 'CREATE_POLICY_PARAMETER',
                            resolvedOption: 'CREATE_POLICY_PARAMETER',
                            unclearTerm: c.unclearTerm,
                            notes: 'Create policy parameter',
                          })
                        }
                      >
                        Create policy parameter
                      </button>
                      <button
                        type="button"
                        disabled={busy}
                        className="bt-btn bt-btn-secondary bt-btn-sm"
                        onClick={() =>
                          void onResolve(id, {
                            uiAction: 'ASK_CUSTOMER',
                            resolvedOption: 'ASK_CUSTOMER',
                            unclearTerm: c.unclearTerm,
                            notes: 'Ask customer / keep unresolved',
                          })
                        }
                      >
                        Ask Customer / Keep Unresolved
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="mt-3 text-sm text-emerald-800">
                    Resolved as: <strong>{String(c.resolvedOption ?? '—')}</strong>
                  </div>
                )}

                <button
                  type="button"
                  className="mt-3 text-xs font-medium text-sky-700 hover:underline"
                  onClick={() => setClauseOpen((prev) => ({ ...prev, [id]: !prev[id] }))}
                >
                  {clauseOpen[id] ? 'Hide original clause' : 'View Original Clause'}
                </button>
                {clauseOpen[id] ? (
                  <pre className="mt-2 whitespace-pre-wrap rounded border border-slate-200 bg-slate-50 p-3 text-xs text-slate-700">
                    {String(c.sourceClause ?? '')}
                    {c.pageOrSection ? `\n\nSection: ${String(c.pageOrSection)}` : ''}
                  </pre>
                ) : null}

                <CiTechnicalDetails hidden={prospectDemoMode}>
                  {JSON.stringify(
                    {
                      id,
                      resolutionStatus: c.resolutionStatus,
                      recommendedOption: c.recommendedOption,
                    },
                    null,
                    2,
                  )}
                </CiTechnicalDetails>
              </li>
            )
          })}
          {filtered.length === 0 ? (
            <li>
              <CiEmptyState
                title="No items match this filter"
                detail="Try All or Blocking, or continue to proposed business rules when open items are cleared."
              />
            </li>
          ) : null}
        </ul>
      </CiSection>
    </div>
  )
}
