import { useMemo, useState } from 'react'
import {
  CiEmptyState,
  CiExecutiveSummary,
  CiSection,
  CiTechnicalDetails,
} from '@/components/creditIntelligence/CiSection'
import {
  decisionPolicyDomainLabel,
  kycRequirementTypeLabel,
} from '@/lib/creditIntelligence/businessLexicon'

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

function statusChip(status: string): string {
  switch (status) {
    case 'Approved':
      return 'bg-emerald-100 text-emerald-900'
    case 'Ready':
      return 'bg-sky-100 text-sky-900'
    case 'Blocked':
      return 'bg-rose-100 text-rose-900'
    default:
      return 'bg-amber-100 text-amber-900'
  }
}

function VisualLogic({ visual }: { visual: Record<string, unknown> }) {
  const kind = String(visual.kind ?? 'SIMPLE')
  if (kind === 'EXCEPTION_ALL') {
    const conditions = asList(visual.conditions)
    return (
      <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-3 text-sm">
        <div className="font-semibold text-slate-900">{String(visual.title ?? 'Condition')}</div>
        <div className="mt-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
          {String(visual.subtitle ?? 'EXCEPTION allowed only if ALL:')}
        </div>
        <ul className="mt-2 space-y-1">
          {conditions.map((c, i) => (
            <li key={i} className="flex gap-2 text-slate-800">
              <span className="text-emerald-600" aria-hidden>
                ✓
              </span>
              <span>{String(c)}</span>
            </li>
          ))}
        </ul>
        <div className="mt-2 text-xs text-slate-600">THEN: {String(visual.then ?? '—')}</div>
      </div>
    )
  }
  if (kind === 'COMPOUND') {
    const conditions = asList(visual.conditions)
    return (
      <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-3 text-sm">
        <div className="text-xs font-semibold uppercase text-slate-500">IF all of</div>
        <ul className="mt-2 space-y-2">
          {conditions.map((c, i) => {
            const row = asRecord(c)
            return (
              <li key={i} className="flex flex-wrap items-center gap-2">
                <span className="rounded bg-white px-2 py-1 font-medium text-slate-800">
                  {String(row.left ?? '—')}
                </span>
                <span className="font-semibold text-slate-600">{String(row.operator ?? '')}</span>
                <span className="rounded bg-white px-2 py-1 font-medium text-slate-800">
                  {String(row.right ?? '—')}
                </span>
              </li>
            )
          })}
        </ul>
        <div className="mt-2 font-semibold text-slate-900">THEN {String(visual.then ?? 'Fail')}</div>
      </div>
    )
  }
  if (kind === 'BRANCH') {
    const iff = asRecord(visual.if)
    return (
      <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-3 text-sm">
        <div className="text-xs font-semibold uppercase text-slate-500">IF</div>
        <div className="mt-1 flex flex-wrap items-center gap-2">
          <span className="rounded bg-white px-2 py-1 font-medium">{String(iff.left ?? '—')}</span>
          <span className="font-semibold">{String(iff.operator ?? '')}</span>
          <span className="rounded bg-white px-2 py-1 font-medium">{String(iff.right ?? '—')}</span>
        </div>
        <div className="mt-2">THEN {String(visual.then ?? '—')}</div>
        <div className="mt-1">ELSE {String(visual.else ?? '—')}</div>
      </div>
    )
  }
  const iff = asRecord(visual.if)
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-3 text-sm">
      <div className="text-xs font-semibold uppercase text-slate-500">IF</div>
      <div className="mt-1 flex flex-wrap items-center gap-2">
        <span className="rounded bg-white px-2 py-1 font-medium text-slate-900">
          {String(iff.left ?? '—')}
        </span>
        <span className="font-semibold text-slate-700">{String(iff.operator ?? '')}</span>
        <span className="rounded bg-white px-2 py-1 font-medium text-slate-900">
          {String(iff.right ?? '—')}
        </span>
      </div>
      <div className="mt-2 font-semibold text-slate-900">THEN {String(visual.then ?? 'Fail')}</div>
    </div>
  )
}

type DomainFilter = 'ALL' | 'KYC' | 'CREDIT'

function domainOf(raw: unknown): DomainFilter {
  const d = String(asRecord(raw).decisionDomain ?? 'CREDIT').toUpperCase()
  if (d === 'KYC' || d === 'ELIGIBILITY') return 'KYC'
  return 'CREDIT'
}

export function CiPolicyRulesTab({
  cards,
  busy,
  onReview,
  onViewTests,
  onEditInterpretation,
  prospectDemoMode = false,
}: {
  cards: unknown[]
  busy: boolean
  onReview: (ruleId: string, body: Record<string, unknown>) => Promise<void>
  onViewTests?: () => void
  onEditInterpretation?: () => void
  prospectDemoMode?: boolean
}) {
  const [clauseOpen, setClauseOpen] = useState<Record<string, boolean>>({})
  const [domainFilter, setDomainFilter] = useState<DomainFilter>('ALL')
  const filtered = useMemo(() => {
    if (domainFilter === 'ALL') return cards
    return cards.filter((c) => domainOf(c) === domainFilter)
  }, [cards, domainFilter])
  const needReview = filtered.filter((c) => String(asRecord(c).status ?? '') !== 'Approved').length
  const hasKyc = cards.some((c) => domainOf(c) === 'KYC')

  return (
    <div className="space-y-4">
      <CiExecutiveSummary
        title="Summary"
        nextAction={
          needReview > 0 ? (
            <p className="text-sm font-medium text-amber-900">
              Review and approve proposed business rules before simulation.
            </p>
          ) : (
            <button type="button" className="bt-btn bt-btn-primary bt-btn-sm" onClick={() => onViewTests?.()}>
              Review tests
            </button>
          )
        }
      >
        <p>
          {filtered.length} proposed business rule{filtered.length === 1 ? '' : 's'} · {needReview} still need Credit Head
          confirmation.
        </p>
      </CiExecutiveSummary>

      {hasKyc ? (
        <div className="flex flex-wrap gap-2">
          {(
            [
              ['ALL', 'All'],
              ['KYC', 'KYC & Eligibility'],
              ['CREDIT', 'Credit'],
            ] as const
          ).map(([id, label]) => (
            <button
              key={id}
              type="button"
              onClick={() => setDomainFilter(id)}
              className={`rounded-full px-3 py-1 text-xs font-semibold ${
                domainFilter === id
                  ? 'bg-slate-900 text-white'
                  : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
              }`}
            >
              {label}
            </button>
          ))}
        </div>
      ) : null}

      <CiSection
        title="Proposed business rules"
        description="Business purpose and impact first. Supporting technical detail stays collapsed."
      >
        {filtered.length === 0 ? (
          <CiEmptyState title="No rules in this filter" detail="Try another domain filter." />
        ) : null}
        <ul className="space-y-4">
          {filtered.map((raw) => {
            const r = asRecord(raw)
            const id = String(r.id ?? r.systemRuleId ?? '')
            const status = String(r.status ?? 'Needs Review')
            const dataUsed = asList(r.dataUsed)
            const visual = asRecord(r.visualLogic)
            const purpose =
              String(r.businessPurpose ?? r.whyThisRule ?? r.rationale ?? r.businessRule ?? 'Supports consistent underwriting for this product.')
            const impact = String(
              r.businessImpact ?? r.impact ?? r.resultOnFailure ?? 'Affects applicants who fail this condition.',
            )
            const who = String(r.whoIsAffected ?? r.productScope ?? 'Applicants in the stated product scope')
            const isKyc = domainOf(raw) === 'KYC'

            return (
              <li key={id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <div className="text-lg font-semibold text-slate-900">{String(r.ruleName ?? 'Proposed business rule')}</div>
                    <div className="mt-1 flex flex-wrap items-center gap-2 text-sm text-slate-600">
                      <span>
                        Who is affected: <strong>{who}</strong>
                      </span>
                      {isKyc ? (
                        <>
                          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium">
                            {decisionPolicyDomainLabel(r.decisionDomain)}
                          </span>
                          {r.kycRequirementType ? (
                            <span className="rounded-full bg-indigo-50 px-2 py-0.5 text-xs font-medium text-indigo-900">
                              {kycRequirementTypeLabel(r.kycRequirementType)}
                            </span>
                          ) : null}
                          {r.platformGuardrail ? (
                            <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-semibold text-amber-950">
                              Platform Guardrail
                            </span>
                          ) : null}
                        </>
                      ) : null}
                    </div>
                  </div>
                  <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${statusChip(status)}`}>
                    {status}
                  </span>
                </div>

                {r.blockedReason ? (
                  <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-900">
                    Action required: {String(r.blockedReason)}
                  </div>
                ) : null}

                <div className="mt-4 grid gap-3 rounded-lg border border-sky-100 bg-sky-50/50 px-3 py-3 text-sm sm:grid-cols-3">
                  <div>
                    <div className="text-xs font-semibold uppercase tracking-wide text-sky-800">Why is this rule here?</div>
                    <p className="mt-1 text-slate-800">{purpose}</p>
                  </div>
                  <div>
                    <div className="text-xs font-semibold uppercase tracking-wide text-sky-800">Data required</div>
                    <div className="mt-1 flex flex-wrap gap-1.5">
                      {dataUsed.map((d, i) => (
                        <span key={i} className="rounded-full bg-white px-2 py-0.5 text-xs font-medium text-slate-700">
                          {String(d)}
                        </span>
                      ))}
                      {dataUsed.length === 0 ? <span className="text-xs text-slate-500">See policy clause</span> : null}
                    </div>
                  </div>
                  <div>
                    <div className="text-xs font-semibold uppercase tracking-wide text-sky-800">Business impact</div>
                    <p className="mt-1 text-slate-800">{impact}</p>
                  </div>
                </div>

                <div className="mt-4 grid gap-4 lg:grid-cols-2">
                  <div className="space-y-3 text-sm">
                    <div>
                      <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">Business rule</div>
                      <p className="mt-1 font-medium text-slate-900">{String(r.businessRule ?? '—')}</p>
                    </div>
                    <dl className="grid grid-cols-2 gap-3">
                      <div>
                        <dt className="text-xs text-slate-500">Period</dt>
                        <dd className="font-medium">{String(r.period ?? '—')}</dd>
                      </div>
                      <div>
                        <dt className="text-xs text-slate-500">If information is missing</dt>
                        <dd className="font-medium">{String(r.onMissing ?? '—')}</dd>
                      </div>
                      <div>
                        <dt className="text-xs text-slate-500">Result when rule fails</dt>
                        <dd className="font-medium">{String(r.resultOnFailure ?? '—')}</dd>
                      </div>
                      <div>
                        <dt className="text-xs text-slate-500">Information family</dt>
                        <dd className="font-medium">{String(r.dataFamily ?? '—')}</dd>
                      </div>
                    </dl>
                  </div>
                  <div>
                    <div className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Rule logic</div>
                    <VisualLogic visual={visual} />
                  </div>
                </div>

                <div className="mt-4 flex flex-wrap gap-2">
                  <button
                    type="button"
                    disabled={busy || status === 'Approved' || Boolean(r.platformGuardrail)}
                    className="bt-btn bt-btn-primary bt-btn-sm"
                    title={r.platformGuardrail ? 'Platform guardrails are not editable as ordinary rules' : undefined}
                    onClick={() =>
                      void onReview(id, {
                        uiAction: 'APPROVE',
                        reason: 'Approved by Credit Head in Policy Studio',
                      })
                    }
                  >
                    Approve rule
                  </button>
                  <button
                    type="button"
                    disabled={busy}
                    className="bt-btn bt-btn-secondary bt-btn-sm"
                    onClick={() => onEditInterpretation?.()}
                  >
                    Edit AI understanding
                  </button>
                  <button
                    type="button"
                    disabled={busy}
                    className="bt-btn bt-btn-secondary bt-btn-sm"
                    onClick={() => onEditInterpretation?.()}
                  >
                    Change verified mapping
                  </button>
                  <button
                    type="button"
                    disabled={busy}
                    className="bt-btn bt-btn-secondary bt-btn-sm"
                    onClick={() => onViewTests?.()}
                  >
                    View tests
                  </button>
                  <button
                    type="button"
                    disabled={busy || status === 'Approved'}
                    className="bt-btn bt-btn-secondary bt-btn-sm"
                    onClick={() =>
                      void onReview(id, {
                        uiAction: 'REJECT',
                        reason: 'Rejected by Credit Head in Policy Studio',
                      })
                    }
                  >
                    Reject rule
                  </button>
                  <button
                    type="button"
                    className="bt-btn bt-btn-secondary bt-btn-sm"
                    onClick={() => setClauseOpen((prev) => ({ ...prev, [id]: !prev[id] }))}
                  >
                    View original clause
                  </button>
                </div>

                {clauseOpen[id] ? (
                  <blockquote className="mt-3 whitespace-pre-wrap rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-800">
                    {String(r.sourceClause ?? '—')}
                    {r.section ? `\n\nSection: ${String(r.section)}` : ''}
                  </blockquote>
                ) : null}

                <CiTechnicalDetails hidden={prospectDemoMode}>
                  {JSON.stringify(
                    {
                      systemRuleId: r.systemRuleId,
                      reviewStatus: r.reviewStatus,
                      expression: r.technicalExpression,
                    },
                    null,
                    2,
                  )}
                </CiTechnicalDetails>
              </li>
            )
          })}
          {cards.length === 0 ? (
            <li>
              <CiEmptyState
                title="No proposed business rules yet"
                detail="Resolve ambiguous terms first, or re-open the policy so AI understanding can produce rules for review."
              />
            </li>
          ) : null}
        </ul>
      </CiSection>
    </div>
  )
}
