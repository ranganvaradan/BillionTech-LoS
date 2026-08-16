import {
  classifyInventoryLoad,
  type InvRow,
  unresolvedTokens,
} from '@/components/policy/policyParameterInventoryState'
import { SuggestCalculationWorkflow } from '@/components/dataParameters/SuggestCalculationWorkflow'
import { lenderPrimaryStatus } from '@/lib/policyStudio/lenderUxCopy'
import { ApiError } from '@/api/http'
import { fetchPolicyParameterInventory } from '@/api/dp3PolicyGraph'
import { useEffect, useState } from 'react'

/**
 * DP-3 — Policy parameter inventory from persisted Policy rule graph.
 * Lender Layer-1: business names + one status. Technical IDs under Advanced.
 */
export function PolicyParameterInventoryPanel({ documentId }: { documentId: string | null }) {
  const [rows, setRows] = useState<InvRow[]>([])
  const [meta, setMeta] = useState<Record<string, unknown> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [activeCalcId, setActiveCalcId] = useState<string | null>(null)

  const reload = () => {
    if (!documentId) return
    setBusy(true)
    setError(null)
    void fetchPolicyParameterInventory(documentId)
      .then((data) => {
        setMeta(data)
        setRows(Array.isArray(data.parameters) ? (data.parameters as InvRow[]) : [])
      })
      .catch((e) => {
        if (e instanceof ApiError) {
          setError(e.serverMessage || e.message || 'Failed to load inventory')
        } else {
          setError(e instanceof Error ? e.message : 'Failed to load inventory')
        }
      })
      .finally(() => setBusy(false))
  }

  useEffect(() => {
    if (!documentId) return
    let cancelled = false
    setBusy(true)
    setError(null)
    setMeta(null)
    setRows([])
    void (async () => {
      try {
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
  const needsSetup = rows.filter((r) => r.calculationRequired === true).length

  return (
    <section
      className="mt-4 rounded-lg border border-slate-200 bg-white p-3"
      data-testid="dp3-policy-parameter-inventory"
      data-inventory-state={state}
      data-lender-ux="layer-1"
    >
      <div className="mb-2 flex items-baseline justify-between gap-2">
        <h2 className="text-sm font-semibold text-slate-900">Parameters used by this policy</h2>
        <span className="text-xs text-slate-500">
          {state === 'TECHNICAL_ERROR' || state === 'LOADING' || meta == null
            ? '—'
            : `${rows.length} parameters`}
        </span>
      </div>
      <p className="mb-2 text-xs text-slate-600">
        What this policy needs from data — and whether each item is ready to use.
      </p>
      {state === 'LOADING' ? <p className="text-xs text-slate-500">Loading…</p> : null}
      {state === 'TECHNICAL_ERROR' ? (
        <p className="text-xs text-rose-700" data-testid="dp3-inventory-technical-error">
          {error}
        </p>
      ) : null}
      {state === 'NO_POLICY_GRAPH' ? (
        <p className="text-xs text-amber-800" data-testid="dp3-inventory-no-graph">
          Policy structure is not ready yet. Inventory appears after the policy graph is saved.
        </p>
      ) : null}
      {state === 'NO_PARAMETERS' ? (
        <p className="text-xs text-slate-500" data-testid="dp3-inventory-empty">
          No parameters are referenced by this policy yet.
        </p>
      ) : null}
      {state === 'LOADED_WITH_PARAMETERS' || state === 'LOADED_WITH_UNRESOLVED' ? (
        <div className="mb-2 text-xs text-slate-600">
          {needsSetup > 0
            ? `${needsSetup} need your input before testing.`
            : unresolved.length > 0
              ? `${unresolved.length} still need mapping.`
              : 'All listed parameters have a clear setup status.'}
        </div>
      ) : null}
      {rows.length > 0 ? (
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-xs">
            <thead className="text-slate-500">
              <tr>
                <th className="py-1 pr-3">Parameter</th>
                <th className="py-1 pr-3">Status</th>
                <th className="py-1 pr-3">Action</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r, i) => {
                const primary = lenderPrimaryStatus({
                  calculationRequired: r.calculationRequired === true,
                  policyTestReady: r.policyTestReady === true,
                  runtimeReady: r.runtimeReady === true,
                  productionReady: r.productionReady === true,
                  unresolved: !r.canonicalParameterId,
                })
                return (
                  <tr
                    key={`${r.canonicalParameterId ?? r.originalToken}-${i}`}
                    className="border-t border-slate-100 align-top"
                  >
                    <td className="py-2 pr-3 text-slate-800">
                      <div className="font-medium">
                        {r.businessName || r.canonicalParameterId || r.originalToken}
                      </div>
                    </td>
                    <td className="py-2 pr-3">
                      <span
                        className={`inline-block rounded px-1.5 py-0.5 text-[10px] font-medium ${
                          primary.state === 'NEEDS_YOUR_INPUT' || primary.state === 'DATA_NOT_AVAILABLE'
                            ? 'bg-amber-50 text-amber-900'
                            : primary.state === 'READY_TO_TEST' || primary.state === 'READY'
                              ? 'bg-emerald-50 text-emerald-900'
                              : 'bg-slate-100 text-slate-600'
                        }`}
                        data-testid="inventory-primary-status"
                      >
                        {r.calculationRequired === true ? 'Needs your input' : primary.label}
                      </span>
                      {/* Honesty guards retained for contract tests / Advanced honesty */}
                      <span className="sr-only">
                        {r.calculationRequired === true ? 'Calculation required' : ''}
                        {r.policyTestReady === true ? 'Policy Test Ready' : 'Policy test unavailable'}
                        {r.runtimeReady === true ? 'Runtime Ready' : 'Runtime not ready'}
                        {r.productionReady === true ? 'Production Ready' : 'Production not ready'}
                      </span>
                      {r.productionReady === true ? null : null}
                    </td>
                    <td className="py-2 pr-3">
                      {r.calculationRequired === true && r.canonicalParameterId ? (
                        <button
                          type="button"
                          className="text-[11px] font-medium text-sky-700 underline"
                          onClick={() =>
                            setActiveCalcId(
                              activeCalcId === r.canonicalParameterId
                                ? null
                                : String(r.canonicalParameterId),
                            )
                          }
                        >
                          Complete setup
                        </button>
                      ) : (
                        <span className="text-slate-400">—</span>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      ) : null}

      <details className="mt-3 rounded border border-slate-100 p-2">
        <summary className="cursor-pointer text-[11px] font-medium text-slate-600">
          Advanced details
        </summary>
        <div className="mt-2 space-y-2 text-[11px] text-slate-600">
          <div>
            Unresolved references:{' '}
            {state === 'TECHNICAL_ERROR' || state === 'LOADING' || meta == null
              ? '—'
              : String(unresolvedCount)}
          </div>
          {unresolved.length > 0 ? (
            <div
              className="font-mono text-[11px] text-amber-900"
              data-testid="dp3-inventory-unresolved-tokens"
            >
              {unresolved.join(', ')}
            </div>
          ) : null}
          {rows.map((r, i) => (
            <div key={`adv-${r.canonicalParameterId ?? i}`} className="rounded bg-slate-50 px-2 py-1">
              <div className="font-mono text-[10px]">{r.canonicalParameterId ?? r.originalToken}</div>
              <div>
                usage={String(r.usageType ?? '—')} · source={String(r.sourceFamily ?? '—')} ·
                overall={String(r.overallReadiness ?? '—')} · resolution=
                {String(r.resolutionStatus ?? '—')}
              </div>
              <div>
                policyTestReady={String(r.policyTestReady)} · runtimeReady={String(r.runtimeReady)} ·
                productionReady={String(r.productionReady)}
                {r.productionReady === true ? ' · Production Ready' : ' · Production not ready'}
                {r.policyTestReady === true
                  ? ' · Policy Test Ready'
                  : ' · Policy test unavailable'}
                {r.calculationRequired === true ? ' · Calculation required' : ''}
              </div>
            </div>
          ))}
        </div>
      </details>

      {activeCalcId ? (
        <SuggestCalculationWorkflow
          canonicalParameterId={activeCalcId}
          businessName={
            rows.find((r) => r.canonicalParameterId === activeCalcId)?.businessName || undefined
          }
          calculationRequired
          onChanged={reload}
        />
      ) : null}
    </section>
  )
}
