import { metaForKycStep } from '@/lib/workflow/kycStepIntakeCatalog'
import type { VisualWorkflowStep, WorkflowStepDocumentRequired } from '@/lib/workflowVisual'

const DOCUMENT_TYPES = ['PAN_CARD', 'AADHAAR', 'PHOTOGRAPH', 'BANK_STATEMENT', 'GST_RETURN', 'OTHER']

export function WorkflowStepIntakeOptions({
  step,
  onChange,
}: {
  step: VisualWorkflowStep
  onChange: (next: VisualWorkflowStep) => void
}) {
  const meta = metaForKycStep(step.step)
  const hasIntakeField = meta?.fieldKey != null

  function updateDocs(next: WorkflowStepDocumentRequired[]) {
    onChange({ ...step, documentsRequired: next })
  }

  return (
    <details className="min-w-0 rounded border border-slate-200 bg-white p-2 sm:col-span-2">
      <summary className="cursor-pointer text-xs font-medium text-slate-600">Intake collection</summary>
      <div className="mt-2 space-y-2 border-t border-slate-100 pt-2">
        <label className="flex items-center gap-2 text-xs text-slate-600">
          <input
            type="checkbox"
            className="rounded border-slate-300"
            checked={step.collectAtIntake}
            onChange={(e) => onChange({ ...step, collectAtIntake: e.target.checked })}
          />
          Collect at application intake
        </label>
        {hasIntakeField ? (
          <label className="flex items-center gap-2 text-xs text-slate-600">
            <input
              type="checkbox"
              className="rounded border-slate-300"
              checked={step.fieldRequiredAtIntake}
              disabled={!step.collectAtIntake}
              onChange={(e) => onChange({ ...step, fieldRequiredAtIntake: e.target.checked })}
            />
            Field required at intake ({meta?.label})
          </label>
        ) : null}
        <label className="flex items-center gap-2 text-xs text-slate-600">
          <input
            type="checkbox"
            className="rounded border-slate-300"
            checked={step.documentRequired}
            disabled={!step.collectAtIntake}
            onChange={(e) => onChange({ ...step, documentRequired: e.target.checked })}
          />
          Document upload mandatory
        </label>
        {step.collectAtIntake ? (
          <div className="space-y-1">
            <span className="block text-xs text-slate-500">Required documents (optional override)</span>
            {step.documentsRequired.map((d, idx) => (
              <div key={`${d.documentType}-${idx}`} className="flex flex-wrap items-center gap-2">
                <select
                  className="rounded border border-slate-300 bg-white px-2 py-1 text-xs"
                  value={d.documentType}
                  onChange={(e) => {
                    const next = step.documentsRequired.slice()
                    next[idx] = { ...d, documentType: e.target.value }
                    updateDocs(next)
                  }}
                >
                  {DOCUMENT_TYPES.map((t) => (
                    <option key={t} value={t}>
                      {t}
                    </option>
                  ))}
                </select>
                <label className="flex items-center gap-1 text-xs text-slate-600">
                  <input
                    type="checkbox"
                    className="rounded border-slate-300"
                    checked={d.required}
                    onChange={(e) => {
                      const next = step.documentsRequired.slice()
                      next[idx] = { ...d, required: e.target.checked }
                      updateDocs(next)
                    }}
                  />
                  Required
                </label>
                <button
                  type="button"
                  className="text-xs text-rose-700"
                  onClick={() => updateDocs(step.documentsRequired.filter((_, i) => i !== idx))}
                >
                  Remove
                </button>
              </div>
            ))}
            <button
              type="button"
              className="rounded border border-slate-300 bg-slate-50 px-2 py-0.5 text-xs"
              onClick={() =>
                updateDocs([...step.documentsRequired, { documentType: meta?.defaultDocumentTypes[0] ?? 'OTHER', required: true }])
              }
            >
              Add document
            </button>
          </div>
        ) : null}
      </div>
    </details>
  )
}
