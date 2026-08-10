import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  getPolicyCatalogue,
  linkImmutableCataloguePolicy,
  retireCataloguePolicy,
  scheduleCataloguePolicy,
  type PolicyCatalogueEntry,
} from '@/api/creditIntelligence'
import { ApiError } from '@/api/http'
import { PageHeader } from '@/components/PageHeader'
import { PoliciesWorkspaceNav } from '@/components/workspace/PoliciesWorkspaceNav'
import { CiFixtureBanner } from '@/components/creditIntelligence/CiFixtureBanner'
import { CiEmptyState, CiExecutiveSummary, CiSection } from '@/components/creditIntelligence/CiSection'

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

export function CiPolicyCataloguePage() {
  const [rows, setRows] = useState<PolicyCatalogueEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await getPolicyCatalogue()
      setRows(asList(data.policies) as PolicyCatalogueEntry[])
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not load policy catalogue')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true)
    setError(null)
    try {
      await fn()
      await load()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Catalogue action failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <PageHeader
        title="Scheduled Policies"
        description="Durable approved and scheduled policy versions for this NBFC. Shadow resolution only."
      />
      <PoliciesWorkspaceNav />
      <CiFixtureBanner />
      <div className="mb-4 rounded-lg border border-amber-300 bg-amber-50 px-4 py-2 text-sm text-amber-950">
        Business ACTIVE ≠ Credit Intelligence production authority. allowCanonicalAuthority=false.
      </div>

      <CiExecutiveSummary
        title="What should I do next?"
        nextAction={
          <Link to="/credit-intelligence/policy-studio" className="bt-btn bt-btn-primary bt-btn-sm">
            Open Policy Studio
          </Link>
        }
      >
        <p>
          Schedule policies from Policy Settings after Checker approval. Applications resolve to exactly one
          applicable version in Shadow mode — never compare multiple policies at runtime.
        </p>
      </CiExecutiveSummary>

      {loading ? <p className="text-sm text-slate-600">Loading catalogue…</p> : null}
      {error ? <p className="text-sm text-rose-700">{error}</p> : null}

      {!loading && rows.length === 0 ? (
        <CiEmptyState
          title="No durable policies yet"
          detail="Open Policy Studio, complete approvals, then Schedule Policy to persist a catalogue entry."
          action={
            <Link to="/credit-intelligence/policy-studio" className="bt-btn bt-btn-primary bt-btn-sm">
              Policy Studio
            </Link>
          }
        />
      ) : null}

      {rows.length > 0 ? (
        <CiSection title="Catalogue" description="Persisted across restarts.">
          <div className="overflow-x-auto">
            <table className="min-w-full text-left text-sm">
              <thead className="text-slate-500">
                <tr>
                  <th className="py-2 pr-3">Policy</th>
                  <th className="py-2 pr-3">Version</th>
                  <th className="py-2 pr-3">Products</th>
                  <th className="py-2 pr-3">Status</th>
                  <th className="py-2 pr-3">Linkage</th>
                  <th className="py-2 pr-3">Effective From</th>
                  <th className="py-2 pr-3">Effective Until</th>
                  <th className="py-2 pr-3">Approved / Checker</th>
                  <th className="py-2 pr-3">Shadow apps</th>
                  <th className="py-2 pr-3">Actions</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((raw) => {
                  const r = asRecord(raw)
                  const id = String(r.applicabilityId ?? '')
                  return (
                    <tr key={id || String(r.policyVersion)} className="border-t border-slate-100">
                      <td className="py-2 pr-3 font-medium text-slate-900">
                        <div>{String(r.policyName ?? '—')}</div>
                        <div className="text-xs font-normal text-slate-500">
                          {String(
                            r.scopeSummary
                              ?? ([
                                  asList(r.products).map(String).join(', ') || 'All products',
                                  r.borrowerType ? String(r.borrowerType) : null,
                                  r.minLoanAmount || r.maxLoanAmount
                                    ? `₹${r.minLoanAmount ?? '…'}–₹${r.maxLoanAmount ?? '…'}`
                                    : null,
                                ]
                                  .filter(Boolean)
                                  .join(' · ') || '—'),
                          )}
                        </div>
                      </td>
                      <td className="py-2 pr-3">{String(r.policyVersion ?? '—')}</td>
                      <td className="py-2 pr-3">{asList(r.products).map(String).join(', ') || 'All'}</td>
                      <td className="py-2 pr-3">{String(r.status ?? '—')}</td>
                      <td className="py-2 pr-3 text-xs">
                        {String(r.linkageClass ?? '—')}
                        <div className="text-slate-500">{r.shadowRoutable ? 'Shadow-routable' : 'Not routable'}</div>
                      </td>
                      <td className="py-2 pr-3">{String(r.effectiveFrom ?? '—')}</td>
                      <td className="py-2 pr-3">{String(r.effectiveUntil ?? '—')}</td>
                      <td className="py-2 pr-3">
                        {String(r.approvedBy ?? '—')} / {String(r.checker ?? '—')}
                      </td>
                      <td className="py-2 pr-3">{String(r.applicationsEvaluatedShadow ?? 0)}</td>
                      <td className="py-2 pr-3">
                        <div className="flex flex-wrap gap-2">
                          {id && !r.shadowRoutable ? (
                            <button
                              type="button"
                              disabled={busy}
                              className="rounded bg-emerald-800 px-2 py-1 text-xs text-white disabled:opacity-50"
                              onClick={() =>
                                void run(() =>
                                  linkImmutableCataloguePolicy(id, {
                                    publishDemoPackage: true,
                                    actor: 'catalogue_ui',
                                  }),
                                )
                              }
                            >
                              Link immutable package
                            </button>
                          ) : null}
                          {id && (r.status === 'APPROVED' || r.status === 'SCHEDULED') ? (
                            <button
                              type="button"
                              disabled={busy}
                              className="rounded bg-sky-700 px-2 py-1 text-xs text-white disabled:opacity-50"
                              onClick={() =>
                                void run(() =>
                                  scheduleCataloguePolicy(id, {
                                    effectiveFrom: r.effectiveFrom,
                                    effectiveUntil: r.effectiveUntil,
                                    products: r.products,
                                    businessDate: r.effectiveFrom,
                                  }),
                                )
                              }
                            >
                              Schedule
                            </button>
                          ) : null}
                          {id && r.status !== 'RETIRED' ? (
                            <button
                              type="button"
                              disabled={busy}
                              className="rounded border border-rose-400 px-2 py-1 text-xs text-rose-800 disabled:opacity-50"
                              onClick={() => void run(() => retireCataloguePolicy(id, {}))}
                            >
                              Retire
                            </button>
                          ) : null}
                          {r.documentId ? (
                            <Link
                              className="text-xs font-medium text-sky-800 underline"
                              to="/credit-intelligence/policy-studio"
                            >
                              View
                            </Link>
                          ) : null}
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </CiSection>
      ) : null}
    </div>
  )
}
