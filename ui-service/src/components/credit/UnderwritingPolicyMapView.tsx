import type { ReactNode } from 'react'
import { describeCondition } from '@/lib/credit/scorecardCondition'
import type { DependencyGroup, FormulaDefinition } from '@/api/scorecards'
import { BodmasExpressionPreview } from '@/components/credit/BodmasExpressionPreview'

export type PolicyMapHardRule = {
  id?: string
  parameter: string
  source: string
  condition: string
  decision: string
  message?: string
  dependsOn?: DependencyGroup
  formula?: FormulaDefinition
}

export type PolicyMapScoreParam = {
  id?: string
  parameter: string
  source: string
  condition: string
  weight?: number | string
  score?: number | string
  formula?: FormulaDefinition
  dependsOn?: DependencyGroup
}

export type PolicyMapScope = {
  name: string
  borrowerType?: string
  loanProduct?: string
  priority?: number | string
  active?: boolean
  minAmount?: string | number | null
  maxAmount?: string | number | null
  geography?: string
  version?: number | string
}

function FlowArrow({ label }: { label?: string }) {
  return (
    <div className="flex flex-col items-center py-1">
      <div className="h-4 w-px bg-gradient-to-b from-slate-300 to-orange-300" />
      {label ? (
        <span className="my-1 rounded-full border border-orange-200 bg-orange-50 px-2 py-0.5 text-[10px] font-medium text-orange-800">
          {label}
        </span>
      ) : null}
      <div className="flex h-5 w-5 items-center justify-center rounded-full border border-orange-200 bg-white text-orange-600 shadow-sm">
        <svg viewBox="0 0 12 12" className="h-3 w-3" aria-hidden>
          <path d="M6 2v6M3.5 6.5 6 9l2.5-2.5" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
        </svg>
      </div>
    </div>
  )
}

function MapCard({
  title,
  subtitle,
  tone = 'default',
  children,
}: {
  title: string
  subtitle?: string
  tone?: 'default' | 'match' | 'hard' | 'score' | 'decision'
  children?: ReactNode
}) {
  const tones: Record<string, string> = {
    default: 'border-slate-200 from-white to-slate-50',
    match: 'border-sky-200 from-sky-50 to-white',
    hard: 'border-rose-200 from-rose-50 to-white',
    score: 'border-emerald-200 from-emerald-50 to-white',
    decision: 'border-orange-200 from-orange-50 to-white',
  }
  return (
    <div className={`rounded-xl border bg-gradient-to-br p-3 shadow-sm ${tones[tone]}`}>
      <div className="mb-2">
        <h4 className="text-sm font-semibold text-slate-900">{title}</h4>
        {subtitle ? <p className="mt-0.5 text-xs text-slate-600">{subtitle}</p> : null}
      </div>
      {children}
    </div>
  )
}

function DepBadge({ dependsOn }: { dependsOn?: DependencyGroup }) {
  const n = dependsOn?.conditions?.length ?? 0
  if (!n) return null
  return (
    <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-medium text-slate-700">
      Depends ({dependsOn?.logic === 'ANY' ? 'ANY' : 'ALL'} · {n})
    </span>
  )
}

function FormulaChip({ formula }: { formula?: FormulaDefinition }) {
  if (!formula?.expression) return null
  return (
    <div className="mt-1.5">
      <BodmasExpressionPreview expression={formula.expression} compact title="Formula" />
    </div>
  )
}

export function UnderwritingPolicyMapView({
  kind,
  scope,
  hardRules = [],
  scoreParams = [],
  thresholds,
  legacyPolicy,
}: {
  kind: 'rule' | 'scorecard'
  scope: PolicyMapScope
  hardRules?: PolicyMapHardRule[]
  scoreParams?: PolicyMapScoreParam[]
  thresholds?: { approveMin?: number | string; manualMin?: number | string }
  legacyPolicy?: {
    minBureau?: string
    maxLoan?: string
    requireKyc?: boolean
    decision?: string
    scorecardRows?: Array<{ parameter: string; weight: string; mode: string; approveAt: string; manualAt: string }>
  }
}) {
  const amountRange = [
    scope.minAmount != null && String(scope.minAmount) !== '' ? String(scope.minAmount) : '0',
    scope.maxAmount != null && String(scope.maxAmount) !== '' ? String(scope.maxAmount) : '∞',
  ].join(' – ')

  return (
    <div className="space-y-1">
      <p className="mb-3 text-xs text-slate-600">
        Read-only mapping of how this {kind === 'rule' ? 'underwriting rule set' : 'scorecard'} is wired — match scope,
        gates, scoring, and decision outcomes. Editing is unchanged on the main form.
      </p>

      <MapCard
        tone="match"
        title={scope.name || 'Unnamed'}
        subtitle={`${kind === 'rule' ? 'Rule set' : 'Scorecard'} · ${scope.borrowerType ?? '—'} · ${scope.loanProduct ?? '—'}`}
      >
        <dl className="grid gap-2 text-xs sm:grid-cols-2">
          <div>
            <dt className="text-slate-500">Priority</dt>
            <dd className="font-medium text-slate-900">{scope.priority ?? '—'}</dd>
          </div>
          {scope.version != null ? (
            <div>
              <dt className="text-slate-500">Version</dt>
              <dd className="font-medium text-slate-900">{scope.version}</dd>
            </div>
          ) : null}
          <div>
            <dt className="text-slate-500">Amount band</dt>
            <dd className="font-mono text-slate-900">{amountRange}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Status</dt>
            <dd className="font-medium text-slate-900">{scope.active === false ? 'Inactive' : 'Active / draft'}</dd>
          </div>
          {scope.geography ? (
            <div className="sm:col-span-2">
              <dt className="text-slate-500">Geography</dt>
              <dd className="text-slate-900">{scope.geography}</dd>
            </div>
          ) : null}
        </dl>
      </MapCard>

      <FlowArrow label="When application matches" />

      {legacyPolicy ? (
        <>
          <MapCard tone="default" title="Policy gates" subtitle="Bureau / KYC / amount checks on the rule set">
            <ul className="space-y-1.5 text-xs text-slate-700">
              <li>
                Min bureau score: <span className="font-mono font-medium">{legacyPolicy.minBureau || '—'}</span>
              </li>
              <li>
                Max loan amount: <span className="font-mono font-medium">{legacyPolicy.maxLoan || '—'}</span>
              </li>
              <li>Require KYC success: {legacyPolicy.requireKyc === false ? 'No' : 'Yes'}</li>
              <li>
                Default decision:{' '}
                <span className="rounded bg-slate-100 px-1.5 py-0.5 font-medium">{legacyPolicy.decision || '—'}</span>
              </li>
            </ul>
          </MapCard>
          <FlowArrow label="Then evaluate hard rules" />
        </>
      ) : (
        <FlowArrow label="Evaluate hard rules first" />
      )}

      <MapCard
        tone="hard"
        title="Hard rules"
        subtitle={
          hardRules.length
            ? `${hardRules.length} gate${hardRules.length === 1 ? '' : 's'} — force REJECT or MANUAL_REVIEW`
            : 'No hard rules configured'
        }
      >
        {hardRules.length === 0 ? (
          <p className="text-xs text-slate-500">Applications continue to scoring / thresholds without hard stops.</p>
        ) : (
          <div className="space-y-2">
            {hardRules.map((rule, idx) => (
              <div key={rule.id || idx} className="rounded-lg border border-rose-100 bg-white/80 p-2.5 text-xs">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-semibold text-slate-900">
                    {rule.source} / {rule.parameter}
                  </span>
                  <span className="rounded bg-rose-100 px-1.5 py-0.5 text-[10px] font-semibold text-rose-800">
                    → {rule.decision}
                  </span>
                  <DepBadge dependsOn={rule.dependsOn} />
                </div>
                <p className="mt-1 text-slate-600">{describeCondition(rule.condition)}</p>
                {rule.message ? <p className="mt-1 text-[11px] text-slate-500">{rule.message}</p> : null}
                {(rule.dependsOn?.conditions?.length ?? 0) > 0 ? (
                  <ul className="mt-2 space-y-1 border-t border-rose-50 pt-2">
                    {rule.dependsOn!.conditions.map((c, i) => (
                      <li key={i} className="text-[11px] text-slate-600">
                        If {c.source}/{c.parameter}: {describeCondition(c.condition)}
                      </li>
                    ))}
                  </ul>
                ) : null}
                <FormulaChip formula={rule.formula} />
              </div>
            ))}
          </div>
        )}
      </MapCard>

      <FlowArrow label={kind === 'scorecard' ? 'Then score parameters' : 'Optional scorecard-style rows'} />

      <MapCard
        tone="score"
        title={kind === 'scorecard' ? 'Score parameters' : 'Scorecard rule rows'}
        subtitle={
          scoreParams.length || (legacyPolicy?.scorecardRows?.length ?? 0)
            ? 'Weighted matches contribute points toward the normalized score'
            : 'No weighted parameters configured'
        }
      >
        {scoreParams.length > 0 ? (
          <div className="space-y-2">
            {scoreParams.map((p, idx) => (
              <div key={p.id || idx} className="rounded-lg border border-emerald-100 bg-white/80 p-2.5 text-xs">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-semibold text-slate-900">
                    {p.source} / {p.parameter}
                  </span>
                  <span className="rounded bg-emerald-100 px-1.5 py-0.5 text-[10px] font-medium text-emerald-900">
                    wt {p.weight ?? '—'} · score {p.score ?? '—'}
                  </span>
                  <DepBadge dependsOn={p.dependsOn} />
                </div>
                <p className="mt-1 text-slate-600">{describeCondition(p.condition)}</p>
                <FormulaChip formula={p.formula} />
              </div>
            ))}
          </div>
        ) : legacyPolicy?.scorecardRows?.length ? (
          <ul className="space-y-1.5 text-xs">
            {legacyPolicy.scorecardRows.map((r, i) => (
              <li key={i} className="rounded border border-emerald-100 bg-white/70 px-2 py-1.5">
                <span className="font-medium">{r.parameter}</span> · weight {r.weight} · {r.mode} · approve ≥{' '}
                {r.approveAt || '—'} · manual ≥ {r.manualAt || '—'}
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-xs text-slate-500">No scoring parameters on this configuration.</p>
        )}
      </MapCard>

      {(thresholds || kind === 'scorecard') && (
        <>
          <FlowArrow label="Normalize score → decision" />
          <MapCard tone="decision" title="Decision thresholds" subtitle="Normalized score as % of maximum points">
            <div className="flex flex-wrap gap-3 text-xs">
              <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2">
                <div className="text-[10px] font-medium uppercase text-emerald-800">Auto-approve</div>
                <div className="mt-0.5 font-mono text-base font-semibold text-emerald-950">
                  ≥ {thresholds?.approveMin ?? '—'}%
                </div>
              </div>
              <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2">
                <div className="text-[10px] font-medium uppercase text-amber-800">Manual review</div>
                <div className="mt-0.5 font-mono text-base font-semibold text-amber-950">
                  ≥ {thresholds?.manualMin ?? '—'}%
                </div>
              </div>
              <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2">
                <div className="text-[10px] font-medium uppercase text-rose-800">Below bands</div>
                <div className="mt-0.5 text-sm font-semibold text-rose-950">Reject / policy default</div>
              </div>
            </div>
          </MapCard>
        </>
      )}
    </div>
  )
}
