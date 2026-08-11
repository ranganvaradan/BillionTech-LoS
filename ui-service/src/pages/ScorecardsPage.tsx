import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  activateScorecard,
  approveScorecard,
  confirmScorecardMissingDataPolicies,
  createScorecard,
  createScorecardNewVersion,
  deleteScorecard,
  getScorecardReviewPackage,
  listScorecards,
  previewScorecard,
  recordScorecardPreview,
  returnScorecardForChanges,
  submitScorecardForReview,
  suggestScorecardFactorsFromPolicy,
  updateScorecard,
  type HardRuleRow,
  type ScorecardParameterDef,
  type ScorecardRow,
  type UnderwritingScorecardRequest,
  type UnderwritingScorecardResponse,
} from '@/api/scorecards'
import { ApiError } from '@/api/http'
import { ScorecardParameterEditor, buildParameterDefsFromRows } from '@/components/scorecard/ScorecardParameterEditor'
import { EditorModal } from '@/components/scorecard/EditorModal'
import { UnderwritingPolicyMapView } from '@/components/credit/UnderwritingPolicyMapView'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { AdministrationWorkspaceNav } from '@/components/workspace/AdministrationWorkspaceNav'
import { AmountInputSplit } from '@/components/ui/AmountInputHint'
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
import { isLoanProductCode, LOAN_PRODUCT_CODES, LOAN_PRODUCT_LABELS, loanProductLabel } from '@/catalog/loanProducts'
import {
  defaultParameterForSource,
  paramDef,
} from '@/lib/credit/scorecardConfig'
import { defaultConditionForParam } from '@/lib/credit/scorecardCondition'
import type { BorrowerType } from '@/types/createApplication'

const BORROWER_TYPES: BorrowerType[] = [...BORROWER_TYPE_ORDER]

let rid = 0
function newRow(): ScorecardRow {
  rid += 1
  const source = 'BUREAU'
  const parameter = defaultParameterForSource(source)
  return {
    id: `r${Date.now()}-${rid}`,
    parameter,
    source,
    condition: defaultConditionForParam(paramDef(source, parameter)),
    weight: 1,
    score: 20,
  }
}

function newHard(): HardRuleRow {
  rid += 1
  const source = 'BUREAU'
  const parameter = defaultParameterForSource(source)
  return {
    id: `h${Date.now()}-${rid}`,
    parameter,
    source,
    condition: 'LT:500',
    decision: 'REJECT',
  }
}

export function ScorecardsPage() {
  const [rows, setRows] = useState<UnderwritingScorecardResponse[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<UnderwritingScorecardResponse | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [saving, setSaving] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [listSearch, setListSearch] = useState('')

  const [name, setName] = useState('')
  const [borrowerType, setBorrowerType] = useState<BorrowerType>('INDIVIDUAL')
  const [loanProduct, setLoanProduct] = useState('PERSONAL_LOAN')
  const [version, setVersion] = useState(1)
  const [priority, setPriority] = useState(200)
  const [minAmount, setMinAmount] = useState('')
  const [maxAmount, setMaxAmount] = useState('')
  const [geoState, setGeoState] = useState('')
  const [geoCity, setGeoCity] = useState('')
  const [active, setActive] = useState(true)
  const [approveMin, setApproveMin] = useState(70)
  const [manualMin, setManualMin] = useState(40)
  const [grid, setGrid] = useState<ScorecardRow[]>([newRow()])
  const [parameterDefs, setParameterDefs] = useState<Record<string, ScorecardParameterDef>>({})
  const [hards, setHards] = useState<HardRuleRow[]>([])
  const [mapOpen, setMapOpen] = useState(false)
  const [previewInputs, setPreviewInputs] = useState('{"BUREAU_SCORE":760,"MONTHLY_INCOME":60000,"OBLIGATION_RATIO":30,"AVERAGE_BANK_BALANCE":25000,"KYC_QUALITY":1}')
  const [previewResult, setPreviewResult] = useState<Record<string, unknown> | null>(null)
  const [previewBusy, setPreviewBusy] = useState(false)
  const [policySuggestRaw, setPolicySuggestRaw] = useState('bureau.score\nobligation.ratio\napplication.business_vintage_months')
  const [policySuggestions, setPolicySuggestions] = useState<Array<Record<string, unknown>>>([])
  const [reviewPackage, setReviewPackage] = useState<Record<string, unknown> | null>(null)
  const [govRemarks, setGovRemarks] = useState('')

  const load = useCallback(async () => {
    setLoadError(null)
    setLoading(true)
    try {
      const s = await listScorecards()
      setRows(s)
    } catch (e) {
      setRows(null)
      setLoadError(e instanceof Error ? e.message : 'Failed to load')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const filteredRows = useMemo(() => {
    const items = rows ?? []
    const q = listSearch.trim().toLowerCase()
    if (!q) return items
    return items.filter((r) => {
      const borrowerLabel = (BORROWER_TYPE_LABELS[r.borrowerType as BorrowerType] ?? r.borrowerType).toLowerCase()
      const productLabel = loanProductLabel(r.loanProduct).toLowerCase()
      return (
        r.name.toLowerCase().includes(q)
        || r.borrowerType.toLowerCase().includes(q)
        || borrowerLabel.includes(q)
        || r.loanProduct.toLowerCase().includes(q)
        || productLabel.includes(q)
        || String(r.priority).includes(q)
      )
    })
  }, [rows, listSearch])

  function apply(r: UnderwritingScorecardResponse) {
    setSelected(r)
    setIsCreating(false)
    setName(r.name)
    setBorrowerType(r.borrowerType as BorrowerType)
    setLoanProduct(r.loanProduct)
    setVersion(r.version)
    setPriority(r.priority)
    setMinAmount(r.minAmount != null ? String(r.minAmount) : '')
    setMaxAmount(r.maxAmount != null ? String(r.maxAmount) : '')
    const g = r.geography
    setGeoState(g && typeof g.state === 'string' ? g.state : '')
    setGeoCity(g && typeof g.city === 'string' ? g.city : '')
    setActive(r.active)
    const t = r.thresholdsJson || {}
    setApproveMin(typeof t.approveMinPercent === 'number' ? t.approveMinPercent : 70)
    setManualMin(typeof t.manualMinPercent === 'number' ? t.manualMinPercent : 40)
    const sc = r.scorecardJson || {}
    const rawDefs = (sc as { parameterDefs?: Record<string, ScorecardParameterDef> }).parameterDefs
    const defs = rawDefs && typeof rawDefs === 'object' ? rawDefs : {}
    setParameterDefs(defs)
    const rawRows = (sc as { rows?: unknown }).rows
    if (Array.isArray(rawRows) && rawRows.length) {
      setGrid(
        (rawRows as Record<string, unknown>[]).map((x, i) => {
          const parameter = String(x.parameter ?? 'BUREAU_SCORE')
          const def = defs[parameter]
          const dependsOnRaw = x.dependsOn
          const dependsOn =
            dependsOnRaw &&
            typeof dependsOnRaw === 'object' &&
            Array.isArray((dependsOnRaw as { conditions?: unknown }).conditions) &&
            ((dependsOnRaw as { conditions: unknown[] }).conditions?.length ?? 0) > 0
              ? (dependsOnRaw as ScorecardRow['dependsOn'])
              : undefined
          const safetyPolicies =
            (r.safetyJson as { factorPolicies?: Record<string, { missingData?: string }> } | undefined)
              ?.factorPolicies ?? {}
          const missingFromSafety = safetyPolicies[parameter]?.missingData
          return {
            id: String(x.id ?? `e${i}`),
            parameter,
            source: String(x.source ?? 'BUREAU'),
            condition: String(x.condition ?? 'GTE:650'),
            weight: Number(x.weight) || 1,
            score: Number(x.score) || 0,
            attachment: x.attachment != null ? String(x.attachment) : undefined,
            canonicalParameterId: x.canonicalParameterId != null ? String(x.canonicalParameterId) : undefined,
            canonicalDefinitionVersion:
              x.canonicalDefinitionVersion != null ? Number(x.canonicalDefinitionVersion) : undefined,
            mappingStatus: x.mappingStatus != null ? String(x.mappingStatus) : undefined,
            legacyParameterKey: x.legacyParameterKey != null ? String(x.legacyParameterKey) : undefined,
            factorLabel: x.factorLabel != null ? String(x.factorLabel) : undefined,
            legacyCustomJustified: Boolean(x.legacyCustomJustified),
            missingData:
              missingFromSafety === 'REQUIRED' ||
              missingFromSafety === 'OPTIONAL_DEPRESS' ||
              missingFromSafety === 'OPTIONAL_SKIP'
                ? missingFromSafety
                : x.missingData === 'REQUIRED' ||
                    x.missingData === 'OPTIONAL_DEPRESS' ||
                    x.missingData === 'OPTIONAL_SKIP'
                  ? x.missingData
                  : undefined,
            ...(dependsOn ? { dependsOn } : {}),
            ...(def
              ? {
                  inputType: def.inputType,
                  options: def.options,
                  formula: def.formula,
                }
              : {}),
          }
        }),
      )
    } else {
      setGrid([newRow()])
    }
    const hr = (r.hardRulesJson as { rules?: unknown })?.rules
    if (Array.isArray(hr) && hr.length) {
      setHards(
        (hr as Record<string, unknown>[]).map((x, i) => {
          const dependsOnRaw = x.dependsOn
          const dependsOn =
            dependsOnRaw &&
            typeof dependsOnRaw === 'object' &&
            Array.isArray((dependsOnRaw as { conditions?: unknown }).conditions) &&
            ((dependsOnRaw as { conditions: unknown[] }).conditions?.length ?? 0) > 0
              ? (dependsOnRaw as HardRuleRow['dependsOn'])
              : undefined
          return {
            id: String(x.id ?? `h${i}`),
            parameter: String(x.parameter ?? 'BUREAU_SCORE'),
            source: String(x.source ?? 'BUREAU'),
            condition: String(x.condition ?? 'LT:500'),
            decision: String(x.decision) === 'MANUAL_REVIEW' ? 'MANUAL_REVIEW' : 'REJECT',
            message: x.message != null ? String(x.message) : undefined,
            ...(dependsOn ? { dependsOn } : {}),
          }
        }),
      )
    } else {
      setHards([])
    }
    setActionError(null)
    setReviewPackage(null)
    setGovRemarks('')
  }

  function startNew() {
    setSelected(null)
    setIsCreating(true)
    setName('New scorecard')
    setBorrowerType('INDIVIDUAL')
    setLoanProduct(LOAN_PRODUCT_CODES[0] ?? 'PERSONAL_LOAN')
    setVersion(1)
    setPriority(200)
    setMinAmount('')
    setMaxAmount('')
    setGeoState('')
    setGeoCity('')
    setActive(false)
    setApproveMin(70)
    setManualMin(40)
    setGrid([newRow(), newRow()])
    setParameterDefs({})
    setHards([newHard()])
    setActionError(null)
  }

  function toRequest(): UnderwritingScorecardRequest {
    const minA = minAmount.trim() ? Number.parseFloat(minAmount) : null
    const maxA = maxAmount.trim() ? Number.parseFloat(maxAmount) : null
    const geo: Record<string, unknown> | null =
      geoState.trim() || geoCity.trim()
        ? { ...(geoState.trim() ? { state: geoState.trim() } : {}), ...(geoCity.trim() ? { city: geoCity.trim() } : {}) }
        : null
    return {
      name: name.trim() || 'Scorecard',
      borrowerType,
      loanProduct: loanProduct.trim(),
      version: Math.max(1, version),
      priority,
      minAmount: minA != null && !Number.isNaN(minA) ? minA : null,
      maxAmount: maxA != null && !Number.isNaN(maxA) ? maxA : null,
      geography: geo,
      scorecardJson: {
        rows: grid.map((r) => ({
          id: r.id,
          parameter: r.parameter,
          source: r.source,
          condition: r.condition.trim(),
          weight: r.weight,
          score: r.score,
          ...(r.attachment?.trim() ? { attachment: r.attachment.trim() } : {}),
          ...(r.dependsOn?.conditions?.length ? { dependsOn: r.dependsOn } : {}),
          ...(r.canonicalParameterId
            ? {
                canonicalParameterId: r.canonicalParameterId,
                canonicalDefinitionVersion: r.canonicalDefinitionVersion ?? 1,
                mappingStatus: r.mappingStatus ?? 'EXACT',
                legacyParameterKey: r.legacyParameterKey ?? r.parameter,
              }
            : {}),
          ...(r.factorLabel ? { factorLabel: r.factorLabel } : {}),
          ...(r.legacyCustomJustified ? { legacyCustomJustified: true, mappingStatus: 'LEGACY_CUSTOM' } : {}),
          ...(r.missingData ? { missingData: r.missingData } : {}),
        })),
        ...(Object.keys(buildParameterDefsFromRows(grid)).length
          ? { parameterDefs: buildParameterDefsFromRows(grid) }
          : {}),
      },
      thresholdsJson: { approveMinPercent: approveMin, manualMinPercent: manualMin },
      // Hard rules are managed on Underwriting Rules; preserve existing scorecard hard-rules JSON.
      hardRulesJson: selected?.hardRulesJson ?? { rules: [] },
      safetyJson: {
        ...(selected?.safetyJson ?? {}),
        factorPolicies: Object.fromEntries(
          grid
            .filter((r) => r.parameter && r.missingData)
            .map((r) => [r.parameter, { missingData: r.missingData }]),
        ),
        weightSemantics: 'METADATA_ONLY_NOT_USED_IN_FORMULA',
        bandSemantics: 'EXCLUSIVE_RANGES',
        denominatorSemantics: 'SUM_OF_FACTOR_MAX_WHERE_FACTOR_MAX_IS_MAX_BAND_POINTS',
      },
      active: false,
    }
  }

  async function onSave() {
    setActionError(null)
    setSaving(true)
    try {
      const body = toRequest()
      if (isCreating) {
        const c = await createScorecard(body)
        setIsCreating(false)
        setSelected(c)
        setRows(await listScorecards())
        apply(c)
      } else if (selected) {
        const u = await updateScorecard(selected.id, body)
        setSelected(u)
        setRows(await listScorecards())
        apply(u)
      }
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : e instanceof Error ? e.message : 'Save failed')
    } finally {
      setSaving(false)
    }
  }

  async function onDelete() {
    if (!selected) return
    if (!globalThis.confirm('Delete this scorecard? It must be inactive first.')) return
    setActionError(null)
    try {
      await deleteScorecard(selected.id)
      setSelected(null)
      setIsCreating(false)
      setRows(await listScorecards())
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : e instanceof Error ? e.message : 'Delete failed')
    }
  }

  async function onCreateNewVersion() {
    if (!selected) return
    setActionError(null)
    setSaving(true)
    try {
      const draft = await createScorecardNewVersion(selected.id)
      setIsCreating(false)
      setSelected(draft)
      setRows(await listScorecards())
      apply(draft)
    } catch (e) {
      setActionError(
        e instanceof ApiError ? e.message : e instanceof Error ? e.message : 'Create new version failed',
      )
    } finally {
      setSaving(false)
    }
  }

  async function onPreview() {
    setActionError(null)
    setPreviewBusy(true)
    setPreviewResult(null)
    try {
      let inputs: Record<string, unknown> = {}
      try {
        inputs = JSON.parse(previewInputs) as Record<string, unknown>
      } catch {
        throw new Error('Preview inputs must be valid JSON object of parameter → value')
      }
      if (selected?.id && !isCreating) {
        const updated = await recordScorecardPreview(selected.id, { inputs })
        setSelected(updated)
        setPreviewResult({
          earnedPoints: (updated.governanceJson as { lastPreview?: Record<string, unknown> } | undefined)?.lastPreview
            ?.earnedPoints,
          maxPoints: (updated.governanceJson as { lastPreview?: Record<string, unknown> } | undefined)?.lastPreview
            ?.maxPoints,
          normalizedPercent: (updated.governanceJson as { lastPreview?: Record<string, unknown> } | undefined)
            ?.lastPreview?.normalizedPercent,
          policyDecision: (updated.governanceJson as { lastPreview?: Record<string, unknown> } | undefined)?.lastPreview
            ?.policyDecision,
          recorded: true,
          applicationMutated: false,
        })
      } else {
        setPreviewResult(
          await previewScorecard({
            borrowerType,
            loanProduct,
            scorecardJson: toRequest().scorecardJson,
            thresholdsJson: toRequest().thresholdsJson,
            hardRulesJson: toRequest().hardRulesJson,
            safetyJson: toRequest().safetyJson,
            inputs,
          }),
        )
      }
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : e instanceof Error ? e.message : 'Preview failed')
    } finally {
      setPreviewBusy(false)
    }
  }

  async function refreshSelected(id: string) {
    const all = await listScorecards()
    setRows(all)
    const found = all.find((r) => r.id === id)
    if (found) {
      setSelected(found)
      apply(found)
    }
  }

  async function onSubmitReview() {
    if (!selected) return
    setSaving(true)
    setActionError(null)
    try {
      await confirmScorecardMissingDataPolicies(selected.id).catch(() => undefined)
      const updated = await submitScorecardForReview(selected.id, govRemarks || undefined)
      setSelected(updated)
      await refreshSelected(updated.id)
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : e instanceof Error ? e.message : 'Submit failed')
    } finally {
      setSaving(false)
    }
  }

  async function onApprove() {
    if (!selected) return
    setSaving(true)
    setActionError(null)
    try {
      const updated = await approveScorecard(selected.id, govRemarks || undefined)
      setSelected(updated)
      await refreshSelected(updated.id)
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : e instanceof Error ? e.message : 'Approve failed')
    } finally {
      setSaving(false)
    }
  }

  async function onReturn() {
    if (!selected) return
    if (!govRemarks.trim()) {
      setActionError('Return remarks are required')
      return
    }
    setSaving(true)
    setActionError(null)
    try {
      const updated = await returnScorecardForChanges(selected.id, govRemarks.trim())
      setSelected(updated)
      await refreshSelected(updated.id)
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : e instanceof Error ? e.message : 'Return failed')
    } finally {
      setSaving(false)
    }
  }

  async function onActivate() {
    if (!selected) return
    setSaving(true)
    setActionError(null)
    try {
      const updated = await activateScorecard(selected.id)
      setSelected(updated)
      await refreshSelected(updated.id)
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : e instanceof Error ? e.message : 'Activate failed')
    } finally {
      setSaving(false)
    }
  }

  async function onLoadReviewPackage() {
    if (!selected) return
    setActionError(null)
    try {
      setReviewPackage(await getScorecardReviewPackage(selected.id))
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : e instanceof Error ? e.message : 'Review package failed')
    }
  }

  async function onLoadPolicySuggestions() {
    setActionError(null)
    try {
      const ids = policySuggestRaw
        .split(/[\n,]+/)
        .map((s) => s.trim())
        .filter(Boolean)
      const res = await suggestScorecardFactorsFromPolicy(ids)
      setPolicySuggestions(res.suggestions ?? [])
    } catch (e) {
      setActionError(
        e instanceof ApiError ? e.message : e instanceof Error ? e.message : 'Suggest from policy failed',
      )
    }
  }

  function addSuggestedFactor(s: Record<string, unknown>) {
    const legacy =
      (s.legacyScorecardKey != null ? String(s.legacyScorecardKey) : null) ||
      String(s.canonicalParameterId ?? '').replace(/\./g, '_').toUpperCase()
    if (grid.some((r) => r.parameter === legacy || r.canonicalParameterId === s.canonicalParameterId)) {
      return
    }
    const source = String(s.suggestedScorecardSource ?? 'SCORECARD')
    setGrid((g) => [
      ...g,
      {
        id: `r${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
        parameter: legacy,
        source,
        condition: defaultConditionForParam(paramDef(source, legacy)),
        weight: 1,
        score: 20,
        canonicalParameterId: String(s.canonicalParameterId),
        canonicalDefinitionVersion: Number(s.canonicalDefinitionVersion) || 1,
        mappingStatus: s.legacyScorecardKey ? 'EXACT' : 'SAFE_ALIAS',
        legacyParameterKey: legacy,
        factorLabel: String(s.businessName ?? legacy),
        missingData: 'REQUIRED',
      },
    ])
  }

  const showForm = selected !== null || isCreating
  const status = (selected?.status ?? (isCreating ? 'DRAFT' : 'DRAFT')).toUpperCase()
  const isActiveImmutable = Boolean(
    !isCreating && (selected?.active || status === 'ACTIVE'),
  )
  const isGovernanceFrozen = Boolean(
    !isCreating && (status === 'IN_REVIEW' || status === 'APPROVED'),
  )
  const canEditDraft = Boolean(isCreating || status === 'DRAFT')
  const primaryAction = selected?.primaryAction?.action

  function statusBadge(st: string, isActiveFlag: boolean) {
    const s = st.toUpperCase()
    if (isActiveFlag || s === 'ACTIVE') return <span className="bt-badge bt-badge-green">ACTIVE</span>
    if (s === 'IN_REVIEW') return <span className="bt-badge bt-badge-amber">IN REVIEW</span>
    if (s === 'APPROVED') return <span className="bt-badge bt-badge-blue">APPROVED</span>
    if (s === 'RETIRED') return <span className="bt-badge bt-badge-gray">RETIRED</span>
    return <span className="bt-badge bt-badge-gray">DRAFT</span>
  }

  return (
    <div>
      <PageHeader
        title="Live Scorecards"
        description="Current LOS production configuration. Structured parameter scoring with hard rules and approval thresholds. Matching scorecards run before legacy rule sets (higher priority wins)."
      />
      <AdministrationWorkspaceNav />

      {loading && <LoadingState label="Loading scorecards…" />}
      {loadError && <ErrorState message={loadError} />}

      {rows && !loading && (
        <MasterDetailLayout>
          <MasterListPanel
            title="Scorecards"
            count={filteredRows.length}
            search={listSearch}
            onSearchChange={setListSearch}
            searchPlaceholder="Search scorecards…"
            action={
              <button type="button" onClick={startNew} className="bt-btn bt-btn-primary bt-btn-sm">
                New scorecard
              </button>
            }
            empty={
              filteredRows.length === 0 && !isCreating ? (
                <div className="bt-master-list-empty">
                  {rows.length === 0 ? 'No scorecards yet. Create one to get started.' : 'No scorecards match your search.'}
                </div>
              ) : undefined
            }
          >
            {filteredRows.map((r) => (
              <MasterListItem
                key={r.id}
                active={selected?.id === r.id && !isCreating}
                onClick={() => apply(r)}
                avatar={r.name}
                title={r.name}
                subtitle={`Priority ${r.priority} · v${r.version}`}
                meta={statusBadge(r.status ?? (r.active ? 'ACTIVE' : 'DRAFT'), r.active)}
                tags={
                  <>
                    <span className="bt-tag">{BORROWER_TYPE_LABELS[r.borrowerType as BorrowerType] ?? r.borrowerType}</span>
                    <span className="bt-tag">{loanProductLabel(r.loanProduct)}</span>
                  </>
                }
              />
            ))}
          </MasterListPanel>

          <div>
            {showForm ? (
              <DetailPanel
                title={isCreating ? 'New scorecard' : name}
                description="Configure parameters, hard rules, and decision thresholds for this segment."
                badge={
                  !isCreating && selected
                    ? statusBadge(selected.status ?? 'DRAFT', selected.active)
                    : isCreating
                      ? statusBadge('DRAFT', false)
                      : undefined
                }
                footer={
                  <DetailActions>
                    <button type="button" className="bt-btn bt-btn-ghost" onClick={() => setMapOpen(true)}>
                      View mapping
                    </button>
                    {isCreating || (canEditDraft && !isActiveImmutable) ? (
                      <button type="button" onClick={() => void onSave()} disabled={saving} className="bt-btn bt-btn-secondary">
                        {saving ? 'Saving…' : isCreating ? 'Create draft' : 'Save'}
                      </button>
                    ) : null}
                    {!isCreating && selected && primaryAction === 'SUBMIT_FOR_REVIEW' ? (
                      <button type="button" onClick={() => void onSubmitReview()} disabled={saving} className="bt-btn bt-btn-primary">
                        {saving ? 'Submitting…' : 'Submit for Review'}
                      </button>
                    ) : null}
                    {!isCreating && selected && primaryAction === 'CHECKER_DECIDE' ? (
                      <>
                        <button type="button" onClick={() => void onApprove()} disabled={saving} className="bt-btn bt-btn-primary">
                          {saving ? 'Approving…' : 'Approve'}
                        </button>
                        <button type="button" onClick={() => void onReturn()} disabled={saving} className="bt-btn bt-btn-secondary">
                          Return for changes
                        </button>
                      </>
                    ) : null}
                    {!isCreating && selected && primaryAction === 'ACTIVATE' ? (
                      <button type="button" onClick={() => void onActivate()} disabled={saving} className="bt-btn bt-btn-primary">
                        {saving ? 'Activating…' : 'Activate'}
                      </button>
                    ) : null}
                    {!isCreating && selected && primaryAction === 'CREATE_NEW_VERSION' ? (
                      <button
                        type="button"
                        onClick={() => void onCreateNewVersion()}
                        disabled={saving}
                        className="bt-btn bt-btn-primary"
                      >
                        {saving ? 'Creating…' : 'Create new version'}
                      </button>
                    ) : null}
                    {selected && !isCreating && canEditDraft ? (
                      <button type="button" onClick={() => void onDelete()} className="bt-btn bt-btn-secondary text-rose-700">
                        Delete
                      </button>
                    ) : null}
                    {!isCreating && selected ? (
                      <button type="button" onClick={startNew} className="bt-btn bt-btn-ghost">
                        New instead
                      </button>
                    ) : null}
                  </DetailActions>
                }
              >
                {actionError ? <BtAlert tone="error">{actionError}</BtAlert> : null}
                {isActiveImmutable ? (
                  <BtAlert tone="warning">
                    ACTIVE scorecard is immutable. Use Create new version for DRAFT v{(selected?.version ?? 1) + 1};
                    live scoring stays on this version until the next version is activated.
                  </BtAlert>
                ) : null}
                {isGovernanceFrozen ? (
                  <BtAlert tone="warning">
                    {status === 'IN_REVIEW'
                      ? 'In review — content frozen for checker. Approve or Return for changes.'
                      : 'Approved — content frozen. Activate to go live, or Return for changes.'}
                  </BtAlert>
                ) : null}

                <DetailSection title="Identity & scope">
                  <div className="bt-form-grid">
                    <FormField label="Name" className="sm:col-span-2">
                      <input className="bt-input" value={name} onChange={(e) => setName(e.target.value)} />
                    </FormField>
                    <FormField label="Borrower type">
                      <select className="bt-input" value={borrowerType} onChange={(e) => setBorrowerType(e.target.value as BorrowerType)}>
                        {BORROWER_TYPES.map((b) => (
                          <option key={b} value={b}>
                            {BORROWER_TYPE_LABELS[b]}
                          </option>
                        ))}
                      </select>
                    </FormField>
                    <FormField label="Loan product">
                      <select className="bt-input" value={loanProduct} onChange={(e) => setLoanProduct(e.target.value)}>
                        {LOAN_PRODUCT_CODES.map((c) => (
                          <option key={c} value={c}>
                            {LOAN_PRODUCT_LABELS[c]}
                          </option>
                        ))}
                        {loanProduct && !isLoanProductCode(loanProduct) ? (
                          <option value={loanProduct}>{loanProductLabel(loanProduct)} (legacy)</option>
                        ) : null}
                      </select>
                    </FormField>
                    <FormField label="Version">
                      <input type="number" className="bt-input" value={version} onChange={(e) => setVersion(Number(e.target.value) || 1)} />
                    </FormField>
                    <FormField label="Priority" hint="Higher priority scorecards are evaluated first">
                      <input type="number" className="bt-input" value={priority} onChange={(e) => setPriority(Number(e.target.value) || 0)} />
                    </FormField>
                    <FormField label="Min amount" className="sm:col-span-2">
                      <AmountInputSplit
                        value={minAmount}
                        onChange={setMinAmount}
                        placeholder="Optional"
                        aria-label="Minimum amount"
                      />
                    </FormField>
                    <FormField label="Max amount" className="sm:col-span-2">
                      <AmountInputSplit
                        value={maxAmount}
                        onChange={setMaxAmount}
                        placeholder="Optional"
                        aria-label="Maximum amount"
                      />
                    </FormField>
                    <FormField label="Geography (optional)" className="sm:col-span-2">
                      <div className="flex flex-wrap gap-2">
                        <input className="bt-input flex-1" placeholder="State" value={geoState} onChange={(e) => setGeoState(e.target.value)} />
                        <input className="bt-input flex-1" placeholder="City" value={geoCity} onChange={(e) => setGeoCity(e.target.value)} />
                      </div>
                    </FormField>
                    <FormField label="Lifecycle status" className="sm:col-span-2">
                      <div className="flex flex-wrap items-center gap-2 pt-1">
                        {statusBadge(status, Boolean(selected?.active))}
                        <span className="text-xs text-slate-500">
                          DRAFT → Submit → Checker → Approve → Activate. Runtime uses ACTIVE only.
                        </span>
                      </div>
                    </FormField>
                  </div>
                </DetailSection>

                {!isCreating && selected ? (
                  <DetailSection title="Governance" description="Maker-checker evidence and one primary next action.">
                    <textarea
                      className="bt-input text-sm"
                      rows={2}
                      placeholder="Remarks (required when returning for changes)"
                      value={govRemarks}
                      onChange={(e) => setGovRemarks(e.target.value)}
                    />
                    <div className="mt-2 flex flex-wrap gap-2">
                      <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" onClick={() => void onLoadReviewPackage()}>
                        Load checker review package
                      </button>
                    </div>
                    {selected.governanceJson ? (
                      <pre className="mt-2 max-h-40 overflow-auto rounded border border-slate-200 bg-white p-2 text-[11px] text-slate-700">
                        {JSON.stringify(
                          {
                            submittedBy: selected.governanceJson.submittedBy,
                            submittedAt: selected.governanceJson.submittedAt,
                            checkerDecision: selected.governanceJson.checkerDecision,
                            approvedBy: selected.governanceJson.approvedBy,
                            approvedAt: selected.governanceJson.approvedAt,
                            remarks: selected.governanceJson.remarks,
                            lastPreview: selected.governanceJson.lastPreview,
                            activatedBy: selected.governanceJson.activatedBy,
                          },
                          null,
                          2,
                        )}
                      </pre>
                    ) : null}
                    {reviewPackage ? (
                      <div className="mt-3 space-y-2 text-sm">
                        <div className="font-semibold text-slate-800">Review summary</div>
                        <pre className="max-h-64 overflow-auto rounded border border-slate-200 bg-slate-50 p-2 text-[11px]">
                          {JSON.stringify(
                            {
                              executionReadiness: reviewPackage.executionReadiness,
                              diffVsPrevious: reviewPackage.diffVsPrevious,
                              factors: reviewPackage.factors,
                              thresholds: reviewPackage.thresholds,
                            },
                            null,
                            2,
                          )}
                        </pre>
                      </div>
                    ) : null}
                  </DetailSection>
                ) : null}

                <DetailSection title="Decision thresholds" description="Normalized score as % of maximum points">
                  <div className="flex flex-wrap gap-4">
                    <FormField label="Auto-approve at ≥ (%)">
                      <input type="number" className="bt-input w-24" value={approveMin} onChange={(e) => setApproveMin(Number(e.target.value) || 0)} />
                    </FormField>
                    <FormField label="Manual review at ≥ (%)">
                      <input type="number" className="bt-input w-24" value={manualMin} onChange={(e) => setManualMin(Number(e.target.value) || 0)} />
                    </FormField>
                  </div>
                </DetailSection>

                <DetailSection
                  title="Factors"
                  description="GACAT Source → Parameter → exclusive bands → Points → Missing-data policy. Hard eligibility stays in Policy / Underwriting Rules."
                >
                  <ScorecardParameterEditor
                    rows={grid}
                    onChange={setGrid}
                    parameterDefs={parameterDefs}
                    onParameterDefsChange={setParameterDefs}
                    loanProduct={loanProduct}
                  />
                  <button type="button" className="bt-btn bt-btn-ghost bt-btn-sm mt-3" onClick={() => setGrid((g) => [...g, newRow()])}>
                    + Add legacy / custom band
                  </button>
                </DetailSection>

                <DetailSection
                  title="Suggested from policy"
                  description="Read-only suggestions from an APPROVED policy’s parameters. Add or Ignore — never auto-created; thresholds are not copied."
                >
                  <textarea
                    className="bt-input font-mono text-xs"
                    rows={3}
                    value={policySuggestRaw}
                    onChange={(e) => setPolicySuggestRaw(e.target.value)}
                    placeholder="bureau.score&#10;obligation.ratio&#10;application.business_vintage_months"
                  />
                  <button
                    type="button"
                    className="bt-btn bt-btn-secondary bt-btn-sm mt-2"
                    onClick={() => void onLoadPolicySuggestions()}
                  >
                    Load suggestions
                  </button>
                  {policySuggestions.length > 0 ? (
                    <ul className="mt-3 space-y-2">
                      {policySuggestions.map((s) => (
                        <li
                          key={String(s.canonicalParameterId)}
                          className="flex flex-wrap items-center justify-between gap-2 rounded border border-slate-200 bg-white px-3 py-2 text-sm"
                        >
                          <span>
                            <strong>{String(s.businessName)}</strong>
                            <span className="ml-2 text-xs text-slate-500">{String(s.canonicalParameterId)}</span>
                          </span>
                          <span className="flex gap-2">
                            <button
                              type="button"
                              className="bt-btn bt-btn-primary bt-btn-sm"
                              disabled={isActiveImmutable}
                              onClick={() => addSuggestedFactor(s)}
                            >
                              Add
                            </button>
                            <button
                              type="button"
                              className="bt-btn bt-btn-ghost bt-btn-sm"
                              onClick={() =>
                                setPolicySuggestions((prev) =>
                                  prev.filter((x) => x.canonicalParameterId !== s.canonicalParameterId),
                                )
                              }
                            >
                              Ignore
                            </button>
                          </span>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="mt-2 text-xs text-slate-500">No suggestions loaded.</p>
                  )}
                </DetailSection>

                <DetailSection title="Test / preview" description="Uses ScorecardPolicyEngine / safety scoring. Does not mutate applications.">
                  <textarea
                    className="bt-input font-mono text-xs"
                    rows={3}
                    value={previewInputs}
                    onChange={(e) => setPreviewInputs(e.target.value)}
                  />
                  <button
                    type="button"
                    className="bt-btn bt-btn-secondary bt-btn-sm mt-2"
                    disabled={previewBusy}
                    onClick={() => void onPreview()}
                  >
                    {previewBusy ? 'Running…' : 'Run preview'}
                  </button>
                  {previewResult ? (
                    <div className="mt-3 overflow-x-auto rounded border border-slate-200 bg-white p-3 text-xs">
                      <div className="mb-2 font-semibold text-slate-800">
                        Earned {String(previewResult.earnedPoints)} / Max {String(previewResult.maxPoints)} ·{' '}
                        {String(previewResult.normalizedPercent)}% · {String(previewResult.policyDecision)}
                      </div>
                      <pre className="max-h-64 overflow-auto whitespace-pre-wrap text-[11px] text-slate-700">
                        {JSON.stringify(previewResult.parameterResults ?? previewResult.evidence ?? previewResult, null, 2)}
                      </pre>
                    </div>
                  ) : null}
                </DetailSection>

                <DetailSection
                  title="Hard rules (scorecard)"
                  description="Distinct from scoring factors. Hard eligibility prefers Underwriting Rules / Policy; legacy scorecard hard rules remain executable."
                >
                  <div className="rounded border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950">
                    Hard rule ≠ scoring factor. Example: Bureau Score &lt;600 → Reject (hard) vs bands → points (soft).
                    Same canonical parameter may appear in both; value is not calculated twice.
                  </div>
                  {hards.length > 0 ? (
                    <ul className="mt-3 list-disc space-y-1 pl-5 text-sm text-slate-700">
                      {hards.map((h) => (
                        <li key={h.id}>
                          Hard rule: {h.source} / {h.parameter} / {h.condition} → {h.decision}
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="mt-3 text-sm text-slate-500">No legacy scorecard hard rules are stored on this scorecard.</p>
                  )}
                </DetailSection>
              </DetailPanel>
            ) : (
              <DetailEmptyState
                title="Select a scorecard"
                description="Choose a scorecard from the list to view and edit it, or create a new one."
                action={
                  <button type="button" onClick={startNew} className="bt-btn bt-btn-primary">
                    New scorecard
                  </button>
                }
              />
            )}
          </div>
        </MasterDetailLayout>
      )}
      <EditorModal
        open={mapOpen && showForm}
        title="Scorecard mapping"
        description="Visual, read-only view of match scope, parameters, hard rules, and decision thresholds."
        onClose={() => setMapOpen(false)}
      >
        <UnderwritingPolicyMapView
          kind="scorecard"
          scope={{
            name,
            borrowerType,
            loanProduct,
            priority,
            active,
            minAmount: minAmount || null,
            maxAmount: maxAmount || null,
            geography: [geoState, geoCity].filter(Boolean).join(' / ') || undefined,
            version,
          }}
          hardRules={hards}
          scoreParams={grid.map((r) => ({
            ...r,
            formula: r.formula ?? (r.parameter ? parameterDefs[r.parameter]?.formula : undefined),
          }))}
          thresholds={{ approveMin, manualMin }}
        />
      </EditorModal>
    </div>
  )
}
