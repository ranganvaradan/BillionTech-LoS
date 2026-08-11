import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  approveLifecyclePolicy,
  createLifecycleVersion,
  getLifecycleHistory,
  getLifecycleSettings,
  retireLifecyclePolicy,
  saveLifecycleDraft,
  scheduleLifecyclePolicy,
  submitLifecycleReview,
  type StagingPolicyStudio,
} from '@/api/creditIntelligence'
import { ApiError } from '@/api/http'
import { CiSection, CiTechnicalDetails } from '@/components/creditIntelligence/CiSection'

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

const PROGRESS = ['DRAFT', 'IN REVIEW', 'APPROVED', 'SCHEDULED', 'ACTIVE'] as const

type NavTab = 'scope' | 'rules' | 'tests' | 'approvals' | 'lifecycle'

/**
 * POLICY-LIFECYCLE-FIX-1 — one primary next action, clear blockers, no silent no-ops.
 */
export function CiPolicyLifecycleTab({
  documentId,
  busy,
  setBusy,
  onError,
  onSessionRefresh,
  onNavigateTab,
  session,
}: {
  documentId: string
  busy: boolean
  setBusy: (v: boolean) => void
  onError: (msg: string | null) => void
  onSessionRefresh?: (next?: StagingPolicyStudio) => void
  onNavigateTab?: (tab: NavTab) => void
  session?: StagingPolicyStudio | null
}) {
  const [settings, setSettings] = useState<Record<string, unknown> | null>(null)
  const [history, setHistory] = useState<unknown[]>([])
  const [loading, setLoading] = useState(true)
  const [feedback, setFeedback] = useState<string | null>(null)
  const [products, setProducts] = useState('DIGILEAP')
  const [effectiveFrom, setEffectiveFrom] = useState('2026-09-01')
  const [effectiveUntil, setEffectiveUntil] = useState('')
  const [reason, setReason] = useState('')
  const [confirmApprove, setConfirmApprove] = useState(false)
  const [confirmRetire, setConfirmRetire] = useState(false)
  const [moreOpen, setMoreOpen] = useState(false)

  const reload = async () => {
    setLoading(true)
    onError(null)
    try {
      const s = await getLifecycleSettings(documentId)
      setSettings(s)
      const hdr = asRecord(s.policySettings)
      const productsVal = asList(hdr.products)
      if (productsVal.length) setProducts(productsVal.map(String).join(', '))
      if (hdr.effectiveFrom) setEffectiveFrom(String(hdr.effectiveFrom).slice(0, 10))
      if (hdr.effectiveUntil) setEffectiveUntil(String(hdr.effectiveUntil).slice(0, 10))
      if (hdr.reasonForChange) setReason(String(hdr.reasonForChange))
      const hist = await getLifecycleHistory(documentId)
      setHistory(asList(hist.history))
    } catch (e) {
      onError(e instanceof ApiError ? e.message : 'Could not load policy settings')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void reload()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [documentId])

  const body = () => ({
    products: products
      .split(',')
      .map((p) => p.trim().toUpperCase())
      .filter(Boolean),
    effectiveFrom: effectiveFrom || null,
    effectiveUntil: effectiveUntil || null,
    reasonForChange: reason || null,
  })

  const run = async (
    fn: () => Promise<StagingPolicyStudio | Record<string, unknown>>,
    okMessage?: string,
  ) => {
    setBusy(true)
    onError(null)
    setFeedback(null)
    try {
      const data = await fn()
      const rec = asRecord(data)
      const life = asRecord(rec.lifecycle)
      if (Object.keys(life).length) setSettings(life)
      else if (rec.policySettings || rec.primaryAction) setSettings(data as Record<string, unknown>)
      const msg = String(rec.message ?? life.message ?? okMessage ?? 'Done')
      const newDoc = String(
        asRecord(rec.policyHeader).documentId ?? life.documentId ?? rec.documentId ?? '',
      )
      onSessionRefresh?.(data as StagingPolicyStudio)
      if (newDoc && newDoc !== documentId) {
        setFeedback(`${msg} — opened new draft version.`)
      } else {
        setFeedback(msg)
        await reload()
      }
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : 'Lifecycle action failed'
      onError(msg)
      setFeedback(null)
    } finally {
      setBusy(false)
      setConfirmApprove(false)
      setConfirmRetire(false)
    }
  }

  const hdr = asRecord(settings?.policySettings ?? asRecord(session).policySettings)
  const status = String(settings?.businessStatus ?? hdr.status ?? 'DRAFT')
  const progressCurrent = String(settings?.progressCurrent ?? status)
    .toUpperCase()
    .replace('_', ' ')
  const primary = asRecord(settings?.primaryAction)
  const readinessItems = asList(settings?.readinessItems)
  const blockerDetails = asList(settings?.blockerDetails)
  const approvals = asRecord(settings?.approvals)
  const cmApproval = asRecord(approvals.creditManager)
  const checkerApproval = asRecord(approvals.checker)
  const nextActor = String(settings?.nextActorMessage ?? '')
  const secondary = asList(settings?.secondaryActions)
  const shadow = asRecord(settings?.shadowBoundary)
  const scopeSummary = asRecord(settings?.scopeSummary)

  const ruleCount = useMemo(() => {
    const rules = asList(asRecord(session).underwritingRules)
    const counts = asRecord(asRecord(session).counts)
    return rules.length || Number(counts.underwritingRules ?? counts.rulesTotal ?? 0) || 0
  }, [session])

  const progressIndex = Math.max(
    0,
    PROGRESS.findIndex((s) => s === progressCurrent || (s === 'IN REVIEW' && progressCurrent.includes('REVIEW'))),
  )

  if (loading && !settings) {
    return <p className="text-sm text-slate-600">Loading policy status…</p>
  }

  const firePrimary = () => {
    const code = String(primary.code ?? '')
    if (code === 'GO_APPROVALS_CM' || code === 'GO_APPROVALS_CHECKER') {
      onNavigateTab?.('approvals')
      setFeedback(
        code === 'GO_APPROVALS_CHECKER'
          ? 'Open Maker-checker (Policy details) for Checker approval, then return here to Approve Policy.'
          : 'Open Maker-checker (Policy details) for Credit Manager approval, then return here.',
      )
      return
    }
    if (code === 'SUBMIT_FOR_REVIEW') {
      void run(() => submitLifecycleReview(documentId, body()), 'Policy submitted for review.')
      return
    }
    if (code === 'APPROVE_POLICY') {
      setConfirmApprove(true)
      return
    }
    if (code === 'SCHEDULE_POLICY') {
      void run(
        () =>
          scheduleLifecyclePolicy(documentId, {
            ...body(),
            businessDate: effectiveFrom || '2026-09-01',
          }),
        'Policy scheduled.',
      )
      return
    }
    if (code === 'CREATE_NEW_VERSION') {
      void run(
        () => createLifecycleVersion(documentId, { reasonForChange: reason || 'New version' }),
        'New draft version created.',
      )
      return
    }
    setFeedback(String(primary.disabledReason ?? 'No action available in this status.'))
  }

  const readyForNext = readinessItems.length
    ? readinessItems.every((raw) => Boolean(asRecord(raw).ok))
    : Boolean(settings?.readyForNextStep)

  return (
    <div className="space-y-4" data-testid="policy-lifecycle-tab">
      {feedback ? (
        <div
          className="rounded-lg border border-sky-200 bg-sky-50 px-4 py-2 text-sm text-sky-950"
          data-testid="lifecycle-feedback"
        >
          {feedback}
        </div>
      ) : null}

      {/* Status + progress */}
      <section className="rounded-xl border border-slate-200 bg-white px-4 py-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Current status</p>
            <h2 className="text-xl font-semibold text-slate-900" data-testid="lifecycle-status">
              {progressCurrent}
            </h2>
            <p className="mt-1 text-sm text-slate-700">{nextActor || '—'}</p>
            {scopeSummary.appliesTo ? (
              <p className="mt-1 text-sm text-slate-600">
                Scope: {String(scopeSummary.appliesTo)}
              </p>
            ) : null}
          </div>
          <div className="text-right text-sm text-slate-600">
            <div>{String(hdr.policyName ?? 'Policy')}</div>
            <div>Version {String(hdr.policyVersion ?? 'v1')}</div>
            <div>{ruleCount} underwriting rules</div>
          </div>
        </div>

        <ol className="mt-4 flex flex-wrap gap-2" data-testid="lifecycle-progress">
          {PROGRESS.map((step, i) => {
            const active = i === progressIndex
            const done = i < progressIndex
            return (
              <li
                key={step}
                className={`rounded-full px-3 py-1 text-xs font-semibold ${
                  active
                    ? 'bg-slate-900 text-white'
                    : done
                      ? 'bg-emerald-100 text-emerald-900'
                      : 'bg-slate-100 text-slate-500'
                }`}
              >
                {step}
              </li>
            )
          })}
        </ol>
      </section>

      {/* Readiness */}
      <section className="rounded-xl border border-slate-200 bg-white px-4 py-4">
        <h3 className="text-sm font-semibold text-slate-900">
          {readyForNext ? 'Ready for next step' : 'Not ready for next step'}
        </h3>
        <ul className="mt-2 space-y-1.5 text-sm" data-testid="lifecycle-readiness">
          {readinessItems.map((raw, i) => {
            const item = asRecord(raw)
            const ok = Boolean(item.ok)
            return (
              <li key={i} className="flex flex-wrap items-center gap-2">
                <span className={ok ? 'text-emerald-700' : 'text-amber-800'}>{ok ? '✓' : '•'}</span>
                <span className={ok ? 'text-slate-800' : 'text-amber-950'}>
                  {ok ? String(item.label) : String(item.whenMissing ?? item.label)}
                </span>
                {!ok && item.tab ? (
                  <button
                    type="button"
                    className="text-xs font-semibold text-sky-800 underline"
                    onClick={() => onNavigateTab?.(String(item.tab) as NavTab)}
                  >
                    Go to {String(item.tab)}
                  </button>
                ) : null}
              </li>
            )
          })}
        </ul>
        {blockerDetails.length > 0 && !readyForNext ? (
          <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950">
            <p className="font-semibold">Resolve before continuing</p>
            <ul className="mt-1 list-disc pl-5">
              {blockerDetails.map((raw, i) => {
                const b = asRecord(raw)
                const category = b.category ? String(b.category) : ''
                const action = b.action ? String(b.action) : ''
                const ruleName = b.ruleName ? String(b.ruleName) : ''
                const reason = b.reason ? String(b.reason) : String(b.message ?? '')
                return (
                  <li key={String(b.blockerKey ?? i)}>
                    {category ? (
                      <span className="mr-1 text-[10px] font-semibold uppercase tracking-wide text-amber-800">
                        {category}
                      </span>
                    ) : null}
                    {ruleName ? <span className="font-medium">{ruleName}: </span> : null}
                    {reason}{' '}
                    {action ? <span className="text-amber-900">[{action}]</span> : null}{' '}
                    {b.tab ? (
                      <button
                        type="button"
                        className="font-semibold text-sky-900 underline"
                        onClick={() => onNavigateTab?.(String(b.tab) as NavTab)}
                      >
                        Open {String(b.tab)}
                      </button>
                    ) : null}
                  </li>
                )
              })}
            </ul>
          </div>
        ) : null}
      </section>

      {/* Approvals */}
      <section className="rounded-xl border border-slate-200 bg-white px-4 py-4">
        <h3 className="text-sm font-semibold text-slate-900">Approvals</h3>
        <dl className="mt-2 grid gap-2 text-sm sm:grid-cols-2">
          <div className="rounded border border-slate-100 px-3 py-2">
            <dt className="text-xs text-slate-500">Credit Manager</dt>
            <dd className="font-medium text-slate-900">{String(cmApproval.status ?? 'Pending')}</dd>
          </div>
          <div className="rounded border border-slate-100 px-3 py-2">
            <dt className="text-xs text-slate-500">Checker</dt>
            <dd className="font-medium text-slate-900">{String(checkerApproval.status ?? 'Pending')}</dd>
          </div>
        </dl>
        <button
          type="button"
          className="mt-2 text-sm font-semibold text-sky-800 underline"
          onClick={() => onNavigateTab?.('approvals')}
        >
          Open Maker-checker
        </button>
      </section>

      {/* Schedule fields when relevant */}
      {['APPROVED', 'SCHEDULED'].includes(progressCurrent) ? (
        <CiSection title="Schedule" description="Effective dates for this policy version.">
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="text-sm">
              <span className="text-slate-600">Effective from</span>
              <input
                type="date"
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                value={effectiveFrom}
                onChange={(e) => setEffectiveFrom(e.target.value)}
                disabled={busy}
                data-testid="lifecycle-effective-from"
              />
            </label>
            <label className="text-sm">
              <span className="text-slate-600">Effective until (optional)</span>
              <input
                type="date"
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                value={effectiveUntil}
                onChange={(e) => setEffectiveUntil(e.target.value)}
                disabled={busy}
              />
            </label>
          </div>
          <p className="mt-2 text-xs text-slate-500">
            Products come from Scope ({products || 'not set'}). Business Active is reached when the
            evaluation date is on/after effective from — production underwriting stays disabled.
          </p>
        </CiSection>
      ) : null}

      {/* Primary action */}
      <section className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-4">
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Next action</p>
        <div className="mt-2 flex flex-wrap items-center gap-2">
          <button
            type="button"
            disabled={busy || primary.enabled === false}
            className="bt-btn bt-btn-primary bt-btn-sm disabled:opacity-50"
            data-testid="lifecycle-primary-action"
            onClick={() => firePrimary()}
          >
            {String(primary.label ?? '—')}
          </button>
          {secondary
            .filter((raw) => !asRecord(raw).more)
            .map((raw) => {
              const s = asRecord(raw)
              return (
                <button
                  key={String(s.code)}
                  type="button"
                  disabled={busy}
                  className="bt-btn bt-btn-secondary bt-btn-sm"
                  onClick={() => {
                    if (s.code === 'CREATE_NEW_VERSION') {
                      void run(
                        () =>
                          createLifecycleVersion(documentId, {
                            reasonForChange: reason || 'New version',
                          }),
                        'New draft version created.',
                      )
                    }
                  }}
                >
                  {String(s.label)}
                </button>
              )
            })}
          {secondary.some((raw) => asRecord(raw).more) ? (
            <div className="relative">
              <button
                type="button"
                className="bt-btn bt-btn-secondary bt-btn-sm"
                onClick={() => setMoreOpen((v) => !v)}
              >
                More…
              </button>
              {moreOpen ? (
                <div className="absolute z-10 mt-1 min-w-[10rem] rounded border border-slate-200 bg-white p-2 shadow">
                  {secondary
                    .filter((raw) => asRecord(raw).more)
                    .map((raw) => {
                      const s = asRecord(raw)
                      return (
                        <button
                          key={String(s.code)}
                          type="button"
                          className="block w-full rounded px-2 py-1 text-left text-sm text-rose-800 hover:bg-rose-50"
                          onClick={() => {
                            setMoreOpen(false)
                            if (s.code === 'RETIRE_POLICY') setConfirmRetire(true)
                          }}
                        >
                          {String(s.label)}
                        </button>
                      )
                    })}
                </div>
              ) : null}
            </div>
          ) : null}
        </div>
        {primary.enabled === false && primary.disabledReason ? (
          <p className="mt-2 text-sm text-amber-900" data-testid="lifecycle-primary-blocked">
            {String(primary.disabledReason)}
          </p>
        ) : null}
        {primary.hint ? <p className="mt-1 text-xs text-slate-500">{String(primary.hint)}</p> : null}
        <p className="mt-3 text-xs text-slate-500">
          Save Draft stays in the page header while the policy is editable — it is not a governance
          transition.
        </p>
      </section>

      {/* Approved / operational summary */}
      {['APPROVED', 'SCHEDULED', 'ACTIVE'].includes(progressCurrent) ? (
        <section
          className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-4"
          data-testid="policy-approved-summary"
        >
          <h3 className="text-base font-semibold text-emerald-950">
            {progressCurrent === 'APPROVED'
              ? Boolean(cmApproval.approved) && Boolean(checkerApproval.approved)
                ? 'Policy approved'
                : 'Approval in progress'
              : `Policy ${progressCurrent.toLowerCase()}`}
          </h3>
          <dl className="mt-2 grid gap-1 text-sm text-emerald-950 sm:grid-cols-2">
            <div>
              <dt className="text-xs text-emerald-800">Policy</dt>
              <dd>{String(hdr.policyName ?? '—')}</dd>
            </div>
            <div>
              <dt className="text-xs text-emerald-800">Version</dt>
              <dd>{String(hdr.policyVersion ?? '—')}</dd>
            </div>
            <div>
              <dt className="text-xs text-emerald-800">Scope</dt>
              <dd>{String(scopeSummary.appliesTo ?? '—')}</dd>
            </div>
            <div>
              <dt className="text-xs text-emerald-800">Rules</dt>
              <dd>{ruleCount}</dd>
            </div>
            <div>
              <dt className="text-xs text-emerald-800">Effective from</dt>
              <dd>{String(hdr.effectiveFrom ?? effectiveFrom ?? '—')}</dd>
            </div>
            <div>
              <dt className="text-xs text-emerald-800">Approvals</dt>
              <dd>
                CM {String(cmApproval.status ?? '—')} · Checker {String(checkerApproval.status ?? '—')}
              </dd>
            </div>
          </dl>
          {['APPROVED', 'SCHEDULED', 'ACTIVE'].includes(progressCurrent) ? (
            <div className="mt-3 flex flex-wrap gap-2">
              <Link
                to="/underwriting-scorecards"
                className="bt-btn bt-btn-primary bt-btn-sm"
                data-testid="create-scorecard-handoff"
              >
                Create / Open Scorecard
              </Link>
            </div>
          ) : null}
        </section>
      ) : null}

      <CiSection title="Version history">
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="text-slate-500">
              <tr>
                <th className="py-1 pr-3">Version</th>
                <th className="py-1 pr-3">Status</th>
                <th className="py-1 pr-3">Effective From</th>
                <th className="py-1 pr-3">Approved By</th>
                <th className="py-1 pr-3">Reason</th>
              </tr>
            </thead>
            <tbody>
              {history.length === 0 ? (
                <tr>
                  <td className="py-2 text-slate-500" colSpan={5}>
                    No history rows yet.
                  </td>
                </tr>
              ) : (
                history.map((raw, i) => {
                  const row = asRecord(raw)
                  return (
                    <tr key={i} className="border-t border-slate-100">
                      <td className="py-1.5 pr-3">{String(row.policyVersion ?? '—')}</td>
                      <td className="py-1.5 pr-3">{String(row.status ?? '—')}</td>
                      <td className="py-1.5 pr-3">{String(row.effectiveFrom ?? '—')}</td>
                      <td className="py-1.5 pr-3">{String(row.approvedBy ?? '—')}</td>
                      <td className="py-1.5 pr-3">{String(row.reasonForChange ?? '—')}</td>
                    </tr>
                  )
                })
              )}
            </tbody>
          </table>
        </div>
      </CiSection>

      <CiTechnicalDetails title="Advanced / technical">
        <pre className="whitespace-pre-wrap text-xs text-slate-600">
          {JSON.stringify(
            {
              allowCanonicalAuthority: false,
              businessStatus: status,
              productionAuthority: 'DISABLED',
              shadowBoundary: shadow,
              actions: settings?.actions,
              products,
            },
            null,
            2,
          )}
        </pre>
      </CiTechnicalDetails>

      {/* Approve confirm */}
      {confirmApprove ? (
        <div
          className="fixed inset-0 z-40 flex items-center justify-center bg-slate-900/40 p-4"
          data-testid="approve-confirm"
        >
          <div className="w-full max-w-md rounded-xl bg-white p-4 shadow-lg">
            <h3 className="text-lg font-semibold text-slate-900">Approve this policy version?</h3>
            <dl className="mt-3 space-y-1 text-sm text-slate-700">
              <div>
                <span className="text-slate-500">Policy: </span>
                {String(hdr.policyName ?? '—')}
              </div>
              <div>
                <span className="text-slate-500">Version: </span>
                {String(hdr.policyVersion ?? 'v1')}
              </div>
              <div>
                <span className="text-slate-500">Scope: </span>
                {String(scopeSummary.appliesTo ?? '—')}
              </div>
              <div>
                <span className="text-slate-500">Rules: </span>
                {ruleCount}
              </div>
              <div>
                <span className="text-slate-500">Test: </span>
                {readinessItems.some((r) => String(asRecord(r).label).toLowerCase().includes('test') && asRecord(r).ok)
                  ? 'Completed'
                  : 'Check readiness'}
              </div>
            </dl>
            <div className="mt-4 flex flex-wrap justify-end gap-2">
              <button
                type="button"
                className="bt-btn bt-btn-secondary bt-btn-sm"
                onClick={() => setConfirmApprove(false)}
              >
                Cancel
              </button>
              <button
                type="button"
                className="bt-btn bt-btn-primary bt-btn-sm"
                disabled={busy}
                onClick={() =>
                  void run(() => approveLifecyclePolicy(documentId, body()), 'Policy APPROVED.')
                }
              >
                Approve
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {confirmRetire ? (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="w-full max-w-md rounded-xl bg-white p-4 shadow-lg">
            <h3 className="text-lg font-semibold text-slate-900">Retire this policy version?</h3>
            <p className="mt-2 text-sm text-slate-700">
              This is a high-impact lifecycle action. The version becomes RETIRED.
            </p>
            <div className="mt-4 flex flex-wrap justify-end gap-2">
              <button
                type="button"
                className="bt-btn bt-btn-secondary bt-btn-sm"
                onClick={() => setConfirmRetire(false)}
              >
                Cancel
              </button>
              <button
                type="button"
                className="bt-btn bt-btn-primary bt-btn-sm"
                disabled={busy}
                onClick={() => void run(() => retireLifecyclePolicy(documentId, {}), 'Policy retired.')}
              >
                Retire
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {/* Keep Save Draft callable from Versions for editable drafts (persistence only) */}
      {!['APPROVED', 'SCHEDULED', 'ACTIVE', 'RETIRED', 'SUPERSEDED'].includes(progressCurrent) ? (
        <div className="flex justify-end">
          <button
            type="button"
            disabled={busy}
            className="text-sm font-semibold text-slate-600 underline"
            data-testid="lifecycle-save-draft"
            onClick={() => void run(() => saveLifecycleDraft(documentId, body()), 'Draft saved.')}
          >
            Save Draft settings
          </button>
        </div>
      ) : null}
    </div>
  )
}
