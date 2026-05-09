import { providersForWorkflowStep } from '@/lib/integrationProviderMatrix'
import {
  WORKFLOW_STEP_TYPES,
  createEmptyVisualStep,
  defaultProviderForWorkflowStep,
  getStepMatrixHelp,
  isPostKycWorkflowStep,
  type VisualWorkflowStep,
} from '@/lib/workflowVisual'

type Props = {
  steps: VisualWorkflowStep[]
  onChange: (next: VisualWorkflowStep[]) => void
}

function move<T>(arr: T[], from: number, to: number): T[] {
  if (to < 0 || to >= arr.length) return arr
  const c = arr.slice()
  const [x] = c.splice(from, 1)
  c.splice(to, 0, x)
  return c
}

function StepCard(props: {
  s: VisualWorkflowStep
  i: number
  steps: VisualWorkflowStep[]
  onChange: (next: VisualWorkflowStep[]) => void
}) {
  const { s, i, steps, onChange } = props
  const postKyc = isPostKycWorkflowStep(s.step)
  const help = getStepMatrixHelp(s.step)
  const providerOptions = providersForWorkflowStep(s.step)
  return (
    <li
      className={
        postKyc
          ? 'rounded-lg border border-indigo-200/80 bg-indigo-50/50 p-3 shadow-sm'
          : 'rounded-lg border border-slate-200 bg-slate-50/80 p-3 shadow-sm'
      }
    >
      {postKyc ? (
        <p className="mb-2 text-xs font-medium text-indigo-800">After KYC (credit / bureau)</p>
      ) : (
        <p className="mb-2 text-xs font-medium text-slate-600">KYC verification</p>
      )}
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
        <span className="text-xs font-medium text-slate-500">
          Order: <span className="font-mono text-slate-800">{i + 1}</span>
        </span>
        <div className="flex flex-wrap gap-1">
          <button
            type="button"
            className="rounded border border-slate-300 bg-white px-2 py-0.5 text-xs"
            onClick={() => onChange(move(steps, i, i - 1))}
            disabled={i === 0}
          >
            Up
          </button>
          <button
            type="button"
            className="rounded border border-slate-300 bg-white px-2 py-0.5 text-xs"
            onClick={() => onChange(move(steps, i, i + 1))}
            disabled={i === steps.length - 1}
          >
            Down
          </button>
          <button
            type="button"
            className="rounded border border-rose-200 bg-rose-50 px-2 py-0.5 text-xs text-rose-900"
            onClick={() => onChange(steps.filter((x) => x.id !== s.id))}
          >
            Remove
          </button>
        </div>
      </div>
      <div className="grid gap-2 sm:grid-cols-2">
        <label className="block text-xs text-slate-600 sm:col-span-2">
          <span className="mb-0.5 block text-slate-500">Step name (label, optional)</span>
          <input
            className="w-full rounded border border-slate-300 px-2 py-1 text-sm"
            value={s.name}
            onChange={(e) => {
              const v = e.target.value
              onChange(
                steps.map((x) => (x.id === s.id ? { ...x, name: v } : x)),
              )
            }}
            placeholder="e.g. Customer PAN"
          />
        </label>
        <label className="block text-xs text-slate-600">
          <span className="mb-0.5 block text-slate-500">Step type *</span>
          <select
            className="w-full rounded border border-slate-300 bg-white px-2 py-1 text-sm"
            value={s.step}
            onChange={(e) => {
              const v = e.target.value
              onChange(
                steps.map((x) =>
                  x.id === s.id
                    ? { ...x, step: v, provider: defaultProviderForWorkflowStep(v) }
                    : x,
                ),
              )
            }}
          >
            {WORKFLOW_STEP_TYPES.map((t) => (
              <option key={t} value={t}>
                {t}
                {isPostKycWorkflowStep(t) ? ' (post-KYC / eSign)' : ''}
              </option>
            ))}
          </select>
          <p className="mt-1 text-xs text-slate-500">
            <span className="font-medium text-slate-600">Purpose:</span> {help.purpose}
            <br />
            <span className="font-medium text-slate-600">Applies to:</span> {help.appliesTo}
          </p>
        </label>
        <label className="block text-xs text-slate-600">
          <span className="mb-0.5 block text-slate-500">Provider (ordered primary → fallback)</span>
          <select
            className="w-full rounded border border-slate-300 bg-white px-2 py-1 text-sm"
            value={s.provider || defaultProviderForWorkflowStep(s.step)}
            onChange={(e) => {
              const v = e.target.value
              onChange(
                steps.map((x) => (x.id === s.id ? { ...x, provider: v } : x)),
              )
            }}
          >
            {providerOptions.map((p) => (
              <option key={p} value={p}>
                {p}
              </option>
            ))}
          </select>
        </label>
        <label className="flex items-center gap-2 text-xs text-slate-600 sm:col-span-2">
          <input
            type="checkbox"
            className="rounded border-slate-300"
            checked={s.mandatory}
            onChange={(e) => {
              const v = e.target.checked
              onChange(
                steps.map((x) => (x.id === s.id ? { ...x, mandatory: v } : x)),
              )
            }}
          />
          Mandatory
        </label>
      </div>
    </li>
  )
}

export function WorkflowStepEditorPanel({ steps, onChange }: Props) {
  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-xs font-medium text-slate-500">Workflow steps (order = execution order)</span>
        <button
          type="button"
          onClick={() => onChange([...steps, createEmptyVisualStep()])}
          className="rounded border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-800"
        >
          Add step
        </button>
      </div>
      {steps.length === 0 ? (
        <p className="text-sm text-slate-500">No steps. Add a step or use Advanced JSON below.</p>
      ) : null}
      <ul className="space-y-2">
        {steps.map((s, i) => (
          <StepCard key={s.id} s={s} i={i} steps={steps} onChange={onChange} />
        ))}
      </ul>
    </div>
  )
}
