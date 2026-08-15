import { useCallback, useEffect, useMemo, useState } from 'react'
import { uploadDocument } from '@/api/documents'
import {
  chooseRequirementMode,
  getBorrowerCustomerRequirements,
  getStaffCustomerRequirements,
  submitDirectInput,
  submitRequirementDocument,
  type CustomerAction,
  type CustomerRequirementsView,
  type FulfilmentMode,
} from '@/api/customerRequirements'
import { ApiError } from '@/api/http'
import {
  intakeFieldCaptionClass,
  intakeFieldInputClass,
  intakeFieldLabelClass,
  intakePrimaryButtonClass,
  intakeSecondaryButtonClass,
  intakeStepSectionClass,
} from '@/lib/intake/intakeStepLayout'

/**
 * W5 — Dynamic customer requirements panel.
 * Authority: W4 RequirementPlan only. No GACAT IDs in customer copy.
 */
export function CustomerRequirementsPanel({
  applicationId,
  variant = 'staff',
  actorRole,
}: {
  applicationId: string
  variant?: 'staff' | 'borrower'
  actorRole?: string
}) {
  const borrower = variant === 'borrower'
  const role = actorRole ?? (borrower ? 'CUSTOMER' : 'RM')
  const [view, setView] = useState<CustomerRequirementsView | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [whyOpen, setWhyOpen] = useState<Record<string, boolean>>({})
  const [drafts, setDrafts] = useState<Record<string, string>>({})

  const load = useCallback(async () => {
    setErr(null)
    try {
      const data = borrower
        ? await getBorrowerCustomerRequirements(applicationId)
        : await getStaffCustomerRequirements(applicationId)
      setView(data)
      const next: Record<string, string> = {}
      for (const a of [...(data.actions || []), ...(data.providedOrProcessing || [])]) {
        const v = a.draftValue?.value
        if (typeof v === 'string' && a.itemIds[0]) next[a.itemIds[0]] = v
      }
      setDrafts((prev) => ({ ...prev, ...next }))
    } catch (e) {
      setView(null)
      setErr(e instanceof ApiError ? e.message : 'Could not load requirements')
    }
  }, [applicationId, borrower])

  useEffect(() => {
    void load()
  }, [load])

  const sections = useMemo(() => {
    if (!view) return [] as { name: string; actions: CustomerAction[] }[]
    const map = new Map<string, CustomerAction[]>()
    for (const a of view.actions.filter((x) => x.actionable)) {
      const list = map.get(a.section) ?? []
      list.push(a)
      map.set(a.section, list)
    }
    return [...map.entries()].map(([name, actions]) => ({ name, actions }))
  }, [view])

  async function onChoose(action: CustomerAction, mode: FulfilmentMode) {
    const itemId = action.itemIds[0]
    if (!itemId) return
    setBusy(true)
    try {
      const next = await chooseRequirementMode(applicationId, itemId, mode, role, borrower)
      setView(next)
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : 'Could not save choice')
    } finally {
      setBusy(false)
    }
  }

  async function onSaveDraft(action: CustomerAction) {
    const itemId = action.itemIds[0]
    if (!itemId) return
    setBusy(true)
    try {
      const next = await submitDirectInput(
        applicationId,
        itemId,
        { value: drafts[itemId] ?? '', saveDraftOnly: true, actorRole: role },
        borrower,
      )
      setView(next)
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : 'Could not save draft')
    } finally {
      setBusy(false)
    }
  }

  async function onSubmitDirect(action: CustomerAction) {
    const itemId = action.itemIds[0]
    if (!itemId) return
    setBusy(true)
    try {
      const next = await submitDirectInput(
        applicationId,
        itemId,
        { value: drafts[itemId] ?? '', verified: false, actorRole: role },
        borrower,
      )
      setView(next)
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : 'Could not submit')
    } finally {
      setBusy(false)
    }
  }

  async function onUpload(action: CustomerAction, file: File | null) {
    if (!file) return
    const itemId = action.itemIds[0]
    if (!itemId) return
    setBusy(true)
    try {
      const docType = action.documentGroup || 'SUPPORTING_DOCUMENT'
      const uploaded = await uploadDocument(applicationId, file, docType)
      const ref = uploaded.id || `${docType}:${file.name}`
      const next = await submitRequirementDocument(
        applicationId,
        itemId,
        { documentRef: String(ref), actorRole: role },
        borrower,
      )
      setView(next)
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : 'Upload failed')
    } finally {
      setBusy(false)
    }
  }

  if (err && !view) {
    return <p className="text-sm text-rose-700">{err}</p>
  }
  if (!view) {
    return <p className="text-sm text-slate-600">Loading requirements…</p>
  }
  if (!view.planPresent) {
    return null
  }

  const s = view.summary

  return (
    <div className={intakeStepSectionClass}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="text-base font-semibold text-slate-900">Information we still need</h3>
          <p className="mt-1 text-sm text-slate-600">
            {borrower
              ? 'Complete the items below so we can continue reviewing your application.'
              : 'Customer/RM actionable requirements from the Requirement Plan.'}
          </p>
        </div>
        {variant === 'staff' ? (
          <span className="rounded bg-slate-100 px-2 py-1 text-[11px] text-slate-500">RM-assisted entry allowed</span>
        ) : null}
      </div>

      <div className="mt-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-5">
        <SummaryChip label="Information required" value={s.informationRequiredCount} />
        <SummaryChip label="Documents required" value={s.documentsRequiredCount} />
        <SummaryChip label="Provided" value={s.providedCount} />
        <SummaryChip label="Processing" value={s.processingCount} />
        <SummaryChip label="Remaining actions" value={s.remainingActionsCount} emphasize />
      </div>

      {err ? <p className="mt-3 text-sm text-rose-700">{err}</p> : null}

      {s.remainingActionsCount === 0 ? (
        <p className="mt-4 text-sm text-emerald-800">{view.emptyMessage || "You're all set."}</p>
      ) : null}

      <div className="mt-5 space-y-6">
        {sections.map((sec) => (
          <div key={sec.name} className="space-y-3">
            <h4 className="text-sm font-semibold text-slate-800">{sec.name}</h4>
            {sec.actions.map((action) => (
              <ActionCard
                key={action.actionKey}
                action={action}
                busy={busy}
                whyOpen={!!whyOpen[action.actionKey]}
                onToggleWhy={() =>
                  setWhyOpen((m) => ({ ...m, [action.actionKey]: !m[action.actionKey] }))
                }
                draft={action.itemIds[0] ? drafts[action.itemIds[0]] ?? '' : ''}
                onDraftChange={(v) => {
                  const id = action.itemIds[0]
                  if (!id) return
                  setDrafts((d) => ({ ...d, [id]: v }))
                }}
                onChoose={(mode) => void onChoose(action, mode)}
                onSaveDraft={() => void onSaveDraft(action)}
                onSubmitDirect={() => void onSubmitDirect(action)}
                onUpload={(f) => void onUpload(action, f)}
              />
            ))}
          </div>
        ))}
      </div>

      {view.providedOrProcessing.length > 0 ? (
        <div className="mt-6 space-y-3 border-t border-slate-100 pt-4">
          <h4 className="text-sm font-semibold text-slate-800">Already provided</h4>
          {view.providedOrProcessing.map((action) => (
            <div
              key={`p-${action.actionKey}`}
              className="rounded-md border border-emerald-100 bg-emerald-50/50 px-4 py-3"
            >
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="text-sm font-medium text-slate-900">{action.title}</div>
                <span className="text-xs font-medium text-emerald-800">✓ {action.customerStatusLabel}</span>
              </div>
              {action.processingLabel ? (
                <p className="mt-1 text-xs text-slate-600">{action.processingLabel}</p>
              ) : null}
              {variant === 'staff' ? (
                <p className="mt-1 text-[11px] text-slate-500">
                  Customer fulfilment: {action.customerFulfilment} · Data readiness: {action.dataReadiness}
                </p>
              ) : null}
            </div>
          ))}
        </div>
      ) : null}
    </div>
  )
}

function SummaryChip({
  label,
  value,
  emphasize,
}: {
  label: string
  value: number
  emphasize?: boolean
}) {
  return (
    <div
      className={`rounded-md border px-3 py-2 ${
        emphasize ? 'border-amber-200 bg-amber-50' : 'border-slate-100 bg-slate-50'
      }`}
    >
      <div className="text-[11px] text-slate-500">{label}</div>
      <div className="text-lg font-semibold text-slate-900">{value}</div>
    </div>
  )
}

function ActionCard({
  action,
  busy,
  whyOpen,
  onToggleWhy,
  draft,
  onDraftChange,
  onChoose,
  onSaveDraft,
  onSubmitDirect,
  onUpload,
}: {
  action: CustomerAction
  busy: boolean
  whyOpen: boolean
  onToggleWhy: () => void
  draft: string
  onDraftChange: (v: string) => void
  onChoose: (mode: FulfilmentMode) => void
  onSaveDraft: () => void
  onSubmitDirect: () => void
  onUpload: (file: File | null) => void
}) {
  const effectiveMode =
    action.chosenMode ||
    action.preferredMode ||
    (action.allowedModes.length === 1 ? action.allowedModes[0] : null)

  return (
    <div className="rounded-md border border-slate-200 bg-white p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <div className="text-sm font-medium text-slate-900">
            {action.title}
            {action.reuploadRequired ? (
              <span className="ml-2 text-xs font-medium text-rose-700">Re-upload needed</span>
            ) : (
              <span className="ml-2 text-xs text-amber-800">{action.customerStatusLabel}</span>
            )}
          </div>
          {action.description ? <p className="mt-1 text-xs text-slate-600">{action.description}</p> : null}
        </div>
        <button type="button" className="text-[11px] text-slate-500 underline" onClick={onToggleWhy}>
          Why do we need this?
        </button>
      </div>
      {whyOpen && action.whyNeeded ? (
        <p className="mt-2 rounded bg-slate-50 px-3 py-2 text-xs text-slate-600">{action.whyNeeded}</p>
      ) : null}

      {action.showChoice ? (
        <div className="mt-3 space-y-2">
          <p className="text-sm text-slate-700">How would you like to provide this information?</p>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              className={intakeSecondaryButtonClass}
              disabled={busy}
              onClick={() => onChoose('DIRECT_INPUT')}
            >
              Enter details
            </button>
            <button
              type="button"
              className={intakeSecondaryButtonClass}
              disabled={busy}
              onClick={() => onChoose('DOCUMENT_UPLOAD')}
            >
              Upload supporting document
            </button>
          </div>
        </div>
      ) : null}

      {!action.showChoice && effectiveMode === 'DIRECT_INPUT' ? (
        <label className={`${intakeFieldLabelClass} mt-3 block`}>
          <span className={intakeFieldCaptionClass}>
            {action.field?.label || action.title}
            {action.field?.required ? ' *' : ''}
            {action.field?.unit ? ` (${action.field.unit})` : ''}
          </span>
          {action.field?.inputType === 'boolean' ? (
            <input
              type="checkbox"
              className="mt-1"
              checked={draft === 'true'}
              disabled={busy}
              onChange={(e) => onDraftChange(e.target.checked ? 'true' : 'false')}
            />
          ) : (
            <input
              type={action.field?.inputType === 'number' ? 'number' : 'text'}
              className={intakeFieldInputClass}
              value={draft}
              disabled={busy}
              onChange={(e) => onDraftChange(e.target.value)}
            />
          )}
          {action.field?.helpText ? (
            <span className="mt-1 block text-[11px] text-slate-400">{action.field.helpText}</span>
          ) : null}
          <div className="mt-3 flex flex-wrap gap-2">
            <button type="button" className={intakeSecondaryButtonClass} disabled={busy} onClick={onSaveDraft}>
              Save draft
            </button>
            <button type="button" className={intakePrimaryButtonClass} disabled={busy} onClick={onSubmitDirect}>
              Submit
            </button>
          </div>
        </label>
      ) : null}

      {!action.showChoice && effectiveMode === 'DOCUMENT_UPLOAD' ? (
        <div className="mt-3">
          <input
            type="file"
            accept=".pdf,image/*"
            className="text-sm"
            disabled={busy}
            onChange={(e) => {
              const file = e.target.files?.[0] ?? null
              if (e.target) e.target.value = ''
              onUpload(file)
            }}
          />
          {action.linkedLabels?.length > 1 ? (
            <p className="mt-2 text-[11px] text-slate-500">
              This document covers: {action.linkedLabels.join(', ')}
            </p>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
