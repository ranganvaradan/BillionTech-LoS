import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  getStagingWorkspace,
  replayStagingCase,
  resolveShadowApplication,
  type StagingReplayResult,
  type StagingWorkspace,
} from '@/api/creditIntelligence'
import { ApiError } from '@/api/http'
import { PageHeader } from '@/components/PageHeader'
import { PoliciesWorkspaceNav } from '@/components/workspace/PoliciesWorkspaceNav'
import { CiFixtureBanner } from '@/components/creditIntelligence/CiFixtureBanner'
import {
  CiEmptyState,
  CiExecutiveSummary,
  CiLoadingCopy,
  CiOutcomeBadge,
  CiSection,
  CiStatCard,
  CiTechnicalDetails,
} from '@/components/creditIntelligence/CiSection'
import {
  asRecord,
  businessOutcomeLabel,
  caseFriendlyTitle,
  listFromUnknown,
  pickDisplay,
} from '@/lib/creditIntelligence/businessLexicon'
import { maskPiiDeep } from '@/lib/creditIntelligence/maskPii'

function JsonDump({ value }: { value: unknown }) {
  return (
    <pre className="max-h-64 overflow-auto rounded bg-slate-50 p-3 text-[11px] leading-relaxed text-slate-500">
      {JSON.stringify(maskPiiDeep(value), null, 2)}
    </pre>
  )
}

function EvidenceFamily({
  title,
  source,
  fallbackKeys,
}: {
  title: string
  source: Record<string, unknown>
  fallbackKeys: string[]
}) {
  const candidates = [
    asRecord(source[title.toLowerCase()]),
    asRecord(source[title]),
    asRecord(source[`${title.toLowerCase()}Evidence`]),
    asRecord(source[`${title}Evidence`]),
  ]
  const nested = candidates.find((c) => Object.keys(c).length > 0) ?? {}

  const concerns = listFromUnknown(
    nested.concerns ?? nested.flags ?? nested.issues ?? source[`${title.toLowerCase()}Concerns`],
  )
  const strengths = listFromUnknown(
    nested.strengths ?? nested.positives ?? source[`${title.toLowerCase()}Strengths`],
  )
  const coverage =
    pickDisplay(nested, ['coverage', 'present', 'available', 'status', 'summary']) ??
    pickDisplay(source, fallbackKeys)

  const hasContent = Boolean(coverage || concerns.length || strengths.length || Object.keys(nested).length)

  return (
    <div className="rounded-lg border border-slate-200 bg-white px-4 py-3">
      <div className="text-sm font-semibold text-slate-900">{title}</div>
      {coverage ? <p className="mt-1 text-xs text-slate-600">Coverage: {coverage}</p> : null}
      {strengths.length > 0 ? (
        <ul className="mt-2 space-y-1 text-sm text-emerald-800">
          {strengths.slice(0, 4).map((s, i) => (
            <li key={i}>✓ {s}</li>
          ))}
        </ul>
      ) : null}
      {concerns.length > 0 ? (
        <ul className="mt-2 space-y-1 text-sm text-rose-800">
          {concerns.slice(0, 4).map((s, i) => (
            <li key={i}>! {s}</li>
          ))}
        </ul>
      ) : null}
      {!hasContent ? (
        <p className="mt-2 text-xs text-slate-500">No {title.toLowerCase()} highlights in this sample file.</p>
      ) : null}
      {hasContent && !strengths.length && !concerns.length && !coverage ? (
        <p className="mt-2 text-xs text-slate-600">
          {pickDisplay(nested, Object.keys(nested).slice(0, 3)) ?? 'Information present — open Supporting Details if needed.'}
        </p>
      ) : null}
    </div>
  )
}

export function CiWorkspacePage() {
  const { caseCode = 'CASE_A' } = useParams()
  const [ws, setWs] = useState<StagingWorkspace | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [replay, setReplay] = useState<StagingReplayResult | null>(null)
  const [replaying, setReplaying] = useState(false)
  const [applicable, setApplicable] = useState<Record<string, unknown> | null>(null)
  const [evalDate, setEvalDate] = useState('2026-08-25')

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await getStagingWorkspace(caseCode)
      setWs(data)
      try {
        const product = String(asRecord(data).product ?? 'DIGILEAP')
        const resolved = await resolveShadowApplication({
          applicationCode: caseCode,
          productCode: product.includes('SMART') ? 'SMART_SWITCH' : 'DIGILEAP',
          evaluationDate: evalDate,
        })
        setApplicable(resolved)
      } catch {
        setApplicable(null)
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not open this credit file')
      setWs(null)
    } finally {
      setLoading(false)
    }
  }, [caseCode, evalDate])

  useEffect(() => {
    void load()
  }, [load])

  const onReplay = async () => {
    setReplaying(true)
    try {
      setReplay(await replayStagingCase(caseCode))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not re-check this assessment')
    } finally {
      setReplaying(false)
    }
  }

  const evidence = asRecord(ws?.creditEvidenceView)
  const decision = asRecord(ws?.creditDecisionView)
  const ai = asRecord(ws?.aiUnderwriterView)
  const dual = asRecord(ws?.legacyVsCanonical)
  const explanation = asRecord(ws?.decisionExplanation)
  const policy = asRecord(ws?.policyResult)
  const rec = asRecord(ws?.recommendation)
  const cutover = asRecord(ws?.cutover)
  const comparisons = Array.isArray(dual.comparisons) ? dual.comparisons : []

  const friendlyTitle = caseFriendlyTitle(caseCode, typeof ws?.title === 'string' ? ws.title : null)

  const materialDiffs = useMemo(() => {
    return comparisons.filter((row) => {
      const r = asRecord(row)
      const cls = String(r.differenceClass ?? '').toUpperCase()
      return cls && cls !== 'SAME' && cls !== 'MATCH' && cls !== 'NONE'
    }).length
  }, [comparisons])

  const outcome = rec.outcome ?? decision.outcome ?? decision.recommendation
  const amount = rec.amount ?? decision.recommendedAmount ?? decision.amount
  const reason =
    pickDisplay(explanation, ['primaryReason', 'reason', 'summary', 'narrative']) ||
    pickDisplay(decision, ['primaryReason', 'reason', 'summary']) ||
    pickDisplay(rec, ['reason', 'rationale'])
  const conditions = listFromUnknown(
    explanation.conditions ?? decision.conditions ?? rec.conditions ?? policy.conditions,
  )
  const authority =
    pickDisplay(decision, ['authority', 'authorityLevel', 'approver']) ||
    pickDisplay(rec, ['authority']) ||
    'Credit Manager review (draft only)'

  const crossChecks = listFromUnknown(
    evidence.reconciliations ?? evidence.crossChecks ?? evidence.consistencyChecks ?? evidence.checks,
  )
  const concerns = listFromUnknown(evidence.concerns ?? evidence.riskFlags ?? evidence.flags)
  const strengths = listFromUnknown(evidence.strengths ?? evidence.positives)

  return (
    <div>
      <PageHeader
        title={friendlyTitle}
        description="Credit file view for sample applications. Draft assessment only — not a live underwriting decision."
        actions={
          <div className="flex flex-wrap gap-2">
            <Link to="/credit-intelligence/applications" className="bt-btn bt-btn-secondary bt-btn-sm">
              All applications
            </Link>
            <Link to="/credit-intelligence/dual-run" className="bt-btn bt-btn-secondary bt-btn-sm">
              Compare Impact
            </Link>
            <button
              type="button"
              className="bt-btn bt-btn-primary bt-btn-sm"
              disabled={replaying}
              onClick={() => void onReplay()}
            >
              {replaying ? 'Re-checking…' : 'Re-check assessment'}
            </button>
          </div>
        }
      />
      <PoliciesWorkspaceNav />
      <CiFixtureBanner text={typeof ws?.fixtureBanner === 'string' ? ws.fixtureBanner : undefined} />
      {loading ? (
        <CiLoadingCopy
          lines={[
            'Preparing credit file…',
            'Gathering customer, bureau and banking evidence',
            'Summarising recommendation',
            'Comparing with current LOS where available',
          ]}
        />
      ) : null}
      {error ? <p className="text-sm text-rose-700">{error}</p> : null}

      {ws && !loading ? (
        <>
          <CiExecutiveSummary
            title="Executive summary"
            nextAction={
              <Link to="/credit-intelligence/policy-studio" className="bt-btn bt-btn-primary bt-btn-sm">
                Review related policy
              </Link>
            }
          >
            <div className="flex flex-wrap items-center gap-3">
              <CiOutcomeBadge value={outcome} />
              <span className="text-slate-700">
                Recommended amount: <strong>{amount != null ? String(amount) : '—'}</strong>
              </span>
            </div>
            {reason ? <p className="mt-3 text-slate-700">{reason}</p> : null}
            <p className="mt-2 text-xs text-slate-500">
              Production authority is off. This is a draft recommendation for Credit Manager review.
            </p>
          </CiExecutiveSummary>

          <CiSection
            title="Applicable Policy"
            description="Shadow mode — exactly one policy for this application and evaluation date."
          >
            <div className="mb-3 flex flex-wrap items-end gap-3">
              <label className="text-sm">
                <span className="text-slate-600">Evaluation date</span>
                <input
                  type="date"
                  className="mt-1 block rounded border border-slate-300 px-3 py-2"
                  value={evalDate}
                  onChange={(e) => setEvalDate(e.target.value)}
                />
              </label>
              <button
                type="button"
                className="rounded bg-slate-800 px-3 py-2 text-sm text-white"
                onClick={() => void load()}
              >
                Resolve
              </button>
            </div>
            {applicable ? (
              <div className="rounded border border-slate-200 bg-slate-50 px-4 py-3 text-sm">
                <div className="font-semibold text-slate-900">
                  {String(applicable.banner ?? applicable.outcome)}
                </div>
                {asRecord(applicable.selectedPolicy).policyName ? (
                  <dl className="mt-2 grid gap-1 sm:grid-cols-2">
                    <div>
                      <dt className="text-slate-500">Policy</dt>
                      <dd className="font-medium">{String(asRecord(applicable.selectedPolicy).policyName)}</dd>
                    </div>
                    <div>
                      <dt className="text-slate-500">Version</dt>
                      <dd className="font-medium">{String(asRecord(applicable.selectedPolicy).policyVersion)}</dd>
                    </div>
                    <div>
                      <dt className="text-slate-500">Effective</dt>
                      <dd>
                        {String(asRecord(applicable.selectedPolicy).effectiveFrom ?? '—')}
                        {asRecord(applicable.selectedPolicy).effectiveUntil
                          ? ` → ${String(asRecord(applicable.selectedPolicy).effectiveUntil)}`
                          : ''}
                      </dd>
                    </div>
                    <div>
                      <dt className="text-slate-500">Evaluation Mode</dt>
                      <dd>Shadow</dd>
                    </div>
                  </dl>
                ) : null}
                <p className="mt-2 text-slate-700">{String(applicable.reason ?? '')}</p>
                <p className="mt-2 text-xs text-slate-500">
                  Production underwriting remains legacy. allowCanonicalAuthority=false.
                </p>
              </div>
            ) : (
              <p className="text-sm text-slate-600">
                No durable catalogue match yet — schedule a policy from Policy Settings / Catalogue.
              </p>
            )}
          </CiSection>

          <div className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <CiStatCard label="Decision" value={businessOutcomeLabel(outcome)} tone="review" />
            <CiStatCard label="Recommended amount" value={amount != null ? String(amount) : '—'} />
            <CiStatCard
              label="Policy differences"
              value={materialDiffs}
              tone={materialDiffs > 0 ? 'review' : 'approved'}
            />
            <CiStatCard label="Live production" value="Off" tone="info" />
          </div>

          <CiSection
            title="Evidence"
            description="Presented like a credit file — strengths and concerns first."
          >
            <div className="grid gap-3 lg:grid-cols-2">
              <EvidenceFamily title="Customer" source={evidence} fallbackKeys={['customer', 'applicant', 'kyc']} />
              <EvidenceFamily title="Bureau" source={evidence} fallbackKeys={['bureau', 'creditBureau']} />
              <EvidenceFamily title="Banking" source={evidence} fallbackKeys={['banking', 'bank', 'statements']} />
              <EvidenceFamily title="GST" source={evidence} fallbackKeys={['gst']} />
              <EvidenceFamily title="ITR" source={evidence} fallbackKeys={['itr', 'tax']} />
              <div className="rounded-lg border border-slate-200 bg-white px-4 py-3">
                <div className="text-sm font-semibold text-slate-900">Cross-checks</div>
                {crossChecks.length > 0 ? (
                  <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-700">
                    {crossChecks.slice(0, 6).map((c, i) => (
                      <li key={i}>{c}</li>
                    ))}
                  </ul>
                ) : (
                  <p className="mt-2 text-xs text-slate-500">No cross-check highlights surfaced for this sample.</p>
                )}
              </div>
            </div>

            <div className="mt-4 grid gap-3 lg:grid-cols-2">
              <div className="rounded-lg border border-emerald-200 bg-emerald-50/60 px-4 py-3">
                <div className="text-xs font-semibold uppercase tracking-wide text-emerald-900">Strengths</div>
                {strengths.length > 0 ? (
                  <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-emerald-900">
                    {strengths.map((s, i) => (
                      <li key={i}>{s}</li>
                    ))}
                  </ul>
                ) : (
                  <p className="mt-2 text-sm text-emerald-900/80">No strength highlights listed.</p>
                )}
              </div>
              <div className="rounded-lg border border-rose-200 bg-rose-50/60 px-4 py-3">
                <div className="text-xs font-semibold uppercase tracking-wide text-rose-900">Concerns</div>
                {concerns.length > 0 ? (
                  <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-rose-900">
                    {concerns.map((s, i) => (
                      <li key={i}>{s}</li>
                    ))}
                  </ul>
                ) : (
                  <p className="mt-2 text-sm text-rose-900/80">No concern highlights listed.</p>
                )}
              </div>
            </div>

            {Array.isArray(ws.investigationQuestions) && ws.investigationQuestions.length > 0 ? (
              <div className="mt-4">
                <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                  Questions for investigation
                </div>
                <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-700">
                  {ws.investigationQuestions.map((q) => (
                    <li key={q}>{q}</li>
                  ))}
                </ul>
              </div>
            ) : null}

            <CiTechnicalDetails title="Supporting information (Evidence)">
              <JsonDump value={evidence} />
            </CiTechnicalDetails>
          </CiSection>

          <CiSection title="Decision" description="What Credit would see as the draft recommendation.">
            <dl className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-3">
              <div>
                <dt className="text-xs text-slate-500">Requested</dt>
                <dd className="font-semibold">
                  {pickDisplay(decision, ['requestedAmount', 'requested']) ||
                    pickDisplay(rec, ['requestedAmount', 'requested']) ||
                    '—'}
                </dd>
              </div>
              <div>
                <dt className="text-xs text-slate-500">Recommended</dt>
                <dd className="font-semibold">{amount != null ? String(amount) : '—'}</dd>
              </div>
              <div>
                <dt className="text-xs text-slate-500">Outcome</dt>
                <dd>
                  <CiOutcomeBadge value={outcome} />
                </dd>
              </div>
              <div className="sm:col-span-2">
                <dt className="text-xs text-slate-500">Reason</dt>
                <dd className="mt-1 text-slate-800">{reason || 'See policy comparison and evidence for detail.'}</dd>
              </div>
              <div>
                <dt className="text-xs text-slate-500">Authority</dt>
                <dd className="font-medium text-slate-800">{authority}</dd>
              </div>
            </dl>
            {conditions.length > 0 ? (
              <div className="mt-4">
                <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">Conditions</div>
                <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-700">
                  {conditions.map((c, i) => (
                    <li key={i}>{c}</li>
                  ))}
                </ul>
              </div>
            ) : null}
            <p className="mt-4 text-sm text-slate-700">
              {pickDisplay(ai, ['summary', 'narrative', 'banner']) ||
                'AI assistance is advisory only and does not replace Credit Manager judgement.'}
            </p>
            <CiTechnicalDetails title="Supporting information (Decision)">
              <JsonDump value={{ decision, explanation, policy, recommendation: rec, ai }} />
            </CiTechnicalDetails>
          </CiSection>

          <CiSection
            title="Current LOS vs proposed policy"
            description={
              materialDiffs > 0
                ? `${materialDiffs} material difference${materialDiffs === 1 ? '' : 's'} for Credit attention.`
                : 'No material differences in this sample comparison.'
            }
            actions={
              <Link to="/credit-intelligence/dual-run" className="bt-btn bt-btn-secondary bt-btn-sm">
                Open comparison
              </Link>
            }
          >
            {comparisons.length > 0 ? (
              <div className="overflow-x-auto">
                <table className="bt-table min-w-full text-sm">
                  <thead className="border-b border-slate-200 bg-slate-50 text-slate-600">
                    <tr>
                      <th className="px-3 py-2 text-left font-medium">Business rule</th>
                      <th className="px-3 py-2 text-left font-medium">Current LOS</th>
                      <th className="px-3 py-2 text-left font-medium">Proposed policy</th>
                      <th className="px-3 py-2 text-left font-medium">Difference</th>
                    </tr>
                  </thead>
                  <tbody>
                    {comparisons.map((row, idx) => {
                      const r = asRecord(row)
                      return (
                        <tr key={idx} className="border-b border-slate-100">
                          <td className="px-3 py-2">
                            {String(r.ruleName ?? r.businessRule ?? r.ruleId ?? 'Rule')}
                          </td>
                          <td className="px-3 py-2">
                            <CiOutcomeBadge value={r.legacyOutcome} />
                          </td>
                          <td className="px-3 py-2">
                            <CiOutcomeBadge value={r.canonicalOutcome} />
                          </td>
                          <td className="px-3 py-2 text-slate-700">
                            {businessOutcomeLabel(r.differenceClass)}
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            ) : (
              <CiEmptyState
                title="No comparison rows yet"
                detail="Open Dual Run after a draft policy is available, or pick another sample application."
              />
            )}
            <CiTechnicalDetails title="Developer Diagnostics">
              <JsonDump value={dual} />
            </CiTechnicalDetails>
          </CiSection>

          <CiSection title="Consistency check" description="Confirm the same assessment can be reproduced.">
            <dl className="grid gap-2 text-sm sm:grid-cols-3">
              <div>
                <dt className="text-xs text-slate-500">File consistency</dt>
                <dd className="font-semibold">
                  {String(asRecord(ws.replay).match ?? '—') === 'true' || asRecord(ws.replay).match === true
                    ? 'Consistent'
                    : String(asRecord(ws.replay).match ?? 'Not checked')}
                </dd>
              </div>
              {replay ? (
                <div>
                  <dt className="text-xs text-slate-500">Re-check result</dt>
                  <dd className="font-semibold">{replay.match ? 'Consistent' : 'Needs attention'}</dd>
                </div>
              ) : (
                <p className="text-sm text-slate-600 sm:col-span-2">
                  Use Re-check assessment to confirm the recommendation is stable for this sample.
                </p>
              )}
            </dl>
            <CiTechnicalDetails title="Developer Diagnostics">
              <JsonDump value={{ cutover, replay: ws.replay, postReplay: replay }} />
              <div className="mt-2 text-[11px] text-slate-500">
                Identifiers are retained for support only and are not required for Credit review.
              </div>
            </CiTechnicalDetails>
          </CiSection>
        </>
      ) : null}
    </div>
  )
}
