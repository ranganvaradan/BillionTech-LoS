import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getP2ValidationDashboard, getDecisionPolicyCorpusDashboard, runDecisionPolicyCorpusValidation, runP2Validation } from '@/api/creditIntelligence'
import { ApiError } from '@/api/http'
import { PageHeader } from '@/components/PageHeader'
import { AdministrationWorkspaceNav } from '@/components/workspace/AdministrationWorkspaceNav'
import { CiFixtureBanner } from '@/components/creditIntelligence/CiFixtureBanner'
import { CiEmptyState, CiExecutiveSummary, CiSection, CiStatCard } from '@/components/creditIntelligence/CiSection'

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

export function CiP2ValidationDashboardPage() {
  const [data, setData] = useState<Record<string, unknown> | null>(null)
  const [dpV1, setDpV1] = useState<Record<string, unknown> | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(await getP2ValidationDashboard())
      try {
        setDpV1(await getDecisionPolicyCorpusDashboard())
      } catch {
        /* DP-V1 optional until deploy */
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not load P2 dashboard')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const run = async () => {
    setBusy(true)
    setError(null)
    try {
      const result = await runP2Validation({})
      setData(result)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'P2 validation run failed')
    } finally {
      setBusy(false)
    }
  }

  const runDpV1 = async () => {
    setBusy(true)
    setError(null)
    try {
      setDpV1(await runDecisionPolicyCorpusValidation({}))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'DP-V1 validation run failed')
    } finally {
      setBusy(false)
    }
  }

  const d = data ?? {}
  const cert = String(d.certificationStatus ?? 'INSUFFICIENT_EVIDENCE')
  const defects = asList(d.defects)
  const cases = asList(d.cases)
  const comparison = asRecord(d.comparisonDistribution)
  const discovery = asRecord(d.discovery)
  const dp = dpV1 ?? {}
  const dpCert = String(dp.certificationStatus ?? dp.latestCertificationStatus ?? 'INSUFFICIENT_EVIDENCE')
  const exportSpec = asRecord(dp.datasetExportSpec)
  const stores = asList(dp.storesSearched)

  return (
    <div>
      <PageHeader
        title="P2 Validation"
        description="Shadow-only certification of durable policy routing against real/stored LOS applications. Technical readiness tool."
      />
      <AdministrationWorkspaceNav />
      <CiFixtureBanner />
      <div className="mb-4 rounded-lg border border-amber-300 bg-amber-50 px-4 py-2 text-sm text-amber-950">
        allowCanonicalAuthority=false. Production underwriting remains legacy. Certification never claims PRODUCTION_READY.
      </div>

      <CiExecutiveSummary
        title="Certification"
        nextAction={
          <button type="button" className="bt-btn bt-btn-primary bt-btn-sm" disabled={busy} onClick={() => void run()}>
            {busy ? 'Running…' : 'Run P2 validation'}
          </button>
        }
      >
        <p>
          Status: <strong>{cert}</strong>. Minimum {String(d.minRealStoredRequired ?? 20)} real/stored applications
          required for SHADOW_VALIDATED. Fixtures do not count.
        </p>
      </CiExecutiveSummary>

      <CiSection
        title="DP-V1 — Decision Policy real corpus"
        description="KYC→Credit→Risk→Offer→Decision against real/stored historical evidence. Shadow only — never PRODUCTION_READY."
        actions={
          <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" disabled={busy} onClick={() => void runDpV1()}>
            {busy ? 'Running…' : 'Run DP-V1 validation'}
          </button>
        }
      >
        <div className="mb-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950">
          {String(dp.banner ?? 'DP-V1 SHADOW VALIDATION — DOES NOT AFFECT PRODUCTION UNDERWRITING')}
          {' · '}
          <strong>{dpCert}</strong>
          {' · '}
          allowCanonicalAuthority=false
        </div>
        <div className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <CiStatCard label="Loan apps (staging)" value={String(dp.loanApplicationsDiscovered ?? 0)} />
          <CiStatCard label="Real/stored usable" value={String(dp.realStoredCount ?? 0)} />
          <CiStatCard label="Corpus imported" value={String(dp.corpusImportedTotal ?? 0)} />
          <CiStatCard label="Min required" value={String(dp.minRequired ?? 20)} />
        </div>
        <p className="mb-3 text-sm text-slate-700">{String(dp.message ?? '')}</p>
        <details className="mb-3 text-sm">
          <summary className="cursor-pointer font-semibold text-slate-800">Stores searched</summary>
          <ul className="mt-2 list-disc space-y-1 pl-5 text-slate-600">
            {stores.map((s, i) => (
              <li key={i}>{String(s)}</li>
            ))}
          </ul>
        </details>
        <details className="text-sm">
          <summary className="cursor-pointer font-semibold text-slate-800">Exact export / import specification</summary>
          <pre className="mt-2 max-h-64 overflow-auto rounded bg-slate-50 p-2 text-[11px] text-slate-700">
            {JSON.stringify(exportSpec, null, 2)}
          </pre>
          <p className="mt-2 text-xs text-slate-500">
            Import: POST /api/v1/internal/credit-intelligence/staging-demo/decision-policy-corpus/import
          </p>
        </details>
      </CiSection>

      {loading ? <p className="text-sm text-slate-600">Loading…</p> : null}
      {error ? <p className="text-sm text-rose-700">{error}</p> : null}

      <div className="mb-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <CiStatCard label="Apps scanned" value={String(d.scannedLoanApplications ?? 0)} />
        <CiStatCard label="Real/stored" value={String(d.realStoredCount ?? 0)} />
        <CiStatCard label="Shadow routed" value={String(d.routingSuccessCount ?? 0)} />
        <CiStatCard label="Replay pass" value={String(d.replayPassCount ?? 0)} />
      </div>

      <CiSection title="Policy Routing" description="ONE POLICY SELECTED — no multi-policy compare at runtime.">
        <p className="text-sm text-slate-700">
          Hook invocations: {String(d.underwritingHookInvocations ?? 0)}. Package exact:{' '}
          {String(d.packageExactCount ?? 0)}. Unlinked ACTIVE/SCHEDULED:{' '}
          {String(d.unlinkedActiveScheduledPolicies ?? 0)}.
        </p>
        <Link to="/credit-intelligence/policy-catalogue" className="bt-btn bt-btn-secondary bt-btn-sm mt-2 inline-flex">
          Open Policy Catalogue
        </Link>
      </CiSection>

      <CiSection title="Data Coverage" description="Honest discovery of staging/dev stores.">
        <p className="text-sm text-slate-700">
          Discovery scanned: {String(discovery.scannedLoanApplications ?? d.scannedLoanApplications ?? 0)}. Stores
          searched are listed in technical detail for ops — no production connections.
        </p>
      </CiSection>

      <CiSection title="Shadow Outcomes / Legacy Differences">
        {Object.keys(comparison).length === 0 ? (
          <CiEmptyState title="No comparisons yet" detail="Load real/stored applications, then run validation." />
        ) : (
          <ul className="text-sm text-slate-700">
            {Object.entries(comparison).map(([k, v]) => (
              <li key={k}>
                {k}: {String(v)}
              </li>
            ))}
          </ul>
        )}
      </CiSection>

      <CiSection title="Replay">
        <p className="text-sm text-slate-700">Replay pass count: {String(d.replayPassCount ?? 0)}</p>
      </CiSection>

      <CiSection title="Defects">
        {defects.length === 0 ? (
          <p className="text-sm text-slate-600">No defects recorded for the latest run.</p>
        ) : (
          <ul className="space-y-2 text-sm">
            {defects.map((raw, i) => {
              const row = asRecord(raw)
              return (
                <li key={i} className="rounded border border-slate-200 px-3 py-2">
                  <div className="font-medium">
                    {String(row.severity)} · {String(row.defectType)} {row.blocking ? '(blocking)' : ''}
                  </div>
                  <div className="text-slate-600">{String(row.rootCause ?? '')}</div>
                  <div className="text-slate-500">{String(row.recommendedAction ?? '')}</div>
                </li>
              )
            })}
          </ul>
        )}
      </CiSection>

      <CiSection title="Cases">
        {cases.length === 0 ? (
          <CiEmptyState
            title="No real/stored applications"
            detail="Staging loan_applications is empty. Provide the export dataset listed below."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full text-left text-sm">
              <thead className="text-slate-500">
                <tr>
                  <th className="py-2 pr-3">App</th>
                  <th className="py-2 pr-3">Product</th>
                  <th className="py-2 pr-3">Origin</th>
                  <th className="py-2 pr-3">Routing</th>
                  <th className="py-2 pr-3">Replay</th>
                  <th className="py-2 pr-3">Compare</th>
                </tr>
              </thead>
              <tbody>
                {cases.map((raw, i) => {
                  const c = asRecord(raw)
                  return (
                    <tr key={i} className="border-t border-slate-100">
                      <td className="py-2 pr-3">{String(c.applicationToken ?? '')}</td>
                      <td className="py-2 pr-3">{String(c.product ?? '')}</td>
                      <td className="py-2 pr-3">{String(c.origin ?? '')}</td>
                      <td className="py-2 pr-3">{String(c.resolverOutcome ?? '')}</td>
                      <td className="py-2 pr-3">{String(c.replayPass ?? '')}</td>
                      <td className="py-2 pr-3">{String(c.comparisonClass ?? '')}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </CiSection>
    </div>
  )
}
