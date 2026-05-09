import { buildIntakeReadback } from '@/lib/intake/intakeReadback'
import type { ApplicationResponse } from '@/types/application'

export function BorrowerSubmittedIntakePanel({ app }: { app: ApplicationResponse }) {
  const sections = buildIntakeReadback(app)
  if (sections.length === 0) {
    return (
      <p className="text-sm text-slate-600">
        No detailed intake fields are stored on this application yet. They appear here after the applicant completes the
        intake steps (or after staff entry).
      </p>
    )
  }
  return (
    <div className="space-y-6">
      {sections.map((sec) => (
        <div key={sec.title}>
          <h3 className="mb-2 text-sm font-semibold text-slate-900">{sec.title}</h3>
          <div className="grid gap-2 sm:grid-cols-2">
            {sec.rows.map((r) => (
              <div key={r.label} className="rounded border border-slate-100 bg-slate-50/80 px-3 py-2">
                <div className="text-xs font-medium text-slate-500">{r.label}</div>
                <div className="mt-0.5 text-sm text-slate-900 break-words">{r.value}</div>
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}
