import { formatMoneyWithScale } from '@/lib/format'
import type { DecisionSummaryModel } from '@/lib/decision/decisionPresentation'

export function DecisionSummaryPanel({
  model,
  onFocusSection,
}: {
  model: DecisionSummaryModel
  onFocusSection?: (section: NonNullable<DecisionSummaryModel['nextFocus']>) => void
}) {
  return (
    <div className="bt-section-card bt-section-card--hero space-y-3 p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-slate-900">Decision Summary</h3>
          <p className="mt-0.5 text-xs text-slate-500">Human credit conclusion and completion progress.</p>
        </div>
        {model.nextFocus && model.nextFocusLabel && onFocusSection ? (
          <button
            type="button"
            className="bt-btn bt-btn-secondary bt-btn-sm"
            onClick={() => onFocusSection(model.nextFocus!)}
          >
            {model.nextFocusLabel}
          </button>
        ) : null}
      </div>

      <dl className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-3">
        <div>
          <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">
            Assessment outcome
          </dt>
          <dd className="mt-0.5 font-medium text-slate-900">{model.assessmentOutcome}</dd>
        </div>
        <div>
          <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">CAM status</dt>
          <dd className="mt-0.5 font-medium text-slate-900">{model.camStatusLabel}</dd>
        </div>
        <div>
          <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">
            Sanction status
          </dt>
          <dd className="mt-0.5 font-medium text-slate-900">{model.sanctionStatusLabel}</dd>
        </div>
        <div>
          <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">
            Requested amount
          </dt>
          <dd className="mt-0.5 font-medium tabular-nums text-slate-900">
            {formatMoneyWithScale(model.requestedAmount)}
          </dd>
        </div>
        {model.sanctionedAmount != null ? (
          <div>
            <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">
              Sanctioned amount
            </dt>
            <dd className="mt-0.5 font-medium tabular-nums text-slate-900">
              {formatMoneyWithScale(model.sanctionedAmount)}
            </dd>
          </div>
        ) : null}
        <div>
          <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Current stage</dt>
          <dd className="mt-0.5 font-medium text-slate-900">{model.stageLabel}</dd>
        </div>
      </dl>

      {model.emptyHint ? <p className="text-sm text-slate-600">{model.emptyHint}</p> : null}
    </div>
  )
}
