import { useEffect, useMemo, useState } from 'react'
import { PageHeader } from '@/components/PageHeader'
import { AdministrationWorkspaceNav } from '@/components/workspace/AdministrationWorkspaceNav'
import {
  getDataParametersBySource,
  getDataParametersOverview,
  searchDataParameters,
} from '@/api/liveReadiness'
import { ApiError } from '@/api/http'

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

type Tab = 'by-source' | 'by-parameter' | 'gaps'

export function DataParametersPage() {
  const [tab, setTab] = useState<Tab>('by-source')
  const [overview, setOverview] = useState<Record<string, unknown> | null>(null)
  const [source, setSource] = useState('')
  const [sourceView, setSourceView] = useState<Record<string, unknown> | null>(null)
  const [q, setQ] = useState('')
  const [search, setSearch] = useState<Record<string, unknown> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    getDataParametersOverview()
      .then((data) => {
        setOverview(data)
        const sources = asList(data.sources).map(String)
        if (sources[0]) setSource(sources[0])
      })
      .catch((e) => setError(e instanceof ApiError ? e.message : 'Failed to load Data & Parameters'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    if (!source) return
    getDataParametersBySource(source)
      .then(setSourceView)
      .catch((e) => setError(e instanceof ApiError ? e.message : 'Failed to load source'))
  }, [source])

  const summaries = useMemo(() => asList(overview?.bySourceSummary).map(asRecord), [overview])
  const gaps = asRecord(overview?.gapsManual)

  const runSearch = async () => {
    if (!q.trim()) return
    try {
      setSearch(await searchDataParameters(q.trim()))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Search failed')
    }
  }

  return (
    <div className="space-y-4" data-testid="data-parameters-page">
      <PageHeader
        title="Data & Parameters"
        description="What the institution can know — CanonicalParameterRegistry read model (not a new catalogue)."
      />
      <AdministrationWorkspaceNav />

      {error ? (
        <div className="rounded border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-900">{error}</div>
      ) : null}
      {loading ? <p className="text-sm text-slate-600">Loading…</p> : null}

      <div className="flex flex-wrap gap-2">
        {(
          [
            ['by-source', 'By Source'],
            ['by-parameter', 'By Parameter'],
            ['gaps', 'Gaps / Manual'],
          ] as const
        ).map(([id, label]) => (
          <button
            key={id}
            type="button"
            className={`bt-btn bt-btn-sm ${tab === id ? 'bt-btn-primary' : 'bt-btn-secondary'}`}
            onClick={() => setTab(id)}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === 'by-source' ? (
        <section className="space-y-3 rounded-xl border border-slate-200 bg-white p-4">
          <div className="flex flex-wrap gap-2">
            {summaries.map((s) => (
              <button
                key={String(s.source)}
                type="button"
                className={`rounded-full px-3 py-1 text-xs font-semibold ${
                  source === s.source ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-700'
                }`}
                onClick={() => setSource(String(s.source))}
              >
                {String(s.source)} · {String(s.count ?? 0)}
              </button>
            ))}
          </div>
          {sourceView ? (
            <div className="grid gap-4 lg:grid-cols-3">
              {(
                [
                  ['raw', 'RAW'],
                  ['derived', 'DERIVED'],
                  ['manual', 'MANUAL'],
                ] as const
              ).map(([key, label]) => (
                <div key={key}>
                  <h3 className="text-sm font-semibold text-slate-900">{label}</h3>
                  <ul className="mt-2 space-y-2 text-sm">
                    {asList(sourceView[key]).map((raw, i) => {
                      const p = asRecord(raw)
                      return (
                        <li key={i} className="rounded border border-slate-100 px-2 py-1.5">
                          <div className="font-medium text-slate-900">{String(p.businessName)}</div>
                          <div className="text-xs text-slate-500">{String(p.id)}</div>
                          {p.calculationSummary ? (
                            <div className="mt-1 text-xs text-slate-600">{String(p.calculationSummary)}</div>
                          ) : null}
                          <details className="mt-1 text-xs text-slate-500">
                            <summary>Advanced</summary>
                            <pre className="mt-1 whitespace-pre-wrap">
                              {JSON.stringify(p.existingImplementationBinding ?? p, null, 2)}
                            </pre>
                          </details>
                        </li>
                      )
                    })}
                  </ul>
                </div>
              ))}
            </div>
          ) : null}
        </section>
      ) : null}

      {tab === 'by-parameter' ? (
        <section className="space-y-3 rounded-xl border border-slate-200 bg-white p-4">
          <div className="flex flex-wrap gap-2">
            <input
              className="min-w-[16rem] flex-1 rounded border border-slate-300 px-3 py-2 text-sm"
              placeholder="Search bureau score, ADB, GST…"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') void runSearch()
              }}
            />
            <button type="button" className="bt-btn bt-btn-primary bt-btn-sm" onClick={() => void runSearch()}>
              Search
            </button>
          </div>
          <ul className="space-y-2 text-sm">
            {asList(search?.results).map((raw, i) => {
              const p = asRecord(raw)
              return (
                <li key={i} className="rounded border border-slate-100 px-3 py-2">
                  <div className="font-medium">{String(p.businessName)}</div>
                  <div className="text-xs text-slate-500">
                    {String(p.evaluatedFrom)} · {String(p.type)} · {String(p.id)}
                  </div>
                  {p.calculationSummary ? (
                    <p className="mt-1 text-xs text-slate-600">{String(p.calculationSummary)}</p>
                  ) : null}
                </li>
              )
            })}
          </ul>
        </section>
      ) : null}

      {tab === 'gaps' ? (
        <section className="space-y-3 rounded-xl border border-slate-200 bg-white p-4 text-sm">
          <h3 className="font-semibold">Manual parameters ({String(gaps.manualCount ?? 0)})</h3>
          <ul className="space-y-1">
            {asList(gaps.manualParameters).map((raw, i) => {
              const p = asRecord(raw)
              return (
                <li key={i}>
                  {String(p.businessName)} <span className="text-slate-500">({String(p.id)})</span>
                </li>
              )
            })}
          </ul>
          <h3 className="mt-4 font-semibold">
            Availability / binding gaps ({String(gaps.availabilityGapCount ?? 0)})
          </h3>
          <ul className="space-y-1">
            {asList(gaps.availabilityGaps).map((raw, i) => {
              const p = asRecord(raw)
              return (
                <li key={i}>
                  {String(p.businessName)} — {String(p.availability ?? '—')}
                </li>
              )
            })}
          </ul>
        </section>
      ) : null}

      <p className="text-xs text-slate-500">
        allowCanonicalAuthority=false · production authority unchanged · no duplicate catalogue
      </p>
    </div>
  )
}
