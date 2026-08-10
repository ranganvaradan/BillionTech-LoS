import type { WorkflowConfigResponse } from '@/types/workflow'
import { resolveTenureRules } from '@/lib/workflow/workflowIntakeRules'
import { tenureMagnitudeLabel } from '@/catalog/lmsTenureUnits'

export function IntakeTenureField({
  workflow,
  value,
  lmsTenureUnit,
  onChange,
}: {
  workflow: WorkflowConfigResponse | null
  value: string
  lmsTenureUnit: string
  onChange: (v: string) => void
}) {
  const rules = resolveTenureRules(workflow)
  const label = tenureMagnitudeLabel(lmsTenureUnit)

  if (rules?.inputMode === 'dropdown' && (rules.options?.length ?? 0) > 0) {
    return (
      <label className="block text-sm text-slate-700">
        <span className="mb-1 block text-xs font-medium text-slate-500">
          {label} *
        </span>
        <select className="bt-input w-full" value={value} onChange={(e) => onChange(e.target.value)}>
          <option value="">Select tenure</option>
          {rules.options!.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </label>
    )
  }

  const min = rules?.min
  const max = rules?.max
  const hint =
    min != null && max != null && min > 0 && max > 0
      ? `Allowed range: ${min}–${max} ${lmsTenureUnit.toLowerCase()}`
      : undefined

  return (
    <label className="block text-sm text-slate-700">
      <span className="mb-1 block text-xs font-medium text-slate-500">{label}</span>
      <input
        className="bt-input w-full"
        type="number"
        min={min && min > 0 ? min : 1}
        max={max && max > 0 ? max : undefined}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
      {hint ? <p className="mt-0.5 text-xs text-slate-500">{hint}</p> : null}
    </label>
  )
}
