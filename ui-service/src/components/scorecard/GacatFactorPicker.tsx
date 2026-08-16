import { useEffect, useState } from 'react'
import { listGacatScorecardFactors } from '@/api/scorecards'
import { SuggestCalculationWorkflow } from '@/components/dataParameters/SuggestCalculationWorkflow'

export type GacatFactorPick = {
  canonicalParameterId: string
  canonicalDefinitionVersion: number
  businessName: string
  sourceFamily: string
  suggestedScorecardSource: string
  legacyScorecardKey: string | null
  unit: string | null
  availability: string | null
  howObtained: string | null
  productionReady: boolean
  authoringValueType: string | null
  type: string | null
}

type Props = {
  onPick: (factor: GacatFactorPick) => void
  disabled?: boolean
}

export function GacatFactorPicker({ onPick, disabled }: Props) {
  const [q, setQ] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [items, setItems] = useState<Array<Record<string, unknown>>>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    const t = window.setTimeout(() => {
      setBusy(true)
      setError(null)
      void listGacatScorecardFactors(q.trim() || undefined)
        .then((res) => {
          if (!cancelled) setItems(res.parameters ?? [])
        })
        .catch((e: unknown) => {
          if (!cancelled) setError(e instanceof Error ? e.message : 'Failed to load GACAT factors')
        })
        .finally(() => {
          if (!cancelled) setBusy(false)
        })
    }, 200)
    return () => {
      cancelled = true
      window.clearTimeout(t)
    }
  }, [q])

  const selected = items.find((x) => String(x.canonicalParameterId) === selectedId) ?? null

  function applyPick() {
    if (!selected) return
    onPick({
      canonicalParameterId: String(selected.canonicalParameterId),
      canonicalDefinitionVersion: Number(selected.canonicalDefinitionVersion) || 1,
      businessName: String(selected.businessName ?? selected.canonicalParameterId),
      sourceFamily: String(selected.sourceFamily ?? ''),
      suggestedScorecardSource: String(selected.suggestedScorecardSource ?? 'SCORECARD'),
      legacyScorecardKey: selected.legacyScorecardKey != null ? String(selected.legacyScorecardKey) : null,
      unit: selected.unit != null ? String(selected.unit) : null,
      availability: selected.availability != null ? String(selected.availability) : null,
      howObtained: selected.howObtained != null ? String(selected.howObtained) : null,
      productionReady: Boolean(selected.productionReady),
      authoringValueType: selected.authoringValueType != null ? String(selected.authoringValueType) : null,
      type: selected.type != null ? String(selected.type) : null,
    })
    setSelectedId(null)
    setQ('')
  }

  return (
    <div className="rounded-lg border border-sky-200 bg-sky-50/40 p-3" data-testid="gacat-factor-picker">
      <div className="mb-2 text-xs font-semibold uppercase tracking-wide text-sky-900">
        Add factor from policy catalogue
      </div>
      <p className="mb-2 text-[11px] text-slate-600">
        Choose Source → Parameter from the catalogue. Do not redefine how the value is obtained.
      </p>
      <input
        className="bt-input bt-input-sm mb-2 w-full"
        placeholder="Search: bureau, cibil, dpd, adb, foir, gst, turnover, vintage, kyc…"
        value={q}
        disabled={disabled}
        onChange={(e) => setQ(e.target.value)}
      />
      {busy ? <p className="text-[11px] text-slate-500">Searching…</p> : null}
      {error ? <p className="text-[11px] text-rose-700">{error}</p> : null}
      <select
        className="bt-input bt-input-sm mb-2 w-full"
        size={6}
        disabled={disabled || items.length === 0}
        value={selectedId ?? ''}
        onChange={(e) => setSelectedId(e.target.value || null)}
      >
        {items.map((p) => (
          <option key={String(p.canonicalParameterId)} value={String(p.canonicalParameterId)}>
            {String(p.businessName)} · {String(p.sourceFamily ?? '')}
            {p.legacyScorecardKey ? ` · ${String(p.legacyScorecardKey)}` : ''}
          </option>
        ))}
      </select>
      {selected ? (
        <div className="mb-2 rounded border border-slate-200 bg-white px-2 py-2 text-[11px] text-slate-700">
          <div>
            <strong>{String(selected.businessName)}</strong>
          </div>
          <div>Source: {String(selected.sourceFamily ?? '—')}</div>
          <div>
            Type: {String(selected.type ?? '—')}
            {selected.unit ? ` · Unit: ${String(selected.unit)}` : ''}
          </div>
          <div>Availability: {String(selected.availability ?? '—')}</div>
          <div>How obtained: {String(selected.howObtained ?? 'Automatic / existing LOS path')}</div>
          <div>
            Production-ready:{' '}
            {selected.productionReady === true ? (
              <span className="text-emerald-700">Yes</span>
            ) : (
              <span className="text-amber-800">Not production-ready</span>
            )}
          </div>
          {selected.calculationRequired === true ? (
            <SuggestCalculationWorkflow
              canonicalParameterId={String(selected.canonicalParameterId)}
              businessName={String(selected.businessName ?? selected.canonicalParameterId)}
              calculationRequired
            />
          ) : null}
          <details className="mt-1">
            <summary className="cursor-pointer text-slate-500">Advanced</summary>
            <div className="mt-1 font-mono text-[10px] text-slate-600">
              <div>canonical: {String(selected.canonicalParameterId)}</div>
              <div>definition version: {String(selected.canonicalDefinitionVersion ?? 1)}</div>
              <div>legacy key: {String(selected.legacyScorecardKey ?? '—')}</div>
            </div>
          </details>
        </div>
      ) : null}
      <button
        type="button"
        className="bt-btn bt-btn-primary bt-btn-sm"
        disabled={disabled || !selected}
        onClick={applyPick}
      >
        Add selected factor
      </button>
    </div>
  )
}
