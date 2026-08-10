import { useMemo, useState } from 'react'
import {
  CiEmptyState,
  CiExecutiveSummary,
  CiSection,
  CiTechnicalDetails,
} from '@/components/creditIntelligence/CiSection'
import { CiBusinessMeasureDesigner } from '@/pages/creditIntelligence/CiBusinessMeasureDesigner'

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

function statusTone(status: string): string {
  switch (status) {
    case 'READY':
    case 'PLATFORM_GUARDRAIL':
      return 'bg-emerald-100 text-emerald-900'
    case 'READY_WITH_FALLBACK':
      return 'bg-sky-100 text-sky-900'
    case 'MANUAL_INPUT_REQUIRED':
    case 'MANUAL_VERIFICATION':
      return 'bg-amber-100 text-amber-950'
    case 'DEFINITION_REQUIRED':
    case 'BUSINESS_DEFINITION_REQUIRED':
    case 'MAPPING_REQUIRED':
    case 'METRIC_REQUIRED':
    case 'RECONCILIATION_REQUIRED':
    case 'DATA_SOURCE_REQUIRED':
    case 'APPLICATION_INPUT_REQUIRED':
    case 'MATCH_CAPABILITY_REQUIRED':
    case 'WORKFLOW_CONFIGURATION_REQUIRED':
    case 'INTEGRATION_CONFIGURATION_REQUIRED':
    case 'MANUAL_CONFIGURATION_REQUIRED':
      return 'bg-orange-100 text-orange-950'
    case 'NOT_IMPLEMENTABLE':
    case 'ENGINEERING_REQUIRED':
      return 'bg-rose-100 text-rose-950'
    default:
      return 'bg-slate-100 text-slate-800'
  }
}

function bandTone(band: string): string {
  if (band.includes('READY') && !band.includes('NEEDS')) return 'text-emerald-800'
  if (band.includes('MOSTLY')) return 'text-sky-800'
  if (band.includes('NEEDS')) return 'text-amber-800'
  return 'text-rose-800'
}

type FilterId = 'ALL' | 'READY' | 'FALLBACK' | 'MANUAL' | 'ATTENTION' | 'BLOCKING'
type CategoryId = 'All' | 'Bureau' | 'Banking' | 'GST' | 'ITR' | 'Application' | 'KYC' | 'Financial' | 'Other'

function matchesFilter(status: string, filter: FilterId, critical?: boolean): boolean {
  if (filter === 'ALL') return true
  if (filter === 'READY') return status === 'READY'
  if (filter === 'FALLBACK') return status === 'READY_WITH_FALLBACK'
  if (filter === 'MANUAL') return status === 'MANUAL_INPUT_REQUIRED' || status === 'MANUAL_VERIFICATION'
  if (filter === 'BLOCKING') {
    return (
      critical === true &&
      ['DATA_SOURCE_REQUIRED', 'MAPPING_REQUIRED', 'METRIC_REQUIRED', 'DEFINITION_REQUIRED', 'NOT_IMPLEMENTABLE'].includes(
        status,
      )
    )
  }
  // ATTENTION
  return ![
    'READY',
    'READY_WITH_FALLBACK',
    'MANUAL_INPUT_REQUIRED',
    'MANUAL_VERIFICATION',
  ].includes(status)
}

function categoryOf(row: Record<string, unknown>): string {
  const cat = String(row.category ?? '')
  if (cat) return cat
  const code = String(row.dataElementCode ?? row.dataNeeded ?? '').toLowerCase()
  if (code.startsWith('bureau')) return 'Bureau'
  if (code.startsWith('banking') || code.startsWith('bank.')) return 'Banking'
  if (code.startsWith('gst')) return 'GST'
  if (code.startsWith('itr')) return 'ITR'
  if (code.startsWith('application') || code.startsWith('kyc')) return code.startsWith('kyc') ? 'KYC' : 'Application'
  if (code.includes('turnover') || code.includes('emi') || code.includes('edi')) return 'Financial'
  return 'Other'
}

export function CiPolicyDataReadinessTab({
  session,
  prospectDemoMode,
  onGoApprovals,
  documentId,
  busy,
  setBusy,
  onError,
  onSessionUpdate,
}: {
  session: Record<string, unknown>
  prospectDemoMode: boolean
  onGoApprovals?: () => void
  documentId?: string
  busy?: boolean
  setBusy?: (v: boolean) => void
  onError?: (msg: string | null) => void
  onSessionUpdate?: (session: Record<string, unknown>) => void
}) {
  const impl = asRecord(session.implementability)
  const summary = asRecord(impl.summary ?? session.implementabilitySummary)
  const [filter, setFilter] = useState<FilterId>('ALL')
  const [category, setCategory] = useState<CategoryId>('All')
  const [expandedRule, setExpandedRule] = useState<string | null>(null)
  const [designerCode, setDesignerCode] = useState<string | null>(null)

  const pct = Number(summary.implementationReadinessPercent ?? 0)
  const band = String(summary.readinessBand ?? '—')
  const rules = asList(impl.rules)
  const gaps = asList(impl.gaps)
  const nextActions = asList(impl.nextActions)
  const matrix = asList(impl.requirementMatrix)
  const sourcesRaw = asList(impl.decisionPolicyDataSources)
  const sources = sourcesRaw.length > 0 ? sourcesRaw : asList(impl.dataSources)
  const analystMessage = String(impl.analystMessage ?? '')
  const domainCards = asList(impl.decisionPolicyDomainReadiness ?? impl.decisionPolicySections)
  const kycRequirements = asList(impl.kycRequirementReadiness)
  const overall = asRecord(impl.decisionPolicyOverall)
  const demoLabel = String(overall.demoLabel ?? '')
  const designNote = String(impl.designTimeVsRuntimeNote ?? '')

  const filteredMatrix = useMemo(() => {
    return matrix.filter((raw) => {
      const row = asRecord(raw)
      const status = String(row.status ?? '')
      if (!matchesFilter(status, filter, Boolean(row.critical))) return false
      if (category !== 'All' && categoryOf(row) !== category) return false
      return true
    })
  }, [matrix, filter, category])

  if (!impl.summary && Object.keys(summary).length === 0) {
    return (
      <CiEmptyState
        title="Data readiness not available"
        detail="Open a policy session to assess whether configured data sources can execute the proposed rules."
      />
    )
  }

  const cards = [
    { label: 'Ready', value: Number(summary.ready ?? 0), tone: 'text-emerald-800' },
    { label: 'Ready with fallback', value: Number(summary.readyWithFallback ?? 0), tone: 'text-sky-800' },
    { label: 'Needs clarification', value: Number(summary.needsClarification ?? 0), tone: 'text-amber-800' },
    { label: 'Manual verification', value: Number(summary.manualVerification ?? 0), tone: 'text-amber-900' },
    { label: 'Missing data', value: Number(summary.missingData ?? 0), tone: 'text-orange-900' },
    { label: 'Blocked', value: Number(summary.blocked ?? 0), tone: 'text-rose-800' },
  ]

  return (
    <div className="space-y-4">
      <CiExecutiveSummary
        title="What should I do next?"
        nextAction={
          nextActions.length > 0 ? (
            <span className="text-sm text-slate-700">
              {nextActions.length} action{nextActions.length === 1 ? '' : 's'} required before this policy can be
              implemented.
            </span>
          ) : onGoApprovals ? (
            <button type="button" className="bt-btn bt-btn-primary bt-btn-sm" onClick={onGoApprovals}>
              Continue to Approvals
            </button>
          ) : null
        }
      >
        <p>
          {analystMessage ||
            String(summary.headline ?? 'Assess whether this NBFC can implement the policy with configured data.')}
        </p>
      </CiExecutiveSummary>

      <section className="rounded-2xl border border-slate-200 bg-gradient-to-br from-slate-50 via-white to-sky-50 px-6 py-6 shadow-sm">
        <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">
          Decision Policy Implementation Readiness
        </p>
        {demoLabel ? (
          <p className="mt-1 text-xs font-semibold uppercase tracking-wide text-amber-800">{demoLabel}</p>
        ) : null}
        <div className="mt-2 flex flex-wrap items-end gap-4">
          <div className={`text-6xl font-bold tabular-nums ${bandTone(band)}`}>{pct}%</div>
          <div className="pb-2">
            <div className={`text-xl font-semibold ${bandTone(band)}`}>
              {String(overall.status ?? band)}
            </div>
            <p className="mt-1 max-w-xl text-sm text-slate-700">
              {Number(summary.ready ?? overall.ready ?? 0) + Number(summary.readyWithFallback ?? 0)} of{' '}
              {Number(summary.rulesIdentified ?? overall.requirements ?? 0)} policy rules can be executed using your
              currently configured data
              {Number(summary.distinctDataElements ?? 0) > 0
                ? ` (${Number(summary.distinctDataElements)} distinct business data elements).`
                : '.'}
            </p>
          </div>
        </div>
        <div className="mt-4 flex flex-wrap gap-4 text-sm text-slate-700">
          <span>
            Automation coverage <strong>{Number(summary.automationCoveragePercent ?? 0)}%</strong>
          </span>
          <span>
            Manual review <strong>{Number(summary.manualReviewPercent ?? 0)}%</strong>
          </span>
          <span>
            Currently blocked <strong>{Number(summary.currentlyBlockedPercent ?? 0)}%</strong>
          </span>
        </div>
        {designNote ? <p className="mt-3 text-xs text-slate-500">{designNote}</p> : null}
      </section>

      {domainCards.length > 0 ? (
        <CiSection
          title="Domain readiness"
          description="Policy design readiness by Decision Policy domain — not application runtime completeness."
        >
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {domainCards.map((raw, i) => {
              const d = asRecord(raw)
              const req = Number(d.requirements ?? d.registeredFactCount ?? 0)
              if (req <= 0 && !d.scorecardLinkage) return null
              return (
                <div key={i} className="rounded-xl border border-slate-200 bg-white px-4 py-4 shadow-sm">
                  <div className="text-sm font-semibold text-slate-900">
                    {String(d.sectionName ?? d.sectionCode ?? 'Domain')}
                  </div>
                  <div className={`mt-1 text-xs font-semibold uppercase ${statusTone(String(d.status ?? ''))}`}>
                    {String(d.status ?? '—').replace(/_/g, ' ')}
                  </div>
                  <div className="mt-3 grid grid-cols-2 gap-2 text-xs text-slate-700">
                    <div>
                      <div className="text-lg font-bold tabular-nums text-slate-900">{req}</div>
                      Requirements
                    </div>
                    <div>
                      <div className="text-lg font-bold tabular-nums text-emerald-800">
                        {Number(d.ready ?? d.supportedFactCount ?? 0)}
                      </div>
                      Ready
                    </div>
                    <div>
                      <div className="text-lg font-bold tabular-nums text-amber-800">{Number(d.manual ?? 0)}</div>
                      Manual
                    </div>
                    <div>
                      <div className="text-lg font-bold tabular-nums text-rose-800">
                        {Number(d.blocked ?? 0) + Number(d.needsAttention ?? 0)}
                      </div>
                      Needs attention / blocked
                    </div>
                  </div>
                  {d.detail ? <p className="mt-2 text-xs text-slate-500">{String(d.detail)}</p> : null}
                  {d.scorecardLinkage ? (
                    <p className="mt-2 text-xs text-slate-500">
                      Scorecard: {String(d.scorecardLinkage).replace(/_/g, ' ')}
                    </p>
                  ) : null}
                </div>
              )
            })}
          </div>
        </CiSection>
      ) : null}

      {kycRequirements.length > 0 ? (
        <CiSection
          title="KYC & Eligibility readiness"
          description="Policy requirement vs workflow capability vs integration — design-time only."
        >
          <div className="overflow-x-auto rounded-lg border border-slate-200">
            <table className="min-w-full text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-3 py-2">Requirement</th>
                  <th className="px-3 py-2">Data / verification</th>
                  <th className="px-3 py-2">Workflow</th>
                  <th className="px-3 py-2">Primary source</th>
                  <th className="px-3 py-2">Fallback</th>
                  <th className="px-3 py-2">Status</th>
                  <th className="px-3 py-2">Action</th>
                </tr>
              </thead>
              <tbody>
                {kycRequirements.map((raw, i) => {
                  const row = asRecord(raw)
                  return (
                    <tr key={i} className="border-t border-slate-100 align-top">
                      <td className="px-3 py-2 font-medium text-slate-900">
                        {String(row.requirement ?? '—')}
                        {row.critical ? (
                          <span className="ml-2 rounded bg-rose-100 px-1.5 py-0.5 text-[10px] font-semibold text-rose-900">
                            Critical
                          </span>
                        ) : null}
                      </td>
                      <td className="px-3 py-2 text-slate-700">{String(row.dataNeeded ?? '—')}</td>
                      <td className="px-3 py-2 text-slate-700">{String(row.workflowCapability ?? '—')}</td>
                      <td className="px-3 py-2 text-slate-700">{String(row.primarySource ?? '—')}</td>
                      <td className="px-3 py-2 text-slate-700">{String(row.fallbackSource ?? '—')}</td>
                      <td className="px-3 py-2">
                        <span
                          className={`rounded-full px-2 py-0.5 text-xs font-semibold ${statusTone(String(row.status ?? ''))}`}
                        >
                          {String(row.statusDisplay ?? row.status ?? '—').replace(/_/g, ' ')}
                        </span>
                      </td>
                      <td className="max-w-xs px-3 py-2 text-xs text-slate-600">{String(row.action ?? '—')}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </CiSection>
      ) : null}

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
        {cards.map((c) => (
          <div key={c.label} className="rounded-xl border border-slate-200 bg-white px-4 py-3 shadow-sm">
            <div className={`text-2xl font-bold tabular-nums ${c.tone}`}>{c.value}</div>
            <div className="mt-1 text-xs font-semibold uppercase tracking-wide text-slate-500">{c.label}</div>
          </div>
        ))}
      </div>

      <CiSection
        title="Critical data coverage"
        description="Knockout / hard eligibility / decision-critical rules require resolvable data before draft readiness."
      >
        <div className="flex flex-wrap gap-6 text-sm">
          <div>
            <div className="text-2xl font-bold text-slate-900">{Number(summary.criticalRules ?? 0)}</div>
            <div className="text-xs uppercase text-slate-500">Critical rules</div>
          </div>
          <div>
            <div className="text-2xl font-bold text-emerald-800">
              {Number(summary.criticalFullyImplementable ?? 0)}
            </div>
            <div className="text-xs uppercase text-slate-500">Fully implementable</div>
          </div>
          <div>
            <div className="text-2xl font-bold text-rose-800">{Number(summary.criticalBlocked ?? 0)}</div>
            <div className="text-xs uppercase text-slate-500">Blocked</div>
          </div>
        </div>
        {Boolean(summary.draftBlockedByCriticalDataGap) || Boolean(summary.draftBlockedByCriticalKycGap) ? (
          <p className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-950">
            {Boolean(summary.draftBlockedByCriticalKycGap)
              ? 'Decision Policy draft readiness is blocked by unresolved critical KYC / eligibility data gaps.'
              : 'Policy cannot be marked implementation-ready while a blocking critical rule has unresolved data requirements.'}
          </p>
        ) : (
          <p className="mt-3 text-sm text-emerald-800">No critical-rule data blockers for draft readiness.</p>
        )}
      </CiSection>

      <CiSection title="Data sources" description="Primary sources for this NBFC deployment — fallback is for outage only.">
        {sources.length === 0 ? (
          <p className="text-sm text-slate-600">No sourced data elements referenced yet.</p>
        ) : (
          <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200 bg-white">
            {sources.map((raw, i) => {
              const s = asRecord(raw)
              return (
                <li key={i} className="flex flex-wrap items-center gap-3 px-4 py-3 text-sm">
                  <span className="min-w-[7rem] font-semibold text-slate-900">
                    {String(s.category ?? s.family ?? 'Source')}
                  </span>
                  <span className="text-slate-700">{String(s.primarySource ?? s.source ?? '—')}</span>
                  <span className="rounded bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-700">
                    {String(s.role ?? 'PRIMARY')}
                  </span>
                  <span
                    className={`ml-auto rounded-full px-2 py-0.5 text-xs font-semibold ${
                      String(s.availability ?? '').toLowerCase().includes('available') &&
                      !String(s.availability ?? '').toLowerCase().includes('partial')
                        ? 'bg-emerald-100 text-emerald-900'
                        : 'bg-amber-100 text-amber-950'
                    }`}
                  >
                    {String(s.availabilityLabel ?? s.availability ?? '—')}
                  </span>
                  {s.fallbackSource ? (
                    <span className="w-full text-xs text-slate-500">
                      Fallback: {String(s.fallbackSource)} (used only if primary unavailable)
                    </span>
                  ) : null}
                </li>
              )
            })}
          </ul>
        )}
      </CiSection>

      {nextActions.length > 0 ? (
        <CiSection title="Recommended next actions" description="Deterministic actions from data gaps — not AI guesses.">
          <ol className="space-y-3">
            {nextActions.map((raw, i) => {
              const a = asRecord(raw)
              const options = asList(a.options)
              return (
                <li key={i} className="rounded-xl border border-slate-200 bg-white px-4 py-3 shadow-sm">
                  <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                    Action {i + 1}
                  </div>
                  <div className="mt-1 text-base font-semibold text-slate-900">
                    {String(a.title ?? a.dataElement ?? 'Gap')}
                  </div>
                  <p className="mt-1 text-sm text-slate-700">{String(a.detail ?? '')}</p>
                  {a.recommended ? (
                    <p className="mt-1 text-sm font-medium text-slate-900">
                      Recommended: {String(a.recommended)}
                    </p>
                  ) : null}
                  {options.length > 0 ? (
                    <ul className="mt-2 list-decimal space-y-1 pl-5 text-sm text-slate-700">
                      {options.map((o, j) => (
                        <li key={j}>{String(o)}</li>
                      ))}
                    </ul>
                  ) : null}
                  {a.dependsOnRules ? (
                    <p className="mt-2 text-xs text-slate-500">
                      Required by: {asList(a.dependsOnRules).map(String).join(', ')}
                    </p>
                  ) : null}
                </li>
              )
            })}
          </ol>
        </CiSection>
      ) : null}

      <CiSection title="Data gaps" description="Grouped by what is missing — options shown, not auto-chosen.">
        {gaps.length === 0 ? (
          <p className="text-sm text-emerald-800">No material data gaps identified for the current rule set.</p>
        ) : (
          <div className="space-y-3">
            {gaps.map((raw, i) => {
              const g = asRecord(raw)
              const deps = asList(g.dependentRules)
              const options = asList(g.resolutionOptions ?? g.options)
              const code = String(g.dataElementCode ?? '')
              const canDefine =
                Boolean(g.canDefineBusinessMeasure) ||
                String(g.designerAction) === 'DEFINE_BUSINESS_MEASURE' ||
                String(g.designerAction) === 'APPLICATION_INPUT_REQUIRED'
              return (
                <div key={i} className="rounded-xl border border-slate-200 bg-white px-4 py-3">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-semibold text-slate-900">
                      {String(g.businessName ?? g.whatIsMissing ?? g.title)}
                    </span>
                    <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-700">
                      {String(g.classification ?? 'OTHER')}
                    </span>
                    <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${statusTone(String(g.status ?? ''))}`}>
                      {String(g.gapType ?? g.status ?? 'GAP')}
                    </span>
                    {g.blocksPolicy ? (
                      <span className="rounded-full bg-rose-100 px-2 py-0.5 text-xs font-semibold text-rose-900">
                        Blocks draft
                      </span>
                    ) : null}
                  </div>
                  <p className="mt-1 text-sm text-slate-700">{String(g.detail ?? g.whatIsMissing ?? '')}</p>
                  {deps.length > 0 ? (
                    <p className="mt-1 text-xs text-slate-500">
                      Required by {deps.length} rule{deps.length === 1 ? '' : 's'}:{' '}
                      {deps.slice(0, 4).map((d) => String(asRecord(d).ruleName ?? d)).join(', ')}
                      {deps.length > 4 ? '…' : ''}
                    </p>
                  ) : null}
                  {options.length > 0 ? (
                    <ul className="mt-2 list-decimal space-y-1 pl-5 text-sm text-slate-700">
                      {options.map((o, j) => (
                        <li key={j}>{String(o)}</li>
                      ))}
                    </ul>
                  ) : null}
                  {canDefine && documentId && code && code !== '__UNMAPPED__' && code !== 'null' ? (
                    <button
                      type="button"
                      className="bt-btn bt-btn-primary bt-btn-sm mt-3"
                      onClick={() => setDesignerCode(code)}
                    >
                      {String(g.designerAction) === 'APPLICATION_INPUT_REQUIRED'
                        ? 'Define application input'
                        : 'Define business measure'}
                    </button>
                  ) : null}
                </div>
              )
            })}
          </div>
        )}
      </CiSection>

      <CiSection title="Data requirement matrix" description="Policy requirement → data → source → availability.">
        <div className="mb-3 flex flex-wrap gap-2">
          {(
            [
              ['ALL', 'All'],
              ['READY', 'Ready'],
              ['FALLBACK', 'Fallback'],
              ['MANUAL', 'Manual'],
              ['ATTENTION', 'Needs attention'],
              ['BLOCKING', 'Blocking'],
            ] as [FilterId, string][]
          ).map(([id, label]) => (
            <button
              key={id}
              type="button"
              onClick={() => setFilter(id)}
              className={`rounded-full px-3 py-1 text-xs font-semibold ${
                filter === id ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-700'
              }`}
            >
              {label}
            </button>
          ))}
        </div>
        <div className="mb-3 flex flex-wrap gap-2">
          {(
            ['All', 'Bureau', 'Banking', 'GST', 'ITR', 'Application', 'KYC', 'Financial', 'Other'] as CategoryId[]
          ).map((c) => (
            <button
              key={c}
              type="button"
              onClick={() => setCategory(c)}
              className={`rounded-full px-2.5 py-1 text-xs font-medium ${
                category === c ? 'bg-sky-700 text-white' : 'bg-sky-50 text-sky-900'
              }`}
            >
              {c}
            </button>
          ))}
        </div>
        <div className="overflow-x-auto rounded-lg border border-slate-200">
          <table className="min-w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-3 py-2">Policy requirement</th>
                <th className="px-3 py-2">Data needed</th>
                <th className="px-3 py-2">Primary source</th>
                <th className="px-3 py-2">Fallback</th>
                <th className="px-3 py-2">Availability</th>
                <th className="px-3 py-2">Status</th>
              </tr>
            </thead>
            <tbody>
              {filteredMatrix.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-3 py-4 text-slate-600">
                    No rows for this filter.
                  </td>
                </tr>
              ) : (
                filteredMatrix.map((raw, i) => {
                  const row = asRecord(raw)
                  return (
                    <tr key={i} className="border-t border-slate-100">
                      <td className="px-3 py-2 font-medium text-slate-900">
                        {String(row.policyRequirement ?? row.ruleName ?? '—')}
                      </td>
                      <td className="px-3 py-2 text-slate-700">
                        {String(row.dataNeeded ?? row.businessName ?? '—')}
                      </td>
                      <td className="px-3 py-2 text-slate-700">{String(row.primarySource ?? '—')}</td>
                      <td className="px-3 py-2 text-slate-600">
                        {String(row.fallback ?? row.fallbackSource ?? '—')}
                      </td>
                      <td className="px-3 py-2">{String(row.availability ?? '—')}</td>
                      <td className="px-3 py-2">
                        <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${statusTone(String(row.status ?? ''))}`}>
                          {String(row.status ?? '—')}
                        </span>
                      </td>
                    </tr>
                  )
                })
              )}
            </tbody>
          </table>
        </div>
      </CiSection>

      <CiSection title="Rule-level data readiness" description="Each proposed business rule and its data path.">
        <ul className="space-y-3">
          {rules.map((raw, i) => {
            const r = asRecord(raw)
            const id = String(r.ruleId ?? r.systemRuleId ?? i)
            const open = expandedRule === id
            const reqs = asList(r.requirements)
            return (
              <li key={id} className="rounded-xl border border-slate-200 bg-white shadow-sm">
                <button
                  type="button"
                  className="flex w-full flex-wrap items-center gap-2 px-4 py-3 text-left"
                  onClick={() => setExpandedRule(open ? null : id)}
                >
                  <span className="font-semibold text-slate-900">{String(r.ruleName ?? 'Business rule')}</span>
                  {r.critical ? (
                    <span className="rounded bg-rose-50 px-1.5 py-0.5 text-[10px] font-bold uppercase text-rose-800">
                      Critical
                    </span>
                  ) : null}
                  <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${statusTone(String(r.implementability ?? ''))}`}>
                    {String(r.implementability ?? '—')}
                  </span>
                  <span className="ml-auto text-xs text-slate-500">{open ? 'Hide' : 'Show'} detail</span>
                </button>
                {open ? (
                  <div className="space-y-2 border-t border-slate-100 px-4 py-3 text-sm text-slate-700">
                    <p>
                      <span className="font-semibold text-slate-500">Rule: </span>
                      {String(r.businessRule ?? '—')}
                    </p>
                    <div>
                      <div className="text-xs font-semibold uppercase text-slate-500">Required data</div>
                      <ul className="mt-1 space-y-2">
                        {reqs.map((reqRaw, j) => {
                          const req = asRecord(reqRaw)
                          const stages = asRecord(req.stages)
                          const code = String(req.dataElementCode ?? '')
                          return (
                            <li key={j} className="rounded-lg border border-slate-100 bg-slate-50 px-3 py-2">
                              <div className="font-medium text-slate-900">
                                {String(req.businessName ?? req.dataElementCode)} —{' '}
                                {String(req.availability ?? req.status)}
                              </div>
                              {Object.keys(stages).length > 0 ? (
                                <div className="mt-1 grid gap-1 text-xs text-slate-600 sm:grid-cols-2">
                                  <span>
                                    Source data:{' '}
                                    {Boolean(asRecord(stages.sourceData).ok) ? '✓' : '⚠'}{' '}
                                    {String(asRecord(stages.sourceData).label ?? '')}
                                  </span>
                                  <span>
                                    Business definition:{' '}
                                    {Boolean(asRecord(stages.businessDefinition).ok) ? '✓' : '⚠'}{' '}
                                    {String(asRecord(stages.businessDefinition).label ?? '')}
                                  </span>
                                  <span>
                                    Business measure:{' '}
                                    {Boolean(asRecord(stages.businessMeasure).ok) ? '✓' : '—'}{' '}
                                    {String(asRecord(stages.businessMeasure).label ?? '')}
                                  </span>
                                  <span>
                                    Rule:{' '}
                                    {Boolean(asRecord(stages.ruleExecutability).ok) ? '✓' : '✕'}{' '}
                                    {String(asRecord(stages.ruleExecutability).label ?? '')}
                                  </span>
                                </div>
                              ) : null}
                              {req.canDefineBusinessMeasure && documentId && code ? (
                                <button
                                  type="button"
                                  className="bt-btn bt-btn-secondary bt-btn-sm mt-2"
                                  onClick={() => setDesignerCode(code)}
                                >
                                  Define business measure
                                </button>
                              ) : null}
                            </li>
                          )
                        })}
                      </ul>
                    </div>
                    {r.onMissing ? (
                      <p>
                        <span className="font-semibold text-slate-500">Missing-data behaviour: </span>
                        {String(r.onMissing)}
                      </p>
                    ) : null}
                    {r.recommendedAction ? (
                      <p>
                        <span className="font-semibold text-slate-500">Recommended action: </span>
                        {String(r.recommendedAction)}
                      </p>
                    ) : null}
                  </div>
                ) : null}
              </li>
            )
          })}
        </ul>
      </CiSection>

      {!prospectDemoMode ? (
        <CiTechnicalDetails title="Application-time contract (documented, not activated)">
          <pre className="overflow-x-auto whitespace-pre-wrap text-xs text-slate-700">
            {JSON.stringify(
              {
                applicationTimeContract: impl.applicationTimeContract,
                policyImpactLabNote: impl.policyImpactLabNote,
                packageStatusMapping: impl.packageStatusMapping,
                allowCanonicalAuthority: summary.allowCanonicalAuthority,
                gapClassifications: impl.gapClassifications,
              },
              null,
              2,
            )}
          </pre>
        </CiTechnicalDetails>
      ) : null}

      {designerCode && documentId && setBusy && onError && onSessionUpdate ? (
        <CiBusinessMeasureDesigner
          documentId={documentId}
          dataElementCode={designerCode}
          busy={Boolean(busy)}
          setBusy={setBusy}
          onError={onError}
          onComplete={(next) => {
            onSessionUpdate(next)
            setDesignerCode(null)
          }}
          onClose={() => setDesignerCode(null)}
        />
      ) : null}
    </div>
  )
}
