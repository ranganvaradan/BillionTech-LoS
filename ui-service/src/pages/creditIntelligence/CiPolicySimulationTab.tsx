import { useEffect, useMemo, useState } from 'react'
import {
  getDecisionPolicyE2eFixtureMatrix,
  getKycShadowFixtureMatrix,
  getSimulationContext,
  getSimulationRun,
  markSimulationReviewed,
  runDecisionPolicyE2eSimulation,
  runPolicySimulation,
  simulationExportCsvUrl,
  type SimulationContext,
  type SimulationResult,
} from '@/api/creditIntelligence'
import { ApiError } from '@/api/http'
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
  asList,
  asRecord,
  businessOutcomeLabel,
  softFixtureLabel,
} from '@/lib/creditIntelligence/businessLexicon'

export function CiPolicySimulationTab({
  documentId,
  policyName,
  busy,
  setBusy,
  onError,
  prospectDemoMode = false,
}: {
  documentId: string
  policyName?: string
  busy: boolean
  setBusy: (v: boolean) => void
  onError: (msg: string | null) => void
  prospectDemoMode?: boolean
}) {
  const [ctx, setCtx] = useState<SimulationContext | null>(null)
  const [selected, setSelected] = useState<string[]>([])
  const [dataSource, setDataSource] = useState('VALIDATION_FIXTURES')
  const [result, setResult] = useState<SimulationResult | null>(null)
  const [drillCode, setDrillCode] = useState<string | null>(null)
  const [loadingCtx, setLoadingCtx] = useState(true)
  const [simReviewedMsg, setSimReviewedMsg] = useState<string | null>(null)
  const [kycShadowMatrix, setKycShadowMatrix] = useState<Record<string, unknown> | null>(null)
  const [e2eResult, setE2eResult] = useState<Record<string, unknown> | null>(null)
  const [e2eDrill, setE2eDrill] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoadingCtx(true)
      onError(null)
      try {
        const data = await getSimulationContext(documentId)
        if (cancelled) return
        setCtx(data)
        const defaults = Array.isArray(data.defaultSelected)
          ? data.defaultSelected.map(String)
          : asList(data.applications).map((a) => String(asRecord(a).applicationCode ?? ''))
        setSelected(defaults.filter(Boolean).slice(0, 10))
        try {
          const matrix = await getKycShadowFixtureMatrix()
          if (!cancelled) setKycShadowMatrix(matrix)
        } catch {
          /* KYC-5 matrix optional when endpoint not yet live */
        }
        try {
          const e2e = await getDecisionPolicyE2eFixtureMatrix()
          if (!cancelled) setE2eResult(e2e)
        } catch {
          /* KYC-7 matrix optional when endpoint not yet live */
        }
      } catch (e) {
        if (!cancelled) {
          onError(e instanceof ApiError ? e.message : 'Could not load simulation context')
        }
      } finally {
        if (!cancelled) setLoadingCtx(false)
      }
    })()
    return () => {
      cancelled = true
    }
    // intentionally depend on documentId only; onError is setState from parent
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [documentId])

  const apps = useMemo(() => asList(ctx?.applications), [ctx])
  const aggregates = asRecord(result?.aggregates)
  const kycCases = asList(kycShadowMatrix?.cases)
  const e2eApps = asList(e2eResult?.applications)
  const e2eAggregates = asRecord(e2eResult?.aggregates)
  const e2eRecs = asRecord(e2eAggregates.recommendations)
  const e2eDrillRow = useMemo(() => {
    if (!e2eDrill) return null
    const row = e2eApps.map(asRecord).find((a) => String(a.caseCode ?? a.applicationCode) === e2eDrill)
    return row ? asRecord(row.drillDown) : null
  }, [e2eDrill, e2eApps])
  const policyImpact = asList(result?.policyImpact)
  const ruleImpact = asList(result?.ruleImpact)
  const missingSummary = asList(result?.missingDataSummary)
  const history = asList(result?.history ?? ctx?.history)
  const resultApps = asList(result?.applications)
  const drill = useMemo(() => {
    if (!drillCode) return null
    const row = resultApps.map(asRecord).find((a) => String(a.applicationCode) === drillCode)
    return row ? asRecord(row.drillDown) : null
  }, [drillCode, resultApps])

  const toggle = (code: string) => {
    setSelected((prev) => {
      if (prev.includes(code)) return prev.filter((c) => c !== code)
      if (prev.length >= 10) return prev
      return [...prev, code]
    })
  }

  const run = async () => {
    setBusy(true)
    onError(null)
    try {
      const data = await runPolicySimulation(documentId, {
        applicationCodes: selected,
        dataSource,
        reviewer: 'credit_manager',
      })
      setResult(data)
      setDrillCode(null)
    } catch (e) {
      onError(e instanceof ApiError ? e.message : 'Simulation failed')
    } finally {
      setBusy(false)
    }
  }

  const runE2e = async () => {
    setBusy(true)
    onError(null)
    try {
      const data = await runDecisionPolicyE2eSimulation({ documentId })
      setE2eResult(data)
      setE2eDrill(null)
    } catch (e) {
      onError(e instanceof ApiError ? e.message : 'Decision Policy E2E simulation failed')
    } finally {
      setBusy(false)
    }
  }

  const openHistory = async (runId: string) => {
    setBusy(true)
    onError(null)
    try {
      const data = await getSimulationRun(documentId, runId)
      setResult(data)
      setDrillCode(null)
    } catch (e) {
      onError(e instanceof ApiError ? e.message : 'Could not open simulation run')
    } finally {
      setBusy(false)
    }
  }

  const tested = Number(aggregates.applicationsTested ?? resultApps.length) || 0
  const passed = Number(aggregates.passed ?? 0)
  const referred = Number(aggregates.referred ?? 0)
  const failed = Number(aggregates.failed ?? 0)
  const di = Number(aggregates.dataInsufficient ?? 0)
  const topReject = asRecord(ruleImpact[0])
  const topMissing = asRecord(missingSummary[0])

  if (loadingCtx) {
    return (
      <CiLoadingCopy
        lines={[
          'Preparing simulations…',
          'Loading demo applications',
          'Checking policy readiness for simulation',
        ]}
      />
    )
  }

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-sky-300 bg-sky-50 px-4 py-2 text-sm font-semibold text-sky-950">
        {softFixtureLabel(String(result?.simulationBanner ?? ctx?.simulationBanner ?? ''))}
        {!result?.simulationBanner && !ctx?.simulationBanner ? 'Simulation only — not a live underwriting decision' : null}
      </div>

      {!result ? (
        <CiExecutiveSummary title="What should I do next?">
          <p>
            Test how the policy affects KYC, credit assessment, risk/score, eligible amount, offer and final
            recommendation. Select sample applications and run the draft policy.
          </p>
        </CiExecutiveSummary>
      ) : (
        <CiExecutiveSummary
          title="Summary"
          nextAction={
            <button
              type="button"
              className="bt-btn bt-btn-primary bt-btn-sm"
              disabled={busy || !result?.runId}
              onClick={() =>
                void (async () => {
                  setBusy(true)
                  onError(null)
                  try {
                    const data = await markSimulationReviewed(documentId, {
                      runId: String(result?.runId ?? ''),
                      reviewer: 'credit_manager',
                    })
                    setSimReviewedMsg(
                      String(
                        data.message ??
                          'Simulation reviewed — draft understanding only, not production approval.',
                      ),
                    )
                  } catch (e) {
                    onError(e instanceof ApiError ? e.message : 'Could not mark simulation reviewed')
                  } finally {
                    setBusy(false)
                  }
                })()
              }
            >
              Mark simulation reviewed
            </button>
          }
        >
          <p className="text-base font-semibold text-slate-900">{tested} applications tested</p>
          <ul className="mt-2 grid gap-1 text-sm text-slate-700 sm:grid-cols-2">
            <li>
              <strong className="text-emerald-800">{passed}</strong> Approved
              {tested ? ` (${Math.round((passed / tested) * 100)}%)` : ''}
            </li>
            <li>
              <strong className="text-amber-800">{referred}</strong> Manual Credit Review
              {tested ? ` (${Math.round((referred / tested) * 100)}%)` : ''}
            </li>
            <li>
              <strong className="text-rose-800">{failed}</strong> Declined
              {tested ? ` (${Math.round((failed / tested) * 100)}%)` : ''}
            </li>
            <li>
              <strong className="text-sky-800">{di}</strong> Missing Information
              {tested ? ` (${Math.round((di / tested) * 100)}%)` : ''}
            </li>
          </ul>
          {topReject.ruleName ? (
            <p className="mt-3 text-sm text-slate-700">
              Largest rejection / review driver: <strong>{String(topReject.ruleName)}</strong>
            </p>
          ) : null}
          {topMissing.family ? (
            <p className="mt-1 text-sm text-slate-700">
              Largest missing-information reason: <strong>{String(topMissing.family)}</strong>
            </p>
          ) : null}
          <p className="mt-2 text-xs text-slate-500">Policy readiness: Ready for Credit Manager Review (draft only)</p>
          {simReviewedMsg ? <p className="mt-2 text-sm font-medium text-emerald-800">{simReviewedMsg}</p> : null}
        </CiExecutiveSummary>
      )}

      {kycCases.length > 0 ? (
        <CiSection
          title="Decision Policy KYC — Shadow"
          description="SHADOW — DOES NOT AFFECT APPLICATION. Validation fixtures only — not production certification."
        >
          <div className="mb-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950">
            Shadow evaluation · certification:{' '}
            <strong>{String(kycShadowMatrix?.certificationStatus ?? 'SHADOW_EVALUATION_READY')}</strong>
            {' · '}
            allowCanonicalAuthority=false
          </div>
          <div className="overflow-x-auto rounded-lg border border-slate-200">
            <table className="min-w-full text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-3 py-2">Case</th>
                  <th className="px-3 py-2">Current KYC</th>
                  <th className="px-3 py-2">Shadow Policy KYC</th>
                  <th className="px-3 py-2">Difference</th>
                </tr>
              </thead>
              <tbody>
                {kycCases.map((raw, i) => {
                  const row = asRecord(raw)
                  return (
                    <tr key={i} className="border-t border-slate-100">
                      <td className="px-3 py-2 font-medium text-slate-900">{String(row.caseCode ?? '—')}</td>
                      <td className="px-3 py-2">{String(row.productionKyc ?? '—')}</td>
                      <td className="px-3 py-2">{String(row.shadowKyc ?? '—')}</td>
                      <td className="px-3 py-2 text-xs text-slate-600">
                        {String(row.comparisonClass ?? '—').replace(/_/g, ' ')}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </CiSection>
      ) : null}

      {e2eApps.length > 0 ? (
        <CiSection
          title="Decision Policy — End-to-end simulation"
          description="SIMULATION ONLY — DOES NOT AFFECT PRODUCTION UNDERWRITING. Validation fixtures — not production cutover."
          actions={
            <button
              type="button"
              className="bt-btn bt-btn-secondary bt-btn-sm"
              disabled={busy}
              onClick={() => void runE2e()}
            >
              {busy ? 'Running…' : 'Re-run E2E simulation'}
            </button>
          }
        >
          <div className="mb-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950">
            {String(e2eResult?.simulationBanner ?? 'SIMULATION ONLY — DOES NOT AFFECT PRODUCTION UNDERWRITING')}
            {' · '}
            <strong>{String(e2eResult?.policyName ?? '—')}</strong> v{String(e2eResult?.policyVersion ?? '—')}
            {' · '}
            status={String(e2eResult?.certificationStatus ?? '—')}
            {' · '}
            allowCanonicalAuthority=false
          </div>
          <div className="mb-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-5 text-sm">
            {(
              [
                ['KYC', 'Eligibility gate'],
                ['Credit', 'Underwriting rules'],
                ['Risk', 'Score / grade'],
                ['Offer', 'Limit · tenure · pricing'],
                ['Decision', 'Recommendation'],
              ] as const
            ).map(([label, hint]) => (
              <div key={label} className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                <div className="font-semibold text-slate-900">{label}</div>
                <div className="text-xs text-slate-600">{hint}</div>
              </div>
            ))}
          </div>
          <div className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <CiStatCard label="Applications tested" value={Number(e2eAggregates.applicationsTested ?? e2eApps.length)} tone="info" />
            <CiStatCard label="Approve" value={Number(e2eRecs.APPROVE ?? e2eAggregates.approve ?? 0)} tone="approved" />
            <CiStatCard label="Counter offer" value={Number(e2eRecs.COUNTER_OFFER ?? e2eAggregates.counterOffer ?? 0)} tone="info" />
            <CiStatCard label="Replay %" value={`${Number(e2eResult?.replayPassRate ?? 0)}%`} tone="approved" />
          </div>
          <div className="overflow-x-auto rounded-lg border border-slate-200">
            <table className="min-w-full text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-3 py-2">Application</th>
                  <th className="px-3 py-2">KYC</th>
                  <th className="px-3 py-2">Credit</th>
                  <th className="px-3 py-2">Score / Grade</th>
                  <th className="px-3 py-2">Requested</th>
                  <th className="px-3 py-2">Recommended</th>
                  <th className="px-3 py-2">Decision</th>
                  <th className="px-3 py-2">Review</th>
                </tr>
              </thead>
              <tbody>
                {e2eApps.map((raw, i) => {
                  const row = asRecord(raw)
                  const code = String(row.caseCode ?? row.applicationCode ?? i)
                  return (
                    <tr
                      key={code}
                      className="border-t border-slate-100 cursor-pointer hover:bg-sky-50"
                      onClick={() => setE2eDrill(code)}
                    >
                      <td className="px-3 py-2 font-medium text-slate-900">
                        {String(row.displayName ?? code)}
                        <div className="text-[11px] text-amber-800">VALIDATION FIXTURE</div>
                      </td>
                      <td className="px-3 py-2">
                        <CiOutcomeBadge value={String(row.kycOutcome ?? row.kyc ?? '')} />
                      </td>
                      <td className="px-3 py-2 text-xs">
                        {String(row.credit ?? row.creditOutcome ?? '—')}
                      </td>
                      <td className="px-3 py-2 text-xs">{String(row.scoreDisplay ?? 'NOT_RUN')}</td>
                      <td className="px-3 py-2 text-xs">{String(row.requestedAmountDisplay ?? '—')}</td>
                      <td className="px-3 py-2 text-xs">{String(row.recommendedAmountDisplay ?? '—')}</td>
                      <td className="px-3 py-2">
                        <CiOutcomeBadge value={String(row.recommendationCode ?? row.decision ?? '')} />
                      </td>
                      <td className="px-3 py-2 text-xs">
                        {row.reviewRequired || row.reviewNeeded ? 'Yes' : 'No'}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          {e2eDrillRow ? (
            <div className="mt-4 space-y-3 rounded-lg border border-slate-200 bg-white p-4 text-sm">
              <div className="flex items-center justify-between">
                <h4 className="font-semibold text-slate-900">Application drill-down — {e2eDrill}</h4>
                <button type="button" className="text-xs text-slate-600 underline" onClick={() => setE2eDrill(null)}>
                  Close
                </button>
              </div>
              {(
                [
                  ['APPLICATION', e2eDrillRow.application],
                  ['KYC & ELIGIBILITY', e2eDrillRow.kyc],
                  ['CREDIT ASSESSMENT', e2eDrillRow.credit],
                  ['RISK', e2eDrillRow.risk],
                  ['OFFER', e2eDrillRow.offer],
                  ['DECISION', e2eDrillRow.decision],
                ] as const
              ).map(([title, body]) => (
                <details key={title} open={title === 'DECISION' || title === 'KYC & ELIGIBILITY'}>
                  <summary className="cursor-pointer font-semibold text-slate-800">{title}</summary>
                  <pre className="mt-2 max-h-48 overflow-auto rounded bg-slate-50 p-2 text-[11px] text-slate-700">
                    {JSON.stringify(body ?? {}, null, 2)}
                  </pre>
                </details>
              ))}
              <details>
                <summary className="cursor-pointer text-xs font-semibold text-slate-600">TECHNICAL DETAILS</summary>
                <pre className="mt-2 max-h-40 overflow-auto rounded bg-slate-50 p-2 text-[11px] text-slate-600">
                  {JSON.stringify(e2eDrillRow.technicalDetails ?? {}, null, 2)}
                </pre>
              </details>
            </div>
          ) : null}
          <CiTechnicalDetails title="E2E package / hash">
            {JSON.stringify(
              {
                packageHash: asRecord(e2eResult?.technicalDetails).contentHash ?? e2eResult?.contentHash,
                packageId: asRecord(e2eResult?.technicalDetails).packageId ?? e2eResult?.packageId,
                replayPassRate: e2eResult?.replayPassRate,
                certificationNote: e2eResult?.certificationNote,
                allowCanonicalAuthority: false,
              },
              null,
              2,
            )}
          </CiTechnicalDetails>
        </CiSection>
      ) : null}

      <CiSection
        title="Run policy"
        description="Test how the policy affects KYC, credit assessment, risk/score, eligible amount, offer and final recommendation."
      >
        <dl className="grid gap-3 text-sm sm:grid-cols-3">
          <div>
            <dt className="text-xs text-slate-500">Policy</dt>
            <dd className="font-semibold">{String(ctx?.policyName ?? policyName ?? '—')}</dd>
          </div>
          <div>
            <dt className="text-xs text-slate-500">Status</dt>
            <dd>
              <span className="inline-flex rounded-full bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-800">
                {String(ctx?.policyStatus ?? 'Draft')}
              </span>
            </dd>
          </div>
          <div>
            <dt className="text-xs text-slate-500">Data source</dt>
            <dd>
              <select
                className="mt-0.5 rounded border border-slate-300 px-2 py-1 text-sm"
                value={dataSource}
                onChange={(e) => setDataSource(e.target.value)}
              >
                <option value="VALIDATION_FIXTURES">Demo cases</option>
                <option value="STAGING_APPLICATIONS">Staging applications (future)</option>
              </select>
            </dd>
          </div>
        </dl>
        <p className="mt-3 text-xs font-medium text-amber-800">Demo sample — not real borrower data</p>
      </CiSection>

      <CiSection
        title="Demo cases"
        description="Designed validation scenarios for prospect demonstration — not portfolio statistics."
      >
        <ul className="grid gap-2 sm:grid-cols-2 lg:grid-cols-5">
          {asList(ctx?.demoCaseGroups).map((raw) => {
            const g = asRecord(raw)
            return (
              <li
                key={String(g.key ?? g.label)}
                className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm"
              >
                <div className="font-semibold text-slate-900">{String(g.label ?? '')}</div>
                <p className="mt-1 text-xs text-slate-600">{String(g.description ?? '')}</p>
              </li>
            )
          })}
        </ul>
        <p className="mt-2 text-xs text-slate-500">
          {String(ctx?.note ?? 'These are designed test scenarios, not portfolio statistics.')}
        </p>
      </CiSection>

      <CiSection
        title="Select applications"
        description="Choose up to 10 applications. Each has an explicit product so only applicable rules run."
      >
        {apps.length === 0 ? (
          <CiEmptyState
            title="No simulations available yet"
            detail="Run a simulation to understand how this policy behaves before approval. Sample applications will appear when the policy is ready."
          />
        ) : (
          <ul className="grid gap-2 sm:grid-cols-2">
            {apps.map((raw) => {
              const a = asRecord(raw)
              const code = String(a.applicationCode ?? '')
              const checked = selected.includes(code)
              return (
                <li key={code}>
                  <label
                    className={`flex cursor-pointer gap-3 rounded-lg border px-3 py-2 text-sm ${
                      checked ? 'border-sky-400 bg-sky-50' : 'border-slate-200 bg-white'
                    }`}
                  >
                    <input
                      type="checkbox"
                      className="mt-1"
                      checked={checked}
                      onChange={() => toggle(code)}
                    />
                    <span className="min-w-0 flex-1">
                      <span className="font-semibold text-slate-900">
                        {String(a.displayName ?? code)}
                      </span>
                      <span className="ml-2 text-xs text-slate-500">
                        {String(a.requestedAmountDisplay ?? '')}
                      </span>
                      <div className="text-sm font-medium text-sky-900">
                        {String(a.scenarioLabel ?? '')}
                      </div>
                      <div className="text-[11px] text-slate-500">
                        Product {String(a.product ?? '—')}
                        {a.demoCategory ? ` · ${String(a.demoCategory)}` : ''}
                      </div>
                      <details className="mt-1">
                        <summary className="cursor-pointer text-[11px] font-semibold text-slate-600">
                          Demo context
                        </summary>
                        <p className="mt-1 text-xs text-slate-600">
                          <span className="font-semibold">Scenario purpose:</span>{' '}
                          {String(a.scenarioPurpose ?? a.description ?? '—')}
                        </p>
                      </details>
                      <div className="mt-0.5 text-[11px] font-medium text-amber-800">
                        Demo sample — not real borrower data
                      </div>
                    </span>
                  </label>
                </li>
              )
            })}
          </ul>
        )}
        <div className="mt-4 flex flex-wrap items-center gap-3">
          <button
            type="button"
            className="bt-btn bt-btn-primary"
            disabled={busy || selected.length === 0 || !ctx?.readyForSimulation}
            onClick={() => void run()}
          >
            {busy ? 'Running policy…' : 'Run policy'}
          </button>
          <span className="text-xs text-slate-500">{selected.length} selected (max 10)</span>
        </div>
      </CiSection>

      {result ? (
        <>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <CiStatCard label="Applications tested" value={tested} tone="info" />
            <CiStatCard
              label="Approval %"
              value={tested ? `${Math.round((passed / tested) * 100)}%` : '—'}
              tone="approved"
            />
            <CiStatCard
              label="Manual review %"
              value={tested ? `${Math.round((referred / tested) * 100)}%` : '—'}
              tone="review"
            />
            <CiStatCard
              label="Decline %"
              value={tested ? `${Math.round((failed / tested) * 100)}%` : '—'}
              tone="action"
            />
          </div>

          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
            {(
              [
                ['Approved', aggregates.approve, 'approved'],
                ['Approved with Conditions', aggregates.approveWithConditions, 'approved'],
                ['Counter Offer', aggregates.counterOffer, 'info'],
                ['Manual Credit Review', aggregates.recommendRefer, 'review'],
                ['Declined', aggregates.decline, 'action'],
              ] as const
            ).map(([label, val, tone]) => (
              <CiStatCard key={label} label={label} value={Number(val ?? 0)} tone={tone} />
            ))}
          </div>

          <div className="grid gap-4 lg:grid-cols-3">
            <CiSection title="Policy impact">
              <ul className="space-y-1 text-sm">
                {policyImpact.map((raw, i) => {
                  const r = asRecord(raw)
                  return (
                    <li key={i} className="flex justify-between gap-2 border-b border-slate-50 py-1">
                      <span>{businessOutcomeLabel(r.class)}</span>
                      <strong>{String(r.count ?? 0)}</strong>
                    </li>
                  )
                })}
              </ul>
            </CiSection>
            <CiSection title="Top drivers of review / decline">
              <ul className="space-y-1 text-sm">
                {ruleImpact.map((raw, i) => {
                  const r = asRecord(raw)
                  return (
                    <li key={i} className="flex justify-between gap-2 border-b border-slate-50 py-1">
                      <span>{String(r.ruleName ?? '')}</span>
                      <strong>{String(r.count ?? 0)}</strong>
                    </li>
                  )
                })}
                {ruleImpact.length === 0 ? (
                  <li className="text-slate-500">No review or decline rule hits in this sample.</li>
                ) : null}
              </ul>
            </CiSection>
            <CiSection title="Missing information">
              <ul className="space-y-1 text-sm">
                {missingSummary.map((raw, i) => {
                  const r = asRecord(raw)
                  return (
                    <li key={i} className="flex justify-between gap-2 border-b border-slate-50 py-1">
                      <span>{String(r.family ?? '')}</span>
                      <strong>{String(r.count ?? 0)}</strong>
                    </li>
                  )
                })}
                {missingSummary.length === 0 ? (
                  <li className="text-slate-500">No missing-information hits in this sample.</li>
                ) : null}
              </ul>
            </CiSection>
          </div>

          <CiSection
            title="Application results"
            description="Click a row for detail. Simulation only — not a production decision."
            actions={
              result.runId ? (
                <a
                  className="bt-btn bt-btn-secondary bt-btn-sm"
                  href={simulationExportCsvUrl(documentId, String(result.runId))}
                  target="_blank"
                  rel="noreferrer"
                >
                  Export
                </a>
              ) : null
            }
          >
            <div className="overflow-x-auto">
              <table className="min-w-full text-left text-sm">
                <thead className="border-b border-slate-200 text-xs uppercase text-slate-500">
                  <tr>
                    <th className="px-2 py-2">Application</th>
                    <th className="px-2 py-2">Scenario</th>
                    <th className="px-2 py-2">Requested</th>
                    <th className="px-2 py-2">Policy result</th>
                    <th className="px-2 py-2">Recommendation</th>
                    <th className="px-2 py-2">Recommended amt</th>
                    <th className="px-2 py-2">Top reason</th>
                    <th className="px-2 py-2">Information quality</th>
                    <th className="px-2 py-2">Review</th>
                  </tr>
                </thead>
                <tbody>
                  {resultApps.map((raw) => {
                    const a = asRecord(raw)
                    const code = String(a.applicationCode ?? '')
                    return (
                      <tr
                        key={code}
                        className={`cursor-pointer border-b border-slate-100 hover:bg-sky-50 ${
                          drillCode === code ? 'bg-sky-50' : ''
                        }`}
                        onClick={() => setDrillCode(code)}
                      >
                        <td className="px-2 py-2 font-medium">
                          {String(a.displayName ?? code)}
                          <div className="text-[10px] text-slate-500">{String(a.product ?? '')}</div>
                          <div className="text-[10px] font-medium text-amber-800">Demo sample</div>
                        </td>
                        <td className="px-2 py-2 text-xs font-medium text-sky-900">
                          {String(a.scenarioLabel ?? '—')}
                        </td>
                        <td className="px-2 py-2">{String(a.requestedAmountDisplay ?? '—')}</td>
                        <td className="px-2 py-2">
                          <CiOutcomeBadge value={a.policyResult} />
                        </td>
                        <td className="px-2 py-2 text-xs font-medium">
                          {businessOutcomeLabel(a.recommendation)}
                        </td>
                        <td className="px-2 py-2">{String(a.recommendedAmountDisplay ?? '—')}</td>
                        <td className="px-2 py-2 max-w-[220px] truncate" title={String(a.topReason ?? '')}>
                          {String(a.topReason ?? '—')}
                        </td>
                        <td className="px-2 py-2">{String(a.dataQuality ?? '—')}</td>
                        <td className="px-2 py-2">{String(a.reviewNeededLabel ?? '—')}</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </CiSection>

          {drill && drillCode ? (
            <CiSection title={`Application detail — ${String(asRecord(resultApps.find((x) => String(asRecord(x).applicationCode) === drillCode)).displayName ?? drillCode)}`}>
              <details className="mb-3 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm open:pb-3">
                <summary className="cursor-pointer font-semibold text-slate-800">Demo context</summary>
                <p className="mt-2 text-slate-700">
                  <span className="font-semibold">Scenario purpose:</span>{' '}
                  {String(drill.scenarioPurpose ?? '—')}
                </p>
                <p className="mt-1 text-xs text-amber-900">
                  {softFixtureLabel(String(drill.demoResolutionBanner ?? 'Demo resolution — customer confirmation required'))}
                </p>
              </details>
              <div className="grid gap-4 lg:grid-cols-2">
                <div className="space-y-3 text-sm">
                  <div>
                    <span className="text-xs font-semibold uppercase text-slate-500">Policy result</span>
                    <div className="mt-1">
                      <CiOutcomeBadge value={drill.policyResult} />
                    </div>
                  </div>
                  <dl className="grid grid-cols-2 gap-2">
                    <div>
                      <dt className="text-xs text-slate-500">Rules approved</dt>
                      <dd className="font-semibold text-emerald-800">{String(drill.rulesPassed ?? 0)}</dd>
                    </div>
                    <div>
                      <dt className="text-xs text-slate-500">Rules declined</dt>
                      <dd className="font-semibold text-rose-800">{String(drill.rulesFailed ?? 0)}</dd>
                    </div>
                    <div>
                      <dt className="text-xs text-slate-500">Manual review</dt>
                      <dd className="font-semibold text-amber-800">{String(drill.rulesReferred ?? 0)}</dd>
                    </div>
                    <div>
                      <dt className="text-xs text-slate-500">Missing information</dt>
                      <dd className="font-semibold text-sky-800">{String(drill.dataInsufficient ?? 0)}</dd>
                    </div>
                  </dl>
                  <div>
                    <div className="text-xs font-semibold uppercase text-slate-500">Primary reason</div>
                    <p className="mt-1 font-medium text-slate-900">{String(drill.primaryReason ?? '—')}</p>
                    <ul className="mt-2 list-disc space-y-1 pl-5 text-slate-700">
                      {asList(drill.secondaryReasons).map((s, i) => (
                        <li key={i}>{String(s)}</li>
                      ))}
                    </ul>
                  </div>
                  <div>
                    <div className="text-xs font-semibold uppercase text-slate-500">
                      Requested vs recommended
                    </div>
                    <p className="mt-1">
                      {String(drill.requestedAmountDisplay ?? '—')} →{' '}
                      <strong>{String(drill.recommendedAmountDisplay ?? '—')}</strong>
                    </p>
                  </div>
                </div>
                <div className="space-y-3 text-sm">
                  <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-3">
                    <div className="text-xs font-semibold uppercase text-slate-500">
                      Current LOS vs draft policy
                    </div>
                    <div className="mt-2 flex flex-wrap gap-3">
                      <div>
                        Current LOS: <CiOutcomeBadge value={asRecord(drill.legacyComparison).currentLos} />
                      </div>
                      <div>
                        Draft policy: <CiOutcomeBadge value={asRecord(drill.legacyComparison).draftPolicy} />
                      </div>
                    </div>
                    <p className="mt-2 text-slate-800">
                      {String(asRecord(drill.legacyComparison).reason ?? '')}
                    </p>
                  </div>
                  <div>
                    <div className="text-xs font-semibold uppercase text-slate-500">Rule outcomes</div>
                    <ul className="mt-1 space-y-1">
                      {asList(drill.ruleOutcomes).map((raw, i) => {
                        const r = asRecord(raw)
                        return (
                          <li key={i} className="flex justify-between gap-2">
                            <span>{String(r.ruleName ?? '')}</span>
                            <CiOutcomeBadge value={r.outcome} />
                          </li>
                        )
                      })}
                    </ul>
                  </div>
                  <div>
                    <div className="text-xs font-semibold uppercase text-slate-500">Evidence used</div>
                    <ul className="mt-1 list-disc pl-5">
                      {asList(drill.evidenceUsed).map((e, i) => (
                        <li key={i}>{String(e)}</li>
                      ))}
                    </ul>
                  </div>
                  <div>
                    <div className="text-xs font-semibold uppercase text-slate-500">Conditions</div>
                    <ul className="mt-1 list-disc pl-5">
                      {asList(drill.conditions).map((e, i) => (
                        <li key={i}>{String(e)}</li>
                      ))}
                    </ul>
                  </div>
                </div>
              </div>
            </CiSection>
          ) : null}

          <CiSection title="Simulation history" description="Previous runs are preserved — never overwritten.">
            <ul className="space-y-2 text-sm">
              {history.map((raw, i) => {
                const h = asRecord(raw)
                const summary = asRecord(h.outcomeSummary)
                return (
                  <li
                    key={String(h.runId ?? i)}
                    className="flex flex-wrap items-center justify-between gap-2 rounded border border-slate-200 px-3 py-2"
                  >
                    <div>
                      <div className="font-medium">
                        {h.runDate ? new Date(String(h.runDate)).toLocaleString() : '—'}
                      </div>
                      <div className="text-xs text-slate-500">
                        {String(h.applicationsCount ?? '—')} apps · Approved {String(summary.passed ?? '—')} / Manual
                        review {String(summary.referred ?? '—')} / Declined {String(summary.failed ?? '—')}
                      </div>
                    </div>
                    <button
                      type="button"
                      className="bt-btn bt-btn-secondary bt-btn-sm"
                      disabled={busy}
                      onClick={() => void openHistory(String(h.runId))}
                    >
                      Open run
                    </button>
                  </li>
                )
              })}
              {history.length === 0 ? (
                <li>
                  <CiEmptyState
                    title="No simulations have been run yet"
                    detail="Run a simulation to understand how this policy behaves before approval."
                  />
                </li>
              ) : null}
            </ul>
          </CiSection>

          <CiTechnicalDetails hidden={prospectDemoMode}>
            {JSON.stringify(result.technicalDetails ?? {}, null, 2)}
          </CiTechnicalDetails>
        </>
      ) : null}
    </div>
  )
}

