import { useMemo, useState } from 'react'
import {
  CiEmptyState,
  CiExecutiveSummary,
  CiSection,
  CiTechnicalDetails,
} from '@/components/creditIntelligence/CiSection'
import {
  decisionPolicyDomainLabel,
  kycRequirementTypeLabel,
} from '@/lib/creditIntelligence/businessLexicon'
import { CiCapabilityCataloguePanel } from '@/pages/creditIntelligence/CiCapabilityCataloguePanel'

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
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
      return 'bg-indigo-100 text-indigo-900'
    case 'Ignored':
      return 'bg-slate-200 text-slate-700'
    case 'Deleted':
      return 'bg-slate-100 text-slate-500 line-through'
    case 'Blocked':
    case 'Needs your input':
    case 'Needs Review':
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
  'Credit Rules',
] as const

type StatusFilter = 'ALL' | 'NEEDS_REVIEW' | 'ACCEPTED' | 'MANUAL_INPUT' | 'IGNORED'

function VisualLogic({ visual }: { visual: Record<string, unknown> }) {
  const kind = String(visual.kind ?? 'SIMPLE')
  if (kind === 'EXCEPTION_ALL') {
    const conditions = asList(visual.conditions)
    return (
      <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-3 text-sm">
        <div className="font-semibold text-slate-900">{String(visual.title ?? 'Condition')}</div>
        <div className="mt-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
          {String(visual.subtitle ?? 'EXCEPTION allowed only if ALL:')}
        </div>
        <ul className="mt-2 space-y-1">
          {conditions.map((c, i) => (
            <li key={i} className="flex gap-2 text-slate-800">
              <span className="text-emerald-600" aria-hidden>
                ✓
              </span>
              <span>{String(c)}</span>
            </li>
          ))}
        </ul>
        <div className="mt-2 text-xs text-slate-600">THEN: {String(visual.then ?? '—')}</div>
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
      return status === 'Needs your input' || status === 'Needs Review' || status === 'Blocked' || status === 'Ready'
    case 'ACCEPTED':
      return status === 'Accepted' || status === 'Edited' || status === 'Approved'
    case 'MANUAL_INPUT':
      return status === 'Manual Input'
    case 'IGNORED':
      return status === 'Ignored' || status === 'Deleted'
    default:
      return true
  }
}

export function CiPolicyRulesTab({
  cards,
  busy,
  onReview,
  onViewTests,
  onSaveDraft,
  onActivationCheck,
  onAddPlainEnglishRule,
  onCatalogueChanged,
  documentId,
  prospectDemoMode = false,
}: {
  cards: unknown[]
  busy: boolean
  onReview: (ruleId: string, body: Record<string, unknown>) => Promise<void>
  onViewTests?: () => void
  onSaveDraft?: () => void
  onActivationCheck?: () => void
  onAddPlainEnglishRule?: (group: string, text: string) => Promise<void>
  onCatalogueChanged?: (session?: unknown) => void
  documentId?: string | null
  prospectDemoMode?: boolean
}) {
  const [clauseOpen, setClauseOpen] = useState<Record<string, boolean>>({})
  const [editOpen, setEditOpen] = useState<Record<string, boolean>>({})
  const [editDraft, setEditDraft] = useState<Record<string, string>>({})
  const [manualOpen, setManualOpen] = useState<Record<string, boolean>>({})
  const [manualLabel, setManualLabel] = useState<Record<string, string>>({})
  const [manualType, setManualType] = useState<Record<string, string>>({})
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')
  const [search, setSearch] = useState('')
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({})
  const [addOpen, setAddOpen] = useState<Record<string, boolean>>({})
  const [addText, setAddText] = useState<Record<string, string>>({})
  const [undoStack, setUndoStack] = useState<{ id: string; prev: Record<string, unknown> }[]>([])
  const [catalogueOpen, setCatalogueOpen] = useState(false)
  const [catalogueEdit, setCatalogueEdit] = useState<{
    ruleId: string
    businessCapabilityId: string
    parameters?: Record<string, unknown>
    failureTreatment?: string
  } | null>(null)

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
      manual: all.filter((s) => s === 'Manual Input').length,
      ignored: all.filter((s) => s === 'Ignored').length,
      accepted: all.filter((s) => s === 'Accepted' || s === 'Edited' || s === 'Approved').length,
    }
  }, [cards])

  const readyToAccept = useMemo(
    () =>
      cards.filter((c) => {
        const r = asRecord(c)
        return String(r.status) === 'Ready' && !r.blockedReason && !r.platformGuardrail
      }),
    [cards],
  )

  const acceptAllReady = async () => {
    if (readyToAccept.length === 0) return
    if (
      !window.confirm(
        `Accept ${readyToAccept.length} ready rule${readyToAccept.length === 1 ? '' : 's'}? Ambiguous or incomplete rules will not be accepted.`,
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
    <div className="space-y-4">
      <CiExecutiveSummary
        title="Review Rules"
        nextAction={
          <div className="flex flex-wrap gap-2">
            {onSaveDraft ? (
              <button type="button" className="bt-btn bt-btn-primary bt-btn-sm" disabled={busy} onClick={onSaveDraft}>
                Save Draft
              </button>
            ) : null}
            {onViewTests ? (
              <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" onClick={onViewTests}>
                Test Policy
              </button>
            ) : null}
            {onActivationCheck ? (
              <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" onClick={onActivationCheck}>
                Activation Check
              </button>
            ) : null}
          </div>
        }
      >
        <p className="text-sm text-slate-700">
          <strong>{totals.total}</strong> rules identified · <strong>{totals.ready}</strong> ready ·{' '}
          <strong>{totals.needs}</strong> need your input · <strong>{totals.manual}</strong> manual input ·{' '}
          <strong>{totals.ignored}</strong> ignored
        </p>
        <p className="mt-1 text-xs text-slate-500">
          Save Draft anytime — unresolved items and ignored rules do not block a draft.
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
          [
            ['ALL', 'All'],
            ['NEEDS_REVIEW', 'Needs review'],
            ['ACCEPTED', 'Accepted'],
            ['MANUAL_INPUT', 'Manual input'],
            ['IGNORED', 'Ignored'],
          ] as const
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
                  const dataUsed = asList(r.dataUsed)
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
                        <div>
                          <div className="text-lg font-semibold text-slate-900">
                            {String(r.ruleName ?? 'Business rule')}
                          </div>
                          <div className="mt-1 flex flex-wrap gap-2 text-xs text-slate-600">
                            <span className="rounded-full bg-slate-100 px-2 py-0.5">
                              {decisionPolicyDomainLabel(r.decisionDomain)}
                            </span>
                            {r.capabilityBadge || r.existingCapability || r.catalogueBacked ? (
                              <span className="rounded-full bg-emerald-50 px-2 py-0.5 font-medium text-emerald-900">
                                {String(r.capabilityBadge ?? 'Existing capability')}
                              </span>
                            ) : null}
                            {r.sourceLabel ? (
                              <span className="rounded-full bg-slate-50 px-2 py-0.5 text-slate-700">
                                {String(r.sourceLabel)}
                              </span>
                            ) : null}
                            {r.kycRequirementType ? (
                              <span className="rounded-full bg-indigo-50 px-2 py-0.5 text-indigo-900">
                                {kycRequirementTypeLabel(r.kycRequirementType)}
                              </span>
                            ) : null}
                            {Boolean(r.manualReviewRequired) ||
                            String(r.failureTreatment ?? '').toUpperCase() === 'MANUAL_REVIEW' ? (
                              <span className="rounded-full bg-violet-100 px-2 py-0.5 font-semibold text-violet-950">
                                Manual review
                              </span>
                            ) : null}
                          </div>
                        </div>
                        <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${statusChip(status)}`}>
                          {status === 'Needs Review' ? 'Needs your input' : status}
                        </span>
                      </div>

                      {needsInput && r.blockedReason ? (
                        <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950">
                          Needs your input: {String(r.blockedReason)}
                        </div>
                      ) : null}

                      <div className="mt-3 space-y-2 text-sm">
                        <div>
                          <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                            Business statement
                          </div>
                          <p className="mt-1 font-medium text-slate-900">{String(r.businessRule ?? '—')}</p>
                        </div>
                        <dl className="grid gap-2 sm:grid-cols-3">
                          <div>
                            <dt className="text-xs text-slate-500">Outcome if fails</dt>
                            <dd className="font-medium">{String(r.resultOnFailure ?? '—')}</dd>
                          </div>
                          <div>
                            <dt className="text-xs text-slate-500">Data / source</dt>
                            <dd className="font-medium">
                              {dataUsed.length ? dataUsed.map(String).join(', ') : String(r.dataFamily ?? '—')}
                            </dd>
                          </div>
                          <div>
                            <dt className="text-xs text-slate-500">If information missing</dt>
                            <dd className="font-medium">{String(r.onMissing ?? '—')}</dd>
                          </div>
                        </dl>
                        <div className="mt-2">
                          <div className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
                            Key condition
                          </div>
                          <VisualLogic visual={visual} />
                        </div>
                      </div>

                      {!isTerminal ? (
                        <div className="mt-4 flex flex-wrap gap-2">
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
                          <button
                            type="button"
                            disabled={busy || Boolean(r.platformGuardrail)}
                            className="bt-btn bt-btn-secondary bt-btn-sm"
                            onClick={() => {
                              if (r.businessCapabilityId) {
                                setCatalogueEdit({
                                  ruleId: id,
                                  businessCapabilityId: String(r.businessCapabilityId),
                                  parameters: asRecord(r.parameters),
                                  failureTreatment: String(r.failureTreatment ?? 'REJECT'),
                                })
                                setCatalogueOpen(true)
                                return
                              }
                              setEditOpen((p) => ({ ...p, [id]: !p[id] }))
                              setEditDraft((p) => ({
                                ...p,
                                [id]: p[id] ?? String(r.businessRule ?? ''),
                              }))
                            }}
                          >
                            Edit
                          </button>
                          <button
                            type="button"
                            disabled={busy || status === 'Ignored'}
                            className="bt-btn bt-btn-secondary bt-btn-sm"
                            onClick={() =>
                              void onReview(id, {
                                uiAction: 'IGNORE',
                                reason: 'Ignored for now — retained in draft, not an activation blocker',
                              })
                            }
                          >
                            Ignore for now
                          </button>
                          <button
                            type="button"
                            disabled={busy || Boolean(r.platformGuardrail)}
                            className="bt-btn bt-btn-secondary bt-btn-sm"
                            onClick={() => {
                              setUndoStack((s) => [...s, { id, prev: r }])
                              void onReview(id, {
                                uiAction: 'DELETE',
                                reason: 'Not part of the intended policy',
                              })
                            }}
                          >
                            Delete
                          </button>
                          <button
                            type="button"
                            disabled={busy}
                            className="bt-btn bt-btn-secondary bt-btn-sm"
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
                            className="bt-btn bt-btn-secondary bt-btn-sm"
                            onClick={() => setClauseOpen((prev) => ({ ...prev, [id]: !prev[id] }))}
                          >
                            View source
                          </button>
                        </div>
                      ) : null}

                      {editOpen[id] ? (
                        <div className="mt-3 space-y-2 rounded-lg border border-slate-200 bg-slate-50 p-3">
                          <label className="block text-sm">
                            <span className="text-slate-600">Rule wording</span>
                            <textarea
                              className="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm"
                              rows={3}
                              value={editDraft[id] ?? ''}
                              onChange={(e) => setEditDraft((p) => ({ ...p, [id]: e.target.value }))}
                              disabled={busy}
                            />
                          </label>
                          <button
                            type="button"
                            disabled={busy}
                            className="bt-btn bt-btn-primary bt-btn-sm"
                            onClick={() =>
                              void onReview(id, {
                                uiAction: 'EDIT',
                                businessRule: editDraft[id],
                                reason: 'Edited by Credit Manager',
                                humanChanges: { businessRule: editDraft[id] },
                              }).then(() => setEditOpen((p) => ({ ...p, [id]: false })))
                            }
                          >
                            Save edit
                          </button>
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

                      {clauseOpen[id] ? (
                        <blockquote className="mt-3 whitespace-pre-wrap rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-800">
                          {String(r.sourceClause ?? '—')}
                          {r.section ? `\n\nSection: ${String(r.section)}` : ''}
                        </blockquote>
                      ) : null}

                      <CiTechnicalDetails hidden={prospectDemoMode}>
                        {JSON.stringify(
                          {
                            systemRuleId: r.systemRuleId,
                            reviewStatus: r.reviewStatus,
                            disposition: r.disposition,
                            expression: r.technicalExpression,
                          },
                          null,
                          2,
                        )}
                      </CiTechnicalDetails>
                    </li>
                  )
                })}
              </ul>
            ) : null}

            <div className="mt-3 border-t border-slate-100 pt-3">
              <button
                type="button"
                className="text-sm font-semibold text-sky-800"
                onClick={() => setAddOpen((p) => ({ ...p, [name]: !p[name] }))}
              >
                + Add rule
              </button>
              {addOpen[name] ? (
                <div className="mt-2 space-y-2">
                  <textarea
                    className="w-full rounded border border-slate-300 px-3 py-2 text-sm"
                    rows={2}
                    placeholder='e.g. "Minimum bureau score is 700."'
                    value={addText[name] ?? ''}
                    onChange={(e) => setAddText((p) => ({ ...p, [name]: e.target.value }))}
                    disabled={busy}
                  />
                  <button
                    type="button"
                    disabled={busy || !(addText[name] ?? '').trim()}
                    className="bt-btn bt-btn-primary bt-btn-sm"
                    onClick={() => {
                      const text = (addText[name] ?? '').trim()
                      if (!text) return
                      if (onAddPlainEnglishRule) {
                        void onAddPlainEnglishRule(name, text).then(() => {
                          setAddText((p) => ({ ...p, [name]: '' }))
                          setAddOpen((p) => ({ ...p, [name]: false }))
                        })
                      } else {
                        window.alert(
                          'Plain-English add uses the existing interpretation path. Paste the rule into Ambiguous Terms / analyst flow, or contact support if Add Rule API is unavailable in this build.',
                        )
                      }
                    }}
                  >
                    Interpret & review
                  </button>
                </div>
              ) : null}
            </div>
          </CiSection>
        )
      })}
    </div>
  )
}
