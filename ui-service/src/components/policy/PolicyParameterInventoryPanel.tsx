import { useEffect, useState } from 'react'
import { fetchPolicyParameterInventory, materializePolicyGraph } from '@/api/dp3PolicyGraph'

type InvRow = {
  canonicalParameterId?: string | null
  originalToken?: string
  resolutionStatus?: string
  usageType?: string
  overallReadiness?: string
  productionReady?: boolean
  policyTestReady?: boolean
  runtimeReady?: boolean
  sourceFamily?: string
  businessName?: string
}

/**
 * DP-3 — Policy parameter inventory derived from persisted Policy rule graph.
 */
export function PolicyParameterInventoryPanel({ documentId }: { documentId: string | null }) {
  const [rows, setRows] = useState<InvRow[]>([])
  const [meta, setMeta] = useState<Record<string, unknown> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!documentId) return
    let cancelled = false
    setBusy(true)
    setError(null)
    void (async () => {
      try {
        await materializePolicyGraph(documentId)
        const data = await fetchPolicyParameterInventory(documentId)
        if (cancelled) return
        setMeta(data)
        const params = Array.isArray(data.parameters) ? (data.parameters as InvRow[]) : []
        setRows(params)
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Failed to load inventory')
      } finally {
        if (!cancelled) setBusy(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [documentId])

  if (!documentId) return null

  return (
    <section
      className="mt-4 rounded-lg border border-slate-200 bg-white p-3"
      data-testid="dp3-policy-parameter-inventory"
    >
      <div className="mb-2 flex items-baseline justify-between gap-2">
        <h2 className="text-sm font-semibold text-slate-900">Policy parameter inventory (GACAT)</h2>
        <span className="text-xs text-slate-500">
          unresolved: {String(meta?.unresolvedOperandCount ?? '—')}
        </span>
      </div>
      <p className="mb-2 text-xs text-slate-600">
        Derived from persisted Policy rule graph. Unresolved tokens stay unresolved — no fuzzy mapping.
      </p>
      {busy ? <p className="text-xs text-slate-500">Loading…</p> : null}
      {error ? <p className="text-xs text-rose-700">{error}</p> : null}
      {!busy && !error && rows.length === 0 ? (
        <p className="text-xs text-slate-500">No graph operands yet.</p>
      ) : null}
      {rows.length > 0 ? (
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-xs">
            <thead className="text-slate-500">
              <tr>
                <th className="py-1 pr-3">Parameter</th>
                <th className="py-1 pr-3">Usage</th>
                <th className="py-1 pr-3">Readiness</th>
                <th className="py-1 pr-3">Status</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r, i) => (
                <tr key={`${r.canonicalParameterId ?? r.originalToken}-${i}`} className="border-t border-slate-100">
                  <td className="py-1 pr-3 font-mono text-[11px] text-slate-800">
                    {r.canonicalParameterId ?? r.originalToken}
                    {r.businessName ? (
                      <div className="font-sans text-[11px] text-slate-500">{r.businessName}</div>
                    ) : null}
                  </td>
                  <td className="py-1 pr-3">{r.usageType ?? '—'}</td>
                  <td className="py-1 pr-3">
                    {String(r.overallReadiness ?? '—')}
                    <div className="text-[10px] text-slate-500">
                      PT:{String(!!r.policyTestReady)} RT:{String(!!r.runtimeReady)} PR:
                      {String(!!r.productionReady)}
                    </div>
                  </td>
                  <td className="py-1 pr-3">{r.resolutionStatus ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </section>
  )
}
