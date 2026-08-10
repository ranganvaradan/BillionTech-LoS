import { useEffect, useState } from 'react'
import { getCreditCapabilityCatalogue, type CreditCapabilityCatalogue } from '@/api/creditIntelligence'
import { ApiError } from '@/api/http'
import { CiSection } from '@/components/creditIntelligence/CiSection'

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

/**
 * POLICY-UX-2A foundation UX — browse normalized capabilities.
 * Selecting a capability shows description/params only (full editor is UX-2C).
 */
export function CiCapabilityCataloguePanel({
  open,
  onClose,
}: {
  open: boolean
  onClose: () => void
}) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [catalogue, setCatalogue] = useState<CreditCapabilityCatalogue | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(null)

  useEffect(() => {
    if (!open) return
    setLoading(true)
    setError(null)
    void getCreditCapabilityCatalogue(false)
      .then((data) => {
        setCatalogue(data)
        setSelectedId(null)
      })
      .catch((e) => setError(e instanceof ApiError ? e.message : 'Could not load capability catalogue'))
      .finally(() => setLoading(false))
  }, [open])

  if (!open) return null

  const groups = asRecord(catalogue?.groups)
  const groupNames = Object.keys(groups)
  const selected = (() => {
    if (!selectedId) return null
    for (const name of groupNames) {
      for (const raw of asList(groups[name])) {
        const c = asRecord(raw)
        if (String(c.businessCapabilityId) === selectedId) return c
      }
    }
    return null
  })()

  return (
    <div className="rounded-xl border border-indigo-200 bg-indigo-50/40 p-4 shadow-sm">
      <div className="mb-3 flex flex-wrap items-start justify-between gap-2">
        <div>
          <h3 className="text-base font-semibold text-slate-900">Browse / Add Rule</h3>
          <p className="mt-1 text-xs text-slate-600">
            Universal credit capabilities — same business vocabulary as live underwriting.
            Parameter editing comes next; this view is foundation only.
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
        <div className="grid gap-4 lg:grid-cols-[1fr_1.1fr]">
          <div className="max-h-[28rem] space-y-3 overflow-y-auto pr-1">
            {groupNames.map((group) => (
              <CiSection key={group} title={`${group} (${asList(groups[group]).length})`}>
                <ul className="space-y-1">
                  {asList(groups[group]).map((raw) => {
                    const c = asRecord(raw)
                    const id = String(c.businessCapabilityId ?? '')
                    const active = id === selectedId
                    return (
                      <li key={id}>
                        <button
                          type="button"
                          onClick={() => setSelectedId(id)}
                          className={`w-full rounded-lg px-3 py-2 text-left text-sm ${
                            active
                              ? 'bg-slate-900 text-white'
                              : 'bg-white text-slate-800 ring-1 ring-slate-200 hover:bg-slate-50'
                          }`}
                        >
                          <div className="font-medium">{String(c.businessName ?? id)}</div>
                          <div className={`mt-0.5 text-[11px] ${active ? 'text-slate-300' : 'text-slate-500'}`}>
                            {id}
                            {c.productionSupported ? ' · Live underwriting' : ''}
                            {c.studioSupported ? ' · Studio' : ''}
                          </div>
                        </button>
                      </li>
                    )
                  })}
                </ul>
              </CiSection>
            ))}
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
                  </div>
                </div>
                <p className="text-slate-700">{String(selected.description ?? '')}</p>
                <dl className="grid gap-2 sm:grid-cols-2">
                  <div>
                    <dt className="text-xs text-slate-500">Data source</dt>
                    <dd className="font-medium">{String(selected.dataSource ?? '—')}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-slate-500">Availability</dt>
                    <dd className="font-medium">{String(selected.dataAvailability ?? '—')}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-slate-500">Supported treatments</dt>
                    <dd className="font-medium">
                      {asList(selected.supportedTreatments).map(String).join(', ') || '—'}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-slate-500">Manual</dt>
                    <dd className="font-medium">
                      {selected.manualInputPossible ? 'Input allowed' : 'No input'}
                      {' · '}
                      {selected.manualReviewPossible ? 'Review allowed' : 'No review'}
                    </dd>
                  </div>
                </dl>
                <div>
                  <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                    Parameters
                  </div>
                  <ul className="mt-2 space-y-1">
                    {asList(selected.parameterDefinitions).map((raw, i) => {
                      const p = asRecord(raw)
                      return (
                        <li key={i} className="rounded bg-slate-50 px-2 py-1.5 text-xs text-slate-800">
                          <strong>{String(p.label ?? p.name)}</strong>
                          {' · '}
                          {String(p.type ?? '')}
                          {p.unit ? ` (${String(p.unit)})` : ''}
                          {p.defaultValue != null ? ` · default ${String(p.defaultValue)}` : ''}
                        </li>
                      )
                    })}
                    {asList(selected.parameterDefinitions).length === 0 ? (
                      <li className="text-xs text-slate-500">No editable parameters</li>
                    ) : null}
                  </ul>
                </div>
                <p className="text-xs text-amber-900">
                  Selecting a capability here does not yet add it to the draft. Parameter editing and
                  add-to-policy land in POLICY-UX-2C.
                </p>
              </div>
            ) : (
              <p className="text-slate-600">
                Select a capability from the list. Catalogue count:{' '}
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
