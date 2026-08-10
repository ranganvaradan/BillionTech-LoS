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

function isKycCard(raw: unknown): boolean {
  const r = asRecord(raw)
  const d = String(r.decisionDomain ?? '').toUpperCase()
  return d === 'KYC' || d === 'ELIGIBILITY'
}

/** Plain-English KYC & Eligibility requirements — Decision Policy authoring only. */
export function CiPolicyKycTab({
  cards,
  domainBreakdown,
  analystMessage,
}: {
  cards: unknown[]
  domainBreakdown?: Record<string, unknown> | null
  analystMessage?: string | null
}) {
  const kycCards = cards.filter(isKycCard)
  const bd = domainBreakdown ?? {}

  return (
    <div className="space-y-4">
      <CiExecutiveSummary title="KYC & Eligibility">
        <p className="text-sm text-slate-700">
          {analystMessage ||
            `I identified ${Number(bd.kycEligibilityRequirements ?? kycCards.length)} KYC & Eligibility requirement${
              Number(bd.kycEligibilityRequirements ?? kycCards.length) === 1 ? '' : 's'
            } from this policy document.`}
        </p>
        <p className="mt-2 text-xs text-slate-500">
          Authoring only — does not change production KYC execution, provider routing, or the underwriting gate.
          Policy Studio asks what is required; Workflows / Integrations decide how it is executed.
        </p>
      </CiExecutiveSummary>

      <CiSection
        title="Requirements"
        description="Business language first. Technical DSL stays under Technical Details."
      >
        {kycCards.length === 0 ? (
          <CiEmptyState
            title="No KYC & Eligibility requirements identified"
            detail="Upload a Decision Policy with KYC clauses, or open the KYC validation sample from the landing page."
          />
        ) : (
          <ul className="space-y-4">
            {kycCards.map((raw) => {
              const r = asRecord(raw)
              const id = String(r.id ?? r.systemRuleId ?? '')
              const status = String(r.status ?? 'Needs Review')
              const dataUsed = asList(r.dataUsed)
              const guardrail = Boolean(r.platformGuardrail)
              const editable = r.studioEditable !== false && !guardrail

              return (
                <li key={id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div>
                      <div className="text-lg font-semibold text-slate-900">
                        {String(r.ruleName ?? 'KYC requirement')}
                      </div>
                      <div className="mt-1 flex flex-wrap gap-2 text-xs">
                        <span className="rounded-full bg-slate-100 px-2 py-0.5 font-medium text-slate-700">
                          {decisionPolicyDomainLabel(r.decisionDomain)}
                        </span>
                        {r.kycRequirementType ? (
                          <span className="rounded-full bg-indigo-50 px-2 py-0.5 font-medium text-indigo-900">
                            {kycRequirementTypeLabel(r.kycRequirementType)}
                          </span>
                        ) : null}
                        {guardrail ? (
                          <span className="rounded-full bg-amber-100 px-2 py-0.5 font-semibold text-amber-950">
                            Platform Guardrail · Protected
                          </span>
                        ) : null}
                      </div>
                    </div>
                    <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${statusChip(status)}`}>
                      {status}
                    </span>
                  </div>

                  {!editable ? (
                    <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950">
                      Platform Guardrail — displayed for awareness; not editable as an ordinary NBFC business rule.
                    </div>
                  ) : null}

                  {r.blockedReason ? (
                    <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-900">
                      {String(r.blockedReason)}
                    </div>
                  ) : null}

                  {r.matchCapabilityMissing ? (
                    <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950">
                      MATCH CAPABILITY REQUIRED — do not invent a match score.
                    </div>
                  ) : null}

                  <div className="mt-4 space-y-3 text-sm">
                    <div>
                      <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                        Policy requirement
                      </div>
                      <p className="mt-1 font-medium text-slate-900">{String(r.sourceClause ?? r.businessRule ?? '—')}</p>
                    </div>
                    <dl className="grid gap-3 sm:grid-cols-2">
                      <div>
                        <dt className="text-xs text-slate-500">Applies to</dt>
                        <dd className="font-medium">{String(r.productScope ?? 'All applicable applicants')}</dd>
                      </div>
                      <div>
                        <dt className="text-xs text-slate-500">Required data / verification</dt>
                        <dd className="mt-1 flex flex-wrap gap-1.5">
                          {dataUsed.map((d, i) => (
                            <span
                              key={i}
                              className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-700"
                            >
                              {String(d)}
                            </span>
                          ))}
                          {dataUsed.length === 0 ? <span className="text-slate-500">—</span> : null}
                        </dd>
                      </div>
                      <div>
                        <dt className="text-xs text-slate-500">If verified / satisfied</dt>
                        <dd className="font-medium text-emerald-800">{String(r.resultOnPass ?? 'Pass')}</dd>
                      </div>
                      <div>
                        <dt className="text-xs text-slate-500">If verification fails</dt>
                        <dd className="font-medium text-rose-800">{String(r.resultOnFailure ?? 'Fail')}</dd>
                      </div>
                      <div>
                        <dt className="text-xs text-slate-500">If provider / data unavailable</dt>
                        <dd className="font-medium text-amber-900">
                          {String(r.outcomeOnMissing ?? r.onMissing ?? 'Missing Information')}
                        </dd>
                      </div>
                      <div>
                        <dt className="text-xs text-slate-500">Manual review</dt>
                        <dd className="font-medium">
                          {r.manualReviewRequired ? 'Required (REFER)' : 'Not required'}
                        </dd>
                      </div>
                    </dl>
                  </div>

                  <CiTechnicalDetails>
                    {JSON.stringify(
                      {
                        systemRuleId: r.systemRuleId,
                        decisionDomain: r.decisionDomain,
                        kycRequirementType: r.kycRequirementType,
                        guardrailClass: r.guardrailClass,
                        expression: r.technicalExpression,
                        policySelectsProvider: false,
                        policyEnqueuesWorkflowStep: false,
                      },
                      null,
                      2,
                    )}
                  </CiTechnicalDetails>
                </li>
              )
            })}
          </ul>
        )}
      </CiSection>
    </div>
  )
}
