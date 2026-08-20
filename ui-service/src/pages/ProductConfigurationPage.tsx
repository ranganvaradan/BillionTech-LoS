import { useEffect, useMemo, useState } from 'react'
import { PageHeader } from '@/components/PageHeader'
import { AdministrationWorkspaceNav } from '@/components/workspace/AdministrationWorkspaceNav'
import {
  composeProductConfiguration,
  getGoldenProductConfiguration,
  getProductConfigurationOptions,
  listExternalProductMappings,
  listExternalProductSystems,
  createExternalProductMapping,
  patchExternalProductMappingStatus,
} from '@/api/liveReadiness'
import { ApiError } from '@/api/http'
import { distinctCanonicalLosProductOptions } from '@/lib/productConfiguration/losProductOption'

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

export function ProductConfigurationPage() {
  const [options, setOptions] = useState<Record<string, unknown> | null>(null)
  const [borrowerType, setBorrowerType] = useState('COMPANY')
  const [loanProduct, setLoanProduct] = useState('')
  const [intakeSegment, setIntakeSegment] = useState('BORROWER')
  const [workflowId, setWorkflowId] = useState('')
  const [liveRuleSetId, setLiveRuleSetId] = useState('')
  const [scorecardId, setScorecardId] = useState('')
  const [assignmentRuleSetId, setAssignmentRuleSetId] = useState('')
  const [policyDocumentId, setPolicyDocumentId] = useState('')
  const [externalProductSystems, setExternalProductSystems] = useState<string[]>([])
  const [externalSystem, setExternalSystem] = useState<string>('')
  const [bookTypeFilter, setBookTypeFilter] = useState<string>('')
  const [mappings, setMappings] = useState<any[]>([])
  const [mappingsBusy, setMappingsBusy] = useState(false)
  const [mappingAsOf, setMappingAsOf] = useState(() => new Date().toISOString().slice(0, 10))

  const [createExternalProductCode, setCreateExternalProductCode] = useState('')
  const [createBookType, setCreateBookType] = useState('OWN_BOOK')
  const [createVersion, setCreateVersion] = useState<number>(1)
  const [createStatus, setCreateStatus] = useState('INACTIVE')
  const [createEffectiveFrom, setCreateEffectiveFrom] = useState(mappingAsOf)
  const [createEffectiveTo, setCreateEffectiveTo] = useState('9999-12-31')

  const [result, setResult] = useState<Record<string, unknown> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const canonicalProductOptions = useMemo(
    () => distinctCanonicalLosProductOptions(asList(options?.products)),
    [options],
  )

  useEffect(() => {
    getProductConfigurationOptions()
      .then((data) => {
        setOptions(data)
        const golden = asRecord(asRecord(data.goldenPreset).selection)
        if (golden.borrowerType) setBorrowerType(String(golden.borrowerType))
        if (golden.loanProduct) {
          setLoanProduct(String(golden.loanProduct))
        } else if (Array.isArray(data.products) && data.products.length > 0) {
          // Canonical product list comes from the backend (no free typing).
          const first = distinctCanonicalLosProductOptions(data.products)[0]
          if (first?.code) setLoanProduct(first.code)
        }
        if (golden.workflowId) setWorkflowId(String(golden.workflowId))
        if (golden.liveRuleSetId) setLiveRuleSetId(String(golden.liveRuleSetId))
        if (golden.scorecardId) setScorecardId(String(golden.scorecardId))
      })
      .catch((e) => setError(e instanceof ApiError ? e.message : 'Failed to load options'))
  }, [])

  // external_product_mapping admin dropdown + catalogue loader for selected LOS Product.
  useEffect(() => {
    if (!loanProduct) return
    const prevExternalSystem = externalSystem
    setMappings([])
    setExternalProductSystems([])
    setExternalSystem('')
    setCreateExternalProductCode('')
    void (async () => {
      try {
        const systems = await listExternalProductSystems(loanProduct)
        setExternalProductSystems(systems)
        const preferred = systems.includes(prevExternalSystem) ? prevExternalSystem : systems[0] ?? ''
        setExternalSystem(preferred)
      } catch (e) {
        // Non-fatal: mapping admin may be empty if catalogue isn't present yet.
        setExternalProductSystems([])
        setExternalSystem('')
      }
    })()
  }, [loanProduct])

  useEffect(() => {
    if (!externalSystem) {
      setMappings([])
      return
    }
    setMappingsBusy(true)
    void (async () => {
      try {
        const rows = await listExternalProductMappings({
          losProductCode: loanProduct,
          externalSystem,
          bookType: bookTypeFilter || undefined,
          asOf: mappingAsOf,
        })
        setMappings(rows)
        const versionMax = rows.reduce((acc, r) => (Number(r.version ?? acc) > acc ? Number(r.version) : acc), 1)
        setCreateVersion(Number.isFinite(versionMax) ? versionMax + 1 : 1)
      } catch (e) {
        setMappings([])
      } finally {
        setMappingsBusy(false)
      }
    })()
  }, [loanProduct, externalSystem, bookTypeFilter, mappingAsOf])

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
      const canonicalMode = policyDocumentId.trim().length > 0
      const data = await composeProductConfiguration({
        borrowerType,
        loanProduct,
        intakeSegment,
        workflowId: workflowId || null,
        liveRuleSetId: canonicalMode ? null : liveRuleSetId || null,
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
  const canonicalMode = policyDocumentId.trim().length > 0
  const policyStudioNotProductionAuthority = Boolean(policyStudio?.notProductionAuthority)

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
            <select
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              value={loanProduct}
              onChange={(e) => setLoanProduct(e.target.value)}
              disabled={!options || canonicalProductOptions.length === 0}
            >
              <option value="">— select —</option>
              {canonicalProductOptions.map((o) => (
                <option key={o.code} value={o.code}>
                  {o.label}
                </option>
              ))}
            </select>
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
          {canonicalMode
            ? 'Canonical mode: Policy Studio is underwriting authority (frozen). Live Rule Set is legacy/historical only.'
            : 'Legacy mode: Live Rule Set is underwriting authority. Policy Studio is publication metadata only.'}
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
            <span className="text-slate-600">
              {canonicalMode ? 'Legacy Live Rule Set (non-authoritative)' : 'Runtime Rule Set (Live Underwriting)'}
            </span>
            <select
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              value={liveRuleSetId}
              onChange={(e) => setLiveRuleSetId(e.target.value)}
              disabled={canonicalMode}
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
            <span className="text-slate-600">Policy Studio Policy (Underwriting Authority)</span>
            <input
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              placeholder="UUID (set to enable canonical Policy Studio underwriting mode)"
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

      <section className="rounded-xl border border-slate-200 bg-white p-4 space-y-3">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">External Product Mapping (Admin)</h2>
        <div className="grid gap-3 sm:grid-cols-4">
          <label className="text-sm">
            <span className="text-slate-600">External system</span>
            <select
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              value={externalSystem}
              onChange={(e) => setExternalSystem(e.target.value)}
              disabled={mappingsBusy || externalProductSystems.length === 0}
            >
              {externalProductSystems.length === 0 ? <option value="">— none —</option> : null}
              {externalProductSystems.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </label>
          <label className="text-sm">
            <span className="text-slate-600">Book type</span>
            <select
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
              value={bookTypeFilter}
              onChange={(e) => setBookTypeFilter(e.target.value)}
              disabled={mappingsBusy}
            >
              <option value="">All</option>
              <option value="OWN_BOOK">Own Book</option>
              <option value="COLENDING">Colending</option>
            </select>
          </label>
          <label className="text-sm">
            <span className="text-slate-600">Effective “as-of” date</span>
            <input
              type="date"
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm"
              value={mappingAsOf}
              onChange={(e) => setMappingAsOf(e.target.value)}
            />
          </label>
          <label className="text-sm">
            <span className="text-slate-600">LOS Product</span>
            <select
              className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm"
              value={loanProduct}
              onChange={(e) => setLoanProduct(e.target.value)}
              disabled={!options || canonicalProductOptions.length === 0}
            >
              <option value="">— select —</option>
              {canonicalProductOptions.map((o) => (
                <option key={o.code} value={o.code}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
        </div>

        <div className="rounded border border-slate-100 bg-slate-50 p-3 text-sm">
          <div className="flex items-center justify-between gap-2">
            <div className="font-semibold">Mappings</div>
            <div className="text-xs text-slate-600">
              {mappingsBusy ? 'Loading…' : `${String(mappings.length)} row(s)`}
            </div>
          </div>

          {mappingsBusy ? null : mappings.length === 0 ? (
            <div className="mt-2 text-xs text-slate-600">
              No mappings found for the selected LOS Product + External System.
            </div>
          ) : (
            <div className="mt-3 overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="text-xs text-slate-500">
                    <th className="py-1 pr-3">Version</th>
                    <th className="py-1 pr-3">Book type</th>
                    <th className="py-1 pr-3">External product code</th>
                    <th className="py-1 pr-3">Effective period</th>
                    <th className="py-1 pr-3">Status</th>
                    <th className="py-1">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {mappings.map((m) => {
                    const status = String(m.status ?? '—')
                    const activeToday = Boolean(m.activeToday)
                    return (
                      <tr key={String(m.id)} className="border-t border-slate-100 align-top">
                        <td className="py-2 pr-3">
                          <div className="font-medium">{String(m.version ?? '—')}</div>
                          {activeToday ? <div className="text-xs text-emerald-700">Active (as-of)</div> : null}
                        </td>
                        <td className="py-2 pr-3">
                          <span className="text-xs">{String(m.bookType ?? '—')}</span>
                        </td>
                        <td className="py-2 pr-3">
                          <div className="font-medium">{String(m.externalProductCode ?? '—')}</div>
                        </td>
                        <td className="py-2 pr-3 text-xs text-slate-700">
                          {String(m.effectiveFrom ?? '—')} → {String(m.effectiveTo ?? '—')}
                        </td>
                        <td className="py-2 pr-3 text-xs">
                          <span className={activeToday ? 'text-emerald-700' : 'text-slate-700'}>{status}</span>
                        </td>
                        <td className="py-2">
                          <div className="flex flex-wrap gap-2">
                            <button
                              type="button"
                              className="bt-btn bt-btn-sm bt-btn-secondary"
                              disabled={activeToday || String(m.status ?? '').toUpperCase() === 'RETIRED'}
                              onClick={async () => {
                                try {
                                  await patchExternalProductMappingStatus(String(m.id), 'ACTIVE')
                                  const rows = await listExternalProductMappings({ losProductCode: loanProduct, externalSystem, bookType: bookTypeFilter || undefined, asOf: mappingAsOf })
                                  setMappings(rows)
                                } catch (e) {
                                  setError(e instanceof Error ? e.message : 'Activate failed')
                                }
                              }}
                            >
                              Activate
                            </button>
                            <button
                              type="button"
                              className="bt-btn bt-btn-sm bt-btn-secondary"
                              disabled={String(m.status ?? '').toUpperCase() !== 'ACTIVE'}
                              onClick={async () => {
                                try {
                                  await patchExternalProductMappingStatus(String(m.id), 'INACTIVE')
                                  const rows = await listExternalProductMappings({ losProductCode: loanProduct, externalSystem, bookType: bookTypeFilter || undefined, asOf: mappingAsOf })
                                  setMappings(rows)
                                } catch (e) {
                                  setError(e instanceof Error ? e.message : 'Deactivate failed')
                                }
                              }}
                            >
                              Deactivate
                            </button>
                            <button
                              type="button"
                              className="bt-btn bt-btn-sm bt-btn-secondary"
                              disabled={String(m.status ?? '').toUpperCase() === 'RETIRED'}
                              onClick={async () => {
                                try {
                                  await patchExternalProductMappingStatus(String(m.id), 'RETIRED')
                                  const rows = await listExternalProductMappings({ losProductCode: loanProduct, externalSystem, bookType: bookTypeFilter || undefined, asOf: mappingAsOf })
                                  setMappings(rows)
                                } catch (e) {
                                  setError(e instanceof Error ? e.message : 'Retire failed')
                                }
                              }}
                            >
                              Retire
                            </button>
                          </div>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div className="rounded border border-slate-100 bg-white p-3">
          <div className="font-semibold text-sm">Create new mapping version</div>
          <div className="mt-2 grid gap-3 sm:grid-cols-2">
            <label className="text-sm">
              <span className="text-slate-600">External product code</span>
              <input
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm"
                value={createExternalProductCode}
                onChange={(e) => setCreateExternalProductCode(e.target.value)}
                placeholder="e.g. EXTERNAL_CODE"
              />
            </label>
            <label className="text-sm">
              <span className="text-slate-600">Version</span>
              <input
                type="number"
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm"
                value={createVersion}
                onChange={(e) => setCreateVersion(Number(e.target.value))}
              />
            </label>
            <label className="text-sm">
              <span className="text-slate-600">Book type *</span>
              <select
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm"
                value={createBookType}
                onChange={(e) => setCreateBookType(e.target.value)}
              >
                <option value="OWN_BOOK">Own Book</option>
                <option value="COLENDING">Colending</option>
              </select>
            </label>
            <label className="text-sm">
              <span className="text-slate-600">Status</span>
              <select
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm"
                value={createStatus}
                onChange={(e) => setCreateStatus(e.target.value)}
              >
                <option value="INACTIVE">INACTIVE</option>
                <option value="ACTIVE">ACTIVE</option>
              </select>
            </label>
            <label className="text-sm">
              <span className="text-slate-600">Effective From</span>
              <input
                type="date"
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm"
                value={createEffectiveFrom}
                onChange={(e) => setCreateEffectiveFrom(e.target.value)}
              />
            </label>
            <label className="text-sm">
              <span className="text-slate-600">Effective To</span>
              <input
                type="date"
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm"
                value={createEffectiveTo}
                onChange={(e) => setCreateEffectiveTo(e.target.value)}
              />
            </label>
            <div className="flex items-end">
              <button
                type="button"
                className="bt-btn bt-btn-primary bt-btn-sm"
                disabled={!externalSystem || !createExternalProductCode || !createEffectiveFrom || !createEffectiveTo}
                onClick={async () => {
                  try {
                    await createExternalProductMapping({
                      losProductCode: loanProduct,
                      externalSystem,
                      bookType: createBookType,
                      externalProductCode: createExternalProductCode,
                      version: createVersion,
                      status: createStatus,
                      effectiveFrom: createEffectiveFrom,
                      effectiveTo: createEffectiveTo,
                    })
                    const rows = await listExternalProductMappings({ losProductCode: loanProduct, externalSystem, bookType: bookTypeFilter || undefined, asOf: mappingAsOf })
                    setMappings(rows)
                    setCreateExternalProductCode('')
                  } catch (e) {
                    setError(e instanceof Error ? e.message : 'Create mapping failed')
                  }
                }}
              >
                Create mapping
              </button>
            </div>
          </div>
          <div className="mt-2 text-xs text-slate-600">
            Evidence rows are immutable: to change external product code / effective window, create a new version row.
          </div>
        </div>
      </section>

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
              <div className="text-xs font-semibold uppercase text-slate-500">
                Policy Studio ({policyStudioNotProductionAuthority ? 'Governance only / non-authoritative' : 'Canonical underwriting authority'})
              </div>
              <div className="font-medium">
                {policyStudio.policyName
                  ? `${String(policyStudio.policyName)} ${String(policyStudio.policyVersion ?? '')}`
                  : 'Not linked'}
              </div>
              <div className={policyStudioNotProductionAuthority ? 'text-xs text-amber-900' : 'text-xs text-emerald-900'}>
                {policyStudioNotProductionAuthority ? 'Not production authority until controlled publication' : 'Underwriting authority (frozen)'}
              </div>
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
              {lms.ready && lms.externalProductMappingId ? (
                <div className="mt-2 space-y-1 text-xs text-slate-700">
                  <div>
                    External mapping: {String(lms.externalSystem ?? '—')} / code {String(lms.externalProductCode ?? '—')}
                  </div>
                  <div>
                    Mapping v{String(lms.externalProductMappingVersion ?? '—')} · {String(lms.externalMappingEffectiveFrom ?? '—')} →{' '}
                    {String(lms.externalMappingEffectiveTo ?? '—')}
                  </div>
                  <div>Mapping status: {String(lms.externalMappingStatus ?? '—')}</div>
                </div>
              ) : !lms.ready ? (
                <div className="mt-2 space-y-1 text-xs text-rose-900">
                  <div>Reason: {String(lms.reason ?? '—')}</div>
                  <div>Reason code: {String(lms.reasonCode ?? '—')}</div>
                </div>
              ) : null}
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
