import { useEffect, useState } from 'react'
import { updateCategoryPropositionConfig } from '@/api/categorySelection'
import { ApiError } from '@/api/http'
import { userFriendlyMessage } from '@/lib/userFriendlyError'

const SAFE_ROUTE_OPTS = [
  { value: 'FINANCIAL_STATEMENTS', label: 'Financial statements' },
  { value: 'BANK_AA', label: 'Bank account data' },
  { value: 'BOTH', label: 'Accepts either / both' },
] as const

/**
 * Admin UX for Category Selection — SAFE progressive questions + display order.
 * Does not expose Policy thresholds. Reuses existing proposition-config API.
 */
export function PropositionDisambiguationEditor({
  categoryId,
  status,
  governanceJson,
  onSaved,
}: {
  categoryId: string
  status: string
  governanceJson?: Record<string, unknown> | null
  onSaved?: () => void
}) {
  const prop = (governanceJson?.proposition ?? {}) as Record<string, unknown>
  const dis = (governanceJson?.disambiguation ?? {}) as Record<string, unknown>
  const attrs = (dis.attributes ?? {}) as Record<string, unknown>
  const routeRaw = attrs.financial_data_route
  const initialRoutes = Array.isArray(routeRaw)
    ? routeRaw.map(String)
    : routeRaw
      ? [String(routeRaw)]
      : []

  const [customerFacingName, setCustomerFacingName] = useState(String(prop.customerFacingName ?? ''))
  const [shortDescription, setShortDescription] = useState(String(prop.shortDescription ?? ''))
  const [requirementsSummary, setRequirementsSummary] = useState(String(prop.requirementsSummary ?? ''))
  const [displayOrder, setDisplayOrder] = useState(String(prop.displayOrder ?? '100'))
  const [allowAuto, setAllowAuto] = useState(prop.allowAutoSingleMatch !== false)
  const [routes, setRoutes] = useState<string[]>(initialRoutes)
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState<string | null>(null)
  const [err, setErr] = useState<string | null>(null)

  useEffect(() => {
    const p = (governanceJson?.proposition ?? {}) as Record<string, unknown>
    const d = (governanceJson?.disambiguation ?? {}) as Record<string, unknown>
    const a = (d.attributes ?? {}) as Record<string, unknown>
    const r = a.financial_data_route
    setCustomerFacingName(String(p.customerFacingName ?? ''))
    setShortDescription(String(p.shortDescription ?? ''))
    setRequirementsSummary(String(p.requirementsSummary ?? ''))
    setDisplayOrder(String(p.displayOrder ?? '100'))
    setAllowAuto(p.allowAutoSingleMatch !== false)
    setRoutes(Array.isArray(r) ? r.map(String) : r ? [String(r)] : [])
  }, [governanceJson, categoryId])

  const editable = ['DRAFT', 'APPROVED', 'ACTIVE'].includes(String(status).toUpperCase())

  const toggleRoute = (v: string) => {
    setRoutes((prev) => (prev.includes(v) ? prev.filter((x) => x !== v) : [...prev, v]))
  }

  const save = async () => {
    setBusy(true)
    setErr(null)
    setMsg(null)
    try {
      await updateCategoryPropositionConfig(categoryId, {
        proposition: {
          customerFacingName: customerFacingName.trim() || undefined,
          shortDescription: shortDescription.trim() || undefined,
          requirementsSummary: requirementsSummary.trim() || undefined,
          displayOrder: Number(displayOrder) || 100,
          allowAutoSingleMatch: allowAuto,
        },
        disambiguation: {
          attributes: {
            financial_data_route: routes,
          },
        },
      })
      setMsg('Proposition & disambiguation settings saved.')
      onSaved?.()
    } catch (e) {
      setErr(e instanceof ApiError ? userFriendlyMessage(e) : 'Could not save proposition config')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-3" data-testid="proposition-disambiguation-editor">
      <p className="text-xs text-slate-600">
        When another active lending proposition matches the same initial customer information, the
        application may ask these progressive questions or allow explicit proposition selection. Safe
        questions only — Policy thresholds are never exposed here.
      </p>

      <label className="block text-xs">
        <span className="font-medium text-slate-700">Customer-facing name</span>
        <input
          className="bt-input mt-1"
          value={customerFacingName}
          disabled={!editable || busy}
          onChange={(e) => setCustomerFacingName(e.target.value)}
        />
      </label>
      <label className="block text-xs">
        <span className="font-medium text-slate-700">Short description</span>
        <input
          className="bt-input mt-1"
          value={shortDescription}
          disabled={!editable || busy}
          onChange={(e) => setShortDescription(e.target.value)}
        />
      </label>
      <label className="block text-xs">
        <span className="font-medium text-slate-700">Requirements summary (customer-facing)</span>
        <input
          className="bt-input mt-1"
          value={requirementsSummary}
          disabled={!editable || busy}
          onChange={(e) => setRequirementsSummary(e.target.value)}
        />
      </label>
      <div className="flex flex-wrap gap-4">
        <label className="block text-xs">
          <span className="font-medium text-slate-700">Display / selection order</span>
          <input
            className="bt-input mt-1 w-28"
            type="number"
            value={displayOrder}
            disabled={!editable || busy}
            onChange={(e) => setDisplayOrder(e.target.value)}
          />
        </label>
        <label className="mt-5 flex items-center gap-2 text-xs text-slate-700">
          <input
            type="checkbox"
            checked={allowAuto}
            disabled={!editable || busy}
            onChange={(e) => setAllowAuto(e.target.checked)}
          />
          Allow auto-select when this is the only match
        </label>
      </div>

      <fieldset className="rounded border border-slate-200 bg-slate-50 p-3">
        <legend className="px-1 text-xs font-semibold text-slate-800">
          Progressive question: How would you like to provide financial information?
        </legend>
        <p className="mb-2 text-[11px] text-slate-500">
          Mark which answers keep this proposition eligible. Leave empty to skip progressive
          disambiguation for this Category (explicit selection may still apply).
        </p>
        <div className="flex flex-wrap gap-3">
          {SAFE_ROUTE_OPTS.map((o) => (
            <label key={o.value} className="flex items-center gap-2 text-xs text-slate-700">
              <input
                type="checkbox"
                checked={routes.includes(o.value)}
                disabled={!editable || busy}
                onChange={() => toggleRoute(o.value)}
              />
              {o.label}
            </label>
          ))}
        </div>
      </fieldset>

      {err ? <p className="text-xs text-rose-700">{err}</p> : null}
      {msg ? <p className="text-xs text-emerald-800">{msg}</p> : null}
      {!editable ? (
        <p className="text-xs text-slate-500">Open a DRAFT, APPROVED, or ACTIVE version to edit these settings.</p>
      ) : (
        <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" disabled={busy} onClick={() => void save()}>
          Save proposition settings
        </button>
      )}
    </div>
  )
}
