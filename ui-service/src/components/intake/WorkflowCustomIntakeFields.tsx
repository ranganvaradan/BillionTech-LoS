import { workflowCustomFields } from '@/lib/workflow/workflowIntakeRules'
import type { WorkflowConfigResponse } from '@/types/workflow'

/**
 * Renders workflow-configured custom intake fields (TEXT / NUMBER / BOOLEAN).
 * Values are stored under personalInfo.customFields via the intake payloads.
 */
export function WorkflowCustomIntakeFields({
  workflow,
  values,
  onChange,
  className = 'block text-sm text-slate-700 sm:col-span-2',
}: {
  workflow: WorkflowConfigResponse | null | undefined
  values: Record<string, string | boolean>
  onChange: (key: string, value: string | boolean) => void
  className?: string
}) {
  const fields = workflowCustomFields(workflow)
  if (!fields.length) return null

  return (
    <>
      {fields.map((field) => {
        const key = field.key
        if (!key) return null
        const raw = values[key]
        const requiredMark = field.required ? ' *' : ''

        if (field.type === 'BOOLEAN') {
          return (
            <label key={key} className={`${className} flex items-start gap-2`}>
              <input
                type="checkbox"
                className="mt-1"
                checked={raw === true}
                onChange={(e) => onChange(key, e.target.checked)}
              />
              <span>
                <span className="block text-xs font-medium text-slate-500">
                  {field.label}
                  {requiredMark}
                </span>
                {field.helpText ? <span className="mt-0.5 block text-[11px] text-slate-400">{field.helpText}</span> : null}
              </span>
            </label>
          )
        }

        return (
          <label key={key} className={className}>
            <span className="mb-1 block text-xs font-medium text-slate-500">
              {field.label}
              {requiredMark}
            </span>
            <input
              type={field.type === 'NUMBER' ? 'number' : 'text'}
              className="bt-input w-full"
              value={typeof raw === 'boolean' ? '' : String(raw ?? '')}
              onChange={(e) => onChange(key, e.target.value)}
            />
            {field.helpText ? <span className="mt-1 block text-[11px] text-slate-400">{field.helpText}</span> : null}
          </label>
        )
      })}
    </>
  )
}
