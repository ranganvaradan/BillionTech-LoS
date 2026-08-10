import { useMemo, useState } from 'react'

type JsonValue = null | boolean | number | string | JsonValue[] | { [key: string]: JsonValue }

function isPlainObject(v: unknown): v is Record<string, unknown> {
  return v != null && typeof v === 'object' && !Array.isArray(v)
}

function stringifyValue(v: unknown): string {
  if (v === undefined) return '—'
  if (v === null) return 'null'
  if (typeof v === 'string') return v === '' ? '(empty)' : v
  if (typeof v === 'number' || typeof v === 'boolean') return String(v)
  try {
    return JSON.stringify(v, null, 2)
  } catch {
    return String(v)
  }
}

function valuesEqual(a: unknown, b: unknown): boolean {
  if (a === b) return true
  if (a == null || b == null) return a === b
  if (typeof a !== typeof b) return false
  if (typeof a !== 'object') return false
  try {
    return JSON.stringify(a) === JSON.stringify(b)
  } catch {
    return false
  }
}

function flattenObject(
  value: unknown,
  prefix = '',
  out: Record<string, unknown> = {},
): Record<string, unknown> {
  if (value === undefined) return out
  if (!isPlainObject(value) && !Array.isArray(value)) {
    out[prefix || '(value)'] = value as JsonValue
    return out
  }
  if (Array.isArray(value)) {
    if (value.length === 0) {
      out[prefix || '(array)'] = []
      return out
    }
    value.forEach((item, idx) => {
      const key = prefix ? `${prefix}[${idx}]` : `[${idx}]`
      if (isPlainObject(item) || Array.isArray(item)) flattenObject(item, key, out)
      else out[key] = item
    })
    return out
  }
  const keys = Object.keys(value)
  if (keys.length === 0) {
    out[prefix || '(object)'] = {}
    return out
  }
  for (const k of keys) {
    const next = prefix ? `${prefix}.${k}` : k
    const child = value[k]
    if (isPlainObject(child) || Array.isArray(child)) flattenObject(child, next, out)
    else out[next] = child
  }
  return out
}

export type AuditDiffRow = {
  field: string
  before: unknown
  after: unknown
  status: 'added' | 'removed' | 'changed' | 'unchanged'
}

export function buildAuditDiffRows(
  before: Record<string, unknown> | null | undefined,
  after: Record<string, unknown> | null | undefined,
): AuditDiffRow[] {
  const left = flattenObject(before ?? {})
  const right = flattenObject(after ?? {})
  const keys = Array.from(new Set([...Object.keys(left), ...Object.keys(right)])).sort((a, b) =>
    a.localeCompare(b),
  )
  return keys.map((field) => {
    const hasBefore = Object.prototype.hasOwnProperty.call(left, field)
    const hasAfter = Object.prototype.hasOwnProperty.call(right, field)
    const b = left[field]
    const a = right[field]
    if (hasBefore && !hasAfter) return { field, before: b, after: undefined, status: 'removed' as const }
    if (!hasBefore && hasAfter) return { field, before: undefined, after: a, status: 'added' as const }
    if (!valuesEqual(b, a)) return { field, before: b, after: a, status: 'changed' as const }
    return { field, before: b, after: a, status: 'unchanged' as const }
  })
}

const STATUS_STYLES: Record<AuditDiffRow['status'], string> = {
  added: 'bg-emerald-50 border-emerald-200 text-emerald-900',
  removed: 'bg-rose-50 border-rose-200 text-rose-900',
  changed: 'bg-amber-50 border-amber-200 text-amber-950',
  unchanged: 'bg-slate-50 border-slate-200 text-slate-700',
}

const STATUS_LABEL: Record<AuditDiffRow['status'], string> = {
  added: 'Added',
  removed: 'Removed',
  changed: 'Changed',
  unchanged: 'Unchanged',
}

export function AuditDiffView({
  before,
  after,
  beforeLabel = 'Before',
  afterLabel = 'After',
  changedFieldsHint,
}: {
  before?: Record<string, unknown> | null
  after?: Record<string, unknown> | null
  beforeLabel?: string
  afterLabel?: string
  changedFieldsHint?: string | null
}) {
  const [showUnchanged, setShowUnchanged] = useState(false)
  const [showRaw, setShowRaw] = useState(false)
  const rows = useMemo(() => buildAuditDiffRows(before, after), [before, after])
  const changed = rows.filter((r) => r.status !== 'unchanged')
  const visible = showUnchanged ? rows : changed

  return (
    <div className="mt-0 space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-semibold text-amber-900">
          {changed.length} change{changed.length === 1 ? '' : 's'}
        </span>
        {changedFieldsHint ? (
          <span className="text-[10px] text-[var(--bt-gray-500)]">Hint: {changedFieldsHint}</span>
        ) : null}
        <button
          type="button"
          className="text-[10px] font-medium text-[var(--bt-orange)] hover:underline"
          onClick={() => setShowUnchanged((v) => !v)}
        >
          {showUnchanged ? 'Hide unchanged' : 'Show unchanged'}
        </button>
        <button
          type="button"
          className="text-[10px] font-medium text-slate-600 hover:underline"
          onClick={() => setShowRaw((v) => !v)}
        >
          {showRaw ? 'Hide raw JSON' : 'Raw JSON'}
        </button>
      </div>

      {visible.length === 0 ? (
        <p className="rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-[11px] text-slate-600">
          No field-level differences detected.
        </p>
      ) : (
        <div className="overflow-hidden rounded-lg border border-slate-200">
          <div className="grid grid-cols-[minmax(7rem,1.1fr)_1fr_1fr_auto] gap-0 border-b border-slate-200 bg-slate-100/80 px-2 py-1.5 text-[10px] font-semibold uppercase tracking-wide text-slate-500">
            <span>Field</span>
            <span>{beforeLabel}</span>
            <span>{afterLabel}</span>
            <span>Status</span>
          </div>
          <ul className="max-h-64 divide-y divide-slate-100 overflow-auto bg-white">
            {visible.map((row) => (
              <li
                key={row.field}
                className={`grid grid-cols-[minmax(7rem,1.1fr)_1fr_1fr_auto] gap-2 px-2 py-2 text-[11px] ${STATUS_STYLES[row.status]}`}
              >
                <span className="break-all font-mono font-medium">{row.field}</span>
                <span className="whitespace-pre-wrap break-words font-mono opacity-90">
                  {stringifyValue(row.before)}
                </span>
                <span className="whitespace-pre-wrap break-words font-mono font-medium">
                  {stringifyValue(row.after)}
                </span>
                <span className="self-start rounded px-1.5 py-0.5 text-[10px] font-semibold">
                  {STATUS_LABEL[row.status]}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {showRaw ? (
        <div className="grid gap-2 md:grid-cols-2">
          <div>
            <p className="mb-1 text-[10px] font-semibold uppercase text-slate-500">{beforeLabel} JSON</p>
            <pre className="max-h-40 overflow-auto rounded bg-[var(--bt-gray-50)] p-2 text-[10px]">
              {JSON.stringify(before ?? {}, null, 2)}
            </pre>
          </div>
          <div>
            <p className="mb-1 text-[10px] font-semibold uppercase text-slate-500">{afterLabel} JSON</p>
            <pre className="max-h-40 overflow-auto rounded bg-[var(--bt-gray-50)] p-2 text-[10px]">
              {JSON.stringify(after ?? {}, null, 2)}
            </pre>
          </div>
        </div>
      ) : null}
    </div>
  )
}
