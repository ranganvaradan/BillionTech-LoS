import { useMemo, useState } from 'react'
import {
  CiEmptyState,
  CiExecutiveSummary,
  CiSection,
  CiTechnicalDetails,
} from '@/components/creditIntelligence/CiSection'
import { decisionPolicyDomainLabel } from '@/lib/creditIntelligence/businessLexicon'
import { CiCapabilityCataloguePanel } from '@/pages/creditIntelligence/CiCapabilityCataloguePanel'
import { CiParameterResolverPanel } from '@/pages/creditIntelligence/CiParameterResolverPanel'
import {
  CiDataCalcResolutionPanel,
  resolveKindForGroup,
  type DataCalcResolveKind,
} from '@/pages/creditIntelligence/CiDataCalcResolutionPanel'
import { CiRuleAuthoringPanel } from '@/pages/creditIntelligence/CiRuleAuthoringPanel'
import {
  cmStatusLabel,
  filterParameterGroups,
  groupDataCalculations,
  itemKindLabel,
  type CmParamStatus,
} from '@/pages/creditIntelligence/policyDataCalculationGroups'

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

function cmStatusChip(status: CmParamStatus): string {
  switch (status) {
    case 'READY':
      return 'bg-emerald-100 text-emerald-900'
    case 'NEEDS_YOUR_INPUT':
      return 'bg-amber-100 text-amber-950'
    case 'NEEDS_CONFIGURATION':
      return 'bg-orange-100 text-orange-950'
    case 'MANUAL_INPUT':
      return 'bg-indigo-100 text-indigo-900'
    case 'NOT_CURRENTLY_AVAILABLE':
      return 'bg-slate-200 text-slate-700'
    default:
      return 'bg-slate-100 text-slate-700'
  }
}

function statusChip(status: string): string {
  switch (status) {
    case 'Accepted':
    case 'Approved':
    case 'Edited':
      return 'bg-emerald-100 text-emerald-900'
    case 'Ready':
      return 'bg-sky-100 text-sky-900'
    case 'Manual Input':
    case 'Manual Review':
      return 'bg-indigo-100 text-indigo-900'
    case 'Data requirement':
    case 'Metric adjustment':
    case 'Non-underwriting':
      return 'bg-slate-100 text-slate-700'
    case 'Ignored':
      return 'bg-slate-200 text-slate-700'
    case 'Policy requirement':
      return 'bg-slate-100 text-slate-800'
    case 'Deleted':
      return 'bg-slate-100 text-slate-500 line-through'
    case 'Blocked':
    case 'Needs your input':
    case 'Needs Review':
    case 'Needs configuration':
    case 'Unavailable':
      return 'bg-amber-100 text-amber-900'
    default:
      return 'bg-amber-100 text-amber-900'
  }
}

const GROUP_ORDER = [
  'KYC & Eligibility',
  'Bureau',
  'Banking',
  'Financial / Income',
  'GST / Business',
  'Collateral',
  'Risk / Exceptions',
  'Limit & Pricing',
  'Decision / Review',
  'Data requirements',
  'Metric adjustments',
  'Product / Configuration',
  'Documents',
  'Portfolio Controls',
  'Servicing',
  'Other policy content',
  'Narrative / Excluded',
  'Credit Rules',
] as const

type StatusFilter = 'ALL' | 'NEEDS_REVIEW' | 'ACCEPTED' | 'MANUAL_INPUT' | 'DATA_REQ' | 'IGNORED'

function VisualLogic({ visual }: { visual: Record<string, unknown> }) {
  const kind = String(visual.kind ?? 'SIMPLE')
  if (kind === 'EXCEPTION_ALL') {
    const conditions = asList(visual.conditions)
    return (
      <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-3 text-sm">
        <div className="font-semibold text-slate-900">{String(visual.title ?? 'Overdue Exception Eligibility')}</div>
        <div className="mt-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
          {String(visual.subtitle ?? 'Allow only if ALL:')}
        </div>
        <ul className="mt-2 space-y-1.5">
          {conditions.map((c, i) => {
            const row = asRecord(c)
            const structured = row.parameter != null
            return (
              <li key={i} className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5 text-slate-800">
                <span className="min-w-[10rem] font-medium">{structured ? String(row.parameter) : String(c)}</span>
                {structured ? (
                  <>
                    <span className="text-slate-500">{String(row.operator ?? '')}</span>
                    <span>{String(row.value ?? '')}</span>
                  </>
                ) : null}
                {row.needsDefinition || row.badge ? (
                  <span className="rounded bg-amber-100 px-1.5 py-0.5 text-xs font-semibold text-amber-900">
                    {String(row.badge ?? 'Needs definition')}
                  </span>
                ) : null}
              </li>
            )
          })}
        </ul>
        <div className="mt-2 text-xs text-slate-600">If not satisfied: {String(visual.then ?? 'Reject')}</div>
      </div>
    )
  }
  if (kind === 'COMPOUND') {
    const conditions = asList(visual.conditions)
    return (
      <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-3 text-sm">
        <div className="text-xs font-semibold uppercase text-slate-500">IF all of</div>
        <ul className="mt-2 space-y-2">
          {conditions.map((c, i) => {
            const row = asRecord(c)
            return (
              <li key={i} className="flex flex-wrap items-center gap-2">
                <span className="rounded bg-white px-2 py-1 font-medium text-slate-800">
                  {String(row.left ?? '—')}
                </span>
                <span className="font-semibold text-slate-600">{String(row.operator ?? '')}</span>
                <span className="rounded bg-white px-2 py-1 font-medium text-slate-800">
                  {String(row.right ?? '—')}
                </span>
              </li>
            )
          })}
        </ul>
        <div className="mt-2 font-semibold text-slate-900">THEN {String(visual.then ?? 'Fail')}</div>
      </div>
    )
  }
  if (kind === 'BRANCH') {
    const iff = asRecord(visual.if)
    return (
      <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-3 text-sm">
        <div className="text-xs font-semibold uppercase text-slate-500">IF</div>
        <div className="mt-1 flex flex-wrap items-center gap-2">
          <span className="rounded bg-white px-2 py-1 font-medium">{String(iff.left ?? '—')}</span>
          <span className="font-semibold">{String(iff.operator ?? '')}</span>
          <span className="rounded bg-white px-2 py-1 font-medium">{String(iff.right ?? '—')}</span>
        </div>
        <div className="mt-2">THEN {String(visual.then ?? '—')}</div>
        <div className="mt-1">ELSE {String(visual.else ?? '—')}</div>
      </div>
    )
  }
  const iff = asRecord(visual.if)
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-3 text-sm">
      <div className="text-xs font-semibold uppercase text-slate-500">IF</div>
      <div className="mt-1 flex flex-wrap items-center gap-2">
        <span className="rounded bg-white px-2 py-1 font-medium text-slate-900">
          {String(iff.left ?? '—')}
        </span>
        <span className="font-semibold text-slate-700">{String(iff.operator ?? '')}</span>
        <span className="rounded bg-white px-2 py-1 font-medium text-slate-900">
          {String(iff.right ?? '—')}
        </span>
      </div>
      <div className="mt-2 font-semibold text-slate-900">THEN {String(visual.then ?? 'Fail')}</div>
    </div>
  )
}

function matchesStatusFilter(status: string, filter: StatusFilter): boolean {
  switch (filter) {
    case 'ALL':
      return status !== 'Deleted'
    case 'NEEDS_REVIEW':
      return (
        status === 'Needs your input' ||
        status === 'Needs Review' ||
        status === 'Blocked' ||
        status === 'Needs configuration' ||
        status === 'Unavailable'
      )
    case 'ACCEPTED':
      return (
        status === 'Ready' ||
        status === 'Accepted' ||
        status === 'Edited' ||
        status === 'Approved'
      )
    case 'MANUAL_INPUT':
      return status === 'Manual Input' || status === 'Manual Review'
    case 'DATA_REQ':
      return status === 'Data requirement' || status === 'Metric adjustment' || status === 'Non-underwriting'
    case 'IGNORED':
      return status === 'Ignored'
    default:
      return true
  }
}

export function CiPolicyRulesTab({
  cards,
  dataAndCalculations = [],
  otherPolicyContent = [],
  policyDataResolutions = {},
  busy,
  setBusy,
  onError,
  onReview,
  onViewTests,
  onSaveDraft,
  onActivationCheck,
  onCatalogueChanged,
  onSession,
  documentId,
  ingestionBinding,
  prospectDemoMode = false,
  compactShell = false,
}: {
  cards: unknown[]
  dataAndCalculations?: unknown[]
  otherPolicyContent?: unknown[]
  policyDataResolutions?: Record<string, unknown>
  busy: boolean
  setBusy?: (v: boolean) => void
  onError?: (msg: string | null) => void
  onReview: (ruleId: string, body: Record<string, unknown>) => Promise<void>
  onViewTests?: () => void
  onSaveDraft?: () => void
  onActivationCheck?: () => void
  onCatalogueChanged?: (session?: unknown) => void
  onSession?: (session: Record<string, unknown>) => void
  documentId?: string | null
  ingestionBinding?: Record<string, unknown> | null
  prospectDemoMode?: boolean
  /** POLICY-UX-SHELL-1 — start with rules, not a large summary block. */
  compactShell?: boolean
}) {
  const [clauseOpen, setClauseOpen] = useState<Record<string, boolean>>({})
  const [editOpen, setEditOpen] = useState<Record<string, boolean>>({})
  const [manualOpen, setManualOpen] = useState<Record<string, boolean>>({})
  const [manualLabel, setManualLabel] = useState<Record<string, string>>({})
  const [manualType, setManualType] = useState<Record<string, string>>({})
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')
  const [search, setSearch] = useState('')
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({})
  const [undoStack, setUndoStack] = useState<{ id: string; prev: Record<string, unknown> }[]>([])
  const [authoringOpen, setAuthoringOpen] = useState(false)
  const [catalogueOpen, setCatalogueOpen] = useState(false)
  const [catalogueEdit, setCatalogueEdit] = useState<{
    ruleId: string
    businessCapabilityId: string
    parameters?: Record<string, unknown>
    failureTreatment?: string
  } | null>(null)
  const [resolver, setResolver] = useState<{
    ruleId: string
    operand: Record<string, unknown>
  } | null>(null)
  const [dataCalcResolver, setDataCalcResolver] = useState<{
    ruleId: string
    parameterId: string
    title: string
    kind: DataCalcResolveKind
  } | null>(null)
  const [dataCalcFilter, setDataCalcFilter] = useState<
    'ALL' | 'READY' | 'NEEDS_INPUT' | 'NEEDS_CONFIGURATION' | 'MANUAL' | 'UNAVAILABLE'
  >('ALL')
  const [dataCalcSource, setDataCalcSource] = useState('ALL')

  const parameterGroups = useMemo(
    () => groupDataCalculations(dataAndCalculations, cards, policyDataResolutions),
    [dataAndCalculations, cards, policyDataResolutions],
  )
  const visibleParameterGroups = useMemo(
    () => filterParameterGroups(parameterGroups, dataCalcFilter, dataCalcSource === 'ALL' ? undefined : dataCalcSource),
    [parameterGroups, dataCalcFilter, dataCalcSource],
  )
  const dataCalcSources = useMemo(() => {
    const s = new Set(parameterGroups.map((g) => g.source).filter(Boolean))
    return ['ALL', ...Array.from(s)]
  }, [parameterGroups])
  const adjustmentCount = useMemo(
    () => parameterGroups.reduce((n, g) => n + g.policyAdjustments.length, 0),
    [parameterGroups],
  )

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase()
    return cards.filter((c) => {
      const r = asRecord(c)
      const status = String(r.status ?? 'Needs your input')
      if (!matchesStatusFilter(status, statusFilter)) return false
      if (!q) return true
      const hay = `${r.ruleName ?? ''} ${r.businessRule ?? ''} ${r.sourceClause ?? ''}`.toLowerCase()
      return hay.includes(q)
    })
  }, [cards, statusFilter, search])

  const groups = useMemo(() => {
    const map = new Map<string, unknown[]>()
    for (const c of filtered) {
      const g = String(asRecord(c).businessGroup ?? asRecord(c).dataFamily ?? 'Credit Rules')
      if (!map.has(g)) map.set(g, [])
      map.get(g)!.push(c)
    }
    const ordered: { name: string; items: unknown[] }[] = []
    for (const name of GROUP_ORDER) {
      if (map.has(name)) {
        ordered.push({ name, items: map.get(name)! })
        map.delete(name)
      }
    }
    for (const [name, items] of map) {
      ordered.push({ name, items })
    }
    return ordered
  }, [filtered])

  const totals = useMemo(() => {
    const all = cards.map((c) => String(asRecord(c).status ?? ''))
    return {
      total: cards.length,
      ready: all.filter((s) => s === 'Ready' || s === 'Accepted' || s === 'Edited' || s === 'Approved').length,
      needs: all.filter((s) => s === 'Needs your input' || s === 'Needs Review' || s === 'Blocked').length,
      manual: all.filter((s) => s === 'Manual Input' || s === 'Manual Review').length,
      dataReq: all.filter((s) => s === 'Data requirement' || s === 'Metric adjustment').length,
      ignored: all.filter((s) => s === 'Ignored').length,
      accepted: all.filter((s) => s === 'Accepted' || s === 'Edited' || s === 'Approved').length,
    }
  }, [cards])

  const readyToAccept = useMemo(
    () =>
      cards.filter((c) => {
        const r = asRecord(c)
        if (String(r.status) !== 'Ready' || r.platformGuardrail) return false
        if (r.acceptAllEligible === true) return true
        if (r.dataRequirementOnly || r.metricAdjustment) return false
        if (r.capabilityConflict || r.potentialDuplicate || r.NEEDS_INPUT) return false
        const avail = String(r.dataAvailability ?? '')
        if (avail === 'UNAVAILABLE' || avail === 'NEEDS_CONFIGURATION') return false
        return !r.blockedReason
      }),
    [cards],
  )

  const acceptAllReady = async () => {
    if (readyToAccept.length === 0) return
    if (
      !window.confirm(
        `Accept ${readyToAccept.length} ready rule${readyToAccept.length === 1 ? '' : 's'}? Data requirements, metric adjustments, manual, conflict and unavailable items are skipped.`,
      )
    ) {
      return
    }
    for (const c of readyToAccept) {
      const id = String(asRecord(c).id ?? '')
      if (!id) continue
      await onReview(id, {
        uiAction: 'ACCEPT',
        reason: 'Accepted via Accept all ready rules',
      })
    }
  }

  return (
    <div className="space-y-3">
      {compactShell ? (
        <>
          <div className="flex flex-wrap items-end justify-between gap-2">
            <div>
              <h2 className="text-lg font-semibold text-slate-900">Rules</h2>
              <p className="text-sm text-slate-600">
                {totals.total} underwriting rules · {totals.ready} ready · {totals.needs} need input
                {parameterGroups.length > 0 ? (
                  <span className="text-slate-500">
                    {' '}
                    · {parameterGroups.length} parameters · {adjustmentCount} adjustments
                  </span>
                ) : null}
                {otherPolicyContent.length > 0 ? (
                  <span className="text-slate-500">
                    {' '}
                    · {otherPolicyContent.length} other policy content
                  </span>
                ) : null}
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                className="bt-btn bt-btn-primary bt-btn-sm"
                data-testid="add-rule"
                onClick={() => setAuthoringOpen((v) => !v)}
              >
                {authoringOpen ? 'Hide add rule' : '+ Add rule'}
              </button>
              <button
                type="button"
                className="bt-btn bt-btn-secondary bt-btn-sm"
                onClick={() => {
                  setCatalogueEdit(null)
                  setCatalogueOpen((v) => !v)
                }}
              >
                {catalogueOpen ? 'Hide catalogue' : 'Browse catalogue'}
              </button>
            </div>
          </div>
          {authoringOpen && documentId && setBusy && onError && onSession ? (
            <CiRuleAuthoringPanel
              documentId={documentId}
              busy={busy}
              setBusy={setBusy}
              onError={onError}
              onSession={(data) => {
                onSession(data)
                setAuthoringOpen(false)
              }}
              onClose={() => setAuthoringOpen(false)}
            />
          ) : null}
        </>
      ) : (
        <>
          <CiExecutiveSummary
            title="Review Rules"
            nextAction={
              <div className="flex flex-wrap gap-2">
                {onSaveDraft ? (
                  <button
                    type="button"
                    className="bt-btn bt-btn-primary bt-btn-sm"
                    disabled={busy}
                    onClick={onSaveDraft}
                  >
                    Save Draft
                  </button>
                ) : null}
                {onViewTests ? (
                  <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" onClick={onViewTests}>
                    Test Policy
                  </button>
                ) : null}
                {onActivationCheck ? (
                  <button
                    type="button"
                    className="bt-btn bt-btn-secondary bt-btn-sm"
                    onClick={onActivationCheck}
                  >
                    Activation Check
                  </button>
                ) : null}
              </div>
            }
          >
            <p className="text-sm text-slate-700">
              <strong>{totals.ready}</strong> ready · <strong>{totals.needs}</strong> need your input ·{' '}
              <strong>{dataAndCalculations.length}</strong> data &amp; calculations ·{' '}
              <strong>{totals.manual}</strong> manual · <strong>{totals.ignored}</strong> ignored by you
              <span className="text-slate-500"> ({totals.total} underwriting rules)</span>
            </p>
            {ingestionBinding ? (
              <p className="mt-2 text-sm text-slate-700">
                <strong>{String(ingestionBinding.totalClauses ?? totals.total)}</strong> clauses interpreted ·{' '}
                <strong>{String(ingestionBinding.existingAutomatedCapabilities ?? 0)}</strong> existing automated ·{' '}
                <strong>{String(ingestionBinding.manualInputs ?? 0)}</strong> manual inputs ·{' '}
                <strong>{String(ingestionBinding.manualReviews ?? 0)}</strong> manual review ·{' '}
                <strong>{String(ingestionBinding.productConfiguration ?? 0)}</strong> product/config ·{' '}
                <strong>{String(ingestionBinding.documentRequirements ?? 0)}</strong> documents ·{' '}
                <strong>{String(ingestionBinding.portfolioControls ?? 0)}</strong> portfolio ·{' '}
                <strong>{String(ingestionBinding.servicingOrNarrative ?? 0)}</strong> servicing/narrative
              </p>
            ) : null}
            <p className="mt-1 text-xs text-slate-500">
              Save Draft anytime — unresolved items, documents, portfolio and servicing do not block a draft.
            </p>
          </CiExecutiveSummary>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              className="bt-btn bt-btn-secondary bt-btn-sm"
              onClick={() => {
                setCatalogueEdit(null)
                setCatalogueOpen((v) => !v)
              }}
            >
              {catalogueOpen ? 'Hide capability catalogue' : 'Browse / Add Rule'}
            </button>
          </div>
        </>
      )}
      <CiCapabilityCataloguePanel
        open={catalogueOpen}
        onClose={() => {
          setCatalogueOpen(false)
          setCatalogueEdit(null)
        }}
        documentId={documentId}
        busy={busy}
        editCapability={catalogueEdit}
        onAdded={(session) => {
          setCatalogueOpen(false)
          setCatalogueEdit(null)
          onCatalogueChanged?.(session)
        }}
      />

      <div className="flex flex-wrap items-center gap-2">
        {(
          (compactShell
            ? ([
                ['ALL', 'All'],
                ['NEEDS_REVIEW', 'Need input'],
                ['ACCEPTED', 'Ready'],
              ] as const)
            : ([
                ['ALL', 'All'],
                ['NEEDS_REVIEW', 'Needs review'],
                ['ACCEPTED', 'Accepted'],
                ['MANUAL_INPUT', 'Manual input'],
                ['DATA_REQ', 'Data / metrics'],
                ['IGNORED', 'Ignored by you'],
              ] as const)
          )
        ).map(([id, label]) => (
          <button
            key={id}
            type="button"
            onClick={() => setStatusFilter(id)}
            className={`rounded-full px-3 py-1 text-xs font-semibold ${
              statusFilter === id ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
            }`}
          >
            {label}
          </button>
        ))}
        <input
          type="search"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search rules…"
          className="ml-auto min-w-[12rem] flex-1 rounded border border-slate-300 px-3 py-1.5 text-sm"
        />
        {readyToAccept.length > 0 ? (
          <button
            type="button"
            disabled={busy}
            className="bt-btn bt-btn-secondary bt-btn-sm"
            onClick={() => void acceptAllReady()}
          >
            Accept all ready ({readyToAccept.length})
          </button>
        ) : null}
        {undoStack.length > 0 ? (
          <button
            type="button"
            disabled={busy}
            className="bt-btn bt-btn-secondary bt-btn-sm"
            onClick={() => {
              const last = undoStack[undoStack.length - 1]
              setUndoStack((s) => s.slice(0, -1))
              void onReview(last.id, {
                uiAction: 'ACCEPT',
                reason: 'Undo exclude — restored to draft',
                humanChanges: { undo: true },
              })
            }}
          >
            Undo last delete
          </button>
        ) : null}
      </div>

      {groups.length === 0 ? (
        <CiEmptyState
          title="No rules in this filter"
          detail="Try All, or clear search. After ingest, extracted rules appear here for review."
        />
      ) : null}

      {groups.map(({ name, items }) => {
        const open = !collapsed[name]
        return (
          <CiSection
            key={name}
            title={`${name} (${items.length})`}
            description="Review each rule: Accept, Edit, Ignore for now, Delete, or Manual input."
          >
            <button
              type="button"
              className="mb-3 text-xs font-semibold text-sky-800 underline"
              onClick={() => setCollapsed((p) => ({ ...p, [name]: !p[name] }))}
            >
              {open ? 'Collapse' : 'Expand'}
            </button>
            {open ? (
              <ul className="space-y-4">
                {items.map((raw) => {
                  const r = asRecord(raw)
                  const id = String(r.id ?? r.systemRuleId ?? '')
                  const status = String(r.status ?? 'Needs your input')
                  const visual = asRecord(r.visualLogic)
                  const isTerminal = status === 'Deleted'
                  const needsInput = status === 'Needs your input' || status === 'Blocked' || status === 'Needs Review'

                  return (
                    <li
                      key={id}
                      className={`rounded-xl border bg-white p-4 shadow-sm ${
                        isTerminal ? 'border-slate-100 opacity-60' : 'border-slate-200'
                      }`}
                    >
                      <div className="flex flex-wrap items-start justify-between gap-2">
                        <div className="min-w-0 flex-1">
                          <div className="text-lg font-semibold text-slate-900">
                            {String(r.ruleName ?? 'Business rule')}
                          </div>
                          {String(visual.kind ?? '') === 'EXCEPTION_ALL' ? (
                            <div className="mt-2">
                              <VisualLogic visual={visual} />
                            </div>
                          ) : (
                            <p className="mt-1 text-base font-medium text-slate-800">
                              {String(
                                r.businessRule ||
                                  [r.parameterName, r.operatorValueLabel].filter(Boolean).join(' ') ||
                                  '—',
                              )}
                            </p>
                          )}
                          {r.period ? (
                            <p className="mt-1 text-sm text-slate-600">Period: {String(r.period)}</p>
                          ) : null}
                          {r.failureConditionLabel ? (
                            <p className="mt-1 text-sm text-slate-600">{String(r.failureConditionLabel)}</p>
                          ) : null}
                        </div>
                        <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${statusChip(status)}`}>
                          {status === 'Needs Review' ? 'Needs your input' : status === 'Ignored' ? 'Ignored by you' : status}
                        </span>
                      </div>

                      <div className="mt-3 grid gap-2 text-sm sm:grid-cols-2">
                        <div>
                          <div className="text-xs text-slate-500">Evaluated from</div>
                          <div className="font-medium text-slate-900">
                            {String(r.evaluatedFrom ?? r.dataSource ?? '—')}
                          </div>
                          <div className="text-xs text-slate-600">
                            {String(r.dataAvailabilityLabel ?? '')}
                          </div>
                        </div>
                        <div>
                          <div className="text-xs text-slate-500">
                            {r.failureConditionLabel ? 'Treatment' : 'If rule fails'}
                          </div>
                          <div className="font-medium text-slate-900">
                            {String(r.treatment ?? r.failureTreatmentDisplay ?? r.resultOnFailure ?? '—')}
                          </div>
                        </div>
                      </div>

                      {asList(r.operands).length > 0 ? (
                        <div className="mt-3 space-y-2" data-testid="rule-operands">
                          {asList(r.operands).map((opRaw, oi) => {
                            const op = asRecord(opRaw)
                            const unresolved = Boolean(op.unresolved)
                            const unavailable = Boolean(op.unavailable)
                            return (
                              <div
                                key={oi}
                                className={`rounded-lg border px-3 py-2 text-sm ${
                                  unresolved
                                    ? 'border-amber-200 bg-amber-50'
                                    : unavailable
                                      ? 'border-slate-300 bg-slate-50'
                                      : 'border-slate-200 bg-white'
                                }`}
                              >
                                <div className="font-medium text-slate-900">
                                  {String(op.businessName ?? op.label ?? 'Parameter')}
                                </div>
                                {unresolved ? (
                                  <div className="mt-1 flex flex-wrap items-center gap-2">
                                    <span className="text-amber-900">Not yet mapped</span>
                                    <button
                                      type="button"
                                      disabled={busy}
                                      className="bt-btn bt-btn-secondary bt-btn-sm"
                                      data-testid={`resolve-parameter-${String(op.operandKey ?? oi)}`}
                                      onClick={() => setResolver({ ruleId: id, operand: op })}
                                    >
                                      Resolve parameter
                                    </button>
                                  </div>
                                ) : unavailable ? (
                                  <p className="mt-1 text-slate-700">
                                    {String(
                                      op.message ??
                                        'Understood, but unavailable from current data sources.',
                                    )}
                                  </p>
                                ) : (
                                  <div className="mt-1 text-slate-700">
                                    {String(op.evaluatedFrom ?? '—')} ·{' '}
                                    {String(op.availabilityLabel ?? op.resolutionState ?? '—')}
                                    {op.howCalculated ? (
                                      <details className="mt-1">
                                        <summary className="cursor-pointer text-xs font-medium text-slate-600">
                                          How calculated
                                        </summary>
                                        <p className="mt-1 text-xs text-slate-600">
                                          {String(op.howCalculated)}
                                        </p>
                                      </details>
                                    ) : null}
                                  </div>
                                )}
                              </div>
                            )
                          })}
                        </div>
                      ) : null}

                      {needsInput && r.blockedReason && asList(r.operands).length === 0 ? (
                        <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950">
                          {String(r.blockedReason)}
                          {asRecord(r.cleanDefinition).actions ? (
                            <div className="mt-2 flex flex-wrap gap-2">
                              <button
                                type="button"
                                disabled={busy}
                                className="bt-btn bt-btn-secondary bt-btn-sm"
                                onClick={() =>
                                  setResolver({
                                    ruleId: id,
                                    operand: {
                                      operandKey: 'clean_history',
                                      businessName: 'Clean credit history',
                                      unresolved: true,
                                    },
                                  })
                                }
                              >
                                Resolve parameter
                              </button>
                            </div>
                          ) : null}
                        </div>
                      ) : null}

                      {asRecord(r.howCalculated).calculation ? (
                        <details className="mt-3 rounded border border-slate-100 bg-slate-50 px-3 py-2 text-sm">
                          <summary className="cursor-pointer font-medium text-slate-800">How is this calculated?</summary>
                          <dl className="mt-2 space-y-1 text-slate-700">
                            <div><dt className="text-xs text-slate-500">Evaluated from</dt><dd>{String(asRecord(r.howCalculated).source ?? r.evaluatedFrom ?? '—')}</dd></div>
                            <div><dt className="text-xs text-slate-500">Parameter</dt><dd>{String(asRecord(r.howCalculated).metric ?? r.parameterName ?? '—')}</dd></div>
                            <div><dt className="text-xs text-slate-500">Calculation</dt><dd>{String(asRecord(r.howCalculated).calculation ?? '—')}</dd></div>
                            <div><dt className="text-xs text-slate-500">Period</dt><dd>{String(asRecord(r.howCalculated).assessmentPeriod ?? r.period ?? '—')}</dd></div>
                            <div><dt className="text-xs text-slate-500">Missing data</dt><dd>{String(asRecord(r.howCalculated).missingData ?? r.onMissing ?? '—')}</dd></div>
                          </dl>
                        </details>
                      ) : null}

                      {!isTerminal ? (
                        <div className="mt-4 flex flex-wrap items-center gap-2">
                          {status !== 'Data requirement' && status !== 'Metric adjustment' && status !== 'Non-underwriting' ? (
                          <button
                            type="button"
                            disabled={busy || status === 'Accepted' || status === 'Approved' || Boolean(r.platformGuardrail)}
                            className="bt-btn bt-btn-primary bt-btn-sm"
                            onClick={() =>
                              void onReview(id, {
                                uiAction: 'ACCEPT',
                                reason: 'Accepted by Credit Manager — matches intended policy',
                              })
                            }
                          >
                            Accept
                          </button>
                          ) : null}
                          <button
                            type="button"
                            disabled={busy || Boolean(r.platformGuardrail)}
                            className="bt-btn bt-btn-secondary bt-btn-sm"
                            data-testid={`edit-rule-${id}`}
                            onClick={() => setEditOpen((p) => ({ ...p, [id]: !p[id] }))}
                          >
                            Edit
                          </button>
                          <details className="relative">
                            <summary className="bt-btn bt-btn-secondary bt-btn-sm list-none cursor-pointer">
                              More…
                            </summary>
                            <div className="absolute z-10 mt-1 flex min-w-[10rem] flex-col gap-1 rounded border border-slate-200 bg-white p-2 shadow-md">
                          <button
                            type="button"
                            disabled={busy || status === 'Ignored' || status === 'Policy requirement'}
                            className="rounded px-2 py-1 text-left text-sm hover:bg-slate-50 disabled:opacity-50"
                            onClick={() =>
                              void onReview(id, {
                                uiAction: 'KEEP_AS_POLICY_REQUIREMENT',
                                reason: 'Kept as policy requirement — not an executable parameter',
                              })
                            }
                          >
                            Keep as policy requirement
                          </button>
                          <button
                            type="button"
                            disabled={busy || status === 'Ignored'}
                            className="rounded px-2 py-1 text-left text-sm hover:bg-slate-50 disabled:opacity-50"
                            onClick={() =>
                              void onReview(id, {
                                uiAction: 'IGNORE_FOR_AUTOMATION',
                                reason: 'Ignored for automation — source wording retained',
                              })
                            }
                          >
                            Ignore for automation
                          </button>
                          <button
                            type="button"
                            disabled={busy || status === 'Ignored'}
                            className="rounded px-2 py-1 text-left text-sm hover:bg-slate-50 disabled:opacity-50"
                            onClick={() =>
                              void onReview(id, {
                                uiAction: 'IGNORE',
                                reason: 'Ignored for now — retained in draft, not an activation blocker',
                              })
                            }
                          >
                            Ignore for now
                          </button>
                          <details className="rounded px-2 py-1">
                            <summary className="cursor-pointer text-sm text-slate-700">Reclassify…</summary>
                            <div className="mt-1 flex flex-col gap-1">
                              {(
                                [
                                  ['DOCUMENT_REQUIREMENT', 'Document requirement'],
                                  ['NARRATIVE', 'Narrative / informational'],
                                  ['DATA_REQUIREMENT', 'Data requirement'],
                                  ['NEW_AUTOMATABLE_RULE', 'Underwriting rule'],
                                ] as const
                              ).map(([code, label]) => (
                                <button
                                  key={code}
                                  type="button"
                                  disabled={busy}
                                  className="rounded px-2 py-1 text-left text-xs hover:bg-slate-50 disabled:opacity-50"
                                  onClick={() =>
                                    void onReview(id, {
                                      uiAction: 'RECLASSIFY',
                                      classification: code,
                                      reason: `Reclassified as ${label}`,
                                    })
                                  }
                                >
                                  {label}
                                </button>
                              ))}
                            </div>
                          </details>
                          <button
                            type="button"
                            disabled={busy || Boolean(r.platformGuardrail)}
                            className="rounded px-2 py-1 text-left text-sm text-rose-700 hover:bg-rose-50 disabled:opacity-50"
                            onClick={() => {
                              setUndoStack((s) => [...s, { id, prev: r }])
                              void onReview(id, {
                                uiAction: 'DELETE',
                                reason: 'Not part of the intended policy',
                              })
                            }}
                          >
                            Delete extracted item
                          </button>
                          <button
                            type="button"
                            disabled={busy}
                            className="rounded px-2 py-1 text-left text-sm hover:bg-slate-50 disabled:opacity-50"
                            onClick={() => {
                              setManualOpen((p) => ({ ...p, [id]: !p[id] }))
                              setManualLabel((p) => ({
                                ...p,
                                [id]: p[id] ?? String(r.ruleName ?? 'Manual input'),
                              }))
                              setManualType((p) => ({ ...p, [id]: p[id] ?? 'YES_NO' }))
                            }}
                          >
                            Manual input
                          </button>
                          <button
                            type="button"
                            className="rounded px-2 py-1 text-left text-sm hover:bg-slate-50"
                            onClick={() => setClauseOpen((prev) => ({ ...prev, [id]: !prev[id] }))}
                          >
                            From policy
                          </button>
                          <details className="rounded px-2 py-1">
                            <summary className="cursor-pointer text-sm text-slate-700">Advanced / technical details</summary>
                            <div className="mt-2 space-y-1 text-xs text-slate-600">
                              {r.systemRuleId ? <div>System id: {String(r.systemRuleId)}</div> : null}
                              {r.businessCapabilityId ? <div>Capability: {String(r.businessCapabilityId)}</div> : null}
                              {r.matchConfidence ? <div>Confidence: {String(r.matchConfidence)}</div> : null}
                              {r.capabilityBadge ? <div>Match: {String(r.capabilityBadge)}</div> : null}
                              {r.metricLineageTechnical ? (
                                <pre className="overflow-auto rounded bg-slate-100 p-2 text-[11px]">
                                  {JSON.stringify(r.metricLineageTechnical, null, 2)}
                                </pre>
                              ) : null}
                              {r.technicalExpression ? (
                                <pre className="overflow-auto rounded bg-slate-100 p-2 text-[11px]">
                                  {JSON.stringify(r.technicalExpression, null, 2)}
                                </pre>
                              ) : null}
                            </div>
                          </details>
                            </div>
                          </details>
                        </div>
                      ) : null}

                      {clauseOpen[id] ? (
                        <div className="mt-2 rounded border border-slate-100 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                          <div className="text-xs font-semibold uppercase text-slate-500">From policy</div>
                          <p className="mt-1">{String(r.fromPolicy ?? r.sourceClause ?? '—')}</p>
                        </div>
                      ) : null}

                      {!prospectDemoMode ? (
                        <CiTechnicalDetails title="Advanced / technical details" hidden={false}>
                          <div className="space-y-2 text-xs text-slate-600">
                            <div>Domain: {decisionPolicyDomainLabel(r.decisionDomain)}</div>
                            {r.systemRuleId ? <div>System id: {String(r.systemRuleId)}</div> : null}
                          </div>
                        </CiTechnicalDetails>
                      ) : null}

                      {editOpen[id] && documentId && setBusy && onError && onSession ? (
                        <div className="mt-3">
                          <CiRuleAuthoringPanel
                            documentId={documentId}
                            busy={busy}
                            setBusy={setBusy}
                            onError={onError}
                            replaceRuleId={id}
                            initialText={String(r.businessRule ?? r.sourceClause ?? '')}
                            onSession={(data) => {
                              onSession(data)
                              setEditOpen((p) => ({ ...p, [id]: false }))
                            }}
                            onClose={() => setEditOpen((p) => ({ ...p, [id]: false }))}
                          />
                        </div>
                      ) : null}

                      {manualOpen[id] ? (
                        <div className="mt-3 space-y-2 rounded-lg border border-indigo-200 bg-indigo-50/60 p-3">
                          <p className="text-xs text-indigo-950">
                            Manual input: an authorised user supplies a missing fact during application review.
                            Distinct from Manual review (human judgement on existing evidence).
                          </p>
                          <label className="block text-sm">
                            <span className="text-slate-600">Input label</span>
                            <input
                              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                              value={manualLabel[id] ?? ''}
                              onChange={(e) => setManualLabel((p) => ({ ...p, [id]: e.target.value }))}
                              disabled={busy}
                            />
                          </label>
                          <label className="block text-sm">
                            <span className="text-slate-600">Input type</span>
                            <select
                              className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                              value={manualType[id] ?? 'YES_NO'}
                              onChange={(e) => setManualType((p) => ({ ...p, [id]: e.target.value }))}
                              disabled={busy}
                            >
                              <option value="YES_NO">Yes / No</option>
                              <option value="DROPDOWN">Dropdown</option>
                              <option value="NUMBER">Number</option>
                              <option value="TEXT">Text</option>
                              <option value="DATE">Date</option>
                            </select>
                          </label>
                          <button
                            type="button"
                            disabled={busy}
                            className="bt-btn bt-btn-primary bt-btn-sm"
                            onClick={() =>
                              void onReview(id, {
                                uiAction: 'MANUAL_INPUT',
                                manualInputLabel: manualLabel[id],
                                manualInputType: manualType[id],
                                requiredActor: 'Credit Manager',
                                reason: 'Designated Manual Input',
                              }).then(() => setManualOpen((p) => ({ ...p, [id]: false })))
                            }
                          >
                            Save Manual Input
                          </button>
                        </div>
                      ) : null}

                    </li>
                  )
                })}
              </ul>
            ) : null}

            <div className="mt-3 border-t border-slate-100 pt-3">
              <button
                type="button"
                className="text-sm font-semibold text-sky-800"
                onClick={() => setAuthoringOpen(true)}
              >
                + Add rule
              </button>
            </div>
          </CiSection>
        )
      })}

      {parameterGroups.length > 0 ? (
        <CiSection
          title="Data & calculations"
          description="Parameters this policy needs — source, calculation, and policy-specific adjustments. Not underwriting decision rules."
        >
          <div className="mb-3 flex flex-wrap gap-2" data-testid="data-calc-filters">
            {(
              [
                ['ALL', 'All'],
                ['READY', 'Ready'],
                ['NEEDS_INPUT', 'Needs input'],
                ['NEEDS_CONFIGURATION', 'Needs configuration'],
                ['MANUAL', 'Manual'],
                ['UNAVAILABLE', 'Unavailable'],
              ] as const
            ).map(([id, label]) => (
              <button
                key={id}
                type="button"
                className={`rounded-full px-2.5 py-1 text-xs font-semibold ${
                  dataCalcFilter === id ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-700'
                }`}
                onClick={() => setDataCalcFilter(id)}
              >
                {label}
              </button>
            ))}
            <select
              className="rounded border border-slate-300 px-2 py-1 text-xs"
              value={dataCalcSource}
              onChange={(e) => setDataCalcSource(e.target.value)}
              aria-label="Filter by source"
            >
              {dataCalcSources.map((s) => (
                <option key={s} value={s}>
                  {s === 'ALL' ? 'All sources' : s}
                </option>
              ))}
            </select>
            <span className="self-center text-xs text-slate-500">
              {visibleParameterGroups.length} parameters · {adjustmentCount} adjustments
            </span>
          </div>
          <ul className="space-y-3" data-testid="data-calc-parameter-groups">
            {visibleParameterGroups.map((g) => (
              <li
                key={g.parameterId}
                className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm"
                data-parameter-id={g.parameterId}
              >
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <div className="font-semibold text-slate-900">{g.name}</div>
                    <p className="mt-0.5 text-xs text-slate-500">
                      {[g.source, g.type === 'DERIVED' ? 'Derived' : g.type === 'RAW' ? 'Raw' : g.type, g.period]
                        .filter(Boolean)
                        .join(' · ')}
                      {' · '}
                      {itemKindLabel(g.itemKind)}
                    </p>
                  </div>
                  <span
                    className={`rounded-full px-2 py-0.5 text-xs font-semibold ${cmStatusChip(g.cmStatus)}`}
                  >
                    {cmStatusLabel(g.cmStatus)}
                  </span>
                </div>
                {g.statusReason ? <p className="mt-1 text-xs text-slate-600">{g.statusReason}</p> : null}
                {g.usedFor ? (
                  <p className="mt-1 text-xs text-slate-600">Used for: {g.usedFor}</p>
                ) : null}
                {g.usedByRules.length > 0 ? (
                  <p className="mt-1 text-xs text-slate-600">
                    Used by {g.usedByRules.length} rule{g.usedByRules.length === 1 ? '' : 's'}:{' '}
                    {g.usedByRules.map((r) => r.name).join(', ')}
                  </p>
                ) : g.itemKind !== 'REPORT_ANALYST_INFORMATION' ? (
                  <p className="mt-1 text-xs text-slate-500">Not linked to an underwriting rule in this policy</p>
                ) : null}

                {g.howCalculated ? (
                  <details className="mt-2">
                    <summary className="cursor-pointer text-xs font-medium text-sky-800">How calculated</summary>
                    <div className="mt-1 space-y-0.5 text-xs text-slate-600">
                      {g.howCalculated.calculation ? (
                        <p>
                          <span className="font-medium">Base (enterprise): </span>
                          {String(g.howCalculated.calculation)}
                        </p>
                      ) : null}
                      {g.howCalculated.source ? <p>Source: {String(g.howCalculated.source)}</p> : null}
                      {g.howCalculated.assessmentPeriod ? (
                        <p>Window: {String(g.howCalculated.assessmentPeriod)}</p>
                      ) : null}
                      {g.howCalculated.missingData ? (
                        <p>Missing data: {String(g.howCalculated.missingData)}</p>
                      ) : null}
                      {g.howCalculated.note ? (
                        <p className="text-slate-500">{String(g.howCalculated.note)}</p>
                      ) : null}
                    </div>
                  </details>
                ) : null}

                {g.policyAdjustments.length > 0 ? (
                  <div className="mt-2 rounded border border-amber-100 bg-amber-50/60 px-2 py-2">
                    <p className="text-xs font-semibold text-amber-950">Policy-specific adjustments</p>
                    <p className="text-[11px] text-amber-900/80">
                      Scoped to this policy — does not change the enterprise Data &amp; Parameters definition.
                    </p>
                    <ul className="mt-1 space-y-1.5">
                      {g.policyAdjustments.map((a) => (
                        <li key={a.id} className="text-xs text-slate-800">
                          <div className="flex flex-wrap items-center justify-between gap-2">
                            <span className="font-medium">{a.title}</span>
                            <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${cmStatusChip(a.status)}`}>
                              {cmStatusLabel(a.status)}
                            </span>
                          </div>
                          {a.reason ? <p className="text-slate-600">{a.reason}</p> : null}
                        </li>
                      ))}
                    </ul>
                  </div>
                ) : null}

                {g.howDefined ? (
                  <div className="mt-2 rounded border border-emerald-100 bg-emerald-50/70 px-2 py-2 text-xs text-emerald-950">
                    <p className="font-semibold">How defined</p>
                    <p className="mt-0.5">{g.howDefined}</p>
                    {g.displayStatus ? (
                      <p className="mt-1 font-medium">{g.displayStatus}</p>
                    ) : null}
                    {g.executionImpact ? (
                      <p className="mt-0.5 text-[11px] text-emerald-900/80">
                        Activation impact: {g.executionImpact === 'NON_BLOCKING' ? 'Non-blocking' : 'Blocking when required by UW rules'}
                      </p>
                    ) : null}
                  </div>
                ) : null}

                {(g.cmStatus === 'NEEDS_YOUR_INPUT' || g.cmStatus === 'NEEDS_CONFIGURATION'
                  || (g.missingDefinition && Object.keys(g.missingDefinition).length > 0)) ? (
                  <div className="mt-2 rounded border border-sky-100 bg-sky-50 px-2 py-2 text-xs text-sky-950">
                    {!g.howDefined && g.missingDefinition ? (
                      <>
                        <p className="font-semibold">
                          {String(g.missingDefinition.question ?? 'Definition needed')}
                        </p>
                        {g.missingDefinition.hint ? (
                          <p className="mt-0.5 text-sky-900/80">{String(g.missingDefinition.hint)}</p>
                        ) : null}
                      </>
                    ) : null}
                    <div className="mt-2 flex flex-wrap gap-2">
                      <button
                        type="button"
                        className="bt-btn bt-btn-primary bt-btn-sm"
                        disabled={busy || !g.resolveRuleId}
                        data-testid={`data-calc-resolve-${g.parameterId}`}
                        onClick={() => {
                          if (!g.resolveRuleId) return
                          const isAdj = g.policyAdjustments.some((a) =>
                            a.sourceClause.toLowerCase().includes('bulk')
                            || a.sourceClause.toLowerCase().includes('10'))
                          setDataCalcResolver({
                            ruleId: g.resolveRuleId,
                            parameterId: g.parameterId,
                            title: g.name,
                            kind: resolveKindForGroup(
                              g.parameterId,
                              String(asRecord(g.missingDefinition).action ?? g.resolveAction ?? ''),
                              isAdj && g.parameterId === 'banking.avg_daily_balance_3m',
                            ),
                          })
                        }}
                      >
                        {g.resolveAction === 'CONFIGURE'
                          ? 'Configure calculation'
                          : g.resolveAction === 'RESOLVE'
                            ? 'Resolve parameter'
                            : 'Define'}
                      </button>
                      <button
                        type="button"
                        className="bt-btn bt-btn-secondary bt-btn-sm"
                        disabled={busy || !g.resolveRuleId}
                        data-testid={`data-calc-keep-${g.parameterId}`}
                        onClick={() => {
                          if (!g.resolveRuleId) return
                          void onReview(g.resolveRuleId, {
                            uiAction: 'KEEP_AS_POLICY_REQUIREMENT',
                            reason: 'Kept as policy requirement — not an executable parameter',
                          })
                        }}
                      >
                        Keep as policy requirement
                      </button>
                      <button
                        type="button"
                        className="bt-btn bt-btn-secondary bt-btn-sm"
                        disabled={busy || !g.resolveRuleId}
                        data-testid={`data-calc-ignore-${g.parameterId}`}
                        onClick={() => {
                          if (!g.resolveRuleId) return
                          void onReview(g.resolveRuleId, {
                            uiAction: 'IGNORE_FOR_AUTOMATION',
                            reason: 'Ignored for automation — source wording retained',
                          })
                        }}
                      >
                        Ignore for automation
                      </button>
                      {g.policyAdjustments.some((a) => a.status === 'NEEDS_CONFIGURATION') ? (
                        <button
                          type="button"
                          className="bt-btn bt-btn-secondary bt-btn-sm"
                          disabled={busy || !g.resolveRuleId}
                          onClick={() => {
                            const adj = g.policyAdjustments.find((a) => a.status === 'NEEDS_CONFIGURATION')
                            const rid = String(asRecord(adj?.raw).id ?? g.resolveRuleId ?? '')
                            if (!rid) return
                            setDataCalcResolver({
                              ruleId: rid,
                              parameterId: g.parameterId,
                              title: adj?.title ?? 'Policy adjustment',
                              kind: 'ADJUSTMENT',
                            })
                          }}
                        >
                          Configure adjustment
                        </button>
                      ) : null}
                    </div>
                  </div>
                ) : null}

                <details className="mt-2">
                  <summary className="cursor-pointer text-xs font-medium text-slate-600">
                    View source / Policy wording ({g.sourceClauses.length})
                  </summary>
                  <ul className="mt-1 space-y-1 text-xs text-slate-600">
                    {g.sourceClauses.map((c, i) => (
                      <li key={i} className="rounded bg-white px-2 py-1 border border-slate-100">
                        {c}
                      </li>
                    ))}
                  </ul>
                  <p className="mt-1 text-[11px] text-slate-400">{g.parameterId}</p>
                </details>
              </li>
            ))}
          </ul>
        </CiSection>
      ) : null}

      {otherPolicyContent.length > 0 ? (
        <CiSection
          title="Other policy content"
          description="Narrative, document/data requirements, servicing, portfolio, or clauses not used for underwriting. Not counted as underwriting rules."
        >
          <ul className="space-y-2">
            {otherPolicyContent.map((raw) => {
              const r = asRecord(raw)
              const id = String(r.id ?? r.systemRuleId ?? Math.random())
              return (
                <li key={id} className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="font-medium text-slate-900">
                      {String(r.ruleName ?? r.status ?? 'Policy content')}
                    </span>
                    <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${statusChip(String(r.status ?? ''))}`}>
                      {String(r.status ?? 'Other')}
                    </span>
                  </div>
                  <p className="mt-1 text-slate-700">{String(r.businessRule ?? r.sourceClause ?? '—')}</p>
                  {/* Already-classified data/calc items must never offer Replace with underwriting rule */}
                  {r.dataRequirementOnly ||
                  r.metricAdjustment ||
                  ['Data requirement', 'Metric adjustment', 'Non-underwriting'].includes(String(r.status ?? '')) ? null : (
                    <>
                      <div className="mt-2">
                        <button
                          type="button"
                          disabled={busy}
                          className="bt-btn bt-btn-secondary bt-btn-sm"
                          onClick={() => setEditOpen((p) => ({ ...p, [id]: !p[id] }))}
                        >
                          Replace with underwriting rule
                        </button>
                      </div>
                      {editOpen[id] && documentId && setBusy && onError && onSession ? (
                        <div className="mt-3">
                          <CiRuleAuthoringPanel
                            documentId={documentId}
                            busy={busy}
                            setBusy={setBusy}
                            onError={onError}
                            replaceRuleId={id}
                            initialText={String(r.businessRule ?? r.sourceClause ?? '')}
                            onSession={(data) => {
                              onSession(data)
                              setEditOpen((p) => ({ ...p, [id]: false }))
                            }}
                            onClose={() => setEditOpen((p) => ({ ...p, [id]: false }))}
                          />
                        </div>
                      ) : null}
                    </>
                  )}
                </li>
              )
            })}
          </ul>
        </CiSection>
      ) : null}

      <CiParameterResolverPanel
        open={Boolean(resolver)}
        onClose={() => setResolver(null)}
        operand={resolver?.operand ?? {}}
        ruleId={resolver?.ruleId ?? ''}
        busy={busy}
        onResolve={onReview}
      />
      <CiDataCalcResolutionPanel
        open={Boolean(dataCalcResolver)}
        onClose={() => setDataCalcResolver(null)}
        kind={dataCalcResolver?.kind ?? 'THRESHOLD'}
        parameterId={dataCalcResolver?.parameterId ?? ''}
        title={dataCalcResolver?.title ?? ''}
        ruleId={dataCalcResolver?.ruleId ?? ''}
        busy={busy}
        onResolve={onReview}
      />
    </div>
  )
}
