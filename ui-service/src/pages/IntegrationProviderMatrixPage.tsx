import { useEffect, useState } from 'react'
import { getProviderMatrix, type IntegrationProviderMatrixRow } from '@/api/integrations'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { ApiError } from '@/api/http'

export function IntegrationProviderMatrixPage() {
  const [rows, setRows] = useState<IntegrationProviderMatrixRow[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    getProviderMatrix()
      .then((r) => {
        if (!cancelled) {
          setRows(r)
          setError(null)
        }
      })
      .catch((e: unknown) => {
        if (!cancelled) {
          setRows(null)
          setError(e instanceof ApiError ? e.serverMessage : String(e))
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div>
      <PageHeader
        title="Integration provider matrix"
        description="Read-only view of default provider priority and fallbacks (aggregator_routing). Higher priority is attempted first."
      />
      {loading && <LoadingState label="Loading matrix…" />}
      {error && !rows && <ErrorState message={error} />}
      {rows && rows.length === 0 ? (
        <p className="text-sm text-slate-600">No routing rows returned.</p>
      ) : null}
      {rows && rows.length > 0 ? (
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm">
          <table className="min-w-full border-collapse text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase tracking-wide text-slate-600">
              <tr>
                <th className="px-3 py-2">Integration</th>
                <th className="px-3 py-2">Step</th>
                <th className="px-3 py-2">Provider</th>
                <th className="px-3 py-2">Priority</th>
                <th className="px-3 py-2">Fallback</th>
                <th className="px-3 py-2">Purpose</th>
                <th className="px-3 py-2">Applies to</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => {
                const meta = r.metadata
                const purpose = meta && typeof meta.purpose === 'string' ? meta.purpose : '—'
                const applies =
                  meta && typeof meta.appliesTo === 'string' ? meta.appliesTo : '—'
                const stepLabel =
                  r.kycStepType ?? r.esignStepType ?? (r.integrationType === 'ESIGN' ? '(all eSign)' : '(global KYC)')
                return (
                  <tr key={r.id} className="border-b border-slate-100 last:border-0">
                    <td className="px-3 py-2 font-mono text-xs">{r.integrationType}</td>
                    <td className="px-3 py-2 font-mono text-xs">{stepLabel}</td>
                    <td className="px-3 py-2 font-mono text-xs">{r.providerName}</td>
                    <td className="px-3 py-2">{r.priority}</td>
                    <td className="px-3 py-2">{r.allowFallback ? 'yes' : 'no'}</td>
                    <td className="max-w-xs px-3 py-2 text-slate-700">{purpose}</td>
                    <td className="max-w-xs px-3 py-2 text-slate-700">{applies}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      ) : null}
    </div>
  )
}
