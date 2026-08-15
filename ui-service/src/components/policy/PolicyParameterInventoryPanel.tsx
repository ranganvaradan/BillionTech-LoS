import { useEffect, useState } from 'react'
import { ApiError } from '@/api/http'
import { fetchPolicyParameterInventory } from '@/api/dp3PolicyGraph'
import {
  classifyInventoryLoad,
  type InvRow,
  unresolvedTokens,
} from '@/components/policy/policyParameterInventoryState'

/**
 * DP-3 — Policy parameter inventory derived from persisted Policy rule graph.
 * Identifier contract: policy document id (same as Policy Studio session header.documentId).
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
    setMeta(null)
    setRows([])
    void (async () => {
      try {
        // GET ensures materialization when graph missing; do not rematerialize on every open.
        const data = await fetchPolicyParameterInventory(documentId)
        if (cancelled) return
        setMeta(data)
        const params = Array.isArray(data.parameters) ? (data.parameters as InvRow[]) : []
        setRows(params)
      } catch (e) {
        if (cancelled) return
        if (e instanceof ApiError) {
          setError(e.serverMessage || e.message || 'Failed to load inventory')
        } else {
          setError(e instanceof Error ? e.message : 'Failed to load inventory')
        }
      } finally {
        if (!cancelled) setBusy(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [documentId])

  if (!documentId) return null

  const graphPresent =
    meta == null ? null : meta.graphPresent === true ? true : meta.graphPresent === false ? false : null
  const unresolvedCount = Number(meta?.unresolvedOperandCount ?? 0)
  const state = classifyInventoryLoad({
    busy,
    error,
    graphPresent,
    rows,
    unresolvedCount,
  })
  const unresolved = unresolvedTokens(rows)
  const resolvedCount = rows.length - unresolved.length

  return (
    <section
      className="mt-4 rounded-lg border border-slate-200 bg-white p-3"
      data-testid="dp3-policy-parameter-inventory"
      data-inventory-state={state}
    >
      <div className="mb-2 flex items-baseline justify-between gap-2">
        <h2 className="text-sm font-semibold text-slate-900">Policy parameter inventory (GACAT)</h2>
        <span className="text-xs text-slate-500">
          unresolved:{' '}
          {state === 'TECHNICAL_ERROR' || state === 'LOADING' || meta == null
            ? '—'
            : String(unresolvedCount)}
        </span>
      </div>
      <p className="mb-2 text-xs text-slate-600">
        Derived from persisted Policy rule graph. Unresolved tokens stay unresolved — no fuzzy mapping.
      </p>
      {state === 'LOADING' ? <p className="text-xs text-slate-500">Loading…</p> : null}
      {state === 'TECHNICAL_ERROR' ? (
        <p className="text-xs text-rose-700" data-testid="dp3-inventory-technical-error">
          {error}
        </p>
      ) : null}
      {state === 'NO_POLICY_GRAPH' ? (
        <p className="text-xs text-amber-800" data-testid="dp3-inventory-no-graph">
          No materialized Policy rule graph (NO_MATERIALIZED_POLICY_GRAPH). Inventory is not available
          until the graph is persisted.
        </p>
      ) : null}
      {state === 'NO_PARAMETERS' ? (
        <p className="text-xs text-slate-500" data-testid="dp3-inventory-empty">
          Policy graph is present but has no parameter operands.
        </p>
      ) : null}
      {state === 'LOADED_WITH_PARAMETERS' || state === 'LOADED_WITH_UNRESOLVED' ? (
        <div className="mb-2 text-xs text-slate-600">
          Resolved parameters: {resolvedCount}. Unresolved references: {unresolved.length}.
          {unresolved.length > 0 ? (
            <div className="mt-1 font-mono text-[11px] text-amber-900" data-testid="dp3-inventory-unresolved-tokens">
              {unresolved.join(', ')}
            </div>
          ) : null}
        </div>
      ) : null}
      {rows.length > 0 ? (
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-xs">
            <thead className="text-slate-500">
              <tr>
                <th className="py-1 pr-3">Parameter</th>
                <th className="py-1 pr-3">Usage</th>
                <th className="py-1 pr-3">Source</th>
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
                  <td className="py-1 pr-3">{r.sourceFamily ?? '—'}</td>
                  <td className="py-1 pr-3">
                    <div className="flex flex-wrap gap-1">
                      <span
                        className={`rounded px-1.5 py-0.5 text-[10px] font-medium ${
                          r.policyTestReady
                            ? 'bg-emerald-50 text-emerald-900'
                            : 'bg-slate-100 text-slate-500'
                        }`}
                        title="Can this parameter be used in Policy Test simulations?"
                      >
                        Policy Test Ready
                      </span>
                      <span
                        className={`rounded px-1.5 py-0.5 text-[10px] font-medium ${
                          r.runtimeReady
                            ? 'bg-sky-50 text-sky-900'
                            : 'bg-slate-100 text-slate-500'
                        }`}
                        title="Available for runtime evaluation when fulfilment path succeeds"
                      >
                        Runtime Ready
                      </span>
                      <span
                        className={`rounded px-1.5 py-0.5 text-[10px] font-medium ${
                          r.productionReady
                            ? 'bg-indigo-50 text-indigo-900'
                            : 'bg-slate-100 text-slate-500'
                        }`}
                        title="Certified for production lending use"
                      >
                        Production Ready
                      </span>
                    </div>
                    <div className="mt-0.5 text-[10px] text-slate-500">
                      Overall: {String(r.overallReadiness ?? '—')}
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
