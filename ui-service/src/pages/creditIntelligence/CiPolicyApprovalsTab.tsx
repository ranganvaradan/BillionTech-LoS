import { useEffect, useState } from 'react'
import {
  buildDraftPolicy,
  compareDraftVersions,
  getApprovalsContext,
  invalidateApprovalDemo,
  submitCheckerApproval,
  submitCreditManagerApproval,
  type ApprovalsContext,
} from '@/api/creditIntelligence'
import { ApiError } from '@/api/http'
import { CiExecutiveSummary, CiSection } from '@/components/creditIntelligence/CiSection'
import { businessOutcomeLabel } from '@/lib/creditIntelligence/businessLexicon'

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

function toneClass(tone: string): string {
  if (tone === 'GREEN') return 'text-emerald-800'
  if (tone === 'RED') return 'text-rose-800'
  return 'text-amber-800'
}

function stageClass(state: string): string {
  if (state === 'DONE') return 'bg-emerald-100 text-emerald-900'
  if (state === 'CURRENT') return 'bg-sky-600 text-white'
  return 'bg-slate-100 text-slate-500'
}

export function CiPolicyApprovalsTab({
  documentId,
  busy,
  setBusy,
  onError,
  onSessionRefresh,
  demoActor,
  setDemoActor,
  prospectDemoMode,
}: {
  documentId: string
  busy: boolean
  setBusy: (v: boolean) => void
  onError: (msg: string | null) => void
  onSessionRefresh?: () => void
  demoActor: string
  setDemoActor: (v: string) => void
  prospectDemoMode: boolean
}) {
  const [ctx, setCtx] = useState<ApprovalsContext | null>(null)
  const [comments, setComments] = useState('')
  const [confirmCm, setConfirmCm] = useState(false)
  const [diff, setDiff] = useState<Record<string, unknown> | null>(null)
  const [loading, setLoading] = useState(true)

  const reload = async () => {
    setLoading(true)
    onError(null)
    try {
      setCtx(await getApprovalsContext(documentId))
    } catch (e) {
      onError(e instanceof ApiError ? e.message : 'Could not load approvals')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void reload()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [documentId])

  const run = async (fn: () => Promise<ApprovalsContext | Record<string, unknown>>) => {
    setBusy(true)
    onError(null)
    try {
      const data = await fn()
      setCtx(data as ApprovalsContext)
      onSessionRefresh?.()
    } catch (e) {
      onError(e instanceof ApiError ? e.message : 'Approval action failed')
      try {
        setCtx(await getApprovalsContext(documentId))
      } catch {
        /* ignore */
      }
    } finally {
      setBusy(false)
    }
  }

  if (loading && !ctx) {
    return <p className="text-sm text-slate-600">Loading approvals…</p>
  }
  if (!ctx) {
    return <p className="text-sm text-slate-600">Approvals unavailable.</p>
  }

  const stages = asList(ctx.stages)
  const checklist = asList(ctx.readinessChecklist)
  const blocking = asList(ctx.blockingItems)
  const nonBlocking = asList(ctx.nonBlockingItems)
  const summary = asRecord(ctx.draftSummary)
  const actors = asList(ctx.demoActors)

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-amber-300 bg-amber-50 px-4 py-2 text-sm font-semibold text-amber-950">
        {String(ctx.draftBanner ?? 'Draft only — not active in production lending')}
      </div>

      {ctx.approvalInvalidated ? (
        <div className="rounded-lg border border-rose-300 bg-rose-50 px-4 py-3 text-sm text-rose-950">
          <div className="font-semibold">Approval invalidated because the policy changed.</div>
          <p className="mt-1">{String(ctx.invalidationMessage ?? 'Re-review required.')}</p>
        </div>
      ) : null}

      <CiExecutiveSummary title="What should I do next?">
        <p>
          {blocking.length > 0
            ? `Resolve ${blocking.length} remaining issue${blocking.length === 1 ? '' : 's'} before Credit Manager approval.`
            : ctx.canBuildDraft
              ? 'Ready to build the draft policy package after approvals.'
              : 'Complete Credit Manager and Checker approvals, then build the draft package.'}
        </p>
      </CiExecutiveSummary>

      <CiSection title="Approval progress" description="Live approval stages for this draft policy.">
        <ol className="flex flex-wrap gap-2">
          {stages.map((raw) => {
            const s = asRecord(raw)
            return (
              <li
                key={String(s.key)}
                className={`rounded-full px-3 py-1 text-xs font-semibold ${stageClass(String(s.state ?? ''))}`}
              >
                {String(s.label ?? s.key)}
              </li>
            )
          })}
        </ol>
      </CiSection>

      <CiSection title="Policy readiness">
        <ul className="space-y-1 text-sm">
          {checklist.map((raw) => {
            const c = asRecord(raw)
            return (
              <li key={String(c.key)} className={`font-medium ${toneClass(String(c.tone ?? ''))}`}>
                {String(c.display ?? c.label)}
              </li>
            )
          })}
        </ul>
      </CiSection>

      <div className="grid gap-4 lg:grid-cols-2">
        <CiSection title="Resolve remaining issues">
          {blocking.length === 0 ? (
            <p className="text-sm text-emerald-800">No blocking items.</p>
          ) : (
            <ul className="list-disc space-y-1 pl-5 text-sm text-rose-900">
              {blocking.map((raw, i) => {
                const b = asRecord(raw)
                return <li key={i}>{String(b.label ?? '')}</li>
              })}
            </ul>
          )}
        </CiSection>
        <CiSection title="Non-blocking notes">
          <ul className="list-disc space-y-1 pl-5 text-sm text-slate-700">
            {nonBlocking.map((raw, i) => {
              const b = asRecord(raw)
              return <li key={i}>{String(b.label ?? '')}</li>
            })}
          </ul>
        </CiSection>
      </div>

      {!prospectDemoMode ? (
        <CiSection title="Demo reviewer" description="Staging-only role switch for maker-checker.">
          <select
            className="rounded border border-slate-300 px-2 py-1 text-sm"
            value={demoActor}
            onChange={(e) => setDemoActor(e.target.value)}
          >
            {actors.map((raw) => {
              const a = asRecord(raw)
              return (
                <option key={String(a.id)} value={String(a.id)}>
                  {String(a.displayName ?? a.id)}
                </option>
              )
            })}
          </select>
          <p className="mt-2 text-xs text-slate-500">
            Maker-checker remains enforced. Same user cannot be final checker.
          </p>
        </CiSection>
      ) : null}

      <CiSection title="Credit Manager approval">
        {!confirmCm ? (
          <button
            type="button"
            className="bt-btn bt-btn-primary"
            disabled={busy || !ctx.canApproveCreditManager}
            onClick={() => setConfirmCm(true)}
          >
            Approve AI understanding
          </button>
        ) : (
          <div className="space-y-3 rounded-lg border border-sky-200 bg-sky-50 px-4 py-3 text-sm">
            <p className="font-semibold text-sky-950">Confirmation summary</p>
            <p>
              You are approving the AI understanding of clauses, verified business mappings, business measure
              definitions, proposed business rules, expected test outcomes, and simulation understanding for draft
              purposes only.
            </p>
            <textarea
              className="w-full rounded border border-slate-300 px-2 py-1 text-sm"
              rows={2}
              placeholder="Comments (optional)"
              value={comments}
              onChange={(e) => setComments(e.target.value)}
            />
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                className="bt-btn bt-btn-primary"
                disabled={busy}
                onClick={() =>
                  void (async () => {
                    await run(() =>
                      submitCreditManagerApproval(documentId, {
                        reviewer: demoActor || 'credit_manager',
                        comments: comments || undefined,
                      }),
                    )
                    setConfirmCm(false)
                  })()
                }
              >
                Confirm approval
              </button>
              <button type="button" className="bt-btn bt-btn-secondary" onClick={() => setConfirmCm(false)}>
                Cancel
              </button>
            </div>
          </div>
        )}
        {ctx.creditManagerApproved ? (
          <p className="mt-2 text-sm font-semibold text-emerald-800">Credit Manager ✓</p>
        ) : null}
      </CiSection>

      <CiSection title="Send for checker">
        <button
          type="button"
          className="bt-btn bt-btn-primary"
          disabled={busy || !ctx.canApproveChecker}
          onClick={() =>
            void run(() =>
              submitCheckerApproval(documentId, {
                reviewer: demoActor === 'credit_manager' ? 'policy_checker' : demoActor || 'policy_checker',
                comments: comments || undefined,
              }),
            )
          }
        >
          Approve draft policy (Checker)
        </button>
        {ctx.checkerApproved ? (
          <p className="mt-2 text-sm font-semibold text-emerald-800">Checker ✓</p>
        ) : null}
        <p className="mt-2 text-xs text-slate-500">
          Does not activate production. Never publishes underwriting authority.
        </p>
      </CiSection>

      <CiSection title="Draft policy package">
        <button
          type="button"
          className="bt-btn bt-btn-primary"
          disabled={busy || !ctx.canBuildDraft}
          onClick={() =>
            void run(() =>
              buildDraftPolicy(documentId, {
                createdBy: demoActor || 'credit_manager',
              }),
            )
          }
        >
          Build draft policy
        </button>
        {!ctx.canBuildDraft ? (
          <ul className="mt-2 list-disc pl-5 text-sm text-rose-800">
            {asList(ctx.draftBuildBlockers).map((b, i) => (
              <li key={i}>{String(b)}</li>
            ))}
          </ul>
        ) : null}

        {summary.policyName ? (
          <div className="mt-4 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm">
            <div className="text-xs font-semibold uppercase text-emerald-900">Draft Policy Summary</div>
            <dl className="mt-2 grid gap-1 sm:grid-cols-2">
              <div>
                <dt className="text-slate-500">Policy</dt>
                <dd className="font-semibold">{String(summary.policyName)}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Version</dt>
                <dd className="font-semibold">{String(summary.versionLabel)}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Rules</dt>
                <dd>{String(summary.rules)}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Products</dt>
                <dd>{String(summary.products)}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Ambiguities</dt>
                <dd>{String(summary.blockingAmbiguities)} blocking</dd>
              </div>
              <div>
                <dt className="text-slate-500">Tests</dt>
                <dd>
                  {String(summary.testsApproved)} / {String(summary.testsTotal)} approved
                </dd>
              </div>
              <div>
                <dt className="text-slate-500">Applications simulated</dt>
                <dd>{String(summary.applicationsSimulated ?? '—')}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Approval</dt>
                <dd>
                  Credit Manager {summary.creditManagerApproved ? '✓' : '—'} · Checker{' '}
                  {summary.checkerApproved ? '✓' : '—'}
                </dd>
              </div>
              <div>
                <dt className="text-slate-500">Status</dt>
                <dd className="font-semibold">{businessOutcomeLabel(summary.status)}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Production</dt>
                <dd className="font-semibold text-rose-800">{String(summary.production)}</dd>
              </div>
            </dl>
          </div>
        ) : null}

        <div className="mt-3 flex flex-wrap gap-2">
          <a
            className="bt-btn bt-btn-secondary bt-btn-sm"
            href={`/api/v1/internal/credit-intelligence/staging-demo/policy-studio/documents/${encodeURIComponent(documentId)}/export/summary.html`}
            target="_blank"
            rel="noreferrer"
          >
            Export summary
          </a>
          <a
            className="bt-btn bt-btn-secondary bt-btn-sm"
            href={`/api/v1/internal/credit-intelligence/staging-demo/policy-studio/documents/${encodeURIComponent(documentId)}/export/rules.csv`}
            target="_blank"
            rel="noreferrer"
          >
            Export rules
          </a>
          <button
            type="button"
            className="bt-btn bt-btn-secondary bt-btn-sm"
            disabled={busy}
            onClick={() =>
              void (async () => {
                setBusy(true)
                try {
                  setDiff(await compareDraftVersions(documentId))
                } catch (e) {
                  onError(e instanceof ApiError ? e.message : 'Diff unavailable')
                } finally {
                  setBusy(false)
                }
              })()
            }
          >
            Compare draft versions
          </button>
          {!prospectDemoMode ? (
            <button
              type="button"
              className="bt-btn bt-btn-secondary bt-btn-sm"
              disabled={busy || !summary.policyName}
              onClick={() => void run(() => invalidateApprovalDemo(documentId, { reviewer: demoActor }))}
            >
              Demo: material edit invalidation
            </button>
          ) : null}
        </div>

        {diff ? (
          <div className="mt-3 rounded border border-slate-200 bg-white px-3 py-2 text-sm">
            <div className="font-semibold">{String(diff.label ?? 'Version comparison')}</div>
            {!diff.available ? (
              <p className="mt-1 text-slate-600">{String(diff.message ?? '')}</p>
            ) : (
              <ul className="mt-2 list-disc pl-5">
                {asList(diff.businessDiff).map((raw, i) => {
                  const d = asRecord(raw)
                  return (
                    <li key={i}>
                      <strong>{String(d.type)}</strong>
                      {d.detail && d.detail !== '—' ? ` — ${String(d.detail)}` : ''}
                    </li>
                  )
                })}
              </ul>
            )}
          </div>
        ) : null}
      </CiSection>
    </div>
  )
}
