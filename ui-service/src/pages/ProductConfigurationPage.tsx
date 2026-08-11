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
      const sel = asRecord(asRecord(data.compose))
      // keep selectors in sync from readiness refs
      const refs = asRecord(asRecord(data.readiness).references)
      if (refs.borrowerType) setBorrowerType(String(refs.borrowerType))
      if (refs.loanProduct) setLoanProduct(String(refs.loanProduct))
      if (refs.workflowId) setWorkflowId(String(refs.workflowId))
      if (refs.liveRuleSetId) setLiveRuleSetId(String(refs.liveRuleSetId))
      if (refs.scorecardId) setScorecardId(String(refs.scorecardId))
      void sel
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Golden compose failed')
    } finally {
      setBusy(false)
    }
  }

  const readiness = asRecord(result?.readiness)
  const checks = asRecord(readiness.checks)
  const status = String(result?.status ?? readiness.status ?? '—')
  const ready = Boolean(result?.ready ?? readiness.ready)

  return (
    <div className="space-y-4" data-testid="product-configuration-page">
      <PageHeader
        title="Product Configuration"
        description="Compose existing Workflow · Live Rules · Scorecard (and optional Studio policy metadata). No new engines."
      />
      <AdministrationWorkspaceNav />

      {error ? (
        <div className="rounded border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-900">{error}</div>
      ) : null}

      <section className="grid gap-3 rounded-xl border border-slate-200 bg-white p-4 sm:grid-cols-2 lg:grid-cols-3">
        <label className="text-sm">
          <span className="text-slate-600">Borrower type</span>
          <input
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
            value={borrowerType}
            onChange={(e) => setBorrowerType(e.target.value)}
          />
        </label>
        <label className="text-sm">
          <span className="text-slate-600">Loan product</span>
          <input
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
            value={loanProduct}
            onChange={(e) => setLoanProduct(e.target.value)}
          />
        </label>
        <label className="text-sm">
          <span className="text-slate-600">Intake segment</span>
          <input
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
            value={intakeSegment}
            onChange={(e) => setIntakeSegment(e.target.value)}
          />
        </label>
        <label className="text-sm sm:col-span-2">
          <span className="text-slate-600">Workflow</span>
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
        <label className="text-sm sm:col-span-2">
          <span className="text-slate-600">Live Underwriting Rule Set (production path)</span>
          <select
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
            value={liveRuleSetId}
            onChange={(e) => setLiveRuleSetId(e.target.value)}
          >
            <option value="">— select —</option>
            {rules.map((r) => (
              <option key={String(r.id)} value={String(r.id)}>
                {String(r.name)} {r.active ? '' : '[inactive]'}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm sm:col-span-2">
          <span className="text-slate-600">Live Scorecard</span>
          <select
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
            value={scorecardId}
            onChange={(e) => setScorecardId(e.target.value)}
          >
            <option value="">— select —</option>
            {scorecards.map((s) => (
              <option key={String(s.id)} value={String(s.id)}>
                {String(s.name)} v{String(s.version)} {s.active ? '' : '[inactive]'}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm sm:col-span-2">
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
        <label className="text-sm sm:col-span-2">
          <span className="text-slate-600">Policy Studio document id (metadata only)</span>
          <input
            className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
            placeholder="Optional UUID — not production authority"
            value={policyDocumentId}
            onChange={(e) => setPolicyDocumentId(e.target.value)}
          />
        </label>
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
          className={`rounded-xl border px-4 py-4 ${
            ready ? 'border-emerald-200 bg-emerald-50' : 'border-amber-200 bg-amber-50'
          }`}
          data-testid="readiness-result"
        >
          <h2 className="text-lg font-semibold text-slate-900">
            Readiness: {status}
          </h2>
          <p className="mt-1 text-sm text-slate-700">
            Runtime authority remains Live UW path · allowCanonicalAuthority=false
          </p>
          <dl className="mt-3 grid gap-2 text-sm sm:grid-cols-2">
            {Object.entries(checks).map(([k, v]) => (
              <div key={k} className="rounded border border-white/60 bg-white/70 px-2 py-1">
                <dt className="text-xs text-slate-500">{k}</dt>
                <dd className="font-medium">{String(v)}</dd>
              </div>
            ))}
          </dl>
          <div className="mt-3 text-sm">
            <h3 className="font-semibold">Versions / IDs</h3>
            <pre className="mt-1 overflow-x-auto rounded bg-white/80 p-2 text-xs">
              {JSON.stringify(readiness.references ?? asRecord(result.compose), null, 2)}
            </pre>
          </div>
          {asList(readiness.gaps).length > 0 ? (
            <div className="mt-3 text-sm">
              <h3 className="font-semibold text-amber-950">Gaps</h3>
              <ul className="mt-1 list-disc pl-5 text-amber-950">
                {asList(readiness.gaps).map((g, i) => (
                  <li key={i}>{String(g)}</li>
                ))}
              </ul>
            </div>
          ) : null}
          {asList(readiness.requiredParameters).length > 0 ? (
            <div className="mt-3 text-sm">
              <h3 className="font-semibold">Required parameters</h3>
              <ul className="mt-1 space-y-1">
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
              </ul>
            </div>
          ) : null}
        </section>
      ) : null}
    </div>
  )
}
