import { useEffect, useMemo, useState } from 'react'
import { PageHeader } from '@/components/PageHeader'
import { AdministrationWorkspaceNav } from '@/components/workspace/AdministrationWorkspaceNav'
import {
  composeProductConfiguration,
  getGoldenProductConfiguration,
  getProductConfigurationOptions,
} from '@/api/liveReadiness'
import { ApiError } from '@/api/http'

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

export function ProductConfigurationPage() {
  const [options, setOptions] = useState<Record<string, unknown> | null>(null)
  const [borrowerType, setBorrowerType] = useState('COMPANY')
  const [loanProduct, setLoanProduct] = useState('TERM_LOAN')
  const [intakeSegment, setIntakeSegment] = useState('BORROWER')
  const [workflowId, setWorkflowId] = useState('')
  const [liveRuleSetId, setLiveRuleSetId] = useState('')
  const [scorecardId, setScorecardId] = useState('')
  const [assignmentRuleSetId, setAssignmentRuleSetId] = useState('')
  const [policyDocumentId, setPolicyDocumentId] = useState('')
  const [result, setResult] = useState<Record<string, unknown> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    getProductConfigurationOptions()
      .then((data) => {
        setOptions(data)
        const golden = asRecord(asRecord(data.goldenPreset).selection)
        if (golden.borrowerType) setBorrowerType(String(golden.borrowerType))
        if (golden.loanProduct) setLoanProduct(String(golden.loanProduct))
        if (golden.workflowId) setWorkflowId(String(golden.workflowId))
        if (golden.liveRuleSetId) setLiveRuleSetId(String(golden.liveRuleSetId))
        if (golden.scorecardId) setScorecardId(String(golden.scorecardId))
      })
      .catch((e) => setError(e instanceof ApiError ? e.message : 'Failed to load options'))
  }, [])

  const workflows = useMemo(
    () =>
      asList(options?.workflows)
        .map(asRecord)
        .filter(
          (w) =>
            (!borrowerType || String(w.borrowerType) === borrowerType) &&
            (!loanProduct || String(w.loanProduct) === loanProduct),
        ),
    [options, borrowerType, loanProduct],
  )
  const rules = useMemo(
    () =>
      asList(options?.liveRuleSets)
        .map(asRecord)
        .filter(
          (r) =>
            (!borrowerType || String(r.borrowerType) === borrowerType) &&
            (!loanProduct || String(r.loanProduct).replace(' ', '_') === loanProduct.replace(' ', '_')),
        ),
    [options, borrowerType, loanProduct],
  )
  const scorecards = useMemo(
    () =>
      asList(options?.scorecards)
        .map(asRecord)
        .filter(
          (s) =>
            (!borrowerType || String(s.borrowerType) === borrowerType) &&
            (!loanProduct || String(s.loanProduct).replace(' ', '_') === loanProduct.replace(' ', '_')),
        ),
    [options, borrowerType, loanProduct],
  )

  const runCompose = async () => {
    setBusy(true)
    setError(null)
    try {
      const data = await composeProductConfiguration({
        borrowerType,
        loanProduct,
        intakeSegment,
        workflowId: workflowId || null,
        liveRuleSetId: liveRuleSetId || null,
        scorecardId: scorecardId || null,
        assignmentRuleSetId: assignmentRuleSetId || null,
        policyDocumentId: policyDocumentId || null,
      })
      setResult(data)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Compose failed')
    } finally {
      setBusy(false)
    }
  }

  const runGolden = async () => {
    setBusy(true)
    setError(null)
    try {
      const data = await getGoldenProductConfiguration()
      setResult(data)
      const refs = asRecord(asRecord(data.readiness).references)
      if (refs.borrowerType) setBorrowerType(String(refs.borrowerType))
      if (refs.loanProduct) setLoanProduct(String(refs.loanProduct))
      if (refs.workflowId) setWorkflowId(String(refs.workflowId))
      if (refs.liveRuleSetId) setLiveRuleSetId(String(refs.liveRuleSetId))
      if (refs.scorecardId) setScorecardId(String(refs.scorecardId))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Golden compose failed')
    } finally {
      setBusy(false)
    }
  }

  const readiness = asRecord(result?.readiness)
  const compose = asRecord(result?.compose)
  const checks = asRecord(readiness.checks)
  const status = String(result?.status ?? readiness.status ?? '—')
  const ready = Boolean(result?.ready ?? readiness.ready)
  const runtime = asRecord(compose.runtimeResolution)
  const lms = asRecord(compose.lms)
  const plp = asRecord(compose.plp)
  const policyStudio = asRecord(compose.policyStudio)
  const mismatchKeys = asList(result?.mismatchKeys)

  return (
    <div className="space-y-4" data-testid="product-configuration-page">
      <PageHeader
        title="Product Configuration"
        description="Business-facing composition of existing Workflow · Live Rules · Scorecard. Preview must match runtime resolvers."
      />
      <AdministrationWorkspaceNav />

      {error ? (
        <div className="rounded border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-900">{error}</div>
      ) : null}

      <section className="rounded-xl border border-slate-200 bg-white p-4">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">Scope</h2>
        <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <label className="text-sm">
            <span className="text-slate-600">Product</span>
            <input
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              value={loanProduct}
              onChange={(e) => setLoanProduct(e.target.value)}
            />
          </label>
          <label className="text-sm">
            <span className="text-slate-600">Borrower type</span>
            <input
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              value={borrowerType}
              onChange={(e) => setBorrowerType(e.target.value)}
            />
          </label>
          <label className="text-sm">
            <span className="text-slate-600">Segment</span>
            <input
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              value={intakeSegment}
              onChange={(e) => setIntakeSegment(e.target.value)}
            />
          </label>
        </div>
      </section>

      <section className="rounded-xl border border-slate-200 bg-white p-4">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">Runtime Configuration</h2>
        <p className="mt-1 text-xs text-slate-500">
          Labels below are production authorities. Policy Studio is governance / shadow only.
        </p>
        <div className="mt-3 grid gap-3 sm:grid-cols-1 lg:grid-cols-2">
          <label className="text-sm">
            <span className="text-slate-600">Runtime Workflow</span>
            <select
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              value={workflowId}
              onChange={(e) => setWorkflowId(e.target.value)}
            >
              <option value="">— select —</option>
              {workflows.map((w) => (
                <option key={String(w.id)} value={String(w.id)}>
                  {String(w.name)} (v{String(w.version)}) {w.active ? '' : '[inactive]'}
                </option>
              ))}
            </select>
          </label>
          <label className="text-sm">
            <span className="text-slate-600">Runtime Rule Set (Live Underwriting)</span>
            <select
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              value={liveRuleSetId}
              onChange={(e) => setLiveRuleSetId(e.target.value)}
            >
              <option value="">— select —</option>
              {rules.map((r) => (
                <option key={String(r.id)} value={String(r.id)}>
                  {String(r.name)} priority={String(r.priority ?? '—')} {r.active ? '' : '[inactive]'}
                </option>
              ))}
            </select>
          </label>
          <label className="text-sm">
            <span className="text-slate-600">Runtime Scorecard</span>
            <select
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              value={scorecardId}
              onChange={(e) => setScorecardId(e.target.value)}
            >
              <option value="">— select —</option>
              {scorecards.map((s) => (
                <option key={String(s.id)} value={String(s.id)}>
                  {String(s.name)} v{String(s.version)} [{String(s.status ?? (s.active ? 'ACTIVE' : '—'))}]
                </option>
              ))}
            </select>
          </label>
          <label className="text-sm">
            <span className="text-slate-600">Policy Studio Policy (Governance only / Shadow)</span>
            <input
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              placeholder="Optional UUID — not production authority"
              value={policyDocumentId}
              onChange={(e) => setPolicyDocumentId(e.target.value)}
            />
          </label>
          <label className="text-sm">
            <span className="text-slate-600">Assignment rule set (optional)</span>
            <select
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              value={assignmentRuleSetId}
              onChange={(e) => setAssignmentRuleSetId(e.target.value)}
            >
              <option value="">— none —</option>
              {asList(options?.assignmentRuleSets).map((raw) => {
                const a = asRecord(raw)
                return (
                  <option key={String(a.id)} value={String(a.id)}>
                    {String(a.name)}
                  </option>
                )
              })}
            </select>
          </label>
        </div>
      </section>

      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          className="bt-btn bt-btn-primary bt-btn-sm"
          disabled={busy}
          onClick={() => void runCompose()}
          data-testid="compose-readiness"
        >
          Check readiness
        </button>
        <button
          type="button"
          className="bt-btn bt-btn-secondary bt-btn-sm"
          disabled={busy}
          onClick={() => void runGolden()}
          data-testid="golden-compose"
        >
          Load golden Company Term Loan
        </button>
      </div>

      {result ? (
        <section
          className={`space-y-4 rounded-xl border px-4 py-4 ${
            ready ? 'border-emerald-200 bg-emerald-50' : 'border-amber-200 bg-amber-50'
          }`}
          data-testid="readiness-result"
        >
          <div>
            <h2 className="text-lg font-semibold text-slate-900">Status: {status}</h2>
            <p className="mt-1 text-sm text-slate-700">
              {String(compose.scopeSummary ?? `${loanProduct} · ${borrowerType} · ${intakeSegment}`)}
            </p>
            <p className="mt-1 text-xs text-slate-600">
              Preview matches runtime: {String(result.productConfigMatchesRuntime ?? '—')} · allowCanonicalAuthority=false
            </p>
          </div>

          {mismatchKeys.length > 0 ? (
            <div className="rounded border border-rose-300 bg-rose-50 px-3 py-2 text-sm text-rose-950">
              <div className="font-semibold">Routing mismatch</div>
              <ul className="mt-1 list-disc pl-5">
                {mismatchKeys.map((k, i) => (
                  <li key={i}>{String(k)}</li>
                ))}
              </ul>
            </div>
          ) : null}

          <div className="grid gap-3 sm:grid-cols-2">
            <div className="rounded border border-white/70 bg-white/80 px-3 py-2 text-sm">
              <div className="text-xs font-semibold uppercase text-slate-500">Runtime Workflow</div>
              <div className="font-medium">{String(asRecord(compose.workflow).name ?? '—')}</div>
              <div className="text-xs text-slate-600">
                v{String(asRecord(compose.workflow).version ?? '—')} · match {String(asRecord(runtime.workflow).match ?? '—')}
              </div>
            </div>
            <div className="rounded border border-white/70 bg-white/80 px-3 py-2 text-sm">
              <div className="text-xs font-semibold uppercase text-slate-500">Runtime Rule Set</div>
              <div className="font-medium">{String(asRecord(compose.liveRuleSet).name ?? '—')}</div>
              <div className="text-xs text-slate-600">
                priority {String(asRecord(compose.liveRuleSet).priority ?? '—')} · match {String(asRecord(runtime.liveRuleSet).match ?? '—')}
              </div>
            </div>
            <div className="rounded border border-white/70 bg-white/80 px-3 py-2 text-sm">
              <div className="text-xs font-semibold uppercase text-slate-500">Runtime Scorecard</div>
              <div className="font-medium">{String(asRecord(compose.scorecard).name ?? '—')}</div>
              <div className="text-xs text-slate-600">
                v{String(asRecord(compose.scorecard).version ?? '—')} · {String(asRecord(compose.scorecard).status ?? '—')}
              </div>
            </div>
            <div className="rounded border border-dashed border-slate-300 bg-white/60 px-3 py-2 text-sm">
              <div className="text-xs font-semibold uppercase text-slate-500">Policy Studio (Governance only)</div>
              <div className="font-medium">
                {policyStudio.policyName
                  ? `${String(policyStudio.policyName)} ${String(policyStudio.policyVersion ?? '')}`
                  : 'Not linked'}
              </div>
              <div className="text-xs text-amber-900">Not production authority until controlled publication</div>
            </div>
          </div>

          <div>
            <h3 className="text-sm font-semibold text-slate-800">Data Readiness</h3>
            <ul className="mt-2 space-y-1 text-sm">
              {asList(readiness.requiredParameters).map((raw, i) => {
                const p = asRecord(raw)
                return (
                  <li key={i}>
                    <span className="font-medium">{String(p.businessName ?? p.parameterId)}</span>
                    {' — '}
                    {String(p.classification)}
                    {p.gap ? <span className="text-amber-900"> · {String(p.gap)}</span> : null}
                  </li>
                )
              })}
              {asList(readiness.requiredParameters).length === 0 ? (
                <li className="text-slate-600">No required parameters extracted for this selection.</li>
              ) : null}
            </ul>
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            <div className="rounded border border-white/70 bg-white/80 px-3 py-2 text-sm">
              <div className="text-xs font-semibold uppercase text-slate-500">LMS</div>
              <div>Entry: {String(lms.lmsEntry ?? '—')}</div>
              <div>Product code: {String(lms.lmsProductCode ?? '—')}</div>
              <div>Status: {String(lms.status ?? '—')}</div>
            </div>
            <div className="rounded border border-white/70 bg-white/80 px-3 py-2 text-sm">
              <div className="text-xs font-semibold uppercase text-slate-500">PLP</div>
              <div>{String(plp.status ?? 'NOT_REQUIRED')}</div>
              <div className="text-xs text-slate-600">{String(plp.note ?? '')}</div>
            </div>
          </div>

          {asList(readiness.gaps).length > 0 ? (
            <div className="text-sm">
              <h3 className="font-semibold text-amber-950">Blockers</h3>
              <ul className="mt-1 list-disc pl-5 text-amber-950">
                {asList(readiness.gaps).map((g, i) => (
                  <li key={i}>{String(g)}</li>
                ))}
              </ul>
            </div>
          ) : null}

          <dl className="grid gap-2 text-sm sm:grid-cols-2">
            {Object.entries(checks).map(([k, v]) => (
              <div key={k} className="rounded border border-white/60 bg-white/70 px-2 py-1">
                <dt className="text-xs text-slate-500">{k}</dt>
                <dd className="font-medium">{String(v)}</dd>
              </div>
            ))}
          </dl>
        </section>
      ) : null}
    </div>
  )
}
