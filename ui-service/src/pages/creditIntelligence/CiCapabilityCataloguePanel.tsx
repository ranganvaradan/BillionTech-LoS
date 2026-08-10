import { useEffect, useMemo, useState } from 'react'
import {
  addCatalogueCapabilityRule,
  getCreditCapabilityCatalogue,
  type CatalogueCapabilityAddBody,
  type CreditCapabilityCatalogue,
} from '@/api/creditIntelligence'
import { ApiError } from '@/api/http'
import { CiSection } from '@/components/creditIntelligence/CiSection'

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

const COMMON_IDS = [
  'BUREAU.MIN_SCORE',
  'FIN.FOIR_MAX',
  'ELIG.BUSINESS_VINTAGE_MIN',
  'BANK.CHEQUE_BOUNCE_MAX',
  'BANK.TURNOVER_PCT_GST_MIN',
  'FIN.DSCR_MIN',
  'LIMIT.ABS_CAP',
]

const TREATMENT_LABELS: Record<string, string> = {
  REJECT: 'Reject',
  MANUAL_REVIEW: 'Manual Review',
  REFER: 'Refer',
  WARNING: 'Warning',
  SCORE_IMPACT: 'Score Impact',
  LIMIT_ADJUSTMENT: 'Limit adjustment',
  PRICE_ADJUSTMENT: 'Pricing',
}

function formatInr(value: unknown): string {
  const n = Number(String(value ?? '').replace(/[₹,\s]/g, ''))
  if (!Number.isFinite(n)) return String(value ?? '')
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 0,
  }).format(n)
}

function parseMoneyInput(raw: string): number | null {
  const n = Number(raw.replace(/[₹,\s]/g, ''))
  return Number.isFinite(n) ? n : null
}

function availabilityLabel(cap: Record<string, unknown>): string {
  const a = String(cap.dataAvailability ?? 'AUTOMATIC').toUpperCase()
  if (a.includes('UNAVAILABLE')) return 'Unavailable'
  if (a.includes('MANUAL_ONLY')) return 'Manual only'
  if (a.includes('MANUAL') || cap.manualInputPossible) return 'Manual input available'
  return 'Automatic'
}

function defaultParams(cap: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const raw of asList(cap.parameterDefinitions)) {
    const p = asRecord(raw)
    const name = String(p.name ?? '')
    if (!name) continue
    out[name] = p.defaultValue ?? ''
  }
  return out
}

function validateClient(cap: Record<string, unknown>, params: Record<string, unknown>): string | null {
  for (const raw of asList(cap.parameterDefinitions)) {
    const p = asRecord(raw)
    const name = String(p.name ?? '')
    const label = String(p.label ?? name)
    const type = String(p.type ?? '').toUpperCase()
    const v = params[name]
    if (v === '' || v == null) {
      if (p.defaultValue == null && cap.parameterisable !== false) {
        return `${label} is required`
      }
      continue
    }
    if (type === 'PERCENT') {
      const n = Number(v)
      if (!Number.isFinite(n) || n < 0 || n > 100) return `${label} must be between 0 and 100`
    }
    if (type === 'SCORE') {
      const n = Number(v)
      if (!Number.isFinite(n) || n < 300 || n > 900) {
        return `${label} should be a reasonable bureau score (300–900)`
      }
    }
    if (type === 'MONEY_INR') {
      const n = typeof v === 'number' ? v : parseMoneyInput(String(v))
      if (n == null || n < 0) return `${label} must be a valid amount`
    }
    if ((type === 'INTEGER' || type === 'NUMBER' || type === 'DECIMAL') && name.toLowerCase().includes('window')) {
      if (Number(v) <= 0) return `${label} must be greater than 0`
    }
  }
  return null
}

/**
 * POLICY-UX-2C — browse catalogue, edit business parameters, add to draft.
 */
export function CiCapabilityCataloguePanel({
  open,
  onClose,
  documentId,
  busy = false,
  onAdded,
  editCapability,
}: {
  open: boolean
  onClose: () => void
  documentId?: string | null
  busy?: boolean
  onAdded?: (session: unknown) => void
  /** When set, opens editor for an existing catalogue-backed rule */
  editCapability?: {
    ruleId: string
    businessCapabilityId: string
    parameters?: Record<string, unknown>
    failureTreatment?: string
  } | null
}) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [catalogue, setCatalogue] = useState<CreditCapabilityCatalogue | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [showAdvanced, setShowAdvanced] = useState(false)
  const [params, setParams] = useState<Record<string, unknown>>({})
  const [treatment, setTreatment] = useState('REJECT')
  const [useManualInput, setUseManualInput] = useState(false)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [editingRuleId, setEditingRuleId] = useState<string | null>(null)

  useEffect(() => {
    if (!open) return
    setLoading(true)
    setError(null)
    void getCreditCapabilityCatalogue(showAdvanced)
      .then((data) => {
        setCatalogue(data)
        if (!editCapability) {
          setSelectedId(null)
          setEditingRuleId(null)
        }
      })
      .catch((e) => setError(e instanceof ApiError ? e.message : 'Could not load capability catalogue'))
      .finally(() => setLoading(false))
  }, [open, showAdvanced, editCapability])

  useEffect(() => {
    if (!open || !editCapability || !catalogue) return
    setSelectedId(editCapability.businessCapabilityId)
    setEditingRuleId(editCapability.ruleId)
    setParams(editCapability.parameters ?? {})
    setTreatment(editCapability.failureTreatment ?? 'REJECT')
  }, [open, editCapability, catalogue])

  const allCaps = useMemo(() => {
    const groups = asRecord(catalogue?.groups)
    const list: Record<string, unknown>[] = []
    for (const name of Object.keys(groups)) {
      for (const raw of asList(groups[name])) {
        list.push(asRecord(raw))
      }
    }
    return list
  }, [catalogue])

  const filteredGroups = useMemo(() => {
    const q = search.trim().toLowerCase()
    const groups = asRecord(catalogue?.groups)
    const out: Record<string, Record<string, unknown>[]> = {}
    const aliasMap = asRecord(catalogue && 'searchAliases' in catalogue ? (catalogue as Record<string, unknown>).searchAliases : {})
    const aliasIds = new Set<string>()
    if (q) {
      for (const [alias, ids] of Object.entries(aliasMap)) {
        if (alias.includes(q) || q.includes(alias)) {
          for (const id of asList(ids)) aliasIds.add(String(id))
        }
      }
    }
    for (const group of Object.keys(groups)) {
      const items = asList(groups[group])
        .map(asRecord)
        .filter((c) => {
          if (!q) return true
          const id = String(c.businessCapabilityId ?? '')
          const hay = `${id} ${c.businessName ?? ''} ${c.description ?? ''}`.toLowerCase()
          return hay.includes(q) || aliasIds.has(id)
        })
      if (items.length) out[group] = items
    }
    return out
  }, [catalogue, search])

  const selected = allCaps.find((c) => String(c.businessCapabilityId) === selectedId) ?? null

  useEffect(() => {
    if (!selected || editingRuleId) return
    setParams(defaultParams(selected))
    const treatments = asList(selected.supportedTreatments).map(String)
    setTreatment(treatments.includes('REJECT') ? 'REJECT' : treatments[0] ?? 'REJECT')
    setUseManualInput(false)
    setFormError(null)
  }, [selectedId]) // eslint-disable-line react-hooks/exhaustive-deps

  if (!open) return null

  const common = asList((catalogue as Record<string, unknown> | null)?.commonCapabilities).map(asRecord)
  const commonList = common.length
    ? common
    : allCaps.filter((c) => COMMON_IDS.includes(String(c.businessCapabilityId)))

  const onSelect = (id: string) => {
    setSelectedId(id)
    setEditingRuleId(null)
    setFormError(null)
  }

  const onAdd = async () => {
    if (!selected || !documentId) {
      setFormError(documentId ? 'Select a capability' : 'Open a policy draft first')
      return
    }
    const err = validateClient(selected, params)
    if (err) {
      setFormError(err)
      return
    }
    const cleaned: Record<string, unknown> = {}
    for (const [k, v] of Object.entries(params)) {
      const def = asList(selected.parameterDefinitions)
        .map(asRecord)
        .find((p) => p.name === k)
      const type = String(def?.type ?? '').toUpperCase()
      if (type === 'MONEY_INR') {
        const n = typeof v === 'number' ? v : parseMoneyInput(String(v ?? ''))
        cleaned[k] = n ?? v
      } else if (type === 'INTEGER' || type === 'NUMBER' || type === 'SCORE' || type === 'PERCENT' || type === 'DECIMAL') {
        cleaned[k] = v === '' || v == null ? v : Number(v)
      } else {
        cleaned[k] = v
      }
    }
    const body: CatalogueCapabilityAddBody = {
      businessCapabilityId: String(selected.businessCapabilityId),
      parameters: cleaned,
      failureTreatment: treatment,
      useManualInput,
      dataRequirement: useManualInput ? 'MANUAL_INPUT' : String(selected.dataAvailability ?? 'AUTOMATIC'),
    }
    if (editingRuleId) body.ruleId = editingRuleId
    if (useManualInput) {
      body.manualInputLabel = String(selected.businessName ?? 'Manual input')
      body.manualInputType = 'NUMBER'
      body.requiredActor = 'Credit Officer'
    }
    setSaving(true)
    setFormError(null)
    try {
      const session = await addCatalogueCapabilityRule(documentId, body)
      onAdded?.(session)
      onClose()
    } catch (e) {
      setFormError(e instanceof ApiError ? e.message : 'Could not add capability to draft')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="rounded-xl border border-indigo-200 bg-indigo-50/40 p-4 shadow-sm">
      <div className="mb-3 flex flex-wrap items-start justify-between gap-2">
        <div>
          <h3 className="text-base font-semibold text-slate-900">Browse / Add Rule</h3>
          <p className="mt-1 text-xs text-slate-600">
            Select a capability, set business parameters and treatment, then add it to this draft.
          </p>
        </div>
        <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" onClick={onClose}>
          Close
        </button>
      </div>

      {loading ? <p className="text-sm text-slate-600">Loading catalogue…</p> : null}
      {error ? (
        <p className="rounded border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800" role="alert">
          {error}
        </p>
      ) : null}

      {!loading && !error && catalogue ? (
        <div className="grid gap-4 lg:grid-cols-[1fr_1.15fr]">
          <div className="space-y-3">
            <label className="block text-sm">
              <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">Search</span>
              <input
                className="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2 text-sm"
                placeholder="bureau, cibil, bounce, FOIR, DSCR, GST, vintage, ticket…"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </label>
            <label className="flex items-center gap-2 text-xs text-slate-600">
              <input
                type="checkbox"
                checked={showAdvanced}
                onChange={(e) => setShowAdvanced(e.target.checked)}
              />
              Show advanced / alias capabilities
            </label>

            {!search.trim() && commonList.length ? (
              <CiSection title="Common rules">
                <ul className="flex flex-wrap gap-2">
                  {commonList.map((c) => {
                    const id = String(c.businessCapabilityId ?? '')
                    return (
                      <li key={id}>
                        <button
                          type="button"
                          className={`rounded-full px-3 py-1 text-xs font-medium ring-1 ${
                            id === selectedId
                              ? 'bg-slate-900 text-white ring-slate-900'
                              : 'bg-white text-slate-700 ring-slate-200 hover:bg-slate-50'
                          }`}
                          onClick={() => onSelect(id)}
                        >
                          {String(c.businessName ?? id)}
                        </button>
                      </li>
                    )
                  })}
                </ul>
              </CiSection>
            ) : null}

            <div className="max-h-[26rem] space-y-3 overflow-y-auto pr-1">
              {Object.keys(filteredGroups).map((group) => (
                <CiSection key={group} title={`${group} (${filteredGroups[group].length})`}>
                  <ul className="space-y-1">
                    {filteredGroups[group].map((c) => {
                      const id = String(c.businessCapabilityId ?? '')
                      const active = id === selectedId
                      return (
                        <li key={id}>
                          <button
                            type="button"
                            onClick={() => onSelect(id)}
                            className={`w-full rounded-lg px-3 py-2 text-left text-sm ${
                              active
                                ? 'bg-slate-900 text-white'
                                : 'bg-white text-slate-800 ring-1 ring-slate-200 hover:bg-slate-50'
                            }`}
                          >
                            <div className="font-medium">{String(c.businessName ?? id)}</div>
                            <div className={`mt-0.5 text-[11px] ${active ? 'text-slate-300' : 'text-slate-500'}`}>
                              {id}
                              {c.aliasOf ? ` · alias of ${String(c.aliasOf)}` : ''}
                            </div>
                          </button>
                        </li>
                      )
                    })}
                  </ul>
                </CiSection>
              ))}
              {Object.keys(filteredGroups).length === 0 ? (
                <p className="text-sm text-slate-600">No capabilities match this search.</p>
              ) : null}
            </div>
          </div>

          <div className="rounded-lg border border-slate-200 bg-white p-4 text-sm">
            {selected ? (
              <div className="space-y-3">
                <div>
                  <div className="text-lg font-semibold text-slate-900">
                    {String(selected.businessName ?? '')}
                  </div>
                  <div className="mt-1 text-xs font-medium text-slate-500">
                    {String(selected.businessCapabilityId ?? '')}
                    {editingRuleId ? ' · editing draft rule' : ''}
                  </div>
                </div>
                <p className="text-slate-700">{String(selected.description ?? '')}</p>

                {String(selected.businessCapabilityId) === 'BUREAU.MIN_SCORE' ? (
                  <p className="rounded bg-sky-50 px-2 py-1.5 text-xs text-sky-950">
                    Preferred bureau score capability. The product eligibility gate is under Advanced only.
                  </p>
                ) : null}

                <dl className="grid gap-2 sm:grid-cols-2">
                  <div>
                    <dt className="text-xs text-slate-500">Data</dt>
                    <dd className="font-medium">{String(selected.dataSource ?? '—')}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-slate-500">Availability</dt>
                    <dd className="font-medium">{availabilityLabel(selected)}</dd>
                  </div>
                </dl>

                <div className="space-y-2">
                  {asList(selected.parameterDefinitions).map((raw) => {
                    const p = asRecord(raw)
                    const name = String(p.name ?? '')
                    const type = String(p.type ?? '').toUpperCase()
                    const value = params[name] ?? ''
                    return (
                      <label key={name} className="block">
                        <span className="text-xs font-semibold text-slate-600">
                          {String(p.label ?? name)}
                          {type === 'PERCENT' ? ' (%)' : ''}
                          {type === 'MONEY_INR' ? ' (₹)' : ''}
                        </span>
                        {type === 'ENUM' || name === 'unit' ? (
                          <select
                            className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                            value={String(value)}
                            onChange={(e) => setParams((prev) => ({ ...prev, [name]: e.target.value }))}
                            disabled={busy || saving}
                          >
                            <option value="YEARS">Years</option>
                            <option value="MONTHS">Months</option>
                            <option value="true">Yes / true</option>
                            <option value="false">No / false</option>
                          </select>
                        ) : (
                          <input
                            className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                            type={type === 'MONEY_INR' ? 'text' : 'number'}
                            step={type === 'DECIMAL' || type === 'PERCENT' ? '0.01' : '1'}
                            value={
                              type === 'MONEY_INR' && value !== '' && value != null
                                ? formatInr(value).replace(/^₹\s?/, '')
                                : String(value)
                            }
                            onChange={(e) => {
                              const rawVal = e.target.value
                              if (type === 'MONEY_INR') {
                                const n = parseMoneyInput(rawVal)
                                setParams((prev) => ({ ...prev, [name]: n ?? rawVal }))
                              } else {
                                setParams((prev) => ({ ...prev, [name]: rawVal }))
                              }
                            }}
                            disabled={busy || saving}
                          />
                        )}
                        {type === 'MONEY_INR' && value !== '' && value != null ? (
                          <span className="mt-0.5 block text-[11px] text-slate-500">{formatInr(value)}</span>
                        ) : null}
                      </label>
                    )
                  })}
                  {asList(selected.parameterDefinitions).length === 0 ? (
                    <p className="text-xs text-slate-500">No editable parameters for this capability.</p>
                  ) : null}
                </div>

                <label className="block">
                  <span className="text-xs font-semibold text-slate-600">If rule fails</span>
                  <select
                    className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                    value={treatment}
                    onChange={(e) => setTreatment(e.target.value)}
                    disabled={busy || saving}
                  >
                    {asList(selected.supportedTreatments).map((t) => {
                      const key = String(t)
                      return (
                        <option key={key} value={key}>
                          {TREATMENT_LABELS[key] ?? key}
                        </option>
                      )
                    })}
                  </select>
                </label>

                {selected.manualInputPossible ? (
                  <label className="flex items-start gap-2 text-xs text-slate-700">
                    <input
                      type="checkbox"
                      className="mt-0.5"
                      checked={useManualInput}
                      onChange={(e) => setUseManualInput(e.target.checked)}
                      disabled={busy || saving}
                    />
                    <span>
                      Use manual input instead
                      <span className="mt-0.5 block text-slate-500">
                        Optional when automatic data exists. Distinct from Manual Review treatment.
                      </span>
                    </span>
                  </label>
                ) : null}

                {formError ? (
                  <p className="rounded border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-800" role="alert">
                    {formError}
                  </p>
                ) : null}

                <div className="flex flex-wrap gap-2 pt-1">
                  <button
                    type="button"
                    className="bt-btn bt-btn-secondary bt-btn-sm"
                    onClick={onClose}
                    disabled={saving}
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    className="bt-btn bt-btn-primary bt-btn-sm"
                    disabled={busy || saving || !documentId}
                    onClick={() => void onAdd()}
                  >
                    {saving ? 'Saving…' : editingRuleId ? 'Update in Policy' : 'Add to Policy'}
                  </button>
                </div>
                {!documentId ? (
                  <p className="text-xs text-amber-900">Open or upload a policy draft to add capabilities.</p>
                ) : null}
              </div>
            ) : (
              <p className="text-slate-600">
                Select a capability to edit parameters. Catalogue count:{' '}
                <strong>{String(catalogue.capabilityCount ?? 0)}</strong>
                {catalogue.allowCanonicalAuthority === false ? (
                  <span className="mt-2 block text-xs text-slate-500">
                    Production authority remains disabled.
                  </span>
                ) : null}
              </p>
            )}
          </div>
        </div>
      ) : null}
    </div>
  )
}
