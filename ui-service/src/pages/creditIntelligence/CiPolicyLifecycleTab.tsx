import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  approveLifecyclePolicy,
  createLifecycleVersion,
  getLifecycleHistory,
  getLifecycleSettings,
  retireLifecyclePolicy,
  resolveShadowApplication,
  saveLifecycleDraft,
  scheduleLifecyclePolicy,
  submitLifecycleReview,
  type StagingPolicyStudio,
} from '@/api/creditIntelligence'
import { ApiError } from '@/api/http'
import { CiExecutiveSummary, CiSection, CiTechnicalDetails } from '@/components/creditIntelligence/CiSection'

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

function gateMark(ok: boolean): string {
  return ok ? '✓' : '○'
}

export function CiPolicyLifecycleTab({
  documentId,
  busy,
  setBusy,
  onError,
  onSessionRefresh,
  session,
}: {
  documentId: string
  busy: boolean
  setBusy: (v: boolean) => void
  onError: (msg: string | null) => void
  onSessionRefresh?: (next?: StagingPolicyStudio) => void
  session?: StagingPolicyStudio | null
}) {
  const [settings, setSettings] = useState<Record<string, unknown> | null>(null)
  const [history, setHistory] = useState<unknown[]>([])
  const [loading, setLoading] = useState(true)
  const [products, setProducts] = useState('DIGILEAP')
  const [effectiveFrom, setEffectiveFrom] = useState('2026-09-01')
  const [effectiveUntil, setEffectiveUntil] = useState('')
  const [customerSegment, setCustomerSegment] = useState('')
  const [reason, setReason] = useState('')
  const [shadowResult, setShadowResult] = useState<Record<string, unknown> | null>(null)
  const [evalDate, setEvalDate] = useState('2026-08-25')
  const [shadowProduct, setShadowProduct] = useState('DIGILEAP')

  const reload = async () => {
    setLoading(true)
    onError(null)
    try {
      const s = await getLifecycleSettings(documentId)
      setSettings(s)
      const hdr = asRecord(s.policySettings)
      const productsVal = asList(hdr.products)
      if (productsVal.length) setProducts(productsVal.map(String).join(', '))
      if (hdr.effectiveFrom) setEffectiveFrom(String(hdr.effectiveFrom).slice(0, 10))
      if (hdr.effectiveUntil) setEffectiveUntil(String(hdr.effectiveUntil).slice(0, 10))
      if (hdr.customerSegment) setCustomerSegment(String(hdr.customerSegment))
      if (hdr.reasonForChange) setReason(String(hdr.reasonForChange))
      const hist = await getLifecycleHistory(documentId)
      setHistory(asList(hist.history))
    } catch (e) {
      onError(e instanceof ApiError ? e.message : 'Could not load policy settings')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void reload()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [documentId])

  const body = () => ({
    products: products
      .split(',')
      .map((p) => p.trim().toUpperCase())
      .filter(Boolean),
    effectiveFrom: effectiveFrom || null,
    effectiveUntil: effectiveUntil || null,
    customerSegment: customerSegment || null,
    reasonForChange: reason || null,
  })

  const run = async (fn: () => Promise<StagingPolicyStudio | Record<string, unknown>>) => {
    setBusy(true)
    onError(null)
    try {
      const data = await fn()
      const life = asRecord(asRecord(data).lifecycle)
      if (Object.keys(life).length) setSettings(life)
      else if (asRecord(data).policySettings) setSettings(data as Record<string, unknown>)
      onSessionRefresh?.(data as StagingPolicyStudio)
      await reload()
    } catch (e) {
      onError(e instanceof ApiError ? e.message : 'Lifecycle action failed')
    } finally {
      setBusy(false)
    }
  }

  if (loading && !settings) {
    return <p className="text-sm text-slate-600">Loading policy settings…</p>
  }

  const hdr = asRecord(settings?.policySettings ?? asRecord(session).policySettings)
  const impl = asRecord(settings?.implementationStatus ?? asRecord(session).implementationStatus)
  const status = String(settings?.businessStatus ?? hdr.status ?? 'DRAFT')
  const ready = Boolean(settings?.readyToSchedule)
  const blockers = asList(settings?.readyToScheduleBlockers)
  const authority = asRecord(settings?.authoritySeparation)

  const approvedSummary = (() => {
    const rules = asList(asRecord(session).underwritingRules)
    const counts = asRecord(asRecord(session).counts)
    const ruleCount =
      rules.length ||
      Number(counts.rulesTotal ?? counts.rules ?? 0) ||
      0
    let autoDerived = 0
    let manual = 0
    for (const row of rules) {
      const r = asRecord(row)
      const binding = String(r.bindingState ?? r.evaluatedFrom ?? r.resolutionState ?? '')
      const ops = asList(r.operands)
      if (ops.length) {
        for (const op of ops) {
          const o = asRecord(op)
          const st = String(o.resolutionState ?? o.status ?? o.evaluatedFrom ?? '').toUpperCase()
          if (st.includes('MANUAL')) manual += 1
          else if (st.includes('DERIVED') || st.includes('RAW') || st.includes('AUTOMATIC')) autoDerived += 1
        }
      } else if (/manual/i.test(binding)) {
        manual += 1
      } else if (/derived|raw|automatic|ready/i.test(binding)) {
        autoDerived += 1
      }
    }
    const ingest = asRecord(asRecord(session).ingestionBinding)
    if (!autoDerived && !manual) {
      autoDerived = Number(ingest.automatic ?? ingest.derived ?? 0) || 0
      manual = Number(ingest.manualInputs ?? ingest.manual ?? 0) || 0
    }
    return { ruleCount, autoDerived, manual }
  })()

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-amber-300 bg-amber-50 px-4 py-2 text-sm text-amber-950">
        <div className="font-semibold">Policy business status ≠ production authority</div>
        <p className="mt-1">
          Status: <strong>{status}</strong>
          {' · '}
          Credit Intelligence production authority:{' '}
          <strong>{String(authority.creditIntelligenceProductionAuthority ?? 'DISABLED')}</strong>
          {' · '}
          allowCanonicalAuthority=false
        </p>
      </div>

      <CiExecutiveSummary title="Versions & activation">
        <p className="text-sm text-slate-700">
          Draft → Ready for review → Approved → Active/Scheduled — using existing governance controls.
        </p>
        {asRecord(settings?.scopeSummary).appliesTo ? (
          <p className="mt-2 text-sm text-slate-800">
            <span className="text-slate-500">Applies to · </span>
            {String(asRecord(settings?.scopeSummary).appliesTo)}
          </p>
        ) : null}
      </CiExecutiveSummary>

      {['APPROVED', 'SCHEDULED', 'ACTIVE'].includes(status.toUpperCase()) ? (
        <section
          className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-4"
          data-testid="policy-approved-summary"
        >
          <h3 className="text-base font-semibold text-emerald-950">Policy approved</h3>
          <dl className="mt-2 grid gap-1 text-sm text-emerald-950 sm:grid-cols-2">
            <div>
              <dt className="text-xs text-emerald-800">Scope</dt>
              <dd>{String(asRecord(settings?.scopeSummary).appliesTo ?? '—')}</dd>
            </div>
            <div>
              <dt className="text-xs text-emerald-800">Version</dt>
              <dd>{String(hdr.policyVersion ?? '—')}</dd>
            </div>
            <div>
              <dt className="text-xs text-emerald-800">Underwriting rules</dt>
              <dd>{String(approvedSummary.ruleCount)}</dd>
            </div>
            <div>
              <dt className="text-xs text-emerald-800">Automatic/derived parameters</dt>
              <dd>{String(approvedSummary.autoDerived)}</dd>
            </div>
            <div>
              <dt className="text-xs text-emerald-800">Manual parameters</dt>
              <dd>{String(approvedSummary.manual)}</dd>
            </div>
            <div>
              <dt className="text-xs text-emerald-800">Status</dt>
              <dd>{status}</dd>
            </div>
          </dl>
          <p className="mt-2 text-xs text-emerald-900">
            Next business step: define scoring factors separately — not every policy rule becomes a
            scorecard factor. Bands, weights and cut-offs require Credit Manager confirmation.
          </p>
          <div className="mt-3 flex flex-wrap gap-2">
            <Link to="/underwriting-scorecards" className="bt-btn bt-btn-primary bt-btn-sm" data-testid="create-scorecard-handoff">
              Create Scorecard
            </Link>
            <Link to="/underwriting-scorecards" className="bt-btn bt-btn-secondary bt-btn-sm">
              Open Scorecard
            </Link>
          </div>
        </section>
      ) : null}

      <CiSection title="Policy definition" description="Business metadata — no technical IDs.">
        <dl className="grid gap-3 sm:grid-cols-2 text-sm">
          <div>
            <dt className="text-slate-500">Policy Name</dt>
            <dd className="font-medium text-slate-900">{String(hdr.policyName ?? '—')}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Version</dt>
            <dd className="font-medium text-slate-900">{String(hdr.policyVersion ?? '—')}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Policy Type</dt>
            <dd className="font-medium text-slate-900">{String(hdr.policyType ?? '—')}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Status</dt>
            <dd className="font-medium text-slate-900">{status}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Replaces</dt>
            <dd className="font-medium text-slate-900">{String(hdr.replaces ?? '—')}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Approved By / Checker</dt>
            <dd className="font-medium text-slate-900">
              {String(hdr.approvedBy ?? '—')} / {String(hdr.checker ?? '—')}
            </dd>
          </div>
        </dl>

        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          <label className="text-sm">
            <span className="text-slate-600">Effective from</span>
            <input
              type="date"
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              value={effectiveFrom}
              onChange={(e) => setEffectiveFrom(e.target.value)}
              disabled={busy}
            />
          </label>
          <label className="text-sm">
            <span className="text-slate-600">Effective until (optional)</span>
            <input
              type="date"
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              value={effectiveUntil}
              onChange={(e) => setEffectiveUntil(e.target.value)}
              disabled={busy}
            />
          </label>
          <label className="text-sm sm:col-span-2">
            <span className="text-slate-600">Reason for change</span>
            <input
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              disabled={busy}
            />
          </label>
        </div>
        <p className="mt-3 text-xs text-slate-500">
          Product / borrower / amount scope: edit on the Scope tab (not free-text JSON here).
          Current products for schedule payload: {products || 'All'}.
        </p>
      </CiSection>

      <CiSection title="Policy implementation status">
        <ul className="grid gap-2 sm:grid-cols-3 text-sm">
          <li>Understanding {gateMark(Boolean(impl.understanding))}</li>
          <li>Data Readiness {gateMark(Boolean(impl.dataReadiness))}</li>
          <li>Tests {gateMark(Boolean(impl.tests))}</li>
          <li>Simulation {gateMark(Boolean(impl.simulation))}</li>
          <li>Credit Manager {gateMark(Boolean(impl.creditManager))}</li>
          <li>Checker {gateMark(Boolean(impl.checker))}</li>
        </ul>
        {ready ? (
          <p className="mt-3 text-sm font-semibold text-emerald-800">READY TO SCHEDULE</p>
        ) : (
          <div className="mt-3 text-sm text-amber-900">
            <p className="font-semibold">Not ready to schedule</p>
            {blockers.length > 0 ? (
              <ul className="mt-1 list-disc pl-5">
                {blockers.map((b) => (
                  <li key={String(b)}>{String(b)}</li>
                ))}
              </ul>
            ) : null}
          </div>
        )}
      </CiSection>

      <CiSection title="Actions" description="Business lifecycle — Activate Canonical Authority is not available.">
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            disabled={busy}
            className="rounded bg-slate-800 px-3 py-2 text-sm text-white disabled:opacity-50"
            onClick={() => void run(() => saveLifecycleDraft(documentId, body()))}
          >
            Save Draft
          </button>
          <button
            type="button"
            disabled={busy}
            className="rounded bg-slate-700 px-3 py-2 text-sm text-white disabled:opacity-50"
            onClick={() => void run(() => submitLifecycleReview(documentId, body()))}
          >
            Submit for Review
          </button>
          <button
            type="button"
            disabled={busy}
            className="rounded bg-emerald-700 px-3 py-2 text-sm text-white disabled:opacity-50"
            onClick={() => void run(() => approveLifecyclePolicy(documentId, body()))}
          >
            Approve Policy
          </button>
          <button
            type="button"
            disabled={busy}
            className="rounded bg-sky-700 px-3 py-2 text-sm text-white disabled:opacity-50"
            onClick={() => void run(() => scheduleLifecyclePolicy(documentId, { ...body(), businessDate: evalDate }))}
          >
            Schedule Policy
          </button>
          <button
            type="button"
            disabled={busy}
            className="rounded border border-slate-400 px-3 py-2 text-sm text-slate-800 disabled:opacity-50"
            onClick={() => void run(() => createLifecycleVersion(documentId, { reasonForChange: reason || 'New version' }))}
          >
            Create New Version
          </button>
          <button
            type="button"
            disabled={busy}
            className="rounded border border-rose-400 px-3 py-2 text-sm text-rose-800 disabled:opacity-50"
            onClick={() => void run(() => retireLifecyclePolicy(documentId, {}))}
          >
            Retire Policy
          </button>
        </div>
        <p className="mt-2 text-xs text-slate-500">
          Send for Checker remains on the Approvals tab (maker-checker). Comparison belongs under Portfolio
          Intelligence → Policy Impact Lab — not application processing.
        </p>
      </CiSection>

      <CiSection title="Shadow application resolution" description="Staging demo only — one policy selected.">
        <div className="flex flex-wrap gap-3 items-end">
          <label className="text-sm">
            <span className="text-slate-600">Application</span>
            <input className="mt-1 block rounded border border-slate-300 px-3 py-2" value="APP-X" readOnly />
          </label>
          <label className="text-sm">
            <span className="text-slate-600">Product</span>
            <input
              className="mt-1 block rounded border border-slate-300 px-3 py-2"
              value={shadowProduct}
              onChange={(e) => setShadowProduct(e.target.value)}
            />
          </label>
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
            disabled={busy}
            className="rounded bg-indigo-700 px-3 py-2 text-sm text-white disabled:opacity-50"
            onClick={() => {
              void (async () => {
                setBusy(true)
                onError(null)
                try {
                  setShadowResult(
                    await resolveShadowApplication({
                      applicationCode: 'APP-X',
                      productCode: shadowProduct,
                      evaluationDate: evalDate,
                    }),
                  )
                } catch (e) {
                  onError(e instanceof ApiError ? e.message : 'Shadow resolve failed')
                } finally {
                  setBusy(false)
                }
              })()
            }}
          >
            Resolve (shadow)
          </button>
        </div>
        {shadowResult ? (
          <div className="mt-3 rounded border border-slate-200 bg-slate-50 px-4 py-3 text-sm">
            <div className="font-semibold text-slate-900">
              {String(shadowResult.banner ?? shadowResult.outcome)}
            </div>
            <p className="mt-1 text-slate-700">{String(shadowResult.reason ?? '')}</p>
            {asRecord(shadowResult.selectedPolicy).policyName ? (
              <p className="mt-2">
                Resolved policy:{' '}
                <strong>
                  {String(asRecord(shadowResult.selectedPolicy).policyName)}{' '}
                  {String(asRecord(shadowResult.selectedPolicy).policyVersion)}
                </strong>
              </p>
            ) : null}
            <p className="mt-2 text-xs text-slate-500">
              Mode: {String(shadowResult.mode)} · Production authority DISABLED
            </p>
          </div>
        ) : null}
      </CiSection>

      <CiSection title="Policy history">
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="text-slate-500">
              <tr>
                <th className="py-1 pr-3">Version</th>
                <th className="py-1 pr-3">Status</th>
                <th className="py-1 pr-3">Effective From</th>
                <th className="py-1 pr-3">Effective Until</th>
                <th className="py-1 pr-3">Approved By</th>
                <th className="py-1 pr-3">Replaces</th>
                <th className="py-1 pr-3">Reason</th>
              </tr>
            </thead>
            <tbody>
              {history.map((raw, i) => {
                const row = asRecord(raw)
                return (
                  <tr key={i} className="border-t border-slate-100">
                    <td className="py-1.5 pr-3">{String(row.policyVersion ?? '—')}</td>
                    <td className="py-1.5 pr-3">{String(row.status ?? '—')}</td>
                    <td className="py-1.5 pr-3">{String(row.effectiveFrom ?? '—')}</td>
                    <td className="py-1.5 pr-3">{String(row.effectiveUntil ?? '—')}</td>
                    <td className="py-1.5 pr-3">{String(row.approvedBy ?? '—')}</td>
                    <td className="py-1.5 pr-3">{String(row.replaces ?? '—')}</td>
                    <td className="py-1.5 pr-3">{String(row.reasonForChange ?? '—')}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
        <p className="mt-2 text-xs text-slate-500">
          View / Compare Versions: analytical only — Portfolio Intelligence → Policy Impact Lab (placeholder).
        </p>
      </CiSection>

      <CiTechnicalDetails>
        <pre className="whitespace-pre-wrap text-xs text-slate-600">
          {JSON.stringify(
            {
              allowCanonicalAuthority: false,
              businessStatus: status,
              productionAuthority: 'DISABLED',
              portfolioIntelligence: 'Policy Impact Lab — not enabled',
            },
            null,
            2,
          )}
        </pre>
      </CiTechnicalDetails>
    </div>
  )
}
