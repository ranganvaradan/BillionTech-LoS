import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  listEligibleRuleSets,
  listEligibleScorecards,
  type ActivationReadiness,
  type EligibleRuleSet,
  type EligibleScorecard,
  type LifecycleAction,
} from '@/api/customerCategories'
import {
  activatePolicySet,
  approvePolicySet,
  copyPolicySet,
  createPolicySet,
  listPolicySets,
  policySetActivationReadiness,
  retirePolicySet,
  returnPolicySet,
  submitPolicySet,
  updatePolicySet,
  type PolicySet,
  type PolicySetRequest,
} from '@/api/policySets'
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
import {
  displayAmountRange,
  displayBorrowerType,
  displayLoanProduct,
  formatInstant,
  hasAction,
  historyEventLabel,
  isEditableStatus,
  statusLabel,
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

function ruleSetOptionLabel(r: EligibleRuleSet): string {
  return `${r.name} · ${displayBorrowerType(r.borrowerType)} · ${displayLoanProduct(r.loanProduct)} · ${displayAmountRange(r.minAmount, r.maxAmount)} · ${r.active ? 'ACTIVE' : 'inactive'}`
}

function scorecardOptionLabel(s: EligibleScorecard): string {
  return `${s.name} · ${displayBorrowerType(s.borrowerType)} · ${displayLoanProduct(s.loanProduct)} · ${displayAmountRange(s.minAmount, s.maxAmount)} · ${statusLabel(s.status)}`
}

function auditLine(label: string, by: string | null | undefined, at: string | null | undefined) {
  if (!by && !at) return null
  return (
    <li key={label}>
      {label} by {by || '—'} · {formatInstant(at)}
    </li>
  )
}

export function PolicySetsPage() {
  const [rows, setRows] = useState<PolicySet[] | null>(null)
  const [ruleSets, setRuleSets] = useState<EligibleRuleSet[]>([])
  const [scorecards, setScorecards] = useState<EligibleScorecard[]>([])
  const [loadError, setLoadError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<PolicySet | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [saving, setSaving] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [listSearch, setListSearch] = useState('')
  const [readiness, setReadiness] = useState<ActivationReadiness | null>(null)
  const [readinessOpen, setReadinessOpen] = useState(false)

  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [primaryRuleSetId, setPrimaryRuleSetId] = useState('')
  const [scorecardId, setScorecardId] = useState('')
  const [effectiveFrom, setEffectiveFrom] = useState('')
  const [effectiveUntil, setEffectiveUntil] = useState('')
  const [reasonForChange, setReasonForChange] = useState('')

  const rsById = useMemo(() => new Map(ruleSets.map((r) => [r.id, r])), [ruleSets])
  const scById = useMemo(() => new Map(scorecards.map((s) => [s.id, s])), [scorecards])

  const load = useCallback(async () => {
    setLoadError(null)
    setLoading(true)
    try {
      const [ps, rs, sc] = await Promise.all([
        listPolicySets(),
        listEligibleRuleSets(),
        listEligibleScorecards(),
      ])
      setRows(ps)
      setRuleSets(rs)
      setScorecards(sc)
    } catch (e) {
      setRows(null)
      setLoadError(userFriendlyMessage(e, 'Failed to load Policy Sets'))
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
      const rs = rsById.get(r.primaryRuleSetId)
      const sc = r.scorecardId ? scById.get(r.scorecardId) : undefined
      const hay = [
        r.code,
        r.name,
        r.status,
        String(r.versionNo),
        rs?.name,
        sc?.name,
        String(r.usedByCategoryCount ?? 0),
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()
      return hay.includes(q)
    })
  }, [rows, listSearch, rsById, scById])

  function apply(r: PolicySet) {
    setSelected(r)
    setIsCreating(false)
    setCode(r.code)
    setName(r.name)
    setDescription(r.description ?? '')
    setPrimaryRuleSetId(r.primaryRuleSetId)
    setScorecardId(r.scorecardId ?? '')
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
    setPrimaryRuleSetId(ruleSets[0]?.id ?? '')
    setScorecardId(scorecards[0]?.id ?? '')
    setEffectiveFrom('')
    setEffectiveUntil('')
    setReasonForChange('')
    setActionError(null)
    setReadiness(null)
    setReadinessOpen(false)
  }

  function toBody(): PolicySetRequest {
    return {
      code: code.trim().toUpperCase() || selected?.code || '',
      name: name.trim(),
      description: description.trim() || null,
      primaryRuleSetId,
      scorecardId,
      effectiveFrom: fromLocalInput(effectiveFrom),
      effectiveUntil: fromLocalInput(effectiveUntil),
      reasonForChange: reasonForChange.trim() || null,
    }
  }

  async function refreshSelected(id: string) {
    const [ps, rs, sc] = await Promise.all([
      listPolicySets(),
      listEligibleRuleSets(),
      listEligibleScorecards(),
    ])
    setRows(ps)
    setRuleSets(rs)
    setScorecards(sc)
    const found = ps.find((p) => p.id === id)
    if (found) apply(found)
  }

  async function runAction(fn: () => Promise<PolicySet | void>, fallback: string) {
    setSaving(true)
    setActionError(null)
    try {
      const updated = await fn()
      if (updated && typeof updated === 'object' && 'id' in updated) {
        await refreshSelected(updated.id)
      } else {
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
      if (!primaryRuleSetId) {
        setActionError('Select a primary rule set')
        return
      }
      if (!scorecardId) {
        setActionError('Select a scorecard')
        return
      }
      setSaving(true)
      if (isCreating) {
        if (!code.trim()) {
          setActionError('Code is required')
          setSaving(false)
          return
        }
        const c = await createPolicySet(toBody())
        setIsCreating(false)
        await refreshSelected(c.id)
      } else if (selected) {
        const u = await updatePolicySet(selected.id, toBody())
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
      const r = await policySetActivationReadiness(selected.id)
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
    await runAction(() => activatePolicySet(selected.id), 'Activate failed')
  }

  function onRetire() {
    if (!selected) return
    const reason = globalThis.prompt('Retirement reason (required):')
    if (reason == null) return
    if (!reason.trim()) {
      setActionError('A retirement reason is required.')
      return
    }
    void runAction(() => retirePolicySet(selected.id, { reason: reason.trim() }), 'Retire failed')
  }

  const showForm = selected !== null || isCreating
  const status = (selected?.status ?? 'DRAFT').toUpperCase()
  const editable =
    isCreating || (selected != null && isEditableStatus(status) && hasAction(selected.allowedActions, 'EDIT'))
  const allowed = selected?.allowedActions ?? []

  function can(action: LifecycleAction) {
    return !isCreating && selected != null && hasAction(allowed, action)
  }

  /** Ensure selected refs remain visible even if no longer in eligible catalogue. */
  const ruleSetOptions = useMemo(() => {
    const list = [...ruleSets]
    if (primaryRuleSetId && !list.some((r) => r.id === primaryRuleSetId)) {
      list.unshift({
        id: primaryRuleSetId,
        name: selected?.primaryRuleSetId === primaryRuleSetId ? 'Linked rule set' : 'Current rule set',
        borrowerType: 'ANY',
        loanProduct: 'ANY',
        minAmount: null,
        maxAmount: null,
        priority: 0,
        active: false,
      })
    }
    return list
  }, [ruleSets, primaryRuleSetId, selected])

  const scorecardOptions = useMemo(() => {
    const list = [...scorecards]
    if (scorecardId && !list.some((s) => s.id === scorecardId)) {
      list.unshift({
        id: scorecardId,
        name: 'Linked scorecard',
        borrowerType: 'ANY',
        loanProduct: 'ANY',
        minAmount: null,
        maxAmount: null,
        priority: 0,
        status: 'UNKNOWN',
        active: false,
      })
    }
    return list
  }, [scorecards, scorecardId])

  return (
    <div>
      <PageHeader
        title="Policy Sets"
        description="Compose one primary underwriting rule set and one scorecard. Phase 1 does not support additional rule sets."
      />
      <AdministrationWorkspaceNav />

      {loading && <LoadingState label="Loading Policy Sets…" />}
      {loadError && <ErrorState message={loadError} />}

      {rows && !loading && (
        <MasterDetailLayout>
          <MasterListPanel
            title="Policy Sets"
            count={filtered.length}
            search={listSearch}
            onSearchChange={setListSearch}
            searchPlaceholder="Search Policy Sets…"
            action={
              <button type="button" onClick={startNew} className="bt-btn bt-btn-primary bt-btn-sm">
                Create Policy Set
              </button>
            }
            empty={
              filtered.length === 0 && !isCreating ? (
                <div className="bt-master-list-empty">
                  {rows.length === 0 ? (
                    <>
                      <p>No Policy Sets configured yet</p>
                      <button type="button" onClick={startNew} className="bt-btn bt-btn-primary bt-btn-sm mt-2">
                        Create Policy Set
                      </button>
                    </>
                  ) : (
                    'No Policy Sets match your search.'
                  )}
                </div>
              ) : undefined
            }
          >
            {filtered.map((r) => {
              const rs = rsById.get(r.primaryRuleSetId)
              const sc = r.scorecardId ? scById.get(r.scorecardId) : undefined
              return (
                <MasterListItem
                  key={r.id}
                  active={selected?.id === r.id && !isCreating}
                  onClick={() => apply(r)}
                  avatar={r.code}
                  title={r.name}
                  subtitle={`${r.code} · v${r.versionNo} · used by ${r.usedByCategoryCount ?? 0}`}
                  meta={statusBadge(r.status)}
                  tags={
                    <>
                      <span className="bt-tag">{rs?.name ?? 'Rule set?'}</span>
                      <span className="bt-tag">{sc?.name ?? (r.scorecardId ? 'Scorecard?' : 'No scorecard')}</span>
                      <span className="bt-tag">
                        {formatInstant(r.effectiveFrom)} – {formatInstant(r.effectiveUntil)}
                      </span>
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
                title={isCreating ? 'Create Policy Set' : name || selected?.name}
                description="Draft edits only. Link eligible ACTIVE executables by name — never paste UUIDs."
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
                            () => submitPolicySet(selected!.id, { remarks: reasonForChange || undefined }),
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
                            () => approvePolicySet(selected!.id, { remarks: reasonForChange || undefined }),
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
                            () => returnPolicySet(selected!.id, { remarks: reasonForChange || undefined }),
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
                        onClick={() => void runAction(() => copyPolicySet(selected!.id), 'Copy failed')}
                      >
                        Copy / Create New Version
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
                  Composition: Category → Policy Set → Rule Set → Scorecard. This Policy Set selects the executable rule
                  set and scorecard; categories bind to the Policy Set.
                </BtAlert>

                {!editable && !isCreating ? (
                  <BtAlert tone="warning">
                    {statusLabel(status)} Policy Sets are read-only. Use Copy for a new DRAFT version when needed.
                  </BtAlert>
                ) : null}

                <DetailSection title="Composition">
                  <div className="bt-form-grid">
                    {isCreating ? (
                      <FormField label="Code">
                        <input
                          className="bt-input"
                          value={code}
                          onChange={(e) => setCode(e.target.value)}
                          disabled={!editable}
                          placeholder="e.g. PS_IND_PERSONAL"
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
                    <FormField label="Primary rule set" className="sm:col-span-2" hint="Eligible ACTIVE underwriting rule sets">
                      <select
                        className="bt-input"
                        value={primaryRuleSetId}
                        onChange={(e) => setPrimaryRuleSetId(e.target.value)}
                        disabled={!editable}
                      >
                        <option value="">Select rule set…</option>
                        {ruleSetOptions.map((r) => (
                          <option key={r.id} value={r.id}>
                            {ruleSetOptionLabel(r)}
                          </option>
                        ))}
                      </select>
                    </FormField>
                    <FormField label="Scorecard" className="sm:col-span-2" hint="Eligible executable scorecards">
                      <select
                        className="bt-input"
                        value={scorecardId}
                        onChange={(e) => setScorecardId(e.target.value)}
                        disabled={!editable}
                      >
                        <option value="">Select scorecard…</option>
                        {scorecardOptions.map((s) => (
                          <option key={s.id} value={s.id}>
                            {scorecardOptionLabel(s)}
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
                      <FormField label="Version / usage" className="sm:col-span-2">
                        <div className="flex flex-wrap items-center gap-2 pt-1 text-sm text-slate-600">
                          {statusBadge(selected.status)}
                          <span>v{selected.versionNo}</span>
                          <span>Used by {selected.usedByCategoryCount ?? 0} categor{selected.usedByCategoryCount === 1 ? 'y' : 'ies'}</span>
                        </div>
                      </FormField>
                    ) : null}
                  </div>
                </DetailSection>

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
                title="Select a Policy Set"
                description="Choose a Policy Set from the list, or create one."
                action={
                  <button type="button" onClick={startNew} className="bt-btn bt-btn-primary">
                    Create Policy Set
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
        description="Review checks before activating this Policy Set."
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
