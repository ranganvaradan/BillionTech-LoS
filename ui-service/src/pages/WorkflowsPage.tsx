import { useCallback, useEffect, useState } from 'react'
import {
  activateWorkflow,
  createWorkflow,
  deactivateWorkflow,
  deleteWorkflow,
  listWorkflows,
  updateWorkflow,
} from '@/api/workflows'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { WorkflowStepEditorPanel } from '@/components/WorkflowStepEditorPanel'
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
import { formatInstant } from '@/lib/format'
import {
  createEmptyVisualStep,
  parseWorkflowStepsFromJson,
  visualStepsToJsonArray,
  type VisualWorkflowStep,
} from '@/lib/workflowVisual'
import type { WorkflowConfigRequest, WorkflowConfigResponse } from '@/types/workflow'
import type { BorrowerType } from '@/types/createApplication'

const BORROWER_TYPES: BorrowerType[] = [...BORROWER_TYPE_ORDER]

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
  const [visualSteps, setVisualSteps] = useState<VisualWorkflowStep[]>([])
  const [stepsJson, setStepsJson] = useState('[]')
  const [workflowPosition, setWorkflowPosition] = useState('BEFORE_ESIGN')
  const [vkycConditionRows, setVkycConditionRows] = useState<VkycConditionRow[]>([])
  const [vkycTriggerConditionJson, setVkycTriggerConditionJson] = useState('[]')
  const [vkycConditionJsonError, setVkycConditionJsonError] = useState<string | null>(null)

  const [actionError, setActionError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [toggling, setToggling] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)

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

  function resetFormToNew() {
    setSuccessMessage(null)
    setSelected(null)
    setIsCreating(true)
    setName('New workflow')
    setBorrowerType('INDIVIDUAL')
    setLoanProduct('PERSONAL_LOAN')
    setWorkflowPosition('BEFORE_ESIGN')
    setVkycConditionRows([])
    setVkycTriggerConditionJson('[]')
    setVkycConditionJsonError(null)
    const v = [createEmptyVisualStep()]
    setVisualSteps(v)
    setStepsJson(JSON.stringify(visualStepsToJsonArray(v), null, 2))
    setActionError(null)
  }

  function applySelection(w: WorkflowConfigResponse) {
    setSuccessMessage(null)
    setSelected(w)
    setIsCreating(false)
    setName(w.name)
    setBorrowerType((w.borrowerType as BorrowerType) || 'INDIVIDUAL')
    setLoanProduct(w.loanProduct)
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
    const body: WorkflowConfigRequest = {
      name: name.trim() || 'Unnamed workflow',
      borrowerType,
      loanProduct: loanProduct.trim() || 'PERSONAL_LOAN',
      steps,
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
        const updated = await updateWorkflow(selected.id, body)
        setList((prev) => (prev ? prev.map((w) => (w.id === updated.id ? updated : w)) : [updated]))
        applySelection(updated)
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

  return (
    <div>
      <PageHeader
        title="Workflow configuration"
        description="Create, edit, activate, and remove KYC and bureau step templates per borrower type and loan product."
      />

      {loading && <LoadingState label="Loading workflows…" />}
      {loadError && <ErrorState message={loadError} />}

      {list && !loading && (
        <div>
          {successMessage ? (
            <p
              className="mb-4 rounded border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-900"
              role="status"
            >
              {successMessage}
            </p>
          ) : null}
          <div className="grid gap-6 lg:grid-cols-2">
            <div>
              <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                <h2 className="text-sm font-semibold text-slate-900">Workflows</h2>
                <button
                  type="button"
                  onClick={resetFormToNew}
                  className="rounded-md border border-slate-800 bg-slate-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-800"
                >
                  New workflow
                </button>
              </div>
              {list.length === 0 && !isCreating ? (
                <p className="text-sm text-slate-600">No workflows yet. Click &quot;New workflow&quot; to add one.</p>
              ) : (
                <ul className="divide-y divide-slate-200 rounded-lg border border-slate-200 bg-white">
                  {list.map((w) => (
                    <li key={w.id}>
                      <button
                        type="button"
                        onClick={() => applySelection(w)}
                        className={[
                          'w-full px-3 py-2.5 text-left text-sm transition-colors hover:bg-slate-50',
                          selected?.id === w.id && !isCreating ? 'bg-slate-100' : '',
                        ].join(' ')}
                      >
                        <div className="font-medium text-slate-900">{w.name}</div>
                        <div className="text-xs text-slate-500">
                          {BORROWER_TYPE_LABELS[w.borrowerType as BorrowerType] ?? w.borrowerType} ·{' '}
                          {loanProductLabel(w.loanProduct)} · v{w.version}
                          {w.active ? (
                            <span className="ml-2 rounded bg-emerald-100 px-1.5 py-0.5 text-emerald-800">Active</span>
                          ) : (
                            <span className="ml-2 rounded bg-slate-100 px-1.5 py-0.5 text-slate-600">Inactive</span>
                          )}
                        </div>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <div>
              {showEditor ? (
                <div className="space-y-3">
                <h2 className="text-sm font-semibold text-slate-900">
                  {isCreating ? 'New workflow' : 'Edit'}
                </h2>
                <p className="text-xs text-slate-500">
                  New configs are <strong>inactive</strong> until you activate them. Only one active workflow applies per
                  borrower type and loan product. Delete is allowed for <strong>inactive</strong> workflows only.
                </p>
                {actionError ? (
                  <p className="rounded border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900" role="alert">
                    {actionError}
                  </p>
                ) : null}
                <div className="grid gap-3 sm:grid-cols-2">
                  <label className="block text-sm text-slate-700 sm:col-span-2">
                    <span className="mb-1 block text-xs font-medium text-slate-500">Name</span>
                    <input
                      className="w-full rounded-md border border-slate-300 px-3 py-2"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                    />
                  </label>
                  <label className="block text-sm text-slate-700">
                    <span className="mb-1 block text-xs font-medium text-slate-500">Borrower type</span>
                    <select
                      className="w-full rounded-md border border-slate-300 bg-white px-3 py-2"
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
                    <span className="mb-1 block text-xs font-medium text-slate-500">Loan product (code in API)</span>
                    <select
                      className="w-full rounded-md border border-slate-300 bg-white px-3 py-2"
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
                    <p className="mt-0.5 text-xs text-slate-500">Applications and APIs store the code (e.g. PERSONAL_LOAN).</p>
                  </label>
                  <label className="block text-sm text-slate-700 sm:col-span-2">
                    <span className="mb-1 block text-xs font-medium text-slate-500">VKYC workflow position</span>
                    <select
                      className="w-full rounded-md border border-slate-300 bg-white px-3 py-2"
                      value={workflowPosition}
                      onChange={(e) => setWorkflowPosition(e.target.value)}
                    >
                      <option value="BEFORE_ESIGN">Before eSign</option>
                      <option value="AFTER_ESIGN">After eSign completion</option>
                      <option value="AFTER_UNDERWRITING">After underwriting</option>
                      <option value="CUSTOM">Custom (by step order)</option>
                    </select>
                  </label>
                </div>

                <WorkflowStepEditorPanel steps={visualSteps} onChange={setVisualSteps} />

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
                      className="h-48 w-full rounded-md border border-slate-300 bg-white px-3 py-2 font-mono text-xs"
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
                <div className="rounded border border-slate-200 bg-slate-50">
                  <div className="border-b border-slate-200 px-3 py-2 text-sm font-medium text-slate-800">
                    VKYC trigger conditions
                  </div>
                  <div className="space-y-2 p-3">
                    <p className="text-xs text-slate-500">
                      Define when VKYC must be triggered. Conditions are combined with AND. When empty,
                      VKYC runs for every application that reaches this position in the workflow.
                    </p>
                    <VkycConditionBuilder
                      rows={vkycConditionRows}
                      onChange={(next) => {
                        setVkycConditionRows(next)
                        // Keep the advanced JSON view in sync as the user edits
                        // structured rows, so toggling between the two never
                        // shows stale JSON.
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
                          className="h-28 w-full rounded-md border border-slate-300 bg-white px-3 py-2 font-mono text-xs"
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

                <div className="flex flex-wrap items-center gap-2">
                  <button
                    type="button"
                    onClick={() => void onSave()}
                    disabled={saving}
                    className="rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
                  >
                    {saving ? 'Saving…' : isCreating ? 'Create' : 'Save changes'}
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
                {!isCreating && selected ? (
                  <p className="text-xs text-slate-500">
                    Id: {selected.id} · version {selected.version} ·
                    {selected.createdAt ? ` created ${formatInstant(selected.createdAt)}` : ''}
                  </p>
                ) : null}
              </div>
            ) : (
              <p className="text-sm text-slate-600">Select a workflow to edit, or create a new one.</p>
            )}
            </div>
          </div>
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
