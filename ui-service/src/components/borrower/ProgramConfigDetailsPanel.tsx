import { useState } from 'react'
import type { ProgramDetailRow } from '@/utils/programDetailsDisplay'

export function ProgramConfigDetailsPanel({
  rows,
  label = 'Show more details',
  hideLabel = 'Hide details',
  className = '',
}: {
  rows: ProgramDetailRow[]
  label?: string
  hideLabel?: string
  className?: string
}) {
  const [open, setOpen] = useState(false)
  if (rows.length === 0) return null

  return (
    <div className={className}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="text-xs font-semibold text-blue-600 hover:text-blue-800 focus:outline-none focus-visible:underline"
      >
        {open ? hideLabel : label}
      </button>
      {open ? (
        <dl className="mt-2.5 grid grid-cols-1 gap-x-5 gap-y-2.5 rounded-lg border border-[var(--bt-gray-200)] bg-white/80 p-3 text-sm sm:grid-cols-2">
          {rows.map((row) => (
            <div key={row.label} className="min-w-0">
              <dt className="text-[11px] font-medium uppercase tracking-wide text-[var(--bt-gray-500)]">
                {row.label}
              </dt>
              <dd className="mt-0.5 break-words font-medium text-[var(--bt-gray-800)]">{row.value}</dd>
            </div>
          ))}
        </dl>
      ) : null}
    </div>
  )
}
