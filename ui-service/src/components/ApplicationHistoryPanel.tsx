import { useMemo, useState } from 'react'
import { formatInstant } from '@/lib/format'
import type { ApplicationTimelineEntry, ApplicationTimelineResponse } from '@/types/applicationTimeline'
import { ApplicationFlowMap } from '@/components/ApplicationFlowMap'

type ViewMode = 'list' | 'map'
type DetailMode = 'friendly' | 'json'

function categoryBadgeClass(category: string): string {
  switch (category) {
    case 'STATUS':
      return 'border-sky-200 bg-sky-50 text-sky-800'
    case 'PROCESS':
      return 'border-violet-200 bg-violet-50 text-violet-800'
    case 'INTEGRATION':
      return 'border-cyan-200 bg-cyan-50 text-cyan-900'
    case 'ACTION':
    default:
      return 'border-amber-200 bg-amber-50 text-amber-900'
  }
}

function resultBadgeClass(result: string | null | undefined): string {
  const r = (result ?? '').toUpperCase()
  if (r.includes('FAIL') || r.includes('REJECT') || r === 'FAILED' || r === 'ERROR') {
    return 'text-rose-700'
  }
  if (r.includes('SUCCESS') || r.includes('COMPLETED') || r.includes('DISBURSED') || r === 'PASS') {
    return 'text-emerald-700'
  }
  return 'text-[var(--bt-gray-700)]'
}

function dayKey(iso: string | null | undefined): string {
  if (!iso) return 'Unknown date'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return 'Unknown date'
  return d.toLocaleDateString(undefined, { weekday: 'short', year: 'numeric', month: 'short', day: 'numeric' })
}

function humanLabel(key: string): string {
  return key
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase())
}

function isPlainObject(v: unknown): v is Record<string, unknown> {
  return !!v && typeof v === 'object' && !Array.isArray(v)
}

function flattenFriendlyDetails(details: Record<string, unknown> | undefined): { label: string; value: string }[] {
  if (!details) return []
  const rows: { label: string; value: string }[] = []

  const push = (label: string, value: unknown) => {
    if (value == null || value === '') return
    if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
      rows.push({ label, value: String(value) })
      return
    }
    if (Array.isArray(value)) {
      if (value.every((x) => typeof x === 'string' || typeof x === 'number')) {
        rows.push({ label, value: value.map(String).join(', ') })
      } else {
        rows.push({ label, value: `${value.length} item(s)` })
      }
      return
    }
  }

  const skipRaw = new Set(['requestPayload', 'responsePayload', 'outputJson', 'previousState', 'newState'])
  for (const [k, v] of Object.entries(details)) {
    if (skipRaw.has(k)) continue
    push(humanLabel(k), v)
  }

  const ns = details.newState
  if (isPlainObject(ns)) {
    for (const [k, v] of Object.entries(ns)) {
      if (k === 'steps' || k === 'stepSummary') continue
      push(humanLabel(k), v)
    }
    if (Array.isArray(ns.steps) || Array.isArray(ns.stepSummary)) {
      const steps = (ns.steps ?? ns.stepSummary) as unknown[]
      rows.push({ label: 'KYC steps', value: `${steps.length} step(s) evaluated` })
    }
  }

  const ps = details.previousState
  if (isPlainObject(ps) && ps.outcome != null) {
    push('Previous outcome', ps.outcome)
  }

  if (typeof details.requestPayload === 'string' && details.requestPayload.trim()) {
    rows.push({ label: 'Request', value: 'Available — use View JSON' })
  }
  if (typeof details.responsePayload === 'string' && details.responsePayload.trim()) {
    rows.push({ label: 'Response', value: 'Available — use View JSON' })
  }

  return rows
}

function TimelineEntryDetails({ entry }: { entry: ApplicationTimelineEntry }) {
  const [mode, setMode] = useState<DetailMode>('friendly')
  const friendly = useMemo(() => flattenFriendlyDetails(entry.details), [entry.details])
  const jsonText = useMemo(() => JSON.stringify(entry.details ?? {}, null, 2), [entry.details])

  return (
    <div className="mt-3 overflow-hidden rounded-lg border border-[var(--bt-gray-100)] bg-[var(--bt-gray-50)]">
      <div className="flex items-center justify-between gap-2 border-b border-[var(--bt-gray-100)] px-3 py-2">
        <p className="text-[11px] font-semibold uppercase tracking-wide text-[var(--bt-gray-500)]">Details</p>
        <div className="inline-flex rounded-md border border-[var(--bt-gray-200)] bg-white p-0.5">
          <button
            type="button"
            className={`rounded px-2 py-0.5 text-[11px] font-medium ${
              mode === 'friendly' ? 'bg-[var(--bt-orange)] text-white' : 'text-[var(--bt-gray-600)]'
            }`}
            onClick={() => setMode('friendly')}
          >
            Summary
          </button>
          <button
            type="button"
            className={`rounded px-2 py-0.5 text-[11px] font-medium ${
              mode === 'json' ? 'bg-[var(--bt-orange)] text-white' : 'text-[var(--bt-gray-600)]'
            }`}
            onClick={() => setMode('json')}
          >
            JSON
          </button>
        </div>
      </div>
      {mode === 'friendly' ? (
        friendly.length === 0 ? (
          <p className="px-3 py-3 text-xs text-[var(--bt-gray-500)]">No additional details.</p>
        ) : (
          <dl className="grid gap-0 sm:grid-cols-2">
            {friendly.map((row) => (
              <div
                key={`${row.label}-${row.value}`}
                className="border-b border-[var(--bt-gray-100)] px-3 py-2 last:border-b-0 sm:odd:border-r"
              >
                <dt className="text-[10px] font-semibold uppercase tracking-wide text-[var(--bt-gray-500)]">
                  {row.label}
                </dt>
                <dd className="mt-0.5 break-words text-xs text-[var(--bt-gray-800)]">{row.value}</dd>
              </div>
            ))}
          </dl>
        )
      ) : (
        <pre className="max-h-64 overflow-auto whitespace-pre-wrap break-words p-3 font-mono text-[11px] text-[var(--bt-gray-700)]">
          {jsonText}
        </pre>
      )}
    </div>
  )
}

export function ApplicationHistoryPanel({ timeline }: { timeline: ApplicationTimelineResponse }) {
  const [mode, setMode] = useState<ViewMode>('list')
  const [expanded, setExpanded] = useState<string | null>(null)
  const [flowFullscreen, setFlowFullscreen] = useState(false)

  const grouped = useMemo(() => {
    const map = new Map<string, ApplicationTimelineEntry[]>()
    for (const e of timeline.entries ?? []) {
      const key = dayKey(e.occurredAt)
      const list = map.get(key) ?? []
      list.push(e)
      map.set(key, list)
    }
    return [...map.entries()]
  }, [timeline.entries])

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-medium text-[var(--bt-gray-900)]">Application history</h2>
          <p className="mt-0.5 text-sm text-[var(--bt-gray-600)]">
            Status changes, staff actions, process checks, and integration API calls.
          </p>
        </div>
        <div className="inline-flex rounded-lg border border-[var(--bt-gray-200)] bg-white p-0.5 shadow-sm">
          <button
            type="button"
            className={`rounded-md px-3 py-1.5 text-sm font-medium transition ${
              mode === 'list'
                ? 'bg-[var(--bt-orange)] text-white'
                : 'text-[var(--bt-gray-700)] hover:bg-[var(--bt-gray-50)]'
            }`}
            onClick={() => setMode('list')}
          >
            Timeline
          </button>
          <button
            type="button"
            className={`rounded-md px-3 py-1.5 text-sm font-medium transition ${
              mode === 'map'
                ? 'bg-[var(--bt-orange)] text-white'
                : 'text-[var(--bt-gray-700)] hover:bg-[var(--bt-gray-50)]'
            }`}
            onClick={() => setMode('map')}
          >
            Flow map
          </button>
        </div>
      </div>

      {mode === 'map' ? (
        <ApplicationFlowMap
          timeline={timeline}
          fullscreen={flowFullscreen}
          onToggleFullscreen={() => setFlowFullscreen((v) => !v)}
        />
      ) : (timeline.entries?.length ?? 0) === 0 ? (
        <div className="bt-card bt-empty-state p-4">No history recorded yet.</div>
      ) : (
        <div className="space-y-6">
          {grouped.map(([day, rows]) => (
            <div key={day}>
              <p className="mb-3 text-xs font-semibold uppercase tracking-wide text-[var(--bt-gray-500)]">{day}</p>
              <ol className="relative space-y-0 border-l-2 border-[var(--bt-gray-200)] pl-5">
                {rows.map((entry) => {
                  const open = expanded === entry.id
                  const hasDetails = entry.details && Object.keys(entry.details).length > 0
                  return (
                    <li key={entry.id} className="relative pb-5 last:pb-0">
                      <span
                        className={`absolute -left-[1.4rem] top-1.5 h-2.5 w-2.5 rounded-full border-2 border-white shadow ${
                          entry.category === 'INTEGRATION'
                            ? 'bg-cyan-500'
                            : entry.category === 'STATUS'
                              ? 'bg-sky-500'
                              : entry.category === 'PROCESS'
                                ? 'bg-violet-500'
                                : 'bg-[var(--bt-orange)]'
                        }`}
                      />
                      <div className="bt-card border border-[var(--bt-gray-100)] p-3 shadow-sm">
                        <div className="flex flex-wrap items-start justify-between gap-2">
                          <div className="min-w-0 flex-1">
                            <div className="flex flex-wrap items-center gap-2">
                              <span
                                className={`inline-flex rounded-md border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${categoryBadgeClass(String(entry.category))}`}
                              >
                                {entry.category}
                              </span>
                              <p className="text-sm font-semibold text-[var(--bt-gray-900)]">{entry.title}</p>
                            </div>
                            {entry.summary ? (
                              <p className="mt-1 text-sm text-[var(--bt-gray-600)]">{entry.summary}</p>
                            ) : null}
                            <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-[var(--bt-gray-500)]">
                              <span className="tabular-nums">{formatInstant(entry.occurredAt)}</span>
                              {entry.completedAt && entry.completedAt !== entry.occurredAt ? (
                                <span className="tabular-nums">Completed {formatInstant(entry.completedAt)}</span>
                              ) : null}
                              {entry.actor ? <span>Actor: {entry.actor}</span> : null}
                              {entry.result ? (
                                <span className={`font-medium ${resultBadgeClass(entry.result)}`}>
                                  {entry.result}
                                </span>
                              ) : null}
                            </div>
                          </div>
                          {hasDetails ? (
                            <button
                              type="button"
                              className="bt-btn bt-btn-secondary bt-btn-sm shrink-0"
                              onClick={() => setExpanded(open ? null : entry.id)}
                            >
                              {open ? 'Hide details' : 'Details'}
                            </button>
                          ) : null}
                        </div>
                        {open ? <TimelineEntryDetails entry={entry} /> : null}
                      </div>
                    </li>
                  )
                })}
              </ol>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
