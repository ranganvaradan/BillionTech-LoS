import { useEffect, useState } from 'react'
import { getProviderMatrix, type IntegrationProviderMatrixRow } from '@/api/integrations'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { AdministrationWorkspaceNav } from '@/components/workspace/AdministrationWorkspaceNav'
import { BtCard } from '@/components/ui/BtCard'
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
    <div className="space-y-4">
      <PageHeader
        title="Integrations"
        description="Read-only view of default provider priority and fallbacks (aggregator_routing). Higher priority is attempted first."
      />
      <AdministrationWorkspaceNav />
      {loading && <LoadingState label="Loading matrix…" />}
      {error && !rows && <ErrorState message={error} />}
      {rows && rows.length === 0 ? (
        <p className="text-sm text-[var(--bt-gray-500)]">No routing rows returned.</p>
      ) : null}
      {rows && rows.length > 0 ? (
        <BtCard className="overflow-x-auto">
          <table className="bt-table">
            <thead>
              <tr>
                <th>Integration</th>
                <th>Step</th>
                <th>Provider</th>
                <th>Priority</th>
                <th>Fallback</th>
                <th>Purpose</th>
                <th>Applies to</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => {
                const meta = r.metadata
                const purpose = meta && typeof meta.purpose === 'string' ? meta.purpose : '—'
                const applies = meta && typeof meta.appliesTo === 'string' ? meta.appliesTo : '—'
                const stepLabel =
                  r.kycStepType ?? r.esignStepType ?? (r.integrationType === 'ESIGN' ? '(all eSign)' : '(global KYC)')
                return (
                  <tr key={r.id}>
                    <td className="font-mono text-xs">{r.integrationType}</td>
                    <td className="font-mono text-xs">{stepLabel}</td>
                    <td className="font-mono text-xs">{r.providerName}</td>
                    <td>{r.priority}</td>
                    <td>{r.allowFallback ? 'yes' : 'no'}</td>
                    <td className="max-w-xs">{purpose}</td>
                    <td className="max-w-xs">{applies}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </BtCard>
      ) : null}
    </div>
  )
}
