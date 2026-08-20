import { useCallback, useEffect, useMemo, useState } from 'react'
import { listWorkflowEventTemplateMappings } from '@/api/workflowEventTemplateMappings'
import {
  activateWorkflow,
  createNewWorkflowVersion,
  createWorkflow,
  deactivateWorkflow,
  deleteWorkflow,
  listWorkflows,
  updateWorkflow,
} from '@/api/workflows'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { AdministrationWorkspaceNav } from '@/components/workspace/AdministrationWorkspaceNav'
import {
  DetailEmptyState,
  DetailPanel,
  DetailSection,
  MasterDetailLayout,
  MasterListItem,
  MasterListPanel,
} from '@/components/ui/AdminLayout'
import { WorkflowStepEditorPanel } from '@/components/WorkflowStepEditorPanel'
import { WorkflowIntakeRulesPanel } from '@/components/workflow/WorkflowIntakeRulesPanel'
import {
  jsonToRows,
  rowsToJson,
  validateRows,
  VkycConditionBuilder,
  type VkycConditionRow,
} from '@/components/VkycConditionBuilder'
import { ApiError } from '@/api/http'
import { BORROWER_TYPE_LABELS, BORROWER_TYPE_ORDER } from '@/catalog/borrowerTypes'
import { isLoanProductCode, LOAN_PRODUCT_CODES, LOAN_PRODUCT_LABELS, loanProductLabel } from '@/catalog/loanProducts'
import {
  DEFAULT_LMS_TENURE_UNIT,
  LMS_TENURE_UNIT_OPTIONS,
  workflowUsesPlpLmsConfig,
} from '@/catalog/lmsTenureUnits'
import { formatInstant } from '@/lib/format'
import {
  filterWorkflowsForLenderUi,
} from '@/lib/lenderConfigVisibility'
import { isClientLenderSurface } from '@/lib/runtimeEnv'
import {
  deriveProcessNotificationsFromSteps,
  parseProcessNotifications,
  processNotificationsToJsonArray,
  type ProcessNotificationConfig,
} from '@/lib/workflowProcessNotifications'
import {
  createEmptyVisualStep,
  parseWorkflowStepsFromJson,
  visualStepsToJsonArray,
  type VisualWorkflowStep,
} from '@/lib/workflowVisual'
import { staticWorkflowEventTemplateMappings } from '@/lib/workflowEventTemplateMappingsFallback'
import type { WorkflowEventTemplateMappingDto } from '@/api/workflowEventTemplateMappings'
import type { WorkflowConfigRequest, WorkflowConfigResponse, WorkflowIntakeConfig, WorkflowIntakeSegment } from '@/types/workflow'
import { defaultWorkflowDrivenIntakeConfig, intakeConfigFromApi } from '@/lib/workflow/workflowIntakeRules'
import {
  NOTIFICATION_PROCESS_BUSINESS_LABELS,
  projectLenderJourney,
  workflowSetupStatus,
} from '@/lib/workflow/workflowLenderJourney'
import {
  formatLenderWorkflowVersionLabel,
  lenderFacingWorkflowVersion,
} from '@/lib/workflow/workflowLenderVersion'
import { getDataParametersOverview } from '@/api/liveReadiness'
import { Link } from 'react-router-dom'
import type { BorrowerType } from '@/types/createApplication'

const BORROWER_TYPES: BorrowerType[] = [...BORROWER_TYPE_ORDER]

type WorkflowDetailTab =
  | 'overview'
  | 'application'
  | 'kyc'
  | 'dataCollection'
  | 'journey'
  | 'notifications'
  | 'advanced'

export function WorkflowsPage() {
  const [list, setList] = useState<WorkflowConfigResponse[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const [selected, setSelected] = useState<WorkflowConfigResponse | null>(null)
  /** Draft new row before first save (POST) */
  const [isCreating, setIsCreating] = useState(false)
  const [name, setName] = useState('')
  const [borrowerType, setBorrowerType] = useState<BorrowerType>('INDIVIDUAL')
  const [loanProduct, setLoanProduct] = useState('')
  const [lmsProductCode, setLmsProductCode] = useState('')
  const [lmsTenureUnit, setLmsTenureUnit] = useState(DEFAULT_LMS_TENURE_UNIT)
  const [intakeSegment, setIntakeSegment] = useState<WorkflowIntakeSegment>('BORROWER')
  const [creditVintage, setCreditVintage] = useState('ANY')
  const [bureauEnabled, setBureauEnabled] = useState(true)
  const [autoPullBureauAfterKycSuccess, setAutoPullBureauAfterKycSuccess] = useState(true)
  const [intakeIdentitySchemaJson, setIntakeIdentitySchemaJson] = useState('[]')
  const [intakeConfig, setIntakeConfig] = useState<WorkflowIntakeConfig>(() => defaultWorkflowDrivenIntakeConfig())
  const [detailTab, setDetailTab] = useState<WorkflowDetailTab>('overview')
  const [visualSteps, setVisualSteps] = useState<VisualWorkflowStep[]>([])
  const [processNotifications, setProcessNotifications] = useState<ProcessNotificationConfig[]>([])
  const [stepsJson, setStepsJson] = useState('[]')
  const [workflowPosition, setWorkflowPosition] = useState('BEFORE_ESIGN')
  const [vkycConditionRows, setVkycConditionRows] = useState<VkycConditionRow[]>([])
  const [vkycTriggerConditionJson, setVkycTriggerConditionJson] = useState('[]')
  const [vkycConditionJsonError, setVkycConditionJsonError] = useState<string | null>(null)
  const [sourceCapabilitySummary, setSourceCapabilitySummary] = useState<Record<string, unknown>[]>([])

  /** Catalog for workflow notification template picker (proxied via los-core → notification-service). */
  const [templateMappings, setTemplateMappings] = useState<WorkflowEventTemplateMappingDto[]>(() =>
    staticWorkflowEventTemplateMappings(),
  )

  const [actionError, setActionError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [toggling, setToggling] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [listSearch, setListSearch] = useState('')

  function messageForWorkflowDeleteError(e: unknown): string {
    if (e instanceof ApiError && e.reason === 'WORKFLOW_ACTIVE_DELETE_FORBIDDEN') {
      return 'Deactivate this workflow before deleting it.'
    }
    if (e instanceof ApiError) {
      return (e.serverMessage || e.message || '').trim() || 'Delete failed'
    }
    if (e instanceof Error && e.message) return e.message
    return 'Delete failed'
  }

  const load = useCallback(async () => {
    setLoadError(null)
    setLoading(true)
    try {
      const data = await listWorkflows()
      setList(data)
    } catch (e) {
      setList(null)
      setLoadError(e instanceof Error ? e.message : 'Failed to load workflows')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- load is async; setState after await
    void load()
  }, [load])

  const [showPlatformCatalogue, setShowPlatformCatalogue] = useState(false)
  const [linkedWorkflowIds, setLinkedWorkflowIds] = useState<Set<string>>(new Set())

  useEffect(() => {
    if (!isClientLenderSurface() || showPlatformCatalogue) return
    let cancelled = false
    void (async () => {
      try {
        const { listCustomerCategories } = await import('@/api/customerCategories')
        const cats = await listCustomerCategories()
        if (cancelled) return
        const ids = new Set<string>()
        for (const c of cats) {
          if (c.workflowId) ids.add(String(c.workflowId))
        }
        setLinkedWorkflowIds(ids)
      } catch {
        if (!cancelled) setLinkedWorkflowIds(new Set())
      }
    })()
    return () => {
      cancelled = true
    }
  }, [showPlatformCatalogue])

  const filteredList = useMemo(() => {
    const base = filterWorkflowsForLenderUi(list ?? [], {
      showPlatformCatalogue,
      linkedWorkflowIds,
    })
    const q = listSearch.trim().toLowerCase()
    if (!q) return base
    return base.filter((w) => {
      const borrowerLabel = (BORROWER_TYPE_LABELS[w.borrowerType as BorrowerType] ?? w.borrowerType).toLowerCase()
      const productLabel = loanProductLabel(w.loanProduct).toLowerCase()
      const segmentLabel = (w.intakeSegment === 'ANCHOR' ? 'anchor' : 'borrower').toLowerCase()
      return (
        w.name.toLowerCase().includes(q)
        || w.borrowerType.toLowerCase().includes(q)
        || borrowerLabel.includes(q)
        || w.loanProduct.toLowerCase().includes(q)
        || productLabel.includes(q)
        || segmentLabel.includes(q)
      )
    })
  }, [list, listSearch, showPlatformCatalogue, linkedWorkflowIds])

  useEffect(() => {
    let cancelled = false
    async function hydrateTemplateCatalog(): Promise<void> {
      try {
        const rows = await listWorkflowEventTemplateMappings()
        if (!cancelled && rows?.length) {
          setTemplateMappings(rows)
        }
      } catch {
        if (!cancelled) setTemplateMappings(staticWorkflowEventTemplateMappings())
      }
    }
    void hydrateTemplateCatalog()
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    void (async () => {
      try {
        const ov = await getDataParametersOverview()
        if (cancelled) return
        const rows = Array.isArray(ov.sourceCapabilitySummary)
          ? (ov.sourceCapabilitySummary as Record<string, unknown>[])
          : []
        setSourceCapabilitySummary(rows)
      } catch {
        if (!cancelled) setSourceCapabilitySummary([])
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  function resetFormToNew() {
    setSuccessMessage(null)
    setSelected(null)
    setIsCreating(true)
    setName('')
    setBorrowerType('INDIVIDUAL')
    setLoanProduct('PERSONAL_LOAN')
    setLmsProductCode('')
    setLmsTenureUnit(DEFAULT_LMS_TENURE_UNIT)
    setIntakeSegment('BORROWER')
    setCreditVintage('ANY')
    setBureauEnabled(true)
    setAutoPullBureauAfterKycSuccess(true)
    setIntakeIdentitySchemaJson('[]')
    setIntakeConfig(defaultWorkflowDrivenIntakeConfig())
    setDetailTab('overview')
    setWorkflowPosition('BEFORE_ESIGN')
    setVkycConditionRows([])
    setVkycTriggerConditionJson('[]')
    setVkycConditionJsonError(null)
    const v = [createEmptyVisualStep()]
    setVisualSteps(v)
    setProcessNotifications([])
    setStepsJson(JSON.stringify(visualStepsToJsonArray(v), null, 2))
    setActionError(null)
  }

  function applySelection(
    w: WorkflowConfigResponse,
    options?: { preserveTab?: boolean; intakeFallback?: WorkflowIntakeConfig },
  ) {
    setSuccessMessage(null)
    setSelected(w)
    setIsCreating(false)
    setName(w.name)
    setBorrowerType((w.borrowerType as BorrowerType) || 'INDIVIDUAL')
    setLoanProduct(w.loanProduct)
    setLmsProductCode(w.lmsProductCode?.trim() || '')
    setLmsTenureUnit(w.lmsTenureUnit?.trim() || DEFAULT_LMS_TENURE_UNIT)
    setIntakeSegment(w.intakeSegment === 'ANCHOR' ? 'ANCHOR' : 'BORROWER')
    setCreditVintage(w.creditVintage?.trim() || 'ANY')
    setBureauEnabled(w.bureauEnabled !== false)
    setAutoPullBureauAfterKycSuccess(w.autoPullBureauAfterKycSuccess !== false)
    setIntakeIdentitySchemaJson(JSON.stringify(w.intakeIdentitySchema ?? [], null, 2))
    setIntakeConfig(intakeConfigFromApi(w, options?.intakeFallback))
    if (!options?.preserveTab) setDetailTab('overview')
    setWorkflowPosition(w.workflowPosition ?? 'BEFORE_ESIGN')
    const existingConditions = (w.vkycTriggerCondition ?? []) as unknown
    setVkycConditionRows(jsonToRows(existingConditions))
    setVkycTriggerConditionJson(JSON.stringify(existingConditions, null, 2))
    setVkycConditionJsonError(null)
    const raw = w.steps
    const arr: Record<string, unknown>[] = Array.isArray(raw)
      ? (raw as unknown[]).map((s) => (s && typeof s === 'object' && !Array.isArray(s) ? (s as Record<string, unknown>) : {}))
      : []
    const vis = parseWorkflowStepsFromJson(arr)
    setVisualSteps(vis)
    const processRows = parseProcessNotifications(w.processNotificationMappings ?? [])
    setProcessNotifications(processRows.length > 0 ? processRows : deriveProcessNotificationsFromSteps(vis))
    setStepsJson(JSON.stringify(visualStepsToJsonArray(vis), null, 2))
    setActionError(null)
  }

  function cancelCreate() {
    setIsCreating(false)
    setActionError(null)
  }

  function syncJsonFromVisual() {
    setStepsJson(JSON.stringify(visualStepsToJsonArray(visualSteps), null, 2))
  }

  function applyAdvancedToVisual() {
    try {
      const parsed = JSON.parse(stepsJson) as unknown
      if (!Array.isArray(parsed)) {
        setActionError('Steps must be a JSON array of step objects.')
        return
      }
      setVisualSteps(parseWorkflowStepsFromJson(parsed as Record<string, unknown>[]))
      setActionError(null)
    } catch {
      setActionError('Invalid JSON. Fix the steps field and try again.')
    }
  }

  async function onSave() {
    setActionError(null)
    setSuccessMessage(null)
    const steps = visualStepsToJsonArray(visualSteps)
    // The structured builder is the source of truth; the JSON textarea only
    // wins when the user has explicitly synced from advanced mode and there
    // is no JSON parse error pending. This keeps backend compatibility intact
    // while preventing malformed JSON from sneaking in.
    const rowErrors = validateRows(vkycConditionRows)
    if (Object.keys(rowErrors).length > 0) {
      setActionError('Fix invalid VKYC trigger conditions before saving.')
      return
    }
    let vkycConditions: Record<string, unknown>[] = rowsToJson(vkycConditionRows) as Record<string, unknown>[]
    if (vkycConditionRows.length === 0 && vkycTriggerConditionJson.trim() !== '' && vkycTriggerConditionJson.trim() !== '[]') {
      try {
        const parsed = JSON.parse(vkycTriggerConditionJson) as unknown
        if (!Array.isArray(parsed)) {
          setActionError('VKYC trigger condition JSON must be an array.')
          return
        }
        vkycConditions = parsed as Record<string, unknown>[]
      } catch {
        setActionError('Invalid VKYC trigger condition JSON.')
        return
      }
    }
    let intakeIdentitySchema: Record<string, unknown>[] | undefined
    try {
      const parsed = JSON.parse(intakeIdentitySchemaJson.trim() || '[]') as unknown
      if (!Array.isArray(parsed)) {
        setActionError('Anchor identity schema must be a JSON array of field objects (or [] for none).')
        return
      }
      intakeIdentitySchema = parsed as Record<string, unknown>[]
    } catch {
      setActionError('Invalid anchor identity schema JSON.')
      return
    }

    const body: WorkflowConfigRequest = {
      name: name.trim() || 'Unnamed workflow',
      borrowerType,
      loanProduct: loanProduct.trim() || 'PERSONAL_LOAN',
      lmsProductCode: workflowUsesPlpLmsConfig(loanProduct, intakeSegment) ? undefined : (lmsProductCode.trim() || undefined),
      lmsTenureUnit: workflowUsesPlpLmsConfig(loanProduct, intakeSegment) ? undefined : lmsTenureUnit.trim() || DEFAULT_LMS_TENURE_UNIT,
      intakeSegment,
      creditVintage: creditVintage.trim() || 'ANY',
      bureauEnabled,
      autoPullBureauAfterKycSuccess,
      intakeIdentitySchema,
      intakeConfig: JSON.parse(JSON.stringify(intakeConfig)) as WorkflowIntakeConfig,
      steps,
      processNotificationMappings: processNotificationsToJsonArray(processNotifications),
      vkycTriggerCondition: vkycConditions,
      workflowPosition,
    }

    if (!loanProduct.trim() || !isLoanProductCode(loanProduct.trim())) {
      setActionError('Select a standard loan product (code) from the list.')
      return
    }

    setSaving(true)
    try {
      if (isCreating) {
        const created = await createWorkflow(body)
        setList((prev) => (prev ? [created, ...prev] : [created]))
        setIsCreating(false)
        applySelection(created)
      } else {
        if (!selected) return
        const immutable =
          selected.publicationStatus === 'ACTIVE' ||
          selected.publicationStatus === 'SUPERSEDED' ||
          selected.publicationStatus === 'RETIRED' ||
          (selected.active && selected.publicationStatus !== 'DRAFT')
        const updated = immutable
          ? await createNewWorkflowVersion(selected.id, body)
          : await updateWorkflow(selected.id, body)
        const merged: WorkflowConfigResponse = {
          ...updated,
          intakeConfig: intakeConfigFromApi(updated, body.intakeConfig),
        }
        setList((prev) => {
          const next = prev ? [...prev] : []
          const idx = next.findIndex((w) => w.id === merged.id)
          if (idx >= 0) next[idx] = merged
          else next.unshift(merged)
          return next
        })
        applySelection(merged, { preserveTab: true, intakeFallback: body.intakeConfig })
        if (immutable) {
          setSuccessMessage(`Created Workflow Version ${merged.version}. The previous version is unchanged.`)
        }
      }
    } catch (e) {
      const m = e instanceof ApiError ? e.message : e instanceof Error ? e.message : 'Save failed'
      setActionError(m || 'Save failed')
    } finally {
      setSaving(false)
    }
  }

  async function onActivate() {
    if (!selected) return
    setActionError(null)
    setSuccessMessage(null)
    setToggling(true)
    const id = selected.id
    try {
      await activateWorkflow(id)
      const data = await listWorkflows()
      setList(data)
      const w = data.find((x) => x.id === id)
      if (w) applySelection(w)
      else {
        setSelected(null)
        setIsCreating(false)
        setActionError('Workflow list was refreshed; selection was cleared because this workflow is no longer listed.')
      }
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Activate failed')
    } finally {
      setToggling(false)
    }
  }

  async function onDeactivate() {
    if (!selected) return
    setActionError(null)
    setSuccessMessage(null)
    setToggling(true)
    const id = selected.id
    try {
      await deactivateWorkflow(id)
      const data = await listWorkflows()
      setList(data)
      const w = data.find((x) => x.id === id)
      if (w) applySelection(w)
      else {
        setSelected(null)
        setIsCreating(false)
        setActionError('Workflow list was refreshed; selection was cleared because this workflow is no longer listed.')
      }
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Deactivate failed')
    } finally {
      setToggling(false)
    }
  }

  async function onConfirmDelete() {
    if (!selected) return
    if (selected.active) {
      setActionError('Deactivate this workflow before deleting it.')
      setShowDeleteConfirm(false)
      return
    }
    setActionError(null)
    setSuccessMessage(null)
    setDeleting(true)
    const idToDelete = selected.id
    try {
      await deleteWorkflow(idToDelete)
      const data = await listWorkflows()
      setList(data)
      setShowDeleteConfirm(false)
      setSelected(null)
      setIsCreating(false)
      setSuccessMessage('Workflow deleted')
    } catch (e) {
      setActionError(messageForWorkflowDeleteError(e))
    } finally {
      setDeleting(false)
    }
  }

  const showEditor = selected !== null || isCreating

  const journeyStages = useMemo(
    () =>
      projectLenderJourney({
        intakeConfig,
        steps: visualSteps,
        processNotifications,
      }),
    [intakeConfig, visualSteps, processNotifications],
  )

  const setupStatus = useMemo(
    () =>
      workflowSetupStatus({
        name,
        intakeConfig,
        steps: visualSteps,
        processNotifications,
      }),
    [name, intakeConfig, visualSteps, processNotifications],
  )

  const compactDataSources = useMemo(() => {
    const catalog: { id: string; label: string; match: RegExp }[] = [
      { id: 'bureau', label: 'Bureau', match: /bureau/i },
      { id: 'bank', label: 'Bank Statements', match: /bank|bsa|statement/i },
      { id: 'gst', label: 'GST', match: /^gst$|gst\b/i },
      { id: 'aa', label: 'Account Aggregator', match: /account aggregator|\baa\b/i },
      { id: 'kyc', label: 'KYC', match: /^kyc$/i },
    ]
    return catalog.map((c) => {
      const row = sourceCapabilitySummary.find((r) => c.match.test(String(r.source ?? '')))
      const platform = String(row?.platformLabel ?? row?.platformIntegration ?? '')
      const org = String(row?.yourOrganisationLabel ?? row?.yourOrganisation ?? '')
      const platformReady =
        /production ready|available|integrated/i.test(platform) ||
        String(row?.platformIntegration ?? '').toUpperCase() === 'PRODUCTION_READY'
      return {
        id: c.id,
        label: c.label,
        platformCapable: row ? platformReady || Boolean(row) : false,
        orgStatus: org,
        hasRow: Boolean(row),
      }
    })
  }, [sourceCapabilitySummary])

  return (
    <div>
      <PageHeader
        title="Workflows"
        description="Define how applications move from intake through identity checks, data collection, credit assessment, and later lending stages. Policy and Scorecard stay with Customer Category — not here."
      />
      <AdministrationWorkspaceNav />

      {loading && <LoadingState label="Loading workflows…" />}
      {loadError && <ErrorState message={loadError} />}

      {list && !loading && (
        <div>
          {isClientLenderSurface() ? (
            <label className="mb-3 flex items-center gap-2 text-xs text-slate-600">
              <input
                type="checkbox"
                checked={showPlatformCatalogue}
                onChange={(e) => setShowPlatformCatalogue(e.target.checked)}
              />
              Show platform / seed workflows (diagnostics)
            </label>
          ) : null}
          {successMessage ? (
            <p
              className="bt-alert bt-alert-success mb-4"
              role="status"
            >
              {successMessage}
            </p>
          ) : null}
          <MasterDetailLayout>
            <MasterListPanel
              title="Workflows"
              count={filteredList.length}
              search={listSearch}
              onSearchChange={setListSearch}
              searchPlaceholder="Search workflows…"
              action={
                <button type="button" onClick={resetFormToNew} className="bt-btn bt-btn-primary bt-btn-sm">
                  New workflow
                </button>
              }
              empty={
                filteredList.length === 0 && !isCreating ? (
                  <div className="bt-master-list-empty">
                    {list.length === 0
                      ? 'No workflows yet. Click "New workflow" to add one.'
                      : 'No workflows match your search.'}
                  </div>
                ) : undefined
              }
            >
              {filteredList.map((w) => (
                <MasterListItem
                  key={w.id}
                  active={selected?.id === w.id && !isCreating}
                  onClick={() => applySelection(w)}
                  avatar={w.name}
                  title={`${w.name} · v${lenderFacingWorkflowVersion(w)}`}
                  subtitle={formatLenderWorkflowVersionLabel(w)}
                  meta={
                    w.publicationStatus === 'ACTIVE' || w.active ? (
                      <span className="bt-badge bt-badge-green">{w.publicationStatus || 'ACTIVE'}</span>
                    ) : (
                      <span className="bt-badge bt-badge-gray">{w.publicationStatus || 'DRAFT'}</span>
                    )
                  }
                  tags={
                    <>
                      <span className="bt-tag">
                        {BORROWER_TYPE_LABELS[w.borrowerType as BorrowerType] ?? w.borrowerType}
                      </span>
                      <span className="bt-tag">{loanProductLabel(w.loanProduct)}</span>
                      <span className="bt-tag">{w.intakeSegment === 'ANCHOR' ? 'Anchor' : 'Borrower'}</span>
                    </>
                  }
                />
              ))}
            </MasterListPanel>

            {showEditor ? (
              <DetailPanel
                title={isCreating ? 'Create workflow' : name || 'Untitled workflow'}
                description={
                  isCreating
                    ? 'Name the journey and choose who it applies to, then save a draft. Configure Application, Identity & KYC, Data collection, Journey, and Notifications next.'
                    : selected?.active
                      ? 'Active — used by new applications when bound through an applicable Customer Category.'
                      : 'Draft — editable. Activate when ready to bind through a Customer Category.'
                }
                badge={
                  !isCreating && selected ? (
                    selected.active ? (
                      <span className="bt-badge bt-badge-green">Active</span>
                    ) : (
                      <span className="bt-badge bt-badge-gray">Draft</span>
                    )
                  ) : undefined
                }
              >
                {actionError ? (
                  <p className="bt-alert bt-alert-warning mb-4" role="alert">
                    {actionError}
                  </p>
                ) : null}
                <div className="mb-4 flex flex-wrap gap-1 border-b border-slate-200 pb-2" data-testid="workflow-lender-tabs">
                  {(
                    [
                      ['overview', 'Overview'],
                      ['application', 'Application'],
                      ['kyc', 'Identity & KYC'],
                      ['dataCollection', 'Data collection'],
                      ['journey', 'Journey'],
                      ['notifications', 'Notifications'],
                      ['advanced', 'Advanced'],
                    ] as const
                  ).map(([id, label]) => (
                    <button
                      key={id}
                      type="button"
                      onClick={() => setDetailTab(id)}
                      className={
                        detailTab === id
                          ? 'rounded-md bg-slate-800 px-3 py-1.5 text-sm font-medium text-white'
                          : 'rounded-md border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-50'
                      }
                    >
                      {label}
                    </button>
                  ))}
                </div>

                {detailTab === 'overview' ? (
                <DetailSection title="Overview">
                  <div className="grid gap-3 sm:grid-cols-2">
                    <label className="block text-sm text-slate-700 sm:col-span-2">
                      <span className="mb-1 block text-xs font-medium text-slate-500">Workflow name</span>
                      <input
                        className="bt-input w-full"
                        value={name}
                        onChange={(e) => setName(e.target.value)}
                        placeholder="Personal Loan – Individual Borrower"
                      />
                    </label>
                    <label className="block text-sm text-slate-700">
                      <span className="mb-1 block text-xs font-medium text-slate-500">Entity type</span>
                      <select
                        className="bt-input w-full"
                        value={borrowerType}
                        onChange={(e) => setBorrowerType(e.target.value as BorrowerType)}
                      >
                        {BORROWER_TYPES.map((bt) => (
                          <option key={bt} value={bt}>
                            {BORROWER_TYPE_LABELS[bt]}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label className="block text-sm text-slate-700">
                      <span className="mb-1 block text-xs font-medium text-slate-500">Loan product</span>
                      <select
                        className="bt-input w-full"
                        value={loanProduct}
                        onChange={(e) => setLoanProduct(e.target.value)}
                      >
                        {LOAN_PRODUCT_CODES.map((c) => (
                          <option key={c} value={c}>
                            {LOAN_PRODUCT_LABELS[c]}
                          </option>
                        ))}
                        {loanProduct && !isLoanProductCode(loanProduct) ? (
                          <option value={loanProduct}>{loanProductLabel(loanProduct)} (legacy value)</option>
                        ) : null}
                      </select>
                    </label>
                    <label className="block text-sm text-slate-700">
                      <span className="mb-1 block text-xs font-medium text-slate-500">Customer role</span>
                      <select
                        className="bt-input w-full"
                        value={intakeSegment}
                        onChange={(e) => setIntakeSegment(e.target.value as WorkflowIntakeSegment)}
                      >
                        <option value="BORROWER">Borrower</option>
                        <option value="ANCHOR">Anchor</option>
                      </select>
                    </label>
                    <label className="block text-sm text-slate-700">
                      <span className="mb-1 block text-xs font-medium text-slate-500">Credit Vintage</span>
                      <select
                        className="bt-input w-full"
                        value={creditVintage}
                        onChange={(e) => setCreditVintage(e.target.value)}
                      >
                        <option value="ANY">Any</option>
                        <option value="NEW">New</option>
                        <option value="EXISTING_CUSTOMER">Existing Customer</option>
                        <option value="EXISTING_CUSTOMER_OF_GROUP">Existing Customer of Group</option>
                      </select>
                    </label>
                    {!isCreating && selected ? (
                      <div className="rounded border border-slate-100 bg-slate-50 p-3 text-xs text-slate-700 sm:col-span-2">
                        <div>{formatLenderWorkflowVersionLabel(selected)}</div>
                        <div className="mt-1 text-slate-500">
                          {BORROWER_TYPE_LABELS[borrowerType] ?? borrowerType} · {loanProductLabel(loanProduct)} ·{' '}
                          {intakeSegment === 'ANCHOR' ? 'Anchor' : 'Borrower'}
                        </div>
                      </div>
                    ) : null}
                  </div>

                  {!isCreating ? (
                    <div className="mt-4 rounded-lg border border-slate-200 bg-white p-3" data-testid="workflow-setup-status">
                      <h3 className="text-sm font-semibold text-slate-900">Setup status</h3>
                      <p className="mt-1 text-xs text-slate-600">
                        {setupStatus.ready ? 'Ready to activate' : 'Needs attention before activate'}
                      </p>
                      <ul className="mt-2 space-y-1 text-xs text-slate-700">
                        {setupStatus.items.map((item) => (
                          <li key={item.id}>
                            {item.done ? '✓' : '○'} {item.label}
                            {item.optional ? ' (optional)' : ''}
                          </li>
                        ))}
                      </ul>
                      {!setupStatus.ready ? (
                        <ul className="mt-2 list-disc pl-4 text-xs text-amber-900">
                          {setupStatus.attention.map((a, i) => (
                            <li key={i}>{a}</li>
                          ))}
                        </ul>
                      ) : null}
                    </div>
                  ) : null}
                </DetailSection>
                ) : null}

                {detailTab === 'application' ? (
                <DetailSection title="Application">
                  <p className="mb-3 text-xs text-slate-600">
                    What information and documents should the applicant provide? Credit-policy data requirements are
                    decided by Policy — not duplicated here.
                  </p>
                  <WorkflowIntakeRulesPanel
                    intakeConfig={intakeConfig}
                    onChange={setIntakeConfig}
                    bureauEnabled={bureauEnabled}
                    autoPullBureauAfterKycSuccess={autoPullBureauAfterKycSuccess}
                    onBureauEnabledChange={setBureauEnabled}
                    onAutoPullBureauAfterKycSuccessChange={setAutoPullBureauAfterKycSuccess}
                    visualSteps={visualSteps}
                    hideBureauRequirementControls
                    hideIntakePolicyControl
                  />
                </DetailSection>
                ) : null}

                {detailTab === 'kyc' ? (
                <DetailSection title="Identity & KYC">
                <p className="mb-2 text-xs text-slate-600">
                  How should the applicant&apos;s identity and KYC be completed? Notifications are configured under
                  Notifications.
                </p>
                <WorkflowStepEditorPanel
                  steps={visualSteps}
                  onChange={setVisualSteps}
                  templateMappings={templateMappings}
                  processNotifications={processNotifications}
                  onProcessNotificationsChange={setProcessNotifications}
                  showIntakeOptions={intakeConfig.policy === 'WORKFLOW_DRIVEN'}
                  showProcessNotifications={false}
                  showSteps
                />
                </DetailSection>
                ) : null}

                {detailTab === 'dataCollection' ? (
                <DetailSection title="Data collection">
                  <p className="text-sm text-slate-700">
                    Data collection is driven automatically by your Credit Policy.
                  </p>
                  <p className="mt-2 text-sm text-slate-700">
                    When your policy requires bureau, banking, GST or other information, BillionTech collects it from
                    the available source after the required identity, consent and other prerequisites are completed.
                  </p>
                  <p className="mt-2 text-xs text-slate-600">
                    This workflow does not configure which underwriting data is required, and it is not where source
                    subscriptions are managed.
                  </p>
                  <div className="mt-4" data-testid="workflow-data-collection-sources">
                    <h3 className="text-sm font-semibold text-slate-900">Available data sources</h3>
                    <p className="mt-1 text-[11px] text-slate-500">
                      &quot;Platform capable&quot; means BillionTech can collect from this family — not that your
                      organisation is subscribed.
                    </p>
                    <ul className="mt-2 divide-y divide-slate-100 rounded border border-slate-200 bg-white">
                      {compactDataSources.map((s) => (
                        <li
                          key={s.id}
                          className="flex flex-wrap items-baseline justify-between gap-2 px-3 py-2 text-sm text-slate-800"
                        >
                          <span className="font-medium">{s.label}</span>
                          <span className="text-xs text-slate-600">
                            {s.hasRow
                              ? s.platformCapable
                                ? 'Platform capable'
                                : 'Not yet integrated'
                              : 'See Data & Parameters'}
                          </span>
                        </li>
                      ))}
                    </ul>
                    <p className="mt-3 text-sm">
                      <Link className="font-medium text-slate-900 underline underline-offset-2" to="/data-parameters">
                        Manage data sources →
                      </Link>
                    </p>
                  </div>
                </DetailSection>
                ) : null}

                {detailTab === 'journey' ? (
                <DetailSection title="Journey">
                  <p className="mb-3 text-xs text-slate-600">
                    How this lending journey moves from application through credit assessment and later stages.
                    Stages reflect capabilities already supported by the platform — not a second workflow engine.
                  </p>
                  <ol className="space-y-0" data-testid="workflow-journey">
                    {journeyStages.map((stage, idx) => (
                      <li key={stage.id} className="relative pb-4 pl-6">
                        {idx < journeyStages.length - 1 ? (
                          <span className="absolute left-[0.55rem] top-5 h-[calc(100%-0.5rem)] w-px bg-slate-200" />
                        ) : null}
                        <span className="absolute left-0 top-1 flex h-4 w-4 items-center justify-center rounded-full border border-slate-300 bg-white text-[9px] font-semibold text-slate-600">
                          {idx + 1}
                        </span>
                        <div className="rounded border border-slate-100 bg-white px-3 py-2">
                          <div className="flex flex-wrap items-baseline justify-between gap-2">
                            <h4 className="text-sm font-semibold text-slate-900">{stage.name}</h4>
                            <span className="text-[10px] font-medium uppercase tracking-wide text-slate-500">
                              {stage.statusLabel}
                            </span>
                          </div>
                          <p className="mt-0.5 text-xs text-slate-600">{stage.purpose}</p>
                          {stage.detail ? (
                            <p className="mt-1 text-xs text-slate-500">{stage.detail}</p>
                          ) : null}
                        </div>
                      </li>
                    ))}
                  </ol>
                  <div className="mt-2 rounded border border-slate-100 bg-slate-50 p-3 text-xs text-slate-600">
                    <p className="font-medium text-slate-800">Credit assessment</p>
                    <p className="mt-1">
                      Applications reaching this stage are evaluated against the Policy selected for the Customer
                      Category. Hard rules are evaluated and the linked Scorecard is applied where configured.
                      Unavailable required data follows existing fail-closed behaviour.
                    </p>
                    <p className="mt-2 text-slate-500">
                      Policy: selected through Customer Category · Scorecard: selected through the linked Policy
                    </p>
                  </div>
                </DetailSection>
                ) : null}

                {detailTab === 'notifications' ? (
                <DetailSection title="Notifications">
                  <p className="mb-2 text-xs text-slate-600">
                    Notify borrowers or staff at business events on this journey. Only supported channels and templates
                    are listed.
                  </p>
                  <WorkflowStepEditorPanel
                    steps={visualSteps}
                    onChange={setVisualSteps}
                    templateMappings={templateMappings}
                    processNotifications={processNotifications}
                    onProcessNotificationsChange={setProcessNotifications}
                    showIntakeOptions={false}
                    showProcessNotifications
                    showSteps={false}
                    processLabelOverrides={NOTIFICATION_PROCESS_BUSINESS_LABELS}
                  />
                </DetailSection>
                ) : null}

                {detailTab === 'advanced' ? (
                <>
                <DetailSection title="Advanced">
                  <p className="mb-3 text-xs text-amber-900">
                    Expert configuration for implementation and support. Normal lender setup does not require this
                    section.
                  </p>
                  {!workflowUsesPlpLmsConfig(loanProduct, intakeSegment) ? (
                    <div className="mb-4 grid gap-3 sm:grid-cols-2" data-testid="workflow-advanced-lms">
                      <label className="block text-sm text-slate-700">
                        <span className="mb-1 block text-xs font-medium text-slate-500">LMS product code</span>
                        <input
                          className="bt-input w-full"
                          value={lmsProductCode}
                          onChange={(e) => setLmsProductCode(e.target.value)}
                          placeholder="Configured Encore LMS product code"
                        />
                      </label>
                      <label className="block text-sm text-slate-700">
                        <span className="mb-1 block text-xs font-medium text-slate-500">LMS tenure type</span>
                        <select
                          className="bt-input w-full"
                          value={lmsTenureUnit}
                          onChange={(e) => setLmsTenureUnit(e.target.value)}
                        >
                          {LMS_TENURE_UNIT_OPTIONS.map((o) => (
                            <option key={o.value} value={o.value}>
                              {o.label}
                            </option>
                          ))}
                        </select>
                      </label>
                    </div>
                  ) : null}
                  <label className="block text-sm text-slate-700 sm:col-span-2">
                    <span className="mb-1 block text-xs font-medium text-slate-500">
                      Anchor identity schema (JSON array, optional)
                    </span>
                    <textarea
                      className="min-h-[7rem] bt-input w-full font-mono text-xs"
                      spellCheck={false}
                      value={intakeIdentitySchemaJson}
                      onChange={(e) => setIntakeIdentitySchemaJson(e.target.value)}
                      placeholder='[{"key":"entityPan","label":"Entity PAN","required":true,"inputType":"text","maxLength":10}]'
                    />
                  </label>
                  <label className="block text-sm text-slate-700 sm:col-span-2">
                    <span className="mb-1 block text-xs font-medium text-slate-500">VKYC workflow position</span>
                    <select
                      className="bt-input w-full max-w-md"
                      value={workflowPosition}
                      onChange={(e) => setWorkflowPosition(e.target.value)}
                    >
                      <option value="BEFORE_ESIGN">Before eSign</option>
                      <option value="AFTER_ESIGN">After eSign completion</option>
                      <option value="AFTER_UNDERWRITING">After underwriting</option>
                      <option value="CUSTOM">Custom (by step order)</option>
                    </select>
                  </label>
                </DetailSection>

                <DetailSection title="Steps JSON">
                <details
                  className="rounded border border-slate-200 bg-slate-50"
                  onToggle={(e) => {
                    if ((e.target as HTMLDetailsElement).open) {
                      setStepsJson(JSON.stringify(visualStepsToJsonArray(visualSteps), null, 2))
                    }
                  }}
                >
                  <summary className="cursor-pointer select-none px-3 py-2 text-sm font-medium text-slate-800">
                    Advanced JSON
                  </summary>
                  <div className="space-y-2 border-t border-slate-200 p-3">
                    <p className="text-xs text-slate-500">
                      Raw <code className="rounded bg-slate-100 px-1">steps</code> array. Opening this section syncs
                      from the cards above. After editing, use &quot;Apply to visual editor&quot; before saving.
                    </p>
                    <textarea
                      className="h-48 bt-input w-full font-mono text-xs"
                      value={stepsJson}
                      onChange={(e) => setStepsJson(e.target.value)}
                      spellCheck={false}
                    />
                    <div className="flex flex-wrap gap-2">
                      <button
                        type="button"
                        className="rounded border border-slate-300 bg-white px-2 py-1 text-sm"
                        onClick={syncJsonFromVisual}
                      >
                        Sync from visual
                      </button>
                      <button
                        className="rounded border border-slate-800 bg-slate-800 px-2 py-1 text-sm text-white"
                        type="button"
                        onClick={applyAdvancedToVisual}
                      >
                        Apply to visual editor
                      </button>
                    </div>
                  </div>
                </details>
                <div className="min-w-0 overflow-hidden rounded border border-slate-200 bg-slate-50">
                  <div className="border-b border-slate-200 px-3 py-2 text-sm font-medium text-slate-800">
                    VKYC trigger conditions
                  </div>
                  <div className="min-w-0 space-y-2 p-3">
                    <p className="text-xs text-slate-500">
                      Define when VKYC must be triggered. Conditions are combined with AND. When empty,
                      VKYC runs for every application that reaches this position in the workflow.
                    </p>
                    <VkycConditionBuilder
                      rows={vkycConditionRows}
                      onChange={(next) => {
                        setVkycConditionRows(next)
                        setVkycTriggerConditionJson(JSON.stringify(rowsToJson(next), null, 2))
                        setVkycConditionJsonError(null)
                      }}
                    />
                    <details
                      className="rounded border border-slate-200 bg-white"
                      onToggle={(e) => {
                        if ((e.target as HTMLDetailsElement).open) {
                          setVkycTriggerConditionJson(JSON.stringify(rowsToJson(vkycConditionRows), null, 2))
                          setVkycConditionJsonError(null)
                        }
                      }}
                    >
                      <summary className="cursor-pointer select-none px-3 py-2 text-xs font-medium text-slate-700">
                        Advanced JSON (read / edit raw payload)
                      </summary>
                      <div className="space-y-2 border-t border-slate-200 p-3">
                        <p className="text-xs text-slate-500">
                          The raw JSON below is what gets stored. Editing here is for power users only — the
                          backend evaluator expects the shape{' '}
                          <code className="rounded bg-slate-100 px-1">{`[{"field":"...","operator":"...","value":...}]`}</code>.
                        </p>
                        <textarea
                          className="h-28 bt-input w-full font-mono text-xs"
                          value={vkycTriggerConditionJson}
                          onChange={(e) => {
                            setVkycTriggerConditionJson(e.target.value)
                            setVkycConditionJsonError(null)
                          }}
                          spellCheck={false}
                        />
                        {vkycConditionJsonError ? (
                          <p className="text-xs text-amber-800" role="alert">
                            {vkycConditionJsonError}
                          </p>
                        ) : null}
                        <div className="flex flex-wrap gap-2">
                          <button
                            type="button"
                            className="rounded border border-slate-800 bg-slate-800 px-2 py-1 text-xs text-white"
                            onClick={() => {
                              try {
                                const parsed = JSON.parse(vkycTriggerConditionJson) as unknown
                                if (!Array.isArray(parsed)) {
                                  setVkycConditionJsonError('JSON must be an array.')
                                  return
                                }
                                setVkycConditionRows(jsonToRows(parsed))
                                setVkycConditionJsonError(null)
                              } catch {
                                setVkycConditionJsonError('Invalid JSON. Fix and try again.')
                              }
                            }}
                          >
                            Apply JSON to builder
                          </button>
                          <button
                            type="button"
                            className="rounded border border-slate-300 bg-white px-2 py-1 text-xs"
                            onClick={() =>
                              setVkycTriggerConditionJson(JSON.stringify(rowsToJson(vkycConditionRows), null, 2))
                            }
                          >
                            Sync from builder
                          </button>
                        </div>
                      </div>
                    </details>
                  </div>
                </div>
                </DetailSection>
                </>
                ) : null}

                <div className="flex flex-wrap items-center gap-2 pt-2">
                  <button
                    type="button"
                    onClick={() => void onSave()}
                    disabled={saving}
                    className="bt-btn bt-btn-primary disabled:opacity-50"
                  >
                    {saving
                      ? 'Saving…'
                      : isCreating
                        ? 'Create'
                        : selected &&
                            (selected.publicationStatus === 'ACTIVE' ||
                              selected.publicationStatus === 'SUPERSEDED' ||
                              selected.publicationStatus === 'RETIRED' ||
                              (selected.active && selected.publicationStatus !== 'DRAFT'))
                          ? 'Create New Version'
                          : 'Save changes'}
                  </button>
                  {isCreating ? (
                    <button
                      type="button"
                      onClick={cancelCreate}
                      className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm text-slate-800"
                    >
                      Cancel
                    </button>
                  ) : null}
                  {!isCreating && selected ? (
                    <>
                      {!selected.active ? (
                        <button
                          type="button"
                          onClick={() => void onActivate()}
                          disabled={toggling}
                          className="rounded-md border border-emerald-600 bg-emerald-50 px-3 py-1.5 text-sm font-medium text-emerald-900 disabled:opacity-50"
                        >
                          {toggling ? '…' : 'Activate'}
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => void onDeactivate()}
                          disabled={toggling}
                          className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-800 disabled:opacity-50"
                        >
                          {toggling ? '…' : 'Deactivate'}
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={() => {
                          if (selected.active) return
                          setActionError(null)
                          setSuccessMessage(null)
                          setShowDeleteConfirm(true)
                        }}
                        disabled={deleting || selected.active}
                        title={selected.active ? 'Deactivate before deleting' : undefined}
                        className="rounded-md border border-rose-300 bg-rose-50 px-3 py-1.5 text-sm font-medium text-rose-900 disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        Delete
                      </button>
                    </>
                  ) : null}
                </div>
                {!isCreating && selected && selected.active ? (
                  <p className="text-xs text-amber-800">Deactivate this workflow before deleting it.</p>
                ) : null}
                {!isCreating && selected && detailTab === 'advanced' ? (
                  <p className="mt-3 text-xs text-slate-500">
                    Id: {selected.id} · persisted version {selected.version} ·
                    {selected.createdAt ? ` created ${formatInstant(selected.createdAt)}` : ''}
                  </p>
                ) : null}
              </DetailPanel>
            ) : (
              <DetailEmptyState
                title="Select a workflow"
                description="Pick a workflow from the list to view or edit its configuration, or create a new one."
                action={
                  <button type="button" onClick={resetFormToNew} className="bt-btn bt-btn-primary">
                    New workflow
                  </button>
                }
              />
            )}
          </MasterDetailLayout>
        </div>
      )}

      {showDeleteConfirm && selected && !selected.active ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="delete-wf-title"
        >
          <div className="w-full max-w-md rounded-lg border border-slate-200 bg-white p-5 shadow-lg">
            <h2 id="delete-wf-title" className="text-base font-semibold text-slate-900">
              Delete workflow?
            </h2>
            <p className="mt-2 text-sm text-slate-600">
              {`Permanently remove "${selected.name}" for ${selected.borrowerType} / ${selected.loanProduct}? This does not change existing loan applications (they do not store a workflow id).`}
            </p>
            <div className="mt-4 flex flex-wrap justify-end gap-2">
              <button
                type="button"
                className="rounded border border-slate-300 bg-white px-3 py-1.5 text-sm"
                onClick={() => setShowDeleteConfirm(false)}
              >
                Cancel
              </button>
              <button
                type="button"
                className="rounded border border-rose-700 bg-rose-800 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
                disabled={deleting}
                onClick={() => void onConfirmDelete()}
              >
                {deleting ? 'Deleting…' : 'Delete'}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  )
}
