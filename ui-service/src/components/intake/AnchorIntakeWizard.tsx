import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { createApplication, updateApplication } from '@/api/applications'
import { listDocuments, uploadDocument } from '@/api/documents'
import { listWorkflows } from '@/api/workflows'
import { submitApplicationForKyc } from '@/api/flow'
import { ApiError } from '@/api/http'
import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'
import { consentHelper } from '@/lib/intake/intakeLabels'
import {
  documentSlotsForAnchorIntake,
  missingAnchorDocumentTypes,
} from '@/lib/intake/intakeDocumentSlots'
import { createEmptyIntakeFormState, type IntakeFormState, type IntakeMode } from '@/lib/intake/intakeTypes'
import { allConsentsChecked, validateConsentStep } from '@/lib/intake/intakeValidation'
import {
  buildAnchorConsentUpdate,
  buildAnchorCreateRequest,
  buildAnchorIdentityUpdate,
} from '@/lib/intake/anchorIntakePayloads'
import { createEmptyAnchorFormState, type AnchorFormState } from '@/lib/intake/anchorIntakeTypes'
import { clearAnchorDraft, loadAnchorDraft, saveAnchorDraft } from '@/lib/anchorWizardDraft'
import {
  intakeFieldCaptionClass,
  intakeFieldInputClass,
  intakeFieldLabelClass,
  intakeFieldSelectClass,
  intakePrimaryButtonClass,
  intakeSecondaryButtonClass,
  intakeStepActionsClass,
  intakeStepFieldGridClass,
  intakeStepSectionClass,
} from '@/lib/intake/intakeStepLayout'
import { IndiaStateCityPincodeFields } from '@/components/intake/IndiaStateCityPincodeFields'
import { BORROWER_TYPE_LABELS } from '@/catalog/borrowerTypes'
import { ANCHOR_BORROWER_TYPE } from '@/lib/intake/anchorIntakeConstants'
import { ensureCitiesLoadedForStateName, ensureGeoStatesLoaded } from '@/lib/intake/masterGeoClientCache'
import { validateIntakeLocation } from '@/lib/intake/intakeValidation'
import { workflowLoanProductDisplayName, productsForIntakeSegment } from '@/utils/workflowProducts'
import type { WorkflowConfigResponse } from '@/types/workflow'
import type { BorrowerType } from '@/types/createApplication'

const STEP_LABELS = ['Product & request', 'Corporate', 'Identity', 'Documents', 'Consent', 'Review'] as const

const IDENTITY_KEYS = new Set([
  'entityPan',
  'gstin',
  'cin',
  'bankAccountNumber',
  'ifscCode',
  'accountHolderName',
])

export type IdentityFieldDef = {
  key: keyof Pick<
    AnchorFormState,
    'entityPan' | 'gstin' | 'cin' | 'bankAccountNumber' | 'ifscCode' | 'accountHolderName'
  >
  label: string
  required: boolean
  maxLength?: number
}

function defaultIdentityFields(borrowerType: BorrowerType): IdentityFieldDef[] {
  const base: IdentityFieldDef[] = [
    { key: 'entityPan', label: 'Entity PAN', required: true, maxLength: 10 },
    { key: 'gstin', label: 'GSTIN', required: false, maxLength: 15 },
  ]
  if (borrowerType === 'COMPANY') {
    base.push({ key: 'cin', label: 'CIN (Corporate Identification Number)', required: true, maxLength: 21 })
  } else {
    base.push({ key: 'cin', label: 'CIN (if applicable)', required: false, maxLength: 21 })
  }
  base.push(
    { key: 'bankAccountNumber', label: 'Bank account number', required: true },
    { key: 'ifscCode', label: 'IFSC', required: true, maxLength: 11 },
    { key: 'accountHolderName', label: 'Account holder name', required: true },
  )
  return base
}

function parseIdentitySchema(
  workflow: WorkflowConfigResponse | null,
  borrowerType: BorrowerType,
): IdentityFieldDef[] {
  const raw = workflow?.intakeIdentitySchema
  if (!Array.isArray(raw) || raw.length === 0) {
    return defaultIdentityFields(borrowerType)
  }
  const out: IdentityFieldDef[] = []
  for (const row of raw) {
    if (!row || typeof row !== 'object') continue
    const m = row as Record<string, unknown>
    const key = String(m.key ?? '')
    if (!IDENTITY_KEYS.has(key)) continue
    out.push({
      key: key as IdentityFieldDef['key'],
      label: String(m.label ?? key),
      required: Boolean(m.required),
      maxLength: typeof m.maxLength === 'number' ? m.maxLength : undefined,
    })
  }
  return out.length ? out : defaultIdentityFields(borrowerType)
}

const PAN_RE = /^[A-Z]{5}[0-9]{4}[A-Z]$/i
const IFSC_RE = /^[A-Z]{4}0[A-Z0-9]{6}$/i

function Stepper({ step }: { step: number }) {
  return (
    <ol className="mb-8 flex flex-wrap items-center gap-2 border-b border-slate-200 pb-4 text-sm">
      {STEP_LABELS.map((label, i) => (
        <li key={label} className="flex items-center gap-2">
          <span
            className={[
              'flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-xs font-semibold',
              i < step ? 'bg-[var(--bt-green-bg)] text-[var(--bt-green)]' : i === step ? 'bg-[var(--bt-orange)] text-white' : 'bg-[var(--bt-gray-100)] text-[var(--bt-gray-500)]',
            ].join(' ')}
            aria-current={i === step ? 'step' : undefined}
          >
            {i + 1}
          </span>
          <span className={i === step ? 'font-medium text-slate-900' : 'text-slate-600'}>{label}</span>
          {i < STEP_LABELS.length - 1 ? <span className="hidden sm:inline text-slate-300">·</span> : null}
        </li>
      ))}
    </ol>
  )
}

export type AnchorIntakeWizardProps = {
  /** When opened from unified staff new application (after invoice discounting → Anchor). */
  variant?: 'standalone' | 'embedded'
  /** Mirrors parent staff wizard mode (internal vs sales-assisted). */
  staffIntakeMode?: IntakeMode
  /** Prefill amount / tenure from the unified product step. */
  initialRequest?: { requestedAmount: string; tenureMonths: string }
  /** Embedded only: return to product + onboarding-type step. */
  onExitEmbedded?: () => void
}

export function AnchorIntakeWizard({
  variant = 'standalone',
  staffIntakeMode = 'ADMIN_INTERNAL',
  initialRequest,
  onExitEmbedded,
}: AnchorIntakeWizardProps) {
  const navigate = useNavigate()
  const { user } = useAuth()
  const [step, setStep] = useState(0)
  const [form, setForm] = useState<AnchorFormState>(createEmptyAnchorFormState)
  const [applicationId, setApplicationId] = useState<string | null>(null)
  const [activeWorkflows, setActiveWorkflows] = useState<WorkflowConfigResponse[]>([])
  const [workflowsState, setWorkflowsState] = useState<'loading' | 'ok' | 'err'>('loading')
  const [workflowsError, setWorkflowsError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [docWarning, setDocWarning] = useState<string | null>(null)

  const anchorProducts = useMemo(
    () => productsForIntakeSegment(activeWorkflows, 'ANCHOR', ANCHOR_BORROWER_TYPE),
    [activeWorkflows],
  )
  const selectedWorkflow = useMemo(
    () => anchorProducts.find((w) => w.loanProduct === form.loanProduct) ?? null,
    [anchorProducts, form.loanProduct],
  )
  const identityFields = useMemo(
    () => parseIdentitySchema(selectedWorkflow, ANCHOR_BORROWER_TYPE),
    [selectedWorkflow],
  )

  const loadWorkflows = useCallback(async () => {
    setWorkflowsState('loading')
    setWorkflowsError(null)
    try {
      const all = await listWorkflows()
      const act = all.filter((w) => w.active)
      setActiveWorkflows(act)
      setWorkflowsState('ok')
      setForm((f) => {
        const list = productsForIntakeSegment(act, 'ANCHOR', ANCHOR_BORROWER_TYPE)
        const next = { ...f, borrowerType: ANCHOR_BORROWER_TYPE }
        if (list.length === 0) return { ...next, loanProduct: 'BUSINESS_WC_INVOICE_DISCOUNTING' }
        if (list.some((w) => w.loanProduct === f.loanProduct)) return next
        return { ...next, loanProduct: list[0]!.loanProduct as AnchorFormState['loanProduct'] }
      })
    } catch (e) {
      setActiveWorkflows([])
      setWorkflowsError(e instanceof Error ? e.message : 'Failed to load workflows')
      setWorkflowsState('err')
    }
  }, [])

  useEffect(() => {
    void loadWorkflows()
  }, [loadWorkflows])

  useEffect(() => {
    if (variant === 'embedded' && initialRequest) {
      setForm((f) => ({
        ...f,
        borrowerType: ANCHOR_BORROWER_TYPE,
        requestedAmount: initialRequest.requestedAmount,
        tenureMonths: initialRequest.tenureMonths,
        purpose: '',
      }))
      return
    }
    const d = loadAnchorDraft()
    if (d?.form) {
      setForm({ ...d.form, borrowerType: ANCHOR_BORROWER_TYPE, purpose: '' })
      setStep(d.step)
      setApplicationId(d.applicationId)
    }
  }, [variant, initialRequest])

  useEffect(() => {
    if (variant === 'embedded') return
    saveAnchorDraft({ step, applicationId, form })
  }, [variant, step, applicationId, form])

  const syncDocumentsFromServer = useCallback(async (id: string) => {
    try {
      const docs = await listDocuments(id)
      setForm((f) => {
        const m = { ...f.documentUploaded }
        for (const d of docs) {
          m[d.documentType] = true
        }
        return { ...f, documentUploaded: m }
      })
    } catch {
      /* optional */
    }
  }, [])

  useEffect(() => {
    if (applicationId && step >= 3) void syncDocumentsFromServer(applicationId)
  }, [applicationId, step, syncDocumentsFromServer])

  function validateStep0(): string | null {
    if (!form.requestedAmount.trim() || Number.parseFloat(form.requestedAmount) <= 0) {
      return 'Enter a valid requested amount.'
    }
    if (!anchorProducts.length) {
      return 'No active anchor workflow for corporate invoice discounting. Ask an admin to activate anchor invoice-discounting workflows.'
    }
    return null
  }

  function validateStep1(): string | null {
    if (!form.corporateName.trim()) return 'Corporate name is required.'
    if (!form.email.trim()) return 'Email is required.'
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) return 'Enter a valid email.'
    if (!form.mobile.trim() || form.mobile.replace(/\D/g, '').length < 10) return 'Enter a valid mobile number.'
    if (!form.dateOfIncorporation.trim()) return 'Date of incorporation is required.'
    if (!form.addressLine.trim()) return 'Address is required.'
    return validateIntakeLocation(form.state, form.city, form.pincode, 'staff_basic')
  }

  function validateStep2(): string | null {
    for (const f of identityFields) {
      const v = String(form[f.key] ?? '').trim()
      if (f.required && !v) {
        return `${f.label} is required.`
      }
      if (f.key === 'entityPan' && v && !PAN_RE.test(v)) {
        return 'Enter a valid 10-character Entity PAN.'
      }
      if (f.key === 'ifscCode' && v && !IFSC_RE.test(v)) {
        return 'Enter a valid IFSC (11 characters).'
      }
      if (f.maxLength && v.length > f.maxLength) {
        return `${f.label} must be at most ${f.maxLength} characters.`
      }
    }
    return null
  }

  async function onUploadFile(documentType: string, file: File | null) {
    if (!applicationId || !file) return
    setBusy(true)
    setError(null)
    try {
      await uploadDocument(applicationId, file, documentType)
      setForm((f) => ({ ...f, documentUploaded: { ...f.documentUploaded, [documentType]: true } }))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Upload failed')
    } finally {
      setBusy(false)
    }
  }

  async function handleNext() {
    setError(null)
    setDocWarning(null)
    if (step === 0) {
      const v = validateStep0()
      if (v) {
        setError(v)
        return
      }
      setStep(1)
      return
    }
    if (step === 1) {
      try {
        await ensureGeoStatesLoaded()
        await ensureCitiesLoadedForStateName(form.state)
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Could not load location master data. Try again.')
        return
      }
      const v = validateStep1()
      if (v) {
        setError(v)
        return
      }
      setBusy(true)
      try {
        if (!applicationId) {
          const req = buildAnchorCreateRequest(form, user, staffIntakeMode)
          const res = await createApplication(req)
          setApplicationId(res.id)
        } else {
          await updateApplication(applicationId, {
            businessInfo: {
              corporateName: form.corporateName.trim(),
              email: form.email.trim(),
              mobile: form.mobile.trim(),
              dateOfIncorporation: form.dateOfIncorporation.trim(),
              addressLine: form.addressLine.trim(),
              city: form.city.trim(),
              state: form.state.trim(),
              country: form.country.trim(),
              pincode: form.pincode.replace(/\D/g, '').slice(0, 6),
            } as Record<string, unknown>,
          })
        }
        setStep(2)
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'Could not save corporate details.')
      } finally {
        setBusy(false)
      }
      return
    }
    if (step === 2) {
      const v = validateStep2()
      if (v) {
        setError(v)
        return
      }
      if (!applicationId) return
      setBusy(true)
      try {
        await updateApplication(applicationId, buildAnchorIdentityUpdate(form))
        setStep(3)
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'Could not save identity details.')
      } finally {
        setBusy(false)
      }
      return
    }
    if (step === 3) {
      const miss = missingAnchorDocumentTypes(form.borrowerType, form.documentUploaded)
      if (miss.length) {
        setDocWarning(`Recommended uploads still missing: ${miss.join(', ')}. You can continue or go back to upload.`)
      }
      setStep(4)
      return
    }
    if (step === 4) {
      const intakeSlice = {
        ...createEmptyIntakeFormState(),
        ...form,
      } as IntakeFormState
      const c = validateConsentStep(intakeSlice)
      if (c) {
        setError(c)
        return
      }
      if (!applicationId) return
      setBusy(true)
      try {
        await updateApplication(applicationId, buildAnchorConsentUpdate(form, user))
        setStep(5)
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'Could not save consents.')
      } finally {
        setBusy(false)
      }
    }
  }

  function goBack() {
    setError(null)
    if (step > 0) setStep((s) => s - 1)
  }

  async function onSubmitFinal() {
    if (!applicationId) return
    const intakeSlice = { ...createEmptyIntakeFormState(), ...form } as IntakeFormState
    if (!allConsentsChecked(intakeSlice)) {
      setError('All consents are required before submission.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await submitApplicationForKyc(applicationId)
      if (variant === 'standalone') {
        clearAnchorDraft()
      }
      void navigate(`/applications/${applicationId}`, { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Submit failed')
    } finally {
      setBusy(false)
    }
  }

  const docSlots = documentSlotsForAnchorIntake(form.borrowerType)

  return (
    <div>
      {variant === 'standalone' ? (
        <>
          <PageHeader
            title="New anchor (invoice discounting)"
            description="Onboard a corporate anchor for invoice discounting. This uses the same approval flow as borrower applications after submit."
          />
          <div className="-mt-2 mb-6">
            <Link to="/applications" className="text-sm font-medium text-slate-700 underline-offset-2 hover:underline">
              Back to applications
            </Link>
          </div>
        </>
      ) : (
        <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 className="bt-card-title">Anchor onboarding</h2>
            <p className="mt-1 text-sm text-slate-600">
              Invoice discounting — anchor intake. You can go back to change product or onboarding type.
            </p>
          </div>
          {onExitEmbedded ? (
            <button
              type="button"
              className="shrink-0 rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-800 hover:bg-slate-50"
              onClick={onExitEmbedded}
            >
              ← Change product / type
            </button>
          ) : null}
        </div>
      )}

      {workflowsState === 'loading' ? <p className="text-sm text-slate-600">Loading workflows…</p> : null}
      {workflowsState === 'err' && workflowsError ? <ErrorState message={workflowsError} /> : null}

      <Stepper step={step} />

      {error ? (
        <div className="mb-4">
          <ErrorState message={error} />
        </div>
      ) : null}

      {step === 0 ? (
        <section className={intakeStepSectionClass}>
          <h2 className="bt-card-title">Product &amp; request</h2>
          <p className="text-xs text-slate-600">
            Anchor onboarding is for corporate entities ({BORROWER_TYPE_LABELS[ANCHOR_BORROWER_TYPE]}).
          </p>
          <div className={intakeStepFieldGridClass}>
            <label className={`${intakeFieldLabelClass} sm:col-span-2`}>
              <span className={intakeFieldCaptionClass}>Invoice discounting product</span>
              <select
                className={intakeFieldSelectClass}
                value={form.loanProduct}
                onChange={(e) =>
                  setForm((f) => ({
                    ...f,
                    borrowerType: ANCHOR_BORROWER_TYPE,
                    loanProduct: e.target.value as AnchorFormState['loanProduct'],
                  }))
                }
                disabled={variant === 'embedded'}
              >
                {anchorProducts.map((w) => (
                  <option key={w.id} value={w.loanProduct}>
                    {workflowLoanProductDisplayName(w.loanProduct)}
                  </option>
                ))}
              </select>
            </label>
            <label className={intakeFieldLabelClass}>
              <span className={intakeFieldCaptionClass}>Requested amount (INR) *</span>
              <input
                className={intakeFieldInputClass}
                value={form.requestedAmount}
                onChange={(e) => setForm((f) => ({ ...f, requestedAmount: e.target.value }))}
                inputMode="decimal"
              />
            </label>
            <label className={intakeFieldLabelClass}>
              <span className={intakeFieldCaptionClass}>Tenure (months)</span>
              <input
                className={intakeFieldInputClass}
                value={form.tenureMonths}
                onChange={(e) => setForm((f) => ({ ...f, tenureMonths: e.target.value }))}
                inputMode="numeric"
              />
            </label>
          </div>
        </section>
      ) : null}

      {step === 1 ? (
        <section className={intakeStepSectionClass}>
          <h2 className="bt-card-title">Corporate details</h2>
          <label className={intakeFieldLabelClass}>
            <span className={intakeFieldCaptionClass}>Corporate name</span>
            <input
              className={intakeFieldInputClass}
              value={form.corporateName}
              onChange={(e) => setForm((f) => ({ ...f, corporateName: e.target.value }))}
            />
          </label>
          <div className={intakeStepFieldGridClass}>
            <label className={intakeFieldLabelClass}>
              <span className={intakeFieldCaptionClass}>Email</span>
              <input
                type="email"
                className={intakeFieldInputClass}
                value={form.email}
                onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
              />
            </label>
            <label className={intakeFieldLabelClass}>
              <span className={intakeFieldCaptionClass}>Mobile</span>
              <input
                className={intakeFieldInputClass}
                value={form.mobile}
                onChange={(e) => setForm((f) => ({ ...f, mobile: e.target.value }))}
              />
            </label>
          </div>
          <label className={intakeFieldLabelClass}>
            <span className={intakeFieldCaptionClass}>Date of incorporation</span>
            <input
              type="date"
              className={intakeFieldInputClass}
              value={form.dateOfIncorporation}
              onChange={(e) => setForm((f) => ({ ...f, dateOfIncorporation: e.target.value }))}
            />
          </label>
          <label className={intakeFieldLabelClass}>
            <span className={intakeFieldCaptionClass}>Address</span>
            <input
              className={intakeFieldInputClass}
              value={form.addressLine}
              onChange={(e) => setForm((f) => ({ ...f, addressLine: e.target.value }))}
            />
          </label>
          <IndiaStateCityPincodeFields
            stateValue={form.state}
            cityValue={form.city}
            pincodeValue={form.pincode}
            onStateChange={(v) => setForm((f) => ({ ...f, state: v, city: '' }))}
            onCityChange={(v) => setForm((f) => ({ ...f, city: v }))}
            onPincodeChange={(v) => setForm((f) => ({ ...f, pincode: v }))}
          />
          <label className={intakeFieldLabelClass}>
            <span className={intakeFieldCaptionClass}>Country</span>
            <input
              className={intakeFieldInputClass}
              value={form.country}
              onChange={(e) => setForm((f) => ({ ...f, country: e.target.value }))}
            />
          </label>
        </section>
      ) : null}

      {step === 2 ? (
        <section className={intakeStepSectionClass}>
          <h2 className="bt-card-title">Identity &amp; bank</h2>
          <p className="text-xs text-slate-600">
            Fields follow the active anchor workflow
            {selectedWorkflow?.intakeIdentitySchema?.length ? ' configuration' : ' defaults'}.
          </p>
          <div className={intakeStepFieldGridClass}>
            {identityFields.map((fld) => (
              <label key={fld.key} className={`${intakeFieldLabelClass} sm:col-span-2`}>
                <span className={intakeFieldCaptionClass}>
                  {fld.label}
                  {fld.required ? ' *' : ''}
                </span>
                <input
                  className={intakeFieldInputClass}
                  value={String(form[fld.key] ?? '')}
                  maxLength={fld.maxLength}
                  onChange={(e) => setForm((f) => ({ ...f, [fld.key]: e.target.value }))}
                />
              </label>
            ))}
          </div>
        </section>
      ) : null}

      {step === 3 && applicationId ? (
        <section className={intakeStepSectionClass}>
          <h2 className="bt-card-title">Documents</h2>
          {docWarning ? <p className="bt-alert bt-alert-warning">{docWarning}</p> : null}
          <ul className="space-y-4">
            {docSlots.map((slot) => (
              <li key={slot.documentType} className="rounded-md border border-slate-100 bg-slate-50/80 p-4">
                <div className="mb-2 text-sm font-medium text-slate-900">{slot.label}</div>
                <p className="mb-2 text-xs text-slate-600">{slot.reason}</p>
                <div className="flex flex-wrap items-center gap-3">
                  <input
                    type="file"
                    accept=".pdf,image/*"
                    className="text-sm"
                    onChange={(e) => {
                      const file = e.target.files?.[0] ?? null
                      if (e.target) e.target.value = ''
                      void onUploadFile(slot.documentType, file)
                    }}
                    disabled={busy}
                  />
                  {form.documentUploaded[slot.documentType] ? (
                    <span className="text-xs font-medium text-emerald-800">Received</span>
                  ) : (
                    <span className="text-xs text-amber-800">Not uploaded</span>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {step === 4 ? (
        <section className={intakeStepSectionClass}>
          <h2 className="bt-card-title">Consents</h2>
          <p className="text-xs text-slate-600">{consentHelper(staffIntakeMode)}</p>
          <div className="space-y-2 text-sm text-slate-800">
            <label className="flex items-start gap-2">
              <input
                type="checkbox"
                className="mt-1"
                checked={form.consentKyc}
                onChange={(e) => setForm((f) => ({ ...f, consentKyc: e.target.checked }))}
              />
              <span>I consent to KYC verification, including use of the documents and details provided.</span>
            </label>
            <label className="flex items-start gap-2">
              <input
                type="checkbox"
                className="mt-1"
                checked={form.consentBureau}
                onChange={(e) => setForm((f) => ({ ...f, consentBureau: e.target.checked }))}
              />
              <span>I consent to a credit bureau pull and related credit checks for this application.</span>
            </label>
            <label className="flex items-start gap-2">
              <input
                type="checkbox"
                className="mt-1"
                checked={form.consentAccountAggregator}
                onChange={(e) => setForm((f) => ({ ...f, consentAccountAggregator: e.target.checked }))}
              />
              <span>
                I consent to bank statement or account-aggregated data access when required to assess the application.
              </span>
            </label>
            <label className="flex items-start gap-2">
              <input
                type="checkbox"
                className="mt-1"
                checked={form.consentComms}
                onChange={(e) => setForm((f) => ({ ...f, consentComms: e.target.checked }))}
              />
              <span>I consent to receive updates about this application on WhatsApp, SMS, and email.</span>
            </label>
          </div>
        </section>
      ) : null}

      {step === 5 && applicationId ? (
        <section className={intakeStepSectionClass}>
          <h2 className="bt-card-title">Review &amp; submit</h2>
          <div className="grid gap-3 text-sm sm:grid-cols-2">
            <div className="rounded border border-slate-100 p-3">
              <h3 className="text-xs font-semibold uppercase text-slate-500">Entity</h3>
              <p className="mt-1 text-slate-900">{BORROWER_TYPE_LABELS[form.borrowerType]}</p>
              <p className="text-slate-600">{workflowLoanProductDisplayName(form.loanProduct)}</p>
            </div>
            <div className="rounded border border-slate-100 p-3">
              <h3 className="text-xs font-semibold uppercase text-slate-500">Corporate</h3>
              <p className="mt-1 text-slate-900">{form.corporateName}</p>
              <p className="text-slate-600">
                {form.email} · {form.mobile}
              </p>
            </div>
            <div className="rounded border border-slate-100 p-3 sm:col-span-2">
              <h3 className="text-xs font-semibold uppercase text-slate-500">Intake</h3>
              <p className="mt-1 text-slate-900">Anchor (invoice discounting)</p>
              <p className="text-xs text-slate-500">Application ref: {applicationId}</p>
            </div>
          </div>
        </section>
      ) : null}

      <div className={intakeStepActionsClass}>
        {step > 0 ? (
          <button type="button" className={intakeSecondaryButtonClass} onClick={goBack} disabled={busy}>
            Back
          </button>
        ) : null}
        {step < 5 ? (
          <button
            type="button"
            className={intakePrimaryButtonClass}
            onClick={() => void handleNext()}
            disabled={busy || (step === 3 && !applicationId)}
          >
            {busy ? 'Saving…' : 'Continue'}
          </button>
        ) : (
          <button type="button" className={intakePrimaryButtonClass} onClick={() => void onSubmitFinal()} disabled={busy}>
            {busy ? 'Submitting…' : 'Submit for KYC'}
          </button>
        )}
      </div>
    </div>
  )
}
