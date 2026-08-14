import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  activateCustomerCategory,
  approveCustomerCategory,
  copyCustomerCategory,
  createCustomerCategory,
  customerCategoryActivationReadiness,
  deleteCustomerCategory,
  listCustomerCategories,
  retireCustomerCategory,
  returnCustomerCategory,
  submitCustomerCategory,
  updateCustomerCategory,
  type ActivationReadiness,
  type CategoryRequest,
  type CustomerCategory,
  type LifecycleAction,
} from '@/api/customerCategories'
import { listPolicySets, type PolicySet } from '@/api/policySets'
import { ApiError } from '@/api/http'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { AdministrationWorkspaceNav } from '@/components/workspace/AdministrationWorkspaceNav'
import { EditorModal } from '@/components/scorecard/EditorModal'
import {
  BtAlert,
  DetailActions,
  DetailEmptyState,
  DetailPanel,
  DetailSection,
  FormField,
  MasterDetailLayout,
  MasterListItem,
  MasterListPanel,
} from '@/components/ui/AdminLayout'
import { BORROWER_TYPE_LABELS, BORROWER_TYPE_ORDER } from '@/catalog/borrowerTypes'
import { LOAN_PRODUCT_CODES, LOAN_PRODUCT_LABELS } from '@/catalog/loanProducts'
import {
  ANY_TOKEN,
  displayAmountRange,
  displayBorrowerType,
  displayIntake,
  displayLoanProduct,
  formatInstant,
  hasAction,
  historyEventLabel,
  INTAKE_OPTIONS,
  isEditableStatus,
  overlapPeerName,
  parseOptionalAmount,
  statusLabel,
  toApiMatchValue,
} from '@/lib/customerCategory/display'
import { userFriendlyMessage } from '@/lib/userFriendlyError'

function statusBadge(status: string) {
  const s = status.toUpperCase()
  if (s === 'ACTIVE') return <span className="bt-badge bt-badge-green">ACTIVE</span>
  if (s === 'IN_REVIEW') return <span className="bt-badge bt-badge-amber">IN REVIEW</span>
  if (s === 'APPROVED') return <span className="bt-badge bt-badge-blue">APPROVED</span>
  if (s === 'RETIRED') return <span className="bt-badge bt-badge-gray">RETIRED</span>
  return <span className="bt-badge bt-badge-gray">DRAFT</span>
}

function toLocalInput(iso: string | null | undefined): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso.slice(0, 16)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`
}

function fromLocalInput(v: string): string | null {
  const t = v.trim()
  if (!t) return null
  const d = new Date(t)
  return Number.isNaN(d.getTime()) ? t : d.toISOString()
}

function policySetLabel(ps: PolicySet | undefined): string {
  if (!ps) return 'Policy Set (unresolved)'
  return `${ps.name} · ${ps.code} · ${statusLabel(ps.status)}`
}

function auditLine(label: string, by: string | null | undefined, at: string | null | undefined) {
  if (!by && !at) return null
  return (
    <li key={label}>
      {label} by {by || '—'} · {formatInstant(at)}
    </li>
  )
}

export function CustomerCategoriesPage() {
  const [rows, setRows] = useState<CustomerCategory[] | null>(null)
  const [policySets, setPolicySets] = useState<PolicySet[]>([])
  const [loadError, setLoadError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<CustomerCategory | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [saving, setSaving] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [listSearch, setListSearch] = useState('')
  const [readiness, setReadiness] = useState<ActivationReadiness | null>(null)
  const [readinessOpen, setReadinessOpen] = useState(false)

  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [borrowerType, setBorrowerType] = useState(ANY_TOKEN)
  const [loanProduct, setLoanProduct] = useState(ANY_TOKEN)
  const [intakeSegment, setIntakeSegment] = useState(ANY_TOKEN)
  const [minAmount, setMinAmount] = useState('')
  const [maxAmount, setMaxAmount] = useState('')
  const [policySetId, setPolicySetId] = useState('')
  const [effectiveFrom, setEffectiveFrom] = useState('')
  const [effectiveUntil, setEffectiveUntil] = useState('')
  const [reasonForChange, setReasonForChange] = useState('')

  const psById = useMemo(() => new Map(policySets.map((p) => [p.id, p])), [policySets])

  const load = useCallback(async () => {
    setLoadError(null)
    setLoading(true)
    try {
      const [cats, ps] = await Promise.all([listCustomerCategories(), listPolicySets()])
      setRows(cats)
      setPolicySets(ps)
    } catch (e) {
      setRows(null)
      setLoadError(userFriendlyMessage(e, 'Failed to load Customer Categories'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const filtered = useMemo(() => {
    const items = rows ?? []
    const q = listSearch.trim().toLowerCase()
    if (!q) return items
    return items.filter((r) => {
      const ps = psById.get(r.policySetId)
      const hay = [
        r.code,
        r.name,
        r.status,
        String(r.versionNo),
        displayBorrowerType(r.borrowerType),
        displayLoanProduct(r.loanProduct),
        displayIntake(r.intakeSegment),
        displayAmountRange(r.minAmount, r.maxAmount),
        ps?.name,
        ps?.code,
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()
      return hay.includes(q)
    })
  }, [rows, listSearch, psById])

  function apply(r: CustomerCategory) {
    setSelected(r)
    setIsCreating(false)
    setCode(r.code)
    setName(r.name)
    setDescription(r.description ?? '')
    setBorrowerType(r.entityType || r.borrowerType || ANY_TOKEN)
    setLoanProduct(r.loanProduct || ANY_TOKEN)
    setIntakeSegment(r.customerRole || r.intakeSegment || ANY_TOKEN)
    setMinAmount(r.minAmount != null ? String(r.minAmount) : '')
    setMaxAmount(r.maxAmount != null ? String(r.maxAmount) : '')
    setPolicySetId(r.policySetId)
    setEffectiveFrom(toLocalInput(r.effectiveFrom))
    setEffectiveUntil(toLocalInput(r.effectiveUntil))
    setReasonForChange(r.reasonForChange ?? '')
    setActionError(null)
    setReadiness(null)
    setReadinessOpen(false)
  }

  function startNew() {
    setSelected(null)
    setIsCreating(true)
    setCode('')
    setName('')
    setDescription('')
    setBorrowerType(ANY_TOKEN)
    setLoanProduct(ANY_TOKEN)
    setIntakeSegment(ANY_TOKEN)
    setMinAmount('')
    setMaxAmount('')
    setPolicySetId(policySets[0]?.id ?? '')
    setEffectiveFrom('')
    setEffectiveUntil('')
    setReasonForChange('')
    setActionError(null)
    setReadiness(null)
    setReadinessOpen(false)
  }

  function toBody(): CategoryRequest {
    const entityType = toApiMatchValue(borrowerType)
    const customerRole = toApiMatchValue(intakeSegment)
    return {
      code: code.trim().toUpperCase(),
      name: name.trim(),
      description: description.trim() || null,
      // Canonical + transitional aliases (must agree when both present).
      entityType,
      borrowerType: entityType,
      loanProduct: toApiMatchValue(loanProduct),
      customerRole,
      intakeSegment: customerRole,
      minAmount: parseOptionalAmount(minAmount),
      maxAmount: parseOptionalAmount(maxAmount),
      policySetId,
      effectiveFrom: fromLocalInput(effectiveFrom),
      effectiveUntil: fromLocalInput(effectiveUntil),
      reasonForChange: reasonForChange.trim() || null,
    }
  }

  async function refreshSelected(id: string) {
    const [cats, ps] = await Promise.all([listCustomerCategories(), listPolicySets()])
    setRows(cats)
    setPolicySets(ps)
    const found = cats.find((c) => c.id === id)
    if (found) apply(found)
  }

  async function runAction(fn: () => Promise<CustomerCategory | void>, fallback: string) {
    setSaving(true)
    setActionError(null)
    try {
      const updated = await fn()
      if (updated && typeof updated === 'object' && 'id' in updated) {
        await refreshSelected(updated.id)
      } else {
        setSelected(null)
        setIsCreating(false)
        await load()
      }
    } catch (e) {
      setActionError(userFriendlyMessage(e, fallback))
    } finally {
      setSaving(false)
    }
  }

  async function onSave() {
    setActionError(null)
    try {
      if (!name.trim()) {
        setActionError('Name is required')
        return
      }
      if (!policySetId) {
        setActionError('Select a Policy Set')
        return
      }
      setSaving(true)
      if (isCreating) {
        if (!code.trim()) {
          setActionError('Code is required')
          setSaving(false)
          return
        }
        const c = await createCustomerCategory(toBody())
        setIsCreating(false)
        await refreshSelected(c.id)
      } else if (selected) {
        const u = await updateCustomerCategory(selected.id, toBody())
        await refreshSelected(u.id)
      }
    } catch (e) {
      setActionError(userFriendlyMessage(e, e instanceof ApiError ? e.message : 'Save failed'))
    } finally {
      setSaving(false)
    }
  }

  async function openActivateModal() {
    if (!selected) return
    setActionError(null)
    setSaving(true)
    try {
      const r = await customerCategoryActivationReadiness(selected.id)
      setReadiness(r)
      setReadinessOpen(true)
    } catch (e) {
      setActionError(userFriendlyMessage(e, 'Could not load activation readiness'))
    } finally {
      setSaving(false)
    }
  }

  async function confirmActivate() {
    if (!selected || !readiness?.ready) return
    setReadinessOpen(false)
    await runAction(() => activateCustomerCategory(selected.id), 'Activate failed')
  }

  function onRetire() {
    if (!selected) return
    const reason = globalThis.prompt('Retirement reason (required):')
    if (reason == null) return
    if (!reason.trim()) {
      setActionError('A retirement reason is required.')
      return
    }
    void runAction(() => retireCustomerCategory(selected.id, { reason: reason.trim() }), 'Retire failed')
  }

  const showForm = selected !== null || isCreating
  const status = (selected?.status ?? 'DRAFT').toUpperCase()
  const editable = isCreating || (selected != null && isEditableStatus(status) && hasAction(selected.allowedActions, 'EDIT'))
  const allowed = selected?.allowedActions ?? []

  function can(action: LifecycleAction) {
    return !isCreating && selected != null && hasAction(allowed, action)
  }

  return (
    <div>
      <PageHeader
        title="Customer Categories"
        description="Govern matching scope and Policy Set binding. Categories do not contain underwriting rules."
      />
      <AdministrationWorkspaceNav />

      {loading && <LoadingState label="Loading Customer Categories…" />}
      {loadError && <ErrorState message={loadError} />}

      {rows && !loading && (
        <MasterDetailLayout>
          <MasterListPanel
            title="Categories"
            count={filtered.length}
            search={listSearch}
            onSearchChange={setListSearch}
            searchPlaceholder="Search categories…"
            action={
              <button type="button" onClick={startNew} className="bt-btn bt-btn-primary bt-btn-sm">
                Create Category
              </button>
            }
            empty={
              filtered.length === 0 && !isCreating ? (
                <div className="bt-master-list-empty">
                  {rows.length === 0 ? (
                    <>
                      <p>No Customer Categories configured yet</p>
                      <button type="button" onClick={startNew} className="bt-btn bt-btn-primary bt-btn-sm mt-2">
                        Create Category
                      </button>
                    </>
                  ) : (
                    'No categories match your search.'
                  )}
                </div>
              ) : undefined
            }
          >
            {filtered.map((r) => {
              const ps = psById.get(r.policySetId)
              const overlaps = r.overlapWarnings?.length ?? 0
              return (
                <MasterListItem
                  key={r.id}
                  active={selected?.id === r.id && !isCreating}
                  onClick={() => apply(r)}
                  avatar={r.code}
                  title={r.name}
                  subtitle={`${r.code} · v${r.versionNo} · ${displayAmountRange(r.minAmount, r.maxAmount)}`}
                  meta={
                    <span className="flex flex-wrap items-center gap-1">
                      {statusBadge(r.status)}
                      {overlaps > 0 ? <span className="bt-badge bt-badge-amber">Overlap</span> : null}
                    </span>
                  }
                  tags={
                    <>
                      <span className="bt-tag">{displayBorrowerType(r.borrowerType)}</span>
                      <span className="bt-tag">{displayLoanProduct(r.loanProduct)}</span>
                      <span className="bt-tag">{displayIntake(r.intakeSegment)}</span>
                      <span className="bt-tag">{ps?.name ?? 'Policy Set?'}</span>
                      <span className="bt-tag text-slate-500">{formatInstant(r.updatedAt)}</span>
                    </>
                  }
                />
              )
            })}
          </MasterListPanel>

          <div>
            {showForm ? (
              <DetailPanel
                title={isCreating ? 'Create Category' : name || selected?.name}
                description="Draft edits only. ACTIVE / RETIRED / IN_REVIEW / APPROVED are read-only."
                badge={!isCreating && selected ? statusBadge(selected.status) : statusBadge('DRAFT')}
                footer={
                  <DetailActions>
                    {editable ? (
                      <button type="button" onClick={() => void onSave()} disabled={saving} className="bt-btn bt-btn-secondary">
                        {saving ? 'Saving…' : isCreating ? 'Create draft' : 'Save'}
                      </button>
                    ) : null}
                    {can('SUBMIT') ? (
                      <button
                        type="button"
                        disabled={saving}
                        className="bt-btn bt-btn-primary"
                        onClick={() =>
                          void runAction(
                            () => submitCustomerCategory(selected!.id, { remarks: reasonForChange || undefined }),
                            'Submit failed',
                          )
                        }
                      >
                        Submit
                      </button>
                    ) : null}
                    {can('APPROVE') ? (
                      <button
                        type="button"
                        disabled={saving}
                        className="bt-btn bt-btn-primary"
                        onClick={() =>
                          void runAction(
                            () => approveCustomerCategory(selected!.id, { remarks: reasonForChange || undefined }),
                            'Approve failed',
                          )
                        }
                      >
                        Approve
                      </button>
                    ) : null}
                    {can('RETURN') ? (
                      <button
                        type="button"
                        disabled={saving}
                        className="bt-btn bt-btn-secondary"
                        onClick={() =>
                          void runAction(
                            () => returnCustomerCategory(selected!.id, { remarks: reasonForChange || undefined }),
                            'Return failed',
                          )
                        }
                      >
                        Return
                      </button>
                    ) : null}
                    {can('ACTIVATE') ? (
                      <button type="button" disabled={saving} className="bt-btn bt-btn-primary" onClick={() => void openActivateModal()}>
                        Activate
                      </button>
                    ) : null}
                    {can('RETIRE') ? (
                      <button type="button" disabled={saving} className="bt-btn bt-btn-secondary" onClick={onRetire}>
                        Retire
                      </button>
                    ) : null}
                    {can('COPY') ? (
                      <button
                        type="button"
                        disabled={saving}
                        className="bt-btn bt-btn-ghost"
                        onClick={() => void runAction(() => copyCustomerCategory(selected!.id), 'Copy failed')}
                      >
                        Copy / Create New Version
                      </button>
                    ) : null}
                    {can('DELETE') ? (
                      <button
                        type="button"
                        disabled={saving}
                        className="bt-btn bt-btn-secondary text-rose-700"
                        onClick={() => {
                          if (!globalThis.confirm('Delete this DRAFT category?')) return
                          void runAction(async () => {
                            await deleteCustomerCategory(selected!.id)
                          }, 'Delete failed')
                        }}
                      >
                        Delete
                      </button>
                    ) : null}
                    {!isCreating ? (
                      <button type="button" onClick={startNew} className="bt-btn bt-btn-ghost">
                        New
                      </button>
                    ) : null}
                  </DetailActions>
                }
              >
                {actionError ? <BtAlert tone="error">{actionError}</BtAlert> : null}

                <BtAlert tone="info">
                  Composition: Category → Policy Set → Rule Set → Scorecard. The category binds a Policy Set; it does not
                  contain underwriting rules.
                </BtAlert>

                {!editable && !isCreating ? (
                  <BtAlert tone="warning">
                    {statusLabel(status)} categories are read-only. Use Copy for a new DRAFT version when needed.
                  </BtAlert>
                ) : null}

                <DetailSection title="Definition">
                  <div className="bt-form-grid">
                    {isCreating ? (
                      <FormField label="Code">
                        <input
                          className="bt-input"
                          value={code}
                          onChange={(e) => setCode(e.target.value)}
                          disabled={!editable}
                          placeholder="e.g. IND_PERSONAL"
                        />
                      </FormField>
                    ) : (
                      <FormField label="Code">
                        <input className="bt-input" value={code} disabled readOnly />
                      </FormField>
                    )}
                    <FormField label="Name" className="sm:col-span-2">
                      <input className="bt-input" value={name} onChange={(e) => setName(e.target.value)} disabled={!editable} />
                    </FormField>
                    <FormField label="Description" className="sm:col-span-2">
                      <textarea
                        className="bt-input"
                        rows={2}
                        value={description}
                        onChange={(e) => setDescription(e.target.value)}
                        disabled={!editable}
                      />
                    </FormField>
                    <FormField label="Entity Type">
                      <select
                        className="bt-input"
                        value={borrowerType}
                        onChange={(e) => setBorrowerType(e.target.value)}
                        disabled={!editable}
                      >
                        <option value={ANY_TOKEN}>Any</option>
                        {BORROWER_TYPE_ORDER.map((b) => (
                          <option key={b} value={b}>
                            {BORROWER_TYPE_LABELS[b]}
                          </option>
                        ))}
                      </select>
                    </FormField>
                    <FormField label="Loan product">
                      <select
                        className="bt-input"
                        value={loanProduct}
                        onChange={(e) => setLoanProduct(e.target.value)}
                        disabled={!editable}
                      >
                        <option value={ANY_TOKEN}>Any</option>
                        {LOAN_PRODUCT_CODES.map((c) => (
                          <option key={c} value={c}>
                            {LOAN_PRODUCT_LABELS[c]}
                          </option>
                        ))}
                      </select>
                    </FormField>
                    <FormField label="Customer Role">
                      <select
                        className="bt-input"
                        value={intakeSegment}
                        onChange={(e) => setIntakeSegment(e.target.value)}
                        disabled={!editable}
                      >
                        {INTAKE_OPTIONS.map((o) => (
                          <option key={o.value} value={o.value}>
                            {o.label}
                          </option>
                        ))}
                      </select>
                    </FormField>
                    <FormField label="Min amount (inclusive)" hint="Blank = unbounded below">
                      <input
                        className="bt-input"
                        value={minAmount}
                        onChange={(e) => setMinAmount(e.target.value)}
                        disabled={!editable}
                        placeholder="Unbounded"
                      />
                    </FormField>
                    <FormField label="Max amount (inclusive)" hint="Blank = unbounded above">
                      <input
                        className="bt-input"
                        value={maxAmount}
                        onChange={(e) => setMaxAmount(e.target.value)}
                        disabled={!editable}
                        placeholder="Unbounded"
                      />
                    </FormField>
                    <FormField label="Policy Set" className="sm:col-span-2">
                      <select
                        className="bt-input"
                        value={policySetId}
                        onChange={(e) => setPolicySetId(e.target.value)}
                        disabled={!editable}
                      >
                        <option value="">Select Policy Set…</option>
                        {policySets.map((p) => (
                          <option key={p.id} value={p.id}>
                            {policySetLabel(p)}
                          </option>
                        ))}
                      </select>
                    </FormField>
                    <FormField label="Effective from">
                      <input
                        type="datetime-local"
                        className="bt-input"
                        value={effectiveFrom}
                        onChange={(e) => setEffectiveFrom(e.target.value)}
                        disabled={!editable}
                      />
                    </FormField>
                    <FormField label="Effective until">
                      <input
                        type="datetime-local"
                        className="bt-input"
                        value={effectiveUntil}
                        onChange={(e) => setEffectiveUntil(e.target.value)}
                        disabled={!editable}
                      />
                    </FormField>
                    <FormField label="Reason for change" className="sm:col-span-2">
                      <textarea
                        className="bt-input"
                        rows={2}
                        value={reasonForChange}
                        onChange={(e) => setReasonForChange(e.target.value)}
                        disabled={!editable && !can('SUBMIT') && !can('APPROVE') && !can('RETURN')}
                      />
                    </FormField>
                    {!isCreating && selected ? (
                      <FormField label="Version / status" className="sm:col-span-2">
                        <div className="flex flex-wrap items-center gap-2 pt-1">
                          {statusBadge(selected.status)}
                          <span className="text-xs text-slate-500">v{selected.versionNo}</span>
                        </div>
                      </FormField>
                    ) : null}
                  </div>
                </DetailSection>

                {!isCreating && selected ? (
                  <DetailSection title="Overlap warnings" description="WARNING only — does not block activation by itself.">
                    {(selected.overlapWarnings?.length ?? 0) === 0 ? (
                      <p className="text-sm text-slate-500">No overlap warnings for this category.</p>
                    ) : (
                      <ul className="space-y-2 text-sm">
                        {selected.overlapWarnings!.map((w, i) => {
                          const selfKey = `${selected.code}@v${selected.versionNo}`
                          const reasons = Array.isArray(w.reasons) ? (w.reasons as unknown[]).map(String).join('; ') : ''
                          return (
                            <li key={i} className="rounded border border-amber-200 bg-amber-50 px-3 py-2 text-amber-950">
                              <strong>WARNING</strong> vs {overlapPeerName(w, selfKey)}
                              {reasons ? <span className="block text-xs mt-1">{reasons}</span> : null}
                            </li>
                          )
                        })}
                      </ul>
                    )}
                  </DetailSection>
                ) : null}

                {!isCreating && selected ? (
                  <DetailSection title="History">
                    {(selected.history?.length ?? 0) === 0 ? (
                      <p className="text-sm text-slate-500">No history events.</p>
                    ) : (
                      <ul className="space-y-1 text-sm text-slate-700">
                        {selected.history!.map((h, i) => (
                          <li key={i}>
                            {historyEventLabel(h.event)} · {String(h.displayName ?? h.userId ?? '—')} ·{' '}
                            {formatInstant(h.at != null ? String(h.at) : null)}
                            {h.remarks ? <span className="text-slate-500"> — {String(h.remarks)}</span> : null}
                          </li>
                        ))}
                      </ul>
                    )}
                  </DetailSection>
                ) : null}

                {!isCreating && selected ? (
                  <DetailSection title="Audit trail">
                    <ul className="space-y-1 text-sm text-slate-700">
                      {auditLine('Created', selected.createdBy, selected.createdAt)}
                      {auditLine('Submitted', selected.submittedBy, selected.submittedAt)}
                      {auditLine('Approved', selected.approvedBy, selected.approvedAt)}
                      {auditLine('Activated', selected.activatedBy, selected.activatedAt)}
                      {auditLine('Retired', selected.retiredBy, selected.retiredAt)}
                      {selected.retirementReason ? (
                        <li key="rr">Retirement reason: {selected.retirementReason}</li>
                      ) : null}
                    </ul>
                  </DetailSection>
                ) : null}
              </DetailPanel>
            ) : (
              <DetailEmptyState
                title="Select a category"
                description="Choose a Customer Category from the list, or create one."
                action={
                  <button type="button" onClick={startNew} className="bt-btn bt-btn-primary">
                    Create Category
                  </button>
                }
              />
            )}
          </div>
        </MasterDetailLayout>
      )}

      <EditorModal
        open={readinessOpen && readiness != null}
        title="Activation readiness"
        description="Review checks before activating. Overlaps are warnings only."
        onClose={() => setReadinessOpen(false)}
      >
        {readiness ? (
          <div className="space-y-3">
            <BtAlert tone={readiness.ready ? 'success' : 'warning'}>
              {readiness.ready ? 'Ready to activate.' : 'Not ready — resolve failing checks first.'}
            </BtAlert>
            <ul className="space-y-2 text-sm">
              {readiness.checks.map((c) => (
                <li key={c.code} className="flex gap-2 rounded border border-slate-200 px-3 py-2">
                  <span className={c.ok ? 'text-emerald-700' : 'text-rose-700'}>{c.ok ? '✓' : '✗'}</span>
                  <span>
                    <strong>{c.label}</strong>
                    {c.detail ? <span className="block text-xs text-slate-500">{c.detail}</span> : null}
                  </span>
                </li>
              ))}
            </ul>
            {(readiness.overlapWarnings?.length ?? 0) > 0 ? (
              <div>
                <div className="text-sm font-semibold text-amber-900">Overlap warnings</div>
                <ul className="mt-1 space-y-1 text-xs text-amber-900">
                  {readiness.overlapWarnings.map((w, i) => (
                    <li key={i}>{overlapPeerName(w, selected ? `${selected.code}@v${selected.versionNo}` : '')}</li>
                  ))}
                </ul>
              </div>
            ) : null}
            <div className="flex gap-2 pt-2">
              <button
                type="button"
                className="bt-btn bt-btn-primary"
                disabled={!readiness.ready || saving}
                onClick={() => void confirmActivate()}
              >
                Confirm activate
              </button>
              <button type="button" className="bt-btn bt-btn-ghost" onClick={() => setReadinessOpen(false)}>
                Cancel
              </button>
            </div>
          </div>
        ) : null}
      </EditorModal>
    </div>
  )
}
