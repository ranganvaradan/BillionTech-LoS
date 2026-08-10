import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  compareDraftVersions,
  getApprovalsContext,
  getSimulationContext,
  getStagingPolicyStudio,
  getStagingWorkspace,
  listStagingCases,
  runPolicySimulation,
  type ApprovalsContext,
  type SimulationResult,
  type StagingCaseSummary,
  type StagingPolicyStudio,
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
import { asList, asRecord, softFixtureLabel } from '@/lib/creditIntelligence/businessLexicon'
import {
  answerComparisonQuestions,
  buildApprovalReadiness,
  buildBusinessReportHtml,
  buildChangeCards,
  buildChangedApplications,
  buildChangesCsv,
  buildDefinitionChanges,
  buildExecutiveSummary,
  buildProductImpacts,
  buildTopDrivers,
  buildVersionMeta,
  differenceClassLabel,
  groupChangesBySection,
  simulationMix,
  type PolicySideMeta,
} from '@/pages/creditIntelligence/policyComparisonModel'

type DraftKind = 'banking' | 'bureau'
type ViewTab = 'overview' | 'changes' | 'simulation' | 'products' | 'definitions' | 'readiness' | 'ask'

function MetaCard({ title, meta }: { title: string; meta: PolicySideMeta }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white px-4 py-3 shadow-sm">
      <div className="text-[11px] font-semibold uppercase tracking-[0.12em] text-slate-500">{title}</div>
      <div className="mt-1 text-lg font-semibold text-slate-900">{meta.name}</div>
      <dl className="mt-3 grid grid-cols-2 gap-2 text-sm">
        <div>
          <dt className="text-xs text-slate-500">Version</dt>
          <dd className="font-medium">{meta.version}</dd>
        </div>
        <div>
          <dt className="text-xs text-slate-500">Status</dt>
          <dd className="font-medium">{meta.status}</dd>
        </div>
        <div>
          <dt className="text-xs text-slate-500">Created by</dt>
          <dd className="font-medium">{meta.createdBy}</dd>
        </div>
        <div>
          <dt className="text-xs text-slate-500">Created date</dt>
          <dd className="font-medium">{meta.createdDate}</dd>
        </div>
        <div>
          <dt className="text-xs text-slate-500">Simulation</dt>
          <dd className="font-medium">{meta.simulationStatus}</dd>
        </div>
        <div>
          <dt className="text-xs text-slate-500">Approval</dt>
          <dd className="font-medium">{meta.approvalStatus}</dd>
        </div>
      </dl>
    </div>
  )
}

function downloadBlob(filename: string, content: string, mime: string) {
  const blob = new Blob([content], { type: mime })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

export function CiDualRunPage() {
  const [cases, setCases] = useState<StagingCaseSummary[]>([])
  const [caseCode, setCaseCode] = useState('CASE_B')
  const [draftKind, setDraftKind] = useState<DraftKind>('banking')
  const [compareLeft, setCompareLeft] = useState<'current' | 'previous'>('current')
  const [tab, setTab] = useState<ViewTab>('overview')

  const [session, setSession] = useState<StagingPolicyStudio | null>(null)
  const [dualRows, setDualRows] = useState<Record<string, unknown>[]>([])
  const [dualRaw, setDualRaw] = useState<Record<string, unknown>>({})
  const [simulation, setSimulation] = useState<SimulationResult | null>(null)
  const [approvals, setApprovals] = useState<ApprovalsContext | null>(null)
  const [draftDiff, setDraftDiff] = useState<Record<string, unknown> | null>(null)

  const [loading, setLoading] = useState(true)
  const [simulating, setSimulating] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [askIndex, setAskIndex] = useState(0)

  useEffect(() => {
    void listStagingCases()
      .then(setCases)
      .catch(() => setCases([]))
  }, [])

  const documentId = String(asRecord(session?.policyHeader).documentId ?? '')

  const loadComparison = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [studio, ws] = await Promise.all([
        getStagingPolicyStudio(draftKind),
        getStagingWorkspace(caseCode),
      ])
      setSession(studio)
      const dual = asRecord(ws.legacyVsCanonical)
      setDualRaw(dual)
      setDualRows(Array.isArray(dual.comparisons) ? dual.comparisons.map(asRecord) : [])

      const docId = String(asRecord(studio.policyHeader).documentId ?? '')
      if (docId) {
        const [appr, diff, simCtx] = await Promise.all([
          getApprovalsContext(docId).catch(() => null),
          compareDraftVersions(docId).catch(() => null),
          getSimulationContext(docId).catch(() => null),
        ])
        setApprovals(appr)
        setDraftDiff(diff)
        // Prefer latest history run if present; else leave null until user runs
        const history = asList(simCtx?.history).map(asRecord)
        if (history[0]?.runId) {
          // history entries may be summaries only — user can re-run
          setSimulation(null)
        } else {
          setSimulation(null)
        }
      } else {
        setApprovals(null)
        setDraftDiff(null)
        setSimulation(null)
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not load policy comparison')
      setSession(null)
      setDualRows([])
    } finally {
      setLoading(false)
    }
  }, [caseCode, draftKind])

  useEffect(() => {
    void loadComparison()
  }, [loadComparison])

  const runBothPoliciesSimulation = async () => {
    if (!documentId) {
      setError('Open a draft policy before running simulation comparison.')
      return
    }
    setSimulating(true)
    setError(null)
    try {
      const ctx = await getSimulationContext(documentId)
      const defaults = Array.isArray(ctx.defaultSelected)
        ? ctx.defaultSelected.map(String)
        : asList(ctx.applications).map((a) => String(asRecord(a).applicationCode ?? '')).filter(Boolean)
      const codes = defaults.slice(0, 10)
      const result = await runPolicySimulation(documentId, {
        applicationCodes: codes,
        dataSource: 'VALIDATION_FIXTURES',
        reviewer: 'credit_manager',
      })
      setSimulation(result)
      setTab('simulation')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Simulation comparison failed')
    } finally {
      setSimulating(false)
    }
  }

  const openAmbiguities = Number(
    asRecord(session?.readinessBanner).ambiguities ?? asRecord(session?.counts).openAmbiguities ?? 0,
  )
  const ruleCards = asList(session?.ruleCards)
  const ambiguityCards = asList(session?.ambiguityCards)

  const versions = useMemo(
    () => buildVersionMeta({ session, approvals, simulation, draftDiff }),
    [session, approvals, simulation, draftDiff],
  )

  const cards = useMemo(
    () =>
      buildChangeCards({
        dualComparisons: dualRows,
        draftDiff,
        simulation,
        ruleCards,
      }),
    [dualRows, draftDiff, simulation, ruleCards],
  )

  const sections = useMemo(() => groupChangesBySection(cards), [cards])
  const changedApps = useMemo(() => buildChangedApplications(simulation), [simulation])
  const executive = useMemo(
    () =>
      buildExecutiveSummary({
        cards,
        simulation,
        dualComparisons: dualRows,
        approvals,
        openAmbiguities,
      }),
    [cards, simulation, dualRows, approvals, openAmbiguities],
  )
  const drivers = useMemo(
    () => buildTopDrivers({ cards, simulation, changedApps }),
    [cards, simulation, changedApps],
  )
  const products = useMemo(
    () => buildProductImpacts({ cards, ruleCards, changedApps, draftDiff }),
    [cards, ruleCards, changedApps, draftDiff],
  )
  const definitions = useMemo(
    () => buildDefinitionChanges({ draftDiff, ambiguityCards, cards }),
    [draftDiff, ambiguityCards, cards],
  )
  const readiness = useMemo(
    () => buildApprovalReadiness(approvals, simulation, openAmbiguities),
    [approvals, simulation, openAmbiguities],
  )
  const ask = useMemo(
    () =>
      answerComparisonQuestions({
        cards,
        changedApps,
        executive,
        products,
        simulation,
      }),
    [cards, changedApps, executive, products, simulation],
  )
  const mix = simulationMix(simulation)
  // Current LOS mix is not separately returned — show proposed mix + note; derive "current" proxies from unchanged apps + flipped
  const currentProxy = useMemo(() => {
    if (!simulation) return null
    // Approximate: for changed apps, count old decisions; for unchanged, use new (=old)
    let passed = 0
    let referred = 0
    let failed = 0
    let di = 0
    const apps = asList(simulation.applications).map(asRecord)
    for (const a of apps) {
      const legacy = asRecord(asRecord(a.drillDown).legacyComparison)
      const oldD = String(a.currentLosOutcome ?? legacy.currentLos ?? a.policyResult ?? '')
      const label = oldD.toUpperCase()
      if (label.includes('PASS') || label.includes('APPROVE')) passed++
      else if (label.includes('REFER')) referred++
      else if (label.includes('FAIL') || label.includes('DECLINE')) failed++
      else if (label.includes('INSUFFICIENT') || label === 'DI') di++
      else {
        // fallback to proposed aggregates split if unknown
      }
    }
    const known = passed + referred + failed + di
    if (known === 0) return null
    return { passed, referred, failed, di, tested: apps.length }
  }, [simulation])

  const leftMeta = compareLeft === 'previous' && versions.previous ? versions.previous : versions.current

  const tabs: { id: ViewTab; label: string }[] = [
    { id: 'overview', label: 'Executive' },
    { id: 'changes', label: 'Business changes' },
    { id: 'simulation', label: 'Simulation' },
    { id: 'products', label: 'Products' },
    { id: 'definitions', label: 'Definitions' },
    { id: 'readiness', label: 'Approval readiness' },
    { id: 'ask', label: 'Ask AI' },
  ]

  const exportHtml = () => {
    const html = buildBusinessReportHtml({
      title: 'Policy Comparison — Business Report',
      executive,
      current: leftMeta,
      draft: versions.draft,
      cards,
      changedApps,
      readiness,
      aiExplanation: executive.aiExplanation,
    })
    downloadBlob('policy-comparison-report.html', html, 'text/html;charset=utf-8')
  }

  const exportCsv = () => {
    downloadBlob('policy-comparison-changes.csv', buildChangesCsv(cards, changedApps), 'text/csv;charset=utf-8')
  }

  return (
    <div>
      <PageHeader
        title="Compare Impact"
        description="Board-paper view for Credit Heads — what changed, who is affected, and whether this is safe to approve."
        actions={
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              className="bt-btn bt-btn-primary bt-btn-sm"
              disabled={simulating || loading || !documentId}
              onClick={() => void runBothPoliciesSimulation()}
            >
              {simulating ? 'Comparing decisions…' : 'Run policy comparison'}
            </button>
            <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" onClick={exportHtml}>
              Export report
            </button>
            <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" onClick={exportCsv}>
              Export CSV
            </button>
            <Link to="/credit-intelligence/policy-studio" className="bt-btn bt-btn-secondary bt-btn-sm">
              Open Policy Studio
            </Link>
          </div>
        }
      />
      <PoliciesWorkspaceNav />
      <CiFixtureBanner />
      <p className="mb-4 text-xs text-slate-500">
        Draft for review only — not live in production lending. Verified production authority is off.
      </p>

      <CiSection title="Policy selection" description="Compare current production LOS with a draft or uploaded policy version.">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <label className="text-sm">
            <span className="text-xs font-semibold uppercase text-slate-500">Baseline</span>
            <select
              className="bt-select mt-1 w-full"
              value={compareLeft}
              onChange={(e) => setCompareLeft(e.target.value as 'current' | 'previous')}
            >
              <option value="current">Current Policy (LOS)</option>
              <option value="previous" disabled={!versions.previous}>
                Previous Version {versions.previous ? `(${versions.previous.version})` : '(unavailable)'}
              </option>
            </select>
          </label>
          <label className="text-sm">
            <span className="text-xs font-semibold uppercase text-slate-500">Proposed</span>
            <select
              className="bt-select mt-1 w-full"
              value={draftKind}
              onChange={(e) => setDraftKind(e.target.value as DraftKind)}
            >
              <option value="banking">Draft / Uploaded — Banking policy</option>
              <option value="bureau">Draft / Uploaded — Bureau policy</option>
            </select>
          </label>
          <label className="text-sm">
            <span className="text-xs font-semibold uppercase text-slate-500">Sample credit file</span>
            <select
              className="bt-select mt-1 w-full"
              value={caseCode}
              onChange={(e) => setCaseCode(e.target.value)}
            >
              {(cases.length ? cases : [{ caseCode: 'CASE_B', title: 'Legacy default' } as StagingCaseSummary]).map(
                (c) => (
                  <option key={c.caseCode} value={c.caseCode}>
                    {c.title || c.caseCode}
                  </option>
                ),
              )}
            </select>
          </label>
          <div className="flex items-end">
            <button
              type="button"
              className="bt-btn bt-btn-secondary w-full"
              disabled={loading}
              onClick={() => void loadComparison()}
            >
              Refresh comparison
            </button>
          </div>
        </div>
      </CiSection>

      {loading ? (
        <CiLoadingCopy
          lines={[
            'Preparing policy comparison…',
            'Loading draft policy version',
            'Reading current LOS outcomes',
            'Summarising business changes',
          ]}
        />
      ) : null}
      {error ? <p className="mb-4 text-sm text-rose-700">{error}</p> : null}

      {!loading && session ? (
        <>
          <div className="mb-4 grid gap-4 lg:grid-cols-2">
            <MetaCard title="Baseline" meta={leftMeta} />
            <MetaCard title="Proposed" meta={versions.draft} />
          </div>

          <div className="mb-4 flex flex-wrap gap-2 border-b border-slate-200 pb-2">
            {tabs.map((t) => (
              <button
                key={t.id}
                type="button"
                onClick={() => setTab(t.id)}
                className={`rounded-full px-3 py-1.5 text-sm font-medium ${
                  tab === t.id ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
                }`}
              >
                {t.label}
              </button>
            ))}
          </div>

          {tab === 'overview' ? (
            <div className="space-y-4">
              <CiExecutiveSummary
                title="Executive summary"
                nextAction={
                  !simulation ? (
                    <button
                      type="button"
                      className="bt-btn bt-btn-primary bt-btn-sm"
                      disabled={simulating}
                      onClick={() => void runBothPoliciesSimulation()}
                    >
                      Run simulation comparison
                    </button>
                  ) : (
                    <button type="button" className="bt-btn bt-btn-primary bt-btn-sm" onClick={() => setTab('readiness')}>
                      Review approval readiness
                    </button>
                  )
                }
              >
                <p className="text-base font-semibold text-slate-900">{executive.aiExplanation}</p>
                <ul className="mt-3 list-disc space-y-1 pl-5 text-sm text-slate-700">
                  {executive.businessImpactLines.map((l) => (
                    <li key={l}>{l}</li>
                  ))}
                </ul>
              </CiExecutiveSummary>

              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                <div className="rounded-xl border border-slate-200 bg-gradient-to-br from-slate-50 to-white px-4 py-4">
                  <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Policy risk</div>
                  <div className="mt-2 text-xl font-semibold text-slate-900">{executive.risk}</div>
                </div>
                <div className="rounded-xl border border-slate-200 bg-gradient-to-br from-slate-50 to-white px-4 py-4">
                  <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Business impact</div>
                  <div className="mt-2 text-sm font-medium text-slate-800">
                    {executive.businessImpactLines[0] ?? 'See details below'}
                  </div>
                </div>
                <div className="rounded-xl border border-slate-200 bg-gradient-to-br from-slate-50 to-white px-4 py-4">
                  <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Confidence</div>
                  <div className="mt-2 text-xl font-semibold text-slate-900">{executive.confidence}</div>
                </div>
                <div className="rounded-xl border border-slate-200 bg-gradient-to-br from-slate-50 to-white px-4 py-4">
                  <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">
                    Overall recommendation
                  </div>
                  <div className="mt-2 text-xl font-semibold text-slate-900">{executive.recommendation}</div>
                </div>
              </div>

              <CiSection title="AI change explanation" description="Grounded in comparison results only.">
                <p className="text-sm leading-relaxed text-slate-800">{executive.aiExplanation}</p>
              </CiSection>

              <div className="grid gap-4 lg:grid-cols-2">
                <CiSection title="Top drivers">
                  <div className="space-y-3 text-sm">
                    <div>
                      <div className="text-xs font-semibold uppercase text-slate-500">Largest rule changes</div>
                      <ul className="mt-1 list-disc pl-5 text-slate-700">
                        {drivers.largestRuleChanges.length
                          ? drivers.largestRuleChanges.map((x) => <li key={x}>{x}</li>)
                          : <li>None identified yet</li>}
                      </ul>
                    </div>
                    <div>
                      <div className="text-xs font-semibold uppercase text-slate-500">Largest decision changes</div>
                      <ul className="mt-1 list-disc pl-5 text-slate-700">
                        {drivers.largestDecisionChanges.length
                          ? drivers.largestDecisionChanges.map((x) => <li key={x}>{x}</li>)
                          : <li>Run simulation to measure decision changes</li>}
                      </ul>
                    </div>
                  </div>
                </CiSection>
                <CiSection title="Data gaps & exceptions">
                  <div className="space-y-3 text-sm">
                    <div>
                      <div className="text-xs font-semibold uppercase text-slate-500">Largest data gaps</div>
                      <ul className="mt-1 list-disc pl-5 text-slate-700">
                        {drivers.largestDataGaps.length
                          ? drivers.largestDataGaps.map((x) => <li key={x}>{x}</li>)
                          : <li>None in current simulation</li>}
                      </ul>
                    </div>
                    <div>
                      <div className="text-xs font-semibold uppercase text-slate-500">Largest exception changes</div>
                      <ul className="mt-1 list-disc pl-5 text-slate-700">
                        {drivers.largestExceptionChanges.length
                          ? drivers.largestExceptionChanges.map((x) => <li key={x}>{x}</li>)
                          : <li>No exception-section changes listed</li>}
                      </ul>
                    </div>
                  </div>
                </CiSection>
              </div>
            </div>
          ) : null}

          {tab === 'changes' ? (
            <div className="space-y-4">
              {sections.length === 0 ? (
                <CiEmptyState
                  title="No material business changes listed"
                  detail="Dual-run and draft differences may still be loading, or the policies align on this sample. Run simulation for decision-level impact."
                  action={
                    <button type="button" className="bt-btn bt-btn-primary bt-btn-sm" onClick={() => void runBothPoliciesSimulation()}>
                      Run simulation comparison
                    </button>
                  }
                />
              ) : (
                sections.map((g) => (
                  <CiSection key={g.section} title={g.section} description={`${g.cards.length} change${g.cards.length === 1 ? '' : 's'}`}>
                    <ul className="space-y-3">
                      {g.cards.map((c) => (
                        <li key={c.id} className="rounded-xl border border-slate-200 bg-white px-4 py-4 shadow-sm">
                          <div className="text-lg font-semibold text-slate-900">{c.title}</div>
                          {c.differenceClass ? (
                            <div className="mt-1 text-xs font-medium text-sky-800">
                              {differenceClassLabel(c.differenceClass)}
                            </div>
                          ) : null}
                          <div className="mt-3 grid gap-3 sm:grid-cols-2">
                            <div className="rounded-lg bg-slate-50 px-3 py-2">
                              <div className="text-[11px] font-semibold uppercase text-slate-500">Current</div>
                              <div className="mt-1 font-semibold text-slate-900">{c.currentValue}</div>
                            </div>
                            <div className="rounded-lg bg-sky-50 px-3 py-2">
                              <div className="text-[11px] font-semibold uppercase text-sky-700">Proposed</div>
                              <div className="mt-1 font-semibold text-slate-900">{c.proposedValue}</div>
                            </div>
                          </div>
                          <div className="mt-3 text-sm">
                            <div className="text-xs font-semibold uppercase text-slate-500">Business impact</div>
                            <p className="mt-1 text-slate-800">{c.businessImpact}</p>
                          </div>
                          {c.affectedProducts.length > 0 ? (
                            <div className="mt-2 text-sm">
                              <div className="text-xs font-semibold uppercase text-slate-500">Affected products</div>
                              <p className="mt-1 font-medium text-slate-800">{c.affectedProducts.join(', ')}</p>
                            </div>
                          ) : null}
                          {c.estimatedImpact ? (
                            <div className="mt-2 text-sm text-amber-900">
                              <span className="text-xs font-semibold uppercase">Estimated impact</span>
                              <p className="mt-0.5">{c.estimatedImpact}</p>
                            </div>
                          ) : null}
                          {c.explanation ? (
                            <p className="mt-2 text-xs text-slate-500">{c.explanation}</p>
                          ) : null}
                        </li>
                      ))}
                    </ul>
                  </CiSection>
                ))
              )}
            </div>
          ) : null}

          {tab === 'simulation' ? (
            <div className="space-y-4">
              {!simulation ? (
                <CiEmptyState
                  title="No simulations have been compared yet"
                  detail="Run both policies against sample applications to see approvals, manual reviews, declines, and missing information side by side."
                  action={
                    <button
                      type="button"
                      className="bt-btn bt-btn-primary"
                      disabled={simulating}
                      onClick={() => void runBothPoliciesSimulation()}
                    >
                      {simulating ? 'Running…' : 'Run both policies'}
                    </button>
                  }
                />
              ) : (
                <>
                  <CiExecutiveSummary title="Simulation comparison">
                    <p>
                      {mix.tested} sample applications tested under the proposed draft. Decisions below use business
                      outcomes only.
                    </p>
                  </CiExecutiveSummary>

                  <div className="grid gap-4 lg:grid-cols-2">
                    <CiSection title="Current (LOS)">
                      {currentProxy ? (
                        <div className="grid grid-cols-2 gap-2">
                          <CiStatCard label="Approved" value={currentProxy.passed} tone="approved" />
                          <CiStatCard label="Manual Credit Review" value={currentProxy.referred} tone="review" />
                          <CiStatCard label="Declined" value={currentProxy.failed} tone="action" />
                          <CiStatCard label="Missing Information" value={currentProxy.di} tone="info" />
                        </div>
                      ) : (
                        <p className="text-sm text-slate-600">
                          Current LOS outcome mix is shown per application in the changed list when available.
                        </p>
                      )}
                    </CiSection>
                    <CiSection title="Proposed (draft)">
                      <div className="grid grid-cols-2 gap-2">
                        <CiStatCard label="Approved" value={mix.passed} tone="approved" />
                        <CiStatCard label="Manual Credit Review" value={mix.referred} tone="review" />
                        <CiStatCard label="Declined" value={mix.failed} tone="action" />
                        <CiStatCard label="Missing Information" value={mix.di} tone="info" />
                      </div>
                    </CiSection>
                  </div>

                  <CiSection title="Applications changed" description="Only applications whose decision differs.">
                    {changedApps.length === 0 ? (
                      <p className="text-sm text-slate-600">
                        No sample applications changed decision between current LOS and draft policy in this run.
                      </p>
                    ) : (
                      <div className="overflow-x-auto">
                        <table className="min-w-full text-left text-sm">
                          <thead className="border-b border-slate-200 text-xs uppercase text-slate-500">
                            <tr>
                              <th className="px-2 py-2">Application</th>
                              <th className="px-2 py-2">Old decision</th>
                              <th className="px-2 py-2">New decision</th>
                              <th className="px-2 py-2">Business reason</th>
                            </tr>
                          </thead>
                          <tbody>
                            {changedApps.map((a) => (
                              <tr key={a.code} className="border-b border-slate-100 align-top">
                                <td className="px-2 py-2 font-medium">
                                  {a.name}
                                  {a.product ? (
                                    <div className="text-[11px] text-slate-500">{a.product}</div>
                                  ) : null}
                                  <div className="text-[10px] text-amber-800">{softFixtureLabel()}</div>
                                </td>
                                <td className="px-2 py-2">
                                  <CiOutcomeBadge value={a.oldDecision} />
                                </td>
                                <td className="px-2 py-2">
                                  <CiOutcomeBadge value={a.newDecision} />
                                </td>
                                <td className="px-2 py-2 text-slate-700">{a.businessReason}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    )}
                  </CiSection>
                </>
              )}
            </div>
          ) : null}

          {tab === 'products' ? (
            <div className="space-y-4">
              {products.length === 0 ? (
                <CiEmptyState
                  title="No product impact isolated yet"
                  detail="Product scope appears after rule cards, simulation, or draft differences identify affected products."
                />
              ) : (
                <div className="grid gap-3 sm:grid-cols-2">
                  {products.map((p) => (
                    <div key={p.product} className="rounded-xl border border-slate-200 bg-white px-4 py-4 shadow-sm">
                      <div className="text-lg font-semibold text-slate-900">{p.product}</div>
                      <dl className="mt-3 grid grid-cols-3 gap-2 text-sm">
                        <div>
                          <dt className="text-xs text-slate-500">Rules added</dt>
                          <dd className="text-lg font-semibold">{p.rulesAdded}</dd>
                        </div>
                        <div>
                          <dt className="text-xs text-slate-500">Rules removed</dt>
                          <dd className="text-lg font-semibold">{p.rulesRemoved}</dd>
                        </div>
                        <div>
                          <dt className="text-xs text-slate-500">Threshold changes</dt>
                          <dd className="text-lg font-semibold">{p.thresholdChanges}</dd>
                        </div>
                      </dl>
                      <p className="mt-3 text-sm text-slate-700">{p.businessImpact}</p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ) : null}

          {tab === 'definitions' ? (
            <div className="space-y-4">
              {definitions.length === 0 ? (
                <CiEmptyState
                  title="No definition changes listed"
                  detail="Definition and ambiguity resolutions appear after draft package versions or ambiguous-term confirmations."
                />
              ) : (
                <CiSection title="Definition changes" description="Business terms whose meaning or mapping changed.">
                  <div className="overflow-x-auto">
                    <table className="min-w-full text-left text-sm">
                      <thead className="border-b border-slate-200 text-xs uppercase text-slate-500">
                        <tr>
                          <th className="px-2 py-2">Business term</th>
                          <th className="px-2 py-2">Previous meaning</th>
                          <th className="px-2 py-2">New meaning</th>
                          <th className="px-2 py-2">Rules depending on it</th>
                          <th className="px-2 py-2">Simulation impact</th>
                        </tr>
                      </thead>
                      <tbody>
                        {definitions.map((d, i) => (
                          <tr key={`${d.term}-${i}`} className="border-b border-slate-100 align-top">
                            <td className="px-2 py-2 font-medium">{d.term}</td>
                            <td className="px-2 py-2 text-slate-700">{d.previousMeaning}</td>
                            <td className="px-2 py-2 text-slate-700">{d.newMeaning}</td>
                            <td className="px-2 py-2 text-slate-600">{d.rulesDepending}</td>
                            <td className="px-2 py-2 text-slate-600">{d.simulationImpact}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </CiSection>
              )}
            </div>
          ) : null}

          {tab === 'readiness' ? (
            <div className="space-y-4">
              <CiExecutiveSummary title="Is this safe to approve?">
                <p className="text-lg font-semibold text-slate-900">
                  Business recommendation: {readiness.recommendation}
                </p>
              </CiExecutiveSummary>
              <CiSection title="Approval readiness checklist">
                <ul className="space-y-2">
                  {readiness.items.map((item) => (
                    <li
                      key={item.label}
                      className={`flex items-center gap-3 rounded-lg border px-3 py-2 text-sm ${
                        item.done
                          ? 'border-emerald-200 bg-emerald-50 text-emerald-900'
                          : 'border-amber-200 bg-amber-50 text-amber-950'
                      }`}
                    >
                      <span className="text-base font-bold">{item.done ? '✓' : '○'}</span>
                      {item.label}
                    </li>
                  ))}
                </ul>
                <div className="mt-4 flex flex-wrap gap-2">
                  <Link to="/credit-intelligence/policy-studio" className="bt-btn bt-btn-primary bt-btn-sm">
                    Continue in Policy Studio
                  </Link>
                  <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" onClick={exportHtml}>
                    Export executive summary
                  </button>
                </div>
              </CiSection>
            </div>
          ) : null}

          {tab === 'ask' ? (
            <div className="space-y-4">
              <CiSection title="Ask AI about changes" description="Preset questions answered only from this comparison.">
                <div className="flex flex-wrap gap-2">
                  {ask.map((q, i) => (
                    <button
                      key={q.question}
                      type="button"
                      onClick={() => setAskIndex(i)}
                      className={`rounded-full px-3 py-1.5 text-sm font-medium ${
                        askIndex === i ? 'bg-sky-700 text-white' : 'bg-slate-100 text-slate-700'
                      }`}
                    >
                      {q.question}
                    </button>
                  ))}
                </div>
                <div className="mt-4 rounded-xl border border-sky-100 bg-sky-50/70 px-4 py-4 text-sm text-slate-800">
                  <div className="text-xs font-semibold uppercase tracking-wide text-sky-800">Answer</div>
                  <p className="mt-2 whitespace-pre-wrap leading-relaxed">{ask[askIndex]?.answer}</p>
                </div>
              </CiSection>
            </div>
          ) : null}

          <CiTechnicalDetails title="Developer Diagnostics">
            {JSON.stringify(
              {
                documentId,
                caseCode,
                draftKind,
                dualSummary: {
                  ruleCount: dualRaw.ruleCount ?? dualRows.length,
                  byDifferenceClass: asRecord(dualRaw.dualPolicyComparison).byDifferenceClass,
                },
                draftDiffAvailable: draftDiff?.available,
                simulationRunId: simulation?.runId,
              },
              null,
              2,
            )}
          </CiTechnicalDetails>
        </>
      ) : null}
    </div>
  )
}
