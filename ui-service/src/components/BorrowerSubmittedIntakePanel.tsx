import { useEffect, useMemo, useState } from 'react'
import { listWorkflows } from '@/api/workflows'
import { buildIntakeReadback } from '@/lib/intake/intakeReadback'
import { resolveIntakeSegment } from '@/lib/applicationPartyLabels'
import type { ApplicationResponse } from '@/types/application'

export function BorrowerSubmittedIntakePanel({ app }: { app: ApplicationResponse }) {
  const [customFieldLabels, setCustomFieldLabels] = useState<Record<string, string> | null>(null)

  useEffect(() => {
    let cancelled = false
    void (async () => {
      try {
        const workflows = await listWorkflows()
        if (cancelled) return
        const segment = resolveIntakeSegment(app.intakeSegment)
        const match =
          (app.workflowId ? workflows.find((w) => w.id === app.workflowId) : null) ??
          workflows.find(
            (w) =>
              w.active !== false &&
              w.borrowerType === app.borrowerType &&
              w.loanProduct === app.loanProduct &&
              (w.intakeSegment ?? 'BORROWER') === segment,
          )
        const fields = match?.intakeConfig?.customFields ?? []
        if (!fields.length) {
          setCustomFieldLabels(null)
          return
        }
        const labels: Record<string, string> = {}
        for (const f of fields) {
          if (f?.key && f?.label) labels[f.key] = f.label
        }
        setCustomFieldLabels(Object.keys(labels).length ? labels : null)
      } catch {
        if (!cancelled) setCustomFieldLabels(null)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [app.workflowId, app.borrowerType, app.loanProduct, app.intakeSegment])

  const sections = useMemo(
    () => buildIntakeReadback(app, customFieldLabels),
    [app, customFieldLabels],
  )

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
        <div key={sec.title} className="bt-section-card bt-section-card--default p-4">
          <h3 className="mb-3 bt-section-card__title">{sec.title}</h3>
          <div className="grid gap-2 sm:grid-cols-2">
            {sec.rows.map((r) => (
              <div
                key={`${sec.title}-${r.label}`}
                className="rounded-lg border border-slate-100 bg-gradient-to-b from-slate-50 to-white px-3 py-2 shadow-sm"
              >
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
