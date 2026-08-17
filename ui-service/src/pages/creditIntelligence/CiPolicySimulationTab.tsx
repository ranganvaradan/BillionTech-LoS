import { useEffect, useMemo, useState } from 'react'
import {
  getPolicyTestContext,
  runPolicyApplicationTest,
  runPolicyQuickTest,
  type PolicyTestContext,
  type PolicyTestResult,
} from '@/api/creditIntelligence'
import { ApiError } from '@/api/http'
import { CiExecutiveSummary, CiSection, CiTechnicalDetails } from '@/components/creditIntelligence/CiSection'
import { SuggestCalculationWorkflow } from '@/components/dataParameters/SuggestCalculationWorkflow'
import { testInputDisplayLabel } from '@/lib/policyStudio/lenderTruthDisplay'

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

type Mode = 'QUICK' | 'APPLICATION'

/**
 * POLICY-UX-2E — Credit Manager Test experience (Quick / Existing Application).
 * Reuses draft evaluator; does not mutate applications or policy definitions.
 */
export function CiPolicySimulationTab({
  documentId,
  policyName,
  busy,
  setBusy,
  onError,
  onResolveParameter,
}: {
  documentId: string
  policyName?: string
  busy: boolean
  setBusy: (v: boolean) => void
  onError: (msg: string | null) => void
  prospectDemoMode?: boolean
  onResolveParameter?: () => void
}) {
  const [mode, setMode] = useState<Mode>('QUICK')
  const [ctx, setCtx] = useState<PolicyTestContext | null>(null)
  const [result, setResult] = useState<PolicyTestResult | null>(null)
  const [loading, setLoading] = useState(true)
  const [testValues, setTestValues] = useState<Record<string, string>>({})
  const [appCode, setAppCode] = useState('')
  const [howOpen, setHowOpen] = useState<Record<string, boolean>>({})

  const load = async () => {
    setLoading(true)
    onError(null)
    try {
      const data = await getPolicyTestContext(documentId)
      setCtx(data)
      const defaults: Record<string, string> = {}
      for (const row of asList(data.requiredParameters)) {
        const p = asRecord(row)
        const key = String(p.parameterKey ?? '')
        if (!key) continue
        if (p.defaultHint != null && p.defaultHint !== '') {
          defaults[key] = String(p.defaultHint)
        }
      }
      setTestValues((prev) => ({ ...defaults, ...prev }))
      const apps = asList(data.applications)
      if (apps.length && !appCode) {
        setAppCode(String(asRecord(apps[0]).applicationCode ?? ''))
      }
    } catch (e) {
      onError(e instanceof ApiError ? e.message : 'Could not load Test context')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [documentId])

  const required = useMemo(() => asList(ctx?.requiredParameters).map(asRecord), [ctx])
  const readiness = asRecord(ctx?.readiness)
  const needsAttention = asList(readiness.needsAttention).map(asRecord)
  const apps = useMemo(() => asList(ctx?.applications).map(asRecord), [ctx])
  const recent = asList(result?.recentTests ?? ctx?.recentTests).map(asRecord)
  const summary = asRecord(result?.summary)
  const ruleResults = asList(result?.ruleResults).map(asRecord)
  const currentVsDraft = asRecord(result?.currentVsDraft)
  const blockers = asList(result?.blockers).map(String)

  const setValue = (key: string, value: string) => {
    setTestValues((prev) => ({ ...prev, [key]: value }))
  }

  const run = async () => {
    setBusy(true)
    onError(null)
    try {
      const values: Record<string, string | number> = {}
      for (const [k, v] of Object.entries(testValues)) {
        if (v === '' || v == null) continue
        const n = Number(String(v).replace(/[,₹%\s]/g, ''))
        values[k] = Number.isFinite(n) && String(v).trim() !== '' ? n : v
      }
      const data =
        mode === 'QUICK'
          ? await runPolicyQuickTest(documentId, { testValues: values })
          : await runPolicyApplicationTest(documentId, {
              applicationCode: appCode,
              testValues: values,
            })
      setResult(data)
    } catch (e) {
      onError(e instanceof ApiError ? e.message : 'Could not run test')
    } finally {
      setBusy(false)
    }
  }

  if (loading && !ctx) {
    return <p className="text-sm text-slate-600">Loading test…</p>
  }

  return (
    <div className="space-y-4" data-testid="policy-test-experience">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h2 className="text-lg font-semibold text-slate-900">Test Policy</h2>
          <p className="text-sm text-slate-600">
            {policyName ? `${policyName} · ` : ''}
            If this draft were applied, what would happen and why?
          </p>
        </div>
        <p className="text-xs text-slate-500" data-testid="test-safety-banner">
          Test only — does not change the application or underwriting decision
        </p>
      </div>

      <div className="flex flex-wrap gap-2" data-testid="test-mode-tabs">
        {(
          [
            ['QUICK', 'Quick Test'],
            ['APPLICATION', 'Existing Application'],
          ] as const
        ).map(([id, label]) => (
          <button
            key={id}
            type="button"
            className={`rounded-full px-3 py-1.5 text-sm font-medium ${
              mode === id
                ? 'bg-slate-900 text-white'
                : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
            }`}
            onClick={() => {
              setMode(id)
              setResult(null)
            }}
          >
            {label}
          </button>
        ))}
        <button
          type="button"
          disabled
          className="cursor-not-allowed rounded-full px-3 py-1.5 text-sm font-medium text-slate-400 ring-1 ring-slate-200"
          title="Historical batch requires a wired corpus — not faked in this phase"
        >
          Historical / Batch (future)
        </button>
      </div>

      {mode === 'APPLICATION' ? (
        <CiSection title="Select application" description="Stored validation applications — read-only.">
          <select
            className="bt-input max-w-xl"
            value={appCode}
            onChange={(e) => setAppCode(e.target.value)}
            data-testid="test-application-select"
          >
            {apps.map((a) => (
              <option key={String(a.applicationCode)} value={String(a.applicationCode)}>
                {String(a.displayName ?? a.applicationCode)}
                {a.product ? ` · ${String(a.product)}` : ''}
              </option>
            ))}
          </select>
          <p className="mt-1 text-xs text-slate-500">{String(ctx?.applicationNote ?? '')}</p>
        </CiSection>
      ) : null}

      <CiSection
        title="Required parameters"
        description={`${Number(readiness.required ?? required.length)} required · ${Number(readiness.availableAutomatically ?? 0)} automatic · ${Number(readiness.unresolved ?? 0)} unresolved`}
      >
        {needsAttention.length ? (
          <div
            className="mb-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950"
            data-testid="test-needs-attention"
          >
            <div className="font-medium">Needs attention</div>
            <ul className="mt-1 list-disc pl-5">
              {needsAttention.map((n, i) => (
                <li key={i}>
                  {String(n.businessName)} — {String(n.issue).replace(/_/g, ' ')}
                </li>
              ))}
            </ul>
          </div>
        ) : null}

        <div className="grid gap-3 sm:grid-cols-2">
          {required.map((p) => {
            const key = String(p.parameterKey)
            const status = String(p.status ?? '')
            const unresolved = status === 'UNRESOLVED'
            const how = asRecord(p.howCalculated)
            return (
              <label
                key={key}
                className="block rounded-lg border border-slate-200 bg-white px-3 py-2"
                data-testid={`test-param-${key}`}
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="text-sm font-medium text-slate-900">{String(p.businessName)}</span>
                  <StatusPill status={status} valuePresent={Boolean(testValues[key])} />
                </div>
                <input
                  className="bt-input mt-1 w-full"
                  value={testValues[key] ?? ''}
                  placeholder={
                    unresolved ? 'Enter temporary test value' : String(p.defaultHint ?? '')
                  }
                  onChange={(e) => setValue(key, e.target.value)}
                />
                <p className="mt-1 text-xs text-slate-500">
                  {unresolved
                    ? 'Cannot evaluate until resolved — temporary value is test-only'
                    : String(p.sourceLabel ?? '')}
                  {testValues[key] && (unresolved || status === 'MANUAL_INPUT') ? (
                    <span className="ml-1 font-medium text-sky-800">· Test value only</span>
                  ) : null}
                </p>
                {p.calculationRequired !== true && (how.calculation || how.source) ? (
                  <button
                    type="button"
                    className="mt-1 text-xs font-medium text-sky-800 hover:underline"
                    onClick={() => setHowOpen((o) => ({ ...o, [key]: !o[key] }))}
                  >
                    How calculated
                  </button>
                ) : null}
                {p.calculationRequired !== true && howOpen[key] ? (
                  <div className="mt-1 rounded bg-slate-50 px-2 py-1 text-xs text-slate-700">
                    {how.source ? <div>Source: {String(how.source)}</div> : null}
                    {how.period ? <div>Period: {String(how.period)}</div> : null}
                    {how.calculation ? <div>Calculation: {String(how.calculation)}</div> : null}
                  </div>
                ) : null}
                {p.calculationRequired === true &&
                String(p.canonicalParameterId ?? p.parameterId ?? '') ? (
                  <SuggestCalculationWorkflow
                    canonicalParameterId={String(p.canonicalParameterId ?? p.parameterId)}
                    businessName={String(p.businessName ?? '')}
                    calculationRequired
                  />
                ) : null}
                {unresolved && onResolveParameter ? (
                  <button
                    type="button"
                    className="mt-1 text-xs font-medium text-emerald-800 hover:underline"
                    onClick={() => onResolveParameter()}
                  >
                    Resolve parameter
                  </button>
                ) : null}
              </label>
            )
          })}
        </div>
      </CiSection>

      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          className="bt-btn bt-btn-primary"
          data-testid="run-test"
          disabled={busy || (mode === 'APPLICATION' && !appCode)}
          onClick={() => void run()}
        >
          Run Test
        </button>
        <span className="text-xs text-slate-500">Save Draft is always available — testing is not required.</span>
      </div>

      {result ? (
        <div className="space-y-4" data-testid="test-results">
          {blockers.length ? (
            <div className="rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-950">
              <div className="font-semibold">Cannot fully evaluate this policy</div>
              <ul className="mt-1 list-disc pl-5">
                {blockers.map((b) => (
                  <li key={b}>{b}</li>
                ))}
              </ul>
            </div>
          ) : null}

          <CiExecutiveSummary title="Simulated Decision">
            <p
              className="text-2xl font-semibold tracking-tight text-slate-900"
              data-testid="simulated-decision"
            >
              {String(result.simulatedDecision ?? '—')}
            </p>
            <p className="mt-1 text-sm text-slate-700">
              {Number(summary.passed ?? 0)} passed · {Number(summary.failed ?? 0)} failed ·{' '}
              {Number(summary.needsManualInput ?? 0)} needs input ·{' '}
              {Number(summary.couldNotEvaluate ?? 0)} could not be evaluated
            </p>
          </CiExecutiveSummary>

          {Object.keys(currentVsDraft).length ? (
            <CiSection title="Current vs draft" description="From existing comparison output.">
              <dl className="grid gap-2 text-sm sm:grid-cols-3">
                <div>
                  <dt className="text-slate-500">Current</dt>
                  <dd className="font-medium">{String(currentVsDraft.currentLos ?? '—')}</dd>
                </div>
                <div>
                  <dt className="text-slate-500">Draft</dt>
                  <dd className="font-medium">{String(currentVsDraft.draftPolicy ?? '—')}</dd>
                </div>
                <div>
                  <dt className="text-slate-500">Impact</dt>
                  <dd className="font-medium">{String(currentVsDraft.impactClass ?? '—')}</dd>
                </div>
              </dl>
              {currentVsDraft.reason ? (
                <p className="mt-2 text-sm text-slate-700">{String(currentVsDraft.reason)}</p>
              ) : null}
            </CiSection>
          ) : null}

          <CiSection title="Hard Rules" description="Pass/fail per underwriting rule.">
            <div className="space-y-3">
              {ruleResults.map((r, i) => (
                <RuleResultCard
                  key={i}
                  rule={r}
                  howOpen={howOpen}
                  setHowOpen={setHowOpen}
                  onResolve={onResolveParameter}
                />
              ))}
              {ruleResults.length === 0 ? (
                <p className="text-sm text-slate-500">No applicable underwriting rules evaluated.</p>
              ) : null}
            </div>
          </CiSection>

          {asRecord(result.scorecard).outcome || asList(result.scorecardFactors).length || result.scorecardIncluded ? (
            <CiSection
              title="Scorecard"
              description="Optional Policy-linked score — subordinate to hard rules / final Policy outcome."
            >
              <dl className="mb-3 grid gap-2 text-sm sm:grid-cols-3" data-testid="test-scorecard-summary">
                <div>
                  <dt className="text-slate-500">Total score</dt>
                  <dd className="font-medium">
                    {String(asRecord(result.scorecard).weightedScore ?? asRecord(result.scorecard).totalScore ?? '—')}
                  </dd>
                </div>
                <div>
                  <dt className="text-slate-500">Scorecard outcome</dt>
                  <dd className="font-medium">{String(asRecord(result.scorecard).outcome ?? '—')}</dd>
                </div>
                <div>
                  <dt className="text-slate-500">Mode</dt>
                  <dd className="font-medium">
                    {String(asRecord(result.scorecard).scoringMode ?? 'POLICY_WEIGHTED_V2')}
                  </dd>
                </div>
              </dl>
              <div className="overflow-x-auto">
                <table className="min-w-full text-left text-xs">
                  <thead className="text-slate-500">
                    <tr>
                      <th className="py-1 pr-2">Factor</th>
                      <th className="py-1 pr-2">Raw value</th>
                      <th className="py-1 pr-2">Factor score</th>
                      <th className="py-1 pr-2">Raw weight</th>
                      <th className="py-1 pr-2">Normalized</th>
                      <th className="py-1 pr-2">Contribution</th>
                    </tr>
                  </thead>
                  <tbody>
                    {asList(result.scorecardFactors ?? asRecord(result.scorecard).factorEvidence).map((raw, i) => {
                      const f = asRecord(raw)
                      return (
                        <tr key={i} className="border-t border-slate-100">
                          <td className="py-1 pr-2 font-mono text-[11px]">
                            {String(f.canonicalParameterId ?? f.parameter ?? f.factor ?? '—')}
                          </td>
                          <td className="py-1 pr-2">{String(f.rawValue ?? f.value ?? '—')}</td>
                          <td className="py-1 pr-2">{String(f.factorScore ?? f.bandPointsEarned ?? '—')}</td>
                          <td className="py-1 pr-2">{String(f.rawWeight ?? f.weight ?? '—')}</td>
                          <td className="py-1 pr-2">
                            {f.normalizedWeight != null ? `${Number(f.normalizedWeight).toFixed(1)}%` : '—'}
                          </td>
                          <td className="py-1 pr-2">{String(f.weightedContribution ?? f.contribution ?? '—')}</td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            </CiSection>
          ) : null}

          <CiExecutiveSummary title="Final Policy outcome">
            <p className="text-lg font-semibold text-slate-900" data-testid="final-policy-outcome">
              {String(result.simulatedDecision ?? summary.outcome ?? '—')}
            </p>
            <p className="mt-1 text-xs text-slate-600">
              Policy remains sole underwriting authority. Scorecard contributes when linked; it does not
              replace hard rules.
            </p>
          </CiExecutiveSummary>

          {recent.length ? (
            <CiSection title="Recent tests" description="Session-only history — not a durable audit table.">
              <ul className="divide-y divide-slate-100 text-sm">
                {recent.slice(0, 8).map((h, i) => (
                  <li key={i} className="flex flex-wrap justify-between gap-2 py-2">
                    <span>
                      {String(h.testTypeLabel ?? h.testType)} · {String(h.application ?? 'Quick test')}
                    </span>
                    <span className="text-slate-600">
                      {String(h.decision ?? '—')} · {String(h.policyVersion ?? '')}
                    </span>
                  </li>
                ))}
              </ul>
            </CiSection>
          ) : null}

          <CiTechnicalDetails title="Evaluation path (technical)">
            <pre className="whitespace-pre-wrap text-xs text-slate-600">
              {String(result.evaluationEngine ?? '')}
              {'\n'}
              {String(result.decisionPrecedence ?? '')}
              {'\n'}
              scorecardIncluded={String(result.scorecardIncluded ?? false)}
              {'\n'}
              applicationMutated={String(result.applicationMutated ?? false)}
              {'\n'}
              policyMutated={String(result.policyMutated ?? false)}
              {'\n'}
              allowCanonicalAuthority=false
            </pre>
          </CiTechnicalDetails>
        </div>
      ) : null}
    </div>
  )
}

function StatusPill({ status, valuePresent }: { status: string; valuePresent?: boolean }) {
  const label = testInputDisplayLabel(status, valuePresent)
  const cls =
    status === 'UNRESOLVED'
      ? 'bg-amber-100 text-amber-900'
      : status === 'MANUAL_INPUT' || status === 'MANUAL'
        ? 'bg-sky-100 text-sky-900'
        : 'bg-slate-100 text-slate-700'
  return <span className={`rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase ${cls}`}>{label}</span>
}

function RuleResultCard({
  rule,
  howOpen,
  setHowOpen,
  onResolve,
}: {
  rule: Record<string, unknown>
  howOpen: Record<string, boolean>
  setHowOpen: (fn: (o: Record<string, boolean>) => Record<string, boolean>) => void
  onResolve?: () => void
}) {
  const actuals = asList(rule.actuals).map(asRecord)
  const children = asList(rule.children).map(asRecord)
  const result = String(rule.result ?? '')
  const tone =
    result === 'PASS'
      ? 'border-emerald-200 bg-emerald-50'
      : result === 'FAIL'
        ? 'border-rose-200 bg-rose-50'
        : 'border-amber-200 bg-amber-50'

  return (
    <article className={`rounded-lg border px-3 py-3 ${tone}`} data-testid="rule-result-card">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h4 className="text-sm font-semibold text-slate-900">{String(rule.ruleName)}</h4>
        <span className="text-xs font-bold uppercase tracking-wide text-slate-800">{result}</span>
      </div>
      {actuals.length ? (
        <ul className="mt-2 space-y-0.5 text-sm text-slate-800">
          {actuals.map((a, i) => (
            <li key={i}>
              {String(a.label)}: <strong>{String(a.displayValue ?? a.value ?? '—')}</strong>
              {a.unresolved ? <span className="text-amber-800"> (unresolved)</span> : null}
              {asRecord(a.howCalculated).calculation ? (
                <button
                  type="button"
                  className="ml-2 text-xs text-sky-800 hover:underline"
                  onClick={() =>
                    setHowOpen((o) => ({
                      ...o,
                      [`r-${String(rule.ruleName)}-${i}`]: !o[`r-${String(rule.ruleName)}-${i}`],
                    }))
                  }
                >
                  How calculated
                </button>
              ) : null}
              {howOpen[`r-${String(rule.ruleName)}-${i}`] ? (
                <div className="text-xs text-slate-600">
                  {String(asRecord(a.howCalculated).calculation)}
                </div>
              ) : null}
            </li>
          ))}
        </ul>
      ) : null}
      <p className="mt-2 text-sm text-slate-700">
        <span className="text-slate-500">Rule · </span>
        {String(rule.policyCondition ?? '—')}
      </p>
      {rule.treatment ? (
        <p className="text-sm text-slate-700">
          <span className="text-slate-500">Treatment · </span>
          {String(rule.treatment)}
        </p>
      ) : null}
      {rule.why ? (
        <p className="mt-1 text-sm text-slate-800">
          <span className="text-slate-500">Why · </span>
          {String(rule.why)}
        </p>
      ) : null}

      {Boolean(rule.compound) && children.length ? (
        <div className="mt-3 rounded border border-white/60 bg-white/70 px-3 py-2" data-testid="compound-children">
          <p className="text-xs font-semibold uppercase text-slate-600">
            {String(rule.compoundSubtitle ?? 'Allow only if ALL:')}
          </p>
          <ul className="mt-1 space-y-1 text-sm">
            {children.map((c, i) => (
              <li key={i}>
                <span className="font-mono text-xs">{String(c.mark ?? '')}</span>{' '}
                {String(c.ruleName)}
                {c.unresolved ? <span className="text-amber-800"> — unresolved</span> : null}
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {Boolean(rule.resolveParameter) && onResolve ? (
        <button
          type="button"
          className="mt-2 text-xs font-medium text-emerald-800 hover:underline"
          onClick={() => onResolve()}
        >
          Resolve parameter
        </button>
      ) : null}
    </article>
  )
}
