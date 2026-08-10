import type { ReactNode } from 'react'
import { businessOutcomeLabel, outcomeChipClass } from '@/lib/creditIntelligence/businessLexicon'

export function CiSection({
  title,
  description,
  children,
  actions,
}: {
  title: string
  description?: string
  children: ReactNode
  actions?: ReactNode
}) {
  return (
    <section className="bt-card mb-4 overflow-hidden">
      <div className="flex flex-wrap items-start justify-between gap-2 border-b border-slate-100 px-4 py-3">
        <div>
          <h2 className="bt-card-title text-base">{title}</h2>
          {description ? <p className="mt-0.5 text-[13px] text-[var(--bt-gray-500)]">{description}</p> : null}
        </div>
        {actions ? <div className="flex shrink-0 flex-wrap gap-2">{actions}</div> : null}
      </div>
      <div className="px-4 py-3">{children}</div>
    </section>
  )
}

/** Collapsed-by-default developer diagnostics. Hidden entirely in Prospect Demo Mode. */
export function CiTechnicalDetails({
  title = 'Technical Details',
  children,
  hidden,
}: {
  title?: string
  children: ReactNode
  /** When true (Prospect Demo Mode), render nothing. */
  hidden?: boolean
}) {
  if (hidden) return null
  return (
    <details className="mt-3 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-600">
      <summary className="cursor-pointer font-medium text-slate-700">{title}</summary>
      <div className="mt-2 overflow-x-auto whitespace-pre-wrap break-all font-mono text-slate-500">{children}</div>
    </details>
  )
}

export function CiExecutiveSummary({
  title = 'Summary',
  children,
  nextAction,
}: {
  title?: string
  children: ReactNode
  nextAction?: ReactNode
}) {
  return (
    <div className="mb-4 rounded-xl border border-slate-200 bg-gradient-to-br from-slate-50 to-white px-5 py-4 shadow-sm">
      <div className="text-[11px] font-semibold uppercase tracking-[0.12em] text-slate-500">{title}</div>
      <div className="mt-2 text-sm text-slate-800">{children}</div>
      {nextAction ? <div className="mt-4">{nextAction}</div> : null}
    </div>
  )
}

export function CiOutcomeBadge({ value }: { value: unknown }) {
  return (
    <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-semibold ${outcomeChipClass(value)}`}>
      {businessOutcomeLabel(value)}
    </span>
  )
}

export function CiEmptyState({ title, detail, action }: { title: string; detail: string; action?: ReactNode }) {
  return (
    <div className="rounded-xl border border-dashed border-slate-300 bg-slate-50 px-6 py-10 text-center">
      <p className="text-base font-semibold text-slate-900">{title}</p>
      <p className="mx-auto mt-2 max-w-md text-sm text-slate-600">{detail}</p>
      {action ? <div className="mt-5 flex justify-center">{action}</div> : null}
    </div>
  )
}

export function CiStatCard({
  label,
  value,
  tone = 'neutral',
}: {
  label: string
  value: string | number
  tone?: 'approved' | 'review' | 'info' | 'action' | 'neutral'
}) {
  const valueCls =
    tone === 'approved'
      ? 'text-emerald-800'
      : tone === 'review'
        ? 'text-amber-800'
        : tone === 'info'
          ? 'text-sky-800'
          : tone === 'action'
            ? 'text-rose-800'
            : 'text-slate-900'
  return (
    <div className="rounded-lg border border-slate-200 bg-white px-3 py-3">
      <div className={`text-2xl font-semibold tabular-nums ${valueCls}`}>{value}</div>
      <div className="mt-1 text-xs font-medium text-slate-600">{label}</div>
    </div>
  )
}

export function CiLoadingCopy({ lines }: { lines: string[] }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white px-5 py-6">
      <p className="text-sm font-medium text-slate-900">{lines[0]}</p>
      <ul className="mt-4 space-y-2 text-sm text-slate-600">
        {lines.slice(1).map((line) => (
          <li key={line} className="flex gap-2">
            <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-sky-600" />
            {line}
          </li>
        ))}
      </ul>
    </div>
  )
}
