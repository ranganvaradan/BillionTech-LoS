import type { PinnedWorkflowDisplay } from '@/lib/workflow/pinnedWorkflowDisplay'

type Props = {
  display: PinnedWorkflowDisplay
  className?: string
  tone?: 'neutral' | 'success'
}

/** Read-only summary of the workflow pinned on the application via Customer Category selection. */
export function PinnedWorkflowSummary({ display, className = '', tone = 'neutral' }: Props) {
  const labelTone = tone === 'success' ? 'text-emerald-800' : 'text-slate-800'
  const metaTone = tone === 'success' ? 'text-emerald-700' : 'text-slate-500'
  const categoryLabel = display.categoryDisplayName ?? display.categoryCode

  return (
    <div className={`text-xs ${metaTone} ${className}`.trim()}>
      <p>
        Workflow{' '}
        <span className={`font-medium ${labelTone}`}>{display.workflowName}</span> (v{display.workflowVersion})
      </p>
      {categoryLabel != null && display.categoryVersion != null ? (
        <p className="mt-1">
          Selected through Customer Category:{' '}
          <span className={`font-medium ${labelTone}`}>{categoryLabel}</span> (v{display.categoryVersion})
        </p>
      ) : null}
    </div>
  )
}
